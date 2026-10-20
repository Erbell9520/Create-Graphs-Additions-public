package com.createstats.surveyor.core;

public final class SpeedPlan {
    public static final double CREEP = 1.25;

    public static final double APPROACH = 3.0;

    public static final double BEND_WINDOW = 1.5;

    public static final double STEEP = 0.25;

    public static final double VERY_STEEP = 0.45;

    public static final double BUMP_SPEED = 3.0;

    public static final double LEAN_SHARE = 0.6;

    public static final SpeedPlan EMPTY = new SpeedPlan(PathSpline.EMPTY, new double[0], new byte[0], new int[0]);

    public enum Cause {
        CRUISE, LIMIT, BEND, GRADE, STOP, END, SURFACE, BUMP, LEAN;

        static final Cause[] ALL = values();
    }

    public record Terrain(double[] grip, double[] grade, double[] cross, double[] bump) {
        boolean fits(int n) {
            return grip != null && grip.length == n && grade != null && grade.length == n && cross != null && cross.length == n
                    && bump != null && bump.length == n;
        }

        double gripAt(int i) {
            double g = grip[i];
            return Double.isFinite(g) ? Math.max(0.05, Math.min(1.0, g)) : 1.0;
        }
    }

    private final PathSpline sp;
    private final double[] v;
    private final byte[] cause;
    private final int[] source;

    private SpeedPlan(PathSpline sp, double[] v, byte[] cause, int[] source) {
        this.sp = sp;
        this.v = v;
        this.cause = cause;
        this.source = source;
    }

    public static SpeedPlan of(PathSpline sp, Pathway p) {
        return build(sp, p, p.cruise(), p.comfort());
    }

    public static SpeedPlan build(PathSpline sp, Pathway p, double cruise, Comfort comfort) {
        return build(sp, p, cruise, comfort, null);
    }

    public static SpeedPlan build(PathSpline sp, Pathway p, double cruise, Comfort comfort, Terrain terrain) {
        int n = sp.sampleCount();
        if (n == 0) return EMPTY;
        Terrain t = terrain != null && terrain.fits(n) ? terrain : null;
        double top = Math.max(CREEP, Math.min(PathNode.MAX_SPEED, cruise));
        double[] v = new double[n];
        java.util.Arrays.fill(v, top);
        byte[] cause = new byte[n];
        int[] source = new int[n];
        for (int i = 0; i < n; i++) source[i] = i;
        double step = sp.step();

        int m = sp.nodeCount();
        for (int k = 0; k < m && k < p.nodes().size(); k++) {
            double lim = p.nodes().get(k).speedLimit();
            if (!(lim > 0.0)) continue;
            double from = sp.nodeS(k);
            double to = k + 1 < m ? sp.nodeS(k + 1) : (sp.closed() ? sp.length() : sp.length());
            if (k + 1 >= m && !sp.closed()) continue;
            limitRange(sp, v, cause, source, from, to, Math.max(CREEP, lim), Cause.LIMIT);
        }

        int reach = (int) Math.ceil(BEND_WINDOW / step);
        double[] worst = worstCurvature(sp, reach);
        for (int i = 0; i < n; i++) {
            double k = worst[i];
            double grip = t == null ? 1.0 : t.gripAt(i);
            if (k > 1.0e-6) {
                lower(v, cause, source, i, Math.max(CREEP, Math.sqrt(comfort.lateral * grip / k)), grip < 0.99 ? Cause.SURFACE : Cause.BEND, i);
            }
            double surveyed = t == null ? Double.NaN : t.grade()[i];
            double g = Math.abs(Double.isFinite(surveyed) ? surveyed : sp.grade(sp.sampleS(i)));
            if (g > VERY_STEEP) lower(v, cause, source, i, Math.max(CREEP, top * 0.35), Cause.GRADE, i);
            else if (g > STEEP) lower(v, cause, source, i, Math.max(CREEP, top * 0.7), Cause.GRADE, i);
            if (t != null) {
                double bump = t.bump()[i];
                if (Double.isFinite(bump) && Math.abs(bump) > PathSurvey.BUMP) lower(v, cause, source, i, Math.max(CREEP, BUMP_SPEED), Cause.BUMP, i);
                double cross = t.cross()[i];
                if (Double.isFinite(cross) && Math.abs(cross) > PathSurvey.CROSS_RED) lower(v, cause, source, i, CREEP, Cause.LEAN, i);
                else if (Double.isFinite(cross) && Math.abs(cross) > PathSurvey.CROSS_AMBER) {
                    lower(v, cause, source, i, Math.max(CREEP, top * LEAN_SHARE), Cause.LEAN, i);
                }
            }
        }

        for (int k = 0; k < m && k < p.nodes().size(); k++) {
            if (!p.nodes().get(k).isStop()) continue;
            double at = sp.nodeS(k);
            limitRange(sp, v, cause, source, at - APPROACH, at + step * 0.5, CREEP, Cause.STOP);
        }
        if (!sp.closed()) limitRange(sp, v, cause, source, sp.length() - APPROACH, sp.length() + 1.0, CREEP, Cause.END);

        passes(sp, v, cause, source, comfort, t);
        return new SpeedPlan(sp, v, cause, source);
    }

    private static void lower(double[] v, byte[] cause, int[] source, int i, double cap, Cause why, int from) {
        if (cap < v[i]) {
            v[i] = cap;
            cause[i] = (byte) why.ordinal();
            source[i] = from;
        }
    }

    private static void limitRange(PathSpline sp, double[] v, byte[] cause, int[] source, double from, double to,
                                   double cap, Cause why) {
        int n = v.length;
        double step = sp.step();
        int a = (int) Math.floor(from / step);
        int b = (int) Math.ceil(to / step);
        int end = sp.closed() ? Math.floorMod(b - 1, n) : Math.max(0, Math.min(n - 1, b - 1));
        for (int k = a; k < b; k++) {
            int i = sp.closed() ? Math.floorMod(k, n) : k;
            if (i < 0 || i >= n) continue;
            lower(v, cause, source, i, cap, why, end);
        }
    }

    static double[] worstCurvature(PathSpline sp, int reach) {
        int n = sp.sampleCount();
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double worst = 0.0;
            for (int d = -reach; d <= reach; d++) {
                int j = i + d;
                if (sp.closed()) j = Math.floorMod(j, n);
                else if (j < 0 || j >= n) continue;
                worst = Math.max(worst, Math.abs(sp.curvature(j)));
            }
            out[i] = worst;
        }
        return out;
    }

    private static void passes(PathSpline sp, double[] v, byte[] cause, int[] source, Comfort comfort, Terrain t) {
        int n = v.length;
        double step = sp.step();
        double dec = 2.0 * comfort.decel * step;
        double acc = 2.0 * comfort.accel * step;
        if (sp.closed()) {
            for (int r = 0; r < 2; r++) {
                for (int k = n - 1; k >= 0; k--) {
                    int next = (k + 1) % n;
                    ramp(v, cause, source, k, next, dec * grip(t, k, next));
                }
            }
            for (int r = 0; r < 2; r++) {
                for (int k = 0; k < n; k++) {
                    int prev = Math.floorMod(k - 1, n);
                    ramp(v, cause, source, k, prev, acc * grip(t, k, prev));
                }
            }
            return;
        }
        for (int i = n - 2; i >= 0; i--) ramp(v, cause, source, i, i + 1, dec * grip(t, i, i + 1));
        for (int i = 1; i < n; i++) ramp(v, cause, source, i, i - 1, acc * grip(t, i, i - 1));
    }

    private static double grip(Terrain t, int i, int j) {
        return t == null ? 1.0 : Math.min(t.gripAt(i), t.gripAt(j));
    }

    private static void ramp(double[] v, byte[] cause, int[] source, int i, int j, double twiceRateStep) {
        double cap = Math.sqrt(v[j] * v[j] + twiceRateStep);
        if (cap < v[i]) {
            v[i] = cap;
            cause[i] = cause[j];
            source[i] = source[j];
        }
    }

    public double at(double s) {
        int n = v.length;
        if (n == 0) return 0.0;
        if (n == 1) return v[0];
        double w = sp.wrap(s);
        double f = w / sp.step();
        int i = (int) Math.floor(f);
        double t = f - i;
        if (sp.closed()) {
            i = Math.floorMod(i, n);
            return v[i] + (v[(i + 1) % n] - v[i]) * t;
        }
        if (i >= n - 1) return v[n - 1];
        return v[i] + (v[i + 1] - v[i]) * t;
    }

    public double sample(int i) {
        return v[i];
    }

    public Cause cause(int i) {
        return i >= 0 && i < cause.length ? Cause.ALL[cause[i]] : Cause.CRUISE;
    }

    public int source(int i) {
        return i >= 0 && i < source.length ? source[i] : i;
    }

    public int indexAt(double s) {
        int n = v.length;
        if (n == 0) return -1;
        int i = (int) Math.floor(sp.wrap(s) / sp.step());
        return sp.closed() ? Math.floorMod(i, n) : Math.max(0, Math.min(n - 1, i));
    }

    public int size() {
        return v.length;
    }

    public double seconds(double from, double to) {
        int n = v.length;
        if (n < 2) return 0.0;
        double d = sp.ahead(from, to);
        if (d <= 0.0) return 0.0;
        double step = sp.step();
        double t = 0.0;
        double s = from;
        double left = d;
        while (left > 1.0e-9) {
            double h = Math.min(step, left);
            t += h / Math.max(CREEP * 0.5, at(s + h * 0.5));
            s += h;
            left -= h;
        }
        return t;
    }
}
