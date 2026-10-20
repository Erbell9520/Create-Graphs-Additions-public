package com.createstats.seismic;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicScanCostTest {
    private static final Object STONE = "stone";
    private static final Object DEEPSLATE = "deepslate";
    private static final Object ANDESITE = "andesite";
    private static final Object GRAVEL = "gravel";
    private static final Object AIR = "air";
    private static final Object IRON = "iron_ore";
    private static final Object COAL = "coal_ore";
    private static final Object DIAMOND = "diamond_ore";

    private static final IdMapper<Object> IDS = new IdMapper<>();

    static {
        for (final Object s : new Object[] {AIR, STONE, DEEPSLATE, ANDESITE, GRAVEL, IRON, COAL, DIAMOND}) {
            IDS.add(s);
        }
    }

    private static Map<Long, PalettedContainer<Object>> world() {
        final Map<Long, PalettedContainer<Object>> out = new HashMap<>();
        for (int sx = -5; sx <= 4; sx++) {
            for (int sz = -5; sz <= 4; sz++) {
                for (int sy = -5; sy <= -1; sy++) {
                    final PalettedContainer<Object> c = new PalettedContainer<>(IDS, STONE, PalettedContainer.Strategy.SECTION_STATES);
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                c.set(x, y, z, rock(sx * 16 + x, sy * 16 + y, sz * 16 + z));
                            }
                        }
                    }
                    out.put(key(sx, sy, sz), c);
                }
            }
        }
        return out;
    }

    private static Object rock(int x, int y, int z) {
        final double cave = noise(x / 9, y / 6, z / 9, 1);
        if (cave > 0.93) {
            return AIR;
        }
        if (noise(x / 3, y / 3, z / 3, 2) > 0.985) {
            return IRON;
        }
        if (noise(x / 3, y / 2, z / 3, 3) > 0.98) {
            return COAL;
        }
        if (y < -50 && noise(x / 2, y / 2, z / 2, 4) > 0.997) {
            return DIAMOND;
        }
        if (noise(x / 5, y / 4, z / 5, 5) > 0.9) {
            return noise(x, y, z, 6) > 0.5 ? ANDESITE : GRAVEL;
        }
        return y < -32 ? DEEPSLATE : STONE;
    }

    private static double noise(int x, int y, int z, int seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed * 0x27D4EB2F165667C5L;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFFL) / (double) 0x1000000;
    }

    private static long key(int sx, int sy, int sz) {
        return ((long) (sx & 0xFFFF) << 32) | ((long) (sy & 0xFFFF) << 16) | (sz & 0xFFFF);
    }

    private static final class Rock implements ShellScan.Ground<PalettedContainer<Object>> {
        final Map<Long, PalettedContainer<Object>> world;
        final Object target;
        final Reference2IntOpenHashMap<Object> kinds = new Reference2IntOpenHashMap<>();

        Rock(Map<Long, PalettedContainer<Object>> world, Object target) {
            this.world = world;
            this.target = target;
            kinds.defaultReturnValue(Integer.MIN_VALUE);
        }

        @Override
        public PalettedContainer<Object> section(int sx, int sy, int sz) {
            final PalettedContainer<Object> c = world.get(key(sx, sy, sz));
            return c != null && c.maybeHas(s -> s == target) ? c : null;
        }

        @Override
        public int kindAt(PalettedContainer<Object> s, int x, int y, int z) {
            final Object state = s.get(x & 15, y & 15, z & 15);
            int k = kinds.getInt(state);
            if (k == Integer.MIN_VALUE) {
                k = state == target ? 0 : -1;
                kinds.put(state, k);
            }
            return k;
        }

        @Override
        public boolean covered(int x, int y, int z) {
            return true;
        }
    }

    private static long[] survey(Map<Long, PalettedContainer<Object>> world, Object target, boolean diagonal,
                                 long[] counts) {
        final ShellScan<PalettedContainer<Object>> scan = new ShellScan<>(new Rock(world, target), 0, 0, 0, 64,
                new VeinBook(SeismicSurvey.MAX_BLOCKS, diagonal));
        final long[] ticks = new long[SeismicWave.scanTicks(64)];
        int t = 0;
        while (!scan.done()) {
            final long start = System.nanoTime();
            scan.step();
            ticks[t++] = System.nanoTime() - start;
        }
        counts[0] = scan.checked();
        counts[1] = scan.veins();
        counts[2] = scan.blocks();
        return ticks;
    }

    @Test
    @DisplayName("a radius-64 survey's worst tick, for iron, diamond and voids, measured on real palette storage")
    void radius64() {
        final Map<Long, PalettedContainer<Object>> world = world();
        final StringBuilder report = new StringBuilder("\nSeismic scan cost, radius 64, ")
                .append(SurveyShells.countWithin(64)).append(" blocks in the bowl:\n");
        for (final Object[] target : new Object[][] {{IRON, true}, {DIAMOND, true}, {AIR, false}}) {
            long[] best = null;
            long[] counts = new long[3];

            for (int run = 0; run < 6; run++) {
                final long[] c = new long[3];
                final long[] ticks = survey(world, target[0], (boolean) target[1], c);
                if (best == null || Arrays.stream(ticks).sum() < Arrays.stream(best).sum()) {
                    best = ticks;
                    counts = c;
                }
            }
            final long worst = Arrays.stream(best).max().orElse(0);
            final long total = Arrays.stream(best).sum();
            report.append(String.format(java.util.Locale.ROOT,
                    "  %-11s read %,9d blocks over %d ticks, worst tick %.3f ms, all %.2f ms; %,d veins, %,d blocks%n",
                    target[0], counts[0], best.length, worst / 1.0e6, total / 1.0e6, counts[1], counts[2]));
            assertTrue(worst < 50_000_000L, target[0] + ": a tick took " + worst / 1.0e6 + " ms");
        }
        System.out.print(report);
    }
}
