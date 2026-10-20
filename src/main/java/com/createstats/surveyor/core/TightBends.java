package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.List;

public final class TightBends {
    public static final double CUT = 1.0;

    static final double LOCK_TRAVEL = 0.75;

    static final double CRAWL = 2.0;

    static final double STEP = 0.1;

    static final double ON_LINE = 0.15;

    public record Bend(double from, double to, double radius, double turn, double cut, int node) {
        public double middle(PathSpline sp) {
            return sp.closed() ? sp.wrap(from + sp.ahead(from, to) / 2.0) : (from + to) / 2.0;
        }
    }

    public record Result(boolean[] impossible, double[] radius, List<Bend> bends, double[] deviation) {
    }

    private TightBends() {
    }

    public static Result of(Pathway p, PathSpline sp, double own) {
        int n = sp.sampleCount();
        boolean[] impossible = new boolean[n];
        double[] radius = new double[n];
        List<Bend> bends = new ArrayList<>();
        if (n < 3 || !(own > 0.0) || sp.length() < 2.0) return new Result(impossible, radius, bends, new double[n]);
        double[] dev = deviation(p, sp, own);
        boolean[] bad = new boolean[n];
        for (int i = 0; i < n; i++) bad[i] = dev[i] > CUT;

        int start = 0;
        if (sp.closed()) {
            start = -1;
            for (int i = 0; i < n; i++) {
                if (dev[i] < ON_LINE) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                java.util.Arrays.fill(impossible, true);
                double tight = Double.POSITIVE_INFINITY;
                for (int i = 0; i < n; i++) tight = Math.min(tight, radiusAt(sp, sp.sampleS(i)));
                java.util.Arrays.fill(radius, tight);
                bends.add(new Bend(0.0, sp.length(), tight, 2.0 * Math.PI, max(dev), p.nodes().isEmpty() ? -1 : p.nodes().get(0).id()));
                return new Result(impossible, radius, bends, dev);
            }
        }
        int i = 0;
        while (i < n) {
            int at = sp.closed() ? (start + i) % n : i;
            if (!bad[at]) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && bad[sp.closed() ? (start + j) % n : j]) j++;

            int from = i;
            while (from > 0 && dev[sp.closed() ? (start + from - 1) % n : from - 1] >= ON_LINE && i - from < 160) from--;
            int to = j;
            while (to < n && dev[sp.closed() ? (start + to) % n : to] >= ON_LINE && to - j < 160) to++;
            List<Integer> run = new ArrayList<>();
            for (int q = from; q < to; q++) run.add(sp.closed() ? (start + q) % n : q);
            double tight = Double.POSITIVE_INFINITY;
            double turn = 0.0;
            double worst = 0.0;
            for (int q : run) {
                double sq = sp.sampleS(q);
                tight = Math.min(tight, radiusAt(sp, sq));
                turn += sp.curvatureAt(sq) * sp.step();
                worst = Math.max(worst, dev[q]);
            }
            for (int q : run) {
                impossible[q] = true;
                radius[q] = tight;
            }
            double sFrom = sp.sampleS(run.get(0));
            double sTo = sp.sampleS(run.get(run.size() - 1));
            double mid = sp.closed() ? sp.wrap(sFrom + sp.ahead(sFrom, sTo) / 2.0) : (sFrom + sTo) / 2.0;
            int node = sp.nearestNode(mid);
            bends.add(new Bend(sFrom, sTo, tight, Math.abs(turn), worst,
                    node >= 0 && node < p.nodes().size() ? p.nodes().get(node).id() : -1));
            i = Math.max(to, j);
        }
        return new Result(impossible, radius, bends, dev);
    }

    public static double[] deviation(Pathway p, PathSpline sp, double own) {
        int n = sp.sampleCount();
        double[] dev = new double[n];
        double kmax = 1.0 / own;
        double slew = kmax / LOCK_TRAVEL * STEP;
        double reach = Math.max(LateralControl.L_MIN, LateralControl.T_REACH * CRAWL);
        List<Double> sharp = new ArrayList<>();
        for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
            if (p.nodes().get(k).corner() == Corner.SHARP) sharp.add(sp.nodeS(k));
        }

        double s = 0.0;
        if (sp.closed()) {
            double calm = Double.POSITIVE_INFINITY;
            for (double q = 0.0; q < sp.length(); q += 1.0) {
                double k = Math.abs(averaged(sp, q, 2.0));
                if (k < calm) {
                    calm = k;
                    s = q;
                }
            }
        }
        double x = sp.x(s);
        double z = sp.z(s);
        double[] t0 = sp.tangent(s);
        double psi = PathSpline.heading(t0[0], t0[1]);
        double k = clamp(averaged(sp, s, 1.0), kmax);
        double total = sp.closed() ? 2.0 * sp.length() : sp.length() + 2.0;
        double keepFrom = sp.closed() ? sp.length() : 0.0;
        double travelled = 0.0;
        while (travelled < total) {
            double before = s;
            s = project(sp, x, z, s);
            if (!sp.closed() && s >= sp.length() - 1.0e-6) break;

            for (double q : sharp) {
                boolean passed = sp.closed() ? sp.ahead(before, q) <= sp.ahead(before, s) && sp.ahead(before, s) < 2.0
                        : before < q && s >= q;
                if (passed) {
                    s = sp.wrap(q + 0.05);
                    x = sp.x(s);
                    z = sp.z(s);
                    double[] tq = sp.tangent(s);
                    psi = PathSpline.heading(tq[0], tq[1]);
                    k = 0.0;
                }
            }
            double lx = sp.x(s);
            double lz = sp.z(s);
            double[] t = sp.tangent(s);

            double ey = (x - lx) * -t[1] + (z - lz) * t[0];
            double epsi = PathSpline.wrapAngle(psi - PathSpline.heading(t[0], t[1]));
            double line = averaged(sp, s + CRAWL * LateralControl.LAG, 1.0);
            double want = clamp(line - ey / (reach * reach) - 2.0 * LateralControl.ZETA * epsi / reach, kmax);
            k += clamp(want - k, slew);
            psi += k * STEP;
            x += Math.sin(psi) * STEP;
            z -= Math.cos(psi) * STEP;
            travelled += STEP;
            if (travelled >= keepFrom) {
                int i = index(sp, s);
                dev[i] = Math.max(dev[i], Math.abs(ey));
            }
        }
        return dev;
    }

    private static double project(PathSpline sp, double x, double z, double near) {
        double best = near;
        double bestD = Double.POSITIVE_INFINITY;
        for (double d = -1.0; d <= 2.0; d += 0.05) {
            double q = near + d;
            if (!sp.closed() && (q < 0.0 || q > sp.length())) continue;
            double dx = sp.x(q) - x;
            double dz = sp.z(q) - z;
            double dd = dx * dx + dz * dz;
            if (dd < bestD) {
                bestD = dd;
                best = q;
            }
        }
        return sp.closed() ? sp.wrap(best) : best;
    }

    private static int index(PathSpline sp, double s) {
        int n = sp.sampleCount();
        int i = (int) Math.floor(sp.wrap(s) / sp.step() + 0.5);
        return sp.closed() ? Math.floorMod(i, n) : Math.max(0, Math.min(n - 1, i));
    }

    private static double radiusAt(PathSpline sp, double s) {
        double k = Math.abs(averaged(sp, s, 1.0));
        return k < 1.0e-9 ? Double.POSITIVE_INFINITY : 1.0 / k;
    }

    private static double clamp(double v, double m) {
        return Math.max(-m, Math.min(m, v));
    }

    private static double max(double[] a) {
        double m = 0.0;
        for (double v : a) m = Math.max(m, v);
        return m;
    }

    public static double averaged(PathSpline sp, double at, double half) {
        double sum = 0.0;
        int n = 0;
        for (double d = -half; d <= half + 1.0e-9; d += sp.step()) {
            double q = at + d;
            if (!sp.closed() && (q < 0.0 || q > sp.length())) continue;
            sum += sp.curvatureAt(q);
            n++;
        }
        return n == 0 ? 0.0 : sum / n;
    }
}
