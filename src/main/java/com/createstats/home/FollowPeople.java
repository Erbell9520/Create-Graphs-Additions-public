package com.createstats.home;

public final class FollowPeople {
    public static final int OFF_TICKS = 600;

    public enum Action { NONE, ON, OFF }

    private boolean known;
    private boolean occupied;
    private boolean litByUs;
    private boolean emptyTiming;
    private long emptySince;

    private boolean wasDark;

    public Action update(boolean occupiedNow, long gameTime) {
        return update(occupiedNow, gameTime, true);
    }

    public Action update(boolean occupiedNow, long gameTime, boolean dark) {
        final boolean dusk = dark && !wasDark;
        wasDark = dark;
        if (!known) {
            known = true;
            occupied = occupiedNow;
            return Action.NONE;
        }
        if (occupiedNow) {
            emptyTiming = false;
            if (!occupied) {
                occupied = true;
                if (!dark) {
                    return Action.NONE;
                }
                litByUs = true;
                return Action.ON;
            }
            if (dusk && !litByUs) {
                litByUs = true;
                return Action.ON;
            }
            return Action.NONE;
        }
        if (occupied) {
            occupied = false;
            emptyTiming = true;
            emptySince = gameTime;
            return Action.NONE;
        }
        if (emptyTiming && litByUs && gameTime - emptySince >= OFF_TICKS) {
            emptyTiming = false;
            litByUs = false;
            return Action.OFF;
        }
        return Action.NONE;
    }
}
