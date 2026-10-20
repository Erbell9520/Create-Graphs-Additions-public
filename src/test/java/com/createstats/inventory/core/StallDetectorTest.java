package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StallDetectorTest {
    @Test
    void aStoreThatNeverMovedIsIdleNotStalled() {
        final StallDetector d = new StallDetector();
        for (long t = 0; t < 50_000; t += 100) {
            d.observe(t, false);
            assertFalse(d.stalled(t));
        }
        assertEquals(-1L, d.quietTicks(50_000));
    }

    @Test
    void itLightsAfterTheStallTimeOfQuietFollowingMovement() {
        final StallDetector d = new StallDetector();
        d.observe(1000, true);
        assertFalse(d.stalled(1000 + 1199), "the default minute has not passed");
        assertTrue(d.stalled(1000 + 1200));
        assertEquals(1200L, d.quietTicks(2200));
    }

    @Test
    void movementClearsIt() {
        final StallDetector d = new StallDetector();
        d.observe(0, true);
        assertTrue(d.stalled(5000));
        d.observe(5000, true);
        assertFalse(d.stalled(5005));
    }

    @Test
    void aMinecraftDayOfQuietIsIdleAgain() {
        final StallDetector d = new StallDetector();
        d.observe(0, true);
        assertTrue(d.stalled(23_999));
        assertFalse(d.stalled(24_000), "a day later it is a store nobody uses");
    }

    @Test
    void theStallTimesCycleInTheScreensOrder() {
        assertEquals(600, StallDetector.ticksFor(0));
        assertEquals(1200, StallDetector.ticksFor(1));
        assertEquals(6000, StallDetector.ticksFor(2));
        assertEquals(1000, StallDetector.ticksFor(3), "a Minecraft hour is a thousand ticks");
        final StallDetector d = new StallDetector();
        d.setStallIndex(0);
        d.observe(0, true);
        assertTrue(d.stalled(600));
        d.setStallIndex(5);
        assertEquals(1, d.stallIndex(), "the index wraps");
    }

    @Test
    void resetForgetsTheLine() {
        final StallDetector d = new StallDetector();
        d.observe(0, true);
        d.reset();
        assertFalse(d.stalled(5000));
        d.restore(100);
        assertTrue(d.stalled(1300));
    }
}
