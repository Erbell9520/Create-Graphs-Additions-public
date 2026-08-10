package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.FluidPumpBlock;
import com.createstats.block.FluidPumpValueBoxTransform;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FeGridMember;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.PumpTier;
import com.createstats.power.WireTier;
import com.createstats.power.FeWireTerminal;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** [1] */
public class FluidPumpBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    /** A running reserve, so a momentary grid dip does not stall the flow. */
    private static final int BUFFER = 64000;

    /** How fast the impeller reads as spinning, in degrees per tick per mB/t of throughput. */
    private static final float SPIN_DEG_PER_MB = 0.5f;

    private int energy;

    /** The pressure being asserted onto the pipe this tick: the served flow doubled, capped by grade. */
    private int servedPressure;
    /** The last pressure asserted, so the network is only kicked when it actually changes. */
    private int lastPressure = Integer.MIN_VALUE;
    /** Ticks since the run was last flooded, so a pipe edited downstream is picked back up. */
    private int ticksSinceFlood;
    /** How often, in ticks, a running pump re-floods its run as a keep-alive. Half a second. */
    private static final int FLOOD_INTERVAL = 10;
    /** What the dial costs to hold at full flow, whether or not the buffer could pay this tick. */
    private int ratedDraw;
    /** What the pump actually spent last tick, which is what the panel shows. */
    private int measuredDraw;
    /** The flow the pump actually managed this tick, in mB/t. Synced for the impeller. */
    private int effectiveFlow;

    private final FeManagedState managed = new FeManagedState();
    /** Wires strung to this pump's built-in connector, and each grade, keyed by the far position. */
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;
    @Nullable
    private ScrollValueBehaviour powerDial;

    // client animation
    private int syncedFlow;
    private float clientAngle;
    private float prevClientAngle;

    /** Receive only: a pump is a load and never a source for anything else. */
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

    public FluidPumpBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // grade

    private PumpTier tier() {
        return getBlockState().getBlock() instanceof FluidPumpBlock pump ? pump.tier() : PumpTier.MK_I;
    }

    private int maxFlow() {
        return tier().maxFlowMbPerTick();
    }

    private int maxPressure() {
        return tier().maxPressure();
    }

    private Direction facing() {
        return getBlockState().hasProperty(FluidPumpBlock.FACING)
                ? getBlockState().getValue(FluidPumpBlock.FACING) : Direction.NORTH;
    }

    /** FE burned per mB moved. Config, with a fallback for the client before a world is joined. */
    private static int feCostPerMb() {
        try {
            return Math.max(0, ModConfiguration.COMMON.pumpFeCostPerMb.get());
        } catch (Exception ignored) {
            return 8;
        }
    }

    // behaviours: the pipe, and the power dial

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new PumpPipeBehaviour(this));

        powerDial = new ScrollValueBehaviour(
                Component.translatable("block.create_stats.fluid_pump.power"), this,
                new FluidPumpValueBoxTransform());
        powerDial.between(0, maxFlow());
        powerDial.withFormatter(v -> v + " mB/t");
        powerDial.withCallback(v -> onDialChanged());
        behaviours.add(powerDial);
    }

    private int targetFlow() {
        return powerDial == null ? 0 : Mth.clamp(powerDial.getValue(), 0, maxFlow());
    }

    private void onDialChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            // Recompute at once so the box slide is felt immediately rather than on the next tick.
            recompute();
        }
    }

    // tick

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            advanceClientSpin();
            return;
        }
        // Wire membership, exactly like the Induction Range: build the network from this node when it
        // is missing or stale, then let the shared solve fill the buffer behind energyHandle().
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        // [2]
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        recompute();
        maybeSync();
    }

    /**
     * Decide the flow the pump holds this tick and spend the FE for it, then assert the matching
     * pressure onto the pipe.
     */
    private void recompute() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // [3]
        final boolean cut = level.hasNeighborSignal(worldPosition) || managed.isSuppressed();
        final int want = cut ? 0 : targetFlow();
        final int cost = feCostPerMb();
        ratedDraw = want * cost;

        // Step the flow down until the buffer can pay for a full tick of it, so an underfed pump eases
        // off rather than stuttering between full flow and nothing.
        int flow = want;
        if (cost > 0) {
            while (flow > 0 && energy < flow * cost) {
                flow--;
            }
        }
        measuredDraw = flow * cost;
        if (measuredDraw > 0) {
            energy -= measuredDraw;
            setChanged();
        }
        effectiveFlow = flow;

        // Create moves max(1, pressure / 2), so to move `flow` mB/t the pipe wants pressure flow * 2.
        servedPressure = Math.min(maxPressure(), flow * 2);
        // [4]
        assertOwnPressure();
        // [5]
        final boolean changed = servedPressure != lastPressure;
        lastPressure = servedPressure;
        if (changed || (servedPressure > 0 && ++ticksSinceFlood >= FLOOD_INTERVAL)) {
            floodRun();
            ticksSinceFlood = 0;
        }
    }

    /** [6] */
    private void assertOwnPressure() {
        final FluidTransportBehaviour pipe = getBehaviour(FluidTransportBehaviour.TYPE);
        if (pipe == null) {
            return;
        }
        final Direction front = facing();
        for (final Direction side : new Direction[] { front, front.getOpposite() }) {
            final PipeConnection connection = pipe.getConnection(side);
            if (connection == null) {
                continue;
            }
            final boolean pull = side == front.getOpposite();
            final Couple<Float> pressure = connection.getPressure();
            pressure.set(pull, (float) servedPressure);
            pressure.set(!pull, 0.0f);
        }
    }

    /** [7] */
    private void floodRun() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final Direction front = facing();
        final BlockPos frontPos = worldPosition.relative(front);
        final BlockPos backPos = worldPosition.relative(front.getOpposite());
        // Wipe both runs first, so the flood sets pressure rather than piling onto the previous one.
        FluidPropagator.propagateChangedPipe(level, frontPos, level.getBlockState(frontPos));
        FluidPropagator.propagateChangedPipe(level, backPos, level.getBlockState(backPos));
        final FluidTransportBehaviour pipe = getBehaviour(FluidTransportBehaviour.TYPE);
        if (pipe != null) {
            pipe.wipePressure();
        }
        assertOwnPressure();
        if (servedPressure > 0) {
            distributePressureTo(front, servedPressure);
            distributePressureTo(front.getOpposite(), servedPressure);
        }
    }

    /** [8] */
    private void distributePressureTo(Direction side, float pressure) {
        if (level == null || pressure <= 0) {
            return;
        }
        final Direction front = facing();
        final boolean pull = side == front.getOpposite();
        final BlockFace start = new BlockFace(worldPosition, side);
        final Set<BlockFace> targets = new HashSet<>();
        final Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

        if (!pull) {
            FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());
        }
        if (hasReachedValidEndpoint(level, start, pull)) {
            return;   // a tank or open end sits right against this side; the own-connection pressure moves it
        }

        pipeGraph.computeIfAbsent(worldPosition, k -> Pair.of(0, new IdentityHashMap<>()))
                .getSecond().put(side, pull);
        pipeGraph.computeIfAbsent(start.getConnectedPos(), k -> Pair.of(1, new IdentityHashMap<>()))
                .getSecond().put(side.getOpposite(), !pull);

        final List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
        final Set<BlockPos> visited = new HashSet<>();
        final int maxDistance = FluidPropagator.getPumpRange();
        frontier.add(Pair.of(1, start.getConnectedPos()));

        while (!frontier.isEmpty()) {
            final Pair<Integer, BlockPos> entry = frontier.remove(0);
            final int distance = entry.getFirst();
            final BlockPos currentPos = entry.getSecond();
            if (!level.isLoaded(currentPos) || visited.contains(currentPos)) {
                continue;
            }
            visited.add(currentPos);
            final BlockState currentState = level.getBlockState(currentPos);
            final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
            if (pipe == null) {
                continue;
            }
            for (final Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                final BlockFace blockFace = new BlockFace(currentPos, face);
                final BlockPos connectedPos = blockFace.getConnectedPos();
                if (!level.isLoaded(connectedPos) || blockFace.isEquivalent(start)) {
                    continue;
                }
                if (hasReachedValidEndpoint(level, blockFace, pull)) {
                    pipeGraph.computeIfAbsent(currentPos, k -> Pair.of(distance, new IdentityHashMap<>()))
                            .getSecond().put(face, pull);
                    targets.add(blockFace);
                    continue;
                }
                final FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
                if (pipeBehaviour == null || pipeBehaviour instanceof PumpPipeBehaviour
                        || visited.contains(connectedPos) || distance + 1 >= maxDistance) {
                    continue;
                }
                // Never walk through a Create pump; treat it as a wall so the two do not fight.
                if (level.getBlockEntity(connectedPos)
                        instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity) {
                    continue;
                }
                pipeGraph.computeIfAbsent(currentPos, k -> Pair.of(distance, new IdentityHashMap<>()))
                        .getSecond().put(face, pull);
                pipeGraph.computeIfAbsent(connectedPos, k -> Pair.of(distance + 1, new IdentityHashMap<>()))
                        .getSecond().put(face.getOpposite(), !pull);
                frontier.add(Pair.of(distance + 1, connectedPos));
            }
        }

        final Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
        searchForEndpointRecursively(pipeGraph, targets, validFaces,
                new BlockFace(start.getPos(), start.getOppositeFace()), pull);

        for (final Set<BlockFace> set : validFaces.values()) {
            final int parallelBranches = Math.max(1, set.size() - 1);
            for (final BlockFace face : set) {
                final BlockPos pipePos = face.getPos();
                final Direction pipeSide = face.getFace();
                if (pipePos.equals(worldPosition)) {
                    continue;
                }
                final Pair<Integer, Map<Direction, Boolean>> node = pipeGraph.get(pipePos);
                if (node == null) {
                    continue;
                }
                final Boolean inbound = node.getSecond().get(pipeSide);
                if (inbound == null) {
                    continue;
                }
                final FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
                if (pipeBehaviour != null) {
                    pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
                }
            }
        }
    }

    /** The endpoint-resolving DFS, ported from {@code PumpBlockEntity.searchForEndpointRecursively}. */
    private boolean searchForEndpointRecursively(
            Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph, Set<BlockFace> targets,
            Map<Integer, Set<BlockFace>> validFaces, BlockFace currentFace, boolean pull) {
        final BlockPos currentPos = currentFace.getPos();
        final Pair<Integer, Map<Direction, Boolean>> node = pipeGraph.get(currentPos);
        if (node == null) {
            return false;
        }
        final int distance = node.getFirst();
        final Map<Direction, Boolean> map = node.getSecond();
        boolean atLeastOneBranchSuccessful = false;
        for (final Direction nextFacing : Direction.values()) {
            if (nextFacing == currentFace.getFace() || !map.containsKey(nextFacing)) {
                continue;
            }
            final BlockFace localTarget = new BlockFace(currentPos, nextFacing);
            if (targets.contains(localTarget)) {
                validFaces.computeIfAbsent(distance, k -> new HashSet<>()).add(localTarget);
                atLeastOneBranchSuccessful = true;
                continue;
            }
            if (map.get(nextFacing) != pull) {
                continue;
            }
            if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
                    new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull)) {
                continue;
            }
            validFaces.computeIfAbsent(distance, k -> new HashSet<>()).add(localTarget);
            atLeastOneBranchSuccessful = true;
        }
        if (atLeastOneBranchSuccessful) {
            validFaces.computeIfAbsent(distance, k -> new HashSet<>()).add(currentFace);
        }
        return atLeastOneBranchSuccessful;
    }

    /** Whether the block across this face is a place fluid can leave the pipe, ported from Create. */
    private boolean hasReachedValidEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
        final BlockPos connectedPos = blockFace.getConnectedPos();
        final BlockState connectedState = world.getBlockState(connectedPos);
        final BlockEntity blockEntity = world.getBlockEntity(connectedPos);
        final Direction face = blockFace.getFace();

        // Another pipe on this axis is more run to walk, not an endpoint.
        final FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace())) {
            return false;
        }
        // Anything exposing a fluid handler toward us is an endpoint: a tank, a machine, a foreign pipe.
        if (blockEntity != null && blockEntity.getLevel() != null) {
            final IFluidHandler capability = blockEntity.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(), face.getOpposite());
            if (capability != null) {
                return true;
            }
        }
        // An open end, air or a waterloggable block, is an endpoint too.
        return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
    }

    // sync

    private int lastSyncedFlow = -1;

    private void maybeSync() {
        // The impeller only needs a fresh figure when the flow crosses a bucket or stops and starts,
        // so a steadily running pump does not resync every tick.
        final boolean crossedZero = (effectiveFlow > 0) != (lastSyncedFlow > 0);
        final boolean movedEnough = Math.abs(effectiveFlow - lastSyncedFlow) > Math.max(2, maxFlow() / 16);
        if (crossedZero || movedEnough) {
            lastSyncedFlow = effectiveFlow;
            sendData();
        }
    }

    // the pipe segment

    private class PumpPipeBehaviour extends FluidTransportBehaviour {
        PumpPipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            final Direction front = state.hasProperty(FluidPumpBlock.FACING)
                    ? state.getValue(FluidPumpBlock.FACING) : Direction.NORTH;
            return direction.getAxis() == front.getAxis();
        }
    }

    // grid membership

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

    /** [9] */
    @Override
    public int transferRate() {
        return tier().maxFlowMbPerTick() * feCostPerMb() * 2;
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
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    // built-in connector: a wire terminal, exactly like a Power Connector

    @Override
    public BlockPos terminalPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return links;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        return linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        links.add(at);
        linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        syncLinks();
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        linkTiers.remove(far.asLong());
        if (links.remove(far)) {
            syncLinks();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final Map<WireTier, Integer> refund = new java.util.EnumMap<>(WireTier.class);
        for (BlockPos link : new ArrayList<>(links)) {
            refund.merge(tierOf(link), 1, Integer::sum);
            if (level.getBlockEntity(link) instanceof FeWireTerminal other && other != this) {
                other.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        linkTiers.clear();
        syncLinks();
        for (Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition,
                    new net.minecraft.world.item.ItemStack(
                            com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    /** Rebuild the network and push the new link list to the wire renderer on any wiring change. */
    private void syncLinks() {
        setChanged();
        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // managed load

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
        refresh();
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

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    /** A shed or a force-off has to stop the flow now, not once the buffer coasts down. */
    private void refresh() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            recompute();
        }
    }

    // client accessors, for the renderer

    /** Throughput this pump is moving, mB/t. The impeller spins in proportion to it. */
    public int shownFlow() {
        return level != null && level.isClientSide() ? syncedFlow : effectiveFlow;
    }

    /** The impeller's interpolated angle for this frame. */
    public float renderAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevClientAngle, clientAngle);
    }

    private void advanceClientSpin() {
        prevClientAngle = clientAngle;
        clientAngle += syncedFlow * SPIN_DEG_PER_MB;
        // [10]
        if (clientAngle >= 360.0f) {
            clientAngle -= 360.0f;
            prevClientAngle -= 360.0f;
        }
    }

    // goggle overlay

    /** [11] */
    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        final java.util.Locale loc = java.util.Locale.ROOT;
        final int flow = shownFlow();
        final int set = targetFlow();
        final int draw = flow * feCostPerMb();
        tooltip.add(gogglePad(net.minecraft.network.chat.Component
                .translatable(getBlockState().getBlock().getDescriptionId())
                .withStyle(net.minecraft.ChatFormatting.GRAY)));
        tooltip.add(gogglePad(net.minecraft.network.chat.Component.literal(
                flow > 0 ? String.format(loc, "Moving %,d mB/t", flow) : "Idle")
                .withStyle(flow > 0 ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.DARK_GRAY)));
        tooltip.add(gogglePad(net.minecraft.network.chat.Component.literal(
                String.format(loc, "Dial: %,d mB/t", set))
                .withStyle(flow < set ? net.minecraft.ChatFormatting.GOLD
                        : net.minecraft.ChatFormatting.GRAY)));
        if (draw > 0) {
            tooltip.add(gogglePad(net.minecraft.network.chat.Component.literal(
                    String.format(loc, "Drawing: %,d FE/t", draw))
                    .withStyle(net.minecraft.ChatFormatting.AQUA)));
        }
        tooltip.add(gogglePad(net.minecraft.network.chat.Component.literal(
                String.format(loc, "Buffer: %,d / %,d FE", energy, BUFFER))
                .withStyle(net.minecraft.ChatFormatting.AQUA)));
        return true;
    }

    private static net.minecraft.network.chat.Component gogglePad(
            net.minecraft.network.chat.Component line) {
        return net.minecraft.network.chat.Component.literal("    ").append(line);
    }

    // save

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        final long[] linkArr = new long[links.size()];
        final byte[] tierArr = new byte[links.size()];
        for (int i = 0; i < links.size(); i++) {
            linkArr[i] = links.get(i).asLong();
            tierArr[i] = (byte) tierOf(links.get(i)).ordinal();
        }
        tag.putLongArray("Links", linkArr);
        tag.putByteArray("LinkTiers", tierArr);
        if (clientPacket) {
            tag.putInt("Flow", effectiveFlow);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
        managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        links.clear();
        linkTiers.clear();
        final long[] packed = tag.getLongArray("Links");
        final byte[] tiers = tag.getByteArray("LinkTiers");
        for (int i = 0; i < packed.length; i++) {
            final BlockPos p = BlockPos.of(packed[i]);
            links.add(p);
            if (i < tiers.length) {
                linkTiers.put(packed[i], WireTier.byOrdinal(tiers[i]));
            }
        }
        if (clientPacket) {
            syncedFlow = Mth.clamp(tag.getInt("Flow"), 0, maxFlow());
        }
    }
}
