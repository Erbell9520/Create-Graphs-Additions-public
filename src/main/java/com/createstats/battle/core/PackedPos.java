package com.createstats.battle.core;

public final class PackedPos {
    public static final int XZ_HALF = 1024;
    public static final int Y_HALF = 256;

    private static final int Z_MASK = 0x7FF;
    private static final int Y_MASK = 0x1FF;
    private static final int X_SHIFT = 20;
    private static final int Y_SHIFT = 11;

    private PackedPos() {
    }

    public static boolean fits(int dx, int dy, int dz) {
        return dx >= -XZ_HALF && dx < XZ_HALF && dz >= -XZ_HALF && dz < XZ_HALF && dy >= -Y_HALF && dy < Y_HALF;
    }

    public static int pack(int dx, int dy, int dz) {
        if (!fits(dx, dy, dz)) {
            throw new IllegalArgumentException("offset out of range: " + dx + ", " + dy + ", " + dz);
        }
        return ((dx + XZ_HALF) << X_SHIFT) | ((dy + Y_HALF) << Y_SHIFT) | (dz + XZ_HALF);
    }

    public static int dx(int packed) {
        return ((packed >>> X_SHIFT) & Z_MASK) - XZ_HALF;
    }

    public static int dy(int packed) {
        return ((packed >>> Y_SHIFT) & Y_MASK) - Y_HALF;
    }

    public static int dz(int packed) {
        return (packed & Z_MASK) - XZ_HALF;
    }

    public static int offset(int packed, int ox, int oy, int oz) {
        int x = dx(packed) + ox;
        int y = dy(packed) + oy;
        int z = dz(packed) + oz;
        return fits(x, y, z) ? pack(x, y, z) : -1;
    }

    public static int chebyshev(int a, int b) {
        return Math.max(Math.abs(dx(a) - dx(b)), Math.max(Math.abs(dy(a) - dy(b)), Math.abs(dz(a) - dz(b))));
    }
}
