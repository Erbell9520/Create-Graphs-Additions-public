package com.createstats.flight.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SubLevels {
    private SubLevels() {
    }

    public static List<Object> listActive(ServerLevel level) {
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Object container = containerClass.getMethod("getContainer", Level.class).invoke(null, level);
            Object all = container.getClass().getMethod("getAllSubLevels").invoke(container);
            List<Object> out = new ArrayList<>();
            if (all instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o != null) {
                        out.add(o);
                    }
                }
            } else if (all instanceof Object[] array) {
                for (Object o : array) {
                    if (o != null) {
                        out.add(o);
                    }
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    @Nullable
    public static UUID uniqueId(Object subLevel) {
        try {
            Object value = subLevel.getClass().getMethod("getUniqueId").invoke(subLevel);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public static Object findById(ServerLevel level, UUID id) {
        for (Object subLevel : listActive(level)) {
            if (id.equals(uniqueId(subLevel))) {
                return subLevel;
            }
        }
        return null;
    }

    @Nullable
    public static Vec3 centerOf(Object subLevel) {
        Vector3d origin = SableBridge.originWorld(subLevel);
        if (origin != null && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z)) {
            return new Vec3(origin.x, origin.y, origin.z);
        }
        Vector3d local = SableBridge.centerOfMassLocal(subLevel);
        Vector3d world = SableBridge.localToWorld(subLevel, local);
        if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
            return new Vec3(world.x, world.y, world.z);
        }
        return null;
    }
}
