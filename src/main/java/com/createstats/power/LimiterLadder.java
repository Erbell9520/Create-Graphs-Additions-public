package com.createstats.power;

import java.util.Locale;

public final class LimiterLadder {
    private static final int[] STEPS = {
            0,
            1, 2, 3, 4, 6,
            8, 12, 16, 24, 32, 48,
            64, 96, 128, 192, 256, 384,
            512, 768, 1024, 1536, 2048, 3072,
            4096, 6144, 8192, 12288, 16384, 24576,
            32768, 49152, 65536, 98304, 131072, 196608,
            262144, 393216, 524288, 786432, 1048576,
    };

    public static final int MAX_NOTCH = STEPS.length - 1;

    public static final int DEFAULT_NOTCH = 26;

    public static final int[] PRESET_NOTCHES = {0, 16, 24, 26, 30, MAX_NOTCH};

    private LimiterLadder() {
    }

    public static int feAt(int notch) {
        return STEPS[clamp(notch)];
    }

    public static int clamp(int notch) {
        return notch < 0 ? 0 : Math.min(notch, MAX_NOTCH);
    }

    public static int nearestNotch(int fe) {
        if (fe <= 0) {
            return 0;
        }
        if (fe >= STEPS[MAX_NOTCH]) {
            return MAX_NOTCH;
        }
        int best = 1;
        double bestError = Double.MAX_VALUE;
        for (int i = 1; i <= MAX_NOTCH; i++) {
            final double error = Math.abs(Math.log((double) fe / STEPS[i]));
            if (error < bestError) {
                bestError = error;
                best = i;
            }
        }
        return best;
    }

    public static int notchForFraction(double fraction) {
        final double t = fraction < 0.0 ? 0.0 : Math.min(1.0, fraction);
        return (int) Math.round(t * MAX_NOTCH);
    }

    public static float fractionForNotch(int notch) {
        return clamp(notch) / (float) MAX_NOTCH;
    }

    public static String format(int fe) {
        return String.format(Locale.ROOT, "%,d FE/t", fe);
    }

    public static String compact(long fe) {
        final long magnitude = Math.abs(fe);
        if (magnitude < 1000L) {
            return String.valueOf(fe);
        }
        if (magnitude < 1_000_000L) {
            final double k = fe / 1000.0;
            return k == Math.rint(k) ? String.format(Locale.ROOT, "%.0fk", k)
                    : String.format(Locale.ROOT, "%.1fk", k);
        }
        return String.format(Locale.ROOT, "%.2fM", fe / 1_000_000.0);
    }
}
