package com.createstats.blockentity;

import com.createstats.block.SecuritySensorBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.SecurityDevice;
import com.createstats.security.Clearance;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecurityRoster;
import com.createstats.security.HazardWatch;
import com.createstats.security.SensorKind;
import com.createstats.security.SensorWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SecuritySensorBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        SecurityDevice, BoreLinks.Socket, com.createstats.security.GovernedDevice,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int DRAIN = 1;

    private static final int BUFFER = 200;

    private static final int POLL_TICKS = 5;

    private static final int POLICY_TICKS = 40;

    private static final int REPORT_COOLDOWN = 40;

    private static final int LOG_ONLY_COOLDOWN = 200;

    private static final int LENS_HOLD = 30;

    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int SYNC_STEP = BUFFER / 8;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
    private int ratedDraw;
    private int measuredDraw;

    private int lastSyncedEnergy = Integer.MIN_VALUE;
    private boolean lastSyncedPowered;
    private int ticksSinceSync;

    private final List<BlockPos> bores = new java.util.ArrayList<>(1);

    private int sincePoll;
    private int sincePolicy = POLICY_TICKS;
    private boolean staggered;

    private int sweepLayer;

    private boolean sweepFound;

    private int radius = -1;

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private java.util.UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;

    @Nullable
    private SecurityRoster roster;

    private int required = Clearance.DEFAULT_REQUIRED;

    private boolean armed = true;

    private boolean linked;

    private long lastTrip = Long.MIN_VALUE;

    private boolean clientGoverned;

    private boolean powered;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public SecuritySensorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        managed.setPriority(defaultTier(SecuritySensorBlock.kindOf(state)));
    }

    // security sensors default high. shedding one isn't "a machine stopped", it's an unwatched house
    public static FePriority defaultTier(@Nullable SensorKind kind) {
        if (kind == null) {
            return FePriority.CRITICAL;
        }
        return switch (kind) {
            case MOTION, FIRE -> FePriority.CRITICAL;
            case TAMPER, WATER -> FePriority.HIGH;
        };
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static int drawPerTick() {
        return DRAIN;
    }

    public SensorKind kind() {
        final SensorKind fromBlock = SecuritySensorBlock.kindOf(getBlockState());
        return fromBlock == null ? SensorKind.MOTION : fromBlock;
    }

    public int radius() {
        return radius < 0 ? kind().defaultRadius() : radius;
    }

    public boolean armedHere() {
        return armed;
    }

    public boolean watching() {
        return kind().isHazard() || armed;
    }

    public boolean tripping() {
        return level != null && lastTrip != Long.MIN_VALUE
                && level.getGameTime() - lastTrip < LENS_HOLD;
    }

    public boolean hasPower() {
        if (level != null && level.isClientSide()) {
            return powered;
        }
        return energy >= DRAIN && !managed.isSuppressed();
    }

    public boolean governed() {
        return level != null && level.isClientSide() ? clientGoverned : roster != null;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!staggered) {
            staggered = true;
            final int spread = Math.floorMod(worldPosition.hashCode(), POLL_TICKS);
            sincePoll = spread;
            sincePolicy = POLICY_TICKS - Math.floorMod(worldPosition.hashCode(), POLICY_TICKS);
        }

        if (network == null || network.isDirty()) {
            if (BoreLinks.prune(level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        ratedDraw = managed.isForcedOff() ? 0 : DRAIN;
        final boolean running = !managed.isSuppressed() && energy >= DRAIN;
        if (running) {
            energy -= DRAIN;
            measuredDraw = DRAIN;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        if (++sincePolicy >= POLICY_TICKS) {
            sincePolicy = 0;
            findPolicy();
        }

        powered = running;
        pushReadout();

        if (!running) {
            return;
        }

        if (++sincePoll < POLL_TICKS) {
            return;
        }
        sincePoll = 0;
        poll();
    }

    private void findPolicy() {
        if (level == null) {
            return;
        }
        final SecurityLink.Contact<HomeSecurityPanelBE> contact = SecurityLink.resolve(panel(),
                panelPos != null, panelPos != null && level.isLoaded(panelPos),
                network != null || offeredPanel != null, this::searchCircuit);
        if (contact.state() == SecurityLink.UNKNOWN) {
            return;
        }
        if (contact.state().isLost()) {
            loseLink();
            return;
        }
        final HomeSecurityPanelBE found = contact.panel();
        if (found == null) {
            return;
        }
        final boolean wasLinked = linked;
        linked = true;
        panelPos = found.getBlockPos();
        panelHousehold = com.createstats.security.SecurityIdentity.householdOf(found);
        offeredPanel = null;
        final boolean wasGoverned = roster != null;
        final int wasRequired = required;
        final boolean wasArmed = armed;
        final int wasRadius = radius;
        roster = found.rosterSnapshot();
        required = found.requiredLevel(worldPosition);
        radius = SensorKind.clampRadius(found.sensorRadius(worldPosition, kind()));
        armed = found.sensorArmed(kind(), worldPosition);
        setChanged();

        if (!wasGoverned || !wasLinked || wasRequired != required || wasArmed != armed
                || wasRadius != radius) {
            sync();
        }
    }

    private void loseLink() {
        panelPos = null;
        if (!linked && roster == null) {
            return;
        }
        linked = false;

        roster = null;
        armed = false;
        setChanged();
        sync();
    }

    @Nullable
    private HomeSecurityPanelBE searchCircuit() {
        final HomeSecurityPanelBE wired = com.createstats.security.PanelSweep.nearest(level, network,
                worldPosition, com.createstats.security.SecuritySegment.policyAnchor(level, worldPosition),
                panelHousehold);
        return wired != null ? wired : com.createstats.security.SecurityIdentity.offered(level, offeredPanel,
                worldPosition, panelHousehold);
    }

    public void repoll() {
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public void offerPanel(BlockPos panel) {
        if (panelPos == null && panel != null && !panel.equals(offeredPanel)) {
            offeredPanel = panel.immutable();
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    public void releasePanel(BlockPos panel) {
        if (panel == null) {
            return;
        }
        if (panel.equals(offeredPanel)) {
            offeredPanel = null;
        }
        if (panel.equals(panelPos)) {
            panelPos = null;
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    @Nullable
    public java.util.UUID governingHousehold() {
        return panelHousehold;
    }

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (level == null || panelPos == null || !level.isLoaded(panelPos)) {
            return null;
        }
        return level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE panel
                && com.createstats.security.SecurityIdentity.accepts(level, panelHousehold, worldPosition, panel)
                ? panel : null;
    }

    private void poll() {
        switch (kind()) {
            case MOTION -> pollMotion();
            case FIRE -> pollFire();
            case WATER -> pollWater();

            case TAMPER -> {
            }
        }
    }

    private void pollMotion() {
        if (level == null) {
            return;
        }
        final double r = radius();
        final AABB box = new AABB(worldPosition).inflate(r);
        final double rSq = r * r;
        final double cx = worldPosition.getX() + 0.5;
        final double cy = worldPosition.getY() + 0.5;
        final double cz = worldPosition.getZ() + 0.5;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!entity.isAlive() || entity.distanceToSqr(cx, cy, cz) > rSq) {
                continue;
            }
            if (!trips(entity)) {
                continue;
            }
            report(entity.getName().getString());
            return;
        }
    }

    // hostiles still trip. a creeper at the wall is exactly when the doors should shut
    private boolean trips(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isSpectator() && !player.isCreative() && !permits(player);
        }
        return entity instanceof Enemy;
    }

    public boolean permits(Player player) {
        return roster == null || roster.passes(player.getUUID(), required);
    }

    private void pollFire() {
        if (level == null) {
            return;
        }
        for (Entity entity : level.getEntitiesOfClass(Entity.class,
                new AABB(worldPosition).inflate(radius()))) {
            if (entity.isOnFire()) {
                sweepFound = true;
                report(entity.getName().getString() + " on fire");

                sweep(true);
                return;
            }
        }
        final String found = sweep(true);
        if (found != null) {
            report(found);
        }
    }

    private void pollWater() {
        final String found = sweep(false);
        if (found != null) {
            report(found);
        }
    }

    @Nullable
    private String sweep(boolean fire) {
        if (level == null) {
            return null;
        }
        final int r = radius();

        final int lowest = -r;
        final int highest = fire ? r : 0;
        final int layers = highest - lowest + 1;
        if (this.sweepLayer >= layers) {
            this.sweepLayer = 0;
        }
        final int dy = lowest + this.sweepLayer;
        this.sweepLayer = (this.sweepLayer + 1) % layers;
        final String found = sweepLayer(fire, r, dy);
        if (found != null) {
            this.sweepFound = true;
        }
        if (this.sweepLayer == 0) {
            final HomeSecurityPanelBE panel = panel();
            if (panel != null) {
                panel.observeHazard(worldPosition, kind(),
                        this.sweepFound ? HazardWatch.Report.SWEEP_FOUND : HazardWatch.Report.SWEEP_CLEAN,
                        layers * POLL_TICKS, REPORT_COOLDOWN);
            }
            this.sweepFound = false;
        }
        return found;
    }

    @Nullable
    private String sweepLayer(boolean fire, int r, int dy) {
        if (level == null) {
            return null;
        }
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                cursor.set(worldPosition.getX() + dx, worldPosition.getY() + dy,
                        worldPosition.getZ() + dz);
                if (!level.isLoaded(cursor)) {
                    continue;
                }
                final BlockState state = level.getBlockState(cursor);
                if (fire) {
                    if (state.is(BlockTags.FIRE)) {
                        return state.getBlock().getName().getString();
                    }

                    if (state.is(BlockTags.CAMPFIRES)
                            && state.hasProperty(BlockStateProperties.LIT)
                            && state.getValue(BlockStateProperties.LIT)) {
                        return state.getBlock().getName().getString();
                    }
                    if (state.getFluidState().is(FluidTags.LAVA)) {
                        return "lava";
                    }
                } else {
                    final FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
                        return "water";
                    }
                }
            }
        }
        return null;
    }

    public void report(String cause) {
        if (level == null || level.isClientSide() || !hasPower()) {
            return;
        }
        final long now = level.getGameTime();

        final int cooldown = watching() ? REPORT_COOLDOWN : LOG_ONLY_COOLDOWN;
        if (lastTrip != Long.MIN_VALUE && now - lastTrip < cooldown) {
            return;
        }
        final HomeSecurityPanelBE panel = panel();
        if (panel == null) {
            lastTrip = now;
            sync();
            return;
        }
        if (kind().isHazard()) {
            panel.observeHazard(worldPosition, kind(), HazardWatch.Report.POSITIVE, sweepTicks(), REPORT_COOLDOWN);
        }
        panel.tripped(worldPosition, kind(), cause);
        lastTrip = now;
        setChanged();
        sync();
    }

    private int sweepTicks() {
        final int r = radius();
        final int layers = kind() == SensorKind.FIRE ? 2 * r + 1 : r + 1;
        return layers * POLL_TICKS;
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.SENSOR;
    }

    @Override
    public DeviceState deviceState() {
        if (!hasPower()) {
            return DeviceState.UNPOWERED;
        }
        return tripping() ? DeviceState.ACTIVE : DeviceState.IDLE;
    }

    @Override
    public float integrity() {
        return -1.0f;
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return 64;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public BlockPos borePosition() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> bores() {
        return bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (bores.contains(far)) {
            return false;
        }
        bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        setChanged();
        invalidateNetwork();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(getBlockState().getBlock().getName()));
        if (!hasPower()) {
            tooltip.add(FeGoggle.bad("No power, blind"));
        } else if (tripping()) {
            tooltip.add(FeGoggle.warn("Tripped"));
        } else if (kind().isHazard()) {
            tooltip.add(FeGoggle.ok("Always watching"));
        } else if (armed) {
            tooltip.add(FeGoggle.ok("Armed"));
        } else {
            tooltip.add(FeGoggle.dim("Logging only"));
        }

        tooltip.add(FeGoggle.dim(watchLine() + ", " + radius() + " blocks"));
        tooltip.add(FeGoggle.stored(energy, BUFFER));
        tooltip.add(FeGoggle.draw(measuredDraw));
        if (!governed()) {
            tooltip.add(FeGoggle.warn("No security panel on this circuit, nothing to report to"));
        } else if (!kind().isHazard()) {
            tooltip.add(FeGoggle.dim("Ignores " + Clearance.badge(required).getString() + " and above"));
        }
        return true;
    }

    private String watchLine() {
        return switch (kind()) {
            case MOTION -> "Intruders and hostile mobs";
            case TAMPER -> "Blocks broken, placed or blown up";
            case FIRE -> "Fire, lava and burning creatures";
            case WATER -> "Water, at or below itself";
        };
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && kind() == SensorKind.TAMPER) {
            SensorWatch.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            SensorWatch.remove(level, worldPosition);
        }
        invalidateNetwork();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            ticksSinceSync = 0;
            lastSyncedEnergy = energy;
            lastSyncedPowered = powered;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void pushReadout() {
        if (lastSyncedEnergy == Integer.MIN_VALUE || powered != lastSyncedPowered) {
            sync();
            return;
        }
        if (ticksSinceSync < SYNC_INTERVAL_TICKS) {
            ticksSinceSync++;
            return;
        }
        if (Math.abs(energy - lastSyncedEnergy) < SYNC_STEP) {
            return;
        }
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putInt("Radius", radius);
        tag.putInt("Required", required);
        tag.putBoolean("Armed", armed);
        tag.putLong("LastTrip", lastTrip);
        if (panelPos != null) {
            tag.putLong("Panel", panelPos.asLong());
        }
        if (panelHousehold != null) {
            tag.putUUID("PanelHousehold", panelHousehold);
        }

        if (roster != null) {
            tag.put("Roster", roster.save());
        }
        BoreLinks.save(tag, bores);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.min(BUFFER, tag.getInt("Energy"));
        if (tag.contains("Tier")) {
            managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }
        radius = tag.contains("Radius") ? tag.getInt("Radius") : -1;
        required = Clearance.clamp(tag.contains("Required")
                ? tag.getInt("Required") : Clearance.DEFAULT_REQUIRED);
        armed = !tag.contains("Armed") || tag.getBoolean("Armed");
        lastTrip = tag.contains("LastTrip") ? tag.getLong("LastTrip") : Long.MIN_VALUE;
        panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
        panelHousehold = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        if (tag.contains("Roster")) {
            final SecurityRoster loaded = new SecurityRoster();
            loaded.load(tag.getCompound("Roster"));
            roster = loaded;
        } else {
            roster = null;
        }
        bores.clear();
        bores.addAll(BoreLinks.load(tag));
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Governed", roster != null);

        tag.putInt("Energy", energy);
        tag.putInt("Draw", measuredDraw);
        tag.putBoolean("Armed", armed);
        tag.putLong("LastTrip", lastTrip);
        tag.putInt("Radius", radius);

        tag.putInt("Required", required);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientGoverned = tag.getBoolean("Governed");
        energy = Math.min(BUFFER, tag.getInt("Energy"));
        measuredDraw = tag.getInt("Draw");
        powered = measuredDraw > 0;
        armed = tag.getBoolean("Armed");
        lastTrip = tag.getLong("LastTrip");
        radius = tag.getInt("Radius");
        required = Clearance.clamp(tag.getInt("Required"));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @Nullable
    public net.minecraft.core.BlockPos governingPanel() {
        return panelPos;
    }
}
