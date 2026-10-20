package com.createstats.timeswitch;

// aiming is pure, an off-by-one pin looks right in every screenshot
public final class TimeSwitchDial {
    private TimeSwitchDial() {
    }

    public static final double CU = 8.8;
    public static final double CV = 8.8;

    public static final double DIAL_R = 5.0;

    public static final double COLLAR_R0 = 5.0;
    public static final double COLLAR_R1 = 5.45;
    public static final double COLLAR_H1 = 2.25;

    public static final double FACE_H = 1.9;

    public static final double SLOT_R0 = 3.45;
    public static final double SLOT_R1 = 4.85;

    public static final double SLIDER_LEN = 0.72;
    public static final double SLIDER_W = 0.86;
    public static final double SLIDER_H = 0.42;

    public static final double OUT_R0 = SLOT_R1 - SLIDER_LEN;

    public static final double IN_R0 = SLOT_R0;

    public static final double PIN_TOP_H = FACE_H + SLIDER_H;

    public static final double HUB_R = 1.15;
    public static final double HUB_TOP_H = 2.75;

    public static final double SLIDE_U0 = 2.25;
    public static final double SLIDE_U1 = 3.05;
    public static final double SLIDE_V0 = 4.4;
    public static final double SLIDE_V1 = 12.6;
    public static final double SLIDE_FLOOR_H = 1.56;
    public static final double KNOB_U0 = 2.05;
    public static final double KNOB_U1 = 3.25;
    public static final double KNOB_HALF = 0.85;
    public static final double KNOB_H0 = 1.5;
    public static final double KNOB_H1 = 2.7;

    private static final double[] DETENT_V = {8.5, 11.2, 5.8};

    private static final double SLACK = 0.12;

    public static final double PIN_DEGREES = 360.0 / TimeSwitchProgram.HOURS;

    public static double dialDegrees(double hourF) {
        return -PIN_DEGREES * hourF;
    }

    public static double detentV(TimeSwitchProgram.Mode mode) {
        return DETENT_V[mode.id()];
    }

    public static double viewAngle(double u, double v) {
        final double a = Math.toDegrees(Math.atan2(CU - u, v - CV));
        return a < 0.0 ? a + 360.0 : a;
    }

    public static double pinViewAngle(int hour, double dialDegrees) {
        final double a = (Math.floorMod(hour, TimeSwitchProgram.HOURS) + 0.5) * PIN_DEGREES + dialDegrees;
        return a - Math.floor(a / 360.0) * 360.0;
    }

    public static int pinAt(double u, double v, double dialDegrees) {
        final double r = Math.hypot(u - CU, v - CV);
        if (r < SLOT_R0 - SLACK || r > SLOT_R1 + SLACK) {
            return -1;
        }
        double phi = viewAngle(u, v) - dialDegrees;
        phi -= Math.floor(phi / 360.0) * 360.0;
        return ((int) Math.floor(phi / PIN_DEGREES)) % TimeSwitchProgram.HOURS;
    }

    public static int sweep(int from, int to) {
        final int n = TimeSwitchProgram.HOURS;
        from = Math.floorMod(from, n);
        to = Math.floorMod(to, n);
        final int forward = Math.floorMod(to - from, n);
        final int step = forward <= n / 2 ? 1 : -1;
        final int count = step > 0 ? forward : n - forward;
        int mask = 0;
        for (int k = 0; k <= count; k++) {
            mask |= TimeSwitchProgram.bit(from + step * k);
        }
        return mask;
    }

    public static TimeSwitchProgram.Mode modeAt(double v) {
        final double onAuto = (DETENT_V[1] + DETENT_V[0]) / 2.0;
        final double autoOff = (DETENT_V[0] + DETENT_V[2]) / 2.0;
        if (v >= onAuto) {
            return TimeSwitchProgram.Mode.ON;
        }
        return v >= autoOff ? TimeSwitchProgram.Mode.AUTO : TimeSwitchProgram.Mode.OFF;
    }

    public enum Kind { NONE, PIN, MODE, HUB }

    public record Aim(Kind kind, int index, double t) {
        public static final Aim NONE = new Aim(Kind.NONE, -1, Double.MAX_VALUE);

        public boolean isPin() {
            return kind == Kind.PIN;
        }
    }

    public static Aim aim(double ou, double oh, double ov, double du, double dh, double dv,
                          double dialDegrees, TimeSwitchProgram.Mode currentMode) {
        if (dh >= 0.0) {
            return Aim.NONE;
        }
        Aim best = Aim.NONE;

        for (double plane : new double[] {HUB_TOP_H, FACE_H}) {
            final double t = (plane - oh) / dh;
            if (t > 0.0 && t < best.t()
                    && Math.hypot(ou + du * t - CU, ov + dv * t - CV) <= HUB_R + SLACK) {
                best = new Aim(Kind.HUB, -1, t);
            }
        }

        final double tp = (PIN_TOP_H - oh) / dh;
        if (tp > 0.0 && tp < best.t()) {
            final int pin = pinAt(ou + du * tp, ov + dv * tp, dialDegrees);
            if (pin >= 0) {
                best = new Aim(Kind.PIN, pin, tp);
            }
        }

        final double kv = detentV(currentMode);
        final double tk = boxHit(ou, oh, ov, du, dh, dv, KNOB_U0, KNOB_H0, kv - KNOB_HALF,
                KNOB_U1, KNOB_H1, kv + KNOB_HALF);
        if (tk > 0.0 && tk < best.t()) {
            best = new Aim(Kind.MODE, currentMode.id(), tk);
        }

        final double ts = (SLIDE_FLOOR_H - oh) / dh;
        if (ts > 0.0 && ts < best.t()) {
            final double u = ou + du * ts;
            final double v = ov + dv * ts;
            if (u >= KNOB_U0 - SLACK && u <= KNOB_U1 + SLACK
                    && v >= SLIDE_V0 - SLACK && v <= SLIDE_V1 + SLACK) {
                best = new Aim(Kind.MODE, modeAt(v).id(), ts);
            }
        }
        return best;
    }

    static double boxHit(double ou, double oh, double ov, double du, double dh, double dv,
                         double u0, double h0, double v0, double u1, double h1, double v1) {
        final double[] o = {ou, oh, ov};
        final double[] d = {du, dh, dv};
        final double[] lo = {u0, h0, v0};
        final double[] hi = {u1, h1, v1};
        double near = -Double.MAX_VALUE;
        double far = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1.0e-12) {
                if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
                    return -1.0;
                }
                continue;
            }
            double a = (lo[axis] - o[axis]) / d[axis];
            double b = (hi[axis] - o[axis]) / d[axis];
            if (a > b) {
                final double swap = a;
                a = b;
                b = swap;
            }
            near = Math.max(near, a);
            far = Math.min(far, b);
            if (near > far) {
                return -1.0;
            }
        }
        return far < 0.0 ? -1.0 : Math.max(0.0, near);
    }
}
