package com.createstats.book.content;

import java.util.Locale;

public final class Num {
    private Num() {
    }

    public static String of(double value) {
        final double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.format(Locale.ROOT, "%,d", (long) rounded);
        }
        return String.format(Locale.ROOT, "%,.1f", rounded);
    }

    public static String of(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
