package com.createstats.cinecam.core;

// the ray started inside our own upper block, vanilla's clip says hit, every check failed
public final class SightLine {
    public static final double CLEAR = 0.02;

    public static final double NEAR_AIM = 1.5;

    public static final double CELLS_HIGH = 2.0;

    private SightLine() {
    }

    public static double exit(double ox, double oy, double oz, double dx, double dy, double dz) {
        return Math.min(slab(ox, dx, 1.0), Math.min(slab(oy, dy, CELLS_HIGH), slab(oz, dz, 1.0)));
    }

    private static double slab(double o, double d, double hi) {
        if (d > 1e-12) return (hi - o) / d;
        if (d < -1e-12) return -o / d;
        return Double.POSITIVE_INFINITY;
    }

    public static boolean reached(boolean miss, boolean onTargetShip, double hitToAim) {
        return miss || onTargetShip || hitToAim < NEAR_AIM;
    }
}
