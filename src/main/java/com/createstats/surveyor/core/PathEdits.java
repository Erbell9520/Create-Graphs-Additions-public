package com.createstats.surveyor.core;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PathEdits {
    public static final double JOIN_MERGE = 1.0;

    public static final double JOIN_REACH = 8.0;

    public static final double DUPLICATE_OFFSET = 3.0;

    public static final double BRUSH_RADIUS = 5.0;

    static final int BRUSH_PASSES = 4;
    static final double BRUSH_STRENGTH = 0.5;

    public static final double JUNCTION_REACH = 4.0;

    private PathEdits() {
    }

    public record Split(Pathway first, @Nullable Pathway second) {
    }

    @Nullable
    public static Split split(Pathway p, int nodeId, UUID secondId, String secondName) {
        int k = p.indexOf(nodeId);
        List<PathNode> nodes = p.nodes();
        if (k < 0) return null;
        if (p.closed()) {
            List<PathNode> list = new ArrayList<>(nodes.size() + 1);
            for (int i = 0; i < nodes.size(); i++) list.add(nodes.get((k + i) % nodes.size()));
            list.add(plain(nodes.get(k), p.nextNodeId()));
            Pathway opened = p.withNodeList(list, p.nextNodeId() + 1).withKind(PathKind.ONE_WAY);
            return new Split(opened, null);
        }
        if (k == 0 || k == nodes.size() - 1) return null;
        Pathway first = p.withNodeList(new ArrayList<>(nodes.subList(0, k + 1)), p.nextNodeId());
        List<PathNode> rest = new ArrayList<>(nodes.size() - k);
        int next = 1;
        rest.add(plain(nodes.get(k), next++));
        for (int i = k + 1; i < nodes.size(); i++) rest.add(renumbered(nodes.get(i), next++));
        Pathway second = new Pathway(secondId, secondName, p.colour(), PathKind.ONE_WAY, p.owner(), p.shared(), p.cruise(),
                p.comfort(), 1L, next, rest, p.flags());
        return new Split(first, second);
    }

    public enum End {
        FIRST, LAST
    }

    @Nullable
    public static Pathway join(Pathway a, End aEnd, Pathway b, End bEnd) {
        if (a.closed() || b.closed() || a.id().equals(b.id()) || a.nodes().isEmpty() || b.nodes().isEmpty()) return null;
        PathNode an = aEnd == End.FIRST ? a.nodes().get(0) : a.nodes().get(a.nodes().size() - 1);
        PathNode bn = bEnd == End.FIRST ? b.nodes().get(0) : b.nodes().get(b.nodes().size() - 1);
        double gap = Math.hypot(an.x() - bn.x(), an.z() - bn.z());
        if (gap > JOIN_REACH) return null;

        List<PathNode> bList = new ArrayList<>(b.nodes());
        boolean after = aEnd == End.LAST;

        if (after ? bEnd == End.LAST : bEnd == End.FIRST) Collections.reverse(bList);
        if (gap <= JOIN_MERGE) {
            if (after) bList.remove(0);
            else bList.remove(bList.size() - 1);
        }
        int next = a.nextNodeId();
        List<PathNode> renumbered = new ArrayList<>(bList.size());
        for (PathNode n : bList) renumbered.add(renumbered(n, next++));
        List<PathNode> list = new ArrayList<>(a.nodes().size() + renumbered.size());
        if (after) {
            list.addAll(a.nodes());
            list.addAll(renumbered);
        } else {
            list.addAll(renumbered);
            list.addAll(a.nodes());
        }
        Pathway joined = a.withNodeList(list, next);
        return joined.kind() == PathKind.ONE_WAY ? joined : joined.withKind(PathKind.ONE_WAY);
    }

    public static Pathway duplicate(Pathway p, UUID id, String name, UUID owner, int colour, double dx, double dz) {
        List<PathNode> list = new ArrayList<>(p.nodes().size());
        for (PathNode n : p.nodes()) list.add(n.at(n.x() + dx, n.y(), n.z() + dz));
        return new Pathway(id, name, Math.floorMod(colour, 16), p.kind(), owner, false, p.cruise(), p.comfort(), 1L,
                p.nextNodeId(), list, p.flags());
    }

    public static Pathway translate(Pathway p, int from, int to, double dx, double dy, double dz) {
        int n = p.nodes().size();
        if (n == 0 || from < 0 || to < 0 || from >= n || to >= n) return p;
        List<PathNode> list = new ArrayList<>(p.nodes());
        int count = p.closed() ? Math.floorMod(to - from, n) + 1 : Math.max(0, to - from) + 1;
        for (int j = 0; j < count; j++) {
            int i = (from + j) % n;
            PathNode node = list.get(i);
            list.set(i, node.at(node.x() + dx, node.y() + dy, node.z() + dz));
        }
        return p.withNodeList(list, p.nextNodeId());
    }

    public static Pathway smooth(Pathway p, List<double[]> stroke, double radius) {
        int n = p.nodes().size();
        if (n < 3 || stroke.isEmpty() || !(radius > 0.0)) return p;
        boolean closed = p.closed();
        double[] w = new double[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (PathFairing.anchor(p, i)) continue;
            if (!closed && (i == 0 || i == n - 1)) continue;
            PathNode node = p.nodes().get(i);
            double best = 0.0;
            for (double[] b : stroke) {
                double d = Math.hypot(node.x() - b[0], node.z() - b[2]);
                if (d < radius) best = Math.max(best, (1.0 - d / radius) * (1.0 - d / radius));
            }
            w[i] = best;
            any |= best > 0.0;
        }
        if (!any) return p;
        double[] x = new double[n], y = new double[n], z = new double[n];
        for (int i = 0; i < n; i++) {
            PathNode node = p.nodes().get(i);
            x[i] = node.x();
            y[i] = node.y();
            z[i] = node.z();
        }
        for (int pass = 0; pass < BRUSH_PASSES; pass++) {
            double[] nx = x.clone(), ny = y.clone(), nz = z.clone();
            for (int i = 0; i < n; i++) {
                if (w[i] <= 0.0) continue;
                int a = closed ? Math.floorMod(i - 1, n) : i - 1;
                int b = closed ? (i + 1) % n : i + 1;
                double k = BRUSH_STRENGTH * w[i];
                nx[i] = x[i] + k * ((x[a] + x[b]) * 0.5 - x[i]);
                ny[i] = y[i] + k * ((y[a] + y[b]) * 0.5 - y[i]);
                nz[i] = z[i] + k * ((z[a] + z[b]) * 0.5 - z[i]);
            }
            x = nx;
            y = ny;
            z = nz;
        }
        List<PathNode> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(w[i] > 0.0 ? p.nodes().get(i).at(x[i], y[i], z[i]) : p.nodes().get(i));
        return p.withNodeList(list, p.nextNodeId());
    }

    public record Meeting(UUID pathway, int nodeId, double x, double y, double z, double distance) {
    }

    @Nullable
    public static Meeting nearestMeeting(Pathway p, int nodeId, List<Pathway> others) {
        PathNode node = p.node(nodeId);
        if (node == null) return null;
        Meeting best = null;
        for (Pathway o : others) {
            if (o.id().equals(p.id())) continue;
            for (PathNode m : o.nodes()) {
                double d = Math.hypot(m.x() - node.x(), m.z() - node.z());
                if (d <= JUNCTION_REACH && Math.abs(m.y() - node.y()) <= 3.0 && (best == null || d < best.distance())) {
                    best = new Meeting(o.id(), m.id(), m.x(), m.y(), m.z(), d);
                }
            }
        }
        return best;
    }

    private static PathNode plain(PathNode n, int id) {
        return new PathNode(id, n.x(), n.y(), n.z(), "", 0.0, Corner.SMOOTH, null);
    }

    private static PathNode renumbered(PathNode n, int id) {
        return new PathNode(id, n.x(), n.y(), n.z(), n.name(), n.speedLimit(), n.corner(), n.stop());
    }
}
