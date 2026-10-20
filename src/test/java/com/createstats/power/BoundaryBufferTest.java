package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryBufferTest {
    private static final int PLENTY = 1_000_000;

    private static int runTick(LimiterBudget budget, int cap, long now) {
        budget.roll(now);
        final int out = budget.outflowAvailable(PLENTY, cap);
        budget.bookOutflow(out);
        budget.bookIntake(budget.intakeRoom(PLENTY, cap));
        return out;
    }

    @Test
    @DisplayName("a cap above the buffer buys nothing: the block passes the buffer")
    void theBufferIsTheCeiling() {
        final LimiterBudget budget = new LimiterBudget(8_000);
        final int cap = 16_384;

        runTick(budget, cap, 1L);
        assertEquals(8_000, runTick(budget, cap, 2L), "the buffer, not the cap");
        assertEquals(8_000, runTick(budget, cap, 3L), "and it stays there");
    }

    @Test
    @DisplayName("a buffer sized to the cap passes the cap")
    void aSizedBufferPassesTheCap() {
        final LimiterBudget budget = new LimiterBudget(8_000);
        final int cap = 16_384;
        budget.setCapacity(Math.max(8_000, cap));

        runTick(budget, cap, 1L);
        assertEquals(cap, runTick(budget, cap, 2L));
    }

    @Test
    @DisplayName("the cap still holds when it is the smaller of the two")
    void theCapStillHolds() {
        final LimiterBudget budget = new LimiterBudget(8_000);
        final int cap = 1_024;
        budget.setCapacity(Math.max(8_000, cap));

        runTick(budget, cap, 1L);
        assertEquals(cap, runTick(budget, cap, 2L));
        assertEquals(8_000, budget.capacity(), "and the cushion is still the floor, not the setting");
    }

    @Test
    @DisplayName("shrinking the buffer does not delete what is in it")
    void shrinkingKeepsTheCharge() {
        final LimiterBudget budget = new LimiterBudget(16_384);
        budget.roll(1L);
        budget.bookIntake(budget.intakeRoom(PLENTY, 16_384));
        assertEquals(16_384, budget.energy());

        budget.setCapacity(8_000);
        assertEquals(16_384, budget.energy(), "still there");
        assertEquals(16_384, budget.capacity(), "and reported as holdable, or the handler is invalid");
        assertEquals(0, budget.room(PLENTY), "nothing more may arrive until it drains");

        budget.roll(2L);
        budget.bookOutflow(budget.outflowAvailable(PLENTY, 8_000));
        assertEquals(8_384, budget.energy());
        assertEquals(8_384, budget.capacity());
    }

    @Test
    @DisplayName("a shut box still reports a capacity, or it stops being one of ours")
    void aShutBoxStillHasCapacity() {
        final LimiterBudget budget = new LimiterBudget(8_000);
        budget.setCapacity(Math.max(8_000, 0));
        assertEquals(8_000, budget.capacity());
    }
}
