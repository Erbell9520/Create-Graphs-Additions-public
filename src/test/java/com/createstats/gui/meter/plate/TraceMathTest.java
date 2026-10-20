package com.createstats.gui.meter.plate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceMathTest {
    @Test
    void aFullRingAtFullWidthIsTheRingItself() {
        final int[] ring = new int[300];
        for (int i = 0; i < 300; i++) {
            ring[i] = i * 3;
        }
        assertArrayEquals(ring, TraceMath.columns(ring, 300, 300));
    }

    @Test
    void aFillingRingFillsFromTheRightSoNowIsAlwaysTheRightEdge() {
        final int[] cols = TraceMath.columns(new int[]{5, 6, 7}, 300, 300);
        assertEquals(TraceMath.EMPTY, cols[0]);
        assertEquals(TraceMath.EMPTY, cols[296]);
        assertEquals(5, cols[297]);
        assertEquals(7, cols[299]);
    }

    @Test
    void aNarrowTraceKeepsASingleSpike() {
        final int[] ring = new int[300];
        ring[151] = 9000;
        final int[] cols = TraceMath.columns(ring, 300, 252);
        int max = 0;
        for (final int v : cols) {
            max = Math.max(max, v);
        }
        assertEquals(9000, max, "a one-sample spike must survive a narrower screen");
    }

    @Test
    void theScaleSteps() {
        assertEquals(10, TraceMath.niceCeil(0));
        assertEquals(10, TraceMath.niceCeil(7));
        assertEquals(800, TraceMath.niceCeil(640));
        assertEquals(1000, TraceMath.niceCeil(1000));
        assertEquals(2500, TraceMath.niceCeil(2048));
        assertEquals(20000, TraceMath.niceCeil(16384));
    }

    @Test
    void anAllZeroSpanIsScaledToTheLifetimePeak() {
        final int[] zeros = new int[300];
        assertEquals(2500, TraceMath.axisFor(zeros, 2048), "a flat line must read as zero against something");
        assertEquals(10, TraceMath.axisFor(zeros, 0));
    }

    @Test
    void theAxisNeverEntersTheLabelBand() {
        final int bottom = 100;
        final int plotH = 64;
        assertEquals(bottom, TraceMath.rowOf(0, 800, bottom, plotH));
        assertEquals(bottom - plotH, TraceMath.rowOf(800, 800, bottom, plotH));
        assertEquals(bottom - plotH, TraceMath.rowOf(5000, 800, bottom, plotH), "a value past the axis is clamped");
        assertEquals(bottom, TraceMath.rowOf(-40, 800, bottom, plotH));
    }

    @Test
    void theEnvelopeIsMadeOfTheSamples() {
        final int e = TraceMath.EMPTY;
        final int[][] env = TraceMath.envelope(new int[]{50, 40, 60, e, 55}, 1);
        assertArrayEquals(new int[]{40, 50}, env[0]);
        assertArrayEquals(new int[]{40, 60}, env[1]);
        assertArrayEquals(new int[]{40, 60}, env[2]);
        assertArrayEquals(new int[]{e, e}, env[3]);
        assertArrayEquals(new int[]{55, 55}, env[4], "an empty neighbour adds nothing");
    }

    @Test
    void theNewestColumnIsNow() {
        assertEquals(0, TraceMath.ageTicks(299, 300, 300, 4));
        assertEquals(1196, TraceMath.ageTicks(0, 300, 300, 4));
        assertTrue(TraceMath.ageTicks(0, 300, 300, 60) > TraceMath.ageTicks(0, 300, 300, 20));
    }
}
