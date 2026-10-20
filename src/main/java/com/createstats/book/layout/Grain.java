package com.createstats.book.layout;

public final class Grain {
    public static final int TILE = 256;

    private Grain() {
    }

    public static int colour(int base, float amp, int px, int py, int cell, int seed) {
        final int cells = TILE / cell;
        final int cx = Math.floorMod(px, TILE) / cell;
        final int cy = Math.floorMod(py, TILE) / cell;
        // lean on the neighbours so it reads as fibre, not salt
        final float n = (2.0f * noise(cx, cy, cells, seed) + noise(cx - 1, cy, cells, seed)
                + noise(cx + 1, cy, cells, seed) + noise(cx, cy - 1, cells, seed)
                + noise(cx, cy + 1, cells, seed)) / 6.0f;
        final int d = Math.round((n - 0.5f) * 2.0f * amp * 255.0f);
        final int r = clamp(((base >> 16) & 0xFF) + d);
        final int g = clamp(((base >> 8) & 0xFF) + d);
        final int b = clamp((base & 0xFF) + d);
        return (base & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static float noise(int cx, int cy, int cells, int seed) {
        final int x = Math.floorMod(cx, cells);
        final int y = Math.floorMod(cy, cells);
        int h = x * 374761393 + y * 668265263 + seed * (int) 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0f;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
