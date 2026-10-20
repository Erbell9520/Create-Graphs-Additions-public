package com.createstats.train.smart;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.createstats.network.RailSnapshotPacket;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RailNetworkService {
    private RailNetworkService() {
    }

    private static final int LIVE_INTERVAL_TICKS = 5;

    private static final int SUBSCRIPTION_TTL_TICKS = 100;

    private static final Map<UUID, Subscriber> SUBSCRIBERS = new HashMap<>();

    private static final long NEVER_SCANNED = Long.MIN_VALUE;

    private static long lastScanTick = NEVER_SCANNED;
    private static RailSnapshot lastLive = RailSnapshot.EMPTY;

    private static final class Subscriber {
        long expiresAt;

        int knownChecksum = Integer.MIN_VALUE;
    }

    public static void subscribe(ServerPlayer player) {
        if (player == null) {
            return;
        }
        final long now = player.level().getGameTime();
        SUBSCRIBERS.computeIfAbsent(player.getUUID(), id -> new Subscriber())
                .expiresAt = now + SUBSCRIPTION_TTL_TICKS;
    }

    public static void unsubscribe(ServerPlayer player) {
        if (player != null) {
            SUBSCRIBERS.remove(player.getUUID());
        }
    }

    public static boolean hasSubscribers() {
        return !SUBSCRIBERS.isEmpty();
    }

    public static RailSnapshot current() {
        return lastLive;
    }

    public static void tick(Level level) {
        if (!(level instanceof ServerLevel server) || SUBSCRIBERS.isEmpty()) {
            return;
        }
        final long now = server.getGameTime();

        if (server.dimension() != Level.OVERWORLD) {
            return;
        }

        expire(now);
        if (SUBSCRIBERS.isEmpty() || !dueForScan(now)) {
            return;
        }
        lastScanTick = now;

        final boolean anyStale = anyStale();
        final RailSnapshot snapshot = RailScanner.scan(server, anyStale);
        if (!anyStale) {
            snapshot.adoptTopology(lastLive);
        }
        lastLive = snapshot;

        final RailSnapshotPacket live = new RailSnapshotPacket(snapshot, false);
        final RailSnapshotPacket full = anyStale
                ? new RailSnapshotPacket(snapshot, true) : null;

        for (final Map.Entry<UUID, Subscriber> entry : SUBSCRIBERS.entrySet()) {
            final ServerPlayer player = server.getServer().getPlayerList()
                    .getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            final Subscriber sub = entry.getValue();
            if (full != null && sub.knownChecksum != snapshot.topologyChecksum) {
                PacketDistributor.sendToPlayer(player, full);
                sub.knownChecksum = snapshot.topologyChecksum;
            } else {
                PacketDistributor.sendToPlayer(player, live);
            }
        }
    }

    private static boolean dueForScan(long now) {
        if (lastScanTick == NEVER_SCANNED) {
            return true;
        }
        final long sinceScan = now - lastScanTick;
        return sinceScan < 0L || sinceScan >= LIVE_INTERVAL_TICKS;
    }

    private static boolean anyStale() {
        for (final Subscriber sub : SUBSCRIBERS.values()) {
            if (sub.knownChecksum != lastLive.topologyChecksum) {
                return true;
            }
        }
        return false;
    }

    private static void expire(long now) {
        final Iterator<Map.Entry<UUID, Subscriber>> it = SUBSCRIBERS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt <= now) {
                it.remove();
            }
        }
    }

    public static void reset() {
        SUBSCRIBERS.clear();
        lastLive = RailSnapshot.EMPTY;
        lastScanTick = NEVER_SCANNED;
        RailScanner.reset();
    }
}
