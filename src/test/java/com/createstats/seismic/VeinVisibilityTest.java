package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinVisibilityTest {
    private static final double CURRENT = Double.NaN;

    @Test
    @DisplayName("proximity: full within 16 blocks, gone beyond 32, eased between with no edge at either end")
    void proximity() {
        assertEquals(1.0, VeinVisibility.proximity(0.0));
        assertEquals(1.0, VeinVisibility.proximity(VeinVisibility.NEAR));
        assertEquals(0.0, VeinVisibility.proximity(VeinVisibility.FAR));
        assertEquals(0.0, VeinVisibility.proximity(200.0));
        assertEquals(0.5, VeinVisibility.proximity((VeinVisibility.NEAR + VeinVisibility.FAR) / 2.0), 1e-12);
        double prev = 1.0;
        for (double d = VeinVisibility.NEAR; d <= VeinVisibility.FAR; d += 0.01) {
            final double p = VeinVisibility.proximity(d);
            assertTrue(p <= prev + 1e-12, "closer is never less visible, at " + d);
            prev = p;
        }

        assertTrue(1.0 - VeinVisibility.proximity(VeinVisibility.NEAR + 0.5) < 0.002);
        assertTrue(VeinVisibility.proximity(VeinVisibility.FAR - 0.5) < 0.002);
    }

    @Test
    @DisplayName("a vein appears only with its echo, rises in three ticks and flashes white for ten")
    void theEcho() {
        assertEquals(0.0, VeinVisibility.target(-5.0, -50.0, 3.0, CURRENT), "nothing before its echo, overview or not");
        assertEquals(0.0, VeinVisibility.target(0.0, -50.0, 3.0, CURRENT), "nothing at the echo's first instant: no pop");
        assertEquals(1.0, VeinVisibility.target(VeinVisibility.RISE_TICKS, -50.0, 3.0, CURRENT), 1e-12);
        assertTrue(VeinVisibility.flash(0.5) > 0.9, "the flash burns at the echo");
        assertEquals(0.0, VeinVisibility.flash(VeinVisibility.FLASH_TICKS));
        assertEquals(0.0, VeinVisibility.flash(-1.0));
    }

    @Test
    @DisplayName("the overview holds every vein at full, then the farthest let go first and each settles to its distance")
    void theOverview() {
        assertEquals(1.0, VeinVisibility.target(50.0, -1.0, 60.0, CURRENT), 1e-12);

        final double far = VeinVisibility.overview(20.0, 60.0);
        final double near = VeinVisibility.overview(20.0, 2.0);
        assertTrue(far < 0.5, "the far vein fades first: " + far);
        assertEquals(1.0, near, 1e-12, "the near vein is still held");

        final double after = VeinVisibility.STAGGER_TICKS + VeinVisibility.FADE_TICKS + 1.0;
        for (final double d : new double[] {0.0, 10.0, 16.0, 20.0, 24.0, 28.0, 32.0, 40.0, 64.0}) {
            assertEquals(VeinVisibility.proximity(d), VeinVisibility.target(80.0, after, d, CURRENT), 1e-12, "at " + d);
        }
        assertEquals(0.0, VeinVisibility.target(80.0, after, 40.0, CURRENT), "beyond 32 blocks, hidden");
        assertEquals(1.0, VeinVisibility.target(80.0, after, 10.0, CURRENT), "within 16, full");
    }

    @Test
    @DisplayName("walking toward a vein brings it back, and closer is never less visible")
    void walkingCloser() {
        final double after = 500.0;
        double prev = 0.0;
        for (double d = 48.0; d >= 0.0; d -= 0.25) {
            final double v = VeinVisibility.target(900.0, after, d, CURRENT);
            assertTrue(v >= prev - 1e-12, "a step closer dimmed it at " + d);
            prev = v;
        }
        assertEquals(1.0, prev, 1e-12);
    }

    @Test
    @DisplayName("every change is a fade: the target moves smoothly through a whole survey, and what is drawn eases")
    void noPops() {
        for (final double d : new double[] {0.0, 8.0, 18.0, 25.0, 31.0, 45.0, 70.0}) {
            double prev = 0.0;
            for (double t = 0.0; t <= 400.0; t += 0.05) {
                final double v = VeinVisibility.target(t - 20.0, t - 140.0, d, CURRENT);
                assertTrue(v >= 0.0 && v <= 1.0);
                assertTrue(Math.abs(v - prev) < 0.03, "a step of " + (v - prev) + " at t " + t + ", distance " + d);
                prev = v;
            }
        }

        double shown = 0.0;
        for (int tick = 0; tick < 3; tick++) {
            final double next = VeinVisibility.ease(shown, 1.0);
            assertTrue(next - shown <= VeinVisibility.EASE_UP + 1e-12, "brightens by a share a tick, never at once");
            shown = next;
        }
        assertTrue(shown > 0.8 && shown < 1.0, "most of the way in three ticks: " + shown);
        final double down = VeinVisibility.ease(shown, 0.0);
        assertEquals(shown * (1.0 - VeinVisibility.EASE_DOWN), down, 1e-12, "and goes out slowly");
    }

    @Test
    @DisplayName("a survey a newer blow replaces fades out, it never vanishes")
    void retired() {
        assertEquals(1.0, VeinVisibility.retire(CURRENT));
        assertEquals(1.0, VeinVisibility.retire(-3.0));
        assertTrue(VeinVisibility.retire(VeinVisibility.RETIRE_TICKS * 0.5) > 0.3);
        assertEquals(0.0, VeinVisibility.retire(VeinVisibility.RETIRE_TICKS));
        double prev = 1.0;
        for (double s = 0.0; s <= VeinVisibility.RETIRE_TICKS; s += 0.05) {
            final double r = VeinVisibility.retire(s);
            assertTrue(Math.abs(r - prev) < 0.01 && r <= prev + 1e-12, "a step at " + s);
            prev = r;
        }
    }
}
