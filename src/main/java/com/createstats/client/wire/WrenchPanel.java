package com.createstats.client.wire;

import java.util.List;

public final class WrenchPanel {
    public static final int PAD = 5;

    public static final int TAB_H = 12;

    public static final int WIRE_TAB = 22;

    public static final int TAB_GAP = 2;

    public static final int LIFT = 62;

    public static final String ALL_KEY = WireRing.ALL_KEY;
    public static final String COUNT_KEY = "create_stats.wire_tension.wire_count";
    public static final String HINT_KEY = "create_stats.wire_tension.wrench_hint";
    public static final String EMPTY_KEY = "create_stats.wire_tension.no_wires";

    private WrenchPanel() {
    }

    public record View(List<int[]> tabs, int selected, String all, String level, int[] swatch, int pip, int pips,
                       String detail, String hint) {
        public View {
            tabs = List.copyOf(tabs);
        }
    }

    public static int allTab(WireCanvas m, String all) {
        return m.width(all) + 8;
    }

    public static int[] size(WireCanvas m, View v) {
        final int tabs = allTab(m, v.all()) + v.tabs().size() * (WIRE_TAB + TAB_GAP);
        final int pips = v.pips() * WireRing.PIP_PITCH;
        final int title = 10 + m.width(v.level()) + 8 + pips;
        final int w = Math.max(Math.max(tabs, title), Math.max(m.width(v.detail()), m.width(v.hint()))) + PAD * 2;
        final int h = PAD + TAB_H + 4 + 10 + 10 + 11 + PAD - 2;
        return new int[]{w, h};
    }

    public static int[] allTabBox(WireCanvas m, View v, int x, int y) {
        return new int[]{x + PAD, y + PAD, allTab(m, v.all()), TAB_H};
    }

    public static int[] wireTabBox(WireCanvas m, View v, int x, int y, int i) {
        return new int[]{x + PAD + allTab(m, v.all()) + TAB_GAP + i * (WIRE_TAB + TAB_GAP), y + PAD, WIRE_TAB, TAB_H};
    }

    public static int row(int y) {
        return y + PAD + TAB_H + 4;
    }

    public static int[] levelBox(WireCanvas m, View v, int x, int y) {
        return new int[]{x + PAD + (v.swatch() != null ? 10 : 0), row(y), m.width(v.level()), 8};
    }

    public static int[] swatchBox(View v, int x, int y) {
        return v.swatch() == null ? null : new int[]{x + PAD, row(y), 7, 7};
    }

    public static int[] pipsBox(View v, int x, int y, int w) {
        final int pips = v.pips() * WireRing.PIP_PITCH;
        return new int[]{x + w - PAD - pips + 2, row(y) + 2, pips - 2, 4};
    }

    public static int[] detailBox(WireCanvas m, View v, int x, int y) {
        return new int[]{x + PAD, row(y) + 10, m.width(v.detail()), 8};
    }

    public static int[] hintBox(WireCanvas m, View v, int x, int y) {
        return new int[]{x + PAD, row(y) + 23, m.width(v.hint()), 8};
    }

    public static void draw(WireCanvas c, View v, int x, int y) {
        final int[] size = size(c, v);
        final int w = size[0];
        bevel(c, x, y, w, size[1]);

        int tx = x + PAD;
        final int ty = y + PAD;
        final int allTab = allTab(c, v.all());
        tab(c, tx, ty, allTab, v.selected() == -1);
        c.text(v.all(), tx + 4, ty + 2, WireRing.LABEL);
        tx += allTab + TAB_GAP;
        for (int i = 0; i < v.tabs().size(); i++) {
            tab(c, tx, ty, WIRE_TAB, v.selected() == i);
            swatch(c, tx + 3, ty + 3, v.tabs().get(i));
            c.text(String.valueOf(i + 1), tx + 12, ty + 2, WireRing.LABEL);
            tx += WIRE_TAB + TAB_GAP;
        }

        final int row = row(y);
        int nx = x + PAD;
        if (v.swatch() != null) {
            swatch(c, nx, row, v.swatch());
            nx += 10;
        }
        c.text(v.level(), nx, row, WireRing.LABEL);
        WireRing.pips(c, x + w - PAD - v.pips() * WireRing.PIP_PITCH + 2, row + 2, v.pip(), v.pips());
        c.text(v.detail(), x + PAD, row + 10, WireRing.LABEL);
        c.fill(x + PAD, row + 20, x + w - PAD, row + 21, WireRing.RECESSED);
        c.text(v.hint(), x + PAD, row + 23, WireRing.SHADOW);
    }

    public static int[] emptySize(WireCanvas m, String line) {
        return new int[]{m.width(line) + PAD * 2, 9 + PAD * 2 - 1};
    }

    public static void drawEmpty(WireCanvas c, String line, int x, int y) {
        final int[] size = emptySize(c, line);
        bevel(c, x, y, size[0], size[1]);
        c.text(line, x + PAD, y + PAD, WireRing.LABEL);
    }

    public static void bevel(WireCanvas c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, WireRing.OUTLINE);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, WireRing.FACE);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, WireRing.HIGHLIGHT);
        c.fill(x + 1, y + 1, x + 2, y + h - 1, WireRing.HIGHLIGHT);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, WireRing.SHADOW);
        c.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, WireRing.SHADOW);
    }

    public static void tab(WireCanvas c, int x, int y, int w, boolean on) {
        c.fill(x, y, x + w, y + TAB_H, on ? WireRing.HIGHLIGHT : WireRing.SHADOW);
        c.fill(x + 1, y + 1, x + w - 1, y + TAB_H - 1, on ? WireRing.FACE : WireRing.RECESSED);
        if (on) {
            c.fill(x + 1, y + TAB_H - 1, x + w, y + TAB_H, WireRing.SHADOW);
            c.fill(x + w - 1, y + 1, x + w, y + TAB_H, WireRing.SHADOW);
        }
    }

    public static void swatch(WireCanvas c, int x, int y, int[] rgb) {
        c.fill(x, y, x + 7, y + 7, WireRing.OUTLINE);
        c.fill(x + 1, y + 1, x + 6, y + 6, 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
    }
}
