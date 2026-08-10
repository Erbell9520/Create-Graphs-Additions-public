package com.createstats.gui;

import net.minecraft.client.gui.GuiGraphics;

/** [1] */
final class DispatchUiFx {

    private DispatchUiFx() {
    }

    // math / easing

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float easeOut(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t);
    }

    static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    /**
     * Frame-rate independent approach of {@code current} toward {@code target}.
     * {@code speed} is roughly "fraction closed per tick"; higher snaps faster.
     */
    static float approach(float current, float target, float dtTicks, float speed) {
        if (dtTicks <= 0f) {
            return current;
        }
        float factor = 1f - (float) Math.exp(-speed * dtTicks);
        float next = current + (target - current) * factor;
        // Snap when effectively arrived to avoid endless tiny residuals.
        if (Math.abs(target - next) < 0.0008f) {
            return target;
        }
        return next;
    }

    /** 0..1 sine pulse. */
    static float pulse01(float time, float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed);
    }

    // color

    static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    static int multiplyAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        a = Math.max(0, Math.min(255, Math.round(a * factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Brightens an opaque RGB color toward white by {@code amount} (0..1). */
    static int brighten(int rgb, float amount) {
        return lerpColor(0xFF000000 | (rgb & 0x00FFFFFF), 0xFFFFFFFF, amount);
    }

    // primitives

    /** Thin one-pixel rectangle outline. */
    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** [2] */
    static void liveProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                float progress, int fillColor, int trackColor,
                                int borderColor, boolean active, float time) {
        if (w <= 2 || h <= 0) {
            return;
        }
        progress = clamp01(progress);
        // Track
        g.fill(x, y, x + w, y + h, trackColor);
        int fillW = Math.round((w - 2) * progress);
        if (fillW > 0) {
            int fx = x + 1;
            int fy = y + 1;
            int fh = h - 2;
            g.fill(fx, fy, fx + fillW, y + h - 1, fillColor);
            // Top highlight line for a slightly rounded, lit feel.
            g.fill(fx, fy, fx + fillW, fy + 1, brighten(fillColor, 0.35f));
            // Leading edge glow.
            int edge = Math.min(fx + fillW, x + w - 1);
            g.fill(edge - 1, fy, edge, y + h - 1, brighten(fillColor, 0.55f));

            if (active && fh > 0) {
                // Marching diagonal sheen clipped to the filled region.
                g.enableScissor(fx, fy, fx + fillW, fy + fh);
                int period = 14;
                int phase = (int) ((time * 1.4f) % period);
                int sheen = withAlpha(0xFFFFFFFF, 46);
                for (int sx = fx - h - period + phase; sx < fx + fillW + h; sx += period) {
                    for (int row = 0; row < fh; row++) {
                        int px = sx + row;
                        g.fill(px, fy + row, px + 2, fy + row + 1, sheen);
                    }
                }
                g.disableScissor();
            }
        }
        outline(g, x, y, w, h, borderColor);
    }

    /**
     * A pulsing "live" indicator: a solid core plus an expanding, fading ring.
     * Communicates that data is updating / a state is active.
     */
    static void pulseDot(GuiGraphics g, int cx, int cy, int coreRadius, int color, float time, float speed) {
        float p = pulse01(time, speed);
        int ringR = coreRadius + 1 + Math.round(p * 3f);
        int ringAlpha = Math.round((1f - p) * 150f);
        ringOutline(g, cx, cy, ringR, withAlpha(color, ringAlpha));
        int coreBright = brighten(color, 0.25f * p);
        g.fill(cx - coreRadius, cy - coreRadius, cx + coreRadius + 1, cy + coreRadius + 1, coreBright);
    }

    /** Cheap square "ring" (four edges) centered on cx,cy with the given radius. */
    static void ringOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) {
            return;
        }
        int x = cx - r;
        int y = cy - r;
        int s = r * 2 + 1;
        g.fill(x, y, x + s, y + 1, color);
        g.fill(x, y + s - 1, x + s, y + s, color);
        g.fill(x, y, x + 1, y + s, color);
        g.fill(x + s - 1, y, x + s, y + s, color);
    }

    /**
     * Marching chevrons inside a horizontal strip, indicating directional flow
     * (e.g. cargo loading toward the train, or a train approaching). Direction
     * {@code +1} flows right, {@code -1} flows left.
     */
    static void marchingChevrons(GuiGraphics g, int x, int y, int w, int h, int color, float time, int dir) {
        if (w <= 4 || h <= 2) {
            return;
        }
        g.enableScissor(x, y, x + w, y + h);
        int spacing = h + 6;
        int half = h / 2;
        float speed = 0.9f;
        int travel = (int) ((time * speed) % spacing);
        int offset = dir >= 0 ? travel : spacing - travel;
        for (int base = x - spacing; base < x + w + spacing; base += spacing) {
            int cxp = base + offset;
            // Fade chevrons near the strip edges for a soft look.
            float edgeFade = 1f;
            int distLeft = cxp - x;
            int distRight = (x + w) - cxp;
            int fadeZone = h * 2;
            if (distLeft < fadeZone) {
                edgeFade = Math.min(edgeFade, Math.max(0f, distLeft / (float) fadeZone));
            }
            if (distRight < fadeZone) {
                edgeFade = Math.min(edgeFade, Math.max(0f, distRight / (float) fadeZone));
            }
            int c = multiplyAlpha(color, 0.30f + 0.70f * edgeFade);
            for (int i = 0; i < half; i++) {
                int topX = dir >= 0 ? cxp - half + i : cxp + half - i;
                g.fill(topX, y + i, topX + 2, y + i + 1, c);
                int botX = dir >= 0 ? cxp - half + i : cxp + half - i;
                g.fill(botX, y + h - 1 - i, botX + 2, y + h - i, c);
            }
        }
        g.disableScissor();
    }

    /** Horizontal sweeping scan line used as a subtle "live" affordance on panels. */
    static void scanLine(GuiGraphics g, int x, int y, int w, int h, int color, float time, float speed) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int sx = x + (int) ((time * speed) % w);
        int a = (color >>> 24) & 0xFF;
        g.fill(sx, y, sx + 1, y + h, color);
        g.fill(sx + 1, y, sx + 2, y + h, withAlpha(color, a / 2));
    }
}
