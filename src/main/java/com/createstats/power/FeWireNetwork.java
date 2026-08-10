package com.createstats.power;

import com.createstats.block.CableBlock;
import com.createstats.blockentity.CableBE;
import com.createstats.blockentity.SwitchBE;
import com.createstats.blockentity.PowerConnectorBE;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** [1] */
public final class FeWireNetwork {

    private final List<FeGridMember> nodes;
    private boolean dirty;
    private long lastSolvedTick = Long.MIN_VALUE;
    /** Rotates the fair-share starting point so the same sink is not always served last. */
    private int distributionCursor;
    private FeGridSolver.Result lastResult = FeGridSolver.Result.EMPTY;
    /** Ticks a PDU claim survives without renewal, so a broken PDU releases its branch. */
    private static final int MANAGED_GRACE_TICKS = 5;
    private long managedUntilTick = Long.MIN_VALUE;
    /** Reused every solve so instrumenting flow costs no per-tick allocation once warmed up. */
    private final Map<Long, Integer> flowByNode = new HashMap<>();

    private FeWireNetwork(List<FeGridMember> nodes) {
        this.nodes = nodes;
    }

    /** [2] */
    public static FeWireNetwork buildFrom(PowerConnectorBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** Builds the network containing a cable segment. */
    public static FeWireNetwork buildFrom(CableBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** Builds the network containing a switch. Only ever called while its contacts are closed. */
    public static FeWireNetwork buildFrom(SwitchBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** Builds the network containing a range, which banks with every range within one block. */
    public static FeWireNetwork buildFrom(com.createstats.blockentity.InductionRangeBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** Builds the network containing a lamp, which conducts to the lamps touching it. */
    public static FeWireNetwork buildFrom(com.createstats.blockentity.LampBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** Builds the network containing an FE Fluid Pump, which is fed through its connector side. */
    public static FeWireNetwork buildFrom(com.createstats.blockentity.FluidPumpBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** Builds the network containing an FE Motor, fed through its built-in connector like the pump. */
    public static FeWireNetwork buildFrom(com.createstats.blockentity.FeMotorBE origin) {
        return build(origin.getLevel(), origin);
    }

    /** [3] */
    private static FeWireNetwork build(Level level, Object origin) {
        final List<FeGridMember> found = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        // Keyed by machine position, so a machine touched by several segments of the same run is
        // served exactly once per tick rather than once per segment touching it.
        final Map<BlockPos, FeAttachment> attachments = new HashMap<>();
        final Deque<Object> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(posOf(origin).immutable());

        while (!queue.isEmpty()) {
            final Object node = queue.poll();
            if (node instanceof PowerConnectorBE connector) {
                found.add(connector);
                // [4]
                walkTerminalLinks(connector, level, seen, queue);
                enqueueTouchingCable(level, connector.getBlockPos(), seen, queue);
            } else if (node instanceof SwitchBE sw) {
                // [5]
                if (!sw.isClosed()) {
                    continue;
                }
                found.add(sw);
                // A closed switch bridges the wiring on both sides, cable included, so a run that
                // meets a switch is one grid closed and two open.
                spreadFromBlock(level, sw.getBlockPos(), seen, queue, true);
            } else if (node instanceof CableBE cable) {
                found.add(cable);
                spreadAlongRun(level, cable.getBlockPos(), seen, queue);
                // Not cable here: a run continues by its own geometry (spreadAlongRun), never by bare
                // face adjacency, or two runs passing beside each other would fuse into one grid.
                spreadFromBlock(level, cable.getBlockPos(), seen, queue, false);
                collectAttachments(level, cable, attachments);
            } else if (node instanceof com.createstats.blockentity.InductionRangeBE range) {
                found.add(range);
                spreadFromBlock(level, range.getBlockPos(), seen, queue, true);
                spreadToBank(level, range.getBlockPos(), seen, queue);
            } else if (node instanceof com.createstats.blockentity.LampBE lamp) {
                // [6]
                found.add(lamp);
                spreadFromBlock(level, lamp.getBlockPos(), seen, queue, true);
            } else if (node instanceof com.createstats.blockentity.FluidPumpBE pump) {
                // [7]
                found.add(pump);
                spreadFromBlock(level, pump.getBlockPos(), seen, queue, true);
                walkTerminalLinks(pump, level, seen, queue);
            } else if (node instanceof com.createstats.blockentity.FeMotorBE motor) {
                // A motor is a load with a built-in connector, exactly like the pump.
                found.add(motor);
                spreadFromBlock(level, motor.getBlockPos(), seen, queue, true);
                walkTerminalLinks(motor, level, seen, queue);
            }
        }

        // [8]
        for (FeGridMember node : found) {
            if (node instanceof PowerConnectorBE connector) {
                attachments.remove(connector.getBlockPos()
                        .relative(connector.mountedOn()));
            }
        }
        found.addAll(attachments.values());

        final FeWireNetwork network = new FeWireNetwork(found);
        for (FeGridMember node : found) {
            if (node instanceof PowerConnectorBE connector) {
                connector.adoptNetwork(network);
            } else if (node instanceof CableBE cable) {
                cable.adoptNetwork(network);
            } else if (node instanceof SwitchBE sw) {
                sw.adoptNetwork(network);
            } else if (node instanceof com.createstats.blockentity.InductionRangeBE range) {
                range.adoptNetwork(network);
            } else if (node instanceof com.createstats.blockentity.LampBE lamp) {
                lamp.adoptNetwork(network);
            } else if (node instanceof com.createstats.blockentity.FluidPumpBE pump) {
                pump.adoptNetwork(network);
            } else if (node instanceof com.createstats.blockentity.FeMotorBE motor) {
                motor.adoptNetwork(network);
            }
        }
        return network;
    }

    /** [9] */
    private static void collectAttachments(Level level, CableBE cable,
                                           Map<BlockPos, FeAttachment> out) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos neighbour = cable.getBlockPos().relative(dir);
            if (!level.isLoaded(neighbour) || out.containsKey(neighbour)) {
                continue;
            }
            if (level.getBlockEntity(neighbour) instanceof FeGridMember) {
                continue;
            }
            final net.neoforged.neoforge.energy.IEnergyStorage storage = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    neighbour, dir.getOpposite());
            if (storage != null) {
                out.put(neighbour.immutable(),
                        new FeAttachment(neighbour.immutable(), storage, cable.transferRate()));
            }
        }
    }

    /** Adds any cable or switch touching a connector, so a run can start or end at one. */
    private static void enqueueTouchingCable(Level level, BlockPos at, Set<BlockPos> seen,
                                             Deque<Object> queue) {
        spreadFromBlock(level, at, seen, queue, true);
    }

    /** [10] */
    private static void walkTerminalLinks(FeWireTerminal terminal, Level level, Set<BlockPos> seen,
                                          Deque<Object> queue) {
        for (BlockPos link : new ArrayList<>(terminal.links())) {
            if (level == null || !level.isLoaded(link)) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity linkBE = level.getBlockEntity(link);
            if (linkBE instanceof com.createstats.blockentity.PowerDistributionUnitBE) {
                continue;
            }
            if (linkBE instanceof FeWireTerminal) {
                if (seen.add(link.immutable())) {
                    queue.add(linkBE);
                }
                continue;
            }
            terminal.removeDeadLink(link);
        }
    }

    /** [11] */
    private static void spreadFromBlock(Level level, BlockPos at, Set<BlockPos> seen,
                                        Deque<Object> queue, boolean includeCable) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos neighbour = at.relative(dir);
            if (!level.isLoaded(neighbour) || seen.contains(neighbour)) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(neighbour);
            if (be instanceof PowerConnectorBE || be instanceof SwitchBE
                    || be instanceof com.createstats.blockentity.LampBE
                    || be instanceof com.createstats.blockentity.InductionRangeBE
                    || be instanceof com.createstats.blockentity.FluidPumpBE
                    || be instanceof com.createstats.blockentity.FeMotorBE
                    || (includeCable && be instanceof CableBE)) {
                seen.add(neighbour.immutable());
                queue.add(be);
            }
        }
    }

    /** [12] */
    private static void spreadAlongRun(Level level, BlockPos at, Set<BlockPos> seen,
                                       Deque<Object> queue) {
        if (level == null) {
            return;
        }
        for (BlockPos next : CableLinks.continuations(CableBlock.linkViewOf(level), at)) {
            if (!level.isLoaded(next) || seen.contains(next)) {
                continue;
            }
            if (level.getBlockEntity(next) instanceof CableBE cable) {
                seen.add(next.immutable());
                queue.add(cable);
            }
        }
    }

    /** [13] */
    private static void spreadToBank(Level level, BlockPos at, Set<BlockPos> seen,
                                     Deque<Object> queue) {
        if (level == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    final BlockPos near = at.offset(dx, dy, dz);
                    if (!level.isLoaded(near) || seen.contains(near)) {
                        continue;
                    }
                    if (level.getBlockEntity(near)
                            instanceof com.createstats.blockentity.InductionRangeBE other) {
                        seen.add(near.immutable());
                        queue.add(other);
                    }
                }
            }
        }
    }

    private static BlockPos posOf(Object node) {
        return ((net.minecraft.world.level.block.entity.BlockEntity) node).getBlockPos();
    }

    /** Forces every member to rebuild on its next tick. Called whenever links or nodes change. */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Number of connectors on this grid. */
    public int size() {
        return nodes.size();
    }

    /** What the last solve moved: collected, delivered, stored and total capacity. */
    public FeGridSolver.Result lastResult() {
        return lastResult;
    }

    /** [14] */
    public void markPduManaged(long gameTime) {
        this.managedUntilTick = gameTime + MANAGED_GRACE_TICKS;
    }

    public boolean isPduManaged(long gameTime) {
        return gameTime <= managedUntilTick;
    }

    /** Live members, so a PDU can serve them itself. */
    public List<FeGridMember> members() {
        return nodes;
    }

    /** [15] */
    public void solveOnce(Level level) {
        if (dirty || level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        if (isPduManaged(now)) {
            // The PDU owns distribution on this branch this tick, so nothing flows through the flat
            // grid: hold every member's goggle readout at zero rather than a stale pre-PDU value.
            for (FeGridMember node : nodes) {
                node.setLastFlow(0);
            }
            return;
        }
        if (now == lastSolvedTick) {
            return;
        }
        lastSolvedTick = now;

        // Drop any node removed since the last rebuild. If that empties the grid, force a rebuild
        // rather than solving over nothing.
        nodes.removeIf(node -> node.isGone(level));
        if (nodes.isEmpty()) {
            dirty = true;
            lastResult = FeGridSolver.Result.EMPTY;
            return;
        }
        flowByNode.clear();
        lastResult = FeGridSolver.solve(nodes, distributionCursor++, flowByNode);
        // Every node gets an explicit value, including the ones that moved nothing, so a connector
        // that goes idle reads 0 immediately rather than holding its last nonzero flow forever.
        for (FeGridMember node : nodes) {
            node.setLastFlow(flowByNode.getOrDefault(node.nodeKey(), 0));
        }
    }
}
