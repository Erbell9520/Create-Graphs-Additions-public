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

/** [1] */
public final class SableBridge {

    // capability bootstrap (one-shot, atomic)

    private static final AtomicBoolean COMPANION_WARNED        = new AtomicBoolean(false);
    private static final AtomicBoolean SUBLEVEL_PHYS_WARNED    = new AtomicBoolean(false);
    private static final AtomicBoolean AERO_PROPS_WARNED       = new AtomicBoolean(false);
    private static final AtomicBoolean NAV_TABLE_WARNED        = new AtomicBoolean(false);

    // Lazy-initialized capability handles. null = not yet probed; non-null = ready.
    private static volatile @Nullable CompanionCaps   COMPANION;
    private static volatile @Nullable SubLevelPhysCaps PHYS;
    private static volatile @Nullable Class<?> PHYS_SERVER_SUBLEVEL_CLASS;
    private static volatile @Nullable AeroPropCaps    PROPS;
    private static volatile @Nullable NavTableCaps    NAV;

    // per-tick cache

    private static volatile long currentTick = Long.MIN_VALUE;
    private static final Map<String, Object> TICK_CACHE = new HashMap<>();
    private static final Object NULL_SENTINEL = new Object();

    private SableBridge() {}

    /** Reset the per-tick cache. Call once per server tick from the FCC BE. */
    public static void beginTick(long tick) {
        synchronized (TICK_CACHE) {
            if (tick != currentTick) {
                TICK_CACHE.clear();
                currentTick = tick;
            }
        }
    }

    // top-level availability

    /** {@code true} if the core companion + pose path is callable. */
    public static boolean isAvailable() {
        return companion() != null;
    }

    // =====================================================================
    // CAPABILITY: companion (SubLevelAccess + logicalPose)
    // =====================================================================

    private static final class CompanionCaps {
        final Object instance;                    // SableCompanion.INSTANCE
        final Method getContaining;               // SableCompanion#getContaining(BlockEntity) -> SubLevelAccess
        final Method getVelocity;                 // SableCompanion#getVelocity(Level, SubLevelAccess, Vec3) -> Vec3
        final Method logicalPose;                 // SubLevelAccess#logicalPose() -> Pose3dc
        final Method poseOrientation;             // Pose3dc#orientation() -> Quaterniond
        final Method posePosition;                // Pose3dc#position() -> Vector3d
        final Method poseRotationPoint;           // Pose3dc#rotationPoint() -> Vector3d (== CoM local)
        final Method poseTransformPosition;        // Pose3dc#transformPosition(Vector3dc, Vector3d)
        final Method poseTransformPositionInverse; // Pose3dc#transformPositionInverse(Vector3dc, Vector3d)
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
            // [2]
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Object instance = sableClass.getField("HELPER").get(null);
            if (instance == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] Sable.HELPER is null, Sable not initialized yet. Flight features disabled until next tick.");
                return null;
            }
            DebugLogging.fccDebug("[FCC/Bridge] companion bootstrap using HELPER class={}",
                    instance.getClass().getName());

            // [3]
            Method getContaining = ReflectionAccessors.findMethodAccepting(
                    instance.getClass(), "getContaining",
                    net.minecraft.world.level.block.entity.BlockEntity.class);
            if (getContaining == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] getContaining(BlockEntity) not found on {}",
                            instance.getClass().getName());
                return null;
            }

            // SubLevelAccess class is derived from getContaining's return type, this is
            // always from Sable's classloader regardless of which CL we used to bootstrap.
            Class<?> slaClass = getContaining.getReturnType();
            DebugLogging.fccDebug("[FCC/Bridge] slaClass={}", slaClass.getName());

            // logicalPose() → Pose3dc. Zero-arg, resolve from slaClass.
            Method logicalPose = ReflectionAccessors.findMethodAccepting(slaClass, "logicalPose");
            if (logicalPose == null) {
                if (COMPANION_WARNED.compareAndSet(false, true))
                    CreateStats.LOGGER.warn("[FCC/Bridge] logicalPose() not found on {}", slaClass.getName());
                return null;
            }
            Class<?> poseClass = logicalPose.getReturnType();

            // getVelocity(Level, SubLevelAccess, Vec3), the SubLevelAccess param type is
            // from Sable's classloader (= slaClass). Match: 3 params, middle param == slaClass.
            Method getVelocity = findVelocityMethod(instance.getClass(), slaClass);

            // Pose accessors (zero-arg).
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

    /**
     * Find {@code getVelocity(Level, SubLevelAccess, Vec3)} on the companion class.
     * The SubLevelAccess param type is from Sable's classloader (not ours), so we
     * match by name + param count + middle-param class identity.
     */
    @Nullable
    private static Method findVelocityMethod(Class<?> companionClass, Class<?> slaClass) {
        for (Method m : companionClass.getMethods()) {
            if (!m.getName().equals("getVelocity") || m.getParameterCount() != 3) continue;
            Class<?>[] p = m.getParameterTypes();
            // p[0] must accept Level (or subclass), p[1] must be slaClass, p[2] must accept Vec3
            if (p[0].isAssignableFrom(Level.class)
                    && p[1] == slaClass
                    && p[2].isAssignableFrom(Vec3.class)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /** [4] */
    @Nullable
    public static Object subLevelAccessOf(@Nullable BlockEntity be) {
        if (be == null) return null;
        CompanionCaps c = companion();
        if (c == null) return null;
        return ReflectionAccessors.invoke(c.getContaining, c.instance, be);
    }

    // =====================================================================
    // SHIP / SUB-LEVEL ENUMERATION (for targeting). Reflection-only; safe when Sable absent.
    // =====================================================================

    private static final String SUBLEVEL_CONTAINER_CLASS = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";

    /** All active sub-levels (ships) in a level, or an empty list when Sable is absent or has none. */
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

    /** The stable UUID of a sub-level (ship), or {@code null}. */
    @Nullable
    public static UUID subLevelId(@Nullable Object subLevel) {
        try {
            Object v = invokeNoArg(subLevel, "getUniqueId");
            return v instanceof UUID u ? u : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** A display name for a sub-level (ship), falling back to a short id. */
    public static String subLevelName(@Nullable Object subLevel) {
        try {
            Object n = invokeNoArg(subLevel, "getName");
            if (n instanceof String s && !s.isBlank()) return s;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        UUID id = subLevelId(subLevel);
        return id == null ? "Ship" : "Ship " + id.toString().substring(0, 8);
    }

    /** The world-space center of a sub-level (ship), or {@code null}. */
    @Nullable
    public static Vector3d subLevelWorldCenter(@Nullable Object subLevel) {
        Vector3d origin = originWorld(subLevel);
        if (origin != null && isFinite(origin)) return origin;
        Vector3d localCenter = centerOfMassLocal(subLevel);
        Vector3d world = localToWorld(subLevel, localCenter);
        return (world != null && isFinite(world)) ? world : null;
    }

    /** Find an active sub-level (ship) by its UUID, or {@code null}. */
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

    /** The body→world rotation quaternion. Returns a fresh {@link Quaterniond}. */
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

    /** Pose anchor in world coordinates, normally the world-space center of mass. Returns a fresh {@link Vector3d}. */
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

    /** Project a sub-level local coordinate into world coordinates using Sable's own pose transform. */
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

    /** [5] */
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
                return (dx * dx + dy * dy + dz * dz) <= 1024.0; // 32 blocks
            }
            // On a contraption but transform unavailable, trust the open.
            return true;
        }
        BlockPos pos = be.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /** Project a world coordinate back into sub-level local coordinates using Sable's own pose transform. */
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

    /**
     * Center of mass in sub-level <b>local</b> frame. Sourced from
     * {@code logicalPose().rotationPoint()}, which {@code MergedMassTracker} writes
     * each physics tick.
     */
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

    /**
     * Velocity of a fixed world-space point on the contraption (includes rotational
     * contribution via the rigid body). Result is cached per-tick keyed on world coords.
     *
     * @param outerLevel the real Minecraft {@link Level} (NOT the sub-level)
     * @param subLevelAccess the SubLevelAccess from {@link #subLevelAccessOf}
     * @param worldPos the world-space query point (typically world CoM)
     */
    @Nullable
    public static Vec3 velocityAt(@Nullable Level outerLevel, @Nullable Object subLevelAccess, @Nullable Vec3 worldPos) {
        if (outerLevel == null || subLevelAccess == null || worldPos == null) return null;
        CompanionCaps c = companion();
        if (c == null || c.getVelocity == null) return null;
        Vector3d local = worldToLocal(subLevelAccess, new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        if (local == null) return null;

        // Cache key includes coords (defensive, a Vec3 reused across calls would alias otherwise).
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

    private static boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    // =====================================================================
    // CAPABILITY: subLevelPhysics (mass, inertia, gravity, RigidBodyHandle)
    // =====================================================================

    private static final class SubLevelPhysCaps {
        @Nullable final Method getMassTracker;        // ServerSubLevel#getMassTracker() -> MassTracker
        @Nullable final Method massTrackerGetMass;    // MassTracker#getMass() -> double
        @Nullable final Method massTrackerInertia;    // MassTracker#getInertiaTensor() -> Matrix3d
        @Nullable final Method massTrackerCoM;        // MassTracker#getCenterOfMass() -> Vector3d (fallback)
        @Nullable final Method getGravityVector;      // SubLevel#getGravityVector() -> Vector3d
        @Nullable final Method handleOf;              // RigidBodyHandle.of(ServerSubLevel) -> RigidBodyHandle
        @Nullable final Method handleIsValid;         // RigidBodyHandle#isValid() -> boolean
        @Nullable final Method handleAngularVel;      // RigidBodyHandle#getAngularVelocity(Vector3d) -> Vector3d
        @Nullable final Method handleLinearVel;       // RigidBodyHandle#getLinearVelocity(Vector3d) -> Vector3d
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

    /** Total mass in kg (or whatever Sable's units are). NaN if unavailable. */
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

    /** [6] */
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

    /** [7] */
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

    /** Fallback enabled after in-game testing showed {@code ServerSubLevel#getGravityVector()} can return null. */
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
        } catch (LinkageError | RuntimeException ignore) { /* dormant fallback, silent */ }
        return null;
    }

    /** Angular velocity in world frame, fresh copy. */
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

    /** Linear velocity in world frame, fresh copy. */
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

    // [8]

    /** [9] */
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

    /** COM linear velocity (world frame) for a target sub-level Object. {@code null} if unavailable. */
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

    /** COM angular velocity (world frame, rad/tick) for a target sub-level Object. {@code null} if unavailable. */
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

    /** Gravity vector (world frame) for a target sub-level Object. {@code null} if unavailable. */
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

    /** Total mass for a target sub-level Object. {@code NaN} if unavailable. */
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

    /** {@code be.getLevel()} downcast to ServerSubLevel if applicable; else {@code null}. */
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

    // =====================================================================
    // CAPABILITY: aeronauticsProps
    // =====================================================================

    /** Snapshot of one propeller bearing's relevant state. All vectors are fresh copies. */
    public record PropData(
            double thrustSigned,            // Physical force scalar along thrustDirLocal
                                            // (positive = force in direction of thrustDirLocal).
                                            // Equals -BlockEntityPropeller.getThrust() because
                                            // Sable applies -getThrust * airflow * pressure.
            double rpm,                     // getRotationSpeed()
            float sailPower,                // totalSailPower
            Vector3d thrustDirLocal,        // sub-level local frame (NOT world)
            @Nullable Quaternionf tiltQuat, // gyro: actual lerp'd orientation
            boolean isGyro
    ) {}

    private static final class AeroPropCaps {
        @Nullable final Class<?> propClass;
        @Nullable final Class<?> gyroClass;
        @Nullable final Field    thrustDirection;     // PropellerBearingBE.thrustDirection (Vector3d, sublevel-local)
        @Nullable final Field    totalSailPower;      // PropellerBearingBE.totalSailPower (float)
        @Nullable final Method   getThrust;           // PropellerBearingBE.getThrust() -> double
        @Nullable final Method   getRotationSpeed;    // PropellerBearingBE.getRotationSpeed() -> float
        @Nullable final Field    tiltQuat;            // GyroscopicPropellerBearingBE.tiltQuat (Quaternionf)
        @Nullable final Field    tiltVector;          // GyroscopicPropellerBearingBE.tiltVector (Vector3d, protected final, must mutate in place)
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

    /** Read a snapshot of a propeller bearing BE. Returns {@code null} if {@code be} is not a propeller. */
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
        // [10]
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

    /**
     * Write a new target tilt direction into a gyro propeller's {@code tiltVector} field.
     * <b>Mutates in place</b> (the field is {@code protected final} so we cannot replace it).
     *
     * <p>The vector is in <b>sub-level local</b> frame.</p>
     *
     * @return {@code true} if the write succeeded, {@code false} otherwise
     */
    public static boolean writeGyroTiltVector(@Nullable BlockEntity gyroBE, double x, double y, double z) {
        if (gyroBE == null) return false;
        AeroPropCaps a = props();
        if (a == null || a.gyroClass == null || a.tiltVector == null) return false;
        if (!a.gyroClass.isInstance(gyroBE)) return false;
        Object tv = ReflectionAccessors.read(a.tiltVector, gyroBE);
        if (!(tv instanceof Vector3d vec)) return false;
        // In-place mutation, required because field is protected final.
        vec.set(x, y, z);
        return true;
    }

    // =====================================================================
    // CAPABILITY: navTable
    // =====================================================================

    private static final class NavTableCaps {
        @Nullable final Class<?> navClass;
        @Nullable final Field    currentTarget;     // public Vec3 currentTarget
        NavTableCaps(Class<?> c, Field f) { this.navClass = c; this.currentTarget = f; }
    }

    @Nullable
    private static NavTableCaps nav() {
        NavTableCaps n = NAV;
        if (n != null) return n;
        try {
            Class<?> c = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity");
            Field f = (c != null) ? ReflectionAccessors.field(c, "currentTarget") : null;
            n = new NavTableCaps(c, f);
            NAV = n;
            return n;
        } catch (LinkageError | RuntimeException ex) {
            if (NAV_TABLE_WARNED.compareAndSet(false, true))
                CreateStats.LOGGER.warn("[FCC/Bridge] nav table bootstrap failed: {}", ex.toString());
            return null;
        }
    }

    /** Current world-space target of a Nav Table BE, or {@code null} if not navigating. */
    @Nullable
    public static Vec3 navTableTarget(@Nullable BlockEntity navBE) {
        if (navBE == null) return null;
        NavTableCaps n = nav();
        if (n == null || n.navClass == null || n.currentTarget == null) return null;
        if (!n.navClass.isInstance(navBE)) return null;
        Object v = ReflectionAccessors.read(n.currentTarget, navBE);
        return (v instanceof Vec3 vec) ? vec : null;
    }

    // =====================================================================
    // CAPABILITY: sensors (Simulated GimbalSensor / AltitudeSensor / VelocitySensor)
    // =====================================================================

    private static final AtomicBoolean SENSORS_WARNED = new AtomicBoolean(false);

    private static final class SensorCaps {
        @Nullable final Class<?> gimbalClass;
        @Nullable final Class<?> altitudeClass;
        @Nullable final Class<?> velocityClass;
        SensorCaps(Class<?> g, Class<?> a, Class<?> v) {
            this.gimbalClass = g; this.altitudeClass = a; this.velocityClass = v;
        }
    }

    private static volatile @Nullable SensorCaps SENSORS;

    @Nullable
    private static SensorCaps sensors() {
        SensorCaps s = SENSORS;
        if (s != null) return s;
        try {
            // FQNs verified against SimulatedCompatHelper:70-72.
            Class<?> g = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity");
            Class<?> a = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity");
            Class<?> v = ReflectionAccessors.findClass("dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity");
            s = new SensorCaps(g, a, v);
            SENSORS = s;
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
    @Nullable public static Class<?> navTableClass()       { NavTableCaps n = nav();   return n == null ? null : n.navClass; }

    // predicate-based matchers (use class token when available; fall back to BE-type registry key)

    /** [11] */
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

    // =====================================================================
    // Sensor / on-contraption block discovery
    // =====================================================================

    /** [12] */
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
            // Defensive: never let a chunk-iteration glitch break sensor discovery.
            DebugLogging.fccDebug("[FCC/Bridge] findBlocksOfType failed", ex);
            return List.of();
        }
        return out;
    }

    /** [13] */
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
