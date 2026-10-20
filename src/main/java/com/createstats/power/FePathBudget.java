package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.HashMap;
import java.util.Map;

// pooled solve has no paths, so cables in between were never asked. per edge budget on the flood tree. the breaker lesson
public final class FePathBudget {
    public static final FePathBudget NONE =
            new FePathBudget(new long[0], new int[0], new int[0], new boolean[0]);

    private final long[] keys;
    private final Map<Long, Integer> indexOf;

    private final int[] up;

    private final int[] upRate;

    private final int[] spent;

    private final int[] nodeRate;

    private final int[] nodeSpent;

    private final int[][] feedFrom;

    private final int[][] feedRate;

    private final int[][] feedSpent;

    private final int feedSpans;

    private boolean[] reached = new boolean[0];

    private final int[] parent;
    private final int[] edgeRateToParent;
    private long rootedOn = Long.MIN_VALUE;

    private long tick = Long.MIN_VALUE;

    public FePathBudget(long[] keys, int[] parent, int[] edgeRateToParent, boolean[] supplier) {
        this(keys, parent, edgeRateToParent, supplier, null);
    }

    public FePathBudget(long[] keys, int[] parent, int[] edgeRateToParent, boolean[] supplier,
                        @org.jetbrains.annotations.Nullable int[] nodeRate) {
        this(keys, parent, edgeRateToParent, supplier, nodeRate, null);
    }

    public FePathBudget(long[] keys, int[] parent, int[] edgeRateToParent, boolean[] supplier,
                        @org.jetbrains.annotations.Nullable int[] nodeRate,
                        @org.jetbrains.annotations.Nullable java.util.List<FeFeeds> feeds) {
        this.keys = keys;
        this.indexOf = new HashMap<>(Math.max(4, keys.length * 2));
        for (int i = 0; i < keys.length; i++) {
            indexOf.put(keys[i], i);
        }
        final int n = parent.length;
        this.up = new int[n];
        this.upRate = new int[n];
        this.spent = new int[n];
        this.nodeRate = new int[n];
        this.nodeSpent = new int[n];
        this.parent = parent;
        this.edgeRateToParent = edgeRateToParent;
        java.util.Arrays.fill(up, -1);
        java.util.Arrays.fill(upRate, FeGridSolver.NO_CEILING);
        java.util.Arrays.fill(this.nodeRate, FeGridSolver.NO_CEILING);
        if (nodeRate != null) {
            for (int i = 0; i < n && i < nodeRate.length; i++) {
                this.nodeRate[i] = nodeRate[i] >= FeGridSolver.NO_CEILING
                        ? FeGridSolver.NO_CEILING : Math.max(0, nodeRate[i]);
            }
        }

        final boolean any = feeds != null && !feeds.isEmpty();
        this.feedFrom = new int[any ? n : 0][];
        this.feedRate = new int[any ? n : 0][];
        this.feedSpent = new int[any ? n : 0][];
        int spans = 0;
        if (any) {
            for (FeFeeds sink : feeds) {
                final Integer at = indexOf.get(sink.sink());
                if (at == null || sink.count() == 0) {
                    continue;
                }

                int kept = 0;
                final int[] from = new int[sink.count()];
                final int[] rate = new int[sink.count()];
                for (int j = 0; j < sink.count(); j++) {
                    final Integer far = indexOf.get(sink.from()[j]);
                    if (far == null || far.intValue() == at.intValue()) {
                        continue;
                    }
                    from[kept] = far;
                    rate[kept] = sink.rate()[j] >= FeGridSolver.NO_CEILING
                            ? FeGridSolver.NO_CEILING : Math.max(0, sink.rate()[j]);
                    kept++;
                }
                if (kept == 0) {
                    continue;
                }
                feedFrom[at] = java.util.Arrays.copyOf(from, kept);
                feedRate[at] = java.util.Arrays.copyOf(rate, kept);
                feedSpent[at] = new int[kept];
                spans += kept;
            }
        }
        this.feedSpans = spans;
        reroot(parent, edgeRateToParent, supplier);
    }

    // root on the driver, the panel isn't on its own leg
    public void rootOn(long anchorKey) {
        if (anchorKey == rootedOn) {
            return;
        }
        final Integer at = indexOf.get(anchorKey);
        if (at == null) {
            return;
        }
        rootedOn = anchorKey;
        final boolean[] seeds = new boolean[Math.max(1, parent.length)];
        seeds[at] = true;
        java.util.Arrays.fill(up, -1);
        java.util.Arrays.fill(upRate, FeGridSolver.NO_CEILING);
        java.util.Arrays.fill(spent, 0);
        java.util.Arrays.fill(nodeSpent, 0);
        clearFeeds();
        reroot(parent, edgeRateToParent, seeds);
    }

    public boolean hasGradedEdges() {
        for (int i = 0; i < parent.length; i++) {
            if (parent[i] >= 0 && edgeRateToParent[i] < FeGridSolver.NO_CEILING) {
                return true;
            }
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                return true;
            }
        }
        return hasGradedFeed();
    }

    private boolean hasGradedFeed() {
        if (feedSpans == 0) {
            return false;
        }
        for (int[] rates : feedRate) {
            if (rates == null) {
                continue;
            }
            for (int rate : rates) {
                if (rate < FeGridSolver.NO_CEILING) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reroot(int[] parent, int[] edgeRateToParent, boolean[] supplier) {
        final int n = parent.length;
        if (n == 0) {
            return;
        }
        final int[] firstChild = new int[n];
        final int[] nextSibling = new int[n];
        java.util.Arrays.fill(firstChild, -1);
        for (int i = 0; i < n; i++) {
            if (parent[i] >= 0) {
                nextSibling[i] = firstChild[parent[i]];
                firstChild[parent[i]] = i;
            }
        }
        final boolean[] seen = new boolean[n];
        this.reached = seen;
        final int[] queue = new int[n];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < n && i < supplier.length; i++) {
            if (supplier[i]) {
                seen[i] = true;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            final int at = queue[head++];

            final int over = parent[at];
            if (over >= 0 && !seen[over]) {
                seen[over] = true;
                up[over] = at;
                upRate[over] = edgeRateToParent[at];
                queue[tail++] = over;
            }
            for (int child = firstChild[at]; child >= 0; child = nextSibling[child]) {
                if (!seen[child]) {
                    seen[child] = true;
                    up[child] = at;
                    upRate[child] = edgeRateToParent[child];
                    queue[tail++] = child;
                }
            }
        }
    }

    public int deliveredThrough(long sinkKey, long fromKey, long now) {
        final Integer at = indexOf.get(sinkKey);
        final Integer far = indexOf.get(fromKey);
        if (at == null || far == null || !isParallel(at) || now != tick) {
            return 0;
        }
        int total = 0;
        for (int j = 0; j < feedFrom[at].length; j++) {
            if (feedFrom[at][j] == far) {
                total = FeRates.plus(total, feedSpent[at][j]);
            }
        }
        return total;
    }

    public int feedSpanCount() {
        return feedSpans;
    }

    public int movesInto(long now, long[] from, long[] to, int[] fe) {
        if (feedSpans == 0 || now != tick) {
            return 0;
        }
        int count = 0;
        for (int at = 0; at < feedFrom.length; at++) {
            if (feedFrom[at] == null) {
                continue;
            }
            for (int j = 0; j < feedFrom[at].length; j++) {
                if (feedSpent[at][j] > 0 && count < from.length) {
                    from[count] = keys[at];
                    to[count] = keys[feedFrom[at][j]];
                    fe[count] = feedSpent[at][j];
                    count++;
                }
            }
        }
        return count;
    }

    public boolean isEmpty() {
        for (int i = 0; i < up.length; i++) {
            if (up[i] >= 0 && upRate[i] < FeGridSolver.NO_CEILING) {
                return false;
            }
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                return false;
            }
        }
        return !hasGradedFeed();
    }

    public int remaining(long nodeKey, long now) {
        final Integer at = indexOf.get(nodeKey);
        if (at == null) {
            return FeGridSolver.NO_CEILING;
        }
        roll(now);
        if (isParallel(at)) {
            return parallelRoom(at);
        }
        int room = FeGridSolver.NO_CEILING;
        int steps = 0;
        for (int i = at; i >= 0 && steps <= up.length; i = up[i], steps++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                room = Math.min(room, Math.max(0, nodeRate[i] - nodeSpent[i]));
                if (room == 0) {
                    return 0;
                }
            }
            if (up[i] < 0 || upRate[i] >= FeGridSolver.NO_CEILING) {
                continue;
            }
            room = Math.min(room, Math.max(0, upRate[i] - spent[i]));
            if (room == 0) {
                return 0;
            }
        }
        return room;
    }

    public void spend(long nodeKey, int fe, long now) {
        if (fe <= 0) {
            return;
        }
        final Integer at = indexOf.get(nodeKey);
        if (at == null) {
            return;
        }
        roll(now);
        if (isParallel(at)) {
            spendParallel(at, fe);
            return;
        }
        int steps = 0;
        for (int i = at; i >= 0 && steps <= up.length; i = up[i], steps++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                nodeSpent[i] = FeRates.plus(nodeSpent[i], fe);
            }
            if (up[i] >= 0 && upRate[i] < FeGridSolver.NO_CEILING) {
                spent[i] = FeRates.plus(spent[i], fe);
            }
        }
    }

    private boolean isParallel(int at) {
        return at < feedFrom.length && feedFrom[at] != null;
    }

    private int[] scratchEdge;
    private int[] scratchNode;
    private int[] scratchEdgeGen;
    private int[] scratchNodeGen;
    private int scratchGen;

    private int parallelRoom(int at) {
        final int body = nodeRate[at] >= FeGridSolver.NO_CEILING
                ? FeGridSolver.NO_CEILING : Math.max(0, nodeRate[at] - nodeSpent[at]);
        if (body == 0) {
            return 0;
        }
        if (at >= reached.length || !reached[at] || up[at] < 0) {
            return body;
        }
        beginScratch();
        long total = 0;
        final int tree = claim(edgeRoom(at), up[at]);
        if (tree >= FeGridSolver.NO_CEILING) {
            return body;
        }
        total += tree;
        for (int j = 0; j < feedFrom[at].length; j++) {
            final int far = feedFrom[at][j];
            if (far >= reached.length || !reached[far]) {
                continue;
            }
            final int span = feedRate[at][j] >= FeGridSolver.NO_CEILING
                    ? FeGridSolver.NO_CEILING : Math.max(0, feedRate[at][j] - feedSpent[at][j]);
            final int room = claim(span, far);
            if (room >= FeGridSolver.NO_CEILING) {
                return body;
            }
            total += room;
        }
        return (int) Math.min(body, Math.min(total, FeGridSolver.NO_CEILING));
    }

    private int edgeRoom(int at) {
        return upRate[at] >= FeGridSolver.NO_CEILING
                ? FeGridSolver.NO_CEILING : Math.max(0, upRate[at] - spent[at]);
    }

    private int claim(int spanRoom, int far) {
        int room = spanRoom;
        int steps = 0;
        for (int i = far; i >= 0 && steps <= up.length && room > 0; i = up[i], steps++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                room = Math.min(room, Math.max(0, nodeRate[i] - nodeSpent[i] - scratch(scratchNode, scratchNodeGen, i)));
            }
            if (up[i] >= 0 && upRate[i] < FeGridSolver.NO_CEILING) {
                room = Math.min(room, Math.max(0, upRate[i] - spent[i] - scratch(scratchEdge, scratchEdgeGen, i)));
            }
        }
        if (room <= 0 || room >= FeGridSolver.NO_CEILING) {
            return Math.max(0, room);
        }
        steps = 0;
        for (int i = far; i >= 0 && steps <= up.length; i = up[i], steps++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                addScratch(scratchNode, scratchNodeGen, i, room);
            }
            if (up[i] >= 0 && upRate[i] < FeGridSolver.NO_CEILING) {
                addScratch(scratchEdge, scratchEdgeGen, i, room);
            }
        }
        return room;
    }

    private void beginScratch() {
        if (scratchEdge == null) {
            final int n = up.length;
            scratchEdge = new int[n];
            scratchNode = new int[n];
            scratchEdgeGen = new int[n];
            scratchNodeGen = new int[n];
            scratchGen = 0;
        }
        if (scratchGen == Integer.MAX_VALUE) {
            java.util.Arrays.fill(scratchEdgeGen, 0);
            java.util.Arrays.fill(scratchNodeGen, 0);
            scratchGen = 0;
        }
        scratchGen++;
    }

    private int scratch(int[] value, int[] gen, int i) {
        return gen[i] == scratchGen ? value[i] : 0;
    }

    private void addScratch(int[] value, int[] gen, int i, int fe) {
        if (gen[i] != scratchGen) {
            gen[i] = scratchGen;
            value[i] = 0;
        }
        value[i] = FeRates.plus(value[i], fe);
    }

    private void spendParallel(int at, int fe) {
        if (nodeRate[at] < FeGridSolver.NO_CEILING) {
            nodeSpent[at] = FeRates.plus(nodeSpent[at], fe);
        }
        if (at >= reached.length || !reached[at] || up[at] < 0) {
            return;
        }
        int left = fe;
        int part = take(left, Math.min(edgeRoom(at), pathRoom(up[at])));
        bookTree(at, part);
        left -= part;
        for (int j = 0; j < feedFrom[at].length && left > 0; j++) {
            final int far = feedFrom[at][j];
            if (far >= reached.length || !reached[far]) {
                continue;
            }
            final int span = feedRate[at][j] >= FeGridSolver.NO_CEILING
                    ? FeGridSolver.NO_CEILING : Math.max(0, feedRate[at][j] - feedSpent[at][j]);
            part = take(left, Math.min(span, pathRoom(far)));
            if (part <= 0) {
                continue;
            }
            feedSpent[at][j] = FeRates.plus(feedSpent[at][j], part);
            bookPath(far, part);
            left -= part;
        }
        if (left > 0) {
            bookTree(at, left);
        }
    }

    private static int take(int left, int room) {
        return room >= FeGridSolver.NO_CEILING ? left : Math.max(0, Math.min(left, room));
    }

    private void bookTree(int at, int fe) {
        if (fe <= 0) {
            return;
        }
        if (upRate[at] < FeGridSolver.NO_CEILING) {
            spent[at] = FeRates.plus(spent[at], fe);
        }
        bookPath(up[at], fe);
    }

    private int pathRoom(int far) {
        int room = FeGridSolver.NO_CEILING;
        int steps = 0;
        for (int i = far; i >= 0 && steps <= up.length; i = up[i], steps++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                room = Math.min(room, Math.max(0, nodeRate[i] - nodeSpent[i]));
            }
            if (up[i] >= 0 && upRate[i] < FeGridSolver.NO_CEILING) {
                room = Math.min(room, Math.max(0, upRate[i] - spent[i]));
            }
        }
        return room;
    }

    private void bookPath(int far, int fe) {
        int steps = 0;
        for (int i = far; i >= 0 && steps <= up.length; i = up[i], steps++) {
            if (nodeRate[i] < FeGridSolver.NO_CEILING) {
                nodeSpent[i] = FeRates.plus(nodeSpent[i], fe);
            }
            if (up[i] >= 0 && upRate[i] < FeGridSolver.NO_CEILING) {
                spent[i] = FeRates.plus(spent[i], fe);
            }
        }
    }

    private void clearFeeds() {
        if (feedSpans == 0) {
            return;
        }
        for (int[] spends : feedSpent) {
            if (spends != null) {
                java.util.Arrays.fill(spends, 0);
            }
        }
    }

    private void roll(long now) {
        if (now != tick) {
            tick = now;
            java.util.Arrays.fill(spent, 0);
            java.util.Arrays.fill(nodeSpent, 0);
            clearFeeds();
        }
    }

    public record Throttled(IEnergyStorage inner, FePathBudget budget, long nodeKey,
                            java.util.function.LongSupplier clock) implements IEnergyStorage {
        public static Throttled of(IEnergyStorage inner, FePathBudget budget, long nodeKey,
                                   net.minecraft.world.level.Level level) {
            return new Throttled(inner, budget, nodeKey, level::getGameTime);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final long now = clock.getAsLong();
            final int allowed = Math.min(maxReceive, budget.remaining(nodeKey, now));
            if (allowed <= 0) {
                return 0;
            }
            final int moved = inner.receiveEnergy(allowed, simulate);
            if (!simulate) {
                budget.spend(nodeKey, moved, now);
            }
            return moved;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return inner.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return inner.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return inner.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return inner.canExtract();
        }

        @Override
        public boolean canReceive() {
            return inner.canReceive();
        }
    }
}
