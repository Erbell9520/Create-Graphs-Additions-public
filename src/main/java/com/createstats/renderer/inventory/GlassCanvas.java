package com.createstats.renderer.inventory;

import com.createstats.renderer.BitmapFont;

// int array so the port can be checked offline against the mock
public final class GlassCanvas {
    public final int width;
    public final int height;
    public final int[] px;

    public GlassCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.px = new int[width * height];
    }

    public int get(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : px[y * width + x];
    }

    public void put(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            px[y * width + x] = argb;
        }
    }

    public void fill(int x0, int y0, int x1, int y1, int argb) {
        final int ax = Math.max(0, x0);
        final int ay = Math.max(0, y0);
        final int bx = Math.min(width, x1);
        final int by = Math.min(height, y1);
        for (int y = ay; y < by; y++) {
            final int row = y * width;
            for (int x = ax; x < bx; x++) {
                px[row + x] = argb;
            }
        }
    }

    public void blend(int x0, int y0, int x1, int y1, int argb) {
        final int ax = Math.max(0, x0);
        final int ay = Math.max(0, y0);
        final int bx = Math.min(width, x1);
        final int by = Math.min(height, y1);
        for (int y = ay; y < by; y++) {
            for (int x = ax; x < bx; x++) {
                px[y * width + x] = over(px[y * width + x], argb);
            }
        }
    }

    public static int over(int dst, int src) {
        final int sa = src >>> 24;
        if (sa == 255) {
            return src;
        }
        if (sa == 0) {
            return dst;
        }
        final int da = dst >>> 24;
        final float a = sa / 255.0f;
        final float oa = a + (da / 255.0f) * (1.0f - a);
        if (oa <= 0.0f) {
            return 0;
        }
        final int r = Math.round((((src >> 16) & 255) * a + ((dst >> 16) & 255) * (da / 255.0f) * (1.0f - a)) / oa);
        final int g = Math.round((((src >> 8) & 255) * a + ((dst >> 8) & 255) * (da / 255.0f) * (1.0f - a)) / oa);
        final int b = Math.round(((src & 255) * a + (dst & 255) * (da / 255.0f) * (1.0f - a)) / oa);
        return (Math.round(oa * 255.0f) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    public static int width(String s, int scale) {
        return s.isEmpty() ? 0 : (s.length() * 6 - 1) * scale;
    }

    public int text(String s, int x, int y, int argb, int scale) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            final byte[] rows = BitmapFont.glyphRows(s.charAt(i));
            for (int r = 0; r < 7; r++) {
                final int bits = rows[r] & 0x1F;
                for (int c = 0; c < 5; c++) {
                    if ((bits & (1 << (4 - c))) != 0) {
                        for (int sy = 0; sy < scale; sy++) {
                            for (int sx = 0; sx < scale; sx++) {
                                put(cx + c * scale + sx, y + r * scale + sy, argb);
                            }
                        }
                    }
                }
            }
            cx += 6 * scale;
        }
        return cx - x;
    }

    public int textRight(String s, int rx, int y, int argb, int scale) {
        return text(s, rx - width(s, scale), y, argb, scale);
    }

    public int textCentre(String s, int x, int y, int w, int argb, int scale) {
        return text(s, x + (w - width(s, scale)) / 2, y, argb, scale);
    }

    public int glow(String s, int x, int y, int main, int halo, int scale) {
        text(s, x + 1, y, halo, scale);
        text(s, x - 1, y, halo, scale);
        text(s, x, y + 1, halo, scale);
        text(s, x, y - 1, halo, scale);
        return text(s, x, y, main, scale);
    }

    public boolean blank(int y0, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(height, y1); y++) {
            for (int x = 0; x < width; x++) {
                if ((px[y * width + x] >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
