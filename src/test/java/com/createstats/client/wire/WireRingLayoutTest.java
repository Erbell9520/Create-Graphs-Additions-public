package com.createstats.client.wire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireRingLayoutTest {
    private static final double GAP = Math.toRadians(40.0);

    private static double minPairGap(double[] a) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < a.length; i++) {
            for (int j = i + 1; j < a.length; j++) {
                min = Math.min(min, WireRingLayout.between(a[i], a[j]));
            }
        }
        return min;
    }

    @Test
    void wiresAlreadyApartAreLeftWhereTheyPoint() {
        final double[] in = {0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};
        assertArrayEquals(in, WireRingLayout.spread(in, GAP), 1e-12);
    }

    @Test
    void twoWiresLeavingTogetherArePushedApartSymmetrically() {
        final double[] out = WireRingLayout.spread(new double[] {1.0, 1.1}, GAP);
        assertEquals(GAP, WireRingLayout.between(out[0], out[1]), 1e-6);

        assertEquals(1.05, (out[0] + out[1]) / 2, 1e-6);
        assertTrue(out[0] < out[1], "cyclic order kept");
    }

    @Test
    void identicalDirectionsStillComeApart() {
        final double[] out = WireRingLayout.spread(new double[] {0.0, 0.0, 0.0}, GAP);
        assertTrue(minPairGap(out) >= GAP - 1e-6, "gap " + Math.toDegrees(minPairGap(out)));
    }

    @Test
    void aFullIndustrialConnectorFitsEvenWhenAllSixBunch() {
        final double[] out = WireRingLayout.spread(new double[] {0.1, 0.15, 0.2, 0.25, 0.3, 0.35}, GAP);
        assertTrue(minPairGap(out) >= GAP - 1e-6, "gap " + Math.toDegrees(minPairGap(out)));
    }

    @Test
    void moreWiresThanFitAreSharedEvenlyInsteadOfNeverSettling() {
        final double[] in = new double[12];
        final double[] out = WireRingLayout.spread(in, GAP);

        assertTrue(minPairGap(out) >= Math.toRadians(30.0) * 0.98 - 1e-6,
                "gap " + Math.toDegrees(minPairGap(out)));
    }

    @Test
    void wrapAroundZeroIsOneNeighbourhood() {
        final double[] out = WireRingLayout.spread(new double[] {Math.toRadians(359), Math.toRadians(1)}, GAP);
        assertTrue(minPairGap(out) >= GAP - 1e-6);
        assertTrue(WireRingLayout.between(out[0], Math.toRadians(340)) < 1e-6, "moved down by half the shortfall");
    }

    @Test
    void sneakScrollWalksEveryWireThenWrapsBothWays() {
        assertEquals(0, WireRingLayout.nextPick(-1, 3, -1));
        assertEquals(2, WireRingLayout.nextPick(1, 3, -1));
        assertEquals(-1, WireRingLayout.nextPick(2, 3, -1));
        assertEquals(2, WireRingLayout.nextPick(-1, 3, 1));
        assertEquals(-1, WireRingLayout.nextPick(0, 3, 1));

        for (int start = -1; start < 3; start++) {
            int at = start;
            int back = start;
            for (int k = 0; k < 4; k++) {
                at = WireRingLayout.nextPick(at, 3, -1);
                back = WireRingLayout.nextPick(back, 3, 1);
            }
            assertEquals(start, at);
            assertEquals(start, back);
        }
    }

    @Test
    void aPickPastTheEndOfAShrunkConnectorIsTreatedAsEveryWire() {
        assertEquals(0, WireRingLayout.nextPick(5, 2, -1));
        assertEquals(1, WireRingLayout.nextPick(5, 2, 1));

        assertEquals(0, WireRingLayout.nextPick(-1, 1, -1));
        assertEquals(-1, WireRingLayout.nextPick(0, 1, -1));
        assertEquals(-1, WireRingLayout.nextPick(-1, 0, 1));
    }
}
