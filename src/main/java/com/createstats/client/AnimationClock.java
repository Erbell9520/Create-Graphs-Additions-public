package com.createstats.client;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class AnimationClock {
    private AnimationClock() {
    }

    public static double time(@Nullable Level level, float partialTicks) {
        return level == null ? partialTicks : (double) level.getGameTime() + partialTicks;
    }

    public static double time(long gameTime, float partialTicks) {
        return (double) gameTime + partialTicks;
    }

    public static final long WRAP_TICKS = 720720L;

    public static float wrapped(@Nullable Level level, float partialTicks) {
        return level == null ? partialTicks
                : (float) Math.floorMod(level.getGameTime(), WRAP_TICKS) + partialTicks;
    }
}
