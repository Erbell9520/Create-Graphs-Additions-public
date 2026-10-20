package com.createstats.seismic;

public final class VeinVisibility {
    public static final double NEAR = 16.0;

    public static final double FAR = 32.0;

    public static final int OVERVIEW_TICKS = 100;

    public static final double STAGGER_TICKS = 30.0;

    public static final double STAGGER_SPAN = 64.0;

    public static final double FADE_TICKS = 30.0;

    public static final double RISE_TICKS = 3.0;

    public static final double FLASH_TICKS = 10.0;

    public static final double RETIRE_TICKS = 20.0;

    public static final double EASE_UP = 0.45;
    public static final double EASE_DOWN = 0.14;

    public static final double GONE = 0.01;

    public static final double SHEET_TICKS = 8.0;

    public static final double SHEETS_SPAN = 32.0;

    private VeinVisibility() {
    }

    public static int sheetDelay(int sheet, int sheets) {
        if (sheets <= 1 || sheet <= 0) {
            return 0;
        }
        final double step = Math.min(SHEET_TICKS, SHEETS_SPAN / (sheets - 1));
        return (int) Math.round(Math.min(sheet, sheets - 1) * step);
    }

    public static double proximity(double distance) {
        if (!(distance > NEAR)) {
            return 1.0;
        }
        if (distance >= FAR) {
            return 0.0;
        }
        return smoother((FAR - distance) / (FAR - NEAR));
    }

    public static double overview(double sinceEnd, double distance) {
        final double start = STAGGER_TICKS * clamp(1.0 - Math.max(0.0, distance) / STAGGER_SPAN);
        final double u = (sinceEnd - start) / FADE_TICKS;
        return u <= 0.0 ? 1.0 : 1.0 - smooth(Math.min(1.0, u));
    }

    public static double reveal(double age) {
        return age <= 0.0 ? 0.0 : smooth(Math.min(1.0, age / RISE_TICKS));
    }

    public static double flash(double age) {
        return age <= 0.0 || age >= FLASH_TICKS ? 0.0 : 1.0 - smooth(age / FLASH_TICKS);
    }

    public static double retire(double sinceRetired) {
        return !(sinceRetired > 0.0) ? 1.0 : 1.0 - smooth(Math.min(1.0, sinceRetired / RETIRE_TICKS));
    }

    public static double target(double age, double sinceEnd, double distance, double sinceRetired) {
        return reveal(age) * Math.max(proximity(distance), overview(sinceEnd, distance)) * retire(sinceRetired);
    }

    public static double ease(double shown, double target) {
        return shown + (target - shown) * (target > shown ? EASE_UP : EASE_DOWN);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static double smooth(double u) {
        final double v = clamp(u);
        return v * v * (3.0 - 2.0 * v);
    }

    static double smoother(double u) {
        final double v = clamp(u);
        return v * v * v * (v * (6.0 * v - 15.0) + 10.0);
    }
}
