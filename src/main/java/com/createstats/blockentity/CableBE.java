package com.createstats.blockentity;

import com.createstats.block.CableBlock;
import com.createstats.power.CableLinks;
import com.createstats.power.CableTier;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeWireNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** [1] */
public class CableBE extends BlockEntity implements FeGridMember,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    /** [2] */
    public static final int SEGMENT_BUFFER = 25;

    @Nullable
    private FeWireNetwork network;

    private int energy;
    /** Signed FE moved through this segment on the last solve. Drives the readout and the pulse. */
    private int lastFlow;

    // diagnostics, synced
    /** [3] */
    private int load;
    /** True when this segment is being asked for at least its full rating. Drawn hot. */
    private boolean overloaded;
    /** Segments in the whole run, and the weakest rate anywhere on it. Only moves when the run does. */
    private int runSegments = 1;
    private int runWeakestRate;

    private int lastSyncedLoad = -1;
    private int lastSyncedShape = Integer.MIN_VALUE;
    private int ticksSinceSync;
    /** Sixteen steps of load, matching {@link #load}. */
    private static final int LOAD_STEPS = 15;
    /** A still cable still says something once every two seconds, as drift insurance. */
    private static final int SYNC_HEARTBEAT_TICKS = 40;

    /** Exposed so third-party machines can push energy into a run the way they would into a pipe. */
    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(Math.min(maxReceive, transferRate()), SEGMENT_BUFFER - energy);
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
            return SEGMENT_BUFFER;
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

    public CableBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // [4]
        this.ticksSinceSync = Math.floorMod(pos.hashCode(), SYNC_HEARTBEAT_TICKS);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    /** This segment's grade, which decides what it carries and how wide it is drawn. */
    public CableTier tier() {
        return getBlockState().getBlock() instanceof CableBlock cable ? cable.tier() : CableTier.SURFACE;
    }

    /** The circuit this segment is on. Two colours that touch are two circuits, not one. */
    public net.minecraft.world.item.DyeColor colour() {
        return getBlockState().getBlock() instanceof CableBlock
                ? getBlockState().getValue(CableBlock.COLOR)
                : net.minecraft.world.item.DyeColor.ORANGE;
    }

    /** [5] */
    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
            measureRun();
        }
        if (network != null) {
            network.solveOnce(level);
        }
        syncDiagnosticsIfDue();
    }

    /** [6] */
    private void measureRun() {
        int segments = 0;
        int weakest = Integer.MAX_VALUE;
        if (network != null) {
            for (com.createstats.power.FeGridMember member : network.members()) {
                if (member instanceof CableBE cable) {
                    segments++;
                    weakest = Math.min(weakest, cable.transferRate());
                }
            }
        }
        runSegments = Math.max(1, segments);
        runWeakestRate = weakest == Integer.MAX_VALUE ? transferRate() : weakest;
    }

    /** Adopt the network this segment was found to belong to. Called only by {@link FeWireNetwork}. */
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

    // diagnostics

    /** How hard this segment is working, 0 to 1. Read by the renderer on both sides. */
    public float loadFraction() {
        return Math.max(0.0f, Math.min(1.0f, load / (float) LOAD_STEPS));
    }

    /** True when the run wants at least everything this segment can carry. Drawn hot. */
    public boolean isOverloaded() {
        return overloaded;
    }

    /** [7] */
    private void syncDiagnosticsIfDue() {
        final int rate = Math.max(1, transferRate());
        final int magnitude = Math.abs(lastFlow);
        final int step = Math.min(LOAD_STEPS, magnitude * LOAD_STEPS / rate);
        final boolean hot = magnitude >= rate;
        final int shape = runSegments * 31 + runWeakestRate;
        ticksSinceSync++;
        if (step == lastSyncedLoad && hot == overloaded && shape == lastSyncedShape
                && ticksSinceSync < SYNC_HEARTBEAT_TICKS) {
            return;
        }
        load = step;
        overloaded = hot;
        lastSyncedLoad = step;
        lastSyncedShape = shape;
        ticksSinceSync = 0;
        setChanged();
        // Flag 2: clients only. A cable getting busier is not a redstone event.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** [8] */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final int rate = transferRate();
        tooltip.add(Component.literal("    ").append(
                Component.literal(tier().getDisplayName()).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Carrying %,d of %,d FE/t", Math.abs(lastFlow), rate))
                .withStyle(overloaded ? ChatFormatting.RED
                        : lastFlow != 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Run: %d segments, %s", runSegments,
                        runWeakestRate < rate
                                ? String.format(Locale.ROOT, "throttled to %,d FE/t by a thinner grade",
                                        runWeakestRate)
                                : "no thinner grade on it"))
                .withStyle(runWeakestRate < rate ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)));
        return true;
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
        final int clamped = Math.max(0, Math.min(amount, SEGMENT_BUFFER));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
        }
    }

    @Override
    public int bufferCapacity() {
        return SEGMENT_BUFFER;
    }

    /** FE per tick one block of this grade moves. The whole difference between the three grades. */
    @Override
    public int transferRate() {
        return tier().rate(PowerConnectorBE.baseTransferRate());
    }

    /** [9] */
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

    /** Everything moving through this position on the last solve. */
    public int lastFlow() {
        return lastFlow;
    }

    // rendering

    /** [10] */
    public CableLinks.Link link(Direction face, Direction towards) {
        return level == null ? CableLinks.Link.NONE
                : CableLinks.of(CableBlock.linkViewOf(level), worldPosition, face, towards);
    }

    /**
     * True when the cable this run joins in {@code towards} is a <b>thinner</b> grade than this one.
     *
     * <p>This is what closes the see-through step where two grades meet. Where a run carries straight
     * on from a Surface Cable into a Bus Duct, the two bars are different sizes, and the heavier one's
     * end was left open on the assumption the neighbour filled it, which a thinner neighbour cannot:
     * the ring around the thin cord became a hole into the hollow of the thick one. So the heavier side
     * caps its end here, and the thinner run plugs into the centre of that cap, which reads as the
     * reducer it actually is instead of a gap.</p>
     *
     * @param kind the already-resolved link, so this does not recompute it
     */
    public boolean neighborThinner(Direction face, Direction towards, CableLinks.Link kind) {
        if (level == null) {
            return false;
        }
        final BlockPos ahead = switch (kind) {
            case FLAT -> worldPosition.relative(towards);
            case CONVEX -> worldPosition.relative(towards).relative(face);
            default -> null;   // a turn into a wall or a machine has no coplanar grade step to close
        };
        return ahead != null
                && level.getBlockEntity(ahead) instanceof CableBE other
                && other.tier().ordinal() < tier().ordinal();
    }

    /** [11] */
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
        // [12]
        return (float) Math.max(minimum, Math.min(0.4, depth));
    }

    // persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Flow", lastFlow);
        tag.putByte("Load", (byte) load);
        tag.putBoolean("Hot", overloaded);
        tag.putInt("RunN", runSegments);
        tag.putInt("RunMin", runWeakestRate);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // [13]
        if (tag.contains("StrandEnergy")) {
            final int[] stored = tag.getIntArray("StrandEnergy");
            energy = stored.length > 0 ? Math.max(0, Math.min(SEGMENT_BUFFER, stored[0])) : 0;
        } else {
            energy = Math.max(0, Math.min(SEGMENT_BUFFER, tag.getInt("Energy")));
        }
        load = Math.max(0, Math.min(LOAD_STEPS, tag.getByte("Load")));
        overloaded = tag.getBoolean("Hot");
        lastFlow = tag.getInt("Flow");
        runSegments = Math.max(1, tag.getInt("RunN"));
        runWeakestRate = Math.max(0, tag.getInt("RunMin"));
        // What was loaded is what the client already has, so a freshly loaded chunk does not
        // immediately resend everything it just read off disk.
        lastSyncedLoad = load;
        lastSyncedShape = runSegments * 31 + runWeakestRate;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    /** [14] */
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
