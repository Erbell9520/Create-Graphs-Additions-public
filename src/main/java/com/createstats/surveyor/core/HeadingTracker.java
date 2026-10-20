package com.createstats.surveyor.core;

public final class HeadingTracker {
    static final double ALPHA = 0.5;
    static final double BETA = 0.12;
    static final double DT = 0.05;

    private double heading = Double.NaN;
    private double rate;

    public void reset() {
        heading = Double.NaN;
        rate = 0.0;
    }

    public void update(double measured) {
        if (!Double.isFinite(measured)) return;
        if (Double.isNaN(heading)) {
            heading = measured;
            rate = 0.0;
            return;
        }
        double predicted = heading + rate * DT;
        double surprise = PathSpline.wrapAngle(measured - predicted);
        heading = PathSpline.wrapAngle(predicted + ALPHA * surprise);
        rate += BETA * surprise / DT;
    }

    public double heading() {
        return heading;
    }

    public double rate() {
        return rate;
    }
}
