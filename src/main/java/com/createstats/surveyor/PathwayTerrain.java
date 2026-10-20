package com.createstats.surveyor;

import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.network.PathwayTerrainPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class PathwayTerrain {
    static final int MARGIN = 24;

    static final int MAX_CELLS = 192;

    static final int BUDGET = 768;
    static final int REFRESH_TICKS = 12000;

    public record Tile(int x0, int z0, int cell, int w, int h, byte[] colours) {
    }

    private static final class Job {
        final long bounds;
        final int x0;
        final int z0;
        final int cell;
        final int w;
        final int h;
        final byte[] colours;
        int cursor;
        long doneAt = Long.MIN_VALUE / 2;
        Tile sent;

        Job(long bounds, int x0, int z0, int cell, int w, int h) {
            this.bounds = bounds;
            this.x0 = x0;
            this.z0 = z0;
            this.cell = cell;
            this.w = w;
            this.h = h;
            this.colours = new byte[w * h];
        }
    }

    private static final Map<ServerLevel, Map<UUID, Job>> JOBS = new WeakHashMap<>();

    private PathwayTerrain() {
    }

    public static Tile tile(ServerLevel level, UUID pathway) {
        Map<UUID, Job> m = JOBS.get(level);
        Job j = m == null ? null : m.get(pathway);
        return j == null ? null : j.sent;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 5 != 0) return;
        PathwayStore store = PathwayStore.get(level);
        Map<UUID, Job> m = JOBS.computeIfAbsent(level, k -> new HashMap<>());
        m.keySet().removeIf(id -> store.get(id) == null);
        long now = level.getGameTime();
        int budget = BUDGET * 5;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Pathway p : store.all()) {
            if (p.nodes().isEmpty()) continue;
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (PathNode n : p.nodes()) {
                minX = Math.min(minX, n.x());
                minZ = Math.min(minZ, n.z());
                maxX = Math.max(maxX, n.x());
                maxZ = Math.max(maxZ, n.z());
            }
            int x0 = (int) Math.floor(minX) - MARGIN;
            int z0 = (int) Math.floor(minZ) - MARGIN;
            int spanX = (int) Math.ceil(maxX) + MARGIN - x0;
            int spanZ = (int) Math.ceil(maxZ) + MARGIN - z0;
            int cell = Math.max(1, (int) Math.ceil(Math.max(spanX, spanZ) / (double) MAX_CELLS));
            x0 = Math.floorDiv(x0, cell) * cell;
            z0 = Math.floorDiv(z0, cell) * cell;
            int w = Math.max(1, (int) Math.ceil(spanX / (double) cell) + 1);
            int h = Math.max(1, (int) Math.ceil(spanZ / (double) cell) + 1);
            long bounds = ((long) x0 * 31 + z0) * 31 * 31 + (long) w * 131 + h * 7L + cell;
            Job j = m.get(p.id());
            if (j == null || j.bounds != bounds) {
                j = new Job(bounds, x0, z0, cell, w, h);
                m.put(p.id(), j);
            }
            int n = j.w * j.h;
            if (j.cursor >= n) {
                if (now - j.doneAt < REFRESH_TICKS) continue;
                j.cursor = 0;
            }
            while (budget > 0 && j.cursor < n) {
                int i = j.cursor % j.w;
                int k = j.cursor / j.w;
                j.colours[j.cursor] = read(level, j.x0 + i * j.cell + j.cell / 2, j.z0 + k * j.cell + j.cell / 2, j.cell, pos);
                j.cursor++;
                budget--;
            }
            if (j.cursor >= n) {
                j.doneAt = now;
                Tile t = new Tile(j.x0, j.z0, j.cell, j.w, j.h, j.colours.clone());
                boolean changed = j.sent == null || !java.util.Arrays.equals(j.sent.colours(), t.colours());
                j.sent = t;
                if (changed) PathwaySync.terrain(level, p.id(), t);
            }
            if (budget <= 0) return;
        }
    }

    static byte read(ServerLevel level, int x, int z, int cell, BlockPos.MutableBlockPos pos) {
        if (!level.hasChunkAt(pos.set(x, 0, z))) return 0;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        pos.set(x, y, z);
        BlockState state = level.getBlockState(pos);
        MapColor colour = state.getMapColor(level, pos);
        if (colour == MapColor.NONE) return 0;
        MapColor.Brightness b;
        if (!state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) {
            int depth = 0;
            BlockPos.MutableBlockPos down = new BlockPos.MutableBlockPos(x, y, z);
            while (depth < 12 && level.getFluidState(down).is(FluidTags.WATER)) {
                depth++;
                down.move(0, -1, 0);
            }
            double d = depth * 0.1 + ((x + z) & 1) * 0.2;
            b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            colour = MapColor.WATER;
        } else {
            int north = level.hasChunkAt(pos.set(x, 0, z - cell)) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - cell) - 1 : y;
            b = y > north ? MapColor.Brightness.HIGH : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        return colour.getPackedId(b);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
    }

    static PathwayTerrainPacket packet(UUID pathway, Tile t) {
        return new PathwayTerrainPacket(pathway, t.x0(), t.z0(), t.cell(), t.w(), t.h(), t.colours());
    }
}
