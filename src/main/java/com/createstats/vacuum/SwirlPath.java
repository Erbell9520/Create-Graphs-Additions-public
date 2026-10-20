package com.createstats.vacuum;

public final class SwirlPath {
    private SwirlPath() {
    }

    public static double eased(double t) {
        final double c = Math.max(0.0, Math.min(1.0, t));
        return Math.pow(c, 1.6);
    }

    public static double[] at(double[] from, double[] to, double radius, double turns, double phase, double t) {
        final double e = eased(t);
        final double[] d = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        final double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        final double[] base = {from[0] + d[0] * e, from[1] + d[1] * e, from[2] + d[2] * e};
        if (len < 1.0e-6) {
            return base;
        }
        final double[] axis = {d[0] / len, d[1] / len, d[2] / len};
        final double[] u = perpendicular(axis);
        final double[] v = cross(axis, u);
        final double r = radius * 4.0 * e * (1.0 - e);
        final double angle = phase + 2.0 * Math.PI * turns * e;
        final double c = Math.cos(angle) * r;
        final double s = Math.sin(angle) * r;
        return new double[] {
                base[0] + u[0] * c + v[0] * s,
                base[1] + u[1] * c + v[1] * s,
                base[2] + u[2] * c + v[2] * s};
    }

    public static float tumble(double t, double seed) {
        return (float) (seed * 360.0 + 720.0 * eased(t));
    }

    public static float scale(double t) {
        final double e = eased(t);
        return (float) (e < 0.7 ? 1.0 : 1.0 - (e - 0.7) / 0.3 * 0.6);
    }

    private static double[] perpendicular(double[] a) {
        final double[] seed = Math.abs(a[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        final double[] p = cross(seed, a);
        final double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new double[] {p[0] / l, p[1] / l, p[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
