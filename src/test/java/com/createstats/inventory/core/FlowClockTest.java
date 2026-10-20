package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowClockTest {
    @Test
    void risingWithAHighMarkTargetsHigh() {
        final FlowClock c = new FlowClock();
        final FlowClock.Projection p = c.update(2417, 5120, 18.0, 512.0, 4096.0, false);
        assertEquals(FlowClock.Kind.HIGH, p.kind());
        assertEquals((4096 - 2417) / 18.0 * 60.0, p.seconds(), 1e-6);
    }

    @Test
    void aWatchedItemTakesItsBandFromItsOwnScaleNotTheVault() {
        assertEquals(FlowClock.Kind.STEADY, new FlowClock().update(2417, 1_000_000, 18.0, 512.0, 4096.0, false).kind());
        final FlowClock.Projection p = new FlowClock().update(2417, 1_000_000, 18.0, 512.0, 4096.0, false, 4096.0);
        assertEquals(FlowClock.Kind.HIGH, p.kind());
    }

    @Test
    void risingPastHighOrWithoutMarksTargetsFull() {
        final FlowClock c = new FlowClock();
        assertEquals(FlowClock.Kind.FULL, c.update(5000, 10_000, 100.0, 512.0, 4096.0, false).kind());
        final FlowClock d = new FlowClock();
        final FlowClock.Projection p = d.update(700, 1728, 20.0, null, null, false);
        assertEquals(FlowClock.Kind.FULL, p.kind());
        assertEquals((1728 - 700) / 20.0 * 60.0, p.seconds(), 1e-6);
    }

    @Test
    void fallingAboveLowTargetsLowElseEmpty() {
        final FlowClock c = new FlowClock();
        final FlowClock.Projection low = c.update(1000, 10_000, -36.0, 512.0, 4096.0, false);
        assertEquals(FlowClock.Kind.LOW, low.kind());
        assertEquals((1000 - 512) / 36.0 * 60.0, low.seconds(), 1e-6);
        final FlowClock d = new FlowClock();
        final FlowClock.Projection empty = d.update(431, 10_000, -36.0, 512.0, 4096.0, false);
        assertEquals(FlowClock.Kind.EMPTY, empty.kind(), "already past LOW: the next thing is empty");
        assertEquals(431 / 36.0 * 60.0, empty.seconds(), 1e-6);
    }

    @Test
    void energyDrainingReadsRuntime() {
        final FlowClock c = new FlowClock();

        final FlowClock.Projection p = c.update(1_240_000, 2_000_000, -1400.0 * 1200, null, null, true);
        assertEquals(FlowClock.Kind.RUNTIME, p.kind());
        assertEquals(1_240_000 / 1400.0 / 20.0, p.seconds(), 1e-6);
    }

    @Test
    void underTheBandItIsSteadyAndItMustExceedTwiceTheBandToLeave() {
        final FlowClock c = new FlowClock();
        final double band = FlowClock.band(1728);
        assertEquals(1.728, band, 1e-9);
        assertEquals(FlowClock.Kind.STEADY, c.update(500, 1728, band * 1.5, null, null, false).kind(),
                "starting steady, 1.5 bands does not leave");
        assertEquals(FlowClock.Kind.FULL, c.update(500, 1728, band * 2.5, null, null, false).kind());
        assertEquals(FlowClock.Kind.FULL, c.update(500, 1728, band * 1.5, null, null, false).kind(),
                "once moving, 1.5 bands still moves: hysteresis");
        assertEquals(FlowClock.Kind.STEADY, c.update(500, 1728, band * 0.5, null, null, false).kind());
    }

    @Test
    void theBandHasAFloorOfHalfAnItemAMinute() {
        assertEquals(0.5, FlowClock.band(27), 1e-9);
        assertEquals(1000.0, FlowClock.band(1_000_000), 1e-9);
    }

    @Test
    void beyondThirtyDaysIsSteady() {
        final FlowClock c = new FlowClock();
        assertEquals(FlowClock.Kind.STEADY, c.update(100, 1728, 10.0, null, 1e9, false).kind());

        final FlowClock.Projection p = new FlowClock().update(0, 1e9, 2.0 * FlowClock.band(1e9) + 1, null, null, false);
        assertEquals(FlowClock.Kind.FULL, p.kind());
        assertTrue(p.seconds() <= 30_000.0);
    }

    @Test
    void anUnknownCapacityGivesNoClock() {
        final FlowClock c = new FlowClock();
        assertEquals(FlowClock.Kind.NONE, c.update(100, 0, 50.0, null, null, false).kind());
        assertEquals(FlowClock.Kind.NONE, c.update(100, -1, -50.0, 10.0, 200.0, false).kind());
    }

    @Test
    void alreadyFullOrEmptyIsSteady() {
        assertEquals(FlowClock.Kind.STEADY, new FlowClock().update(1728, 1728, 30.0, null, null, false).kind());
        assertEquals(FlowClock.Kind.STEADY, new FlowClock().update(0, 1728, -30.0, null, null, false).kind());
    }
}
