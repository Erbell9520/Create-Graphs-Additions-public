package com.createstats.battle.core;

public final class RefitRules {
    public static final long FIGHT_TICKS = 1200L;

    public static final int BANNER_MIN = 8;

    private RefitRules() {
    }

    public static boolean isRefit(long now, long lastHitAt) {
        return !inFight(now, lastHitAt);
    }

    public static boolean inFight(long now, long lastHitAt) {
        return lastHitAt >= 0 && now - lastHitAt < FIGHT_TICKS;
    }

    public static boolean banner(int unexplainedPeacetimeLosses, boolean playerAboard) {
        return unexplainedPeacetimeLosses >= BANNER_MIN && playerAboard;
    }
}
