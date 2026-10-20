package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationNeverConduitTest {
    private static final int BASE = 8_192;
    private static final int HV = BASE * WireTier.HV.multiplier();
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final long TICK = 3L;

    private static long key(int index) {
        return 9_000L + index;
    }

    private static long[] keys(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = key(i);
        }
        return out;
    }

    private static Map<Long, Long> parentOf(int[] parent) {
        final Map<Long, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < parent.length; i++) {
            out.put(key(i), parent[i] < 0 ? key(i) : key(parent[i]));
        }
        return out;
    }

    private static final class Source implements IEnergyStorage {
        long given;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int out = Math.max(0, maxExtract);
            if (!simulate) {
                given += out;
            }
            return out;
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE / 2;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
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

    private static final class Machine implements IEnergyStorage {
        final int appetite;
        long taken;

        Machine(int appetite) {
            this.appetite = appetite;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int take = (int) Math.max(0, Math.min(maxReceive, appetite - taken));
            if (!simulate) {
                taken += take;
            }
            return take;
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
            return appetite;
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

    private record Node(long key, IEnergyStorage attached, int rate, FePathBudget budget) implements FeGridNode {
        @Override
        public long nodeKey() {
            return key;
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

        @Override
        public int transferRate() {
            return rate;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return attached;
        }

        @Override
        public int deliveryAllowance() {
            return budget.remaining(key, TICK);
        }

        @Override
        public void bookDelivery(int fe) {
            budget.spend(key, fe, TICK);
        }
    }

    @Test
    @DisplayName("before, and still for a machine that has not opted in: two plants join through it, and FE crosses it to the other side")
    void aWalkedThroughTerminalIsConduit() {
        final int[] parent = {-1, 0, 0, 1, 2, 2};
        final int[] rate = {NONE, HV, HV, NONE, NONE, NONE};
        final FePathBudget budget = new FePathBudget(keys(6), parent, rate,
                new boolean[] {false, false, false, true, true, false});
        final Source one = new Source();
        final Source two = new Source();
        final Machine load = new Machine(30_000);
        final List<FeGridNode> nodes = new ArrayList<>();
        nodes.add(new Node(key(3), one, 100_000, budget));
        nodes.add(new Node(key(4), two, 0, budget));
        nodes.add(new Node(key(5), load, 30_000, budget));
        final Map<Long, Integer> flows = new java.util.HashMap<>();
        FeGridSolver.solve(nodes, 0, flows, false, NONE);
        assertEquals(30_000, load.taken, "the load on plant two's side is served");
        assertEquals(30_000, one.given, "entirely out of plant one");

        final FeFlowGraph graph = FeFlowGraph.of(parentOf(parent), Map.of());
        graph.recompute(flows, Map.of(), TICK);
        assertEquals(30_000, graph.edgeFlow(key(1), key(0), TICK), "into the machine on stud A");
        assertEquals(30_000, graph.edgeFlow(key(2), key(0), TICK), "and out of it on stud B: the machine is conduit");
    }

    @Test
    @DisplayName("now: two plants are two circuits, and a load on plant two's side is fed by plant two alone")
    void twoGridsStayTwo() {
        final FePathBudget one = new FePathBudget(keys(3), new int[] {-1, 0, 1}, new int[] {NONE, NONE, HV},
                new boolean[] {true, false, false}, null, List.of());
        final FePathBudget two = new FePathBudget(keys(4), new int[] {-1, 0, 1, 1}, new int[] {NONE, NONE, HV, NONE},
                new boolean[] {true, false, false, false}, null, List.of());
        final Source plantOne = new Source();
        final Source plantTwo = new Source();
        final Machine station = new Machine(Integer.MAX_VALUE / 4);
        final Machine load = new Machine(30_000);
        FeGridSolver.solve(List.of(new Node(key(0), plantOne, 100_000, one), new Node(key(2), station, 200_000, one)),
                0, null, false, NONE);
        FeGridSolver.solve(List.of(new Node(key(0), plantTwo, 0, two), new Node(key(2), station, 200_000, two),
                new Node(key(3), load, 30_000, two)), 0, null, false, NONE);
        assertEquals(0, load.taken, "plant two is dark, and plant one's FE never reaches its side");
        assertEquals(HV, station.taken, "the station takes plant one's FE down its own line and no further");
        assertEquals(HV, plantOne.given);
    }

    @Test
    @DisplayName("the ring: the old tree charged a machine on connector B across both spans and through the station; the new one never does")
    void theRingNoLongerRoutesThroughTheStation() {
        final FePathBudget old = new FePathBudget(keys(4), new int[] {-1, 0, 0, 1}, new int[] {NONE, HV, HV, NONE},
                new boolean[] {false, false, false, true});
        old.spend(key(0), HV, TICK);
        assertEquals(0, old.remaining(key(2), TICK),
                "the old shape: connector B's own machine starved by span A, which its FE never crosses");

        final FePathBudget now = new FePathBudget(keys(4), new int[] {-1, 0, 1, 2}, new int[] {NONE, HV, NONE, NONE},
                new boolean[] {false, false, true, false}, null,
                List.of(new FeFeeds(key(0), new long[] {key(3)}, new int[] {HV})));
        now.spend(key(0), HV, TICK);
        assertEquals(NONE, now.remaining(key(3), TICK), "B's machine is behind nothing graded, as it really is");
        assertTrue(now.remaining(key(0), TICK) == HV, "and the station still has line B");
    }
}
