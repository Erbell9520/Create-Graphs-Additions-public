package com.createstats.statsmk3.layout;

public final class GridSpec {
    public static final int CELLS_PER_BLOCK = 4;

    public static final int MAX_COLS = 32;
    public static final int MAX_ROWS = 24;

    public static final int MAX_TILES = 32;

    private GridSpec() {
    }

    public static int colsFor(int multiblockWidth) {
        return clamp(Math.max(1, multiblockWidth) * CELLS_PER_BLOCK, CELLS_PER_BLOCK, MAX_COLS);
    }

    public static int rowsFor(int multiblockHeight) {
        return clamp(Math.max(1, multiblockHeight) * CELLS_PER_BLOCK, CELLS_PER_BLOCK, MAX_ROWS);
    }

    public static int consoleCols() {
        return 5;
    }

    public static int consoleRows() {
        return 4;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
