package com.createstats.battle.core;

// closed form of cbc's integrator, read from the decompile, not a guess
public final class Trajectory {
    public static final int MAX_STEPS = 400;

    private static final double NORMALIZE_EPSILON = 1.0E-5;

    private final double g;
    private final double k;
    private final boolean quadratic;

    public Trajectory(double gravity, double drag, boolean quadratic) {
        this.g = gravity;
        this.k = Math.max(0.0, drag);
        this.quadratic = quadratic;
    }

    public double gravity() {
        return g;
    }

    public double drag() {
        return k;
    }

    public boolean quadratic() {
        return quadratic;
    }

    public boolean closedForm() {
        return !quadratic && k < 1.0;
    }

    public double[] position(int n, double[] x0, double[] v0, double[] out) {
        if (n <= 0) {
            copy(x0, out);
            return out;
        }
        if (!closedForm()) {
            return stepped(n, x0, v0, out, null);
        }
        if (k == 0.0) {
            double nn = n;
            out[0] = x0[0] + nn * v0[0];
            out[1] = x0[1] + nn * v0[1] + 0.5 * g * nn * nn;
            out[2] = x0[2] + nn * v0[2];
            return out;
        }
        double s = sumFactor(n);
        double c = 1.0 - 0.5 * k;
        double vty = g / k;
        out[0] = x0[0] + c * (v0[0] * s);
        out[1] = x0[1] + c * (n * vty + (v0[1] - vty) * s) + n * 0.5 * g;
        out[2] = x0[2] + c * (v0[2] * s);
        return out;
    }

    public double[] velocity(int n, double[] v0, double[] out) {
        if (n <= 0) {
            copy(v0, out);
            return out;
        }
        if (!closedForm()) {
            double[] scratch = new double[3];
            stepped(n, new double[3], v0, scratch, out);
            return out;
        }
        if (k == 0.0) {
            out[0] = v0[0];
            out[1] = v0[1] + n * g;
            out[2] = v0[2];
            return out;
        }
        double qn = Math.exp(n * Math.log1p(-k));
        double vty = g / k;
        out[0] = qn * v0[0];
        out[1] = vty + qn * (v0[1] - vty);
        out[2] = qn * v0[2];
        return out;
    }

    public double reach(double n, double v0h) {
        if (n <= 0.0) return 0.0;
        if (k == 0.0) return v0h * n;
        return (1.0 - 0.5 * k) * v0h * (-Math.expm1(n * Math.log1p(-k))) / k;
    }

    public double reachBound(double v0h) {
        if (k == 0.0) return Double.POSITIVE_INFINITY;
        return (1.0 - 0.5 * k) * v0h / k;
    }

    public double ticksToReach(double d, double v0h) {
        if (d <= 0.0) return 0.0;
        if (v0h <= 0.0) return Double.NaN;
        if (k == 0.0) return d / v0h;
        double f = d * k / ((1.0 - 0.5 * k) * v0h);
        if (f >= 1.0) return Double.NaN;
        return Math.log1p(-f) / Math.log1p(-k);
    }

    public static void step(double[] x, double[] v, double g, double k, boolean quadratic) {
        double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double ax = 0.0;
        double ay = g;
        double az = 0.0;
        if (speed >= NORMALIZE_EPSILON) {
            double drag = k * speed;
            if (quadratic) drag *= speed;
            drag = Math.min(drag, speed);
            double f = -drag / speed;
            ax += f * v[0];
            ay += f * v[1];
            az += f * v[2];
        }
        x[0] += v[0] + 0.5 * ax;
        x[1] += v[1] + 0.5 * ay;
        x[2] += v[2] + 0.5 * az;
        v[0] += ax;
        v[1] += ay;
        v[2] += az;
    }

    private double[] stepped(int n, double[] x0, double[] v0, double[] outX, double[] outV) {
        double[] x = x0.clone();
        double[] v = v0.clone();
        int steps = Math.min(n, MAX_STEPS);
        for (int i = 0; i < steps; i++) {
            step(x, v, g, k, quadratic);
        }
        copy(x, outX);
        if (outV != null) copy(v, outV);
        return outX;
    }

    private double sumFactor(int n) {
        return -Math.expm1(n * Math.log1p(-k)) / k;
    }

    private static void copy(double[] from, double[] to) {
        to[0] = from[0];
        to[1] = from[1];
        to[2] = from[2];
    }
}
