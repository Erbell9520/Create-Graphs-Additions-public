package com.createstats.vccmk4.air.core;

public final class GasLaw {
    public static final double FILL_TICKS = 180.0;

    public static final double RESPONSE_FACTOR = 5.0;

    public static final double RESPONSE_RANGE = 0.05;

    public static final double STRENGTH = 1.5;

    public static final int STEPS = 15;

    private GasLaw() {
    }

    public static double nudge(double gap, double capacity) {
        if (!(capacity > 0.0) || !Double.isFinite(gap)) return 0.0;
        double x = gap / (capacity * RESPONSE_RANGE);
        return gap / FILL_TICKS * (1.0 + RESPONSE_FACTOR / (1.0 + 3.0 * x * x));
    }

    public static double gapFor(double step, double capacity) {
        if (!(capacity > 0.0) || !Double.isFinite(step)) return 0.0;
        double lo = -5.0 * capacity;
        double hi = 5.0 * capacity;
        for (int i = 0; i < 64; i++) {
            double mid = 0.5 * (lo + hi);
            if (nudge(mid, capacity) < step) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    public static double step(double gas, double target, double capacity) {
        return gas + nudge(target - gas, capacity);
    }

    public static double nearTicks() {
        return FILL_TICKS / (1.0 + RESPONSE_FACTOR);
    }

    public static double holds(double gas, double strength, double pressure) {
        return gas * strength * pressure;
    }
}
