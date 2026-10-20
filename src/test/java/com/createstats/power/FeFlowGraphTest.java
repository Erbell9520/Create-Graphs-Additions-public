package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeFlowGraphTest {
    private static final long NOW = 1_000L;

    private static FeFlowGraph tree(Map<Long, Long> attachments, long... childParent) {
        final Map<Long, Long> parentOf = new LinkedHashMap<>();
        for (int i = 0; i < childParent.length; i += 2) {
            parentOf.put(childParent[i], childParent[i + 1]);
        }
        return FeFlowGraph.of(parentOf, attachments);
    }

    private static Map<Long, Integer> flows(Object... keyValue) {
        final Map<Long, Integer> out = new HashMap<>();
        for (int i = 0; i < keyValue.length; i += 2) {
            out.put(((Number) keyValue[i]).longValue(), ((Number) keyValue[i + 1]).intValue());
        }
        return out;
    }

    @Test
    @DisplayName("a straight run carries the load's draw at every segment between the two ends")
    void aStraightRunCarriesTheSameFigureEndToEnd() {
        final FeFlowGraph graph = tree(Map.of(90L, 4L), 1, 1, 2, 1, 3, 2, 4, 3);
        graph.recompute(flows(1, -600, 90L, 600), Map.of(), NOW);

        assertEquals(600, graph.flowThrough(1L, NOW));
        assertEquals(600, graph.flowThrough(2L, NOW));
        assertEquals(600, graph.flowThrough(3L, NOW));
        assertEquals(600, graph.flowThrough(4L, NOW), "the segment the machine hangs off carries it too");
    }

    @Test
    @DisplayName("the stub reports its own draw, not the circuit's, which is the whole bug")
    void stubReportsItsOwnDrawNotTheCircuits() {
        final FeFlowGraph graph = tree(Map.of(20L, 6L, 21L, 4L),
                1, 1, 2, 1, 3, 2, 4, 3, 5, 2, 6, 5);
        graph.recompute(flows(1, -1800, 20L, 300, 21L, 1500), Map.of(), NOW);

        assertEquals(1800, graph.flowThrough(1L, NOW));
        assertEquals(1800, graph.flowThrough(2L, NOW), "the tee sees everything downstream of it");

        assertEquals(1500, graph.flowThrough(3L, NOW));
        assertEquals(1500, graph.flowThrough(4L, NOW));

        assertEquals(300, graph.flowThrough(5L, NOW));
        assertEquals(300, graph.flowThrough(6L, NOW));
    }

    @Test
    @DisplayName("a tee carries what goes in, not the sum of what comes out")
    void aTeeCarriesTheLargerSide() {
        final FeFlowGraph graph = tree(Map.of(30L, 3L, 40L, 4L), 1, 1, 2, 1, 3, 2, 4, 2);
        graph.recompute(flows(1, -100, 30L, 50, 40L, 50), Map.of(), NOW);

        assertEquals(100, graph.flowThrough(2L, NOW), "100 in, 50 + 50 out: the block carried 100");
        assertEquals(50, graph.flowThrough(3L, NOW));
        assertEquals(50, graph.flowThrough(4L, NOW));
    }

    @Test
    @DisplayName("a generator partway along a run only relieves the wiring beyond it")
    void aGeneratorPartwayAlongOnlyFeedsWhatIsBeyondIt() {
        final FeFlowGraph graph = tree(Map.of(90L, 3L), 1, 1, 2, 1, 3, 2);
        graph.recompute(flows(2, -40, 90L, 100), Map.of(), NOW);

        assertEquals(100, graph.flowThrough(3L, NOW), "the far segment still carries the whole draw");
        assertEquals(100, graph.flowThrough(2L, NOW), "and so does the block the generator is on");
        assertEquals(60, graph.flowThrough(1L, NOW), "but only the shortfall crosses the wire above it");
    }

    @Test
    @DisplayName("a segment with the supply on one side and the load on the other carries, not cancels")
    void aSupplyAndALoadOnOneSegmentDoNotCancel() {
        final FeFlowGraph graph = tree(Map.of(80L, 1L, 90L, 1L), 1, 1, 2, 1);
        graph.recompute(flows(80L, -450, 90L, 450), Map.of(), NOW);

        assertEquals(450, graph.flowThrough(1L, NOW), "450 in and 450 out crossed this block");
        assertEquals(0, graph.flowThrough(2L, NOW), "and nothing at all reached the stub beyond it");
    }

    @Test
    @DisplayName("a lone segment between a meter and a machine reads what crossed it")
    void aLoneSegmentBetweenTwoAttachmentsReadsIt() {
        final FeFlowGraph graph = tree(Map.of(80L, 1L, 90L, 1L), 1, 1);
        graph.recompute(flows(80L, -450, 90L, 450), Map.of(), NOW);

        assertEquals(450, graph.flowThrough(1L, NOW));
    }

    @Test
    @DisplayName("a tee whose supply sits on the tee reports everything it hands out, not its largest branch")
    void aTeeFedAtTheTeeCarriesTheWhole() {
        final FeFlowGraph graph = tree(Map.of(80L, 1L, 30L, 2L, 40L, 3L), 1, 1, 2, 1, 3, 1);
        graph.recompute(flows(80L, -600, 30L, 300, 40L, 300), Map.of(), NOW);

        assertEquals(600, graph.flowThrough(1L, NOW));
        assertEquals(300, graph.flowThrough(2L, NOW));
        assertEquals(300, graph.flowThrough(3L, NOW));
    }

    @Test
    @DisplayName("a segment's own traffic is the larger of its two directions, never their sum")
    void aSegmentsOwnTrafficIsNotDoubleCounted() {
        final FeFlowGraph graph = tree(Map.of(80L, 1L, 90L, 1L), 1, 1);
        graph.recompute(flows(80L, -500, 90L, 200), Map.of(), NOW);

        assertEquals(500, graph.flowThrough(1L, NOW));
    }

    @Test
    @DisplayName("an idle circuit reads a real zero rather than falling back to the circuit total")
    void idleCircuitReadsZero() {
        final FeFlowGraph graph = tree(Map.of(), 1, 1, 2, 1);
        graph.recompute(Map.of(), Map.of(), NOW);

        assertEquals(0, graph.flowThrough(1L, NOW));
        assertEquals(0, graph.flowThrough(2L, NOW));
    }

    @Test
    @DisplayName("a graph that has never run, or has gone stale, says so instead of printing zero")
    void staleAndUnbuiltGraphsAnswerUnknown() {
        final FeFlowGraph fresh = tree(Map.of(), 1, 1);
        assertEquals(FeFlowGraph.UNKNOWN, fresh.flowThrough(1L, NOW),
                "never computed: the caller has to fall back, not print a confident nought");

        fresh.recompute(flows(1, 500), Map.of(), NOW);
        assertEquals(500, fresh.flowThrough(1L, NOW));
        assertEquals(500, fresh.flowThrough(1L, NOW + 1), "one tick of slack, for tick-order jitter");
        assertEquals(FeFlowGraph.UNKNOWN, fresh.flowThrough(1L, NOW + 2), "older than that is not a reading");
        assertEquals(FeFlowGraph.UNKNOWN, fresh.flowThrough(99L, NOW), "a node this grid never had");
    }

    @Test
    @DisplayName("a lone node reports what its own block moved, having no edge to measure")
    void aLoneNodeReportsItsOwnTransfer() {
        final FeFlowGraph graph = tree(Map.of(), 7, 7);
        graph.recompute(flows(7, -320), Map.of(), NOW);
        assertEquals(320, graph.flowThrough(7L, NOW));
    }

    @Test
    @DisplayName("a managed leg with one machine on it needs its supply edge declared, or it reads zero")
    void managedLegNeedsItsBoundaryEdge() {
        final FeFlowGraph graph = tree(Map.of(), 5, 5);
        graph.recompute(flows(5, 0), Map.of(), NOW);
        assertEquals(0, graph.flowThrough(5L, NOW), "without the boundary there is nothing to measure");

        graph.recompute(flows(5, 0), Map.of(), 5L, 100, NOW);
        assertEquals(100, graph.flowThrough(5L, NOW), "the panel's wire is real and carries the leg");
    }

    @Test
    @DisplayName("a managed Power Meter input grid needs the meter as its load seed")
    void managedMeterInputGridNeedsTheMeterSeed() {
        final FeFlowGraph graph = tree(Map.of(80L, 3L), 1, 1, 2, 1, 3, 2);
        graph.recompute(Map.of(), Map.of(), 1L, 450, NOW);
        assertEquals(0, graph.flowThrough(3L, NOW), "the boundary alone only lights the anchor");

        graph.recompute(flows(80L, 450), Map.of(), 1L, 450, NOW);
        assertEquals(450, graph.flowThrough(1L, NOW));
        assertEquals(450, graph.flowThrough(2L, NOW));
        assertEquals(450, graph.flowThrough(3L, NOW));
    }

    @Test
    @DisplayName("the panel's supply anchor puts the flow between the panel and the machine, not the flood's root")
    void managedFlowRunsFromTheAnchor() {
        final FeFlowGraph graph = tree(Map.of(), 1, 1, 2, 1, 3, 2, 4, 3, 5, 4);

        graph.recompute(flows(5, 200, 3, -200), Map.of(), 3L, 200, NOW);

        assertEquals(0, graph.flowThrough(1L, NOW), "nothing crosses the wiring above the anchor");
        assertEquals(0, graph.flowThrough(2L, NOW));
        assertEquals(200, graph.flowThrough(3L, NOW));
        assertEquals(200, graph.flowThrough(4L, NOW));
        assertEquals(200, graph.flowThrough(5L, NOW));
    }

    @Test
    @DisplayName("FE crossing the seam seeds the segment that carried it, not the grid at large")
    void seamFlowLandsOnTheSegmentThatCarriedIt() {
        final FeFlowGraph graph = tree(Map.of(), 1, 1, 2, 1, 3, 2);
        graph.recompute(flows(1, -400), flows(3, 400), NOW);

        assertEquals(400, graph.flowThrough(3L, NOW));
        assertEquals(400, graph.flowThrough(2L, NOW));
        assertEquals(400, graph.flowThrough(1L, NOW));
    }

    @Test
    @DisplayName("the solver's own flow map drives it end to end, with no figure above the wire's rating")
    void endToEndAgainstARealSolve() {
        final Store supply = Store.source(100_000);
        final Store load = Store.machine(3_000);
        final Node source = new Node(1L, supply);
        final Node cableA = new Node(2L, null);
        final Node junction = new Node(3L, null);
        final Node cableB = new Node(4L, null);
        final Node machine = new Node(5L, load);
        final List<Node> grid = List.of(source, cableA, junction, cableB, machine);
        final long[] keys = {1L, 2L, 3L, 4L, 5L};
        final int rate = 4_096;

        final Map<Long, Integer> solved = new HashMap<>();
        final FeGridSolver.Result first = FeGridSolver.solve(grid, 0, solved);
        assertTrue(first.delivered() > 0, "the machine has to have been served for this to mean anything");

        assertTrue(solved.containsKey(1L) && solved.containsKey(5L));
        assertTrue(!solved.containsKey(2L) && !solved.containsKey(3L) && !solved.containsKey(4L));

        final FeFlowGraph graph = tree(Map.of(), 1, 1, 2, 1, 3, 2, 4, 3, 5, 4);
        graph.recompute(solved, Map.of(), NOW);

        for (long key : new long[] {2L, 3L, 4L, 5L}) {
            assertEquals(first.delivered(), graph.flowThrough(key, NOW),
                    "every block between the source and the machine carries what the machine took");
        }

        final int intoBuffers = 25 * grid.size();
        assertEquals(first.delivered() + intoBuffers, graph.flowThrough(1L, NOW));

        load.energy = 0;
        solved.clear();
        final FeGridSolver.Result steady = FeGridSolver.solve(grid, 1, solved);
        graph.recompute(solved, Map.of(), NOW + 1);

        for (long key : keys) {
            assertEquals(steady.delivered(), graph.flowThrough(key, NOW + 1),
                    "in the steady state the whole run carries one figure");
            assertTrue(graph.flowThrough(key, NOW + 1) <= rate,
                    "and no block ever reports above what it is rated to carry");
        }
    }

    private static final class Store implements IEnergyStorage {
        int energy;
        final int capacity;
        final boolean extractable;
        final boolean receivable;

        private Store(int energy, int capacity, boolean extractable, boolean receivable) {
            this.energy = energy;
            this.capacity = capacity;
            this.extractable = extractable;
            this.receivable = receivable;
        }

        static Store source(int energy) {
            return new Store(energy, energy, true, false);
        }

        static Store machine(int capacity) {
            return new Store(0, capacity, false, true);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int accepted = Math.max(0, Math.min(maxReceive, capacity - energy));
            if (!receivable || accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int given = Math.max(0, Math.min(maxExtract, energy));
            if (!extractable || given <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= given;
            }
            return given;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return extractable;
        }

        @Override
        public boolean canReceive() {
            return receivable;
        }
    }

    private static final class Node implements FeGridNode {
        private final long key;
        @Nullable
        private final Store attached;
        private int stored;

        Node(long key, @Nullable Store attached) {
            this.key = key;
            this.attached = attached;
        }

        @Override
        public long nodeKey() {
            return key;
        }

        @Override
        public int storedEnergy() {
            return stored;
        }

        @Override
        public void setStoredEnergy(int amount) {
            stored = amount;
        }

        @Override
        public int bufferCapacity() {
            return 25;
        }

        @Override
        public int transferRate() {
            return 4_096;
        }

        @Override
        @Nullable
        public IEnergyStorage attachedStorage() {
            return attached;
        }
    }
}
