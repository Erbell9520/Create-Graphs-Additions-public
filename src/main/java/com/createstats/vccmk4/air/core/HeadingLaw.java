package com.createstats.vccmk4.air.core;

// take the heading only once the turn has nearly stopped, or it swings back
public final class HeadingLaw {
    static final double K_RUDDER = 1.0;

    static final double K_HEADING = 0.8;

    static final double K_RATE = 1.5;

    static final double K_INT = 0.3;

    static final double CAPTURE_RATE = 1.0;

    static final double DEADBAND = 0.05;

    static final double INTEGRATE_WITHIN = 2.0;

    static final double LEARN = 0.05;

    static final double LEARN_RATE = 0.5;

    private double hold = Double.NaN;
    private double integral;
    private double damping;

    private double asked;

    public void reset() {
        hold = Double.NaN;
        integral = 0.0;
    }

    public double damping() {
        return damping;
    }

    public void restoreDamping(double learned) {
        if (Double.isFinite(learned) && learned >= 0.0) damping = learned;
    }

    public void observe(double rate, double accel, double applied, double inertia) {
        if (Math.abs(rate) < LEARN_RATE || !Double.isFinite(accel) || !Double.isFinite(applied) || !(inertia > 0.0)) return;
        double phi = -rate;
        double r = inertia * accel * Math.PI / 180.0 - applied;
        double e = r - damping * phi;
        damping += LEARN * e * phi / (0.05 + phi * phi);
        damping = Math.max(0.0, damping);
    }

    public double hold() {
        return hold;
    }

    public void holdHeading(double degrees) {
        hold = Double.isFinite(degrees) ? normalise(degrees) : Double.NaN;
    }

    public double moment(double steer, double heading, double rate, double inertia, double maxRate, double dt) {
        double target;
        if (Math.abs(steer) > DEADBAND) {
            hold = Double.NaN;
            target = Math.max(-1.0, Math.min(1.0, steer)) * maxRate;
        } else {
            if (Double.isNaN(hold) && Math.abs(rate) <= CAPTURE_RATE) hold = normalise(heading);
            target = Double.isNaN(hold) ? 0.0
                    : Math.max(-maxRate, Math.min(maxRate, K_HEADING * wrap(hold - heading)));
        }
        asked = target;
        return inertia * rateAccel(target, rate, maxRate, dt) * Math.PI / 180.0 + damping * rate;
    }

    public double rudder(double rate, double maxRate) {
        if (!(maxRate > 0.0) || !Double.isFinite(rate)) return 0.0;
        double t = Double.isFinite(asked) ? asked : 0.0;
        return Math.max(-1.0, Math.min(1.0, (t + K_RUDDER * (t - rate)) / maxRate));
    }

    double rateAccel(double target, double rate, double maxRate, double dt) {
        double err = target - rate;
        if (Math.abs(err) < INTEGRATE_WITHIN) {
            integral = Math.max(-maxRate, Math.min(maxRate, integral + K_INT * err * dt));
        }
        return K_RATE * err + integral;
    }

    public static double bearing(double dx, double dz) {
        return normalise(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    public static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }

    public static double normalise(double degrees) {
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }
}
