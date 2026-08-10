package com.createstats.flight.runtime;

import com.createstats.DebugLogging;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** [1] */
public final class WheelMountSteeringOverrides {

    private static final Map<Long, Entry> BY_POS = new ConcurrentHashMap<>();
    private static long lastWriteDebugTick = Long.MIN_VALUE;

    private WheelMountSteeringOverrides() {
    }

    public static void setForTick(BlockPos pos, int leftSignal, int rightSignal, long tick) {
        if (pos == null) return;
        int left = clampSignal(leftSignal);
        int right = clampSignal(rightSignal);
        Entry entry = new Entry(left, right, tick);
        BY_POS.put(pos.asLong(), entry);
        if ((left > 0 || right > 0) && (lastWriteDebugTick == Long.MIN_VALUE || tick - lastWriteDebugTick >= 20L)) {
            lastWriteDebugTick = tick;
            DebugLogging.fccInfo("[FCC/WHEEL_WRITE] pos={} tick={} left={} right={}", pos, tick, left, right);
        }
    }

    @Nullable
    public static Entry getForTick(BlockPos pos, long tick) {
        if (pos == null) return null;
        Entry entry = BY_POS.get(pos.asLong());
        if (entry == null) return null;
        if (entry.tick() <= tick && tick - entry.tick() <= 1L) return entry;
        return null;
    }

    @Nullable
    public static Entry peek(BlockPos pos) {
        if (pos == null) return null;
        return BY_POS.get(pos.asLong());
    }

    private static int clampSignal(int value) {
        return Math.max(0, Math.min(15, value));
    }

    public record Entry(int leftSignal, int rightSignal, long tick) {
    }
}
