package com.createstats.surveyor;

import com.createstats.CreateStats;
import com.createstats.ModConfiguration;
import com.createstats.vccmk4.route.RouteReflect;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class RouteChunkTickets {
    public static final TicketController CONTROLLER =
            new TicketController(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vehicle_route"), RouteChunkTickets::validate);

    private static final Map<UUID, Held> HELD = new ConcurrentHashMap<>();

    private record Held(String dimension, LongSet chunks) {
    }

    private RouteChunkTickets() {
    }

    @SubscribeEvent
    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(RouteReflect::registerTicketType);
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        for (UUID owner : new java.util.ArrayList<>(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        PathwayStore store = PathwayStore.get(level);
        for (UUID console : new java.util.ArrayList<>(store.running().keySet())) store.setRunning(console, null);
    }

    public static void sweepSable(ServerLevel level) {
        for (Object[] held : RouteReflect.heldTickets(level)) {
            RouteReflect.releaseById(level, (UUID) held[0], (String) held[1]);
        }
    }

    public static boolean budgetFor(UUID console) {
        if (HELD.containsKey(console)) return true;
        return HELD.size() < maxVehicles();
    }

    public static void hold(ServerLevel level, UUID console, LongSet chunks) {
        if (!enabled()) {
            release(level, console);
            return;
        }
        String dim = level.dimension().location().toString();
        Held before = HELD.get(console);
        if (before != null && !before.dimension().equals(dim)) {
            release(level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(before.dimension()))), console);
            before = null;
        }
        LongSet had = before == null ? new LongOpenHashSet() : before.chunks();
        LongSet now = new LongOpenHashSet(chunks);
        for (long c : now) {
            if (had.contains(c)) continue;
            try {
                CONTROLLER.forceChunk(level, console, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
            } catch (RuntimeException e) {
                RouteReflect.warnOnce("chunkTicket", e.toString());
                return;
            }
        }
        for (long c : had) {
            if (now.contains(c)) continue;
            CONTROLLER.forceChunk(level, console, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
        }
        HELD.put(console, new Held(dim, now));
    }

    public static void release(ServerLevel level, UUID console) {
        Held h = HELD.remove(console);
        if (h == null || level == null) return;
        for (long c : h.chunks()) {
            try {
                CONTROLLER.forceChunk(level, console, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
            } catch (RuntimeException e) {
                RouteReflect.warnOnce("chunkTicket", e.toString());
            }
        }
    }

    public static boolean holding(UUID console) {
        return HELD.containsKey(console);
    }

    public static int holders() {
        return HELD.size();
    }

    public static LongSet around(double[][] points, int radius) {
        LongSet out = new LongOpenHashSet();
        for (double[] p : points) {
            int cx = (int) Math.floor(p[0]) >> 4;
            int cz = (int) Math.floor(p[1]) >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) out.add(ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        return out;
    }

    public static boolean enabled() {
        try {
            return ModConfiguration.COMMON.routeChunkLoading.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static int radius() {
        try {
            return ModConfiguration.COMMON.routeChunkRadius.get();
        } catch (RuntimeException e) {
            return 1;
        }
    }

    static int maxVehicles() {
        try {
            return ModConfiguration.COMMON.maxRouteVehicles.get();
        } catch (RuntimeException e) {
            return 8;
        }
    }

    public static void forgetAll() {
        HELD.clear();
    }

    @SuppressWarnings("unused")
    private static Set<UUID> consoles() {
        return HELD.keySet();
    }
}
