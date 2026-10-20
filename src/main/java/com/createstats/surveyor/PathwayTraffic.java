package com.createstats.surveyor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class PathwayTraffic {
    public static final int STALE_TICKS = 40;

    private record Entry(RouteTrack track, long tick) {
    }

    private static final Map<ResourceKey<Level>, Map<UUID, Entry>> BY_LEVEL = new ConcurrentHashMap<>();

    private PathwayTraffic() {
    }

    public static void report(ServerLevel level, RouteTrack t) {
        BY_LEVEL.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(t.console(), new Entry(t, level.getGameTime()));
    }

    public static void forget(ServerLevel level, UUID console) {
        Map<UUID, Entry> m = BY_LEVEL.get(level.dimension());
        if (m != null) m.remove(console);
    }

    public static List<RouteTrack> on(ServerLevel level, UUID pathway) {
        List<RouteTrack> out = new ArrayList<>();
        Map<UUID, Entry> m = BY_LEVEL.get(level.dimension());
        if (m == null) return out;
        long now = level.getGameTime();
        for (Entry e : m.values()) {
            if (now - e.tick() <= STALE_TICKS && e.track().pathway().equals(pathway)) out.add(e.track());
        }
        return out;
    }

    public static int vehiclesOn(ServerLevel level, UUID pathway) {
        return on(level, pathway).size();
    }

    public static Collection<RouteTrack> all(ServerLevel level) {
        List<RouteTrack> out = new ArrayList<>();
        Map<UUID, Entry> m = BY_LEVEL.get(level.dimension());
        if (m == null) return out;
        long now = level.getGameTime();
        m.values().removeIf(e -> now - e.tick() > STALE_TICKS * 10L || e.tick() > now + 20);
        for (Entry e : m.values()) if (now - e.tick() <= STALE_TICKS) out.add(e.track());
        return out;
    }

    public static RouteTrack find(ServerLevel level, UUID console) {
        Map<UUID, Entry> m = BY_LEVEL.get(level.dimension());
        Entry e = m == null ? null : m.get(console);
        return e == null || level.getGameTime() - e.tick() > STALE_TICKS ? null : e.track();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BY_LEVEL.clear();
    }
}
