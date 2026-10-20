package com.createstats.surveyor;

import com.createstats.surveyor.core.PathNetwork;
import com.createstats.surveyor.core.Pathway;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// holds lapse if not renewed, a broken vehicle never locks a junction forever
@EventBusSubscriber(modid = "create_stats")
public final class PathwayNetworks {
    public static final int STALE_TICKS = 40;

    private record Built(long key, PathNetwork net) {
    }

    private record Hold(UUID console, String name, long tick) {
    }

    private static final Map<ServerLevel, Built> NETS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<Long, Hold>> HOLDS = new WeakHashMap<>();

    private PathwayNetworks() {
    }

    public static PathNetwork of(ServerLevel level) {
        Collection<Pathway> all = PathwayStore.get(level).all();
        long key = 1469598103934665603L;
        List<Pathway> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> a.id().compareTo(b.id()));
        for (Pathway p : sorted) {
            key = (key ^ p.id().getMostSignificantBits()) * 1099511628211L;
            key = (key ^ p.id().getLeastSignificantBits()) * 1099511628211L;
            key = (key ^ p.version()) * 1099511628211L;
        }
        Built b = NETS.get(level);
        if (b == null || b.key() != key) {
            b = new Built(key, PathNetwork.of(sorted));
            NETS.put(level, b);
        }
        return b.net();
    }

    private static long place(PathNetwork.Zone z) {
        return ((long) Math.round(z.x()) << 32) ^ (Math.round(z.z()) & 0xFFFFFFFFL);
    }

    @Nullable
    public static String reserve(ServerLevel level, PathNetwork.Zone z, UUID console, String name, long now) {
        Map<Long, Hold> m = HOLDS.computeIfAbsent(level, k -> new HashMap<>());
        long key = place(z);
        Hold h = m.get(key);
        if (h != null && !h.console().equals(console) && now - h.tick() <= STALE_TICKS && h.tick() <= now) return h.name();
        m.put(key, new Hold(console, name, now));
        return null;
    }

    public static void release(ServerLevel level, PathNetwork.Zone z, UUID console) {
        Map<Long, Hold> m = HOLDS.get(level);
        if (m == null) return;
        long key = place(z);
        Hold h = m.get(key);
        if (h != null && h.console().equals(console)) m.remove(key);
    }

    public static void releaseAll(ServerLevel level, UUID console) {
        Map<Long, Hold> m = HOLDS.get(level);
        if (m != null) m.values().removeIf(h -> h.console().equals(console));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NETS.clear();
        HOLDS.clear();
    }
}
