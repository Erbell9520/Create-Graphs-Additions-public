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

/** [1] */
public class SwitchBE extends BlockEntity implements FeGridMember {

    /** Buffer per switch. Tiny, like cable: this is accounting, not storage. */
    private static final int BUFFER = 200;

    private int energy;
    private int lastFlow;
    @Nullable
    private FeWireNetwork network;
    /** Lamps this switch gates without being wired to them. */
    private final List<BlockPos> linked = new ArrayList<>();

    /** [2] */
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
            return taken;
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

    /** True when the contacts are closed, so the run conducts through here. */
    public boolean isClosed() {
        return getBlockState().getBlock() instanceof SwitchBlock && SwitchBlock.isOn(getBlockState());
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
    }

    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    /** [3] */
    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    /** [4] */
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

    // wireless links

    public List<BlockPos> linkedLamps() {
        return linked;
    }

    /** Adds or removes a gated lamp. Returns true when it was added. */
    public boolean toggleLink(BlockPos lamp) {
        if (linked.remove(lamp)) {
            pushGateToLinked();
            return false;
        }
        linked.add(lamp.immutable());
        setChanged();
        pushGateToLinked();
        return true;
    }

    /** [5] */
    public void pushGateToLinked() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final boolean shut = !isClosed();
        for (BlockPos lamp : new ArrayList<>(linked)) {
            if (!level.isLoaded(lamp)) {
                continue;
            }
            if (level.getBlockEntity(lamp) instanceof LampBE be) {
                be.setGated(shut);
            } else {
                linked.remove(lamp);   // no longer a lamp: stop carrying a dead link
                setChanged();
            }
        }
    }

    // feGridMember

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
        return PowerConnectorBE.baseTransferRate();
    }

    /** A switch is pure conduit: it feeds nothing of its own. */
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

    /** Signed FE per tick passing through, so the switch can show whether current is actually flowing. */
    public int lastFlow() {
        return lastFlow;
    }

    // persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
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
        linked.clear();
        for (long packed : tag.getLongArray("Linked")) {
            linked.add(BlockPos.of(packed));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
