package com.createstats.aerial;

public final class NeedleSpring {
    public static final double OMEGA = Math.sqrt(150.0);

    private double x = Double.NaN;
    private double v;

    public double follow(double target, double dt) {
        if (!Double.isFinite(target)) {
            return target;
        }
        if (!Double.isFinite(this.x) || !(dt > 0.0)) {
            if (!Double.isFinite(this.x)) {
                this.x = target;
                this.v = 0.0;
            }
            return this.x;
        }
        final double d = this.x - target;
        final double c = this.v + OMEGA * d;
        final double e = Math.exp(-OMEGA * dt);
        this.x = target + (d + c * dt) * e;
        this.v = (this.v - OMEGA * c * dt) * e;
        return this.x;
    }

    public double snap(double target) {
        this.x = target;
        this.v = 0.0;
        return target;
    }

    public void reset() {
        this.x = Double.NaN;
        this.v = 0.0;
    }
}
