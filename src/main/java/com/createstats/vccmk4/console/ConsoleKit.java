package com.createstats.vccmk4.console;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// create's grammar. "clear understandable and create/minecraft themed", not modern
abstract class ConsoleKit {
    static final int CASING = 0xFFC6C6C6;
    static final int CASING_LIGHT = 0xFFFFFFFF;
    static final int CASING_SHADE = 0xFF555555;

    static final int EDGE = 0xFF000000;

    static final int ON_CASING = 0xFF404040;

    static final int BRASS = 0xFFE4B763;
    static final int BRASS_HI = 0xFFFFEB8C;
    static final int BRASS_SHADE = 0xFF592424;

    static final int BRASS_DIM = 0xFFA87B3C;

    static final int ON_BRASS = 0xFF592424;

    static final int SLATE = 0xFF383838;

    static final int SLATE_DEEP = 0xFF262626;

    static final int SLATE_LIGHT = 0xFF4A4A4A;

    static final int SLATE_EDGE = 0xFF141414;
    static final int SLATE_RIM = 0xFF6A6A6A;

    static final int TEXT = 0xFFF2F2F2;
    static final int TEXT_DIM = 0xFFAAAAAA;
    static final int TEXT_FAINT = 0xFF808080;

    static final int GOOD = 0xFF55FF55;
    static final int WARN = 0xFFFFAA00;
    static final int BAD = 0xFFFF5555;
    static final int INFO = 0xFF55FFFF;
    static final int YELLOW = 0xFFFFFF55;

    static final int DISPLAY = 0xFF000000;
    static final int DISPLAY_EDGE = 0xFFA0A0A0;

    static final int SLOT = 0xFF8B8B8B;
    static final int SLOT_SHADE = 0xFF373737;

    static final int BUTTON = 0xFF6F6F6F;
    static final int BUTTON_LIGHT = 0xFFAAAAAA;
    static final int BUTTON_SHADE = 0xFF565656;
    static final int BUTTON_HOT = 0xFF757575;
    static final int BUTTON_HOT_LIGHT = 0xFFAFAFAF;
    static final int BUTTON_HOT_SHADE = 0xFF5C5C5C;
    static final int BUTTON_DEAD = 0xFF2C2C2C;

    static final int ON_BUTTON = 0xFFFFFFFF;
    static final int ON_BUTTON_DEAD = 0xFFA0A0A0;

    static final int CARD = SLATE_DEEP;
    static final int CARD_EDGE = SLATE_EDGE;

    static final int WELL = DISPLAY;
    static final int WELL_EDGE = 0xFF505050;

    static final int IVORY = 0xFFF3ECD6;
    static final int IVORY_DIM = 0xFFD9CBA6;
    static final int ON_IVORY = 0xFF2C2116;

    static final int LAMP_ON = 0xFFFFAA00;
    static final int LAMP_WHITE = 0xFFFFF6DC;
    static final int LAMP_RED = 0xFFFF4B3C;

    static final int PAD = 8;

    static final int HEADER_H = 20;
    static final int TAB_Y = 21;
    static final int TAB_H = 13;
    static final int CONTENT_Y = 40;
    static final int BUTTON_H = 14;
    static final int LINE = 10;

    static final int CARD_HEAD = 13;

    static final int MOUSE_RIGHT = 1;
    static final int KEY_ESCAPE = 256;

    record Hit(int x, int y, int w, int h, @Nullable Runnable left, @Nullable Runnable right, @Nullable List<String> tip,
               boolean popup) {
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    record Popup(int x, int y, int w, List<String> labels, List<Runnable> actions, int current) {
    }

    record ScrollArea(int x0, int y0, int x1, int y1, String list) {
    }

    interface RowDrawer {
        void row(int index, int x0, int y, int x1);
    }

    protected final ConsoleHost host;
    private final String langPrefix;
    private final Map<String, Integer> scroll = new HashMap<>();
    @Nullable protected Popup popup;

    protected ConsoleCanvas c;
    protected int left;
    protected int top;
    protected int w;
    protected int h;
    protected int mx;
    protected int my;
    protected int rowH;
    protected final List<Hit> hits = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private List<String> tooltip = List.of();

    ConsoleKit(ConsoleHost host, String langPrefix) {
        this.host = host;
        this.langPrefix = langPrefix;
    }

    public List<String> tooltip() {
        return tooltip;
    }

    protected void begin(ConsoleCanvas canvas, int screenW, int screenH, int mouseX, int mouseY, int panelW, int panelH) {
        c = canvas;
        w = panelW;
        h = panelH;
        left = (screenW - w) / 2;
        top = (screenH - h) / 2;
        mx = mouseX;
        my = mouseY;
        rowH = h >= 320 ? 17 : 15;
        hits.clear();
        scrollAreas.clear();
        tooltip = List.of();
    }

    protected void end() {
        if (popup != null) drawPopup();
        boolean popupOpen = popup != null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (popupOpen && !hit.popup()) continue;
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mx, my)) {
                tooltip = wrapAll(hit.tip(), 220);
                return;
            }
        }
    }

    protected boolean click(double x, double y, int button) {
        if (popup != null) {
            popup = null;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.popup() && hit.contains(x, y) && hit.left() != null) {
                    hit.left().run();
                    host.click();
                    break;
                }
            }
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.popup() || !hit.contains(x, y)) continue;
            Runnable action = button == MOUSE_RIGHT ? hit.right() : hit.left();
            if (action == null) continue;
            action.run();
            host.click();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double x, double y, double dy) {
        if (dy == 0.0 || popup != null) return false;
        for (ScrollArea a : scrollAreas) {
            if (x >= a.x0() && x < a.x1() && y >= a.y0() && y < a.y1()) {
                scroll.merge(a.list(), dy > 0 ? -1 : 1, Integer::sum);
                return true;
            }
        }
        return false;
    }

    protected void resetScroll() {
        scroll.clear();
    }

    protected int scrollOf(String list) {
        return scroll.getOrDefault(list, 0);
    }

    protected void scrollTo(String list, int first) {
        scroll.put(list, Math.max(0, first));
    }

    protected int list(int x0, int y0, int x1, int y1, int count, String name, RowDrawer drawer) {
        int visible = Math.max(1, (y1 - y0) / rowH);
        int max = Math.max(0, count - visible);
        int first = clampInt(scroll.getOrDefault(name, 0), 0, max);
        scroll.put(name, first);
        boolean bar = count > visible;
        int rx1 = bar ? x1 - 7 : x1;
        for (int i = first; i < Math.min(count, first + visible); i++) {
            drawer.row(i, x0, y0 + (i - first) * rowH, rx1);
        }
        if (bar) {
            int sh = visible * rowH - 2;
            sunk(x1 - 5, y0, 5, sh, DISPLAY, SLATE_EDGE, SLATE_RIM);
            int knob = Math.max(10, sh * visible / count);
            int ky = y0 + (sh - knob) * first / Math.max(1, max);
            raised(x1 - 5, ky, 5, knob, SLOT, CASING, CASING_SHADE);
        }
        scrollAreas.add(new ScrollArea(x0, y0, x1, y0 + visible * rowH, name));
        return rx1;
    }

    protected void plate(int x, int y, int pw, int ph, int colour) {
        if (pw < 3 || ph < 3) {
            c.fill(x, y, x + pw, y + ph, colour);
            return;
        }
        c.fill(x + 1, y, x + pw - 1, y + ph, colour);
        c.fill(x, y + 1, x + 1, y + ph - 1, colour);
        c.fill(x + pw - 1, y + 1, x + pw, y + ph - 1, colour);
    }

    protected void plate(int x, int y, int pw, int ph, int fill, int edge) {
        plate(x, y, pw, ph, edge);
        if (pw > 2 && ph > 2) plate(x + 1, y + 1, pw - 2, ph - 2, fill);
    }

    protected void gradient(int x0, int y0, int x1, int y1, int top, int bottom, int steps) {
        int n = Math.max(1, Math.min(steps, y1 - y0));
        for (int i = 0; i < n; i++) {
            int a = y0 + (y1 - y0) * i / n;
            int b = y0 + (y1 - y0) * (i + 1) / n;
            c.fill(x0, a, x1, b, mix(top, bottom, n == 1 ? 0.0 : i / (double) (n - 1)));
        }
    }

    protected int card(int x, int y, int cw, int ch, @Nullable String title) {
        if (title == null || title.isEmpty()) {
            sunk(x, y, cw, ch, CARD, SLATE_EDGE, SLATE_RIM);
            return y + 4;
        }
        sunk(x, y + CARD_HEAD - 1, cw, ch - CARD_HEAD + 1, CARD, SLATE_EDGE, SLATE_RIM);
        brassStrip(x, y, cw, CARD_HEAD);
        textFit(title, x + 5, y + 3, cw - 10, ON_BRASS);
        return y + CARD_HEAD + 4;
    }

    protected int chip(int x, int y, String text, int colour) {
        int cw = host.width(text) + 8;
        display(x, y, cw, 11);
        c.text(text, x + 4, y + 2, colour);
        return cw;
    }

    protected void raised(int x, int y, int bw, int bh, int face, int light, int shade) {
        if (bw < 3 || bh < 3) {
            c.fill(x, y, x + bw, y + bh, face);
            return;
        }
        c.fill(x, y, x + bw, y + bh, EDGE);
        c.fill(x + 1, y + 1, x + bw - 1, y + bh - 1, shade);
        c.fill(x + 1, y + 1, x + bw - 2, y + bh - 2, light);
        c.fill(x + 2, y + 2, x + bw - 2, y + bh - 2, face);
    }

    protected void sunk(int x, int y, int bw, int bh, int face, int dark, int light) {
        if (bw < 3 || bh < 3) {
            c.fill(x, y, x + bw, y + bh, face);
            return;
        }
        c.fill(x, y, x + bw, y + bh, light);
        c.fill(x, y, x + bw - 1, y + bh - 1, dark);
        c.fill(x + 1, y + 1, x + bw - 1, y + bh - 1, face);
    }

    protected void casing(int x, int y, int cw, int ch) {
        c.fill(x + 1, y, x + cw - 1, y + ch, EDGE);
        c.fill(x, y + 1, x + cw, y + ch - 1, EDGE);
        c.fill(x + 1, y + 1, x + cw - 1, y + ch - 1, CASING_SHADE);
        c.fill(x + 1, y + 1, x + cw - 3, y + ch - 3, CASING_LIGHT);
        c.fill(x + 3, y + 3, x + cw - 3, y + ch - 3, CASING);
    }

    protected void brassStrip(int x, int y, int sw, int sh) {
        c.fill(x, y, x + sw, y + sh, EDGE);
        c.fill(x + 1, y + 1, x + sw - 1, y + sh - 1, BRASS_SHADE);
        c.fill(x + 1, y + 1, x + sw - 2, y + sh - 2, BRASS_HI);
        c.fill(x + 2, y + 2, x + sw - 2, y + sh - 2, BRASS);
    }

    protected void display(int x, int y, int dw, int dh) {
        c.fill(x, y, x + dw, y + dh, DISPLAY_EDGE);
        c.fill(x + 1, y + 1, x + dw - 1, y + dh - 1, DISPLAY);
    }

    protected int nameplate(String text, int x, int y, int colour) {
        int tw = host.width(text) + 4;
        rectQuad(x, y, x + tw, y + 10, 0x66000000);
        c.text(text, x + 2, y + 1, colour);
        return tw;
    }

    protected void rectQuad(double x0, double y0, double x1, double y1, int argb) {
        c.quad((float) x0, (float) y0, (float) x1, (float) y0, (float) x1, (float) y1, (float) x0, (float) y1, argb);
    }

    protected void disc(double cx, double cy, double r, int colour) {
        int n = r < 4 ? 8 : r < 10 ? 12 : 20;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n;
            double a1 = Math.PI * 2 * (i + 1) / n;
            triangle((float) cx, (float) cy, (float) (cx + r * Math.cos(a0)), (float) (cy + r * Math.sin(a0)),
                    (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)), colour);
        }
    }

    protected void ring(double cx, double cy, double r0, double r1, int colour) {
        int n = r1 < 10 ? 16 : 28;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n;
            double a1 = Math.PI * 2 * (i + 1) / n;
            quadCw(cx + r0 * Math.cos(a0), cy + r0 * Math.sin(a0), cx + r1 * Math.cos(a0), cy + r1 * Math.sin(a0),
                    cx + r1 * Math.cos(a1), cy + r1 * Math.sin(a1), cx + r0 * Math.cos(a1), cy + r0 * Math.sin(a1), colour);
        }
    }

    protected void quadCw(double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3, int argb) {
        double area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (area < 0) {
            c.quad((float) x0, (float) y0, (float) x3, (float) y3, (float) x2, (float) y2, (float) x1, (float) y1, argb);
        } else {
            c.quad((float) x0, (float) y0, (float) x1, (float) y1, (float) x2, (float) y2, (float) x3, (float) y3, argb);
        }
    }

    protected void arrow(double x0, double y0, double x1, double y1, double width, double head, int colour) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        if (len < 1.0e-6) return;
        double ux = dx / len;
        double uy = dy / len;
        double bx = x1 - ux * head;
        double by = y1 - uy * head;
        if (len > head) line(x0, y0, bx, by, width, colour);
        triangle((float) x1, (float) y1, (float) (bx - uy * head * 0.6), (float) (by + ux * head * 0.6),
                (float) (bx + uy * head * 0.6), (float) (by - ux * head * 0.6), colour);
    }

    protected void hit(int x, int y, int hw, int hh, @Nullable Runnable onLeft, @Nullable Runnable onRight, @Nullable List<String> tip) {
        hits.add(new Hit(x, y, hw, hh, onLeft, onRight, tip, false));
    }

    protected boolean hot(int x, int y, int bw, int bh) {
        return popup == null && mx >= x && my >= y && mx < x + bw && my < y + bh;
    }

    protected void button(int x, int y, int bw, String text, boolean pressed, @Nullable Runnable onLeft, @Nullable Runnable onRight,
                          @Nullable List<String> tip) {
        boolean live = onLeft != null || onRight != null;
        boolean isHot = live && hot(x, y, bw, BUTTON_H);
        if (pressed) {
            raised(x, y, bw, BUTTON_H, isHot ? BRASS_HI : BRASS, isHot ? 0xFFFFF6C4 : BRASS_HI, BRASS_DIM);
            if (isHot) outline(x, y, bw, BUTTON_H, CASING_LIGHT);
            c.text(text, x + (bw - host.width(text)) / 2, y + 3, ON_BRASS);
        } else if (!live) {
            raised(x, y, bw, BUTTON_H, BUTTON_DEAD, BUTTON_DEAD, BUTTON_DEAD);
            c.text(text, x + (bw - host.width(text)) / 2, y + 3, ON_BUTTON_DEAD);
        } else {
            raised(x, y, bw, BUTTON_H, isHot ? BUTTON_HOT : BUTTON, isHot ? BUTTON_HOT_LIGHT : BUTTON_LIGHT,
                    isHot ? BUTTON_HOT_SHADE : BUTTON_SHADE);
            if (isHot) outline(x, y, bw, BUTTON_H, CASING_LIGHT);
            c.text(text, x + (bw - host.width(text)) / 2, y + 3, ON_BUTTON);
        }
        hit(x, y, bw, BUTTON_H, onLeft, onRight, tip);
    }

    protected void outline(int x, int y, int bw, int bh, int colour) {
        c.fill(x, y, x + bw, y + 1, colour);
        c.fill(x, y + bh - 1, x + bw, y + bh, colour);
        c.fill(x, y + 1, x + 1, y + bh - 1, colour);
        c.fill(x + bw - 1, y + 1, x + bw, y + bh - 1, colour);
    }

    protected int button(int x, int y, String text, boolean pressed, @Nullable Runnable onLeft, @Nullable List<String> tip) {
        int bw = host.width(text) + 12;
        button(x, y, bw, text, pressed, onLeft, null, tip);
        return bw;
    }

    protected int segmented(int x, int y, List<String> labels, int chosen, List<Runnable> actions, List<List<String>> tips) {
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            int bw = host.width(labels.get(i)) + 10;
            button(cx, y, bw, labels.get(i), i == chosen, actions.get(i), null, tips == null || i >= tips.size() ? null : tips.get(i));
            cx += bw + 1;
        }
        return cx - x - 1;
    }

    protected int segmentedWidth(List<String> labels) {
        int total = 0;
        for (String l : labels) total += host.width(l) + 10 + 1;
        return Math.max(0, total - 1);
    }

    protected int stepperWidth(int valueW) {
        return 12 + 4 + valueW + 4 + 12;
    }

    protected void stepper(int x, int y, String value, int valueW, Runnable minus, Runnable plus, List<String> tip) {
        button(x, y, 12, "-", false, minus, null, tip);
        c.text(value, x + 16 + (valueW - host.width(value)) / 2, y + 3, TEXT);
        hit(x + 12, y, valueW + 8, BUTTON_H, null, null, tip);
        button(x + 16 + valueW + 4, y, 12, "+", false, plus, null, tip);
    }

    protected int checkboxWidth(String text) {
        return 20 + (text.isEmpty() ? 0 : host.width(text));
    }

    protected void checkbox(int x, int y, boolean on, String text, Runnable toggle, List<String> tip) {
        boolean isHot = hot(x, y - 1, checkboxWidth(text), 11);
        if (on) {
            raised(x, y, 16, 9, BRASS, BRASS_HI, BRASS_DIM);

            c.fill(x + 5, y + 4, x + 7, y + 6, ON_BRASS);
            c.fill(x + 7, y + 5, x + 8, y + 7, ON_BRASS);
            c.fill(x + 8, y + 2, x + 10, y + 6, ON_BRASS);
            c.fill(x + 10, y + 2, x + 11, y + 4, ON_BRASS);
        } else {
            sunk(x, y, 16, 9, SLOT, SLOT_SHADE, CASING_LIGHT);
            c.fill(x + 3, y + 4, x + 13, y + 5, SLOT_SHADE);
        }
        if (isHot) outline(x - 1, y - 1, 18, 11, CASING_LIGHT);
        if (!text.isEmpty()) c.text(text, x + 20, y + 1, TEXT_DIM);
        hit(x, y - 1, checkboxWidth(text), 11, toggle, null, tip);
    }

    protected void tab(int x, int y, int tw, String text, boolean on, boolean alert) {
        boolean isHot = hot(x, y, tw, TAB_H);
        if (on) {
            c.fill(x, y, x + tw, y + TAB_H + 1, EDGE);
            c.fill(x + 1, y + 1, x + tw - 1, y + TAB_H + 1, SLATE);
            c.fill(x + 1, y + 1, x + tw - 1, y + 3, BRASS);
            c.fill(x + 1, y + 1, x + tw - 1, y + 2, BRASS_HI);
            c.text(text, x + 6, y + 4, TEXT);
        } else {
            raised(x, y + 1, tw, TAB_H - 1, isHot ? 0xFFA0A0A0 : SLOT, CASING, CASING_SHADE);
            c.text(text, x + 6, y + 4, isHot ? 0xFF202020 : ON_CASING);
        }
        if (alert) {
            int dx = x + 6 + host.width(text) + 3;
            c.fill(dx, y + 5, dx + 3, y + 8, on ? WARN : 0xFFB35A00);
            c.fill(dx + 1, y + 4, dx + 2, y + 9, on ? WARN : 0xFFB35A00);
        }
    }

    protected void openPopup(int x, int y, List<String> labels, List<Runnable> actions, int current) {
        int pw = 0;
        for (String l : labels) pw = Math.max(pw, host.width(l) + 14);
        popup = new Popup(x, y, pw, labels, actions, current);
    }

    // tall lists wrap into columns
    private void drawPopup() {
        Popup p = popup;
        if (p == null) return;
        c.layer();
        int n = p.labels().size();
        int perColumn = Math.max(1, Math.min(n, (h - 12) / 12));
        int columns = (n + perColumn - 1) / perColumn;
        int rows = (n + columns - 1) / columns;
        int pw = p.w();
        int totalW = pw * columns;
        int ph = rows * 12 + 4;
        int px = Math.max(left + 2, Math.min(p.x(), left + w - PAD - totalW));
        int py = p.y();
        if (py + ph > top + h - 2) py = Math.max(top + 2, p.y() - BUTTON_H - ph);
        if (py + ph > top + h - 2) py = Math.max(top + 2, top + h - 2 - ph);

        casing(px - 3, py - 3, totalW + 6, ph + 6);
        for (int i = 0; i < n; i++) {
            int col = i / rows;
            int row = i % rows;
            int rx = px + col * pw;
            int ry = py + 2 + row * 12;
            boolean over = mx >= rx && mx < rx + pw && my >= ry && my < ry + 12;
            if (i == p.current()) raised(rx + 1, ry, pw - 2, 12, BRASS, BRASS_HI, BRASS_DIM);
            else if (over) c.fill(rx + 1, ry, rx + pw - 1, ry + 12, 0xFFE0E0E0);
            c.text(p.labels().get(i), rx + 7, ry + 2, i == p.current() ? ON_BRASS : ON_CASING);
            hits.add(new Hit(rx + 2, ry, pw - 4, 12, p.actions().get(i), null, null, true));
        }
    }

    protected void bevel(int x, int y, int bw, int bh, boolean hot) {
        raised(x, y, bw, bh, hot ? BUTTON_HOT : BUTTON, hot ? BUTTON_HOT_LIGHT : BUTTON_LIGHT, hot ? BUTTON_HOT_SHADE : BUTTON_SHADE);
    }

    protected void well(int x, int y, int ww, int wh, int face) {
        sunk(x, y, ww, wh, face, SLATE_EDGE, SLATE_RIM);
    }

    protected void bar(int x, int y, int bw, int bh, double value, int colour) {
        well(x, y, bw, bh, WELL);
        double v = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
        int fillW = (int) Math.round((bw - 2) * v);
        if (fillW > 0) segments(x + 1, y + 1, x + 1 + fillW, y + bh - 1, colour);
    }

    protected void centredBar(int x, int y, int bw, int bh, double value, int colour) {
        well(x, y, bw, bh, WELL);
        int mid = x + bw / 2;
        double v = Double.isFinite(value) ? Math.max(-1.0, Math.min(1.0, value)) : 0.0;
        int reach = (int) Math.round((bw / 2 - 1) * Math.abs(v));
        if (reach > 0) {
            if (v > 0) segments(mid + 1, y + 1, mid + 1 + reach, y + bh - 1, colour);
            else segments(mid - reach, y + 1, mid, y + bh - 1, colour);
        }
        c.fill(mid, y, mid + 1, y + bh, TEXT_DIM);
    }

    protected void segments(int x0, int y0, int x1, int y1, int colour) {
        if (x1 <= x0 || y1 <= y0) return;
        c.fill(x0, y0, x1, y1, colour);
        if (y1 - y0 >= 3) c.fill(x0, y0, x1, y0 + 1, mix(colour, 0xFFFFFFFF, 0.45));
        if (y1 - y0 >= 4) c.fill(x0, y1 - 1, x1, y1, mix(colour, 0xFF000000, 0.35));
        int notch = mix(colour, 0xFF000000, 0.55);
        for (int k = x0 + 5; k < x1 - 1; k += 5) c.fill(k, y0, k + 1, y1, notch);
    }

    protected void triangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        double area = (x1 - x0) * (double) (y2 - y0) - (y1 - y0) * (double) (x2 - x0);
        if (area < 0) c.quad(x0, y0, x2, y2, x1, y1, x1, y1, argb);
        else c.quad(x0, y0, x1, y1, x2, y2, x2, y2, argb);
    }

    protected void line(double x0, double y0, double x1, double y1, double width, int argb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        if (len < 1.0e-6) return;
        double nx = -dy / len * width / 2.0;
        double ny = dx / len * width / 2.0;
        c.quad((float) (x0 - nx), (float) (y0 - ny), (float) (x1 - nx), (float) (y1 - ny), (float) (x1 + nx),
                (float) (y1 + ny), (float) (x0 + nx), (float) (y0 + ny), argb);
    }

    protected void square(double cx, double cy, double half, int argb) {
        c.quad((float) (cx - half), (float) (cy - half), (float) (cx + half), (float) (cy - half), (float) (cx + half),
                (float) (cy + half), (float) (cx - half), (float) (cy + half), argb);
    }

    protected String tr(String key, Object... args) {
        return host.tr(langPrefix + key, args);
    }

    protected String fit(String text, int max) {
        if (text == null) return "";
        if (host.width(text) <= max) return text;
        String dots = "...";
        int dw = host.width(dots);
        if (max <= dw) return "";
        int end = text.length();
        while (end > 0 && host.width(text.substring(0, end)) + dw > max) end--;
        return text.substring(0, end).stripTrailing() + dots;
    }

    protected void textFit(String text, int x, int y, int max, int colour) {
        String shown = fit(text, max);
        c.text(shown, x, y, colour);
        if (!shown.equals(text) && !text.isEmpty()) hit(x, y - 1, Math.max(1, host.width(shown)), 10, null, null, List.of(text));
    }

    protected void textRight(String text, int x1, int y, int colour) {
        c.text(text, x1 - host.width(text), y, colour);
    }

    protected void textCentre(String text, int cx, int y, int colour) {
        c.text(text, cx - host.width(text) / 2, y, colour);
    }

    protected List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (host.width(candidate) <= max) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(host.width(word) <= max ? word : fit(word, max));
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    protected List<String> wrapAll(List<String> lines, int max) {
        List<String> out = new ArrayList<>();
        for (String l : lines) out.addAll(wrap(l, max));
        return out;
    }

    static int mix(int a, int b, double t) {
        double u = Math.max(0.0, Math.min(1.0, t));
        int ca = (a >>> 24) & 255;
        int cr = (a >>> 16) & 255;
        int cg = (a >>> 8) & 255;
        int cb = a & 255;
        int da = (b >>> 24) & 255;
        int dr = (b >>> 16) & 255;
        int dg = (b >>> 8) & 255;
        int db = b & 255;
        int oa = (int) Math.round(ca + (da - ca) * u);
        int or = (int) Math.round(cr + (dr - cr) * u);
        int og = (int) Math.round(cg + (dg - cg) * u);
        int ob = (int) Math.round(cb + (db - cb) * u);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    static int alpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
