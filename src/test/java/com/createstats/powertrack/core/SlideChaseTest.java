package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideChaseTest {
    @Test
    @DisplayName("from under its block the sled reaches a block's slide in well under a second, without overshoot")
    void runsOutAndSettles() {
        final SlideChase s = new SlideChase();
        assertEquals(0.0, s.step(0.0, 1.0), 0.0, "born under its own block");
        double prev = 0.0;
        for (int f = 1; f <= 60; f++) {
            final double t = f / 60.0;
            final double v = s.step(t, 1.0);
            assertTrue(v >= prev - 1e-12, "never backs off");
            assertTrue(v <= 1.0 + 1e-12, "never overshoots");
            assertTrue(v - prev <= SlideChase.MAX_SPEED / 60.0 + 1e-12, "never faster than its top speed");
            prev = v;
        }
        assertEquals(1.0, prev, 0.01, "arrived within a second");
        final SlideChase fresh = new SlideChase();
        fresh.step(0.0, 1.0);
        assertTrue(fresh.step(1.0 / 60.0, 1.0) > 0.0, "moves on the first frame after birth");
    }

    @Test
    @DisplayName("a hitch or a paused game moves it one capped step, not all the way")
    void aHitchIsCapped() {
        final SlideChase s = new SlideChase();
        s.step(0.0, -1.0);
        final double v = s.step(30.0, -1.0);
        assertEquals(-SlideChase.MAX_SPEED * SlideChase.MAX_STEP, v, 1e-9);
    }

    @Test
    @DisplayName("on a curve it tracks a moving target closely")
    void tracksACurve() {
        final SlideChase s = new SlideChase();
        s.step(0.0, 0.0);
        double worst = 0.0;
        for (int f = 1; f <= 600; f++) {
            final double t = f / 60.0;
            final double target = 0.3 * Math.sin(t);
            final double v = s.step(t, target);
            if (t > 1.0) {
                worst = Math.max(worst, Math.abs(v - target));
            }
        }
        assertTrue(worst < 0.04, "lag " + worst);
    }
}
