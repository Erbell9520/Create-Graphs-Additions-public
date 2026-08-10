package com.createstats.client.battle;

import com.createstats.battle.DamageObserverVoxelMap;
import com.createstats.network.RequestObserverVoxelMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** [1] */
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

    /** [2] */
    public static synchronized void ensureFresh(BlockPos observerPos) {
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(observerPos);
        if (e != null && now - e.receivedAtMs < TTL_MS) {
            return;
        }
        Long last = LAST_REQUEST_MS.get(observerPos);
        if (last != null && now - last < REQUEST_THROTTLE_MS) {
            return;
        }
        LAST_REQUEST_MS.put(observerPos, now);
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new RequestObserverVoxelMapPacket(observerPos));
        }
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
