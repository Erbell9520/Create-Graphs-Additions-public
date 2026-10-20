package com.createstats.client.timeswitch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TimeSwitchRingLayoutTest {
    private static final int KEEP = 24;

    private static final int HINT = 13;

    @Test
    void everyAutomaticScaleScreenFitsWithTheGaps() {
        for (int w = 320; w <= 1100; w++) {
            for (int h = 240; h <= 620; h++) {
                check(w, h, KEEP);
            }
        }
    }

    @Test
    void aLongerKeepStillFitsTheNarrowestScreen() {
        for (int keep = 10; keep <= 34; keep++) {
            for (int h = 240; h <= 300; h++) {
                check(320, h, keep);
            }
        }
    }

    @Test
    void wideScreensUseTheFullReach() {
        final TimeSwitchRingLayout at1080p = TimeSwitchRingLayout.of(960, 540, KEEP);
        assertEquals(TimeSwitchRingLayout.PANEL_W + 12, at1080p.rx());
        assertEquals(TimeSwitchRingLayout.PANEL_H + 34, at1080p.ry());
    }

    @Test
    void theHubIsTheWordAndAPadEitherSide() {
        final int[][] rects = TimeSwitchRingLayout.of(640, 360, KEEP).rects(640, 360);
        assertEquals(KEEP + 2 * TimeSwitchRingLayout.PAD, rects[4][2]);
        assertEquals(TimeSwitchRingLayout.HUB_H, rects[4][3]);

        assertTrue(Math.abs(rects[4][0] + rects[4][2] / 2.0 - 320) <= 0.5);
    }

    private static void check(int w, int h, int keep) {
        final int[][] r = TimeSwitchRingLayout.of(w, h, keep).rects(w, h);
        for (int k = 0; k < r.length; k++) {
            final int[] a = r[k];
            if (a[0] < 0 || a[1] < 0 || a[0] + a[2] > w || a[1] + a[3] > h - HINT) {
                fail("button " + k + " leaves a " + w + " x " + h + " screen (Keep " + keep + " px): "
                        + java.util.Arrays.toString(a));
            }
            for (int j = k + 1; j < r.length; j++) {
                final int gap = gap(a, r[j]);
                final int least = j == 4 ? TimeSwitchRingLayout.GAP : 1;
                if (gap < least) {
                    fail("buttons " + k + " and " + j + " are " + gap + " px apart on a " + w + " x " + h
                            + " screen (Keep " + keep + " px), want at least " + least + ": "
                            + java.util.Arrays.toString(a) + " " + java.util.Arrays.toString(r[j]));
                }
            }
        }
    }

    private static int gap(int[] a, int[] b) {
        final int dx = Math.max(b[0] - (a[0] + a[2]), a[0] - (b[0] + b[2]));
        final int dy = Math.max(b[1] - (a[1] + a[3]), a[1] - (b[1] + b[3]));
        return Math.max(dx, dy);
    }
}
