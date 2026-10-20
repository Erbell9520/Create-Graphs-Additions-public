package com.createstats.power;

public final class CapacitorDischarge {
    public static final int PEAK_FE_PER_TICK = 65_536;

    public static final int BURST_TICKS = 320;

    public static final int CAPACITY = PEAK_FE_PER_TICK / 2 * BURST_TICKS;

    public static final int CHARGE_FE_PER_TICK = 3_072;

    private CapacitorDischarge() {
    }

    public static long drainedAfter(int k) {
        if (k <= 0) {
            return 0;
        }
        if (k >= BURST_TICKS) {
            return CAPACITY;
        }
        final long d = BURST_TICKS - k;
        return CAPACITY - (CAPACITY * d * d) / ((long) BURST_TICKS * BURST_TICKS);
    }

    public static int allowanceAt(int t) {
        if (t < 0 || t >= BURST_TICKS) {
            return 0;
        }
        return (int) (drainedAfter(t + 1) - drainedAfter(t));
    }

    public static float rampAt(float t) {
        return Math.max(0f, Math.min(1f, 1f - t / BURST_TICKS));
    }

    public static float whinePitch(float r) {
        return 0.5f + 0.5f * Math.max(0f, Math.min(1f, r));
    }

    public static int ticksToFull(int stored) {
        final int missing = Math.max(0, CAPACITY - Math.max(0, stored));
        return (missing + CHARGE_FE_PER_TICK - 1) / CHARGE_FE_PER_TICK;
    }
}
