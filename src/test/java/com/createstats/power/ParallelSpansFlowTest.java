package com.createstats.power;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelSpansFlowTest {
    private static final int BASE = 8_192;
    private static final int HV = BASE * WireTier.HV.multiplier();
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final CrownLaw LAW = CrownLaw.DEFAULT;
    private static final int FULL = 25;

    private static long key(int index) {
        return 7_000L + index;
    }

    private static long[] keys(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = key(i);
        }
        return out;
    }

    private static final class Clock {
        long now;
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
        final Clock clock;
        long tick = Long.MIN_VALUE;
        int takenThisTick;
        long taken;

        Machine(int appetite, Clock clock) {
            this.appetite = appetite;
            this.clock = clock;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (clock.now != tick) {
                tick = clock.now;
                takenThisTick = 0;
            }
            final int take = Math.max(0, Math.min(maxReceive, appetite - takenThisTick));
            if (!simulate) {
                takenThisTick += take;
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

    private static final class Station implements IEnergyStorage {
        final CrownHold hold = new CrownHold();
        final FeIntakeBudget intake = new FeIntakeBudget();
        final Clock clock;
        int held;
        int picked;
        long taken;

        Station(Clock clock, int held, int picked) {
            this.clock = clock;
            this.held = held;
            this.picked = picked;
        }

        int rate() {
            return 2 * Math.max(LAW.totalDraw(1), LAW.totalDraw(picked));
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final long capacity = LAW.storeCapacity(picked);
            final long draw = LAW.totalDraw(held);
            final int take = (int) Math.min(Math.min(maxReceive, hold.room(capacity, draw)),
                    intake.remaining(rate(), clock.now));
            if (take > 0 && !simulate) {
                hold.accept(take, capacity, draw);
                intake.spend(rate(), clock.now, take);
                taken += take;
            }
            return Math.max(0, take);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, hold.stored());
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, LAW.storeCapacity(picked) + LAW.totalDraw(held));
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
        final long key;
        final IEnergyStorage attached;
        final java.util.function.IntSupplier rate;
        final FePathBudget budget;
        final Clock clock;

        final int buffer;
        int stored;

        Node(long key, IEnergyStorage attached, java.util.function.IntSupplier rate, FePathBudget budget, Clock clock) {
            this(key, attached, rate, budget, clock, 0);
        }

        Node(long key, IEnergyStorage attached, java.util.function.IntSupplier rate, FePathBudget budget, Clock clock,
             int buffer) {
            this.key = key;
            this.attached = attached;
            this.rate = rate;
            this.budget = budget;
            this.clock = clock;
            this.buffer = buffer;
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
            stored = Math.max(0, Math.min(amount, buffer));
        }

        @Override
        public int bufferCapacity() {
            return buffer;
        }

        @Override
        public int transferRate() {
            return rate.getAsInt();
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return attached;
        }

        @Override
        public int deliveryAllowance() {
            return budget.remaining(key, clock.now);
        }

        @Override
        public void bookDelivery(int fe) {
            budget.spend(key, fe, clock.now);
        }
    }

    private static Map<Long, Long> parentOf(int[] parent) {
        final Map<Long, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < parent.length; i++) {
            out.put(key(i), parent[i] < 0 ? key(i) : key(parent[i]));
        }
        return out;
    }

    private static final class Ring {
        final Clock clock = new Clock();
        final int[] parent = {-1, 0, 0, 1, 0};
        final int[] rate;
        final FePathBudget budget;
        final Source plant = new Source();
        final Station station;
        final Machine machine;
        final Node connectorA;
        final Node connectorB;
        final List<FeGridNode> nodes = new ArrayList<>();

        Ring(int runA, int runB, int runC, int spanA, int spanB, int plantRate, int appetite, boolean machineOnB,
             int held, int picked, boolean feeds, int connectorBuffer) {
            this.rate = new int[] {NONE, runA, runB, spanA, runC};
            final boolean[] supply = {true, false, false, false, false};
            this.budget = new FePathBudget(keys(5), parent, rate, supply, null, feeds
                    ? List.of(new FeFeeds(key(3), new long[] {key(2)}, new int[] {spanB})) : List.of());
            this.station = new Station(clock, held, picked);
            this.machine = appetite > 0 ? new Machine(appetite, clock) : null;
            nodes.add(new Node(key(0), plant, () -> plantRate, budget, clock));
            connectorA = new Node(key(1), null, () -> NONE, budget, clock, connectorBuffer);
            connectorB = new Node(key(2), machineOnB ? machine : null, () -> Math.max(1, appetite), budget, clock,
                    connectorBuffer);
            nodes.add(connectorA);
            nodes.add(connectorB);
            nodes.add(new Node(key(3), station, station::rate, budget, clock));
            if (machine != null && !machineOnB) {
                nodes.add(new Node(key(4), machine, () -> appetite, budget, clock));
            }
        }

        Ring(int spanA, int spanB, int plantRate, boolean feeds) {
            this(NONE, NONE, NONE, spanA, spanB, plantRate, 0, false, FULL, FULL, feeds, 0);
        }

        FeGridSolver.Result tick(Map<Long, Integer> flows) {
            clock.now++;
            return FeGridSolver.solve(nodes, (int) clock.now, flows, false, NONE);
        }

        long conduit() {
            return (long) connectorA.stored + connectorB.stored;
        }

        long delivered() {
            return station.taken + (machine == null ? 0 : machine.taken);
        }
    }

    @Test
    @DisplayName("a full station on two HV Lines from one plant holds, fed every tick; on one line it rides through and drops")
    void aFullStationHoldsOnTheRing() {
        final Ring two = new Ring(HV, HV, 400_000, true);
        two.station.hold.restore(LAW.storeCapacity(FULL), CrownHold.Phase.FED);
        for (int t = 0; t < 1_200; t++) {
            two.tick(null);
            final CrownHold.Verdict verdict = two.station.hold.step(FULL, FULL, LAW, true);
            assertEquals(CrownHold.Verdict.HOLD, verdict, "tick " + t);
            assertEquals(CrownHold.Phase.FED, two.station.hold.phase(), "tick " + t + ": never riding through");
        }

        final Ring one = new Ring(HV, HV, 400_000, false);
        one.station.hold.restore(LAW.storeCapacity(FULL), CrownHold.Phase.FED);
        int dropped = -1;
        for (int t = 0; t < 1_200 && dropped < 0; t++) {
            one.tick(null);
            if (one.station.hold.step(FULL, FULL, LAW, true) == CrownHold.Verdict.RELEASE_ALL) {
                dropped = t;
            }
        }
        assertTrue(dropped > 0, "the old shape, KI-177: one line rides a full station through and then drops it all");
    }

    @Test
    @DisplayName("over random rings, FE out of the plant is FE into the machines and the conduit, and no conductor carries past its rating")
    void ringConservesAndHoldsEveryRating() {
        final Random random = new Random(1_77_1L);
        for (int trial = 0; trial < 2_000; trial++) {
            final int runA = random.nextInt(3) == 0 ? NONE : 1_000 + random.nextInt(140_000);
            final int runB = random.nextInt(3) == 0 ? NONE : 1_000 + random.nextInt(140_000);
            final int runC = random.nextInt(3) == 0 ? NONE : 1_000 + random.nextInt(140_000);
            final int spanA = 1_000 + random.nextInt(70_000);
            final int spanB = 1_000 + random.nextInt(70_000);
            final int plantRate = random.nextInt(250_000);
            final int appetite = random.nextInt(2) == 0 ? 0 : random.nextInt(60_000);
            final int held = random.nextInt(FULL + 1);
            final int buffer = random.nextBoolean() ? 0 : 16_000;
            final Ring ring = new Ring(runA, runB, runC, spanA, spanB, plantRate, appetite, false, held,
                    Math.max(1, held), true, buffer);
            for (int t = 0; t < 3; t++) {
                final Map<Long, Integer> flows = new java.util.HashMap<>();
                final long expectedRoom = ring.budget.remaining(key(3), ring.clock.now + 1);
                final long stationBefore = ring.station.taken;
                final FeGridSolver.Result result = ring.tick(flows);
                assertEquals(0, result.spilled(), "trial " + trial + " tick " + t + ": nothing deleted");
                assertEquals(ring.plant.given, ring.delivered() + ring.conduit(),
                        "trial " + trial + " tick " + t + ": FE drawn from supply is FE delivered, or in the wire");
                final long stationIn = ring.station.taken - stationBefore;
                assertTrue(stationIn <= Math.min(expectedRoom, ring.station.rate()),
                        "trial " + trial + ": the station took no more than its lines and its own rating allow");

                final FeFlowGraph graph = FeFlowGraph.of(parentOf(ring.parent), Map.of());
                final long[] from = new long[ring.budget.feedSpanCount()];
                final long[] to = new long[from.length];
                final int[] fe = new int[from.length];
                final int moves = ring.budget.movesInto(ring.clock.now, from, to, fe);
                graph.recompute(flows, Map.of(), FeFlowGraph.NO_BOUNDARY, 0, from, to, fe, moves, ring.clock.now);
                for (int child = 1; child < ring.parent.length; child++) {
                    final int carried = graph.edgeFlow(key(child), key(ring.parent[child]), ring.clock.now);
                    assertTrue(ring.rate[child] >= NONE || carried <= ring.rate[child],
                            "trial " + trial + ": edge " + child + " carried " + carried + " over " + ring.rate[child]);
                }
                final int lineB = ring.budget.deliveredThrough(key(3), key(2), ring.clock.now);
                assertTrue(lineB <= spanB, "trial " + trial + ": line B carried " + lineB + " over " + spanB);
                assertTrue(stationIn - lineB <= spanA, "trial " + trial + ": line A over its rating");
            }
        }
    }

    @Test
    @DisplayName("KI-34's residual: a heavy machine sharing line B's run over-counts Collect; it parks in the conduit while it fits")
    void aMachineSharingLineBsRun() {
        final Ring before = new Ring(NONE, HV, NONE, HV, HV, 400_000, 50_000, true, FULL, FULL, false, 16_000);
        final FeGridSolver.Result old = before.tick(null);
        assertEquals(0, old.spilled(), "the old shape: the station on line A alone never shared line B's run");

        final Ring roomy = new Ring(NONE, HV, NONE, HV, HV, 400_000, 50_000, true, FULL, FULL, true, 64_000);
        for (int t = 0; t < 20; t++) {
            final FeGridSolver.Result result = roomy.tick(null);
            assertEquals(0, result.spilled(), "tick " + t + ": the over-count fits the conduit, so nothing is lost");
            assertEquals(roomy.plant.given, roomy.delivered() + roomy.conduit(), "tick " + t);
        }

        final Ring tight = new Ring(NONE, HV, NONE, HV, HV, 400_000, 50_000, true, FULL, FULL, true, 16_000);
        final FeGridSolver.Result spilled = tight.tick(null);
        assertTrue(spilled.spilled() > 0,
                "past the conduit's room the over-count is deleted: KI-34's open residual, reached through line B");
    }

    @Test
    @DisplayName("the readout puts each line's share on its own wire: line A first, line B the rest")
    void eachLineReadsItsOwnShare() {
        final Ring ring = new Ring(HV, HV, 400_000, true);

        final Map<Long, Integer> flows = new java.util.HashMap<>();
        ring.tick(flows);
        assertEquals(2 * HV, ring.station.taken);
        final FeFlowGraph graph = FeFlowGraph.of(parentOf(ring.parent), Map.of());
        final long[] from = new long[1];
        final long[] to = new long[1];
        final int[] fe = new int[1];
        final int moves = ring.budget.movesInto(ring.clock.now, from, to, fe);
        assertEquals(1, moves);
        graph.recompute(flows, Map.of(), FeFlowGraph.NO_BOUNDARY, 0, from, to, fe, moves, ring.clock.now);
        assertEquals(HV, graph.edgeFlow(key(3), key(1), ring.clock.now), "span A carries line A's share");
        assertEquals(HV, graph.edgeFlow(key(1), key(0), ring.clock.now), "and the run to connector A");
        assertEquals(HV, graph.edgeFlow(key(2), key(0), ring.clock.now), "line B's share runs to connector B");

        final FeFlowGraph unmoved = FeFlowGraph.of(parentOf(ring.parent), Map.of());
        unmoved.recompute(flows, Map.of(), ring.clock.now);
        assertEquals(2 * HV, unmoved.edgeFlow(key(3), key(1), ring.clock.now),
                "without the move both lines' FE reads down span A, twice its rating, and line B reads nothing");
        assertEquals(0, unmoved.edgeFlow(key(2), key(0), ring.clock.now));
    }

    private static final class TwoGrids {
        final Clock clock = new Clock();
        final FePathBudget one;
        final FePathBudget two;
        final Source plantOne = new Source();
        final Source plantTwo = new Source();
        final Station station;
        final Machine onB;
        final List<FeGridNode> nodesOne = new ArrayList<>();
        final List<FeGridNode> nodesTwo = new ArrayList<>();
        final boolean twoFirst;

        TwoGrids(int spanA, int spanB, int rateOne, int rateTwo, int machineOnB, int held, int picked,
                 boolean twoFirst) {
            final boolean[] supply = {true, false, false};
            this.one = new FePathBudget(keys(3), new int[] {-1, 0, 1}, new int[] {NONE, NONE, spanA}, supply, null,
                    List.of());
            this.two = new FePathBudget(keys(3), new int[] {-1, 0, 1}, new int[] {NONE, NONE, spanB}, supply, null,
                    List.of());
            this.station = new Station(clock, held, picked);
            this.onB = machineOnB > 0 ? new Machine(machineOnB, clock) : null;
            this.twoFirst = twoFirst;
            nodesOne.add(new Node(key(0), plantOne, () -> rateOne, one, clock));
            nodesOne.add(new Node(key(2), station, station::rate, one, clock));
            nodesTwo.add(new Node(key(0), plantTwo, () -> rateTwo, two, clock));
            nodesTwo.add(new Node(key(2), station, station::rate, two, clock));
            if (onB != null) {
                nodesTwo.add(new Node(key(1), onB, () -> machineOnB, two, clock));
            }
        }

        void tick() {
            clock.now++;
            if (twoFirst) {
                FeGridSolver.solve(nodesTwo, (int) clock.now, null, false, NONE);
                FeGridSolver.solve(nodesOne, (int) clock.now, null, false, NONE);
            } else {
                FeGridSolver.solve(nodesOne, (int) clock.now, null, false, NONE);
                FeGridSolver.solve(nodesTwo, (int) clock.now, null, false, NONE);
            }
        }
    }

    @Test
    @DisplayName("a full station on two plants, one HV Line each, holds; neither plant alone could carry it")
    void aFullStationHoldsOnTwoPlants() {
        for (boolean twoFirst : new boolean[] {false, true}) {
            final TwoGrids grids = new TwoGrids(HV, HV, 70_000, 70_000, 0, FULL, FULL, twoFirst);
            grids.station.hold.restore(LAW.storeCapacity(FULL), CrownHold.Phase.FED);
            for (int t = 0; t < 1_200; t++) {
                grids.tick();
                assertEquals(CrownHold.Verdict.HOLD, grids.station.hold.step(FULL, FULL, LAW, true), "tick " + t);
                assertEquals(CrownHold.Phase.FED, grids.station.hold.phase(), "tick " + t);
            }
        }
    }

    @Test
    @DisplayName("over random feeds on two plants: each circuit conserves, a machine on plant two is fed by plant two alone, and the station never takes twice its rating")
    void twoGridsConserveAndNeverConduct() {
        final Random random = new Random(1_78_2L);
        for (int trial = 0; trial < 2_000; trial++) {
            final int spanA = 1_000 + random.nextInt(70_000);
            final int spanB = 1_000 + random.nextInt(70_000);
            final int rateOne = random.nextInt(200_000);
            final int rateTwo = random.nextInt(4) == 0 ? 0 : random.nextInt(200_000);
            final int machine = random.nextInt(2) == 0 ? 0 : 1 + random.nextInt(80_000);
            final int held = random.nextInt(FULL + 1);
            final TwoGrids grids = new TwoGrids(spanA, spanB, rateOne, rateTwo, machine, held, Math.max(1, held),
                    random.nextBoolean());
            final long before = grids.station.taken;
            grids.tick();
            final long stationIn = grids.station.taken - before;
            final long machineIn = grids.onB == null ? 0 : grids.onB.taken;
            assertEquals(grids.plantOne.given + grids.plantTwo.given, stationIn + machineIn,
                    "trial " + trial + ": FE drawn is FE delivered");
            assertTrue(grids.plantOne.given <= spanA, "trial " + trial + ": plant one reaches nothing but span A");
            assertTrue(machineIn <= grids.plantTwo.given,
                    "trial " + trial + ": the machine on plant two was fed by plant two alone (KI-178)");
            assertTrue(stationIn <= grids.station.rate(),
                    "trial " + trial + ": the station's own rating held across both circuits, not once per circuit");
            if (rateTwo == 0) {
                assertEquals(0, machineIn, "trial " + trial + ": with plant two dark its machine gets nothing");
            }
        }
    }
}
