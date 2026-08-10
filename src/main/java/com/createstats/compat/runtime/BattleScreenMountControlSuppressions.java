package com.createstats.compat.runtime;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BattleScreenMountControlSuppressions {

    private static final Map<Long, Long> BY_POS = new ConcurrentHashMap<>();

    private BattleScreenMountControlSuppressions() {
    }

    public static void suppressForTick(@Nullable BlockPos pos, long tick) {
        if (pos == null) {
            return;
        }
        BY_POS.put(pos.asLong(), tick);
    }

    public static boolean isSuppressed(@Nullable BlockPos pos, long tick) {
        if (pos == null) {
            return false;
        }
        Long entryTick = BY_POS.get(pos.asLong());
        return entryTick != null && entryTick <= tick && tick - entryTick <= 1L;
    }
}