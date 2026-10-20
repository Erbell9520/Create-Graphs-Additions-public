package com.createstats.vccmk4.air.core;

// learn the lift nobody commanded
public final class VerticalObserver {
    public static final int DRAG = 0;
    public static final int WING = 1;
    public static final int THRUST = 2;
    public static final int CONSTANT = 3;
    public static final int TERMS = 4;

    static final double FORGET = 1.0 - 1.0 / 200.0;

    static final double NOISE_SHARE = 0.02;

    static final double GATE = 6.0;

    public static final double DRAG_PRIOR = 0.45;

    static final double DRAG_FLOOR = 0.25;

    static final double DRAG_CEILING = 4.0;

    static final double WING_SHARE = 0.1;

    static final double LEAN_MAX = 1.0;

    static final double CONSTANT_SHARE = 0.5;

    static final int REOPEN = 10;

    public static final double AIRTIGHT_DRAG = 0.33;

    public static final double UNIVERSAL_DRAG = 0.09;

    private final double[] theta = new double[TERMS];
    private final double[][] p = new double[TERMS][TERMS];
    private final double[] cap = new double[TERMS];
    private final double[] lo = new double[TERMS];
    private final double[] hi = new double[TERMS];
    private double noise = 1.0;
    private double weight = 11.0;
    private int samples;
    private int skipped;

    private int outside;
    private int outsideSign;
    private int reopened;

    public VerticalObserver() {
        reset(1.0, 11.0);
    }

    public static double physicsDrag(int airtightBlocks, double mass) {
        double m = mass > 0.0 && Double.isFinite(mass) ? mass : 0.0;
        return AIRTIGHT_DRAG * Math.max(0, airtightBlocks) + UNIVERSAL_DRAG * m;
    }

    public void reset(double mass, double g) {
        reset(mass, g, Double.NaN);
    }

    public void reset(double mass, double g, double dragPrior) {
        double m = mass > 0.0 && Double.isFinite(mass) ? mass : 1.0;
        double w = m * (g > 0.0 && Double.isFinite(g) ? g : 11.0);
        java.util.Arrays.fill(theta, 0.0);
        double drag = dragPrior > 0.0 && Double.isFinite(dragPrior) ? dragPrior : DRAG_PRIOR * m;
        theta[DRAG] = drag;
        cap[DRAG] = sq(0.5 * Math.max(drag, DRAG_PRIOR * m));
        cap[WING] = sq(0.02 * w);
        cap[THRUST] = sq(0.2);
        cap[CONSTANT] = sq(0.25 * w);
        for (int i = 0; i < TERMS; i++) {
            java.util.Arrays.fill(p[i], 0.0);
            p[i][i] = cap[i];
        }
        weight = w;
        bound(drag, w);
        noise = sq(NOISE_SHARE * w);
        samples = 0;
        skipped = 0;
        outside = 0;
        outsideSign = 0;
        reopened = 0;
    }

    public void setLoadRoom(double force) {
        double room = Math.max(CONSTANT_SHARE * weight, Double.isFinite(force) ? force : 0.0);
        lo[CONSTANT] = Math.min(lo[CONSTANT], -room);
    }

    public boolean atMost(double force) {
        if (!Double.isFinite(force)) return false;
        double was = theta[CONSTANT];
        theta[CONSTANT] = Math.max(lo[CONSTANT], Math.min(was, force));
        return theta[CONSTANT] < was;
    }

    public void dropLoad() {
        if (theta[CONSTANT] < 0.0) theta[CONSTANT] = 0.0;
    }

    public double load() {
        return Math.max(0.0, -theta[CONSTANT]);
    }

    public int reopened() {
        return reopened;
    }

    public void setDragPrior(double dragPrior) {
        if (!(dragPrior > 0.0) || !Double.isFinite(dragPrior)) return;
        lo[DRAG] = DRAG_FLOOR * dragPrior;
        hi[DRAG] = DRAG_CEILING * dragPrior;
        clampAll();
    }

    private void bound(double drag, double w) {
        lo[DRAG] = DRAG_FLOOR * drag;
        hi[DRAG] = DRAG_CEILING * drag;
        lo[WING] = -WING_SHARE * w;
        hi[WING] = WING_SHARE * w;
        lo[THRUST] = -LEAN_MAX;
        hi[THRUST] = LEAN_MAX;
        lo[CONSTANT] = -CONSTANT_SHARE * w;
        hi[CONSTANT] = CONSTANT_SHARE * w;
    }

    private void clampAll() {
        for (int i = 0; i < TERMS; i++) theta[i] = Math.max(lo[i], Math.min(hi[i], theta[i]));
    }

    double[] bounds(int term) {
        return new double[] {lo[term], hi[term]};
    }

    public static double[] regressors(double vy, double airspeed, double forwardForce, double pressure) {
        double p = Double.isFinite(pressure) && pressure > 0.0 ? pressure : 1.0;
        return new double[] {
                -finite(vy) * p,
                Math.abs(finite(airspeed)) * p,
                finite(forwardForce),
                1.0
        };
    }

    public double predict(double[] phi) {
        double s = 0.0;
        for (int i = 0; i < TERMS; i++) s += theta[i] * phi[i];
        return s;
    }

    public double part(int term, double[] phi) {
        return theta[term] * phi[term];
    }

    public boolean observe(double residual, double[] phi) {
        if (!Double.isFinite(residual)) return false;
        double[] pp = new double[TERMS];
        double s = noise;
        for (int i = 0; i < TERMS; i++) {
            double r = 0.0;
            for (int j = 0; j < TERMS; j++) r += p[i][j] * phi[j];
            pp[i] = r;
            s += phi[i] * r;
        }
        double e = residual - predict(phi);
        if (e * e > GATE * GATE * s) {
            int sign = e > 0.0 ? 1 : -1;
            outside = sign == outsideSign ? outside + 1 : 1;
            outsideSign = sign;
            if (outside < REOPEN) {
                skipped++;
                return false;
            }
            reopen();
            s = noise;
            for (int i = 0; i < TERMS; i++) {
                double r = 0.0;
                for (int j = 0; j < TERMS; j++) r += p[i][j] * phi[j];
                pp[i] = r;
                s += phi[i] * r;
            }
        }
        outside = 0;
        double[] k = new double[TERMS];
        for (int i = 0; i < TERMS; i++) k[i] = pp[i] / s;
        for (int i = 0; i < TERMS; i++) theta[i] += k[i] * e;
        clampAll();
        for (int i = 0; i < TERMS; i++) {
            for (int j = 0; j < TERMS; j++) p[i][j] = (p[i][j] - k[i] * pp[j]) / FORGET;
        }

        for (int i = 0; i < TERMS; i++) {
            for (int j = i + 1; j < TERMS; j++) {
                double m = 0.5 * (p[i][j] + p[j][i]);
                p[i][j] = m;
                p[j][i] = m;
            }
        }
        for (int i = 0; i < TERMS; i++) {
            if (p[i][i] > cap[i]) {
                double f = Math.sqrt(cap[i] / p[i][i]);
                for (int j = 0; j < TERMS; j++) {
                    p[i][j] *= f;
                    p[j][i] *= f;
                }
            }
        }
        samples++;
        return true;
    }

    private void reopen() {
        for (int j = 0; j < TERMS; j++) {
            p[CONSTANT][j] = 0.0;
            p[j][CONSTANT] = 0.0;
        }
        p[CONSTANT][CONSTANT] = cap[CONSTANT];
        outside = 0;
        reopened++;
    }

    public double estimate(int term) {
        return theta[term];
    }

    public double dragPerMass(double mass) {
        return mass > 0.0 ? theta[DRAG] / mass : Double.NaN;
    }

    double variance(int term) {
        return p[term][term];
    }

    double prior(int term) {
        return cap[term];
    }

    public int samples() {
        return samples;
    }

    public int skipped() {
        return skipped;
    }

    public double[] state() {
        return theta.clone();
    }

    public void restore(double[] state) {
        if (state == null || state.length != TERMS) return;
        for (int i = 0; i < TERMS; i++) if (Double.isFinite(state[i])) theta[i] = state[i];
        clampAll();
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
