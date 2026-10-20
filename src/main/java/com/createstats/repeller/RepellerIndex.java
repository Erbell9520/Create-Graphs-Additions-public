package com.createstats.repeller;

import com.createstats.CreateStats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RepellerIndex {
    private static final class LevelIndex {
        final Map<Long, SpawnIndex.Entry<EntityType<?>>> live = new HashMap<>();
        volatile SpawnIndex<EntityType<?>> snapshot = SpawnIndex.empty();
    }

    private static final Map<ResourceKey<Level>, LevelIndex> LEVELS = new ConcurrentHashMap<>();
    private static volatile boolean anyActive;

    private RepellerIndex() {
    }

    public static boolean anyActive() {
        return anyActive;
    }

    public static SpawnIndex<EntityType<?>> of(ResourceKey<Level> dimension) {
        final LevelIndex li = LEVELS.get(dimension);
        return li == null ? SpawnIndex.empty() : li.snapshot;
    }

    public static void put(ServerLevel level, SpawnIndex.Entry<EntityType<?>> entry) {
        if (entry.type() == null || entry.radius() <= 0.0) {
            remove(level, entry.id());
            return;
        }
        final LevelIndex li = LEVELS.computeIfAbsent(level.dimension(), k -> new LevelIndex());
        li.live.put(entry.id(), entry);
        publish(li);
    }

    public static void remove(ServerLevel level, long id) {
        final LevelIndex li = LEVELS.get(level.dimension());
        if (li != null && li.live.remove(id) != null) {
            publish(li);
        }
    }

    public static int size(ResourceKey<Level> dimension) {
        return of(dimension).size();
    }

    private static void publish(LevelIndex li) {
        li.snapshot = SpawnIndex.of(li.live.values());
        boolean any = false;
        for (LevelIndex other : LEVELS.values()) {
            if (!other.snapshot.isEmpty()) {
                any = true;
                break;
            }
        }
        anyActive = any;
    }

    @EventBusSubscriber(modid = CreateStats.MOD_ID)
    public static final class Lifecycle {
        private Lifecycle() {
        }

        @SubscribeEvent
        public static void onStopped(ServerStoppedEvent event) {
            LEVELS.clear();
            anyActive = false;
        }
    }
}
