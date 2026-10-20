package com.createstats.seismic;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// getChunkNow, never loads. palette check skips whole sections
public final class SeismicSurvey implements ShellScan.Ground<LevelChunkSection> {
    public static final int MAX_BLOCKS = 32_768;

    private final ServerLevel level;
    private final SeismicTargets.Target target;
    @Nullable
    private final Heightmap.Types cover;
    private final ShellScan<LevelChunkSection> scan;
    private final Reference2IntOpenHashMap<BlockState> kinds = new Reference2IntOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
    private long peakNanos;

    public SeismicSurvey(ServerLevel level, BlockPos base, SeismicTargets.Target target, int radius) {
        this.level = level;
        this.target = target;
        this.cover = target.cover();
        this.kinds.defaultReturnValue(Integer.MIN_VALUE);
        this.scan = new ShellScan<>(this, base.getX(), base.getY(), base.getZ(), radius,
                new VeinBook(MAX_BLOCKS, target.diagonal()));
    }

    public SeismicTargets.Target target() {
        return target;
    }

    public int radius() {
        return scan.radius();
    }

    public int tick() {
        return scan.tick();
    }

    public boolean done() {
        return scan.done();
    }

    public boolean full() {
        return scan.full();
    }

    public long checked() {
        return scan.checked();
    }

    public long peakNanos() {
        return peakNanos;
    }

    public int veins() {
        return scan.veins();
    }

    public int blocks() {
        return scan.blocks();
    }

    public int nearest() {
        return scan.nearest();
    }

    public List<VeinBook.Vein> step() {
        final long start = System.nanoTime();
        final List<VeinBook.Vein> closed = scan.step();
        peakNanos = Math.max(peakNanos, System.nanoTime() - start);
        return closed;
    }

    @Override
    @Nullable
    public LevelChunkSection section(int sx, int sy, int sz) {
        final LevelChunk chunk = chunk(sx, sz);
        if (chunk == null) {
            return null;
        }
        final int index = level.getSectionIndexFromSectionY(sy);
        if (index < 0 || index >= chunk.getSections().length) {
            return null;
        }
        final LevelChunkSection section = chunk.getSection(index);
        return target.maybeIn(section) ? section : null;
    }

    @Override
    public int kindAt(LevelChunkSection s, int x, int y, int z) {
        final BlockState state = s.getBlockState(x & 15, y & 15, z & 15);
        int k = kinds.getInt(state);
        if (k == Integer.MIN_VALUE) {
            k = target.kindOf(state);
            kinds.put(state, k);
        }
        return k;
    }

    @Override
    public boolean covered(int x, int y, int z) {
        if (cover == null) {
            return true;
        }
        final LevelChunk chunk = chunk(x >> 4, z >> 4);
        return chunk != null && y < chunk.getHeight(cover, x & 15, z & 15);
    }

    @Nullable
    private LevelChunk chunk(int cx, int cz) {
        final long key = ChunkPos.asLong(cx, cz);
        LevelChunk c = chunks.get(key);
        if (c == null && !chunks.containsKey(key)) {
            c = level.getChunkSource().getChunkNow(cx, cz);
            chunks.put(key, c);
        }
        return c;
    }
}
