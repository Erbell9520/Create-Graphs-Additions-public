package com.createstats.vcu;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VcuActuators {
    static final String SWIVEL = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity";
    static final String WHEEL_MOUNT = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity";
    static final String BURNER = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity";
    static final String VENT = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity";
    private static final String PIPELINE = "dev.ryanhcode.sable.api.physics.PhysicsPipeline";
    private static final String SERVER_SUB_LEVEL = "dev.ryanhcode.sable.sublevel.ServerSubLevel";

    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static volatile Field swivelTarget;
    private static volatile Method swivelSubLevelId;
    private static volatile Field wheelLifted;
    private static volatile Method burnerSet;
    private static volatile Method ventSet;
    private static volatile Method neighbours;
    private static volatile Field governorLastInput;

    private VcuActuators() {
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

    static boolean setSwivelAngle(ServerLevel level, BlockEntity swivel, double degrees) {
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

    static void setBurnerSignal(BlockEntity be, int signal) {
        Method m = burnerSet;
        if (m == null) {
            Class<?> c = cls(BURNER);
            if (c == null) return;
            try {
                m = c.getMethod("setSignalStrength", int.class);
                burnerSet = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("burnerSet", e);
                return;
            }
        }
        try {
            m.invoke(be, signal);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("burnerInvoke", e);
        }
    }

    static void setVentSignal(BlockEntity be, int signal) {
        Method m = ventSet;
        if (m == null) {
            Class<?> c = cls(VENT);
            if (c == null) return;
            try {
                m = c.getMethod("updateSignal", int.class);
                ventSet = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("ventSet", e);
                return;
            }
        }
        try {
            m.invoke(be, signal);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("ventInvoke", e);
        }
    }

    static boolean wheelLifted(BlockEntity be) {
        Field f = wheelLifted;
        if (f == null) {
            Class<?> c = cls(WHEEL_MOUNT);
            if (c == null) return false;
            try {
                f = c.getDeclaredField("liftedUp");
                f.setAccessible(true);
                wheelLifted = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("wheelLifted", e);
                return false;
            }
        }
        try {
            return f.getBoolean(be);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static void wake(ServerLevel level, @Nullable Object subLevel) {
        com.createstats.flight.bridge.SableWake.wake(level, subLevel);
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

    private static void warnOnce(String key, Throwable e) {
        if (WARNED.add(key)) {
            CreateStats.LOGGER.warn("[VCU] {} unavailable: {}", key, e.toString());
        }
    }
}
