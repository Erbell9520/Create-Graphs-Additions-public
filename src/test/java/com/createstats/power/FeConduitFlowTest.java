package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeConduitFlowTest {
    private static final class Rig {
        final Node source = new Node(1L, Store.source(10_000));
        final Node machine = new Node(2L, Store.machine(10_000));
        final Node cableA = new Node(10L, null);
        final Node cableB = new Node(11L, null);
        final Node junction = new Node(12L, null);
        final Map<Long, Integer> flows = new HashMap<>();

        List<FeGridNode> nodes() {
            return List.of(source, cableA, junction, cableB, machine);
        }

        FeGridSolver.Result solve() {
            flows.clear();
            return FeGridSolver.solve(nodes(), 0, flows);
        }
    }

    @Test
    @DisplayName("the solver records no flow for conduit, which is why a wire readout built on it reads zero")
    void conduitIsAbsentFromTheFlowMap() {
        final Rig rig = new Rig();
        final FeGridSolver.Result result = rig.solve();

        assertTrue(result.delivered() > 0, "the machine has to have been served for this to mean anything");

        assertTrue(rig.flows.containsKey(rig.source.nodeKey()));
        assertTrue(rig.flows.containsKey(rig.machine.nodeKey()));

        assertFalse(rig.flows.containsKey(rig.cableA.nodeKey()));
        assertFalse(rig.flows.containsKey(rig.cableB.nodeKey()));
        assertFalse(rig.flows.containsKey(rig.junction.nodeKey()));
        assertEquals(0, rig.flows.getOrDefault(rig.junction.nodeKey(), 0),
                "a junction box reading its own lastFlow can only ever print 0");
    }

    @Test
    @DisplayName("the circuit's own figure is nonzero on the same solve, and is what conduit must show")
    void circuitThroughputIsTheHonestFigure() {
        final Rig rig = new Rig();
        final FeGridSolver.Result result = rig.solve();

        assertTrue(result.consumed() > 0);
        assertEquals(result.delivered() + result.charged(), result.consumed());

        assertEquals(rig.machine.attached.energy, result.delivered());
    }

    @Test
    @DisplayName("an idle circuit reports zero, so the fix cannot make a dead run look alive")
    void idleCircuitStaysZero() {
        final Rig rig = new Rig();
        rig.machine.attached.energy = rig.machine.attached.capacity;
        final FeGridSolver.Result result = rig.solve();

        assertEquals(0, result.delivered());
        assertEquals(0, result.consumed());
    }

    @Test
    @DisplayName("a managed grid's own result is all zeroes, so its manager has to report for it")
    void observeReportsNoMovementAtAll() {
        final Rig rig = new Rig();

        final FeGridSolver.Result managed = FeGridSolver.observe(rig.nodes());

        assertTrue(managed.demand() > 0, "the machine is still asking, which is the point");
        assertEquals(0, managed.delivered());
        assertEquals(0, managed.charged());
        assertEquals(0, managed.consumed(),
                "so a conduit readout falling back on the solve result reads zero on a managed circuit");

        assertEquals(0, rig.machine.attached.energy);
    }

    @Test
    @DisplayName("a circuit that cannot meet its demand reports a shortfall, which is the starved code")
    void shortfallIsVisibleToTheBox() {
        final Rig rig = new Rig();
        rig.source.attached.energy = 40;
        final FeGridSolver.Result result = rig.solve();

        assertTrue(result.demand() > result.delivered());
        assertTrue(result.shortfall() > 0);
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
