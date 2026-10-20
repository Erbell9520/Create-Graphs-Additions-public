package com.createstats.powertrack.core;

public final class SlideChase {
    public static final double RATE = 10.0;

    public static final double MAX_SPEED = 4.0;

    public static final double MAX_STEP = 0.1;

    private double value;
    private double last = Double.NaN;

    public double step(double timeSeconds, double target) {
        if (Double.isNaN(last)) {
            last = timeSeconds;
            return value;
        }
        final double dt = Math.max(0.0, Math.min(MAX_STEP, timeSeconds - last));
        last = timeSeconds;
        final double gap = target - value;
        double move = gap * (1.0 - Math.exp(-RATE * dt));
        final double cap = MAX_SPEED * dt;
        move = Math.max(-cap, Math.min(cap, move));
        value += move;
        return value;
    }

    public double value() {
        return value;
    }
}
