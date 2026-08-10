package com.createstats.gui.pdu;

import net.minecraft.client.gui.GuiGraphics;

/** [1] */
public final class PduTheme {

    private PduTheme() {
    }

    // [2]

    /** Panel behind everything. */
    public static final int BG_DEEP = 0xFF12161B;
    /** Card surface. */
    public static final int BG_PANEL = 0xFF1A1F26;
    /** Hovered card, input field. */
    public static final int BG_RAISED = 0xFF232A33;
    /** 1 px card border. */
    public static final int STROKE = 0xFF2E3742;
    /** Focused border, and the focus ring. */
    public static final int STROKE_HOT = 0xFF3D4855;
    /** Primary text. */
    public static final int TEXT = 0xFFE4E9EF;
    /** Labels, units. */
    public static final int TEXT_DIM = 0xFF8A96A4;
    /** Disabled, placeholder. */
    public static final int TEXT_FAINT = 0xFF56606C;
    /** Running, healthy. */
    public static final int OK = 0xFF5FBF7F;
    /** Shed, starved, approaching the reserve floor. */
    public static final int WARN = 0xFFD9A441;
    /** No power, overload. */
    public static final int CRIT = 0xFFCF5C4A;
    /** Storage, charging, informational. */
    public static final int INFO = 0xFF5AA9D6;
    /** Brass: selection, active tab underline. */
    public static final int ACCENT = 0xFFB08C46;

    // metrics
    /** The base spacing unit. Every gap and pad is a multiple of it, so nothing lands on an odd pixel. */
    public static final int BASE = 4;
    /** Padding inside a card. */
    public static final int PAD = 10;
    /** Gap between cards. */
    public static final int GAP = 8;
    /** Inspector drawer width at NORMAL, clamped to the content in narrower panels. */
    public static final int DRAWER_W = 132;

    // breakpoints
    /** [3] */
    public enum Breakpoint {
        /** The floor: a small window or a high GUI scale. Tighter rows, no room to keep the inspector open. */
        COMPACT(336, 216, 14),
        /** The default, roughly a double chest screen plus half again. */
        NORMAL(408, 264, 16),
        /** Room to breathe: a large window at GUI scale 1 or 2. */
        LARGE(504, 336, 18);

        public final int idealW;
        public final int idealH;
        /** Table row height. Spec: 12 compact, 14 normal, 16 large. */
        public final int rowH;

        Breakpoint(int idealW, int idealH, int rowH) {
            this.idealW = idealW;
            this.idealH = idealH;
            this.rowH = rowH;
        }
    }

    /**
     * The largest breakpoint whose ideal size fits the window with a margin to spare, falling back to
     * COMPACT. Pure, so it can be unit tested: a geometry slip here is a panel drawn off the screen,
     * exactly the sort of thing that stays invisible until someone opens it at GUI scale 3 on a
     * laptop.
     *
     * @param w available width in GUI space (Screen.width)
     * @param h available height in GUI space (Screen.height)
     */
    public static Breakpoint currentFor(int w, int h) {
        // [4]
        final int margin = 2 * BASE;
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

    // draw primitives

    /** A card surface: the panel fill plus its 1 px border, the base every {@link PduCard} sits on. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG_PANEL);
        outline(g, x, y, w, h, STROKE);
    }

    /** A 1 px border, drawn as four thin fills so it batches rather than costing a real outline call. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** [5] */
    public static void focusRing(GuiGraphics g, int x, int y, int w, int h) {
        outline(g, x - 1, y - 1, w + 2, h + 2, STROKE_HOT);
    }

    /** A colour with its alpha channel replaced, for drawing a tint dimmer as a border or a wash. */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
