package com.createstats.surveyor.core;

public final class LongitudinalControl {
    public enum Mode {
        DRIVE, COAST, BRAKE, HOLD, REVERSE
    }

    public record Out(double throttle, double brake, double aRef, double vRef, double aCmd, Mode mode) {
        public static final Out HOLD = new Out(0.0, 1.0, 0.0, 0.0, 0.0, Mode.HOLD);
    }

    static final double DT = 0.05;

    static final double KP = 1.0;
    static final double KI = 0.4;
    static final double I_MAX = 1.5;

    static final double RESYNC = 1.5;

    static final double LINEAR = 1.6;

    static final double CRAWL = 0.3;

    private double vRef = Double.NaN;
    private double aRef;
    private double integral;

    public void reset() {
        vRef = Double.NaN;
        aRef = 0.0;
        integral = 0.0;
    }

    public double vRef() {
        return vRef;
    }

    public double aRef() {
        return aRef;
    }

    public Out drive(double v, double goal, double accel, double decel, double jerk, PlantModel plant, double grade) {
        double g = Math.max(0.0, goal);
        if (Double.isNaN(vRef) || Math.abs(v - vRef) > RESYNC) {
            vRef = Math.max(0.0, v);
            integral = 0.0;
        }
        double dv = g - vRef;
        double reach = Math.sqrt(2.0 * jerk * Math.abs(dv));
        double want = Math.copySign(Math.min(reach, LINEAR * Math.abs(dv)), dv);
        want = clamp(want, -decel, accel);
        aRef += clamp(want - aRef, -jerk * DT, jerk * DT);
        vRef = Math.max(0.0, vRef + aRef * DT);
        if (vRef == 0.0 && aRef < 0.0) aRef = 0.0;
        return realise(v, plant, grade);
    }

    public Out stopWithin(double v, double distance, double maxDecel, PlantModel plant, double grade) {
        if (distance <= 0.02 || v <= 0.02) {
            vRef = 0.0;
            aRef = 0.0;
            integral = 0.0;
            return Out.HOLD;
        }
        double need = -Math.min(maxDecel, v * v / (2.0 * distance));
        aRef = need;
        vRef = v;
        integral = 0.0;
        return invert(v, need, plant, grade, need, v);
    }

    public Out hold() {
        vRef = 0.0;
        aRef = 0.0;
        integral = 0.0;
        return Out.HOLD;
    }

    public Out reverse(double v, double speed, PlantModel plant) {
        reset();
        double want = -Math.abs(speed);

        double a = want - v;
        double u = clamp((want + plant.tau() * a) / Math.max(0.5, plant.k()), -1.0, 0.0);
        return new Out(u, 0.0, 0.0, want, a, Mode.REVERSE);
    }

    private Out realise(double v, PlantModel plant, double grade) {
        double err = vRef - v;
        double aCmd = aRef + KP * err + integral;
        Out out = invert(v, aCmd, plant, grade, aRef, vRef);

        boolean pinnedUp = out.throttle() >= 1.0 && err > 0.0;
        boolean pinnedDown = out.brake() >= 1.0 && err < 0.0;
        if (!pinnedUp && !pinnedDown) integral = clamp(integral + KI * err * DT, -I_MAX, I_MAX);
        return out;
    }

    static Out invert(double v, double a, PlantModel plant, double grade, double aRef, double vRef) {
        double tau = plant.tau();
        double need = (Math.max(0.0, v) + tau * (a - grade)) / plant.k();
        if (need >= 0.0) {
            double u = Math.min(1.0, need);
            return new Out(u, 0.0, aRef, vRef, a, u < 1.0e-3 ? Mode.COAST : Mode.DRIVE);
        }
        double speed = Math.max(CRAWL, v);
        double b = clamp((tau * (grade - a) / speed - 1.0) / plant.beta(), 0.0, 1.0);
        return new Out(0.0, b, aRef, vRef, a, b < 1.0e-3 ? Mode.COAST : Mode.BRAKE);
    }

    public static double stopSpeed(double distance, double decel, double jerk) {
        if (!(distance > 0.0)) return 0.0;
        double r = decel * decel / Math.max(1.0e-6, jerk);
        return Math.max(0.0, (-r + Math.sqrt(r * r + 8.0 * decel * distance)) * 0.5);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
