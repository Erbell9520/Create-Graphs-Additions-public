package com.createstats.cinecam.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

final class CinePose {
    private record Api(Class<?> owner, Method renderPose, Class<?> poseClass, Method point, Method normal,
                       Method normalInverse, Method position) {
    }

    @Nullable private static volatile Api api;
    private static volatile boolean missing;

    private CinePose() {
    }

    @Nullable
    static Vec3 point(@Nullable Object subLevel, Vec3 local, float partial) {
        return vec(call(subLevel, partial, Kind.POINT, local));
    }

    @Nullable
    static Vec3 normal(@Nullable Object subLevel, Vec3 local, float partial) {
        return vec(call(subLevel, partial, Kind.NORMAL, local));
    }

    @Nullable
    static Vec3 normalInverse(@Nullable Object subLevel, Vec3 world, float partial) {
        return vec(call(subLevel, partial, Kind.NORMAL_INVERSE, world));
    }

    @Nullable
    static Vec3 position(@Nullable Object subLevel, float partial) {
        Object v = call(subLevel, partial, Kind.POSITION, null);
        return v instanceof Vector3dc p && Double.isFinite(p.x()) && Double.isFinite(p.z()) ? new Vec3(p.x(), p.y(), p.z()) : null;
    }

    private enum Kind { POINT, NORMAL, NORMAL_INVERSE, POSITION }

    @Nullable
    private static Vec3 vec(@Nullable Object v) {
        return v instanceof Vec3 out && Double.isFinite(out.x) && Double.isFinite(out.y) && Double.isFinite(out.z) ? out : null;
    }

    @Nullable
    private static Object call(@Nullable Object subLevel, float partial, Kind kind, @Nullable Vec3 arg) {
        if (subLevel == null || missing) return null;
        try {
            Api a = api;
            if (a == null || a.owner() != subLevel.getClass()) {
                Method rp = null;
                for (Method m : subLevel.getClass().getMethods()) {
                    if (m.getName().equals("renderPose") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class) {
                        rp = m;
                        break;
                    }
                }
                if (rp == null) {
                    // server sub-level or old sable, caller falls back
                    return null;
                }
                Object pose = rp.invoke(subLevel, partial);
                if (pose == null) return null;
                Class<?> pc = pose.getClass();
                a = new Api(subLevel.getClass(), rp, pc, pc.getMethod("transformPosition", Vec3.class),
                        pc.getMethod("transformNormal", Vec3.class), pc.getMethod("transformNormalInverse", Vec3.class),
                        pc.getMethod("position"));
                api = a;
            }
            Object pose = a.renderPose().invoke(subLevel, partial);
            if (pose == null || pose.getClass() != a.poseClass()) return null;
            return switch (kind) {
                case POINT -> a.point().invoke(pose, arg);
                case NORMAL -> a.normal().invoke(pose, arg);
                case NORMAL_INVERSE -> a.normalInverse().invoke(pose, arg);
                case POSITION -> a.position().invoke(pose);
            };
        } catch (NoSuchMethodException e) {
            missing = true;
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
