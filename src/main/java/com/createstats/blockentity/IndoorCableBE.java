package com.createstats.blockentity;

import com.createstats.block.IndoorCableBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.IndoorCable;
import com.createstats.power.IndoorCableLinks;
import com.createstats.power.IndoorCableRunBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IndoorCableBE extends BlockEntity implements FeGridMember, BoreLinks.Host,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Nullable
    private FeWireNetwork network;

    @Nullable
    private IndoorCableRunBudget runBudget;

    private int energy;
    private int lastFlow;

    private final List<BlockPos> bores = new ArrayList<>(0);

    private int load;
    private boolean overloaded;

    private int carried;
    private int runSegments = 1;

    private int lastSyncedLoad = -1;
    private int lastSyncedShape = Integer.MIN_VALUE;
    private int ticksSinceSync;

    private final com.createstats.power.WireJoints joints = new com.createstats.power.WireJoints();

    private static final int LOAD_STEPS = 15;

    private static final int SYNC_HEARTBEAT_TICKS = 40;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        private int limit(int wanted, boolean arriving) {
            final int rated = Math.min(wanted, transferRate());
            if (runBudget == null || level == null) {
                return rated;
            }
            final long now = level.getGameTime();
            return Math.min(rated, arriving
                    ? runBudget.intakeRemaining(now) : runBudget.outflowRemaining(now));
        }

        private void book(int moved, boolean simulate, boolean arriving) {
            if (simulate || moved <= 0 || runBudget == null || level == null) {
                return;
            }
            final long now = level.getGameTime();
            if (arriving) {
                runBudget.spendIntake(now, moved);
            } else {
                runBudget.spendOutflow(now, moved);
            }
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int limit = limit(maxReceive, true);
            final int room = Math.max(0, Math.min(limit, IndoorCable.SEGMENT_BUFFER - energy));
            if (room > 0 && !simulate) {
                energy += room;
                setChanged();
            }
            if (room >= limit || network == null) {
                book(room, simulate, true);
                return room;
            }
            final int moved = room + network.takeFromExternal(nodeKey(), limit - room, simulate, level);
            book(moved, simulate, true);
            return moved;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int limit = limit(maxExtract, false);
            final int taken = Math.max(0, Math.min(limit, energy));
            if (taken > 0 && !simulate) {
                energy -= taken;
                setChanged();
            }
            final int moved = taken >= limit || network == null
                    ? taken
                    : taken + network.giveToExternal(nodeKey(), limit - taken, simulate, level);
            book(moved, simulate, false);
            return FeWireNetwork.noteSeamTransfer(network, level, simulate, moved, nodeKey());
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return IndoorCable.SEGMENT_BUFFER;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public IndoorCableBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        this.ticksSinceSync = Math.floorMod(pos.hashCode(), SYNC_HEARTBEAT_TICKS);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public com.createstats.power.IndoorCableAnchor anchor() {
        return IndoorCableBlock.anchorOf(getBlockState());
    }

    public com.createstats.power.IndoorCableAnchor anchorTowards(Direction towards) {
        if (level == null) {
            return anchor();
        }
        final net.minecraft.world.level.block.state.BlockState ahead =
                level.getBlockState(worldPosition.relative(towards));
        return ahead.getBlock() instanceof IndoorCableBlock
                ? IndoorCableBlock.anchorOf(ahead) : anchor();
    }

    public DyeColor colour() {
        return getBlockState().getBlock() instanceof IndoorCableBlock
                ? getBlockState().getValue(IndoorCableBlock.COLOR)
                : DyeColor.LIGHT_GRAY;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            if (BoreLinks.prune(level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
            measureRun();
        }
        if (network != null) {
            network.solveOnce(level);
        }
        syncDiagnosticsIfDue();
    }

    private void measureRun() {
        int segments = 0;
        if (network != null) {
            for (FeGridMember member : network.members()) {
                if (member instanceof IndoorCableBE other && other.runBudget == runBudget) {
                    segments++;
                }
            }
        }
        runSegments = Math.max(1, segments);
    }

    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    public void adoptRunBudget(IndoorCableRunBudget budget) {
        this.runBudget = budget;
    }

    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Nullable
    public IndoorCableRunBudget runBudget() {
        return runBudget;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
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

    public IndoorCableLinks.Link link(Direction towards) {
        return level == null ? IndoorCableLinks.Link.NONE
                : IndoorCableLinks.of(IndoorCableBlock.linkViewOf(level), worldPosition, towards);
    }

    @Nullable
    public BlockPos boreTowards(Direction towards) {
        for (BlockPos far : bores) {
            if (BoreLinks.axisBetween(worldPosition, far) == towards) {
                return far;
            }
        }
        return null;
    }

    public float reachInto(Direction towards) {
        final float minimum = 1.0f / 16.0f;
        if (level == null) {
            return minimum;
        }
        final BlockPos ahead = worldPosition.relative(towards);
        final VoxelShape shape = level.getBlockState(ahead).getShape(level, ahead);
        if (shape.isEmpty()) {
            return minimum;
        }
        final Direction.Axis axis = towards.getAxis();
        final double depth = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.min(axis)
                : 1.0 - shape.max(axis);

        return (float) Math.max(minimum, Math.min(0.4, depth));
    }

    public float loadFraction() {
        return Math.max(0.0f, Math.min(1.0f, load / (float) LOAD_STEPS));
    }

    public com.createstats.power.WireJoints joints() {
        return joints;
    }

    public boolean isOverloaded() {
        return overloaded;
    }

    private void syncDiagnosticsIfDue() {
        final int rate = Math.max(1, transferRate());
        final int magnitude = carrying();
        final int step = com.createstats.power.FeRates.loadStep(magnitude, rate, LOAD_STEPS);
        final boolean hot = magnitude >= rate;

        final int shape = (runSegments * 31 + bores.size()) * 7 + anchor().ordinal();

        final boolean live = magnitude > 0;
        ticksSinceSync++;
        if (step == lastSyncedLoad && hot == overloaded && shape == lastSyncedShape
                && live == (carried > 0) && ticksSinceSync < SYNC_HEARTBEAT_TICKS) {
            return;
        }
        load = step;
        carried = magnitude;
        overloaded = hot;
        lastSyncedLoad = step;
        lastSyncedShape = shape;
        ticksSinceSync = 0;
        setChanged();

        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public int carrying() {
        if (network == null || level == null) {
            return 0;
        }
        final long now = level.getGameTime();
        final int mine = network.flowThrough(nodeKey(), now);
        return mine == com.createstats.power.FeFlowGraph.UNKNOWN
                ? network.throughputFePerTick(now) : mine;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final int rate = transferRate();
        tooltip.add(com.createstats.power.FeGoggle.title("Indoor Cable"));
        tooltip.add(com.createstats.power.FeGoggle.carrying(carried, rate));
        tooltip.add(com.createstats.power.FeGoggle.capacity(rate));

        if (!bores.isEmpty()) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "Run of %d segments, %d bore%s from here", runSegments, bores.size(),
                    bores.size() == 1 ? "" : "s")));
        }
        return true;
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
        final int clamped = Math.max(0, Math.min(amount, IndoorCable.SEGMENT_BUFFER));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
        }
    }

    @Override
    public int bufferCapacity() {
        return IndoorCable.SEGMENT_BUFFER;
    }

    @Override
    public int transferRate() {
        return IndoorCable.rate(PowerConnectorBE.baseTransferRate());
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
        tag.putByte("Load", (byte) load);
        tag.putInt("Carried", carried);
        tag.putBoolean("Hot", overloaded);
        tag.putInt("RunN", runSegments);
        BoreLinks.save(tag, bores);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(IndoorCable.SEGMENT_BUFFER, tag.getInt("Energy")));
        load = Math.max(0, Math.min(LOAD_STEPS, tag.getByte("Load")));
        carried = Math.max(0, tag.getInt("Carried"));
        overloaded = tag.getBoolean("Hot");
        runSegments = Math.max(1, tag.getInt("RunN"));
        bores.clear();
        bores.addAll(BoreLinks.load(tag));

        lastSyncedLoad = load;
        lastSyncedShape = (runSegments * 31 + bores.size()) * 7 + anchor().ordinal();
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
    }
}
