package com.createstats.gui.home;

import java.util.Arrays;

public final class PlanGrid {
    public static final byte NONE = 0;

    public static final byte GROUND = 1;

    public static final byte FLOOR = 2;

    public static final byte RAISED = 3;

    public static final byte WALL = 4;

    public static final byte WINDOW = 5;
    public static final byte DOOR = 6;
    public static final byte GATE = 7;

    public static final byte FENCE = 8;

    public static final byte STAIRS = 9;

    public static final byte STAIRS_BELOW = 10;
    public static final byte LADDER = 11;
    public static final byte WATER = 12;
    public static final byte LAVA = 13;

    public static final byte HOLE = 14;
    public static final byte FOLIAGE = 15;

    public static final byte POST = 16;

    public static final byte SYM_NONE = 0;
    public static final byte SYM_BED = 1;
    public static final byte SYM_CHEST = 2;
    public static final byte SYM_BARREL = 3;
    public static final byte SYM_SHELF = 4;
    public static final byte SYM_POT = 5;

    public static final byte SYM_SEAT = 6;
    public static final byte SYM_FURNACE = 7;

    public static final byte SYM_HATCH = 8;

    public static final byte SYM_PLANT = 9;

    public static final byte SYM_PANELS = 10;

    public static final byte SYM_THIN = 11;

    public static final byte SYM_TABLE = 12;

    public static final byte SYM_BENCH = 13;

    public static final byte SYM_ANVIL = 14;

    public static final byte SYM_CAULDRON = 15;

    public static final byte SYM_LECTERN = 16;

    public static final byte SYM_CANDLE = 17;

    public static final byte SYM_LANTERN = 18;

    public static final byte SYM_CROP = 19;

    public static final byte SYM_CANE = 20;

    public static final byte SYM_BOOK = 21;

    public static final int F_OPEN = 1;

    public static final int F_HEAD = 1 << 1;

    public static final int F_AXIS_Z = 1 << 2;

    public static final int F_LOW = 1 << 3;

    public static final int F_DEEP = 1 << 4;

    public static final int F_NATURAL = 1 << 5;

    public static final int F_ROOFED = 1 << 6;

    public static final int F_SPAWN = 1 << 7;

    public static final int F_LIT = 1 << 8;

    public static final int F_DOUBLE = 1 << 9;

    public static final int F_PATH = 1 << 10;

    public static final int F_FURROW = 1 << 11;

    public static final int F_N = 1 << 12;
    public static final int F_E = 1 << 13;
    public static final int F_S = 1 << 14;
    public static final int F_W = 1 << 15;

    public static final int F_CANOPY = 1 << 16;

    public static final int F_BARS = 1 << 17;

    public static final int F_GROWN = 1 << 18;

    public static final byte NORTH = 0;
    public static final byte EAST = 1;
    public static final byte SOUTH = 2;
    public static final byte WEST = 3;
    public static final byte UP = 4;
    public static final byte DOWN = 5;
    public static final byte NO_DIR = -1;

    public final int x0;
    public final int z0;
    public final int cols;
    public final int rows;

    private int floorY = Integer.MIN_VALUE;

    final byte[] kind;
    final byte[] sym;

    final int[] colour;

    final int[] under;

    final int[] accent;
    final int[] flags;
    final byte[] dir;

    final byte[] edges;

    final byte[] light;

    public PlanGrid(int x0, int z0, int cols, int rows) {
        this.x0 = x0;
        this.z0 = z0;
        this.cols = cols;
        this.rows = rows;
        final int n = cols * rows;
        kind = new byte[n];
        sym = new byte[n];
        colour = new int[n];
        under = new int[n];
        accent = new int[n];
        flags = new int[n];
        dir = new byte[n];
        edges = new byte[n];
        light = new byte[n];
        Arrays.fill(colour, -1);
        Arrays.fill(under, -1);
        Arrays.fill(accent, -1);
        Arrays.fill(dir, NO_DIR);
        Arrays.fill(light, (byte) 15);
    }

    public boolean contains(int x, int z) {
        return x >= x0 && z >= z0 && x < x0 + cols && z < z0 + rows;
    }

    public void floorY(int y) {
        floorY = y;
    }

    public int floorY() {
        return floorY;
    }

    public int index(int x, int z) {
        return (z - z0) * cols + (x - x0);
    }

    public void set(int x, int z, byte kind, int colour) {
        final int i = index(x, z);
        this.kind[i] = kind;
        this.colour[i] = colour;
    }

    public void sym(int x, int z, byte sym) {
        this.sym[index(x, z)] = sym;
    }

    public void under(int x, int z, int colour) {
        under[index(x, z)] = colour;
    }

    public void accent(int x, int z, int colour) {
        accent[index(x, z)] = colour;
    }

    public void flag(int x, int z, int bits) {
        flags[index(x, z)] |= bits;
    }

    public void dir(int x, int z, byte d) {
        dir[index(x, z)] = d;
    }

    public void doorEdges(int x, int z, byte closed, byte open) {
        edges[index(x, z)] = (byte) ((closed & 3) | ((open & 3) << 2));
    }

    public void light(int x, int z, int level) {
        light[index(x, z)] = (byte) Math.max(0, Math.min(15, level));
    }

    public byte kind(int x, int z) {
        return contains(x, z) ? kind[index(x, z)] : NONE;
    }

    public byte sym(int x, int z) {
        return contains(x, z) ? sym[index(x, z)] : SYM_NONE;
    }

    public int colour(int x, int z) {
        return contains(x, z) ? colour[index(x, z)] : -1;
    }

    public int under(int x, int z) {
        return contains(x, z) ? under[index(x, z)] : -1;
    }

    public int accent(int x, int z) {
        return contains(x, z) ? accent[index(x, z)] : -1;
    }

    public int flags(int x, int z) {
        return contains(x, z) ? flags[index(x, z)] : 0;
    }

    public boolean has(int x, int z, int bits) {
        return (flags(x, z) & bits) == bits;
    }

    public byte dir(int x, int z) {
        return contains(x, z) ? dir[index(x, z)] : NO_DIR;
    }

    public byte closedEdge(int x, int z) {
        return (byte) (contains(x, z) ? edges[index(x, z)] & 3 : 0);
    }

    public byte openEdge(int x, int z) {
        return (byte) (contains(x, z) ? (edges[index(x, z)] >> 2) & 3 : 0);
    }

    public int light(int x, int z) {
        return contains(x, z) ? light[index(x, z)] : 15;
    }

    public static boolean isWallish(byte k) {
        return k == WALL || k == WINDOW || k == DOOR || k == POST;
    }

    public static boolean stopsLight(byte k) {
        return k == WALL || k == WINDOW || k == POST || k == NONE;
    }

    public boolean isClearFloor(int x, int z) {
        final byte k = kind(x, z);
        return (k == FLOOR || k == GROUND) && sym(x, z) == SYM_NONE;
    }

    public void settle() {
        for (int z = z0; z < z0 + rows; z++) {
            for (int x = x0; x < x0 + cols; x++) {
                final byte k = kind(x, z);
                if (k == WINDOW || k == GATE) {
                    if (!has(x, z, F_AXIS_Z) && !has(x, z, F_OPEN)) {
                        final boolean alongX = lineFriend(kind(x - 1, z)) || lineFriend(kind(x + 1, z));
                        final boolean alongZ = lineFriend(kind(x, z - 1)) || lineFriend(kind(x, z + 1));
                        if (!alongX && alongZ) {
                            flag(x, z, F_AXIS_Z);
                        }
                    }
                } else if (k == WALL && !has(x, z, F_NATURAL)) {
                    if (!joins(kind(x - 1, z)) && !joins(kind(x + 1, z)) && !joins(kind(x, z - 1))
                            && !joins(kind(x, z + 1))) {
                        kind[index(x, z)] = POST;
                    }
                } else if (k == RAISED && sym(x, z) == SYM_SHELF && dir(x, z) == NO_DIR) {
                    for (byte d = 0; d < 4; d++) {
                        if (isWallish(kind(x + dx(d), z + dz(d)))) {
                            dir(x, z, opposite(d));
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean lineFriend(byte k) {
        return k == WALL || k == WINDOW || k == DOOR || k == POST || k == FENCE || k == GATE;
    }

    private static boolean joins(byte k) {
        return k == WALL || k == WINDOW || k == DOOR || k == GATE || k == FENCE;
    }

    public static int dx(int d) {
        return d == EAST ? 1 : d == WEST ? -1 : 0;
    }

    public static int dz(int d) {
        return d == SOUTH ? 1 : d == NORTH ? -1 : 0;
    }

    public static byte opposite(byte d) {
        return d < 0 || d > 3 ? d : (byte) ((d + 2) & 3);
    }
}
