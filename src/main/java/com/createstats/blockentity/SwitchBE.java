package com.createstats.blockentity;

import com.createstats.block.SwitchBlock;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeWireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// open switch = not a member, the flood stops, two networks
public class SwitchBE extends BlockEntity implements FeGridMember,
        com.createstats.power.FixtureLinker,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
    private static final int BUFFER = 200;

    private static final int SYNC_HEARTBEAT_TICKS = 600;

    private int energy;
    private int lastFlow;

    private boolean carrying;
    private int ticksSinceSync;
    @Nullable
    private FeWireNetwork network;

    private final List<BlockPos> linked = new ArrayList<>();

    private int dimLevel = com.createstats.block.RotaryDimmerBlock.LEVELS - 1;

    private int timePins = com.createstats.timeswitch.TimeSwitchProgram.NONE;

    private com.createstats.timeswitch.TimeSwitchProgram.Mode timeMode =
            com.createstats.timeswitch.TimeSwitchProgram.Mode.AUTO;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!isClosed()) {
                return 0;
            }
            final int room = Math.min(Math.min(maxReceive, transferRate()), BUFFER - energy);
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
            if (!isClosed()) {
                return 0;
            }
            final int taken = Math.min(Math.min(maxExtract, transferRate()), energy);
            if (taken <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= taken;
                setChanged();
            }
            return com.createstats.power.FeWireNetwork.noteSeamTransfer(network, level, simulate,
                    taken, nodeKey());
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
            return isClosed();
        }

        @Override
        public boolean canReceive() {
            return isClosed();
        }
    };

    public SwitchBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public boolean isClosed() {
        return getBlockState().getBlock() instanceof SwitchBlock && SwitchBlock.isOn(getBlockState());
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (getBlockState().getBlock() instanceof SwitchBlock plate) {
            plate.tickSwitch(level, worldPosition, getBlockState(), this);
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        syncPilotIfDue();
    }

    private void syncPilotIfDue() {
        // not lastFlow, it's always 0 on pure conduit. the pilot lamp could never light. yine mi

        final boolean live = isClosed() && level != null && network != null && carriedFePerTick() > 0;
        ticksSinceSync++;
        if (live == carrying && ticksSinceSync < SYNC_HEARTBEAT_TICKS) {
            return;
        }
        carrying = live;
        ticksSinceSync = 0;
        setChanged();

        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    private int carriedFePerTick() {
        if (level == null || network == null) {
            return 0;
        }
        final long now = level.getGameTime();
        final int mine = network.flowThrough(nodeKey(), now);
        return mine == com.createstats.power.FeFlowGraph.UNKNOWN
                ? network.throughputFePerTick(now) : mine;
    }

    public boolean isCarrying() {
        return carrying;
    }

    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

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
    }

    public void invalidateBranch() {
        invalidateNetwork();
        if (level == null) {
            return;
        }
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            final BlockPos neighbour = worldPosition.relative(dir);
            if (level.isLoaded(neighbour)
                    && level.getBlockEntity(neighbour) instanceof FeGridMember member) {
                member.invalidateNetwork();
            }
        }
    }

    @Override
    public BlockPos linkerPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> linkedFixtures() {
        return linked;
    }

    @Override
    public boolean toggleLink(BlockPos fixture) {
        if (linked.remove(fixture)) {
            setChanged();
            unlinkFixture(fixture);
            syncLinks();
            return false;
        }
        linked.add(fixture.immutable());
        setChanged();
        if (level != null && level.isLoaded(fixture)
                && level.getBlockEntity(fixture) instanceof WallOutletBE outlet) {
            outlet.setLinked(true);
        }
        pushGateToLinked();

        pushLevelToLinked();
        syncLinks();
        return true;
    }

    private void syncLinks() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public int dimLevel() {
        return dimLevel;
    }

    public boolean stepDimLevel(int delta) {
        final int next = Math.max(0, Math.min(com.createstats.block.RotaryDimmerBlock.LEVELS - 1,
                dimLevel + delta));
        if (next == dimLevel) {
            return false;
        }
        dimLevel = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        pushLevelToLinked();
        return true;
    }

    public void pushLevelToLinked() {
        if (level == null || level.isClientSide()
                || !(getBlockState().getBlock() instanceof com.createstats.block.RotaryDimmerBlock)) {
            return;
        }
        for (BlockPos fixture : new ArrayList<>(linked)) {
            if (level.isLoaded(fixture) && level.getBlockEntity(fixture) instanceof LampBE lamp) {
                lamp.setMode(dimLevel);
            }
        }
    }

    public int timePins() {
        return timePins;
    }

    public com.createstats.timeswitch.TimeSwitchProgram.Mode timeMode() {
        return timeMode;
    }

    public boolean setTimePins(int pins) {
        final int next = pins & com.createstats.timeswitch.TimeSwitchProgram.ALL;
        if (next == timePins) {
            return false;
        }
        timePins = next;
        timeChanged();
        return true;
    }

    public boolean setTimeMode(com.createstats.timeswitch.TimeSwitchProgram.Mode mode) {
        if (mode == timeMode) {
            return false;
        }
        timeMode = mode;
        timeChanged();
        return true;
    }

    private void timeChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public String settingsKey() {
        final net.minecraft.world.level.block.Block block = getBlockState().getBlock();
        if (block instanceof com.createstats.block.TimeSwitchBlock) {
            return "create_stats:time_switch";
        }
        if (block instanceof com.createstats.block.RotaryDimmerBlock) {
            return "create_stats:rotary_dimmer";
        }
        return "create_stats:switch";
    }

    @Override
    public boolean hasCopyableSettings() {
        final net.minecraft.world.level.block.Block block = getBlockState().getBlock();
        return block instanceof com.createstats.block.TimeSwitchBlock
                || block instanceof com.createstats.block.RotaryDimmerBlock;
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        final net.minecraft.world.level.block.Block block = getBlockState().getBlock();
        if (block instanceof com.createstats.block.TimeSwitchBlock) {
            tag.putInt("TimePins", timePins);
            tag.putByte("TimeMode", (byte) timeMode.id());
        } else if (block instanceof com.createstats.block.RotaryDimmerBlock) {
            tag.putInt("Level", dimLevel);
        }
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @Nullable net.minecraft.world.entity.player.Player player, boolean simulate) {
        final net.minecraft.world.level.block.Block block = getBlockState().getBlock();
        if (block instanceof com.createstats.block.TimeSwitchBlock plate) {
            if (!tag.contains("TimePins") || !tag.contains("TimeMode")) {
                return false;
            }
            if (simulate) {
                return true;
            }
            setTimePins(tag.getInt("TimePins"));
            setTimeMode(com.createstats.timeswitch.TimeSwitchProgram.Mode.byId(tag.getByte("TimeMode")));
            if (level != null && !level.isClientSide()) {
                plate.tickSwitch(level, worldPosition, getBlockState(), this);
            }
            return true;
        }
        if (block instanceof com.createstats.block.RotaryDimmerBlock) {
            if (!tag.contains("Level")) {
                return false;
            }
            if (simulate) {
                return true;
            }
            if (!setDimLevel(tag.getInt("Level"))) {
                pushLevelToLinked();
            }
            return true;
        }
        return false;
    }

    public boolean setDimLevel(int wanted) {
        final int next = Math.max(0, Math.min(com.createstats.block.RotaryDimmerBlock.LEVELS - 1, wanted));
        if (next == dimLevel) {
            return false;
        }
        dimLevel = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        pushLevelToLinked();
        return true;
    }

    private void unlinkFixture(BlockPos fixture) {
        if (level == null || !level.isLoaded(fixture)) {
            return;
        }
        if (level.getBlockEntity(fixture) instanceof com.createstats.power.Gateable be) {
            be.setGated(false);
        }
        if (level.getBlockEntity(fixture) instanceof WallOutletBE outlet) {
            outlet.setLinked(false);
        }
    }

    public void pushGateToLinked() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final boolean shut = !isClosed();
        boolean pruned = false;
        for (BlockPos fixture : new ArrayList<>(linked)) {
            if (!level.isLoaded(fixture)) {
                continue;
            }
            if (level.getBlockEntity(fixture) instanceof com.createstats.power.Gateable be) {
                be.setGated(shut);
            } else {
                linked.remove(fixture);
                setChanged();
                pruned = true;
            }
        }
        if (pruned) {
            syncLinks();
        }
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return energy;
    }

    @Override
    public void setStoredEnergy(int amount) {
        final int clamped = Math.max(0, Math.min(amount, BUFFER));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
        }
    }

    @Override
    public int bufferCapacity() {
        return BUFFER;
    }

    @Override
    public int transferRate() {
        final Level lvl = level;
        if (lvl == null) {
            return PowerConnectorBE.baseTransferRate();
        }
        final long now = lvl.getGameTime();
        if (now != ratedTick) {
            ratedTick = now;
            int weakest = 0;
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                final BlockEntity be =
                        lvl.getBlockEntity(worldPosition.relative(direction));
                final int rate = be instanceof com.createstats.blockentity.CableBE cable
                        ? cable.transferRate()
                        : be instanceof com.createstats.blockentity.IndoorCableBE conduit
                                ? conduit.transferRate() : 0;
                if (rate > 0) {
                    weakest = weakest == 0 ? rate : Math.min(weakest, rate);
                }
            }
            rated = weakest > 0 ? weakest : PowerConnectorBE.baseTransferRate();
        }
        return rated;
    }

    private int rated = PowerConnectorBE.baseTransferRate();
    private long ratedTick = Long.MIN_VALUE;

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        if (getBlockState().getBlock() instanceof SwitchBlock plate
                && plate.addGoggleLines(this, tooltip, isPlayerSneaking)) {
            return true;
        }
        if (getBlockState().getBlock() instanceof com.createstats.block.RotaryDimmerBlock) {
            tooltip.add(com.createstats.power.FeGoggle.title("Rotary Dimmer"));
            tooltip.add(isClosed()
                    ? com.createstats.power.FeGoggle.ok("On, " + (dimLevel + 1) * 10 + "%")
                    : com.createstats.power.FeGoggle.warn("Pushed off, circuit broken"));
            tooltip.add(com.createstats.power.FeGoggle.dim(linked.isEmpty()
                    ? "No lamps linked: bind them with a Switch Link Card"
                    : linked.size() + (linked.size() == 1 ? " fixture" : " fixtures") + " linked"));
            if (isClosed()) {
                tooltip.add(com.createstats.power.FeGoggle.capacity(transferRate()));
            }
            return true;
        }
        tooltip.add(com.createstats.power.FeGoggle.title("Switch"));
        if (isClosed()) {
            tooltip.add(com.createstats.power.FeGoggle.ok("Closed, conducting"));
            tooltip.add(com.createstats.power.FeGoggle.capacity(transferRate()));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.warn("Open, circuit broken"));
        }
        return true;
    }

    @Override
    @Nullable
    public IEnergyStorage attachedStorage() {
        return null;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
        this.lastFlow = flow;
    }

    public int lastFlow() {
        return lastFlow;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putBoolean("Live", carrying);
        tag.putInt("Level", dimLevel);

        if (timePins != com.createstats.timeswitch.TimeSwitchProgram.NONE) {
            tag.putInt("TimePins", timePins);
        }
        if (timeMode != com.createstats.timeswitch.TimeSwitchProgram.Mode.AUTO) {
            tag.putByte("TimeMode", (byte) timeMode.id());
        }
        final long[] packed = new long[linked.size()];
        for (int i = 0; i < linked.size(); i++) {
            packed[i] = linked.get(i).asLong();
        }
        tag.putLongArray("Linked", packed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        carrying = tag.getBoolean("Live");

        dimLevel = tag.contains("Level")
                ? Math.max(0, Math.min(com.createstats.block.RotaryDimmerBlock.LEVELS - 1, tag.getInt("Level")))
                : com.createstats.block.RotaryDimmerBlock.LEVELS - 1;

        timePins = tag.getInt("TimePins") & com.createstats.timeswitch.TimeSwitchProgram.ALL;
        timeMode = com.createstats.timeswitch.TimeSwitchProgram.Mode.byId(
                tag.contains("TimeMode") ? tag.getByte("TimeMode") : 0);

        ticksSinceSync = 0;
        linked.clear();
        for (long packed : tag.getLongArray("Linked")) {
            linked.add(BlockPos.of(packed));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();

        if (level != null && !level.isClientSide()) {
            for (BlockPos fixture : new ArrayList<>(linked)) {
                unlinkFixture(fixture);
            }
        }
    }
}
