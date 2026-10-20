package com.createstats.client.battle;

import com.createstats.battle.DamageObserverVoxelMap;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

// nothing fills this since the bridge rewrite
public final class ClientObserverVoxelCache {
    private static final long TTL_MS = 5_000L;
    private static final long REQUEST_THROTTLE_MS = 1_000L;

    private static final Map<BlockPos, Entry> CACHE = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_REQUEST_MS = new HashMap<>();

    private ClientObserverVoxelCache() {}

    public static synchronized DamageObserverVoxelMap get(BlockPos observerPos) {
        Entry e = CACHE.get(observerPos);
        return e == null ? null : e.map;
    }

    public static synchronized long getLastUpdateMs(BlockPos observerPos) {
        Entry e = CACHE.get(observerPos);
        return e == null ? 0L : e.receivedAtMs;
    }

    public static synchronized void ensureFresh(BlockPos observerPos) {
    }

    public static synchronized void acceptPayload(BlockPos observerPos, DamageObserverVoxelMap map) {
        if (map == null) {
            CACHE.remove(observerPos);
            return;
        }
        CACHE.put(observerPos, new Entry(map, System.currentTimeMillis()));
    }

    public static synchronized void invalidate(BlockPos observerPos) {
        CACHE.remove(observerPos);
        LAST_REQUEST_MS.remove(observerPos);
    }

    public static synchronized void clearAll() {
        CACHE.clear();
        LAST_REQUEST_MS.clear();
    }

    private static final class Entry {
        final DamageObserverVoxelMap map;
        final long receivedAtMs;
        Entry(DamageObserverVoxelMap map, long receivedAtMs) {
            this.map = map;
            this.receivedAtMs = receivedAtMs;
        }
    }
}
