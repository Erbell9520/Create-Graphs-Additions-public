package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedBarTest {
    private static int lit(LedBar.Frame f) {
        int n = 0;
        for (int i = 0; i < LedBar.LEDS; i++) {
            if (f.role()[i] != LedBar.OFF && f.level()[i] > 0.0f) {
                n++;
            }
        }
        return n;
    }

    @Test
    void theThrottleLightsFromTheLeftInShiftColoursAndReverseFromTheRightInRed() {
        final LedBar.Frame half = LedBar.live(0.4, LedBar.Warning.NONE, 0.0);
        assertEquals(6, lit(half), "0.4 of fifteen");
        assertEquals(LedBar.LOW, half.role()[0]);
        assertEquals(LedBar.MID, half.role()[5]);
        assertEquals(LedBar.OFF, half.role()[6]);
        final LedBar.Frame back = LedBar.live(-0.2, LedBar.Warning.NONE, 0.0);
        assertEquals(3, lit(back));
        assertEquals(LedBar.BAD, back.role()[14]);
        assertEquals(LedBar.OFF, back.role()[0]);
    }

    @Test
    void aWarningFlashesTheWholeBarSixTimesASecondWithTheThrottleBetween() {
        final LedBar.Frame on = LedBar.live(0.4, LedBar.Warning.SPIN, 0.0);
        assertEquals(LedBar.LEDS, lit(on));
        assertEquals(LedBar.COOL, on.role()[14]);
        final LedBar.Frame off = LedBar.live(0.4, LedBar.Warning.SPIN, 1.0 / (LedBar.FLASH_HZ * 2.0) + 1e-6);
        assertEquals(6, lit(off), "between flashes the live bar");
        assertEquals(LedBar.BAD, LedBar.live(0.0, LedBar.Warning.LOCK, 0.0).role()[0]);
        int flips = 0;
        boolean was = LedBar.flashOn(0.0);
        for (int k = 1; k <= 1000; k++) {
            final boolean now = LedBar.flashOn(k / 1000.0);
            flips += now != was ? 1 : 0;
            was = now;
        }
        assertEquals(12, flips, "six on, six off in a second");
    }

    @Test
    void theSelfTestLightsFromTheMiddleOutAndEndsAllLit() {
        assertEquals(0, lit(LedBar.selfTest(0.0)));
        final LedBar.Frame early = LedBar.selfTest(LedBar.SELF_TEST * 0.2);
        assertTrue(early.role()[7] != LedBar.OFF, "the middle first");
        assertEquals(LedBar.OFF, early.role()[0]);
        assertEquals(LedBar.LEDS, lit(LedBar.selfTest(LedBar.SELF_TEST)));
    }

    @Test
    void theSweepRunsEndToEndAndBack() {
        assertEquals(1.0f, LedBar.sweep(0.0).level()[0], 0.0f);
        assertEquals(1.0f, LedBar.sweep(LedBar.SWEEP_PASS).level()[14], 0.0f);
        assertEquals(1.0f, LedBar.sweep(2.0 * LedBar.SWEEP_PASS).level()[0], 0.0f);
        assertTrue(lit(LedBar.sweep(0.3)) <= 3, "one LED and its tail");
    }
}
