package com.createstats.inventory.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BeltPassCounter {
    public record Item(int identity, String key, float position, int count) {
    }

    private static final int SLOTS = StockFlow.WINDOW_SECONDS + 1;

    private Map<Integer, Float> last = new HashMap<>();
    private final Map<String, long[][]> buckets = new LinkedHashMap<>();
    private long startSecond = Long.MIN_VALUE;
    private long lastPass = Long.MIN_VALUE;

    public Map<String, Integer> observe(long gameTime, List<Item> items, float gate, boolean positive) {
        final long sec = Math.floorDiv(gameTime, (long) StockFlow.TICKS_A_SECOND);
        if (this.startSecond == Long.MIN_VALUE) {
            this.startSecond = sec;
        }
        final Map<Integer, Float> now = new HashMap<>();
        final Map<String, Integer> counted = new LinkedHashMap<>();
        for (Item item : items) {
            now.put(item.identity(), item.position());
            final Float before = this.last.get(item.identity());
            if (before == null) {
                continue;
            }
            final boolean crossed = positive
                    ? before < gate && item.position() >= gate
                    : before > gate && item.position() <= gate;
            if (crossed) {
                counted.merge(item.key(), Math.max(1, item.count()), Integer::sum);
            }
        }
        this.last = now;
        for (Map.Entry<String, Integer> e : counted.entrySet()) {
            add(e.getKey(), sec, e.getValue());
            this.lastPass = gameTime;
        }
        return counted;
    }

    private void add(String key, long sec, int n) {
        if (!this.buckets.containsKey(key) && this.buckets.size() >= StockFlow.MAX_KEYS) {
            this.buckets.entrySet().removeIf(e -> {
                for (long s : e.getValue()[1]) {
                    if (s > sec - StockFlow.WINDOW_SECONDS) {
                        return false;
                    }
                }
                return true;
            });
            if (this.buckets.size() >= StockFlow.MAX_KEYS) {
                key = StockFlow.OTHER;
            }
        }
        final long[][] ring = this.buckets.computeIfAbsent(key, k -> {
            final long[][] r = new long[2][SLOTS];
            java.util.Arrays.fill(r[1], Long.MIN_VALUE);
            return r;
        });
        final int i = (int) Math.floorMod(sec, (long) SLOTS);
        if (ring[1][i] != sec) {
            ring[1][i] = sec;
            ring[0][i] = 0L;
        }
        ring[0][i] += n;
    }

    public int coveredSeconds(long gameTime) {
        if (this.startSecond == Long.MIN_VALUE) {
            return 0;
        }
        final long sec = Math.floorDiv(gameTime, (long) StockFlow.TICKS_A_SECOND);
        return (int) Math.max(0L, Math.min(StockFlow.WINDOW_SECONDS, sec - 1L - this.startSecond));
    }

    public boolean gathering(long gameTime) {
        return coveredSeconds(gameTime) < StockFlow.WINDOW_SECONDS;
    }

    public double perMinute(String key, long gameTime) {
        final long[][] ring = this.buckets.get(key);
        final int covered = coveredSeconds(gameTime);
        if (ring == null || covered <= 0) {
            return 0.0;
        }
        final long sec = Math.floorDiv(gameTime, (long) StockFlow.TICKS_A_SECOND);
        long sum = 0L;
        for (int i = 0; i < SLOTS; i++) {
            if (ring[1][i] >= sec - covered && ring[1][i] <= sec - 1L) {
                sum += ring[0][i];
            }
        }
        return sum * (StockFlow.WINDOW_SECONDS / (double) covered);
    }

    public double totalPerMinute(long gameTime) {
        double t = 0.0;
        for (String key : this.buckets.keySet()) {
            t += perMinute(key, gameTime);
        }
        return t;
    }

    public List<StockFlow.Mover> top(int n, long gameTime) {
        final List<StockFlow.Mover> all = new ArrayList<>();
        for (String key : this.buckets.keySet()) {
            if (!StockFlow.OTHER.equals(key)) {
                all.add(new StockFlow.Mover(key, perMinute(key, gameTime)));
            }
        }
        all.sort((a, b) -> {
            final int c = Double.compare(b.perMinute(), a.perMinute());
            return c != 0 ? c : a.key().compareTo(b.key());
        });
        return all.size() <= n ? all : new ArrayList<>(all.subList(0, n));
    }

    public long lastPass() {
        return this.lastPass;
    }

    public void reset() {
        this.last = new HashMap<>();
        this.buckets.clear();
        this.startSecond = Long.MIN_VALUE;
        this.lastPass = Long.MIN_VALUE;
    }
}
