package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorSwingTest {
    private static final double LEVEL = CollectorPose.angleOnRail(RailProfile.CAP_TOP);
    private static final double FRAME = 1.0 / 60.0;

    @Test
    @DisplayName("on metal the drawn arm is the rest, every frame, rising or falling (INV-27)")
    void onMetalItIsTheRest() {
        final CollectorSwing swing = new CollectorSwing();
        swing.step(0.0, CollectorPose.STOP_DEG, false);
        double t = 0.0;

        final double[] rests = {CollectorPose.STOP_DEG + 0.4, 28, 33, 38, LEVEL, LEVEL, 38, 31, 24, CollectorPose.STOP_DEG + 0.2};
        for (double rest : rests) {
            assertEquals(rest, swing.step(t += FRAME, rest, true), 1e-9, "the drawn shoe left the metal it touches");
        }
    }

    @Test
    @DisplayName("off a ramp's end the arm falls over a few frames, rings visibly off the stop, and settles on it")
    void fallsBouncesAndSettles() {
        final CollectorSwing swing = new CollectorSwing();
        swing.step(0.0, LEVEL, true);
        double t = 0.0;
        final double first = swing.step(t += FRAME, CollectorPose.STOP_DEG, false);
        assertTrue(first > CollectorPose.STOP_DEG + 5, "a drop is not a teleport: one frame in it is still high, " + first);
        double landedAt = Double.NaN;
        double highestAfter = CollectorPose.STOP_DEG;
        int strikes = 0;
        double last = first;
        for (int i = 0; i < 120; i++) {
            final double a = swing.step(t += FRAME, CollectorPose.STOP_DEG, false);
            assertTrue(a >= CollectorPose.STOP_DEG - 1e-9, "never through its stop");
            if (a <= CollectorPose.STOP_DEG + 1e-9 && last > CollectorPose.STOP_DEG + 1e-9) {
                strikes++;
            }
            if (Double.isNaN(landedAt) && a <= CollectorPose.STOP_DEG + 1e-9) {
                landedAt = t;
            } else if (!Double.isNaN(landedAt)) {
                highestAfter = Math.max(highestAfter, a);
            }
            last = a;
        }
        assertTrue(landedAt < 0.2, "it reaches the stop inside a fifth of a second: " + landedAt);
        assertTrue(highestAfter > CollectorPose.STOP_DEG + 2, "it rings off the stop, visibly: " + highestAfter);
        assertTrue(highestAfter < CollectorPose.STOP_DEG + 7, "a ring, not a second swing: " + highestAfter);
        assertTrue(strikes >= 2 && strikes <= 4, "it rings a couple of times, then settles: " + strikes + " strikes");
        assertEquals(CollectorPose.STOP_DEG, last, 1e-9, "and settles");
    }

    @Test
    @DisplayName("leaving a descending ramp it carries the ramp's downward speed into its fall")
    void carriesTheRampsSpeed() {
        final CollectorSwing riding = new CollectorSwing();
        final CollectorSwing still = new CollectorSwing();
        double t = 0.0;
        riding.step(t, 36, true);
        still.step(t, 30, true);
        riding.step(t += FRAME, 30, true);
        still.step(t, 30, true);
        final double a = riding.step(t += FRAME, CollectorPose.STOP_DEG, false);
        final double b = still.step(t, CollectorPose.STOP_DEG, false);
        assertTrue(a < b, "an arm sliding down the ramp falls on faster than one that was still: " + a + " " + b);
    }

    @Test
    @DisplayName("a falling arm the next ramp rises under is caught by it, and never hangs below the rail")
    void caughtByTheNextRamp() {
        final CollectorSwing swing = new CollectorSwing();
        double t = 0.0;
        swing.step(t, LEVEL, true);
        swing.step(t += FRAME, CollectorPose.STOP_DEG, false);
        swing.step(t += FRAME, CollectorPose.STOP_DEG, false);
        assertEquals(30.0, swing.step(t += FRAME, 30.0, true), 1e-9, "landing on metal it stops dead on it");
    }

    @Test
    @DisplayName("a hitch or a paused game never flings it, and time running backwards starts it afresh")
    void hitches() {
        final CollectorSwing swing = new CollectorSwing();
        swing.step(0.0, LEVEL, true);
        final double after = swing.step(5.0, CollectorPose.STOP_DEG, false);
        assertTrue(after >= CollectorPose.STOP_DEG - 1e-9);
        final double still = swing.step(5.0, CollectorPose.STOP_DEG, false);
        assertTrue(Math.abs(still - after) < 1e-6 || still == CollectorPose.STOP_DEG, "a frame with no time moves nothing");
        assertEquals(LEVEL, swing.step(1.0, LEVEL, true), 1e-9, "time going backwards snaps to the rest");
    }
}
