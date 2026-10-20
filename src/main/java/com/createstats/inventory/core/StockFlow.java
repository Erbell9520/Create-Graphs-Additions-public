package com.createstats.inventory.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StockFlow {
    public static final String OTHER = "#other";
    public static final int WINDOW_SECONDS = 60;
    public static final int MAX_KEYS = 64;
    public static final int TICKS_A_SECOND = 20;

    private final int maxKeys;
    private Map<String, Long> last;
    private long baselineSecond = Long.MIN_VALUE;
    private long lastSecond = Long.MIN_VALUE;
    private final Map<String, Ring> rings = new LinkedHashMap<>();

    public StockFlow() {
        this(MAX_KEYS);
    }

    public StockFlow(int maxKeys) {
        this.maxKeys = Math.max(1, maxKeys);
    }

    private static final int SLOTS = WINDOW_SECONDS + 1;

    private static final class Ring {
        final long[] in = new long[SLOTS];
        final long[] out = new long[SLOTS];
        final long[] second = new long[SLOTS];
        long lastMoved = Long.MIN_VALUE;

        Ring() {
            java.util.Arrays.fill(second, Long.MIN_VALUE);
        }

        void add(long sec, long dIn, long dOut) {
            final int i = (int) Math.floorMod(sec, (long) SLOTS);
            if (second[i] != sec) {
                second[i] = sec;
                in[i] = 0L;
                out[i] = 0L;
            }
            in[i] += dIn;
            out[i] += dOut;
            lastMoved = sec;
        }

        long sumIn(long from, long to) {
            long s = 0L;
            for (int i = 0; i < SLOTS; i++) {
                if (second[i] >= from && second[i] <= to) {
                    s += in[i];
                }
            }
            return s;
        }

        long sumOut(long from, long to) {
            long s = 0L;
            for (int i = 0; i < SLOTS; i++) {
                if (second[i] >= from && second[i] <= to) {
                    s += out[i];
                }
            }
            return s;
        }
    }

    public void rebaseline() {
        this.last = null;
        this.baselineSecond = Long.MIN_VALUE;
        this.rings.clear();
    }

    public boolean awaitingBaseline() {
        return this.last == null;
    }

    public Map<String, Long> observe(long gameTime, Map<String, Long> counts) {
        final long sec = Math.floorDiv(gameTime, (long) TICKS_A_SECOND);
        this.lastSecond = sec;
        if (this.last == null) {
            this.last = new HashMap<>(counts);
            this.baselineSecond = sec;
            return Collections.emptyMap();
        }
        final Map<String, Long> deltas = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            final long now = e.getValue() == null ? 0L : e.getValue();
            final long before = this.last.getOrDefault(e.getKey(), 0L);
            if (now != before) {
                deltas.put(e.getKey(), now - before);
            }
        }
        for (Map.Entry<String, Long> e : this.last.entrySet()) {
            if (!counts.containsKey(e.getKey()) && e.getValue() != 0L) {
                deltas.put(e.getKey(), -e.getValue());
            }
        }
        this.last = new HashMap<>(counts);
        for (Map.Entry<String, Long> d : deltas.entrySet()) {
            final long v = d.getValue();
            ringFor(d.getKey(), sec).add(sec, Math.max(0L, v), Math.max(0L, -v));
        }
        return deltas;
    }

    private Ring ringFor(String key, long sec) {
        Ring ring = this.rings.get(key);
        if (ring != null) {
            return ring;
        }
        if (namedRings() >= this.maxKeys) {
            this.rings.entrySet().removeIf(e -> !e.getKey().equals(OTHER)
                    && e.getValue().lastMoved < sec - WINDOW_SECONDS);
        }
        final String name = namedRings() < this.maxKeys ? key : OTHER;
        return this.rings.computeIfAbsent(name, k -> new Ring());
    }

    private int namedRings() {
        return this.rings.containsKey(OTHER) ? this.rings.size() - 1 : this.rings.size();
    }

    public int coveredSeconds(long gameTime) {
        if (this.last == null || this.baselineSecond == Long.MIN_VALUE) {
            return 0;
        }
        final long sec = Math.floorDiv(gameTime, (long) TICKS_A_SECOND);

        final long covered = sec - 1L - this.baselineSecond;
        return (int) Math.max(0L, Math.min(WINDOW_SECONDS, covered));
    }

    public boolean gathering(long gameTime) {
        return coveredSeconds(gameTime) < WINDOW_SECONDS;
    }

    public int secondsUntilRates(long gameTime) {
        return WINDOW_SECONDS - coveredSeconds(gameTime);
    }

    public record Rate(double inPerMinute, double outPerMinute) {
        public static final Rate ZERO = new Rate(0.0, 0.0);

        public double net() {
            return inPerMinute - outPerMinute;
        }
    }

    public Rate rate(String key, long gameTime) {
        final Ring ring = this.rings.get(key);
        final int covered = coveredSeconds(gameTime);
        if (ring == null || covered <= 0) {
            return Rate.ZERO;
        }
        final long sec = Math.floorDiv(gameTime, (long) TICKS_A_SECOND);
        final long to = sec - 1L;
        final long from = sec - covered;
        final double scale = WINDOW_SECONDS / (double) covered;
        return new Rate(ring.sumIn(from, to) * scale, ring.sumOut(from, to) * scale);
    }

    public Rate total(long gameTime) {
        double in = 0.0;
        double out = 0.0;
        for (String key : this.rings.keySet()) {
            final Rate r = rate(key, gameTime);
            in += r.inPerMinute();
            out += r.outPerMinute();
        }
        return new Rate(in, out);
    }

    public double net(String key, long gameTime) {
        return rate(key, gameTime).net();
    }

    public record Mover(String key, double perMinute) {
    }

    public List<Mover> top(int n, boolean incoming, long gameTime) {
        final List<Mover> all = new ArrayList<>();
        for (String key : this.rings.keySet()) {
            if (OTHER.equals(key)) {
                continue;
            }
            final Rate r = rate(key, gameTime);
            final double v = incoming ? r.inPerMinute() : r.outPerMinute();
            if (v > 0.0) {
                all.add(new Mover(key, v));
            }
        }
        all.sort((a, b) -> {
            final int c = Double.compare(b.perMinute(), a.perMinute());
            return c != 0 ? c : a.key().compareTo(b.key());
        });
        return all.size() <= n ? all : new ArrayList<>(all.subList(0, n));
    }

    public List<String> movingKeys() {
        return new ArrayList<>(this.rings.keySet());
    }

    public long lastCount(String key) {
        return this.last == null ? 0L : this.last.getOrDefault(key, 0L);
    }

    long lastSecond() {
        return this.lastSecond;
    }

    public boolean tracked(String key) {
        return this.rings.containsKey(key) && !OTHER.equals(key);
    }
}
