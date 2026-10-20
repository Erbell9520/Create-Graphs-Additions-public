package com.createstats.client.wire;

import java.util.List;

public final class WireRing {
    public static final int HUB_RADIUS = 24;

    public static final int PANEL_H = 34;

    public static final int PANEL_MIN_W = 76;

    public static final int HUB_H = 30;

    public static final double MIN_GAP = Math.toRadians(40.0);

    public static final int HINT_FROM_BOTTOM = 34;

    public static final int PANEL_FLOOR = 40;

    public static final int PIP_PITCH = 10;

    public static final int OUTLINE = 0xFF000000;
    public static final int HIGHLIGHT = 0xFFFFFFFF;
    public static final int FACE = 0xFFC6C6C6;
    public static final int FACE_HOT = 0xFFD8D8D8;
    public static final int RECESSED = 0xFF8B8B8B;
    public static final int SHADOW = 0xFF555555;
    public static final int LABEL = 0xFF404040;
    public static final int HINT = 0xFFFFFFFF;

    private WireRing() {
    }

    public static final String SPAN_KEY = "create_stats.wire_tension.span";

    public static final String ALL_KEY = "create_stats.wire_tension.all";

    public static final String MIXED_KEY = "create_stats.wire_tension.mixed";

    public static final String HINT_KEY = "create_stats.wire_tension.hint";

    public static String levelKey(com.createstats.power.WireTension level) {
        return "create_stats.wire_tension." + level.key();
    }

    public static Object[] spanArgs(double span, com.createstats.power.WireTension level) {
        return new Object[]{String.format(java.util.Locale.ROOT, "%.0f", span),
                String.format(java.util.Locale.ROOT, "%.1f", level.sagOver(span))};
    }

    public record Panel(int[] swatch, String level, String span, int pip, int pips, double angle) {
    }

    public record Frame(int width, int height, int cx, int cy, int rx, int ry) {
        public static Frame of(int width, int height) {
            final int ry = Math.max(50, Math.min(84, height / 2 - PANEL_H - 12));
            final int rx = Math.max(ry, Math.min((int) (ry * 1.7), width / 2 - 50));
            return new Frame(width, height, width / 2, height / 2, rx, ry);
        }

        public int ax(double angle) {
            return (int) Math.round(cx + Math.cos(angle) * rx);
        }

        public int ay(double angle) {
            return (int) Math.round(cy + Math.sin(angle) * ry);
        }
    }

    public static int panelWidth(WireCanvas m, String level, String span) {
        final int title = 10 + m.width(level) + 4;
        final int sub = m.width(span);
        return Math.max(PANEL_MIN_W, Math.max(title, sub) + 12);
    }

    public static int[] panelBox(WireCanvas m, Frame f, Panel p) {
        final int w = panelWidth(m, p.level(), p.span());
        final int x = Math.max(4, Math.min(f.width() - w - 4, f.ax(p.angle()) - w / 2));
        final int y = Math.max(4, Math.min(f.height() - PANEL_H - PANEL_FLOOR, f.ay(p.angle()) - PANEL_H / 2));
        return new int[]{x, y, w, PANEL_H};
    }

    public static int[] hubBox(WireCanvas m, Frame f, String all, String sub) {
        final int w = Math.max(m.width(all), m.width(sub)) + 14;
        return new int[]{f.cx() - w / 2, f.cy() - HUB_H / 2, w, HUB_H};
    }

    public static int[] hintBox(WireCanvas m, Frame f, String hint) {
        final int w = m.width(hint);
        return new int[]{f.cx() - w / 2, f.height() - HINT_FROM_BOTTOM, w, 8};
    }

    public static int[] swatchBox(int[] panel) {
        return new int[]{panel[0] + 5, panel[1] + 5, 7, 7};
    }

    public static int[] levelBox(WireCanvas m, int[] panel, String level) {
        return new int[]{panel[0] + 15, panel[1] + 5, m.width(level), 8};
    }

    public static int[] spanBox(WireCanvas m, int[] panel, String span) {
        return new int[]{panel[0] + 6, panel[1] + 15, m.width(span), 8};
    }

    public static int[] pipsBox(int[] panel, int pips) {
        return new int[]{panel[0] + 6, panel[1] + PANEL_H - 8, pips * PIP_PITCH - 2, 4};
    }

    public static int[][] dots(Frame f, double angle) {
        final int x0 = f.cx();
        final int y0 = f.cy();
        final int x1 = f.ax(angle);
        final int y1 = f.ay(angle);
        final double len = Math.hypot(x1 - x0, y1 - y0);
        final int steps = (int) (len / 4.0);
        final int[][] out = new int[Math.max(0, steps - 3)][];
        for (int i = 3; i < steps; i++) {
            final int x = (int) Math.round(x0 + (x1 - x0) * (double) i / steps);
            final int y = (int) Math.round(y0 + (y1 - y0) * (double) i / steps);
            out[i - 3] = new int[]{x, y};
        }
        return out;
    }

    public static int pick(Frame f, List<Panel> panels, int dx, int dy) {
        if (dx * dx + dy * dy <= HUB_RADIUS * HUB_RADIUS || panels.isEmpty()) {
            return -1;
        }
        final double at = Math.atan2(dy / (double) f.ry(), dx / (double) f.rx());
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        for (int k = 0; k < panels.size(); k++) {
            final double gap = WireRingLayout.between(at, panels.get(k).angle());
            if (gap < bestGap) {
                bestGap = gap;
                best = k;
            }
        }
        return best;
    }

    public static void draw(WireCanvas c, Frame f, List<Panel> panels, String all, String hubSub, String hint,
                            int hovered) {
        for (final Panel p : panels) {
            for (final int[] d : dots(f, p.angle())) {
                c.fill(d[0] + 1, d[1] + 1, d[0] + 2, d[1] + 2, OUTLINE);
                c.fill(d[0], d[1], d[0] + 1, d[1] + 1, HIGHLIGHT);
            }
        }
        for (int k = 0; k < panels.size(); k++) {
            drawPanel(c, f, panels.get(k), k == hovered);
        }
        final int[] hub = hubBox(c, f, all, hubSub);
        bevel(c, hub[0], hub[1], hub[2], hub[3], hovered == -1);
        c.text(all, f.cx() - c.width(all) / 2, hub[1] + 5, LABEL);
        c.text(hubSub, f.cx() - c.width(hubSub) / 2, hub[1] + 16, LABEL);
        final int[] h = hintBox(c, f, hint);
        c.shadowText(hint, h[0], h[1], HINT);
    }

    private static void drawPanel(WireCanvas c, Frame f, Panel p, boolean hot) {
        final int[] b = panelBox(c, f, p);
        bevel(c, b[0], b[1], b[2], b[3], hot);

        final int swatch = 0xFF000000 | (p.swatch()[0] << 16) | (p.swatch()[1] << 8) | p.swatch()[2];
        c.fill(b[0] + 5, b[1] + 5, b[0] + 12, b[1] + 12, OUTLINE);
        c.fill(b[0] + 6, b[1] + 6, b[0] + 11, b[1] + 11, swatch);
        c.text(p.level(), b[0] + 15, b[1] + 5, LABEL);
        c.text(p.span(), b[0] + 6, b[1] + 15, LABEL);
        pips(c, b[0] + 6, b[1] + PANEL_H - 8, p.pip(), p.pips());
    }

    public static void pips(WireCanvas c, int x, int y, int chosen, int count) {
        for (int t = 0; t < count; t++) {
            final int px = x + t * PIP_PITCH;
            final boolean on = t == chosen;
            c.fill(px, y, px + 8, y + 4, on ? OUTLINE : SHADOW);
            c.fill(px + 1, y + 1, px + 7, y + 3, on ? HIGHLIGHT : RECESSED);
        }
    }

    public static void bevel(WireCanvas c, int x, int y, int w, int h, boolean hot) {
        c.fill(x, y, x + w, y + h, hot ? HIGHLIGHT : OUTLINE);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? FACE_HOT : FACE);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
        c.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        c.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);
    }
}
