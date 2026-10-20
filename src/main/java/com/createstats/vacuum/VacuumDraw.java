package com.createstats.vacuum;

public final class VacuumDraw {
    private VacuumDraw() {
    }

    public static final int OUTLET_RATING = 512;

    public static final int COLLECTOR_BUFFER = 4_000;
    public static final int COLLECTOR_MIN_RADIUS = 1;
    public static final int COLLECTOR_MAX_RADIUS = 8;

    public static final int COLLECTOR_PER_ITEM = 32;

    public static final int COLLECTOR_CAPTURES_PER_TICK = 2;

    public static int collectorFan(int radius) {
        return 4 + 2 * clamp(radius, COLLECTOR_MIN_RADIUS, COLLECTOR_MAX_RADIUS);
    }

    public static int collectorRated(boolean switchedOn, boolean redstone, boolean forcedOff, int radius) {
        return !switchedOn || redstone || forcedOff ? 0 : collectorFan(radius);
    }

    public static int collectorPeak(int radius) {
        return collectorFan(radius) + COLLECTOR_PER_ITEM * COLLECTOR_CAPTURES_PER_TICK;
    }

    public static final int CLEANER_BUFFER = 4_000;

    public static final int CLEANER_STANDBY = 1;

    public static final int CLEANER_SUCTION = 40;
    public static final int CLEANER_PER_ITEM = 16;
    public static final int CLEANER_CAPTURES_PER_TICK = 2;

    public static final int CLEANER_PER_BLOCK = 24;

    public static int cleanerPeak() {
        return CLEANER_SUCTION + CLEANER_PER_ITEM * CLEANER_CAPTURES_PER_TICK + CLEANER_PER_BLOCK;
    }

    public static final int SNIFFER_BUFFER = 2_000;
    public static final int SNIFFER_IDLE = 4;
    public static final int SNIFFER_PER_ITEM = 24;

    public static final int SNIFFER_IN_FLIGHT = 2;

    public static int snifferPeak() {
        return SNIFFER_IDLE + SNIFFER_PER_ITEM * SNIFFER_IN_FLIGHT;
    }

    public static boolean canAfford(int stored, int fanThisTick, int perItem) {
        return stored - fanThisTick >= perItem;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
