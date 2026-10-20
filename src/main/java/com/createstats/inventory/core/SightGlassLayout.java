package com.createstats.inventory.core;

public final class SightGlassLayout {
    public static final int GLASS_W = 224;
    public static final int GLASS_H = 192;

    public static final int TOP = 20;

    public static final int MAX_GRID_SLOTS = 54;

    public static final int FULL_FOOT = 90;

    public enum Kind {
        GRID,
        TILES
    }

    public record Layout(Kind kind, int cols, int rows, int cellW, int cellH, int gap, int x0, int y0,
                         int shown, boolean compactFoot) {
        public int cellX(int i) {
            return x0 + (i % cols) * (cellW + gap);
        }

        public int cellY(int i) {
            return y0 + (i / cols) * (cellH + gap);
        }

        public int bottom() {
            return y0 + rows * (cellH + gap) - gap;
        }

        public int width() {
            return cols * cellW + (cols - 1) * gap;
        }
    }

    public static final int TILE_COLS = 4;
    public static final int TILE_ROWS = 4;
    public static final int TILE_W = 53;
    public static final int TILE_H = 22;

    public static Layout forSlots(int slots) {
        final int n = Math.max(1, slots);
        if (n > MAX_GRID_SLOTS) {
            final int gap = 2;
            final int w = TILE_COLS * TILE_W + (TILE_COLS - 1) * gap;
            return new Layout(Kind.TILES, TILE_COLS, TILE_ROWS, TILE_W, TILE_H, gap, (GLASS_W - w) / 2, TOP,
                    TILE_COLS * TILE_ROWS, false);
        }
        final int cols;
        if (n == 5) {
            cols = 5;
        } else if (n == 9) {
            cols = 3;
        } else {
            cols = Math.min(9, n);
        }
        final int rows = (n + cols - 1) / cols;
        final int cell = rows <= 3 ? 22 : 20;
        final int gap = 2;
        final int w = cols * cell + (cols - 1) * gap;
        final int x0 = (GLASS_W - w) / 2;
        final int bottom = TOP + rows * (cell + gap) - gap;
        final boolean compact = GLASS_H - (bottom + 7) < FULL_FOOT;
        return new Layout(Kind.GRID, cols, rows, cell, cell, gap, x0, TOP, n, compact);
    }
}
