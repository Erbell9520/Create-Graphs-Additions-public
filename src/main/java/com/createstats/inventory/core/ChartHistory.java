package com.createstats.inventory.core;

import java.util.Arrays;
import java.util.OptionalInt;

public final class ChartHistory {
    public static final int PERIOD_TICKS = 200;
    public static final int CAPACITY = 360;
    public static final int STEPS_A_DAY = 120;

    public static final int MINUTE_SAMPLES = 6;

    public static final int NIGHT_FROM = 65;
    public static final int NIGHT_TO = 115;

    private final int[] values = new int[CAPACITY];
    private final int[] stamps = new int[CAPACITY];
    private final boolean[] gaps = new boolean[CAPACITY];

    private int start;
    private int size;
    private long lastPeriod = Long.MIN_VALUE;

    public static long periodOf(long gameTime) {
        return Math.floorDiv(gameTime, (long) PERIOD_TICKS);
    }

    public static boolean isSampleTick(long gameTime) {
        return Math.floorMod(gameTime, (long) PERIOD_TICKS) == 0L;
    }

    public static int stampOf(long dayTime) {
        return (int) Math.floorDiv(dayTime, (long) PERIOD_TICKS);
    }

    public static int day(int stamp) {
        return Math.floorDiv(stamp, STEPS_A_DAY);
    }

    public static int step(int stamp) {
        return Math.floorMod(stamp, STEPS_A_DAY);
    }

    public static int hour(int stamp) {
        return (6 + step(stamp) / 5) % 24;
    }

    public static int minute(int stamp) {
        return (step(stamp) % 5) * 12;
    }

    public static boolean night(int stamp) {
        final int s = step(stamp);
        return s >= NIGHT_FROM && s < NIGHT_TO;
    }

    public static boolean jump(int previousStamp, int stamp) {
        final int d = stamp - previousStamp;
        return d < 0 || d > 1;
    }

    public void record(long period, int stamp, int value) {
        append(period, stamp, value, false);
    }

    public void recordGap(long period, int stamp) {
        append(period, stamp, 0, true);
    }

    private void append(long period, int stamp, int value, boolean gap) {
        if (this.lastPeriod != Long.MIN_VALUE) {
            if (period <= this.lastPeriod) {
                return;
            }
            final long missed = period - this.lastPeriod - 1L;
            if (missed > 0L) {
                final long keep = Math.min(missed, CAPACITY - 1L);
                for (long i = keep; i >= 1L; i--) {
                    push((int) (stamp - i), 0, true);
                }
            }
        }
        push(stamp, value, gap);
        this.lastPeriod = period;
    }

    private void push(int stamp, int value, boolean gap) {
        final int at;
        if (this.size < CAPACITY) {
            at = (this.start + this.size) % CAPACITY;
            this.size++;
        } else {
            at = this.start;
            this.start = (this.start + 1) % CAPACITY;
        }
        this.values[at] = value;
        this.stamps[at] = stamp;
        this.gaps[at] = gap;
    }

    public int size() {
        return this.size;
    }

    public long lastPeriod() {
        return this.lastPeriod;
    }

    private int at(int k) {
        if (k < 0 || k >= this.size) {
            throw new IndexOutOfBoundsException(k + " of " + this.size);
        }
        return (this.start + k) % CAPACITY;
    }

    public int value(int k) {
        return this.values[at(k)];
    }

    public int stamp(int k) {
        return this.stamps[at(k)];
    }

    public boolean gap(int k) {
        return this.gaps[at(k)];
    }

    public OptionalInt newestValue() {
        for (int k = this.size - 1; k >= 0; k--) {
            if (!gap(k)) {
                return OptionalInt.of(value(k));
            }
        }
        return OptionalInt.empty();
    }

    public void clear() {
        this.start = 0;
        this.size = 0;
        this.lastPeriod = Long.MIN_VALUE;
    }

    public record Reading(int day, int hour, int minute, boolean clockJumped, boolean gap, int value,
                          boolean hasNet, int netPerMinute) {
    }

    public Reading reading(int k) {
        final int stamp = stamp(k);
        final boolean jumped = k > 0 && jump(stamp(k - 1), stamp);
        if (gap(k)) {
            return new Reading(day(stamp), hour(stamp), minute(stamp), jumped, true, 0, false, 0);
        }
        boolean whole = k >= MINUTE_SAMPLES;
        for (int j = k - MINUTE_SAMPLES; whole && j <= k; j++) {
            if (gap(j)) {
                whole = false;
            }
        }
        final int net = whole ? value(k) - value(k - MINUTE_SAMPLES) : 0;
        return new Reading(day(stamp), hour(stamp), minute(stamp), jumped, false, value(k), whole, net);
    }

    public int[] exportValues() {
        final int[] out = new int[this.size];
        for (int k = 0; k < this.size; k++) {
            out[k] = value(k);
        }
        return out;
    }

    public int[] exportStamps() {
        final int[] out = new int[this.size];
        for (int k = 0; k < this.size; k++) {
            out[k] = stamp(k);
        }
        return out;
    }

    public byte[] exportFlags() {
        final byte[] out = new byte[this.size];
        for (int k = 0; k < this.size; k++) {
            out[k] = gap(k) ? (byte) 1 : (byte) 0;
        }
        return out;
    }

    public void restore(long lastPeriod, int[] values, int[] stamps, byte[] flags) {
        clear();
        final int n = Math.min(Math.min(values.length, stamps.length), flags.length);
        final int from = Math.max(0, n - CAPACITY);
        for (int k = from; k < n; k++) {
            push(stamps[k], values[k], flags[k] != 0);
        }
        this.lastPeriod = this.size == 0 ? Long.MIN_VALUE : lastPeriod;
    }

    public ChartHistory copy() {
        final ChartHistory c = new ChartHistory();
        c.restore(this.lastPeriod, exportValues(), exportStamps(), exportFlags());
        return c;
    }

    @Override
    public String toString() {
        return "ChartHistory[" + this.size + " to " + this.lastPeriod + ": " + Arrays.toString(exportValues()) + "]";
    }
}
