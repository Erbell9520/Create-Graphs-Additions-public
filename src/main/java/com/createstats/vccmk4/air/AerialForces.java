package com.createstats.vccmk4.air;

import com.createstats.aerial.AerialSnapshot;
import com.createstats.compat.SimulatedCompatHelper;
import com.createstats.flight.bridge.ReflectionAccessors;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// no forces = every flight page figure said "no forces reported" in game while the offline sheets looked great
final class AerialForces {
    private static final String PHYSICS_SYSTEM = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem";

    private AerialForces() {
    }

    static void keepTracking(@Nullable Object serverSubLevel) {
        if (serverSubLevel == null) return;
        Method m = ReflectionAccessors.method(serverSubLevel.getClass(), "enableIndividualQueuedForcesTracking", boolean.class);
        if (m == null) {
            AerialReflect.warnOnce("forceTracking", "no enableIndividualQueuedForcesTracking(boolean) on "
                    + serverSubLevel.getClass().getName());
            return;
        }
        ReflectionAccessors.invoke(m, serverSubLevel, true);
    }

    static List<AerialSnapshot.Force> read(Level level, @Nullable Object serverSubLevel, Quaterniond bodyToWorld,
                                           Vector3d comPlot, Vector3d comWorld) {
        if (serverSubLevel == null) return List.of();
        Method groupsOf = ReflectionAccessors.method(serverSubLevel.getClass(), "getQueuedForceGroups");
        if (groupsOf == null) {
            AerialReflect.warnOnce("forceGroups", "no getQueuedForceGroups() on " + serverSubLevel.getClass().getName());
            return List.of();
        }
        if (!(ReflectionAccessors.invoke(groupsOf, serverSubLevel) instanceof Map<?, ?> groups) || groups.isEmpty()) {
            return List.of();
        }
        double dt = 0.05 / substeps(level);
        Map<String, Sum> sums = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : groups.entrySet()) {
            String key = SimulatedCompatHelper.forceGroupKey(e.getKey());
            Object group = e.getValue();
            if (group == null) continue;
            Sum sum = sums.computeIfAbsent(key.isEmpty() ? "other" : key, k -> new Sum());
            Vector3d total = lastLocalForce(group);
            Vector3d recorded = new Vector3d();
            boolean anyPoint = false;
            Object points = ReflectionAccessors.invoke(ReflectionAccessors.method(group.getClass(), "getRecordedPointForces"), group);
            if (points instanceof Iterable<?> list) {
                for (Object pf : list) {
                    if (pf == null) continue;
                    Object f = ReflectionAccessors.invoke(ReflectionAccessors.method(pf.getClass(), "force"), pf);
                    Object p = ReflectionAccessors.invoke(ReflectionAccessors.method(pf.getClass(), "point"), pf);
                    if (!(f instanceof Vector3dc force) || !(p instanceof Vector3dc point)) continue;
                    recorded.add(force);
                    double w = force.length();
                    sum.pointWeight += w;
                    sum.pointSum.add(point.x() * w, point.y() * w, point.z() * w);
                    anyPoint = true;
                }
            }

            Vector3d local = total != null && total.lengthSquared() > 1.0e-18 ? total : anyPoint ? recorded : null;
            if (local != null) sum.local.add(local);
        }
        List<AerialSnapshot.Force> out = new ArrayList<>();
        for (Map.Entry<String, Sum> e : sums.entrySet()) {
            Sum s = e.getValue();
            if (s.local.lengthSquared() < 1.0e-18) continue;
            Vector3d world = bodyToWorld.transform(new Vector3d(s.local)).div(dt);
            com.createstats.aerial.V3 centre = com.createstats.aerial.V3.NAN;
            if (s.pointWeight > 1.0e-12) {
                Vector3d plot = new Vector3d(s.pointSum).div(s.pointWeight);
                Vector3d w = bodyToWorld.transform(plot.sub(comPlot)).add(comWorld);
                centre = new com.createstats.aerial.V3(w.x, w.y, w.z);
            }
            out.add(new AerialSnapshot.Force(e.getKey(), new com.createstats.aerial.V3(world.x, world.y, world.z), centre));
        }
        return out;
    }

    private static final class Sum {
        final Vector3d local = new Vector3d();
        final Vector3d pointSum = new Vector3d();
        double pointWeight;
    }

    @Nullable
    private static Vector3d lastLocalForce(Object group) {
        Object total = ReflectionAccessors.invoke(ReflectionAccessors.method(group.getClass(), "getForceTotal"), group);
        if (total == null) return null;
        Field f = ReflectionAccessors.field(total.getClass(), "lastLocalForce");
        if (f == null) {
            AerialReflect.warnOnce("lastLocalForce", "no lastLocalForce on " + total.getClass().getName());
            return null;
        }
        return ReflectionAccessors.read(f, total) instanceof Vector3dc v ? new Vector3d(v) : null;
    }

    private static int substeps(Level level) {
        Class<?> system = ReflectionAccessors.findClass(PHYSICS_SYSTEM);
        Method get = system == null ? null : ReflectionAccessors.findMethodAccepting(system, "get", Level.class);
        Object sys = get == null ? null : ReflectionAccessors.invoke(get, null, level);
        Object config = sys == null ? null : ReflectionAccessors.invoke(ReflectionAccessors.method(sys.getClass(), "getConfig"), sys);
        Field f = config == null ? null : ReflectionAccessors.field(config.getClass(), "substepsPerTick");
        Object n = f == null ? null : ReflectionAccessors.read(f, config);
        return n instanceof Number num && num.intValue() >= 1 ? num.intValue() : 2;
    }
}
