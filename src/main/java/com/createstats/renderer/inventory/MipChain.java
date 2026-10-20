package com.createstats.renderer.inventory;

public final class MipChain {
    private MipChain() {
    }

    public static int[][] build(int[] argb, int width, int height, int maxLevel) {
        if (maxLevel < 0 || width <= 0 || height <= 0 || argb.length != width * height
                || (width >> maxLevel) << maxLevel != width || (height >> maxLevel) << maxLevel != height) {
            throw new IllegalArgumentException("mip chain " + width + " x " + height + " to level " + maxLevel);
        }
        final int[][] out = new int[maxLevel + 1][];
        out[0] = argb;
        int w = width;
        int h = height;
        for (int level = 1; level <= maxLevel; level++) {
            final int nw = w / 2;
            final int nh = h / 2;
            final int[] src = out[level - 1];
            final int[] dst = new int[nw * nh];
            for (int y = 0; y < nh; y++) {
                for (int x = 0; x < nw; x++) {
                    final int i = (2 * y) * w + 2 * x;
                    dst[y * nw + x] = average(src[i], src[i + 1], src[i + w], src[i + w + 1]);
                }
            }
            out[level] = dst;
            w = nw;
            h = nh;
        }
        return out;
    }

    static int average(int a, int b, int c, int d) {
        final int aa = a >>> 24;
        final int ba = b >>> 24;
        final int ca = c >>> 24;
        final int da = d >>> 24;
        final int sumA = aa + ba + ca + da;
        if (sumA == 0) {
            return 0;
        }
        final int r = weighted(a >> 16, aa, b >> 16, ba, c >> 16, ca, d >> 16, da, sumA);
        final int g = weighted(a >> 8, aa, b >> 8, ba, c >> 8, ca, d >> 8, da, sumA);
        final int bl = weighted(a, aa, b, ba, c, ca, d, da, sumA);
        final int alpha = (sumA + 2) >> 2;
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int weighted(int a, int aa, int b, int ba, int c, int ca, int d, int da, int sumA) {
        final int sum = (a & 255) * aa + (b & 255) * ba + (c & 255) * ca + (d & 255) * da;
        return Math.min(255, (sum + sumA / 2) / sumA);
    }

    public static int[] pad(int[] src, int w, int h, int pw, int ph) {
        if (pw < w || ph < h || src.length != w * h) {
            throw new IllegalArgumentException("pad " + w + " x " + h + " into " + pw + " x " + ph);
        }
        final int[] out = new int[pw * ph];
        for (int y = 0; y < ph; y++) {
            final int sy = Math.min(y, h - 1);
            for (int x = 0; x < pw; x++) {
                out[y * pw + x] = src[sy * w + Math.min(x, w - 1)];
            }
        }
        return out;
    }

    public static void blit(int[] src, int sw, int sh, int[] dst, int dw, int dx, int dy) {
        for (int y = 0; y < sh; y++) {
            System.arraycopy(src, y * sw, dst, (dy + y) * dw + dx, sw);
        }
    }
}
