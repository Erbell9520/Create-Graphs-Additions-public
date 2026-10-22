package com.createstats.flight.bridge;

import com.createstats.CreateStats;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

public final class SableWake {
    static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    static final String SERVER_CONTAINER = "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer";
    static final String SYSTEM = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem";
    static final String PIPELINE = "dev.ryanhcode.sable.api.physics.PhysicsPipeline";
    static final String BODY = "dev.ryanhcode.sable.api.physics.PhysicsPipelineBody";

    private record Path(MethodHandle getContainer, Class<?> serverContainer, MethodHandle physicsSystem,
                        MethodHandle getPipeline, Class<?> body, MethodHandle wakeUp) {
    }

    private static volatile Path path;
    private static volatile boolean missing;
    private static volatile boolean warned;

    private SableWake() {
    }

    public static boolean wake(Level level, @Nullable Object body) {
        if (body == null || level == null || level.isClientSide()) return false;
        Path p = path();
        if (p == null || !p.body().isInstance(body)) return false;
        try {
            Object container = p.getContainer().invoke(level);
            if (!p.serverContainer().isInstance(container)) return false;
            Object system = p.physicsSystem().invoke(container);
            if (system == null) return false;
            Object pipeline = p.getPipeline().invoke(system);
            if (pipeline == null) return false;
            p.wakeUp().invoke(pipeline, body);
            return true;
        } catch (Throwable t) {
            if (ForeignHandles.fatal(t)) throw (Error) t;
            warnOnce(t.toString());
            return false;
        }
    }

    @Nullable
    private static Path path() {
        Path p = path;
        if (p != null || missing) return p;
        Class<?> container = ReflectionAccessors.findClass(CONTAINER);
        Class<?> serverContainer = ReflectionAccessors.findClass(SERVER_CONTAINER);
        Class<?> system = ReflectionAccessors.findClass(SYSTEM);
        Class<?> pipeline = ReflectionAccessors.findClass(PIPELINE);
        Class<?> body = ReflectionAccessors.findClass(BODY);
        if (container == null || serverContainer == null || system == null || pipeline == null || body == null) {
            missing = true;
            return null;
        }
        MethodHandle getContainer = ForeignHandles.findStatic(container, "getContainer", container, Level.class);
        MethodHandle physicsSystem = ForeignHandles.findVirtual(serverContainer, "physicsSystem", system);
        MethodHandle getPipeline = ForeignHandles.findVirtual(system, "getPipeline", pipeline);
        MethodHandle wakeUp = ForeignHandles.findVirtual(pipeline, "wakeUp", void.class, body);
        if (getContainer == null || physicsSystem == null || getPipeline == null || wakeUp == null) {
            missing = true;
            warnOnce("Sable's wake path not found (getContainer " + (getContainer != null) + ", physicsSystem "
                    + (physicsSystem != null) + ", getPipeline " + (getPipeline != null) + ", wakeUp " + (wakeUp != null) + ")");
            return null;
        }
        p = new Path(getContainer, serverContainer, physicsSystem, getPipeline, body, wakeUp);
        path = p;
        return p;
    }

    private static void warnOnce(String detail) {
        if (warned) return;
        warned = true;
        CreateStats.LOGGER.warn("[Sable] could not wake a body, ships may hang asleep: {}", detail);
    }
}
