package com.createstats.stormcoil;

// ties go to the rod, you put it there on purpose
public final class CoilCatch {
    public static final int PRIME_TICKS = 60;

    static final int[] CALL_MEAN = {1800, 1200, 900, 600};

    private CoilCatch() {
    }

    public static int callDelay(CoilStage stage, double roll) {
        final double r = Math.max(0.0, Math.min(1.0, roll));
        return Math.max(PRIME_TICKS + 20, (int) Math.round(CALL_MEAN[stage.ordinal()] * (0.5 + r)));
    }

    public static boolean primed(long now, long due) {
        return due > 0 && now < due && due - now <= PRIME_TICKS;
    }

    public static boolean inRadius(CoilStage stage, long distSq) {
        final long r = stage.catchRadius;
        return distSq >= 0 && distSq <= r * r;
    }

    public static boolean beatsRod(long coilDistSq, long rodDistSq) {
        return rodDistSq < 0 || coilDistSq < rodDistSq;
    }
}
