package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PathFairing {
    public static final double TOLERANCE = 0.6;

    public static final double FIT = 0.1;

    static final double SPACING = 1.0;

    private PathFairing() {
    }

    public record Result(Pathway pathway, int before, int after, double worstDeviation, double lambda) {
    }

    public static Result fair(Pathway p) {
        return fair(p, TOLERANCE);
    }

    public static Result fair(Pathway p, double tolerance) {
        PathSpline sp = PathSpline.of(p);
        int nodes = p.nodes().size();
        if (sp.isEmpty() || sp.length() < 4.0 || nodes < 3) return new Result(p, nodes, nodes, 0.0, 0.0);
        boolean closed = sp.closed();

        int m = Math.max(4, (int) Math.round(sp.length() / SPACING));
        int count = closed ? m : m + 1;
        double h = sp.length() / m;
        double[][] d = new double[3][count];
        for (int i = 0; i < count; i++) {
            double s = i * h;
            d[0][i] = sp.x(s);
            d[1][i] = sp.y(s);
            d[2][i] = sp.z(s);
        }

        boolean[] fixed = new boolean[count];
        boolean[] kink = new boolean[count];
        int[] anchorIndex = new int[nodes];
        Arrays.fill(anchorIndex, -1);
        for (int k = 0; k < nodes && k < sp.nodeCount(); k++) {
            PathNode n = p.nodes().get(k);
            if (!anchor(p, k)) continue;
            int i = (int) Math.round(sp.nodeS(k) / h);
            i = closed ? Math.floorMod(i, count) : Math.max(0, Math.min(count - 1, i));
            fixed[i] = true;

            d[0][i] = n.x();
            d[1][i] = n.y();
            d[2][i] = n.z();
            anchorIndex[k] = i;
            if (n.corner() == Corner.SHARP) kink[i] = true;
        }
        if (!closed) {
            fixed[0] = true;
            fixed[count - 1] = true;
        }

        double lo = 0.0;
        double hi = 4096.0;
        double[][] best = copy(d);
        double bestDev = 0.0;
        double bestLambda = 0.0;
        for (int it = 0; it < 22; it++) {
            double lambda = it == 0 ? hi : 0.5 * (lo + hi);
            double[] rows = rowLambda(count, lambda);
            double[][] x = new double[3][];
            for (int c = 0; c < 3; c++) x[c] = solve(d[c], fixed, kink, closed, rows, c == 1 ? 0.25 : 1.0, null);
            double dev = worst(x, d);
            if (dev <= tolerance) {
                best = x;
                bestDev = dev;
                bestLambda = lambda;
                if (it == 0) break;
                lo = lambda;
            } else {
                hi = lambda;
            }
        }
        List<PathNode> out = resample(p, sp, best, fixed, anchorIndex, closed, h);
        Pathway faired = p.withNodeList(out, p.nextNodeId() + out.size());
        return new Result(faired, nodes, out.size(), bestDev, bestLambda);
    }

    public record Drivable(Pathway pathway, Result fair, int opened, List<TightBends.Bend> left) {
    }

    // keep a widening only if the bend is gone and nothing new appeared
    public static Drivable fairFor(Pathway p, double own, java.util.function.BiPredicate<Double, Double> ok) {
        Result r = fair(p);
        Pathway q = r.pathway();
        int opened = 0;
        if (own > 0.0) {
            List<double[]> tried = new ArrayList<>();
            for (int round = 0; round < 12; round++) {
                PathSpline sp = PathSpline.of(q);
                TightBends.Bend next = null;
                double[] at = null;
                for (TightBends.Bend b : TightBends.of(q, sp, own).bends()) {
                    double m = b.middle(sp);
                    double[] xz = {sp.x(m), sp.z(m)};
                    if (!near(tried, xz, 4.0)) {
                        next = b;
                        at = xz;
                        break;
                    }
                }
                if (next == null) break;
                tried.add(at);
                Widened w = openBend(q, next.middle(sp), own, ok);
                if (w != null) {
                    q = w.pathway();
                    opened++;
                }
            }
        }
        List<TightBends.Bend> left = own > 0.0 ? TightBends.of(q, PathSpline.of(q), own).bends() : List.of();
        return new Drivable(q, r, opened, left);
    }

    public static Widened openBend(Pathway q, double s, double own, java.util.function.BiPredicate<Double, Double> ok) {
        if (!(own > 0.0)) return null;
        PathSpline sp = PathSpline.of(q);
        double x = sp.x(s);
        double z = sp.z(s);
        int before = TightBends.of(q, sp, own).bends().size();
        for (double f : WIDEN_FACTORS) {
            Widened w = widen(q, s, own * f, ok);
            if (w == null || w.radius() < own * 0.9) continue;
            PathSpline ws = PathSpline.of(w.pathway());
            List<TightBends.Bend> after = TightBends.of(w.pathway(), ws, own).bends();
            boolean gone = true;
            for (TightBends.Bend b : after) {
                double m = b.middle(ws);
                if (Math.hypot(ws.x(m) - x, ws.z(m) - z) < 8.0) gone = false;
            }
            if (gone && after.size() < before && shift(sp, ws) <= MAX_SHIFT) return w;
        }
        return null;
    }

    private static boolean near(List<double[]> points, double[] xz, double within) {
        for (double[] p : points) if (Math.hypot(p[0] - xz[0], p[1] - xz[1]) < within) return true;
        return false;
    }

    public static final double MAX_SHIFT = 10.0;

    static final double[] WIDEN_FACTORS = {1.25, 1.1, 1.0};

    public static double shift(PathSpline from, PathSpline to) {
        double worst = 0.0;
        int n = from.sampleCount();
        for (int i = 0; i < to.sampleCount(); i += 2) {
            double x = to.sampleX(i);
            double z = to.sampleZ(i);
            double best = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                double dx = from.sampleX(j) - x;
                double dz = from.sampleZ(j) - z;
                best = Math.min(best, dx * dx + dz * dz);
            }
            worst = Math.max(worst, best);
        }
        return Math.sqrt(worst);
    }

    public static boolean anchor(Pathway p, int index) {
        PathNode n = p.nodes().get(index);
        if (index == 0) return true;
        if (!p.closed() && index == p.nodes().size() - 1) return true;
        return !n.name().isEmpty() || n.isStop() || n.corner() != Corner.SMOOTH || n.speedLimit() > 0.0;
    }

    public record Widened(Pathway pathway, double target, double radius) {
    }

    public static Widened widen(Pathway p, double s, double radius, java.util.function.BiPredicate<Double, Double> ok) {
        if (radius <= 0.0 || p.nodes().size() < 3) return null;
        PathSpline sp = PathSpline.of(p);
        double before = tightest(sp, s);
        if (before >= radius) return null;
        Widened best = fillet(p, sp, s, radius, ok);
        if (best != null && best.radius() >= radius * 0.95) return best;
        Widened u = uTurn(p, sp, s, radius, ok);
        if (u != null && (best == null || u.radius() > best.radius())) best = u;
        return best != null && best.radius() > before * 1.2 ? best : null;
    }

    static Widened fillet(Pathway p, PathSpline sp, double s, double radius, java.util.function.BiPredicate<Double, Double> ok) {
        double at = s;
        double worst = 0.0;
        for (double d = -8.0; d <= 8.0; d += sp.step()) {
            double k = Math.abs(smoothed(sp, s + d));
            if (k > worst) {
                worst = k;
                at = s + d;
            }
        }
        double a = at;
        double b = at;
        while (at - a < 40.0 && Math.abs(smoothed(sp, a - 0.5)) > 1.0 / (radius * 3.0)) a -= 0.5;
        while (b - at < 40.0 && Math.abs(smoothed(sp, b + 0.5)) > 1.0 / (radius * 3.0)) b += 0.5;
        double turn = 0.0;
        for (double q = a; q < b; q += sp.step()) turn += sp.curvatureAt(q) * sp.step();
        double abs = Math.abs(turn);
        boolean balloon = abs > Math.toRadians(200.0);
        if (abs < Math.toRadians(5.0) || abs > Math.toRadians(340.0) || (abs > Math.toRadians(160.0) && !balloon)) return null;
        for (int attempt = 0; attempt < 6; attempt++) {
            if (!sp.closed() && (a < 0.0 || b > sp.length())) return null;
            if (sp.closed() && b - a > sp.length() - 8.0) return null;
            double[] ta = sp.tangent(a);
            double[] tb = sp.tangent(b);
            double ax = sp.x(a);
            double az = sp.z(a);
            double bx = sp.x(b);
            double bz = sp.z(b);

            double det = ta[1] * tb[0] - ta[0] * tb[1];
            if (Math.abs(det) < 1.0e-6) return null;
            double rx = bx - ax;
            double rz = bz - az;
            double u = (rz * tb[0] - rx * tb[1]) / det;
            double w = (rx * ta[1] - rz * ta[0]) / det;

            double legs = PathSpline.wrapAngle(PathSpline.heading(tb[0], tb[1]) - PathSpline.heading(ta[0], ta[1]));
            double expect = balloon ? turn - Math.signum(turn) * 2.0 * Math.PI : turn;
            if (Math.abs(legs - expect) > Math.toRadians(30.0)) return null;
            double sweep = balloon ? legs + Math.signum(turn) * 2.0 * Math.PI : legs;
            double tangent = balloon ? radius * Math.tan((2.0 * Math.PI - Math.abs(sweep)) / 2.0) : radius * Math.tan(Math.abs(sweep) / 2.0);

            double needA = balloon ? -tangent - u : tangent - u;
            double needB = balloon ? -tangent - w : tangent - w;
            if (!balloon && (u < 0.0 || w < 0.0)) return null;
            if (needA > -0.5 || needB > -0.5) {
                a -= Math.max(0.0, needA + 0.5) + 1.0;
                b += Math.max(0.0, needB + 0.5) + 1.0;
                continue;
            }
            double vx = ax + ta[0] * u;
            double vz = az + ta[1] * u;
            double dir = balloon ? 1.0 : -1.0;
            double sAx = vx + dir * ta[0] * tangent;
            double sAz = vz + dir * ta[1] * tangent;
            double sBx = vx - dir * tb[0] * tangent;
            double sBz = vz - dir * tb[1] * tangent;
            double side = sweep > 0.0 ? 1.0 : -1.0;

            double cx = sAx + -ta[1] * side * radius;
            double cz = sAz + ta[0] * side * radius;

            List<PathNode> arc = new ArrayList<>();
            int first = p.nextNodeId();
            int next = legNodes(arc, first, ax, az, sAx, sAz, sp.y(a), true, ok);
            if (next >= 0) next = arcNodes(arc, next, sAx, sAz, sBx, sBz, cx, cz, radius, sweep, radius * Math.abs(sweep), sp.y(a), sp.y(b), ok);
            if (next >= 0) next = legNodes(arc, next, sBx, sBz, bx, bz, sp.y(b), false, ok);
            if (next < 0) return null;

            arc.add(new PathNode(next++, bx, sp.y(b), bz, "", 0.0, Corner.SMOOTH, null));
            List<PathNode> list = replaceStretch(p, sp, at, a, b, arc);
            if (list == null) return null;
            Pathway widened = p.withNodeList(list, next);
            return judged(widened, radius, first, next);
        }
        return null;
    }

    static List<PathNode> replaceStretch(Pathway p, PathSpline sp, double at, double a, double b, List<PathNode> insert) {
        int count = Math.min(p.nodes().size(), sp.nodeCount());
        boolean[] inside = new boolean[count];
        int before = -1;
        double beforeOff = Double.NEGATIVE_INFINITY;
        int kept = 0;
        for (int k = 0; k < count; k++) {
            double off = sp.closed() ? sp.signedDelta(at, sp.nodeS(k)) : sp.nodeS(k) - at;

            inside[k] = off > -(at - a) - 1.0 && off < (b - at) + 1.0;
            if (inside[k] && strongAnchor(p, k)) return null;
            if (inside[k]) continue;
            kept++;
            if (off <= -(at - a) - 1.0 && off > beforeOff) {
                beforeOff = off;
                before = k;
            }
        }
        if (kept < 2) return null;
        List<PathNode> out = new ArrayList<>(count + insert.size());
        if (before < 0) out.addAll(insert);
        for (int k = 0; k < count; k++) {
            if (!inside[k]) out.add(p.nodes().get(k));
            if (k == before) out.addAll(insert);
        }
        return out;
    }

    private static Widened judged(Pathway widened, double target, int firstId, int endId) {
        PathSpline ws = PathSpline.of(widened);
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < widened.nodes().size() && k < ws.nodeCount(); k++) {
            int id = widened.nodes().get(k).id();
            if (id < firstId || id >= endId) continue;
            lo = Math.min(lo, ws.nodeS(k));
            hi = Math.max(hi, ws.nodeS(k));
        }
        if (!Double.isFinite(lo)) return null;
        double after = Double.POSITIVE_INFINITY;
        for (double q = lo - 6.0; q <= hi + 6.0; q += 2.0) after = Math.min(after, tightest(ws, q));
        return new Widened(widened, target, after);
    }

    static Widened uTurn(Pathway p, PathSpline sp, double s, double radius, java.util.function.BiPredicate<Double, Double> ok) {
        double at = s;
        double worst = 0.0;
        for (double d = -8.0; d <= 8.0; d += sp.step()) {
            double k = Math.abs(smoothed(sp, s + d));
            if (k > worst) {
                worst = k;
                at = s + d;
            }
        }
        double a = at;
        double b = at;
        while (at - a < 40.0 && Math.abs(smoothed(sp, a - 0.5)) > 1.0 / (radius * 3.0)) a -= 0.5;
        while (b - at < 40.0 && Math.abs(smoothed(sp, b + 0.5)) > 1.0 / (radius * 3.0)) b += 0.5;
        double turn = 0.0;
        for (double q = a; q < b; q += sp.step()) turn += sp.curvatureAt(q) * sp.step();
        if (Math.abs(turn) < Math.toRadians(150.0) || Math.abs(turn) > Math.toRadians(210.0)) return null;
        double sign = Math.signum(turn);
        for (int attempt = 0; attempt < 6; attempt++) {
            if (!sp.closed() && (a < 0.0 || b > sp.length())) return null;
            if (sp.closed() && b - a > sp.length() - 8.0) return null;
            double[] ta = sp.tangent(a);
            double[] tb = sp.tangent(b);
            double ax = sp.x(a);
            double az = sp.z(a);
            double bx = sp.x(b);
            double bz = sp.z(b);

            double ux = ta[0] - tb[0];
            double uz = ta[1] - tb[1];
            double ul = Math.hypot(ux, uz);
            if (ul < 1.0e-6) return null;
            ux /= ul;
            uz /= ul;
            double reach = Double.NEGATIVE_INFINITY;
            double px = ax;
            double pz = az;
            for (double q = a; q <= b; q += sp.step()) {
                double d = (sp.x(q) - ax) * ux + (sp.z(q) - az) * uz;
                if (d > reach) {
                    reach = d;
                    px = sp.x(q);
                    pz = sp.z(q);
                }
            }

            double[] c = {-uz * sign, ux * sign};
            double[] m1 = meet(ax, az, ta, px, pz, c);
            double[] m2 = meet(px, pz, c, bx, bz, tb);
            if (m1 == null || m2 == null) return null;
            double th1 = PathSpline.wrapAngle(PathSpline.heading(c[0], c[1]) - PathSpline.heading(ta[0], ta[1]));
            double th2 = PathSpline.wrapAngle(PathSpline.heading(tb[0], tb[1]) - PathSpline.heading(c[0], c[1]));
            if (Math.signum(th1) != sign || Math.signum(th2) != sign) return null;
            double t1 = radius * Math.tan(Math.abs(th1) / 2.0);
            double t2 = radius * Math.tan(Math.abs(th2) / 2.0);

            if (m2[2] - m1[3] < t1 + t2) return null;
            double needA = t1 + 0.5 - m1[2];
            double needB = t2 + 0.5 + m2[3];
            if (needA > 0.0 || needB > 0.0) {
                a -= Math.max(0.0, needA) + 1.0;
                b += Math.max(0.0, needB) + 1.0;
                continue;
            }
            double v1x = m1[0];
            double v1z = m1[1];
            double v2x = m2[0];
            double v2z = m2[1];
            double a1x = v1x - ta[0] * t1;
            double a1z = v1z - ta[1] * t1;
            double b1x = v1x + c[0] * t1;
            double b1z = v1z + c[1] * t1;
            double a2x = v2x - c[0] * t2;
            double a2z = v2z - c[1] * t2;
            double b2x = v2x + tb[0] * t2;
            double b2z = v2z + tb[1] * t2;

            double c1x = a1x + -ta[1] * sign * radius;
            double c1z = a1z + ta[0] * sign * radius;
            double c2x = a2x + -c[1] * sign * radius;
            double c2z = a2z + c[0] * sign * radius;
            double ya = sp.y(a);
            double yb = sp.y(b);
            List<PathNode> nodes = new ArrayList<>();
            int first = p.nextNodeId();
            int next = legNodes(nodes, first, ax, az, a1x, a1z, ya, true, ok);
            if (next >= 0) next = arcNodes(nodes, next, a1x, a1z, b1x, b1z, c1x, c1z, radius, th1, radius * Math.abs(th1), ya, 0.5 * (ya + yb), ok);
            if (next >= 0) next = legNodes(nodes, next, b1x, b1z, a2x, a2z, 0.5 * (ya + yb), false, ok);
            if (next >= 0) next = arcNodes(nodes, next, a2x, a2z, b2x, b2z, c2x, c2z, radius, th2, radius * Math.abs(th2), 0.5 * (ya + yb), yb, ok);
            if (next >= 0) next = legNodes(nodes, next, b2x, b2z, bx, bz, yb, false, ok);
            if (next < 0) return null;
            nodes.add(new PathNode(next++, bx, yb, bz, "", 0.0, Corner.SMOOTH, null));
            List<PathNode> list = replaceStretch(p, sp, at, a, b, nodes);
            if (list == null) return null;
            return judged(p.withNodeList(list, next), radius, first, next);
        }
        return null;
    }

    static double[] meet(double ax, double az, double[] da, double bx, double bz, double[] db) {
        double det = -da[0] * db[1] + da[1] * db[0];
        if (Math.abs(det) < 1.0e-9) return null;
        double rx = bx - ax;
        double rz = bz - az;
        double ta = (-rx * db[1] + rz * db[0]) / det;
        double tb = (da[0] * rz - da[1] * rx) / det;
        return new double[]{ax + da[0] * ta, az + da[1] * ta, ta, tb};
    }

    private static int legNodes(List<PathNode> list, int next, double x0, double z0, double x1, double z1, double y,
                                boolean withStart, java.util.function.BiPredicate<Double, Double> ok) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int pieces = Math.max(1, (int) Math.round(len / 2.0));
        if (len < 0.75) return next;
        for (int i = withStart ? 0 : 1; i < pieces; i++) {
            double f = (double) i / pieces;
            double x = x0 + (x1 - x0) * f;
            double z = z0 + (z1 - z0) * f;
            if (ok != null && !ok.test(x, z)) return -1;
            list.add(new PathNode(next++, x, y, z, "", 0.0, Corner.SMOOTH, null));
        }
        return next;
    }

    private static int arcNodes(java.util.List<PathNode> list, int next, double sAx, double sAz, double sBx, double sBz,
                                double cx, double cz, double radius, double turn, double arc, double y0, double y1,
                                java.util.function.BiPredicate<Double, Double> ok) {
        int pieces = Math.max(2, (int) Math.ceil(arc / 2.0));
        double a0 = Math.atan2(sAz - cz, sAx - cx);
        for (int i = 0; i <= pieces; i++) {
            double ang = a0 + (turn > 0.0 ? 1.0 : -1.0) * Math.abs(turn) * i / pieces;
            double x = i == 0 ? sAx : (i == pieces ? sBx : cx + radius * Math.cos(ang));
            double z = i == 0 ? sAz : (i == pieces ? sBz : cz + radius * Math.sin(ang));
            if (ok != null && !ok.test(x, z)) return -1;
            double f = (double) i / pieces;
            list.add(new PathNode(next++, x, y0 + (y1 - y0) * f * f * (3.0 - 2.0 * f), z, "", 0.0, Corner.SMOOTH, null));
        }
        return next;
    }

    static boolean strongAnchor(Pathway p, int index) {
        PathNode n = p.nodes().get(index);
        if (!p.closed() && (index == 0 || index == p.nodes().size() - 1)) return true;
        return !n.name().isEmpty() || n.isStop() || n.corner() == Corner.SHARP || n.speedLimit() > 0.0;
    }

    static double smoothed(PathSpline sp, double s) {
        double k = 0.0;
        int n = 0;
        for (double e = -1.0; e <= 1.0 + 1.0e-9; e += sp.step()) {
            k += sp.curvatureAt(s + e);
            n++;
        }
        return k / n;
    }

    public static double tightest(PathSpline sp, double s) {
        double worst = 0.0;
        for (double d = -8.0; d <= 8.0; d += sp.step()) {
            double k = 0.0;
            int n = 0;
            for (double e = -1.0; e <= 1.0 + 1.0e-9; e += sp.step()) {
                k += sp.curvatureAt(s + d + e);
                n++;
            }
            worst = Math.max(worst, Math.abs(k / n));
        }
        return worst < 1.0e-9 ? Double.POSITIVE_INFINITY : 1.0 / worst;
    }

    static double[] solve(double[] d, boolean[] fixed, boolean[] kink, boolean closed, double[] rows, double scale,
                          double[] weight) {
        int n = d.length;
        double[] lam = new double[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            lam[i] = rows[i] * scale;
            any |= lam[i] > 0.0;
        }
        double[] x = d.clone();
        if (!any) return x;
        double[] r = new double[n];
        double[] ax = apply(x, fixed, kink, closed, lam, weight);
        for (int i = 0; i < n; i++) r[i] = fixed[i] ? 0.0 : (weight == null ? 1.0 : weight[i]) * d[i] - ax[i];
        double[] pdir = r.clone();
        double rr = dot(r, r);
        double tol = 1.0e-18 * Math.max(1.0, dot(d, d));
        for (int it = 0; it < Math.min(4 * n + 50, 3000) && rr > tol; it++) {
            double[] ap = apply(pdir, fixed, kink, closed, lam, weight);
            for (int i = 0; i < n; i++) if (fixed[i]) ap[i] = 0.0;
            double pap = dot(pdir, ap);
            if (!(pap > 0.0)) break;
            double alpha = rr / pap;
            for (int i = 0; i < n; i++) {
                if (fixed[i]) continue;
                x[i] += alpha * pdir[i];
                r[i] -= alpha * ap[i];
            }
            double rr2 = dot(r, r);
            double beta = rr2 / rr;
            rr = rr2;
            for (int i = 0; i < n; i++) pdir[i] = fixed[i] ? 0.0 : r[i] + beta * pdir[i];
        }
        return x;
    }

    private static double[] apply(double[] v, boolean[] fixed, boolean[] kink, boolean closed, double[] lam, double[] weight) {
        int n = v.length;
        double[] out = v.clone();
        if (weight != null) for (int i = 0; i < n; i++) out[i] *= weight[i];
        int rows = closed ? n : n - 2;
        for (int r = 0; r < rows; r++) {
            int c = closed ? r : r + 1;
            if (kink[c]) continue;
            int a = closed ? Math.floorMod(c - 1, n) : c - 1;
            int b = closed ? Math.floorMod(c + 1, n) : c + 1;
            double dd = lam[c] * (v[a] - 2.0 * v[c] + v[b]);
            out[a] += dd;
            out[c] -= 2.0 * dd;
            out[b] += dd;
        }
        return out;
    }

    private static double[] rowLambda(int n, double lambda) {
        double[] r = new double[n];
        Arrays.fill(r, lambda);
        return r;
    }

    static double[] curvature(double[][] x, boolean closed, double h) {
        int n = x[0].length;
        double[] k = new double[n];
        for (int i = 0; i < n; i++) {
            int a = closed ? Math.floorMod(i - 1, n) : i - 1;
            int b = closed ? Math.floorMod(i + 1, n) : i + 1;
            if (a < 0 || b >= n) continue;
            double ddx = x[0][a] - 2.0 * x[0][i] + x[0][b];
            double ddz = x[2][a] - 2.0 * x[2][i] + x[2][b];
            k[i] = Math.sqrt(ddx * ddx + ddz * ddz) / (h * h);
        }
        return k;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static double worst(double[][] x, double[][] d) {
        double w = 0.0;
        for (int i = 0; i < x[0].length; i++) {
            double dx = x[0][i] - d[0][i];
            double dz = x[2][i] - d[2][i];
            w = Math.max(w, Math.sqrt(dx * dx + dz * dz));
        }
        return w;
    }

    private static double[][] copy(double[][] a) {
        double[][] out = new double[a.length][];
        for (int i = 0; i < a.length; i++) out[i] = a[i].clone();
        return out;
    }

    private static List<PathNode> resample(Pathway p, PathSpline old, double[][] fit, boolean[] fixed, int[] anchorIndex,
                                           boolean closed, double h) {
        int count = fit[0].length;

        List<Integer> breaks = new ArrayList<>();
        List<PathNode> breakNodes = new ArrayList<>();
        for (int k = 0; k < p.nodes().size(); k++) {
            if (anchorIndex[k] < 0) continue;
            breaks.add(anchorIndex[k]);
            breakNodes.add(p.nodes().get(k));
        }

        List<Integer> chosen = new ArrayList<>();
        List<PathNode> fromAnchor = new ArrayList<>();
        int pieces = closed ? breaks.size() : breaks.size() - 1;
        int step = Math.max(2, (int) Math.round(12.0 / h));
        for (int b = 0; b < pieces; b++) {
            int a0 = breaks.get(b);
            int a1 = closed ? breaks.get((b + 1) % breaks.size()) : breaks.get(b + 1);
            int len = closed ? Math.floorMod(a1 - a0, count) : a1 - a0;
            if (closed && breaks.size() == 1) len = count;
            chosen.add(a0);
            fromAnchor.add(breakNodes.get(b));
            for (int j = step; j < len - step / 2; j += step) {
                chosen.add(closed ? Math.floorMod(a0 + j, count) : a0 + j);
                fromAnchor.add(null);
            }
        }
        if (!closed) {
            chosen.add(breaks.get(breaks.size() - 1));
            fromAnchor.add(breakNodes.get(breakNodes.size() - 1));
        }

        for (int round = 0; round < 40; round++) {
            List<PathNode> trial = build(p, fit, chosen, fromAnchor, fixed);
            PathSpline sp = PathSpline.of(trial, closed);
            int worstSpan = -1;
            double worstDev = FIT;
            int worstAt = -1;
            int spans = closed ? chosen.size() : chosen.size() - 1;
            for (int s = 0; s < spans; s++) {
                int i0 = chosen.get(s);
                int i1 = chosen.get((s + 1) % chosen.size());
                int len = closed ? Math.floorMod(i1 - i0, count) : i1 - i0;
                if (len < 2) continue;
                double sA = sp.nodeS(s);
                double sB = s + 1 < sp.nodeCount() ? sp.nodeS(s + 1) : sp.length();
                double span = closed ? sp.ahead(sA, sB) : sB - sA;
                for (int j = 1; j < len; j++) {
                    int i = closed ? Math.floorMod(i0 + j, count) : i0 + j;
                    double at = sA + span * j / len;

                    PathProjection.Result r = PathProjection.window(sp, fit[0][i], fit[1][i], fit[2][i], at, 3.0, 3.0);
                    double dev = r.found() ? Math.abs(r.lateral()) : Double.POSITIVE_INFINITY;
                    if (dev > worstDev) {
                        worstDev = dev;
                        worstSpan = s;
                        worstAt = closed ? Math.floorMod(i0 + len / 2, count) : i0 + len / 2;
                    }
                }
            }
            if (worstSpan < 0 || chosen.size() >= Pathway.MAX_NODES - 1) break;
            chosen.add(worstSpan + 1, worstAt);
            fromAnchor.add(worstSpan + 1, null);
        }
        return build(p, fit, chosen, fromAnchor, fixed);
    }

    private static List<PathNode> build(Pathway p, double[][] fit, List<Integer> chosen, List<PathNode> fromAnchor,
                                        boolean[] fixed) {
        List<PathNode> out = new ArrayList<>(chosen.size());
        int next = p.nextNodeId();
        for (int k = 0; k < chosen.size(); k++) {
            PathNode a = fromAnchor.get(k);
            int i = chosen.get(k);
            if (a != null) {
                out.add(fixed[i] ? a : a.at(fit[0][i], fit[1][i], fit[2][i]));
            } else {
                out.add(new PathNode(next++, fit[0][i], fit[1][i], fit[2][i], "", 0.0, Corner.SMOOTH, null));
            }
        }
        return out;
    }
}
