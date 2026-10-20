package com.createstats.block;

import net.minecraft.util.Mth;

public final class DoorTravel {
    private DoorTravel() {
    }

    public static float openness(float elapsed, int ticks, boolean open) {
        final float travelled = Mth.clamp(elapsed / Math.max(1, ticks), 0.0f, 1.0f);
        return open ? travelled : 1.0f - travelled;
    }

    public static long resumeStart(long now, int ticks, float openness, boolean opening) {
        final float at = Mth.clamp(openness, 0.0f, 1.0f);
        final float done = opening ? at : 1.0f - at;

        return now - Math.round(done * Math.max(1, ticks));
    }

    public static int remaining(long now, long moveStart, int ticks) {
        return (int) Math.max(0L, Math.max(1, ticks) - (now - moveStart));
    }

    public static boolean settled(long now, long moveStart, int ticks) {
        return now - moveStart >= Math.max(1, ticks);
    }
}
