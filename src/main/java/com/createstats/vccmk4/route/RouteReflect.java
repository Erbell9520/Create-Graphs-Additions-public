package com.createstats.vccmk4.route;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.ReflectionAccessors;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteReflect {
    static final String TICKET_TYPE = "dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType";
    static final String TICKET = "dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket";
    static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    static final String SERVER_SUB_LEVEL = "dev.ryanhcode.sable.sublevel.ServerSubLevel";
    static final String STAFF_HANDLER = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler";
    public static final ResourceLocation TICKET_ID = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vehicle_route");

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    @Nullable private static volatile Object ticketType;

    private RouteReflect() {
    }

    public static void registerTicketType() {
        if (ticketType != null) return;
        try {
            Class<?> type = Class.forName(TICKET_TYPE);
            Method create = type.getMethod("create", ResourceLocation.class, Codec.class);
            ticketType = create.invoke(null, TICKET_ID, Codec.STRING);
        } catch (ClassNotFoundException e) {
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("ticketType", e.toString());
        }
    }

    public static boolean ticketsAvailable() {
        return ticketType != null;
    }

    public static boolean forceLoad(ServerLevel level, @Nullable Object serverSubLevel, UUID console, boolean hold) {
        Object type = ticketType;
        if (type == null || serverSubLevel == null) return false;
        try {
            Object container = container(level);
            if (container == null) return false;
            String name = hold ? "addForceLoadTicket" : "removeForceLoadTicket";
            Method m = method(container.getClass(), name, 3);
            if (m == null) {
                warnOnce(name, "no " + name + " on " + container.getClass().getName());
                return false;
            }
            Object r = m.invoke(container, serverSubLevel, type, console.toString());
            return !(r instanceof Boolean b) || b || hold;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("forceLoad", e.toString());
            return false;
        }
    }

    public static List<Object[]> heldTickets(ServerLevel level) {
        List<Object[]> out = new ArrayList<>();
        Object type = ticketType;
        if (type == null) return out;
        try {
            Object container = container(level);
            if (container == null) return out;
            Method all = method(container.getClass(), "getAllTickets", 0);
            if (all == null || !(all.invoke(container) instanceof Map<?, ?> map)) return out;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof UUID subLevel)) continue;
                Method tickets = method(e.getValue().getClass(), "tickets", 0);
                if (tickets == null || !(tickets.invoke(e.getValue()) instanceof Collection<?> set)) continue;
                for (Object t : set) {
                    Method typeOf = method(t.getClass(), "type", 0);
                    Method key = method(t.getClass(), "key", 0);
                    if (typeOf == null || key == null || typeOf.invoke(t) != type) continue;
                    if (key.invoke(t) instanceof String s) out.add(new Object[]{subLevel, s});
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("heldTickets", e.toString());
        }
        return out;
    }

    public static void releaseById(ServerLevel level, UUID subLevel, String console) {
        Object type = ticketType;
        if (type == null) return;
        try {
            Object container = container(level);
            if (container == null) return;
            Method get = method(container.getClass(), "getSubLevel", 1, UUID.class);
            Object sub = get == null ? null : get.invoke(container, subLevel);
            if (sub != null) {
                Method m = method(container.getClass(), "removeForceLoadTicket", 3);
                if (m != null) m.invoke(container, sub, type, console);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("releaseById", e.toString());
        }
    }

    @Nullable
    private static Object container(Level level) throws ReflectiveOperationException {
        Class<?> c = Class.forName(CONTAINER);
        for (Method m : c.getMethods()) {
            if (m.getName().equals("getContainer") && java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(level)) {
                return m.invoke(null, level);
            }
        }
        warnOnce("container", "SubLevelContainer.getContainer(Level) not found");
        return null;
    }

    @Nullable
    public static int[] plotBounds(@Nullable Object subLevel) {
        Object plot = call(subLevel, "getPlot");
        if (!(call(plot, "getBoundingBox") instanceof BoundingBox3ic b)) return null;
        return new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    @Nullable
    public static double[] worldBounds(@Nullable Object subLevel) {
        if (!(call(subLevel, "boundingBox") instanceof BoundingBox3dc b)) return null;
        return new double[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    private static volatile Object[] staffPath;
    private static volatile boolean staffMissing;

    public static boolean staffHeld(ServerLevel level, @Nullable Object serverSubLevel) {
        if (serverSubLevel == null || staffMissing) return false;
        Object[] path = staffPath;
        if (path == null) {
            Class<?> handler = ReflectionAccessors.findClass(STAFF_HANDLER);
            Class<?> session = ReflectionAccessors.findClass(STAFF_HANDLER + "$DragSession");
            Method get = handler == null ? null : ReflectionAccessors.findMethodAccepting(handler, "get", ServerLevel.class);
            Method isLocked = null;
            if (handler != null) {
                for (Method m : handler.getMethods()) {
                    if (m.getName().equals("isLocked") && m.getParameterCount() == 1) {
                        isLocked = m;
                        break;
                    }
                }
            }
            java.lang.reflect.Field sessions = null;
            java.lang.reflect.Field held = null;
            try {
                sessions = handler == null ? null : ReflectionAccessors.field(handler, "draggingSessions");
                held = session == null ? null : ReflectionAccessors.field(session, "subLevel");
            } catch (RuntimeException e) {
                warnOnce("staff", e.toString());
            }
            if (get == null || sessions == null || held == null) {
                staffMissing = true;
                if (handler != null) warnOnce("staff", "PhysicsStaffServerHandler.get, draggingSessions or DragSession.subLevel not found");
                return false;
            }
            path = new Object[]{get, isLocked, sessions, held};
            staffPath = path;
        }
        try {
            Object handler = ((Method) path[0]).invoke(null, level);
            if (handler == null) return false;
            Method isLocked = (Method) path[1];
            if (isLocked != null && isLocked.getParameterTypes()[0].isInstance(serverSubLevel)
                    && Boolean.TRUE.equals(isLocked.invoke(handler, serverSubLevel))) {
                return true;
            }
            if (((java.lang.reflect.Field) path[2]).get(handler) instanceof Map<?, ?> sessions) {
                java.lang.reflect.Field held = (java.lang.reflect.Field) path[3];
                for (Object s : sessions.values()) {
                    if (s != null && held.get(s) == serverSubLevel) return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("staff", e.toString());
        }
        return false;
    }

    @Nullable
    private static Object call(@Nullable Object target, String name) {
        if (target == null) return null;
        Method m = ReflectionAccessors.method(target.getClass(), name);
        if (m == null) {
            warnOnce(name, "no " + name + "() on " + target.getClass().getName());
            return null;
        }
        return ReflectionAccessors.invoke(m, target);
    }

    @Nullable
    private static Method method(Class<?> c, String name, int params, Class<?>... first) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != params) continue;
                if (first.length > 0 && !m.getParameterTypes()[0].isAssignableFrom(first[0])) continue;
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    public static void warnOnce(String what, String detail) {
        if (WARNED.add(what)) CreateStats.LOGGER.warn("[VCC Mk IV] route {} unavailable: {}", what, detail);
    }
}
