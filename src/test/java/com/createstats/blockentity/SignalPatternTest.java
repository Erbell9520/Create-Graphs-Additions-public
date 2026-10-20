package com.createstats.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalPatternTest {
    private static double dutyOver(SignalPattern pattern, double span) {
        int lit = 0;
        final int steps = 20_000;
        for (int i = 0; i < steps; i++) {
            if (pattern.brightness(span * i / steps, 0.0D, 0.0D) > 0.5D) {
                lit++;
            }
        }
        return lit / (double) steps;
    }

    private static int pulsesOver(SignalPattern pattern, double span) {
        int pulses = 0;
        boolean wasLit = false;
        final int steps = 20_000;
        for (int i = 0; i < steps; i++) {
            final boolean lit = pattern.brightness(span * i / steps, 0.0D, 0.0D) > 0.5D;
            if (lit && !wasLit) {
                pulses++;
            }
            wasLit = lit;
        }
        return pulses;
    }

    @Test
    void steadyIsAlwaysFullAndOffIsAlwaysDark() {
        for (int i = 0; i < 100; i++) {
            final double t = i * 0.137D;
            assertEquals(1.0D, SignalPattern.STEADY.brightness(t, 0.0D, 0.0D), 1.0e-9);
            assertEquals(0.0D, SignalPattern.OFF.brightness(t, 0.0D, 0.0D), 1.0e-9);
        }
    }

    @Test
    void blinkIsOnceASecondAtHalfDuty() {
        assertEquals(4, pulsesOver(SignalPattern.BLINK, 4.0D));
        assertEquals(0.5D, dutyOver(SignalPattern.BLINK, 4.0D), 0.02D);
    }

    @Test
    void flashIsTwoPipsPerPeriod() {
        assertEquals(4, pulsesOver(SignalPattern.FLASH, 3.0D));

        assertTrue(dutyOver(SignalPattern.FLASH, 3.0D) < 0.20D);
    }

    @Test
    void pulseBreathesWithoutGoingDark() {
        double min = 1.0D;
        double max = 0.0D;
        for (int i = 0; i < 5_000; i++) {
            final double v = SignalPattern.PULSE.brightness(4.8D * i / 5_000, 0.0D, 0.0D);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        assertTrue(min > 0.2D, "pulse floor was " + min);
        assertTrue(max > 0.98D, "pulse never reached full: " + max);
    }

    @Test
    void strobeIsABriefPopWithADecayingTail() {
        assertTrue(dutyOver(SignalPattern.STROBE, 2.5D) < 0.10D);

        final double justAfter = SignalPattern.STROBE.brightness(0.06D, 0.0D, 0.0D);
        final double later = SignalPattern.STROBE.brightness(0.20D, 0.0D, 0.0D);
        assertTrue(justAfter > later, "tail did not decay");
        assertTrue(later < 0.15D, "tail lingered at " + later);
    }

    @Test
    void rotateHandsOverBetweenAdjacentFaces() {
        final double step = Math.PI / 4.0D;

        for (int face = 0; face < 8; face++) {
            final double a = step * face;
            final double b = step * (face + 1);
            boolean overlapped = false;
            for (int i = 0; i < 2_000 && !overlapped; i++) {
                final double t = i * 0.002D;
                overlapped = SignalPattern.ROTATE.brightness(t, a, 0.0D) > 0.5D
                        && SignalPattern.ROTATE.brightness(t, b, 0.0D) > 0.5D;
            }
            assertTrue(overlapped, "faces " + face + " and " + (face + 1) + " never overlap");
        }
    }

    @Test
    void rotateAlwaysHasABeamOnSomeFace() {
        for (int i = 0; i < 2_000; i++) {
            final double t = i * 0.002D;
            double brightest = 0.0D;
            for (int face = 0; face < 8; face++) {
                brightest = Math.max(brightest,
                        SignalPattern.ROTATE.brightness(t, Math.PI * 2.0D * face / 8.0D, 0.0D));
            }
            assertTrue(brightest > 0.80D, "at t=" + t + " the brightest face was only " + brightest);
        }
    }

    @Test
    void rotateKeepsAFloorOnEveryFace() {
        for (int face = 0; face < 8; face++) {
            final double bearing = Math.PI * 2.0D * face / 8.0D;
            for (int i = 0; i < 200; i++) {
                final double v = SignalPattern.ROTATE.brightness(i * 0.011D, bearing, 0.0D);
                assertTrue(v >= 0.15D, "face " + face + " went to " + v);
            }
        }
    }

    @Test
    void faultStuttersIrregularlyButIsDeterministic() {
        final double duty = dutyOver(SignalPattern.FAULT, 60.0D);
        assertTrue(duty > 0.15D && duty < 0.75D, "fault duty was " + duty);

        for (int i = 0; i < 50; i++) {
            final double t = i * 0.31D;
            assertEquals(SignalPattern.FAULT.brightness(t, 0.0D, 0.5D),
                    SignalPattern.FAULT.brightness(t, 0.0D, 0.5D), 1.0e-12);
        }

        boolean differed = false;
        for (int i = 0; i < 400 && !differed; i++) {
            final double t = i * 0.02D;
            differed = Math.abs(SignalPattern.FAULT.brightness(t, 0.0D, 0.0D)
                    - SignalPattern.FAULT.brightness(t, 0.0D, 1.7D)) > 0.4D;
        }
        assertTrue(differed, "two jitters produced the same stutter");
    }

    @Test
    void severityRanksInTheOrderThingsActuallyGetWorse() {
        assertTrue(StatusTowerLightBE.SignalState.OFF.severity()
                < StatusTowerLightBE.SignalState.NORMAL.severity());
        assertTrue(StatusTowerLightBE.SignalState.NORMAL.severity()
                < StatusTowerLightBE.SignalState.NO_DATA.severity());
        assertTrue(StatusTowerLightBE.SignalState.NO_DATA.severity()
                < StatusTowerLightBE.SignalState.WARNING.severity());
        assertTrue(StatusTowerLightBE.SignalState.WARNING.severity()
                < StatusTowerLightBE.SignalState.CRITICAL.severity());
    }

    @Test
    void worstPicksTheHigherSeverityEitherWayRound() {
        assertSame(StatusTowerLightBE.SignalState.CRITICAL,
                StatusTowerLightBE.SignalState.worst(StatusTowerLightBE.SignalState.CRITICAL,
                        StatusTowerLightBE.SignalState.NORMAL));
        assertSame(StatusTowerLightBE.SignalState.CRITICAL,
                StatusTowerLightBE.SignalState.worst(StatusTowerLightBE.SignalState.NORMAL,
                        StatusTowerLightBE.SignalState.CRITICAL));
        assertSame(StatusTowerLightBE.SignalState.WARNING,
                StatusTowerLightBE.SignalState.worst(StatusTowerLightBE.SignalState.NO_DATA,
                        StatusTowerLightBE.SignalState.WARNING));
    }

    @Test
    void noDataAlwaysStuttersUnlessAnimationIsOff() {
        for (StatusTowerLightSettings.AlertStyle style : StatusTowerLightSettings.AlertStyle.values()) {
            final SignalPattern pattern = style.patternFor(StatusTowerLightBE.SignalState.NO_DATA);
            if (style == StatusTowerLightSettings.AlertStyle.SOLID) {
                assertSame(SignalPattern.STEADY, pattern);
            } else {
                assertSame(SignalPattern.FAULT, pattern, "style " + style);
            }
        }
    }

    @Test
    void everyStyleEscalatesFromWarningToCritical() {
        for (StatusTowerLightSettings.AlertStyle style : StatusTowerLightSettings.AlertStyle.values()) {
            if (style == StatusTowerLightSettings.AlertStyle.SOLID) {
                continue;
            }
            final SignalPattern warning = style.patternFor(StatusTowerLightBE.SignalState.WARNING);
            final SignalPattern critical = style.patternFor(StatusTowerLightBE.SignalState.CRITICAL);
            assertNotEquals(warning, critical, "style " + style + " cannot tell its severities apart");
            assertNotEquals(SignalPattern.STEADY, warning, "style " + style + " warning is invisible");
        }
    }

    @Test
    void everyStyleIsDarkWhenOffAndSteadyWhenNormal() {
        for (StatusTowerLightSettings.AlertStyle style : StatusTowerLightSettings.AlertStyle.values()) {
            assertSame(SignalPattern.OFF, style.patternFor(StatusTowerLightBE.SignalState.OFF),
                    "style " + style);
            assertSame(SignalPattern.STEADY, style.patternFor(StatusTowerLightBE.SignalState.NORMAL),
                    "style " + style);
        }
    }

    @Test
    void onlyRotateSpinsTheVane() {
        for (SignalPattern pattern : SignalPattern.values()) {
            assertEquals(pattern == SignalPattern.ROTATE, pattern.spinsVane(), pattern.name());
        }
    }

    @Test
    void sounderRespectsItsThreshold() {
        final StatusTowerLightSettings.Sounder off = StatusTowerLightSettings.Sounder.OFF;
        final StatusTowerLightSettings.Sounder critical = StatusTowerLightSettings.Sounder.CRITICAL;
        final StatusTowerLightSettings.Sounder warning = StatusTowerLightSettings.Sounder.WARNING;

        for (StatusTowerLightBE.SignalState state : StatusTowerLightBE.SignalState.values()) {
            assertTrue(!off.soundsFor(state), "silent sounder fired on " + state);
        }
        assertTrue(critical.soundsFor(StatusTowerLightBE.SignalState.CRITICAL));
        assertTrue(!critical.soundsFor(StatusTowerLightBE.SignalState.WARNING));
        assertTrue(warning.soundsFor(StatusTowerLightBE.SignalState.WARNING));
        assertTrue(warning.soundsFor(StatusTowerLightBE.SignalState.CRITICAL));
        assertTrue(!warning.soundsFor(StatusTowerLightBE.SignalState.NORMAL));
    }

    @Test
    void lensColoursAreDistinctAndAutoHasNoneOfItsOwn() {
        final java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (StatusTowerLightSettings.Lens lens : StatusTowerLightSettings.Lens.values()) {
            if (lens.isAuto()) {
                assertEquals(0, lens.rgb());
                continue;
            }
            assertTrue(lens.rgb() != 0, lens + " has no colour");
            assertTrue(seen.add(lens.rgb()), lens + " duplicates another lens colour");
        }
    }
}
