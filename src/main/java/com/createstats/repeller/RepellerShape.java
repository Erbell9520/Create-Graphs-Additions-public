package com.createstats.repeller;

public enum RepellerShape {
    SPHERE,
    COLUMN;

    public boolean contains(double cx, double cy, double cz, double r, double x, double y, double z) {
        if (r <= 0.0) {
            return false;
        }
        final double dx = x - cx;
        final double dz = z - cz;
        final double flat = dx * dx + dz * dz;
        if (this == COLUMN) {
            return flat <= r * r;
        }
        final double dy = y - cy;
        return flat + dy * dy <= r * r;
    }

    public static RepellerShape byOrdinal(int ordinal) {
        return ordinal == COLUMN.ordinal() ? COLUMN : SPHERE;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static long chunkKeyAt(double x, double z) {
        return chunkKey(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    public static boolean discTouchesChunk(double cx, double cz, double r, int chunkX, int chunkZ) {
        if (r <= 0.0) {
            return false;
        }
        final double minX = chunkX * 16.0;
        final double minZ = chunkZ * 16.0;
        final double nx = Math.max(minX, Math.min(minX + 16.0, cx));
        final double nz = Math.max(minZ, Math.min(minZ + 16.0, cz));
        final double dx = nx - cx;
        final double dz = nz - cz;
        return dx * dx + dz * dz <= r * r;
    }

    public static long[] chunksTouched(double cx, double cz, double r) {
        if (r <= 0.0) {
            return new long[0];
        }
        final int x0 = ((int) Math.floor(cx - r)) >> 4;
        final int x1 = ((int) Math.floor(cx + r)) >> 4;
        final int z0 = ((int) Math.floor(cz - r)) >> 4;
        final int z1 = ((int) Math.floor(cz + r)) >> 4;
        final long[] tmp = new long[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (discTouchesChunk(cx, cz, r, x, z)) {
                    tmp[n++] = chunkKey(x, z);
                }
            }
        }
        return java.util.Arrays.copyOf(tmp, n);
    }
}
