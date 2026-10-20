package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunLogTest {
    @Test
    void aSteadyClimbingRunSumsUp() {
        RunLog log = new RunLog();
        for (int i = 0; i <= 20; i++) {
            double t = i * 0.5;
            log.add(t, 10.0 * t, 64 + 2.0 * t, 0);
        }
        RunLog.Summary s = log.summary();
        assertEquals(10.0, s.seconds(), 1e-9);
        assertEquals(Math.hypot(100, 20), s.distance(), 1e-6);
        assertEquals(Math.hypot(10, 2), s.topSpeed(), 1e-6);
        assertEquals(84.0, s.topHeight(), 1e-9);
        assertEquals(2.0, s.bestClimb(), 1e-9);
        assertEquals(21, s.samples());
    }

    @Test
    void aWanderingReadingDoesNotMakeASpeedThatNeverHappened() {
        RunLog log = new RunLog();
        for (int i = 0; i <= 40; i++) {
            double t = i * 0.5;
            double wobble = (i % 2 == 0) ? 0.5 : -0.5;
            log.add(t, 5.0 * t, 64, wobble);
        }

        assertTrue(log.summary().topSpeed() <= 5.0 + 1.0 + 1e-9, "top " + log.summary().topSpeed());
    }

    @Test
    void aLongRunKeepsItsEarlyRecordsAfterTheRingWraps() {
        RunLog log = new RunLog();
        for (int i = 0; i < RunLog.CAPACITY + 300; i++) {
            double t = i * 0.5;
            double y = i < 10 ? 200 : 64;
            log.add(t, t, y, 0);
        }
        RunLog.Summary s = log.summary();
        assertEquals(200.0, s.topHeight(), 1e-9);
        assertEquals(RunLog.CAPACITY, s.samples());
        assertEquals((RunLog.CAPACITY + 299) * 0.5, s.seconds(), 1e-9);
    }
}
