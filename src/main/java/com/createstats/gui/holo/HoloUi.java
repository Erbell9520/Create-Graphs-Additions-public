package com.createstats.gui.holo;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HoloUi {
    public static final int SCRIM = 0xB405070A;
    public static final int CARD_BG = 0xF20E1216;
    public static final int CARD_BORDER = 0xFF232C33;
    public static final int DIVIDER = 0xFF192026;
    public static final int SURFACE = 0xFF161C21;
    public static final int SURFACE_HI = 0xFF1E262C;
    public static final int TEXT_HI = 0xFFE6EEF2;
    public static final int TEXT_MID = 0xFF9AA9B2;
    public static final int TEXT_DIM = 0xFF5E6A72;
    public static final int ACCENT = 0xFF48D6E8;
    public static final int ACCENT_DK = 0xFF08181C;
    public static final int ACCENT_SOFT = 0xFF1B3B42;
    public static final int KNOB_OFF = 0xFF5E6A72;
    public static final int WARN = 0xFFFF8A5B;

    public static final int PAD = 16;

    public static final int ROW_H = 22;
    public static final int HEADER_H = 44;
    public static final int TAB_H = 24;
    public static final int FOOTER_H = 34;

    public static final int TEXT_H = 8;

    private HoloUi() {
    }

    public static float easeOutCubic(float t) {
        final float u = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - u * u * u;
    }

    public static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return channel(a, b, t, 24) << 24 | channel(a, b, t, 16) << 16
                | channel(a, b, t, 8) << 8 | channel(a, b, t, 0);
    }

    private static int channel(int a, int b, float t, int shift) {
        final int av = a >>> shift & 0xFF;
        final int bv = b >>> shift & 0xFF;
        return av + Math.round((bv - av) * t);
    }

    public static int fade(int argb, float alphaMul) {
        final int a = Math.round((argb >>> 24) * Mth.clamp(alphaMul, 0.0f, 1.0f));
        return a << 24 | (argb & 0xFFFFFF);
    }

    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (h - 2 * r > 0) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
        for (int i = 0; i < r; i++) {
            final double dy = r - i - 0.5;
            final int inset = (int) Math.round(r - Math.sqrt((double) r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    public static void roundFrame(GuiGraphics g, int x, int y, int w, int h, int r, int border, int fill) {
        roundRect(g, x, y, w, h, r, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void hLine(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void text(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.drawString(font, s, x, y, color, shadow);
    }

    public static void textRight(GuiGraphics g, Font font, String s, int rightX, int y, int color, boolean shadow) {
        g.drawString(font, s, rightX - font.width(s), y, color, shadow);
    }

    public static void textCenter(GuiGraphics g, Font font, String s, int centerX, int y, int color, boolean shadow) {
        g.drawString(font, s, centerX - font.width(s) / 2, y, color, shadow);
    }

    public static String clip(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }
        final int dots = font.width("...");
        String out = s;
        while (!out.isEmpty() && font.width(out) + dots > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    // wall clock so animations don't stall
    public static final class Anim {
        private final int durationMs;
        private float from;
        private float to;
        private long startMs;

        public Anim(float initial, int durationMs) {
            this.from = initial;
            this.to = initial;
            this.durationMs = Math.max(1, durationMs);
            this.startMs = 0L;
        }

        public void target(float value) {
            if (Math.abs(value - this.to) < 1.0E-4f) {
                return;
            }
            this.from = this.value();
            this.to = value;
            this.startMs = Util.getMillis();
        }

        public void snap(float value) {
            this.from = value;
            this.to = value;
            this.startMs = 0L;
        }

        public float value() {
            if (this.startMs == 0L) {
                return this.to;
            }
            final float t = (Util.getMillis() - this.startMs) / (float) this.durationMs;
            if (t >= 1.0f) {
                return this.to;
            }
            return this.from + (this.to - this.from) * easeOutCubic(t);
        }

        public float goal() {
            return this.to;
        }
    }
}
