package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.PowerDistributionUnitBlock;
import com.createstats.gui.pdu.PduHistory;
import com.createstats.gui.pdu.PduTopology;
import com.createstats.network.PduHistoryPacket;
import com.createstats.registry.ModBlocks;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeGridNode;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.FeProfiles;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.PduAutoController;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** [1] */
public class PowerDistributionUnitBE extends BlockEntity {

    /** [2] */
    public static final int LEGS = com.createstats.power.PduStuds.COUNT + 1;

    /** The leg index of the back cable boss. Studs are 0 to 7; the boss is the one after them. */
    public static final int BACK_LEG = com.createstats.power.PduStuds.COUNT;

    private int energy;
    private int cursor;
    private FeGridSolver.BranchResult lastBranch = FeGridSolver.BranchResult.EMPTY;
    private int lastSyncedEnergy = -1;
    private int ticksSinceSync = 0;
    private int lastRosterSignature = Integer.MIN_VALUE;

    // active Auto controller
    /** [3] */
    private static final long NEVER = PduAutoController.NEVER;

    /** FE taken in since the last tick, snapshotted into {@link #measuredInput} each tick. */
    private int inputAccumulator;
    /** Real FE per tick this PDU pulled in on the last tick. Measured, not inferred. */
    private int measuredInput;
    /** Decaying rolling max of {@link #measuredInput}: observed supply from any source. */
    private int observedInput;
    /** The admit decision, which is pure and therefore unit tested. Holds the switching history. */
    private final PduAutoController autoController = new PduAutoController();
    /** Game time of the last upstream read, so one runs every {@link #supplyReadIntervalTicks()}. */
    private long lastSupplyReadTick = NEVER;
    /** Last upstream read: raw generation capacity, before smoothing. */
    private int rawGeneration;
    /** Last upstream read: whether a capacitor bank on the feeding grid is charging. */
    private boolean rawBankCharging;
    /** True when the branch wiring, not generation, is what caps the budget. Synced for the panel. */
    private boolean lastThroughputLimited;
    /** The branch throughput ceiling as of the last control step. Synced for the panel. */
    private int lastCeiling;
    /** [4] */
    private Map<Long, PduReason> lastReasons = Map.of();

    /** [5] */
    private int syncedCapacity;
    private int syncedFloorPct = 15;

    /** Last Active Auto telemetry, synced so the screen can show supply against demand. */
    private int lastGeneration;
    private int lastDemand;
    private int lastBudget;
    private int lastServedDemand;
    private int lastEnduranceTicks = Integer.MAX_VALUE;
    private boolean lastSurplus;

    /** Server-side smoothing of the noisiest inputs, so the controller and the readout are steady. */
    private static final float TELEMETRY_EMA = 0.1f;
    private float smoothGen = -1.0f;
    private float smoothDemand = -1.0f;

    /** Receive only. The PDU is a load upstream and a source only to its own branch. */
    private final IEnergyStorage inputView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(Math.min(maxReceive, transferRate()), bufferCapacity() - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                inputAccumulator += room;
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
            return bufferCapacity();
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

    public PowerDistributionUnitBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** [6] */
    private final IEnergyStorage backOutputView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;   // an output that took energy back would let the branch feed its own meter
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!isLegEnergised(BACK_LEG)) {
                return 0;
            }
            final int taken = Math.min(Math.min(maxExtract, legCeiling(BACK_LEG)), energy);
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
            return bufferCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public IEnergyStorage energyHandle() {
        return inputView;
    }

    /** The handle the back boss presents. The one face of this block that gives energy out. */
    public IEnergyStorage backOutputHandle() {
        return backOutputView;
    }

    public int storedEnergy() {
        return energy;
    }

    /** Buffer capacity for display. Server truth, so a client never reads its own unsynced config. */
    public int capacity() {
        return syncedCapacity > 0 ? syncedCapacity : bufferCapacity();
    }

    public float fillFraction() {
        final int cap = capacity();
        return cap <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, energy / (float) cap));
    }

    public FeGridSolver.BranchResult lastBranchResult() {
        return lastBranch;
    }

    // tick

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (energy > bufferCapacity()) {
            energy = bufferCapacity();
        }
        // Publish the tuning the server is actually running on, so the client draws the server's
        // buffer and reserve floor rather than its own unsynced copy of a COMMON config.
        syncedCapacity = bufferCapacity();
        syncedFloorPct = reserveFloorPct();
        autoController.setTuning(new PduAutoController.Tuning(dwellTicks(), restoreIntervalTicks()));

        // Snapshot the real intake since the last tick, and fold it into the decaying rolling max
        // that stands in for observed supply from sources the PDU cannot introspect.
        measuredInput = inputAccumulator;
        inputAccumulator = 0;
        observedInput = Math.max(measuredInput, Math.round(observedInput * observedDecay()));

        // [7]
        pruneStudLinks();

        // [8]
        final List<FeGridNode> managed = collectBranchLoads();

        // [9]
        final List<FeGridNode> loads = new ArrayList<>(managed.size());
        for (FeGridNode node : managed) {
            if (lockOf(node) == FeLock.FORCE_OFF) {
                setShedFlag(node, true);
            } else {
                loads.add(node);
            }
        }

        // Running overhead, only while FE Dependent Mode is on so the block is free otherwise.
        if (FeProfiles.feModeEnabled()) {
            energy = Math.max(0, energy - idleDrainPerTick());
        }

        if (loads.isEmpty()) {
            lastBranch = FeGridSolver.BranchResult.EMPTY;
        } else {
            // [10]
            if (mode != PduMode.AUTO) {
                for (FeGridNode node : loads) {
                    setShedFlag(node, false);
                }
            }
            lastBranch = switch (mode) {
                case OFF -> serveAcrossLegs(loads, false);
                case MANUAL -> serveAcrossLegs(loads, true);
                case AUTO -> serveActiveAuto(loads, Math.min(energy, branchThroughputCeiling()));
            };
            energy -= lastBranch.delivered();
            if (lastBranch.delivered() > 0) {
                setChanged();
            }
        }
        rebuildRoster(managed);
        recordHistory();
        syncIfDue();
    }

    /** [11] */
    private List<FeGridNode> collectBranchLoads() {
        final List<FeGridNode> loads = new ArrayList<>();
        legOfNode.clear();
        int conductors = 0;
        int buffered = 0;
        int capacity = 0;
        final java.util.Set<Long> seenNodes = new HashSet<>();
        for (int leg = 0; leg < LEGS; leg++) {
            for (FeWireNetwork network : legNetworks(leg)) {
                network.markPduManaged(level.getGameTime());
                for (com.createstats.power.FeGridMember node : network.members()) {
                    // [12]
                    if (node instanceof CableBE cable) {
                        conductors++;
                        buffered += cable.storedEnergy();
                        capacity += cable.bufferCapacity();
                    } else if (node instanceof PowerConnectorBE connector) {
                        conductors++;
                        buffered += connector.storedEnergy();
                        capacity += connector.bufferCapacity();
                    }
                    final IEnergyStorage attached = node.attachedStorage();
                    if (attached == null || !attached.canReceive() || attached.canExtract()) {
                        continue;
                    }
                    // [13]
                    if (worldPosition.equals(devicePosOf(node))) {
                        continue;
                    }
                    // A machine touched by two legs belongs to the first that reached it. Serving it
                    // twice in one tick would hand it two deliveries and double count its demand.
                    if (!seenNodes.add(node.nodeKey())) {
                        continue;
                    }
                    legOfNode.put(node.nodeKey(), leg);
                    loads.add(node);
                }
            }
        }
        wiring = new BranchWiring(conductors, buffered, capacity);
        return loads;
    }

    /** Which leg a node hangs off, or the default leg when it somehow was not tagged. */
    private int legOf(FeGridNode node) {
        return legOfNode.getOrDefault(node.nodeKey(), 0);
    }

    /** [14] */
    public Direction outputFace() {
        return Direction.UP;
    }

    /** [15] */
    public Direction backFace() {
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return facing.getOpposite();
    }

    /** [16] */
    public static int backRate() {
        return studRate() * 2;
    }

    /** True when a feed may be wired to this face: either side boss, and nothing else. */
    public boolean isInputFace(Direction face) {
        if (face == null || face.getAxis() == Direction.Axis.Y) {
            return false;
        }
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return face == facing.getClockWise() || face == facing.getCounterClockWise();
    }

    /** Per boss, and there are two of them. */
    public static int inputRatePerBoss() {
        return 96_000;
    }

    /** [17] */
    public Direction inputFace() {
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return facing.getClockWise();
    }

    // active Auto: supply reading + the controller

    /** The block a connector is mounted on, or null. Used to read dynamo and cap bank telemetry. */
    @Nullable
    private BlockEntity attachedBlockEntity(com.createstats.power.FeGridMember node) {
        if (level == null) {
            return null;
        }
        // A connector is mounted on exactly one block, so its attachment is known from its facing.
        if (node instanceof PowerConnectorBE connector) {
            final Direction facing = connector.getBlockState().hasProperty(com.createstats.block.PowerConnectorBlock.FACING)
                    ? connector.getBlockState().getValue(com.createstats.block.PowerConnectorBlock.FACING)
                    : Direction.UP;
            return level.getBlockEntity(connector.getBlockPos().relative(facing.getOpposite()));
        }
        // A cable segment touches up to six blocks, so the generator or bank it is reading has to be
        // found by looking, not by geometry.
        final com.createstats.blockentity.CableBE cable =
                node instanceof com.createstats.blockentity.CableBE direct ? direct : null;
        if (cable != null) {
            for (Direction dir : Direction.values()) {
                final BlockEntity be = level.getBlockEntity(cable.getBlockPos().relative(dir));
                if (be instanceof KineticDynamoBE || be instanceof CapacitorBankBE) {
                    return be;
                }
            }
        }
        return null;
    }

    /** Real FE per tick this PDU pulled in on the last server tick. Measured, not inferred. */
    public int measuredInputFePerTick() {
        return measuredInput;
    }

    /** Active Auto telemetry, all synced to the client for the screen. */
    public int generationFePerTick() {
        return lastGeneration;
    }

    public int branchDemandFePerTick() {
        return lastDemand;
    }

    public int budgetFePerTick() {
        return lastBudget;
    }

    public int servedDemandFePerTick() {
        return lastServedDemand;
    }

    public int autoEnduranceTicks() {
        return lastEnduranceTicks;
    }

    public boolean isSurplus() {
        return lastSurplus;
    }

    /** True when the branch wiring caps the budget, so building more generation would not help. */
    public boolean isThroughputLimited() {
        return lastThroughputLimited;
    }

    /** The most the branch wiring can carry, so the panel can name the ceiling it is hitting. */
    public int branchCeilingFePerTick() {
        return lastCeiling;
    }

    public float reserveFloorFraction() {
        return syncedFloorPct / 100.0f;
    }

    /** [18] */
    public int measuredGeneration() {
        return Math.max(rawGeneration, observedInput);
    }

    /** [19] */
    private void readUpstream() {
        int knownGen = 0;
        boolean charging = false;
        final Direction out = outputFace();
        final List<FeWireNetwork> counted = new ArrayList<>(2);

        for (Direction face : Direction.values()) {
            // [20]
            if (face == out || !isInputFace(face)) {
                continue;
            }
            final FeWireNetwork network = feedNetworkOn(face);
            if (network == null) {
                continue;
            }
            boolean alreadyCounted = false;
            for (FeWireNetwork seen : counted) {
                if (seen == network) {
                    alreadyCounted = true;
                    break;
                }
            }
            if (alreadyCounted) {
                continue;
            }
            counted.add(network);
            for (com.createstats.power.FeGridMember node : network.members()) {
                final BlockEntity attached = attachedBlockEntity(node);
                if (attached instanceof KineticDynamoBE dynamo) {
                    knownGen += dynamo.potentialFePerTick();
                } else if (!charging && attached instanceof CapacitorBankBE bank
                        && bank.chargeRatePerTick() > 0) {
                    charging = true;
                }
            }
        }
        rawGeneration = knownGen;
        rawBankCharging = charging;
    }

    /** The grid of a connector mounted on the given face of this block, if there is one. */
    @Nullable
    private FeWireNetwork feedNetworkOn(Direction face) {
        if (level.getBlockEntity(worldPosition.relative(face)) instanceof PowerConnectorBE connector
                && connector.getBlockState().hasProperty(com.createstats.block.PowerConnectorBlock.FACING)
                && connector.getBlockState().getValue(com.createstats.block.PowerConnectorBlock.FACING) == face) {
            return connector.network();
        }
        return null;
    }

    /** [21] */
    public static int studRate() {
        return PowerConnectorBE.baseTransferRate() * PowerConnectorBE.advancedMultiplier();
    }

    /** [22] */
    public int legCeiling(int leg) {
        if (leg == BACK_LEG) {
            // [23]
            final com.createstats.power.CableTier tier = backCableTier();
            return tier == null ? 0 : Math.min(backRate(), tier.rate(PowerConnectorBE.baseTransferRate()));
        }
        return com.createstats.power.PduStudWiring.ceiling(studRate(),
                PowerConnectorBE.baseTransferRate(), bestStudTier(leg));
    }

    /** The grade of cable sitting on the back boss, or null when there is none. */
    @Nullable
    private com.createstats.power.CableTier backCableTier() {
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(worldPosition.relative(backFace()))
                instanceof com.createstats.blockentity.CableBE cable ? cable.tier() : null;
    }

    /** The best grade strung to one stud, or null when the stud is bare. */
    @Nullable
    private com.createstats.power.WireTier bestStudTier(int leg) {
        if (leg < 0 || leg >= com.createstats.power.PduStuds.COUNT) {
            return null;
        }
        final List<com.createstats.power.WireTier> tiers = new ArrayList<>(studLinks.get(leg).size());
        for (StudLink link : studLinks.get(leg)) {
            tiers.add(link.tier());
        }
        return com.createstats.power.PduStudWiring.best(tiers);
    }

    /** The whole board's output rating, which is every leg's added together. */
    public int branchThroughputCeiling() {
        int total = 0;
        for (int leg = 0; leg < LEGS; leg++) {
            total += legCeiling(leg);
        }
        return total > 0 ? total : studRate();
    }

    /** [24] */
    private List<FeWireNetwork> legNetworks(int leg) {
        if (level == null || leg < 0 || leg >= LEGS) {
            return List.of();
        }
        if (leg == BACK_LEG) {
            // [25]
            if (!(level.getBlockEntity(worldPosition.relative(backFace()))
                    instanceof com.createstats.blockentity.CableBE cable)) {
                return List.of();
            }
            FeWireNetwork network = cable.network();
            if (network == null || network.isDirty()) {
                network = FeWireNetwork.buildFrom(cable);
            }
            return network == null ? List.of() : List.of(network);
        }
        if (studLinks.get(leg).isEmpty()) {
            return List.of();
        }
        final List<FeWireNetwork> found = new ArrayList<>(2);
        for (StudLink link : studLinks.get(leg)) {
            if (!level.isLoaded(link.to())
                    || !(level.getBlockEntity(link.to()) instanceof PowerConnectorBE connector)) {
                continue;   // unloaded, or pruned next tick if it's actually gone
            }
            FeWireNetwork network = connector.network();
            if (network == null || network.isDirty()) {
                network = FeWireNetwork.buildFrom(connector);
            }
            if (network == null) {
                continue;
            }
            // [26]
            boolean seen = false;
            for (FeWireNetwork other : found) {
                if (other == network) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                found.add(network);
            }
        }
        return found;
    }

    // the eight studs, as wire terminals

    /** One wire span from a stud to a Power Connector, and the grade it was strung with. */
    public record StudLink(BlockPos to, com.createstats.power.WireTier tier) {
    }

    /** One list per stud. Server truth, carried in the update tag so the renderer can draw them. */
    private final List<List<StudLink>> studLinks = newStudLinks();

    private static List<List<StudLink>> newStudLinks() {
        final List<List<StudLink>> lists = new ArrayList<>(com.createstats.power.PduStuds.COUNT);
        for (int i = 0; i < com.createstats.power.PduStuds.COUNT; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    /** The wires on one stud, for the renderer and the panel. Empty for an index that is not a stud. */
    public List<StudLink> studLinks(int stud) {
        return stud >= 0 && stud < com.createstats.power.PduStuds.COUNT
                ? studLinks.get(stud) : List.of();
    }

    /** [27] */
    public boolean hasAnyOutput() {
        return wiresOnStuds() > 0 || legRating(BACK_LEG) > 0;
    }

    /** How many wires are strung to the bus bar in total. Studs only; the back boss takes cable. */
    public int wiresOnStuds() {
        int total = 0;
        for (List<StudLink> links : studLinks) {
            total += links.size();
        }
        return total;
    }

    /** How many wires one stud holds, so the item can refuse a ninth without knowing the storage. */
    public int studLinkCount(int stud) {
        return studLinks(stud).size();
    }

    /** [28] */
    public boolean isLinkedToConnector(BlockPos connector) {
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                if (link.to().equals(connector)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Strings one wire from a stud to a connector, both sides at once. Server side; the caller has
     * already validated the stud, the range and both ends' capacity.
     */
    public void linkStud(int stud, PowerConnectorBE connector, com.createstats.power.WireTier tier) {
        if (level == null || level.isClientSide() || stud < 0
                || stud >= com.createstats.power.PduStuds.COUNT) {
            return;
        }
        studLinks.get(stud).add(new StudLink(connector.getBlockPos().immutable(),
                tier == null ? com.createstats.power.WireTier.COPPER : tier));
        connector.addStudLink(worldPosition, tier);
        syncStudLinks();
    }

    /** [29] */
    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || stud < 0
                || stud >= com.createstats.power.PduStuds.COUNT) {
            return 0;
        }
        final List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        final int removed = links.size();
        final java.util.Map<com.createstats.power.WireTier, Integer> refund =
                new java.util.EnumMap<>(com.createstats.power.WireTier.class);
        for (StudLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);
            if (level.getBlockEntity(link.to()) instanceof PowerConnectorBE connector) {
                connector.removeStudLink(worldPosition);
            }
        }
        links.clear();
        for (java.util.Map.Entry<com.createstats.power.WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition,
                    new net.minecraft.world.item.ItemStack(
                            com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
        syncStudLinks();
        return removed;
    }

    /** Drops every wire on every stud. Called when the panel itself is broken. */
    public void dropAllStudWires() {
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            dropStudWires(stud);
        }
    }

    /**
     * Forgets every wire to one connector without dropping an item, because the connector's own strip
     * has already refunded it. Called from {@link PowerConnectorBE#dropWiresAndUnlinkAll}.
     */
    public void forgetLinksTo(BlockPos connector) {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> link.to().equals(connector));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    /** [30] */
    private void pruneStudLinks() {
        if (level == null) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> level.isLoaded(link.to())
                    && !(level.getBlockEntity(link.to()) instanceof PowerConnectorBE connector
                    && connector.links().contains(worldPosition)));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void syncStudLinks() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            // Flag 2: clients only. The wire renderer and the leg rows need the new list; the
            // neighbours do not need a redstone update out of a wiring change.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Breaker state per leg. Closed and untripped is the ordinary case. */
    private final boolean[] legClosed = newAllClosed();
    private final boolean[] legTripped = new boolean[LEGS];
    /** Which leg each managed node hangs off, rebuilt every tick with the rosters. */
    private final java.util.Map<Long, Integer> legOfNode = new HashMap<>();
    /** Client side copies of the per leg figures, filled from the update tag. */
    private final int[] syncedLegCeiling = new int[LEGS];
    private final int[] syncedLegDemand = new int[LEGS];
    private final int[] syncedLegAdmitted = new int[LEGS];

    /** This leg's rating as the panel should show it: the server's figure on either side. */
    public int legRating(int leg) {
        if (leg < 0 || leg >= syncedLegCeiling.length) {
            return 0;
        }
        return level != null && level.isClientSide() ? syncedLegCeiling[leg] : legCeiling(leg);
    }

    /** [31] */
    public boolean isLegWired(int leg) {
        return legRating(leg) > 0;
    }

    public int legShownDemand(int leg) {
        if (leg < 0 || leg >= syncedLegDemand.length) {
            return 0;
        }
        return level != null && level.isClientSide() ? syncedLegDemand[leg] : legDemand(leg);
    }

    public int legShownAdmitted(int leg) {
        if (leg < 0 || leg >= syncedLegAdmitted.length) {
            return 0;
        }
        return level != null && level.isClientSide() ? syncedLegAdmitted[leg] : legAdmitted(leg);
    }

    /** True when this leg turned something away for want of conductor, on either side. */
    public boolean isLegOverCapacity(int leg) {
        return legShownDemand(leg) > legShownAdmitted(leg);
    }

    /** How many managed devices hang off this leg. */
    public int legDeviceCount(int leg) {
        int count = 0;
        for (BranchDevice d : roster) {
            if (d.leg() == leg) {
                count++;
            }
        }
        return count;
    }

    /** Consecutive ticks each leg has been over its conductor, for the trip delay. */
    private final int[] legOverload = new int[LEGS];
    /** Three seconds of sustained overload before a breaker throws. A spike must not trip one. */
    private static final int TRIP_TICKS = 60;
    /** Last screen result, kept so the panel can report per leg demand without redoing the work. */
    @Nullable
    private com.createstats.power.PduLegPlan.Screened lastScreened;

    /** FE per tick this leg was asked for, whether or not its conductor could carry it. */
    public int legDemand(int leg) {
        return lastScreened != null && leg >= 0 && leg < lastScreened.legDemand().length
                ? lastScreened.legDemand()[leg] : 0;
    }

    /** FE per tick that fitted inside this leg's conductor. */
    public int legAdmitted(int leg) {
        return lastScreened != null && leg >= 0 && leg < lastScreened.legAdmitted().length
                ? lastScreened.legAdmitted()[leg] : 0;
    }

    /** True when this leg turned something away for want of conductor, not for want of supply. */
    public boolean isLegAtCapacity(int leg) {
        return lastScreened != null && lastScreened.isAtCapacity(leg);
    }

    private static boolean[] newAllClosed() {
        final boolean[] closed = new boolean[LEGS];
        java.util.Arrays.fill(closed, true);
        return closed;
    }

    public boolean isLegClosed(int leg) {
        return leg >= 0 && leg < legClosed.length && legClosed[leg];
    }

    public boolean isLegTripped(int leg) {
        return leg >= 0 && leg < legTripped.length && legTripped[leg];
    }

    /** True when this leg may draw: the player has not opened it and it has not tripped. */
    public boolean isLegEnergised(int leg) {
        return isLegClosed(leg) && !isLegTripped(leg);
    }

    /** Opens or closes one leg's breaker, from the panel. Clearing a trip is the same action. */
    public void setLegClosed(int leg, boolean closed) {
        if (leg < 0 || leg >= legClosed.length || level == null || level.isClientSide()) {
            return;
        }
        legClosed[leg] = closed;
        if (closed) {
            legTripped[leg] = false;   // closing a breaker is how a player resets one
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** True when there is spare supply: the buffer is above the restore mark or a bank is charging. */
    private boolean isUpstreamSurplus() {
        return energy > Math.round(bufferCapacity() * restoreSocFraction()) || rawBankCharging;
    }

    /** The player's override for a load, or {@link FeLock#NONE} for anything that is not ours. */
    private static FeLock lockOf(FeGridNode node) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            return fe.lock();
        }
        return node instanceof com.createstats.power.FeManagedLoad managed ? managed.managedLock() : FeLock.NONE;
    }

    private static int priorityOrdinalOf(FeGridNode node) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            return fe.priority().ordinal();
        }
        return node instanceof com.createstats.power.FeManagedLoad managed
                ? managed.managedPriority().ordinal() : FePriority.DEFAULT.ordinal();
    }

    /** [32] */
    private static com.createstats.power.FeLoadCost costOf(FeGridNode node) {
        final IEnergyStorage attached = node.attachedStorage();
        if (attached instanceof FeConsumer fe) {
            return com.createstats.power.FeLoadCost.consumer(
                    fe.measuredDrainFePerTick(), fe.settings().activeDrain());
        }
        if (node instanceof com.createstats.power.FeRatedLoad rated) {
            return com.createstats.power.FeLoadCost.rated(
                    rated.ratedDrawFePerTick(), rated.measuredDrawFePerTick());
        }
        return attached == null ? com.createstats.power.FeLoadCost.NONE
                : com.createstats.power.FeLoadCost.opaque(
                        attached.receiveEnergy(node.transferRate(), true));
    }

    /** What a device draws, so the controller knows the running cost and a shed device's restore cost. */
    private static int demandOf(FeGridNode node) {
        return costOf(node).budgetCost();
    }

    private static void setShedFlag(FeGridNode node, boolean shed) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            fe.setLoadShed(shed);
            return;
        }
        // [33]
        if (node instanceof com.createstats.power.FeManagedLoad managed) {
            managed.setManagedShed(shed);
        }
    }

    /** [34] */
    private FeGridSolver.BranchResult serveAcrossLegs(List<FeGridNode> loads, boolean byPriority) {
        final List<List<FeGridNode>> byLeg = new ArrayList<>();
        for (int i = 0; i < LEGS; i++) {
            byLeg.add(new ArrayList<>());
        }
        for (FeGridNode node : loads) {
            byLeg.get(legOf(node)).add(node);
        }
        int delivered = 0;
        int served = 0;
        int shed = 0;
        int remaining = energy;
        for (int leg = 0; leg < byLeg.size(); leg++) {
            final List<FeGridNode> here = byLeg.get(leg);
            if (here.isEmpty()) {
                continue;
            }
            if (!isLegEnergised(leg)) {
                for (FeGridNode node : here) {
                    setShedFlag(node, true);
                }
                continue;   // an open breaker is a switch, not a shed, so it is not counted as one
            }
            final int pool = Math.min(remaining, legCeiling(leg));
            final FeGridSolver.BranchResult result = byPriority
                    ? FeGridSolver.serveByPriority(here, pool, cursor++)
                    : FeGridSolver.serveFlat(here, pool, cursor++);
            delivered += result.delivered();
            served += result.served();
            shed += result.shed();
            remaining = Math.max(0, remaining - result.delivered());
        }
        return new FeGridSolver.BranchResult(delivered, served, shed);
    }

    /** [35] */
    private FeGridSolver.BranchResult serveActiveAuto(List<FeGridNode> loads, int pool) {
        final long now = level.getGameTime();
        final int floorEnergy = Math.round(bufferCapacity() * (reserveFloorPct() / 100.0f));
        final boolean belowFloor = energy < floorEnergy;
        final int rideThrough = Math.max(0, energy - floorEnergy) / rideHorizonTicks();

        // [36]
        final List<com.createstats.power.PduLegPlan.Device> tagged = new ArrayList<>(loads.size());
        final Map<Long, FeGridNode> byKey = new HashMap<>();
        for (FeGridNode node : loads) {
            tagged.add(new com.createstats.power.PduLegPlan.Device(
                    node.nodeKey(), legOf(node), priorityOrdinalOf(node), demandOf(node), lockOf(node)));
            byKey.put(node.nodeKey(), node);
        }

        // [37]
        final com.createstats.power.PduLegPlan.Leg[] legs =
                new com.createstats.power.PduLegPlan.Leg[LEGS];
        for (int leg = 0; leg < legs.length; leg++) {
            legs[leg] = new com.createstats.power.PduLegPlan.Leg(isLegEnergised(leg), legCeiling(leg));
        }
        final com.createstats.power.PduLegPlan.Screened screened =
                com.createstats.power.PduLegPlan.screen(tagged, legs);
        lastScreened = screened;
        final List<PduAutoController.Device> devices = screened.survivors();
        final int totalDemand = screened.totalDemand();

        // [38]
        for (int leg = 0; leg < legs.length; leg++) {
            if (screened.isAtCapacity(leg) && isLegEnergised(leg)) {
                legOverload[leg] = Math.min(TRIP_TICKS, legOverload[leg] + 1);
                if (legOverload[leg] >= TRIP_TICKS) {
                    legTripped[leg] = true;
                    legOverload[leg] = 0;
                }
            } else {
                legOverload[leg] = 0;
            }
        }

        // [39]
        if (lastSupplyReadTick == NEVER || now - lastSupplyReadTick >= supplyReadIntervalTicks()) {
            lastSupplyReadTick = now;
            readUpstream();
        }
        // Smooth generation so a sawtoothing input (the observed-supply peak, a wobbling dynamo) does
        // not jitter every decision and every bar.
        final int rawGen = measuredGeneration();
        smoothGen = smoothGen < 0.0f ? rawGen : smoothGen + (rawGen - smoothGen) * TELEMETRY_EMA;
        final int gen = Math.max(0, Math.round(smoothGen));
        final int ceiling = branchThroughputCeiling();
        final boolean surplus = isUpstreamSurplus();
        // [40]
        final int runBudget = surplus ? ceiling : Math.min(ceiling, gen + rideThrough);
        final int admitBudget = surplus ? ceiling : Math.min(ceiling, gen);
        // [41]
        final boolean throughputLimited = surplus ? totalDemand > ceiling : ceiling < gen + rideThrough;
        final PduAutoController.Supply supply = new PduAutoController.Supply(
                runBudget, admitBudget, Math.round(gen * burstMarginFraction()),
                belowFloor, surplus, throughputLimited);
        lastThroughputLimited = throughputLimited;
        lastCeiling = ceiling;

        final PduAutoController.Plan plan = autoController.decide(devices, supply, now);
        // [42]
        final Map<Long, PduReason> reasons = new HashMap<>(plan.reasons());
        reasons.putAll(screened.blocked());
        lastReasons = reasons;

        // Split the branch by the decision. Admitted nodes are collected in the controller's admit
        // order (tier, then cheapest) so the per-tier fair share still starts where it used to.
        final List<FeGridNode> admitted = new ArrayList<>(plan.admitted().size());
        for (long key : plan.admitted()) {
            final FeGridNode node = byKey.get(key);
            if (node != null) {
                admitted.add(node);
            }
        }
        // Mark everything not admitted as shed (or NO POWER, if nothing at all could be admitted).
        final boolean anyAdmitted = !admitted.isEmpty();
        int shed = 0;
        for (FeGridNode node : loads) {
            if (!plan.isRunning(node.nodeKey())) {
                setShedFlag(node, anyAdmitted);
                if (anyAdmitted) {
                    shed++;
                }
            }
        }
        // Physically serve the admitted set, leg by leg, each against its own conductor. One board
        // wide pool would let a hungry leg spend capacity that belongs to a different wire.
        final List<List<FeGridNode>> admittedByLeg = new ArrayList<>();
        for (int i = 0; i < LEGS; i++) {
            admittedByLeg.add(new ArrayList<>());
        }
        for (FeGridNode node : admitted) {
            admittedByLeg.get(legOf(node)).add(node);
        }
        int deliveredTotal = 0;
        int remaining = pool;
        for (int leg = 0; leg < admittedByLeg.size(); leg++) {
            final List<FeGridNode> here = admittedByLeg.get(leg);
            if (here.isEmpty() || !isLegEnergised(leg)) {
                continue;
            }
            final FeGridSolver.BranchResult legResult = FeGridSolver.serveByPriority(
                    here, Math.min(remaining, legCeiling(leg)), cursor++);
            deliveredTotal += legResult.delivered();
            remaining = Math.max(0, remaining - legResult.delivered());
        }
        final FeGridSolver.BranchResult served =
                new FeGridSolver.BranchResult(deliveredTotal, admitted.size(), 0);
        // [43]
        for (FeGridNode node : admitted) {
            setShedFlag(node, false);
        }

        // Telemetry for the screen: generation, demand, what is actually running, and the reserve line.
        smoothDemand = smoothDemand < 0.0f ? totalDemand : smoothDemand + (totalDemand - smoothDemand) * TELEMETRY_EMA;
        lastGeneration = gen;
        lastDemand = Math.max(0, Math.round(smoothDemand));
        lastBudget = runBudget;
        lastServedDemand = plan.admittedDemand();
        lastSurplus = supply.surplus();
        final int netDrain = Math.max(0, plan.admittedDemand() - gen + idleDrainPerTick());
        lastEnduranceTicks = netDrain > 0 ? Math.max(0, energy - floorEnergy) / netDrain : Integer.MAX_VALUE;

        return new FeGridSolver.BranchResult(served.delivered(), admitted.size(), shed);
    }

    // branch roster, synced so the screen can list it

    /**
     * One managed device, as the screen sees it.
     *
     * @param lighting whether this is a light fitting, so the screen can show lighting on its own tab.
     *                 A base's lights are the one load group a player thinks about as a group: they
     *                 are numerous, individually trivial, and the first thing you want to shed.
     */
    /**
     * @param id a per branch number scoping this device against others of the same name, or 0 when it
     *           is the only one of its kind. See {@link com.createstats.power.PduDeviceIds}.
     */
    public record BranchDevice(BlockPos pos, String name, int id, int leg, FePriority priority,
                               int draw, boolean shed, FeLock lock, PduReason reason,
                               boolean lighting) {

        /** How this device reads on a row: its name, plus its number when it needs one. */
        public String label() {
            return com.createstats.power.PduDeviceIds.label(name, id, id <= 0);
        }
    }

    /** [44] */
    public record BranchWiring(int segments, int buffered, int capacity) {
        public static final BranchWiring NONE = new BranchWiring(0, 0, 0);
    }

    private BranchWiring wiring = BranchWiring.NONE;

    /** [45] */
    private final com.createstats.power.PduDeviceIds deviceIds = new com.createstats.power.PduDeviceIds();

    public BranchWiring wiring() {
        return wiring;
    }

    private final List<BranchDevice> roster = new ArrayList<>();
    private PduMode mode = PduMode.MANUAL;

    // [46]
    private final PduHistory history1m = new PduHistory();
    private final PduHistory history5m = new PduHistory();
    private final PduHistory history15m = new PduHistory();
    /** Players watching the HISTORY view, by UUID, with the range they watch and when last heard from. */
    private final Map<UUID, HistorySub> historySubs = new HashMap<>();

    private static final class HistorySub {
        int range;
        long lastSeen;
    }

    /** A watcher that has gone this many ticks without a keepalive is dropped. Five seconds. */
    private static final long HISTORY_TIMEOUT = 100L;
    /** Beyond this distance a watcher is dropped, a generous backstop to the client's own auto close. */
    private static final double HISTORY_RANGE_SQ = 128.0 * 128.0;

    /** The branch's wiring as a spanning tree, synced for the WIRING diagram, rebuilt on roster change. */
    private List<PduTopology.BranchNode> topology = List.of();
    private boolean topologyTruncated;

    public List<BranchDevice> roster() {
        return roster;
    }

    public PduMode mode() {
        return mode;
    }

    public void setMode(PduMode newMode) {
        this.mode = newMode == null ? PduMode.MANUAL : newMode;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Cycles OFF to MANUAL to AUTO, from the one screen button. */
    public void cycleMode() {
        setMode(mode.next());
    }

    /** [47] */
    private interface DeviceSettings {
        FePriority priority();

        void setPriority(FePriority tier);

        FeLock lock();

        void setLock(FeLock lock);
    }

    /** The settings of the device at this position, or null when it is not a controllable load. */
    @Nullable
    private DeviceSettings settingsAt(BlockPos pos) {
        if (level == null) {
            return null;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.createstats.power.IFeConsumer owner) {
            final FeConsumer fe = owner.feConsumer();
            return new DeviceSettings() {
                @Override
                public FePriority priority() {
                    return fe.priority();
                }

                @Override
                public void setPriority(FePriority tier) {
                    fe.setPriority(tier);
                }

                @Override
                public FeLock lock() {
                    return fe.lock();
                }

                @Override
                public void setLock(FeLock lock) {
                    fe.setLock(lock);
                    // Coming off a force-off, a stale shed flag would read as if the unlock did
                    // nothing. The next control step decides properly.
                    if (lock == FeLock.NONE) {
                        fe.setLoadShed(false);
                    }
                }
            };
        }
        if (be instanceof com.createstats.power.FeManagedLoad managed) {
            return new DeviceSettings() {
                @Override
                public FePriority priority() {
                    return managed.managedPriority();
                }

                @Override
                public void setPriority(FePriority tier) {
                    managed.setManagedPriority(tier);
                }

                @Override
                public FeLock lock() {
                    return managed.managedLock();
                }

                @Override
                public void setLock(FeLock lock) {
                    managed.setManagedLock(lock);
                }
            };
        }
        return null;
    }

    /** Cycles one managed device's tier, from the screen. */
    public void cyclePriority(BlockPos devicePos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final DeviceSettings settings = settingsAt(devicePos);
        if (settings != null) {
            settings.setPriority(settings.priority().next());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Cycles one managed device's player override, from the screen. */
    public void cycleLock(BlockPos devicePos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final DeviceSettings settings = settingsAt(devicePos);
        if (settings != null) {
            settings.setLock(settings.lock().next());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** The positions of every device on the branch this tick, the authority a bulk action is checked against. */
    private Set<BlockPos> branchPositions() {
        final Set<BlockPos> positions = new HashSet<>(roster.size() * 2);
        for (BranchDevice d : roster) {
            positions.add(d.pos());
        }
        return positions;
    }

    /** [48] */
    public void bulkSetPriority(List<BlockPos> targets, int tierOrdinal) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final FePriority tier = FePriority.byOrdinal(tierOrdinal);
        boolean changed = false;
        for (BlockPos pos : com.createstats.power.PduBranchGuard.retainOnBranch(targets, branchPositions())) {
            final DeviceSettings settings = settingsAt(pos);
            if (settings != null) {
                settings.setPriority(tier);
                changed = true;
            }
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** sets the lock of every named device actually on this branch. validated like the tier set. */
    public void bulkSetLock(List<BlockPos> targets, int lockOrdinal) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final FeLock lock = FeLock.byOrdinal(lockOrdinal);
        boolean changed = false;
        for (BlockPos pos : com.createstats.power.PduBranchGuard.retainOnBranch(targets, branchPositions())) {
            final DeviceSettings settings = settingsAt(pos);
            if (settings != null) {
                settings.setLock(lock);
                changed = true;
            }
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** [49] */
    public void setAllLightingTier(int tierOrdinal) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final FePriority tier = FePriority.byOrdinal(tierOrdinal);
        boolean changed = false;
        for (BranchDevice d : roster) {
            if (!d.lighting()) {
                continue;
            }
            final DeviceSettings settings = settingsAt(d.pos());
            if (settings != null) {
                settings.setPriority(tier);
                changed = true;
            }
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** [50] */
    public void assignAutoPriorities() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (BranchDevice device : new ArrayList<>(roster)) {
            final DeviceSettings settings = settingsAt(device.pos());
            if (settings != null) {
                settings.setPriority(autoTierFor(level.getBlockState(device.pos()).getBlock()));
            }
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** [51] */
    private static FePriority autoTierFor(net.minecraft.world.level.block.Block block) {
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !"create_stats".equals(id.getNamespace())) {
            return FePriority.DEFAULT;
        }
        return AUTO_TIERS.getOrDefault(id.getPath(), FePriority.DEFAULT);
    }

    /** [52] */
    private static final Map<String, FePriority> AUTO_TIERS = Map.ofEntries(
            // Lose these and you lose the vehicle.
            Map.entry("flight_control_computer", FePriority.CRITICAL),
            Map.entry("vcc_mk2", FePriority.CRITICAL),
            Map.entry("pathfinder_computer", FePriority.CRITICAL),
            // Safety and situational awareness.
            Map.entry("path_scanner", FePriority.HIGH),
            Map.entry("stats_alarm", FePriority.HIGH),
            Map.entry("damage_observer", FePriority.HIGH),
            Map.entry("battle_screen_display", FePriority.HIGH),
            // Instrumentation and factory logic: useful, survivable.
            Map.entry("factory_logic_controller", FePriority.NORMAL),
            Map.entry("production_detector", FePriority.NORMAL),
            Map.entry("stats_display_panel", FePriority.NORMAL),
            Map.entry("stats_display_mk2", FePriority.NORMAL),
            Map.entry("stats_recorder", FePriority.NORMAL),
            Map.entry("dispatch_board", FePriority.NORMAL),
            Map.entry("mini_display", FePriority.NORMAL),
            // Nice to have.
            Map.entry("holographic_projector", FePriority.LOW),
            // Decoration. First to go, and no great loss when it does.
            Map.entry("status_tower_light", FePriority.OPTIONAL),
            Map.entry("stage_light", FePriority.OPTIONAL),
            Map.entry("stage_light_mk2", FePriority.OPTIONAL),
            Map.entry("projected_light", FePriority.OPTIONAL));

    /** [53] */
    private PduReason reasonFor(long nodeKey, FeConsumer fe) {
        if (fe.lock() == FeLock.FORCE_OFF) {
            return PduReason.LOCKED_OFF;
        }
        if (mode == PduMode.AUTO) {
            return lastReasons.getOrDefault(nodeKey, PduReason.RUNNING);
        }
        if (fe.lock() == FeLock.FORCE_ON) {
            return PduReason.LOCKED_ON;
        }
        return fe.isLoadShed() ? PduReason.TIER_SHED : PduReason.RUNNING;
    }

    /** The block a wire node is feeding, or null when it cannot be identified. */
    /** The lights this mod ships that a player manages as a group, beyond the basic Lamp. */
    private static final Set<String> LIGHTING_IDS = Set.of(
            "status_tower_light", "stage_light", "stage_light_mk2", "projected_light");

    /** [54] */
    private boolean isLighting(BlockPos at) {
        if (level == null) {
            return false;
        }
        if (level.getBlockEntity(at) instanceof com.createstats.blockentity.LampBE) {
            return true;
        }
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock());
        return id != null && "create_stats".equals(id.getNamespace()) && LIGHTING_IDS.contains(id.getPath());
    }

    private BlockPos devicePosOf(FeGridNode node) {
        // An attachment already knows exactly which block it serves; that is the whole reason it
        // exists, and searching for it again would be able to disagree with it.
        if (node instanceof com.createstats.power.FeAttachment attachment) {
            return attachment.machine();
        }
        if (node instanceof PowerConnectorBE connector) {
            return connector.getBlockPos().relative(connector.getBlockState()
                    .getValue(com.createstats.block.PowerConnectorBlock.FACING).getOpposite());
        }
        if (node instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();   // a lamp and anything else that is its own load
        }
        return null;
    }

    private void rebuildRoster(List<FeGridNode> loads) {
        roster.clear();
        cachedRollCall = null;
        // [55]
        final Map<BlockPos, String> names = new HashMap<>(loads.size() * 2);
        final Map<String, Integer> nameCounts = new HashMap<>();
        for (FeGridNode node : loads) {
            final BlockPos devicePos = devicePosOf(node);
            if (devicePos == null || node.attachedStorage() == null) {
                continue;
            }
            final String name = level.getBlockState(devicePos).getBlock().getName().getString();
            names.put(devicePos, name);
            nameCounts.merge(name, 1, Integer::sum);
        }
        deviceIds.assignAll(names);
        for (FeGridNode node : loads) {
            final IEnergyStorage attached = node.attachedStorage();
            if (attached instanceof FeConsumer fe) {
                // Where the device actually is. A connector's attachment follows from its facing; a
                // cable segment touches up to six blocks, so it has to be found by looking.
                final BlockPos devicePos = devicePosOf(node);
                if (devicePos == null) {
                    continue;
                }
                final String name = names.getOrDefault(devicePos, "");
                roster.add(new BranchDevice(devicePos, name, numberFor(devicePos, name, names, nameCounts),
                        legOf(node), fe.priority(), fe.measuredDrainFePerTick(), fe.isLoadShed(),
                        fe.lock(), reasonFor(node.nodeKey(), fe), false));
            } else {
                // [56]
                final BlockPos devicePos = devicePosOf(node);
                if (devicePos == null || attached == null) {
                    continue;
                }
                // [57]
                final int draw = costOf(node).reportedDraw();
                // [58]
                final FePriority tier = FePriority.byOrdinal(priorityOrdinalOf(node));
                final FeLock lock = lockOf(node);
                // [59]
                PduReason infraReason = mode == PduMode.AUTO
                        ? lastReasons.getOrDefault(node.nodeKey(), PduReason.RUNNING)
                        : PduReason.RUNNING;
                // [60]
                if (lock == FeLock.FORCE_OFF) {
                    infraReason = PduReason.LOCKED_OFF;
                } else if (lock == FeLock.FORCE_ON) {
                    infraReason = PduReason.LOCKED_ON;
                }
                final String name = names.getOrDefault(devicePos, "");
                roster.add(new BranchDevice(devicePos, name,
                        numberFor(devicePos, name, names, nameCounts), legOf(node),
                        tier, draw, infraReason.isShed(), lock,
                        infraReason, isLighting(devicePos)));
            }
        }
        roster.sort((a, b) -> Integer.compare(a.priority().ordinal(), b.priority().ordinal()));
    }

    /** [61] */
    private int numberFor(BlockPos pos, String name, Map<BlockPos, String> present,
                          Map<String, Integer> counts) {
        return counts.getOrDefault(name, 0) > 1 ? deviceIds.idFor(pos, name, present) : 0;
    }

    // sync

    /** [62] */
    private int rosterSignature() {
        int h = mode.ordinal();
        h = h * 31 + lastBranch.served();
        h = h * 31 + lastBranch.shed();
        h = h * 31 + (lastSurplus ? 1 : 0);
        h = h * 31 + (lastThroughputLimited ? 1 : 0);
        for (int leg = 0; leg < legClosed.length; leg++) {
            // [63]
            h = h * 31 + (legClosed[leg] ? 1 : 0);
            h = h * 31 + (legTripped[leg] ? 2 : 0);
            // [64]
            h = h * 31 + (leg < com.createstats.power.PduStuds.COUNT ? studLinks.get(leg).size() : 0);
        }
        for (BranchDevice d : roster) {
            h = h * 31 + d.pos().hashCode();
            h = h * 31 + d.priority().ordinal();
            h = h * 31 + (d.shed() ? 1 : 0);
            h = h * 31 + d.lock().ordinal();
            h = h * 31 + d.reason().ordinal();
        }
        return h;
    }

    private void syncIfDue() {
        final int bucket = Math.max(1, bufferCapacity() / 64);
        ticksSinceSync++;
        final int signature = rosterSignature();
        final boolean rosterMoved = signature != lastRosterSignature;
        if (!rosterMoved && Math.abs(energy - lastSyncedEnergy) < bucket && ticksSinceSync < 20) {
            return;
        }
        if (rosterMoved) {
            // [65]
            rebuildTopology();
        }
        lastRosterSignature = signature;
        lastSyncedEnergy = energy;
        ticksSinceSync = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Served", lastBranch.served());
        tag.putInt("Shed", lastBranch.shed());
        tag.putInt("Delivered", lastBranch.delivered());
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Gen", lastGeneration);
        tag.putInt("Dmd", lastDemand);
        tag.putInt("Bud", lastBudget);
        tag.putInt("Svd", lastServedDemand);
        tag.putInt("End", lastEnduranceTicks);
        tag.putBoolean("Srp", lastSurplus);
        tag.putBoolean("Thr", lastThroughputLimited);
        tag.putInt("Ceil", lastCeiling);
        // Server tuning, carried so the client never draws its own unsynced COMMON config.
        tag.putInt("Cap", syncedCapacity);
        tag.putInt("Flr", syncedFloorPct);
        final net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (BranchDevice d : roster) {
            final CompoundTag e = new CompoundTag();
            e.putLong("P", d.pos().asLong());
            e.putString("N", d.name());
            e.putInt("Id", d.id());
            e.putInt("Leg", d.leg());
            e.putInt("T", d.priority().ordinal());
            e.putInt("D", d.draw());
            e.putBoolean("S", d.shed());
            e.putInt("L", d.lock().ordinal());
            e.putInt("R", d.reason().ordinal());
            e.putBoolean("Lt", d.lighting());
            list.add(e);
        }
        tag.put("Roster", list);
        // Per leg state, synced so the panel draws the server's breakers and the server's numbers
        // rather than a client side guess at either.
        final int legCount = LEGS;
        final int[] legState = new int[legCount * 4];
        for (int leg = 0; leg < legCount; leg++) {
            legState[leg * 4] = (legClosed[leg] ? 1 : 0) | (legTripped[leg] ? 2 : 0);
            legState[leg * 4 + 1] = legCeiling(leg);
            legState[leg * 4 + 2] = legDemand(leg);
            legState[leg * 4 + 3] = legAdmitted(leg);
        }
        tag.putIntArray("Legs", legState);
        tag.putInt("WireN", wiring.segments());
        tag.putInt("WireE", wiring.buffered());
        tag.putInt("WireC", wiring.capacity());

        // The wires themselves. Saved because they are the wiring, and carried in the update tag
        // because the client draws them and reads the leg rows off them.
        final net.minecraft.nbt.ListTag spans = new net.minecraft.nbt.ListTag();
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                final CompoundTag e = new CompoundTag();
                e.putByte("S", (byte) stud);
                e.putLong("P", link.to().asLong());
                e.putByte("T", (byte) link.tier().ordinal());
                spans.add(e);
            }
        }
        tag.put("StudWires", spans);

        final net.minecraft.nbt.ListTag topo = new net.minecraft.nbt.ListTag();
        for (PduTopology.BranchNode node : topology) {
            final CompoundTag e = new CompoundTag();
            e.putLong("P", node.pos().asLong());
            e.putByte("K", node.kind());
            e.putByte("T", node.tier());
            e.putBoolean("O", node.open());
            e.putInt("Pa", node.parentIndex());
            topo.add(e);
        }
        tag.put("Topo", topo);
        tag.putBoolean("TopoTrunc", topologyTruncated);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getInt("Energy"));
        lastBranch = new FeGridSolver.BranchResult(
                tag.getInt("Delivered"), tag.getInt("Served"), tag.getInt("Shed"));
        if (tag.contains("Mode")) {
            mode = PduMode.byOrdinal(tag.getInt("Mode"));
        } else {
            // Migrate the old boolean: shedding on was tiered (MANUAL), off was flat (OFF).
            mode = !tag.contains("Shedding") || tag.getBoolean("Shedding") ? PduMode.MANUAL : PduMode.OFF;
        }
        lastGeneration = Math.max(0, tag.getInt("Gen"));
        lastDemand = Math.max(0, tag.getInt("Dmd"));
        lastBudget = Math.max(0, tag.getInt("Bud"));
        lastServedDemand = Math.max(0, tag.getInt("Svd"));
        lastEnduranceTicks = tag.contains("End") ? tag.getInt("End") : Integer.MAX_VALUE;
        lastSurplus = tag.getBoolean("Srp");
        lastThroughputLimited = tag.getBoolean("Thr");
        lastCeiling = Math.max(0, tag.getInt("Ceil"));
        syncedCapacity = Math.max(0, tag.getInt("Cap"));
        syncedFloorPct = tag.contains("Flr") ? Math.max(0, Math.min(90, tag.getInt("Flr"))) : 15;
        roster.clear();
        cachedRollCall = null;
        final net.minecraft.nbt.ListTag list = tag.getList("Roster", 10);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag e = list.getCompound(i);
            roster.add(new BranchDevice(BlockPos.of(e.getLong("P")), e.getString("N"), e.getInt("Id"),
                    e.getInt("Leg"),
                    FePriority.byOrdinal(e.getInt("T")), e.getInt("D"), e.getBoolean("S"),
                    FeLock.byOrdinal(e.getInt("L")), PduReason.byOrdinal(e.getInt("R")),
                    e.getBoolean("Lt")));
        }
        final int[] legState = tag.getIntArray("Legs");
        for (int leg = 0; leg < LEGS; leg++) {
            final int base = leg * 4;
            if (base + 3 >= legState.length) {
                break;
            }
            legClosed[leg] = (legState[base] & 1) != 0;
            legTripped[leg] = (legState[base] & 2) != 0;
            syncedLegCeiling[leg] = legState[base + 1];
            syncedLegDemand[leg] = legState[base + 2];
            syncedLegAdmitted[leg] = legState[base + 3];
        }
        wiring = new BranchWiring(tag.getInt("WireN"), tag.getInt("WireE"), tag.getInt("WireC"));

        for (List<StudLink> links : studLinks) {
            links.clear();
        }
        final net.minecraft.nbt.ListTag spans = tag.getList("StudWires", 10);
        for (int i = 0; i < spans.size(); i++) {
            final CompoundTag e = spans.getCompound(i);
            final int stud = e.getByte("S");
            if (stud < 0 || stud >= com.createstats.power.PduStuds.COUNT) {
                continue;   // a stud that is not one of ours: drop it rather than carry it forward
            }
            studLinks.get(stud).add(new StudLink(BlockPos.of(e.getLong("P")),
                    com.createstats.power.WireTier.byOrdinal(e.getByte("T"))));
        }

        final List<PduTopology.BranchNode> topo = new ArrayList<>();
        final net.minecraft.nbt.ListTag topoList = tag.getList("Topo", 10);
        for (int i = 0; i < topoList.size(); i++) {
            final CompoundTag e = topoList.getCompound(i);
            topo.add(new PduTopology.BranchNode(BlockPos.of(e.getLong("P")), e.getByte("K"), e.getByte("T"),
                    e.getBoolean("O"), e.getInt("Pa")));
        }
        topology = topo;
        topologyTruncated = tag.getBoolean("TopoTrunc");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    // history

    /** [66] */
    private void recordHistory() {
        final long t = level.getGameTime();
        final boolean a1 = t % 4 == 0;
        final boolean a5 = t % 20 == 0;
        final boolean a15 = t % 60 == 0;
        if (!a1 && !a5 && !a15) {
            return;
        }
        final int supply = mode == PduMode.AUTO ? lastGeneration : measuredInput;
        final int demand = mode == PduMode.AUTO ? lastDemand : currentRosterDemand();
        final int shed = lastBranch.shed();
        if (a1) {
            history1m.record(supply, demand, shed);
        }
        if (a5) {
            history5m.record(supply, demand, shed);
        }
        if (a15) {
            history15m.record(supply, demand, shed);
        }
        if (!historySubs.isEmpty()) {
            broadcastHistory(a1, a5, a15);
        }
    }

    /** The total draw of everything on the branch except what the player has switched off. */
    private int currentRosterDemand() {
        int d = 0;
        for (BranchDevice device : roster) {
            if (device.lock() != FeLock.FORCE_OFF) {
                d += device.draw();
            }
        }
        return d;
    }

    /** [67] */
    private void broadcastHistory(boolean a1, boolean a5, boolean a15) {
        final MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        final long now = level.getGameTime();
        final Iterator<Map.Entry<UUID, HistorySub>> it = historySubs.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, HistorySub> entry = it.next();
            final ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            final HistorySub sub = entry.getValue();
            if (player == null || player.level() != level || now - sub.lastSeen > HISTORY_TIMEOUT
                    || player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5) > HISTORY_RANGE_SQ) {
                it.remove();
                continue;
            }
            final boolean advanced = sub.range == 0 ? a1 : sub.range == 1 ? a5 : a15;
            if (advanced) {
                final PduHistory h = historyFor(sub.range);
                final int last = h.count() - 1;
                PacketDistributor.sendToPlayer(player, PduHistoryPacket.delta(worldPosition, sub.range,
                        h.supplyAt(last), h.demandAt(last), h.shedAt(last)));
            }
        }
    }

    private PduHistory historyFor(int range) {
        return range == 0 ? history1m : range == 1 ? history5m : history15m;
    }

    /** Registers a watcher for a range and sends them the full buffer for it now. */
    public void subscribeHistory(ServerPlayer player, int range) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final int r = Mth.clamp(range, 0, 2);
        final HistorySub sub = historySubs.computeIfAbsent(player.getUUID(), k -> new HistorySub());
        sub.range = r;
        sub.lastSeen = level.getGameTime();
        final PduHistory h = historyFor(r);
        PacketDistributor.sendToPlayer(player, PduHistoryPacket.full(worldPosition, r,
                h.supplyChronological(), h.demandChronological(), h.shedChronological()));
    }

    public void keepAliveHistory(UUID id, int range) {
        final HistorySub sub = historySubs.get(id);
        if (sub != null && level != null) {
            sub.range = Mth.clamp(range, 0, 2);
            sub.lastSeen = level.getGameTime();
        }
    }

    public void unsubscribeHistory(UUID id) {
        historySubs.remove(id);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // A removed panel keeps no watchers; without this the map would hold UUIDs the sweep never
        // revisits, because a removed block entity stops ticking.
        historySubs.clear();
    }

    // topology (WIRING diagram)

    /** [68] */
    private void rebuildTopology() {
        if (level == null) {
            return;
        }
        final List<PduTopology.RawNode> raws = new ArrayList<>();
        final Set<BlockPos> added = new HashSet<>();
        final BlockPos root = worldPosition.immutable();
        final List<BlockPos> studWires = new ArrayList<>(wiresOnStuds() + 1);
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                studWires.add(link.to());
            }
        }
        // [69]
        studWires.add(worldPosition.relative(backFace()));
        raws.add(new PduTopology.RawNode(root, (byte) PduTopology.Kind.PDU.ordinal(), (byte) 0, false,
                studWires));
        added.add(root);

        final List<FeWireNetwork> networks = new ArrayList<>();
        for (int leg = 0; leg < LEGS; leg++) {
            for (FeWireNetwork network : legNetworks(leg)) {
                boolean seen = false;
                for (FeWireNetwork other : networks) {
                    if (other == network) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    networks.add(network);
                }
            }
        }
        for (FeWireNetwork network : networks) {
            for (com.createstats.power.FeGridMember member : network.members()) {
                if (!(member instanceof BlockEntity be)) {
                    continue;
                }
                final BlockPos p = be.getBlockPos().immutable();
                final PduTopology.Kind kind;
                byte tier = 0;
                List<BlockPos> links = List.of();
                if (member instanceof PowerConnectorBE connector) {
                    kind = PduTopology.Kind.CONNECTOR;
                    links = new ArrayList<>(connector.links());
                } else if (member instanceof CableBE cableNode) {
                    kind = switch (cableNode.tier()) {
                        case SURFACE -> PduTopology.Kind.CABLE;
                        case TRAY -> PduTopology.Kind.TRAY;
                        case DUCT -> PduTopology.Kind.DUCT;
                    };
                } else if (member instanceof SwitchBE) {
                    kind = PduTopology.Kind.SWITCH;   // members are closed; an open one is added below
                } else {
                    kind = PduTopology.Kind.DEVICE;   // a lamp, a range, any future built-in load
                    tier = deviceTierByte(p);
                }
                if (added.add(p)) {
                    raws.add(new PduTopology.RawNode(p, (byte) kind.ordinal(), tier, false, links));
                }
                // An FeConsumer machine is not a network member; it hangs off its connector.
                if (member instanceof PowerConnectorBE connector) {
                    final BlockPos machine = attachedMachinePos(connector);
                    if (machine != null
                            && level.getBlockEntity(machine) instanceof com.createstats.power.IFeConsumer
                            && added.add(machine)) {
                        raws.add(new PduTopology.RawNode(machine, (byte) PduTopology.Kind.DEVICE.ordinal(),
                                deviceTierByte(machine), false, List.of()));
                    }
                }
                // Open switches touching a member are outside the network; show them as breaks.
                for (Direction dir : Direction.values()) {
                    final BlockPos np = p.relative(dir).immutable();
                    if (level.getBlockEntity(np) instanceof SwitchBE sw && !sw.isClosed() && added.add(np)) {
                        raws.add(new PduTopology.RawNode(np, (byte) PduTopology.Kind.SWITCH.ordinal(),
                                (byte) 0, true, List.of()));
                    }
                }
            }
        }

        final PduTopology.Result result = PduTopology.build(raws, root, PduTopology.MAX_NODES);
        topology = result.nodes();
        topologyTruncated = result.truncated();
    }

    private byte deviceTierByte(BlockPos pos) {
        final DeviceSettings settings = settingsAt(pos);
        return (byte) (settings != null ? settings.priority().ordinal() : FePriority.DEFAULT.ordinal());
    }

    @Nullable
    private BlockPos attachedMachinePos(PowerConnectorBE connector) {
        if (!connector.getBlockState().hasProperty(com.createstats.block.PowerConnectorBlock.FACING)) {
            return null;
        }
        final Direction facing = connector.getBlockState().getValue(com.createstats.block.PowerConnectorBlock.FACING);
        return connector.getBlockPos().relative(facing.getOpposite()).immutable();
    }

    public List<PduTopology.BranchNode> topology() {
        return topology;
    }

    public boolean topologyTruncated() {
        return topologyTruncated;
    }

    /** Goggle readout: mode, buffer, what the branch is doing, and in AUTO the supply picture. */
    public void addPduTooltip(List<Component> tooltip) {
        tooltip.add(Component.literal("    ").append(Component
                .literal("Mode: " + mode.getDisplayName())
                .withStyle(mode == PduMode.AUTO ? ChatFormatting.GREEN
                        : mode == PduMode.MANUAL ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Buffer: %,d / %,d FE", energy, capacity()))
                .withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Branch: %d served, %d shed", lastBranch.served(), lastBranch.shed()))
                .withStyle(lastBranch.shed() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));
        if (mode != PduMode.AUTO) {
            return;
        }
        // In AUTO the interesting number is not the buffer, it is whether generation covers demand,
        // so the supply picture is worth a line without opening the panel.
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Gen %,d FE/t, running %,d of %,d",
                        lastGeneration, lastServedDemand, lastDemand))
                .withStyle(lastDemand > lastGeneration ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));
        tooltip.add(Component.literal("    ").append(Component
                .literal("Reserve: " + rideText())
                .withStyle(lastEnduranceTicks == Integer.MAX_VALUE ? ChatFormatting.GREEN
                        : lastEnduranceTicks < 200 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
    }

    /** How long the spendable reserve lasts at the current net drain, as mm:ss or "stable". */
    private String rideText() {
        if (lastEnduranceTicks == Integer.MAX_VALUE) {
            return "stable, generation covers the running set";
        }
        final int seconds = lastEnduranceTicks / 20;
        return String.format(Locale.ROOT, "%d:%02d remaining", seconds / 60, seconds % 60);
    }

    /** [70] */
    public TierRollCall tierRollCall() {
        // [71]
        if (cachedRollCall != null) {
            return cachedRollCall;
        }
        final int tiers = FePriority.values().length;
        final int[] total = new int[tiers];
        final int[] served = new int[tiers];
        for (BranchDevice device : roster) {
            if (device.lock() == FeLock.FORCE_OFF) {
                // [72]
                continue;
            }
            final int i = device.priority().ordinal();
            total[i]++;
            if (!device.shed()) {
                served[i]++;
            }
        }
        cachedRollCall = new TierRollCall(total, served);
        return cachedRollCall;
    }

    /** Lazily built by {@link #tierRollCall()}, dropped whenever {@link #roster} changes. */
    @Nullable
    private TierRollCall cachedRollCall;

    /**
     * Per-tier device counts for the in-world face.
     *
     * @param total  devices present at each tier
     * @param served how many of those are currently being served, so a partly-shed tier is
     *               distinguishable from a fully shed one
     */
    public record TierRollCall(int[] total, int[] served) {
    }

    // config

    public static int bufferCapacity() {
        try {
            return ModConfiguration.COMMON.pduBufferCapacity.get();
        } catch (RuntimeException ignored) {
            return 512000;
        }
    }

    public static int transferRate() {
        try {
            return ModConfiguration.COMMON.pduTransferRate.get();
        } catch (RuntimeException ignored) {
            return 8192;
        }
    }

    public static int idleDrainPerTick() {
        try {
            return Math.max(0, ModConfiguration.COMMON.pduIdleDrain.get() / 20);
        } catch (RuntimeException ignored) {
            return 51;
        }
    }

    // [73]

    /** Percent of the buffer held back as the CRITICAL ride-through. Server-side read. */
    private static int reserveFloorPct() {
        try {
            return ModConfiguration.COMMON.pduReserveFloorPct.get();
        } catch (RuntimeException ignored) {
            return 15;
        }
    }

    private static float restoreSocFraction() {
        try {
            return ModConfiguration.COMMON.pduRestoreSocPct.get() / 100.0f;
        } catch (RuntimeException ignored) {
            return 0.45f;
        }
    }

    private static float burstMarginFraction() {
        try {
            return ModConfiguration.COMMON.pduBurstMarginPct.get() / 100.0f;
        } catch (RuntimeException ignored) {
            return 0.10f;
        }
    }

    private static int dwellTicks() {
        try {
            return ModConfiguration.COMMON.pduDwellTicks.get();
        } catch (RuntimeException ignored) {
            return 60;
        }
    }

    private static int restoreIntervalTicks() {
        try {
            return ModConfiguration.COMMON.pduRestoreIntervalTicks.get();
        } catch (RuntimeException ignored) {
            return 20;
        }
    }

    private static int rideHorizonTicks() {
        try {
            return Math.max(1, ModConfiguration.COMMON.pduRideHorizonTicks.get());
        } catch (RuntimeException ignored) {
            return 600;
        }
    }

    private static int supplyReadIntervalTicks() {
        try {
            return Math.max(1, ModConfiguration.COMMON.pduSupplyReadIntervalTicks.get());
        } catch (RuntimeException ignored) {
            return 10;
        }
    }

    private static float observedDecay() {
        try {
            return ModConfiguration.COMMON.pduObservedDecayPct.get() / 100.0f;
        } catch (RuntimeException ignored) {
            return 0.98f;
        }
    }
}
