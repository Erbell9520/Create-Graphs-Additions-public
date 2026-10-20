package com.createstats.surveyor;

import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.PathwayCodec;
import com.createstats.surveyor.network.EditHistoryPacket;
import com.createstats.surveyor.network.PathwayHealthPacket;
import com.createstats.surveyor.network.PathwaySyncPacket;
import com.createstats.surveyor.network.RouteTrackPacket;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class PathwaySync {
    public static final int VIEW_TIMEOUT = 60;
    public static final int TRACK_INTERVAL = 5;

    private static final class View {
        ResourceKey<Level> dimension;
        long last;
        boolean board;
        long logVersion = -1;
        UUID select;
    }

    private static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();

    private PathwaySync() {
    }

    public static void ping(ServerPlayer player, boolean looking, boolean board) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!looking) {
            VIEWS.remove(player.getUUID());
            return;
        }
        View v = VIEWS.get(player.getUUID());
        boolean fresh = v == null || v.dimension != level.dimension() || level.getGameTime() - v.last > VIEW_TIMEOUT;
        if (v == null) {
            v = new View();
            VIEWS.put(player.getUUID(), v);
        }
        v.dimension = level.dimension();
        v.last = level.getGameTime();
        if (board && !v.board) v.logVersion = -1;
        v.board = board;
        if (fresh) sendAll(player, level);
    }

    private static boolean looking(ServerPlayer player, ServerLevel level) {
        View v = VIEWS.get(player.getUUID());
        return v != null && v.dimension == level.dimension() && level.getGameTime() - v.last <= VIEW_TIMEOUT;
    }

    private static void sendAll(ServerPlayer player, ServerLevel level) {
        Collection<Pathway> all = PathwayStore.get(level).all();
        List<byte[]> bytes = new ArrayList<>(all.size());
        List<String> owners = new ArrayList<>(all.size());
        for (Pathway p : all) {
            bytes.add(PathwayCodec.encode(p));
            owners.add(ownerName(level, p.owner()));
        }
        PacketDistributor.sendToPlayer(player, new PathwaySyncPacket(true, bytes, owners, List.of(), takeSelect(player)));
        for (Pathway p : all) {
            byte[] health = PathwaySurveyor.healthBytes(level, p);
            if (health != null) PacketDistributor.sendToPlayer(player, new PathwayHealthPacket(p.id(), p.version(), health));
            PathwayTerrain.Tile tile = PathwayTerrain.tile(level, p.id());
            if (tile != null) PacketDistributor.sendToPlayer(player, PathwayTerrain.packet(p.id(), tile));
        }
    }

    static void terrain(ServerLevel level, UUID pathway, PathwayTerrain.Tile tile) {
        for (ServerPlayer player : level.players()) {
            if (looking(player, level)) PacketDistributor.sendToPlayer(player, PathwayTerrain.packet(pathway, tile));
        }
    }

    public static void health(ServerLevel level, Pathway p, byte[] items) {
        PathwayHealthPacket packet = null;
        for (ServerPlayer player : level.players()) {
            if (!looking(player, level)) continue;
            if (packet == null) packet = new PathwayHealthPacket(p.id(), p.version(), items);
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static void history(ServerPlayer player, List<String> undo, List<String> redo) {
        PacketDistributor.sendToPlayer(player, new EditHistoryPacket(undo, redo));
    }

    public static void changed(ServerLevel level, Pathway p) {
        byte[] bytes = PathwayCodec.encode(p);
        String owner = ownerName(level, p.owner());
        for (ServerPlayer player : level.players()) {
            if (!looking(player, level) && !pendingSelect(player, p.id())) continue;
            UUID select = pendingSelect(player, p.id()) ? takeSelect(player) : new UUID(0, 0);
            PacketDistributor.sendToPlayer(player, new PathwaySyncPacket(false, List.of(bytes), List.of(owner), List.of(), select));
        }
    }

    public static void removed(ServerLevel level, UUID id) {
        for (ServerPlayer player : level.players()) {
            if (looking(player, level)) removedTo(player, id);
        }
    }

    public static void sendTo(ServerPlayer player, Pathway p) {
        if (!(player.level() instanceof ServerLevel level)) return;
        PacketDistributor.sendToPlayer(player, new PathwaySyncPacket(false, List.of(PathwayCodec.encode(p)),
                List.of(ownerName(level, p.owner())), List.of(), new UUID(0, 0)));
    }

    public static void removedTo(ServerPlayer player, UUID id) {
        PacketDistributor.sendToPlayer(player, new PathwaySyncPacket(false, List.of(), List.of(), List.of(id), new UUID(0, 0)));
    }

    public static void select(ServerPlayer player, UUID id) {
        View v = VIEWS.computeIfAbsent(player.getUUID(), k -> new View());
        v.select = id;
    }

    private static boolean pendingSelect(ServerPlayer player, UUID id) {
        View v = VIEWS.get(player.getUUID());
        return v != null && id.equals(v.select);
    }

    private static UUID takeSelect(ServerPlayer player) {
        View v = VIEWS.get(player.getUUID());
        if (v == null || v.select == null) return new UUID(0, 0);
        UUID s = v.select;
        v.select = null;
        return s;
    }

    public static String ownerName(ServerLevel level, UUID owner) {
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        try {
            var cache = level.getServer().getProfileCache();
            if (cache != null) {
                Optional<GameProfile> profile = cache.get(owner);
                if (profile.isPresent()) return profile.get().getName();
            }
        } catch (RuntimeException ignored) {
        }
        return "someone";
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now % TRACK_INTERVAL != 0 || VIEWS.isEmpty()) return;
        List<ServerPlayer> watchers = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (looking(p, level)) watchers.add(p);
        if (watchers.isEmpty()) return;
        List<RouteTrack> tracks = new ArrayList<>(PathwayTraffic.all(level));
        long logVersion = RouteEvents.version(level);
        List<RouteEvents.Line> log = null;
        for (ServerPlayer p : watchers) {
            View v = VIEWS.get(p.getUUID());
            boolean sendLog = v != null && v.board && v.logVersion != logVersion;
            if (sendLog) {
                if (log == null) log = RouteEvents.lines(level);
                v.logVersion = logVersion;
            }
            PacketDistributor.sendToPlayer(p, new RouteTrackPacket(now, tracks, sendLog ? log : List.of(), sendLog));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWS.remove(event.getEntity().getUUID());
        PathwayOps.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        VIEWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VIEWS.clear();
    }
}
