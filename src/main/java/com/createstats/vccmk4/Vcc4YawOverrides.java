package com.createstats.vccmk4;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Vcc4YawOverrides {
    public static final double MAX_DEG = 30.0;

    private static final Map<Long, double[]> BY_POS = new ConcurrentHashMap<>();

    private Vcc4YawOverrides() {
    }

    public static void setForTick(BlockPos pos, double angleDeg, long tick) {
        if (pos == null || !Double.isFinite(angleDeg)) return;
        BY_POS.put(pos.asLong(), new double[]{Math.max(-MAX_DEG, Math.min(MAX_DEG, angleDeg)), tick});
    }

    public static double forTick(BlockPos pos, long tick) {
        if (pos == null) return Double.NaN;
        double[] e = BY_POS.get(pos.asLong());
        if (e == null) return Double.NaN;
        long at = (long) e[1];
        return at <= tick && tick - at <= 1L ? e[0] : Double.NaN;
    }

    public static void clear(BlockPos pos) {
        if (pos != null) BY_POS.remove(pos.asLong());
    }
}
