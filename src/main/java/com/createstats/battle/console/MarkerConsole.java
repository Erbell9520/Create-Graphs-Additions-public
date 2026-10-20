package com.createstats.battle.console;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MarkerConsole {
    public static final int W = 280;
    public static final int H = 150;
    public static final int MAX_NAME = 24;
    public static final int MIN_COVER = 1;
    public static final int MAX_COVER = 8;

    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;

    private record Hit(int x, int y, int w, int h, @Nullable Runnable left, @Nullable List<String> tip) {
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final ConsoleHost host;
    private String name;
    private int cover;
    private int permille;
    private boolean attached;
    private String reportsTo;
    private boolean done;
    private int ticks;

    private ConsoleCanvas c;
    private int mx;
    private int my;
    private final List<Hit> hits = new ArrayList<>();
    private List<String> tooltip = List.of();

    public MarkerConsole(ConsoleHost host, String name, int cover, int permille, boolean attached, String reportsTo) {
        this.host = host;
        this.name = name == null ? "" : name;
        this.cover = Math.max(MIN_COVER, Math.min(MAX_COVER, cover));
        this.permille = permille;
        this.attached = attached;
        this.reportsTo = reportsTo == null ? "" : reportsTo;
    }

    public void accept(int permille, boolean attached, String reportsTo) {
        this.permille = permille;
        this.attached = attached;
        this.reportsTo = reportsTo == null ? "" : reportsTo;
    }

    public String name() {
        return name.strip();
    }

    public int cover() {
        return cover;
    }

    public boolean done() {
        return done;
    }

    public List<String> tooltip() {
        return tooltip;
    }

    public void tick() {
        ticks++;
    }

    public void render(ConsoleCanvas canvas, int screenW, int screenH, int mouseX, int mouseY) {
        c = canvas;
        mx = mouseX;
        my = mouseY;
        hits.clear();
        tooltip = List.of();
        int left = (screenW - W) / 2;
        int top = (screenH - H) / 2;
        bevel(left, top, W, H, false);
        int x0 = left + 8;
        int x1 = left + W - 8;
        int y = top + 7;
        c.text(tr("marker.title"), x0, y, BridgeConsole.LABEL);
        y += 16;

        String nameLabel = tr("marker.name");
        String coverLabel = tr("marker.cover");
        String intLabel = tr("marker.integrity");
        int labelW = Math.max(host.width(nameLabel), Math.max(host.width(coverLabel), host.width(intLabel))) + 8;
        c.text(nameLabel, x0, y + 3, BridgeConsole.LABEL);
        int fx = x0 + labelW;
        well(fx, y, x1 - fx, 14, 0xFF000000);
        String shown = fitTail(name, x1 - fx - 10);
        c.text(shown, fx + 4, y + 3, 0xFFE0E0E0);
        if ((ticks / 10) % 2 == 0) {
            int cx = fx + 4 + host.width(shown);
            c.fill(cx, y + 2, cx + 1, y + 12, 0xFFE0E0E0);
        }
        hit(fx, y, x1 - fx, 14, null, List.of(tr("marker.name.hint")));
        y += 20;

        c.text(coverLabel, x0, y + 3, BridgeConsole.LABEL);
        String value = tr("marker.cover_value", cover, 2 * cover + 1);
        int vw = host.width(tr("marker.cover_value", 8, 17));
        button(fx, y, 12, "-", () -> cover = Math.max(MIN_COVER, cover - 1), List.of(tr("marker.cover.hint")));
        c.text(value, fx + 16 + (vw - host.width(value)) / 2, y + 3, BridgeConsole.INK);
        button(fx + 16 + vw + 4, y, 12, "+", () -> cover = Math.min(MAX_COVER, cover + 1), List.of(tr("marker.cover.hint")));
        y += 20;

        c.text(intLabel, x0, y + 1, BridgeConsole.LABEL);
        int lamp = permille < 0 ? BridgeConsole.RECESSED : permille >= 1000 ? 0xFF2E9D3A : permille >= 900 ? 0xFFE0A000 : 0xFFD0301C;
        c.fill(fx, y + 1, fx + 6, y + 7, BridgeConsole.OUTLINE);
        c.fill(fx + 1, y + 2, fx + 5, y + 6, lamp);
        String reading = permille < 0 ? tr("marker.integrity_none") : BridgeConsole.pct(permille);
        c.text(fit(reading, x1 - fx - 10), fx + 10, y + 1, BridgeConsole.INK);
        y += 14;
        String reports = attached ? (reportsTo.isEmpty() ? tr("marker.reports_ground") : tr("marker.reports", reportsTo)) : tr("marker.alone");
        c.text(fit(reports, x1 - x0), x0, y, BridgeConsole.DIM);
        hit(x0, y, x1 - x0, 10, null, List.of(tr(attached ? "marker.reports.hint" : "marker.alone.hint")));

        String doneLabel = tr("marker.done");
        int dw = host.width(doneLabel) + 20;
        button(x1 - dw, top + H - 8 - 14, dw, doneLabel, () -> done = true, null);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mx, my)) {
                tooltip = hit.tip();
                break;
            }
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y) || hit.left() == null) continue;
            hit.left().run();
            host.click();
            return true;
        }
        return false;
    }

    public boolean charTyped(char ch) {
        if (ch < ' ' || ch == 127) return false;
        if (name.length() >= MAX_NAME) return true;
        name = name + ch;
        return true;
    }

    public boolean keyPressed(int key) {
        if (key == KEY_BACKSPACE) {
            if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
            return true;
        }
        if (key == KEY_ENTER || key == KEY_KP_ENTER) {
            done = true;
            return true;
        }
        return false;
    }

    private void hit(int x, int y, int hw, int hh, @Nullable Runnable onLeft, @Nullable List<String> tip) {
        hits.add(new Hit(x, y, hw, hh, onLeft, tip));
    }

    private void button(int x, int y, int bw, String text, @Nullable Runnable onLeft, @Nullable List<String> tip) {
        boolean hot = mx >= x && my >= y && mx < x + bw && my < y + 14;
        bevel(x, y, bw, 14, hot);
        c.text(text, x + (bw - host.width(text)) / 2, y + 3, BridgeConsole.LABEL);
        hit(x, y, bw, 14, onLeft, tip);
    }

    private void bevel(int x, int y, int bw, int bh, boolean hot) {
        c.fill(x, y, x + bw, y + bh, hot ? BridgeConsole.HIGHLIGHT : BridgeConsole.OUTLINE);
        c.fill(x + 1, y + 1, x + bw - 1, y + bh - 1, hot ? BridgeConsole.FACE_HOT : BridgeConsole.FACE);
        c.fill(x + 1, y + 1, x + bw - 1, y + 2, BridgeConsole.HIGHLIGHT);
        c.fill(x + 1, y + 1, x + 2, y + bh - 1, BridgeConsole.HIGHLIGHT);
        c.fill(x + 1, y + bh - 2, x + bw - 1, y + bh - 1, BridgeConsole.SHADOW);
        c.fill(x + bw - 2, y + 1, x + bw - 1, y + bh - 1, BridgeConsole.SHADOW);
    }

    private void well(int x, int y, int ww, int wh, int face) {
        c.fill(x, y, x + ww, y + wh, BridgeConsole.SHADOW);
        c.fill(x + 1, y + 1, x + ww, y + wh, BridgeConsole.HIGHLIGHT);
        c.fill(x + 1, y + 1, x + ww - 1, y + wh - 1, face);
    }

    private String tr(String key, Object... args) {
        return host.tr(BridgeConsole.L + key, args);
    }

    private String fit(String text, int max) {
        if (host.width(text) <= max) return text;
        int end = text.length();
        while (end > 0 && host.width(text.substring(0, end) + "...") > max) end--;
        return text.substring(0, end) + "...";
    }

    private String fitTail(String text, int max) {
        if (host.width(text) <= max) return text;
        int start = 0;
        while (start < text.length() && host.width(text.substring(start)) > max) start++;
        return text.substring(start);
    }
}
