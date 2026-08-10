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

/** [1] */
class FeGridSolverTest {

    /** A plain FE box. {@code extractable} and {@code receivable} model the four kinds of block. */
    private static final class FakeStorage implements IEnergyStorage {
        int energy;
        final int capacity;
        final boolean extractable;
        final boolean receivable;
        /** Caps a single transfer, to model rate-limited third-party handlers. */
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
        /** Set when the load's handler is a real FeConsumer, which is how priority is carried. */
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

    /** Total FE anywhere in the scenario: node buffers plus every attached block. */
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

    /** A run of connectors: node 0 on a source, the last on a machine, the rest plain wire. */
    private static List<FakeNode> chain(int length, @Nullable FakeStorage head, @Nullable FakeStorage tail) {
        final List<FakeNode> nodes = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            final FakeStorage attached = i == 0 ? head : i == length - 1 ? tail : null;
            nodes.add(new FakeNode(i, 16_000, 4_096, attached));
        }
        return nodes;
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

    /** [2] */
    @Test
    void aThinWireStillCarriesAFatLoad() {
        final FakeStorage battery = FakeStorage.battery(50_000, 50_000);
        final FakeStorage machine = FakeStorage.machine(50_000);
        final List<FakeNode> nodes = new ArrayList<>();
        // Three segments of 25 FE wire: 75 FE of buffer on the whole grid.
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
        // The headline fix. Under pairwise diffusion a ten-node run needed ten ticks to get any
        // energy to the far end, and halved it at every hop.
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
        // Under diffusion the same layout moved power at different speeds depending on which end
        // ticked first. Reversing the node list must now change nothing.
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
        // 1000 FE and three machines that would each swallow everything. Nobody may be starved.
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
        // With an odd amount to split, someone gets the remainder. It must not be the same node
        // every tick, or one machine browns out permanently on a marginal grid.
        List<Integer> firstServed = new ArrayList<>();
        for (int cursor = 0; cursor < 3; cursor++) {
            List<FakeNode> nodes = new ArrayList<>();
            nodes.add(new FakeNode(0, 16_000, 4_096, FakeStorage.source(10)));
            final List<FakeStorage> machines = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                // Rate 4 each, so 10 FE cannot satisfy all three and order becomes visible.
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
        // Battery listed first on purpose: priority must come from role, not from list position.
        nodes.add(new FakeNode(1, 16_000, 4_096, battery));
        nodes.add(new FakeNode(2, 16_000, 4_096, machine));

        FeGridSolver.solve(nodes, 0);
        assertEquals(3_000, machine.energy, "the machine takes the whole supply first");
        assertEquals(0, battery.energy, "storage only sees genuine surplus");
    }

    @Test
    void anIdleBatteryIsNeverDrainedToFillWire() {
        // [3]
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
        // No generator, so the machine has to come off the battery, and only by what it asks for.
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
    void surplusGenerationChargesStorageOnceTheGridIsFull() {
        FakeStorage source = FakeStorage.source(1_000_000);
        FakeStorage battery = FakeStorage.battery(0, 500_000);
        List<FakeNode> nodes = new ArrayList<>();
        nodes.add(new FakeNode(0, 16_000, 4_096, source));
        nodes.add(new FakeNode(1, 16_000, 4_096, battery));
        final int before = totalEnergy(nodes);

        // Early ticks fill the wire buffer and leave storage alone.
        FeGridSolver.solve(nodes, 0);
        assertEquals(0, battery.energy, "the grid buffer fills before storage sees anything");

        for (int tick = 1; tick < 40; tick++) {
            FeGridSolver.solve(nodes, tick);
            assertEquals(before, totalEnergy(nodes), "FE conserved on tick " + tick);
        }
        assertEquals(32_000, nodes.get(0).storedEnergy() + nodes.get(1).storedEnergy(),
                "the wire buffer stays full");
        assertTrue(battery.energy > 0, "genuine surplus reaches storage");
    }

    @Test
    void aRateLimitedSourceCannotBeOverDrawn() {
        // Third-party handlers whose real transfer is smaller than what was asked for must not let
        // the grid credit itself with energy the source never gave up.
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
        // Two passes per solve, so the sink takes its limit twice per tick and the rest is buffered.
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

    // tier-aware branch solve (Power Distribution Unit)

    /** Minimal tuning so a real FeConsumer can stand in as a branch load. */
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

    /**
     * A branch load. Priority is read off the attached handler, and for real consumers that handler
     * <b>is</b> the FeConsumer, so the test attaches a genuine one rather than a fake.
     */
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
                load(0, FePriority.OPTIONAL, fe, 1),   // listed first on purpose
                load(1, FePriority.CRITICAL, fe, 0));

        // 3,000 FE, and the critical load alone can take a full node rate of 4,096.
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
        // Priority must come from the tier, never from position in the list.
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
        // The distinction that keeps a working grid from looking broken: nothing was served, so
        // nothing was *chosen* to be dropped. That is a plain outage, not load shedding.
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

        // Now supply plenty. The previously shed load must clear its flag, not stay latched.
        FeGridSolver.serveByPriority(loads, 50_000, 1);
        assertFalse(fe[1].isLoadShed(), "the flag clears once the tier is served again");
    }

    @Test
    void thirdPartyMachinesAreNeverShedAndSitAtTheDefaultTier() {
        // [4]
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

    // per-node signed flow (the goggle readout on PowerConnectorBE)

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
        // [5]
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
        // Discharging to cover a shortfall reads negative; charging from genuine surplus reads
        // positive. Same node, opposite tick, opposite sign.
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
        // Fill the wire buffers first; storage only charges once the grid itself is full.
        for (int tick = 0; tick < 10; tick++) {
            FeGridSolver.solve(charging, tick);
        }
        chargeFlow.clear();
        FeGridSolver.solve(charging, 10, chargeFlow);
        assertTrue(chargeFlow.getOrDefault(21L, 0) > 0, "surplus charges the battery: positive");
    }

    @Test
    void demandAndShortfallExposeAnUnderProvisionedGrid() {
        // A grid that is keeping up must report no shortfall; one that cannot must report the gap
        // even though nothing has browned out yet. That gap is the early warning.
        FakeStorage source = FakeStorage.source(1_000_000);
        FakeStorage machine = FakeStorage.machine(1_000_000);
        List<FakeNode> healthy = chain(3, source, machine);
        FeGridSolver.Result ok = FeGridSolver.solve(healthy, 0);
        assertEquals(4_096, ok.demand(), "the machine asked for a full node rate");
        assertEquals(0, ok.shortfall(), "and got it");

        // Now starve it: the source can only give 500 per tick.
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
    void enduranceCountsDownOnlyWhileTheGridIsDraining() {
        // Filling or steady: no meaningful countdown.
        FeGridSolver.Result filling = new FeGridSolver.Result(1_000, 0, 50_000, 100_000, 0, 0);
        assertEquals(Integer.MAX_VALUE, filling.enduranceTicks());
        FeGridSolver.Result steady = new FeGridSolver.Result(400, 400, 50_000, 100_000, 400, 0);
        assertEquals(Integer.MAX_VALUE, steady.enduranceTicks());

        // Draining 500 FE/t out of a 50,000 buffer is 100 ticks, five seconds.
        FeGridSolver.Result draining = new FeGridSolver.Result(0, 500, 50_000, 100_000, 500, 0);
        assertEquals(100, draining.enduranceTicks());
        assertEquals(-500, draining.net());
    }
}
