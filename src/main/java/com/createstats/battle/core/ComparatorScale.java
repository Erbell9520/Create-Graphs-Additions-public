package com.createstats.battle.core;

// one step per 1% lost. 15*integrity sits at 15 until 97% and nothing ever trips
public final class ComparatorScale {
    public static final int MODE_INTEGRITY = 0;
    public static final int MODE_UNDER_FIRE = 1;
    public static final int MODE_GUNS_READY = 2;

    public static final long UNDER_FIRE_TICKS = 200L;

    private ComparatorScale() {
    }

    public static int integrity(int lost, int reference) {
        if (reference <= 0 || lost <= 0) return 15;
        long percent = (100L * lost) / reference;
        return 15 - (int) Math.min(15L, percent);
    }

    public static int underFire(long hitAgeTicks) {
        return hitAgeTicks >= 0 && hitAgeTicks < UNDER_FIRE_TICKS ? 15 : 0;
    }

    public static int gunsReady(int ready) {
        return Math.max(0, Math.min(15, ready));
    }

    public static int bridge(int mode, int lost, int reference, long hitAgeTicks, int gunsReady) {
        return switch (mode) {
            case MODE_UNDER_FIRE -> underFire(hitAgeTicks);
            case MODE_GUNS_READY -> gunsReady(gunsReady);
            default -> integrity(lost, reference);
        };
    }

    public static int marker(int intact, int total) {
        if (total <= 0 || intact <= 0) return 0;
        return (int) Math.round(15.0 * intact / total);
    }
}
