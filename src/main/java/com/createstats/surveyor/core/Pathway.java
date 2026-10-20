package com.createstats.surveyor.core;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

public record Pathway(UUID id, String name, int colour, PathKind kind, UUID owner, boolean shared, double cruise,
                      Comfort comfort, long version, int nextNodeId, List<PathNode> nodes, int flags) {
    public static final int AMPHIBIOUS = 1;

    public Pathway(UUID id, String name, int colour, PathKind kind, UUID owner, boolean shared, double cruise,
                   Comfort comfort, long version, int nextNodeId, List<PathNode> nodes) {
        this(id, name, colour, kind, owner, shared, cruise, comfort, version, nextNodeId, nodes, 0);
    }

    public boolean amphibious() {
        return (flags & AMPHIBIOUS) != 0;
    }

    public Pathway withAmphibious(boolean on) {
        return new Pathway(id, name, colour, kind, owner, shared, cruise, comfort, version + 1, nextNodeId, nodes,
                on ? flags | AMPHIBIOUS : flags & ~AMPHIBIOUS);
    }

    public static final double DEFAULT_CRUISE = 6.0;
    public static final double MIN_CRUISE = 1.0;
    public static final int MAX_NAME = 32;

    public static final int MAX_NODES = 512;

    public Pathway {
        name = name == null ? "" : name;
        kind = kind == null ? PathKind.ONE_WAY : kind;
        comfort = comfort == null ? Comfort.NORMAL : comfort;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public static Pathway create(UUID id, String name, int colour, UUID owner) {
        return new Pathway(id, name, Math.floorMod(colour, 16), PathKind.ONE_WAY, owner, false, DEFAULT_CRUISE,
                Comfort.NORMAL, 1L, 1, List.of());
    }

    public boolean closed() {
        return kind == PathKind.LOOP && nodes.size() >= 3;
    }

    public int indexOf(int nodeId) {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).id() == nodeId) return i;
        return -1;
    }

    @Nullable
    public PathNode node(int nodeId) {
        int i = indexOf(nodeId);
        return i < 0 ? null : nodes.get(i);
    }

    public int numberOf(int nodeId) {
        int i = indexOf(nodeId);
        return i < 0 ? 0 : i + 1;
    }

    public String label(int nodeId) {
        PathNode n = node(nodeId);
        if (n == null) return "?";
        return n.name().isEmpty() ? "N" + numberOf(nodeId) : n.name();
    }

    public int stopCount() {
        int count = 0;
        for (PathNode n : nodes) if (n.isStop()) count++;
        return count;
    }

    private Pathway withNodesBumped(List<PathNode> list, int next) {
        return new Pathway(id, name, colour, kind, owner, shared, cruise, comfort, version + 1, next, list, flags);
    }

    public Pathway append(double x, double y, double z, boolean atStart) {
        List<PathNode> list = new ArrayList<>(nodes);
        PathNode n = new PathNode(nextNodeId, x, y, z, "", 0.0, Corner.SMOOTH, null);
        if (atStart) list.add(0, n);
        else list.add(n);
        return withNodesBumped(list, nextNodeId + 1);
    }

    public Pathway appendAll(List<double[]> points) {
        List<PathNode> list = new ArrayList<>(nodes);
        int next = nextNodeId;
        for (double[] p : points) list.add(new PathNode(next++, p[0], p[1], p[2], "", 0.0, Corner.SMOOTH, null));
        return withNodesBumped(list, next);
    }

    public Pathway appendNodes(List<PathNode> add) {
        List<PathNode> list = new ArrayList<>(nodes);
        int next = nextNodeId;
        for (PathNode n : add) {
            list.add(new PathNode(next++, n.x(), n.y(), n.z(), n.name(), n.speedLimit(), n.corner(), n.stop()));
        }
        return withNodesBumped(list, next);
    }

    public Pathway insertAfter(int afterIndex, double x, double y, double z) {
        if (afterIndex < 0 || afterIndex >= nodes.size()) return this;
        List<PathNode> list = new ArrayList<>(nodes);
        list.add(afterIndex + 1, new PathNode(nextNodeId, x, y, z, "", 0.0, Corner.SMOOTH, null));
        return withNodesBumped(list, nextNodeId + 1);
    }

    public Pathway move(int nodeId, double x, double y, double z) {
        return update(nodeId, n -> n.at(x, y, z));
    }

    public Pathway update(int nodeId, UnaryOperator<PathNode> change) {
        int i = indexOf(nodeId);
        if (i < 0) return this;
        List<PathNode> list = new ArrayList<>(nodes);
        list.set(i, change.apply(list.get(i)).sane());
        return withNodesBumped(list, nextNodeId);
    }

    public Pathway delete(int nodeId) {
        int i = indexOf(nodeId);
        if (i < 0) return this;
        List<PathNode> list = new ArrayList<>(nodes);
        list.remove(i);
        Pathway p = withNodesBumped(list, nextNodeId);
        return p.kind == PathKind.LOOP && list.size() < 3 ? p.withKindKeepVersion(PathKind.ONE_WAY) : p;
    }

    public Pathway withNodeList(List<PathNode> list, int next) {
        return withNodesBumped(list, Math.max(next, nextNodeId));
    }

    public Pathway withKind(PathKind k) {
        return new Pathway(id, name, colour, k, owner, shared, cruise, comfort, version + 1, nextNodeId, nodes, flags);
    }

    private Pathway withKindKeepVersion(PathKind k) {
        return new Pathway(id, name, colour, k, owner, shared, cruise, comfort, version, nextNodeId, nodes, flags);
    }

    public Pathway withName(String n) {
        String cut = n == null ? "" : (n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n);
        return new Pathway(id, cut, colour, kind, owner, shared, cruise, comfort, version + 1, nextNodeId, nodes, flags);
    }

    public Pathway withColour(int c) {
        return new Pathway(id, name, Math.floorMod(c, 16), kind, owner, shared, cruise, comfort, version + 1, nextNodeId, nodes, flags);
    }

    public Pathway withShared(boolean s) {
        return new Pathway(id, name, colour, kind, owner, s, cruise, comfort, version + 1, nextNodeId, nodes, flags);
    }

    public Pathway withCruise(double v) {
        double c = Double.isFinite(v) ? Math.max(MIN_CRUISE, Math.min(PathNode.MAX_SPEED, v)) : DEFAULT_CRUISE;
        return new Pathway(id, name, colour, kind, owner, shared, c, comfort, version + 1, nextNodeId, nodes, flags);
    }

    public Pathway withComfort(Comfort c) {
        return new Pathway(id, name, colour, kind, owner, shared, cruise, c, version + 1, nextNodeId, nodes, flags);
    }

    public Pathway prependOutward(List<double[]> outward) {
        return reversed().appendAll(outward).reversed();
    }

    public Pathway reversed() {
        List<PathNode> list = new ArrayList<>(nodes);
        java.util.Collections.reverse(list);
        return withNodesBumped(list, nextNodeId);
    }
}
