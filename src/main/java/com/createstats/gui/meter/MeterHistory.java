package com.createstats.gui.meter;

public final class MeterHistory {
    public static final int SIZE = 300;

    public static final int[] CADENCE = {4, 20, 60};

    public static final int RANGE_1M = 0;
    public static final int RANGE_5M = 1;
    public static final int RANGE_15M = 2;

    private final int[] samples = new int[SIZE];
    private int head;
    private int count;

    public int count() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void record(int fePerTick) {
        samples[head] = fePerTick;
        head = (head + 1) % SIZE;
        if (count < SIZE) {
            count++;
        }
    }

    private int start() {
        return ((head - count) % SIZE + SIZE) % SIZE;
    }

    public int at(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("sample " + i + " of " + count);
        }
        return samples[(start() + i) % SIZE];
    }

    public int latest() {
        return count == 0 ? 0 : at(count - 1);
    }

    public int peakMagnitude() {
        int max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, Math.abs(at(i)));
        }
        return max;
    }

    public int mean() {
        if (count == 0) {
            return 0;
        }
        long sum = 0L;
        for (int i = 0; i < count; i++) {
            sum += at(i);
        }
        return (int) (sum / count);
    }

    public int[] chronological() {
        final int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = at(i);
        }
        return out;
    }

    public void replace(int[] chrono, int n) {
        head = 0;
        count = 0;
        final int m = Math.min(n, chrono.length);
        for (int i = 0; i < m; i++) {
            record(chrono[i]);
        }
    }

    public void clear() {
        head = 0;
        count = 0;
    }

    public static int clampRange(int range) {
        return range < 0 ? 0 : Math.min(range, CADENCE.length - 1);
    }
}
