package com.createstats.gui.home;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// true colour, every surface keeps its own map colour
public final class PlanPainter {
    private PlanPainter() {
    }

    public static final int PAPER = 0xD6BE96;

    public static final int GLASS = 0xC8DFE6;

    public static final int GLOW = 0xFFE066;

    public static final int SWING = 0x6A5A48;

    public static final int VOID = 0x2A241E;

    public static final int WATER_PALE = 0xD8E6F2;

    public static final int WATER_DEEP = 0x1A2F8A;

    public static final int IRON = 0x3C3C3C;

    public static final int LAVA_CAST = 0xFF9A30;

    public static final int FLAME = 0xFFD24A;

    public static final int CANOPY = 0x2E7A22;

    public static final int FULL = 8;

    public static final int SMALL = 6;

    public static final int LOWEST = 135;
    public static final int LOW = 180;
    public static final int NORMAL = 220;
    public static final int HIGH = 255;

    static final float INDOOR = 0.18f;

    static final float OUTDOOR = 0.55f;

    static final float BELOW = 0.65f;

    static final int BELOW_PAPER = 0xC1C1C1;

    static final float BELOW_HUE = 0.7f;

    static final float OUTDOOR_BUILT = 0.25f;

    static final int REACH = 2;

    static final float POOL_MIN = 1.0f;
    static final float POOL_MAX = 2.4f;

    static final int[] POOL_ALPHA = {0x40, 0x30, 0x20, 0x10};

    static final float[] POOL_BAND = {0.30f, 0.55f, 0.78f, 1.0f};

    static final float POOL_FLOOR = 0.55f;

    public record Lamp(long[] cells, float level, int tint) {
        public static Lamp full(long... cells) {
            return new Lamp(cells, 1f, GLOW);
        }
    }

    public record Picture(int[] argb, int w, int h, int s, int x0, int z0) {
    }

    public static Picture paint(PlanGrid g, int s, List<Lamp> lamps, boolean lightView) {
        final Canvas c = new Canvas(g, s);
        c.base();
        c.rugs();
        c.furniture();
        c.stairs();
        c.shadows();
        c.canopy();
        c.cast();
        c.structure();
        if (lightView) {
            c.darkenByLight();
        } else {
            c.pools(lamps);
        }
        return new Picture(c.px, c.w, c.h, s, g.x0, g.z0);
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long p) {
        return (int) (p >> 32);
    }

    public static int unpackZ(long p) {
        return (int) p;
    }

    public static int shade(int rgb, int k) {
        final int r = ((rgb >> 16) & 255) * k / 255;
        final int gg = ((rgb >> 8) & 255) * k / 255;
        final int b = (rgb & 255) * k / 255;
        return (r << 16) | (gg << 8) | b;
    }

    public static int mix(int a, int b, float t) {
        final int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        final int gg = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        final int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    public static int dark(int rgb, float k) {
        final int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k));
        final int gg = Math.min(255, Math.round(((rgb >> 8) & 255) * k));
        final int b = Math.min(255, Math.round((rgb & 255) * k));
        return (r << 16) | (gg << 8) | b;
    }

    public static int luma(int rgb) {
        return (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
    }

    public static int desaturate(int rgb, float t) {
        final int l = luma(rgb);
        return mix(rgb, (l << 16) | (l << 8) | l, t);
    }

    static int hash(int x, int z, int salt) {
        int v = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        v = (v ^ (v >>> 13)) * 0x5BD1E995;
        return (v ^ (v >>> 15)) & 0x7FFFFFFF;
    }

    static int[][] quarter(int r) {
        final java.util.LinkedHashSet<Long> pts = new java.util.LinkedHashSet<>();
        int x = r;
        int y = 0;
        int err = 1 - r;
        while (x >= y) {
            pts.add(pack(x, y));
            pts.add(pack(y, x));
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
        final int[][] out = new int[pts.size()][];
        int i = 0;
        for (long p : pts) {
            out[i++] = new int[] {unpackX(p), unpackZ(p)};
        }
        return out;
    }

    static int poolAlpha(float t, float strength) {
        for (int i = 0; i < POOL_BAND.length; i++) {
            if (t < POOL_BAND[i]) {
                return Math.round(POOL_ALPHA[i] * strength);
            }
        }
        return 0;
    }

    static float poolRadius(float level) {
        return POOL_MIN + (POOL_MAX - POOL_MIN) * Math.max(0f, Math.min(1f, level));
    }

    static final class Canvas {
        final PlanGrid g;
        final int s;
        final int w;
        final int h;
        final int[] px;

        Canvas(PlanGrid g, int s) {
            this.g = g;
            this.s = s;
            this.w = g.cols * s;
            this.h = g.rows * s;
            this.px = new int[w * h];
        }

        void put(int x, int y, int rgb) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                px[y * w + x] = 0xFF000000 | rgb;
            }
        }

        int get(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h ? px[y * w + x] & 0xFFFFFF : PAPER;
        }

        void blend(int x, int y, int rgb, int alpha) {
            if (x < 0 || y < 0 || x >= w || y >= h || alpha <= 0) {
                return;
            }
            final int i = y * w + x;
            if ((px[i] >>> 24) == 0) {
                return;
            }
            px[i] = 0xFF000000 | mix(px[i] & 0xFFFFFF, rgb, alpha / 255f);
        }

        void rect(int x0, int y0, int x1, int y1, int rgb) {
            for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
                for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
                    px[y * w + x] = 0xFF000000 | rgb;
                }
            }
        }

        void frame(int x0, int y0, int x1, int y1, int rgb) {
            for (int x = x0; x < x1; x++) {
                put(x, y0, rgb);
                put(x, y1 - 1, rgb);
            }
            for (int y = y0; y < y1; y++) {
                put(x0, y, rgb);
                put(x1 - 1, y, rgb);
            }
        }

        int ox(int x) {
            return (x - g.x0) * s;
        }

        int oy(int z) {
            return (z - g.z0) * s;
        }

        void edgeLine(int x, int z, int d, int rgb, int inset, boolean dotted) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            for (int t = 0; t < s; t++) {
                if (dotted && (t & 1) == 1) {
                    continue;
                }
                switch (d) {
                    case PlanGrid.NORTH -> put(x0 + t, y0 + inset, rgb);
                    case PlanGrid.SOUTH -> put(x0 + t, y0 + s - 1 - inset, rgb);
                    case PlanGrid.WEST -> put(x0 + inset, y0 + t, rgb);
                    default -> put(x0 + s - 1 - inset, y0 + t, rgb);
                }
            }
        }

        void edgeBlend(int x, int z, int d, int rgb, int inset, int alpha) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            for (int t = 0; t < s; t++) {
                switch (d) {
                    case PlanGrid.NORTH -> blend(x0 + t, y0 + inset, rgb, alpha);
                    case PlanGrid.SOUTH -> blend(x0 + t, y0 + s - 1 - inset, rgb, alpha);
                    case PlanGrid.WEST -> blend(x0 + inset, y0 + t, rgb, alpha);
                    default -> blend(x0 + s - 1 - inset, y0 + t, rgb, alpha);
                }
            }
        }

        // at even scales the centre sits between pixels, shift half in by one
        void disc(int cx, int cy, int r, int rgb) {
            final int e = s % 2 == 0 ? 1 : 0;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy <= r * r + r) {
                        put(cx + dx - (dx >= 0 ? e : 0), cy + dy - (dy >= 0 ? e : 0), rgb);
                    }
                }
            }
        }

        void ring(int cx, int cy, int r, int rgb) {
            final int e = s % 2 == 0 ? 1 : 0;
            for (int[] p : quarter(r)) {
                put(cx + p[0] - e, cy + p[1] - e, rgb);
                put(cx - p[0], cy + p[1] - e, rgb);
                put(cx + p[0] - e, cy - p[1], rgb);
                put(cx - p[0], cy - p[1], rgb);
            }
        }

        boolean outdoors(int x, int z) {
            return !g.has(x, z, PlanGrid.F_ROOFED);
        }

        int floorColour(int x, int z, int rgb) {
            if (rgb < 0) {
                return PAPER;
            }
            if (g.has(x, z, PlanGrid.F_DEEP)) {
                return mix(desaturate(shade(rgb, LOW), 1f - BELOW_HUE), BELOW_PAPER, BELOW);
            }
            final int k = g.has(x, z, PlanGrid.F_LOW) ? LOW : NORMAL;
            return mix(shade(rgb, k), PAPER, outdoors(x, z) ? OUTDOOR : INDOOR);
        }

        int topColour(int x, int z, int rgb) {
            final int top = shade(rgb, HIGH);
            if (g.has(x, z, PlanGrid.F_NATURAL)) {
                return mix(top, PAPER, 0.35f);
            }
            return outdoors(x, z) ? mix(top, PAPER, OUTDOOR_BUILT) : top;
        }

        int edgeColour(int rgb) {
            return shade(rgb, LOWEST);
        }

        int floorUnder(int x, int z) {
            final int u = g.under(x, z);
            return u >= 0 ? floorColour(x, z, u) : PAPER;
        }

        void base() {
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    final byte k = g.kind(x, z);
                    final int x0 = ox(x);
                    final int y0 = oy(z);
                    final int col = g.colour(x, z);
                    switch (k) {
                        case PlanGrid.NONE -> {
                        }
                        case PlanGrid.GROUND, PlanGrid.FLOOR -> {
                            int f = floorColour(x, z, col);
                            if (g.accent(x, z) >= 0 && g.sym(x, z) == PlanGrid.SYM_NONE) {
                                f = mix(f, shade(g.accent(x, z), NORMAL), 0.62f);
                            }
                            rect(x0, y0, x0 + s, y0 + s, f);
                            ground(x, z, f, col);
                        }
                        case PlanGrid.RAISED -> {
                            rect(x0, y0, x0 + s, y0 + s, topColour(x, z, col));
                        }
                        case PlanGrid.WALL -> rect(x0, y0, x0 + s, y0 + s, topColour(x, z, col));
                        case PlanGrid.WINDOW, PlanGrid.DOOR, PlanGrid.GATE, PlanGrid.FENCE, PlanGrid.LADDER,
                             PlanGrid.POST -> rect(x0, y0, x0 + s, y0 + s, floorUnder(x, z));
                        case PlanGrid.STAIRS -> rect(x0, y0, x0 + s, y0 + s,
                                mix(shade(col < 0 ? 0x8F7748 : col, NORMAL), PAPER, INDOOR));
                        case PlanGrid.STAIRS_BELOW -> rect(x0, y0, x0 + s, y0 + s,
                                mix(shade(col < 0 ? 0x8F7748 : col, LOW), PAPER, INDOOR / 2));
                        case PlanGrid.WATER -> water(x, z, col < 0 ? 0x4040FF : col);
                        case PlanGrid.LAVA -> lava(x, z);
                        case PlanGrid.HOLE -> hole(x, z, col);
                        case PlanGrid.FOLIAGE -> foliage(x, z, col < 0 ? 0x007C00 : col);
                        default -> rect(x0, y0, x0 + s, y0 + s, PAPER);
                    }
                }
            }
        }

        void ground(int x, int z, int f, int col) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int hh = hash(x, z, 0);
            final byte sym = g.sym(x, z);
            if (sym == PlanGrid.SYM_PLANT && g.accent(x, z) >= 0) {
                final int petal = shade(g.accent(x, z), HIGH);
                final int leaf = shade(0x007C00, NORMAL);
                final int cx = x0 + s / 2 - 1 + hh % 2;
                final int cy = y0 + s / 2 - 1 + (hh >> 3) % 2;
                put(cx, cy, petal);
                if (s >= SMALL) {
                    put(cx + 1, cy, petal);
                    put(cx, cy + 1, leaf);
                    put(cx + 1, cy + 1, dark(petal, 0.8f));
                }
                return;
            }
            if (sym == PlanGrid.SYM_PANELS) {
                panels(x, z, f);
                return;
            }
            final boolean furrow = g.has(x, z, PlanGrid.F_FURROW);
            final boolean path = g.has(x, z, PlanGrid.F_PATH);
            if (furrow) {
                final int line = dark(f, 0.86f);
                for (int j = 0; j < s; j += 2) {
                    for (int i = 0; i < s; i++) {
                        put(x0 + i, y0 + j, line);
                    }
                }
            }
            if (path) {
                final int edge = dark(f, 0.78f);
                for (int d = 0; d < 4; d++) {
                    final int nx = x + PlanGrid.dx(d);
                    final int nz = z + PlanGrid.dz(d);
                    if (!g.has(nx, nz, PlanGrid.F_PATH) && g.kind(nx, nz) != PlanGrid.NONE) {
                        edgeLine(x, z, d, edge, 0, false);
                    }
                }
            }
            if (sym != PlanGrid.SYM_NONE || furrow || path || s < SMALL || !outdoors(x, z) || col < 0
                    || g.has(x, z, PlanGrid.F_DEEP)) {
                return;
            }
            if (col == 0x7FB238 || col == 0x007C00) {
                if (hh % 3 == 0) {
                    final int tuft = mix(f, 0x3C5A1C, 0.45f);
                    final int tx = x0 + 1 + hh % Math.max(1, s - 4);
                    final int ty = y0 + 1 + (hh >> 4) % Math.max(1, s - 3);
                    put(tx, ty, tuft);
                    put(tx + 2, ty, tuft);
                    put(tx + 1, ty + 1, tuft);
                }
            } else if (col == 0xF7E9A3 || col == 0xD5C98E) {
                for (int n = 0; n < 2; n++) {
                    final int k = hash(x, z, 7 + n);
                    put(x0 + k % s, y0 + (k >> 6) % s, dark(f, 0.88f));
                }
            }
        }

        void panels(int x, int z, int f) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int line = mix(f, 0xB8C8E8, 0.35f);
            for (int j = 0; j < s; j++) {
                for (int i = 0; i < s; i++) {
                    if ((x0 + i) % 3 == 0 || (y0 + j) % 4 == 0) {
                        put(x0 + i, y0 + j, line);
                    }
                }
            }
            final int edge = dark(f, 0.65f);
            for (int d = 0; d < 4; d++) {
                if (g.sym(x + PlanGrid.dx(d), z + PlanGrid.dz(d)) != PlanGrid.SYM_PANELS) {
                    edgeLine(x, z, d, edge, 0, false);
                }
            }
        }

        void water(int x, int z, int col) {
            final int x0 = ox(x);
            final int y0 = oy(z);

            final int shallow = mix(shade(col, NORMAL), WATER_PALE, outdoors(x, z) ? 0.45f : 0.25f);

            final int f = g.has(x, z, PlanGrid.F_DEEP) ? mix(shallow, WATER_DEEP, 0.35f) : shallow;
            rect(x0, y0, x0 + s, y0 + s, f);
            final int hh = hash(x, z, 3);
            if (s >= SMALL && hh % 2 == 0) {
                final int wave = mix(f, 0x22448C, 0.45f);
                final int wx = x0 + 1 + hh % Math.max(1, s - 5);
                final int wy = y0 + 2 + (hh >> 3) % Math.max(1, s - 4);
                final int[][] curl = {{0, 1}, {1, 0}, {2, 0}, {3, 1}, {4, 1}};
                for (int[] p : curl) {
                    if (p[0] < s - 1) {
                        put(wx + p[0], wy + p[1], wave);
                    }
                }
            }
            for (int d = 0; d < 4; d++) {
                if (g.kind(x + PlanGrid.dx(d), z + PlanGrid.dz(d)) != PlanGrid.WATER) {
                    edgeLine(x, z, d, dark(f, 0.8f), 0, false);
                }
            }
        }

        void lava(int x, int z) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            rect(x0, y0, x0 + s, y0 + s, 0xD8621C);
            for (int n = 0; n < Math.max(1, s / 3); n++) {
                final int k = hash(x, z, 11 + n);
                put(x0 + k % s, y0 + (k >> 5) % s, 0xFFC040);
            }
            for (int d = 0; d < 4; d++) {
                if (g.kind(x + PlanGrid.dx(d), z + PlanGrid.dz(d)) != PlanGrid.LAVA) {
                    edgeLine(x, z, d, 0x5A1A08, 0, false);
                }
            }
        }

        void hole(int x, int z, int col) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final boolean deep = g.has(x, z, PlanGrid.F_DEEP) || col < 0;
            final int f = col < 0 ? VOID : mix(shade(col, deep ? LOWEST : LOW), PAPER, 0.10f);
            rect(x0, y0, x0 + s, y0 + s, f);
            if (s >= SMALL && deep) {
                final int hatch = dark(f, 0.72f);
                for (int j = 0; j < s; j++) {
                    for (int i = 0; i < s; i++) {
                        if (((x0 + i) - (y0 + j)) % 4 == 0) {
                            put(x0 + i, y0 + j, hatch);
                        }
                    }
                }
            }
            for (int d = 0; d < 4; d++) {
                final byte n = g.kind(x + PlanGrid.dx(d), z + PlanGrid.dz(d));
                if (n != PlanGrid.HOLE && n != PlanGrid.STAIRS_BELOW && n != PlanGrid.NONE) {
                    edgeLine(x, z, d, dark(f, 0.55f), 0, false);
                }
            }
        }

        void foliage(int x, int z, int col) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int f = mix(shade(col, NORMAL), PAPER, outdoors(x, z) ? 0.40f : 0.20f);
            rect(x0, y0, x0 + s, y0 + s, f);
            final int leaf = dark(f, 0.78f);
            for (int n = 0; n < Math.max(2, s / 2); n++) {
                final int k = hash(x, z, 21 + n);
                put(x0 + k % s, y0 + (k >> 6) % s, leaf);
            }
            for (int d = 0; d < 4; d++) {
                if (g.kind(x + PlanGrid.dx(d), z + PlanGrid.dz(d)) != PlanGrid.FOLIAGE) {
                    edgeLine(x, z, d, dark(f, 0.66f), 0, true);
                }
            }
        }

        void rugs() {
            if (s < SMALL) {
                return;
            }
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    final int rug = g.accent(x, z);
                    final byte k = g.kind(x, z);
                    if (rug < 0 || (k != PlanGrid.FLOOR && k != PlanGrid.GROUND) || g.sym(x, z) != PlanGrid.SYM_NONE) {
                        continue;
                    }
                    final int line = dark(get(ox(x) + s / 2, oy(z) + s / 2), 0.72f);
                    for (int d = 0; d < 4; d++) {
                        final int nx = x + PlanGrid.dx(d);
                        final int nz = z + PlanGrid.dz(d);
                        if (g.accent(nx, nz) == rug && g.sym(nx, nz) == PlanGrid.SYM_NONE) {
                            continue;
                        }
                        for (int t = 0; t < s; t++) {
                            final int ax = ox(x) + (d == PlanGrid.WEST ? 1 : d == PlanGrid.EAST ? s - 2 : t);
                            final int ay = oy(z) + (d == PlanGrid.NORTH ? 1 : d == PlanGrid.SOUTH ? s - 2 : t);
                            if (((d == PlanGrid.NORTH || d == PlanGrid.SOUTH) ? ax : ay) % 3 != 0) {
                                put(ax, ay, line);
                            }
                        }
                    }
                }
            }
        }

        void furniture() {
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    final byte k = g.kind(x, z);
                    if (k == PlanGrid.RAISED) {
                        raised(x, z);
                    } else if ((k == PlanGrid.FLOOR || k == PlanGrid.GROUND) && g.sym(x, z) != PlanGrid.SYM_NONE) {
                        onFloor(x, z);
                    }
                }
            }
        }

        void raised(int x, int z) {
            final int col = g.colour(x, z);
            final byte sym = g.sym(x, z);
            final int top = topColour(x, z, col);
            final int line = edgeColour(col);
            for (int d = 0; d < 4; d++) {
                final int nx = x + PlanGrid.dx(d);
                final int nz = z + PlanGrid.dz(d);
                final byte n = g.kind(nx, nz);
                if (!samePiece(x, z, nx, nz) && !PlanGrid.isWallish(n)) {
                    edgeLine(x, z, d, line, 0, false);
                }
            }
            switch (sym) {
                case PlanGrid.SYM_BED -> bed(x, z, top, line);
                case PlanGrid.SYM_CHEST -> chest(x, z, top, line);
                case PlanGrid.SYM_BARREL -> barrel(x, z, top, line);
                case PlanGrid.SYM_SHELF -> shelf(x, z, top, line);
                case PlanGrid.SYM_SEAT -> seat(x, z, top, line);
                case PlanGrid.SYM_FURNACE -> furnace(x, z, line);
                case PlanGrid.SYM_TABLE -> table(x, z, top, line);
                case PlanGrid.SYM_HATCH -> hatch(x, z, line);
                case PlanGrid.SYM_POT -> pot(x, z);
                case PlanGrid.SYM_BENCH -> bench(x, z, top, line);
                case PlanGrid.SYM_ANVIL -> anvil(x, z);
                case PlanGrid.SYM_CAULDRON -> cauldron(x, z, top, line);
                case PlanGrid.SYM_LECTERN -> lectern(x, z, top, line);
                case PlanGrid.SYM_BOOK -> book(x, z);
                case PlanGrid.SYM_LANTERN -> lantern(x, z);
                case PlanGrid.SYM_CANDLE -> candle(x, z);
                default -> {
                }
            }
        }

        boolean samePiece(int x, int z, int nx, int nz) {
            if (g.kind(nx, nz) != PlanGrid.RAISED || g.colour(nx, nz) != g.colour(x, z)
                    || g.sym(nx, nz) != g.sym(x, z)) {
                return false;
            }
            final byte sym = g.sym(x, z);
            final byte d = g.dir(x, z);
            switch (sym) {
                case PlanGrid.SYM_NONE, PlanGrid.SYM_SHELF, PlanGrid.SYM_TABLE -> {
                    return true;
                }
                case PlanGrid.SYM_BED -> {
                    return d == g.dir(nx, nz)
                            && g.has(x, z, PlanGrid.F_HEAD) != g.has(nx, nz, PlanGrid.F_HEAD)
                            && nx - x == PlanGrid.dx(d) * (g.has(x, z, PlanGrid.F_HEAD) ? -1 : 1)
                            && nz - z == PlanGrid.dz(d) * (g.has(x, z, PlanGrid.F_HEAD) ? -1 : 1);
                }
                case PlanGrid.SYM_CHEST -> {
                    return g.has(x, z, PlanGrid.F_DOUBLE) && g.has(nx, nz, PlanGrid.F_DOUBLE) && d == g.dir(nx, nz);
                }
                case PlanGrid.SYM_SEAT -> {
                    return d >= 0 && d <= 3 && d == g.dir(nx, nz)
                            && ((d == PlanGrid.NORTH || d == PlanGrid.SOUTH) ? nz == z : nx == x);
                }
                default -> {
                    return false;
                }
            }
        }

        void onFloor(int x, int z) {
            switch (g.sym(x, z)) {
                case PlanGrid.SYM_POT -> pot(x, z);
                case PlanGrid.SYM_HATCH -> hatch(x, z, edgeColour(g.colour(x, z) < 0 ? 0x8F7748 : g.accent(x, z) >= 0
                        ? g.accent(x, z) : 0x8F7748));
                case PlanGrid.SYM_THIN -> thin(x, z);
                case PlanGrid.SYM_TABLE -> {
                    if (s >= SMALL) {
                        final int c = g.accent(x, z) >= 0 ? g.accent(x, z) : 0x8F7748;
                        final int x0 = ox(x);
                        final int y0 = oy(z);
                        rect(x0 + 1, y0 + 1, x0 + s - 1, y0 + s - 1, mix(get(x0 + 1, y0 + 1), shade(c, HIGH), 0.55f));
                        frame(x0 + 1, y0 + 1, x0 + s - 1, y0 + s - 1, shade(c, LOW));
                    }
                }
                case PlanGrid.SYM_CANDLE -> candle(x, z);
                case PlanGrid.SYM_LANTERN -> lantern(x, z);
                case PlanGrid.SYM_CROP -> crop(x, z);
                case PlanGrid.SYM_CANE -> cane(x, z);
                default -> {
                }
            }
        }

        void bed(int x, int z, int top, int line) {
            final byte d = g.dir(x, z);
            final boolean head = g.has(x, z, PlanGrid.F_HEAD);
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int blanket = top;
            final int sheet = 0xF0ECE4;
            if (head) {
                rect(x0 + (d == PlanGrid.WEST ? 0 : 1), y0 + (d == PlanGrid.NORTH ? 0 : 1),
                        x0 + s - (d == PlanGrid.EAST ? 0 : 1), y0 + s - (d == PlanGrid.SOUTH ? 0 : 1), sheet);
                if (s >= SMALL) {
                    final int p = Math.max(2, s / 3);
                    final int inset = s >= FULL ? 2 : 1;
                    int px0 = x0 + inset;
                    int py0 = y0 + inset;
                    int px1 = x0 + s - inset;
                    int py1 = y0 + s - inset;
                    switch (d) {
                        case PlanGrid.NORTH -> py1 = py0 + p;
                        case PlanGrid.SOUTH -> py0 = py1 - p;
                        case PlanGrid.WEST -> px1 = px0 + p;
                        default -> px0 = px1 - p;
                    }
                    rect(px0, py0, px1, py1, 0xFFFFFF);
                    frame(px0, py0, px1, py1, dark(sheet, 0.72f));
                } else {
                    edgeLine(x, z, d, 0xFFFFFF, 1, false);
                }

                edgeLine(x, z, PlanGrid.opposite(d), line, 0, false);
            } else {
                rect(x0 + 1, y0 + 1, x0 + s - 1, y0 + s - 1, blanket);
                if (s >= FULL) {
                    final int fold = mix(blanket, 0xFFFFFF, 0.25f);
                    final byte away = PlanGrid.opposite(d);
                    edgeLine(x, z, PlanGrid.opposite(away), fold, 1, false);
                }
            }
        }

        void chest(int x, int z, int top, int line) {
            final byte d = g.dir(x, z);
            final int x0 = ox(x);
            final int y0 = oy(z);
            if (s < SMALL) {
                edgeLine(x, z, d, line, 0, false);
                return;
            }

            final byte back = PlanGrid.opposite(d);
            final int t = Math.max(1, s / 3);
            for (int i = 1; i < s - 1; i++) {
                switch (back) {
                    case PlanGrid.NORTH -> put(x0 + i, y0 + t, line);
                    case PlanGrid.SOUTH -> put(x0 + i, y0 + s - 1 - t, line);
                    case PlanGrid.WEST -> put(x0 + t, y0 + i, line);
                    default -> put(x0 + s - 1 - t, y0 + i, line);
                }
            }
            if (!g.has(x, z, PlanGrid.F_DOUBLE) || latchHere(x, z)) {
                final int lx = d == PlanGrid.WEST ? x0 : d == PlanGrid.EAST ? x0 + s - 1 : x0 + s / 2
                        + (g.has(x, z, PlanGrid.F_DOUBLE) ? latchShift(x, z) : 0);
                final int ly = d == PlanGrid.NORTH ? y0 : d == PlanGrid.SOUTH ? y0 + s - 1 : y0 + s / 2
                        + (g.has(x, z, PlanGrid.F_DOUBLE) ? latchShift(x, z) : 0);
                put(lx, ly, 0x2A2A2A);
                if (s >= FULL) {
                    if (d == PlanGrid.NORTH || d == PlanGrid.SOUTH) {
                        put(lx - 1, ly, 0x2A2A2A);
                    } else {
                        put(lx, ly - 1, 0x2A2A2A);
                    }
                }
            }
        }

        boolean latchHere(int x, int z) {
            final byte d = g.dir(x, z);
            final byte left = (byte) ((d + 1) & 3);
            final int nx = x + PlanGrid.dx(left);
            final int nz = z + PlanGrid.dz(left);
            return !(g.sym(nx, nz) == PlanGrid.SYM_CHEST && g.has(nx, nz, PlanGrid.F_DOUBLE) && g.dir(nx, nz) == d);
        }

        int latchShift(int x, int z) {
            final byte d = g.dir(x, z);
            final byte right = (byte) ((d + 3) & 3);
            return (PlanGrid.dx(right) + PlanGrid.dz(right)) * (s / 2);
        }

        void barrel(int x, int z, int top, int line) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final byte d = g.dir(x, z);
            if (d == PlanGrid.UP || d == PlanGrid.DOWN || d == PlanGrid.NO_DIR) {
                ring(x0 + s / 2, y0 + s / 2, Math.max(1, (s - 2) / 2), line);
            } else if (s >= SMALL) {
                edgeLine(x, z, d, line, 1, false);
                final boolean alongX = d == PlanGrid.EAST || d == PlanGrid.WEST;
                for (int t : new int[] {s / 3, s - 1 - s / 3}) {
                    for (int i = 1; i < s - 1; i++) {
                        if (alongX) {
                            put(x0 + t, y0 + i, line);
                        } else {
                            put(x0 + i, y0 + t, line);
                        }
                    }
                }
            }
        }

        void shelf(int x, int z, int top, int line) {
            if (s < SMALL) {
                return;
            }
            final byte d = g.dir(x, z);
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int[] spines = {0xB02E26, 0x3C44AA, 0x5E7C16, 0xE0B030, 0x835432, 0x169C9C};
            final int depth = Math.max(2, s / 3);
            for (int t = 1; t < s - 1; t++) {
                final int c = spines[hash(x * 31 + t, z, 5) % spines.length];
                for (int k = 1; k <= depth; k++) {
                    switch (d) {
                        case PlanGrid.NORTH -> put(x0 + t, y0 + k - 1, (t % 2 == 0) ? c : dark(c, 0.8f));
                        case PlanGrid.SOUTH -> put(x0 + t, y0 + s - k, (t % 2 == 0) ? c : dark(c, 0.8f));
                        case PlanGrid.WEST -> put(x0 + k - 1, y0 + t, (t % 2 == 0) ? c : dark(c, 0.8f));
                        case PlanGrid.EAST -> put(x0 + s - k, y0 + t, (t % 2 == 0) ? c : dark(c, 0.8f));
                        default -> {
                            return;
                        }
                    }
                }
            }
        }

        void seat(int x, int z, int top, int line) {
            final byte d = g.dir(x, z);
            if (d < 0 || d > 3) {
                return;
            }
            final int back = Math.max(2, s / 3);
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int band = shade(g.colour(x, z), LOW);
            switch (d) {
                case PlanGrid.NORTH -> rect(x0, y0, x0 + s, y0 + back, band);
                case PlanGrid.SOUTH -> rect(x0, y0 + s - back, x0 + s, y0 + s, band);
                case PlanGrid.WEST -> rect(x0, y0, x0 + back, y0 + s, band);
                default -> rect(x0 + s - back, y0, x0 + s, y0 + s, band);
            }
            edgeLine(x, z, d, line, back - 1, false);
            if (s < SMALL) {
                return;
            }

            final byte along = (d == PlanGrid.NORTH || d == PlanGrid.SOUTH) ? PlanGrid.EAST : PlanGrid.SOUTH;
            final int nx = x + PlanGrid.dx(along);
            final int nz = z + PlanGrid.dz(along);
            if (samePiece(x, z, nx, nz)) {
                edgeLine(x, z, along, mix(top, line, 0.5f), 0, false);
            }
        }

        void table(int x, int z, int top, int line) {
            if (s < SMALL) {
                return;
            }
            final int rim = mix(top, line, 0.45f);
            for (int d = 0; d < 4; d++) {
                if (!samePiece(x, z, x + PlanGrid.dx(d), z + PlanGrid.dz(d))) {
                    edgeLine(x, z, d, rim, 1, false);
                }
            }
        }

        void furnace(int x, int z, int line) {
            final byte d = g.dir(x, z);
            if (d < 0 || d > 3 || s < SMALL) {
                return;
            }

            final int mouth = g.has(x, z, PlanGrid.F_LIT) ? 0xFF9A30 : 0x2A2A2A;
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int a = s / 4;
            final int b = s - s / 4;
            for (int t = a; t < b; t++) {
                switch (d) {
                    case PlanGrid.NORTH -> put(x0 + t, y0 + 1, mouth);
                    case PlanGrid.SOUTH -> put(x0 + t, y0 + s - 2, mouth);
                    case PlanGrid.WEST -> put(x0 + 1, y0 + t, mouth);
                    default -> put(x0 + s - 2, y0 + t, mouth);
                }
            }
        }

        void hatch(int x, int z, int line) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            frame(x0 + 1, y0 + 1, x0 + s - 1, y0 + s - 1, line);
            if (s >= SMALL) {
                for (int i = 2; i < s - 2; i++) {
                    put(x0 + i, y0 + s / 2, line);
                }
            }
        }

        void thin(int x, int z) {
            final byte d = g.dir(x, z);
            if (d < 0 || d > 3) {
                return;
            }
            final int c = shade(g.accent(x, z) >= 0 ? g.accent(x, z) : 0x8F7748, LOW);
            edgeLine(x, z, d, c, 0, false);
            if (s >= FULL) {
                edgeLine(x, z, d, c, 1, false);
            }
        }

        void pot(int x, int z) {
            final int cx = ox(x) + s / 2;
            final int cy = oy(z) + s / 2;
            final int r = Math.max(1, s / 3);
            final int terra = 0xA8623C;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy <= r * r + r) {
                        put(cx + dx, cy + dy, terra);
                    }
                }
            }
            for (int[] p : quarter(r)) {
                put(cx + p[0], cy + p[1], 0x5A2E18);
                put(cx - p[0], cy + p[1], 0x5A2E18);
                put(cx + p[0], cy - p[1], 0x5A2E18);
                put(cx - p[0], cy - p[1], 0x5A2E18);
            }
            final int leaf = g.accent(x, z) >= 0 ? shade(g.accent(x, z), HIGH) : 0x4E7A22;
            put(cx, cy, leaf);
            if (s >= SMALL) {
                put(cx - 1, cy, leaf);
                put(cx, cy - 1, 0x4E7A22);
                put(cx + 1, cy - 1, 0x4E7A22);
            }
        }

        void bench(int x, int z, int top, int line) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int c = mix(top, line, 0.7f);
            if (s < SMALL) {
                put(x0 + s / 2, y0 + s / 2, line);
                return;
            }
            frame(x0 + 1, y0 + 1, x0 + s - 1, y0 + s - 1, c);
            if (s >= 12) {
                for (int k = 1; k < 3; k++) {
                    final int t = 1 + Math.round(k * (s - 3) / 3f);
                    for (int i = 1; i < s - 1; i++) {
                        put(x0 + t, y0 + i, c);
                        put(x0 + i, y0 + t, c);
                    }
                }
            } else if (s >= FULL) {
                for (int i = 1; i < s - 1; i++) {
                    put(x0 + s / 2, y0 + i, c);
                    put(x0 + i, y0 + s / 2, c);
                }
            } else {
                put(x0 + s / 2, y0 + s / 2, c);
            }
        }

        void anvil(int x, int z) {
            final byte d = g.dir(x, z);
            final boolean alongX = d != PlanGrid.EAST && d != PlanGrid.WEST;
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int iron = 0x3F3F3F;
            final int lit = 0x8C8C8C;
            if (s < SMALL) {
                for (int t = 0; t < s; t++) {
                    put(alongX ? x0 + t : x0 + s / 2, alongX ? y0 + s / 2 : y0 + t, iron);
                }
                return;
            }
            final int a = Math.max(1, s * 5 / 16);
            final int b = s - a;
            for (int t = 1; t < s - 1; t++) {
                for (int k = a; k < b; k++) {
                    put(alongX ? x0 + t : x0 + k, alongX ? y0 + k : y0 + t, k == a ? lit : iron);
                }
            }
        }

        void cauldron(int x, int z, int top, int line) {
            final int cx = ox(x) + s / 2;
            final int cy = oy(z) + s / 2;
            final int held = g.accent(x, z);
            final int inside = held >= 0 ? shade(held, NORMAL) : dark(top, 0.55f);
            final int r = Math.max(1, (s - 2) / 2);
            if (s < SMALL) {
                disc(cx, cy, Math.max(1, r), inside);
                return;
            }
            disc(cx, cy, r, inside);
            ring(cx, cy, r, line);
        }

        void lectern(int x, int z, int top, int line) {
            final byte d = g.dir(x, z);
            final int x0 = ox(x);
            final int y0 = oy(z);
            if (s < SMALL) {
                if (d >= 0 && d <= 3) {
                    edgeLine(x, z, d, line, 1, false);
                }
                return;
            }
            final int in = s >= FULL ? 2 : 1;
            final int page = mix(top, 0xFFFFFF, 0.6f);
            rect(x0 + in, y0 + in, x0 + s - in, y0 + s - in, page);
            if (d >= 0 && d <= 3) {
                edgeLine(x, z, d, line, in, false);
            }
            if (s >= FULL) {
                final int spine = dark(page, 0.75f);
                if (d == PlanGrid.NORTH || d == PlanGrid.SOUTH) {
                    for (int j = in; j < s - in; j++) {
                        put(x0 + s / 2, y0 + j, spine);
                    }
                } else {
                    for (int i = in; i < s - in; i++) {
                        put(x0 + i, y0 + s / 2, spine);
                    }
                }
            }
        }

        void book(int x, int z) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int page = 0xF2ECDC;
            final int spine = 0xB02E26;
            if (s < SMALL) {
                put(x0 + s / 2, y0 + s / 2, page);
                return;
            }
            final int in = s >= FULL ? Math.max(2, s / 4) : 1;
            rect(x0 + in, y0 + in, x0 + s - in, y0 + s - in, page);
            if (s >= FULL) {
                frame(x0 + in, y0 + in, x0 + s - in, y0 + s - in, dark(page, 0.8f));
            }
            for (int j = in; j < s - in; j++) {
                put(x0 + s / 2 - (s % 2 == 0 ? 1 : 0), y0 + j, spine);
            }
        }

        void candle(int x, int z) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int c = g.accent(x, z) >= 0 ? shade(g.accent(x, z), HIGH) : 0xE8E0C8;
            final int cx = x0 + s / 2 - (s % 2 == 0 ? 1 : 0);
            final int cy = y0 + s / 2;
            put(cx, cy, c);
            if (s >= FULL) {
                put(cx + 1, cy, c);
                put(cx, cy + 1, dark(c, 0.8f));
                put(cx + 1, cy + 1, dark(c, 0.8f));
            }
            if (g.has(x, z, PlanGrid.F_LIT)) {
                put(cx, cy - 1, FLAME);
                if (s >= FULL) {
                    put(cx + 1, cy - 1, FLAME);
                }
                if (s >= 12) {
                    put(cx, cy - 2, dark(FLAME, 0.85f));
                    put(cx + 1, cy - 2, dark(FLAME, 0.85f));
                }
            }
        }

        void lantern(int x, int z) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int glow = g.accent(x, z) >= 0 ? shade(g.accent(x, z), HIGH) : 0xFFC85A;
            final int cx = x0 + s / 2 - (s % 2 == 0 ? 1 : 0);
            final int cy = y0 + s / 2 - (s % 2 == 0 ? 1 : 0);
            if (s < SMALL) {
                rect(cx, cy, cx + 2, cy + 2, glow);
                return;
            }
            final int half = s >= FULL ? 2 : 1;
            rect(cx - half, cy - half, cx + half + 1, cy + half + 1, 0x2A2A2A);
            rect(cx - half + 1, cy - half + 1, cx + half, cy + half, glow);
            put(cx, cy, mix(glow, 0xFFFFFF, 0.5f));
        }

        void crop(int x, int z) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int c = g.accent(x, z) >= 0 ? shade(g.accent(x, z), HIGH) : 0x4E9A2A;
            final int pitch = g.has(x, z, PlanGrid.F_GROWN) ? 2 : 3;
            for (int j = 1; j < s; j += 2) {
                for (int i = (j / 2) % pitch; i < s; i += pitch) {
                    put(x0 + i, y0 + j, c);
                }
            }
        }

        void cane(int x, int z) {
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int c = g.accent(x, z) >= 0 ? shade(g.accent(x, z), HIGH) : 0x8FBF4A;
            final int joint = dark(c, 0.7f);
            final int[] xs = s >= FULL ? new int[] {s / 4, s / 2, s - 1 - s / 4} : new int[] {s / 3, s - 1 - s / 3};
            for (int sx : xs) {
                for (int j = 1; j < s - 1; j++) {
                    put(x0 + sx, y0 + j, j % 3 == 0 ? joint : c);
                }
            }
        }

        void stairs() {
            final Map<Long, Boolean> done = new HashMap<>();
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    final byte k = g.kind(x, z);
                    if ((k != PlanGrid.STAIRS && k != PlanGrid.STAIRS_BELOW) || done.containsKey(pack(x, z))) {
                        continue;
                    }
                    final byte up = g.dir(x, z);
                    if (up < 0 || up > 3) {
                        continue;
                    }

                    final ArrayDeque<Long> q = new ArrayDeque<>();
                    q.add(pack(x, z));
                    done.put(pack(x, z), true);
                    int minX = x;
                    int maxX = x;
                    int minZ = z;
                    int maxZ = z;
                    while (!q.isEmpty()) {
                        final long p = q.poll();
                        final int cx = unpackX(p);
                        final int cz = unpackZ(p);
                        minX = Math.min(minX, cx);
                        maxX = Math.max(maxX, cx);
                        minZ = Math.min(minZ, cz);
                        maxZ = Math.max(maxZ, cz);
                        for (int d = 0; d < 4; d++) {
                            final int nx = cx + PlanGrid.dx(d);
                            final int nz = cz + PlanGrid.dz(d);
                            if (g.kind(nx, nz) == k && g.dir(nx, nz) == up && !done.containsKey(pack(nx, nz))) {
                                done.put(pack(nx, nz), true);
                                q.add(pack(nx, nz));
                            }
                        }
                    }
                    flight(k, up, minX, minZ, maxX, maxZ);
                }
            }
        }

        void flight(byte k, byte up, int minX, int minZ, int maxX, int maxZ) {
            final int x0 = ox(minX);
            final int y0 = oy(minZ);
            final int x1 = ox(maxX + 1);
            final int y1 = oy(maxZ + 1);
            final int base = get(x0 + s / 2, y0 + s / 2);
            final int tread = dark(base, 0.62f);
            final int arrow = dark(base, 0.42f);
            final int step = Math.max(2, s / 2);
            final boolean alongX = up == PlanGrid.EAST || up == PlanGrid.WEST;
            if (alongX) {
                for (int x = x0; x < x1; x += step) {
                    for (int y = y0 + 1; y < y1 - 1; y++) {
                        put(x, y, tread);
                    }
                }
            } else {
                for (int y = y0; y < y1; y += step) {
                    for (int x = x0 + 1; x < x1 - 1; x++) {
                        put(x, y, tread);
                    }
                }
            }

            final byte way = k == PlanGrid.STAIRS ? up : PlanGrid.opposite(up);
            final int n = Math.max(1, Math.min(3, (alongX ? (y1 - y0) : (x1 - x0)) / 3));
            if (alongX) {
                final int my = (y0 + y1) / 2;
                final int a = x0 + 2;
                final int b = x1 - 3;
                for (int x = a; x <= b; x++) {
                    put(x, my, arrow);
                }
                final int tip = way == PlanGrid.EAST ? b : a;
                final int back = way == PlanGrid.EAST ? -1 : 1;
                for (int i = 1; i <= n; i++) {
                    put(tip + back * i, my - i, arrow);
                    put(tip + back * i, my + i, arrow);
                }
            } else {
                final int mx = (x0 + x1) / 2;
                final int a = y0 + 2;
                final int b = y1 - 3;
                for (int y = a; y <= b; y++) {
                    put(mx, y, arrow);
                }
                final int tip = way == PlanGrid.SOUTH ? b : a;
                final int back = way == PlanGrid.SOUTH ? -1 : 1;
                for (int i = 1; i <= n; i++) {
                    put(mx - i, tip + back * i, arrow);
                    put(mx + i, tip + back * i, arrow);
                }
            }

            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int d = 0; d < 4; d++) {
                        final byte nb = g.kind(x + PlanGrid.dx(d), z + PlanGrid.dz(d));
                        if (nb != k && !PlanGrid.isWallish(nb)) {
                            edgeLine(x, z, d, tread, 0, false);
                        }
                    }
                }
            }
        }

        void shadows() {
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    final byte k = g.kind(x, z);
                    if (k != PlanGrid.FLOOR && k != PlanGrid.GROUND && k != PlanGrid.STAIRS) {
                        continue;
                    }
                    final int x0 = ox(x);
                    final int y0 = oy(z);
                    if (g.has(x, z, PlanGrid.F_DEEP)) {
                        for (int d = 0; d < 4; d++) {
                            final int nx = x + PlanGrid.dx(d);
                            final int nz = z + PlanGrid.dz(d);
                            final byte nk = g.kind(nx, nz);
                            if ((nk == PlanGrid.FLOOR || nk == PlanGrid.GROUND) && g.has(nx, nz, PlanGrid.F_DEEP)
                                    && g.dir(nx, nz) != g.dir(x, z)) {
                                edgeLine(x, z, d, dark(get(x0 + s / 2, y0 + s / 2), 0.86f), 0, false);
                            }
                        }
                        continue;
                    }
                    if (g.kind(x, z - 1) == PlanGrid.WALL || g.kind(x, z - 1) == PlanGrid.WINDOW) {
                        for (int i = 0; i < s; i++) {
                            put(x0 + i, y0, dark(get(x0 + i, y0), 0.82f));
                        }
                    }
                    if (g.kind(x - 1, z) == PlanGrid.WALL || g.kind(x - 1, z) == PlanGrid.WINDOW) {
                        for (int j = 0; j < s; j++) {
                            put(x0, y0 + j, dark(get(x0, y0 + j), 0.82f));
                        }
                    }
                }
            }
        }

        void canopy() {
            final int a = 0x2A;
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    if (!g.has(x, z, PlanGrid.F_CANOPY) || g.kind(x, z) == PlanGrid.NONE) {
                        continue;
                    }
                    final int x0 = ox(x);
                    final int y0 = oy(z);
                    for (int j = 0; j < s; j++) {
                        for (int i = 0; i < s; i++) {
                            blend(x0 + i, y0 + j, CANOPY, a);
                        }
                    }
                    final int edge = dark(get(x0 + s / 2, y0 + s / 2), 0.7f);
                    for (int d = 0; d < 4; d++) {
                        final int nx = x + PlanGrid.dx(d);
                        final int nz = z + PlanGrid.dz(d);
                        if (!g.has(nx, nz, PlanGrid.F_CANOPY) && g.kind(nx, nz) != PlanGrid.NONE) {
                            edgeLine(x, z, d, edge, 0, true);
                        }
                    }
                }
            }
        }

        void cast() {
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    if (g.kind(x, z) != PlanGrid.LAVA) {
                        continue;
                    }
                    for (int d = 0; d < 4; d++) {
                        final int nx = x + PlanGrid.dx(d);
                        final int nz = z + PlanGrid.dz(d);
                        final byte n = g.kind(nx, nz);
                        if (n == PlanGrid.LAVA || n == PlanGrid.NONE) {
                            continue;
                        }
                        final int band = Math.max(1, s / 3);
                        for (int k = 0; k < band; k++) {
                            edgeBlend(nx, nz, PlanGrid.opposite((byte) d), LAVA_CAST, k, 0x50 - 0x50 * k / band);
                        }
                    }
                }
            }
        }

        void structure() {
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    switch (g.kind(x, z)) {
                        case PlanGrid.WALL -> wall(x, z);
                        case PlanGrid.WINDOW -> window(x, z);
                        case PlanGrid.DOOR -> door(x, z);
                        case PlanGrid.GATE -> gate(x, z);
                        case PlanGrid.FENCE -> fence(x, z);
                        case PlanGrid.POST -> post(x, z);
                        case PlanGrid.LADDER -> ladder(x, z);
                        default -> {
                        }
                    }
                }
            }
        }

        void wall(int x, int z) {
            final int col = g.colour(x, z);
            final boolean natural = g.has(x, z, PlanGrid.F_NATURAL);
            final int edge = natural ? dark(topColour(x, z, col), 0.62f) : edgeColour(col);
            for (int d = 0; d < 4; d++) {
                final int nx = x + PlanGrid.dx(d);
                final int nz = z + PlanGrid.dz(d);
                final byte n = g.kind(nx, nz);
                final boolean sameMass = natural
                        ? (n == PlanGrid.WALL && g.has(nx, nz, PlanGrid.F_NATURAL))
                        : n == PlanGrid.WALL || n == PlanGrid.WINDOW || n == PlanGrid.POST;
                if (!sameMass && n != PlanGrid.NONE) {
                    edgeLine(x, z, d, edge, 0, false);
                }
            }
        }

        void window(int x, int z) {
            final int col = g.colour(x, z) >= 0 ? g.colour(x, z) : 0x707070;
            final int frame = edgeColour(col);
            final boolean alongZ = g.has(x, z, PlanGrid.F_AXIS_Z);
            final boolean bars = g.has(x, z, PlanGrid.F_BARS);
            final int tint = g.accent(x, z);
            final int pane = bars ? floorUnder(x, z) : tint >= 0 ? mix(GLASS, shade(tint, HIGH), 0.55f) : GLASS;
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int mid = s / 2 - (s % 2 == 0 && s >= 12 ? 1 : 0);
            for (int j = 0; j < s; j++) {
                for (int i = 0; i < s; i++) {
                    final int across = alongZ ? i : j;
                    final int along = alongZ ? j : i;
                    final int c;
                    if (across == 0 || across == s - 1) {
                        c = frame;
                    } else if (bars) {
                        c = across == mid && along % 2 == 0 ? IRON : pane;
                    } else if (across == s / 2 && s >= 5) {
                        c = frame;
                    } else {
                        c = pane;
                    }
                    put(x0 + i, y0 + j, c);
                }
            }
        }

        // read from the door's own shapes so modded doors swing right
        void door(int x, int z) {
            final byte closed = g.closedEdge(x, z);
            final byte open = g.openEdge(x, z);
            final int col = g.colour(x, z) < 0 ? 0x8F7748 : g.colour(x, z);
            if (closed == open || closed == PlanGrid.opposite(open)) {
                leaf(x, z, closed, col);
                return;
            }
            final int x0 = ox(x);
            final int y0 = oy(z);

            final int hx = (closed == PlanGrid.EAST || open == PlanGrid.EAST) ? x0 + s - 1 : x0;
            final int hy = (closed == PlanGrid.SOUTH || open == PlanGrid.SOUTH) ? y0 + s - 1 : y0;
            final int sx = hx == x0 ? 1 : -1;
            final int sy = hy == y0 ? 1 : -1;
            final int r = s - 1;

            final int swing = dark(floorUnder(x, z), 0.55f);
            for (int[] p : quarter(r)) {
                put(hx + sx * p[0], hy + sy * p[1], swing);
            }
            leaf(x, z, g.has(x, z, PlanGrid.F_OPEN) ? open : closed, col);
        }

        void leaf(int x, int z, byte edge, int col) {
            final int lw = leafWidth();
            final int light = shade(col, HIGH);
            final int darkTone = shade(col, LOWEST);
            if (lw == 1) {
                edgeLine(x, z, edge, luma(floorUnder(x, z)) > 128 ? darkTone : light, 0, false);
                return;
            }
            for (int t = 0; t < lw; t++) {
                edgeLine(x, z, edge, t == 0 ? darkTone : light, t, false);
            }
        }

        int leafWidth() {
            return s >= 12 ? 3 : s >= FULL ? 2 : 1;
        }

        void gate(int x, int z) {
            final boolean alongZ = g.has(x, z, PlanGrid.F_AXIS_Z);
            final int c = shade(g.colour(x, z) < 0 ? 0x8F7748 : g.colour(x, z), LOW);
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int mid = s / 2 - (s >= FULL ? 1 : 0);
            final int lw = s >= FULL ? 2 : 1;
            if (!g.has(x, z, PlanGrid.F_OPEN)) {
                for (int t = 0; t < s; t++) {
                    for (int k = 0; k < lw; k++) {
                        if (alongZ) {
                            put(x0 + mid + k, y0 + t, c);
                        } else {
                            put(x0 + t, y0 + mid + k, c);
                        }
                    }
                }
            } else {
                final int len = Math.max(2, s / 3);
                for (int t = 0; t < len; t++) {
                    for (int k = 0; k < lw; k++) {
                        if (alongZ) {
                            put(x0 + mid + k, y0 + t, c);
                            put(x0 + mid + k, y0 + s - 1 - t, c);
                        } else {
                            put(x0 + t, y0 + mid + k, c);
                            put(x0 + s - 1 - t, y0 + mid + k, c);
                        }
                    }
                }
            }
        }

        void fence(int x, int z) {
            final int col = g.colour(x, z) < 0 ? 0x8F7748 : g.colour(x, z);
            final int c = col == GLASS ? dark(GLASS, 0.75f) : shade(col, LOW);
            final int lw = s >= FULL ? 2 : 1;
            final int x0 = ox(x);
            final int y0 = oy(z);
            final int m = s / 2 - lw / 2;
            rect(x0 + m - (s >= FULL ? 1 : 0), y0 + m - (s >= FULL ? 1 : 0), x0 + m + lw + (s >= FULL ? 1 : 0),
                    y0 + m + lw + (s >= FULL ? 1 : 0), c);
            final int f = g.flags(x, z);
            if ((f & PlanGrid.F_N) != 0) {
                rect(x0 + m, y0, x0 + m + lw, y0 + m, c);
            }
            if ((f & PlanGrid.F_S) != 0) {
                rect(x0 + m, y0 + m + lw, x0 + m + lw, y0 + s, c);
            }
            if ((f & PlanGrid.F_W) != 0) {
                rect(x0, y0 + m, x0 + m, y0 + m + lw, c);
            }
            if ((f & PlanGrid.F_E) != 0) {
                rect(x0 + m + lw, y0 + m, x0 + s, y0 + m + lw, c);
            }
        }

        void post(int x, int z) {
            final int col = g.colour(x, z) < 0 ? 0x8F7748 : g.colour(x, z);
            final int fill = topColour(x, z, col);
            final int ringColour = edgeColour(col);
            final int cx = ox(x) + s / 2;
            final int cy = oy(z) + s / 2;
            final int r = Math.max(1, s / 2 - 1);
            disc(cx, cy, r, fill);
            ring(cx, cy, r, ringColour);
        }

        void ladder(int x, int z) {
            final byte facing = g.dir(x, z);
            if (facing < 0 || facing > 3) {
                return;
            }
            final byte wall = PlanGrid.opposite(facing);
            final int c = shade(g.colour(x, z) < 0 ? 0x8F7748 : g.colour(x, z), LOW);
            final int depth = Math.max(2, s / 3);
            final int x0 = ox(x);
            final int y0 = oy(z);
            for (int t = 1; t < s - 1; t++) {
                final boolean rail = t == 1 || t == s - 2;
                final boolean rung = t % 2 == 0;
                for (int k = 0; k < depth; k++) {
                    if (!rail && !(rung && k == depth / 2)) {
                        continue;
                    }
                    switch (wall) {
                        case PlanGrid.NORTH -> put(x0 + t, y0 + k, c);
                        case PlanGrid.SOUTH -> put(x0 + t, y0 + s - 1 - k, c);
                        case PlanGrid.WEST -> put(x0 + k, y0 + t, c);
                        default -> put(x0 + s - 1 - k, y0 + t, c);
                    }
                }
            }
        }

        boolean passes(int x, int z) {
            final byte k = g.kind(x, z);
            if (PlanGrid.stopsLight(k)) {
                return false;
            }
            return k != PlanGrid.DOOR || g.has(x, z, PlanGrid.F_OPEN);
        }

        // eight ways so light rounds a table, never through walls or diagonal corner squeezes
        Map<Long, Integer> reach(long[] cells) {
            final Map<Long, Integer> depth = new HashMap<>();
            final ArrayDeque<Long> q = new ArrayDeque<>();
            for (long p : cells) {
                depth.put(p, 0);
                q.add(p);
            }
            while (!q.isEmpty()) {
                final long p = q.poll();
                final int d = depth.get(p);
                if (d >= REACH) {
                    continue;
                }
                final int x = unpackX(p);
                final int z = unpackZ(p);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        final int nx = x + dx;
                        final int nz = z + dz;
                        if (!passes(nx, nz)) {
                            continue;
                        }
                        if (dx != 0 && dz != 0 && !passes(x + dx, z) && !passes(x, z + dz)) {
                            continue;
                        }
                        final long n = pack(nx, nz);
                        if (depth.containsKey(n)) {
                            continue;
                        }
                        depth.put(n, d + 1);
                        q.add(n);
                    }
                }
            }
            return depth;
        }

        void pools(List<Lamp> lamps) {
            if (lamps == null || lamps.isEmpty()) {
                return;
            }
            final byte[] alpha = new byte[w * h];
            final int[] tint = new int[w * h];
            for (Lamp lamp : lamps) {
                if (lamp.cells().length == 0) {
                    continue;
                }
                final float level = Math.max(0f, Math.min(1f, lamp.level()));
                final float radius = poolRadius(level) * s;
                final float strength = POOL_FLOOR + (1f - POOL_FLOOR) * level;
                final Map<Long, Integer> allowed = reach(lamp.cells());
                final int reachPx = (int) Math.ceil(radius) + 1;
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;
                final float[] centres = new float[lamp.cells().length * 2];
                for (int i = 0; i < lamp.cells().length; i++) {
                    final int cx = ox(unpackX(lamp.cells()[i]));
                    final int cy = oy(unpackZ(lamp.cells()[i]));
                    centres[2 * i] = cx + s / 2f;
                    centres[2 * i + 1] = cy + s / 2f;
                    minX = Math.min(minX, cx - reachPx);
                    maxX = Math.max(maxX, cx + s + reachPx);
                    minY = Math.min(minY, cy - reachPx);
                    maxY = Math.max(maxY, cy + s + reachPx);
                }
                for (int y = Math.max(0, minY); y < Math.min(h, maxY); y++) {
                    final int bz = g.z0 + Math.floorDiv(y, s);
                    for (int x = Math.max(0, minX); x < Math.min(w, maxX); x++) {
                        final int bx = g.x0 + Math.floorDiv(x, s);
                        if (!allowed.containsKey(pack(bx, bz)) || !passes(bx, bz)) {
                            continue;
                        }
                        float best = Float.MAX_VALUE;
                        for (int i = 0; i < centres.length; i += 2) {
                            final float dx = x + 0.5f - centres[i];
                            final float dy = y + 0.5f - centres[i + 1];
                            best = Math.min(best, dx * dx + dy * dy);
                        }
                        final int a = poolAlpha((float) Math.sqrt(best) / radius, strength);
                        final int i = y * w + x;
                        if (a > (alpha[i] & 0xFF)) {
                            alpha[i] = (byte) a;
                            tint[i] = lamp.tint();
                        }
                    }
                }
            }
            for (int i = 0; i < alpha.length; i++) {
                final int a = alpha[i] & 0xFF;
                if (a != 0) {
                    blend(i % w, i / w, tint[i], a);
                }
            }
        }

        void darkenByLight() {
            for (int z = g.z0; z < g.z0 + g.rows; z++) {
                for (int x = g.x0; x < g.x0 + g.cols; x++) {
                    final float k = 0.3f + 0.7f * g.light(x, z) / 15f;
                    if (k >= 0.999f) {
                        continue;
                    }
                    final int x0 = ox(x);
                    final int y0 = oy(z);
                    for (int j = 0; j < s; j++) {
                        for (int i = 0; i < s; i++) {
                            final int idx = (y0 + j) * w + x0 + i;
                            if ((px[idx] >>> 24) != 0) {
                                px[idx] = 0xFF000000 | dark(px[idx] & 0xFFFFFF, k);
                            }
                        }
                    }
                }
            }
        }
    }
}
