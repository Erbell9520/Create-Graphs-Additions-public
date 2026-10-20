package com.createstats.vehiclelight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlightStateTest {
    @Test
    void theHeightIsProbedTwiceASecondAndTheFirstGoodProbeIsTakenAsIs() {
        final HeadlightState s = new HeadlightState();
        assertTrue(s.probeDue(), "the first tick probes");
        int probes = 0;
        for (int i = 0; i < 20; i++) {
            if (s.probeDue()) {
                probes++;
            }
        }
        assertEquals(2, probes, "then twice a second");
        assertTrue(Double.isNaN(s.height()), "unknown until something is found");
        s.measured(Double.NaN);
        assertTrue(Double.isNaN(s.height()), "nothing found is not a height");
        s.measured(1.5);
        assertEquals(1.5, s.height(), 1e-12);
    }

    @Test
    void aNewHeightIsEasedTowardOverAboutASecondAndHeldToItsLimits() {
        final HeadlightState s = new HeadlightState();
        s.measured(1.5);
        s.measured(3.5);
        s.tick();
        assertTrue(s.height() > 1.5 && s.height() < 2.0, "a nudge, not a jump: " + s.height());
        for (int i = 0; i < 19; i++) {
            s.tick();
        }
        assertTrue(s.height() > 3.3, "most of the way in a second: " + s.height());
        s.measured(Double.NaN);
        for (int i = 0; i < 60; i++) {
            s.tick();
        }
        assertEquals(3.5, s.height(), 1e-3, "a probe over a gap is ignored, the last good one kept");
        final HeadlightState low = new HeadlightState();
        low.measured(0.0);
        assertEquals(HeadlightState.MIN_HEIGHT, low.height(), 1e-12);
        final HeadlightState high = new HeadlightState();
        high.measured(30.0);
        assertEquals(HeadlightState.MAX_HEIGHT, high.height(), 1e-12);
    }

    @Test
    void theBeamTurnsIntoABendByTheVehiclesYawRateAndNotAtRest() {
        final HeadlightState s = new HeadlightState();
        s.heading(0.0);
        for (int i = 1; i <= 40; i++) {
            s.heading(Math.toRadians(1.0 * i));
        }

        assertEquals(20.0 * HeadlightState.LOOK_AHEAD, s.bend(), 0.2);
        for (int i = 41; i <= 120; i++) {
            s.heading(Math.toRadians(3.0 * (i - 40) + 40.0));
        }
        assertEquals(HeadlightState.MAX_BEND, s.bend(), 1e-9, "a swivel's reach at most");

        final HeadlightState still = new HeadlightState();
        for (int i = 0; i < 40; i++) {
            still.heading(Math.toRadians(0.05 * Math.sin(i)));
        }
        assertEquals(0.0, still.bend(), 1e-12, "a parked craft's tremble is not a turn");

        final HeadlightState right = new HeadlightState();
        for (int i = 0; i < 40; i++) {
            right.heading(Math.toRadians(179.0 - i) );
        }
        assertTrue(right.bend() < 0.0, "clockwise is right");

        final HeadlightState wrap = new HeadlightState();
        for (int i = 0; i < 40; i++) {
            wrap.heading(Math.atan2(Math.sin(Math.toRadians(170.0 + i)), Math.cos(Math.toRadians(170.0 + i))));
        }
        assertTrue(wrap.bend() > 0.0 && wrap.bend() < 13.0, "turning past south is not a spin: " + wrap.bend());
        wrap.heading(Double.NaN);
        assertEquals(0.0, wrap.bend(), 1e-12, "a lamp pointing up has no heading to turn");
    }

    @Test
    void speedCutsTheShowAndTheAimIsEasedBetweenTicks() {
        final HeadlightState s = new HeadlightState();
        s.travelled(0, 0, 0);
        for (int i = 1; i <= 10; i++) {
            s.travelled(0.25 * i, 0, 0);
        }
        assertEquals(5.0, s.speed(), 0.01, "a quarter block a tick is five blocks a second");
        assertFalse(s.isCut(100L));
        s.cut(100L);
        assertTrue(s.isCut(100L), "that show is over for good");
        assertFalse(s.isCut(200L), "the next one is not");

        assertTrue(Double.isNaN(s.downAt(0.5f)), "no aim before the first tick");
        s.aimed(9.0, 0.0);
        assertEquals(9.0, s.downAt(0.5f), 1e-12, "the first aim has nothing to ease from");
        s.aimed(15.0, 10.0);
        assertEquals(12.0, s.downAt(0.5f), 1e-12);
        assertEquals(5.0, s.leftAt(0.5f), 1e-12);
        assertEquals(15.0, s.downAt(1.0f), 1e-12);
    }
}
