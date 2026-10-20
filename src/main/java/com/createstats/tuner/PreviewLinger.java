package com.createstats.tuner;

public final class PreviewLinger {
    public static final long HOLD_MS = 5_000L;

    public static final long FADE_MS = 2_000L;

    private PreviewLinger() {
    }

    public static double strength(long sinceMs) {
        if (sinceMs <= HOLD_MS) {
            return 1.0;
        }
        if (sinceMs >= HOLD_MS + FADE_MS) {
            return 0.0;
        }
        final double s = (sinceMs - HOLD_MS) / (double) FADE_MS;
        return 1.0 - s * s * (3.0 - 2.0 * s);
    }

    public static boolean gone(long sinceMs) {
        return sinceMs >= HOLD_MS + FADE_MS;
    }

    public static int fade(int argb, double strength) {
        final double k = Math.max(0.0, Math.min(1.0, strength));
        final int alpha = (int) Math.round((argb >>> 24) * k);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
