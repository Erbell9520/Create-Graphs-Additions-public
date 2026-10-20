package com.createstats.flight.bridge;

import com.createstats.CreateStats;
import com.createstats.DebugLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// every read into sable goes through here. reflection, loads without any of them
public final class SableBridge {
    private static final AtomicBoolean COMPANION_WARNED        = new AtomicBoolean(false);
    private static final AtomicBoolean SUBLEVEL_PHYS_WARNED    = new AtomicBoolean(false);
    private static final AtomicBoolean AERO_PROPS_WARNED       = new AtomicBoolean(false);
    private static final AtomicBoolean NAV_TABLE_WARNED        = new AtomicBoolean(false);

    private static volatile @Nullable CompanionCaps   COMPANION;
    private static volatile @Nullable SubLevelPhysCaps PHYS;
    private static volatile @Nullable Class<?> PHYS_SERVER_SUBLEVEL_CLASS;
    private static volatile @Nullable AeroPropCaps    PROPS;
    private static volatile @Nullable NavTableCaps    NAV;

    private static volatile long currentTick = Long.MIN_VALUE;
    private static final Map<String, Object> TICK_CACHE = new HashMap<>();
    private static final Object NULL_SENTINEL = new Object();

    private SableBridge() {}

    public static void beginTick(long tick) {
        synchronized (TICK_CACHE) {
            if (tick != currentTick) {
                TICK_CACHE.clear();
                currentTick = tick;
            }
        }
    }

    public static boolean isAvailable() {
        return companion() != null;
    }

    private static final class CompanionCaps {
        final Object instance;
        final Method getContaining;
        final Method getVelocity;
        final Method logicalPose;
        final Method poseOrientation;
        final Method posePosition;
        final Method poseRotationPoint;
        final Method poseTransformPosition;
        final Method poseTransformPositionInverse;
        CompanionCaps(Object instance, Method getContaining, Method getVelocity,
                      Method logicalPose, Method poseOrientation, Method posePosition, Method poseRotationPoint,
                      Method poseTransformPosition, Method poseTransformPositionInverse) {
            this.instance = instance;
            this.getContaining = getContaining;
            this.getVelocity = getVelocity;
            this.logicalPose = logicalPose;
            this.poseOrientation = poseOrientation;
            this.posePosition = posePosition;
            this.poseRotationPoint = poseRotationPoint;
            this.poseTransformPosition = poseTransformPosition;
            this.poseTransformPositionInverse = poseTransformPositionInverse;
        }
    }

    @Nullable
    private static CompanionCaps companion() {
        CompanionCaps c = COMPANION;
        if (c != null) return c;
        try {
            // Sable.HELPER not SableCompanion.INSTANCE. companion is jij'd twice and our copy's INSTANCE is null. sable tax #8

            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Object instance = sableClass.getField("HELPER").get(null);
            if (instance == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] Sable.HELPER is null, Sable not initialized yet. Flight features disabled until next tick.");
                return null;
            }
            DebugLogging.fccDebug("[FCC/Bridge] companion bootstrap using HELPER class={}",
                    instance.getClass().getName());

            Method getContaining = ReflectionAccessors.findMethodAccepting(
                    instance.getClass(), "getContaining",
                    net.minecraft.world.level.block.entity.BlockEntity.class);
            if (getContaining == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] getContaining(BlockEntity) not found on {}",
                            instance.getClass().getName());
                return null;
            }

            Class<?> slaClass = getContaining.getReturnType();
            DebugLogging.fccDebug("[FCC/Bridge] slaClass={}", slaClass.getName());

            Method logicalPose = ReflectionAccessors.findMethodAccepting(slaClass, "logicalPose");
            if (logicalPose == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] logicalPose() not found on {}", slaClass.getName());
                return null;
            }
            Class<?> poseClass = logicalPose.getReturnType();

            Method getVelocity = findVelocityMethod(instance.getClass(), slaClass);

            Method poseOrientation   = ReflectionAccessors.findMethodAccepting(poseClass, "orientation");
            Method posePosition      = ReflectionAccessors.findMethodAccepting(poseClass, "position");
            Method poseRotationPoint = ReflectionAccessors.findMethodAccepting(poseClass, "rotationPoint");
            Method poseTransformPosition = ReflectionAccessors.findMethodAccepting(
                    poseClass, "transformPosition", Vector3dc.class, Vector3d.class);
            Method poseTransformPositionInverse = ReflectionAccessors.findMethodAccepting(
                    poseClass, "transformPositionInverse", Vector3dc.class, Vector3d.class);

            if (poseOrientation == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] orientation() not found on {}", poseClass.getName());
                return null;
            }

            c = new CompanionCaps(instance, getContaining, getVelocity, logicalPose,
                    poseOrientation, posePosition, poseRotationPoint,
                    poseTransformPosition, poseTransformPositionInverse);
            COMPANION = c;
                DebugLogging.fccInfo("[FCC/Bridge] companion ready: slaClass={} poseClass={} poseTransforms={}/{}",
                    slaClass.getName(), poseClass.getName(),
                    poseTransformPosition != null, poseTransformPositionInverse != null);
            return c;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            if (COMPANION_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] companion bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    @Nullable
    private static Method findVelocityMethod(Class<?> companionClass, Class<?> slaClass) {
        for (Method m : companionClass.getMethods()) {
            if (!m.getName().equals("getVelocity") || m.getParameterCount() != 3) continue;
            Class<?>[] p = m.getParameterTypes();

            if (p[0].isAssignableFrom(Level.class)
                    && p[1] == slaClass
                    && p[2].isAssignableFrom(Vec3.class)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    @Nullable
    public static Object subLevelAccessOf(@Nullable BlockEntity be) {
        if (be == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        return ReflectionAccessors.invoke(c.getContaining, c.instance, be);
    }

    private static final String SUBLEVEL_CONTAINER_CLASS = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";

    public static List<Object> listSubLevels(@Nullable Level level) {
        if (level == null) return List.of();
        try {
            Class<?> containerClass = Class.forName(SUBLEVEL_CONTAINER_CLASS);
            Object container = invokeStaticOneArg(containerClass, "getContainer", level);
            Object all = invokeNoArg(container, "getAllSubLevels");
            if (all instanceof Iterable<?> it) {
                List<Object> out = new ArrayList<>();
                for (Object s : it) {
                    if (s != null) out.add(s);
                }
                return out;
            }
            if (all instanceof Object[] arr) {
                List<Object> out = new ArrayList<>(arr.length);
                for (Object s : arr) {
                    if (s != null) out.add(s);
                }
                return out;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return List.of();
    }

    @Nullable
    public static UUID subLevelId(@Nullable Object subLevel) {
        try {
            Object v = invokeNoArg(subLevel, "getUniqueId");
            return v instanceof UUID u ? u : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static String subLevelName(@Nullable Object subLevel) {
        try {
            Object n = invokeNoArg(subLevel, "getName");
            if (n instanceof String s && !s.isBlank()) return s;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        UUID id = subLevelId(subLevel);
        return id == null ? "Ship" : "Ship " + id.toString().substring(0, 8);
    }

    @Nullable
    public static Vector3d subLevelWorldCenter(@Nullable Object subLevel) {
        Vector3d origin = originWorld(subLevel);
        if (origin != null && isFinite(origin)) return origin;
        Vector3d localCenter = centerOfMassLocal(subLevel);
        Vector3d world = localToWorld(subLevel, localCenter);
        return (world != null && isFinite(world)) ? world : null;
    }

    @Nullable
    public static Object findSubLevelById(@Nullable Level level, @Nullable UUID id) {
        if (id == null) return null;
        for (Object s : listSubLevels(level)) {
            if (id.equals(subLevelId(s))) return s;
        }
        return null;
    }

    private static Object invokeNoArg(@Nullable Object owner, String name) throws ReflectiveOperationException {
        if (owner == null) return null;
        Class<?> cursor = owner.getClass();
        while (cursor != null) {
            for (Method m : cursor.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m.invoke(owner);
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @Nullable
    private static Object invokeStaticOneArg(Class<?> ownerClass, String name, Object arg) throws ReflectiveOperationException {
        Class<?> cursor = ownerClass;
        while (cursor != null) {
            for (Method m : cursor.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(null, arg);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @Nullable
    public static Quaterniond bodyOrientation(@Nullable Object subLevelAccess) {
        if (subLevelAccess == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        Object pose = ReflectionAccessors.invoke(c.logicalPose, subLevelAccess);
        if (pose == null) return null;
        Object q = ReflectionAccessors.invoke(c.poseOrientation, pose);
        if (q instanceof Quaterniondc qd) return new Quaterniond(qd);
        return null;
    }

    @Nullable
    public static Vector3d originWorld(@Nullable Object subLevelAccess) {
        if (subLevelAccess == null) return null;
        CompanionCaps c = companion();
        if (c == null || c.posePosition == null) return null;
        Object pose = ReflectionAccessors.invoke(c.logicalPose, subLevelAccess);
        if (pose == null) return null;
        Object v = ReflectionAccessors.invoke(c.posePosition, pose);
        if (v instanceof Vector3dc vd) return new Vector3d(vd);
        return null;
    }

    @Nullable
    public static Vector3d localToWorld(@Nullable Object subLevelAccess, @Nullable Vector3d localPos) {
        if (subLevelAccess == null || localPos == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        Object pose = ReflectionAccessors.invoke(c.logicalPose, subLevelAccess);
        if (pose == null) return null;

        if (c.poseTransformPosition != null) {
            Vector3d dst = new Vector3d();
            Object out = ReflectionAccessors.invoke(c.poseTransformPosition, pose, new Vector3d(localPos), dst);
            if (out instanceof Vector3dc vd) return new Vector3d(vd);
            if (c.poseTransformPosition.getReturnType() == Void.TYPE && isFinite(dst)) return dst;
        }

        Vector3d anchor = originWorld(subLevelAccess);
        Vector3d rotPoint = centerOfMassLocal(subLevelAccess);
        Quaterniond bodyToWorld = bodyOrientation(subLevelAccess);
        if (anchor != null && rotPoint != null && bodyToWorld != null) {
            return bodyToWorld.transform(new Vector3d(localPos).sub(rotPoint)).add(anchor);
        }
        return null;
    }

    // the BE pos stays at the assembly coords while the ship moves, screen opened then closed next frame. sable tax #9
    public static boolean isPlayerInInteractionRangeFor(@Nullable BlockEntity be, @Nullable Player player) {
        if (be == null || player == null || be.isRemoved()) return false;
        Object subLevel = subLevelAccessOf(be);
        if (subLevel != null) {
            BlockPos pos = be.getBlockPos();
            Vector3d local = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vector3d world = localToWorld(subLevel, local);
            if (world != null && isFinite(world)) {
                double dx = world.x - player.getX();
                double dy = world.y - player.getY();
                double dz = world.z - player.getZ();
                return (dx * dx + dy * dy + dz * dz) <= 1024.0;
            }

            return true;
        }
        BlockPos pos = be.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ship blocks keep plot positions 20.48M out, only the drawing follows. sable tax #10
    @Nullable
    public static Vec3 worldPointOf(@Nullable BlockEntity be, Vec3 point) {
        if (be == null || !SablePresence.LOADED) return point;
        Object subLevel = subLevelAccessOf(be);
        if (subLevel == null) return point;
        Vector3d world = localToWorld(subLevel, new Vector3d(point.x, point.y, point.z));
        return world != null && isFinite(world) ? new Vec3(world.x, world.y, world.z) : null;
    }

    private static final class SablePresence {
        static final boolean LOADED = net.neoforged.fml.ModList.get().isLoaded("sable");
    }

    @Nullable
    public static Vector3d worldToLocal(@Nullable Object subLevelAccess, @Nullable Vector3d worldPos) {
        if (subLevelAccess == null || worldPos == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        Object pose = ReflectionAccessors.invoke(c.logicalPose, subLevelAccess);
        if (pose == null) return null;

        if (c.poseTransformPositionInverse != null) {
            Vector3d dst = new Vector3d();
            Object out = ReflectionAccessors.invoke(c.poseTransformPositionInverse, pose, new Vector3d(worldPos), dst);
            if (out instanceof Vector3dc vd) return new Vector3d(vd);
            if (c.poseTransformPositionInverse.getReturnType() == Void.TYPE && isFinite(dst)) return dst;
        }

        Vector3d anchor = originWorld(subLevelAccess);
        Vector3d rotPoint = centerOfMassLocal(subLevelAccess);
        Quaterniond bodyToWorld = bodyOrientation(subLevelAccess);
        if (anchor != null && rotPoint != null && bodyToWorld != null) {
            return new Quaterniond(bodyToWorld).invert()
                    .transform(new Vector3d(worldPos).sub(anchor))
                    .add(rotPoint);
        }
        return null;
    }

    private static volatile @Nullable Class<?> RENDER_POSE_CLASS;
    private static volatile @Nullable Method RENDER_POSE;

    @Nullable
    private static Object renderPose(Object subLevelAccess, CompanionCaps c, float partialTick) {
        final Class<?> cls = subLevelAccess.getClass();
        Method m = RENDER_POSE;
        if (cls != RENDER_POSE_CLASS) {
            m = ReflectionAccessors.findMethodAccepting(cls, "renderPose", float.class);
            RENDER_POSE = m;
            RENDER_POSE_CLASS = cls;
        }
        final Object pose = m == null ? null : ReflectionAccessors.invoke(m, subLevelAccess, partialTick);
        return pose != null ? pose : ReflectionAccessors.invoke(c.logicalPose, subLevelAccess);
    }

    @Nullable
    public static Vector3d renderLocalToWorld(@Nullable Object subLevelAccess, @Nullable Vector3d localPos,
                                              float partialTick) {
        if (subLevelAccess == null || localPos == null) return null;
        CompanionCaps c = companion();
        if (c == null || c.poseTransformPosition == null) return null;
        Object pose = renderPose(subLevelAccess, c, partialTick);
        if (pose == null) return null;
        Vector3d dst = new Vector3d();
        Object out = ReflectionAccessors.invoke(c.poseTransformPosition, pose, new Vector3d(localPos), dst);
        if (out instanceof Vector3dc vd) return new Vector3d(vd);
        if (c.poseTransformPosition.getReturnType() == Void.TYPE && isFinite(dst)) return dst;
        return null;
    }

    @Nullable
    public static Quaterniond renderOrientation(@Nullable Object subLevelAccess, float partialTick) {
        if (subLevelAccess == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        Object pose = renderPose(subLevelAccess, c, partialTick);
        if (pose == null) return null;
        Object q = ReflectionAccessors.invoke(c.poseOrientation, pose);
        if (q instanceof Quaterniondc qd) return new Quaterniond(qd);
        return null;
    }

    @Nullable
    public static Vector3d centerOfMassLocal(@Nullable Object subLevelAccess) {
        if (subLevelAccess == null) return null;
        CompanionCaps c = companion();
        if (c == null || c.poseRotationPoint == null) return null;
        Object pose = ReflectionAccessors.invoke(c.logicalPose, subLevelAccess);
        if (pose == null) return null;
        Object v = ReflectionAccessors.invoke(c.poseRotationPoint, pose);
        if (v instanceof Vector3dc vd) return new Vector3d(vd);
        return null;
    }

    @Nullable
    public static Vec3 velocityAt(@Nullable Level outerLevel, @Nullable Object subLevelAccess, @Nullable Vec3 worldPos) {
        if (outerLevel == null || subLevelAccess == null || worldPos == null) return null;
        CompanionCaps c = companion();
        if (c == null || c.getVelocity == null) return null;
        Vector3d local = worldToLocal(subLevelAccess, new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        if (local == null) return null;

        String key = "vel:" + System.identityHashCode(subLevelAccess)
                + ':' + Double.doubleToLongBits(worldPos.x)
                + ':' + Double.doubleToLongBits(worldPos.y)
                + ':' + Double.doubleToLongBits(worldPos.z);
        Object cached;
        synchronized (TICK_CACHE) { cached = TICK_CACHE.get(key); }
        if (cached == NULL_SENTINEL) return null;
        if (cached instanceof Vec3 v) return v;

        Vec3 localVec = new Vec3(local.x, local.y, local.z);
        Object res = ReflectionAccessors.invoke(c.getVelocity, c.instance, outerLevel, subLevelAccess, localVec);
        Vec3 out = (res instanceof Vec3 v) ? v : null;
        synchronized (TICK_CACHE) { TICK_CACHE.put(key, out == null ? NULL_SENTINEL : out); }
        return out;
    }

    private static volatile @Nullable Method GROUND_VELOCITY_WORLD;
    private static volatile @Nullable Method AIR_VELOCITY_WORLD;
    private static final AtomicBoolean AIR_VELOCITY_PROBED = new AtomicBoolean(false);
    private static final AtomicBoolean AIR_VELOCITY_WARNED = new AtomicBoolean(false);

    private static void probeWorldVelocityMethods(Class<?> companionClass) {
        if (!AIR_VELOCITY_PROBED.compareAndSet(false, true)) return;
        for (Method m : companionClass.getMethods()) {
            if (m.getParameterCount() != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if (!p[0].isAssignableFrom(Level.class)) continue;
            if (p[1] != Vec3.class) continue;
            if (m.getReturnType() != Vec3.class) continue;
            if (m.getName().equals("getVelocity")) {
                m.setAccessible(true);
                GROUND_VELOCITY_WORLD = m;
            } else if (m.getName().equals("getVelocityRelativeToAir")) {
                m.setAccessible(true);
                AIR_VELOCITY_WORLD = m;
            }
        }
        if (AIR_VELOCITY_WORLD == null && AIR_VELOCITY_WARNED.compareAndSet(false, true)) {
            CreateStats.LOGGER.warn("[FCC/Bridge] getVelocityRelativeToAir(Level, Vec3) not found on {}; airspeed unavailable",
                    companionClass.getName());
        }
        DebugLogging.fccDebug("[FCC/Bridge] world velocity ground={} air={}",
                GROUND_VELOCITY_WORLD != null, AIR_VELOCITY_WORLD != null);
    }

    @Nullable
    private static Vec3 invokeWorldVelocity(boolean relativeToAir, @Nullable Level level, @Nullable Vec3 worldPos) {
        if (level == null || worldPos == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        probeWorldVelocityMethods(c.instance.getClass());
        Method m = relativeToAir ? AIR_VELOCITY_WORLD : GROUND_VELOCITY_WORLD;
        if (m == null) return null;
        Object res = ReflectionAccessors.invoke(m, c.instance, level, worldPos);
        return (res instanceof Vec3 v) ? v : null;
    }

    @Nullable
    public static Vec3 groundVelocityWorldAt(@Nullable Level level, @Nullable Vec3 worldPos) {
        return invokeWorldVelocity(false, level, worldPos);
    }

    @Nullable
    public static Vec3 airVelocityWorldAt(@Nullable Level level, @Nullable Vec3 worldPos) {
        return invokeWorldVelocity(true, level, worldPos);
    }

    @Nullable
    public static Vec3 windWorldAt(@Nullable Level level, @Nullable Vec3 worldPos) {
        Vec3 ground = groundVelocityWorldAt(level, worldPos);
        Vec3 air = airVelocityWorldAt(level, worldPos);
        if (ground == null || air == null) return null;
        return ground.subtract(air);
    }

    private static boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static final class SubLevelPhysCaps {
        @Nullable final Method getMassTracker;
        @Nullable final Method massTrackerGetMass;
        @Nullable final Method massTrackerInertia;
        @Nullable final Method massTrackerCoM;
        @Nullable final Method getGravityVector;
        @Nullable final Method handleOf;
        @Nullable final Method handleIsValid;
        @Nullable final Method handleAngularVel;
        @Nullable final Method handleLinearVel;
        SubLevelPhysCaps(Method gmt, Method mass, Method inertia, Method com, Method grav,
                         Method ho, Method hv, Method ang, Method lin) {
            this.getMassTracker = gmt; this.massTrackerGetMass = mass;
            this.massTrackerInertia = inertia; this.massTrackerCoM = com;
            this.getGravityVector = grav; this.handleOf = ho;
            this.handleIsValid = hv; this.handleAngularVel = ang; this.handleLinearVel = lin;
        }
    }

    @Nullable
    private static SubLevelPhysCaps phys(Object serverSubLevel) {
        if (serverSubLevel == null) return null;
        Class<?> serverSubLevelClass = serverSubLevel.getClass();
        SubLevelPhysCaps p = PHYS;
        if (p != null && PHYS_SERVER_SUBLEVEL_CLASS == serverSubLevelClass) return p;
        try {
            Method getMassTracker = ReflectionAccessors.method(serverSubLevelClass, "getMassTracker");
            Class<?> massTracker = (getMassTracker != null) ? getMassTracker.getReturnType() : null;
            Method massTrackerGetMass = (massTracker != null) ? ReflectionAccessors.method(massTracker, "getMass") : null;
            Method massTrackerInertia = (massTracker != null) ? ReflectionAccessors.method(massTracker, "getInertiaTensor") : null;
            Method massTrackerCoM     = (massTracker != null) ? ReflectionAccessors.method(massTracker, "getCenterOfMass") : null;

            Method getGravityVector = ReflectionAccessors.method(serverSubLevelClass, "getGravityVector");

            Class<?> rbh = findClassAny(serverSubLevelClass.getClassLoader(),
                    "dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle",
                    "dev.ryanhcode.sable.api.physics.RigidBodyHandle",
                    "dev.ryanhcode.sable.physics.handle.RigidBodyHandle",
                    "dev.ryanhcode.sable.physics.RigidBodyHandle"
            );

            Method handleOf       = (rbh != null) ? ReflectionAccessors.findMethodAccepting(rbh, "of", serverSubLevelClass) : null;
            Method handleIsValid  = (rbh != null) ? ReflectionAccessors.method(rbh, "isValid") : null;
            Method handleAngVel   = (rbh != null) ? ReflectionAccessors.method(rbh, "getAngularVelocity", Vector3d.class) : null;
            Method handleLinVel   = (rbh != null) ? ReflectionAccessors.method(rbh, "getLinearVelocity", Vector3d.class) : null;

            p = new SubLevelPhysCaps(getMassTracker, massTrackerGetMass, massTrackerInertia, massTrackerCoM,
                                     getGravityVector, handleOf, handleIsValid, handleAngVel, handleLinVel);
            PHYS = p;
            PHYS_SERVER_SUBLEVEL_CLASS = serverSubLevelClass;
                DebugLogging.fccInfo("[FCC/Bridge] physics ready: serverSubLevel={} rbh={} gravityMethod={}",
                    serverSubLevelClass.getName(),
                    rbh == null ? "missing" : rbh.getName(),
                    getGravityVector != null);
            return p;
        } catch (LinkageError | RuntimeException ex) {
            if (SUBLEVEL_PHYS_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] sub-level physics bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    @Nullable
    private static Class<?> findClassAny(@Nullable ClassLoader loader, String... names) {
        if (loader == null) loader = SableBridge.class.getClassLoader();
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        return null;
    }

    public static double mass(@Nullable BlockEntity be) {
        Object sl = serverSubLevelOf(be);
        if (sl == null) return Double.NaN;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.getMassTracker == null || p.massTrackerGetMass == null) return Double.NaN;
        Object tracker = ReflectionAccessors.invoke(p.getMassTracker, sl);
        if (tracker == null) return Double.NaN;
        Object out = ReflectionAccessors.invoke(p.massTrackerGetMass, tracker);
        if (out instanceof Number n) return n.doubleValue();
        return Double.NaN;
    }

    @Nullable
    public static double[] inertiaTensor(@Nullable BlockEntity be) {
        Object sl = serverSubLevelOf(be);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.getMassTracker == null || p.massTrackerInertia == null) return null;
        Object tracker = ReflectionAccessors.invoke(p.getMassTracker, sl);
        if (tracker == null) return null;
        Object res = ReflectionAccessors.invoke(p.massTrackerInertia, tracker);
        if (res == null) return null;
        if (res instanceof Matrix3dc m) {
            double[] dst = new double[9];
            m.get(dst);
            return dst;
        }
        return null;
    }

    @Nullable
    public static Vector3d gravityWorld(@Nullable BlockEntity be) {
        Object sl = serverSubLevelOf(be);
        if (sl != null) {
            SubLevelPhysCaps p = phys(sl);
            if (p != null && p.getGravityVector != null) {
                Object res = ReflectionAccessors.invoke(p.getGravityVector, sl);
                if (res instanceof Vector3dc vd) return new Vector3d(vd);
            }
        }
        if (USE_DIMENSION_PHYSICS_GRAVITY_FALLBACK && be != null && be.getLevel() != null) {
            Level gravityLevel = be.getLevel();
            if (sl != null) {
                Method getLevel = ReflectionAccessors.method(sl.getClass(), "getLevel");
                Object resolvedLevel = getLevel != null ? ReflectionAccessors.invoke(getLevel, sl) : null;
                if (resolvedLevel instanceof Level level) gravityLevel = level;
            }
            Vector3d fb = gravityFromDimensionPhysics(gravityLevel, gravityLevel.getClass().getClassLoader());
            if (fb != null) return fb;
        }
        return null;
    }

    public static volatile boolean USE_DIMENSION_PHYSICS_GRAVITY_FALLBACK = true;

    @Nullable
    private static Vector3d gravityFromDimensionPhysics(net.minecraft.world.level.Level level, @Nullable ClassLoader loader) {
        try {
            Class<?> dpd = findClassAny(loader,
                    "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData",
                    "dev.ryanhcode.sable.api.physics.DimensionPhysicsData",
                    "dev.ryanhcode.sable.physics.DimensionPhysicsData",
                    "dev.ryanhcode.sable.api.dimension.DimensionPhysicsData",
                    "dev.ryanhcode.sable.dimension.DimensionPhysicsData"
            );
            if (dpd == null) return null;
            Method getGrav = ReflectionAccessors.method(dpd, "getGravity", net.minecraft.world.level.Level.class);
            if (getGrav == null) return null;
            Object res;
            if (java.lang.reflect.Modifier.isStatic(getGrav.getModifiers())) {
                res = ReflectionAccessors.invoke(getGrav, null, level);
            } else {
                Method getInstance = ReflectionAccessors.method(dpd, "getInstance");
                if (getInstance == null || !java.lang.reflect.Modifier.isStatic(getInstance.getModifiers())) return null;
                Object inst = ReflectionAccessors.invoke(getInstance, null);
                if (inst == null) return null;
                res = ReflectionAccessors.invoke(getGrav, inst, level);
            }
            if (res instanceof Vector3dc vd) return new Vector3d(vd);
        } catch (LinkageError | RuntimeException ignore) {  }
        return null;
    }

    @Nullable
    public static Vector3d angularVelocityWorld(@Nullable BlockEntity be) {
        Object handle = rigidBodyHandle(be);
        if (handle == null) return null;
        Object sl = serverSubLevelOf(be);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.handleAngularVel == null) return null;
        Vector3d dst = new Vector3d();
        Object out = ReflectionAccessors.invoke(p.handleAngularVel, handle, dst);
        if (out instanceof Vector3dc vd) return new Vector3d(vd);
        return new Vector3d(dst);
    }

    @Nullable
    public static Vector3d linearVelocityWorld(@Nullable BlockEntity be) {
        Object handle = rigidBodyHandle(be);
        if (handle == null) return null;
        Object sl = serverSubLevelOf(be);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.handleLinearVel == null) return null;
        Vector3d dst = new Vector3d();
        Object out = ReflectionAccessors.invoke(p.handleLinearVel, handle, dst);
        if (out instanceof Vector3dc vd) return new Vector3d(vd);
        return new Vector3d(dst);
    }

    @Nullable
    private static Object resolveServerSubLevelForPhysics(@Nullable Object subLevel) {
        if (subLevel == null) return null;
        if (ReflectionAccessors.method(subLevel.getClass(), "getMassTracker") != null) {
            return subLevel;
        }
        for (String accessor : new String[]{"getServerSubLevel", "serverSubLevel", "getSubLevel", "subLevel", "getServerLevel"}) {
            Method method = ReflectionAccessors.method(subLevel.getClass(), accessor);
            if (method == null) continue;
            Object res;
            try {
                res = ReflectionAccessors.invoke(method, subLevel);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (res != null && ReflectionAccessors.method(res.getClass(), "getMassTracker") != null) {
                return res;
            }
        }
        return null;
    }

    @Nullable
    public static Vector3d linearVelocityWorldOf(@Nullable Object subLevel) {
        Object sl = resolveServerSubLevelForPhysics(subLevel);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.handleLinearVel == null) return null;
        Object handle = rigidBodyHandleOf(sl);
        if (handle == null) return null;
        Vector3d dst = new Vector3d();
        Object out = ReflectionAccessors.invoke(p.handleLinearVel, handle, dst);
        if (out instanceof Vector3dc vd) return new Vector3d(vd);
        return new Vector3d(dst);
    }

    @Nullable
    public static Vector3d angularVelocityWorldOf(@Nullable Object subLevel) {
        Object sl = resolveServerSubLevelForPhysics(subLevel);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.handleAngularVel == null) return null;
        Object handle = rigidBodyHandleOf(sl);
        if (handle == null) return null;
        Vector3d dst = new Vector3d();
        Object out = ReflectionAccessors.invoke(p.handleAngularVel, handle, dst);
        if (out instanceof Vector3dc vd) return new Vector3d(vd);
        return new Vector3d(dst);
    }

    @Nullable
    public static Vector3d gravityWorldOf(@Nullable Object subLevel) {
        Object sl = resolveServerSubLevelForPhysics(subLevel);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.getGravityVector == null) return null;
        Object res = ReflectionAccessors.invoke(p.getGravityVector, sl);
        if (res instanceof Vector3dc vd) return new Vector3d(vd);
        return null;
    }

    public static double massOf(@Nullable Object subLevel) {
        Object sl = resolveServerSubLevelForPhysics(subLevel);
        if (sl == null) return Double.NaN;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.getMassTracker == null || p.massTrackerGetMass == null) return Double.NaN;
        Object tracker = ReflectionAccessors.invoke(p.getMassTracker, sl);
        if (tracker == null) return Double.NaN;
        Object out = ReflectionAccessors.invoke(p.massTrackerGetMass, tracker);
        if (out instanceof Number n) return n.doubleValue();
        return Double.NaN;
    }

    @Nullable
    private static Object rigidBodyHandleOf(@Nullable Object subLevel) {
        Object sl = resolveServerSubLevelForPhysics(subLevel);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.handleOf == null) return null;

        String key = "rbh:" + System.identityHashCode(sl);
        Object cached;
        synchronized (TICK_CACHE) { cached = TICK_CACHE.get(key); }
        if (cached == NULL_SENTINEL) return null;
        if (cached != null) return cached;

        Object handle = ReflectionAccessors.invoke(p.handleOf, null, sl);
        if (handle == null) {
            synchronized (TICK_CACHE) { TICK_CACHE.put(key, NULL_SENTINEL); }
            return null;
        }
        if (p.handleIsValid != null) {
            Object valid = ReflectionAccessors.invoke(p.handleIsValid, handle);
            if (Boolean.FALSE.equals(valid)) {
                synchronized (TICK_CACHE) { TICK_CACHE.put(key, NULL_SENTINEL); }
                return null;
            }
        }
        synchronized (TICK_CACHE) { TICK_CACHE.put(key, handle); }
        return handle;
    }

    @Nullable
    private static Object rigidBodyHandle(@Nullable BlockEntity be) {
        Object sl = serverSubLevelOf(be);
        if (sl == null) return null;
        SubLevelPhysCaps p = phys(sl);
        if (p == null || p.handleOf == null) return null;

        String key = "rbh:" + System.identityHashCode(sl);
        Object cached;
        synchronized (TICK_CACHE) { cached = TICK_CACHE.get(key); }
        if (cached == NULL_SENTINEL) return null;
        if (cached != null) return cached;

        Object handle = ReflectionAccessors.invoke(p.handleOf, null, sl);
        if (handle == null) {
            synchronized (TICK_CACHE) { TICK_CACHE.put(key, NULL_SENTINEL); }
            return null;
        }
        if (p.handleIsValid != null) {
            Object valid = ReflectionAccessors.invoke(p.handleIsValid, handle);
            if (Boolean.FALSE.equals(valid)) {
                synchronized (TICK_CACHE) { TICK_CACHE.put(key, NULL_SENTINEL); }
                return null;
            }
        }
        synchronized (TICK_CACHE) { TICK_CACHE.put(key, handle); }
        return handle;
    }

    @Nullable
    public static Object serverSubLevelOf(@Nullable BlockEntity be) {
        if (be == null) return null;
        Level lv = be.getLevel();
        if (lv == null) return null;
        for (Class<?> c = lv.getClass(); c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if ("dev.ryanhcode.sable.sublevel.ServerSubLevel".equals(name)
                    || "dev.ryanhcode.sable.subLevel.ServerSubLevel".equals(name)
                    || "dev.ryanhcode.sable.api.subLevel.ServerSubLevel".equals(name)
                    || "dev.ryanhcode.sable.api.sublevel.ServerSubLevel".equals(name)) {
                return lv;
            }
        }
        Object subLevel = subLevelAccessOf(be);
        if (subLevel == null) return null;
        Method getMassTracker = ReflectionAccessors.method(subLevel.getClass(), "getMassTracker");
        Method getLevel = ReflectionAccessors.method(subLevel.getClass(), "getLevel");
        return (getMassTracker != null || getLevel != null) ? subLevel : null;
    }

    @Nullable
    public static ServerLevel outerServerLevelOf(@Nullable BlockEntity be) {
        if (be == null) return null;
        Level ownLevel = be.getLevel();
        Object subLevel = serverSubLevelOf(be);
        if (subLevel != null) {
            Method getLevel = ReflectionAccessors.method(subLevel.getClass(), "getLevel");
            Object resolvedLevel = getLevel != null ? ReflectionAccessors.invoke(getLevel, subLevel) : null;
            if (resolvedLevel instanceof ServerLevel serverLevel) return serverLevel;
        }
        return ownLevel instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    public record PropData(
            double thrustSigned,

            double rpm,
            float sailPower,
            Vector3d thrustDirLocal,
            @Nullable Quaternionf tiltQuat,
            boolean isGyro
    ) {}

    private static final class AeroPropCaps {
        @Nullable final Class<?> propClass;
        @Nullable final Class<?> gyroClass;
        @Nullable final Field    thrustDirection;
        @Nullable final Field    totalSailPower;
        @Nullable final Method   getThrust;
        @Nullable final Method   getRotationSpeed;
        @Nullable final Field    tiltQuat;
        @Nullable final Field    tiltVector;
        AeroPropCaps(Class<?> pc, Class<?> gc, Field td, Field sp, Method gt, Method gr, Field tq, Field tv) {
            this.propClass = pc; this.gyroClass = gc; this.thrustDirection = td; this.totalSailPower = sp;
            this.getThrust = gt; this.getRotationSpeed = gr; this.tiltQuat = tq; this.tiltVector = tv;
        }
    }

    @Nullable
    private static AeroPropCaps props() {
        AeroPropCaps a = PROPS;
        if (a != null) return a;
        try {
            Class<?> pc = ReflectionAccessors.findClassAny(
                    "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity"
            );
            Class<?> gc = ReflectionAccessors.findClassAny(
                    "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.gyroscopic_propeller_bearing.GyroscopicPropellerBearingBlockEntity"
            );

            Field td = (pc != null) ? ReflectionAccessors.field(pc, "thrustDirection") : null;
            Field sp = (pc != null) ? ReflectionAccessors.field(pc, "totalSailPower") : null;
            Method gt = (pc != null) ? ReflectionAccessors.method(pc, "getThrust") : null;
            Method gr = (pc != null) ? ReflectionAccessors.method(pc, "getRotationSpeed") : null;
            Field tq = (gc != null) ? ReflectionAccessors.field(gc, "tiltQuat") : null;
            Field tv = (gc != null) ? ReflectionAccessors.field(gc, "tiltVector") : null;

            a = new AeroPropCaps(pc, gc, td, sp, gt, gr, tq, tv);
            PROPS = a;
            return a;
        } catch (LinkageError | RuntimeException ex) {
            if (AERO_PROPS_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] aeronautics propeller bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    @Nullable
    public static PropData readProp(@Nullable BlockEntity be) {
        if (be == null) return null;
        AeroPropCaps a = props();
        if (a == null || a.propClass == null) return null;
        if (!a.propClass.isInstance(be)) return null;

        Object dirObj = ReflectionAccessors.read(a.thrustDirection, be);
        Vector3d dir;
        if (dirObj instanceof Vector3d vd) dir = new Vector3d(vd);
        else dir = new Vector3d();

        Object spObj = ReflectionAccessors.read(a.totalSailPower, be);
        float sp = (spObj instanceof Number n) ? n.floatValue() : 0f;

        Object thObj = ReflectionAccessors.invoke(a.getThrust, be);
        double rawThrust = (thObj instanceof Number n) ? n.doubleValue() : 0.0;

        double th = -rawThrust;

        Object rpmObj = ReflectionAccessors.invoke(a.getRotationSpeed, be);
        double rpm = (rpmObj instanceof Number n) ? n.doubleValue() : 0.0;

        boolean isGyro = a.gyroClass != null && a.gyroClass.isInstance(be);
        Quaternionf q = null;
        if (isGyro && a.tiltQuat != null) {
            Object qObj = ReflectionAccessors.read(a.tiltQuat, be);
            if (qObj instanceof Quaternionf qf) q = new Quaternionf(qf);
        }
        return new PropData(th, rpm, sp, dir, q, isGyro);
    }

    public static boolean writeGyroTiltVector(@Nullable BlockEntity gyroBE, double x, double y, double z) {
        if (gyroBE == null) return false;
        AeroPropCaps a = props();
        if (a == null || a.gyroClass == null || a.tiltVector == null) return false;
        if (!a.gyroClass.isInstance(gyroBE)) return false;
        Object tv = ReflectionAccessors.read(a.tiltVector, gyroBE);
        if (!(tv instanceof Vector3d vec)) return false;

        vec.set(x, y, z);
        return true;
    }

    private static final class NavTableCaps {
        @Nullable final Class<?> navClass;
        @Nullable final Field    currentTarget;
        @Nullable final Method   distanceToTarget;
        @Nullable final Method   lastDistance;
        @Nullable final Method   relativeAngle;
        @Nullable final Method   projectedSelfPos;
        NavTableCaps(Class<?> c, Field f, Method d, Method l, Method a, Method p) {
            this.navClass = c; this.currentTarget = f;
            this.distanceToTarget = d; this.lastDistance = l;
            this.relativeAngle = a; this.projectedSelfPos = p;
        }
    }

    public record NavReading(Vec3 target, double distance, double closingRate,
                             double relativeAngleDeg, @Nullable Vec3 projectedSelf) {}

    public static final int NAV_SAMPLE_INTERVAL_TICKS = 11;

    @Nullable
    private static NavTableCaps nav() {
        NavTableCaps n = NAV;
        if (n != null) return n;
        try {
            Class<?> c = ReflectionAccessors.findClass(
                    "dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity");
            n = new NavTableCaps(c,
                    (c != null) ? ReflectionAccessors.field(c, "currentTarget") : null,
                    (c != null) ? ReflectionAccessors.method(c, "distanceToTarget") : null,
                    (c != null) ? ReflectionAccessors.method(c, "lastDistanceToTarget") : null,
                    (c != null) ? ReflectionAccessors.method(c, "getRelativeAngle") : null,
                    (c != null) ? ReflectionAccessors.method(c, "getProjectedSelfPos") : null);
            NAV = n;
            return n;
        } catch (LinkageError | RuntimeException ex) {
            if (NAV_TABLE_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] nav table bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    @Nullable
    public static NavReading readNav(@Nullable BlockEntity navBE) {
        NavTableCaps n = nav();
        if (navBE == null || n == null || n.navClass == null || !n.navClass.isInstance(navBE)) return null;
        Vec3 target = navTableTarget(navBE);
        if (target == null) return null;

        Double distance = asDouble(ReflectionAccessors.invoke(n.distanceToTarget, navBE));
        Double last = asDouble(ReflectionAccessors.invoke(n.lastDistance, navBE));
        Double angle = asDouble(ReflectionAccessors.invoke(n.relativeAngle, navBE));
        Object projected = ReflectionAccessors.invoke(n.projectedSelfPos, navBE);

        double resolvedDistance = distance == null ? Double.NaN : distance;

        double rate = (distance != null && last != null)
                ? (last - distance) / NAV_SAMPLE_INTERVAL_TICKS
                : Double.NaN;
        return new NavReading(target, resolvedDistance, rate,
                angle == null ? Double.NaN : angle,
                projected instanceof Vec3 v ? v : null);
    }

    @Nullable
    public static Vec3 navTableTarget(@Nullable BlockEntity navBE) {
        if (navBE == null) return null;
        NavTableCaps n = nav();
        if (n == null || n.navClass == null || n.currentTarget == null) return null;
        if (!n.navClass.isInstance(navBE)) return null;
        Object v = ReflectionAccessors.read(n.currentTarget, navBE);
        return (v instanceof Vec3 vec) ? vec : null;
    }

    @Nullable
    public static Vec3 navTableProjectedTarget(@Nullable BlockEntity navBE) {
        NavTableCaps n = nav();
        if (navBE == null || n == null || n.navClass == null || !n.navClass.isInstance(navBE)) return null;
        Method m = ReflectionAccessors.method(n.navClass, "getTargetPosition", boolean.class);
        Object v = ReflectionAccessors.invoke(m, navBE, true);
        return v instanceof Vec3 vec ? vec : null;
    }

    public static net.minecraft.world.item.ItemStack navTableItem(@Nullable BlockEntity navBE) {
        NavTableCaps n = nav();
        if (navBE == null || n == null || n.navClass == null || !n.navClass.isInstance(navBE)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        Object v = ReflectionAccessors.invoke(ReflectionAccessors.method(n.navClass, "getHeldItem"), navBE);
        return v instanceof net.minecraft.world.item.ItemStack s ? s : net.minecraft.world.item.ItemStack.EMPTY;
    }

    private static volatile @Nullable Method projectOut;

    public static Vec3 projectOutOfSubLevel(Level level, Vec3 point) {
        CompanionCaps c = companion();
        if (c == null || level == null || point == null) return point;
        Method m = projectOut;
        if (m == null) {
            m = ReflectionAccessors.findMethodAccepting(c.instance.getClass(), "projectOutOfSubLevel", Level.class, Vec3.class);
            if (m == null) return point;
            projectOut = m;
        }
        Object v = ReflectionAccessors.invoke(m, c.instance, level, point);
        return v instanceof Vec3 out && Double.isFinite(out.x) && Double.isFinite(out.z) ? out : point;
    }

    private static final AtomicBoolean SENSORS_WARNED = new AtomicBoolean(false);

    private static final class SensorCaps {
        @Nullable final Class<?> gimbalClass;
        @Nullable final Class<?> altitudeClass;
        @Nullable final Class<?> velocityClass;
        @Nullable final Class<?> opticalClass;

        @Nullable final Method gimbalXAngle;
        @Nullable final Method gimbalZAngle;

        @Nullable final Method altWorldHeight;
        @Nullable final Method altNormalHeight;
        @Nullable final Method altAirPressure;
        @Nullable final Method altSignal;

        @Nullable final Method velAdjusted;
        @Nullable final Method velNormal;
        @Nullable final Method velRedstone;

        @Nullable final Method optRayDistance;
        @Nullable final Method optHitDistance;
        @Nullable final Method optHitBlock;
        @Nullable final Method optRange;

        SensorCaps(Class<?> g, Class<?> a, Class<?> v, Class<?> o,
                   Method gx, Method gz,
                   Method aw, Method an, Method ap, Method as,
                   Method va, Method vn, Method vr,
                   Method ord, Method ohd, Method ohb, Method orr) {
            this.gimbalClass = g; this.altitudeClass = a; this.velocityClass = v; this.opticalClass = o;
            this.gimbalXAngle = gx; this.gimbalZAngle = gz;
            this.altWorldHeight = aw; this.altNormalHeight = an; this.altAirPressure = ap; this.altSignal = as;
            this.velAdjusted = va; this.velNormal = vn; this.velRedstone = vr;
            this.optRayDistance = ord; this.optHitDistance = ohd; this.optHitBlock = ohb; this.optRange = orr;
        }
    }

    private static volatile @Nullable SensorCaps SENSORS;

    @Nullable
    private static SensorCaps sensors() {
        SensorCaps s = SENSORS;
        if (s != null) return s;
        try {
            Class<?> g = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity");
            Class<?> a = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity");
            Class<?> v = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity");
            Class<?> o = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.lasers.optical_sensor.OpticalSensorBlockEntity");

            s = new SensorCaps(g, a, v, o,
                    (g != null) ? ReflectionAccessors.method(g, "getXAngle") : null,
                    (g != null) ? ReflectionAccessors.method(g, "getZAngle") : null,
                    (a != null) ? ReflectionAccessors.method(a, "getWorldHeight") : null,
                    (a != null) ? ReflectionAccessors.method(a, "getNormalHeight") : null,
                    (a != null) ? ReflectionAccessors.method(a, "getAirPressure") : null,
                    (a != null) ? ReflectionAccessors.method(a, "getSignal") : null,
                    (v != null) ? ReflectionAccessors.method(v, "getAdjustedVelocity") : null,
                    (v != null) ? ReflectionAccessors.method(v, "getCurrentNormal") : null,
                    (v != null) ? ReflectionAccessors.method(v, "getRedstoneStrength") : null,
                    (o != null) ? ReflectionAccessors.method(o, "getRayDistance") : null,
                    (o != null) ? ReflectionAccessors.method(o, "getHitBlockDistance") : null,
                    (o != null) ? ReflectionAccessors.method(o, "getHitBlock") : null,
                    (o != null) ? ReflectionAccessors.method(o, "getLaserRange") : null);
            SENSORS = s;
            DebugLogging.fccDebug("[FCC/Bridge] sensors gimbal={} altitude={} velocity={} optical={}",
                    g != null, a != null, v != null, o != null);
            return s;
        } catch (LinkageError | RuntimeException ex) {
            if (SENSORS_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] sensor bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    @Nullable public static Class<?> gimbalSensorClass()   { SensorCaps s = sensors(); return s == null ? null : s.gimbalClass; }
    @Nullable public static Class<?> altitudeSensorClass() { SensorCaps s = sensors(); return s == null ? null : s.altitudeClass; }
    @Nullable public static Class<?> velocitySensorClass() { SensorCaps s = sensors(); return s == null ? null : s.velocityClass; }
    @Nullable public static Class<?> opticalSensorClass()  { SensorCaps s = sensors(); return s == null ? null : s.opticalClass; }
    @Nullable public static Class<?> navTableClass()       { NavTableCaps n = nav();   return n == null ? null : n.navClass; }

    public static boolean matchesGimbalSensor(BlockEntity be) {
        Class<?> cls = gimbalSensorClass();
        if (cls != null) return cls.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("simulated") && rl.getPath().equals("gimbal_sensor");
    }

    public static boolean matchesAltitudeSensor(BlockEntity be) {
        Class<?> cls = altitudeSensorClass();
        if (cls != null) return cls.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("simulated") && rl.getPath().equals("altitude_sensor");
    }

    public static boolean matchesVelocitySensor(BlockEntity be) {
        Class<?> cls = velocitySensorClass();
        if (cls != null) return cls.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("simulated") && rl.getPath().equals("velocity_sensor");
    }

    public static boolean matchesNavTable(BlockEntity be) {
        Class<?> cls = navTableClass();
        if (cls != null) return cls.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("simulated") && rl.getPath().equals("nav_table");
    }

    public record GimbalReading(double xAngleDeg, double zAngleDeg) {}

    public record AltitudeReading(double worldHeight, double normalHeight, double airPressure, int signal) {}

    public record VelocityReading(double alongNormal, Vector3d normal, int signal) {}

    public record OpticalReading(double distance, double range, boolean hit, @Nullable String hitBlockName) {}

    @Nullable
    public static GimbalReading readGimbal(@Nullable BlockEntity be) {
        SensorCaps s = sensors();
        if (be == null || s == null || s.gimbalClass == null || !s.gimbalClass.isInstance(be)) return null;
        Double x = asDouble(ReflectionAccessors.invoke(s.gimbalXAngle, be));
        Double z = asDouble(ReflectionAccessors.invoke(s.gimbalZAngle, be));
        if (x == null && z == null) return null;
        return new GimbalReading(x == null ? 0.0 : x, z == null ? 0.0 : z);
    }

    @Nullable
    public static AltitudeReading readAltitude(@Nullable BlockEntity be) {
        SensorCaps s = sensors();
        if (be == null || s == null || s.altitudeClass == null || !s.altitudeClass.isInstance(be)) return null;
        Double world = asDouble(ReflectionAccessors.invoke(s.altWorldHeight, be));
        Double normal = asDouble(ReflectionAccessors.invoke(s.altNormalHeight, be));
        Double pressure = asDouble(ReflectionAccessors.invoke(s.altAirPressure, be));
        Double signal = asDouble(ReflectionAccessors.invoke(s.altSignal, be));
        if (world == null && normal == null && pressure == null) return null;
        return new AltitudeReading(
                world == null ? Double.NaN : world,
                normal == null ? Double.NaN : normal,
                pressure == null ? Double.NaN : pressure,
                signal == null ? 0 : (int) Math.round(signal));
    }

    @Nullable
    public static VelocityReading readVelocity(@Nullable BlockEntity be) {
        SensorCaps s = sensors();
        if (be == null || s == null || s.velocityClass == null || !s.velocityClass.isInstance(be)) return null;
        Double along = asDouble(ReflectionAccessors.invoke(s.velAdjusted, be));
        if (along == null) return null;
        Vector3d normal = new Vector3d();
        Object n = ReflectionAccessors.invoke(s.velNormal, be);
        if (n instanceof Vector3dc vc) normal.set(vc.x(), vc.y(), vc.z());
        Double signal = asDouble(ReflectionAccessors.invoke(s.velRedstone, be));
        return new VelocityReading(along, normal, signal == null ? 0 : (int) Math.round(signal));
    }

    @Nullable
    public static OpticalReading readOptical(@Nullable BlockEntity be) {
        SensorCaps s = sensors();
        if (be == null || s == null || s.opticalClass == null || !s.opticalClass.isInstance(be)) return null;
        Double ray = asDouble(ReflectionAccessors.invoke(s.optRayDistance, be));
        Double hitDist = asDouble(ReflectionAccessors.invoke(s.optHitDistance, be));
        Double range = asDouble(ReflectionAccessors.invoke(s.optRange, be));
        if (ray == null && hitDist == null) return null;
        Object block = ReflectionAccessors.invoke(s.optHitBlock, be);
        String blockName = null;
        if (block instanceof net.minecraft.world.level.block.Block b) {
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
            if (rl != null) blockName = rl.toString();
        }
        double distance = hitDist != null && hitDist > 0.0 ? hitDist : (ray == null ? Double.NaN : ray);
        return new OpticalReading(distance, range == null ? Double.NaN : range, blockName != null, blockName);
    }

    private static final AtomicBoolean SWIVEL_WARNED = new AtomicBoolean(false);

    private static final class SwivelCaps {
        @Nullable final Class<?> swivelClass;
        @Nullable final Field targetAngle;
        @Nullable final Method getTargetAngle;
        @Nullable final Method updateServo;
        @Nullable final Method isAssembled;
        SwivelCaps(Class<?> c, Field ta, Method gta, Method us, Method ia) {
            this.swivelClass = c; this.targetAngle = ta; this.getTargetAngle = gta;
            this.updateServo = us; this.isAssembled = ia;
        }
    }

    private static volatile @Nullable SwivelCaps SWIVEL;

    @Nullable
    private static SwivelCaps swivel() {
        SwivelCaps s = SWIVEL;
        if (s != null) return s;
        try {
            Class<?> c = ReflectionAccessors.findClass(
                    "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity");
            s = new SwivelCaps(c,
                    (c != null) ? ReflectionAccessors.field(c, "targetAngleDegrees") : null,
                    (c != null) ? ReflectionAccessors.method(c, "getTargetAngleDegrees") : null,
                    (c != null) ? ReflectionAccessors.method(c, "updateServoCoefficients") : null,
                    (c != null) ? ReflectionAccessors.method(c, "isAssembled") : null);
            SWIVEL = s;
            DebugLogging.fccDebug("[FCC/Bridge] swivel bearing class={} writable={}",
                    c != null, s.targetAngle != null);
            return s;
        } catch (LinkageError | RuntimeException ex) {
            if (SWIVEL_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] swivel bearing bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    public static boolean matchesSwivelBearing(@Nullable BlockEntity be) {
        if (be == null) return false;
        SwivelCaps s = swivel();
        if (s != null && s.swivelClass != null) return s.swivelClass.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("simulated") && rl.getPath().equals("swivel_bearing");
    }

    @Nullable
    public static Double readSwivelTargetAngle(@Nullable BlockEntity be) {
        SwivelCaps s = swivel();
        if (be == null || s == null || s.swivelClass == null || !s.swivelClass.isInstance(be)) return null;
        return asDouble(ReflectionAccessors.invoke(s.getTargetAngle, be));
    }

    public static boolean isSwivelAssembled(@Nullable BlockEntity be) {
        SwivelCaps s = swivel();
        if (be == null || s == null || s.swivelClass == null || !s.swivelClass.isInstance(be)) return false;
        Object result = ReflectionAccessors.invoke(s.isAssembled, be);
        return result instanceof Boolean b && b;
    }

    @Nullable
    public static Double swivelKineticSpeed(@Nullable BlockEntity be) {
        if (be == null) return null;
        SwivelCaps s = swivel();
        if (s == null || s.swivelClass == null || !s.swivelClass.isInstance(be)) return null;
        Method getSpeed = ReflectionAccessors.method(be.getClass(), "getSpeed");
        return asDouble(ReflectionAccessors.invoke(getSpeed, be));
    }

    public static boolean writeSwivelTargetAngle(@Nullable BlockEntity be, double degrees) {
        SwivelCaps s = swivel();
        if (be == null || s == null || s.swivelClass == null || s.targetAngle == null) return false;
        if (!s.swivelClass.isInstance(be)) return false;
        if (!Double.isFinite(degrees)) return false;
        try {
            s.targetAngle.setDouble(be, degrees);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            return false;
        }
        ReflectionAccessors.invoke(s.updateServo, be);
        return true;
    }

    private static final AtomicBoolean ANY_PROP_WARNED = new AtomicBoolean(false);

    private static final class AnyPropCaps {
        @Nullable final Class<?> propellerInterface;
        @Nullable final Method getBlockDirection;
        @Nullable final Method getThrust;
        @Nullable final Method getAirflow;
        @Nullable final Method isActive;
        AnyPropCaps(Class<?> i, Method d, Method t, Method a, Method act) {
            this.propellerInterface = i; this.getBlockDirection = d; this.getThrust = t;
            this.getAirflow = a; this.isActive = act;
        }
    }

    private static volatile @Nullable AnyPropCaps ANY_PROP;

    @Nullable
    private static AnyPropCaps anyProp() {
        AnyPropCaps a = ANY_PROP;
        if (a != null) return a;
        try {
            Class<?> i = ReflectionAccessors.findClass(
                    "dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller");
            a = new AnyPropCaps(i,
                    (i != null) ? ReflectionAccessors.method(i, "getBlockDirection") : null,
                    (i != null) ? ReflectionAccessors.method(i, "getThrust") : null,
                    (i != null) ? ReflectionAccessors.method(i, "getAirflow") : null,
                    (i != null) ? ReflectionAccessors.method(i, "isActive") : null);
            ANY_PROP = a;
            DebugLogging.fccDebug("[FCC/Bridge] generic propeller interface={} direction={}",
                    i != null, a.getBlockDirection != null);
            return a;
        } catch (LinkageError | RuntimeException ex) {
            if (ANY_PROP_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] generic propeller bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    public static boolean matchesAnyPropeller(@Nullable BlockEntity be) {
        if (be == null) return false;
        AnyPropCaps a = anyProp();
        if (a != null && a.propellerInterface != null) return a.propellerInterface.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("aeronautics") && rl.getPath().contains("propeller");
    }

    @Nullable
    public static net.minecraft.core.Direction propellerBlockDirection(@Nullable BlockEntity be) {
        AnyPropCaps a = anyProp();
        if (be == null || a == null || a.propellerInterface == null) return null;
        if (!a.propellerInterface.isInstance(be)) return null;
        Object result = ReflectionAccessors.invoke(a.getBlockDirection, be);
        return result instanceof net.minecraft.core.Direction direction ? direction : null;
    }

    @Nullable
    public static Double propellerThrust(@Nullable BlockEntity be) {
        AnyPropCaps a = anyProp();
        if (be == null || a == null || a.propellerInterface == null) return null;
        if (!a.propellerInterface.isInstance(be)) return null;
        return asDouble(ReflectionAccessors.invoke(a.getThrust, be));
    }

    public static boolean matchesOpticalSensor(BlockEntity be) {
        SensorCaps s = sensors();
        if (s != null && s.opticalClass != null) return s.opticalClass.isInstance(be);
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return rl != null && rl.getNamespace().equals("simulated") && rl.getPath().equals("optical_sensor");
    }

    @Nullable
    private static Double asDouble(@Nullable Object value) {
        return (value instanceof Number n) ? n.doubleValue() : null;
    }

    public static List<BlockPos> findBlocksOfType(@Nullable Level level, @Nullable BlockPos centerPos,
                                                  @Nullable Class<?> beClass, int chunkRadius) {
        if (level == null || centerPos == null || beClass == null) return List.of();
        if (chunkRadius < 0) chunkRadius = 0;
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
        try {
            int cxc = centerPos.getX() >> 4;
            int czc = centerPos.getZ() >> 4;
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    var chunk = level.getChunkSource().getChunk(cxc + dx, czc + dz, false);
                    if (chunk == null) continue;
                    for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                        BlockEntity be = chunk.getBlockEntity(bePos);
                        if (be != null && beClass.isInstance(be)) {
                            out.add(bePos.immutable());
                        }
                    }
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            DebugLogging.fccDebug("[FCC/Bridge] findBlocksOfType failed", ex);
            return List.of();
        }
        return out;
    }

    @Nullable
    public static BlockEntity getBlockEntityOnContraption(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return null;
        try {
            var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
            if (chunk == null) return null;
            return chunk.getBlockEntity(pos);
        } catch (RuntimeException | LinkageError ex) {
            DebugLogging.fccDebug("[FCC/Bridge] getBlockEntityOnContraption({}) failed: {}", pos, ex.toString());
            return null;
        }
    }
}
