package com.createstats.vccmk4.core;

// detent at zero on the way through
public final class Lever {
    public static final int FULL_TICKS = 20;

    private double value;
    private boolean detent;

    public double value() {
        return value;
    }

    public void reset() {
        value = 0.0;
        detent = false;
    }

    public double step(boolean hold, double input) {
        double in = GroundSteer.clamp(input, -1.0, 1.0);
        if (!hold) {
            value = in;
            detent = false;
            return value;
        }
        if (in == 0.0) {
            detent = false;
            return value;
        }
        if (detent) return value;
        double next = GroundSteer.clamp(value + in / FULL_TICKS, -1.0, 1.0);

        boolean reachedZero = value != 0.0 && (Math.abs(next) < 1.0e-9 || Math.signum(next) != Math.signum(value));
        if (reachedZero) {
            next = 0.0;
            detent = true;
        }
        value = next;
        return value;
    }
}
