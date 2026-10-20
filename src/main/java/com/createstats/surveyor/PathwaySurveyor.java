package com.createstats.surveyor;

import com.createstats.surveyor.core.Ground;
import com.createstats.surveyor.core.PathHealth;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.PathSurvey;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.TightBends;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class PathwaySurveyor {
    static final int BUDGET = 384;

    static final int REFRESH_TICKS = 1200;

    static final int UP = 10;
    static final int DOWN = 7;

    static final int URGENT_MAX = 8;

    static final int LOADED_MAX = 256;

    static final int HEALTH_TICKS = 20;
    static final int HEALTH_FILLING_TICKS = 60;

    static final double HEALTH_HEIGHT = 2.0;

    public static final int MOVE_REACH = 12;

    static final class Entry {
        final Pathway pathway;
        final PathSurvey survey;
        int cursor;
        long finishedAt = Long.MIN_VALUE;
        final ArrayDeque<int[]> urgent = new ArrayDeque<>();
        final ArrayDeque<int[]> loaded = new ArrayDeque<>();

        private Map<Long, List<int[]>> chunks;

        long changes;

        byte[] healthBytes;
        long healthAt = Long.MIN_VALUE;
        long healthChanges = -1;
        int healthSurveyed = -1;
        double healthOwn = -1.0;
        boolean healthAmphibious;
        double bendsOwn = -1.0;
        List<TightBends.Bend> bends = List.of();

        Entry(Pathway p) {
            this.pathway = p;
            this.survey = new PathSurvey(p.version(), PathSpline.of(p));
        }

        Map<Long, List<int[]>> chunks() {
            if (chunks == null) {
                chunks = new HashMap<>();
                PathSpline sp = survey.spline();
                long last = Long.MIN_VALUE;
                int[] run = null;
                for (int i = 0; i < sp.sampleCount(); i++) {
                    long key = ChunkPos.asLong((int) Math.floor(sp.sampleX(i)) >> 4, (int) Math.floor(sp.sampleZ(i)) >> 4);
                    if (key != last || run == null) {
                        run = new int[]{i, 0};
                        chunks.computeIfAbsent(key, k -> new ArrayList<>()).add(run);
                        last = key;
                    }
                    run[1]++;
                }
            }
            return chunks;
        }
    }

    private static final Map<ServerLevel, Map<UUID, Entry>> LEVELS = new WeakHashMap<>();

    private PathwaySurveyor() {
    }

    public static PathSurvey survey(ServerLevel level, Pathway p) {
        return entry(level, p).survey;
    }

    public static long changes(ServerLevel level, Pathway p) {
        return entry(level, p).changes;
    }

    static Entry entry(ServerLevel level, Pathway p) {
        Map<UUID, Entry> m = LEVELS.computeIfAbsent(level, k -> new HashMap<>());
        Entry e = m.get(p.id());
        if (e == null || e.pathway.version() != p.version()) {
            e = new Entry(p);
            m.put(p.id(), e);
        }
        return e;
    }

    public static void ahead(ServerLevel level, Pathway p, double s, double reach) {
        Entry e = entry(level, p);
        PathSpline sp = e.survey.spline();
        if (sp.sampleCount() == 0) return;
        int from = e.survey.index(s);
        int count = (int) Math.ceil(Math.max(0.0, reach) / sp.step()) + 1;
        while (e.urgent.size() >= URGENT_MAX) e.urgent.pollFirst();
        e.urgent.addLast(new int[]{from, Math.min(count, sp.sampleCount())});
    }

    public static byte[] healthBytes(ServerLevel level, Pathway p) {
        return entry(level, p).healthBytes;
    }

    public static Object[] leastAgile(ServerLevel level, Pathway p) {
        double own = 0.0;
        String name = "";
        for (RouteTrack t : PathwayTraffic.on(level, p.id())) {
            if (t.minRadius() > own) {
                own = t.minRadius();
                name = t.name();
            }
        }
        return new Object[]{own, name};
    }

    private static void refreshHealth(ServerLevel level, Map<UUID, Entry> m, long now) {
        for (Entry e : m.values()) {
            Object[] agile = leastAgile(level, e.pathway);
            double own = (double) agile[0];
            boolean amphibious = e.pathway.amphibious();
            int surveyed = e.survey.surveyedCount();
            boolean same = e.healthChanges == e.changes && e.healthSurveyed == surveyed && Math.abs(e.healthOwn - own) < 0.05
                    && e.healthAmphibious == amphibious;
            if (same) continue;

            boolean through = e.survey.complete() && e.healthSurveyed < e.survey.size();
            if (!through && e.healthBytes != null && now - e.healthAt < HEALTH_FILLING_TICKS && e.healthOwn == own
                    && e.healthAmphibious == amphibious) {
                continue;
            }
            if (Math.abs(e.bendsOwn - own) > 0.05) {
                e.bends = own > 0.0 ? TightBends.of(e.pathway, e.survey.spline(), own).bends() : List.of();
                e.bendsOwn = own;
            }
            List<PathHealth.Item> items = PathHealth.of(e.pathway, e.survey, amphibious, HEALTH_HEIGHT, e.bends, own, (String) agile[1]);
            byte[] bytes = PathHealth.encode(items);
            e.healthAt = now;
            e.healthChanges = e.changes;
            e.healthSurveyed = surveyed;
            e.healthOwn = own;
            e.healthAmphibious = amphibious;
            if (!Arrays.equals(bytes, e.healthBytes)) {
                e.healthBytes = bytes;
                PathwaySync.health(level, e.pathway, bytes);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Map<UUID, Entry> m = LEVELS.get(level);
        PathwayStore store = PathwayStore.get(level);
        if (m == null) {
            if (store.all().isEmpty()) return;
            m = LEVELS.computeIfAbsent(level, k -> new HashMap<>());
        }
        long now = level.getGameTime();

        for (Pathway p : store.all()) entry(level, p);
        m.keySet().removeIf(id -> store.get(id) == null);
        if (now % HEALTH_TICKS == 0) refreshHealth(level, m, now);
        int budget = BUDGET;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Entry e : m.values()) {
            while (budget > 0 && !e.urgent.isEmpty()) {
                int[] r = e.urgent.peekFirst();
                int n = e.survey.size();
                int take = Math.min(budget, r[1]);
                for (int k = 0; k < take; k++) {
                    int i = e.survey.spline().closed() ? Math.floorMod(r[0] + k, n) : r[0] + k;
                    if (i >= 0 && i < n) sample(level, e, i, now, pos);
                }
                budget -= take;
                if (take >= r[1]) e.urgent.pollFirst();
                else {
                    r[0] += take;
                    r[1] -= take;
                }
            }
            if (budget <= 0) return;
        }

        for (Entry e : m.values()) {
            while (budget > 0 && !e.loaded.isEmpty()) {
                int[] r = e.loaded.pollFirst();
                int n = e.survey.size();
                for (int k = 0; k < r[1]; k++) {
                    int i = r[0] + k;
                    if (i >= 0 && i < n) sample(level, e, i, now, pos);
                }
                budget -= r[1];
            }
            if (budget <= 0) return;
        }

        List<Entry> order = new ArrayList<>(m.values());
        for (Entry e : order) {
            if (budget <= 0) return;
            boolean due = e.finishedAt == Long.MIN_VALUE || now - e.finishedAt >= REFRESH_TICKS;
            if (!due) continue;
            int n = e.survey.size();
            while (budget > 0 && e.cursor < n) {
                sample(level, e, e.cursor++, now, pos);
                budget--;
            }
            if (e.cursor >= n) {
                e.cursor = 0;
                e.finishedAt = now;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEVELS.clear();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long key = event.getChunk().getPos().toLong();

        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> loaded(level, key));
            return;
        }
        loaded(level, key);
    }

    private static void loaded(ServerLevel level, long key) {
        Map<UUID, Entry> m = LEVELS.get(level);
        if (m == null || m.isEmpty()) return;
        for (Entry e : m.values()) {
            List<int[]> runs = e.chunks().get(key);
            if (runs == null) continue;
            for (int[] r : runs) {
                boolean unknown = false;
                for (int k = 0; k < r[1] && !unknown; k++) unknown = e.survey.stamp(r[0] + k) == Long.MIN_VALUE;
                if (!unknown) continue;
                while (e.loaded.size() >= LOADED_MAX) e.loaded.pollFirst();
                e.loaded.addLast(new int[]{r[0], r[1]});
            }
        }
    }

    static void sample(ServerLevel level, Entry e, int i, long now, BlockPos.MutableBlockPos pos) {
        PathSurvey sv = e.survey;
        PathSpline sp = sv.spline();
        double x = sp.sampleX(i);
        double y = sp.sampleY(i);
        double z = sp.sampleZ(i);
        pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
        if (!level.isLoaded(pos)) return;
        Ground before = sv.ground(i);
        double tx = sp.tangentX(i);
        double tz = sp.tangentZ(i);
        double rx = -tz;
        double rz = tx;
        double g = surface(level, x, y, z, pos);
        Ground cls;
        String what = null;
        int headroom = -1;
        if (Double.isNaN(g)) {
            cls = Ground.DROP;
        } else {
            int gx = (int) Math.floor(x);
            int gz = (int) Math.floor(z);
            FluidState fluid = level.getFluidState(pos.set(gx, (int) Math.floor(g - 0.2), gz));
            FluidState above = level.getFluidState(pos.set(gx, (int) Math.floor(g + 0.2), gz));
            if (fluid.is(FluidTags.LAVA) || above.is(FluidTags.LAVA)) {
                cls = Ground.LAVA;
            } else if (fluid.is(FluidTags.WATER) || above.is(FluidTags.WATER)) {
                cls = Ground.WATER;
            } else {
                BlockState under = level.getBlockState(pos.set(gx, (int) Math.floor(g - 0.01), gz));
                cls = classify(under);
                if (cls == Ground.HURT) what = name(under);
            }
            headroom = headroom(level, gx, (int) Math.floor(g + 0.01), gz, pos);
            if (!cls.wall) {
                String blocked = corridor(level, x, z, rx, rz, g, pos);
                if (blocked != null) {
                    cls = Ground.BLOCKED;
                    what = blocked;
                }
            }
        }
        double l = surface(level, x - rx * PathSurvey.TRACK, Double.isNaN(g) ? y : g, z - rz * PathSurvey.TRACK, pos);
        double r = surface(level, x + rx * PathSurvey.TRACK, Double.isNaN(g) ? y : g, z + rz * PathSurvey.TRACK, pos);
        sv.set(i, g, l, r, cls, headroom, what, now);
        if (before != cls) e.changes++;
    }

    static double surface(ServerLevel level, double x, double yHint, double z, BlockPos.MutableBlockPos pos) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int start = (int) Math.floor(yHint) + 1;
        pos.set(bx, start, bz);
        if (!level.isLoaded(pos)) return Double.NaN;
        if (solid(level, pos)) {
            for (int y = start + 1; y <= start + UP; y++) {
                pos.set(bx, y, bz);
                if (!solid(level, pos)) return top(level, bx, y - 1, bz, pos);
            }
            return Double.NaN;
        }
        for (int y = start; y >= start - 1 - DOWN; y--) {
            pos.set(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return y + shape.max(Direction.Axis.Y);
            if (!state.getFluidState().isEmpty()) return y + 0.9;
        }
        return Double.NaN;
    }

    private static boolean solid(ServerLevel level, BlockPos.MutableBlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && !s.getCollisionShape(level, pos).isEmpty();
    }

    private static double top(ServerLevel level, int bx, int y, int bz, BlockPos.MutableBlockPos pos) {
        pos.set(bx, y, bz);
        BlockState s = level.getBlockState(pos);
        VoxelShape shape = s.getCollisionShape(level, pos);
        return shape.isEmpty() ? y : y + shape.max(Direction.Axis.Y);
    }

    private static int headroom(ServerLevel level, int bx, int groundBlock, int bz, BlockPos.MutableBlockPos pos) {
        for (int h = 1; h <= PathSurvey.HEADROOM_MAX; h++) {
            pos.set(bx, groundBlock + h, bz);
            if (solid(level, pos)) return h - 1;
        }
        return PathSurvey.HEADROOM_MAX;
    }

    private static String corridor(ServerLevel level, double x, double z, double rx, double rz, double ground,
                                   BlockPos.MutableBlockPos pos) {
        int base = (int) Math.floor(ground + 0.01);
        for (int w = -1; w <= 1; w++) {
            int bx = (int) Math.floor(x + rx * w);
            int bz = (int) Math.floor(z + rz * w);
            for (int h = 1; h <= 2; h++) {
                pos.set(bx, base + h, bz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (!state.getCollisionShape(level, pos).isEmpty()) return name(state) + " at " + bx + " " + (base + h) + " " + bz;
            }
        }
        return null;
    }

    static Ground classify(BlockState s) {
        if (s.is(BlockTags.ICE)) return Ground.SLIPPERY;
        if (s.is(Blocks.MUD)) return Ground.SOFT;
        if (s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.HONEY_BLOCK)) return Ground.FRICTIVE;
        if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SWEET_BERRY_BUSH)) {
            return Ground.HURT;
        }
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.NYLIUM)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.CLAY)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.BASE_STONE_NETHER)) {
            return s.is(Blocks.DIRT_PATH) ? Ground.ROAD : Ground.NATURAL;
        }
        return Ground.ROAD;
    }

    private static String name(BlockState s) {
        return s.getBlock().getName().getString().toLowerCase(Locale.ROOT);
    }

    static double drivable(ServerLevel level, double x, double yHint, double z, boolean amphibious, BlockPos.MutableBlockPos pos) {
        double g = surface(level, x, yHint, z, pos);
        if (Double.isNaN(g)) return Double.NaN;
        int gx = (int) Math.floor(x);
        int gz = (int) Math.floor(z);
        FluidState fluid = level.getFluidState(pos.set(gx, (int) Math.floor(g - 0.2), gz));
        FluidState above = level.getFluidState(pos.set(gx, (int) Math.floor(g + 0.2), gz));
        if (fluid.is(FluidTags.LAVA) || above.is(FluidTags.LAVA)) return Double.NaN;
        if (!amphibious && (fluid.is(FluidTags.WATER) || above.is(FluidTags.WATER))) return Double.NaN;
        Ground cls = classify(level.getBlockState(pos.set(gx, (int) Math.floor(g - 0.01), gz)));
        if (cls.wall) return Double.NaN;
        if (headroom(level, gx, (int) Math.floor(g + 0.01), gz, pos) < 2) return Double.NaN;
        return g;
    }

    public static java.util.function.BiPredicate<Double, Double> groundOk(ServerLevel level, Pathway p) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, z) -> {
            double hint = nearestY(p, x, z);
            return !Double.isNaN(drivable(level, x, hint, z, p.amphibious(), pos));
        };
    }

    private static double nearestY(Pathway p, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        double y = 64.0;
        for (PathNode n : p.nodes()) {
            double d = (n.x() - x) * (n.x() - x) + (n.z() - z) * (n.z() - z);
            if (d < best) {
                best = d;
                y = n.y();
            }
        }
        return y;
    }

    public static double[] dryPlace(ServerLevel level, Pathway p, int nodeId) {
        int k = p.indexOf(nodeId);
        if (k < 0) return null;
        PathNode n = p.nodes().get(k);
        int count = p.nodes().size();
        PathNode prev = k > 0 ? p.nodes().get(k - 1) : (p.closed() && count > 2 ? p.nodes().get(count - 1) : null);
        PathNode next = k + 1 < count ? p.nodes().get(k + 1) : (p.closed() && count > 2 ? p.nodes().get(0) : null);
        boolean amphibious = p.amphibious();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int r = 1; r <= MOVE_REACH; r++) {
            int around = 8 * r;
            for (int j = 0; j < around; j++) {
                double ang = 2.0 * Math.PI * j / around;
                double x = Math.floor(n.x() + r * Math.cos(ang)) + 0.5;
                double z = Math.floor(n.z() + r * Math.sin(ang)) + 0.5;
                double g = drivable(level, x, n.y(), z, amphibious, pos);
                if (Double.isNaN(g) || !level(level, x, g, z, amphibious, pos)) continue;
                if (prev != null && !clear(level, prev.x(), prev.y(), prev.z(), x, g, z, amphibious, pos)) continue;
                if (next != null && !clear(level, x, g, z, next.x(), next.y(), next.z(), amphibious, pos)) continue;
                return new double[]{x, g, z};
            }
        }
        return null;
    }

    private static boolean level(ServerLevel level, double x, double g, double z, boolean amphibious, BlockPos.MutableBlockPos pos) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            double h = drivable(level, x + d[0], g, z + d[1], amphibious, pos);
            if (Double.isNaN(h) || Math.abs(h - g) > 1.0) return false;
        }
        return true;
    }

    private static boolean clear(ServerLevel level, double x0, double y0, double z0, double x1, double y1, double z1,
                                 boolean amphibious, BlockPos.MutableBlockPos pos) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int steps = Math.max(1, (int) Math.ceil(len / 0.5));
        double last = Double.NaN;
        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps;
            double g = drivable(level, x0 + (x1 - x0) * f, Double.isNaN(last) ? y0 + (y1 - y0) * f : last, z0 + (z1 - z0) * f, amphibious, pos);
            if (Double.isNaN(g)) return false;
            if (!Double.isNaN(last) && Math.abs(g - last) > 1.0) return false;
            last = g;
        }
        return true;
    }
}
