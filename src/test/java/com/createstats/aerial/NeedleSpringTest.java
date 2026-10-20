package com.createstats.aerial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedleSpringTest {
    @Test
    void theFirstReadingPlacesTheNeedle() {
        final NeedleSpring n = new NeedleSpring();
        assertEquals(0.5, n.follow(0.5, 0.016), 0.0);
    }

    @Test
    void itArrivesAndNeverPassesTheReading() {
        final NeedleSpring n = new NeedleSpring();
        n.follow(0.0, 0.016);
        double last = 0.0;
        for (int i = 0; i < 120; i++) {
            final double x = n.follow(1.0, 1.0 / 60.0);
            assertTrue(x <= 1.0 + 1.0e-9, "critically damped: never past the reading, at frame " + i + ": " + x);
            assertTrue(x >= last - 1.0e-12, "and never back the way it came");
            last = x;
        }
        assertEquals(1.0, last, 1.0e-3, "settled within two seconds");
    }

    @Test
    void halfWayWithinAFeedInterval() {
        final NeedleSpring n = new NeedleSpring();
        n.follow(0.0, 0.016);
        double x = 0.0;
        for (int i = 0; i < 12; i++) {
            x = n.follow(1.0, 1.0 / 60.0);
        }
        assertTrue(x > 0.6, "at 0.2 s: " + x);
    }

    @Test
    void aLongFrameSettlesInsteadOfFlinging() {
        final NeedleSpring n = new NeedleSpring();
        n.follow(-1.0, 0.016);
        final double x = n.follow(1.0, 5.0);
        assertEquals(1.0, x, 1.0e-6);
    }

    @Test
    void anUnknownReadingPassesThrough() {
        final NeedleSpring n = new NeedleSpring();
        assertTrue(Double.isNaN(n.follow(Double.NaN, 0.016)));
        assertEquals(0.25, n.snap(0.25), 0.0);
        assertEquals(0.25, n.follow(0.25, 0.1), 1.0e-12);
    }
}
