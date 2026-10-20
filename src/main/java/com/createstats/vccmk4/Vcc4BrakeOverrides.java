package com.createstats.vccmk4;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// offroad reads brake as redstone above the mount. nobody puts redstone over 16 wheels
public final class Vcc4BrakeOverrides {
    private static final Map<Long, long[]> BY_POS = new ConcurrentHashMap<>();

    private Vcc4BrakeOverrides() {
    }

    public static void setForTick(BlockPos pos, int signal, long tick) {
        if (pos == null) return;
        int s = Math.max(0, Math.min(15, signal));
        BY_POS.put(pos.asLong(), new long[]{s, tick});
    }

    public static int forTick(BlockPos pos, long tick) {
        if (pos == null) return -1;
        long[] e = BY_POS.get(pos.asLong());
        if (e == null) return -1;
        return e[1] <= tick && tick - e[1] <= 1L ? (int) e[0] : -1;
    }

    public static void clear(BlockPos pos) {
        if (pos != null) BY_POS.remove(pos.asLong());
    }
}
