package com.createstats.seismic;

import java.util.Arrays;

// sorted by distance so the scan follows the wave front
public final class SurveyShells {
    public static final int MAX_RADIUS = SeismicCharge.MAX_RADIUS;

    private static final int BIAS = 128;

    private SurveyShells() {
    }

    public static int size() {
        return Table.PACKED.length;
    }

    public static int countWithin(double radius) {
        if (radius <= 0.0) {
            return 0;
        }
        final float r = (float) Math.min(MAX_RADIUS, radius);
        final float[] d = Table.DIST;
        int lo = 0;
        int hi = d.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (d[mid] <= r) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public static int from(int tick, int radius) {
        return countWithin(Math.min(radius, SeismicWave.frontAt(tick - 1)));
    }

    public static int to(int tick, int radius) {
        return countWithin(Math.min(radius, SeismicWave.frontAt(tick)));
    }

    public static int packed(int index) {
        return Table.PACKED[index];
    }

    public static float distance(int index) {
        return Table.DIST[index];
    }

    public static int pack(int dx, int dy, int dz) {
        return ((dx + BIAS) << 16) | ((dy + BIAS) << 8) | (dz + BIAS);
    }

    public static int dx(int packed) {
        return ((packed >>> 16) & 0xFF) - BIAS;
    }

    public static int dy(int packed) {
        return ((packed >>> 8) & 0xFF) - BIAS;
    }

    public static int dz(int packed) {
        return (packed & 0xFF) - BIAS;
    }

    public static double distanceOf(int dx, int dy, int dz) {
        final double y = dy + 0.5;
        return Math.sqrt(dx * (double) dx + y * y + dz * (double) dz);
    }

    public static boolean inBowl(int dx, int dy, int dz, double radius) {
        return dy <= -1 && distanceOf(dx, dy, dz) <= radius;
    }

    private static final class Table {
        static final int[] PACKED;
        static final float[] DIST;

        static {
            final int r = MAX_RADIUS;
            final long[] keys = new long[(2 * r + 1) * r * (2 * r + 1)];
            int n = 0;
            for (int dy = -1; dy >= -r; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        final float d = (float) distanceOf(dx, dy, dz);
                        if (d <= r) {
                            keys[n++] = ((long) Float.floatToIntBits(d) << 32) | (pack(dx, dy, dz) & 0xFFFFFFFFL);
                        }
                    }
                }
            }
            final long[] sorted = Arrays.copyOf(keys, n);
            Arrays.sort(sorted);
            PACKED = new int[n];
            DIST = new float[n];
            for (int i = 0; i < n; i++) {
                PACKED[i] = (int) sorted[i];
                DIST[i] = Float.intBitsToFloat((int) (sorted[i] >>> 32));
            }
        }

        private Table() {
        }
    }
}
