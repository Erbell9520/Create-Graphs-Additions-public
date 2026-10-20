package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiterBudgetTest {
    private static final int CAPACITY = 8000;

    @Test
    @DisplayName("intake and outflow are capped separately, so the setting is not halved")
    void eachDirectionGetsItsOwnAllowance() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        budget.setEnergy(4000);

        assertEquals(1000, budget.intakeRoom(9999, 1000), "the whole intake allowance is available");
        budget.bookIntake(1000);
        assertEquals(0, budget.intakeRoom(9999, 1000), "and then it is spent");

        assertEquals(1000, budget.outflowAvailable(9999, 1000),
                "outflow is untouched by what came in");
        budget.bookOutflow(1000);
        assertEquals(0, budget.outflowAvailable(9999, 1000));
    }

    @Test
    @DisplayName("the allowance refills on a new tick and not before")
    void allowanceRefillsPerTick() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        budget.setEnergy(CAPACITY);
        budget.bookOutflow(budget.outflowAvailable(9999, 500));
        assertEquals(0, budget.outflowAvailable(9999, 500));

        assertNull(budget.roll(1L), "the same tick again is not a new tick");
        assertEquals(0, budget.outflowAvailable(9999, 500));

        final LimiterBudget.Tick closed = budget.roll(2L);
        assertNotNull(closed);
        assertEquals(500, closed.moved(), "the closed tick reports what actually left");
        assertEquals(500, budget.outflowAvailable(9999, 500), "a new tick refills the allowance");
    }

    @Test
    @DisplayName("the first roll closes nothing, because there is no tick behind it")
    void firstRollClosesNothing() {
        assertNull(new LimiterBudget(CAPACITY).roll(7L));
    }

    @Test
    @DisplayName("a cap of zero passes nothing in either direction")
    void shutPassesNothing() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        budget.setEnergy(4000);
        assertEquals(0, budget.intakeRoom(9999, 0));
        assertEquals(0, budget.outflowAvailable(9999, 0));
        assertEquals(4000, budget.energy(), "and the buffer is held rather than voided");
    }

    @Test
    @DisplayName("the buffer never overfills or goes negative, whatever it is handed")
    void bufferStaysInRange() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);

        assertEquals(CAPACITY, budget.bookIntake(Integer.MAX_VALUE));
        assertEquals(CAPACITY, budget.energy());
        assertEquals(CAPACITY, budget.bookOutflow(Integer.MAX_VALUE));
        assertEquals(0, budget.energy());
        assertEquals(0, budget.bookOutflow(500), "an empty buffer gives nothing");
    }

    @Test
    @DisplayName("a saved buffer larger than the block is clamped rather than trusted")
    void loadClampsEnergy() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.setEnergy(999_999);
        assertEquals(CAPACITY, budget.energy());
        budget.setEnergy(-5);
        assertEquals(0, budget.energy());
    }

    @Test
    @DisplayName("clipped means the cap bit, not that the buffer was empty")
    void clippedMeansTheCapBit() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        budget.setEnergy(4000);

        budget.noteClipped(budget.stored(9999), budget.outflowAvailable(9999, 100));
        assertTrue(budget.clipped());

        final LimiterBudget quiet = new LimiterBudget(CAPACITY);
        quiet.roll(1L);
        quiet.setEnergy(50);

        quiet.noteClipped(quiet.stored(9999), quiet.outflowAvailable(9999, 100));
        assertFalse(quiet.clipped(), "a setting above the traffic is not limiting anything");
    }

    @Test
    @DisplayName("the clip flag belongs to its own tick")
    void clipFlagResetsPerTick() {
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        budget.setEnergy(4000);
        budget.noteClipped(4000, 100);
        assertTrue(budget.roll(2L).limited());
        assertFalse(budget.clipped(), "the new tick starts clean");
    }

    @Test
    @DisplayName("no more than the cap crosses in a tick, in either direction, under any order")
    void capHoldsUnderAnyOrderOfCallers() {
        final Random random = new Random(20260831L);
        for (int trial = 0; trial < 500; trial++) {
            final int cap = 1 + random.nextInt(3000);
            final LimiterBudget budget = new LimiterBudget(CAPACITY);
            final int startingEnergy = random.nextInt(CAPACITY + 1);
            budget.setEnergy(startingEnergy);
            budget.roll(1L);

            int movedIn = 0;
            int movedOut = 0;
            for (int call = 0; call < 24; call++) {
                final int ask = random.nextInt(CAPACITY * 2);
                if (random.nextBoolean()) {
                    final int simulated = budget.intakeRoom(ask, cap);
                    if (random.nextBoolean()) {
                        movedIn += budget.bookIntake(simulated);
                    }
                } else {
                    final int simulated = budget.outflowAvailable(ask, cap);
                    if (random.nextBoolean()) {
                        movedOut += budget.bookOutflow(simulated);
                    }
                }
            }

            assertTrue(movedIn <= cap,
                    "trial " + trial + ": took in " + movedIn + " against a cap of " + cap);
            assertTrue(movedOut <= cap,
                    "trial " + trial + ": gave out " + movedOut + " against a cap of " + cap);
            assertEquals(startingEnergy + movedIn - movedOut, budget.energy(),
                    "trial " + trial + ": FE was created or destroyed");
            assertEquals(movedIn, budget.taken());
            assertEquals(movedOut, budget.given());
            assertEquals(movedOut, budget.roll(2L).moved());
        }
    }

    @Test
    @DisplayName("sustained throughput over many ticks is the cap and never above it")
    void sustainedThroughputIsTheCap() {
        final int cap = 640;
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        long total = 0L;
        for (long tick = 1; tick <= 200; tick++) {
            final LimiterBudget.Tick closed = budget.roll(tick);
            if (closed != null) {
                total += closed.moved();
            }
            budget.bookIntake(budget.intakeRoom(Integer.MAX_VALUE, cap));
            budget.bookOutflow(budget.outflowAvailable(Integer.MAX_VALUE, cap));
        }

        assertEquals(199L * cap, total, "throughput settles at exactly the cap, and never above it");
        assertEquals(cap, budget.given(), "the tick still open carried the cap too");
    }

    @Test
    @DisplayName("a manager moving FE past the block spends the same allowance its ports do")
    void passThroughSpendsTheOutflowAllowance() {
        final int cap = 500;
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        assertEquals(0, budget.energy(), "nothing was ever put in the buffer");
        assertEquals(300, budget.bookPassThrough(300, cap), "the first delivery fits under the cap");
        assertEquals(300, budget.given(), "and is booked as having crossed");
        assertEquals(0, budget.energy(), "without the buffer moving");
        assertEquals(200, budget.bookPassThrough(400, cap), "the second is clipped to what is left");
        assertEquals(cap, budget.given(), "so the tick carries exactly the cap");
        assertEquals(0, budget.bookPassThrough(100, cap), "and nothing more crosses this tick");
    }

    @Test
    @DisplayName("board traffic and port traffic share one allowance")
    void passThroughAndPortsShareTheCap() {
        final int cap = 400;
        final LimiterBudget budget = new LimiterBudget(CAPACITY);
        budget.roll(1L);
        budget.bookIntake(budget.intakeRoom(1_000, cap));
        assertEquals(250, budget.bookPassThrough(250, cap), "the board moves its share first");
        assertEquals(150, budget.outflowAvailable(1_000, cap),
                "the port may only move what the board left");
        budget.bookOutflow(budget.outflowAvailable(1_000, cap));
        assertEquals(cap, budget.given(), "and the two together are exactly the cap");
    }
}
