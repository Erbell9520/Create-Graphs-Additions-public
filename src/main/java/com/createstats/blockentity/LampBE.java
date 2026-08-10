package com.createstats.blockentity;

import com.createstats.block.LampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** [1] */
public class LampBE extends BlockEntity
        implements com.createstats.power.FeGridMember, com.createstats.power.FeManagedLoad {

    /** Five seconds of running, so a momentary supply dip never shows as a flicker. */
    private static final int BUFFER = 100;
    /** [2] */
    private static final int DRAIN = 1;

    private int energy;
    /** Set by a wirelessly linked switch. Persisted, or a reload would silently re-light the room. */
    private boolean gated;
    /** What this fitting costs to hold lit, so the Power Distribution Unit can budget for it. */
    private int ratedDraw;
    /** What it actually spent last tick, so the panel reports the truth rather than the rating. */
    private int measuredDraw;
    /** Tier and lock as set from the panel. See {@link com.createstats.power.FeManagedLoad}. */
    private final com.createstats.power.FeManagedState managed = new com.createstats.power.FeManagedState();

    /** Receive only: a lamp is a load, never a source for anything else. */
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

    public LampBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    /** Told by a linked switch that it may not light. */
    public void setGated(boolean gated) {
        if (this.gated == gated) {
            return;
        }
        this.gated = gated;
        setChanged();
        refresh();
    }

    public boolean isGated() {
        return gated;
    }

    /** What this fitting costs to run, for the Power Distribution Unit's lighting list. */
    public int drawFePerTick() {
        return DRAIN;
    }

    /** Zero while gated, cut by redstone or switched off from the panel: it is not load. */
    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public com.createstats.power.FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(com.createstats.power.FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public com.createstats.power.FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(com.createstats.power.FeLock lock) {
        managed.setLock(lock);
        setChanged();
        refresh();   // a force-off has to go dark now, not in five seconds when the buffer runs out
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        refresh();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    /** Whether it is actually alight, which is what the panel reports rather than what it wants. */
    public boolean isLit() {
        return getBlockState().hasProperty(LampBlock.LIT) && getBlockState().getValue(LampBlock.LIT);
    }

    // grid membership

    /** [3] */
    @org.jetbrains.annotations.Nullable
    private com.createstats.power.FeWireNetwork network;

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
        // See above: the buffer belongs to the lamp, not to its node.
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    /** Generous next to a one FE drain, so a whole row refills in a tick after a switch closes. */
    @Override
    public int transferRate() {
        return 64;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
        // A lamp shows its state by being lit; there is no gauge to feed.
    }

    public void adoptNetwork(com.createstats.power.FeWireNetwork network) {
        this.network = network;
    }

    /** Forces the grid to be rediscovered, for when a lamp is added to or taken out of a row. */
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            com.createstats.power.FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        // [4]
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        // [5]
        final boolean wantsToRun = !gated && !managed.isSuppressed()
                && !level.hasNeighborSignal(worldPosition);
        // [6]
        ratedDraw = wantsToRun ? DRAIN : 0;
        if (wantsToRun && energy > 0) {
            energy = Math.max(0, energy - DRAIN);
            measuredDraw = DRAIN;
            setChanged();
        } else {
            measuredDraw = 0;
        }
        refresh();
    }

    /** Brings the blockstate in line with whether the lamp is actually alight. */
    private void refresh() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof LampBlock)) {
            return;
        }
        final boolean lit = energy > 0 && !gated && !managed.isSuppressed()
                && !level.hasNeighborSignal(worldPosition);
        if (lit != state.getValue(LampBlock.LIT)) {
            level.setBlock(worldPosition, state.setValue(LampBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putBoolean("Gated", gated);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        gated = tag.getBoolean("Gated");
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
