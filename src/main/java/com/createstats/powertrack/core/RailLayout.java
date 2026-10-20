package com.createstats.powertrack.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RailLayout {
    public static final double INDEX_PAD = 2.0;

    public static final double GRADE_GAP = 1.5;

    public static final double KINK_DEG = 5.0;

    public static final double CLIMB_TOLERANCE = 1.0 / 32.0;

    public record EdgeIn(int a, int b, double length, boolean power, double[] xyz) {
    }

    public static final class Shape {
        private final int lo;
        private final int hi;
        private final double length;
        private final double[] xyz;
        private final int n;

        Shape(int lo, int hi, double length, double[] xyz) {
            this.lo = lo;
            this.hi = hi;
            this.length = Math.max(0.0, length);
            this.xyz = xyz;
            this.n = xyz.length / 3;
        }

        public int lo() {
            return lo;
        }

        public int hi() {
            return hi;
        }

        public double length() {
            return length;
        }

        public int samples() {
            return n;
        }

        public double[] point(double at) {
            final double t = length <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, at / length));
            final double f = t * (n - 1);
            final int k = Math.min(n - 2, (int) Math.floor(f));
            final double u = f - k;
            final int i = 3 * k;
            return new double[]{
                    xyz[i] + (xyz[i + 3] - xyz[i]) * u,
                    xyz[i + 1] + (xyz[i + 4] - xyz[i + 1]) * u,
                    xyz[i + 2] + (xyz[i + 5] - xyz[i + 2]) * u};
        }

        public double[] tangent(double at) {
            final double h = Math.min(0.125, Math.max(1e-3, length / 8.0));
            final double[] p = point(Math.max(0.0, at - h));
            final double[] q = point(Math.min(length, at + h));
            final double dx = q[0] - p[0];
            final double dz = q[2] - p[2];
            final double m = Math.sqrt(dx * dx + dz * dz);
            return m < 1e-9 ? new double[]{1.0, 0.0} : new double[]{dx / m, dz / m};
        }

        public boolean sameLine(Shape other) {
            return other != null && lo == other.lo && hi == other.hi && Double.compare(length, other.length) == 0
                    && Arrays.equals(xyz, other.xyz);
        }

        public double[] bounds() {
            double minX = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (int k = 0; k < n; k++) {
                minX = Math.min(minX, sx(k));
                maxX = Math.max(maxX, sx(k));
                minZ = Math.min(minZ, sz(k));
                maxZ = Math.max(maxZ, sz(k));
            }
            return new double[]{minX, minZ, maxX, maxZ};
        }

        double sx(int k) {
            return xyz[3 * k];
        }

        double sy(int k) {
            return xyz[3 * k + 1];
        }

        double sz(int k) {
            return xyz[3 * k + 2];
        }
    }

    public record Hit(int lo, int hi, double at, double lateral, double floorY, SectionGraph.Piece piece,
                      double x, double z, double tx, double tz) {
        public boolean contact() {
            return piece != null && piece.railAt(at);
        }

        public int section() {
            return piece == null ? -1 : piece.section();
        }

        public double railTopY() {
            return contact() ? floorY + RailProfile.topPx(piece, at) / 16.0 : Double.NaN;
        }
    }

    private final SectionGraph sections;
    private final List<Shape> shapes;
    private final Map<Long, Shape> byKey;

    private final Map<Long, long[]> cells;

    private RailLayout(SectionGraph sections, List<Shape> shapes, Map<Long, long[]> cells) {
        this.sections = sections;
        this.shapes = shapes;
        this.byKey = new HashMap<>();
        for (Shape s : shapes) {
            byKey.put(SectionGraph.key(s.lo, s.hi), s);
        }
        this.cells = cells;
    }

    public static RailLayout empty() {
        return new RailLayout(SectionGraph.empty(), List.of(), Collections.emptyMap());
    }

    public SectionGraph sections() {
        return sections;
    }

    public List<Shape> shapes() {
        return shapes;
    }

    public Shape shape(int a, int b) {
        return byKey.get(SectionGraph.key(a, b));
    }

    public static RailLayout build(Collection<EdgeIn> edges, Collection<SectionGraph.Cut> cuts) {
        final Map<Long, Shape> unique = new TreeMap<>();
        final Map<Long, Boolean> power = new HashMap<>();
        for (EdgeIn e : edges) {
            if (e.a() == e.b() || e.xyz() == null || e.xyz().length < 6) {
                continue;
            }
            final long k = SectionGraph.key(e.a(), e.b());
            if (unique.containsKey(k)) {
                continue;
            }
            final boolean flip = e.a() > e.b();
            unique.put(k, new Shape(Math.min(e.a(), e.b()), Math.max(e.a(), e.b()), e.length(),
                    flip ? reversed(e.xyz()) : e.xyz().clone()));
            power.put(k, e.power());
        }
        final Map<Integer, List<Long>> atNode = new HashMap<>();
        for (Map.Entry<Long, Shape> en : unique.entrySet()) {
            atNode.computeIfAbsent(en.getValue().lo, x -> new ArrayList<>()).add(en.getKey());
            atNode.computeIfAbsent(en.getValue().hi, x -> new ArrayList<>()).add(en.getKey());
        }

        final java.util.Set<Long> bare = new java.util.HashSet<>();
        for (Map.Entry<Long, Shape> en : unique.entrySet()) {
            if (power.get(en.getKey()) && climbsUnevenly(en.getValue())) {
                bare.add(en.getKey());
            }
        }
        final java.util.Set<Integer> stops = new java.util.HashSet<>();
        for (Map.Entry<Integer, List<Long>> en : atNode.entrySet()) {
            final List<Long> here = en.getValue();
            if (here.size() == 2 && pitchChange(unique.get(here.get(0)), unique.get(here.get(1)), en.getKey()) > KINK_DEG) {
                stops.add(en.getKey());
            } else if (here.size() >= 3 && throughKink(unique, power, here, en.getKey())) {
                stops.add(en.getKey());
            }
            for (long k : here) {
                if (bare.contains(k)) {
                    stops.add(en.getKey());
                }
            }
        }

        final List<SectionGraph.Edge> graphEdges = new ArrayList<>(unique.size());
        for (Map.Entry<Long, Shape> en : unique.entrySet()) {
            final Shape s = en.getValue();
            final boolean isPower = power.get(en.getKey());
            double gapLo = 0.0;
            double gapHi = 0.0;
            if (isPower) {
                gapLo = junctionGap(unique, atNode, en.getKey(), s.lo);
                gapHi = junctionGap(unique, atNode, en.getKey(), s.hi);
                if (stops.contains(s.lo)) {
                    gapLo = Math.max(gapLo, GRADE_GAP);
                }
                if (stops.contains(s.hi)) {
                    gapHi = Math.max(gapHi, GRADE_GAP);
                }
            }
            graphEdges.add(new SectionGraph.Edge(s.lo, s.hi, s.length, isPower, gapLo, gapHi,
                    bare.contains(en.getKey())));
        }
        final SectionGraph sections = SectionGraph.build(graphEdges, cuts, stops);

        final List<Shape> shapes = new ArrayList<>();
        final Map<Long, long[]> building = new HashMap<>();
        final Map<Long, Integer> fill = new HashMap<>();
        final java.util.Set<Long> marked = new java.util.HashSet<>();
        for (Map.Entry<Long, Shape> en : unique.entrySet()) {
            if (!power.get(en.getKey())) {
                continue;
            }
            final Shape s = en.getValue();
            final int index = shapes.size();
            shapes.add(s);
            for (int k = 0; k + 1 < s.n; k++) {
                final double ax = s.sx(k);
                final double az = s.sz(k);
                final double dx = s.sx(k + 1) - ax;
                final double dz = s.sz(k + 1) - az;
                final int pieces = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / CELL));
                final long ref = ((long) index << 32) | (k & 0xFFFFFFFFL);
                marked.clear();
                for (int p = 0; p < pieces; p++) {
                    final double x0 = ax + dx * p / pieces;
                    final double z0 = az + dz * p / pieces;
                    final double x1 = ax + dx * (p + 1) / pieces;
                    final double z1 = az + dz * (p + 1) / pieces;
                    final int cx0 = cellOf(Math.min(x0, x1) - INDEX_PAD);
                    final int cx1 = cellOf(Math.max(x0, x1) + INDEX_PAD);
                    final int cz0 = cellOf(Math.min(z0, z1) - INDEX_PAD);
                    final int cz1 = cellOf(Math.max(z0, z1) + INDEX_PAD);
                    for (int cx = cx0; cx <= cx1; cx++) {
                        for (int cz = cz0; cz <= cz1; cz++) {
                            final long c = cell(cx, cz);
                            if (!marked.add(c)) {
                                continue;
                            }
                            long[] refs = building.get(c);
                            final int used = fill.getOrDefault(c, 0);
                            if (refs == null || used == refs.length) {
                                refs = refs == null ? new long[4] : Arrays.copyOf(refs, refs.length * 2);
                                building.put(c, refs);
                            }
                            refs[used] = ref;
                            fill.put(c, used + 1);
                        }
                    }
                }
            }
        }
        final Map<Long, long[]> cells = new HashMap<>(building.size() * 2);
        for (Map.Entry<Long, long[]> en : building.entrySet()) {
            cells.put(en.getKey(), Arrays.copyOf(en.getValue(), fill.get(en.getKey())));
        }
        return new RailLayout(sections, Collections.unmodifiableList(shapes), cells);
    }

    static final double CELL = 4.0;

    private static int cellOf(double v) {
        return (int) Math.floor(v / CELL);
    }

    public Hit nearest(double x, double y, double z, double maxLateral, double minAbove, double maxAbove) {
        final long[] refs = cells.get(cell(cellOf(x), cellOf(z)));
        if (refs == null) {
            return null;
        }
        final double reach = Math.min(maxLateral, INDEX_PAD);
        Hit best = null;
        double bestScore = Double.MAX_VALUE;
        for (long ref : refs) {
            final Shape s = shapes.get((int) (ref >>> 32));
            final int k = (int) ref;
            final double ax = s.sx(k);
            final double az = s.sz(k);
            final double dx = s.sx(k + 1) - ax;
            final double dz = s.sz(k + 1) - az;
            final double len2 = dx * dx + dz * dz;
            double u = len2 < 1e-12 ? 0.0 : ((x - ax) * dx + (z - az) * dz) / len2;
            u = Math.max(0.0, Math.min(1.0, u));
            final double px = ax + dx * u;
            final double pz = az + dz * u;
            final double lateral = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
            if (lateral > reach) {
                continue;
            }
            final double floorY = s.sy(k) + (s.sy(k + 1) - s.sy(k)) * u;
            final double above = y - floorY;
            if (above < minAbove || above > maxAbove) {
                continue;
            }
            final double score = lateral * 64.0 + Math.abs(above - (minAbove + maxAbove) / 2.0);
            if (score < bestScore) {
                bestScore = score;
                final double at = s.length * ((k + u) / (s.n - 1));
                final double m = Math.sqrt(len2);
                best = new Hit(s.lo, s.hi, at, lateral, floorY, sections.pieceAt(s.lo, s.hi, at), px, pz,
                        m < 1e-9 ? 1.0 : dx / m, m < 1e-9 ? 0.0 : dz / m);
            }
        }
        return best;
    }

    private static double junctionGap(Map<Long, Shape> unique, Map<Integer, List<Long>> atNode, long self,
                                      int node) {
        final List<Long> here = atNode.getOrDefault(node, List.of());
        if (here.size() < 3) {
            return 0.0;
        }
        final Shape mine = unique.get(self);
        double gap = 0.0;
        for (long other : here) {
            if (other == self) {
                continue;
            }
            final Shape theirs = unique.get(other);

            gap = Math.max(gap, JunctionClearance.clearing(walker(mine, node), mine.length,
                    walker(theirs, node), theirs.length));
        }
        return gap;
    }

    private static JunctionClearance.Walk walker(Shape s, int fromNode) {
        final boolean fromLo = fromNode == s.lo;
        return (d, xz) -> {
            final double[] p = s.point(fromLo ? d : s.length - d);
            xz[0] = p[0];
            xz[1] = p[2];
        };
    }

    static boolean climbsUnevenly(Shape s) {
        if (s.n <= 2) {
            return false;
        }
        final double y0 = s.sy(0);
        final double y1 = s.sy(s.n - 1);
        for (int k = 1; k < s.n - 1; k++) {
            final double expect = y0 + (y1 - y0) * k / (s.n - 1.0);
            if (Math.abs(s.sy(k) - expect) > CLIMB_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    static double pitchChange(Shape a, Shape b, int node) {
        final double pa = pitchLeaving(a, node);
        final double pb = pitchLeaving(b, node);

        return Math.abs(pa + pb);
    }

    static boolean throughKink(Map<Long, Shape> unique, Map<Long, Boolean> power, List<Long> here, int node) {
        for (int i = 0; i < here.size(); i++) {
            for (int j = i + 1; j < here.size(); j++) {
                final long ki = here.get(i);
                final long kj = here.get(j);
                if (!power.get(ki) && !power.get(kj)) {
                    continue;
                }
                final Shape a = unique.get(ki);
                final Shape b = unique.get(kj);
                final double[] da = leaving(a, node);
                final double[] db = leaving(b, node);
                if (da[0] * db[0] + da[1] * db[1] < -0.7 && pitchChange(a, b, node) > KINK_DEG) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double[] leaving(Shape s, int node) {
        final double d = Math.min(0.5, s.length);
        final boolean fromLo = node == s.lo;
        final double[] p0 = s.point(fromLo ? 0 : s.length);
        final double[] p1 = s.point(fromLo ? d : s.length - d);
        final double dx = p1[0] - p0[0];
        final double dz = p1[2] - p0[2];
        final double m = Math.hypot(dx, dz);
        return m < 1e-9 ? new double[]{0.0, 0.0} : new double[]{dx / m, dz / m};
    }

    private static double pitchLeaving(Shape s, int node) {
        final double d = Math.min(0.5, s.length);
        final boolean fromLo = node == s.lo;
        final double[] p0 = s.point(fromLo ? 0 : s.length);
        final double[] p1 = s.point(fromLo ? d : s.length - d);
        final double h = Math.hypot(p1[0] - p0[0], p1[2] - p0[2]);
        return Math.toDegrees(Math.atan2(p1[1] - p0[1], Math.max(1e-9, h)));
    }

    static double[] walk(Shape s, int fromNode) {
        final int count = JunctionClearance.samples(s.length);
        final double[] out = new double[2 * count];
        final boolean fromLo = fromNode == s.lo;
        for (int i = 0; i < count; i++) {
            final double d = Math.min(s.length, i * JunctionClearance.STEP);
            final double[] p = s.point(fromLo ? d : s.length - d);
            out[2 * i] = p[0];
            out[2 * i + 1] = p[2];
        }
        return out;
    }

    private static double[] reversed(double[] xyz) {
        final int n = xyz.length / 3;
        final double[] out = new double[n * 3];
        for (int i = 0; i < n; i++) {
            System.arraycopy(xyz, 3 * (n - 1 - i), out, 3 * i, 3);
        }
        return out;
    }

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int fingerprint(Collection<EdgeIn> edges, Collection<SectionGraph.Cut> cuts) {
        long h = 1469598103934665603L;
        final long[] keys = new long[edges.size()];
        int i = 0;
        for (EdgeIn e : edges) {
            long k = SectionGraph.key(e.a(), e.b()) * 31L + (e.power() ? 7 : 3);
            k = k * 31L + Double.hashCode(Math.round(e.length() * 1024.0) / 1024.0);
            keys[i++] = k;
        }
        Arrays.sort(keys);
        for (long k : keys) {
            h = (h ^ k) * 1099511628211L;
        }
        final long[] cutKeys = new long[cuts.size()];
        i = 0;
        for (SectionGraph.Cut c : cuts) {
            final double at = c.a() < c.b() ? c.at() : -c.at();
            cutKeys[i++] = SectionGraph.key(c.a(), c.b()) * 131L + Math.round(at * 4096.0);
        }
        Arrays.sort(cutKeys);
        for (long k : cutKeys) {
            h = (h ^ k) * 1099511628211L;
        }
        return (int) (h ^ (h >>> 32));
    }
}
