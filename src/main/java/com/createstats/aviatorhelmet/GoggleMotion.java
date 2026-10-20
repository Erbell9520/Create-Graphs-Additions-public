package com.createstats.aviatorhelmet;

// goggles pivot at the strap loop on the back of the head, not the temples
public final class GoggleMotion {
    public static final double PIVOT_Y = 9.2;
    public static final double PIVOT_Z = 13.25;

    public static final double UP_ANGLE = 24.0;

    public static final double STRETCH = 0.85;

    public static final double REST_PUSH = 0.04;

    static final double[][] PATH = {
        {0.0, 0.0, 0.0},
        {0.2, 0.6, STRETCH},
        {0.8, UP_ANGLE - 2.5, STRETCH},
        {1.0, UP_ANGLE, REST_PUSH},
    };

    public static final double STROKE_SECONDS = 0.42;

    public static final double LAND_SECONDS = STROKE_SECONDS - 0.03;

    public static final int STROKE_LOCK_TICKS = 9;

    private GoggleMotion() {
    }

    private static double ease(double s) {
        return s * s * (3.0 - 2.0 * s);
    }

    public static double[] path(double u) {
        final double c = Math.max(0.0, Math.min(1.0, u));
        for (int i = 1; i < PATH.length; i++) {
            final double[] a = PATH[i - 1];
            final double[] b = PATH[i];
            if (c <= b[0]) {
                final double s = ease((c - a[0]) / (b[0] - a[0]));
                return new double[] {a[1] + (b[1] - a[1]) * s, a[2] + (b[2] - a[2]) * s};
            }
        }
        final double[] last = PATH[PATH.length - 1];
        return new double[] {last[1], last[2]};
    }

    public static double u(boolean down, double seconds) {
        final double x = Math.max(0.0, Math.min(1.0, seconds / STROKE_SECONDS));
        final double s = x * x * x * (x * (6.0 * x - 15.0) + 10.0);
        return down ? 1.0 - s : s;
    }

    public static double squash(double seconds) {
        if (seconds < LAND_SECONDS) {
            return 0.0;
        }
        final double t = seconds - LAND_SECONDS;
        return -0.06 * Math.sin(2.0 * Math.PI * 6.0 * t) * Math.exp(-t / 0.07);
    }

    public static double[] pose(boolean down, double seconds) {
        final double[] p = path(u(down, seconds));
        p[1] += squash(seconds);
        return p;
    }

    public static double[] rest(boolean down) {
        return path(down ? 0.0 : 1.0);
    }

    public static double lensCover(boolean down, double seconds) {
        return 1.0 - u(down, seconds);
    }
}
