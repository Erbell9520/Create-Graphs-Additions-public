package com.createstats.power;

import java.util.Arrays;

public record FeFeeds(long sink, long[] from, int[] rate) {
    public FeFeeds {
        if (from.length != rate.length) {
            throw new IllegalArgumentException("one rate per span: " + from.length + " spans, " + rate.length + " rates");
        }
        from = from.clone();
        rate = rate.clone();
    }

    public int count() {
        return from.length;
    }

    public static final int UNWIRED = 0;

    public static final int PASS = 1;

    public static final int DIRECT = 2;
    // SILENT ahead of DIRECT
    public static final int SILENT = 3;

    public static final int BOARD = 4;

    public static int homeSlot(int[] kind) {
        for (int want = BOARD; want >= DIRECT; want--) {
            for (int i = 0; i < kind.length; i++) {
                if (kind[i] == want) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "FeFeeds[sink=" + sink + ", from=" + Arrays.toString(from) + ", rate=" + Arrays.toString(rate) + "]";
    }
}
