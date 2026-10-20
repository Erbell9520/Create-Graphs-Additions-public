package com.createstats.gui.meter.plate;

import java.util.Locale;

public final class MeterText {
    private MeterText() {
    }

    public static String group(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    public static String compact(long v) {
        final long a = Math.abs(v);
        if (a < 10_000L) {
            return group(v);
        }
        final long[] div = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};
        final String[] unit = {"T", "G", "M", "k"};
        for (int i = 0; i < div.length; i++) {
            if (a >= div[i]) {
                final double x = (double) v / div[i];
                final String f = Math.abs(x) >= 100 ? "%.0f" : Math.abs(x) >= 10 ? "%.1f" : "%.2f";
                return String.format(Locale.ROOT, f, x) + unit[i];
            }
        }
        return group(v);
    }

    public static String duration(int ticks) {
        final int s = Math.round(ticks / 20.0f);
        if (s < 60) {
            return s + "s";
        }
        final int m = s / 60;
        final int r = s % 60;
        return r == 0 ? m + "m" : m + "m " + r + "s";
    }
}
