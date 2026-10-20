package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultRuleTest {
    private static final long MS = 1_000_000L;

    @Test
    void ordinaryTicksNeverTrip() {
        FaultRule rule = new FaultRule();
        for (int i = 0; i < 10_000; i++) assertFalse(rule.overBudget(40 * MS));
    }

    @Test
    void threeStallsInARowTrip() {
        FaultRule rule = new FaultRule();
        assertFalse(rule.overBudget(FaultRule.SLOW_NANOS));
        assertFalse(rule.overBudget(FaultRule.SLOW_NANOS + 1));
        assertTrue(rule.overBudget(2_000 * MS));
        assertEquals(FaultRule.SLOW_RUN, rule.slowRun());
    }

    @Test
    void aGoodTickBetweenStallsStartsTheCountAgain() {
        FaultRule rule = new FaultRule();
        for (int i = 0; i < 100; i++) {
            assertFalse(rule.overBudget(1_500 * MS));
            assertFalse(rule.overBudget(1_500 * MS));
            assertFalse(rule.overBudget(30 * MS));
        }
    }

    @Test
    void oneStuckTickTripsAlone() {
        assertTrue(new FaultRule().overBudget(FaultRule.STUCK_NANOS));
    }

    @Test
    void summaryNamesTheErrorAndTheFirstFrameOfThisMod() {
        RuntimeException e = new IllegalStateException("bad\n  thing");
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.util.ArrayList", "get", "ArrayList.java", 427),
                new StackTraceElement("com.createstats.vccmk4.ConsoleFault", "tick", "ConsoleFault.java", 80),
                new StackTraceElement("com.createstats.vccmk4.air.AerialProps", "drive", "AerialProps.java", 268),
        });
        assertEquals("IllegalStateException: bad thing at AerialProps.drive:268", FaultRule.summary(e));
    }

    @Test
    void summarySkipsTheGuardAndNamesAForeignFrameWhenNoneIsOurs() {
        RuntimeException e = new NullPointerException();
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("dev.ryanhcode.sable.Foo", "bar", "Foo.java", 12),
                new StackTraceElement("com.createstats.vccmk4.ConsoleFault", "tick", "ConsoleFault.java", 80),
        });
        assertEquals("NullPointerException at Foo.bar:12", FaultRule.summary(e));
    }

    @Test
    void summaryClipsALongMessage() {
        String s = FaultRule.summary(new RuntimeException("x".repeat(500)));
        assertTrue(s.length() < 200, s);
        assertTrue(s.contains("..."), s);
    }
}
