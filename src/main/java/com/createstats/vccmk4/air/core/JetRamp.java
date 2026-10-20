package com.createstats.vccmk4.air.core;

// never instant. a redstone step per n ticks max
public final class JetRamp {
    private JetRamp() {
    }

    public static double low(double level, Feel feel) {
        return clamp01(clamp01(level) - step(feel));
    }

    public static double high(double level, double cap, Feel feel) {
        double lo = low(level, feel);
        return Math.max(lo, Math.min(clamp01(cap), clamp01(level) + step(feel)));
    }

    public static double toward(double level, double wanted, Feel feel) {
        double w = Double.isFinite(wanted) ? clamp01(wanted) : 0.0;
        return Math.max(low(level, feel), Math.min(high(level, 1.0, feel), w));
    }

    public static boolean settled(double level, double target, Feel feel) {
        return Math.abs(clamp01(level) - clamp01(target)) <= step(feel) + 1.0e-9;
    }

    public static int ticks(double from, double to, Feel feel) {
        return (int) Math.ceil(Math.abs(clamp01(to) - clamp01(from)) / step(feel) - 1.0e-9);
    }

    private static double step(Feel feel) {
        return (feel == null ? Feel.STANDARD : feel).jetStep();
    }

    static double clamp01(double v) {
        return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.0;
    }
}
