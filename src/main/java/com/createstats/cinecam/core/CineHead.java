package com.createstats.cinecam.core;

public final class CineHead {
    public static final double AZ_RATIO = 4.35;
    public static final double EL_RATIO = 3.0;

    public static final double AZ_RATE = 120.0;
    public static final double EL_RATE = 80.0;

    public static final double PITCH_MIN = -30.0;
    public static final double PITCH_MAX = 70.0;

    public static final double TAU_TRACK = 0.12;

    public static final double TAU_HAND = 0.08;

    private CineHead() {
    }

    public static double wrap180(double d) {
        d = d % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }

    public static double clampPitch(double p) {
        return Math.max(PITCH_MIN, Math.min(PITCH_MAX, p));
    }

    public static double follow(double cur, double want, double dt, double tau, double rate, boolean wrap) {
        double diff = wrap ? wrap180(want - cur) : want - cur;
        double k = tau <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt / tau);
        double step = diff * k;
        double cap = rate * dt;
        if (step > cap) step = cap;
        if (step < -cap) step = -cap;
        return cur + step;
    }

    public static double azimuthWheel(double yaw) {
        return -yaw * AZ_RATIO;
    }

    public static double elevationWheel(double pitch) {
        return -pitch * EL_RATIO;
    }

    public static double bearing(double dx, double dz) {
        double b = Math.toDegrees(Math.atan2(dx, -dz));
        return b < 0.0 ? b + 360.0 : b;
    }

    public static double elevation(double dx, double dy, double dz) {
        return Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    public static double yawFor(double bearing, double facingDeg) {
        return wrap180(facingDeg - bearing);
    }

    public static double[] sight(double facingDeg, double yaw, double pitch) {
        double p = Math.toRadians(pitch);
        double a = Math.toRadians(yaw - facingDeg);

        return new double[]{-Math.sin(a) * Math.cos(p), Math.sin(p), -Math.cos(a) * Math.cos(p)};
    }
}
