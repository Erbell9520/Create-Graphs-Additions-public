package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;

/** [1] */
public final class SignalTraceRenderer {

    private SignalTraceRenderer() {
    }

    // bake

    /**
     * Bakes the full static trace (grid, gradient fill, glow, anti-aliased core, reference lines,
     * comet head, corner brackets) into {@code img} using pure CPU pixel writes. The caller uploads
     * the backing texture once and blits it, drawing only the cheap live overlays (head pulse,
     * hover, text) per frame. Image coordinates are local: (0,0) is the plot's top-left, size w x h.
     *
     * @param reveal                   0..1 left-to-right draw-in progress
     * @param avgVal/warnVal/dangerVal reference values, or NaN to skip
     */
    public static void bakeTrace(NativeImage img, float[] data, float min, float max,
                                 int coreColor, int glowColor, int fillColor, float reveal, boolean drawGlow,
                                 int gridMinorArgb, int gridTickArgb, int baselineArgb, int bracketArgb,
                                 float avgVal, int avgArgb, float warnVal, int warnArgb,
                                 float dangerVal, int dangerArgb) {
        final int w = img.getWidth();
        final int h = img.getHeight();
        if (data == null || data.length < 2 || w < 2 || h < 2) {
            return;
        }
        img.fillRect(0, 0, w, h, 0); // clear to transparent (image is reused across rebuilds)
        final int n = data.length;
        float range = max - min;
        if (range < 1.0e-6f) {
            range = 1.0e-6f;
        }
        final float span = h - 1;

        // Scientific graph-paper minor grid (eighths) + edge tick rulers.
        for (int k = 1; k <= 7; k += 2) {
            final int gx = w * k / 8;
            final int gy = h * k / 8;
            for (int yy = 1; yy < h - 1; ++yy) {
                blend(img, gx, yy, gridMinorArgb);
            }
            for (int xx = 1; xx < w - 1; ++xx) {
                blend(img, xx, gy, gridMinorArgb);
            }
        }
        for (int k = 1; k <= 7; ++k) {
            final int len = (k % 2 == 0) ? 3 : 2;
            final int gx = w * k / 8;
            final int gy = h * k / 8;
            for (int t = 0; t < len; ++t) {
                blend(img, gx, h - 2 - t, gridTickArgb);
                blend(img, 1 + t, gy, gridTickArgb);
            }
        }
        if (min < 0.0f && max > 0.0f) {
            final int zeroY = Math.round((1.0f - (0.0f - min) / range) * span);
            for (int xx = 1; xx < w - 1; xx += 4) {
                blend(img, xx, zeroY, baselineArgb);
                blend(img, xx + 1, zeroY, baselineArgb);
            }
        }

        // Per-column trace envelope in image pixel space (topPy = higher value = smaller y).
        final float[] topPy = new float[w];
        final float[] botPy = new float[w];
        if (n <= w) {
            // Smooth: monotone cubic interpolation of the samples across the columns.
            final float[] m = monotoneTangents(data);
            for (int c = 0; c < w; ++c) {
                final float pos = (w == 1) ? 0.0f : c / (float) (w - 1) * (n - 1);
                final float v = sampleMonotone(data, m, pos);
                final float py = clampF((1.0f - (v - min) / range) * span, 0.0f, span);
                topPy[c] = py;
                botPy[c] = py;
            }
        }
        else {
            // Dense: per-column min/max envelope so spikes survive downsampling.
            for (int c = 0; c < w; ++c) {
                final int a = (int) ((long) c * n / w);
                final int b = Math.max(a + 1, (int) ((long) (c + 1) * n / w));
                float vmin = Float.MAX_VALUE;
                float vmax = -Float.MAX_VALUE;
                for (int i = a; i < b && i < n; ++i) {
                    vmin = Math.min(vmin, data[i]);
                    vmax = Math.max(vmax, data[i]);
                }
                topPy[c] = clampF((1.0f - (vmax - min) / range) * span, 0.0f, span);
                botPy[c] = clampF((1.0f - (vmin - min) / range) * span, 0.0f, span);
            }
        }

        final float revealFront = Math.max(0.0f, Math.min(1.0f, reveal)) * (w - 1);
        final int coreRgb = coreColor & 0xFFFFFF;
        final int glowRgb = glowColor & 0xFFFFFF;
        final int fillRgb = fillColor & 0xFFFFFF;
        final float baseY = span;
        final float halfCore = 0.85f;

        // Gradient area fill (per pixel, free in image memory), strongest under recent data.
        for (int c = 0; c <= revealFront && c < w; ++c) {
            final float edge = edgeFade(revealFront, c);
            final float recency = (w > 1) ? c / (float) (w - 1) : 1.0f;
            final float aTop = (0x16 + 0x2E * recency) * edge;
            if (aTop < 1.0f) {
                continue;
            }
            final float ct = topPy[c];
            final float denom = Math.max(1.0f, baseY - ct);
            for (int yy = (int) Math.ceil(ct); yy <= (int) baseY; ++yy) {
                final int a = (int) (aTop * Math.max(0.0f, 1.0f - (yy - ct) / denom));
                if (a > 0) {
                    blend(img, c, yy, (a << 24) | fillRgb);
                }
            }
        }

        // Reference lines (dashed).
        bakeDash(img, avgVal, min, range, span, avgArgb, 5, 2);
        bakeDash(img, warnVal, min, range, span, warnArgb, 3, 3);
        bakeDash(img, dangerVal, min, range, span, dangerArgb, 3, 3);

        // Soft outer glow (two feathered passes), tinted by the health band. Optional per client pref.
        if (drawGlow) {
            for (int pass = 0; pass < 2; ++pass) {
                final float pad = halfCore + (pass == 0 ? 2.2f : 4.2f);
                final float ga = (pass == 0 ? 0.16f : 0.09f);
                for (int c = 0; c <= revealFront && c < w; ++c) {
                    final int cn = Math.min(w - 1, c + 1);
                    final float segTop = Math.min(topPy[c], topPy[cn]) - pad;
                    final float segBot = Math.max(botPy[c], botPy[cn]) + pad;
                    vSpan(img, c, segTop, segBot, glowRgb, ga * edgeFade(revealFront, c));
                }
            }
        }

        // Crisp anti-aliased core stroke, brightened toward the newest sample.
        for (int c = 0; c <= revealFront && c < w; ++c) {
            final int cn = Math.min(w - 1, c + 1);
            final float segTop = Math.min(topPy[c], topPy[cn]) - halfCore;
            final float segBot = Math.max(botPy[c], botPy[cn]) + halfCore;
            final float recency = (w > 1) ? c / (float) (w - 1) : 1.0f;
            final int col = scaleRgb(coreRgb, 0.74f + 0.42f * recency);
            vSpan(img, c, segTop, segBot, col, edgeFade(revealFront, c));
        }

        // Comet head + short fading tail (static; the live pulse is drawn over the blit).
        final int headC = (int) Math.min(w - 1, Math.max(0.0f, revealFront));
        final int hy = Math.round((topPy[headC] + botPy[headC]) * 0.5f);
        for (int k = 1; k <= 12; ++k) {
            final int tc = headC - k;
            if (tc < 0) {
                break;
            }
            final int ty = Math.round((topPy[tc] + botPy[tc]) * 0.5f);
            final int a = (int) ((1.0f - k / 12.0f) * 0.5f * 255.0f);
            blend(img, tc, ty, (a << 24) | 0xFFFFFF);
        }
        softDot(img, headC, hy, glowRgb, 2, 0.30f);
        final int headCore = 0xFF000000 | scaleRgb(coreRgb, 1.25f);
        blend(img, headC - 1, hy, headCore);
        blend(img, headC, hy - 1, headCore);
        blend(img, headC, hy + 1, headCore);
        blend(img, headC, hy, 0xFFFFFFFF);

        // Corner instrument brackets.
        final int bl = 6;
        for (int t = 0; t < bl; ++t) {
            blend(img, t, 0, bracketArgb);
            blend(img, 0, t, bracketArgb);
            blend(img, w - 1 - t, 0, bracketArgb);
            blend(img, w - 1, t, bracketArgb);
            blend(img, t, h - 1, bracketArgb);
            blend(img, 0, h - 1 - t, bracketArgb);
            blend(img, w - 1 - t, h - 1, bracketArgb);
            blend(img, w - 1, h - 1 - t, bracketArgb);
        }
    }

    private static void bakeDash(NativeImage img, float value, float min, float range, float span,
                                 int argb, int dash, int gap) {
        if (Float.isNaN(value) || value <= min || value >= min + range) {
            return;
        }
        final int y = Math.round((1.0f - (value - min) / range) * span);
        final int w = img.getWidth();
        for (int x = 1; x < w - 1; x += dash + gap) {
            for (int d = 0; d < dash && x + d < w - 1; ++d) {
                blend(img, x + d, y, argb);
            }
        }
    }

    // anti-aliased image primitives

    /** Vertical span [top, bot] at column x with sub-pixel edge coverage, blended into the image. */
    private static void vSpan(NativeImage img, int x, float top, float bot, int rgb, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        if (bot < top) {
            final float t = top;
            top = bot;
            bot = t;
        }
        if (bot - top < 0.02f) {
            bot = top + 0.02f;
        }
        final int y0 = (int) Math.floor(top);
        final int y1 = (int) Math.floor(bot);
        if (y0 == y1) {
            put(img, x, y0, rgb, alpha * (bot - top));
            return;
        }
        put(img, x, y0, rgb, alpha * (1.0f - (top - y0)));
        for (int yy = y0 + 1; yy < y1; ++yy) {
            put(img, x, yy, rgb, alpha);
        }
        put(img, x, y1, rgb, alpha * (bot - y1));
    }

    private static void softDot(NativeImage img, int cx, int cy, int rgb, int radius, float alpha) {
        final int a = Math.min(255, (int) (alpha * 255.0f));
        if (a <= 0) {
            return;
        }
        final int argb = (a << 24) | (rgb & 0xFFFFFF);
        for (int yy = cy - radius; yy <= cy + radius; ++yy) {
            for (int xx = cx - radius; xx <= cx + radius; ++xx) {
                blend(img, xx, yy, argb);
            }
        }
    }

    private static void put(NativeImage img, int x, int y, int rgb, float a) {
        if (a <= 0.0f) {
            return;
        }
        if (a > 1.0f) {
            a = 1.0f;
        }
        blend(img, x, y, ((int) (a * 255.0f) << 24) | (rgb & 0xFFFFFF));
    }

    /** Alpha-composites an ARGB colour over the existing image pixel (NativeImage stores ABGR). */
    private static void blend(NativeImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        final int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            return;
        }
        final int newAbgr = BitmapFont.argbToAbgr(argb);
        if (a >= 255) {
            img.setPixelRGBA(x, y, newAbgr);
            return;
        }
        final int existing = img.getPixelRGBA(x, y);
        final int invA = 255 - a;
        final int r = ((newAbgr & 0xFF) * a + (existing & 0xFF) * invA) / 255;
        final int gg = ((newAbgr >> 8 & 0xFF) * a + (existing >> 8 & 0xFF) * invA) / 255;
        final int b = ((newAbgr >> 16 & 0xFF) * a + (existing >> 16 & 0xFF) * invA) / 255;
        final int oa = Math.min(255, a + (existing >>> 24 & 0xFF) * invA / 255);
        img.setPixelRGBA(x, y, oa << 24 | b << 16 | gg << 8 | r);
    }

    // monotone cubic (Fritsch-Carlson) interpolation

    private static float[] monotoneTangents(float[] y) {
        final int n = y.length;
        final float[] d = new float[Math.max(1, n - 1)];
        for (int i = 0; i < n - 1; ++i) {
            d[i] = y[i + 1] - y[i];
        }
        final float[] m = new float[n];
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; ++i) {
            m[i] = (d[i - 1] + d[i]) * 0.5f;
        }
        for (int i = 0; i < n - 1; ++i) {
            if (d[i] == 0.0f) {
                m[i] = 0.0f;
                m[i + 1] = 0.0f;
            } else {
                final float a = m[i] / d[i];
                final float b = m[i + 1] / d[i];
                final float s = a * a + b * b;
                if (s > 9.0f) {
                    final float tau = 3.0f / (float) Math.sqrt(s);
                    m[i] = tau * a * d[i];
                    m[i + 1] = tau * b * d[i];
                }
            }
        }
        return m;
    }

    private static float sampleMonotone(float[] y, float[] m, float pos) {
        final int n = y.length;
        if (pos <= 0.0f) {
            return y[0];
        }
        if (pos >= n - 1) {
            return y[n - 1];
        }
        final int i = (int) Math.floor(pos);
        final float t = pos - i;
        final float t2 = t * t;
        final float t3 = t2 * t;
        final float h00 = 2.0f * t3 - 3.0f * t2 + 1.0f;
        final float h10 = t3 - 2.0f * t2 + t;
        final float h01 = -2.0f * t3 + 3.0f * t2;
        final float h11 = t3 - t2;
        return h00 * y[i] + h10 * m[i] + h01 * y[i + 1] + h11 * m[i + 1];
    }

    // small helpers

    private static float edgeFade(float revealFront, int c) {
        final float d = revealFront - c;
        if (d >= 8.0f) {
            return 1.0f;
        }
        return Math.max(0.0f, d / 8.0f);
    }

    private static int scaleRgb(int rgb, float f) {
        final int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        final int gg = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        final int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (gg << 8) | b;
    }

    private static float clampF(float v, float lo, float hi) {
        return (v < lo) ? lo : (Math.min(v, hi));
    }
}
