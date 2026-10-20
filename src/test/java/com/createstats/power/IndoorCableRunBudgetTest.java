package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndoorCableRunBudgetTest {
    private static final long NOW = 500L;

    @Test
    @DisplayName("one crossing spends one allowance, not both")
    void arrivingAndLeavingKeepSeparateAllowances() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(1_000);

        budget.spendIntake(NOW, 1_000);
        assertEquals(0, budget.intakeRemaining(NOW), "the run is full for the arriving direction");
        assertEquals(1_000, budget.outflowRemaining(NOW),
                "and can still hand every one of those FE on, which is the whole crossing");
    }

    @Test
    @DisplayName("each direction is still held to the run's rating on its own")
    void neitherDirectionMayExceedTheRating() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(1_000);

        budget.spendOutflow(NOW, 600);
        assertEquals(400, budget.outflowRemaining(NOW));
        budget.spendOutflow(NOW, 999);
        assertEquals(0, budget.outflowRemaining(NOW), "never negative, and never past the rating");
    }

    @Test
    @DisplayName("both allowances refill on the first call of a new tick")
    void bothRefillOnTheNextTick() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(256);
        budget.spendIntake(NOW, 256);
        budget.spendOutflow(NOW, 256);

        assertEquals(0, budget.intakeRemaining(NOW));
        assertEquals(256, budget.intakeRemaining(NOW + 1));
        assertEquals(256, budget.outflowRemaining(NOW + 1));
    }

    @Test
    @DisplayName("a run rated at nothing moves nothing in either direction")
    void aZeroRatedRunIsShut() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(0);
        assertEquals(0, budget.ratePerTick());
        assertEquals(0, budget.intakeRemaining(NOW));
        assertEquals(0, budget.outflowRemaining(NOW));
    }

    @Test
    @DisplayName("the throttle spends the direction the wire sees, not the one the machine sees")
    void throttleSpendsFromTheWiresPointOfView() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(1_000);
        final Box machine = new Box(2_000, 5_000, true, true);
        final IndoorCableRunBudget.Throttled throttled =
                new IndoorCableRunBudget.Throttled(machine, budget, () -> NOW);

        assertEquals(400, throttled.receiveEnergy(400, false));
        assertEquals(600, budget.outflowRemaining(NOW));
        assertEquals(1_000, budget.intakeRemaining(NOW), "arriving FE has its own allowance");

        assertEquals(400, throttled.extractEnergy(400, false));
        assertEquals(600, budget.intakeRemaining(NOW));
        assertEquals(600, budget.outflowRemaining(NOW), "untouched by the second transfer");
    }

    @Test
    @DisplayName("a supply and a load on one run share the rating rather than halving it")
    void aRunFedByAMachineStillCarriesItsRating() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(1_000);
        final Box meter = new Box(50_000, 50_000, true, false);
        final Box machine = new Box(0, 50_000, false, true);
        final var supply = new IndoorCableRunBudget.Throttled(meter, budget, () -> NOW);
        final var load = new IndoorCableRunBudget.Throttled(machine, budget, () -> NOW);

        assertEquals(1_000, supply.extractEnergy(1_000, false), "the run takes its full rating in");
        assertEquals(1_000, load.receiveEnergy(1_000, false), "and hands the same FE straight on");
    }

    @Test
    @DisplayName("a simulated transfer books nothing")
    void simulatingSpendsNothing() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(1_000);
        final Box machine = new Box(2_000, 5_000, true, true);
        final var throttled = new IndoorCableRunBudget.Throttled(machine, budget, () -> NOW);

        throttled.receiveEnergy(400, true);
        throttled.extractEnergy(400, true);
        assertEquals(1_000, budget.intakeRemaining(NOW));
        assertEquals(1_000, budget.outflowRemaining(NOW));
    }

    @Test
    @DisplayName("the machine's own charge and capacity are reported unthrottled")
    void theMachinesFiguresAreItsOwn() {
        final IndoorCableRunBudget budget = new IndoorCableRunBudget(10);
        final Box machine = new Box(4_000, 5_000, true, true);
        final var throttled = new IndoorCableRunBudget.Throttled(machine, budget, () -> NOW);

        assertEquals(4_000, throttled.getEnergyStored(), "a full battery must not look nearly empty");
        assertEquals(5_000, throttled.getMaxEnergyStored());
    }

    private static final class Box implements net.neoforged.neoforge.energy.IEnergyStorage {
        private int energy;
        private final int capacity;
        private final boolean give;
        private final boolean take;

        Box(int energy, int capacity, boolean give, boolean take) {
            this.energy = energy;
            this.capacity = capacity;
            this.give = give;
            this.take = take;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.max(0, Math.min(maxReceive, capacity - energy));
            if (!take || room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int given = Math.max(0, Math.min(maxExtract, energy));
            if (!give || given <= 0) {
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
            return give;
        }

        @Override
        public boolean canReceive() {
            return take;
        }
    }
}
