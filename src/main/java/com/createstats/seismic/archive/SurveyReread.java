package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicWave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class SurveyReread {
    public static final long NEVER = Long.MIN_VALUE;

    public static final int RETRY_TICKS = 100;

    public static final int LISTEN_GRACE = 2;

    public static final int MAX_HELD = 32;

    private SurveyReread() {
    }

    public static boolean inReach(double dx, double dy, double dz, double radius) {
        final double d2 = dx * dx + dy * dy + dz * dz;
        return d2 <= radius * radius;
    }

    public static boolean listening(long lastAt, int lastReach, long now) {
        return lastAt != NEVER && now >= lastAt && now - lastAt <= SeismicWave.listenTicks(lastReach) + LISTEN_GRACE;
    }

    public static boolean shouldAsk(long lastAt, int lastHow, boolean listening, long newestHeld, long askedFor,
                                    long askedAt, long now) {
        if (lastAt == NEVER || listening) {
            return false;
        }
        if (askedAt == NEVER || askedFor != lastAt) {
            return true;
        }
        final boolean lacksNewest = lastHow == 0 && newestHeld < lastAt;
        return lacksNewest && now >= askedAt && now - askedAt >= RETRY_TICKS;
    }

    public enum Route {
        DROP,

        JOIN,

        OPEN,

        RESTART
    }

    public static Route route(long heldAt, boolean heldReplayed, long at, boolean replay, boolean opens) {
        final boolean held = heldAt != NEVER;
        if (held && heldAt > at) {
            return Route.DROP;
        }
        final boolean same = held && heldAt == at;
        if (!replay) {
            return same ? Route.JOIN : Route.OPEN;
        }
        if (!opens) {
            return same && heldReplayed ? Route.JOIN : Route.DROP;
        }
        if (same) {
            return heldReplayed ? Route.RESTART : Route.DROP;
        }
        return Route.OPEN;
    }

    public static boolean lacks(String station, long at, Map<String, Long> held) {
        final Long have = held.get(station);
        return have == null || have < at;
    }

    public static <T> List<T> toSend(List<T> record, Function<T, String> station, ToLongFunction<T> at,
                                     Map<String, Long> held) {
        final List<T> out = new ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (final T t : record) {
            final String s = station.apply(t);
            if (seen.add(s) && lacks(s, at.applyAsLong(t), held)) {
                out.add(t);
            }
        }
        return out;
    }

    public static List<int[]> batches(int[] cells, int maxVeins, int maxCells) {
        final List<int[]> out = new ArrayList<>();
        int from = 0;
        int inBatch = 0;
        for (int i = 0; i < cells.length; i++) {
            final int c = Math.max(0, Math.min(maxCells, cells[i]));
            if (i > from && (i - from >= maxVeins || inBatch + c > maxCells)) {
                out.add(new int[] {from, i});
                from = i;
                inBatch = 0;
            }
            inBatch += c;
        }
        if (cells.length > from) {
            out.add(new int[] {from, cells.length});
        }
        return out;
    }

    public static final class Throttle {
        public static final int CAPACITY = 3;
        public static final int REFILL_TICKS = 100;
        public static final int MIN_GAP = 10;

        static final int PRUNE_ABOVE = 256;

        private static final class Bucket {
            int tokens = CAPACITY;
            long refilledAt;
            long lastAsk = NEVER;

            Bucket(long now) {
                this.refilledAt = now;
            }
        }

        private final Map<UUID, Bucket> buckets = new HashMap<>();

        public synchronized boolean allow(UUID player, long now) {
            Bucket b = buckets.get(player);
            if (b == null || now < b.refilledAt || (b.lastAsk != NEVER && now < b.lastAsk)) {
                b = new Bucket(now);
                buckets.put(player, b);
            }
            final long due = (now - b.refilledAt) / REFILL_TICKS;
            if (due > 0) {
                b.tokens = (int) Math.min(CAPACITY, b.tokens + due);
                b.refilledAt = b.tokens >= CAPACITY ? now : b.refilledAt + due * REFILL_TICKS;
            }
            if (b.lastAsk != NEVER && now - b.lastAsk < MIN_GAP) {
                return false;
            }
            if (b.tokens <= 0) {
                return false;
            }
            b.tokens--;
            b.lastAsk = now;
            if (buckets.size() > PRUNE_ABOVE) {
                prune(now);
            }
            return true;
        }

        synchronized void prune(long now) {
            for (final Iterator<Map.Entry<UUID, Bucket>> it = buckets.entrySet().iterator(); it.hasNext(); ) {
                final Bucket b = it.next().getValue();
                final long idle = b.lastAsk == NEVER ? Long.MAX_VALUE : now - b.lastAsk;
                if (idle >= (long) CAPACITY * REFILL_TICKS) {
                    it.remove();
                }
            }
        }

        synchronized int remembered() {
            return buckets.size();
        }
    }
}
