package com.createstats.flight.bridge;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SableContainers {
    static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    static final String SERVER_CONTAINER = "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer";
    static final String SUB_LEVEL = "dev.ryanhcode.sable.sublevel.SubLevel";
    static final String SERVER_SUB_LEVEL = "dev.ryanhcode.sable.sublevel.ServerSubLevel";
    static final String PLOT = "dev.ryanhcode.sable.sublevel.plot.LevelPlot";
    static final String TICKET_TYPE = "dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType";
    static final String HOLDING_MAP = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap";

    private record Handles(@Nullable Class<?> serverContainer, @Nullable MethodHandle getContainer,
                           @Nullable MethodHandle getAllSubLevels, @Nullable MethodHandle getSubLevel,
                           @Nullable MethodHandle inBounds, @Nullable MethodHandle getPlot,
                           @Nullable MethodHandle getAllTickets, @Nullable MethodHandle addTicket,
                           @Nullable MethodHandle removeTicket, @Nullable MethodHandle holdingMap) {
    }

    private static volatile Handles handles;
    private static volatile boolean missing;
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private SableContainers() {
    }

    public static boolean present() {
        return handles() != null;
    }

    @Nullable
    public static Object of(@Nullable Level level) {
        Handles h = handles();
        if (h == null || level == null || h.getContainer() == null) return null;
        try {
            return h.getContainer().invoke(level);
        } catch (Throwable t) {
            return failed("getContainer", t);
        }
    }

    public static List<Object> subLevels(@Nullable Level level) {
        Handles h = handles();
        Object container = of(level);
        if (h == null || container == null || h.getAllSubLevels() == null) return List.of();
        try {
            Object all = h.getAllSubLevels().invoke(container);
            if (!(all instanceof Iterable<?> it)) return List.of();
            List<Object> out = new ArrayList<>();
            for (Object s : it) {
                if (s != null) out.add(s);
            }
            return out;
        } catch (Throwable t) {
            failed("getAllSubLevels", t);
            return List.of();
        }
    }

    @Nullable
    public static Object subLevel(@Nullable Level level, @Nullable UUID id) {
        Handles h = handles();
        Object container = of(level);
        if (h == null || container == null || id == null || h.getSubLevel() == null) return null;
        try {
            return h.getSubLevel().invoke(container, id);
        } catch (Throwable t) {
            return failed("getSubLevel", t);
        }
    }

    public static boolean inBounds(@Nullable Level level, BlockPos pos) {
        Handles h = handles();
        Object container = of(level);
        if (h == null || container == null || pos == null || h.inBounds() == null) return false;
        try {
            return Boolean.TRUE.equals(h.inBounds().invoke(container, pos));
        } catch (Throwable t) {
            failed("inBounds", t);
            return false;
        }
    }

    @Nullable
    public static Object plot(@Nullable Level level, ChunkPos chunk) {
        Handles h = handles();
        Object container = of(level);
        if (h == null || container == null || chunk == null || h.getPlot() == null) return null;
        try {
            return h.getPlot().invoke(container, chunk);
        } catch (Throwable t) {
            return failed("getPlot", t);
        }
    }

    @Nullable
    public static Map<?, ?> allTickets(@Nullable Level level) {
        Handles h = handles();
        Object container = serverContainer(h, level);
        if (container == null || h.getAllTickets() == null) return null;
        try {
            return h.getAllTickets().invoke(container) instanceof Map<?, ?> m ? m : null;
        } catch (Throwable t) {
            failed("getAllTickets", t);
            return null;
        }
    }

    @Nullable
    public static Boolean forceLoadTicket(@Nullable Level level, @Nullable Object serverSubLevel, @Nullable Object type,
                                          Object key, boolean add) {
        Handles h = handles();
        Object container = serverContainer(h, level);
        MethodHandle m = h == null ? null : add ? h.addTicket() : h.removeTicket();
        if (container == null || m == null || serverSubLevel == null || type == null) return null;
        try {
            return m.invoke(container, serverSubLevel, type, key) instanceof Boolean b ? b : null;
        } catch (Throwable t) {
            failed(add ? "addForceLoadTicket" : "removeForceLoadTicket", t);
            return null;
        }
    }

    @Nullable
    public static Object holdingChunkMap(@Nullable Level level) {
        Handles h = handles();
        Object container = serverContainer(h, level);
        if (container == null || h.holdingMap() == null) return null;
        try {
            return h.holdingMap().invoke(container);
        } catch (Throwable t) {
            return failed("getHoldingChunkMap", t);
        }
    }

    @Nullable
    private static Object serverContainer(@Nullable Handles h, @Nullable Level level) {
        if (h == null || h.serverContainer() == null || level == null || level.isClientSide()) return null;
        Object container = of(level);
        return h.serverContainer().isInstance(container) ? container : null;
    }

    @Nullable
    private static Handles handles() {
        Handles h = handles;
        if (h != null || missing) return h;
        Class<?> container = ReflectionAccessors.findClass(CONTAINER);
        if (container == null) {
            missing = true;
            return null;
        }
        Class<?> serverContainer = ReflectionAccessors.findClass(SERVER_CONTAINER);
        Class<?> subLevel = ReflectionAccessors.findClass(SUB_LEVEL);
        Class<?> serverSubLevel = ReflectionAccessors.findClass(SERVER_SUB_LEVEL);
        Class<?> plot = ReflectionAccessors.findClass(PLOT);
        Class<?> ticketType = ReflectionAccessors.findClass(TICKET_TYPE);
        Class<?> holdingMap = ReflectionAccessors.findClass(HOLDING_MAP);
        h = new Handles(serverContainer,
                ForeignHandles.findStatic(container, "getContainer", container, Level.class),
                ForeignHandles.findVirtual(container, "getAllSubLevels", List.class),
                subLevel == null ? null : ForeignHandles.findVirtual(container, "getSubLevel", subLevel, UUID.class),
                ForeignHandles.findVirtual(container, "inBounds", boolean.class, BlockPos.class),
                plot == null ? null : ForeignHandles.findVirtual(container, "getPlot", plot, ChunkPos.class),
                serverContainer == null ? null : ForeignHandles.findVirtual(serverContainer, "getAllTickets", Map.class),
                ticket(serverContainer, "addForceLoadTicket", serverSubLevel, ticketType),
                ticket(serverContainer, "removeForceLoadTicket", serverSubLevel, ticketType),
                serverContainer == null || holdingMap == null ? null
                        : ForeignHandles.findVirtual(serverContainer, "getHoldingChunkMap", holdingMap));
        List<String> lost = new ArrayList<>();
        if (h.getContainer() == null) lost.add("getContainer");
        if (h.getAllSubLevels() == null) lost.add("getAllSubLevels");
        if (h.getSubLevel() == null) lost.add("getSubLevel");
        if (h.inBounds() == null) lost.add("inBounds");
        if (h.getPlot() == null) lost.add("getPlot");
        if (h.getAllTickets() == null) lost.add("getAllTickets");
        if (h.addTicket() == null) lost.add("addForceLoadTicket");
        if (h.removeTicket() == null) lost.add("removeForceLoadTicket");
        if (h.holdingMap() == null) lost.add("getHoldingChunkMap");
        if (!lost.isEmpty()) warnOnce("handles", "not found in this Sable: " + String.join(", ", lost));
        handles = h;
        return h;
    }

    @Nullable
    private static MethodHandle ticket(@Nullable Class<?> serverContainer, String name, @Nullable Class<?> serverSubLevel,
                                       @Nullable Class<?> ticketType) {
        if (serverContainer == null || serverSubLevel == null || ticketType == null) return null;
        return ForeignHandles.findVirtual(serverContainer, name, boolean.class, serverSubLevel, ticketType, Object.class);
    }

    @Nullable
    private static Object failed(String what, Throwable t) {
        if (ForeignHandles.fatal(t)) throw (Error) t;
        warnOnce(what, t.toString());
        return null;
    }

    private static void warnOnce(String what, String detail) {
        if (WARNED.add(what)) CreateStats.LOGGER.warn("[Sable] container {} unavailable: {}", what, detail);
    }
}
