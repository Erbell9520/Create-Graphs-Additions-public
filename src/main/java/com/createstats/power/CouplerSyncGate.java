package com.createstats.power;

public final class CouplerSyncGate {
    public static final long NEVER = Long.MIN_VALUE;

    private CouplerSyncGate() {
    }

    public static boolean due(long now, long last, int interval) {
        if (last == NEVER || now < last) {
            return true;
        }
        return now - last >= interval;
    }

    public static boolean moved(int now, int synced, int threshold, int full) {
        if (now == synced) {
            return false;
        }
        if (now == 0 || (full > 0 && now >= full)) {
            return true;
        }
        return Math.abs((long) now - (long) synced) >= threshold;
    }
}
