package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicSurvey;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.VeinBook;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

// unloaded columns are holes, never claim ground we didn't read
public final class SeismicArchiveHook {
    private SeismicArchiveHook() {
    }

    private static volatile boolean warned;

    public static void begin(ServerLevel level, BlockPos rig, String target, int reach, long at) {
        try {
            beginRecord(level, rig, target, reach, at);
        } catch (RuntimeException e) {
            warn("begin", rig, e);
        }
    }

    public static void step(ServerLevel level, BlockPos rig, SeismicSurvey survey, List<VeinBook.Vein> closed) {
        try {
            stepRecord(level, rig, survey, closed);
        } catch (RuntimeException e) {
            warn("step", rig, e);
        }
    }

    public static SurveyRecall recall(ServerLevel level, BlockPos rig, String target) {
        return SeismicArchiveData.get(level).archive().recall(rig.getX(), rig.getY(), rig.getZ(), target);
    }

    public record Stamp(Object archive, long revision) {
        public boolean same(Stamp o) {
            return o != null && o.archive == this.archive && o.revision == this.revision;
        }
    }

    public static Stamp stamp(ServerLevel level, BlockPos rig) {
        final SeismicArchive a = SeismicArchiveData.get(level).archive();
        return new Stamp(a, a.revisionOf(ArchiveBlow.key(rig.getX(), rig.getY(), rig.getZ())));
    }

    private static final ExecutorService READER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "create_stats seismic read-back");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    public static <T> void readBack(ServerLevel level, BlockPos rig, Function<List<SurveyRecall>, T> build,
                                    Consumer<T> done) {
        final List<ArchiveBlow> blows = SeismicArchiveData.get(level).archive()
                .newestOfEachTarget(rig.getX(), rig.getY(), rig.getZ());
        final MinecraftServer server = level.getServer();
        READER.execute(() -> {
            T out = null;
            try {
                final List<SurveyRecall> recalls = new ArrayList<>(blows.size());
                for (final ArchiveBlow b : blows) {
                    recalls.add(SurveyRecall.of(b));
                }
                out = build.apply(List.copyOf(recalls));
            } catch (RuntimeException e) {
                warn("read-back", rig, e);
            } finally {
                final T result = out;
                server.execute(() -> {
                    if (server.isSameThread()) {
                        done.accept(result);
                    }
                });
            }
        });
    }

    private static void warn(String what, BlockPos rig, RuntimeException e) {
        if (!warned) {
            warned = true;
            com.createstats.CreateStats.LOGGER.warn("[Seismic] the survey record's {} failed at {}; the rig goes on, "
                    + "this blow may be missing from Stats Displays: {}", what, rig, e.toString(), e);
        }
    }

    private static void beginRecord(ServerLevel level, BlockPos rig, String target, int reach, long at) {
        final SeismicArchiveData data = SeismicArchiveData.get(level);
        final SeismicArchive archive = data.archive();
        archive.closeStale(at);
        final long[] holes = ArchiveBlow.holes(rig.getX(), rig.getZ(), reach,
                (cx, cz) -> level.getChunkSource().getChunkNow(cx, cz) != null);
        final ArchiveBlow blow = archive.begin(rig.getX(), rig.getY(), rig.getZ(), target, reach, at,
                level.getMinBuildHeight(), holes[0], holes[1]);
        blow.recordSurface((x, z) -> {
            final LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            return chunk == null ? ArchiveBlow.NO_SURFACE
                    : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        });
        data.touched();
    }

    private static void stepRecord(ServerLevel level, BlockPos rig, SeismicSurvey survey, List<VeinBook.Vein> closed) {
        final SeismicArchiveData data = SeismicArchiveData.get(level);
        final ArchiveBlow blow = data.archive().open(rig.getX(), rig.getY(), rig.getZ());
        if (blow == null) {
            return;
        }
        final double front = Math.min(survey.radius(), SeismicWave.frontAt(survey.tick()));
        data.archive().step(blow, front, survey.full(), closed, survey.done());
        data.touched();
    }
}
