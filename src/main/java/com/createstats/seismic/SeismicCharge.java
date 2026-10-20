package com.createstats.seismic;

// radius cubed, the survey is a volume
public final class SeismicCharge {
    public static final int MIN_RADIUS = 32;

    public static final int MAX_RADIUS = 64;

    public static final int RADIUS_STEP = 16;

    public static final int DEFAULT_RADIUS = 32;

    public static final int FULL_BURST = 500_000;

    public static final int WINCH_FE_PER_TICK = 4_096;

    public static final int MIN_REACH = 8;

    public static final int FULL_FALL_TICKS = 7;

    public static final double RESTITUTION = 0.3;

    private SeismicCharge() {
    }

    public static int clampRadius(int radius) {
        final int r = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        return MIN_RADIUS + (r - MIN_RADIUS) / RADIUS_STEP * RADIUS_STEP;
    }

    public static int burstFor(int radius) {
        return (int) Math.round(FULL_BURST * heightFor(radius));
    }

    public static double heightFor(int radius) {
        final double f = clampRadius(radius) / (double) MAX_RADIUS;
        return f * f * f;
    }

    public static double heightOf(long stored) {
        return Math.max(0.0, Math.min(1.0, stored / (double) FULL_BURST));
    }

    public static int reachOf(long stored) {
        if (stored <= 0) {
            return 0;
        }
        final double h = Math.min(1.0, (stored + 0.5) / (double) FULL_BURST);
        return (int) Math.floor(MAX_RADIUS * Math.cbrt(h) + 1.0e-9);
    }

    public static int surveyRadius(int setRadius, long stored) {
        final int reach = Math.min(clampRadius(setRadius), reachOf(stored));
        return reach < MIN_REACH ? 0 : reach;
    }

    public static boolean ready(int radius, long stored) {
        return stored >= burstFor(radius);
    }

    public static int winchTake(int radius, long stored, int offered, int alreadyThisTick) {
        final long room = Math.max(0L, burstFor(radius) - stored);
        final int rate = Math.max(0, WINCH_FE_PER_TICK - Math.max(0, alreadyThisTick));
        return (int) Math.max(0L, Math.min(Math.min(room, rate), Math.max(0, offered)));
    }

    public static int ticksToCharge(long stored, int radius, int fePerTick) {
        final long need = burstFor(radius) - Math.max(0L, stored);
        if (need <= 0) {
            return 0;
        }
        final int rate = Math.min(WINCH_FE_PER_TICK, fePerTick);
        if (rate <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, (need + rate - 1) / rate);
    }

    public static double secondsToCharge(int radius, int fePerTick) {
        final int rate = Math.min(WINCH_FE_PER_TICK, fePerTick);
        return rate <= 0 ? Double.POSITIVE_INFINITY : burstFor(radius) / (double) rate / 20.0;
    }

    public static int fallTicks(double height) {
        if (height <= 0.0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(FULL_FALL_TICKS * Math.sqrt(Math.min(1.0, height))));
    }

    public static double fallen(double t, int ticks) {
        if (ticks <= 0 || t >= ticks) {
            return 1.0;
        }
        if (t <= 0.0) {
            return 0.0;
        }
        final double s = t / ticks;
        return s * s;
    }

    public static double bounce(double t, double height, int ticks) {
        if (ticks <= 0 || height <= 0.0 || t <= 0.0) {
            return 0.0;
        }
        final double g = 2.0 * height / ((double) ticks * ticks);
        double v = RESTITUTION * g * ticks;
        double s = t;
        for (int hop = 0; hop < 2; hop++) {
            final double lasts = 2.0 * v / g;
            if (s < lasts) {
                return v * s - 0.5 * g * s * s;
            }
            s -= lasts;
            v *= RESTITUTION;
        }
        return 0.0;
    }

    public static double bounceTicks(int ticks) {
        return 2.0 * RESTITUTION * ticks * (1.0 + RESTITUTION);
    }
}
