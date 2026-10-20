package com.createstats.surveyor.core;

// first tick scan is EMPTY, judging it then paused routes forever with "not on a vehicle"
public final class ReturnStartup {
    public static final int LIMIT = 100;
    private int ticks;

    public boolean waiting(boolean ready) {
        return !ready && ++ticks <= LIMIT;
    }

    public int ticks() {
        return ticks;
    }
}
