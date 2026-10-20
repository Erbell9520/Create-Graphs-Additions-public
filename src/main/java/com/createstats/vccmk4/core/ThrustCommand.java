package com.createstats.vccmk4.core;

public final class ThrustCommand {
    public static final int STEPS = 64;

    public static final double BOOSTER_THRESHOLD = 0.5;

    public static final double LIT = 0.02;

    private ThrustCommand() {
    }

    public static double forKind(JetKind kind, double asked) {
        if (kind == null || !kind.oneWay()) return quantise(clamp(asked, -1.0, 1.0));
        double u = oneWay(asked);
        return kind == JetKind.BOOSTER ? booster(u) : quantise(u);
    }

    public static double oneWay(double asked) {
        return Double.isNaN(asked) ? 0.0 : clamp(asked, 0.0, 1.0);
    }

    public static double booster(double u) {
        return u >= BOOSTER_THRESHOLD ? 1.0 : 0.0;
    }

    public static double quantise(double u) {
        if (Double.isNaN(u)) return 0.0;
        return Math.round(u * STEPS) / (double) STEPS;
    }

    public static double ceiling(double force, double lit, double config) {
        if (Double.isFinite(force) && Double.isFinite(lit) && lit > LIT && force > 0.0) return force / lit;
        return Double.isFinite(config) && config > 0.0 ? config : 0.0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
