package com.createstats.driverhelmet;

public final class VisorMotion {
    public static final double PIVOT_Y = 8.0;

    public static final double PIVOT_Z = 5.5;

    public static final double OPEN_MAX = 76.0;

    public static final int STROKE_LOCK_TICKS = 7;

    static final double[][] OPEN = {
        {0.0000, 0.00}, {0.0500, 0.18}, {0.0792, 1.62}, {0.0918, 3.14}, {0.1168, 7.16},
        {0.1376, 11.46}, {0.1584, 17.14}, {0.1876, 27.04}, {0.2168, 38.37}, {0.2834, 65.34},
        {0.3126, 75.82}, {0.3168, 76.91}, {0.3334, 75.27}, {0.3542, 76.22}, {0.3834, 74.96},
        {0.4210, 76.10}, {0.4542, 75.52}, {0.4960, 76.04}, {0.6042, 75.85},
    };

    static final double[][] CLOSE = {
        {0.0000, 76.00}, {0.0376, 75.73}, {0.0668, 74.33}, {0.0792, 73.03}, {0.0876, 71.62},
        {0.1042, 67.47}, {0.1126, 64.72}, {0.1250, 60.01}, {0.1460, 50.76}, {0.1710, 38.05},
        {0.2168, 13.46}, {0.2376, 3.15}, {0.2460, -0.66}, {0.2500, -1.34}, {0.2584, 0.35},
        {0.2668, 1.03}, {0.2876, -0.32}, {0.3126, 0.67}, {0.3418, -0.15}, {0.3584, 0.16},
        {0.4418, -0.04},
    };

    private VisorMotion() {
    }

    public static double angleAt(boolean opening, double seconds) {
        final double[][] frames = opening ? OPEN : CLOSE;
        if (!(seconds > frames[0][0])) {
            return frames[0][1];
        }
        for (int i = 1; i < frames.length; i++) {
            if (seconds <= frames[i][0]) {
                final double[] a = frames[i - 1];
                final double[] b = frames[i];
                return a[1] + (b[1] - a[1]) * (seconds - a[0]) / (b[0] - a[0]);
            }
        }
        return frames[frames.length - 1][1];
    }

    public static double angle(boolean open, long strokeTick, double nowTicks) {
        return angleAt(open, Math.max(0.0, (nowTicks - strokeTick) / 20.0));
    }

    public static double camPush(double angle) {
        final double s = Math.max(0.0, Math.min(1.0, angle / 6.0));
        return 0.25 * Math.sin(s * Math.PI / 2.0);
    }

    public static double settleSeconds(boolean opening) {
        final double[][] frames = opening ? OPEN : CLOSE;
        return frames[frames.length - 1][0];
    }
}
