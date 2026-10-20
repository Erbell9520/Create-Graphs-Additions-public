package com.createstats.surveyor;

import com.createstats.surveyor.core.Pathway;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class RouteEvents {
    public static final int KEEP = 64;

    public record Line(long time, UUID pathway, String text) {
    }

    private static final class Log {
        final Deque<Line> lines = new ArrayDeque<>();
        long version;
    }

    private static final Map<ResourceKey<Level>, Log> LOGS = new ConcurrentHashMap<>();

    private RouteEvents() {
    }

    public static void add(ServerLevel level, UUID pathway, String text) {
        Log log = LOGS.computeIfAbsent(level.dimension(), k -> new Log());
        synchronized (log) {
            log.lines.addFirst(new Line(level.getGameTime(), pathway, text));
            while (log.lines.size() > KEEP) log.lines.pollLast();
            log.version++;
        }
    }

    static void edited(ServerLevel level, Pathway p) {
    }

    public static long version(ServerLevel level) {
        Log log = LOGS.get(level.dimension());
        return log == null ? 0L : log.version;
    }

    public static List<Line> lines(ServerLevel level) {
        Log log = LOGS.get(level.dimension());
        if (log == null) return List.of();
        synchronized (log) {
            return new ArrayList<>(log.lines);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOGS.clear();
    }
}
