package com.createstats.home;

public final class DayClock {
    private DayClock() {
    }

    public static final int DAY_TICKS = 24000;

    public static final int NIGHT_FROM = 13188;

    public static final int NIGHT_TO = 22812;

    public static int tickOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, (long) DAY_TICKS);
    }

    public static boolean isNight(long dayTime) {
        final int t = tickOfDay(dayTime);
        return t >= NIGHT_FROM && t < NIGHT_TO;
    }

    public static int untilNight(long dayTime) {
        final int t = tickOfDay(dayTime);
        if (t >= NIGHT_FROM && t < NIGHT_TO) {
            return 0;
        }
        return t < NIGHT_FROM ? NIGHT_FROM - t : DAY_TICKS - t + NIGHT_FROM;
    }

    public static int untilDay(long dayTime) {
        final int t = tickOfDay(dayTime);
        return t >= NIGHT_FROM && t < NIGHT_TO ? NIGHT_TO - t : 0;
    }
}
