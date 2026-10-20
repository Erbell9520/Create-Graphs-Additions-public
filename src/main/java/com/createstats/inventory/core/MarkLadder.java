package com.createstats.inventory.core;

// ladders per unit, a board in ones puts everything in column one and one in thousands can't reach 64
public enum MarkLadder {
    ITEMS(new long[]{0, 1, 8, 16, 32, 64, 128, 256, 512, 1024, 1728, 2048, 4096, 8192, 16_384, 32_768, 65_536,
            131_072, 262_144, 524_288, 1_048_576}, 64, 1024),
    PERCENT(fives(), 10, 90),
    PER_MINUTE(new long[]{0, 1, 2, 5, 10, 15, 20, 30, 40, 60, 90, 120, 180, 240, 360, 480, 720, 960}, 0, 60),
    ENERGY_PERCENT(fives(), 20, 95);

    private final long[] notches;
    private final long defaultLow;
    private final long defaultHigh;

    MarkLadder(long[] notches, long defaultLow, long defaultHigh) {
        this.notches = notches;
        this.defaultLow = defaultLow;
        this.defaultHigh = defaultHigh;
    }

    private static long[] fives() {
        final long[] out = new long[21];
        for (int i = 0; i < out.length; i++) {
            out[i] = 5L * i;
        }
        return out;
    }

    public int size() {
        return this.notches.length;
    }

    public int maxNotch() {
        return this.notches.length - 1;
    }

    public long valueAt(int notch) {
        return this.notches[Math.max(0, Math.min(maxNotch(), notch))];
    }

    public int notchOf(long value) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < this.notches.length; i++) {
            final long d = Math.abs(this.notches[i] - value);
            if (d < bestD) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }

    public double fraction(long value) {
        if (value <= this.notches[0]) {
            return 0.0;
        }
        final int last = maxNotch();
        if (value >= this.notches[last]) {
            return 1.0;
        }
        for (int i = 0; i < last; i++) {
            final long a = this.notches[i];
            final long b = this.notches[i + 1];
            if (value >= a && value <= b) {
                final double t = b == a ? 0.0 : (value - a) / (double) (b - a);
                return (i + t) / last;
            }
        }
        return 1.0;
    }

    public long defaultLow() {
        return this.defaultLow;
    }

    public long defaultHigh() {
        return this.defaultHigh;
    }

    public long typedMax() {
        return this == PERCENT || this == ENERGY_PERCENT ? 100L : 9_999_999L;
    }

    public long clampTyped(long value) {
        return Math.max(0L, Math.min(typedMax(), value));
    }

    public String label(int notch) {
        final long v = valueAt(notch);
        if (this == ITEMS && v >= 16_384L) {
            return v >= 1_048_576L ? (v / 1_048_576L) + "M" : (v / 1024L) + "K";
        }
        return FaceFormat.grouped(v);
    }

    public static MarkLadder byOrdinal(int i) {
        final MarkLadder[] all = values();
        return i >= 0 && i < all.length ? all[i] : ITEMS;
    }
}
