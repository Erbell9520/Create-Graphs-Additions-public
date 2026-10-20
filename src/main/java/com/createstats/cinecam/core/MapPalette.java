package com.createstats.cinecam.core;

// error diffusion or the sky is two flat bands
public final class MapPalette {
    public static final int SIZE = 128;

    private MapPalette() {
    }

    public static int[] squareDown(int[] argb, int width, int height) {
        return squareDown(argb, width, height, SIZE);
    }

    public static int[] squareDown(int[] argb, int width, int height, int size) {
        int side = Math.min(width, height);
        int x0 = (width - side) / 2;
        int y0 = (height - side) / 2;
        int[] out = new int[size * size];
        for (int oy = 0; oy < size; oy++) {
            int sy0 = y0 + oy * side / size;
            int sy1 = Math.max(sy0 + 1, y0 + (oy + 1) * side / size);
            for (int ox = 0; ox < size; ox++) {
                int sx0 = x0 + ox * side / size;
                int sx1 = Math.max(sx0 + 1, x0 + (ox + 1) * side / size);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int c = argb[sy * width + sx];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        n++;
                    }
                }
                out[oy * size + ox] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    public static byte[] quantize(int[] rgb, int[] palette, byte[] ids, boolean dither) {
        return quantize(rgb, SIZE, palette, ids, dither);
    }

    public static byte[] quantize(int[] rgb, int size, int[] palette, byte[] ids, boolean dither) {
        int n = size * size;
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        for (int i = 0; i < n; i++) {
            r[i] = (rgb[i] >> 16) & 0xFF;
            g[i] = (rgb[i] >> 8) & 0xFF;
            b[i] = rgb[i] & 0xFF;
        }
        byte[] out = new byte[n];
        for (int y = 0; y < size; y++) {
            boolean ltr = !dither || (y & 1) == 0;
            for (int k = 0; k < size; k++) {
                int x = ltr ? k : size - 1 - k;
                int i = y * size + x;
                float cr = clamp(r[i]), cg = clamp(g[i]), cb = clamp(b[i]);
                int best = nearest(cr, cg, cb, palette);
                out[i] = ids[best];
                if (!dither) continue;
                int p = palette[best];
                float er = cr - ((p >> 16) & 0xFF);
                float eg = cg - ((p >> 8) & 0xFF);
                float eb = cb - (p & 0xFF);
                int dx = ltr ? 1 : -1;
                spread(r, g, b, size, x + dx, y, er, eg, eb, 7f / 16f);
                spread(r, g, b, size, x - dx, y + 1, er, eg, eb, 3f / 16f);
                spread(r, g, b, size, x, y + 1, er, eg, eb, 5f / 16f);
                spread(r, g, b, size, x + dx, y + 1, er, eg, eb, 1f / 16f);
            }
        }
        return out;
    }

    public static int nearest(float r, float g, float b, int[] palette) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int k = 0; k < palette.length; k++) {
            int p = palette[k];
            float dr = r - ((p >> 16) & 0xFF);
            float dg = g - ((p >> 8) & 0xFF);
            float db = b - (p & 0xFF);
            float d = 2f * dr * dr + 4f * dg * dg + 3f * db * db;
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    private static void spread(float[] r, float[] g, float[] b, int size, int x, int y, float er, float eg, float eb, float w) {
        if (x < 0 || x >= size || y >= size) return;
        int i = y * size + x;
        r[i] += er * w;
        g[i] += eg * w;
        b[i] += eb * w;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 255f ? 255f : v);
    }
}
