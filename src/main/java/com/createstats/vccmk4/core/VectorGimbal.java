package com.createstats.vccmk4.core;

// propulsion's own nozzle maths, from the decompile
public final class VectorGimbal {
    public static final double MAX_TILT_DEG = 30.0;

    public static final int STEPS = 15;
    private static final double TILT = Math.tan(Math.toRadians(MAX_TILT_DEG));

    private VectorGimbal() {
    }

    public static double[] right(double[] f) {
        boolean vertical = Math.abs(f[1]) > 0.999;
        double[] reference = vertical ? new double[]{0, 0, 1} : new double[]{0, 1, 0};
        double[] r = cross(f, reference);
        double len = length(r);
        if (len < 1.0e-4) return new double[]{1, 0, 0};
        r[0] /= len;
        r[1] /= len;
        r[2] /= len;
        if (vertical) {
            r[0] = -r[0];
            r[1] = -r[1];
            r[2] = -r[2];
        }
        return r;
    }

    public static double[] up(double[] f) {
        double[] u = cross(right(f), f);
        double len = length(u);
        if (len < 1.0e-9) return new double[]{0, 1, 0};
        return new double[]{u[0] / len, u[1] / len, u[2] / len};
    }

    public static double[] direction(double[] f, double x, double y) {
        double[] r = right(f);
        double[] u = up(f);
        double[] d = new double[3];
        for (int i = 0; i < 3; i++) d[i] = f[i] - x * TILT * r[i] - y * TILT * u[i];
        double len = length(d);
        if (len < 1.0e-9) return new double[]{f[0], f[1], f[2]};
        return new double[]{d[0] / len, d[1] / len, d[2] / len};
    }

    public static float snap(double coordinate) {
        if (Double.isNaN(coordinate)) return 0f;
        double c = Math.max(-1.0, Math.min(1.0, coordinate));
        return (float) (Math.round(c * STEPS) / (double) STEPS);
    }

    public static float steer(double turn, double pushing, double armOfTilt) {
        if (!(pushing > 0.0) || !Double.isFinite(turn) || turn == 0.0 || !Double.isFinite(armOfTilt)
                || Math.abs(armOfTilt) < JetMixer.DIFFERENTIAL_ARM) {
            return 0f;
        }
        return snap(Math.max(-1.0, Math.min(1.0, turn)) * Math.signum(armOfTilt));
    }

    public static double sideways(double x) {
        double angle = Math.atan(Math.max(-1.0, Math.min(1.0, x)) * TILT);
        return Math.sin(angle);
    }

    public static double forward(double x) {
        double angle = Math.atan(Math.max(-1.0, Math.min(1.0, x)) * TILT);
        return Math.cos(angle);
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
