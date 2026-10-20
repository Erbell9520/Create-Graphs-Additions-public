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

class LimitedBranchFlowTest {
    private static final int BUS_DUCT_RATE = 16_384;

    private static final int LIMITER_BUFFER = 8_000;

    private static final int SEGMENT_BUFFER = 25;

    private static final class LimiterGland implements IEnergyStorage {
        private final LimiterBudget budget = new LimiterBudget(LIMITER_BUFFER);
        private final int cap;

        LimiterGland(int cap) {
            this.cap = cap;
        }

        void openTick(long now) {
            budget.roll(now);

            budget.bookIntake(budget.intakeRoom(BUS_DUCT_RATE, cap));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int available = budget.outflowAvailable(maxExtract, cap);
            if (!simulate) {
                budget.bookOutflow(available);
            }
            return available;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return budget.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return LIMITER_BUFFER;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    private static final class Sink implements IEnergyStorage {
        boolean hungry = true;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return hungry ? Math.max(0, maxReceive) : 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private static final class Node implements FeGridNode {
        private final long key;
        private final int capacity;
        @Nullable
        private final IEnergyStorage attached;
        private int stored;

        Node(long key, int capacity, @Nullable IEnergyStorage attached) {
            this.key = key;
            this.capacity = capacity;
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
            stored = Math.max(0, Math.min(capacity, amount));
        }

        @Override
        public int bufferCapacity() {
            return capacity;
        }

        @Override
        public int transferRate() {
            return BUS_DUCT_RATE;
        }

        @Override
        @Nullable
        public IEnergyStorage attachedStorage() {
            return attached;
        }
    }

    private static final class Branch {
        static final long LIMITER = 900L;
        static final long MACHINE = 901L;
        static final long[] DUCT = {1L, 2L, 3L, 4L, 5L};

        static final long CONNECTOR = 6L;
        static final int CONNECTOR_BUFFER = 16_000;

        final LimiterGland gland;
        final Node limiter;
        final Sink sink = new Sink();
        final Node machine;
        final List<Node> duct;
        final Node connector = new Node(CONNECTOR, CONNECTOR_BUFFER, null);
        final FeFlowGraph graph;
        final Map<Long, Integer> flows = new HashMap<>();

        Branch(int cap) {
            gland = new LimiterGland(cap);
            limiter = new Node(LIMITER, 0, gland);
            machine = new Node(MACHINE, 0, sink);
            duct = List.of(new Node(DUCT[0], SEGMENT_BUFFER, null),
                    new Node(DUCT[1], SEGMENT_BUFFER, null),
                    new Node(DUCT[2], SEGMENT_BUFFER, null),
                    new Node(DUCT[3], SEGMENT_BUFFER, null),
                    new Node(DUCT[4], SEGMENT_BUFFER, null));

            final Map<Long, Long> parentOf = new LinkedHashMap<>();
            parentOf.put(DUCT[0], DUCT[0]);
            for (int i = 1; i < DUCT.length; i++) {
                parentOf.put(DUCT[i], DUCT[i - 1]);
            }
            parentOf.put(CONNECTOR, DUCT[DUCT.length - 1]);
            graph = FeFlowGraph.of(parentOf, Map.of(LIMITER, DUCT[0], MACHINE, CONNECTOR));
        }

        List<FeGridNode> nodes() {
            final List<FeGridNode> out = new java.util.ArrayList<>();
            out.add(limiter);
            out.addAll(duct);
            out.add(connector);
            out.add(machine);
            return out;
        }

        FeGridSolver.Result tick(long now, int ceiling) {
            gland.openTick(now);
            flows.clear();
            final FeGridSolver.Result result = FeGridSolver.solve(nodes(), (int) now, flows, true,
                    supplyBudget.remaining(ceiling, now));
            supplyBudget.spend(ceiling, now,
                    Math.max(result.collected(), result.delivered()) + result.charged());
            graph.recompute(flows, Map.of(), now);
            return result;
        }

        final FeSupplyBudget supplyBudget = new FeSupplyBudget();
    }

    @Test
    @DisplayName("no segment past the limiter reports carrying more than the cap")
    void theRunPastALimiterNeverClaimsMoreThanTheCap() {
        final int cap = 1_024;
        final Branch branch = new Branch(cap);

        int worstSegment = 0;
        int worstCircuit = 0;
        for (long now = 1L; now <= 200L; now++) {
            final FeGridSolver.Result result = branch.tick(now, cap);

            worstCircuit = Math.max(worstCircuit, Math.max(0, result.consumed()));
            for (long segment : Branch.DUCT) {
                final int through = branch.graph.flowThrough(segment, now);
                if (through != FeFlowGraph.UNKNOWN) {
                    worstSegment = Math.max(worstSegment, through);
                }
            }
        }

        assertTrue(worstSegment <= cap,
                "a Bus Duct on a branch capped at " + cap + " FE/t reported carrying "
                        + worstSegment + " FE/t");
        assertTrue(worstCircuit <= cap,
                "the circuit total behind the fallback reading was " + worstCircuit
                        + " FE/t on a branch capped at " + cap + " FE/t");
    }

    private static int burstAfterIdle(int cap, int ceiling) {
        final Branch branch = new Branch(cap);
        int worst = 0;
        long now = 0L;

        for (int i = 0; i < 240; i++) {
            branch.sink.hungry = false;
            branch.tick(++now, ceiling);
        }
        branch.sink.hungry = true;
        for (int i = 0; i < 20; i++) {
            branch.tick(++now, ceiling);
            for (long segment : Branch.DUCT) {
                final int through = branch.graph.flowThrough(segment, now);
                if (through != FeFlowGraph.UNKNOWN) {
                    worst = Math.max(worst, through);
                }
            }
        }
        return worst;
    }

    @Test
    @DisplayName("without a supply ceiling a duty-cycled branch dumps its bank at the cable's rating")
    void aDutyCycledMachineUsedToMakeTheRunReportFarMoreThanTheCap() {
        final int cap = 1_024;
        final int burst = burstAfterIdle(cap, FeGridSolver.NO_CEILING);

        assertTrue(burst > cap, "expected the unfixed branch to overshoot, saw " + burst);

        assertEquals(BUS_DUCT_RATE, burst,
                "the overshoot is the cable grade's rating, not some function of the setting");
    }

    @Test
    @DisplayName("the supply ceiling holds the branch to the setting through the burst")
    void theSupplyCeilingHoldsABankedBranchToTheCap() {
        final int cap = 1_024;
        final int burst = burstAfterIdle(cap, cap);

        assertTrue(burst <= cap,
                "a Bus Duct on a branch capped at " + cap + " FE/t reported carrying "
                        + burst + " FE/t after the machine came back on load");
    }

    @Test
    @DisplayName("the ceiling costs a steadily loaded branch nothing")
    void aBranchAlreadyRunningAtTheCapIsUnaffected() {
        final int cap = 1_024;
        final Branch with = new Branch(cap);
        final Branch without = new Branch(cap);
        int withTotal = 0;
        int withoutTotal = 0;
        for (long now = 1L; now <= 200L; now++) {
            withTotal += with.tick(now, cap).delivered();
            withoutTotal += without.tick(now, FeGridSolver.NO_CEILING).delivered();
        }

        assertEquals(withoutTotal, withTotal,
                "a branch that was never banking delivers the same with the ceiling as without it");
        assertTrue(withTotal >= cap * 190,
                "and it really is running at the cap, not merely running identically badly: "
                        + withTotal);
    }
}
