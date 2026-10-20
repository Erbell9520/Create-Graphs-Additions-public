package com.createstats.vacuum;

// 3 points a nugget, create's nugget gives ceil(3*count) back
public final class XpCondenser {
    public static final int POINTS_PER_NUGGET = 3;

    public static final int CAPACITY = POINTS_PER_NUGGET * 128;

    private int points;

    public int points() {
        return points;
    }

    public int room() {
        return CAPACITY - points;
    }

    public int add(int xp) {
        final int take = Math.max(0, Math.min(xp, room()));
        points += take;
        return take;
    }

    public int nuggetsReady() {
        return points / POINTS_PER_NUGGET;
    }

    public int takeNuggets(int n) {
        final int made = Math.max(0, Math.min(n, nuggetsReady()));
        points -= made * POINTS_PER_NUGGET;
        return made;
    }

    public float charge() {
        return Math.min(1.0f, points / (float) POINTS_PER_NUGGET);
    }

    public void load(int saved) {
        points = Math.max(0, Math.min(CAPACITY, saved));
    }
}
