package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeGridSolverTest {
    private static final class FakeStorage implements IEnergyStorage {
        int energy;
        final int capacity;
        final boolean extractable;
        final boolean receivable;

        int perCallLimit = Integer.MAX_VALUE;

        FakeStorage(int energy, int capacity, boolean extractable, boolean receivable) {
            this.energy = energy;
            this.capacity = capacity;
            this.extractable = extractable;
            this.receivable = receivable;
        }

        static FakeStorage source(int energy) {
            return new FakeStorage(energy, energy, true, false);
        }

        static FakeStorage machine(int capacity) {
            return new FakeStorage(0, capacity, false, true);
        }

        static FakeStorage battery(int energy, int capacity) {
            return new FakeStorage(energy, capacity, true, true);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!receivable) {
                return 0;
            }
            final int accepted = Math.min(Math.min(maxReceive, perCallLimit), capacity - energy);
            if (accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!extractable) {
                return 0;
            }
            final int given = Math.min(Math.min(maxExtract, perCallLimit), energy);
            if (given <= 0) {
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

    private static final class FakeNode implements FeGridNode {
        private final long key;
        private int stored;
        private final int capacity;
        private final int rate;
        @Nullable
        private final FakeStorage attached;

        @Nullable
        IEnergyStorage override;

        FakeNode(long key, int capacity, int rate, @Nullable FakeStorage attached) {
            this.key = key;
            this.capacity = capacity;
            this.rate = rate;
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
            return capacity;
        }

        @Override
        public int transferRate() {
            return rate;
        }

        @Override
        @Nullable
        public IEnergyStorage attachedStorage() {
            return override != null ? override : attached;
        }
    }

    private static final class RankedBattery implements IEnergyStorage, FeReserve {
        final FakeStorage inner;
        private final int rank;

        RankedBattery(int rank, int energy, int capacity) {
            this.rank = rank;
            this.inner = FakeStorage.battery(energy, capacity);
        }

        @Override
        public int reserveRank() {
            return rank;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return inner.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return inner.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return inner.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return inner.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private record RankedNode(FakeNode node, RankedBattery battery) {
        int energy() {
            return battery.inner.energy;
        }
    }

    private static RankedNode tank(long key, int rank, int energy, int capacity) {
        final RankedBattery battery = new RankedBattery(rank, energy, capacity);
        final FakeNode node = new FakeNode(key, 16_000, 4_096, null);
        node.override = battery;
        return new RankedNode(node, battery);
    }

    private static int totalWithTanks(List<FakeNode> nodes, List<RankedNode> tanks) {
        int total = totalEnergy(nodes);
        for (RankedNode tank : tanks) {
            total += tank.energy();
        }
        return total;
    }

    private static int totalEnergy(List<FakeNode> nodes) {
        int total = 0;
        for (FakeNode node : nodes) {
            total += node.stored;
            if (node.attached != null) {
                total += node.attached.energy;
            }
        }
        return total;
    }

    private static List<FakeNode> chain(int length, @Nullable FakeStorage head, @Nullable FakeStorage tail) {
        final List<FakeNode> nodes = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            final FakeStorage attached = i == 0 ? head : i == length - 1 ? tail : null;
            nodes.add(new FakeNode(i, 16_000, 4_096, attached));
        }
        return nodes;
    }

    private static final class LyingSink implements IEnergyStorage {
        final FakeStorage inner = FakeStorage.machine(1_000_000);
        private final int realLimit;

        LyingSink(int realLimit) {
            this.realLimit = realLimit;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return simulate ? maxReceive : inner.receiveEnergy(Math.min(maxReceive, realLimit), false);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return inner.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return inner.capacity;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    @Test
    void aSinkThatRenegesOnItsSimulateDoesNotDestroyEnergy() {
        final FakeStorage generator = FakeStorage.source(100_000);
        final LyingSink liar = new LyingSink(37);
        final FakeNode source = new FakeNode(0, 16_000, 4_096, generator);
        final FakeNode sink = new FakeNode(1, 16_000, 4_096, null);
        sink.override = liar;
        final List<FakeNode> nodes = List.of(source, sink);

        final int before = generator.energy + liar.inner.energy;
        for (int tick = 0; tick < 20; tick++) {
            FeGridSolver.solve(nodes, tick);
            final int after = generator.energy + liar.inner.energy
                    + source.stored + sink.stored;
            assertEquals(before, after, "FE must be conserved on tick " + tick);
        }
        assertTrue(liar.inner.energy > 0, "the sink should still have been charged what it would take");
    }

    @Test
    void energyIsConservedAcrossASolve() {
        List<FakeNode> nodes = chain(5, FakeStorage.source(50_000), FakeStorage.machine(50_000));
        final int before = totalEnergy(nodes);
        for (int tick = 0; tick < 30; tick++) {
            FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "FE must be conserved on tick " + tick);
        }
    }

    @Test
    void aThinWireStillCarriesAFatLoad() {
        final FakeStorage battery = FakeStorage.battery(50_000, 50_000);
        final FakeStorage machine = FakeStorage.machine(50_000);
        final List<FakeNode> nodes = new ArrayList<>();

        nodes.add(new FakeNode(0, 25, 4_096, battery));
        nodes.add(new FakeNode(1, 25, 4_096, null));
        nodes.add(new FakeNode(2, 25, 4_096, machine));

        final int before = totalEnergy(nodes);
        FeGridSolver.solve(nodes, 0);
        assertTrue(machine.energy >= 256,
                "a 75 FE grid must still deliver a 256 FE/t load, got " + machine.energy);
        assertEquals(before, totalEnergy(nodes), "and it must not invent or lose any doing it");
    }

    @Test
    void aLongRunDeliversInOneTickNotOneHopPerTick() {
        FakeStorage source = FakeStorage.source(50_000);
        FakeStorage machine = FakeStorage.machine(50_000);
        List<FakeNode> nodes = chain(10, source, machine);

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(4_096, result.collected(), "one node's transfer rate is pulled in");
        assertEquals(4_096, machine.energy, "and it reaches the far end on the same tick");
        assertEquals(4_096, result.delivered());
    }

    @Test
    void deliveryIsIndependentOfNodeOrder() {
        FakeStorage sourceA = FakeStorage.source(50_000);
        FakeStorage machineA = FakeStorage.machine(50_000);
        List<FakeNode> forward = chain(6, sourceA, machineA);

        FakeStorage sourceB = FakeStorage.source(50_000);
        FakeStorage machineB = FakeStorage.machine(50_000);
        List<FakeNode> reversed = new ArrayList<>(chain(6, sourceB, machineB));
        java.util.Collections.reverse(reversed);

        for (int tick = 0; tick < 5; tick++) {
            FeGridSolver.solve(forward, tick);
            FeGridSolver.solve(reversed, tick);
        }
        assertEquals(machineA.energy, machineB.energy);
        assertEquals(sourceA.energy, sourceB.energy);
    }

    @Test
    void scarcePowerIsSplitFairlyBetweenMachines() {
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, FakeStorage.source(1_000)));
        FakeStorage a = FakeStorage.machine(50_000);
        FakeStorage b = FakeStorage.machine(50_000);
        FakeStorage c = FakeStorage.machine(50_000);
        nodes.add(new FakeNode(1, 16_000, 4_096, a));
        nodes.add(new FakeNode(2, 16_000, 4_096, b));
        nodes.add(new FakeNode(3, 16_000, 4_096, c));

        FeGridSolver.solve(nodes, 0);
        assertEquals(1_000, a.energy + b.energy + c.energy);
        for (FakeStorage machine : List.of(a, b, c)) {
            assertTrue(machine.energy > 0, "no machine may be starved by list order");
        }
    }

    @Test
    void theCursorRotatesWhoIsServedLast() {
        List<Integer> firstServed = new ArrayList<>();
        for (int cursor = 0; cursor < 3; cursor++) {
            List<FakeNode> nodes = new ArrayList<>();
            nodes.add(new FakeNode(0, 16_000, 4_096, FakeStorage.source(10)));
            final List<FakeStorage> machines = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                FakeStorage machine = FakeStorage.machine(50_000);
                machines.add(machine);
                nodes.add(new FakeNode(i, 16_000, 4, machine));
            }
            FeGridSolver.solve(nodes, cursor);
            int best = 0;
            for (int i = 1; i < machines.size(); i++) {
                if (machines.get(i).energy > machines.get(best).energy) {
                    best = i;
                }
            }
            firstServed.add(best);
        }
        assertNotEquals(1, firstServed.stream().distinct().count(),
                "the same machine must not win every tick");
    }

    @Test
    void machinesAreServedBeforeStorage() {
        FakeStorage machine = FakeStorage.machine(50_000);
        FakeStorage battery = FakeStorage.battery(0, 50_000);
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, FakeStorage.source(3_000)));

        nodes.add(new FakeNode(1, 16_000, 4_096, battery));
        nodes.add(new FakeNode(2, 16_000, 4_096, machine));

        FeGridSolver.solve(nodes, 0);
        assertEquals(3_000, machine.energy, "the machine takes the whole supply first");
        assertEquals(0, battery.energy, "storage only sees genuine surplus");
    }

    @Test
    void anIdleBatteryIsNeverDrainedToFillWire() {
        FakeStorage battery = FakeStorage.battery(20_000, 50_000);
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, battery));
        final int before = totalEnergy(nodes);

        for (int tick = 0; tick < 20; tick++) {
            FeGridSolver.Result result = FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "FE conserved on tick " + tick);
            assertEquals(0, result.collected(), "nothing is drawing, so nothing may be drawn");
            assertEquals(0, result.delivered());
        }
        assertEquals(20_000, battery.energy, "the battery is untouched");
        assertEquals(0, nodes.get(0).storedEnergy());
    }

    @Test
    void storageCoversExactlyTheShortfallForMachines() {
        FakeStorage battery = FakeStorage.battery(20_000, 50_000);
        FakeStorage machine = FakeStorage.machine(50_000);
        machine.perCallLimit = 300;
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, battery));
        nodes.add(new FakeNode(1, 16_000, 4_096, machine));
        final int before = totalEnergy(nodes);

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(before, totalEnergy(nodes));
        assertEquals(300, machine.energy, "the machine runs off the battery");
        assertEquals(19_700, battery.energy, "and the battery gave up exactly that, not a rate's worth");
        assertEquals(300, result.collected());
        assertEquals(300, result.delivered());
        assertEquals(0, nodes.get(0).storedEnergy() + nodes.get(1).storedEnergy(),
                "nothing was skimmed into the wire on the way past");
    }

    @Test
    void surplusGenerationChargesStorageOnEveryTick() {
        FakeStorage source = FakeStorage.source(1_000_000);
        FakeStorage battery = FakeStorage.battery(0, 500_000);
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, source));
        nodes.add(new FakeNode(1, 16_000, 4_096, battery));
        final int before = totalEnergy(nodes);

        FeGridSolver.Result first = FeGridSolver.solve(nodes, 0);
        assertEquals(4_096, first.charged(), "a source with nothing else to do charges from tick one");
        assertEquals(4_096, battery.energy);

        for (int tick = 1; tick < 40; tick++) {
            FeGridSolver.Result result = FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "FE conserved on tick " + tick);
            assertEquals(4_096, result.charged(), "and keeps charging on tick " + tick);
        }
        assertEquals(40 * 4_096, battery.energy, "at a steady rate, with no gaps");
        assertEquals(0, nodes.get(0).storedEnergy() + nodes.get(1).storedEnergy(),
                "the wire is topped up out of what is left over, and there is nothing left over");
    }

    @Test
    void theWireIsToppedUpOnceThereIsNothingBetterToDoWithTheEnergy() {
        FakeStorage source = FakeStorage.source(1_000_000);
        FakeStorage battery = FakeStorage.battery(500_000, 500_000);
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, source));
        nodes.add(new FakeNode(1, 16_000, 4_096, battery));
        final int before = totalEnergy(nodes);

        for (int tick = 0; tick < 20; tick++) {
            FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "FE conserved on tick " + tick);
        }
        assertEquals(32_000, nodes.get(0).storedEnergy() + nodes.get(1).storedEnergy(),
                "the wire buffer fills to capacity and stops");
        assertEquals(500_000, battery.energy, "and the full battery was not disturbed");
    }

    @Test
    void aBatteryChargesWhileMachinesAreAlsoRunning() {
        FakeStorage source = FakeStorage.source(1_000_000);
        source.perCallLimit = 1_000;
        FakeStorage machine = FakeStorage.machine(50_000);
        machine.perCallLimit = 200;
        FakeStorage battery = FakeStorage.battery(0, 500_000);
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, source));
        nodes.add(new FakeNode(1, 16_000, 4_096, machine));
        nodes.add(new FakeNode(2, 16_000, 4_096, battery));
        final int before = totalEnergy(nodes);

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(before, totalEnergy(nodes));
        assertEquals(200, result.delivered(), "the machine is served first and in full");
        assertTrue(result.charged() > 0, "and the battery gets the rest, on the same tick");
    }

    @Test
    void aRateLimitedSourceCannotBeOverDrawn() {
        FakeStorage source = FakeStorage.source(50_000);
        source.perCallLimit = 37;
        List<FakeNode> nodes = chain(3, source, FakeStorage.machine(50_000));
        final int before = totalEnergy(nodes);

        for (int tick = 0; tick < 10; tick++) {
            FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "no FE minted on tick " + tick);
        }
        assertEquals(50_000 - 370, source.energy, "exactly the rate limit moved each tick");
    }

    @Test
    void aRateLimitedSinkDoesNotVoidTheRemainder() {
        FakeStorage machine = FakeStorage.machine(50_000);
        machine.perCallLimit = 100;
        List<FakeNode> nodes = chain(3, FakeStorage.source(50_000), machine);
        final int before = totalEnergy(nodes);

        for (int tick = 0; tick < 5; tick++) {
            FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "the undelivered remainder stays in the grid");
        }

        assertTrue(machine.energy > 0 && machine.energy <= 5 * 2 * 100);
        assertTrue(nodes.get(0).storedEnergy() + nodes.get(1).storedEnergy()
                + nodes.get(2).storedEnergy() > 0);
    }

    @Test
    void gridBufferNeverExceedsCapacity() {
        List<FakeNode> nodes = chain(4, FakeStorage.source(1_000_000), null);
        for (int tick = 0; tick < 40; tick++) {
            FeGridSolver.solve(nodes, tick);
        }
        int stored = 0;
        for (FakeNode node : nodes) {
            assertTrue(node.storedEnergy() <= node.bufferCapacity(), "no node may overfill");
            stored += node.storedEnergy();
        }
        assertEquals(4 * 16_000, stored, "the grid fills to exactly its capacity and stops");
    }

    private static FeConsumer.Settings consumerSettings(int capacity) {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public int capacity() {
                return capacity;
            }

            @Override
            public int idleDrain() {
                return 0;
            }

            @Override
            public int activeDrain() {
                return 0;
            }

            @Override
            public int burstCost() {
                return 0;
            }

            @Override
            public int rebootThresholdPct() {
                return 0;
            }
        };
    }

    private static FakeNode load(long key, FePriority priority, FeConsumer[] out, int index) {
        final FeConsumer fe = new FeConsumer(consumerSettings(100_000));
        fe.setPriority(priority);
        out[index] = fe;
        final FakeNode node = new FakeNode(key, 16_000, 4_096, null);
        node.override = fe;
        return node;
    }

    @Test
    void criticalIsServedInFullWhileOptionalIsShed() {
        final FeConsumer[] fe = new FeConsumer[2];
        final List<FakeNode> loads = List.of(
                load(0, FePriority.OPTIONAL, fe, 1),
                load(1, FePriority.CRITICAL, fe, 0));

        FeGridSolver.BranchResult result = FeGridSolver.serveByPriority(loads, 3_000, 0);

        assertEquals(3_000, fe[0].getEnergyStored(), "critical takes everything available");
        assertEquals(0, fe[1].getEnergyStored(), "optional gets nothing");
        assertFalse(fe[0].isLoadShed(), "a served load is not shed");
        assertTrue(fe[1].isLoadShed(), "the tier the pool could not reach is shed");
        assertEquals(3_000, result.delivered());
        assertEquals(1, result.served());
        assertEquals(1, result.shed());
    }

    @Test
    void tierOrderBeatsListOrder() {
        final FeConsumer[] a = new FeConsumer[2];
        final List<FakeNode> forward = List.of(
                load(0, FePriority.CRITICAL, a, 0), load(1, FePriority.LOW, a, 1));
        FeGridSolver.serveByPriority(forward, 1_000, 0);

        final FeConsumer[] b = new FeConsumer[2];
        final List<FakeNode> reversed = List.of(
                load(0, FePriority.LOW, b, 1), load(1, FePriority.CRITICAL, b, 0));
        FeGridSolver.serveByPriority(reversed, 1_000, 0);

        assertEquals(a[0].getEnergyStored(), b[0].getEnergyStored());
        assertEquals(1_000, a[0].getEnergyStored());
        assertEquals(0, a[1].getEnergyStored());
        assertEquals(0, b[1].getEnergyStored());
    }

    @Test
    void equalsInOneTierStillSplitFairly() {
        final FeConsumer[] fe = new FeConsumer[3];
        final List<FakeNode> loads = List.of(
                load(0, FePriority.NORMAL, fe, 0),
                load(1, FePriority.NORMAL, fe, 1),
                load(2, FePriority.NORMAL, fe, 2));
        FeGridSolver.serveByPriority(loads, 900, 0);

        assertEquals(900, fe[0].getEnergyStored() + fe[1].getEnergyStored() + fe[2].getEnergyStored());
        for (FeConsumer consumer : fe) {
            assertTrue(consumer.getEnergyStored() > 0, "no equal may be starved by list order");
            assertFalse(consumer.isLoadShed(), "a served tier is never shed");
        }
    }

    @Test
    void aDeadBranchReadsAsOutageNotAsShed() {
        final FeConsumer[] fe = new FeConsumer[2];
        final List<FakeNode> loads = List.of(
                load(0, FePriority.CRITICAL, fe, 0), load(1, FePriority.OPTIONAL, fe, 1));

        FeGridSolver.BranchResult result = FeGridSolver.serveByPriority(loads, 0, 0);

        assertFalse(fe[0].isLoadShed(), "top tier starved is an outage, not a shed");
        assertFalse(fe[1].isLoadShed(), "and so is everything under it");
        assertEquals(0, result.shed());
        assertEquals(0, result.delivered());
    }

    @Test
    void shedFlagsAreRewrittenEveryTickAndNeverGoStale() {
        final FeConsumer[] fe = new FeConsumer[2];
        final List<FakeNode> loads = List.of(
                load(0, FePriority.CRITICAL, fe, 0), load(1, FePriority.OPTIONAL, fe, 1));

        FeGridSolver.serveByPriority(loads, 1_000, 0);
        assertTrue(fe[1].isLoadShed(), "short branch sheds the low tier");

        FeGridSolver.serveByPriority(loads, 50_000, 1);
        assertFalse(fe[1].isLoadShed(), "the flag clears once the tier is served again");
    }

    @Test
    void thirdPartyMachinesAreNeverShedAndSitAtTheDefaultTier() {
        FakeStorage plain = FakeStorage.machine(50_000);
        FakeNode foreign = new FakeNode(0, 16_000, 4_096, plain);
        final FeConsumer[] fe = new FeConsumer[1];
        FakeNode critical = load(1, FePriority.CRITICAL, fe, 0);

        FeGridSolver.BranchResult result = FeGridSolver.serveByPriority(List.of(foreign, critical), 2_000, 0);

        assertEquals(2_000, fe[0].getEnergyStored(), "critical outranks the default tier");
        assertEquals(0, plain.energy, "the default-tier foreign load got what was left, which is nothing");
        assertEquals(1, result.shed(), "it counts as shed even though no flag could be written");
    }

    @Test
    void branchSolveConservesEnergy() {
        final FeConsumer[] fe = new FeConsumer[3];
        final List<FakeNode> loads = List.of(
                load(0, FePriority.CRITICAL, fe, 0),
                load(1, FePriority.NORMAL, fe, 1),
                load(2, FePriority.OPTIONAL, fe, 2));
        for (int tick = 0; tick < 20; tick++) {
            final int before = fe[0].getEnergyStored() + fe[1].getEnergyStored() + fe[2].getEnergyStored();
            final FeGridSolver.BranchResult result = FeGridSolver.serveByPriority(loads, 1_500, tick);
            final int after = fe[0].getEnergyStored() + fe[1].getEnergyStored() + fe[2].getEnergyStored();
            assertEquals(result.delivered(), after - before, "delivered must equal what the loads gained");
        }
    }

    @Test
    void anEmptyGridSolvesToNothing() {
        assertEquals(FeGridSolver.Result.EMPTY, FeGridSolver.solve(List.of(), 0));
    }

    @Test
    void flowIsNegativeLeavingASourceAndPositiveEnteringAMachine() {
        FakeStorage source = FakeStorage.source(50_000);
        FakeStorage machine = FakeStorage.machine(50_000);
        List<FakeNode> nodes = chain(3, source, machine);

        Map<Long, Integer> flow = new HashMap<>();
        FeGridSolver.solve(nodes, 0, flow);

        assertEquals(-4_096, flow.get(0L), "the source's own node reads negative: energy left it");
        assertEquals(4_096, flow.get(2L), "the machine's own node reads positive: energy entered it");
        assertNull(flow.get(1L), "a bare wire node with nothing attached moves nothing and is absent");
    }

    @Test
    void aNullFlowMapChangesNothingAboutTheSolve() {
        FakeStorage sourceA = FakeStorage.source(50_000);
        FakeStorage machineA = FakeStorage.machine(50_000);
        List<FakeNode> withoutMap = chain(4, sourceA, machineA);

        FakeStorage sourceB = FakeStorage.source(50_000);
        FakeStorage machineB = FakeStorage.machine(50_000);
        List<FakeNode> withNullMap = chain(4, sourceB, machineB);

        for (int tick = 0; tick < 10; tick++) {
            FeGridSolver.Result a = FeGridSolver.solve(withoutMap, tick);
            FeGridSolver.Result b = FeGridSolver.solve(withNullMap, tick, null);
            assertEquals(a, b, "tick " + tick);
        }
        assertEquals(machineA.energy, machineB.energy);
        assertEquals(sourceA.energy, sourceB.energy);
    }

    @Test
    void flowThroughStorageReflectsWhicheverDirectionActuallyMoved() {
        FakeStorage battery = FakeStorage.battery(20_000, 50_000);
        FakeStorage hungryMachine = FakeStorage.machine(50_000);
        hungryMachine.perCallLimit = 300;
        List<FakeNode> discharging = new ArrayList<>();
        discharging.add(new FakeNode(10, 16_000, 4_096, battery));
        discharging.add(new FakeNode(11, 16_000, 4_096, hungryMachine));

        Map<Long, Integer> dischargeFlow = new HashMap<>();
        FeGridSolver.solve(discharging, 0, dischargeFlow);
        assertTrue(dischargeFlow.get(10L) < 0, "the battery gave up energy to cover the machine");

        FakeStorage source = FakeStorage.source(1_000_000);
        FakeStorage emptyBattery = FakeStorage.battery(0, 50_000);
        List<FakeNode> charging = new ArrayList<>();
        charging.add(new FakeNode(20, 16_000, 4_096, source));
        charging.add(new FakeNode(21, 16_000, 4_096, emptyBattery));
        Map<Long, Integer> chargeFlow = new HashMap<>();

        for (int tick = 0; tick < 10; tick++) {
            FeGridSolver.solve(charging, tick);
        }
        chargeFlow.clear();
        FeGridSolver.solve(charging, 10, chargeFlow);
        assertTrue(chargeFlow.getOrDefault(21L, 0) > 0, "surplus charges the battery: positive");
    }

    @Test
    void theOutermostTankDischargesFirstAndTheCoreLast() {
        final List<RankedNode> tanks = new ArrayList<>();
        for (int rank = 1; rank <= 5; rank++) {
            tanks.add(tank(rank, rank, 10_000, 50_000));
        }
        final FakeStorage machine = FakeStorage.machine(1_000_000);
        final List<FakeNode> nodes = new ArrayList<>();
        for (RankedNode tank : tanks) {
            nodes.add(tank.node());
        }
        nodes.add(new FakeNode(99, 16_000, 4_096, machine));

        FeGridSolver.solve(nodes, 0);

        assertTrue(tanks.get(4).energy() < 10_000, "rank 5 is the outermost and gives first");
        assertEquals(10_000, tanks.get(0).energy(), "the core is the last tank to be touched");
        assertEquals(10_000, tanks.get(1).energy(), "rank 2 is still untouched behind rank 5");
    }

    @Test
    void tanksEmptyOneAtATimeDownToTheCore() {
        final List<RankedNode> tanks = new ArrayList<>();
        for (int rank = 1; rank <= 3; rank++) {
            tanks.add(tank(rank, rank, 8_000, 50_000));
        }
        final FakeStorage machine = FakeStorage.machine(1_000_000);
        final List<FakeNode> nodes = new ArrayList<>();
        for (RankedNode tank : tanks) {
            nodes.add(tank.node());
        }
        nodes.add(new FakeNode(99, 16_000, 4_096, machine));

        for (int tick = 0; tick < 3; tick++) {
            FeGridSolver.solve(nodes, tick);
        }
        assertEquals(0, tanks.get(2).energy(), "the outermost tank is spent first");
        assertTrue(tanks.get(1).energy() < 8_000, "and the next one has started");
        assertEquals(8_000, tanks.get(0).energy(), "while the core has still not been touched");
    }

    @Test
    void theCoreChargesFirst() {
        final RankedNode core = tank(1, 1, 0, 50_000);
        final RankedNode outer = tank(2, 5, 0, 50_000);
        final List<FakeNode> nodes = new ArrayList<>();

        nodes.add(outer.node());
        nodes.add(core.node());
        nodes.add(new FakeNode(0, 16_000, 4_096, FakeStorage.source(50_000)));

        for (int tick = 0; tick < 20; tick++) {
            FeGridSolver.solve(nodes, tick);
        }
        assertEquals(50_000, core.energy(), "the whole supply went to the core");
        assertEquals(0, outer.energy(), "and the outer tank got what was left, which was nothing");
    }

    @Test
    void theCascadeConservesEnergyExactlyLikeTheFlatSolve() {
        final List<RankedNode> tanks = List.of(
                tank(1, 1, 12_000, 50_000), tank(2, 3, 9_000, 50_000), tank(3, 5, 7_000, 50_000));
        final FakeStorage machine = FakeStorage.machine(60_000);
        final FakeStorage source = FakeStorage.source(40_000);
        final List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, source));
        for (RankedNode tank : tanks) {
            nodes.add(tank.node());
        }
        nodes.add(new FakeNode(99, 16_000, 4_096, machine));

        final int before = totalWithTanks(nodes, tanks);
        for (int tick = 0; tick < 40; tick++) {
            FeGridSolver.solve(nodes, tick);
        }
        assertEquals(before, totalWithTanks(nodes, tanks),
                "reordering storage may change which tank supplies, never how much exists");
    }

    @Test
    void theCascadeDeliversTheSameTotalAsTheFlatSolve() {
        int cascaded = 0;
        int flat = 0;
        for (boolean cascade : List.of(true, false)) {
            final List<RankedNode> tanks = List.of(
                    tank(1, 1, 20_000, 50_000), tank(2, 5, 20_000, 50_000));
            final FakeStorage machine = FakeStorage.machine(1_000_000);
            final List<FakeNode> nodes = new ArrayList<>();
            for (RankedNode tank : tanks) {
                nodes.add(tank.node());
            }
            nodes.add(new FakeNode(99, 16_000, 4_096, machine));
            for (int tick = 0; tick < 5; tick++) {
                FeGridSolver.solve(nodes, tick, null, cascade);
            }
            if (cascade) {
                cascaded = machine.energy;
            } else {
                flat = machine.energy;
            }
        }
        assertEquals(flat, cascaded, "the cascade changes the order, not the throughput");
    }

    @Test
    void anUnrankedBatteryIsNeitherCoreNorShedFirst() {
        final RankedNode core = tank(1, 1, 10_000, 50_000);
        final RankedNode outer = tank(3, 9, 10_000, 50_000);
        final FakeStorage foreign = FakeStorage.battery(10_000, 50_000);
        final FakeStorage machine = FakeStorage.machine(1_000_000);
        final List<FakeNode> nodes = new ArrayList<>();
        nodes.add(core.node());
        nodes.add(new FakeNode(2, 16_000, 4_096, foreign));
        nodes.add(outer.node());
        nodes.add(new FakeNode(99, 16_000, 4_096, machine));

        final int before = totalWithTanks(nodes, List.of(core, outer));
        FeGridSolver.solve(nodes, 0);
        assertTrue(outer.energy() < 10_000, "the rank 9 tank gives first");
        assertEquals(10_000, foreign.energy, "the unranked battery is still holding behind it");
        assertEquals(10_000, core.energy(), "and the core has not been touched");

        for (int tick = 0; tick < 3; tick++) {
            FeGridSolver.solve(nodes, tick);
        }
        assertEquals(0, outer.energy(), "the outermost tank is spent");
        assertTrue(foreign.energy < 10_000, "the middle-ranked battery goes next");
        assertEquals(10_000, core.energy(), "the core still last");
        assertEquals(before, totalWithTanks(nodes, List.of(core, outer)),
                "and nothing was minted along the way");
    }

    @Test
    void tanksAtTheSameRankStillHaveAStableOrder() {
        final List<Integer> firstToGive = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            final List<RankedNode> tanks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tanks.add(tank(100 - i, FeReserve.DEFAULT_RANK, 10_000, 50_000));
            }
            final FakeStorage machine = FakeStorage.machine(1_000_000);
            final List<FakeNode> nodes = new ArrayList<>();

            if (run == 0) {
                for (RankedNode tank : tanks) {
                    nodes.add(tank.node());
                }
            } else {
                for (int i = tanks.size() - 1; i >= 0; i--) {
                    nodes.add(tanks.get(i).node());
                }
            }
            nodes.add(new FakeNode(9_999, 16_000, 4_096, machine));

            FeGridSolver.solve(nodes, 0);
            for (int i = 0; i < tanks.size(); i++) {
                if (tanks.get(i).energy() < 10_000) {
                    firstToGive.add((int) tanks.get(i).node().nodeKey());
                }
            }
        }
        assertEquals(2, firstToGive.size(), "exactly one tank gave on each run");
        assertEquals(firstToGive.get(0), firstToGive.get(1),
                "the same tank gives first however the network happened to enumerate them");
    }

    @Test
    void demandAndShortfallExposeAnUnderProvisionedGrid() {
        FakeStorage source = FakeStorage.source(1_000_000);
        FakeStorage machine = FakeStorage.machine(1_000_000);
        List<FakeNode> healthy = chain(3, source, machine);
        FeGridSolver.Result ok = FeGridSolver.solve(healthy, 0);
        assertEquals(4_096, ok.demand(), "the machine asked for a full node rate");
        assertEquals(0, ok.shortfall(), "and got it");

        FakeStorage trickle = FakeStorage.source(1_000_000);
        trickle.perCallLimit = 500;
        FakeStorage hungry = FakeStorage.machine(1_000_000);
        List<FakeNode> starved = chain(3, trickle, hungry);
        FeGridSolver.Result short1 = FeGridSolver.solve(starved, 0);
        assertEquals(4_096, short1.demand());
        assertTrue(short1.shortfall() > 0, "the gap is visible before anything cuts out");
        assertEquals(short1.demand() - short1.delivered(), short1.shortfall());
    }

    @Test
    void enduranceCountsDownOnlyWhileTheReserveIsDraining() {
        FeGridSolver.Result filling = new FeGridSolver.Result(
                1_000, 0, 50_000, 100_000, 0, 0, 1_000, 0, 200_000, 500_000, 0);
        assertEquals(Integer.MAX_VALUE, filling.enduranceTicks());

        FeGridSolver.Result steady = new FeGridSolver.Result(
                400, 400, 50_000, 100_000, 400, 0, 0, 0, 200_000, 500_000, 0);
        assertEquals(Integer.MAX_VALUE, steady.enduranceTicks());
        assertEquals(0, steady.reserveNet());

        FeGridSolver.Result draining = new FeGridSolver.Result(
                500, 500, 50_000, 100_000, 500, 0, 0, 500, 200_000, 500_000, 0);
        assertEquals(500, draining.enduranceTicks());
        assertEquals(-500, draining.reserveNet());
        assertEquals(250_000, draining.available());
    }

    @Test
    void aGridRunningOffItsBatteriesDoesNotReportItselfStable() {
        FakeStorage battery = FakeStorage.battery(20_000, 50_000);
        FakeStorage machine = FakeStorage.machine(1_000_000);
        machine.perCallLimit = 200;
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, battery));
        nodes.add(new FakeNode(1, 16_000, 4_096, machine));

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertTrue(result.discharged() > 0, "the machine is running off the bank");
        assertEquals(0, result.charged(), "and nothing is charging it back up");
        assertTrue(result.reserveNet() < 0, "so the reserve is going down");
        assertNotEquals(Integer.MAX_VALUE, result.enduranceTicks(),
                "a draining grid must report a real time to empty, not 'stable'");
        assertEquals(20_000, result.reserve(),
                "the reserve is sampled before this tick's own discharge leaves it");
        assertEquals(20_000 - result.discharged(), battery.energy,
                "and the bank really did give up exactly what was reported");
    }

    @Test
    void oneSharedBankIsCountedOnceHoweverManyNodesReachIt() {
        FakeStorage shared = FakeStorage.battery(30_000, 90_000);
        List<FakeNode> nodes = new ArrayList<>();
        FakeNode first = new FakeNode(0, 16_000, 4_096, null);
        FakeNode second = new FakeNode(1, 16_000, 4_096, null);
        first.override = new PooledBattery(shared, 7L);
        second.override = new PooledBattery(shared, 7L);
        nodes.add(first);
        nodes.add(second);

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(30_000, result.reserve(), "one tank, counted once");
        assertEquals(90_000, result.reserveCapacity());
    }

    @Test
    void oneMultiblockBatteryIsOneTankHoweverManyBlocksTheRunPasses() {
        final FakeStorage tank = FakeStorage.battery(0, 8_000_000);
        final FakeStorage source = FakeStorage.source(1_000_000);
        final List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 20_000, source));
        for (int block = 0; block < 4; block++) {
            nodes.add(new FakeNode(10 + block, 0, 4_096, tank));
        }

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(4_096, result.charged(), "one tank, charged at one tank's rated input");
        assertEquals(4_096, tank.energy);
        assertEquals(8_000_000, result.reserveCapacity(), "and its capacity counted once");
    }

    @Test
    void aForeignNetworkMouthIsASourceRatherThanAPhantomBattery() {
        final var mouth = new com.mrh0.createaddition.blocks.connector.base
                .FakeConnectorEnergyHandler(true, 5_000, 1_000_000);
        final FakeStorage battery = FakeStorage.battery(0, 500_000);
        final List<FakeNode> nodes = new ArrayList<>();
        final FakeNode mouthNode = new FakeNode(0, 25, 4_096, null);
        mouthNode.override = mouth;
        nodes.add(mouthNode);
        nodes.add(new FakeNode(1, 25, 4_096, battery));

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(0, result.reserve(), "their network's buffer is not our reserve");
        assertEquals(500_000, result.reserveCapacity(), "only the real battery is a tank");
        assertEquals(4_096, result.charged(), "and it charges from their network like any source");
        assertEquals(4_096, battery.energy);
        assertEquals(1_000_000 - 4_096, mouth.networkBuff);
    }

    @Test
    void aForeignNetworkMouthThatIsPullingIsALoad() {
        final var mouth = new com.mrh0.createaddition.blocks.connector.base
                .FakeConnectorEnergyHandler(false, 5_000, 0);
        final FakeStorage source = FakeStorage.source(1_000_000);
        source.perCallLimit = 360;
        final List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 25, 4_096, source));
        final FakeNode mouthNode = new FakeNode(1, 25, 4_096, null);
        mouthNode.override = mouth;
        nodes.add(mouthNode);

        FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);
        assertEquals(0, result.reserve(), "an export point holds nothing of ours");
        assertEquals(4_096, result.demand(), "it asks for what the wire can carry");
        assertEquals(360, result.delivered(), "and gets what the generator can actually make");
        assertEquals(360, mouth.networkBuff);
    }

    private record PooledBattery(FakeStorage tank, long poolKey)
            implements net.neoforged.neoforge.energy.IEnergyStorage, FeReserve {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return tank.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return tank.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return tank.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return tank.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public int reserveRank() {
            return FeReserve.DEFAULT_RANK;
        }

        @Override
        public long reservePoolKey() {
            return poolKey;
        }
    }

    private static List<FakeNode> run(int generators, FakeStorage machine, int cableRate) {
        final List<FakeNode> nodes = new ArrayList<>();
        for (int i = 0; i < generators; i++) {
            nodes.add(new FakeNode(i, 25, cableRate, FakeStorage.source(10_000_000)));
        }
        if (machine != null) {
            nodes.add(new FakeNode(900, 25, 1_000_000, machine));
        }
        return nodes;
    }

    @Test
    void severalGeneratorsCannotBeatTheCableTheyAreOn() {
        final FakeStorage machine = FakeStorage.machine(10_000_000);
        final List<FakeNode> nodes = run(3, machine, 4_096);
        final int before = totalEnergy(nodes);

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0, null, true, 4_096);

        assertEquals(4_096, result.delivered(),
                "three generators on a 4,096 FE/t run must still deliver 4,096, not their sum");
        assertEquals(before, totalEnergy(nodes), "and the limit must not lose a single FE doing it");
    }

    @Test
    void withoutACeilingThePerNodeRatesStillAddUp() {
        final FakeStorage machine = FakeStorage.machine(10_000_000);
        final List<FakeNode> nodes = run(3, machine, 4_096);

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0);

        assertEquals(12_288, result.delivered(),
                "a grid with no conductor of its own is limited by its nodes and nothing else");
    }

    @Test
    void chargingABankIsHeldToTheCableToo() {
        final List<FakeNode> nodes = run(3, null, 4_096);
        final FakeStorage bank = FakeStorage.battery(0, 10_000_000);
        nodes.add(new FakeNode(900, 25, 32_768, bank));
        final int before = totalEnergy(nodes);

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0, null, true, 4_096);

        assertEquals(4_096, result.charged(), "the bank fills at the cable's rate, not the sum of the run");
        assertEquals(4_096, bank.energy);
        assertEquals(before, totalEnergy(nodes));
    }

    @Test
    void aBankDischargesNoFasterThanTheCableCarries() {
        final List<FakeNode> nodes = new ArrayList<>();
        final FakeStorage bank = FakeStorage.battery(10_000_000, 10_000_000);
        final FakeStorage machine = FakeStorage.machine(10_000_000);
        nodes.add(new FakeNode(0, 25, 32_768, bank));
        nodes.add(new FakeNode(1, 25, 1_000_000, machine));
        final int before = totalEnergy(nodes);

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0, null, true, 4_096);

        assertEquals(4_096, result.discharged());
        assertEquals(4_096, result.delivered());
        assertEquals(before, totalEnergy(nodes));
    }

    @Test
    void theBufferCushionsASpikeWithoutRaisingTheRating() {
        final FakeStorage machine = FakeStorage.machine(10_000_000);
        final List<FakeNode> nodes = run(3, machine, 4_096);
        final int cushion = nodes.size() * 25;
        final int before = totalEnergy(nodes);

        int delivered = 0;
        for (int tick = 0; tick < 40; tick++) {
            delivered += FeGridSolver.solve(nodes, tick, null, true, 4_096).delivered();
            assertEquals(before, totalEnergy(nodes), "FE must be conserved on tick " + tick);
        }
        assertTrue(delivered <= 40 * 4_096 + cushion,
                "40 ticks of a 4,096 FE/t run delivered " + delivered);
        assertTrue(delivered >= 39 * 4_096,
                "and it must actually carry its rating, not throttle itself below it");
    }

    @Test
    void aZeroCeilingMovesNothingAndVoidsNothing() {
        final FakeStorage machine = FakeStorage.machine(10_000_000);
        final List<FakeNode> nodes = run(2, machine, 4_096);
        final int before = totalEnergy(nodes);

        final FeGridSolver.Result result = FeGridSolver.solve(nodes, 0, null, true, 0);

        assertEquals(0, result.delivered());
        assertEquals(0, result.collected());
        assertEquals(before, totalEnergy(nodes));
    }

    @Test
    void aGenerousCeilingIsIndistinguishableFromNone() {
        final FakeStorage machine = FakeStorage.machine(10_000_000);
        final FeGridSolver.Result free = FeGridSolver.solve(run(3, machine, 4_096), 0);
        final FakeStorage other = FakeStorage.machine(10_000_000);
        final FeGridSolver.Result capped =
                FeGridSolver.solve(run(3, other, 4_096), 0, null, true, 1_000_000);

        assertEquals(free.delivered(), capped.delivered());
        assertEquals(free.collected(), capped.collected());
    }

    @Test
    void collectSpreadsAcrossEverySourceWhenSupplyExceedsDemand() {
        final List<FakeNode> nodes = new ArrayList<>();
        final List<FakeStorage> panels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final FakeStorage panel = FakeStorage.source(4_000);
            panels.add(panel);
            nodes.add(new FakeNode(i, 0, 512, panel));
        }
        final FakeStorage machine = FakeStorage.machine(400);
        nodes.add(new FakeNode(10, 0, 4_096, machine));

        final FeGridSolver.Result r = FeGridSolver.solve(nodes, 0);

        assertEquals(400, r.delivered());
        assertEquals(400, machine.energy);
        for (FakeStorage panel : panels) {
            assertEquals(3_900, panel.energy, "every panel gives its share");
        }
    }

    @Test
    void theRemainderIsSweptFromWhoeverStillHasCharge() {
        final List<FakeNode> nodes = new ArrayList<>();
        final FakeStorage dry = FakeStorage.source(10);
        final FakeStorage full = FakeStorage.source(4_000);
        nodes.add(new FakeNode(0, 0, 512, dry));
        nodes.add(new FakeNode(1, 0, 512, full));
        final FakeStorage machine = FakeStorage.machine(400);
        nodes.add(new FakeNode(10, 0, 4_096, machine));

        final FeGridSolver.Result r = FeGridSolver.solve(nodes, 0);

        assertEquals(400, r.delivered());
        assertEquals(0, dry.energy);
        assertEquals(3_610, full.energy);
    }

    @Test
    void theSpreadNeverAsksPastASourcesRating() {
        final List<FakeNode> nodes = new ArrayList<>();
        final FakeStorage a = FakeStorage.source(4_000);
        final FakeStorage b = FakeStorage.source(4_000);
        nodes.add(new FakeNode(0, 0, 100, a));
        nodes.add(new FakeNode(1, 0, 100, b));
        final FakeStorage machine = FakeStorage.machine(10_000);
        nodes.add(new FakeNode(10, 0, 4_096, machine));

        final FeGridSolver.Result r = FeGridSolver.solve(nodes, 0);

        assertEquals(200, r.delivered());
        assertEquals(3_900, a.energy);
        assertEquals(3_900, b.energy);
    }
}
