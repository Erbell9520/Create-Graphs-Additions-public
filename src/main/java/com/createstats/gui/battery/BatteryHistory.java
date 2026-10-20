package com.createstats.gui.battery;

// one ring per range so each is its own resolution
public final class BatteryHistory {
    public static final int SIZE = 300;

    public static final int[] CADENCE = {4, 20, 60};

    public static final int RANGE_1M = 0;
    public static final int RANGE_5M = 1;
    public static final int RANGE_15M = 2;

    private final byte[] soc = new byte[SIZE];
    private final int[] in = new int[SIZE];
    private final int[] out = new int[SIZE];
    private int head;
    private int count;

    public int count() {
        return count;
    }

    public int capacity() {
        return SIZE;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void record(int socPct, int inFe, int outFe) {
        pushRaw((byte) Math.max(0, Math.min(100, socPct)), Math.max(0, inFe), Math.max(0, outFe));
    }

    public void pushRaw(byte socSample, int inSample, int outSample) {
        soc[head] = socSample;
        in[head] = inSample;
        out[head] = outSample;
        head = (head + 1) % SIZE;
        if (count < SIZE) {
            count++;
        }
    }

    private int start() {
        return ((head - count) % SIZE + SIZE) % SIZE;
    }

    private void check(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("sample " + i + " of " + count);
        }
    }

    public int socAt(int i) {
        check(i);
        return soc[(start() + i) % SIZE];
    }

    public int inAt(int i) {
        check(i);
        return in[(start() + i) % SIZE];
    }

    public int outAt(int i) {
        check(i);
        return out[(start() + i) % SIZE];
    }

    public int peakRate() {
        int peak = 0;
        for (int i = 0; i < count; i++) {
            peak = Math.max(peak, Math.max(inAt(i), outAt(i)));
        }
        return peak;
    }

    public int meanNetRate() {
        if (count == 0) {
            return 0;
        }
        long net = 0;
        for (int i = 0; i < count; i++) {
            net += (long) inAt(i) - outAt(i);
        }
        return (int) (net / count);
    }

    public int meanSocPct() {
        if (count == 0) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += socAt(i);
        }
        return total / count;
    }

    public int latestSocPct() {
        return count == 0 ? 0 : socAt(count - 1);
    }

    public byte[] socChronological() {
        final byte[] copy = new byte[count];
        for (int i = 0; i < count; i++) {
            copy[i] = (byte) socAt(i);
        }
        return copy;
    }

    public int[] inChronological() {
        final int[] copy = new int[count];
        for (int i = 0; i < count; i++) {
            copy[i] = inAt(i);
        }
        return copy;
    }

    public int[] outChronological() {
        final int[] copy = new int[count];
        for (int i = 0; i < count; i++) {
            copy[i] = outAt(i);
        }
        return copy;
    }

    public void replace(byte[] socChrono, int[] inChrono, int[] outChrono, int n) {
        head = 0;
        count = 0;
        final int m = Math.min(n, Math.min(socChrono.length,
                Math.min(inChrono.length, outChrono.length)));
        for (int i = 0; i < m; i++) {
            pushRaw(socChrono[i], inChrono[i], outChrono[i]);
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
