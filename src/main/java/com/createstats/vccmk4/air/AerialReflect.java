package com.createstats.vccmk4.air;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.ReflectionAccessors;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class AerialReflect {
    static final String BURNER = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity";
    static final String VENT = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity";
    static final String PROPELLER = "dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller";
    static final String PHYSICS_DATA = "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData";

    static final String SUB_LEVEL_CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    static final String PIPELINE = "dev.ryanhcode.sable.api.physics.PhysicsPipeline";
    static final String SERVER_SUB_LEVEL = "dev.ryanhcode.sable.sublevel.ServerSubLevel";

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Method airPressure;
    private static volatile boolean airPressureMissing;
    private static volatile Method[] wakePath;
    private static volatile boolean wakeMissing;

    private AerialReflect() {
    }

    static boolean is(String className, @Nullable Object o) {
        if (o == null) return false;
        Class<?> c = ReflectionAccessors.findClass(className);
        return c != null && c.isInstance(o);
    }

    static boolean isBurner(@Nullable BlockEntity be) {
        return is(BURNER, be);
    }

    static boolean isVent(@Nullable BlockEntity be) {
        return is(VENT, be);
    }

    @Nullable
    static Object balloonOf(BlockEntity heater) {
        return call(heater, "getBalloon");
    }

    static double fullOutput(BlockEntity heater) {
        boolean vent = isVent(heater);
        Field f = ReflectionAccessors.field(heater.getClass(), vent ? "steamAmountBehaviour" : "hotAirAmountBehaviour");
        Object b = ReflectionAccessors.read(f, heater);
        if (!(b instanceof ScrollValueBehaviour scroll)) {
            warnOnce("dial", "no amount behaviour on " + heater.getClass().getName());
            return 0.0;
        }
        double dial = scroll.getValue();
        if (!vent) return dial;
        Object eff = ReflectionAccessors.read(ReflectionAccessors.field(heater.getClass(), "efficiency"), heater);
        return eff instanceof Number n ? dial * Math.max(0.0, Math.min(1.0, n.doubleValue())) : 0.0;
    }

    static double gasOutput(BlockEntity heater) {
        return number(call(heater, "getGasOutput"), 0.0);
    }

    static int signal(BlockEntity heater) {
        if (isVent(heater)) {
            Object v = ReflectionAccessors.read(ReflectionAccessors.field(heater.getClass(), "signalStrength"), heater);
            return v instanceof Number n ? n.intValue() : 0;
        }
        return (int) number(call(heater, "getSignalStrength"), 0.0);
    }

    static void setSignal(BlockEntity heater, int signal) {
        boolean vent = isVent(heater);

        Class<?> owner = ReflectionAccessors.findClass(vent ? VENT : BURNER);
        Method m = owner == null ? null : ReflectionAccessors.method(owner, vent ? "updateSignal" : "setSignalStrength", int.class);
        if (m == null) {
            warnOnce(vent ? "ventSignal" : "burnerSignal", "no signal setter on " + heater.getClass().getName());
            return;
        }
        ReflectionAccessors.invoke(m, heater, signal);
    }

    static void handBack(BlockEntity heater) {
        if (isVent(heater)) {
            Object raw = ReflectionAccessors.read(ReflectionAccessors.field(heater.getClass(), "rawSignalStrength"), heater);
            setSignal(heater, raw instanceof Number n ? n.intValue() : 0);
            return;
        }
        Method m = ReflectionAccessors.method(heater.getClass(), "updateSignal");
        if (m == null) {
            warnOnce("burnerHandBack", "no updateSignal() on " + heater.getClass().getName());
            return;
        }
        ReflectionAccessors.invoke(m, heater);
    }

    static int capacity(Object balloon) {
        return (int) number(call(balloon, "getCapacity"), 0.0);
    }

    static double filled(Object balloon) {
        return number(call(balloon, "getTotalFilledVolume"), 0.0);
    }

    static double target(Object balloon) {
        return number(call(balloon, "getTotalTargetVolume"), 0.0);
    }

    static double lift(Object balloon) {
        return number(call(balloon, "getTotalLift"), Double.NaN);
    }

    static double volumeChange(Object balloon) {
        return number(call(balloon, "getTotalVolumeChange"), Double.NaN);
    }

    @Nullable
    static Vector3d centre(Object balloon) {
        Object avg = ReflectionAccessors.read(ReflectionAccessors.field(balloon.getClass(), "averagePosition"), balloon);
        Object origin = ReflectionAccessors.read(ReflectionAccessors.field(balloon.getClass(), "physicsOrigin"), balloon);
        if (avg instanceof Vector3dc a && origin instanceof Vector3dc o) return new Vector3d(a).add(o);
        warnOnce("balloonCentre", "averagePosition or physicsOrigin unreadable on " + balloon.getClass().getName());
        return null;
    }

    static boolean isPropeller(@Nullable BlockEntity be) {
        return is(PROPELLER, be);
    }

    static double staticThrust(BlockEntity prop) {
        return number(call(prop, "getThrust"), Double.NaN);
    }

    static double airflow(BlockEntity prop) {
        return number(call(prop, "getAirflow"), Double.NaN);
    }

    static double airPressure(Level level, Vector3d world) {
        Method m = airPressure;
        if (m == null) {
            if (airPressureMissing) return Double.NaN;
            Class<?> c = ReflectionAccessors.findClass(PHYSICS_DATA);
            m = c == null ? null : ReflectionAccessors.findMethodAccepting(c, "getAirPressure", Level.class, Vector3d.class);
            if (m == null) {
                airPressureMissing = true;
                warnOnce("airPressure", "DimensionPhysicsData.getAirPressure(Level, Vector3dc) not found");
                return Double.NaN;
            }
            airPressure = m;
        }
        return number(ReflectionAccessors.invoke(m, null, level, world), Double.NaN);
    }

    static void wake(Level level, @Nullable Object serverSubLevel) {
        if (serverSubLevel == null || wakeMissing) return;
        Method[] path = wakePath;
        if (path == null) {
            Class<?> container = ReflectionAccessors.findClass(SUB_LEVEL_CONTAINER);
            Class<?> pipelineClass = ReflectionAccessors.findClass(PIPELINE);
            Class<?> ssl = ReflectionAccessors.findClass(SERVER_SUB_LEVEL);
            Method getContainer = null;
            if (container != null) {
                for (Method m : container.getMethods()) {
                    if (m.getName().equals("getContainer") && java.lang.reflect.Modifier.isStatic(m.getModifiers())
                            && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(level)) {
                        getContainer = m;
                        break;
                    }
                }
            }
            Method wakeUp = pipelineClass == null || ssl == null ? null : ReflectionAccessors.findMethodAccepting(pipelineClass, "wakeUp", ssl);
            if (getContainer == null || wakeUp == null) {
                wakeMissing = true;
                warnOnce("wake", "SubLevelContainer.getContainer or PhysicsPipeline.wakeUp not found");
                return;
            }
            path = new Method[] {getContainer, wakeUp};
            wakePath = path;
        }
        try {
            Object c = path[0].invoke(null, level);
            if (c == null) return;
            Object system = c.getClass().getMethod("physicsSystem").invoke(c);
            if (system == null) return;
            Object pipeline = system.getClass().getMethod("getPipeline").invoke(system);
            if (pipeline == null || !path[1].getDeclaringClass().isInstance(pipeline)
                    || !path[1].getParameterTypes()[0].isInstance(serverSubLevel)) return;
            path[1].invoke(pipeline, serverSubLevel);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("wake", e.toString());
        }
    }

    static final String STAFF_HANDLER = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler";

    static final int STAFF_NONE = 0;
    static final int STAFF_DRAGGED = 1;
    static final int STAFF_LOCKED = 2;
    private static volatile Object[] staffPath;
    private static volatile boolean staffMissing;

    static int staff(ServerLevel level, @Nullable Object serverSubLevel) {
        if (serverSubLevel == null || staffMissing) return STAFF_NONE;
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
                warnOnce("staff", "PhysicsStaffServerHandler.get, draggingSessions or DragSession.subLevel not found");
                return STAFF_NONE;
            }
            path = new Object[] {get, isLocked, sessions, held};
            staffPath = path;
        }
        try {
            Object handler = ((Method) path[0]).invoke(null, level);
            if (handler == null) return STAFF_NONE;
            Method isLocked = (Method) path[1];
            if (isLocked != null && isLocked.getParameterTypes()[0].isInstance(serverSubLevel)
                    && Boolean.TRUE.equals(isLocked.invoke(handler, serverSubLevel))) {
                return STAFF_LOCKED;
            }
            if (((java.lang.reflect.Field) path[2]).get(handler) instanceof Map<?, ?> sessions) {
                java.lang.reflect.Field held = (java.lang.reflect.Field) path[3];
                for (Object s : sessions.values()) {
                    if (s != null && held.get(s) == serverSubLevel) return STAFF_DRAGGED;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("staff", e.toString());
        }
        return STAFF_NONE;
    }

    @Nullable
    static double[] worldBounds(@Nullable Object subLevel) {
        if (!(call(subLevel, "boundingBox") instanceof BoundingBox3dc b)) return null;
        return new double[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    @Nullable
    static int[] plotBounds(@Nullable Object subLevel) {
        Object plot = call(subLevel, "getPlot");
        if (!(call(plot, "getBoundingBox") instanceof BoundingBox3ic b)) return null;
        return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
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

    private static double number(@Nullable Object o, double fallback) {
        return o instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : fallback;
    }

    static void warnOnce(String what, String detail) {
        if (WARNED.add(what)) CreateStats.LOGGER.warn("[VCC Mk IV Aerial] {} unavailable: {}", what, detail);
    }
}
