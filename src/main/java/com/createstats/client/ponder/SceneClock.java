package com.createstats.client.ponder;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SceneClock {
    private static final Map<Level, long[]> TICKS = Collections.synchronizedMap(new WeakHashMap<>());

    private SceneClock() {
    }

    public static void set(Level level, long tick) {
        TICKS.computeIfAbsent(level, k -> new long[1])[0] = tick;
    }

    public static long now(Level level) {
        if (level instanceof PonderLevel) {
            final long[] t = TICKS.get(level);
            if (t != null) {
                return t[0];
            }
        }
        return level.getGameTime();
    }
}
