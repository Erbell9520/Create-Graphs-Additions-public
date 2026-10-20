package com.createstats.cinecam.core;

public final class FocusRange {
    public static final double FLOOR = 0.25;

    private FocusRange() {
    }

    public static double sigma(double range) {
        return FLOOR + range * range / 400.0;
    }

    public static double wander(long seed, double t) {
        double p1 = (seed & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        double p2 = ((seed >>> 16) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        double p3 = ((seed >>> 32) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        double v = 0.5 * Math.sin(t * 0.73 + p1) + 0.3 * Math.sin(t * 1.91 + p2) + 0.2 * Math.sin(t * 3.37 + p3);
        return Math.max(-1.0, Math.min(1.0, v));
    }

    public static double reading(double range, long seed, double t) {
        return Math.max(FLOOR, range + sigma(range) * wander(seed, t));
    }
}
