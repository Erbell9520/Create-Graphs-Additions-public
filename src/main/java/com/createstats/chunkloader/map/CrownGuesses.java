package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownRegion;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// ask the generator for biomes without generating anything, off thread. the seed never leaves the server
@EventBusSubscriber(modid = "create_stats")
public final class CrownGuesses {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/crown_map");

    static final int CACHE = 256;
    private static final int SIDE = CrownRegion.CHUNKS;

    private static final Map<String, byte[]> READY = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE;
        }
    };
    private static final Map<String, List<Consumer<byte[]>>> RUNNING = new HashMap<>();
    private static ExecutorService worker;
    private static boolean warned;

    private CrownGuesses() {
    }

    private record Job(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, int seaLevel, int rx,
                       int rz) {
    }

    public static void whenReady(ServerLevel level, int rx, int rz, Consumer<byte[]> ready) {
        final String key = level.dimension().location() + "|" + rx + "|" + rz;
        final byte[] have;
        synchronized (READY) {
            have = READY.get(key);
        }
        if (have != null) {
            ready.accept(have);
            return;
        }
        if (level.dimensionType().hasCeiling()) {
            ready.accept(new byte[SIDE * SIDE]);
            return;
        }
        final List<Consumer<byte[]>> waiting = RUNNING.get(key);
        if (waiting != null) {
            waiting.add(ready);
            return;
        }
        final List<Consumer<byte[]>> list = new ArrayList<>();
        list.add(ready);
        RUNNING.put(key, list);
        final MinecraftServer server = level.getServer();
        final ChunkGenerator generator = level.getChunkSource().getGenerator();
        final Job job = new Job(generator, level.getChunkSource().randomState(),
                LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()), generator.getSeaLevel(), rx, rz);
        worker().execute(() -> {
            byte[] out;
            try {
                out = compute(job);
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    LOG.warn("Spark Crown map: the world's generator would not guess region {}, {}", rx, rz, t);
                }
                out = new byte[SIDE * SIDE];
            }
            final byte[] done = out;
            server.execute(() -> {
                synchronized (READY) {
                    READY.put(key, done);
                }
                final List<Consumer<byte[]>> all = RUNNING.remove(key);
                if (all != null) {
                    all.forEach(c -> c.accept(done));
                }
            });
        });
    }

    private static synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r, "create_stats crown map guesses");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return worker;
    }

    static byte[] compute(Job job) {
        final byte[] out = new byte[SIDE * SIDE];
        final int[] surface = new int[(SIDE + 1) * SIDE];
        final DensityFunction depth = job.generator() instanceof NoiseBasedChunkGenerator ? job.random().router().depth()
                : null;
        for (int j = -1; j < SIDE; j++) {
            for (int i = 0; i < SIDE; i++) {
                surface[(j + 1) * SIDE + i] = surface(job, depth, centre(job.rx(), i), centre(job.rz(), j));
            }
        }
        final BiomeSource biomes = job.generator().getBiomeSource();
        final int top = job.height().getMaxBuildHeight() - 1;
        for (int j = 0; j < SIDE; j++) {
            for (int i = 0; i < SIDE; i++) {
                final int x = centre(job.rx(), i);
                final int z = centre(job.rz(), j);
                final int y = surface[(j + 1) * SIDE + i];
                final int at = Math.max(job.height().getMinBuildHeight(), Math.min(top, Math.max(y, job.seaLevel())));
                final Holder<Biome> b = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(at),
                        QuartPos.fromBlock(z), job.random().sampler());
                final CrownGuess.Kind kind = CrownGuess.classify(b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER),
                        b.is(Tags.Biomes.IS_SANDY) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_BADLANDS),
                        b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_ICY), y, job.seaLevel());
                out[j * SIDE + i] = CrownGuess.encode(kind, CrownGuess.shade(kind, y, surface[j * SIDE + i],
                        job.seaLevel()));
            }
        }
        return out;
    }

    private static int centre(int region, int chunk) {
        return (region * SIDE + chunk) * 16 + 8;
    }

    private static int surface(Job job, DensityFunction depth, int x, int z) {
        if (depth != null) {
            final int s = CrownGuess.surface(depth.compute(new DensityFunction.SinglePointContext(x, 0, z)),
                    depth.compute(new DensityFunction.SinglePointContext(x, 128, z)), 0, 128);
            if (s != Integer.MIN_VALUE) {
                return s;
            }
        }
        return job.generator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, job.height(), job.random());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (READY) {
            READY.clear();
        }
        RUNNING.clear();
        synchronized (CrownGuesses.class) {
            if (worker != null) {
                worker.shutdownNow();
                worker = null;
            }
        }
    }
}
