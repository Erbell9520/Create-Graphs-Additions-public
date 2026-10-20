package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntakeLedgerTest {
    @Test
    void theReadingIsTheLastCompletedTickWhateverTheOrder() {
        final IntakeLedger a = new IntakeLedger();

        a.record(100, 300);
        a.record(100, 212);

        assertEquals(512, a.completedBefore(101));
        a.record(101, 512);
        assertEquals(512, a.completedBefore(102));

        final IntakeLedger b = new IntakeLedger();

        assertEquals(0, b.completedBefore(100));
        b.record(100, 300);
        b.record(100, 212);
        assertEquals(512, b.completedBefore(101));
    }

    @Test
    void aTickWithNothingReadsNothing() {
        final IntakeLedger l = new IntakeLedger();
        l.record(10, 64);
        assertEquals(64, l.completedBefore(11));
        assertEquals(0, l.completedBefore(12), "tick 11 brought nothing");
        assertEquals(0, l.completedBefore(40), "a gap is not a reading");
    }

    @Test
    void timeGoingBackwardsStartsClean() {
        final IntakeLedger l = new IntakeLedger();
        l.record(500, 100);
        l.record(20, 7);
        assertEquals(7, l.completedBefore(21));
        assertEquals(0, l.completedBefore(19));
    }

    @Test
    void theSumSaturatesRatherThanWrapping() {
        final IntakeLedger l = new IntakeLedger();
        l.record(1, Integer.MAX_VALUE);
        l.record(1, 10);
        assertEquals(Integer.MAX_VALUE, l.completedBefore(2), "INV-14: a sum saturates, never wraps");
        l.record(2, -5);
        assertEquals(0, l.completedBefore(3), "nothing negative is ever recorded");
    }
}
