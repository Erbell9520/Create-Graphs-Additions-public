package com.createstats.netscan.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.createstats.netscan.NetscanEngine;
import com.createstats.netscan.NetscanHost;
import com.createstats.network.NetscanSubscribePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class NetscanClient {
    private static final long RENEW_INTERVAL_MS = 1000L;

    private static final long GROUP_EXPIRY_MS = 2500L;

    private static final String GROUP_SCREEN = "screen";

    private static final String GROUP_STUDIO = "studio";

    private static final String GROUP_FACE = "face:";

    private static final class Group {
        final LinkedHashSet<BlockPos> positions;
        long refreshedAt;

        Group(LinkedHashSet<BlockPos> positions, long now) {
            this.positions = positions;
            this.refreshedAt = now;
        }
    }

    private static final Map<String, Group> groups = new LinkedHashMap<>();

    private static final Set<BlockPos> onServer = new LinkedHashSet<>();
    private static long nextRenewAt;

    private NetscanClient() {
    }

    public static BlockPos watching() {
        for (final String key : new String[]{GROUP_SCREEN, GROUP_STUDIO}) {
            final Group g = groups.get(key);
            if (g != null && !g.positions.isEmpty()) {
                return g.positions.iterator().next();
            }
        }
        for (final Map.Entry<String, Group> e : groups.entrySet()) {
            if (e.getKey().startsWith(GROUP_FACE) && !e.getValue().positions.isEmpty()) {
                return e.getValue().positions.iterator().next();
            }
        }
        return null;
    }

    public static void beginWatch(BlockPos pos) {
        set(GROUP_SCREEN, pos == null ? List.of() : List.of(pos));
    }

    public static void endWatch() {
        set(GROUP_SCREEN, List.of());
    }

    public static void watch(Collection<BlockPos> positions) {
        set(GROUP_STUDIO, positions);
    }

    public static void watchFace(BlockPos panelPos, Collection<BlockPos> scanners) {
        set(GROUP_FACE + panelPos.asLong(), scanners);
    }

    private static void set(String group, Collection<BlockPos> positions) {
        final LinkedHashSet<BlockPos> next = new LinkedHashSet<>();
        if (positions != null) {
            for (final BlockPos pos : positions) {
                if (pos != null) {
                    next.add(pos.immutable());
                }
            }
        }
        final long now = System.currentTimeMillis();
        if (next.isEmpty()) {
            groups.remove(group);
        } else {
            final Group existing = groups.get(group);
            if (existing != null && existing.positions.equals(next)) {
                existing.refreshedAt = now;
            } else {
                groups.remove(group);
                groups.put(group, new Group(next, now));
            }
        }
        reconcile(now);
    }

    public static void tickWatch() {
        final long now = System.currentTimeMillis();
        reconcile(now);
        if (!onServer.isEmpty() && now >= nextRenewAt) {
            nextRenewAt = now + RENEW_INTERVAL_MS;
            for (final BlockPos pos : onServer) {
                send(pos, true);
            }
        }
    }

    private static void reconcile(long now) {
        groups.entrySet().removeIf(e -> now - e.getValue().refreshedAt > GROUP_EXPIRY_MS);

        final Set<BlockPos> union = new LinkedHashSet<>();
        for (final Group g : groups.values()) {
            union.addAll(g.positions);
        }
        if (union.equals(onServer)) {
            return;
        }
        for (final BlockPos gone : new ArrayList<>(onServer)) {
            if (!union.contains(gone)) {
                send(gone, false);
            }
        }
        for (final BlockPos fresh : union) {
            if (!onServer.contains(fresh)) {
                send(fresh, true);
            }
        }
        onServer.clear();
        onServer.addAll(union);
        nextRenewAt = now + RENEW_INTERVAL_MS;
    }

    private static void send(BlockPos pos, boolean active) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new NetscanSubscribePacket(pos, active));
    }

    public static void acceptLive(BlockPos pos, int hash, float[] speeds, float[] stresses,
                                  float[] capacities, boolean[] over, float[] history) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.isLoaded(pos)) {
            return;
        }
        if (mc.level.getBlockEntity(pos) instanceof NetscanHost host) {
            final NetscanEngine engine = host.scanEngine();
            engine.acceptLive(hash, speeds, stresses, capacities, over, history);
        }
    }
}
