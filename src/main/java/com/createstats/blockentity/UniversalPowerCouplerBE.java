package com.createstats.blockentity;

import com.createstats.block.UniversalPowerCouplerBlock;
import com.createstats.compat.NewAgeCompat;
import com.createstats.compat.NewAgeCompatHelper;
import com.createstats.power.CouplerBuffer;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.ForeignTerminalNode;
import com.createstats.power.PowerCouplerMode;
import com.createstats.power.NewAgeFeed;
import com.createstats.power.WireTier;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// new age rolls foreign blocks back off a getEnergyStored delta. anything forwarding to a network can't honour that, so this block exists
public class UniversalPowerCouplerBE extends SmartBlockEntity
        implements FeManagedLoad, FeWireTerminal, IHaveGoggleInformation {
    public static final int RATE_FE_PER_TICK = 8192;

    public static final int BUFFER_FE = 16384;

    private static final int LOOP_CHECK_INTERVAL = 20;

    private final CouplerBuffer buffer = new CouplerBuffer(BUFFER_FE);
    private final IEnergyStorage seamPort = new SeamPort();
    private final IEnergyStorage gridPort = new GridPort();
    private final FeManagedState managed = new FeManagedState();

    private int importFePerTick;
    private int exportFePerTick;
    private boolean looped;

    private boolean foreignSeamCached;
    private boolean foreignSeamValid;

    private final List<BlockPos> links = new java.util.ArrayList<>(2);

    private final java.util.Map<Long, WireTier> linkTiers = new java.util.HashMap<>(4);

    @Nullable
    private BlockPos seamLink;

    private static final int MAX_GRID_LINKS = com.createstats.power.ConnectorTier.POWER.maxLinks();

    private int syncedImport;
    private int syncedExport;
    private boolean syncedLooped;
    private long lastSyncTick = Long.MIN_VALUE;

    private long modeChangedAt = Long.MIN_VALUE;
    private long syncedModeChangedAt = Long.MIN_VALUE;

    @Nullable
    private PowerCouplerMode seenMode;

    private static final int SYNC_INTERVAL_TICKS = 10;

    private static final int SYNC_THRESHOLD_FE = 64;

    public UniversalPowerCouplerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public PowerCouplerMode mode() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(UniversalPowerCouplerBlock.MODE)
                ? state.getValue(UniversalPowerCouplerBlock.MODE) : PowerCouplerMode.PULL;
    }

    public Direction foreignFace() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(UniversalPowerCouplerBlock.FACING)
                ? state.getValue(UniversalPowerCouplerBlock.FACING) : Direction.NORTH;
    }

    public Direction gridFace() {
        return this.foreignFace().getOpposite();
    }

    @Nullable
    public IEnergyStorage seamHandle() {
        if (this.wiredSeamPos() != null) {
            return null;
        }
        if (!this.foreignSeamValid && this.level != null) {
            this.foreignSeamCached =
                    NewAgeFeed.isForeignSeam(this.level, this.getBlockPos(), this.foreignFace());
            this.foreignSeamValid = true;
        }
        return this.foreignSeamCached ? this.seamPort : null;
    }

    public void invalidateForeignSeam() {
        this.foreignSeamValid = false;
    }

    @Nullable
    public BlockPos wiredSeamPos() {
        return this.seamLink;
    }

    public boolean isSeamLink(@Nullable BlockPos link) {
        return link != null && link.equals(this.seamLink);
    }

    public int gridLinkCount() {
        return this.links.size() - (this.seamLink == null ? 0 : 1);
    }

    public static boolean isSeamTarget(@Nullable net.minecraft.world.level.Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FeWireTerminal || be instanceof com.createstats.power.FeStudPanel
                || be instanceof com.createstats.power.FeGridMember) {
            return false;
        }
        if (ForeignTerminalNode.isBridgeTarget(level, pos)) {
            return true;
        }

        if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null) {
            return true;
        }
        for (Direction face : Direction.values()) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null) {
                return true;
            }
        }
        return false;
    }

    // asMouth: no capacity, never a battery, and a simulate that doesn't secretly transfer
    @Nullable
    private IEnergyStorage wiredSeamHandler() {
        final BlockPos at = this.wiredSeamPos();
        if (this.level == null || at == null || !this.level.isLoaded(at)) {
            return null;
        }
        IEnergyStorage handler = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, null);
        if (handler == null) {
            for (Direction face : Direction.values()) {
                handler = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, face);
                if (handler != null) {
                    break;
                }
            }
        }
        if (handler == null) {
            return null;
        }
        final net.minecraft.world.level.block.entity.BlockEntity endpoint = this.level.getBlockEntity(at);
        if (NewAgeFeed.isNetworkMouth(endpoint, handler)) {
            return NewAgeFeed.seamWireMouth(endpoint, handler);
        }
        return ForeignTerminalNode.asMouth(this.level, at, handler);
    }

    private int wiredSeamRate() {
        final BlockPos at = this.wiredSeamPos();
        if (at == null) {
            return 0;
        }
        return Math.min(RATE_FE_PER_TICK,
                PowerConnectorBE.baseTransferRate() * this.tierOf(at).multiplier());
    }

    @Override
    public BlockPos terminalPos() {
        return this.worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return this.links;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        return this.linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        this.links.add(at);
        this.linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        if (isSeamTarget(this.level, at)) {
            this.seamLink = at;
        }
        this.syncLinks();
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        this.linkTiers.remove(far.asLong());
        if (this.links.remove(far)) {
            if (far.equals(this.seamLink)) {
                this.seamLink = null;
            }
            this.syncLinks();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (this.level == null || this.level.isClientSide() || this.links.isEmpty()) {
            return;
        }
        final java.util.Map<WireTier, Integer> refund = new java.util.EnumMap<>(WireTier.class);
        for (BlockPos link : new java.util.ArrayList<>(this.links)) {
            refund.merge(this.tierOf(link), 1, Integer::sum);

            if (link.equals(this.seamLink)) {
                continue;
            }
            if (this.level.isLoaded(link)
                    && this.level.getBlockEntity(link) instanceof FeWireTerminal far) {
                far.removeDeadLink(this.worldPosition);
            }
        }
        this.links.clear();
        this.linkTiers.clear();
        this.seamLink = null;
        this.syncLinks();
        for (java.util.Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(this.level, this.worldPosition,
                    new net.minecraft.world.item.ItemStack(
                            com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    @Override
    public int maxLinks() {
        return 1 + MAX_GRID_LINKS;
    }

    @Nullable
    public Component refuseSocket(com.createstats.power.CouplerSockets.End socket) {
        if (socket == com.createstats.power.CouplerSockets.End.FOREIGN) {
            return this.seamLink == null ? null
                    : Component.translatable("message.create_stats.coupler.seam_socket_full");
        }
        return this.gridLinkCount() < MAX_GRID_LINKS ? null
                : Component.translatable("message.create_stats.coupler.grid_socket_full",
                        MAX_GRID_LINKS);
    }

    @Override
    @Nullable
    public Component refuseLink(@Nullable net.minecraft.world.level.Level world, BlockPos far) {
        final net.minecraft.world.level.Level here = world == null ? this.level : world;
        if (isSeamTarget(here, far)) {
            return this.refuseSocket(com.createstats.power.CouplerSockets.End.FOREIGN);
        }

        final net.minecraft.world.level.block.entity.BlockEntity there =
                here == null ? null : here.getBlockEntity(far);
        if (there instanceof UniversalPowerCouplerBE) {
            return Component.translatable("message.create_stats.coupler.no_coupler_chain");
        }

        if (there instanceof com.createstats.power.FeStudPanel
                && !(there instanceof FeWireTerminal)) {
            return Component.translatable("message.create_stats.foreign_wire.stud_no_bridge");
        }
        return this.refuseSocket(com.createstats.power.CouplerSockets.End.GRID);
    }

    private void syncLinks() {
        this.setChanged();
        this.invalidateForeignSeam();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    public IEnergyStorage gridHandle() {
        return this.gridPort;
    }

    public int importFePerTick() {
        return this.importFePerTick;
    }

    public int exportFePerTick() {
        return this.exportFePerTick;
    }

    public boolean isLooped() {
        return this.looped;
    }

    public int storedFe() {
        return this.buffer.energy();
    }

    public long modeChangedAt() {
        return this.modeChangedAt;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.foreignSeamValid = false;

        final PowerCouplerMode mode = this.mode();
        if (this.seenMode == null) {
            this.seenMode = mode;
        } else if (this.seenMode != mode) {
            this.seenMode = mode;
            this.modeChangedAt = this.level.getGameTime();
        }
        final CouplerBuffer.Tick closed = this.buffer.roll(this.level.getGameTime());
        if (closed != null) {
            this.importFePerTick = Math.max(0, closed.seamMoved());
            this.exportFePerTick = Math.max(0, -closed.seamMoved());
        }
        if (this.level.getGameTime() % LOOP_CHECK_INTERVAL == 0) {
            final boolean wasLooped = this.looped;
            this.looped = this.detectLoop();
            if (wasLooped != this.looped) {
                this.setChanged();
            }
        }
        this.pumpSeamWire();
        this.pumpThrough();
        this.maybeSync();
    }

    private void pumpSeamWire() {
        if (this.looped || this.level == null || this.wiredSeamPos() == null) {
            return;
        }
        final BlockPos at = this.wiredSeamPos();
        if (!this.level.isLoaded(at)) {
            return;
        }
        if (!isSeamTarget(this.level, at)) {
            this.removeDeadLink(at);
            return;
        }
        final IEnergyStorage foreign = this.wiredSeamHandler();
        if (foreign == null) {
            return;
        }
        final int rate = this.wiredSeamRate();
        if (rate <= 0) {
            return;
        }
        if (this.mode() == PowerCouplerMode.PULL) {
            if (!foreign.canExtract()) {
                return;
            }

            final int room = this.buffer.seamReceive(rate, true);
            final int taken = room > 0 ? foreign.extractEnergy(room, false) : 0;
            if (taken > 0) {
                this.buffer.seamReceive(taken, false);
            }
            return;
        }
        if (!foreign.canReceive()) {
            return;
        }

        final int offer = this.buffer.seamExtract(rate, true);
        final int wanted = offer > 0 ? foreign.receiveEnergy(offer, true) : 0;
        if (wanted > 0) {
            final int moved = foreign.receiveEnergy(wanted, false);
            this.buffer.seamExtract(moved, false);
        }
    }

    private void maybeSync() {
        if (this.level == null) {
            return;
        }
        final long now = this.level.getGameTime();

        final boolean fault = this.syncedLooped != this.looped
                || this.syncedModeChangedAt != this.modeChangedAt;
        if (!fault && now - this.lastSyncTick < SYNC_INTERVAL_TICKS) {
            return;
        }
        final boolean moved = Math.abs(this.importFePerTick - this.syncedImport) >= SYNC_THRESHOLD_FE
                || Math.abs(this.exportFePerTick - this.syncedExport) >= SYNC_THRESHOLD_FE

                || (this.importFePerTick == 0 && this.syncedImport != 0)
                || (this.exportFePerTick == 0 && this.syncedExport != 0);
        if (!fault && !moved) {
            return;
        }
        this.syncedImport = this.importFePerTick;
        this.syncedExport = this.exportFePerTick;
        this.syncedLooped = this.looped;
        this.syncedModeChangedAt = this.modeChangedAt;
        this.lastSyncTick = now;
        this.sendData();
    }

    private void pumpThrough() {
        if (this.looped || this.level == null) {
            return;
        }
        final Direction face = this.gridFace();
        final BlockPos at = this.getBlockPos().relative(face);
        if (PowerMeterBE.isWiring(this.level.getBlockEntity(at))) {
            return;
        }
        final IEnergyStorage neighbour =
                this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, face.getOpposite());
        if (neighbour == null) {
            return;
        }
        if (this.canGridGive() && neighbour.canReceive()) {
            final int offer = this.buffer.gridExtract(RATE_FE_PER_TICK, true, RATE_FE_PER_TICK);
            final int wanted = offer > 0 ? neighbour.receiveEnergy(offer, true) : 0;
            if (wanted > 0) {
                final int moved = neighbour.receiveEnergy(wanted, false);
                this.buffer.gridExtract(moved, false, RATE_FE_PER_TICK);
            }
        } else if (this.canGridReceive() && neighbour.canExtract()) {
            final int room = this.buffer.gridReceive(RATE_FE_PER_TICK, true, RATE_FE_PER_TICK);
            final int available = room > 0 ? neighbour.extractEnergy(room, true) : 0;
            if (available > 0) {
                final int booked = this.buffer.gridReceive(available, false, RATE_FE_PER_TICK);
                neighbour.extractEnergy(booked, false);
            }
        }
    }

    private boolean detectLoop() {
        if (this.level == null || !NewAgeCompat.isAvailable()) {
            return false;
        }

        final BlockPos wired = this.wiredSeamPos();
        final BlockPos seamPos = wired != null ? wired : this.getBlockPos().relative(this.foreignFace());
        if (!this.level.isLoaded(seamPos)) {
            return false;
        }
        final NewAgeCompatHelper.NetworkView view = NewAgeCompatHelper.surveyNetwork(
                this.level.getBlockEntity(seamPos));
        if (view == null) {
            return false;
        }
        final FeWireNetwork mine = this.gridSideNetwork();
        if (mine == null) {
            return false;
        }
        for (BlockPos supported : view.supportedPositions()) {
            if (supported.equals(this.getBlockPos()) || !this.level.isLoaded(supported)) {
                continue;
            }
            if (!(this.level.getBlockEntity(supported) instanceof UniversalPowerCouplerBE other)) {
                continue;
            }

            if (other.mode() != this.mode() && other.gridSideNetwork() == mine) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private FeWireNetwork gridSideNetwork() {
        return this.level == null ? null : FeWireNetwork.liveNetworkOf(
                this.level.getBlockEntity(this.getBlockPos().relative(this.gridFace())));
    }

    private boolean canGridGive() {
        return this.mode().gridGives() && !this.looped;
    }

    private boolean canGridReceive() {
        return this.mode().gridReceives() && !this.looped && !this.managed.isSuppressed();
    }

    private final class SeamPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return UniversalPowerCouplerBE.this.buffer.seamReceive(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return UniversalPowerCouplerBE.this.buffer.seamExtract(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return UniversalPowerCouplerBE.this.buffer.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER_FE;
        }

        @Override
        public boolean canExtract() {
            return UniversalPowerCouplerBE.this.mode().seamGives() && !UniversalPowerCouplerBE.this.looped;
        }

        @Override
        public boolean canReceive() {
            return UniversalPowerCouplerBE.this.mode().seamReceives() && !UniversalPowerCouplerBE.this.looped;
        }
    }

    private final class GridPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!UniversalPowerCouplerBE.this.canGridReceive()) {
                return 0;
            }
            return UniversalPowerCouplerBE.this.buffer.gridReceive(maxReceive, simulate, RATE_FE_PER_TICK);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!UniversalPowerCouplerBE.this.canGridGive()) {
                return 0;
            }
            return UniversalPowerCouplerBE.this.buffer.gridExtract(maxExtract, simulate, RATE_FE_PER_TICK);
        }

        @Override
        public int getEnergyStored() {
            return UniversalPowerCouplerBE.this.buffer.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER_FE;
        }

        @Override
        public boolean canExtract() {
            return UniversalPowerCouplerBE.this.canGridGive();
        }

        @Override
        public boolean canReceive() {
            return UniversalPowerCouplerBE.this.canGridReceive();
        }
    }

    @Override
    public int ratedDrawFePerTick() {
        return this.mode().gridReceives() && !this.looped && !this.managed.isForcedOff()
                ? RATE_FE_PER_TICK : 0;
    }

    @Override
    public int measuredDrawFePerTick() {
        return this.mode().gridReceives() ? this.exportFePerTick : 0;
    }

    @Override
    public FePriority managedPriority() {
        return this.managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        this.managed.setPriority(tier);
        this.setChanged();
    }

    @Override
    public FeLock managedLock() {
        return this.managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        this.managed.setLock(lock);
        this.setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (this.managed.isShed() == shed) {
            return;
        }
        this.managed.setShed(shed);

        this.setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return this.managed.isShed();
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Priority", FePriority.DEFAULT.ordinal());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putInt("Energy", this.buffer.energy());
        tag.putInt("Priority", this.managed.priorityOrdinal());
        tag.putInt("Lock", this.managed.lockOrdinal());
        tag.putBoolean("Looped", this.looped);
        tag.putInt("ImportFe", this.importFePerTick);
        tag.putInt("ExportFe", this.exportFePerTick);
        tag.putLong("ModeChangedAt", this.modeChangedAt);

        final net.minecraft.nbt.ListTag wires = new net.minecraft.nbt.ListTag();
        for (BlockPos link : this.links) {
            final CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", link.asLong());
            entry.putInt("Tier", this.tierOf(link).ordinal());

            entry.putBoolean("Seam", link.equals(this.seamLink));
            wires.add(entry);
        }
        tag.put("SeamWires", wires);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.buffer.setEnergy(tag.getInt("Energy"));

        this.managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        this.looped = tag.getBoolean("Looped");
        this.importFePerTick = tag.getInt("ImportFe");
        this.exportFePerTick = tag.getInt("ExportFe");
        this.modeChangedAt = tag.contains("ModeChangedAt")
                ? tag.getLong("ModeChangedAt") : Long.MIN_VALUE;
        this.links.clear();
        this.linkTiers.clear();
        this.seamLink = null;
        final net.minecraft.nbt.ListTag wires = tag.getList("SeamWires", net.minecraft.nbt.Tag.TAG_COMPOUND);
        final WireTier[] grades = WireTier.values();
        for (int i = 0; i < wires.size(); i++) {
            final CompoundTag entry = wires.getCompound(i);
            final BlockPos at = BlockPos.of(entry.getLong("Pos"));
            final int grade = entry.getInt("Tier");
            this.links.add(at);
            this.linkTiers.put(at.asLong(),
                    grade >= 0 && grade < grades.length ? grades[grade] : WireTier.COPPER);

            if (entry.getBoolean("Seam") || (!entry.contains("Seam") && wires.size() == 1)) {
                this.seamLink = at;
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title("Universal Power Coupler"));

        final BlockPos wired = this.wiredSeamPos();
        final BlockPos seamPos = wired != null ? wired : this.getBlockPos().relative(this.foreignFace());
        final net.minecraft.world.level.block.entity.BlockEntity endpoint =
                this.level == null || !this.level.isLoaded(seamPos)
                        ? null : this.level.getBlockEntity(seamPos);
        final boolean newAge = NewAgeCompat.isAvailable()
                && NewAgeCompatHelper.isElectricalConnector(endpoint);
        final boolean createAddition = endpoint != null
                && endpoint.getClass().getName()
                        .startsWith("com.mrh0.createaddition.blocks.connector.");

        if (wired == null && this.level != null && NewAgeFeed.connectorOnNonForeignFace(
                this.level, this.getBlockPos(), this.foreignFace())) {
            tooltip.add(FeGoggle.bad(Component.translatable(
                    "create_stats.universal_power_coupler.wrong_face")));
            return true;
        }

        if (this.looped) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("create_stats.universal_power_coupler.looped")
                            .withStyle(ChatFormatting.RED)));
            if (isPlayerSneaking && !newAge) {
                tooltip.add(Component.literal("    ").append(
                        Component.translatable("create_stats.universal_power_coupler.loop_new_age_only")
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
            return true;
        }

        final PowerCouplerMode mode = this.mode();
        tooltip.add(Component.literal("    ").append(
                Component.translatable(mode == PowerCouplerMode.PULL
                                ? "create_stats.universal_power_coupler.mode.pull"
                                : "create_stats.universal_power_coupler.mode.push")
                        .withStyle(ChatFormatting.AQUA)));

        final boolean served = this.level != null && (wired != null
                ? isSeamTarget(this.level, wired)
                : NewAgeFeed.isForeignSeam(this.level, this.getBlockPos(), this.foreignFace()));
        if (!served) {
            tooltip.add(FeGoggle.bad(Component.translatable(
                    "create_stats.universal_power_coupler.no_connector")));
            return true;
        }

        tooltip.add(Component.literal("    ").append(
                Component.translatable(newAge
                                ? "create_stats.universal_power_coupler.foreign_new_age"
                                : createAddition
                                        ? "create_stats.universal_power_coupler.foreign_createaddition"
                                        : "create_stats.universal_power_coupler.foreign_generic")
                        .withStyle(ChatFormatting.DARK_GRAY)));

        if (wired != null) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("create_stats.universal_power_coupler.wired_seam",
                                    this.level.getBlockState(wired).getBlock().getName(),
                                    Mth.ceil(Math.sqrt(this.getBlockPos().distSqr(wired))))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (newAge && mode == PowerCouplerMode.PUSH
                && !NewAgeCompatHelper.isPullMode(this.level.getBlockState(seamPos))) {
            tooltip.add(FeGoggle.bad(Component.translatable(
                    "create_stats.universal_power_coupler.needs_their_pull")));
            return true;
        }

        if (newAge && wired != null && mode == PowerCouplerMode.PULL
                && NewAgeCompatHelper.pullSourceCount(endpoint) == 0) {
            tooltip.add(FeGoggle.bad(Component.translatable(
                    "create_stats.universal_power_coupler.no_pull_sources")));
            return true;
        }

        final int crossing = mode == PowerCouplerMode.PULL
                ? this.importFePerTick : this.exportFePerTick;
        tooltip.add(Component.literal("    ").append(
                Component.literal(String.format(Locale.ROOT, "%,d / %,d FE/t",
                        crossing, RATE_FE_PER_TICK))
                        .withStyle(crossing > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)));

        if (!newAge && crossing == 0) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("create_stats.universal_power_coupler.no_flow")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (this.gridLinkCount() > 0) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("create_stats.universal_power_coupler.grid_wires",
                                    this.gridLinkCount())
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (isPlayerSneaking) {
            tooltip.add(FeGoggle.amount("Buffer", this.buffer.energy(), BUFFER_FE));
            if (newAge) {
                final NewAgeCompatHelper.NetworkView view =
                        NewAgeCompatHelper.surveyNetwork(endpoint);
                if (view != null) {
                    tooltip.add(FeGoggle.dim(String.format(Locale.ROOT,
                            "Their network: %d nodes, %d sources, %,d FE/t made",
                            view.nodeCount(), view.sourceCount(), view.generation())));
                    tooltip.add(FeGoggle.dim(String.format(Locale.ROOT,
                            "Their wire: %,d FE/t", view.conductivity())));
                }
            }
        } else {
            tooltip.add(FeGoggle.more());
        }
        return true;
    }
}
