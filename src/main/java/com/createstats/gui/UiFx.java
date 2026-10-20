package com.createstats.gui;

import net.minecraft.client.gui.GuiGraphics;

public final class UiFx {
    private UiFx() {
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t);
    }

    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    public static float approach(float current, float target, float dtTicks, float speed) {
        if (dtTicks <= 0f) {
            return current;
        }
        float factor = 1f - (float) Math.exp(-speed * dtTicks);
        float next = current + (target - current) * factor;

        if (Math.abs(target - next) < 0.0008f) {
            return target;
        }
        return next;
    }

    public static float pulse01(float time, float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed);
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static int multiplyAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        a = Math.max(0, Math.min(255, Math.round(a * factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    public static int brighten(int rgb, float amount) {
        return lerpColor(0xFF000000 | (rgb & 0x00FFFFFF), 0xFFFFFFFF, amount);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void liveProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                float progress, int fillColor, int trackColor,
                                int borderColor, boolean active, float time) {
        if (w <= 2 || h <= 0) {
            return;
        }
        progress = clamp01(progress);

        g.fill(x, y, x + w, y + h, trackColor);
        int fillW = Math.round((w - 2) * progress);
        if (fillW > 0) {
            int fx = x + 1;
            int fy = y + 1;
            int fh = h - 2;
            g.fill(fx, fy, fx + fillW, y + h - 1, fillColor);

            g.fill(fx, fy, fx + fillW, fy + 1, brighten(fillColor, 0.35f));

            int edge = Math.min(fx + fillW, x + w - 1);
            g.fill(edge - 1, fy, edge, y + h - 1, brighten(fillColor, 0.55f));

            if (active && fh > 0) {
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

    public static void pulseDot(GuiGraphics g, int cx, int cy, int coreRadius, int color, float time, float speed) {
        float p = pulse01(time, speed);
        int ringR = coreRadius + 1 + Math.round(p * 3f);
        int ringAlpha = Math.round((1f - p) * 150f);
        ringOutline(g, cx, cy, ringR, withAlpha(color, ringAlpha));
        int coreBright = brighten(color, 0.25f * p);
        g.fill(cx - coreRadius, cy - coreRadius, cx + coreRadius + 1, cy + coreRadius + 1, coreBright);
    }

    public static void ringOutline(GuiGraphics g, int cx, int cy, int r, int color) {
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

    public static void marchingChevrons(GuiGraphics g, int x, int y, int w, int h, int color, float time, int dir) {
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

    public static void scanLine(GuiGraphics g, int x, int y, int w, int h, int color, float time, float speed) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int sx = x + (int) ((time * speed) % w);
        int a = (color >>> 24) & 0xFF;
        g.fill(sx, y, sx + 1, y + h, color);
        g.fill(sx + 1, y, sx + 2, y + h, withAlpha(color, a / 2));
    }

    public static final int PAGE = 0xFFE9E0C8;
    public static final int PAGE_HI = 0xFFF5EFDD;
    public static final int PAGE_LO = 0xFFD8CCAE;
    public static final int PAGE_SUNK = 0xFFCEC0A0;
    public static final int INK = 0xFF2A241C;
    public static final int INK_DIM = 0xFF6B6050;
    public static final int INK_FAINT = 0xFF9B8F78;
    public static final int RULE = 0x1F2A241C;
    public static final int MARGIN_RED = 0xFFB4463A;
    public static final int BRASS = 0xFFB58C3E;
    public static final int BRASS_HI = 0xFFEACB84;
    public static final int BRASS_LO = 0xFF6E5320;
    public static final int IRON = 0xFF24282E;
    public static final int IRON_HI = 0xFF3B424C;
    public static final int IRON_LO = 0xFF13161A;

    public static final int OK = 0xFF2E7538;
    public static final int WARN = 0xFF97620F;
    public static final int BAD = 0xFF9A2F26;

    public static final int TO_TRAIN = 0xFF1E6E86;
    public static final int TO_STATION = 0xFF5A7A1E;

    public static final int FACE = 0xFF161B21;

    public static final int FACE_HI = 0xFF212831;

    public static final int FACE_LO = 0xFF0B0E12;

    public static final int FACE_EDGE = 0xFF090B0E;

    public static final int FACE_RULE = 0x33B58C3E;

    public static final int FACE_ROW = 0x14FFFFFF;

    public static final int LIT = 0xFFF2E9D2;
    public static final int LIT_DIM = 0xFFA69C86;
    public static final int LIT_FAINT = 0xFF6E6857;

    public static final int LIT_OK = 0xFF6ED08C;
    public static final int LIT_WARN = 0xFFE3A93E;
    public static final int LIT_BAD = 0xFFE0685B;
    public static final int LIT_IDLE = 0xFF7C8794;
    public static final int LIT_LIVE = 0xFF57C2E8;

    public static final int LIT_TO_TRAIN = 0xFF4FBEDC;
    public static final int LIT_TO_STATION = 0xFFAAC756;

    public static void plate(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, fill);
        outline(g, x, y, w, h, border);
    }

    public static void sunken(GuiGraphics g, int x, int y, int w, int h, int fill) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, 0x33000000);
        g.fill(x, y, x + 1, y + h, 0x26000000);
        g.fill(x, y + h - 1, x + w, y + h, 0x22FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x1AFFFFFF);
    }

    public static void raised(GuiGraphics g, int x, int y, int w, int h, int fill) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, 0x3AFFFFFF);
        g.fill(x, y, x + 1, y + h, 0x24FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x36000000);
        g.fill(x + w - 1, y, x + w, y + h, 0x28000000);
    }

    public static void hairline(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void hatch(GuiGraphics g, int x, int y, int w, int h, int color, int spacing) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.enableScissor(x, y, x + w, y + h);
        for (int sx = x - h; sx < x + w; sx += spacing) {
            for (int row = 0; row < h; row++) {
                g.fill(sx + row, y + row, sx + row + 1, y + row + 1, color);
            }
        }
        g.disableScissor();
    }

    public static void softShadow(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 1; i <= 4; i++) {
            int a = 0x30 - i * 0x0A;
            outline(g, x - i, y - i, w + i * 2, h + i * 2, withAlpha(0xFF000000, Math.max(0, a)));
        }
    }

    public static void gauge(GuiGraphics g, int x, int y, int w, int h, float value,
                      int fillColor, boolean active, float time) {
        if (w <= 2 || h <= 0) {
            return;
        }
        value = clamp01(value);
        sunken(g, x, y, w, h, PAGE_SUNK);
        int fillW = Math.round((w - 2) * value);
        if (fillW <= 0) {
            return;
        }
        int fx = x + 1;
        int fy = y + 1;
        int fh = Math.max(1, h - 2);
        g.fill(fx, fy, fx + fillW, fy + fh, fillColor);
        g.fill(fx, fy, fx + fillW, fy + 1, brighten(fillColor, 0.28f));
        if (active && fh > 1) {
            g.enableScissor(fx, fy, fx + fillW, fy + fh);
            int period = 10;
            int phase = (int) ((time * 1.1f) % period);
            int sheen = withAlpha(0xFFFFFFFF, 40);
            for (int sx = fx - fh - period + phase; sx < fx + fillW + fh; sx += period) {
                for (int row = 0; row < fh; row++) {
                    g.fill(sx + row, fy + row, sx + row + 2, fy + row + 1, sheen);
                }
            }
            g.disableScissor();
        }
    }

    public static void triangle(GuiGraphics g, int x, int y, int size, int dir, int color) {
        for (int i = 0; i < size; i++) {
            switch (dir) {
                case 1 -> g.fill(x + i, y + i, x + i + 1, y + size * 2 - i, color);
                case -1 -> g.fill(x + size - i - 1, y + i, x + size - i, y + size * 2 - i, color);
                case 2 -> g.fill(x + i, y + size - i - 1, x + size * 2 - i, y + size - i, color);
                default -> g.fill(x + i, y + i, x + size * 2 - i, y + i + 1, color);
            }
        }
    }

    public static int chipWidth(net.minecraft.client.gui.Font font, String label) {
        return font.width(label) + 9;
    }

    public static void chip(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y,
                     String label, int inkColor, int fillColor) {
        int w = chipWidth(font, label);
        int h = 11;
        g.fill(x + 1, y, x + w - 1, y + h, fillColor);
        g.fill(x, y + 1, x + 1, y + h - 1, fillColor);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, fillColor);
        g.drawString(font, label, x + 5, y + 2, inkColor, false);
    }

    public static void sectionLabel(GuiGraphics g, net.minecraft.client.gui.Font font,
                             String label, int x, int y, int right) {
        String caps = label.toUpperCase(java.util.Locale.ROOT);
        g.drawString(font, caps, x, y, INK_FAINT, false);
        int lineX = x + font.width(caps) + 5;
        if (right - lineX > 4) {
            hairline(g, lineX, y + 3, right - lineX, RULE);
        }
    }

    public static void sectionLabelLit(GuiGraphics g, net.minecraft.client.gui.Font font,
                                String label, int x, int y, int right) {
        String caps = label.toUpperCase(java.util.Locale.ROOT);
        g.drawString(font, caps, x, y, LIT_DIM, false);
        int lineX = x + font.width(caps) + 5;
        if (right - lineX > 4) {
            hairline(g, lineX, y + 3, right - lineX, FACE_RULE);
        }
    }

    // "64/256" vs "about a minute left" answer different questions. a drop is a reset, not negative throughput
    public static final class RateTracker {
        private static final float SMOOTHING = 0.35f;
        private static final float FLOOR = 0.0015f;

        private long lastTick = Long.MIN_VALUE;
        private int lastDelivered;
        private float perTick;

        void sample(long tick, int delivered) {
            if (this.lastTick == Long.MIN_VALUE || tick <= this.lastTick) {
                this.lastTick = tick;
                this.lastDelivered = delivered;
                return;
            }
            long dt = tick - this.lastTick;
            int dv = delivered - this.lastDelivered;
            this.lastTick = tick;
            this.lastDelivered = delivered;
            if (dv < 0) {
                this.perTick = 0f;
                return;
            }
            float instant = dv / (float) dt;
            this.perTick += (instant - this.perTick) * SMOOTHING;
        }

        boolean isMoving() {
            return this.perTick >= FLOOR;
        }

        long estimate(int remaining) {
            if (remaining <= 0 || this.perTick < FLOOR) {
                return -1L;
            }
            return (long) Math.ceil(remaining / this.perTick);
        }
    }

    public static final class Rolling {
        private float shown;
        private boolean seeded;

        float update(float target, float dtTicks, float speed) {
            if (!this.seeded) {
                this.shown = target;
                this.seeded = true;
                return this.shown;
            }
            this.shown = approach(this.shown, target, dtTicks, speed);
            return this.shown;
        }

        float shown() {
            return this.shown;
        }
    }
}
