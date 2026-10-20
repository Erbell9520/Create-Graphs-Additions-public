package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class PathNetwork {
    public static final double JOIN = 1.0;
    public static final double JOIN_DY = 1.5;

    public static final double MAX_TURN = 60.0;

    public static final double OCCUPIED_SECONDS = 30.0;

    public static final double TRANSFER_SECONDS = 1.5;

    public static final double ZONE_RADIUS = 3.0;

    public static final double CROSSING = 2.0;

    public record Transfer(UUID from, double fromS, UUID to, double toS, double turn, double x, double z) {
        public double leadIn(double radius) {
            return radius * Math.tan(Math.toRadians(Math.abs(turn)) / 2.0);
        }
    }

    public record Zone(int id, double x, double z, Map<UUID, Double> at) {
        public double[] on(UUID line, PathSpline sp) {
            Double s = at.get(line);
            if (s == null) return null;
            return new double[]{sp.closed() ? sp.wrap(s - ZONE_RADIUS) : Math.max(0.0, s - ZONE_RADIUS),
                    sp.closed() ? sp.wrap(s + ZONE_RADIUS) : Math.min(sp.length(), s + ZONE_RADIUS)};
        }
    }

    public record Leg(UUID pathway, double from, double to) {
    }

    public record Route(List<Leg> legs, List<Transfer> transfers, double seconds, UUID station, int stationNode) {
    }

    private final Map<UUID, Pathway> lines = new LinkedHashMap<>();
    private final Map<UUID, PathSpline> splines = new HashMap<>();
    private final Map<UUID, SpeedPlan> plans = new HashMap<>();
    private final List<Transfer> transfers = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();

    private PathNetwork() {
    }

    public static PathNetwork of(Collection<Pathway> pathways) {
        PathNetwork net = new PathNetwork();
        for (Pathway p : pathways) {
            if (p.nodes().size() < 2) continue;
            PathSpline sp = PathSpline.of(p);
            if (sp.isEmpty()) continue;
            net.lines.put(p.id(), p);
            net.splines.put(p.id(), sp);
            net.plans.put(p.id(), SpeedPlan.of(sp, p));
        }
        net.join();
        return net;
    }

    public Collection<Pathway> lines() {
        return lines.values();
    }

    public PathSpline spline(UUID line) {
        return splines.get(line);
    }

    public List<Transfer> transfers() {
        return transfers;
    }

    public List<Zone> zones() {
        return zones;
    }

    public List<Transfer> from(UUID line) {
        List<Transfer> out = new ArrayList<>();
        for (Transfer t : transfers) if (t.from().equals(line)) out.add(t);
        return out;
    }

    private void join() {
        List<UUID> ids = new ArrayList<>(lines.keySet());
        List<double[]> meets = new ArrayList<>();
        List<UUID[]> meetLines = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                UUID a = ids.get(i);
                UUID b = ids.get(j);
                for (double[] m : meetings(a, b)) {
                    meets.add(m);
                    meetLines.add(new UUID[]{a, b});
                }
            }
        }

        List<double[]> centres = new ArrayList<>();
        List<Map<UUID, Double>> ats = new ArrayList<>();
        for (int k = 0; k < meets.size(); k++) {
            double[] m = meets.get(k);
            UUID[] ab = meetLines.get(k);
            int found = -1;
            for (int z = 0; z < centres.size(); z++) {
                if (Math.hypot(centres.get(z)[0] - m[0], centres.get(z)[1] - m[1]) < ZONE_RADIUS) {
                    found = z;
                    break;
                }
            }
            if (found < 0) {
                centres.add(new double[]{m[0], m[1]});
                ats.add(new LinkedHashMap<>());
                found = centres.size() - 1;
            }
            ats.get(found).putIfAbsent(ab[0], m[2]);
            ats.get(found).putIfAbsent(ab[1], m[3]);
        }
        for (int z = 0; z < centres.size(); z++) zones.add(new Zone(z, centres.get(z)[0], centres.get(z)[1], Map.copyOf(ats.get(z))));
    }

    private List<double[]> meetings(UUID a, UUID b) {
        List<double[]> out = new ArrayList<>();
        Pathway pa = lines.get(a);
        Pathway pb = lines.get(b);
        PathSpline sa = splines.get(a);
        PathSpline sb = splines.get(b);

        for (int i = 0; i < pa.nodes().size() && i < sa.nodeCount(); i++) {
            PathNode na = pa.nodes().get(i);
            for (int j = 0; j < pb.nodes().size() && j < sb.nodeCount(); j++) {
                PathNode nb = pb.nodes().get(j);
                if (Math.hypot(na.x() - nb.x(), na.z() - nb.z()) <= JOIN && Math.abs(na.y() - nb.y()) <= JOIN_DY) {
                    add(out, na.x(), na.z(), sa.nodeS(i), sb.nodeS(j), a, b);
                }
            }
        }

        endsOn(out, a, b, pa, sa, sb, false);
        endsOn(out, b, a, pb, sb, sa, true);

        for (int i = 0; i < sa.sampleCount(); i += 4) {
            double x = sa.sampleX(i);
            double z = sa.sampleZ(i);
            PathProjection.Result r = PathProjection.global(sb, x, sa.sampleY(i), z, 0.0, 0.0, CROSSING, JOIN_DY + 1.0, 180.0);
            if (!r.found() || Math.abs(r.lateral()) > CROSSING) continue;
            boolean known = false;
            for (double[] m : out) known |= Math.hypot(m[0] - x, m[1] - z) < ZONE_RADIUS * 2;
            if (!known) out.add(new double[]{x, z, sa.sampleS(i), r.s()});
        }
        return out;
    }

    private void endsOn(List<double[]> out, UUID a, UUID b, Pathway pa, PathSpline sa, PathSpline sb, boolean swapped) {
        if (pa.closed()) return;
        int last = pa.nodes().size() - 1;
        for (int k : new int[]{0, last}) {
            PathNode n = pa.nodes().get(k);
            PathProjection.Result r = PathProjection.global(sb, n.x(), n.y(), n.z(), 0.0, 0.0, JOIN, JOIN_DY, 180.0);
            if (!r.found() || Math.abs(r.lateral()) > JOIN) continue;
            boolean known = false;
            for (double[] m : out) known |= Math.hypot(m[0] - n.x(), m[1] - n.z()) < JOIN * 1.5;
            if (known) continue;
            double s = k < sa.nodeCount() ? sa.nodeS(k) : (k == 0 ? 0.0 : sa.length());
            if (swapped) add(out, n.x(), n.z(), r.s(), s, b, a);
            else add(out, n.x(), n.z(), s, r.s(), a, b);
        }
    }

    private void add(List<double[]> out, double x, double z, double sa, double sb, UUID a, UUID b) {
        out.add(new double[]{x, z, sa, sb});
        PathSpline pa = splines.get(a);
        PathSpline pb = splines.get(b);
        double ha = PathSpline.heading(pa.tangent(sa)[0], pa.tangent(sa)[1]);
        double hb = PathSpline.heading(pb.tangent(sb)[0], pb.tangent(sb)[1]);
        double turn = Math.toDegrees(PathSpline.wrapAngle(hb - ha));
        if (Math.abs(turn) > MAX_TURN) return;

        if (pb.closed() || sb < pb.length() - 1.0) transfers.add(new Transfer(a, sa, b, sb, turn, x, z));
        if (pa.closed() || sa < pa.length() - 1.0) transfers.add(new Transfer(b, sb, a, sa, -turn, x, z));
    }

    // Routes

    public Route route(UUID line, double s, Predicate<String> station, BiFunction<UUID, Integer, Boolean> occupied) {
        if (!lines.containsKey(line)) return null;
        List<State> states = new ArrayList<>();
        PriorityQueue<Integer> open = new PriorityQueue<>((x, y) -> Double.compare(states.get(x).cost(), states.get(y).cost()));
        Map<String, Double> best = new HashMap<>();
        states.add(new State(line, s, 0.0, -1, null));
        open.add(0);
        Route found = null;
        int guard = 0;
        while (!open.isEmpty() && guard++ < 4096) {
            int idx = open.poll();
            State st = states.get(idx);
            if (found != null && st.cost() >= found.seconds()) break;
            Pathway p = lines.get(st.line());
            PathSpline sp = splines.get(st.line());
            SpeedPlan plan = plans.get(st.line());

            for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
                PathNode n = p.nodes().get(k);
                if (n.name().isEmpty() || !station.test(n.name())) continue;
                double ahead = ahead(sp, st.s(), sp.nodeS(k));
                if (ahead < 0.0) continue;
                double cost = st.cost() + plan.seconds(st.s(), sp.nodeS(k))
                        + (occupied != null && Boolean.TRUE.equals(occupied.apply(p.id(), n.id())) ? OCCUPIED_SECONDS : 0.0);
                if (found == null || cost < found.seconds()) found = build(states, idx, st.line(), sp.nodeS(k), cost, n.id());
            }

            for (Transfer t : from(st.line())) {
                double ahead = ahead(sp, st.s(), t.fromS());
                if (ahead < 0.5) continue;
                double cost = st.cost() + plan.seconds(st.s(), t.fromS()) + TRANSFER_SECONDS;
                String key = t.to() + "@" + Math.round(t.toS() * 4);
                Double had = best.get(key);
                if (had != null && had <= cost) continue;
                best.put(key, cost);
                states.add(new State(t.to(), t.toS(), cost, idx, t));
                open.add(states.size() - 1);
            }
        }
        return found;
    }

    private static double ahead(PathSpline sp, double from, double to) {
        if (sp.closed()) return sp.ahead(from, to);
        return to - from;
    }

    private record State(UUID line, double s, double cost, int prev, Transfer via) {
    }

    private static Route build(List<State> states, int idx, UUID lastLine, double stationS, double cost, int node) {
        List<Leg> legs = new ArrayList<>();
        List<Transfer> via = new ArrayList<>();
        double to = stationS;
        int at = idx;
        while (at >= 0) {
            State st = states.get(at);
            legs.add(0, new Leg(st.line(), st.s(), to));
            if (st.via() != null) {
                via.add(0, st.via());
                to = st.via().fromS();
            }
            at = st.prev();
        }
        return new Route(List.copyOf(legs), List.copyOf(via), cost, lastLine, node);
    }
}
