package com.createstats.chunkloader.look;

public final class CrownEase {
    private CrownEase() {
    }

    public static double clamp(double u) {
        return u < 0 ? 0.0 : u > 1 ? 1.0 : u;
    }

    public static double seg(double u, double a, double b) {
        if (b > a) {
            return clamp((u - a) / (b - a));
        }
        return u >= b ? 1.0 : 0.0;
    }

    public static double smooth(double u) {
        final double c = clamp(u);
        return c * c * (3 - 2 * c);
    }

    public static double outBack(double u, double s) {
        final double v = clamp(u) - 1.0;
        return 1.0 + (s + 1.0) * v * v * v + s * v * v;
    }

    public static double outCubic(double u) {
        final double c = 1.0 - clamp(u);
        return 1.0 - c * c * c;
    }

    public static double inCubic(double u) {
        final double c = clamp(u);
        return c * c * c;
    }

    public static double outBounce(double u) {
        double c = clamp(u);
        final double n1 = 7.5625;
        final double d1 = 2.75;
        if (c < 1 / d1) {
            return n1 * c * c;
        }
        if (c < 2 / d1) {
            c -= 1.5 / d1;
            return n1 * c * c + 0.75;
        }
        if (c < 2.5 / d1) {
            c -= 2.25 / d1;
            return n1 * c * c + 0.9375;
        }
        c -= 2.625 / d1;
        return n1 * c * c + 0.984375;
    }

    public static double flicker(long f) {
        return 0.75 + 0.25 * (0.5 + 0.5 * Math.sin(f * 2.1) * Math.cos(f * 0.77));
    }
}
