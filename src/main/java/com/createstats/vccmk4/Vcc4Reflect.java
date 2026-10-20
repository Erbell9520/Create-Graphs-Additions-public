package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.core.WheelForce;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// every reflective reach in one place, each fails to "not driven" with a log line
public final class Vcc4Reflect {
    static final String SWIVEL = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity";

    static final String SWIVEL_PLATE = "dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity";
    static final String WHEEL_MOUNT = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity";

    static final String ROPE_HOLDER = "dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity";
    static final String SPRING = "dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity";
    static final String DOCKING_CONNECTOR = "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity";
    private static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static final String PIPELINE = "dev.ryanhcode.sable.api.physics.PhysicsPipeline";
    private static final String SERVER_SUB_LEVEL = "dev.ryanhcode.sable.sublevel.ServerSubLevel";
    private static final String BRAKE_HANDLER = "createStats$brakeSignal";

    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static volatile Field swivelTarget;
    private static volatile Method swivelSubLevelId;
    private static volatile Method swivelPlatePos;
    private static volatile Field plateParent;
    private static volatile Field plateParentSubLevel;
    private static volatile Method neighbours;
    private static volatile Field governorLastInput;
    private static volatile Boolean brakeMixinApplied;
    private static volatile Object sableHelper;
    private static volatile Method vehicleSubLevel;
    private static volatile boolean vehicleSubLevelMissing;
    private static volatile Field springDesired;
    private static volatile Field springController;
    private static volatile Field dockOther;
    private static volatile Field dockOtherSubLevel;
    private static volatile Field wheelFriction;

    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private Vcc4Reflect() {
    }

    @Nullable
    static Class<?> cls(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Optional.of(Class.forName(n));
            } catch (ClassNotFoundException | LinkageError e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    static boolean is(String className, @Nullable BlockEntity be) {
        Class<?> c = cls(className);
        return be != null && c != null && c.isInstance(be);
    }

    // this proves the handler merged, not that the injector found its call. there's a separate check for that
    static boolean brakeMixinApplied() {
        Boolean b = brakeMixinApplied;
        if (b == null) {
            boolean found = false;
            Class<?> c = cls(WHEEL_MOUNT);
            if (c != null) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().contains(BRAKE_HANDLER)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) CreateStats.LOGGER.warn("[VCC Mk IV] brake mixin did not apply to {}; the brake key will not work", WHEEL_MOUNT);
            brakeMixinApplied = b = found;
        }
        return b;
    }

    public static boolean setSwivelAngle(ServerLevel level, BlockEntity swivel, double degrees) {
        Field f = swivelTarget;
        if (f == null) {
            Class<?> c = cls(SWIVEL);
            if (c == null) return false;
            try {
                f = c.getDeclaredField("targetAngleDegrees");
                f.setAccessible(true);
                swivelTarget = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("swivelTarget", e);
                return false;
            }
        }
        try {
            double current = f.getDouble(swivel);
            if (Math.abs(current - degrees) < 1.0e-4) return false;
            f.setDouble(swivel, degrees);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("swivelWrite", e);
            return false;
        }
        wake(level, SableBridge.serverSubLevelOf(swivel));
        UUID attached = swivelSubLevelId(swivel);
        if (attached != null) wake(level, SableBridge.findSubLevelById(level, attached));
        return true;
    }

    @Nullable
    static UUID swivelSubLevelId(BlockEntity swivel) {
        Method m = swivelSubLevelId;
        if (m == null) {
            Class<?> c = cls(SWIVEL);
            if (c == null) return null;
            try {
                m = c.getMethod("getSubLevelID");
                swivelSubLevelId = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("swivelSubLevelId", e);
                return null;
            }
        }
        try {
            Object v = m.invoke(swivel);
            return v instanceof UUID u ? u : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    static BlockPos swivelPlatePos(BlockEntity swivel) {
        Method m = swivelPlatePos;
        if (m == null) {
            Class<?> c = cls(SWIVEL);
            if (c == null) return null;
            try {
                m = c.getMethod("getPlatePos");
                swivelPlatePos = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("swivelPlatePos", e);
                return null;
            }
        }
        try {
            return m.invoke(swivel) instanceof BlockPos p ? p : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    static Joint plateParent(BlockEntity plate) {
        Field pos = plateParent;
        Field id = plateParentSubLevel;
        if (pos == null || id == null) {
            Class<?> c = cls(SWIVEL_PLATE);
            if (c == null) return null;
            try {
                pos = c.getDeclaredField("parent");
                pos.setAccessible(true);
                id = c.getDeclaredField("parentSubLevelId");
                id.setAccessible(true);
                plateParent = pos;
                plateParentSubLevel = id;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("plateParent", e);
                return null;
            }
        }
        try {
            Object p = pos.get(plate);
            Object u = id.get(plate);
            return p instanceof BlockPos bp && u instanceof UUID uuid ? new Joint(uuid, bp) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    record Joint(UUID body, BlockPos anchor) {
    }

    static void wake(ServerLevel level, @Nullable Object subLevel) {
        if (subLevel == null) return;
        try {
            Class<?> ssl = cls(SERVER_SUB_LEVEL);
            Class<?> pipelineClass = cls(PIPELINE);
            Class<?> containerClass = cls(CONTAINER);
            if (ssl == null || pipelineClass == null || containerClass == null || !ssl.isInstance(subLevel)) return;
            Method getContainer = null;
            for (Method m : containerClass.getMethods()) {
                if (m.getName().equals("getContainer") && Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(level)) {
                    getContainer = m;
                    break;
                }
            }
            if (getContainer == null) return;
            Object container = getContainer.invoke(null, level);
            if (container == null) return;
            Object system = container.getClass().getMethod("physicsSystem").invoke(container);
            if (system == null) return;
            Object pipeline = system.getClass().getMethod("getPipeline").invoke(system);
            if (pipeline == null) return;
            Method wakeUp = com.createstats.flight.bridge.ReflectionAccessors.findMethodAccepting(pipelineClass, "wakeUp", ssl);
            if (wakeUp == null) throw new NoSuchMethodException(pipelineClass.getName() + ".wakeUp(<" + ssl.getName() + ">)");
            wakeUp.invoke(pipeline, subLevel);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce("wake", e);
        }
    }

    @Nullable
    public static UUID seatCraftId(Entity entity) {
        if (entity == null || entity.getVehicle() == null || vehicleSubLevelMissing) return null;
        Method m = vehicleSubLevel;
        Object helper = sableHelper;
        if (m == null || helper == null) {
            try {
                helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                if (helper == null) return null;
                for (Method candidate : helper.getClass().getMethods()) {
                    if (candidate.getName().equals("getVehicleSubLevel") && candidate.getParameterCount() == 1
                            && candidate.getParameterTypes()[0].isAssignableFrom(Entity.class)) {
                        m = candidate;
                        break;
                    }
                }
                if (m == null) {
                    vehicleSubLevelMissing = true;
                    warnOnce("seatCraft", new NoSuchMethodException("getVehicleSubLevel(Entity)"));
                    return null;
                }
                sableHelper = helper;
                vehicleSubLevel = m;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                vehicleSubLevelMissing = true;
                warnOnce("seatCraft", e);
                return null;
            }
        }
        try {
            Object craft = m.invoke(helper, entity);
            return craft == null ? null : SableBridge.subLevelId(craft);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static List<BlockPos> potentialNeighbours(KineticBlockEntity be) {
        Method m = neighbours;
        if (m == null) {
            try {
                m = RotationPropagator.class.getDeclaredMethod("getPotentialNeighbourLocations", KineticBlockEntity.class);
                m.setAccessible(true);
                neighbours = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("neighbours", e);
                return fallbackNeighbours(be);
            }
        }
        try {
            Object v = m.invoke(null, be);
            return v instanceof List<?> list ? (List<BlockPos>) list : fallbackNeighbours(be);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("neighboursInvoke", e);
            return fallbackNeighbours(be);
        }
    }

    private static List<BlockPos> fallbackNeighbours(KineticBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return List.of(p.north(), p.south(), p.east(), p.west(), p.above(), p.below());
    }

    @Nullable
    static Direction governorLastInput(RpmGovernorBE governor) {
        Field f = governorLastInput;
        if (f == null) {
            try {
                f = RpmGovernorBE.class.getDeclaredField("lastKnownInputFacing");
                f.setAccessible(true);
                governorLastInput = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("governorLastInput", e);
                return null;
            }
        }
        try {
            return f.get(governor) instanceof Direction d ? d : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    record RopeEnd(@Nullable UUID body, BlockPos block) {
    }

    record Rope(Object strand, RopeEnd start, RopeEnd end, double nominal, double current) {
        RopeEnd other(BlockPos here) {
            return start.block().equals(here) ? end : start;
        }
    }

    @Nullable
    static Rope rope(@Nullable BlockEntity holder) {
        if (!is(ROPE_HOLDER, holder)) return null;
        try {
            Object behaviour = call(holder, "getBehavior");
            Object strand = call(behaviour, "getAttachedStrand");
            if (strand == null) return null;
            RopeEnd start = null;
            RopeEnd end = null;
            if (call(strand, "getAttachments") instanceof Iterable<?> attachments) {
                for (Object a : attachments) {
                    Object point = call(a, "point");
                    Object id = call(a, "subLevelID");
                    if (!(call(a, "blockAttachment") instanceof BlockPos block)) continue;
                    RopeEnd e = new RopeEnd(id instanceof UUID u ? u : null, block);
                    if (point != null && "START".equals(point.toString())) start = e;
                    else end = e;
                }
            }
            if (start == null || end == null) return null;
            double goal = call(strand, "getExtension") instanceof Number n ? n.doubleValue() : 1.0;
            int points = call(strand, "getPoints") instanceof List<?> list ? list.size() : 2;
            double current = call(strand, "getCurrentExtension") instanceof Number n ? n.doubleValue() : Double.NaN;
            return new Rope(strand, start, end, goal + Math.max(0, points - 2), current);
        } catch (RuntimeException | LinkageError e) {
            warnOnce("rope", e);
            return null;
        }
    }

    @Nullable
    static Vector3d ropeAttachPoint(@Nullable BlockEntity holder) {
        if (!is(ROPE_HOLDER, holder)) return null;
        try {
            Object behaviour = call(holder, "getBehavior");
            return call(behaviour, "getAttachmentPoint") instanceof Vec3 v ? new Vector3d(v.x, v.y, v.z) : null;
        } catch (RuntimeException | LinkageError e) {
            warnOnce("ropeAttachPoint", e);
            return null;
        }
    }

    @Nullable
    static BlockEntity springPartner(@Nullable BlockEntity spring) {
        if (!is(SPRING, spring)) return null;
        try {
            return call(spring, "getPairedSpring") instanceof BlockEntity be ? be : null;
        } catch (RuntimeException | LinkageError e) {
            warnOnce("springPartner", e);
            return null;
        }
    }

    @Nullable
    static Vector3d springCentre(@Nullable BlockEntity spring) {
        if (!is(SPRING, spring)) return null;
        try {
            return call(spring, "getCenter") instanceof Vector3dc c ? new Vector3d(c) : null;
        } catch (RuntimeException | LinkageError e) {
            warnOnce("springCentre", e);
            return null;
        }
    }

    static double springDesired(@Nullable BlockEntity spring) {
        if (!is(SPRING, spring)) return Double.NaN;
        Field f = springDesired;
        if (f == null) {
            f = field(SPRING, "desiredLength", "springDesired");
            if (f == null) return Double.NaN;
            springDesired = f;
        }
        try {
            return f.getDouble(spring);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Double.NaN;
        }
    }

    static boolean springController(@Nullable BlockEntity spring) {
        if (!is(SPRING, spring)) return false;
        Field f = springController;
        if (f == null) {
            f = field(SPRING, "isController", "springController");
            if (f == null) return false;
            springController = f;
        }
        try {
            return f.getBoolean(spring);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static double springSize(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (!"size".equals(p.getName())) continue;
            String v = String.valueOf(state.getValue(p)).toLowerCase(Locale.ROOT);
            if (v.contains("large")) return 8.0;
            if (v.contains("small")) return 0.5;
            return 1.0;
        }
        return 1.0;
    }

    record Dock(@Nullable BlockPos other, @Nullable UUID otherBody, boolean locked) {
    }

    @Nullable
    static Dock dock(@Nullable BlockEntity be) {
        if (!is(DOCKING_CONNECTOR, be)) return null;
        Field pos = dockOther;
        Field id = dockOtherSubLevel;
        if (pos == null || id == null) {
            pos = field(DOCKING_CONNECTOR, "otherConnectorPosition", "dockOther");
            id = field(DOCKING_CONNECTOR, "otherConnectorSubLevelId", "dockOtherSubLevel");
            if (pos == null || id == null) return null;
            dockOther = pos;
            dockOtherSubLevel = id;
        }
        try {
            Object p = pos.get(be);
            Object u = id.get(be);
            boolean locked = call(be, "isLocked") instanceof Boolean b && b;
            return new Dock(p instanceof BlockPos bp ? bp : null, u instanceof UUID uuid ? uuid : null, locked);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static double wheelStrength(@Nullable BlockEntity mount) {
        if (mount instanceof SmartBlockEntity smart) {
            try {
                ScrollValueBehaviour s = BlockEntityBehaviour.get(smart, ScrollValueBehaviour.TYPE);
                if (s != null && s.getValue() > 0) return s.getValue();
            } catch (RuntimeException e) {
                warnOnce("wheelStrength", e);
            }
        }
        return WheelForce.DEFAULT_STRENGTH;
    }

    static double wheelSurface(@Nullable BlockEntity mount) {
        if (!is(WHEEL_MOUNT, mount)) return 1.0;
        Field f = wheelFriction;
        if (f == null) {
            f = field(WHEEL_MOUNT, "touchingFriction", "wheelSurface");
            if (f == null) return 1.0;
            wheelFriction = f;
        }
        try {
            double v = f.getDouble(mount);
            return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 1.0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0;
        }
    }

    static final String THRUSTER = "dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity";
    static final String THRUSTER_SOLID = "dev.propulsionteam.propulsionsimulated.content.thruster.solid_fuel_thruster.SolidFuelThrusterBlockEntity";

    static final String THRUSTER_VECTOR = "dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.VectorThrusterBlockEntity";
    private static final String THRUSTER_CONTROL_MODE = THRUSTER + "$ControlMode";

    private static volatile Object thrusterPeripheral;
    private static volatile Object thrusterNormal;
    private static volatile Method thrusterSetMode;
    private static volatile Method thrusterSetDigital;
    private static volatile Method thrusterSetRedstone;
    private static volatile Method thrusterSetVector;
    private static volatile Method thrusterClearShare;
    private static volatile Method thrusterIsWorking;
    private static volatile Method thrusterRawCap;
    private static volatile Method thrusterUnits;
    private static volatile Field thrusterWidth;

    static boolean thrusterCommand(@Nullable BlockEntity be, double command) {
        if (!is(THRUSTER, be)) return false;
        Method mode = thrusterSetMode;
        Method digital = thrusterSetDigital;
        Object peripheral = thrusterPeripheral;
        if (mode == null || digital == null || peripheral == null) {
            if (!thrusterModes()) return false;
            mode = thrusterSetMode;
            digital = thrusterSetDigital;
            peripheral = thrusterPeripheral;
        }
        try {
            mode.invoke(be, peripheral);
            digital.invoke(be, (float) Math.max(0.0, Math.min(1.0, command)));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("thrusterCommand", e);
            return false;
        }
    }

    static boolean thrusterHandBack(@Nullable BlockEntity be) {
        if (!is(THRUSTER, be)) return false;
        if (thrusterSetMode == null && !thrusterModes()) return false;
        try {
            thrusterSetDigital.invoke(be, 0f);
            if (be.getLevel() != null) thrusterSetRedstone.invoke(be, be.getLevel().getBestNeighborSignal(be.getBlockPos()));
            thrusterSetMode.invoke(be, thrusterNormal);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("thrusterHandBack", e);
            return false;
        }
    }

    private static synchronized boolean thrusterModes() {
        if (thrusterSetMode != null) return true;
        Class<?> c = cls(THRUSTER);
        Class<?> modes = cls(THRUSTER_CONTROL_MODE);
        if (c == null || modes == null) return false;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object peripheral = Enum.valueOf((Class) modes, "PERIPHERAL");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object normal = Enum.valueOf((Class) modes, "NORMAL");
            Method setMode = c.getMethod("setControlMode", modes);
            Method setDigital = c.getMethod("setDigitalInput", float.class);
            Method setRedstone = c.getMethod("setRedstoneInput", int.class);
            thrusterPeripheral = peripheral;
            thrusterNormal = normal;
            thrusterSetDigital = setDigital;
            thrusterSetRedstone = setRedstone;
            thrusterSetMode = setMode;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("thrusterModes", e);
            return false;
        }
    }

    static double thrusterThrust(@Nullable BlockEntity be) {
        return number(call(be, "getCurrentThrust"));
    }

    static double thrusterUnits(@Nullable BlockEntity be) {
        Method m = thrusterUnits;
        if (m == null) {
            m = declared(THRUSTER, "getThrustUnitsPerKn", "thrusterUnits");
            if (m == null) return 1000.0;
            thrusterUnits = m;
        }
        double v = number(invoke(m, be));
        return Double.isFinite(v) && v > 0.0 ? v : 1000.0;
    }

    @Nullable
    static Vector3d thrusterDirection(@Nullable BlockEntity be) {
        return call(be, "getThrustDirectionLocal") instanceof Vector3dc v ? new Vector3d(v) : null;
    }

    static double thrusterLit(@Nullable BlockEntity be, boolean creative) {
        return number(call(be, creative ? "getEffectiveThrottle" : "getEffectiveThrustPercentage"));
    }

    static double thrusterClear(@Nullable BlockEntity be) {
        Method m = thrusterClearShare;
        if (m == null) {
            m = declared(THRUSTER, "calculateObstructionEffect", "thrusterClear");
            if (m == null) return 1.0;
            thrusterClearShare = m;
        }
        double v = number(invoke(m, be));
        return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 1.0;
    }

    static boolean thrusterWorking(@Nullable BlockEntity be) {
        Method m = thrusterIsWorking;
        if (m == null) {
            m = declared(THRUSTER, "isWorking", "thrusterWorking");
            if (m == null) return true;
            thrusterIsWorking = m;
        }
        return !(invoke(m, be) instanceof Boolean b) || b;
    }

    static double thrusterConfigForce(@Nullable BlockEntity be) {
        Method m = thrusterRawCap;
        if (m == null) {
            m = declared(THRUSTER, "getRawThrustCap", "thrusterConfigForce");
            if (m == null) return Double.NaN;
            thrusterRawCap = m;
        }
        return number(invoke(m, be));
    }

    static int thrusterWidth(@Nullable BlockEntity be) {
        Field f = thrusterWidth;
        if (f == null) {
            f = field(THRUSTER, "width", "thrusterWidth");
            if (f == null) return 1;
            thrusterWidth = f;
        }
        try {
            return Math.max(1, f.getInt(be));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    static boolean thrusterController(@Nullable BlockEntity be) {
        return call(be, "isController") instanceof Boolean b && b;
    }

    static boolean thrusterCreative(@Nullable BlockEntity be) {
        return call(be, "isCreative") instanceof Boolean b && b;
    }

    static boolean thrusterIon(@Nullable BlockEntity be) {
        return call(be, "isIon") instanceof Boolean b && b;
    }

    static int[] thrusterFuel(@Nullable BlockEntity be) {
        int amount = (int) number(call(be, "getFuelAmountMb"), 0);
        int capacity = (int) number(call(be, "getFuelCapacityMb"), 0);
        return new int[]{amount, capacity};
    }

    static int[] thrusterBurn(@Nullable BlockEntity be) {
        int left = (int) number(call(be, "getDisplayBurnTimeRemaining"), 0);
        int total = (int) number(call(be, "getTotalBurnTicks"), 0);
        return new int[]{left, total};
    }

    static int thrusterEmptyBlocks(@Nullable BlockEntity be) {
        return (int) number(call(be, "getEmptyBlocks"), 10);
    }

    @Nullable
    static float[] thrusterNozzleTarget(@Nullable BlockEntity be) {
        if (!is(THRUSTER_VECTOR, be)) return null;
        double x = number(call(be, "getTargetVectorX"));
        double y = number(call(be, "getTargetVectorY"));
        return Double.isFinite(x) && Double.isFinite(y) ? new float[]{(float) x, (float) y} : null;
    }

    @Nullable
    static float[] thrusterNozzleNow(@Nullable BlockEntity be) {
        if (!is(THRUSTER_VECTOR, be)) return null;
        double x = number(call(be, "getCurrentVectorX"));
        double y = number(call(be, "getCurrentVectorY"));
        return Double.isFinite(x) && Double.isFinite(y) ? new float[]{(float) x, (float) y} : null;
    }

    static boolean thrusterSetNozzle(@Nullable BlockEntity be, float x, float y) {
        if (!is(THRUSTER_VECTOR, be)) return false;
        Method m = thrusterSetVector;
        if (m == null) {
            try {
                m = cls(THRUSTER_VECTOR).getMethod("setVectorCoordinates", float.class, float.class);
                m.trySetAccessible();
                thrusterSetVector = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("thrusterSetNozzle", e);
                return false;
            }
        }
        try {
            m.invoke(be, x, y);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("thrusterSetNozzleInvoke", e);
            return false;
        }
    }

    @Nullable
    private static Method declared(String className, String name, String warnKey) {
        Class<?> c = cls(className);
        if (c == null) return null;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            } catch (RuntimeException e) {
                warnOnce(warnKey, e);
                return null;
            }
        }
        warnOnce(warnKey, new NoSuchMethodException(className + "." + name));
        return null;
    }

    @Nullable
    private static Object invoke(Method m, @Nullable Object target) {
        if (target == null) return null;
        try {
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static double number(@Nullable Object v) {
        return v instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private static double number(@Nullable Object v, double fallback) {
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    @Nullable
    private static Field field(String className, String name, String warnKey) {
        Class<?> c = cls(className);
        if (c == null) return null;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            } catch (RuntimeException e) {
                warnOnce(warnKey, e);
                return null;
            }
        }
        warnOnce(warnKey, new NoSuchFieldException(className + "." + name));
        return null;
    }

    @Nullable
    private static Object call(@Nullable Object target, String name) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        Method m = METHODS.computeIfAbsent(c.getName() + "#" + name, k -> {
            try {
                Method found = c.getMethod(name);

                found.trySetAccessible();
                return Optional.of(found);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
        if (m == null) return null;
        try {
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void warnOnce(String key, Throwable e) {
        if (WARNED.add(key)) {
            CreateStats.LOGGER.warn("[VCC Mk IV] {} unavailable: {}", key, e.toString());
        }
    }
}
