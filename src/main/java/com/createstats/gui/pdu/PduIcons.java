package com.createstats.gui.pdu;

import net.minecraft.client.gui.GuiGraphics;

/** [1] */
public final class PduIcons {

    private PduIcons() {
    }

    public static final String OVERVIEW = "overview";
    public static final String LOADS = "loads";
    public static final String LIGHTING = "lighting";
    public static final String WIRING = "wiring";
    public static final String FLOW = "flow";
    public static final String LEGS = "legs";
    public static final String HISTORY = "history";

    /** Draws the named glyph in a 16 by 16 box at {@code (x, y)} in the given colour. */
    public static void draw(GuiGraphics g, String id, int x, int y, int color) {
        switch (id) {
            case OVERVIEW -> overview(g, x, y, color);
            case LOADS -> loads(g, x, y, color);
            case LIGHTING -> lighting(g, x, y, color);
            case WIRING -> wiring(g, x, y, color);
            case FLOW -> flow(g, x, y, color);
            case LEGS -> legs(g, x, y, color);
            case HISTORY -> history(g, x, y, color);
            default -> {
            }
        }
    }

    /** Four cards: the dashboard. */
    private static void overview(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 2, y + 2, x + 7, y + 7, c);
        g.fill(x + 9, y + 2, x + 14, y + 7, c);
        g.fill(x + 2, y + 9, x + 7, y + 14, c);
        g.fill(x + 9, y + 9, x + 14, y + 14, c);
    }

    /** Three rows with a lead marker: a list. */
    private static void loads(GuiGraphics g, int x, int y, int c) {
        for (int row = 0; row < 3; row++) {
            final int ry = y + 3 + row * 4;
            g.fill(x + 2, ry, x + 4, ry + 2, c);
            g.fill(x + 6, ry, x + 14, ry + 2, c);
        }
    }

    /** A sun: brightness, and distinct at a glance from the list. */
    private static void lighting(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 6, y + 6, x + 10, y + 10, c);
        g.fill(x + 7, y + 2, x + 9, y + 4, c);
        g.fill(x + 7, y + 12, x + 9, y + 14, c);
        g.fill(x + 2, y + 7, x + 4, y + 9, c);
        g.fill(x + 12, y + 7, x + 14, y + 9, c);
        g.fill(x + 3, y + 3, x + 5, y + 5, c);
        g.fill(x + 11, y + 3, x + 13, y + 5, c);
        g.fill(x + 3, y + 11, x + 5, y + 13, c);
        g.fill(x + 11, y + 11, x + 13, y + 13, c);
    }

    /** A single line diagram: a bus with two devices hanging off it. */
    private static void wiring(GuiGraphics g, int x, int y, int c) {
        // The vertical bus.
        g.fill(x + 7, y + 2, x + 9, y + 14, c);
        // A node at the top, the PDU.
        g.fill(x + 6, y + 2, x + 10, y + 5, c);
        // Left branch and its device box.
        g.fill(x + 3, y + 7, x + 7, y + 9, c);
        g.fill(x + 2, y + 9, x + 5, y + 12, c);
        // Right branch and its device box.
        g.fill(x + 9, y + 9, x + 13, y + 11, c);
        g.fill(x + 11, y + 11, x + 14, y + 14, c);
    }

    /** A bus bar with terminals standing on it: the eight output legs. */
    private static void legs(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 2, y + 9, x + 14, y + 12, c);
        for (int i = 0; i < 4; i++) {
            g.fill(x + 3 + i * 3, y + 4, x + 5 + i * 3, y + 9, c);
        }
    }

    /** An isometric cube: the flow diagram, and the only glyph in the set that is not flat on. */
    private static void flow(GuiGraphics g, int x, int y, int c) {
        // The top face, as spans, so it reads as a rhombus rather than a square at 16 px.
        for (int row = 0; row <= 3; row++) {
            final int half = 6 - 2 * row;
            g.fill(x + 8 - half, y + 2 + row, x + 8 + half, y + 3 + row, c);
        }
        // The two side faces, one a step shorter so the cube has a visible near corner.
        g.fill(x + 2, y + 6, x + 8, y + 12, c);
        g.fill(x + 8, y + 6, x + 14, y + 12, c);
        g.fill(x + 7, y + 6, x + 9, y + 14, c);
    }

    /** A rising trace against an axis: history. */
    private static void history(GuiGraphics g, int x, int y, int c) {
        // Axes.
        g.fill(x + 2, y + 2, x + 3, y + 14, c);
        g.fill(x + 2, y + 13, x + 14, y + 14, c);
        // The trace, a polyline climbing left to right.
        seg(g, x + 3, y + 11, x + 6, y + 7, c);
        seg(g, x + 6, y + 7, x + 9, y + 9, c);
        seg(g, x + 9, y + 9, x + 13, y + 4, c);
    }

    /** A 1 px line between two points, for the few diagonal strokes a glyph needs. */
    private static void seg(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
