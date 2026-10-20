package com.createstats.power;

public final class FeTickTotal {
    private static final long WINDOW_TICKS = 1;

    private int total;
    private long tick = Long.MIN_VALUE;

    public void add(int fe, long gameTime) {
        if (fe <= 0) {
            return;
        }
        if (gameTime != tick) {
            tick = gameTime;
            total = 0;
        }
        total += fe;
    }

    public int at(long now) {
        if (tick == Long.MIN_VALUE) {
            return 0;
        }

        final long age = now - tick;
        return age >= 0 && age <= WINDOW_TICKS ? total : 0;
    }
}
