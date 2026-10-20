package com.createstats.gui.pdu;

import net.minecraft.client.gui.GuiGraphics;

public final class PduTheme {
    private PduTheme() {
    }

    public static final int BG_DEEP = 0xFF12161B;

    public static final int BG_PANEL = 0xFF1A1F26;

    public static final int BG_RAISED = 0xFF232A33;

    public static final int STROKE = 0xFF2E3742;

    public static final int STROKE_HOT = 0xFF3D4855;

    public static final int TEXT = 0xFFE4E9EF;

    public static final int TEXT_DIM = 0xFF8A96A4;

    public static final int TEXT_FAINT = 0xFF56606C;

    public static final int OK = 0xFF5FBF7F;

    public static final int WARN = 0xFFD9A441;

    public static final int CRIT = 0xFFCF5C4A;

    public static final int INFO = 0xFF5AA9D6;

    public static final int ACCENT = 0xFFB08C46;

    public static final int BASE = 4;

    public static final int PAD = 10;

    public static final int GAP = 8;

    public static final int DRAWER_W = 132;

    public enum Breakpoint {
        COMPACT(336, 216, 14),

        NORMAL(408, 264, 16),

        LARGE(504, 336, 18),
        // XL on top instead of rescaling, no one gets a smaller panel than before
        XL(704, 456, 20);

        public final int idealW;
        public final int idealH;

        public final int rowH;

        Breakpoint(int idealW, int idealH, int rowH) {
            this.idealW = idealW;
            this.idealH = idealH;
            this.rowH = rowH;
        }
    }

    public static Breakpoint currentFor(int w, int h) {
        final int margin = 2 * BASE;
        if (fits(Breakpoint.XL, w, h, margin)) {
            return Breakpoint.XL;
        }
        if (fits(Breakpoint.LARGE, w, h, margin)) {
            return Breakpoint.LARGE;
        }
        if (fits(Breakpoint.NORMAL, w, h, margin)) {
            return Breakpoint.NORMAL;
        }
        return Breakpoint.COMPACT;
    }

    private static boolean fits(Breakpoint bp, int w, int h, int margin) {
        return w >= bp.idealW + margin * 2 && h >= bp.idealH + margin * 2;
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG_PANEL);
        outline(g, x, y, w, h, STROKE);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    public static void focusRing(GuiGraphics g, int x, int y, int w, int h) {
        outline(g, x - 1, y - 1, w + 2, h + 2, STROKE_HOT);
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
