package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.Corner;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.PathwayCodec;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PathwayBoard extends ConsoleKit {
    public static final int MIN_W = 440;
    public static final int MIN_H = 256;
    public static final int MAX_W = 660;
    public static final int MAX_H = 410;
    public static final int MARGIN = 12;
    static final double MIN_SCALE = 0.04;
    static final double MAX_SCALE = 8.0;

    static final int OP_MOVE = 5;
    static final int OP_DELETE_NODE = 7;
    static final int OP_SET_SPEED = 8;
    static final int OP_SET_CORNER = 9;
    static final int OP_SET_NODE_NAME = 10;
    static final int OP_SET_STOP = 11;
    static final int OP_SET_NAME = 12;
    static final int OP_SET_COLOUR = 13;
    static final int OP_SET_KIND = 14;
    static final int OP_SET_CRUISE = 15;
    static final int OP_SET_COMFORT = 16;
    static final int OP_SET_SHARED = 17;
    static final int OP_DELETE_PATHWAY = 18;
    static final int OP_REVERSE = 19;
    static final int OP_FAIR = 23;
    static final int OP_FIX = 24;
    static final int OP_SET_AMPHIBIOUS = 25;

    static final int FIX_MOVE_NODE = 1;
    static final int FIX_FAIR = 2;
    static final int FIX_AMPHIBIOUS = 3;
    static final int FIX_CLEAR = 4;
    static final int FIX_WIDEN = 5;
    static final int HEALTH_UNKNOWN = 1;
    static final int HEALTH_AMBER = 2;
    static final int HEALTH_RED = 3;

    static final int HEALTH_SHOWN = 6;

    static final int ORDER_UNASSIGN = 2;
    static final int ORDER_START = 3;
    static final int ORDER_PAUSE = 4;
    static final int ORDER_SKIP = 5;
    static final int ORDER_SEND_TO = 6;
    static final int ORDER_RECALL = 7;

    static final int[] DYE = {0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA, 0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
            0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA, 0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21};

    private final BoardHost bh;
    private boolean mine = true;
    @Nullable private UUID selPathway;
    @Nullable private UUID selVehicle;
    private int selNode = -1;

    private double viewX;
    private double viewZ;
    private double scale = 1.0;
    private boolean viewSet;

    private int dragKind;
    private double pressX;
    private double pressY;
    private double pressViewX;
    private double pressViewZ;
    private boolean dragged;
    @Nullable private UUID dragPathway;
    private int dragNode = -1;
    private double dragWorldX;
    private double dragWorldZ;

    private int mapX0;
    private int mapY0;
    private int mapX1;
    private int mapY1;
    private int cardScroll;
    private int cardRows;
    private int cardVisible;
    private long confirmDeleteAt = Long.MIN_VALUE;
    private final Map<UUID, double[][]> polylines = new HashMap<>();
    private final Map<UUID, Long> polylineVersion = new HashMap<>();

    private int centre = CENTRE_MAP;
    static final int CENTRE_MAP = 0;
    static final int CENTRE_STATIONS = 1;
    static final int CENTRE_GRAPH = 2;
    static final String[] CENTRE_KEYS = {"centre.map", "centre.stations", "centre.graph"};

    public PathwayBoard(BoardHost host, @Nullable UUID pathway, @Nullable UUID vehicle, int node) {
        super(host, "gui.create_stats.pathways.");
        this.bh = host;
        this.selPathway = pathway;
        this.selVehicle = vehicle;
        this.selNode = node;
        if (pathway != null) mine = false;
    }

    public static int width(int screenW) {
        return clampInt(screenW - 2 * MARGIN, MIN_W, MAX_W);
    }

    public static int height(int screenH) {
        return clampInt(screenH - 2 * MARGIN, MIN_H, MAX_H);
    }

    @Nullable
    public UUID selectedPathway() {
        return selPathway;
    }

    public void render(ConsoleCanvas canvas, int screenW, int screenH, int mouseX, int mouseY) {
        begin(canvas, screenW, screenH, mouseX, mouseY, width(screenW), height(screenH));
        casing(left, top, w, h);
        brassStrip(left + 3, top + 3, w - 6, 15);
        header();
        int bodyTop = top + 21;
        int bodyBottom = top + h - 5;
        int railW = clampInt(w * 22 / 100, 108, 146);
        int cardW = clampInt(w * 31 / 100, 168, 214);
        int logH = h >= 360 ? 58 : 44;
        int railX0 = left + 5;
        int railX1 = railX0 + railW;
        int cardX1 = left + w - 5;
        int cardX0 = cardX1 - cardW;
        mapX0 = railX1 + 4;
        mapX1 = cardX0 - 4;
        mapY0 = bodyTop + TAB_H + 1;
        mapY1 = bodyBottom - logH - 3;
        rail(railX0, bodyTop, railX1, bodyBottom);
        centreTabs(mapX0, bodyTop);
        switch (centre) {
            case CENTRE_STATIONS -> stations();
            case CENTRE_GRAPH -> graph();
            default -> map();
        }
        log(mapX0, mapY1 + 3, mapX1, bodyBottom);
        card(cardX0, bodyTop, cardX1, bodyBottom);
        end();
    }

    private void header() {
        int pathways = bh.pathways().size();
        int running = 0;
        for (BoardHost.Vehicle v : bh.vehicles()) if (RouteRunner.State.byOrdinal(v.state()).drives()) running++;
        String status = tr("status", pathways, running);
        int sw = host.width(status) + 8;
        int sx = left + w - 6 - sw;
        display(sx, top + 5, sw, 11);
        c.text(status, sx + 4, top + 7, running > 0 ? GOOD : TEXT_DIM);
        textFit(tr("title"), left + 8, top + 7, sx - 12 - (left + 8), ON_BRASS);
    }

    private List<BoardHost.Line> listed() {
        List<BoardHost.Line> out = new ArrayList<>();
        for (BoardHost.Line l : bh.pathways()) if (!mine || l.mine() || l.pathway().id().equals(selPathway)) out.add(l);
        return out;
    }

    private void rail(int x0, int y0, int x1, int y1) {
        sunk(x0, y0, x1 - x0, y1 - y0, SLATE, SLATE_EDGE, SLATE_RIM);
        List<String> labels = List.of(tr("mine"), tr("all"));
        int segW = segmentedWidth(labels);
        segmented(x0 + (x1 - x0 - segW) / 2, y0 + 3, labels, mine ? 0 : 1, List.of(() -> mine = true, () -> mine = false),
                List.of(List.of(tr("mine.hint")), List.of(tr("all.hint"))));
        int listTop = y0 + 3 + BUTTON_H + 4;
        int newY = y1 - BUTTON_H - 3;
        List<BoardHost.Line> lines = listed();
        if (lines.isEmpty()) {
            int ty = listTop + 4;
            for (String l : wrap(tr(mine ? "none_mine" : "none"), x1 - x0 - 10)) {
                c.text(l, x0 + 5, ty, TEXT_FAINT);
                ty += LINE;
            }
        }
        list(x0 + 2, listTop, x1 - 2, newY - 3, lines.size(), "rail", (i, rx0, ry, rx1) -> {
            BoardHost.Line l = lines.get(i);
            Pathway p = l.pathway();
            boolean sel = p.id().equals(selPathway);
            boolean over = hot(rx0, ry, rx1 - rx0, rowH - 1);
            if (sel) c.fill(rx0, ry, rx1, ry + rowH - 1, SLATE_LIGHT);
            else if (over) c.fill(rx0, ry, rx1, ry + rowH - 1, 0xFF404040);
            swatch(rx0 + 3, ry + (rowH - 8) / 2, p.colour());

            int glyphW = l.warn() > 0 ? 6 : 0;
            String length = lengthOf(p) + " b";
            String facts = l.vehicles() > 0 ? l.vehicles() + "× " + length : length;
            int room = rx1 - rx0 - 14 - 6 - glyphW;
            if (room - host.width(facts) < NAME_ROOM) facts = length;
            if (room - host.width(facts) < NAME_ROOM) facts = "";
            int fw = facts.isEmpty() ? 0 : host.width(facts);
            textFit(p.name(), rx0 + 14, ry + (rowH - 8) / 2, room - fw, sel ? TEXT : TEXT_DIM);
            if (!facts.isEmpty()) c.text(facts, rx1 - 2 - fw - glyphW, ry + (rowH - 8) / 2, TEXT_FAINT);
            if (l.warn() > 0) {
                int gx = rx1 - 5;
                int gy = ry + rowH / 2 - 3;
                c.fill(gx, gy, gx + 2, gy + 4, l.warn() == 2 ? BAD : WARN);
                c.fill(gx, gy + 5, gx + 2, gy + 7, l.warn() == 2 ? BAD : WARN);
            }
            List<String> tip = new ArrayList<>();
            tip.add(p.name());
            tip.add(tr(p.kind() == PathKind.LOOP ? "kind.loop" : "kind.one_way") + ", " + p.nodes().size() + " " + tr("nodes")
                    + ", " + p.stopCount() + " " + tr("stops") + ", " + lengthOf(p) + " b");
            tip.add(tr("owner", l.owner()) + (p.shared() ? ", " + tr("shared") : ""));
            if (!l.warning().isEmpty()) tip.add(l.warning());
            final UUID id = p.id();
            hit(rx0, ry, rx1 - rx0, rowH - 1, () -> selectPathway(id, true), null, tip);
        });
        button(x0 + 3, newY, x1 - x0 - 6, tr("new"), false, bh::startNew, null, List.of(tr("new.hint")));
    }

    private void selectPathway(UUID id, boolean frame) {
        boolean changed = !id.equals(selPathway);
        selPathway = id;
        selNode = -1;
        selVehicle = null;
        cardScroll = 0;
        if (frame && changed) fit(id);
    }

    private void swatch(int x, int y, int dye) {
        c.fill(x, y, x + 8, y + 8, EDGE);
        c.fill(x + 1, y + 1, x + 7, y + 7, DYE[dye & 15]);
    }

    private void fit(@Nullable UUID only) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (BoardHost.Line l : bh.pathways()) {
            if (only != null && !l.pathway().id().equals(only)) continue;
            for (PathNode n : l.pathway().nodes()) {
                minX = Math.min(minX, n.x());
                maxX = Math.max(maxX, n.x());
                minZ = Math.min(minZ, n.z());
                maxZ = Math.max(maxZ, n.z());
            }
        }
        if (!Double.isFinite(minX)) {
            double[] you = bh.you();
            if (you != null) {
                viewX = you[0];
                viewZ = you[1];
            }
            scale = 1.0;
            viewSet = true;
            return;
        }
        viewX = (minX + maxX) * 0.5;
        viewZ = (minZ + maxZ) * 0.5;
        double mw = Math.max(40, mapX1 - mapX0 - 24);
        double mh = Math.max(40, mapY1 - mapY0 - 24);
        scale = clamp(Math.min(mw / Math.max(8.0, maxX - minX), mh / Math.max(8.0, maxZ - minZ)), MIN_SCALE, MAX_SCALE);
        viewSet = true;
    }

    private double sx(double x) {
        return (mapX0 + mapX1) * 0.5 + (x - viewX) * scale;
    }

    private double sy(double z) {
        return (mapY0 + mapY1) * 0.5 + (z - viewZ) * scale;
    }

    private double wx(double sx) {
        return viewX + (sx - (mapX0 + mapX1) * 0.5) / scale;
    }

    private double wz(double sy) {
        return viewZ + (sy - (mapY0 + mapY1) * 0.5) / scale;
    }

    private boolean inMap(double x, double y) {
        return x >= mapX0 + 1 && x < mapX1 - 1 && y >= mapY0 + 1 && y < mapY1 - 1;
    }

    private record MapLabel(String text, int x, int y, int colour, int priority) {
    }

    private final List<MapLabel> labelQueue = new ArrayList<>();

    private void map() {
        labelQueue.clear();
        if (!viewSet) fit(selPathway);
        sunk(mapX0, mapY0, mapX1 - mapX0, mapY1 - mapY0, 0xFF1E211E, SLATE_EDGE, SLATE_RIM);
        terrain();
        grid();
        List<BoardHost.Line> lines = listed();

        for (BoardHost.Line l : lines) if (!l.pathway().id().equals(selPathway)) drawLine(l, false);
        for (BoardHost.Line l : lines) if (l.pathway().id().equals(selPathway)) drawLine(l, true);
        for (BoardHost.Line l : lines) drawHealth(l, l.pathway().id().equals(selPathway));
        for (BoardHost.Line l : lines) drawNodes(l, l.pathway().id().equals(selPathway));
        drawVehicles();
        int bx = mapX0 + 5;
        int by = mapY1 - 12;
        double blocks = niceBlocks(40.0 / scale);
        int len = (int) Math.round(blocks * scale);
        String scaleText = (blocks >= 1 ? String.valueOf((long) blocks) : String.format(Locale.ROOT, "%.1f", blocks)) + " b";
        placeLabels(new int[]{bx - 2, by - 2, bx + len + 6 + host.width(scaleText), by + 11}, new int[]{mapX1 - 12, mapY0 + 2, mapX1 - 2, mapY0 + 14});
        double[] you = bh.you();
        if (you != null) {
            double px = sx(you[0]);
            double py = sy(you[1]);
            if (inMap(px, py)) {
                disc(px, py, 3.0, 0xFFFFFFFF);
                disc(px, py, 2.0, 0xFF3AB3DA);
            }
        }

        c.fill(bx, by + 6, bx + len, by + 7, TEXT_DIM);
        c.fill(bx, by + 3, bx + 1, by + 7, TEXT_DIM);
        c.fill(bx + len - 1, by + 3, bx + len, by + 7, TEXT_DIM);
        c.text(scaleText, bx + len + 4, by + 1, TEXT_FAINT);
        c.text("N", mapX1 - 10, mapY0 + 4, TEXT_FAINT);
        hit(mapX0, mapY0, mapX1 - mapX0, mapY1 - mapY0, null, null, List.of(tr("map.hint")));
        if (dragKind == 2 && dragged && dragPathway != null) {
            double px = sx(dragWorldX);
            double py = sy(dragWorldZ);
            if (inMap(px, py)) {
                ring(px, py, 4.0, 6.0, 0xFFFFFFFF);
                c.text(tr("map.drop"), (int) px + 8, (int) py - 4, TEXT);
            }
        }
    }

    private void placeLabels(int[]... reserved) {
        List<MapLabel> queue = new ArrayList<>(labelQueue);
        labelQueue.clear();
        queue.sort(java.util.Comparator.comparingInt(MapLabel::priority));
        List<int[]> placed = new ArrayList<>(List.of(reserved));
        for (MapLabel l : queue) {
            if (l.y() < mapY0 + 2 || l.y() + 10 > mapY1 - 1 || l.x() < mapX0 + 2) continue;
            String shown = fit(l.text(), mapX1 - 4 - l.x());
            if (shown.isEmpty()) continue;
            int[] box = {l.x() - 2, l.y() - 1, l.x() + host.width(shown) + 2, l.y() + 9};
            boolean clear = true;
            for (int[] b : placed) {
                if (box[0] < b[2] && b[0] < box[2] && box[1] < b[3] && b[1] < box[3]) {
                    clear = false;
                    break;
                }
            }
            if (!clear) continue;
            nameplate(shown, l.x() - 2, l.y() - 1, l.colour());
            placed.add(box);
        }
    }

    // so you can see the lake from the board
    private void terrain() {
        List<BoardHost.Terrain> tiles = bh.terrain();
        if (tiles.isEmpty()) return;
        for (BoardHost.Terrain t : tiles) {
            double cellPx = t.cell() * scale;
            if (cellPx < 0.35) continue;
            for (int k = 0; k < t.h(); k++) {
                double z = t.z0() + (double) k * t.cell();
                int y0 = (int) Math.floor(sy(z));
                int y1 = (int) Math.floor(sy(z + t.cell()));
                if (y1 <= mapY0 + 1 || y0 >= mapY1 - 1) continue;
                y0 = Math.max(mapY0 + 1, y0);
                y1 = Math.min(mapY1 - 1, Math.max(y0 + 1, y1));
                int i = 0;
                while (i < t.w()) {
                    int colour = t.argb()[k * t.w() + i];
                    int j = i + 1;
                    while (j < t.w() && t.argb()[k * t.w() + j] == colour) j++;
                    if (colour != 0) {
                        int x0 = (int) Math.floor(sx(t.x0() + (double) i * t.cell()));
                        int x1 = (int) Math.floor(sx(t.x0() + (double) j * t.cell()));
                        x0 = Math.max(mapX0 + 1, x0);
                        x1 = Math.min(mapX1 - 1, x1);
                        if (x1 > x0) c.fill(x0, y0, x1, y1, dim(colour));
                    }
                    i = j;
                }
            }
        }
    }

    private static int dim(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (r * 55 + 0x1E * 45) / 100;
        g = (g * 55 + 0x21 * 45) / 100;
        b = (b * 55 + 0x1E * 45) / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void grid() {
        double cell = 16.0;
        while (cell * scale < 10.0) cell *= 4.0;
        if (cell * scale > 400) return;
        int colour = 0xFF2A2E2A;
        double x0 = wx(mapX0);
        double x1 = wx(mapX1);
        double z0 = wz(mapY0);
        double z1 = wz(mapY1);
        for (double x = Math.floor(x0 / cell) * cell; x <= x1; x += cell) {
            int px = (int) Math.round(sx(x));
            if (px > mapX0 && px < mapX1 - 1) c.fill(px, mapY0 + 1, px + 1, mapY1 - 1, colour);
        }
        for (double z = Math.floor(z0 / cell) * cell; z <= z1; z += cell) {
            int py = (int) Math.round(sy(z));
            if (py > mapY0 && py < mapY1 - 1) c.fill(mapX0 + 1, py, mapX1 - 1, py + 1, colour);
        }
    }

    static double niceBlocks(double want) {
        double p = Math.pow(10, Math.floor(Math.log10(Math.max(0.1, want))));
        double[] steps = {1, 2, 5, 10};
        double best = p;
        for (double s : steps) if (s * p <= want) best = s * p;
        return best;
    }

    static final int NAME_ROOM = 56;

    private long lengthOf(Pathway p) {
        Long v = lengthVersion.get(p.id());
        if (v == null || v != p.version()) {
            lengths.put(p.id(), Math.round(PathSpline.of(p).length()));
            lengthVersion.put(p.id(), p.version());
        }
        return lengths.get(p.id());
    }

    private final Map<UUID, Long> lengths = new HashMap<>();
    private final Map<UUID, Long> lengthVersion = new HashMap<>();

    private double[][] polyline(Pathway p) {
        Long v = polylineVersion.get(p.id());
        if (v != null && v == p.version()) return polylines.get(p.id());
        PathSpline sp = PathSpline.of(p);
        int n = Math.max(2, (int) Math.ceil(sp.length()));
        int count = sp.closed() ? n + 1 : n + 1;
        double[][] out = new double[count][2];
        for (int i = 0; i < count; i++) {
            double s = sp.length() * i / n;
            out[i][0] = sp.x(s);
            out[i][1] = sp.z(s);
        }
        polylines.put(p.id(), out);
        polylineVersion.put(p.id(), p.version());
        polylineLength.put(p.id(), sp.length());
        return out;
    }

    private final Map<UUID, Double> polylineLength = new HashMap<>();

    private void drawHealth(BoardHost.Line l, boolean selected) {
        Pathway p = l.pathway();
        List<BoardHost.Health> items = bh.health(p.id());
        if (items.isEmpty() || p.nodes().size() < 2) return;
        double[][] pts = polyline(p);
        int n = pts.length - 1;
        double len = polylineLength.getOrDefault(p.id(), 0.0);
        if (n < 1 || len <= 0.0) return;
        boolean closed = p.closed();
        for (BoardHost.Health h : items) {
            if (h.level() < HEALTH_AMBER) continue;
            int colour = h.level() == HEALTH_RED ? BAD : WARN;
            if (!selected) colour = alpha(colour, 0xC0);
            int i0 = (int) Math.floor(h.from() / len * n);
            int i1 = (int) Math.ceil(h.to() / len * n);
            if (closed && i1 < i0) i1 += n;
            if (i1 <= i0) i1 = i0 + 1;
            for (int i = i0; i < i1 && i - i0 <= n; i++) {
                int a = closed ? Math.floorMod(i, n) : Math.max(0, Math.min(n, i));
                int b = closed ? Math.floorMod(i + 1, n) : Math.max(0, Math.min(n, i + 1));
                double[] seg = clip(sx(pts[a][0]), sy(pts[a][1]), sx(pts[b][0]), sy(pts[b][1]));
                if (seg != null) line(seg[0], seg[1], seg[2], seg[3], selected ? 3.0 : 2.0, colour);
            }
            if (h.level() == HEALTH_RED && h.node() >= 0) {
                PathNode node = p.node(h.node());
                if (node != null) {
                    double x = sx(node.x());
                    double y = sy(node.z());
                    if (inMap(x, y)) ring(x, y, 5.5, 7.0, BAD);
                }
            }
        }
    }

    private void drawLine(BoardHost.Line l, boolean selected) {
        Pathway p = l.pathway();
        if (p.nodes().size() < 2) return;
        double[][] pts = polyline(p);
        int colour = DYE[p.colour() & 15];
        double width = selected ? 3.0 : 2.0;
        if (!selected) colour = alpha(colour, 0xB0);
        double travelled = 0.0;
        double nextArrow = 24.0;
        for (int i = 1; i < pts.length; i++) {
            double ax = sx(pts[i - 1][0]);
            double ay = sy(pts[i - 1][1]);
            double bx = sx(pts[i][0]);
            double by = sy(pts[i][1]);
            double[] seg = clip(ax, ay, bx, by);
            if (seg != null) {
                if (selected) line(seg[0], seg[1], seg[2], seg[3], width + 2.0, 0xFF000000);
                line(seg[0], seg[1], seg[2], seg[3], width, colour);
            }
            double len = Math.hypot(bx - ax, by - ay);
            travelled += len;
            if (selected && travelled >= nextArrow && seg != null && len > 0.5) {
                nextArrow = travelled + 48.0;
                double ux = (bx - ax) / len;
                double uy = (by - ay) / len;
                if (inMap(bx, by) && inMap(bx - ux * 5, by - uy * 5)) {
                    triangle((float) (bx + ux * 3), (float) (by + uy * 3), (float) (bx - ux * 3 - uy * 3), (float) (by - uy * 3 + ux * 3),
                            (float) (bx - ux * 3 + uy * 3), (float) (by - uy * 3 - ux * 3), 0xFFFFFFFF);
                }
            }
        }
    }

    private void drawNodes(BoardHost.Line l, boolean selected) {
        Pathway p = l.pathway();
        boolean labels = selected && scale >= 0.6;
        int colour = DYE[p.colour() & 15];
        for (int k = 0; k < p.nodes().size(); k++) {
            PathNode n = p.nodes().get(k);
            double x = sx(n.x());
            double y = sy(n.z());
            boolean dragging = dragKind == 2 && dragged && p.id().equals(dragPathway) && n.id() == dragNode;
            if (dragging) {
                x = sx(dragWorldX);
                y = sy(dragWorldZ);
            }
            if (!inMap(x, y)) continue;
            boolean isSel = selected && n.id() == selNode;
            boolean over = popup == null && Math.abs(mx - x) <= 4 && Math.abs(my - y) <= 4;
            if (!selected && !n.isStop() && k != 0 && scale < 1.2 && !over) continue;
            if (n.isStop()) {
                double r = isSel || over ? 5.0 : 4.0;
                quadCw(x, y - r - 1, x + r + 1, y, x, y + r + 1, x - r - 1, y, 0xFF000000);
                quadCw(x, y - r, x + r, y, x, y + r, x - r, y, isSel ? 0xFFFFFFFF : colour);
            } else {
                double half = k == 0 ? 3.0 : (isSel || over ? 2.5 : 1.8);
                square(x, y, half + 1, 0xFF000000);
                square(x, y, half, isSel ? 0xFFFFFFFF : (k == 0 ? 0xFFFFFFFF : colour));
            }
            if (isSel) ring(x, y, 6.0, 7.5, 0xFFFFFFFF);
            if (labels || over) {
                labelQueue.add(new MapLabel(p.label(n.id()), (int) x + 6, (int) y - 10, isSel ? TEXT : TEXT_DIM, isSel ? 1 : (over ? 2 : 3)));
            }
            List<String> tip = new ArrayList<>();
            tip.add(p.label(n.id()) + ", " + p.name());
            tip.add(n.speedLimit() > 0 ? tr("speed_limit_value", fmt(n.speedLimit())) : tr("speed_cruise"));
            tip.add(tr("corner." + n.corner().key));
            if (n.isStop()) tip.add(tr("stop_rules", rules(n.stop())));
            tip.add(tr("map.node_hint"));
            final UUID pid = p.id();
            final int nid = n.id();
            hit((int) x - 4, (int) y - 4, 9, 9, () -> pressNode(pid, nid), null, tip);
        }
    }

    private void drawVehicles() {
        for (BoardHost.Vehicle v : bh.vehicles()) {
            double x = sx(v.x());
            double y = sy(v.z());
            if (!inMap(x, y)) continue;
            BoardHost.Line l = line(v.pathway());
            int colour = l == null ? 0xFFFFFFFF : DYE[l.pathway().colour() & 15];
            boolean sel = v.console().equals(selVehicle);
            double hx = Math.sin(v.heading());
            double hy = -Math.cos(v.heading());
            double r = sel ? 6.5 : 5.0;
            double tx = x + hx * r;
            double ty = y + hy * r;
            double lx = x - hx * r * 0.7 + hy * r * 0.7;
            double ly = y - hy * r * 0.7 - hx * r * 0.7;
            double rx = x - hx * r * 0.7 - hy * r * 0.7;
            double ry = y - hy * r * 0.7 + hx * r * 0.7;
            triangle((float) tx, (float) ty, (float) lx, (float) ly, (float) rx, (float) ry, 0xFF000000);
            triangle((float) (tx - hx), (float) (ty - hy), (float) (lx + hx * 0.6 + hy * 0.4), (float) (ly + hy * 0.6 - hx * 0.4),
                    (float) (rx + hx * 0.6 - hy * 0.4), (float) (ry + hy * 0.6 + hx * 0.4), sel ? 0xFFFFFFFF : colour);
            if (sel || scale >= 0.5) labelQueue.add(new MapLabel(v.name(), (int) x + 7, (int) y + 3, sel ? TEXT : TEXT_DIM, sel ? 0 : 4));
            List<String> tip = new ArrayList<>();
            tip.add(v.name() + ", " + stateWord(v.state()) + (v.reason().isEmpty() ? "" : ": " + v.reason()));
            tip.add(String.format(Locale.ROOT, "%.1f b/s", Math.abs(v.speed())));
            final UUID id = v.console();
            hit((int) x - 6, (int) y - 6, 13, 13, () -> selectVehicle(id), null, tip);
        }
    }

    @Nullable
    private BoardHost.Line line(UUID pathway) {
        for (BoardHost.Line l : bh.pathways()) if (l.pathway().id().equals(pathway)) return l;
        return null;
    }

    @Nullable
    private BoardHost.Vehicle vehicle(UUID console) {
        for (BoardHost.Vehicle v : bh.vehicles()) if (v.console().equals(console)) return v;
        return null;
    }

    private void selectVehicle(UUID console) {
        selVehicle = console;
        selNode = -1;
        BoardHost.Vehicle v = vehicle(console);
        if (v != null) selPathway = v.pathway();
        cardScroll = 0;
    }

    private void pressNode(UUID pathway, int node) {
        selPathway = pathway;
        selNode = node;
        selVehicle = null;
        cardScroll = 0;
    }

    @Nullable
    private double[] clip(double x0, double y0, double x1, double y1) {
        double xmin = mapX0 + 1;
        double ymin = mapY0 + 1;
        double xmax = mapX1 - 1;
        double ymax = mapY1 - 1;
        double t0 = 0.0;
        double t1 = 1.0;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - xmin, xmax - x0, y0 - ymin, ymax - y0};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0e-12) {
                if (q[i] < 0) return null;
                continue;
            }
            double t = q[i] / p[i];
            if (p[i] < 0) t0 = Math.max(t0, t);
            else t1 = Math.min(t1, t);
            if (t0 > t1) return null;
        }
        return new double[]{x0 + dx * t0, y0 + dy * t0, x0 + dx * t1, y0 + dy * t1};
    }

    void showCentre(int which) {
        centre = clampInt(which, CENTRE_MAP, CENTRE_GRAPH);
    }

    private void centreTabs(int x0, int y) {
        int tx = x0;
        for (int i = 0; i < CENTRE_KEYS.length; i++) {
            String name = tr(CENTRE_KEYS[i]);
            int tw = host.width(name) + 12;
            tab(tx, y, tw, name, i == centre, false);
            final int which = i;
            hit(tx, y, tw, TAB_H, () -> centre = which, null, List.of(tr(CENTRE_KEYS[i] + ".hint")));
            tx += tw + 2;
        }
    }

    private record Station(BoardHost.Line line, PathNode node, int index) {
    }

    private List<Station> stationList() {
        List<Station> out = new ArrayList<>();
        for (BoardHost.Line l : listed()) {
            List<PathNode> nodes = l.pathway().nodes();
            for (int i = 0; i < nodes.size(); i++) {
                PathNode n = nodes.get(i);
                if (n.isStop() && !n.name().isEmpty()) out.add(new Station(l, n, i));
            }
        }
        return out;
    }

    private void stations() {
        sunk(mapX0, mapY0, mapX1 - mapX0, mapY1 - mapY0, SLATE, SLATE_EDGE, SLATE_RIM);
        List<Station> all = stationList();
        int x0 = mapX0 + 5;
        int x1 = mapX1 - 5;
        if (all.isEmpty()) {
            int ty = mapY0 + 6;
            for (String l : wrap(tr("stations.none"), x1 - x0)) {
                c.text(l, x0, ty, TEXT_FAINT);
                ty += LINE;
            }
            return;
        }

        boolean narrow = x1 - x0 < 260;
        int lineW = host.width(tr("stations.col.line"));
        for (Station st : all) lineW = Math.max(lineW, 11 + host.width(st.line().pathway().name()));
        lineW = narrow ? 0 : Math.min(lineW + 6, (x1 - x0) / 4);
        int colLine = x0 + (x1 - x0) * (narrow ? 44 : 34) / 100;
        int colNext = colLine + lineW;
        int hy = mapY0 + 4;
        textFit(tr("stations.col.station"), x0, hy, colLine - x0 - 4, BRASS);
        if (!narrow) textFit(tr("stations.col.line"), colLine, hy, colNext - colLine - 4, BRASS);
        textFit(tr("stations.col.next"), colNext, hy, x1 - colNext, BRASS);
        c.fill(mapX0 + 3, hy + 10, mapX1 - 3, hy + 11, SLATE_RIM);
        list(mapX0 + 3, hy + 13, mapX1 - 3, mapY1 - 2, all.size(), "stations", (i, rx0, ry, rx1) -> {
            Station st = all.get(i);
            Pathway p = st.line().pathway();
            boolean sel = p.id().equals(selPathway) && st.node().id() == selNode;
            boolean over = hot(rx0, ry, rx1 - rx0, rowH - 1);
            if (sel) c.fill(rx0, ry, rx1, ry + rowH - 1, SLATE_LIGHT);
            else if (over) c.fill(rx0, ry, rx1, ry + rowH - 1, 0xFF404040);
            int ty = ry + (rowH - 8) / 2;
            if (narrow) {
                swatch(rx0 + 2, ty, p.colour());
                textFit(st.node().name(), rx0 + 13, ty, colLine - rx0 - 17, sel ? TEXT : TEXT_DIM);
            } else {
                textFit(st.node().name(), rx0 + 2, ty, colLine - rx0 - 6, sel ? TEXT : TEXT_DIM);
                swatch(colLine, ty, p.colour());
                textFit(p.name(), colLine + 11, ty, colNext - colLine - 15, TEXT_FAINT);
            }
            String[] due = due(p, st.node());
            textFit(due[0], colNext, ty, rx1 - colNext - 2, due[1].equals("here") ? GOOD : (due[1].equals("none") ? TEXT_FAINT : TEXT));
            List<String> tip = new ArrayList<>();
            tip.add(st.node().name() + ", " + p.name() + " (N" + (st.index() + 1) + ")");
            tip.add(tr("stop_rules", rules(st.node().stop())));
            tip.add(tr("stations.row.hint"));
            final UUID pid = p.id();
            final int nid = st.node().id();
            hit(rx0, ry, rx1 - rx0, rowH - 1, () -> {
                pressNode(pid, nid);
                centre = CENTRE_MAP;
                PathNode n = st.node();
                viewX = n.x();
                viewZ = n.z();
                viewSet = true;
            }, null, tip);
        });
    }

    private String[] due(Pathway p, PathNode station) {
        BoardHost.Vehicle here = null;
        BoardHost.Vehicle next = null;
        for (BoardHost.Vehicle v : bh.vehicles()) {
            if (!v.pathway().equals(p.id()) || v.nextStop() != station.id()) continue;
            if (RouteRunner.State.byOrdinal(v.state()) == RouteRunner.State.WAITING) here = v;
            else if (v.eta() >= 0 && (next == null || v.eta() < next.eta())) next = v;
        }
        if (here != null) return new String[]{tr("stations.here", here.name()), "here"};
        if (next != null) return new String[]{tr("stations.due", next.name(), seconds(Math.round(next.eta()))), "due"};
        return new String[]{tr("stations.none_due"), "none"};
    }

    static final long GRAPH_PAST = 6000;
    static final long GRAPH_AHEAD = 2400;

    static final int[] TRAIL = {0xFF5CE1E6, 0xFFE4B763, 0xFF6FE36F, 0xFFB78CFF, 0xFFFF8A65, 0xFFF48FB1};

    private double[] nodeS(Pathway p) {
        Long v = nodeSVersion.get(p.id());
        if (v != null && v == p.version()) return nodeSCache.get(p.id());
        PathSpline sp = PathSpline.of(p);
        double[] out = new double[p.nodes().size() + 1];
        for (int i = 0; i < p.nodes().size(); i++) out[i] = sp.nodeS(i);
        out[p.nodes().size()] = sp.length();
        nodeSCache.put(p.id(), out);
        nodeSVersion.put(p.id(), p.version());
        return out;
    }

    private final Map<UUID, double[]> nodeSCache = new HashMap<>();
    private final Map<UUID, Long> nodeSVersion = new HashMap<>();

    private void graph() {
        sunk(mapX0, mapY0, mapX1 - mapX0, mapY1 - mapY0, 0xFF1E211E, SLATE_EDGE, SLATE_RIM);
        BoardHost.Line l = selPathway == null ? null : line(selPathway);
        if (l == null || l.pathway().nodes().size() < 2) {
            int ty = mapY0 + 6;
            for (String t : wrap(tr("graph.pick"), mapX1 - mapX0 - 12)) {
                c.text(t, mapX0 + 6, ty, TEXT_FAINT);
                ty += LINE;
            }
            return;
        }
        Pathway p = l.pathway();
        double[] ns = nodeS(p);
        double len = ns[p.nodes().size()];
        if (len < 1.0) return;

        List<Integer> stops = new ArrayList<>();
        int labelW = host.width("N00");
        for (int i = 0; i < p.nodes().size(); i++) {
            if (!p.nodes().get(i).isStop()) continue;
            stops.add(i);
            labelW = Math.max(labelW, host.width(p.label(p.nodes().get(i).id())));
        }
        labelW = Math.min(labelW, (mapX1 - mapX0) / 3);
        int gx0 = mapX0 + 6 + labelW + 4;
        int gx1 = mapX1 - 8;
        int gy0 = mapY0 + 7;
        int gy1 = mapY1 - 14;
        if (gx1 - gx0 < 40 || gy1 - gy0 < 30) return;
        long now = bh.now();
        long t0 = now - GRAPH_PAST;
        double span = GRAPH_PAST + GRAPH_AHEAD;
        java.util.function.DoubleUnaryOperator tx = t -> gx0 + (t - t0) / span * (gx1 - gx0);
        java.util.function.DoubleUnaryOperator sy = v -> gy0 + v / len * (gy1 - gy0);

        int lastLabel = Integer.MIN_VALUE;
        for (long m = -GRAPH_PAST / 1200; m <= GRAPH_AHEAD / 1200; m++) {
            int x = (int) Math.round(tx.applyAsDouble(now + m * 1200));
            c.fill(x, gy0, x + 1, gy1, m == 0 ? 0xFF6A6A6A : 0xFF2E322E);
            String word = m == 0 ? tr("graph.now") : (m > 0 ? "+" : "") + m + " " + tr("graph.min");
            int ww = host.width(word);
            if (x - ww / 2 > lastLabel + 6 && x - ww / 2 >= gx0 - 8 && x + ww / 2 <= mapX1 - 3) {
                c.text(word, x - ww / 2, gy1 + 3, m == 0 ? TEXT_DIM : TEXT_FAINT);
                lastLabel = x + ww / 2;
            }
        }

        int lastStopY = Integer.MIN_VALUE;
        for (int i : stops) {
            int y = (int) Math.round(sy.applyAsDouble(ns[i]));
            c.fill(gx0, y, gx1, y + 1, 0xFF4A4A4A);
            if (y - 4 > lastStopY + 9) {
                textFit(p.label(p.nodes().get(i).id()), mapX0 + 6, y - 4, labelW, TEXT_DIM);
                lastStopY = y - 4;
            }
        }

        c.fill(gx0, gy0, gx1, gy0 + 1, 0xFF3A3A3A);
        c.fill(gx0, gy1 - 1, gx1, gy1, 0xFF3A3A3A);

        List<BoardHost.Trail> trails = new ArrayList<>(bh.trails(p.id()));
        trails.sort(java.util.Comparator.comparing(BoardHost.Trail::name));
        boolean closed = p.closed();
        int k = 0;
        for (BoardHost.Trail t : trails) {
            int colour = TRAIL[k++ % TRAIL.length];
            double px = Double.NaN, py = 0;
            int plap = 0;
            for (int i = 0; i < t.time().length; i++) {
                if (t.time()[i] < t0) {
                    px = Double.NaN;
                    continue;
                }
                double x = tx.applyAsDouble(t.time()[i]);
                double y = sy.applyAsDouble(Math.max(0.0, Math.min(len, t.s()[i])));
                if (!Double.isNaN(px)) {
                    if (closed && t.laps()[i] != plap) {
                        double mid = (px + x) / 2.0;
                        line(px, py, mid, gy1, 1.5, colour);
                        line(mid, gy0, x, y, 1.5, colour);
                    } else if (Math.abs(y - py) < (gy1 - gy0) * 0.6 || !closed) {
                        line(px, py, x, y, 1.5, colour);
                    }
                }
                px = x;
                py = y;
                plap = t.laps()[i];
            }
            BoardHost.Vehicle v = vehicle(t.console());
            if (v != null && !Double.isNaN(px)) {
                double nx = tx.applyAsDouble(now);
                double ny = sy.applyAsDouble(Math.max(0.0, Math.min(len, v.s())));
                int idx = v.nextStop() < 0 ? -1 : p.indexOf(v.nextStop());
                if (idx >= 0 && v.eta() >= 0) {
                    double ex = tx.applyAsDouble(now + v.eta() * 20.0);
                    double ey = sy.applyAsDouble(ns[idx]);
                    if (closed && ey < ny) {
                        double f = (len - v.s()) / Math.max(1e-6, len - v.s() + ns[idx]);
                        double mx2 = nx + (ex - nx) * f;
                        dashed(nx, ny, mx2, gy1, colour);
                        dashed(mx2, gy0, ex, ey, colour);
                    } else {
                        dashed(nx, ny, ex, ey, colour);
                    }
                }
                disc(nx, ny, 2.5, colour);
                labelQueueGraph.add(new int[]{(int) nx + 5, (int) ny - 4, colour});
                labelNames.add(t.name());
            }
        }

        List<int[]> placed = new ArrayList<>();
        for (int i = 0; i < labelQueueGraph.size(); i++) {
            int[] at = labelQueueGraph.get(i);
            String name = fit(labelNames.get(i), mapX1 - 4 - at[0]);
            if (name.isEmpty()) continue;
            int[] box = {at[0] - 2, at[1] - 1, at[0] + host.width(name) + 2, at[1] + 9};
            boolean clear = box[1] >= mapY0 + 2 && box[3] <= mapY1 - 2;
            for (int[] b : placed) if (box[0] < b[2] && b[0] < box[2] && box[1] < b[3] && b[1] < box[3]) clear = false;
            if (!clear) continue;
            nameplate(name, at[0] - 2, at[1] - 1, at[2]);
            placed.add(box);
        }
        labelQueueGraph.clear();
        labelNames.clear();
        if (trails.isEmpty()) {
            textFit(tr("graph.no_vehicles"), gx0 + 4, gy0 + 3, gx1 - gx0 - 8, TEXT_FAINT);
        }
        hit(mapX0, mapY0, mapX1 - mapX0, mapY1 - mapY0, null, null, List.of(tr("graph.hint")));
    }

    private final List<int[]> labelQueueGraph = new ArrayList<>();
    private final List<String> labelNames = new ArrayList<>();

    private void dashed(double x0, double y0, double x1, double y1, int colour) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 0.5) return;
        int pieces = (int) Math.ceil(len / 4.0);
        for (int i = 0; i < pieces; i++) {
            double a = i * 4.0 / len;
            double b = Math.min(1.0, (i * 4.0 + 2.0) / len);
            line(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, 1.2, colour);
        }
    }

    private void log(int x0, int y0, int x1, int y1) {
        int body = card(x0, y0, x1 - x0, y1 - y0, tr("log"));
        List<BoardHost.LogLine> lines = new ArrayList<>();
        for (BoardHost.LogLine l : bh.log()) if (selPathway == null || l.pathway().equals(selPathway) || !mine) lines.add(l);
        if (lines.isEmpty()) {
            textFit(tr("log.empty"), x0 + 5, body, x1 - x0 - 10, TEXT_FAINT);
            return;
        }
        int rows = Math.max(1, (y1 - 3 - body) / LINE);
        for (int i = 0; i < Math.min(rows, lines.size()); i++) {
            BoardHost.LogLine l = lines.get(i);
            String when = ago(bh.now() - l.time());
            int ww = host.width(when);
            c.text(when, x0 + 5, body + i * LINE, TEXT_FAINT);
            textFit(l.text(), x0 + 5 + Math.max(ww, host.width("59 min ago")) + 6, body + i * LINE,
                    x1 - 5 - (x0 + 5 + Math.max(ww, host.width("59 min ago")) + 6), i == 0 ? TEXT : TEXT_DIM);
        }
    }

    String ago(long ticks) {
        long s = Math.max(0, ticks / 20);
        if (s < 5) return tr("ago.now");
        if (s < 60) return tr("ago.s", s);
        if (s < 3600) return tr("ago.min", s / 60);
        return tr("ago.h", s / 3600);
    }

    private record Row(int height, RowPainter paint) {
    }

    private interface RowPainter {
        void paint(int x0, int y, int x1);
    }

    private int cardTextW = 150;

    private void card(int x0, int y0, int x1, int y1) {
        cardTextW = Math.max(40, x1 - x0 - 14);
        List<Row> rows = new ArrayList<>();
        String title;
        BoardHost.Line line = selPathway == null ? null : line(selPathway);
        BoardHost.Vehicle vehicle = selVehicle == null ? null : vehicle(selVehicle);

        if (vehicle != null && line == null) line = line(vehicle.pathway());
        PathNode node = line == null || selNode < 0 ? null : line.pathway().node(selNode);
        if (vehicle != null) {
            title = vehicle.name();
            vehicleRows(rows, vehicle, line);
        } else if (node != null) {
            title = line.pathway().label(node.id()) + (node.name().isEmpty() ? "" : " (N" + line.pathway().numberOf(node.id()) + ")");
            nodeRows(rows, line, node);
        } else if (line != null) {
            title = line.pathway().name();
            pathwayRows(rows, line);
        } else {
            title = tr("card.none_title");
            for (String l : List.of(tr("card.none_1"), tr("card.none_2"), tr("card.none_3"))) {
                rows.add(textRow(l, TEXT_DIM, x1 - x0 - 12));
            }
        }
        int body = card(x0, y0, x1 - x0, y1 - y0, title);
        int avail = y1 - 3 - body;
        int total = 0;
        for (Row r : rows) total += r.height();
        cardRows = rows.size();
        cardVisible = avail;
        int maxScroll = Math.max(0, total - avail);
        cardScroll = clampInt(cardScroll, 0, maxScroll);
        int y = body - cardScroll;
        boolean bar = maxScroll > 0;
        int rx1 = bar ? x1 - 9 : x1 - 5;
        for (Row r : rows) {
            if (y >= body - 1 && y + r.height() <= body + avail + 1) r.paint().paint(x0 + 5, y, rx1);
            y += r.height();
        }
        if (bar) {
            int sh = avail - 2;
            sunk(x1 - 7, body, 5, sh, DISPLAY, SLATE_EDGE, SLATE_RIM);
            int knob = Math.max(10, sh * avail / Math.max(1, total));
            int ky = body + (sh - knob) * cardScroll / Math.max(1, maxScroll);
            raised(x1 - 7, ky, 5, knob, SLOT, CASING, CASING_SHADE);
        }
        cardArea = new int[]{x0, body, x1, body + avail};
    }

    private int[] cardArea = new int[4];

    private Row textRow(String text, int colour, int max) {
        List<String> lines = wrap(text, Math.min(max, cardTextW));
        return new Row(Math.max(1, lines.size()) * LINE + 2, (x0, y, x1) -> {
            int ty = y;
            for (String l : wrap(text, x1 - x0)) {
                c.text(l, x0, ty, colour);
                ty += LINE;
            }
        });
    }

    private Row gap(int h) {
        return new Row(h, (x0, y, x1) -> {
        });
    }

    private Row heading(String text) {
        return new Row(13, (x0, y, x1) -> {
            c.fill(x0, y + 10, x1, y + 11, SLATE_RIM);
            textFit(text, x0, y + 1, x1 - x0, BRASS);
        });
    }

    private void pathwayRows(List<Row> rows, BoardHost.Line l) {
        Pathway p = l.pathway();
        boolean edit = l.editable();
        rows.add(textRow(tr("card.facts", p.nodes().size(), lengthOf(p), p.stopCount(), l.vehicles()), TEXT, 180));
        rows.add(textRow(tr("owner", l.owner()) + (p.shared() ? ", " + tr("shared") : ""), TEXT_FAINT, 180));
        if (!l.warning().isEmpty()) rows.add(textRow(l.warning(), l.warn() == 2 ? BAD : WARN, 180));
        rows.add(gap(3));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            button(x0, y, bw, tr("use_with_staff"), false, () -> bh.useWithStaff(p.id()), null, List.of(tr("use_with_staff.hint")));
            button(x0 + bw + 3, y, x1 - x0 - bw - 3, tr("rename"), false, edit ? () -> bh.askText(p.name(), Pathway.MAX_NAME,
                    s -> bh.sendEdit(OP_SET_NAME, p.id(), p.version(), -1, 0, 0, 0, 0, s, new byte[0])) : null, null, List.of(tr("rename.hint")));
        }));
        healthRows(rows, l, edit);
        rows.add(heading(tr("colour")));
        rows.add(new Row(20, (x0, y, x1) -> {
            int size = Math.max(8, Math.min(12, (x1 - x0) / 8 - 1));
            for (int i = 0; i < 16; i++) {
                int sx = x0 + (i % 8) * (size + 1);
                int sy = y + (i / 8) * (size - 1);
                int sh = size - 2;
                c.fill(sx, sy, sx + size, sy + sh, i == p.colour() ? 0xFFFFFFFF : EDGE);
                c.fill(sx + 1, sy + 1, sx + size - 1, sy + sh - 1, DYE[i]);
                final int dye = i;
                hit(sx, sy, size, sh, edit ? () -> bh.sendEdit(OP_SET_COLOUR, p.id(), p.version(), -1, 0, 0, 0, dye, "", new byte[0]) : null,
                        null, List.of(tr("dye." + i)));
            }
        }));
        rows.add(heading(tr("kind")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> segmented(x0, y, List.of(tr("kind.loop"), tr("kind.one_way")),
                p.kind() == PathKind.LOOP ? 0 : 1,
                List.of(edit ? () -> bh.sendEdit(OP_SET_KIND, p.id(), p.version(), -1, 0, 0, 0, PathKind.LOOP.ordinal(), "", new byte[0]) : null,
                        edit ? () -> bh.sendEdit(OP_SET_KIND, p.id(), p.version(), -1, 0, 0, 0, PathKind.ONE_WAY.ordinal(), "", new byte[0]) : null),
                List.of(List.of(tr("kind.loop.hint")), List.of(tr("kind.one_way.hint"))))));
        rows.add(heading(tr("cruise")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            String value = tr("speed_value", fmt(p.cruise()));
            int vw = host.width(tr("speed_value", "40.0"));
            stepper(x0, y, value, vw, edit ? () -> bh.sendEdit(OP_SET_CRUISE, p.id(), p.version(), -1, p.cruise() - 0.5, 0, 0, 0, "", new byte[0]) : null,
                    edit ? () -> bh.sendEdit(OP_SET_CRUISE, p.id(), p.version(), -1, p.cruise() + 0.5, 0, 0, 0, "", new byte[0]) : null,
                    List.of(tr("cruise.hint")));
        }));
        rows.add(heading(tr("comfort")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            List<List<String>> tips = new ArrayList<>();
            for (Comfort cf : Comfort.values()) {
                labels.add(tr("comfort." + cf.key));
                actions.add(edit ? () -> bh.sendEdit(OP_SET_COMFORT, p.id(), p.version(), -1, 0, 0, 0, cf.ordinal(), "", new byte[0]) : null);
                tips.add(List.of(tr("comfort." + cf.key + ".hint")));
            }
            segmented(x0, y, labels, p.comfort().ordinal(), actions, tips);
        }));
        rows.add(gap(3));
        rows.add(new Row(13, (x0, y, x1) -> checkbox(x0, y + 2, p.shared(), tr("shared.toggle"),
                edit && l.mine() ? () -> bh.sendEdit(OP_SET_SHARED, p.id(), p.version(), -1, 0, 0, 0, p.shared() ? 0 : 1, "", new byte[0]) : () -> {
                }, List.of(tr("shared.hint")))));
        List<BoardHost.Vehicle> on = new ArrayList<>();
        for (BoardHost.Vehicle v : bh.vehicles()) if (v.pathway().equals(p.id())) on.add(v);
        if (!on.isEmpty()) {
            rows.add(heading(tr("vehicles")));
            for (BoardHost.Vehicle v : on) {
                rows.add(new Row(rowH, (x0, y, x1) -> {
                    boolean over = hot(x0, y, x1 - x0, rowH - 1);
                    if (over) c.fill(x0 - 2, y, x1, y + rowH - 1, 0xFF404040);
                    String st = stateWord(v.state());
                    int stw = host.width(st);
                    textFit(v.name(), x0, y + (rowH - 8) / 2, x1 - x0 - stw - 6, TEXT);
                    c.text(st, x1 - stw, y + (rowH - 8) / 2, stateColour(v.state()));
                    hit(x0, y, x1 - x0, rowH - 1, () -> selectVehicle(v.console()), null, List.of(tr("vehicle.pick_hint")));
                }));
            }
        }
        rows.add(gap(4));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            button(x0, y, bw, tr("reverse"), false, edit ? () -> bh.sendEdit(OP_REVERSE, p.id(), p.version(), -1, 0, 0, 0, 0, "", new byte[0]) : null,
                    null, List.of(tr("reverse.hint")));
            // MIN_VALUE wrap again, the first click deleted the pathway outright. yine mi

            boolean armed = confirmDeleteAt != Long.MIN_VALUE && bh.now() - confirmDeleteAt < 60;
            button(x0 + bw + 3, y, x1 - x0 - bw - 3, tr(armed ? "delete.confirm" : "delete"), armed, edit ? () -> {
                if (confirmDeleteAt != Long.MIN_VALUE && bh.now() - confirmDeleteAt < 60) {
                    bh.sendEdit(OP_DELETE_PATHWAY, p.id(), p.version(), -1, 0, 0, 0, 0, "", new byte[0]);
                    confirmDeleteAt = Long.MIN_VALUE;
                    selPathway = null;
                } else {
                    confirmDeleteAt = bh.now();
                }
            } : null, null, List.of(tr("delete.hint")));
        }));
    }

    private void healthRows(List<Row> rows, BoardHost.Line l, boolean edit) {
        Pathway p = l.pathway();
        rows.add(heading(tr("health")));
        List<BoardHost.Health> items = bh.health(p.id());
        if (!bh.healthKnown(p.id())) {
            rows.add(textRow(tr("health.surveying"), TEXT_DIM, 180));
        } else {
            int listed = 0;
            boolean unknown = false;
            for (BoardHost.Health h : items) {
                if (h.level() < HEALTH_AMBER) {
                    unknown |= h.level() == HEALTH_UNKNOWN;
                    continue;
                }
                if (listed++ >= HEALTH_SHOWN) continue;
                rows.add(findingRow(p, h));
                String fix = fixLabel(p, h);
                if (fix != null) {
                    rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> button(x0 + 6, y, x1 - x0 - 6, fix, false,
                            edit ? () -> bh.sendEdit(OP_FIX, p.id(), p.version(), h.node(), h.at(), 0, 0, h.fix(), "", new byte[0]) : null,
                            null, List.of(tr(fixKey(h.fix()) + ".hint")))));
                }
            }
            if (listed > HEALTH_SHOWN) rows.add(textRow(tr("health.more", listed - HEALTH_SHOWN), TEXT_FAINT, 180));
            if (unknown) rows.add(textRow(tr("health.unknown"), TEXT_FAINT, 180));
            if (listed == 0 && !unknown) rows.add(textRow(tr("health.fine"), GOOD, 180));
        }
        rows.add(gap(2));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> button(x0, y, x1 - x0, tr("fair"), false,
                edit ? () -> bh.sendEdit(OP_FAIR, p.id(), p.version(), -1, 0, 0, 0, 0, "", new byte[0]) : null, null,
                List.of(tr("fair.hint")))));
        rows.add(new Row(13, (x0, y, x1) -> checkbox(x0, y + 2, p.amphibious(), tr("amphibious.toggle"),
                edit ? () -> bh.sendEdit(OP_SET_AMPHIBIOUS, p.id(), p.version(), -1, 0, 0, 0, p.amphibious() ? 0 : 1, "", new byte[0]) : () -> {
                }, List.of(tr("amphibious.hint")))));
    }

    private Row findingRow(Pathway p, BoardHost.Health h) {
        int colour = h.level() == HEALTH_RED ? BAD : WARN;
        List<String> lines = wrap(h.why(), Math.max(20, cardTextW - 6));
        return new Row(Math.max(1, lines.size()) * LINE + 2, (x0, y, x1) -> {
            c.fill(x0, y + 2, x0 + 3, y + 5, colour);
            int ty = y;
            for (String line : wrap(h.why(), x1 - x0 - 6)) {
                c.text(line, x0 + 6, ty, colour);
                ty += LINE;
            }
            hit(x0, y, x1 - x0, Math.max(LINE, ty - y), () -> showOnMap(p, h), null, List.of(tr("health.show_hint")));
        });
    }

    private void showOnMap(Pathway p, BoardHost.Health h) {
        PathNode n = h.node() >= 0 ? p.node(h.node()) : null;
        if (n != null) {
            viewX = n.x();
            viewZ = n.z();
        } else {
            PathSpline sp = PathSpline.of(p);
            viewX = sp.x(h.at());
            viewZ = sp.z(h.at());
        }
        viewSet = true;
        scale = Math.max(scale, 2.0);
    }

    @Nullable
    private String fixLabel(Pathway p, BoardHost.Health h) {
        String key = fixKey(h.fix());
        if (key == null) return null;
        return h.fix() == FIX_MOVE_NODE ? tr(key, h.node() >= 0 ? p.label(h.node()) : "?") : tr(key);
    }

    @Nullable
    static String fixKey(int fix) {
        return switch (fix) {
            case FIX_MOVE_NODE -> "fix.move";
            case FIX_FAIR -> "fix.fair";
            case FIX_AMPHIBIOUS -> "fix.amphibious";
            case FIX_WIDEN -> "fix.widen";
            default -> null;
        };
    }

    private void vehicleRows(List<Row> rows, BoardHost.Vehicle v, @Nullable BoardHost.Line l) {
        Pathway p = l == null ? null : l.pathway();
        rows.add(new Row(15, (x0, y, x1) -> {
            String st = stateWord(v.state()).toUpperCase(Locale.ROOT);
            int cw = chip(x0, y + 1, st, stateColour(v.state()));
            if (!v.reason().isEmpty()) textFit(v.reason(), x0 + cw + 5, y + 3, x1 - x0 - cw - 5, TEXT_DIM);
        }));
        rows.add(textRow(tr("vehicle.on", p == null ? "?" : p.name()), TEXT, 180));
        rows.add(textRow(tr("vehicle.speed", String.format(Locale.ROOT, "%.1f", Math.abs(v.speed()))), TEXT_DIM, 180));
        if (p != null && v.nextStop() >= 0 && p.node(v.nextStop()) != null) {
            String eta = v.eta() >= 0 ? tr("vehicle.eta", seconds(Math.round(v.eta()))) : "";
            rows.add(textRow(tr("vehicle.next", p.label(v.nextStop())) + (eta.isEmpty() ? "" : ", " + eta), TEXT_DIM, 180));
            PathNode n = p.node(v.nextStop());
            if (n.isStop()) rows.add(textRow(rules(n.stop()), TEXT_FAINT, 180));
        }
        if (v.waitFraction() >= 0) {
            rows.add(new Row(11, (x0, y, x1) -> bar(x0, y + 1, x1 - x0, 8, v.waitFraction(), v.waitFraction() >= 1 ? GOOD : INFO)));
            if (!v.waitText().isEmpty()) rows.add(textRow(v.waitText(), TEXT_DIM, 180));
        }
        if (v.laps() > 0 && v.lastLap() > 0) rows.add(textRow(tr("vehicle.laps", v.laps(), seconds(Math.round(v.lastLap()))), TEXT_FAINT, 180));
        if (v.cargo() >= 0) rows.add(textRow(tr("vehicle.cargo", v.cargo()), TEXT_FAINT, 180));
        rows.add(gap(4));
        RouteRunner.State state = RouteRunner.State.byOrdinal(v.state());
        boolean running = state.drives();
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            button(x0, y, bw, tr(running ? "order.pause" : "order.start"), false,
                    () -> bh.sendOrder(running ? ORDER_PAUSE : ORDER_START, v.console(), new UUID(0, 0), 0), null,
                    List.of(tr(running ? "order.pause.hint" : "order.start.hint")));
            button(x0 + bw + 3, y, x1 - x0 - bw - 3, tr("order.skip"), false,
                    running ? () -> bh.sendOrder(ORDER_SKIP, v.console(), new UUID(0, 0), 0) : null, null, List.of(tr("order.skip.hint")));
        }));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            button(x0, y, bw, tr("order.send"), false, p == null || p.stopCount() == 0 ? null : () -> {
                List<String> labels = new ArrayList<>();
                List<Runnable> actions = new ArrayList<>();
                for (PathNode n : p.nodes()) {
                    if (!n.isStop()) continue;
                    labels.add(p.label(n.id()));
                    actions.add(() -> bh.sendOrder(ORDER_SEND_TO, v.console(), new UUID(0, 0), n.id()));
                }
                openPopup(x0, y + BUTTON_H + 1, labels, actions, -1);
            }, null, List.of(tr("order.send.hint")));
            button(x0 + bw + 3, y, x1 - x0 - bw - 3, tr("order.recall"), false,
                    () -> bh.sendOrder(ORDER_RECALL, v.console(), new UUID(0, 0), 0), null, List.of(tr("order.recall.hint")));
        }));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            button(x0, y, bw, tr("order.show"), false, () -> bh.pin(v.console()), null, List.of(tr("order.show.hint")));
            button(x0 + bw + 3, y, x1 - x0 - bw - 3, tr("order.unassign"), false,
                    () -> bh.sendOrder(ORDER_UNASSIGN, v.console(), new UUID(0, 0), 0), null, List.of(tr("order.unassign.hint")));
        }));
    }

    private void nodeRows(List<Row> rows, BoardHost.Line l, PathNode n) {
        Pathway p = l.pathway();
        boolean edit = l.editable();
        rows.add(textRow(tr("node.of", p.name(), p.numberOf(n.id()), p.nodes().size()), TEXT_DIM, 180));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            button(x0, y, bw, tr("rename"), false, edit ? () -> bh.askText(n.name(), PathNode.MAX_NAME,
                    s -> bh.sendEdit(OP_SET_NODE_NAME, p.id(), p.version(), n.id(), 0, 0, 0, 0, s, new byte[0])) : null, null,
                    List.of(tr("node.rename.hint")));
            button(x0 + bw + 3, y, x1 - x0 - bw - 3, tr("node.back"), false, () -> selNode = -1, null, List.of(tr("node.back.hint")));
        }));
        rows.add(heading(tr("speed_limit")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            String value = n.speedLimit() > 0 ? tr("speed_value", fmt(n.speedLimit())) : tr("speed_none");
            int vw = Math.max(host.width(tr("speed_value", "40.0")), host.width(tr("speed_none")));
            double down = n.speedLimit() > 0 ? n.speedLimit() - 0.5 : p.cruise() - 0.5;
            double up = n.speedLimit() > 0 ? n.speedLimit() + 0.5 : 0.0;
            stepper(x0, y, value, vw, edit ? () -> bh.sendEdit(OP_SET_SPEED, p.id(), p.version(), n.id(), Math.max(1.0, down), 0, 0, 0, "", new byte[0]) : null,
                    edit ? () -> bh.sendEdit(OP_SET_SPEED, p.id(), p.version(), n.id(), up >= p.cruise() ? 0.0 : up, 0, 0, 0, "", new byte[0]) : null,
                    List.of(tr("speed_limit.hint")));
        }));
        rows.add(heading(tr("corner")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            List<List<String>> tips = new ArrayList<>();
            for (Corner cr : Corner.values()) {
                labels.add(tr("corner." + cr.key));
                actions.add(edit ? () -> bh.sendEdit(OP_SET_CORNER, p.id(), p.version(), n.id(), 0, 0, 0, cr.ordinal(), "", new byte[0]) : null);
                tips.add(List.of(tr("corner." + cr.key + ".hint")));
            }
            segmented(x0, y, labels, n.corner().ordinal(), actions, tips);
        }));
        rows.add(gap(3));
        rows.add(new Row(13, (x0, y, x1) -> checkbox(x0, y + 2, n.isStop(), tr("stop.toggle"),
                edit ? () -> setStop(p, n, n.isStop() ? null : Stop.DEFAULT) : () -> {
                }, List.of(tr("stop.hint")))));
        if (n.isStop()) stopRows(rows, p, n, n.stop(), edit);
        rows.add(gap(4));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> button(x0, y, x1 - x0, tr("node.delete"), false,
                edit ? () -> {
                    bh.sendEdit(OP_DELETE_NODE, p.id(), p.version(), n.id(), 0, 0, 0, 0, "", new byte[0]);
                    selNode = -1;
                } : null, null, List.of(tr("node.delete.hint")))));
    }

    private void setStop(Pathway p, PathNode n, @Nullable Stop stop) {
        byte[] data = stop == null ? new byte[0] : PathwayCodec.encodeStop(stop.sane());
        bh.sendEdit(OP_SET_STOP, p.id(), p.version(), n.id(), 0, 0, 0, 0, "", data);
    }

    private void stopRows(List<Row> rows, Pathway p, PathNode n, Stop stop, boolean edit) {
        rows.add(heading(tr("stop.leave_when")));
        List<List<StopCondition>> rules = stop.rules();
        for (int a = 0; a < rules.size(); a++) {
            final int alt = a;
            if (a > 0) rows.add(new Row(11, (x0, y, x1) -> textCentre(tr("stop.or"), (x0 + x1) / 2, y + 1, BRASS)));
            List<StopCondition> conds = rules.get(a);
            for (int k = 0; k < conds.size(); k++) {
                final int idx = k;
                StopCondition cond = conds.get(k);
                rows.add(conditionRow(p, n, stop, alt, idx, cond, edit));
                if (cond instanceof StopCondition.Items || cond instanceof StopCondition.Fluid || cond instanceof StopCondition.Link
                        || cond instanceof StopCondition.TimeOfDay) {
                    rows.add(conditionDetailRow(p, n, stop, alt, idx, cond, edit));
                }
            }
            rows.add(new Row(BUTTON_H + 2, (x0, y, x1) -> {
                boolean room = rules.get(alt).size() < Stop.MAX_CONDITIONS;
                button(x0, y, tr("stop.and"), false, edit && room ? () -> setStop(p, n, withCondition(stop, alt, -1,
                        new StopCondition.Wait(10))) : null, List.of(tr("stop.and.hint")));
            }));
        }
        boolean docks = false;
        for (List<StopCondition> col : rules) for (StopCondition cnd : col) docks |= cnd instanceof StopCondition.Docked;
        if (docks) rows.add(textRow(tr("stop.dock_note"), TEXT_FAINT, 180));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            boolean room = rules.size() < Stop.MAX_ALTERNATIVES;
            button(x0, y, tr("stop.add_or"), false, edit && room ? () -> {
                List<List<StopCondition>> r = new ArrayList<>(stop.rules());
                r.add(List.of(new StopCondition.Wait(60)));
                setStop(p, n, stop.withRules(r));
            } : null, List.of(tr("stop.add_or.hint")));
        }));
        rows.add(heading(tr("stop.leave_after")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int[] steps = {0, 30, 60, 120, 300, 600, 1800, 3600};
            int at = 0;
            for (int i = 0; i < steps.length; i++) if (steps[i] <= stop.leaveAfter()) at = i;
            String value = stop.leaveAfter() <= 0 ? tr("never") : seconds(stop.leaveAfter());
            int vw = Math.max(host.width(tr("never")), host.width("30 min"));
            final int cur = at;
            stepper(x0, y, value, vw, edit ? () -> setStop(p, n, stop.withLeaveAfter(steps[Math.max(0, cur - 1)])) : null,
                    edit ? () -> setStop(p, n, stop.withLeaveAfter(steps[Math.min(steps.length - 1, cur + 1)])) : null,
                    List.of(tr("stop.leave_after.hint")));
        }));
        rows.add(heading(tr("stop.docked")));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int bw = (x1 - x0 - 3) / 2;
            frequencyButton(x0, y, bw, stop.dockFirst(), edit, id -> setStop(p, n, stop.withDock(id, stop.dockSecond())));
            frequencyButton(x0 + bw + 3, y, x1 - x0 - bw - 3, stop.dockSecond(), edit, id -> setStop(p, n, stop.withDock(stop.dockFirst(), id)));
        }));
        rows.add(heading(tr("stop.departure")));
        rows.add(new Row(13, (x0, y, x1) -> checkbox(x0, y + 2, stop.indicate(), tr("stop.indicate"),
                edit ? () -> setStop(p, n, stop.withIndicate(!stop.indicate())) : () -> {
                }, List.of(tr("stop.indicate.hint")))));
        rows.add(new Row(BUTTON_H + 3, (x0, y, x1) -> {
            List<String> labels = List.of(tr("aux.none"), "A", "B", "C", "D");
            List<Runnable> actions = new ArrayList<>();
            for (int i = -1; i < 4; i++) {
                final int aux = i;
                actions.add(edit ? () -> setStop(p, n, stop.withHornAux(aux)) : null);
            }
            int lw = host.width(tr("stop.horn")) + 4;
            c.text(tr("stop.horn"), x0, y + 3, TEXT_DIM);
            segmented(x0 + lw, y, labels, stop.hornAux() + 1, actions, List.of(List.of(tr("stop.horn.hint"))));
        }));
    }

    private Row conditionRow(Pathway p, PathNode n, Stop stop, int alt, int idx, StopCondition cond, boolean edit) {
        return new Row(BUTTON_H + 2, (x0, y, x1) -> {
            String kind = tr("cond." + cond.kind());

            int kw = host.width(kind) + 12;
            button(x0, y, kw, kind, false, edit ? () -> {
                List<String> labels = new ArrayList<>();
                List<Runnable> actions = new ArrayList<>();
                StopCondition[] kinds = {new StopCondition.Wait(10), new StopCondition.Idle(5),
                        new StopCondition.Items(StopCondition.Op.GREATER, 1000, false, ""), new StopCondition.Fluid(StopCondition.Op.GREATER, 8, ""),
                        new StopCondition.Link("", "", true), new StopCondition.TimeOfDay(8, 0, 0), new StopCondition.Players(1, true),
                        new StopCondition.Powered(), new StopCondition.Docked()};
                for (StopCondition k : kinds) {
                    labels.add(tr("cond." + k.kind()));
                    actions.add(() -> setStop(p, n, withCondition(stop, alt, idx, k)));
                }
                openPopup(x0, y + BUTTON_H + 1, labels, actions, -1);
            } : null, null, List.of(tr("cond.kind.hint")));
            int vx = x0 + kw + 3;
            int removeW = 12;
            int room = x1 - vx - removeW - 3;
            switch (cond) {
                case StopCondition.Wait wt -> secondsStepper(vx, y, room, wt.seconds(), edit, s -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Wait(s))));
                case StopCondition.Idle id -> secondsStepper(vx, y, room, id.seconds(), edit, s -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Idle(Math.max(1, s)))));
                case StopCondition.Items it -> {
                    int opW = 14;
                    button(vx, y, opW, it.op().symbol, false, edit ? () -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Items(StopCondition.Op.byOrdinal((it.op().ordinal() + 1) % 3), it.amount(), it.stacks(), it.item()))) : null,
                            null, List.of(tr("cond.op.hint")));
                    amountStepper(vx + opW + 2, y, room - opW - 2, it.amount(), edit, a -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Items(it.op(), a, it.stacks(), it.item()))));
                }
                case StopCondition.Fluid fl -> {
                    int opW = 14;
                    button(vx, y, opW, fl.op().symbol, false, edit ? () -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Fluid(StopCondition.Op.byOrdinal((fl.op().ordinal() + 1) % 3), fl.buckets(), fl.fluid()))) : null,
                            null, List.of(tr("cond.op.hint")));
                    amountStepper(vx + opW + 2, y, room - opW - 2, fl.buckets(), edit, a -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Fluid(fl.op(), a, fl.fluid()))));
                }
                case StopCondition.Link lk -> {
                    String state = tr(lk.powered() ? "cond.link.powered" : "cond.link.unpowered");
                    button(vx, y, Math.min(room, host.width(state) + 10), state, false, edit ? () -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Link(lk.first(), lk.second(), !lk.powered()))) : null, null, List.of(tr("cond.link.hint")));
                }
                case StopCondition.TimeOfDay td -> {
                    int at = td.hour() * 4 + td.minute() / 15;
                    String value = String.format(Locale.ROOT, "%d:%02d", td.hour(), td.minute() / 15 * 15);
                    int vw = Math.max(host.width("23:45"), Math.min(room - stepperWidth(0), host.width(value)));
                    stepper(vx, y, value, vw, edit ? () -> {
                        int q = Math.floorMod(at - 1, 96);
                        setStop(p, n, withCondition(stop, alt, idx, new StopCondition.TimeOfDay(q / 4, q % 4 * 15, td.rotation())));
                    } : null, edit ? () -> {
                        int q = Math.floorMod(at + 1, 96);
                        setStop(p, n, withCondition(stop, alt, idx, new StopCondition.TimeOfDay(q / 4, q % 4 * 15, td.rotation())));
                    } : null, List.of(tr("cond.time_of_day.hint")));
                }
                case StopCondition.Players pl -> {
                    String mode = tr(pl.orMore() ? "cond.players.or_more" : "cond.players.exactly");
                    int mw = host.width(mode) + 10;
                    button(vx, y, mw, mode, false, edit ? () -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Players(pl.count(), !pl.orMore()))) : null, null, List.of(tr("cond.players.hint")));
                    int vw = Math.max(host.width("20"), Math.min(room - mw - 2 - stepperWidth(0), host.width(String.valueOf(pl.count()))));
                    stepper(vx + mw + 2, y, String.valueOf(pl.count()), vw,
                            edit ? () -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Players(Math.max(0, pl.count() - 1), pl.orMore()))) : null,
                            edit ? () -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Players(Math.min(20, pl.count() + 1), pl.orMore()))) : null,
                            List.of(tr("cond.players.hint")));
                }
                case StopCondition.Powered pw -> c.text(fit(tr("cond.powered.what"), room), vx, y + 3, TEXT_DIM);
                case StopCondition.Docked dk -> c.text(fit(tr("cond.docked.what"), room), vx, y + 3, TEXT_DIM);
            }
            button(x1 - removeW, y, removeW, "x", false, edit ? () -> setStop(p, n, withCondition(stop, alt, idx, null)) : null, null,
                    List.of(tr("cond.remove.hint")));
        });
    }

    private Row conditionDetailRow(Pathway p, PathNode n, Stop stop, int alt, int idx, StopCondition cond, boolean edit) {
        return new Row(BUTTON_H + 3, (x0, y, x1) -> {
            int indent = 10;
            switch (cond) {
                case StopCondition.Items it -> {
                    String unit = tr(it.stacks() ? "cond.stacks" : "cond.items_unit");
                    int uw = host.width(tr("cond.stacks")) + 10;
                    button(x0 + indent, y, uw, unit, false, edit ? () -> setStop(p, n, withCondition(stop, alt, idx,
                            new StopCondition.Items(it.op(), it.amount(), !it.stacks(), it.item()))) : null, null, List.of(tr("cond.unit.hint")));
                    filterButton(x0 + indent + uw + 3, y, x1 - (x0 + indent + uw + 3), it.item(), edit, false,
                            id -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Items(it.op(), it.amount(), it.stacks(), id))));
                }
                case StopCondition.Fluid fl -> filterButton(x0 + indent, y, x1 - x0 - indent, fl.fluid(), edit, true,
                        id -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Fluid(fl.op(), fl.buckets(), id))));
                case StopCondition.Link lk -> {
                    int bw = (x1 - x0 - indent - 3) / 2;
                    frequencyButton(x0 + indent, y, bw, lk.first(), edit,
                            id -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Link(id, lk.second(), lk.powered()))));
                    frequencyButton(x0 + indent + bw + 3, y, x1 - x0 - indent - bw - 3, lk.second(), edit,
                            id -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.Link(lk.first(), id, lk.powered()))));
                }
                case StopCondition.TimeOfDay td -> {
                    String label = tr("cond.time_of_day.every." + td.rotation());
                    button(x0 + indent, y, x1 - x0 - indent, fit(label, x1 - x0 - indent - 6), false, edit ? () -> setStop(p, n,
                            withCondition(stop, alt, idx, new StopCondition.TimeOfDay(td.hour(), td.minute(),
                                    (td.rotation() + 1) % StopCondition.TimeOfDay.ROTATION_TICKS.length))) : null,
                            edit ? () -> setStop(p, n, withCondition(stop, alt, idx, new StopCondition.TimeOfDay(td.hour(), td.minute(),
                                    Math.floorMod(td.rotation() - 1, StopCondition.TimeOfDay.ROTATION_TICKS.length)))) : null,
                            List.of(tr("cond.time_of_day.every.hint")));
                }
                default -> {
                }
            }
        });
    }

    private void secondsStepper(int x, int y, int room, int value, boolean edit, java.util.function.IntConsumer set) {
        int[] steps = {0, 1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800, 3600};
        int at = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] <= value) at = i;
        final int cur = at;
        int vw = Math.max(host.width("30 min"), Math.min(room - 32, host.width(seconds(value))));
        stepper(x, y, seconds(value), vw, edit ? () -> set.accept(steps[Math.max(0, cur - 1)]) : null,
                edit ? () -> set.accept(steps[Math.min(steps.length - 1, cur + 1)]) : null, List.of(tr("cond.seconds.hint")));
    }

    private void amountStepper(int x, int y, int room, int value, boolean edit, java.util.function.IntConsumer set) {
        int[] steps = {0, 1, 2, 4, 8, 16, 32, 64, 100, 128, 256, 500, 1000, 1728, 2000, 3456, 5000, 10000, 20000, 50000, 100000};
        int at = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] <= value) at = i;
        final int cur = at;
        int vw = Math.max(host.width(String.valueOf(value)), Math.min(host.width("100000"), room - stepperWidth(0)));
        stepper(x, y, String.valueOf(value), vw, edit ? () -> set.accept(steps[Math.max(0, cur - 1)]) : null,
                edit ? () -> set.accept(steps[Math.min(steps.length - 1, cur + 1)]) : null, List.of(tr("cond.amount.hint")));
    }

    private void frequencyButton(int x, int y, int bw, String id, boolean edit, java.util.function.Consumer<String> set) {
        String label = id.isEmpty() ? tr("freq.none") : bh.itemName(id);
        String held = bh.heldItem();
        button(x, y, bw, fit(label, bw - 6), !id.isEmpty(), edit ? () -> set.accept(held) : null, edit ? () -> set.accept("") : null,
                List.of(tr("freq.hint"), held.isEmpty() ? tr("freq.hand_empty") : tr("freq.hand", bh.itemName(held))));
    }

    private void filterButton(int x, int y, int bw, String id, boolean edit, boolean fluid, java.util.function.Consumer<String> set) {
        String label = id.isEmpty() ? tr("filter.any") : bh.itemName(id);
        String held = fluid ? bh.heldFluid() : bh.heldItem();
        button(x, y, bw, fit(label, bw - 6), !id.isEmpty(), edit ? () -> set.accept(held) : null, edit ? () -> set.accept("") : null,
                List.of(tr(fluid ? "filter.fluid_hint" : "filter.item_hint"), held.isEmpty() ? tr("freq.hand_empty") : tr("freq.hand", bh.itemName(held))));
    }

    static Stop withCondition(Stop stop, int alt, int idx, @Nullable StopCondition with) {
        List<List<StopCondition>> rules = new ArrayList<>();
        for (int a = 0; a < stop.rules().size(); a++) {
            List<StopCondition> conds = new ArrayList<>(stop.rules().get(a));
            if (a == alt) {
                if (idx < 0) {
                    if (with != null) conds.add(with);
                } else if (idx < conds.size()) {
                    if (with == null) conds.remove(idx);
                    else conds.set(idx, with);
                }
            }
            if (!conds.isEmpty()) rules.add(conds);
        }
        return stop.withRules(rules);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (popup == null && centre == CENTRE_MAP && button == 0 && x >= mapX0 && x < mapX1 && y >= mapY0 && y < mapY1) {
            pressX = x;
            pressY = y;
            dragged = false;
            dragKind = 1;
            pressViewX = viewX;
            pressViewZ = viewZ;
            for (BoardHost.Line l : listed()) {
                if (!l.editable()) continue;
                for (PathNode n : l.pathway().nodes()) {
                    if (Math.abs(sx(n.x()) - x) <= 4 && Math.abs(sy(n.z()) - y) <= 4) {
                        dragKind = 2;
                        dragPathway = l.pathway().id();
                        dragNode = n.id();
                        dragWorldX = n.x();
                        dragWorldZ = n.z();
                    }
                }
            }
            return click(x, y, button) || true;
        }
        return click(x, y, button);
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (dragKind == 0) return false;
        if (!dragged && Math.hypot(x - pressX, y - pressY) < 3.0) return true;
        dragged = true;
        if (dragKind == 1) {
            viewX = pressViewX - (x - pressX) / scale;
            viewZ = pressViewZ - (y - pressY) / scale;
        } else {
            dragWorldX = wx(x);
            dragWorldZ = wz(y);
        }
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        int kind = dragKind;
        dragKind = 0;
        if (kind == 2 && dragged && dragPathway != null) {
            BoardHost.Line l = line(dragPathway);
            PathNode n = l == null ? null : l.pathway().node(dragNode);
            if (n != null) {
                double gy = bh.groundY(dragWorldX, dragWorldZ, n.y());
                bh.sendEdit(OP_MOVE, l.pathway().id(), l.pathway().version(), n.id(), dragWorldX, gy, dragWorldZ, 0, "", new byte[0]);
                host.click();
            }
        } else if (kind == 1 && !dragged) {
            UUID nearest = nearestLine(x, y);
            if (nearest != null) selectPathway(nearest, false);
            else {
                selNode = -1;
                selVehicle = null;
            }
        }
        dragPathway = null;
        dragNode = -1;
        return kind != 0;
    }

    @Nullable
    private UUID nearestLine(double x, double y) {
        UUID best = null;
        double bestD = 5.0;
        for (BoardHost.Line l : listed()) {
            double[][] pts = polyline(l.pathway());
            for (double[] pt : pts) {
                double d = Math.hypot(sx(pt[0]) - x, sy(pt[1]) - y);
                if (d < bestD) {
                    bestD = d;
                    best = l.pathway().id();
                }
            }
        }
        return best;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dy) {
        if (popup == null && centre == CENTRE_MAP && x >= mapX0 && x < mapX1 && y >= mapY0 && y < mapY1 && dy != 0.0) {
            double before = wx(x);
            double beforeZ = wz(y);
            scale = clamp(scale * (dy > 0 ? 1.25 : 0.8), MIN_SCALE, MAX_SCALE);
            viewX += before - wx(x);
            viewZ += beforeZ - wz(y);
            return true;
        }
        if (popup == null && x >= cardArea[0] && x < cardArea[2] && y >= cardArea[1] && y < cardArea[3] && dy != 0.0) {
            cardScroll = Math.max(0, cardScroll + (dy > 0 ? -LINE * 3 : LINE * 3));
            return true;
        }
        return super.mouseScrolled(x, y, dy);
    }

    public boolean keyPressed(int key) {
        if (key == KEY_ESCAPE && popup != null) {
            popup = null;
            return true;
        }
        return false;
    }

    String stateWord(int state) {
        return host.tr("create_stats.surveyor.state." + RouteRunner.State.byOrdinal(state).key);
    }

    static int stateColour(int state) {
        return switch (RouteRunner.State.byOrdinal(state)) {
            case RUNNING, APPROACHING, DEPARTING, JOINING -> GOOD;
            case WAITING, CALIBRATING, RECORDING -> INFO;
            case BLOCKED, STUCK, NO_BUDGET -> WARN;
            case LOST -> BAD;
            default -> TEXT_DIM;
        };
    }

    String rules(Stop stop) {
        List<String> alts = new ArrayList<>();
        for (List<StopCondition> alt : stop.rules()) {
            List<String> parts = new ArrayList<>();
            for (StopCondition c : alt) parts.add(condition(c));
            alts.add(String.join(" " + tr("stop.and_word") + " ", parts));
        }
        String s = alts.isEmpty() ? tr("stop.touch") : String.join(" " + tr("stop.or").toLowerCase(Locale.ROOT) + " ", alts);
        if (stop.leaveAfter() > 0) s += ", " + tr("stop.leave_after_short", seconds(stop.leaveAfter()));
        return s;
    }

    String condition(StopCondition c) {
        return switch (c) {
            case StopCondition.Wait w -> tr("cond.wait.short", seconds(w.seconds()));
            case StopCondition.Idle i -> tr("cond.idle.short", seconds(i.seconds()));
            case StopCondition.Items i -> tr("cond.items.short", i.op().symbol, i.amount(),
                    tr(i.stacks() ? "cond.stacks" : "cond.items_unit"), i.item().isEmpty() ? tr("filter.any") : bh.itemName(i.item()));
            case StopCondition.Fluid f -> tr("cond.fluid.short", f.op().symbol, f.buckets(), f.fluid().isEmpty() ? tr("filter.any") : bh.itemName(f.fluid()));
            case StopCondition.Link l -> tr(l.powered() ? "cond.link.short_on" : "cond.link.short_off");
            case StopCondition.TimeOfDay t -> tr("cond.time_of_day.short", String.format(Locale.ROOT, "%d:%02d", t.hour(), t.minute()),
                    tr("cond.time_of_day.every." + t.rotation()));
            case StopCondition.Players pl -> tr(pl.orMore() ? "cond.players.short_or_more" : "cond.players.short", pl.count());
            case StopCondition.Powered pw -> tr("cond.powered.short");
            case StopCondition.Docked d -> tr("cond.docked.short");
        };
    }

    String seconds(long s) {
        if (s < 60) return tr("time.s", s);
        long m = s / 60;
        long r = s % 60;
        return r == 0 ? tr("time.min", m) : tr("time.min_s", m, r);
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
