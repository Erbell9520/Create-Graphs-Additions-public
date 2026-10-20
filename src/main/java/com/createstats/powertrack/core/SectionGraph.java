package com.createstats.powertrack.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// never sees a block, so it's testable
public final class SectionGraph {
    public static final double END_GAP = 0.5;

    public static final double JUNCTION_GAP_MIN = 1.25;

    public static final double INSULATOR_GAP = 1.0;

    public static final double RAMP = 0.75;

    public static final double MIN_STUB = 2 * RAMP + 0.5;

    static final double EPS = 1e-6;

    public record Edge(int a, int b, double length, boolean power, double junctionGapA, double junctionGapB,
                       boolean bare) {
        public Edge(int a, int b, double length, boolean power) {
            this(a, b, length, power, 0.0, 0.0, false);
        }

        public Edge(int a, int b, double length, boolean power, double junctionGapA, double junctionGapB) {
            this(a, b, length, power, junctionGapA, junctionGapB, false);
        }
    }

    public record Cut(int a, int b, double at) {
    }

    public enum RailEnd {
        THROUGH,

        RAMP_JUMPERED,

        RAMP_INSULATED,

        RAMP_END;

        public boolean ramped() {
            return this != THROUGH;
        }
    }

    public record Piece(int section, double from, double to, double railFrom, double railTo,
                        RailEnd fromEnd, RailEnd toEnd) {
        public boolean hasRail() {
            return railTo - railFrom > EPS;
        }

        public boolean railAt(double at) {
            return hasRail() && at >= railFrom - EPS && at <= railTo + EPS;
        }

        public Piece flipped(double length) {
            return new Piece(section, length - to, length - from, length - railTo, length - railFrom,
                    toEnd, fromEnd);
        }
    }

    public enum Gap {
        NONE,

        INSULATED,

        JUNCTION,

        GRADE,

        CLIMBING_CURVE,

        SHORT,

        END
    }

    private record Canon(int lo, int hi, double length) {
    }

    private final Map<Long, Piece[]> pieces;
    private final Map<Long, Double> lengths;
    private final int sections;

    private final Map<Integer, Integer> degree;

    private final java.util.Set<Long> bareEdges;

    private SectionGraph(Map<Long, Piece[]> pieces, Map<Long, Double> lengths, int sections,
                         Map<Integer, Integer> degree, java.util.Set<Long> bareEdges) {
        this.pieces = pieces;
        this.lengths = lengths;
        this.sections = sections;
        this.degree = degree;
        this.bareEdges = bareEdges;
    }

    public static long key(int a, int b) {
        final int lo = Math.min(a, b);
        final int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    public static SectionGraph empty() {
        return new SectionGraph(Collections.emptyMap(), Collections.emptyMap(), 0, Collections.emptyMap(),
                java.util.Set.of());
    }

    public static SectionGraph build(Collection<Edge> edges, Collection<Cut> cuts) {
        return build(edges, cuts, java.util.Set.of());
    }

    public static SectionGraph build(Collection<Edge> edges, Collection<Cut> cuts, java.util.Set<Integer> stopNodes) {
        final Map<Long, Edge> unique = new TreeMap<>();
        for (Edge e : edges) {
            if (e.a() == e.b()) {
                continue;
            }
            unique.putIfAbsent(key(e.a(), e.b()), e);
        }
        final Map<Integer, Integer> degree = new HashMap<>();
        final Map<Integer, Boolean> allPower = new HashMap<>();
        final java.util.Set<Integer> stops = new java.util.HashSet<>(stopNodes);
        for (Edge e : unique.values()) {
            for (int n : new int[]{e.a(), e.b()}) {
                degree.merge(n, 1, Integer::sum);
                allPower.merge(n, e.power(), (x, y) -> x && y);
                if (e.power() && e.bare()) {
                    stops.add(n);
                }
            }
        }

        final Map<Long, double[]> cutsByEdge = new HashMap<>();
        {
            final Map<Long, List<Double>> raw = new HashMap<>();
            for (Cut c : cuts) {
                final long k = key(c.a(), c.b());
                final Edge e = unique.get(k);
                if (e == null || !e.power()) {
                    continue;
                }
                final Canon canon = canon(e);
                double at = c.a() == canon.lo() ? c.at() : canon.length() - c.at();
                at = Math.max(0.0, Math.min(canon.length(), at));
                raw.computeIfAbsent(k, x -> new ArrayList<>()).add(at);
            }
            for (Map.Entry<Long, List<Double>> en : raw.entrySet()) {
                final double[] sorted = en.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();
                final double[] dedup = new double[sorted.length];
                int n = 0;
                for (double v : sorted) {
                    if (n == 0 || v - dedup[n - 1] > EPS) {
                        dedup[n++] = v;
                    }
                }
                cutsByEdge.put(en.getKey(), Arrays.copyOf(dedup, n));
            }
        }

        final List<long[]> pieceKeys = new ArrayList<>();
        final List<double[]> pieceSpans = new ArrayList<>();
        final Map<Long, int[]> pieceIndexByEdge = new HashMap<>();
        final Map<Integer, List<Integer>> piecesAtNode = new HashMap<>();
        for (Map.Entry<Long, Edge> en : unique.entrySet()) {
            final Edge e = en.getValue();
            if (!e.power()) {
                continue;
            }
            final Canon canon = canon(e);
            final double[] cutAt = cutsByEdge.getOrDefault(en.getKey(), new double[0]);
            final int count = cutAt.length + 1;
            final int[] indices = new int[count];
            for (int i = 0; i < count; i++) {
                final double from = i == 0 ? 0.0 : cutAt[i - 1];
                final double to = i == count - 1 ? canon.length() : cutAt[i];
                indices[i] = pieceKeys.size();
                pieceKeys.add(new long[]{en.getKey(), i});
                pieceSpans.add(new double[]{from, to});
            }
            pieceIndexByEdge.put(en.getKey(), indices);
            piecesAtNode.computeIfAbsent(canon.lo(), x -> new ArrayList<>()).add(indices[0]);
            piecesAtNode.computeIfAbsent(canon.hi(), x -> new ArrayList<>()).add(indices[count - 1]);
        }

        final int[] parent = new int[pieceKeys.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (List<Integer> atNode : piecesAtNode.values()) {
            for (int i = 1; i < atNode.size(); i++) {
                union(parent, atNode.get(0), atNode.get(i));
            }
        }

        final Map<Integer, Integer> sectionOfRoot = new HashMap<>();
        final Map<Long, Piece[]> out = new HashMap<>();
        final Map<Long, Double> lengths = new HashMap<>();
        for (Map.Entry<Long, Edge> en : unique.entrySet()) {
            final Edge e = en.getValue();
            if (!e.power()) {
                continue;
            }
            final Canon canon = canon(e);
            final int[] indices = pieceIndexByEdge.get(en.getKey());
            final Piece[] built = new Piece[indices.length];
            for (int i = 0; i < indices.length; i++) {
                final int root = find(parent, indices[i]);
                final int section = sectionOfRoot.computeIfAbsent(root, r -> sectionOfRoot.size());
                final double from = pieceSpans.get(indices[i])[0];
                final double to = pieceSpans.get(indices[i])[1];
                final RailEnd fromEnd = i == 0
                        ? nodeEnd(canon.lo(), degree, allPower, stops) : RailEnd.RAMP_INSULATED;
                final RailEnd toEnd = i == indices.length - 1
                        ? nodeEnd(canon.hi(), degree, allPower, stops) : RailEnd.RAMP_INSULATED;
                final double railFrom = from + gap(fromEnd, gapAt(e, canon.lo()));
                final double railTo = to - gap(toEnd, gapAt(e, canon.hi()));

                final boolean stub = fromEnd.ramped() && toEnd.ramped() && railTo - railFrom < MIN_STUB;
                final double railEnd = e.bare() || stub ? railFrom : Math.max(railFrom, railTo);
                built[i] = new Piece(section, from, to, railFrom, railEnd, fromEnd, toEnd);
            }
            out.put(en.getKey(), built);
            lengths.put(en.getKey(), canon.length());
        }
        final java.util.Set<Long> bare = new java.util.HashSet<>();
        for (Map.Entry<Long, Edge> en : unique.entrySet()) {
            if (en.getValue().power() && en.getValue().bare()) {
                bare.add(en.getKey());
            }
        }
        return new SectionGraph(out, lengths, sectionOfRoot.size(), degree, bare);
    }

    private static Canon canon(Edge e) {
        return new Canon(Math.min(e.a(), e.b()), Math.max(e.a(), e.b()), Math.max(0.0, e.length()));
    }

    private static double gapAt(Edge e, int n) {
        return n == e.a() ? e.junctionGapA() : e.junctionGapB();
    }

    private static RailEnd nodeEnd(int node, Map<Integer, Integer> degree, Map<Integer, Boolean> allPower,
                                   java.util.Set<Integer> stops) {
        final int d = degree.getOrDefault(node, 0);
        if (d >= 3) {
            return RailEnd.RAMP_JUMPERED;
        }
        if (d == 2 && allPower.getOrDefault(node, false)) {
            return stops.contains(node) ? RailEnd.RAMP_JUMPERED : RailEnd.THROUGH;
        }
        return RailEnd.RAMP_END;
    }

    private static double gap(RailEnd end, double requestedJunctionGap) {
        return switch (end) {
            case THROUGH -> 0.0;
            case RAMP_JUMPERED -> Math.max(JUNCTION_GAP_MIN, requestedJunctionGap);
            case RAMP_INSULATED -> INSULATOR_GAP / 2.0;

            case RAMP_END -> Math.max(END_GAP, requestedJunctionGap);
        };
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        final int ra = find(parent, a);
        final int rb = find(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    public int sectionCount() {
        return sections;
    }

    public boolean isPower(int a, int b) {
        return pieces.containsKey(key(a, b));
    }

    public Piece pieceAt(int a, int b, double at) {
        final long k = key(a, b);
        final Piece[] row = pieces.get(k);
        if (row == null) {
            return null;
        }
        final double length = lengths.get(k);
        final boolean flip = a > b;
        final double canonAt = flip ? length - at : at;
        Piece found = row[row.length - 1];
        for (Piece p : row) {
            if (canonAt <= p.to() + EPS) {
                found = p;
                break;
            }
        }
        return flip ? found.flipped(length) : found;
    }

    public int sectionAt(int a, int b, double at) {
        final Piece p = pieceAt(a, b, at);
        return p == null ? -1 : p.section();
    }

    public boolean contact(int a, int b, double at) {
        final Piece p = pieceAt(a, b, at);
        return p != null && p.railAt(at);
    }

    public Gap gapAt(int a, int b, double at) {
        final Piece p = pieceAt(a, b, at);
        if (p == null || p.railAt(at)) {
            return Gap.NONE;
        }
        if (bareEdges.contains(key(a, b))) {
            return Gap.CLIMBING_CURVE;
        }
        if (!p.hasRail()) {
            return Gap.SHORT;
        }
        final double length = lengths.get(key(a, b));
        final boolean fromSide = at < p.railFrom();
        final RailEnd end = fromSide ? p.fromEnd() : p.toEnd();
        final int node = fromSide ? (p.from() <= EPS ? a : -1) : (p.to() >= length - EPS ? b : -1);
        return switch (end) {
            case RAMP_INSULATED -> Gap.INSULATED;
            case RAMP_END -> Gap.END;
            case RAMP_JUMPERED -> node >= 0 && degree.getOrDefault(node, 0) >= 3 ? Gap.JUNCTION : Gap.GRADE;
            case THROUGH -> Gap.NONE;
        };
    }

    public List<Piece> pieces(int a, int b) {
        final long k = key(a, b);
        final Piece[] row = pieces.get(k);
        if (row == null) {
            return List.of();
        }
        if (a < b) {
            return List.of(row);
        }
        final double length = lengths.get(k);
        final List<Piece> out = new ArrayList<>(row.length);
        for (int i = row.length - 1; i >= 0; i--) {
            out.add(row[i].flipped(length));
        }
        return out;
    }
}
