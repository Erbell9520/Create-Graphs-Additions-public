package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.Corner;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// create's RadialWrenchMenu numbers, measured from the decompile
public final class NodeRadial {
    public static final int INNER = 50;
    public static final int OUTER = 110;
    static final int RIM_IN = 112;
    static final int RIM_OUT = 113;
    static final int RING_IN = 47;
    static final int RING_OUT = 48;

    public static final int DEAD = 45;

    static final int LABEL_R = 80;

    static final int BACKGROUND = 0x50101010;
    public static final int FADE_TICKS = 20;
    static final int TEXT = 0xFFEEEEEE;
    static final int TEXT_DIM = 0xFFD0D0D0;
    static final int TEXT_OFF = 0xFF7A7A7A;

    static final int BANDS = 12;
    static final int PIECES = 10;

    public record Item(String id, String label, String what, boolean enabled, boolean current) {
    }

    public record Page(String title, List<Item> items) {
    }

    private final String node;
    private final String line;
    private final List<Page> pages;
    private int page;
    private int selected = -1;

    private double k = 1.0;

    public NodeRadial(String node, String line, List<Page> pages, int startPage) {
        this.node = node == null ? "" : node;
        this.line = line == null ? "" : line;
        this.pages = List.copyOf(pages);
        this.page = Math.max(0, Math.min(pages.size() - 1, startPage));
    }

    public int page() {
        return page;
    }

    public List<Page> pages() {
        return pages;
    }

    public boolean scroll(int dir) {
        int next = page + (dir > 0 ? 1 : -1);
        if (dir == 0 || next < 0 || next >= pages.size()) return false;
        page = next;
        selected = -1;
        return true;
    }

    @Nullable
    public Item chosen() {
        List<Item> items = pages.get(page).items();
        if (selected < 0 || selected >= items.size()) return null;
        Item it = items.get(selected);
        return it.enabled() ? it : null;
    }

    public int selected() {
        return selected;
    }

    public void hover(int dx, int dy) {
        selected = sectorAt(dx, dy, pages.get(page).items().size(), DEAD * k);
    }

    public double scale() {
        return k;
    }

    static double scaleFor(int screenH) {
        return Math.max(0.6, Math.min(1.0, (screenH / 2.0 - 22.0) / (OUTER + 10.0)));
    }

    public static int sectorAt(int dx, int dy, int count) {
        return sectorAt(dx, dy, count, DEAD);
    }

    static int sectorAt(int dx, int dy, int count, double dead) {
        if (count <= 0 || Math.hypot(dx, dy) <= dead) return -1;
        double size = 360.0 / count;
        double deg = Math.toDegrees(Math.atan2(dx, dy));
        return (int) Math.floor(((-deg + 180.0 + size / 2.0) % 360.0) / size) % count;
    }

    public void render(ConsoleCanvas c, ConsoleHost host, int screenW, int screenH, int mouseX, int mouseY, float fade) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        k = scaleFor(screenH);
        double inner = INNER * k;
        double outer = OUTER * k;
        hover(mouseX - cx, mouseY - cy);
        int dimA = Math.round(((BACKGROUND >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, fade)));
        c.fill(0, 0, screenW, screenH, (dimA << 24) | (BACKGROUND & 0xFFFFFF));
        c.layer();

        List<Item> items = pages.get(page).items();
        int n = items.size();
        double size = 360.0 / n;
        for (int i = 0; i < n; i++) {
            double a0 = -90.0 + i * size - size / 2.0;
            double a1 = a0 + size;
            boolean on = i == selected && items.get(i).enabled();
            for (int b = 0; b < BANDS; b++) {
                double r0 = inner + (outer - inner) * (double) b / BANDS;
                double r1 = inner + (outer - inner) * (double) (b + 1) / BANDS;
                double t = (b + 0.5) / BANDS;
                arc(c, cx, cy, r0, r1, a0, a1, shade(t, on));
            }
            int ring = on ? mixYellow(0.5f, 0.5f) : white(0.25f);
            arc(c, cx, cy, inner - (INNER - RING_IN), inner - (INNER - RING_OUT), a0, a1, ring);
            if (on) arc(c, cx, cy, outer + (RIM_IN - OUTER), outer + (RIM_OUT - OUTER), a0, a1, mixYellow(0.3f, 0.6f));

            spoke(c, cx, cy, a0, inner + 6, outer - 4, white(0.35f));
        }

        double hub = inner - (INNER - RING_IN) - 1;
        disc(c, cx, cy, hub, 0x90000000);
        c.layer();

        List<Object[]> words = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            double mid = Math.toRadians(-90.0 + i * size);
            int lx = cx + (int) Math.round(Math.cos(mid) * LABEL_R * k);
            int ly = cy + (int) Math.round(Math.sin(mid) * LABEL_R * k);
            int colour = !it.enabled() ? TEXT_OFF : (i == selected ? TEXT : TEXT_DIM);
            int w = host.width(it.label());
            words.add(new Object[]{it.label(), lx - w / 2, ly - 4, colour});
            if (it.current()) c.fill(lx - w / 2, ly + 5, lx - w / 2 + w - 1, ly + 6, 0xFFE4B763);
        }

        int room = (int) Math.floor(hub * 2) - 6;
        Page p = pages.get(page);
        words.add(centredWord(host, fit(host, p.title(), room), cx, cy - 9, TEXT));
        words.add(centredWord(host, fit(host, node, room), cx, cy + 1, TEXT_DIM));
        if (page > 0) {
            chevron(c, cx, cy - (int) Math.round(42 * k), true, TEXT_DIM);
            words.add(centredWord(host, fit(host, pages.get(page - 1).title(), room - 12), cx, cy - (int) Math.round(35 * k), TEXT_DIM));
        }
        if (page < pages.size() - 1) {
            words.add(centredWord(host, fit(host, pages.get(page + 1).title(), room - 12), cx, cy + (int) Math.round(27 * k), TEXT_DIM));
            chevron(c, cx, cy + (int) Math.round(38 * k), false, TEXT_DIM);
        }

        String header = fit(host, line, screenW - 12);
        if (!header.isEmpty()) {
            int w = host.width(header);
            int y = cy - (int) Math.round(outer) - 16;
            c.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x90000000);
            words.add(new Object[]{header, cx - w / 2, y, TEXT});
        }
        Item under = selected >= 0 && selected < n ? items.get(selected) : null;
        String what = fit(host, under != null ? under.what() : host.tr("create_stats.surveyor.radial.how"), screenW - 12);
        if (!what.isEmpty()) {
            int w = host.width(what);
            int y = cy + (int) Math.round(outer) + 10;
            c.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x90000000);
            words.add(new Object[]{what, cx - w / 2, y, under == null || under.enabled() ? TEXT : TEXT_DIM});
        }

        c.layer();
        for (Object[] w : words) c.text((String) w[0], (int) w[1] + 1, (int) w[2] + 1, shadow((int) w[3]));
        c.layer();
        for (Object[] w : words) c.text((String) w[0], (int) w[1], (int) w[2], (int) w[3]);
    }

    private static Object[] centredWord(ConsoleHost host, String s, int cx, int y, int colour) {
        return new Object[]{s, cx - host.width(s) / 2, y, colour};
    }

    static int shadow(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }

    static String fit(ConsoleHost host, String s, int max) {
        if (s == null || s.isEmpty() || host.width(s) <= max) return s == null ? "" : s;
        String dots = "...";
        int dw = host.width(dots);
        if (max <= dw) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (host.width(b.toString() + s.charAt(i)) + dw > max) break;
            b.append(s.charAt(i));
        }
        return b.toString().stripTrailing() + dots;
    }

    private static void disc(ConsoleCanvas c, int cx, int cy, double r, int argb) {
        int pieces = 40;
        for (int q = 0; q < pieces; q++) {
            double t0 = 2 * Math.PI * q / pieces;
            double t1 = 2 * Math.PI * (q + 1) / pieces;
            c.quad((float) (cx + Math.cos(t0) * r), (float) (cy + Math.sin(t0) * r),
                    (float) (cx + Math.cos(t1) * r), (float) (cy + Math.sin(t1) * r),
                    (float) cx, (float) cy, (float) cx, (float) cy, argb);
        }
    }

    private static void centred(ConsoleCanvas c, ConsoleHost host, String s, int cx, int y, int colour) {
        if (s == null || s.isEmpty()) return;
        c.text(s, cx - host.width(s) / 2, y, colour);
    }

    private static void chevron(ConsoleCanvas c, int cx, int y, boolean up, int colour) {
        for (int k = 0; k < 4; k++) {
            int half = up ? k : 3 - k;
            c.fill(cx - half, y + k, cx + half + 1, y + k + 1, colour);
        }
    }

    private static void arc(ConsoleCanvas c, int cx, int cy, double r0, double r1, double a0, double a1, int argb) {
        for (int k = 0; k < PIECES; k++) {
            double t0 = Math.toRadians(a0 + (a1 - a0) * k / PIECES);
            double t1 = Math.toRadians(a0 + (a1 - a0) * (k + 1) / PIECES);
            c.quad((float) (cx + Math.cos(t0) * r1), (float) (cy + Math.sin(t0) * r1),
                    (float) (cx + Math.cos(t1) * r1), (float) (cy + Math.sin(t1) * r1),
                    (float) (cx + Math.cos(t1) * r0), (float) (cy + Math.sin(t1) * r0),
                    (float) (cx + Math.cos(t0) * r0), (float) (cy + Math.sin(t0) * r0), argb);
        }
    }

    private static void spoke(ConsoleCanvas c, int cx, int cy, double a, double r0, double r1, int argb) {
        double half = Math.toDegrees(0.6 / ((r0 + r1) / 2.0));
        arc1(c, cx, cy, r0, r1, a - half, a + half, argb);
    }

    private static void arc1(ConsoleCanvas c, int cx, int cy, double r0, double r1, double a0, double a1, int argb) {
        double t0 = Math.toRadians(a0);
        double t1 = Math.toRadians(a1);
        c.quad((float) (cx + Math.cos(t0) * r1), (float) (cy + Math.sin(t0) * r1),
                (float) (cx + Math.cos(t1) * r1), (float) (cy + Math.sin(t1) * r1),
                (float) (cx + Math.cos(t1) * r0), (float) (cy + Math.sin(t1) * r0),
                (float) (cx + Math.cos(t0) * r0), (float) (cy + Math.sin(t0) * r0), argb);
    }

    static int shade(double t, boolean chosen) {
        float inner = 0.05f;
        float outer = 0.30f;
        float a = (float) (inner + (outer - inner) * t);
        if (!chosen) return white(a);

        float ya = (float) (0.2 + (0.6 - 0.2) * t);
        return mixYellow(a, ya);
    }

    private static int white(float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | 0xFFFFFF;
    }

    private static int mixYellow(float a, float ya) {
        float r = (1.0f + 0.8f) / 2f;
        float g = (1.0f + 0.8f) / 2f;
        float b = (1.0f + 0.2f) / 2f;
        float alpha = (a + ya) / 2f;
        return (Math.round(alpha * 255) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    public static final int[] LIMITS = {0, 2, 4, 6, 8, 12, 16};

    public static final String[] SNAPS = {"off", "grid", "road", "lines"};

    public static final int STOP_PAGE = 0;

    public static final int LINE_PAGE = 4;

    public static List<Page> forNode(ConsoleHost host, Pathway p, int nodeId, String snap, int stretchMark) {
        PathNode node = p.node(nodeId);
        List<Page> pages = new ArrayList<>();
        if (node == null) return pages;
        int index = p.indexOf(nodeId);
        boolean end = !p.closed() && (index == 0 || index == p.nodes().size() - 1);

        int wait = quickWait(node.stop());
        boolean ownRules = node.stop() != null && wait < 0;
        List<Item> stop = new ArrayList<>();
        stop.add(new Item("stop:0", tr(host, "stop_none"), tr(host, "stop_none.what"), !ownRules, node.stop() == null));
        for (int s : Stop.QUICK_WAITS) {
            String word = s < 60 ? s + " s" : (s / 60) + " min";
            stop.add(new Item("stop:" + s, word, host.tr("create_stats.surveyor.radial.stop_wait.what", word), !ownRules, wait == s));
        }
        stop.add(new Item("card", tr(host, "card"), ownRules ? tr(host, "card.own_rules") : tr(host, "card.what"), true, false));
        pages.add(new Page(host.tr("create_stats.surveyor.radial.page.stop"), stop));

        List<Item> speed = new ArrayList<>();
        for (int v : LIMITS) {
            String word = v == 0 ? tr(host, "limit_none") : v + " b/s";
            String what = v == 0 ? host.tr("create_stats.surveyor.radial.limit_none.what", fmt(p.cruise()))
                    : host.tr("create_stats.surveyor.radial.limit.what", word);
            speed.add(new Item("limit:" + v, word, what, v == 0 || v <= p.cruise(), Math.abs(node.speedLimit() - v) < 0.01));
        }
        pages.add(new Page(host.tr("create_stats.surveyor.radial.page.speed"), speed));

        List<Item> corner = new ArrayList<>();
        for (Corner k : Corner.values()) {
            corner.add(new Item("corner:" + k.key, host.tr("create_stats.surveyor.corner." + k.key),
                    host.tr("create_stats.surveyor.radial.corner." + k.key + ".what"), true, node.corner() == k));
        }
        pages.add(new Page(host.tr("create_stats.surveyor.radial.page.corner"), corner));

        List<Item> nodePage = new ArrayList<>();
        nodePage.add(new Item("card", tr(host, "name"), tr(host, "name.what"), true, false));
        nodePage.add(new Item("split", tr(host, "split"), p.closed() ? tr(host, "split.loop") : end ? tr(host, "split.end") : tr(host, "split.what"),
                p.closed() || !end, false));
        nodePage.add(new Item("join", tr(host, "join"), end ? tr(host, "join.what") : tr(host, "join.not_end"), end, false));
        nodePage.add(new Item("junction", tr(host, "junction"), tr(host, "junction.what"), true, false));
        nodePage.add(new Item("delete", tr(host, "delete"), tr(host, "delete.what"), true, false));
        pages.add(new Page(host.tr("create_stats.surveyor.radial.page.node"), nodePage));

        List<Item> line = new ArrayList<>();
        line.add(new Item("reverse", tr(host, "reverse"), tr(host, "reverse.what"), p.kind() != PathKind.LOOP || p.nodes().size() >= 3, false));
        line.add(new Item("duplicate", tr(host, "duplicate"), tr(host, "duplicate.what"), true, false));
        line.add(new Item("simplify", tr(host, "simplify"), tr(host, "simplify.what"), p.nodes().size() >= 3, false));
        line.add(new Item("fit", tr(host, "fit"), tr(host, "fit.what"), p.nodes().size() >= 3, false));
        line.add(new Item("move_line", tr(host, "move_line"), tr(host, "move_line.what"), true, false));
        if (stretchMark >= 0 && stretchMark != nodeId && p.node(stretchMark) != null) {
            line.add(new Item("move_to", tr(host, "move_to"),
                    host.tr("create_stats.surveyor.radial.move_to.what", p.label(stretchMark)), true, false));
        } else {
            line.add(new Item("move_from", tr(host, "move_from"), tr(host, "move_from.what"), p.nodes().size() >= 2,
                    stretchMark == nodeId));
        }
        pages.add(new Page(host.tr("create_stats.surveyor.radial.page.line"), line));

        List<Item> snaps = new ArrayList<>();
        for (String s : SNAPS) {
            snaps.add(new Item("snap:" + s, tr(host, "snap." + s), tr(host, "snap." + s + ".what"), true, s.equals(snap)));
        }
        pages.add(new Page(host.tr("create_stats.surveyor.radial.page.snap"), snaps));
        return pages;
    }

    static int quickWait(@Nullable Stop s) {
        if (s == null) return 0;
        if (s.rules().size() == 1 && s.rules().get(0).size() == 1 && s.rules().get(0).get(0) instanceof StopCondition.Wait w) {
            for (int q : Stop.QUICK_WAITS) if (q == w.seconds()) return q;
        }
        return -1;
    }

    private static String tr(ConsoleHost host, String key) {
        return host.tr("create_stats.surveyor.radial." + key);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
