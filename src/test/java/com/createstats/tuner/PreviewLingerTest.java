package com.createstats.tuner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewLingerTest {
    @Test
    void itHoldsAtFullStrengthForFiveSeconds() {
        assertEquals(5_000L, PreviewLinger.HOLD_MS);
        assertEquals(1.0, PreviewLinger.strength(0));
        assertEquals(1.0, PreviewLinger.strength(4_999));
        assertEquals(1.0, PreviewLinger.strength(5_000));
        assertEquals(1.0, PreviewLinger.strength(-250), "a clock step backwards never dims it");
        assertFalse(PreviewLinger.gone(5_000));
    }

    @Test
    void thenItFadesOutOverTwoSecondsAndOnlyEverDims() {
        assertEquals(2_000L, PreviewLinger.FADE_MS);
        assertEquals(0.5, PreviewLinger.strength(6_000), 1.0e-9, "halfway through the fade is half strength");
        double last = 1.0;
        for (long t = 5_001; t < 7_000; t += 37) {
            final double s = PreviewLinger.strength(t);
            assertTrue(s < last && s > 0.0, "strictly dimming at " + t + ": " + s);
            last = s;
        }
        assertTrue(PreviewLinger.strength(5_100) > 0.99, "it starts to go slowly");
        assertTrue(PreviewLinger.strength(6_900) < 0.01, "and ends slowly");
    }

    @Test
    void afterSevenSecondsItIsGone() {
        assertEquals(0.0, PreviewLinger.strength(7_000));
        assertEquals(0.0, PreviewLinger.strength(60_000));
        assertFalse(PreviewLinger.gone(6_999));
        assertTrue(PreviewLinger.gone(7_000));
    }

    @Test
    void aFadeScalesTheAlphaAndLeavesTheColour() {
        assertEquals(0xE0123456, PreviewLinger.fade(0xE0123456, 1.0));
        assertEquals(0x70123456, PreviewLinger.fade(0xE0123456, 0.5));
        assertEquals(0x00123456, PreviewLinger.fade(0xE0123456, 0.0));
        assertEquals(0x00123456, PreviewLinger.fade(0xE0123456, -3.0), "clamped below");
        assertEquals(0xC0B0B0B0, PreviewLinger.fade(0xC0B0B0B0, 7.0), "clamped above");
    }
}
