package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionClearanceTest {
    private static double[] straight(double dx, double dz, double length) {
        final int n = JunctionClearance.samples(length);
        final double[] out = new double[2 * n];
        final double m = Math.hypot(dx, dz);
        for (int i = 0; i < n; i++) {
            out[2 * i] = dx / m * i * JunctionClearance.STEP;
            out[2 * i + 1] = dz / m * i * JunctionClearance.STEP;
        }
        return out;
    }

    private static double[] arc(double radius, double length) {
        final int n = JunctionClearance.samples(length);
        final double[] out = new double[2 * n];
        for (int i = 0; i < n; i++) {
            final double a = i * JunctionClearance.STEP / radius;
            out[2 * i] = radius - radius * Math.cos(a);
            out[2 * i + 1] = radius * Math.sin(a);
        }
        return out;
    }

    @Test
    @DisplayName("a trunk leaving the other way is clear at once")
    void trunk() {
        final double d = JunctionClearance.clearing(straight(0, -1, 10), straight(0, 1, 10));
        assertEquals(JunctionClearance.CLEAR / 2, d, JunctionClearance.STEP);
        assertTrue(d < SectionGraph.JUNCTION_GAP_MIN, "the floor then decides it");
    }

    @Test
    @DisplayName("a split clears where the curve has bent CLEAR away, later for a gentler curve")
    void split() {
        final double sharp = JunctionClearance.clearing(straight(0, 1, 20), arc(8, 20));
        final double gentle = JunctionClearance.clearing(straight(0, 1, 20), arc(20, 20));

        assertEquals(Math.sqrt(2 * 8 * JunctionClearance.CLEAR), sharp, 0.15);
        assertTrue(gentle > sharp, "a gentler turnout takes longer to clear");
    }

    @Test
    @DisplayName("a pair that never clears is gapped to the shorter edge, and never more than MAX_GAP")
    void neverClears() {
        assertEquals(2.0, JunctionClearance.clearing(straight(0, 1, 2), straight(0, 1, 2)), 1e-9);
        assertEquals(JunctionClearance.MAX_GAP,
                JunctionClearance.clearing(straight(0, 1, 30), straight(0, 1, 30)), 1e-9);
    }

    private static JunctionClearance.Walk lazy(double[] samples) {
        return (d, xz) -> {
            final int i = (int) Math.round(d / JunctionClearance.STEP);
            xz[0] = samples[2 * i];
            xz[1] = samples[2 * i + 1];
        };
    }

    @Test
    @DisplayName("the walk taken only as far as the lines part answers exactly as the sampled walk, every case")
    void lazyWalkIsTheSampledWalk() {
        final double[][][] pairs = {
                {straight(0, -1, 10), straight(0, 1, 10)},
                {straight(0, 1, 20), arc(8, 20)},
                {straight(0, 1, 20), arc(20, 20)},
                {straight(0, 1, 2), straight(0, 1, 2)},
                {straight(0, 1, 30), straight(0, 1, 30)},
                {straight(1, 0, 5), straight(0, 1, 5)},
                {arc(12, 3.3), straight(1, 1, 7)}};
        final double[][] lengths = {{10, 10}, {20, 20}, {20, 20}, {2, 2}, {30, 30}, {5, 5}, {3.3, 7}};
        for (int k = 0; k < pairs.length; k++) {
            final double sampled = JunctionClearance.clearing(pairs[k][0], pairs[k][1]);
            final double walked = JunctionClearance.clearing(lazy(pairs[k][0]), lengths[k][0], lazy(pairs[k][1]),
                    lengths[k][1]);
            assertEquals(sampled, walked, 1e-12, "pair " + k);
        }
    }

    @Test
    @DisplayName("a crossing at right angles clears inside the floor")
    void crossing() {
        final double d = JunctionClearance.clearing(straight(1, 0, 5), straight(0, 1, 5));
        assertTrue(d < SectionGraph.JUNCTION_GAP_MIN);
    }
}
