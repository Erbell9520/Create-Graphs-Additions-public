package com.createstats.powertrack.core;

public final class JunctionClearance {
    public static final double CLEAR = 19.0 / 16.0;

    public static final double STEP = 1.0 / 32.0;

    public static final double MAX_GAP = 8.0;

    private JunctionClearance() {
    }

    public static double clearing(double[] a, double[] b) {
        final int n = Math.min(a.length, b.length) / 2;
        final double clear2 = CLEAR * CLEAR;
        for (int i = 0; i < n; i++) {
            final double dx = a[2 * i] - b[2 * i];
            final double dz = a[2 * i + 1] - b[2 * i + 1];
            if (dx * dx + dz * dz >= clear2) {
                return Math.min(MAX_GAP, i * STEP);
            }
        }
        return Math.min(MAX_GAP, Math.max(0, n - 1) * STEP);
    }

    public static int samples(double length) {
        return (int) Math.floor(Math.min(length, MAX_GAP + STEP) / STEP) + 1;
    }

    @FunctionalInterface
    public interface Walk {
        void at(double d, double[] xz);
    }

    public static double clearing(Walk a, double lengthA, Walk b, double lengthB) {
        final int n = Math.min(samples(lengthA), samples(lengthB));
        final double clear2 = CLEAR * CLEAR;
        final double[] pa = new double[2];
        final double[] pb = new double[2];
        for (int i = 0; i < n; i++) {
            a.at(Math.min(lengthA, i * STEP), pa);
            b.at(Math.min(lengthB, i * STEP), pb);
            final double dx = pa[0] - pb[0];
            final double dz = pa[1] - pb[1];
            if (dx * dx + dz * dz >= clear2) {
                return Math.min(MAX_GAP, i * STEP);
            }
        }
        return Math.min(MAX_GAP, Math.max(0, n - 1) * STEP);
    }
}
