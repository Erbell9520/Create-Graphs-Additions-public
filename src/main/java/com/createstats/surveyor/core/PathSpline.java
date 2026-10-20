package com.createstats.surveyor.core;

import java.util.Arrays;
import java.util.List;

// centripetal catmull-rom, uniform loops and cusps on uneven spacing
public final class PathSpline {
    public static final double STEP = 0.25;

    public static final double MAX_LENGTH = 8192.0;

    static final double ALPHA = 0.5;

    public static final PathSpline EMPTY = new PathSpline(false, new double[0], new double[0], new double[0], 0.0, new double[0]);

    private final boolean closed;
    private final int n;
    private final double step;
    private final double length;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;

    private final double[] txs;
    private final double[] tzs;

    private final double[] kappa;

    private final double[] nodeS;

    private PathSpline(boolean closed, double[] xs, double[] ys, double[] zs, double length, double[] nodeS) {
        this.closed = closed;
        this.n = xs.length;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.length = length;
        this.nodeS = nodeS;
        int segments = closed ? n : Math.max(1, n - 1);
        this.step = n <= 1 ? STEP : length / segments;
        this.txs = new double[n];
        this.tzs = new double[n];
        this.kappa = new double[n];
        tangents();
        curvatures();
    }

    public static PathSpline of(Pathway p) {
        return of(p.nodes(), p.closed());
    }

    public static PathSpline of(List<PathNode> nodes, boolean closed) {
        int m = nodes.size();
        if (m == 0) return EMPTY;
        if (m == 1) {
            PathNode a = nodes.get(0);
            return new PathSpline(false, new double[]{a.x()}, new double[]{a.y()}, new double[]{a.z()}, 0.0, new double[]{0.0});
        }
        boolean loop = closed && m >= 3;
        int segments = loop ? m : m - 1;

        double[][] dense = new double[3][];
        int[] segStart = new int[segments + 1];
        int count = 0;
        int capacity = 256;
        double[] dx = new double[capacity];
        double[] dy = new double[capacity];
        double[] dz = new double[capacity];
        double[] p0 = new double[3];
        double[] p1 = new double[3];
        double[] p2 = new double[3];
        double[] p3 = new double[3];
        double[] m1 = new double[3];
        double[] m2 = new double[3];
        for (int i = 0; i < segments; i++) {
            PathNode a = nodes.get(i);
            PathNode b = nodes.get((i + 1) % m);
            set(p1, a);
            set(p2, b);
            if (i > 0 || loop) set(p0, nodes.get((i - 1 + m) % m));
            else reflect(p0, p1, p2);
            if (i + 2 < m || loop) set(p3, nodes.get((i + 2) % m));
            else reflect(p3, p2, p1);
            tangentsFor(p0, p1, p2, p3, m1, m2);
            corner(a.corner(), true, p1, p2, m1);
            corner(b.corner(), false, p1, p2, m2);
            double chord = Math.sqrt(sq(p2[0] - p1[0]) + sq(p2[2] - p1[2]));
            int sub = (int) Math.min(4096, Math.max(8, Math.ceil(chord * 8.0)));
            segStart[i] = count;
            for (int j = 0; j < sub; j++) {
                double u = j / (double) sub;
                if (count >= capacity) {
                    capacity *= 2;
                    dx = Arrays.copyOf(dx, capacity);
                    dy = Arrays.copyOf(dy, capacity);
                    dz = Arrays.copyOf(dz, capacity);
                }
                dx[count] = hermite(p1[0], m1[0], p2[0], m2[0], u);
                dy[count] = hermite(p1[1], m1[1], p2[1], m2[1], u);
                dz[count] = hermite(p1[2], m1[2], p2[2], m2[2], u);
                count++;
            }
        }

        if (count + 1 >= capacity) {
            capacity += 2;
            dx = Arrays.copyOf(dx, capacity);
            dy = Arrays.copyOf(dy, capacity);
            dz = Arrays.copyOf(dz, capacity);
        }
        PathNode end = loop ? nodes.get(0) : nodes.get(m - 1);
        dx[count] = end.x();
        dy[count] = end.y();
        dz[count] = end.z();
        count++;
        segStart[segments] = count - 1;

        double[] cum = new double[count];
        for (int i = 1; i < count; i++) {
            cum[i] = cum[i - 1] + Math.sqrt(sq(dx[i] - dx[i - 1]) + sq(dz[i] - dz[i - 1]));
        }
        double length = Math.min(cum[count - 1], MAX_LENGTH);
        double[] nodeS = new double[m];
        for (int i = 0; i < m; i++) {
            nodeS[i] = i < segments ? Math.min(cum[segStart[i]], length) : length;
        }
        if (length < 1.0e-6) {
            PathNode a = nodes.get(0);
            return new PathSpline(false, new double[]{a.x()}, new double[]{a.y()}, new double[]{a.z()}, 0.0, new double[m]);
        }

        int steps = Math.max(1, (int) Math.round(length / STEP));
        int samples = loop ? steps : steps + 1;
        double h = length / steps;
        double[] xs = new double[samples];
        double[] ys = new double[samples];
        double[] zs = new double[samples];
        int j = 0;
        for (int k = 0; k < samples; k++) {
            double target = k * h;
            while (j < count - 2 && cum[j + 1] < target) j++;
            double seg = cum[j + 1] - cum[j];
            double t = seg < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, (target - cum[j]) / seg));
            xs[k] = dx[j] + (dx[j + 1] - dx[j]) * t;
            ys[k] = dy[j] + (dy[j + 1] - dy[j]) * t;
            zs[k] = dz[j] + (dz[j + 1] - dz[j]) * t;
        }
        return new PathSpline(loop, xs, ys, zs, length, nodeS);
    }

    private static void set(double[] out, PathNode n) {
        out[0] = n.x();
        out[1] = n.y();
        out[2] = n.z();
    }

    private static void reflect(double[] out, double[] at, double[] other) {
        for (int i = 0; i < 3; i++) out[i] = 2.0 * at[i] - other[i];
    }

    static void tangentsFor(double[] p0, double[] p1, double[] p2, double[] p3, double[] m1, double[] m2) {
        double d01 = Math.max(1.0e-4, Math.pow(dist(p0, p1), ALPHA));
        double d12 = Math.max(1.0e-4, Math.pow(dist(p1, p2), ALPHA));
        double d23 = Math.max(1.0e-4, Math.pow(dist(p2, p3), ALPHA));
        double t01 = d01;
        double t02 = d01 + d12;
        double t13 = d12 + d23;
        for (int i = 0; i < 3; i++) {
            m1[i] = d12 * ((p1[i] - p0[i]) / t01 - (p2[i] - p0[i]) / t02 + (p2[i] - p1[i]) / d12);
            m2[i] = d12 * ((p2[i] - p1[i]) / d12 - (p3[i] - p1[i]) / t13 + (p3[i] - p2[i]) / d23);
        }
    }

    private static void corner(Corner corner, boolean leaving, double[] p1, double[] p2, double[] m) {
        switch (corner) {
            case SHARP -> {
                for (int i = 0; i < 3; i++) m[i] = p2[i] - p1[i];
            }
            case WIDE -> {
                for (int i = 0; i < 3; i++) m[i] *= Corner.WIDE_TENSION;
            }
            case SMOOTH -> {
            }
        }
    }

    static double hermite(double a, double ma, double b, double mb, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * a + (u3 - 2 * u2 + u) * ma + (-2 * u3 + 3 * u2) * b + (u3 - u2) * mb;
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
    }

    private static double sq(double v) {
        return v * v;
    }

    private void tangents() {
        if (n < 2) {
            if (n == 1) {
                txs[0] = 0.0;
                tzs[0] = -1.0;
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            int a = neighbour(i, -1);
            int b = neighbour(i, 1);
            double hx = xs[b] - xs[a];
            double hz = zs[b] - zs[a];
            double len = Math.sqrt(hx * hx + hz * hz);
            if (len < 1.0e-9) {
                txs[i] = i > 0 ? txs[i - 1] : 0.0;
                tzs[i] = i > 0 ? tzs[i - 1] : -1.0;
            } else {
                txs[i] = hx / len;
                tzs[i] = hz / len;
            }
        }
    }

    private void curvatures() {
        if (n < 3) return;
        for (int i = 0; i < n; i++) {
            int a = neighbour(i, -2);
            int b = neighbour(i, 2);
            double span = closed ? 4.0 * step : (b - a) * step;
            if (span < 1.0e-9) continue;
            double ha = heading(txs[a], tzs[a]);
            double hb = heading(txs[b], tzs[b]);
            kappa[i] = wrapAngle(hb - ha) / span;
        }
    }

    private int neighbour(int i, int offset) {
        int j = i + offset;
        if (closed) return Math.floorMod(j, n);
        return Math.max(0, Math.min(n - 1, j));
    }

    public static double heading(double hx, double hz) {
        return Math.atan2(hx, -hz);
    }

    public static double wrapAngle(double a) {
        double r = a % (2.0 * Math.PI);
        if (r > Math.PI) r -= 2.0 * Math.PI;
        if (r < -Math.PI) r += 2.0 * Math.PI;
        return r;
    }

    public boolean closed() {
        return closed;
    }

    public double length() {
        return length;
    }

    public int sampleCount() {
        return n;
    }

    public boolean isEmpty() {
        return n == 0;
    }

    public double step() {
        return step;
    }

    public double sampleX(int i) {
        return xs[i];
    }

    public double sampleY(int i) {
        return ys[i];
    }

    public double sampleZ(int i) {
        return zs[i];
    }

    public double sampleS(int i) {
        return i * step;
    }

    public double tangentX(int i) {
        return txs[i];
    }

    public double tangentZ(int i) {
        return tzs[i];
    }

    public double curvature(int i) {
        return kappa[i];
    }

    public int nodeCount() {
        return nodeS.length;
    }

    public double nodeS(int index) {
        return index >= 0 && index < nodeS.length ? nodeS[index] : 0.0;
    }

    public double wrap(double s) {
        if (length <= 0.0) return 0.0;
        if (closed) {
            double r = s % length;
            return r < 0 ? r + length : r;
        }
        return Math.max(0.0, Math.min(length, s));
    }

    public double ahead(double from, double to) {
        if (closed) {
            double d = (to - from) % length;
            return d < 0 ? d + length : d;
        }
        return to - from;
    }

    public double signedDelta(double from, double to) {
        if (!closed) return to - from;
        double d = ahead(from, to);
        return d > length * 0.5 ? d - length : d;
    }

    private double index(double s) {
        if (n <= 1) return 0.0;
        double w = wrap(s);
        double f = w / step;
        if (!closed) return Math.min(f, n - 1);
        return f >= n ? f - n : f;
    }

    private double lerp(double[] a, double s) {
        if (n == 0) return 0.0;
        if (n == 1) return a[0];
        double f = index(s);
        int i = (int) Math.floor(f);
        double t = f - i;
        int j = closed ? (i + 1) % n : Math.min(i + 1, n - 1);
        i = Math.max(0, Math.min(n - 1, i));
        return a[i] + (a[j] - a[i]) * t;
    }

    public double x(double s) {
        return lerp(xs, s);
    }

    public double y(double s) {
        return lerp(ys, s);
    }

    public double z(double s) {
        return lerp(zs, s);
    }

    public double[] tangent(double s) {
        double tx = lerp(txs, s);
        double tz = lerp(tzs, s);
        double len = Math.sqrt(tx * tx + tz * tz);
        if (len < 1.0e-9) return new double[]{0.0, -1.0};
        return new double[]{tx / len, tz / len};
    }

    public double curvatureAt(double s) {
        return lerp(kappa, s);
    }

    public double grade(double s) {
        if (n < 2) return 0.0;
        double a = closed ? s - 1.0 : Math.max(0.0, s - 1.0);
        double b = closed ? s + 1.0 : Math.min(length, s + 1.0);
        double run = closed ? 2.0 : b - a;
        return run < 1.0e-6 ? 0.0 : (y(b) - y(a)) / run;
    }

    public int nearestNode(double s) {
        if (nodeS.length == 0) return -1;
        double w = wrap(s);
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < nodeS.length; i++) {
            double d = Math.abs(closed ? signedDelta(nodeS[i], w) : w - nodeS[i]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public int segmentAt(double s) {
        if (nodeS.length == 0) return -1;
        double w = wrap(s);
        int lo = 0;
        int hi = nodeS.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (nodeS[mid] <= w) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }
}
