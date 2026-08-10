package com.createstats.flight.bridge;

import com.createstats.CreateStats;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** [1] */
public final class HingeConstraintBridge {

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);
    private static volatile @Nullable Caps CAPS;
    private static volatile boolean PROBED;

    private HingeConstraintBridge() {
    }

    /** Resolved reflection handles. Non-null = Sable constraint API is callable. */
    private static final class Caps {
        final Method systemRequire;          // SubLevelPhysicsSystem.require(Level) [static]
        final Method systemGetPipeline;      // SubLevelPhysicsSystem.getPipeline()
        final Method pipelineAddConstraint;  // PhysicsPipeline.addConstraint(ServerSubLevel, ServerSubLevel, config)
        final Method pipelineApplyImpulse;   // PhysicsPipeline.applyImpulse(PhysicsPipelineBody, Vector3dc, Vector3dc)
        final @Nullable Method pipelineWakeUp; // PhysicsPipeline.wakeUp(PhysicsPipelineBody), wake sleeping ships
        final Method containerGetContainer;  // SubLevelContainer.getContainer(ServerLevel) [static] -> ServerSubLevelContainer
        final Method containerGetAll;        // ServerSubLevelContainer.getAllSubLevels() -> List<ServerSubLevel>
        final Method containerGetById;       // SubLevelContainer.getSubLevel(UUID)
        final Method subLevelLogicalPose;    // ServerSubLevel.logicalPose() -> Pose3d
        final Method subLevelGetUniqueId;    // ServerSubLevel.getUniqueId() -> UUID
        final Method poseTransform;          // Pose3dc.transformPosition(Vector3dc, Vector3d) local->world
        final Method poseTransformInverse;   // Pose3dc.transformPositionInverse(Vector3dc, Vector3d) world->local
        final Method poseOrientation;        // Pose3dc.orientation() -> Quaterniond
        final Method posePosition;           // Pose3dc.position() -> Vector3d (world pose anchor)
        final Constructor<?> rotaryCtor;     // RotaryConstraintConfiguration(Vector3dc x4)
        final Method handleRemove;           // PhysicsConstraintHandle.remove()
        final Method handleIsValid;          // PhysicsConstraintHandle.isValid()
        final @Nullable Method handleSetContacts; // PhysicsConstraintHandle.setContactsEnabled(boolean)
        final @Nullable Method pushEntityLocal; // SubLevelHelper.pushEntityLocal(SubLevel, Entity) [static]
        final @Nullable Method popEntityLocal;  // SubLevelHelper.popEntityLocal(SubLevel, Entity) [static]
        final Class<?> serverSubLevelClass;

        Caps(Method systemRequire, Method systemGetPipeline, Method pipelineAddConstraint,
             Method pipelineApplyImpulse, @Nullable Method pipelineWakeUp,
             Method containerGetContainer, Method containerGetAll,
             Method containerGetById, Method subLevelLogicalPose, Method subLevelGetUniqueId,
             Method poseTransform, Method poseTransformInverse, Method poseOrientation,
             Method posePosition, Constructor<?> rotaryCtor, Method handleRemove, Method handleIsValid,
             @Nullable Method handleSetContacts,
             @Nullable Method pushEntityLocal, @Nullable Method popEntityLocal,
             Class<?> serverSubLevelClass) {
            this.systemRequire = systemRequire;
            this.systemGetPipeline = systemGetPipeline;
            this.pipelineAddConstraint = pipelineAddConstraint;
            this.pipelineApplyImpulse = pipelineApplyImpulse;
            this.pipelineWakeUp = pipelineWakeUp;
            this.containerGetContainer = containerGetContainer;
            this.containerGetAll = containerGetAll;
            this.containerGetById = containerGetById;
            this.subLevelLogicalPose = subLevelLogicalPose;
            this.subLevelGetUniqueId = subLevelGetUniqueId;
            this.poseTransform = poseTransform;
            this.poseTransformInverse = poseTransformInverse;
            this.poseOrientation = poseOrientation;
            this.posePosition = posePosition;
            this.rotaryCtor = rotaryCtor;
            this.handleRemove = handleRemove;
            this.handleIsValid = handleIsValid;
            this.handleSetContacts = handleSetContacts;
            this.pushEntityLocal = pushEntityLocal;
            this.popEntityLocal = popEntityLocal;
            this.serverSubLevelClass = serverSubLevelClass;
        }
    }

    @Nullable
    private static Caps caps() {
        if (PROBED) {
            return CAPS;
        }
        synchronized (HingeConstraintBridge.class) {
            if (PROBED) {
                return CAPS;
            }
            try {
                CAPS = bootstrap();
            } catch (Throwable ex) {
                if (WARNED.compareAndSet(false, true)) {
                    CreateStats.LOGGER.warn("[Hinge/Bridge] bootstrap failed: {}", ex.toString());
                }
                CAPS = null;
            }
            PROBED = true;
            return CAPS;
        }
    }

    @Nullable
    private static Caps bootstrap() throws ReflectiveOperationException {
        Class<?> systemClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
        Class<?> pipelineClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.api.physics.PhysicsPipeline");
        Class<?> containerClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Class<?> serverSubLevelClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.sublevel.ServerSubLevel");
        Class<?> rotaryConfigClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration");
        Class<?> handleClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle");
        if (systemClass == null || pipelineClass == null || containerClass == null
                || serverSubLevelClass == null || rotaryConfigClass == null || handleClass == null) {
            return null;
        }

        Method systemRequire = ReflectionAccessors.findMethodAccepting(systemClass, "require", Level.class);
        Method systemGetPipeline = ReflectionAccessors.findMethodAccepting(systemClass, "getPipeline");
        Method pipelineAddConstraint = ReflectionAccessors.findMethodAccepting(
                pipelineClass, "addConstraint", serverSubLevelClass, serverSubLevelClass, null);
        Method pipelineApplyImpulse = ReflectionAccessors.findMethodAccepting(
                pipelineClass, "applyImpulse", serverSubLevelClass, Vector3dc.class, Vector3dc.class);
        // Optional: wake sleeping ships so the freshly-added joint is actually solved (Rapier skips
        // sleeping bodies). ServerSubLevel implements PhysicsPipelineBody, so it matches wakeUp.
        Method pipelineWakeUp = ReflectionAccessors.findMethodAccepting(pipelineClass, "wakeUp", serverSubLevelClass);
        Method containerGetContainer = ReflectionAccessors.findMethodAccepting(containerClass, "getContainer", ServerLevel.class);
        Method subLevelGetUniqueId = ReflectionAccessors.findMethodAccepting(serverSubLevelClass, "getUniqueId");
        Method subLevelLogicalPose = ReflectionAccessors.findMethodAccepting(serverSubLevelClass, "logicalPose");
        Method handleRemove = ReflectionAccessors.findMethodAccepting(handleClass, "remove");
        Method handleIsValid = ReflectionAccessors.findMethodAccepting(handleClass, "isValid");
        // Optional: disable collisions between the two hinged ships so the hinge can actually swing
        // open instead of the hulls bumping and snapping shut.
        Method handleSetContacts = ReflectionAccessors.findMethodAccepting(handleClass, "setContactsEnabled", boolean.class);

        if (systemRequire == null || systemGetPipeline == null || pipelineAddConstraint == null
                || pipelineApplyImpulse == null || containerGetContainer == null
                || subLevelGetUniqueId == null || subLevelLogicalPose == null
                || handleRemove == null || handleIsValid == null) {
            return null;
        }

        Class<?> serverContainerClass = containerGetContainer.getReturnType();
        Method containerGetAll = ReflectionAccessors.findMethodAccepting(serverContainerClass, "getAllSubLevels");
        Method containerGetById = ReflectionAccessors.findMethodAccepting(containerClass, "getSubLevel", UUID.class);

        Class<?> poseClass = subLevelLogicalPose.getReturnType();
        Method poseTransform = ReflectionAccessors.findMethodAccepting(poseClass, "transformPosition", Vector3dc.class, Vector3d.class);
        Method poseTransformInverse = ReflectionAccessors.findMethodAccepting(poseClass, "transformPositionInverse", Vector3dc.class, Vector3d.class);
        Method poseOrientation = ReflectionAccessors.findMethodAccepting(poseClass, "orientation");
        Method posePosition = ReflectionAccessors.findMethodAccepting(poseClass, "position");

        Constructor<?> rotaryCtor = rotaryConfigClass.getConstructor(Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class);

        if (containerGetAll == null || poseTransform == null || poseTransformInverse == null
                || poseOrientation == null || posePosition == null) {
            return null;
        }

        // Optional: SubLevelHelper.pushEntityLocal/popEntityLocal for precise in-frame block picking.
        Method pushEntityLocal = null;
        Method popEntityLocal = null;
        Class<?> helperClass = ReflectionAccessors.findClass("dev.ryanhcode.sable.api.SubLevelHelper");
        if (helperClass != null) {
            pushEntityLocal = ReflectionAccessors.findMethodAccepting(helperClass, "pushEntityLocal",
                    serverSubLevelClass, net.minecraft.world.entity.Entity.class);
            popEntityLocal = ReflectionAccessors.findMethodAccepting(helperClass, "popEntityLocal",
                    serverSubLevelClass, net.minecraft.world.entity.Entity.class);
        }

        CreateStats.LOGGER.info("[Hinge/Bridge] Sable constraint API bootstrapped (wakeUp={}, setContacts={}).",
                pipelineWakeUp != null, handleSetContacts != null);
        return new Caps(systemRequire, systemGetPipeline, pipelineAddConstraint, pipelineApplyImpulse,
                pipelineWakeUp, containerGetContainer, containerGetAll, containerGetById, subLevelLogicalPose,
                subLevelGetUniqueId, poseTransform, poseTransformInverse, poseOrientation,
                posePosition, rotaryCtor, handleRemove, handleIsValid, handleSetContacts,
                pushEntityLocal, popEntityLocal, serverSubLevelClass);
    }

    /** {@code true} when the Sable constraint solver is present and callable. */
    public static boolean isAvailable() {
        return caps() != null;
    }

    // sub-level discovery

    /** All server sub-levels (flying ships) in the level, or an empty list if Sable is absent. */
    @SuppressWarnings("unchecked")
    public static List<Object> allServerSubLevels(@Nullable ServerLevel level) {
        Caps c = caps();
        if (c == null || level == null) {
            return List.of();
        }
        try {
            Object container = c.containerGetContainer.invoke(null, level);
            if (container == null) {
                return List.of();
            }
            Object list = c.containerGetAll.invoke(container);
            return list instanceof List ? (List<Object>) list : List.of();
        } catch (Throwable ex) {
            return List.of();
        }
    }

    /** Stable persistence id of a server sub-level, or {@code null}. */
    @Nullable
    public static UUID subLevelId(@Nullable Object serverSubLevel) {
        Caps c = caps();
        if (c == null || serverSubLevel == null) {
            return null;
        }
        try {
            Object id = c.subLevelGetUniqueId.invoke(serverSubLevel);
            return id instanceof UUID ? (UUID) id : null;
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Resolve a server sub-level by its stable id (for re-creating constraints on load). */
    @Nullable
    public static Object subLevelById(@Nullable ServerLevel level, @Nullable UUID id) {
        Caps c = caps();
        if (c == null || level == null || id == null) {
            return null;
        }
        for (Object sub : allServerSubLevels(level)) {
            if (id.equals(subLevelId(sub))) {
                return sub;
            }
        }
        return null;
    }

    /** World-space pose anchor (~world centre of mass) of a sub-level, for ray-picking. */
    @Nullable
    public static Vector3d worldAnchor(@Nullable Object serverSubLevel) {
        Caps c = caps();
        if (c == null || serverSubLevel == null) {
            return null;
        }
        try {
            Object pose = c.subLevelLogicalPose.invoke(serverSubLevel);
            Object pos = c.posePosition.invoke(pose);
            if (pos instanceof Vector3d v) {
                return new Vector3d(v);
            }
            if (pos instanceof org.joml.Vector3dc vc) {
                return new Vector3d(vc);
            }
            return null;
        } catch (Throwable ex) {
            return null;
        }
    }

    // frame conversion (world <-> a sub-level's body-local frame)

    /** Convert a world-space point into the sub-level's body-local frame. */
    @Nullable
    public static Vector3d worldToLocal(@Nullable Object serverSubLevel, @Nullable Vector3dc world) {
        Caps c = caps();
        if (c == null || serverSubLevel == null || world == null) {
            return null;
        }
        try {
            Object pose = c.subLevelLogicalPose.invoke(serverSubLevel);
            Vector3d dest = new Vector3d();
            c.poseTransformInverse.invoke(pose, world, dest);
            return dest;
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Convert a body-local point into world space. */
    @Nullable
    public static Vector3d localToWorld(@Nullable Object serverSubLevel, @Nullable Vector3dc local) {
        Caps c = caps();
        if (c == null || serverSubLevel == null || local == null) {
            return null;
        }
        try {
            Object pose = c.subLevelLogicalPose.invoke(serverSubLevel);
            Vector3d dest = new Vector3d();
            c.poseTransform.invoke(pose, local, dest);
            return dest;
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Convert a world-space direction (unit axis) into the sub-level's body-local frame. */
    @Nullable
    public static Vector3d worldAxisToLocal(@Nullable Object serverSubLevel, @Nullable Vector3dc worldAxis) {
        Caps c = caps();
        if (c == null || serverSubLevel == null || worldAxis == null) {
            return null;
        }
        try {
            Object pose = c.subLevelLogicalPose.invoke(serverSubLevel);
            Object orientation = c.poseOrientation.invoke(pose);
            if (!(orientation instanceof Quaterniond q)) {
                return null;
            }
            Vector3d dest = new Vector3d();
            q.transformInverse(worldAxis, dest);
            return dest.normalize();
        } catch (Throwable ex) {
            return null;
        }
    }

    // constraint creation + pull

    /**
     * Create a rotary (hinge) constraint between two server sub-levels. Pivot points and hinge axes
     * are supplied in each body's local frame. Returns an opaque constraint handle, or {@code null}.
     */
    @Nullable
    public static Object createRotary(@Nullable ServerLevel level, @Nullable Object subA, @Nullable Object subB,
                                      @Nullable Vector3dc pivotLocalA, @Nullable Vector3dc pivotLocalB,
                                      @Nullable Vector3dc axisLocalA, @Nullable Vector3dc axisLocalB) {
        Caps c = caps();
        if (c == null || level == null || subA == null || subB == null
                || pivotLocalA == null || pivotLocalB == null || axisLocalA == null || axisLocalB == null) {
            return null;
        }
        try {
            Object pipeline = pipeline(c, level);
            if (pipeline == null) {
                return null;
            }
            Object config = c.rotaryCtor.newInstance(pivotLocalA, pivotLocalB, axisLocalA, axisLocalB);
            Object handle = c.pipelineAddConstraint.invoke(pipeline, subA, subB, config);
            if (handle != null) {
                // [2]
                if (c.handleSetContacts != null) {
                    try {
                        c.handleSetContacts.invoke(handle, true);
                    } catch (Throwable contactEx) {
                        CreateStats.LOGGER.debug("[Hinge/Bridge] setContactsEnabled(true) failed: {}", contactEx.toString());
                    }
                }
                // Wake both ships: a sleeping Rapier body is skipped by the solver, so without this a
                // parked ship would never feel the new joint's pull-together correction.
                if (c.pipelineWakeUp != null) {
                    try {
                        c.pipelineWakeUp.invoke(pipeline, subA);
                        c.pipelineWakeUp.invoke(pipeline, subB);
                    } catch (Throwable wakeEx) {
                        CreateStats.LOGGER.debug("[Hinge/Bridge] wakeUp after createRotary failed: {}", wakeEx.toString());
                    }
                }
            }
            return handle;
        } catch (Throwable ex) {
            if (WARNED.compareAndSet(false, true)) {
                CreateStats.LOGGER.warn("[Hinge/Bridge] createRotary failed: {}", ex.toString());
            }
            return null;
        }
    }

    /** [3] */
    public static void applyImpulse(@Nullable ServerLevel level, @Nullable Object subLevelBody,
                                    @Nullable Vector3dc impulseWorld, @Nullable Vector3dc pointWorld) {
        Caps c = caps();
        if (c == null || level == null || subLevelBody == null || impulseWorld == null || pointWorld == null) {
            return;
        }
        try {
            Object pipeline = pipeline(c, level);
            if (pipeline != null) {
                c.pipelineApplyImpulse.invoke(pipeline, subLevelBody, impulseWorld, pointWorld);
            }
        } catch (Throwable ex) {
            // best-effort; ignore
        }
    }

    /** Remove a previously created constraint. Safe with {@code null}. */
    public static void removeConstraint(@Nullable Object handle) {
        Caps c = caps();
        if (c == null || handle == null) {
            return;
        }
        try {
            c.handleRemove.invoke(handle);
            CreateStats.LOGGER.debug("[Hinge/Bridge] removeConstraint ok");
        } catch (Throwable ex) {
            CreateStats.LOGGER.warn("[Hinge/Bridge] removeConstraint failed: {}", ex.toString());
        }
    }

    /** Whether a constraint handle is still live. */
    public static boolean isConstraintValid(@Nullable Object handle) {
        Caps c = caps();
        if (c == null || handle == null) {
            return false;
        }
        try {
            Object v = c.handleIsValid.invoke(handle);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ex) {
            return false;
        }
    }

    /** [4] */
    public static boolean withEntityLocal(@Nullable Object serverSubLevel,
                                          @Nullable net.minecraft.world.entity.Entity entity,
                                          Runnable action) {
        Caps c = caps();
        if (c == null || c.pushEntityLocal == null || c.popEntityLocal == null
                || serverSubLevel == null || entity == null || action == null) {
            return false;
        }
        boolean pushed = false;
        try {
            c.pushEntityLocal.invoke(null, serverSubLevel, entity);
            pushed = true;
            action.run();
        } catch (Throwable ex) {
            return false;
        } finally {
            if (pushed) {
                try {
                    c.popEntityLocal.invoke(null, serverSubLevel, entity);
                } catch (Throwable ignored) {
                    // best-effort restore
                }
            }
        }
        return pushed;
    }

    @Nullable
    private static Object pipeline(Caps c, ServerLevel level) throws ReflectiveOperationException {
        Object system = c.systemRequire.invoke(null, level);
        if (system == null) {
            return null;
        }
        return c.systemGetPipeline.invoke(system);
    }
}
