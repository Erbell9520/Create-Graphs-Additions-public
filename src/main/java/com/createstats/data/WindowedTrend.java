package com.createstats.data;

import net.minecraft.nbt.CompoundTag;

public final class WindowedTrend {
    public static final int TIER_SAMPLES = 60;

    public enum Window {
        MINUTE("1m", 20L, 60),
        FIVE_MINUTES("5m", 100L, 300),
        HOUR("1h", 1200L, 3600);

        private final String label;
        private final long intervalTicks;
        private final int spanSeconds;

        Window(String label, long intervalTicks, int spanSeconds) {
            this.label = label;
            this.intervalTicks = intervalTicks;
            this.spanSeconds = spanSeconds;
        }

        public String label() {
            return this.label;
        }

        public long intervalTicks() {
            return this.intervalTicks;
        }

        public int spanSeconds() {
            return this.spanSeconds;
        }

        public static Window byOrdinal(int index) {
            final Window[] all = values();
            return all[Math.floorMod(index, all.length)];
        }
    }

    private static final class Tier {
        private final long intervalTicks;
        private final long[] samples = new long[TIER_SAMPLES];
        private int count;
        private int head;
        private long lastTick = Long.MIN_VALUE;

        Tier(long intervalTicks) {
            this.intervalTicks = intervalTicks;
        }

        boolean offer(long value, long gameTick) {
            if (this.lastTick != Long.MIN_VALUE) {
                if (gameTick < this.lastTick) {
                    this.lastTick = gameTick;
                } else if (gameTick - this.lastTick < this.intervalTicks) {
                    return false;
                }
            }
            this.samples[this.head] = value;
            this.head = (this.head + 1) % TIER_SAMPLES;
            if (this.count < TIER_SAMPLES) {
                this.count++;
            }
            this.lastTick = gameTick;
            return true;
        }

        long[] ordered() {
            final long[] out = new long[this.count];
            final int start = (this.head - this.count + TIER_SAMPLES * 2) % TIER_SAMPLES;
            for (int i = 0; i < this.count; i++) {
                out[i] = this.samples[(start + i) % TIER_SAMPLES];
            }
            return out;
        }

        void clear() {
            this.count = 0;
            this.head = 0;
            this.lastTick = Long.MIN_VALUE;
            java.util.Arrays.fill(this.samples, 0L);
        }
    }

    private final Tier[] tiers = {
            new Tier(Window.MINUTE.intervalTicks()),
            new Tier(Window.FIVE_MINUTES.intervalTicks()),
            new Tier(Window.HOUR.intervalTicks())
    };

    private long latest;
    private boolean seeded;

    public void record(long value, long gameTick) {
        this.latest = value;
        this.seeded = true;
        for (final Tier tier : this.tiers) {
            tier.offer(value, gameTick);
        }
    }

    public void clear() {
        for (final Tier tier : this.tiers) {
            tier.clear();
        }
        this.latest = 0L;
        this.seeded = false;
    }

    public long latest() {
        return this.latest;
    }

    public boolean seeded() {
        return this.seeded;
    }

    public int sampleCount(Window window) {
        return this.tiers[window.ordinal()].count;
    }

    public boolean complete(Window window) {
        return this.tiers[window.ordinal()].count >= TIER_SAMPLES;
    }

    public int coverageSeconds(Window window) {
        final int n = sampleCount(window);
        if (n <= 1) {
            return 0;
        }
        return (int) ((n - 1) * window.intervalTicks() / 20L);
    }

    public long[] series(Window window) {
        return this.tiers[window.ordinal()].ordered();
    }

    public float[] plot(Window window) {
        final long[] raw = series(window);
        final float[] out = new float[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = raw[i];
        }
        return out;
    }

    public double mean(Window window) {
        final long[] s = series(window);
        if (s.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (final long v : s) {
            sum += v;
        }
        return sum / s.length;
    }

    public double stdDev(Window window) {
        final long[] s = series(window);
        if (s.length < 2) {
            return 0.0;
        }
        final double mean = mean(window);
        double acc = 0.0;
        for (final long v : s) {
            final double d = v - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / s.length);
    }

    public double relativeStdDev(Window window) {
        final double mean = mean(window);
        return Math.abs(mean) < 1.0e-6 ? 0.0 : stdDev(window) / mean;
    }

    public long delta(Window window) {
        final long[] s = series(window);
        return s.length < 2 ? 0L : s[s.length - 1] - s[0];
    }

    public double perMinute(Window window) {
        final int seconds = coverageSeconds(window);
        if (seconds <= 0) {
            return 0.0;
        }
        return delta(window) * 60.0 / seconds;
    }

    public long min(Window window) {
        final long[] s = series(window);
        if (s.length == 0) {
            return 0L;
        }
        long m = s[0];
        for (final long v : s) {
            m = Math.min(m, v);
        }
        return m;
    }

    public long max(Window window) {
        final long[] s = series(window);
        if (s.length == 0) {
            return 0L;
        }
        long m = s[0];
        for (final long v : s) {
            m = Math.max(m, v);
        }
        return m;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        for (final Window w : Window.values()) {
            final Tier tier = this.tiers[w.ordinal()];
            tag.putLongArray(w.name(), tier.ordered());
            tag.putLong(w.name() + "T", tier.lastTick);
        }
        tag.putLong("Latest", this.latest);
        tag.putBoolean("Seeded", this.seeded);
        return tag;
    }

    public static WindowedTrend load(CompoundTag tag) {
        final WindowedTrend trend = new WindowedTrend();
        if (tag == null) {
            return trend;
        }
        for (final Window w : Window.values()) {
            final long[] ordered = tag.getLongArray(w.name());
            final Tier tier = trend.tiers[w.ordinal()];
            for (final long v : ordered) {
                tier.samples[tier.head] = v;
                tier.head = (tier.head + 1) % TIER_SAMPLES;
                if (tier.count < TIER_SAMPLES) {
                    tier.count++;
                }
            }
            if (tag.contains(w.name() + "T")) {
                tier.lastTick = tag.getLong(w.name() + "T");
            }
        }
        trend.latest = tag.getLong("Latest");
        trend.seeded = tag.getBoolean("Seeded");
        return trend;
    }
}
