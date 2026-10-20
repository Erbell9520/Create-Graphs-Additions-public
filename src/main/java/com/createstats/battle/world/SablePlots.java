package com.createstats.battle.world;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SablePlots {
    static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    static final String HEAT_MANAGER = "dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager";
    static final String SPLIT_LISTENER = HEAT_MANAGER + "$SplitListener";

    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private static final AtomicBoolean SPLIT_REGISTERED = new AtomicBoolean(false);
    @Nullable
    private static volatile Optional<Class<?>> containerClass;

    private SablePlots() {
    }

    public static boolean present() {
        return containerClass() != null;
    }

    @Nullable
    public static Object subLevelAt(Level level, BlockPos pos) {
        Object container = container(level);
        if (container == null || !Boolean.TRUE.equals(call(container, "inBounds", pos))) return null;
        return call(call(container, "getPlot", new ChunkPos(pos)), "getSubLevel");
    }

    @Nullable
    public static UUID shipAt(Level level, BlockPos pos) {
        Object s = subLevelAt(level, pos);
        return s == null ? null : SableBridge.subLevelId(s);
    }

    public static List<LevelChunk> plotChunks(@Nullable Object subLevel) {
        List<LevelChunk> out = new ArrayList<>();
        Object plot = call(subLevel, "getPlot");
        if (!(call(plot, "getLoadedChunks") instanceof Collection<?> holders)) return out;
        for (Object h : holders) {
            if (call(h, "getChunk") instanceof LevelChunk c) out.add(c);
        }
        return out;
    }

    @Nullable
    public static AABB worldBounds(@Nullable Object subLevel) {
        Object box = call(subLevel, "boundingBox");
        if (box == null) return null;
        double[] v = new double[6];
        String[] names = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
        for (int i = 0; i < 6; i++) {
            if (!(call(box, names[i]) instanceof Number n)) return null;
            v[i] = n.doubleValue();
        }
        return new AABB(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    @Nullable
    public static Object shipContaining(Level level, Vec3 point, double grow) {
        Object best = null;
        double bestVolume = Double.MAX_VALUE;
        for (Object s : SableBridge.listSubLevels(level)) {
            AABB b = worldBounds(s);
            if (b == null || !b.inflate(grow).contains(point)) continue;
            double volume = b.getXsize() * b.getYsize() * b.getZsize();
            if (volume < bestVolume) {
                bestVolume = volume;
                best = s;
            }
        }
        return best;
    }

    public static void registerSplitListener(Consumer<Object[]> handler) {
        if (!SPLIT_REGISTERED.compareAndSet(false, true)) return;
        try {
            Class<?> listenerType = Class.forName(SPLIT_LISTENER);
            Object proxy = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                    (self, method, args) -> switch (method.getName()) {
                        case "addBlocks" -> {
                            try {
                                handler.accept(args == null ? new Object[0] : args);
                            } catch (RuntimeException e) {
                                CreateStats.LOGGER.warn("[Battle] split handler failed: {}", e.toString());
                            }
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> args != null && args.length == 1 && self == args[0];
                        case "toString" -> "create_stats battle split listener";
                        default -> null;
                    });
            Class.forName(HEAT_MANAGER).getMethod("addSplitListener", listenerType).invoke(null, proxy);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CreateStats.LOGGER.info("[Battle] no Sable split listener ({}); broken-away sections use the 64-block rule", e.toString());
        }
    }

    @Nullable
    static Object container(@Nullable Level level) {
        Class<?> owner = containerClass();
        return level == null || owner == null ? null : callStatic(owner, "getContainer", level);
    }

    @Nullable
    private static Class<?> containerClass() {
        Optional<Class<?>> c = containerClass;
        if (c == null) {
            try {
                c = Optional.of(Class.forName(CONTAINER));
            } catch (ClassNotFoundException | LinkageError e) {
                c = Optional.empty();
            }
            containerClass = c;
        }
        return c.orElse(null);
    }

    @Nullable
    static Object call(@Nullable Object target, String name, Object... args) {
        if (target == null) return null;
        Method m = find(target.getClass(), name, args);
        if (m == null) return null;
        try {
            return m.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Object callStatic(Class<?> owner, String name, Object... args) {
        Method m = find(owner, name, args);
        if (m == null) return null;
        try {
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Method find(Class<?> owner, String name, Object[] args) {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name).append('(');
        for (Object a : args) key.append(a == null ? "null" : a.getClass().getName()).append(',');
        return METHODS.computeIfAbsent(key.toString(), k -> Optional.ofNullable(search(owner, name, args))).orElse(null);
    }

    @Nullable
    private static Method search(Class<?> owner, String name, Object[] args) {
        try {
            for (Method m : owner.getMethods()) {
                if (m.getName().equals(name) && accepts(m, args)) return open(m);
            }
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && accepts(m, args)) return open(m);
                }
            }
        } catch (RuntimeException | LinkageError e) {
        }
        return null;
    }

    private static Method open(Method m) {
        try {
            m.setAccessible(true);
        } catch (RuntimeException ignored) {
        }
        return m;
    }

    private static boolean accepts(Method m, Object[] args) {
        Class<?>[] p = m.getParameterTypes();
        if (p.length != args.length) return false;
        for (int i = 0; i < p.length; i++) {
            Object a = args[i];
            if (a == null) {
                if (p[i].isPrimitive()) return false;
            } else if (p[i].isPrimitive()) {
                if (!boxOf(p[i]).isInstance(a)) return false;
            } else if (!p[i].isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxOf(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == boolean.class) return Boolean.class;
        return Object.class;
    }
}
