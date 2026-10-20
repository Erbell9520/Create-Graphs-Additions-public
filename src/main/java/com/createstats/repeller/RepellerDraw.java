package com.createstats.repeller;

public final class RepellerDraw {
    public static final double FE_PER_BLOCK_SQUARED = 0.25;

    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;

    public static final int RADIUS_STEP = 4;

    public static final int DEFAULT_RADIUS = 16;

    public static final int BUFFER = 32 * 1_024;

    public static final int TRANSFER_RATE = 2 * 1_024;

    private RepellerDraw() {
    }

    public static int drawFor(int radius) {
        if (radius <= 0) {
            return 0;
        }
        return (int) Math.ceil(FE_PER_BLOCK_SQUARED * radius * (double) radius - 1.0e-9);
    }

    public static double radiusPaidBy(double fePerTick) {
        return fePerTick <= 0.0 ? 0.0 : Math.sqrt(fePerTick / FE_PER_BLOCK_SQUARED);
    }

    public static double effectiveRadius(int setRadius, double spent) {
        return Math.min(setRadius, radiusPaidBy(spent));
    }

    public static int clampRadius(int radius) {
        final int clamped = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        return MIN_RADIUS + (clamped - MIN_RADIUS) / RADIUS_STEP * RADIUS_STEP;
    }

    public static int stepRadius(int radius, int notches) {
        return clampRadius(clampRadius(radius) + notches * RADIUS_STEP);
    }

    public static int cycleRadius(int radius) {
        final int r = clampRadius(radius);
        return r >= MAX_RADIUS ? MIN_RADIUS : r + RADIUS_STEP;
    }

    public static int notches() {
        return (MAX_RADIUS - MIN_RADIUS) / RADIUS_STEP + 1;
    }
}
