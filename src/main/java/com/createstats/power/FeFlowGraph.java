package com.createstats.power;

import java.util.HashMap;
import java.util.Map;

public final class FeFlowGraph {
    public static final int UNKNOWN = -1;

    private final int[] parent;
    private static final int ROOT = -1;

    private final Map<Long, Integer> indexOf;

    private final Map<Long, Integer> attachmentOwner;

    private final int[] drawn;
    private final int[] supplied;
    private final int[] net;

    private final int[] inflow;
    private final int[] outflow;
    private final int[] through;

    private long computedTick = Long.MIN_VALUE;

    private FeFlowGraph(int[] parent, Map<Long, Integer> indexOf,
                        Map<Long, Integer> attachmentOwner) {
        this.parent = parent;
        this.indexOf = indexOf;
        this.attachmentOwner = attachmentOwner;
        this.drawn = new int[parent.length];
        this.supplied = new int[parent.length];
        this.net = new int[parent.length];
        this.inflow = new int[parent.length];
        this.outflow = new int[parent.length];
        this.through = new int[parent.length];
    }

    public static FeFlowGraph of(Map<Long, Long> parentOf, Map<Long, Long> attachmentOwner) {
        final int n = parentOf.size();
        final int[] parent = new int[n];
        final Map<Long, Integer> indexOf = new HashMap<>(Math.max(4, n * 2));
        int i = 0;
        for (Long key : parentOf.keySet()) {
            indexOf.put(key, i);
            i++;
        }
        i = 0;
        for (Map.Entry<Long, Long> entry : parentOf.entrySet()) {
            final Integer at = indexOf.get(entry.getValue());

            parent[i] = at == null || at >= i ? ROOT : at;
            i++;
        }
        final Map<Long, Integer> owners = new HashMap<>(Math.max(4, attachmentOwner.size() * 2));
        for (Map.Entry<Long, Long> entry : attachmentOwner.entrySet()) {
            final Integer owner = indexOf.get(entry.getValue());
            if (owner != null) {
                owners.put(entry.getKey(), owner);
            }
        }
        return new FeFlowGraph(parent, indexOf, owners);
    }

    public int size() {
        return parent.length;
    }

    public void recompute(Map<Long, Integer> signedFlow, Map<Long, Integer> extraSeeds, long now) {
        recompute(signedFlow, extraSeeds, NO_BOUNDARY, 0, now);
    }

    public static final long NO_BOUNDARY = Long.MIN_VALUE;

    public void recompute(Map<Long, Integer> signedFlow, Map<Long, Integer> extraSeeds,
                          long boundaryKey, int boundaryFlow, long now) {
        recompute(signedFlow, extraSeeds, boundaryKey, boundaryFlow, NO_MOVES, NO_MOVES, NO_FE, 0, now);
    }

    private static final long[] NO_MOVES = new long[0];
    private static final int[] NO_FE = new int[0];

    public void recompute(Map<Long, Integer> signedFlow, Map<Long, Integer> extraSeeds,
                          long boundaryKey, int boundaryFlow,
                          long[] moveFrom, long[] moveTo, int[] moveFe, int moves, long now) {
        java.util.Arrays.fill(drawn, 0);
        java.util.Arrays.fill(supplied, 0);
        applySeeds(signedFlow);
        applySeeds(extraSeeds);
        for (int m = 0; m < moves; m++) {
            final int from = ownerIndexOf(moveFrom[m]);
            final int to = ownerIndexOf(moveTo[m]);
            if (from < 0 || to < 0 || from == to || moveFe[m] <= 0) {
                continue;
            }

            final int fe = Math.min(moveFe[m], drawn[from]);
            drawn[from] -= fe;
            drawn[to] += fe;
        }

        for (int i = 0; i < net.length; i++) {
            net[i] = drawn[i] - supplied[i];

            inflow[i] = supplied[i];
            outflow[i] = drawn[i];
        }

        for (int i = net.length - 1; i > 0; i--) {
            if (parent[i] != ROOT) {
                net[parent[i]] += net[i];
            }
        }

        for (int i = 0; i < net.length; i++) {
            if (parent[i] == ROOT || net[i] == 0) {
                continue;
            }

            final int edge = Math.abs(net[i]);
            if (net[i] > 0) {
                inflow[i] += edge;
                outflow[parent[i]] += edge;
            } else {
                outflow[i] += edge;
                inflow[parent[i]] += edge;
            }
        }
        for (int i = 0; i < net.length; i++) {
            through[i] = Math.max(inflow[i], outflow[i]);
        }
        if (boundaryKey != NO_BOUNDARY) {
            final Integer at = indexOf.get(boundaryKey);
            if (at != null) {
                through[at] = Math.max(through[at], Math.abs(boundaryFlow));
            }
        }
        computedTick = now;
    }

    private void applySeeds(Map<Long, Integer> flows) {
        if (flows.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Integer> entry : flows.entrySet()) {
            final int at = ownerIndexOf(entry.getKey());
            if (at < 0) {
                continue;
            }

            final int fe = entry.getValue();
            if (fe >= 0) {
                drawn[at] += fe;
            } else {
                supplied[at] -= fe;
            }
        }
    }

    private int ownerIndexOf(long key) {
        final Integer direct = indexOf.get(key);
        if (direct != null) {
            return direct;
        }
        final Integer owner = attachmentOwner.get(key);
        return owner != null ? owner : -1;
    }

    public int flowThrough(long nodeKey, long now) {
        if (computedTick == Long.MIN_VALUE) {
            return UNKNOWN;
        }
        final long age = now - computedTick;
        if (age < 0 || age > 1) {
            return UNKNOWN;
        }
        final Integer at = indexOf.get(nodeKey);
        return at == null ? UNKNOWN : through[at];
    }

    public int edgeFlow(long a, long b, long now) {
        if (computedTick == Long.MIN_VALUE) {
            return UNKNOWN;
        }
        final long age = now - computedTick;
        if (age < 0 || age > 1) {
            return UNKNOWN;
        }
        final Integer ia = indexOf.get(a);
        final Integer ib = indexOf.get(b);
        if (ia == null || ib == null) {
            return UNKNOWN;
        }
        if (parent[ib] == ia) {
            return Math.abs(net[ib]);
        }
        if (parent[ia] == ib) {
            return Math.abs(net[ia]);
        }
        return UNKNOWN;
    }
}
