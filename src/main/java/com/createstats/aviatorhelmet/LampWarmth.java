package com.createstats.aviatorhelmet;

// filament warms up red-orange first, it's not a switch
public final class LampWarmth {
    public static final double WARM_SECONDS = 0.35;

    public static final double FIRST_LIGHT = 0.25;

    private LampWarmth() {
    }

    private static double heat(boolean down, double seconds) {
        if (!down || seconds < GoggleMotion.LAND_SECONDS) {
            return -1.0;
        }
        final double x = Math.min(1.0, (seconds - GoggleMotion.LAND_SECONDS) / WARM_SECONDS);
        return x * x * (3.0 - 2.0 * x);
    }

    public static double warm(boolean down, double seconds) {
        return Math.max(0.0, heat(down, seconds));
    }

    public static double level(boolean down, double seconds) {
        final double h = heat(down, seconds);
        return h < 0.0 ? 0.0 : FIRST_LIGHT + (1.0 - FIRST_LIGHT) * h;
    }

    public static boolean lit(boolean down, double seconds) {
        return heat(down, seconds) >= 0.0;
    }
}
