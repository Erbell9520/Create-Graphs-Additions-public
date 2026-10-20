package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedBranchCapTest {
    private static final int CAP = 1_024;
    private static final long BOX = 4_242L;
    private static final long TICK = 7L;

    private static final class Box {
        private final LimiterBudget budget = new LimiterBudget(8_000);

        void openTick(long now) {
            budget.roll(now);
        }

        int managedThroughputCeiling() {
            return Math.max(0, CAP - budget.given());
        }

        void recordManagedTransfer(int fe, boolean clipped) {
            final int booked = budget.bookPassThrough(Math.max(0, fe), CAP);
            budget.noteClipped(fe, booked);
            budget.noteClipped(clipped);
        }

        int reading() {
            return budget.given();
        }

        boolean throttling() {
            return budget.clipped();
        }
    }

    private static int deliver(int offered) {
        return Math.max(0, offered);
    }

    @Test
    @DisplayName("the old way let every pass in the tick move the whole setting")
    void everyPassUsedToGetTheWholeSetting() {
        final Box box = new Box();
        box.openTick(TICK);

        int crossed = 0;
        for (int pass = 0; pass < 3; pass++) {
            crossed += deliver(Math.min(9_999, box.managedThroughputCeiling()));
        }

        box.recordManagedTransfer(crossed, false);

        assertEquals(3 * CAP, crossed,
                "the overshoot is exactly the number of passes times the setting, which is the diagnosis");
        assertEquals(CAP, box.reading(),
                "and the block reports the setting throughout, because bookPassThrough clamps");
        assertTrue(box.throttling(), "wearing the LIM flag while passing three times the cap");
    }

    @Test
    @DisplayName("the ledger holds the whole tick to the setting, however many passes there are")
    void theLedgerHoldsTheWholeTick() {
        final Box box = new Box();
        final FeInstrumentBudget ledger = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        box.openTick(TICK);

        int crossed = 0;
        for (int pass = 0; pass < 3; pass++) {
            final int available = 9_999;
            final int offered = Math.min(available,
                    ledger.remaining(caps, at -> box.managedThroughputCeiling(), TICK));
            final int got = deliver(offered);
            ledger.spend(caps, got, available > offered && got >= offered, TICK);
            crossed += got;
        }
        box.recordManagedTransfer(crossed, ledger.clipped(BOX, TICK));

        assertEquals(CAP, crossed, "one setting for the tick, not one per pass");
        assertEquals(crossed, box.reading(), "and the reading is now what actually crossed");
    }

    @Test
    @DisplayName("a box holding a branch back still says so once it is being obeyed")
    void theBoxStillReportsThrottling() {
        final Box box = new Box();
        final FeInstrumentBudget ledger = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        box.openTick(TICK);

        final int available = 5_568;
        final int offered = Math.min(available,
                ledger.remaining(caps, at -> box.managedThroughputCeiling(), TICK));
        final int got = deliver(offered);
        ledger.spend(caps, got, available > offered && got >= offered, TICK);
        box.recordManagedTransfer(got, ledger.clipped(BOX, TICK));

        assertEquals(CAP, got);
        assertTrue(box.throttling(), "the branch wanted 5,568 and got the setting: that is throttling");
    }

    @Test
    @DisplayName("a branch that wants less than the setting is not reported as throttled")
    void anEasyBranchIsNotThrottled() {
        final Box box = new Box();
        final FeInstrumentBudget ledger = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        box.openTick(TICK);

        final int available = 400;
        final int offered = Math.min(available,
                ledger.remaining(caps, at -> box.managedThroughputCeiling(), TICK));
        final int got = deliver(offered);
        ledger.spend(caps, got, available > offered && got >= offered, TICK);
        box.recordManagedTransfer(got, ledger.clipped(BOX, TICK));

        assertEquals(400, got);
        assertEquals(400, box.reading());
        assertFalse(box.throttling(), "nothing was held back, so the screen says PASS");
    }

    @Test
    @DisplayName("the next tick reopens the allowance on both sides")
    void theNextTickReopensBoth() {
        final Box box = new Box();
        final FeInstrumentBudget ledger = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);

        box.openTick(TICK);
        ledger.spend(caps, CAP, true, TICK);
        box.recordManagedTransfer(CAP, true);
        assertEquals(0, ledger.remaining(caps, at -> box.managedThroughputCeiling(), TICK));

        box.openTick(TICK + 1);
        assertEquals(CAP, ledger.remaining(caps, at -> box.managedThroughputCeiling(), TICK + 1),
                "a new tick is a new setting, on the ledger and on the block alike");
        assertFalse(box.throttling(), "and a clean LIM flag");
    }

    @Test
    @DisplayName("a shut box passes nothing however many passes ask")
    void aShutBoxPassesNothing() {
        final FeInstrumentBudget ledger = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        for (int pass = 0; pass < 3; pass++) {
            assertEquals(0, ledger.remaining(caps, at -> 0, TICK));
        }
    }
}
