package com.createstats.gui.home;

public final class WireStyle {
    private WireStyle() {
    }

    @FunctionalInterface
    public interface Ink {
        void fill(int x0, int y0, int x1, int y1, int argb);
    }

    public static final int COPPER = 0xFFB86B34;
    public static final int COPPER_DARK = 0xFF6A3A1A;

    public static final int COPPER_PALE = 0xFFCFA27A;

    public static final int CORE = 0xFFF0E0C8;

    public static final int CASING = 0xFFFFFFFF;

    public static final int HEAVY_FROM = 1000;
    public static final int MEDIUM_FROM = 100;

    public static int width(int flow, int scale) {
        final int byLoad = flow >= HEAVY_FROM ? 4 : flow >= MEDIUM_FROM ? 3 : 2;
        return Math.min(byLoad, Math.max(2, scale / 2));
    }

    public static int casing(int scale) {
        return width(HEAVY_FROM, scale) + 2;
    }

    public static int colour(int flow) {
        return flow < 0 ? COPPER_PALE : COPPER;
    }

    public static void segment(Ink ink, int ax, int ay, int bx, int by, int w, int argb) {
        final int x0 = Math.min(ax, bx);
        final int x1 = Math.max(ax, bx);
        final int y0 = Math.min(ay, by);
        final int y1 = Math.max(ay, by);
        final int lo = -(w / 2);
        ink.fill(x0 + lo, y0 + lo, x1 + lo + w, y1 + lo + w, argb);
    }

    public static void dotted(Ink ink, int ax, int ay, int bx, int by, int argb) {
        final int x0 = Math.min(ax, bx);
        final int x1 = Math.max(ax, bx);
        final int y0 = Math.min(ay, by);
        final int y1 = Math.max(ay, by);
        for (int x = x0; x <= x1; x += 2) {
            for (int y = y0; y <= y1; y += 2) {
                ink.fill(x, y, x + 1, y + 1, argb);
            }
        }
    }

    public static void chevron(Ink ink, int x, int y, int dx, int dy, int w, int argb) {
        final int off = (w - 1) / 2;
        for (int k = 0; k < 3; k++) {
            if (dx != 0) {
                final int px = x - dx * k;
                ink.fill(px, y - k - 1 - off, px + 1, y - k - off, argb);
                ink.fill(px, y + k + 1 + off, px + 1, y + k + 2 + off, argb);
            } else {
                final int py = y - dy * k;
                ink.fill(x - k - 1 - off, py, x - k - off, py + 1, argb);
                ink.fill(x + k + 1 + off, py, x + k + 2 + off, py + 1, argb);
            }
        }
    }

    public static void junction(Ink ink, int x, int y, int w) {
        final int r = w / 2 + 1;
        ink.fill(x - r, y - r, x + r + 1, y + r + 1, COPPER_DARK);
        ink.fill(x - r + 1, y - r + 1, x + r, y + r, COPPER);
    }

    public static void riser(Ink ink, int x, int y, int w) {
        final int r = w / 2 + 1;
        ink.fill(x - r, y - r, x + r + 1, y + r + 1, COPPER_DARK);
        ink.fill(x - r + 1, y - r + 1, x + r, y + r, CORE);
        ink.fill(x, y, x + 1, y + 1, COPPER_DARK);
    }

    public static void entry(Ink ink, int x, int y, int w) {
        final int r = w / 2 + 2;
        ink.fill(x - r, y - r, x + r + 1, y + r + 1, COPPER_DARK);
        ink.fill(x - r + 1, y - r + 1, x + r, y + r, COPPER);
        ink.fill(x - r + 2, y - r + 2, x + r - 1, y + r - 1, CORE);
    }

    public static void cap(Ink ink, int x, int y, int w) {
        final int r = w / 2;
        ink.fill(x - r, y - r, x + r + 1, y + r + 1, COPPER_DARK);
    }
}
