package com.createstats.power;

public final class CouplerSpanWatch {
    public static final int MISSES_TO_DROP = 3;

    private CouplerSpanWatch() {
    }

    public static int next(int misses, boolean present) {
        return present ? 0 : Math.min(MISSES_TO_DROP, Math.max(0, misses) + 1);
    }

    public static boolean drop(int misses) {
        return misses >= MISSES_TO_DROP;
    }
}
