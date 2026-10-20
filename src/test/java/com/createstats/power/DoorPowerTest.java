package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorPowerTest {
    private static final class Leaf implements IEnergyStorage {
        int energy;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int taken = Math.max(0, Math.min(maxReceive, DoorPower.BUFFER - energy));
            if (!simulate) {
                energy += taken;
            }
            return taken;
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
            return DoorPower.BUFFER;
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

    private record Node(long key, IEnergyStorage handler) implements FeGridNode {
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
            return DoorPower.TRANSFER_RATE;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return handler;
        }
    }

    private static int settle(Leaf leaf, java.util.function.IntUnaryOperator rating, int ticks) {
        final Node node = new Node(1L, leaf);
        final List<FeGridNode> loads = List.of(node);
        for (int tick = 0; tick < ticks; tick++) {
            final Map<Long, Integer> allowance = new HashMap<>();
            allowance.put(node.nodeKey(), rating.applyAsInt(leaf.energy));
            FeGridSolver.serveByPriority(loads, 4096, tick, null, allowance);
            leaf.energy = Math.max(0, leaf.energy - DoorPower.IDLE_DRAIN);
        }
        return leaf.energy;
    }

    @Test
    @DisplayName("rated at its standing drain, a door never banks a single FE")
    void standingDrainAloneStarvesTheDoor() {
        final Leaf leaf = new Leaf();

        assertEquals(0, settle(leaf, stored -> DoorPower.IDLE_DRAIN, 200),
                "a door served at exactly what it burns can never afford a cycle");
        assertTrue(!DoorPower.canAffordCycle(leaf.energy), "and so it can never open");
    }

    @Test
    @DisplayName("rated with its recharge, a door reaches its working reserve and stops asking")
    void rechargeReachesTheWorkingReserve() {
        final Leaf leaf = new Leaf();
        final int held = settle(leaf, DoorPower::ratedDrawFor, 200);
        assertTrue(held >= DoorPower.WORKING_RESERVE,
                "expected at least " + DoorPower.WORKING_RESERVE + " FE, held " + held);
        assertTrue(DoorPower.canAffordCycle(held), "and it can open");

        assertEquals(DoorPower.IDLE_DRAIN, DoorPower.ratedDrawFor(held));
    }

    @Test
    @DisplayName("the recharge is small, and it covers a cycle inside the pace it is written for")
    void theRechargeIsPacedNotGreedy() {
        assertEquals(DoorPower.IDLE_DRAIN + DoorPower.RECHARGE_DRAIN, DoorPower.ratedDrawFor(0));
        assertEquals(DoorPower.IDLE_DRAIN, DoorPower.ratedDrawFor(DoorPower.WORKING_RESERVE));
        assertTrue(DoorPower.RECHARGE_DRAIN * DoorPower.RECHARGE_TICKS >= DoorPower.CYCLE_COST,
                "a door that cannot recover a cycle in the pace it quotes is quoting the wrong pace");
        assertTrue(DoorPower.ratedDrawFor(0) < DoorPower.TRANSFER_RATE / 8,
                "the ask has to stay small enough that admitting a door is still nearly free");
    }

    @Test
    @DisplayName("one cycle is not enough to be a working door: open and shut costs two")
    void theWorkingReserveCoversBothHalvesOfOneUse() {
        assertTrue(DoorPower.WORKING_RESERVE >= DoorPower.CYCLE_COST * 2);
        assertTrue(DoorPower.BUFFER >= DoorPower.WORKING_RESERVE);
    }
}
