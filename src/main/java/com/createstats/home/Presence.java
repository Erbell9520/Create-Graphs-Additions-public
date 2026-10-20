package com.createstats.home;

public final class Presence {
    public static final int LEAVE_TICKS = 200;

    public static final int AWAY_TICKS = 1200;

    public enum Edge { NONE, LAST_OUT, FIRST_HOME }

    private boolean known;

    private boolean home;

    private long emptySince;
    private boolean emptyTiming;

    private long leftAt;

    public Edge update(int playersInside, long gameTime) {
        final boolean anyone = playersInside > 0;
        if (!known) {
            known = true;
            home = anyone;
            emptyTiming = false;
            leftAt = gameTime - AWAY_TICKS;
            return Edge.NONE;
        }
        if (anyone) {
            emptyTiming = false;
            if (!home) {
                home = true;
                return gameTime - leftAt >= AWAY_TICKS ? Edge.FIRST_HOME : Edge.NONE;
            }
            return Edge.NONE;
        }
        if (!home) {
            return Edge.NONE;
        }
        if (!emptyTiming) {
            emptyTiming = true;
            emptySince = gameTime;
            return Edge.NONE;
        }
        if (gameTime - emptySince >= LEAVE_TICKS) {
            home = false;
            emptyTiming = false;
            leftAt = gameTime;
            return Edge.LAST_OUT;
        }
        return Edge.NONE;
    }

    public boolean isHome() {
        return home;
    }
}
