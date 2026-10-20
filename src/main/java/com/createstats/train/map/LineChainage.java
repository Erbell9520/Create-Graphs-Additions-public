package com.createstats.train.map;

import java.util.ArrayList;
import java.util.List;

import com.createstats.train.smart.RailSnapshot;

public final class LineChainage {
    public final int lineIndex;

    public final int[] stops;

    public final float[] chain;

    public final boolean[] surveyed;

    public final float total;

    private final RailMap map;

    private LineChainage(RailMap map, int lineIndex, int[] stops, float[] chain,
                         boolean[] surveyed) {
        this.map = map;
        this.lineIndex = lineIndex;
        this.stops = stops;
        this.chain = chain;
        this.surveyed = surveyed;
        this.total = chain.length == 0 ? 0.0f : chain[chain.length - 1];
    }

    public static final LineChainage EMPTY =
            new LineChainage(null, -1, new int[0], new float[0], new boolean[0]);

    public boolean isEmpty() {
        return this.stops.length < 2;
    }

    public int stopCount() {
        return this.stops.length;
    }

    public RailMap.Node node(int stopIndex) {
        return this.map == null || stopIndex < 0 || stopIndex >= this.stops.length
                ? null : this.map.node(this.stops[stopIndex]);
    }

    public static LineChainage of(RailMap map, int lineIndex) {
        if (map == null || lineIndex < 0 || lineIndex >= map.lines().size()) {
            return EMPTY;
        }
        final RailMap.Line line = map.lines().get(lineIndex);
        final List<Integer> order = new ArrayList<>(line.nodes);
        if (line.loop && order.size() > 1) {
            order.add(order.get(0));
        }
        return build(map, lineIndex, order);
    }

    public static LineChainage ofNodes(RailMap map, List<Integer> nodeOrder) {
        return build(map, -1, nodeOrder);
    }

    private static LineChainage build(RailMap map, int lineIndex, List<Integer> order) {
        if (map == null || order == null || order.size() < 2) {
            return EMPTY;
        }
        final int n = order.size();
        final int[] stops = new int[n];
        final float[] chain = new float[n];
        final boolean[] surveyed = new boolean[Math.max(1, n - 1)];
        for (int i = 0; i < n; i++) {
            stops[i] = order.get(i);
        }
        float running = 0.0f;
        for (int i = 1; i < n; i++) {
            final float leg = legLength(map, stops[i - 1], stops[i], surveyed, i - 1);
            running += leg;
            chain[i] = running;
        }
        return new LineChainage(map, lineIndex, stops, chain, surveyed);
    }

    private static float legLength(RailMap map, int a, int b, boolean[] surveyed, int leg) {
        final RailMap.Edge edge = map.edgeBetween(a, b);
        if (edge != null && edge.length > 1.0) {
            surveyed[leg] = edge.surveyed();
            return (float) edge.length;
        }
        final RailMap.Node from = map.node(a);
        final RailMap.Node to = map.node(b);
        if (from == null || to == null) {
            return 16.0f;
        }
        surveyed[leg] = false;
        return (float) Math.max(16.0, Math.hypot(to.x - from.x, to.z - from.z));
    }

    public float chainageOf(RailSnapshot.TrainState t) {
        if (t == null || isEmpty() || this.map == null) {
            return Float.NaN;
        }
        if (t.currentStation != null) {
            final int node = this.map.nodeOfStation(t.currentStation);
            final int at = standingAt(node, t);
            if (at >= 0) {
                return this.chain[at];
            }
        }
        final int from = this.map.originNodeOf(t);
        final int to = t.destinationStation == null ? -1
                : this.map.nodeOfStation(t.destinationStation);
        if (from < 0 || to < 0) {
            return Float.NaN;
        }
        final int leg = legIndexOf(from, to);
        if (leg < 0) {
            return Float.NaN;
        }

        final boolean forwards = this.stops[leg] == from;
        final float a = this.chain[forwards ? leg : leg + 1];
        final float b = this.chain[forwards ? leg + 1 : leg];
        return a + (b - a) * t.legProgress();
    }

    private int standingAt(int node, RailSnapshot.TrainState t) {
        if (node < 0) {
            return -1;
        }
        final int next = t.destinationStation == null ? -1
                : this.map.nodeOfStation(t.destinationStation);
        int fallback = -1;
        for (int i = 0; i < this.stops.length; i++) {
            if (this.stops[i] != node) {
                continue;
            }
            if (fallback < 0) {
                fallback = i;
            }
            if (next < 0) {
                continue;
            }
            if ((i + 1 < this.stops.length && this.stops[i + 1] == next)
                    || (i > 0 && this.stops[i - 1] == next)) {
                return i;
            }
        }
        return fallback;
    }

    private int indexOf(int node, int hint) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < this.stops.length; i++) {
            if (this.stops[i] != node) {
                continue;
            }
            final int d = Math.abs(i - hint);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    public int legIndexOf(int from, int to) {
        for (int i = 0; i + 1 < this.stops.length; i++) {
            if ((this.stops[i] == from && this.stops[i + 1] == to)
                    || (this.stops[i] == to && this.stops[i + 1] == from)) {
                return i;
            }
        }
        return -1;
    }

    public int stopNear(float metres) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < this.chain.length; i++) {
            final float d = Math.abs(this.chain[i] - metres);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    public float chainAt(int index) {
        return index < 0 || index >= this.chain.length ? Float.NaN : this.chain[index];
    }

    public int nodeOfFilter(String filter, String dimension) {
        return this.map == null ? -1 : this.map.nodeOfFilter(filter, dimension);
    }

    public int startIndex(RailSnapshot.TrainState t) {
        if (t == null || this.map == null || isEmpty()) {
            return -1;
        }
        if (t.currentStation != null) {
            final int at = standingAt(this.map.nodeOfStation(t.currentStation), t);
            if (at >= 0) {
                return at;
            }
        }
        final int from = this.map.originNodeOf(t);
        final int to = t.destinationStation == null ? -1
                : this.map.nodeOfStation(t.destinationStation);
        final int leg = legIndexOf(from, to);
        if (leg < 0) {
            return -1;
        }
        return this.stops[leg] == from ? leg : leg + 1;
    }

    public int stepTo(int at, int targetNode) {
        if (at < 0 || at >= this.stops.length || targetNode < 0) {
            return -1;
        }
        if (this.stops[at] == targetNode) {
            return at;
        }
        if (at + 1 < this.stops.length && this.stops[at + 1] == targetNode) {
            return at + 1;
        }
        if (at > 0 && this.stops[at - 1] == targetNode) {
            return at - 1;
        }
        return -1;
    }

    public int wrapPartner(int at) {
        final int last = this.stops.length - 1;
        if (last < 1 || this.stops[0] != this.stops[last]) {
            return -1;
        }
        if (at == 0) {
            return last;
        }
        return at == last ? 0 : -1;
    }

    public boolean circular() {
        final int last = this.stops.length - 1;
        return last >= 1 && this.stops[0] == this.stops[last];
    }
}
