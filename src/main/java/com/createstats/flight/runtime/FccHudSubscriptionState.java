package com.createstats.flight.runtime;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FccHudSubscriptionState {
    private static final Map<UUID, Set<BlockPos>> SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private FccHudSubscriptionState() {
    }

    public static void set(UUID playerId, BlockPos controllerPos) {
        subscribe(playerId, controllerPos);
    }

    public static void subscribe(UUID playerId, BlockPos controllerPos) {
        if (playerId == null || controllerPos == null) {
            return;
        }
        SUBSCRIPTIONS.computeIfAbsent(playerId, key -> ConcurrentHashMap.newKeySet()).add(controllerPos.immutable());
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SUBSCRIPTIONS.remove(playerId);
    }

    public static void unsubscribe(UUID playerId, @Nullable BlockPos controllerPos) {
        if (playerId == null || controllerPos == null) {
            return;
        }
        Set<BlockPos> subscriptions = SUBSCRIPTIONS.get(playerId);
        if (subscriptions == null) {
            return;
        }
        subscriptions.remove(controllerPos);
        if (subscriptions.isEmpty()) {
            SUBSCRIPTIONS.remove(playerId);
        }
    }

    public static boolean isSubscribed(UUID playerId, BlockPos controllerPos) {
        if (playerId == null || controllerPos == null) {
            return false;
        }
        Set<BlockPos> subscriptions = SUBSCRIPTIONS.get(playerId);
        return subscriptions != null && subscriptions.contains(controllerPos);
    }

    @Nullable
    public static BlockPos subscriptionFor(UUID playerId) {
        Set<BlockPos> subscriptions = SUBSCRIPTIONS.get(playerId);
        if (subscriptions == null || subscriptions.isEmpty()) {
            return null;
        }
        return subscriptions.iterator().next();
    }
}
