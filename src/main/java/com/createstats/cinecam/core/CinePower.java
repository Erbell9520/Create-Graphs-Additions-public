package com.createstats.cinecam.core;

import java.util.Locale;

public final class CinePower {
    public static final int HOUR_TICKS = 20 * 60 * 60;

    private CinePower() {
    }

    public static int batteryFor(int filmingDrain) {
        return (int) Math.min(Integer.MAX_VALUE, (long) HOUR_TICKS * Math.max(0, filmingDrain));
    }

    public static long ticksAt(int stored, int drain) {
        return drain <= 0 ? Long.MAX_VALUE : Math.max(0, stored) / (long) drain;
    }

    public static String duration(long ticks) {
        if (ticks == Long.MAX_VALUE) return "no limit";
        long minutes = Math.max(0L, ticks) / (20L * 60L);
        if (minutes == 0) return "under 1 min";
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) return String.format(Locale.ROOT, "%d min", m);
        return m == 0 ? String.format(Locale.ROOT, "%d h", h) : String.format(Locale.ROOT, "%d h %d min", h, m);
    }

    // exactly serviceTick's filming branch. a new consumer starts powered and would let you in then cut off
    public static boolean canRun(boolean required, boolean powered, int energy, int capacity, int activeDrain, int rebootPct) {
        if (!required) return true;
        boolean reserveOk = powered || energy * 100.0f / Math.max(1, capacity) >= rebootPct;
        return reserveOk && energy >= Math.max(0, activeDrain);
    }

    public static int accepts(int stored, int capacity, int offered, int count) {
        if (count != 1 || offered <= 0) return 0;
        return Math.max(0, Math.min(offered, capacity - Math.max(0, stored)));
    }
}
