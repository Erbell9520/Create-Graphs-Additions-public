package com.createstats.vccmk4.air.core;

// learn the drag with NLMS, it's never guessed
public final class CruiseLaw {
    static final double K_SPEED = 0.6;

    static final double SHED_BAND = 0.5;

    static final double K_INT = 0.08;

    static final double INT_SHARE = 0.25;

    static final double LEARN = 0.05;

    static final double LEARN_SPEED = 0.5;

    public static final double DRAG_PRIOR = 0.2;

    private double drag = Double.NaN;
    private double integral;

    public void reset(double mass) {
        drag = DRAG_PRIOR * Math.max(1.0, mass);
        integral = 0.0;
    }

    public double drag() {
        return drag;
    }

    public void restore(double learned) {
        if (Double.isFinite(learned) && learned >= 0.0) drag = learned;
    }

    public double force(double speed, double target, double mass, double pressure, double accelLimit,
                        double maxForward, double maxReverse, double dt) {
        if (!Double.isFinite(drag)) reset(mass);
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        double err = target - speed;
        if (sheds(speed, target)) {
            double airRate = mass > 0.0 ? drag * p / mass * Math.abs(speed) : 0.0;
            double limit = accelLimit + airRate;
            double aDes = Math.max(-limit, Math.min(limit, K_SPEED * err));
            double want = mass * aDes + drag * speed * p;
            if (want * speed > 0.0) want = 0.0;
            return Math.max(-maxReverse, Math.min(maxForward, want));
        }
        double aDes = Math.max(-accelLimit, Math.min(accelLimit, K_SPEED * err));
        double want = mass * aDes + drag * speed * p + integral;
        double out = Math.max(-maxReverse, Math.min(maxForward, want));
        boolean pinnedUp = want > maxForward && err > 0.0;
        boolean pinnedDown = want < -maxReverse && err < 0.0;
        if (!pinnedUp && !pinnedDown) {
            double limit = INT_SHARE * Math.max(maxForward, maxReverse);
            integral = Math.max(-limit, Math.min(limit, integral + K_INT * mass * err * dt));
        }
        return out;
    }

    static boolean sheds(double speed, double target) {
        double err = target - speed;
        return err * speed < 0.0 && Math.abs(err) > SHED_BAND;
    }

    public void observe(double speed, double accel, double applied, double mass, double pressure) {
        if (!Double.isFinite(drag)) reset(mass);
        if (Math.abs(speed) < LEARN_SPEED || !Double.isFinite(accel) || !Double.isFinite(applied)) return;
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        double phi = -speed * p;
        double r = mass * accel - applied;
        double e = r - drag * phi;
        drag += LEARN * e * phi / (0.05 + phi * phi);
        drag = Math.max(0.0, Math.min(50.0 * Math.max(1.0, mass), drag));
    }

    public double topSpeed(double f0, double fade, double pressure) {
        if (!(f0 > 0.0)) return Double.NaN;
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        double d = (Double.isFinite(drag) ? drag : 0.0) * p + Math.max(0.0, fade);
        return d > 1.0e-9 ? f0 / d : Double.NaN;
    }

    public void clearIntegral() {
        integral = 0.0;
    }
}
