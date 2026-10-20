package com.createstats.vccmk4.route;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// reflection, simulated isn't on our classpath
final class DockReflect {
    static final String CLASS = "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity";

    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    @Nullable private static volatile Class<?> type;
    private static volatile boolean looked;

    private DockReflect() {
    }

    @Nullable
    private static Class<?> type() {
        if (!looked) {
            try {
                type = Class.forName(CLASS);
            } catch (ClassNotFoundException | LinkageError e) {
                type = null;
                RouteReflect.warnOnce("dockingConnector", "no Simulated docking connector: " + e);
            }
            looked = true;
        }
        return type;
    }

    static boolean is(@Nullable BlockEntity be) {
        Class<?> c = type();
        return be != null && c != null && c.isInstance(be);
    }

    static boolean isLocked(BlockEntity be) {
        return bool(be, "isLocked");
    }

    static boolean hasOther(BlockEntity be) {
        return bool(be, "hasOtherConnector");
    }

    static boolean isExtended(BlockEntity be) {
        return bool(be, "isExtended");
    }

    static boolean isRetracted(BlockEntity be) {
        Object r = call(be, "isRetracted");
        return !(r instanceof Boolean b) || b;
    }

    static Vec3 tip(BlockEntity be) {
        Object r = call(be, "getTipPosition");
        if (r instanceof Vec3 v) return v;
        return Vec3.atCenterOf(be.getBlockPos()).add(Vec3.atLowerCornerOf(facing(be).getNormal()).scale(1.5));
    }

    static Direction facing(BlockEntity be) {
        try {
            return be.getBlockState().getValue(BlockStateProperties.FACING);
        } catch (RuntimeException e) {
            return Direction.NORTH;
        }
    }

    private static boolean bool(BlockEntity be, String name) {
        Object r = call(be, name);
        return r instanceof Boolean b && b;
    }

    @Nullable
    private static Object call(BlockEntity be, String name) {
        if (!is(be)) return null;
        try {
            Method m = METHODS.get(name);
            if (m == null) {
                m = type().getMethod(name);
                METHODS.put(name, m);
            }
            return m.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            RouteReflect.warnOnce("docking." + name, e.toString());
            return null;
        }
    }
}
