package com.createstats.seismic.archive;

import com.createstats.seismic.SurveyShells;

import java.util.Arrays;

public final class ArchiveBlow {
    public static final int HOLE_SPAN = 4;
    private static final int HOLE_SIDE = HOLE_SPAN * 2 + 1;

    public static final int SURFACE_STEP = 8;

    public static final short NO_SURFACE = Short.MIN_VALUE;

    public final int id;

    public final int x;
    public final int y;
    public final int z;

    public final String target;

    public final int reach;

    public final long at;

    public final int floor;

    public final long holeLo;
    public final long holeHi;

    short[] surface = new short[0];

    int surfaceX0;
    int surfaceZ0;
    int surfaceN;

    double front;
    double recorded;
    boolean done;
    boolean full;

    int veinsFound;
    int blocksFound;
    float nearestFound = -1.0f;
    int[] cells;

    public ArchiveBlow(int id, int x, int y, int z, String target, int reach, long at, int floor, long holeLo,
                       long holeHi) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.target = target == null ? "" : target;
        this.reach = Math.max(0, Math.min(SurveyShells.MAX_RADIUS, reach));
        this.at = at;
        this.floor = floor;
        this.holeLo = holeLo;
        this.holeHi = holeHi;
        this.cells = new int[0];
    }

    ArchiveBlow snapshot() {
        final ArchiveBlow b = new ArchiveBlow(id, x, y, z, target, reach, at, floor, holeLo, holeHi);
        b.surface = surface;
        b.surfaceX0 = surfaceX0;
        b.surfaceZ0 = surfaceZ0;
        b.surfaceN = surfaceN;
        b.front = front;
        b.recorded = recorded;
        b.done = done;
        b.full = full;
        b.veinsFound = veinsFound;
        b.blocksFound = blocksFound;
        b.nearestFound = nearestFound;
        b.cells = cells;
        return b;
    }

    static ArchiveBlow restored(int id, int x, int y, int z, String target, int reach, long at, int floor, long holeLo,
                                long holeHi, double front, double recorded, boolean done, boolean full, int veinsFound,
                                int blocksFound, float nearestFound, int[] cells, int surfaceX0, int surfaceZ0,
                                int surfaceN, short[] surface) {
        final ArchiveBlow b = new ArchiveBlow(id, x, y, z, target, reach, at, floor, holeLo, holeHi);
        if (surface != null && surfaceN >= 0 && surface.length == surfaceN * surfaceN) {
            b.surfaceX0 = surfaceX0;
            b.surfaceZ0 = surfaceZ0;
            b.surfaceN = surfaceN;
            b.surface = surface.clone();
        }
        b.front = Math.max(0.0, Math.min(b.reach, front));
        b.recorded = Math.max(0.0, Math.min(b.front, recorded));
        b.done = done;
        b.full = full;
        b.veinsFound = Math.max(0, veinsFound);
        b.blocksFound = Math.max(0, blocksFound);
        b.nearestFound = nearestFound;
        final int[] sorted = cells.clone();
        Arrays.sort(sorted);
        b.cells = sorted;
        return b;
    }

    public double front() {
        return front;
    }

    public double recorded() {
        return recorded;
    }

    public boolean done() {
        return done;
    }

    public boolean full() {
        return full;
    }

    public boolean cutShort() {
        return done && front + 1.0e-6 < reach;
    }

    public int veinsFound() {
        return veinsFound;
    }

    public int blocksFound() {
        return blocksFound;
    }

    public float nearestFound() {
        return nearestFound;
    }

    public int[] cells() {
        return cells;
    }

    public int cellCount() {
        return cells.length;
    }

    public long rigKey() {
        return key(x, y, z);
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int keyX(long key) {
        return (int) (key >> 38);
    }

    public static int keyY(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int keyZ(long key) {
        return (int) (key << 26 >> 38);
    }

    public double distanceTo(int bx, int by, int bz) {
        return SurveyShells.distanceOf(bx - x, by - y, bz - z);
    }

    public boolean hole(int bx, int bz) {
        final int dx = (bx >> 4) - (x >> 4) + HOLE_SPAN;
        final int dz = (bz >> 4) - (z >> 4) + HOLE_SPAN;
        if (dx < 0 || dz < 0 || dx >= HOLE_SIDE || dz >= HOLE_SIDE) {
            return false;
        }
        final int bit = dz * HOLE_SIDE + dx;
        return bit < 64 ? (holeLo >>> bit & 1L) != 0L : (holeHi >>> (bit - 64) & 1L) != 0L;
    }

    public static int holeBit(int rigX, int rigZ, int cx, int cz) {
        final int dx = cx - (rigX >> 4) + HOLE_SPAN;
        final int dz = cz - (rigZ >> 4) + HOLE_SPAN;
        if (dx < 0 || dz < 0 || dx >= HOLE_SIDE || dz >= HOLE_SIDE) {
            return -1;
        }
        return dz * HOLE_SIDE + dx;
    }

    public static long[] holes(int rigX, int rigZ, int reach, ChunkProbe loaded) {
        long lo = 0L;
        long hi = 0L;
        for (int cx = (rigX - reach) >> 4; cx <= (rigX + reach) >> 4; cx++) {
            for (int cz = (rigZ - reach) >> 4; cz <= (rigZ + reach) >> 4; cz++) {
                final int bit = holeBit(rigX, rigZ, cx, cz);
                if (bit < 0 || loaded.loaded(cx, cz)) {
                    continue;
                }
                if (bit < 64) {
                    lo |= 1L << bit;
                } else {
                    hi |= 1L << (bit - 64);
                }
            }
        }
        return new long[] {lo, hi};
    }

    @FunctionalInterface
    public interface ChunkProbe {
        boolean loaded(int cx, int cz);
    }

    @FunctionalInterface
    public interface SurfaceProbe {
        int height(int x, int z);
    }

    public void recordSurface(SurfaceProbe probe) {
        final int x0 = Math.floorDiv(x - reach, SURFACE_STEP) + 1;
        final int z0 = Math.floorDiv(z - reach, SURFACE_STEP) + 1;
        final int n = Math.max(0, Math.min(Math.floorDiv(x + reach, SURFACE_STEP) - x0 + 1,
                Math.floorDiv(z + reach, SURFACE_STEP) - z0 + 1));
        final short[] h = new short[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                final int v = probe.height((x0 + i) * SURFACE_STEP, (z0 + j) * SURFACE_STEP);
                h[j * n + i] = v == NO_SURFACE ? NO_SURFACE : (short) Math.max(-2_048, Math.min(4_095, v));
            }
        }
        this.surfaceX0 = x0;
        this.surfaceZ0 = z0;
        this.surfaceN = n;
        this.surface = h;
    }

    public short[] surface() {
        return surface;
    }

    public int surfaceX0() {
        return surfaceX0;
    }

    public int surfaceZ0() {
        return surfaceZ0;
    }

    public int surfaceN() {
        return surfaceN;
    }

    public boolean covers(int bx, int by, int bz) {
        return by <= y - 1 && by >= floor && distanceTo(bx, by, bz) <= recorded + 1.0e-6 && !hole(bx, bz);
    }

    boolean coversWithin(int bx, int by, int bz, double radius) {
        return by <= y - 1 && by >= floor && distanceTo(bx, by, bz) <= radius + 1.0e-6 && !hole(bx, bz);
    }

    public boolean mayOverlap(ArchiveBlow o) {
        final double dx = x - o.x;
        final double dy = y - o.y;
        final double dz = z - o.z;
        final double r = reach + o.reach + 1.0;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    public boolean newerThan(ArchiveBlow o) {
        return at != o.at ? at > o.at : id > o.id;
    }

    public static int cell(int kind, int packed) {
        return (kind & 0x7F) << 24 | (packed & 0xFFFFFF);
    }

    public static int kindOf(int cell) {
        return cell >>> 24;
    }

    public static int packedOf(int cell) {
        return cell & 0xFFFFFF;
    }

    public int cellX(int cell) {
        return x + SurveyShells.dx(packedOf(cell));
    }

    public int cellY(int cell) {
        return y + SurveyShells.dy(packedOf(cell));
    }

    public int cellZ(int cell) {
        return z + SurveyShells.dz(packedOf(cell));
    }

    @Override
    public String toString() {
        return "Blow#" + id + "[" + target + " r" + reach + " at " + x + "," + y + "," + z + " t" + at + ", "
                + cells.length + " cells, read " + String.format(java.util.Locale.ROOT, "%.1f", recorded) + "]";
    }
}
