package com.createstats.chunkloader.core;

import java.util.OptionalLong;
import java.util.function.LongPredicate;

public final class CrownRegion {
    public static final int CHUNKS = 32;

    public static final int FIRST_PLOT_CHUNK = 20_480_000 >> 4;

    public static final int MAX_RING = CHUNKS - 1;

    private CrownRegion() {
    }

    public static int regionOf(int chunk) {
        return Math.floorDiv(chunk, CHUNKS);
    }

    public static int firstBlock(int region) {
        return region * CHUNKS * 16;
    }

    public static int lastBlock(int region) {
        return firstBlock(region) + CHUNKS * 16 - 1;
    }

    public static boolean sameRegion(int ax, int az, int bx, int bz) {
        return regionOf(ax) == regionOf(bx) && regionOf(az) == regionOf(bz);
    }

    public static boolean isPlotChunk(int cx, int cz) {
        return cx >= FIRST_PLOT_CHUNK && cz >= FIRST_PLOT_CHUNK;
    }

    public static long pack(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | ((cz & 0xFFFFFFFFL) << 32);
    }

    public static int x(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int z(long packed) {
        return (int) (packed >>> 32 & 0xFFFFFFFFL);
    }

    public static OptionalLong nextInSpiral(int cx, int cz, LongPredicate accept) {
        for (int ring = 0; ring <= MAX_RING; ring++) {
            final OptionalLong found = scanRing(cx, cz, ring, true, accept);
            if (found.isPresent()) {
                return found;
            }
        }
        return OptionalLong.empty();
    }

    public static long spiralAt(int cx, int cz, int index) {
        final int[] seen = {0};
        for (int ring = 0; ; ring++) {
            final OptionalLong found = scanRing(cx, cz, ring, false, c -> seen[0]++ == index);
            if (found.isPresent()) {
                return found.getAsLong();
            }
        }
    }

    private static OptionalLong scanRing(int cx, int cz, int ring, boolean regionOnly, LongPredicate accept) {
        if (ring == 0) {
            return candidate(cx, cz, cx, cz, regionOnly, accept);
        }
        final int x0 = cx - ring;
        final int z0 = cz - ring;
        final int side = ring * 2;

        for (int edge = 0; edge < 4; edge++) {
            for (int i = 0; i < side; i++) {
                final int x;
                final int z;
                switch (edge) {
                    case 0 -> {
                        x = x0 + i;
                        z = z0;
                    }
                    case 1 -> {
                        x = x0 + side;
                        z = z0 + i;
                    }
                    case 2 -> {
                        x = x0 + side - i;
                        z = z0 + side;
                    }
                    default -> {
                        x = x0;
                        z = z0 + side - i;
                    }
                }
                final OptionalLong c = candidate(cx, cz, x, z, regionOnly, accept);
                if (c.isPresent()) {
                    return c;
                }
            }
        }
        return OptionalLong.empty();
    }

    private static OptionalLong candidate(int cx, int cz, int x, int z, boolean regionOnly, LongPredicate accept) {
        if (regionOnly && !sameRegion(cx, cz, x, z)) {
            return OptionalLong.empty();
        }
        final long packed = pack(x, z);
        return accept.test(packed) ? OptionalLong.of(packed) : OptionalLong.empty();
    }
}
