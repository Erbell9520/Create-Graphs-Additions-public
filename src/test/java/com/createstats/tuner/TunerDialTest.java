package com.createstats.tuner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunerDialTest {
    private static final double GAP = Math.toRadians(14.0);
    private static final double GAP_INNER = Math.toRadians(20.0);

    private static void assertSpread(TunerDial.Chip[] chips, double gapOuter, double gapInner) {
        for (int ring = 0; ring <= 1; ring++) {
            double prev = Double.NEGATIVE_INFINITY;
            for (TunerDial.Chip c : chips) {
                if (c.ring() != ring) {
                    continue;
                }
                assertTrue(c.angle() >= TunerDial.START - 1e-9 && c.angle() <= TunerDial.START + TunerDial.SWEEP + 1e-9,
                        "off the arc");
                if (prev != Double.NEGATIVE_INFINITY) {
                    assertTrue(c.angle() - prev >= (ring == 0 ? gapOuter : gapInner) - 1e-9,
                            "chips closer than the gap, or out of frequency order");
                }
                prev = c.angle();
            }
        }
    }

    @Test
    void stationsWithRoomSitExactlyAtTheirFrequency() {
        final double[] f = {0.0, 0.25, 0.5, 0.75, 1.0};
        final TunerDial.Chip[] chips = TunerDial.place(f, GAP, GAP_INNER);
        for (int i = 0; i < f.length; i++) {
            assertEquals(0, chips[i].ring());
            assertEquals(TunerDial.angleOf(f[i]), chips[i].angle(), 1e-12);
        }
    }

    @Test
    void neighboursAFewTenthsApartArePushedApartSymmetrically() {
        final double[] want = {3.0, 3.1};
        final double[] got = TunerDial.spread(want, 0, 10, 1.0);
        assertEquals(1.0, got[1] - got[0], 1e-9);
        assertEquals(3.05, (got[0] + got[1]) / 2, 1e-9, "the pair still straddles where they belong");
    }

    @Test
    void aChipAtTheEndOfTheArcIsHeldOnIt() {
        final double[] got = TunerDial.spread(new double[] {9.8, 9.9, 10.0}, 0, 10, 1.0);
        assertArrayEquals(new double[] {8.0, 9.0, 10.0}, got, 1e-9);
    }

    @Test
    void sixteenStationsOfOneBiomeFitOneArcAtASmallScale() {
        final double[] f = new double[16];
        for (int i = 0; i < 16; i++) {
            f[i] = i < 8 ? 0.25 + i * 0.01 : 0.5 + (i - 8) * 0.06;
        }
        final TunerDial.Chip[] chips = TunerDial.place(f, GAP, GAP_INNER);
        for (TunerDial.Chip c : chips) {
            assertEquals(0, c.ring(), "sixteen at fourteen degrees fit a 300 degree arc");
        }
        assertSpread(chips, GAP, GAP_INNER);
    }

    @Test
    void aCrowdTakesTheInnerRingAndStillNeverOverlaps() {
        final double[] f = new double[40];
        for (int i = 0; i < f.length; i++) {
            f[i] = (i * 7 % 40) / 40.0;
        }
        java.util.Arrays.sort(f);
        final TunerDial.Chip[] chips = TunerDial.place(f, GAP, GAP_INNER);
        int inner = 0;
        for (TunerDial.Chip c : chips) {
            inner += c.ring();
        }
        assertEquals(20, inner, "every other chip moves inward");

        final double outerGap = Math.min(GAP, TunerDial.SWEEP / 19);
        final double innerGap = Math.min(GAP_INNER, TunerDial.SWEEP / 19);
        assertSpread(chips, outerGap, innerGap);
    }

    @Test
    void thePointerFindsTheChipItIsOnAndNothingInTheGap() {
        final TunerDial.Chip[] chips = TunerDial.place(new double[] {0.0, 0.5, 1.0}, GAP, GAP_INNER);
        final double top = TunerDial.angleOf(0.5);

        assertEquals(1, TunerDial.pick(chips, 0, Math.atan2(-1, 0), GAP / 2));
        assertEquals(1, TunerDial.pick(chips, 0, top + GAP * 0.4, GAP / 2));
        assertEquals(-1, TunerDial.pick(chips, 0, top + GAP, GAP / 2), "between chips is nothing");
        assertEquals(-1, TunerDial.pick(chips, 1, top, GAP / 2), "nothing on the inner ring");

        assertEquals(2, TunerDial.pick(chips, 0, TunerDial.START + TunerDial.SWEEP + GAP * 0.3, GAP / 2));
        assertEquals(0, TunerDial.pick(chips, 0, TunerDial.START - GAP * 0.3, GAP / 2));
    }
}
