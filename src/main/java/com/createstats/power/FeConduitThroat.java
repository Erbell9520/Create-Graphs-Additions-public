package com.createstats.power;

import java.util.HashMap;
import java.util.Map;

public final class FeConduitThroat {
    public static final FeConduitThroat NONE =
            new FeConduitThroat(new long[0], new int[0], new int[0], new int[0], new int[0]);

    private final long[] keys;

    private final int[] parent;

    private final int[] edgeRate;

    private final int[] nodeRate;
    private final int[] suppliers;
    private final int[] receivers;

    private final int[] feedSink;
    private final int[] feedFar;

    private final Map<Long, Integer> byAnchor = new HashMap<>(4);
    private final int ceiling;

    public FeConduitThroat(long[] keys, int[] parent, int[] edgeRate,
                           int[] suppliers, int[] receivers) {
        this(keys, parent, edgeRate, null, suppliers, receivers);
    }

    public FeConduitThroat(long[] keys, int[] parent, int[] edgeRate,
                           @org.jetbrains.annotations.Nullable int[] nodeRate,
                           int[] suppliers, int[] receivers) {
        this(keys, parent, edgeRate, nodeRate, suppliers, receivers, null);
    }

    public FeConduitThroat(long[] keys, int[] parent, int[] edgeRate,
                           @org.jetbrains.annotations.Nullable int[] nodeRate,
                           int[] suppliers, int[] receivers,
                           @org.jetbrains.annotations.Nullable java.util.List<FeFeeds> feeds) {
        this.keys = keys;
        this.parent = parent;
        this.edgeRate = edgeRate;
        this.nodeRate = new int[parent.length];
        java.util.Arrays.fill(this.nodeRate, FeGridSolver.NO_CEILING);
        if (nodeRate != null) {
            for (int i = 0; i < parent.length && i < nodeRate.length; i++) {
                this.nodeRate[i] = nodeRate[i] >= FeGridSolver.NO_CEILING
                        ? FeGridSolver.NO_CEILING : Math.max(0, nodeRate[i]);
            }
        }
        this.suppliers = suppliers;
        this.receivers = receivers;

        int pairs = 0;
        final int[] sinkAt = new int[countSpans(feeds)];
        final int[] farAt = new int[sinkAt.length];
        if (feeds != null) {
            for (FeFeeds sink : feeds) {
                final int at = indexOf(sink.sink());
                if (at < 0) {
                    continue;
                }
                for (long from : sink.from()) {
                    final int far = indexOf(from);
                    if (far < 0 || far == at) {
                        continue;
                    }
                    sinkAt[pairs] = at;
                    farAt[pairs] = far;
                    pairs++;
                }
            }
        }
        this.feedSink = java.util.Arrays.copyOf(sinkAt, pairs);
        this.feedFar = java.util.Arrays.copyOf(farAt, pairs);
        this.ceiling = search(-1);
    }

    private static int countSpans(@org.jetbrains.annotations.Nullable java.util.List<FeFeeds> feeds) {
        int count = 0;
        if (feeds != null) {
            for (FeFeeds sink : feeds) {
                count += sink.count();
            }
        }
        return count;
    }

    public int ceiling() {
        return ceiling;
    }

    public int ceilingFrom(long anchorKey) {
        final int at = indexOf(anchorKey);
        if (at < 0) {
            return ceiling;
        }
        final Integer cached = byAnchor.get(anchorKey);
        if (cached != null) {
            return cached;
        }
        final int found = search(at);
        byAnchor.put(anchorKey, found);
        return found;
    }

    private int indexOf(long key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    private int search(int extraSupplier) {
        final int n = parent.length;
        if (n == 0) {
            return FeGridSolver.NO_CEILING;
        }
        final int[] below = suppliers.clone();
        final int[] sinks = receivers.clone();
        if (extraSupplier >= 0) {
            below[extraSupplier]++;
        }

        final int[] ownSuppliers = below.clone();
        final int[] ownReceivers = sinks.clone();
        int totalSuppliers = 0;
        int totalReceivers = 0;
        for (int i = 0; i < n; i++) {
            totalSuppliers += below[i];
            totalReceivers += sinks[i];
        }
        if (totalSuppliers == 0 || totalReceivers == 0) {
            return FeGridSolver.NO_CEILING;
        }
        for (int i = n - 1; i > 0; i--) {
            if (parent[i] >= 0) {
                below[parent[i]] += below[i];
                sinks[parent[i]] += sinks[i];
            }
        }

        final boolean[] onLoop = feedSink.length == 0 ? null : edgesOnFeedLoops();
        int found = FeGridSolver.NO_CEILING;
        for (int i = 0; i < n; i++) {
            if (parent[i] < 0 || edgeRate[i] >= FeGridSolver.NO_CEILING) {
                continue;
            }

            if (onLoop != null && onLoop[i]) {
                continue;
            }
            final int restSuppliers = totalSuppliers - below[i];
            final int restReceivers = totalReceivers - sinks[i];
            final boolean allSuppliersBelow = below[i] == totalSuppliers && restSuppliers == 0;
            final boolean allReceiversBelow = sinks[i] == totalReceivers && restReceivers == 0;
            if ((allSuppliersBelow && sinks[i] == 0 && restReceivers > 0)
                    || (allReceiversBelow && below[i] == 0 && restSuppliers > 0)) {
                found = Math.min(found, edgeRate[i]);
            }
        }
        return Math.min(found, onLoop == null
                ? nodeThroat(below, sinks, ownSuppliers, ownReceivers, totalSuppliers, totalReceivers)
                : nodeThroatAcrossFeeds(below, sinks, ownSuppliers, ownReceivers, totalSuppliers, totalReceivers));
    }

    private boolean[] edgesOnFeedLoops() {
        final int n = parent.length;
        final int[] depth = new int[n];
        for (int i = 0; i < n; i++) {
            depth[i] = parent[i] >= 0 ? depth[parent[i]] + 1 : 0;
        }
        final boolean[] onLoop = new boolean[n];
        final int[] path = new int[2 * n + 2];
        for (int k = 0; k < feedSink.length; k++) {
            int a = feedSink[k];
            int b = feedFar[k];
            int length = 0;
            while (a != b && length < path.length) {
                if (depth[a] >= depth[b]) {
                    if (parent[a] < 0) {
                        break;
                    }
                    path[length++] = a;
                    a = parent[a];
                } else {
                    if (parent[b] < 0) {
                        break;
                    }
                    path[length++] = b;
                    b = parent[b];
                }
            }
            if (a != b) {
                continue;
            }
            for (int j = 0; j < length; j++) {
                onLoop[path[j]] = true;
            }
        }
        return onLoop;
    }

    private int nodeThroatAcrossFeeds(int[] below, int[] sinks, int[] ownSuppliers, int[] ownReceivers,
                                      int totalSuppliers, int totalReceivers) {
        final int n = parent.length;
        int found = FeGridSolver.NO_CEILING;

        final int[] group = new int[n + 1];
        for (int v = 0; v < n; v++) {
            if (nodeRate[v] >= FeGridSolver.NO_CEILING) {
                continue;
            }
            final boolean crosses = totalSuppliers - ownSuppliers[v] > 0
                    || totalReceivers - ownReceivers[v] > 0;
            if (!crosses) {
                continue;
            }
            for (int i = 0; i <= n; i++) {
                group[i] = i;
            }
            for (int k = 0; k < feedSink.length; k++) {
                if (feedSink[k] == v || feedFar[k] == v) {
                    continue;
                }
                union(group, pieceOf(feedSink[k], v), pieceOf(feedFar[k], v));
            }

            final int[] groupSuppliers = new int[n + 1];
            final int[] groupReceivers = new int[n + 1];
            for (int c = 0; c < n; c++) {
                if (parent[c] == v) {
                    final int root = find(group, c);
                    groupSuppliers[root] += below[c];
                    groupReceivers[root] += sinks[c];
                }
            }
            if (parent[v] >= 0) {
                final int root = find(group, n);
                groupSuppliers[root] += totalSuppliers - below[v];
                groupReceivers[root] += totalReceivers - sinks[v];
            }
            boolean mixed = false;
            for (int g = 0; g <= n && !mixed; g++) {
                mixed = groupSuppliers[g] > 0 && groupReceivers[g] > 0;
            }
            if (!mixed) {
                found = Math.min(found, nodeRate[v]);
            }
        }
        return found;
    }

    private int pieceOf(int x, int v) {
        int at = x;
        int steps = 0;
        while (parent[at] >= 0 && parent[at] != v && steps++ <= parent.length) {
            at = parent[at];
        }
        return parent[at] == v ? at : parent.length;
    }

    private static int find(int[] group, int x) {
        int at = x;
        while (group[at] != at) {
            group[at] = group[group[at]];
            at = group[at];
        }
        return at;
    }

    private static void union(int[] group, int a, int b) {
        final int ra = find(group, a);
        final int rb = find(group, b);
        if (ra != rb) {
            group[ra] = rb;
        }
    }

    private int nodeThroat(int[] below, int[] sinks, int[] ownSuppliers, int[] ownReceivers,
                           int totalSuppliers, int totalReceivers) {
        final int n = parent.length;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                any = true;
                break;
            }
        }
        if (!any) {
            return FeGridSolver.NO_CEILING;
        }

        final boolean[] mixedChild = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (parent[i] >= 0 && below[i] > 0 && sinks[i] > 0) {
                mixedChild[parent[i]] = true;
            }
        }
        int found = FeGridSolver.NO_CEILING;
        for (int i = 0; i < n; i++) {
            if (nodeRate[i] >= FeGridSolver.NO_CEILING || mixedChild[i]) {
                continue;
            }
            final int aboveSuppliers = totalSuppliers - below[i];
            final int aboveReceivers = totalReceivers - sinks[i];
            if (aboveSuppliers > 0 && aboveReceivers > 0) {
                continue;
            }
            final boolean crosses = totalSuppliers - ownSuppliers[i] > 0
                    || totalReceivers - ownReceivers[i] > 0;
            if (crosses) {
                found = Math.min(found, nodeRate[i]);
            }
        }
        return found;
    }
}
