package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownRegion;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class CrownSamples extends SavedData {
    private static final String FILE = "create_stats_crown_samples";

    static final int BUDGET = 16;

    static final int QUEUE = 1 << 15;
    private static final int CHUNKS = CrownRegion.CHUNKS * CrownRegion.CHUNKS;
    private static final Map<ResourceKey<Level>, ArrayBlockingQueue<Long>> WAITING = new ConcurrentHashMap<>();

    private final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    private static final class Region {
        final long[] present = new long[CHUNKS / 64];
        final byte[] samples = new byte[CHUNKS * CrownSampleShade.PER_CHUNK];
    }

    public static CrownSamples get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CrownSamples::new, CrownSamples::load),
                FILE);
    }

    private static int slot(int cx, int cz) {
        return Math.floorMod(cz, CrownRegion.CHUNKS) * CrownRegion.CHUNKS + Math.floorMod(cx, CrownRegion.CHUNKS);
    }

    @Nullable
    public byte[] of(int cx, int cz) {
        final Region r = regions.get(CrownRegion.pack(CrownRegion.regionOf(cx), CrownRegion.regionOf(cz)));
        final int s = slot(cx, cz);
        if (r == null || (r.present[s >>> 6] & 1L << (s & 63)) == 0) {
            return null;
        }
        final int at = s * CrownSampleShade.PER_CHUNK;
        return Arrays.copyOfRange(r.samples, at, at + CrownSampleShade.PER_CHUNK);
    }

    void put(int cx, int cz, byte[] samples) {
        final Region r = regions.computeIfAbsent(CrownRegion.pack(CrownRegion.regionOf(cx), CrownRegion.regionOf(cz)),
                k -> new Region());
        final int s = slot(cx, cz);
        final int at = s * CrownSampleShade.PER_CHUNK;
        final boolean had = (r.present[s >>> 6] & 1L << (s & 63)) != 0;
        if (had && Arrays.equals(r.samples, at, at + CrownSampleShade.PER_CHUNK, samples, 0, samples.length)) {
            return;
        }
        System.arraycopy(samples, 0, r.samples, at, CrownSampleShade.PER_CHUNK);
        r.present[s >>> 6] |= 1L << (s & 63);
        setDirty();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            final ChunkPos p = event.getChunk().getPos();
            WAITING.computeIfAbsent(level.dimension(), k -> new ArrayBlockingQueue<>(QUEUE)).offer(p.toLong());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        final ArrayBlockingQueue<Long> q = WAITING.get(level.dimension());
        if (q == null || q.isEmpty()) {
            return;
        }
        CrownSamples store = null;
        for (int i = 0; i < BUDGET; i++) {
            final Long c = q.poll();
            if (c == null) {
                return;
            }
            final LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            if (chunk == null) {
                continue;
            }
            if (store == null) {
                store = get(level);
            }
            store.put(ChunkPos.getX(c), ChunkPos.getZ(c), sample(level, chunk));
        }
    }

    // held chunks never reload so rebuilds never reached the map. re-read on open
    public static void refresh(ServerLevel level, int x0, int z0, int x1, int z1) {
        final ArrayBlockingQueue<Long> q = WAITING.computeIfAbsent(level.dimension(), k -> new ArrayBlockingQueue<>(QUEUE));
        for (int cz = z0; cz < z1; cz++) {
            for (int cx = x0; cx < x1; cx++) {
                if (level.getChunkSource().getChunkNow(cx, cz) != null) {
                    q.offer(ChunkPos.asLong(cx, cz));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WAITING.clear();
    }

    static byte[] sample(ServerLevel level, LevelChunk chunk) {
        final byte[] out = new byte[CrownSampleShade.PER_CHUNK];
        final ChunkPos cp = chunk.getPos();
        final int minY = level.getMinBuildHeight();
        final LevelChunk north = level.getChunkSource().getChunkNow(cp.x, cp.z - 1);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = 0; j < CrownSampleShade.SIDE; j++) {
            for (int i = 0; i < CrownSampleShade.SIDE; i++) {
                final int lx = CrownSampleShade.readAt(i);
                final int lz = CrownSampleShade.readAt(j);
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                if (y < minY) {
                    continue;
                }
                pos.set(cp.getMinBlockX() + lx, y, cp.getMinBlockZ() + lz);
                BlockState state = chunk.getBlockState(pos);
                MapColor colour = state.getMapColor(level, pos);
                for (int k = 0; colour == MapColor.NONE && y > minY && k < 16; k++) {
                    pos.setY(--y);
                    state = chunk.getBlockState(pos);
                    colour = state.getMapColor(level, pos);
                }
                if (colour == MapColor.NONE) {
                    continue;
                }
                final int brightness;
                if (state.getFluidState().is(FluidTags.WATER)) {
                    int depth = 0;
                    final BlockPos.MutableBlockPos down = pos.mutable();
                    while (depth < CrownSampleShade.MAX_DEPTH && down.getY() >= minY
                            && chunk.getFluidState(down).is(FluidTags.WATER)) {
                        depth++;
                        down.move(0, -1, 0);
                    }
                    brightness = CrownSampleShade.water(depth, pos.getX(), pos.getZ());
                    colour = MapColor.WATER;
                } else {
                    final int nz = lz - CrownSampleShade.STEP;
                    final int northY = nz >= 0 ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, nz)
                            : north != null ? north.getHeight(Heightmap.Types.WORLD_SURFACE, lx, nz + 16) : y;
                    brightness = CrownSampleShade.land(y, northY);
                }
                out[j * CrownSampleShade.SIDE + i] = CrownSampleShade.pack(colour.id, brightness);
            }
        }
        return out;
    }

    private static CrownSamples load(CompoundTag tag, HolderLookup.Provider registries) {
        final CrownSamples out = new CrownSamples();
        final ListTag list = tag.getList("Regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag t = list.getCompound(i);
            final long[] present = t.getLongArray("Present");
            final byte[] samples = t.getByteArray("Samples");
            final Region r = new Region();
            if (present.length != r.present.length || samples.length != r.samples.length) {
                continue;
            }
            System.arraycopy(present, 0, r.present, 0, present.length);
            System.arraycopy(samples, 0, r.samples, 0, samples.length);
            out.regions.put(t.getLong("Region"), r);
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (Long2ObjectOpenHashMap.Entry<Region> e : regions.long2ObjectEntrySet()) {
            final CompoundTag t = new CompoundTag();
            t.putLong("Region", e.getLongKey());
            t.putLongArray("Present", e.getValue().present);
            t.putByteArray("Samples", e.getValue().samples);
            list.add(t);
        }
        tag.put("Regions", list);
        return tag;
    }
}
