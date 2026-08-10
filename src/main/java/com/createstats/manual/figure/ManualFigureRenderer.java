package com.createstats.manual.figure;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** [1] */
public final class ManualFigureRenderer {

    private ManualFigureRenderer() {
    }

    /** Pixels reserved at the bottom of the body for the screen-drawn pip strip. */
    public static final int PIP_RESERVE = 18;

    private static final int TITLE_BAR_H = 22;

    public static void render(GuiGraphics g, Font font, int x, int y, int w, int h, FigureSheet sheet, long millis) {
        g.fill(x, y, x + w, y + h, FigurePalette.SHEET_WASH);
        outline(g, x, y, w, h, 0x88705438);

        // Title bar
        g.fill(x + 6, y + 6, x + w - 6, y + 20, 0x66E2D0AA);
        g.drawString(font, trim(font, sheet.title(), w - 18), x + 10, y + 9, FigurePalette.INK, false);

        int cx = x + 10;
        int cy = y + TITLE_BAR_H + 6;
        int cw = w - 20;
        int ch = Math.max(40, h - TITLE_BAR_H - 6 - PIP_RESERVE);

        // Resolve node pixel rects first so arrows can anchor to them.
        Map<String, int[]> rects = new HashMap<>();
        for (FigureElement e : sheet.elements()) {
            if (e instanceof FigureElement.Node n) {
                int nx = cx + Math.round(n.nx() * cw);
                int ny = cy + Math.round(n.ny() * ch);
                int nw = Math.max(20, Math.round(n.nw() * cw));
                int nh = Math.max(16, Math.round(n.nh() * ch));
                rects.put(n.id(), new int[]{nx, ny, nw, nh});
            }
        }

        // Arrows under nodes.
        for (FigureElement e : sheet.elements()) {
            if (e instanceof FigureElement.Arrow a) {
                int[] from = rects.get(a.fromId());
                int[] to = rects.get(a.toId());
                if (from != null && to != null) {
                    drawFlowArrow(g, from, to, a.color(), millis);
                }
            }
        }

        // Nodes.
        for (FigureElement e : sheet.elements()) {
            if (e instanceof FigureElement.Node n) {
                int[] r = rects.get(n.id());
                drawNode(g, font, r[0], r[1], r[2], r[3], n);
            }
        }

        // Chips & notes on top.
        for (FigureElement e : sheet.elements()) {
            if (e instanceof FigureElement.Chip c) {
                int px = cx + Math.round(c.nx() * cw);
                int py = cy + Math.round(c.ny() * ch);
                drawChip(g, font, px, py, c.label(), c.color());
            } else if (e instanceof FigureElement.Note note) {
                int px = cx + Math.round(note.nx() * cw);
                int py = cy + Math.round(note.ny() * ch);
                int avail = note.centered() ? cw : Math.max(20, cx + cw - px);
                java.util.List<String> lines = wrapText(font, note.text(), avail);
                int blockH = lines.size() * 10;
                int bottom = cy + ch;
                if (py + blockH > bottom) {
                    py = Math.max(cy, bottom - blockH);
                }
                int ly = py;
                for (String line : lines) {
                    int lx = px;
                    if (note.centered()) {
                        lx = px - font.width(line) / 2;
                    }
                    g.drawString(font, line, lx, ly, note.color(), false);
                    ly += 10;
                }
            }
        }
    }

    private static void drawNode(GuiGraphics g, Font font, int x, int y, int w, int h, FigureElement.Node n) {
        if (n.style() == FigureElement.Style.PANEL) {
            g.fill(x, y, x + w, y + h, FigurePalette.PAPER);
            outline(g, x, y, w, h, n.color());
            // readout window
            g.fill(x + 4, y + 4, x + w - 4, y + h / 2, withAlpha(n.color(), 0x33));
            g.drawString(font, trim(font, n.title(), w - 8), x + 5, y + 5, FigurePalette.INK, false);
            if (n.subtitle() != null && !n.subtitle().isEmpty()) {
                g.drawString(font, trim(font, n.subtitle(), w - 8), x + 5, y + h - 11, diagramInk(n.color()), false);
            }
            return;
        }
        g.fill(x, y, x + w, y + h, FigurePalette.PAPER);
        outline(g, x, y, w, h, n.color());
        // accent stripe on top
        g.fill(x, y, x + w, y + 2, n.color());
        g.drawString(font, trim(font, n.title(), w - 8), x + 4, y + 6, FigurePalette.INK, false);
        if (n.subtitle() != null && !n.subtitle().isEmpty()) {
            g.drawString(font, trim(font, n.subtitle(), w - 8), x + 4, y + h - 11, diagramInk(n.color()), false);
        }
    }

    private static void drawChip(GuiGraphics g, Font font, int x, int y, String label, int color) {
        int width = font.width(label) + 10;
        g.fill(x, y, x + width, y + 14, FigurePalette.PAPER_LIGHT);
        outline(g, x, y, width, 14, color);
        g.fill(x, y, x + width, y + 1, color);
        g.drawString(font, label, x + 5, y + 3, paperInk(color), false);
    }

    private static void drawFlowArrow(GuiGraphics g, int[] from, int[] to, int color, long millis) {
        float fromCx = from[0] + from[2] / 2f;
        float fromCy = from[1] + from[3] / 2f;
        float toCx = to[0] + to[2] / 2f;
        float toCy = to[1] + to[3] / 2f;
        float[] start = edgePoint(from, toCx, toCy);
        float[] end = edgePoint(to, fromCx, fromCy);

        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) {
            return;
        }
        int steps = Math.max(8, Math.round(dist / 4f));
        // faint base track
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(start[0] + dx * t);
            int py = Math.round(start[1] + dy * t);
            g.fill(px - 1, py - 1, px + 1, py + 1, 0x66705438);
        }
        // animated pulse
        int phase = Math.floorMod((int) (millis / 40L), steps + 1);
        float tp = phase / (float) steps;
        int pxp = Math.round(start[0] + dx * tp);
        int pyp = Math.round(start[1] + dy * tp);
        g.fill(pxp - 2, pyp - 2, pxp + 3, pyp + 3, color);
        // arrowhead near target
        int hx = Math.round(end[0]);
        int hy = Math.round(end[1]);
        g.fill(hx - 3, hy - 3, hx + 3, hy + 3, color);
    }

    /** Intersection of the segment (rect center → toward point) with the rect border. */
    private static float[] edgePoint(int[] rect, float towardX, float towardY) {
        float cx = rect[0] + rect[2] / 2f;
        float cy = rect[1] + rect[3] / 2f;
        float dx = towardX - cx;
        float dy = towardY - cy;
        if (Math.abs(dx) < 0.001f && Math.abs(dy) < 0.001f) {
            return new float[]{cx, cy};
        }
        float scaleX = dx == 0 ? Float.MAX_VALUE : (rect[2] / 2f) / Math.abs(dx);
        float scaleY = dy == 0 ? Float.MAX_VALUE : (rect[3] / 2f) / Math.abs(dy);
        float s = Math.min(scaleX, scaleY);
        return new float[]{cx + dx * s, cy + dy * s};
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    /** Word-wraps {@code text} to fit {@code maxWidth}, never dropping content. */
    private static java.util.List<String> wrapText(Font font, String text, int maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        if (font.width(text) <= maxWidth) {
            out.add(text);
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            out.add(trim(font, line.toString(), maxWidth));
        }
        return out;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int diagramInk(int color) {
        int r = (color >> 16) & 0xFF;
        int gg = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.max(40, Math.round(r * 0.42f));
        gg = Math.max(32, Math.round(gg * 0.42f));
        b = Math.max(24, Math.round(b * 0.42f));
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private static int paperInk(int color) {
        int r = (color >> 16) & 0xFF;
        int gg = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int max = Math.max(r, Math.max(gg, b));
        if (max <= 0) {
            return 0xFFECDDBA;
        }
        float scale = 232.0f / max;
        r = Math.min(255, Math.round(r * scale));
        gg = Math.min(255, Math.round(gg * scale));
        b = Math.min(255, Math.round(b * scale));
        r = Math.max(104, Math.min(255, Math.round(r * 0.72f + 255 * 0.28f)));
        gg = Math.max(104, Math.min(255, Math.round(gg * 0.72f + 255 * 0.28f)));
        b = Math.max(104, Math.min(255, Math.round(b * 0.72f + 255 * 0.28f)));
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    public static long now() {
        return Util.getMillis();
    }
}
