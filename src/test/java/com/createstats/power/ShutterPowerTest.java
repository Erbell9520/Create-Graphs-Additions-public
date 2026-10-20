package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutterPowerTest {
    private record Node(long key, IEnergyStorage handler) implements FeGridNode {
        @Override
        public long nodeKey() {
            return this.key;
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
            return 128;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return this.handler;
        }
    }

    private static final class Unit implements IEnergyStorage {
        int energy;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int accepted = Math.max(0, Math.min(maxReceive, 240 - this.energy));
            if (!simulate) {
                this.energy += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return this.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return 240;
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

    @Test
    @DisplayName("the recovered doubles still agree with the constants they were built against")
    void doublesMatchTheEconomy() {
        assertEquals(ShutterPower.BUFFER, new Unit().getMaxEnergyStored(),
                "Unit's hard-coded capacity no longer matches ShutterPower.BUFFER");
        assertEquals(ShutterPower.TRANSFER_RATE, new Node(0L, new Unit()).transferRate(),
                "Node's hard-coded transfer rate no longer matches ShutterPower.TRANSFER_RATE");
    }

    @Test
    @DisplayName("a node reports no storage of its own, only an attached buffer")
    void bufferIsCountedOnce() {
        final Unit unit = new Unit();
        final Node node = new Node(1L, unit);
        assertEquals(0, node.storedEnergy(), "the node claimed storage the solve would double count");
        assertEquals(0, node.bufferCapacity(), "the node claimed capacity the solve would double count");
        node.setStoredEnergy(999);
        assertEquals(0, node.storedEnergy(), "setStoredEnergy is meant to be inert on a node");
        assertEquals(0, unit.getEnergyStored(), "setStoredEnergy must not reach the real buffer");
        assertEquals(ShutterPower.BUFFER, node.attachedStorage().getMaxEnergyStored());
    }

    @Test
    @DisplayName("a shutter's buffer fills to exactly three cycles and no further")
    void bufferFillsToThreeCycles() {
        final Unit unit = new Unit();
        assertEquals(ShutterPower.BUFFER, unit.receiveEnergy(Integer.MAX_VALUE, false));
        assertEquals(ShutterPower.BUFFER, unit.getEnergyStored());
        assertEquals(0, unit.receiveEnergy(1, false), "a full buffer kept accepting");
        assertEquals(3 * ShutterPower.CYCLE_COST, ShutterPower.BUFFER,
                "the buffer is meant to be three whole cycles");
    }

    @Test
    @DisplayName("simulating a receive changes nothing")
    void simulateDoesNotCharge() {
        final Unit unit = new Unit();
        assertEquals(100, unit.receiveEnergy(100, true));
        assertEquals(0, unit.getEnergyStored(), "a simulated receive charged the buffer");
    }

    @Test
    @DisplayName("a shutter can afford a move at one cycle and not a unit below it")
    void affordability() {
        assertTrue(ShutterPower.canAffordCycle(ShutterPower.CYCLE_COST));
        assertFalse(ShutterPower.canAffordCycle(ShutterPower.CYCLE_COST - 1));
        assertFalse(ShutterPower.canAffordCycle(0));
    }

    @Test
    @DisplayName("an empty shutter rates its own recharge, and a full one stops asking")
    void ratingIncludesGettingBackToUseful() {
        assertTrue(ShutterPower.ratedDrawFor(0) > ShutterPower.IDLE_DRAIN,
                "an empty shutter did not ask for enough to become able to work");
        assertEquals(ShutterPower.IDLE_DRAIN + ShutterPower.RECHARGE_DRAIN,
                ShutterPower.ratedDrawFor(0));
        assertEquals(ShutterPower.IDLE_DRAIN, ShutterPower.ratedDrawFor(ShutterPower.BUFFER),
                "a full shutter kept asking for a recharge");
        assertEquals(ShutterPower.IDLE_DRAIN,
                ShutterPower.ratedDrawFor(ShutterPower.WORKING_RESERVE),
                "the ask should stop exactly at the working reserve");
        assertTrue(ShutterPower.ratedDrawFor(ShutterPower.WORKING_RESERVE - 1)
                        > ShutterPower.IDLE_DRAIN,
                "one below the reserve should still be asking");
    }

    @Test
    @DisplayName("a shutter costs half a door to move, and neither number drifted")
    void halfADoor() {
        assertEquals(DoorPower.CYCLE_COST / 2, ShutterPower.CYCLE_COST);
        assertEquals(DoorPower.TRANSFER_RATE, ShutterPower.TRANSFER_RATE);
        assertEquals(DoorPower.RECHARGE_TICKS, ShutterPower.RECHARGE_TICKS);
    }

    @Test
    @DisplayName("the two block figures are exactly the constants every shutter already runs on")
    void twoBlocksIsUnchanged() {
        final int min = com.createstats.block.BlastShutterBlock.MIN_HEIGHT;
        assertEquals(ShutterPower.CYCLE_COST, ShutterPower.cycleCostFor(min));
        assertEquals(ShutterPower.BUFFER, ShutterPower.bufferFor(min));
        assertEquals(ShutterPower.WORKING_RESERVE, ShutterPower.workingReserveFor(min));
        assertEquals(ShutterPower.RECHARGE_DRAIN, ShutterPower.rechargeDrainFor(min));
        assertEquals(ShutterPower.ratedDrawFor(0), ShutterPower.ratedDrawFor(0, min));
        assertEquals(ShutterPower.ratedDrawFor(ShutterPower.BUFFER),
                ShutterPower.ratedDrawFor(ShutterPower.BUFFER, min));
        assertTrue(ShutterPower.canAffordCycle(ShutterPower.CYCLE_COST, min));
        assertFalse(ShutterPower.canAffordCycle(ShutterPower.CYCLE_COST - 1, min));
    }

    @Test
    @DisplayName("height costs energy, and the buffer grows with it so a tall shutter can still work")
    void heightCostsEnergy() {
        final int min = com.createstats.block.BlastShutterBlock.MIN_HEIGHT;
        final int max = com.createstats.block.BlastShutterBlock.MAX_HEIGHT;
        int previous = ShutterPower.cycleCostFor(min);
        for (int height = min + 1; height <= max; height++) {
            final int cost = ShutterPower.cycleCostFor(height);
            assertTrue(cost > previous,
                    "height " + height + " costs " + cost + ", no more than " + previous);

            assertTrue(ShutterPower.bufferFor(height) >= cost,
                    "height " + height + " cannot hold one cycle");
            assertTrue(ShutterPower.workingReserveFor(height) >= 2 * cost,
                    "height " + height + " cannot hold a seal and the open after it");
            assertTrue(ShutterPower.bufferFor(height) >= ShutterPower.workingReserveFor(height),
                    "height " + height + " has a reserve its buffer cannot reach");
            assertTrue(ShutterPower.rechargeDrainFor(height) >= 1,
                    "height " + height + " recharges at nothing a tick and never fills");
            previous = cost;
        }

        assertEquals(ShutterPower.cycleCostFor(min), ShutterPower.cycleCostFor(0));
        assertEquals(ShutterPower.cycleCostFor(min), ShutterPower.cycleCostFor(-3));
    }
}
