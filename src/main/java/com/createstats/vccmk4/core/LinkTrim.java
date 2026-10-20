package com.createstats.vccmk4.core;

public final class LinkTrim {
    public static final double STIFFNESS = 4.0;

    public static final double DAMPING = 3.2;

    public static final double LIMIT = 6.0;

    public static final double SPRING_RATE = 72.5;

    public static final double SPRING_SHARE = 0.8;

    public static final double SNAP_WARN = 0.7;

    private LinkTrim() {
    }

    public static double rope(double mass, double gap, double gapRate) {
        if (!(mass > 0.0)) return 0.0;
        double f = mass * (STIFFNESS * gap + DAMPING * gapRate);
        return limit(f, mass);
    }

    public static double spring(double mass, double extension, double rate, double sizeScale) {
        return stretch(mass, springForce(extension, sizeScale), rate);
    }

    public static double stretch(double mass, double force, double rate) {
        if (!(mass > 0.0) || !Double.isFinite(force)) return 0.0;
        return limit(SPRING_SHARE * force + mass * DAMPING * (Double.isFinite(rate) ? rate : 0.0), mass);
    }

    public static double of(LinkKind.Reading reading, double mass, double value, double force, double rate) {
        if (!Double.isFinite(value)) return 0.0;
        return switch (reading) {
            case GAP -> rope(mass, value, Double.isFinite(rate) ? rate : 0.0);
            case STRETCH -> stretch(mass, force, rate);
            default -> 0.0;
        };
    }

    public static double springForce(double extension, double sizeScale) {
        return SPRING_RATE * Math.max(0.0, sizeScale) * extension;
    }

    public static double snapLength(double desiredLength) {
        return desiredLength * 4.0 + 2.0;
    }

    public static double snapShare(double distance, double desiredLength) {
        double snap = snapLength(desiredLength);
        return snap > 0.0 ? Math.max(0.0, distance) / snap : 0.0;
    }

    static double limit(double f, double mass) {
        double lim = LIMIT * mass;
        if (!Double.isFinite(f)) return 0.0;
        return Math.max(-lim, Math.min(lim, f));
    }
}
