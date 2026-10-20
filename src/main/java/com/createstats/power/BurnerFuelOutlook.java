package com.createstats.power;

public final class BurnerFuelOutlook {
    private boolean fed;
    private boolean waiting;

    public void onTake() {
        if (waiting) {
            fed = false;
        }
        waiting = true;
    }

    public void onFeed() {
        fed = true;
        waiting = false;
    }

    public void onHand() {
        if (waiting) {
            fed = false;
        }
    }

    public boolean fed() {
        return fed;
    }

    public boolean waiting() {
        return waiting;
    }

    public void load(boolean fed, boolean waiting) {
        this.fed = fed;
        this.waiting = waiting;
    }

    public static long ticksOnHand(int burnLeft, int count, int ticksPerItem) {
        return Math.max(0L, burnLeft) + (long) Math.max(0, count) * Math.max(0, ticksPerItem);
    }

    public static int outputIn(int potentialNow, long ticksOnHand, boolean fed, int aheadTicks) {
        if (potentialNow <= 0) {
            return 0;
        }
        if (fed) {
            return potentialNow;
        }
        return Math.max(0, aheadTicks) < ticksOnHand ? potentialNow : 0;
    }
}
