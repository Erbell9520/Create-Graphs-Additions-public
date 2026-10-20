package com.createstats.vccmk4.console;

import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.VectorGimbal;
import com.createstats.vccmk4.network.Vcc4Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.createstats.vccmk4.console.Vcc4Console.BOARD_FACE;
import static com.createstats.vccmk4.console.Vcc4Console.FACE;
import static com.createstats.vccmk4.console.Vcc4Console.HIGHLIGHT;
import static com.createstats.vccmk4.console.Vcc4Console.INK;
import static com.createstats.vccmk4.console.Vcc4Console.JET_ON;
import static com.createstats.vccmk4.console.Vcc4Console.LABEL;
import static com.createstats.vccmk4.console.Vcc4Console.OUTLINE;
import static com.createstats.vccmk4.console.Vcc4Console.RECESSED;
import static com.createstats.vccmk4.console.Vcc4Console.SHADOW;
import static com.createstats.vccmk4.console.Vcc4Console.WARN;
import static com.createstats.vccmk4.console.Vcc4Console.WHEEL_DRIVEN;

final class BoardView {
    private final Vcc4Console con;
    private final ConsoleCanvas c;
    private final ConsoleHost host;
    private final Vcc4Snapshot s;
    private final int x0;
    private final int y0;
    private final int x1;
    private final int y1;
    private final List<int[]> taken = new ArrayList<>();
    private final List<Runnable> labels = new ArrayList<>();
    private double scale;
    private double midLat;
    private double topLon;
    private double originY;
    private int midX;

    private BoardView(Vcc4Console con, int x0, int y0, int x1, int y1) {
        this.con = con;
        this.c = con.canvas();
        this.host = con.host();
        this.s = con.snapshot();
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    static void draw(Vcc4Console con, int x0, int y0, int x1, int y1) {
        con.well(x0, y0, x1 - x0, y1 - y0, BOARD_FACE);
        Vcc4Snapshot s = con.snapshot();
        int ix0 = x0 + 3;
        int iy0 = y0 + 3;
        int ix1 = x1 - 3;
        int iy1 = y1 - 3;
        String empty = null;
        if (!s.onCraft()) empty = con.tr("not_on_craft");
        else if (s.wheels().isEmpty() && s.jets().isEmpty() && s.swivels().isEmpty() && s.machines().isEmpty()
                && s.links().isEmpty()) empty = con.tr("nothing_found");
        if (empty != null) {
            int y = iy0 + 6;
            for (String line : con.wrap(empty, ix1 - ix0 - 12)) {
                con.canvas().text(line, ix0 + 6, y, LABEL);
                y += Vcc4Console.LINE;
            }
            return;
        }
        new BoardView(con, ix0, iy0, ix1, iy1).plan();
    }

    private int px(double lateral) {
        return (int) Math.round(midX + (lateral - midLat) * scale);
    }

    private int py(double longitudinal) {
        return (int) Math.round(originY + (topLon - longitudinal) * scale);
    }

    private void fitScale() {
        double latLo = -0.5;
        double latHi = 0.5;
        double lonLo = -0.5;
        double lonHi = 0.5;
        List<double[]> points = new ArrayList<>();
        for (Vcc4Snapshot.Wheel w : s.wheels()) points.add(new double[]{w.lateral(), w.longitudinal(), 1.0});
        for (Vcc4Snapshot.Jet j : s.jets()) points.add(new double[]{j.lateral(), j.longitudinal(), 0.6});
        for (Vcc4Snapshot.Swivel a : s.swivels()) points.add(new double[]{a.lateral(), a.longitudinal(), 0.6});
        for (Vcc4Snapshot.Governor g : s.governors()) points.add(new double[]{g.lateral(), g.longitudinal(), 0.4});
        for (Vcc4Snapshot.Machine m : s.machines()) points.add(new double[]{m.lateral(), m.longitudinal(), 0.6});
        for (Vcc4Snapshot.Link l : s.links()) points.add(new double[]{l.lateral(), l.longitudinal(), 0.4});
        for (Vcc4Snapshot.Body b : s.bodies()) points.add(new double[]{b.lateral(), b.longitudinal(), 0.4});
        for (Vcc4Snapshot.Tie t : s.ties()) {
            points.add(new double[]{t.latA(), t.lonA(), 0.4});
            points.add(new double[]{t.latB(), t.lonB(), 0.4});
        }
        points.add(new double[]{s.comLateral(), s.comLongitudinal(), 0.4});
        for (double[] p : points) {
            latLo = Math.min(latLo, p[0] - p[2]);
            latHi = Math.max(latHi, p[0] + p[2]);
            lonLo = Math.min(lonLo, p[1] - p[2]);
            lonHi = Math.max(lonHi, p[1] + p[2]);
        }

        int marginX = 26;
        int marginTop = 18;
        int marginBottom = 10;
        double sx = (x1 - x0 - 2.0 * marginX) / (latHi - latLo);
        double sy = (y1 - y0 - marginTop - marginBottom) / (lonHi - lonLo);
        scale = Math.max(3.0, Math.min(40.0, Math.min(sx, sy)));
        midLat = (latLo + latHi) * 0.5;
        midX = (x0 + x1) / 2;
        topLon = lonHi;
        double drawnH = (lonHi - lonLo) * scale;
        double room = y1 - y0 - marginTop - marginBottom;
        originY = y0 + marginTop + Math.max(0.0, (room - drawnH) * 0.5);
    }

    private void plan() {
        fitScale();
        int wx = clamp((int) Math.round(scale * 0.25), 2, 7);
        int wy = clamp((int) Math.round(scale * 0.45), 4, 14);

        int fx = (x0 + x1) / 2;
        for (int r = 0; r < 5; r++) c.fill(fx - r, y0 + 2 + r, fx + r + 1, y0 + 3 + r, INK);
        String front = con.tr("front");
        c.text(front, fx + 7, y0 + 2, INK);
        taken.add(new int[]{fx - 5, y0, fx + 8 + host.width(front), y0 + 10});

        boolean anyOwn = false;
        double lLo = Double.MAX_VALUE;
        double lHi = -Double.MAX_VALUE;
        double zLo = Double.MAX_VALUE;
        double zHi = -Double.MAX_VALUE;
        for (Vcc4Snapshot.Wheel w : s.wheels()) {
            if (w.body() != 0) continue;
            anyOwn = true;
            lLo = Math.min(lLo, w.lateral());
            lHi = Math.max(lHi, w.lateral());
            zLo = Math.min(zLo, w.longitudinal());
            zHi = Math.max(zHi, w.longitudinal());
        }
        if (anyOwn) {
            int cx0 = px(lLo) - wx - 3;
            int cx1 = px(lHi) + wx + 4;
            int cy0 = py(zHi) - wy - 3;
            int cy1 = py(zLo) + wy + 4;
            outline(cx0, cy0, cx1, cy1, INK);
            outline(cx0 + 1, cy0 + 1, cx1 - 1, cy1 - 1, HIGHLIGHT);

            SteerPreset preset = SteerPreset.byOrdinal(s.preset());
            boolean anySteer = false;
            for (int i = 0; i < s.roles().length; i++) anySteer |= s.roles()[i] == AxleRole.STEER.ordinal();
            if (anySteer && preset != SteerPreset.CRAB && preset != SteerPreset.SKID) {
                int yv = py(pivot());
                for (int x = cx0 - 6; x < cx1 + 6; x += 4) c.fill(x, yv, x + 2, yv + 1, INK);
                String pivot = con.tr("pivot");
                int tw = host.width(pivot);
                if (cx1 + 8 + tw <= x1) {
                    c.text(pivot, cx1 + 8, yv - 4, INK);
                    taken.add(new int[]{cx1 + 7, yv - 5, cx1 + 9 + tw, yv + 5});
                }
            }
            for (int i = 0; i < s.axleZ().length; i++) {
                int ay = py(s.axleZ()[i]);
                double lo = Double.MAX_VALUE;
                double hi = -Double.MAX_VALUE;
                for (Vcc4Snapshot.Wheel w : s.wheels()) {
                    if (w.axle() != i || w.body() != 0) continue;
                    lo = Math.min(lo, w.lateral());
                    hi = Math.max(hi, w.lateral());
                }
                if (lo <= hi) {
                    c.fill(px(lo), ay, px(hi), ay + 2, INK);
                    taken.add(new int[]{px(lo), ay - 1, px(hi), ay + 3});
                }
                String label = "A" + (i + 1);
                int tw = host.width(label);
                int lx = cx0 - 4 - tw;
                if (lx >= x0 && !collides(lx - 1, ay - 5, lx + tw + 1, ay + 5)) {
                    c.text(label, lx, ay - 4, INK);
                    taken.add(new int[]{lx - 1, ay - 5, lx + tw + 1, ay + 5});
                }
            }
        }
        for (Vcc4Snapshot.Body b : s.bodies()) body(b, wx, wy);
        for (Vcc4Snapshot.Tie t : s.ties()) tie(t);

        int comX = px(s.comLateral());
        int comY = py(s.comLongitudinal());
        c.fill(comX - 4, comY, comX + 5, comY + 1, s.comMeasured() ? INK : SHADOW);
        c.fill(comX, comY - 4, comX + 1, comY + 5, s.comMeasured() ? INK : SHADOW);
        outline(comX - 2, comY - 2, comX + 3, comY + 3, s.comMeasured() ? INK : SHADOW);
        taken.add(new int[]{comX - 4, comY - 4, comX + 5, comY + 5});
        List<String> comTip = new ArrayList<>();
        comTip.add(con.tr("com"));
        comTip.add(con.tr(s.comMeasured() ? "com.measured" : "com.estimated"));
        con.hit(comX - 4, comY - 4, 9, 9, null, null, comTip);

        for (int i = 0; i < s.wheels().size(); i++) wheel(i, wx, wy);
        for (int i = 0; i < s.governors().size(); i++) governor(i);
        for (int i = 0; i < s.machines().size(); i++) machine(i);
        for (int i = 0; i < s.swivels().size(); i++) swivel(i);
        for (int i = 0; i < s.links().size(); i++) link(i);
        for (int i = 0; i < s.jets().size(); i++) jet(i);

        int sx = px(0);
        int sy = py(0);
        c.fill(sx - 2, sy - 2, sx + 3, sy + 3, OUTLINE);
        c.fill(sx - 1, sy - 1, sx + 2, sy + 2, HIGHLIGHT);
        taken.add(new int[]{sx - 2, sy - 2, sx + 3, sy + 3});
        con.hit(sx - 3, sy - 3, 7, 7, null, null, List.of(con.tr("console_here")));

        for (Runnable label : labels) label.run();
    }

    private double pivot() {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < s.axleZ().length; i++) {
            if (i < s.roles().length && s.roles()[i] == AxleRole.FIXED.ordinal()) {
                sum += s.axleZ()[i];
                n++;
            }
        }
        if (n > 0) return sum / n;
        return s.axleZ().length == 0 ? 0 : (s.axleZ()[0] + s.axleZ()[s.axleZ().length - 1]) * 0.5;
    }

    private boolean lit(PartIndex.Kind kind, int index) {
        return con.focus().contains(new PartIndex.Ref(kind, index));
    }

    private void body(Vcc4Snapshot.Body b, int wx, int wy) {
        double lLo = Double.MAX_VALUE;
        double lHi = -Double.MAX_VALUE;
        double zLo = Double.MAX_VALUE;
        double zHi = -Double.MAX_VALUE;
        boolean any = false;
        for (Vcc4Snapshot.Wheel w : s.wheels()) {
            if (w.body() != b.index()) continue;
            any = true;
            lLo = Math.min(lLo, w.lateral());
            lHi = Math.max(lHi, w.lateral());
            zLo = Math.min(zLo, w.longitudinal());
            zHi = Math.max(zHi, w.longitudinal());
        }
        int colour = b.foreign() ? SHADOW : INK;
        int jx = px(b.lateral());
        int jy = py(b.longitudinal());
        c.fill(jx - 1, jy - 1, jx + 2, jy + 2, colour);
        taken.add(new int[]{jx - 2, jy - 2, jx + 3, jy + 3});
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("tip.body", "B" + (b.index() + 1)));
        tip.add(con.trailerWhat(b));
        tip.add(con.linkText(b));
        if (b.takesMode()) tip.add(con.tr("body.mode_hint", con.tr("trailermode." + TrailerMode.byOrdinal(b.mode()).key())));
        if (!b.placed()) tip.add(con.tr("body.unplaced.hint"));
        con.hit(jx - 3, jy - 3, 7, 7, () -> con.setPage(b.takesMode() ? Vcc4Console.Page.TRAILERS : Vcc4Console.Page.PARTS), null, tip);
        String name = "B" + (b.index() + 1);
        if (!any) {
            label(name, jx, jy, 3, colour, false);
            return;
        }
        int cx0 = px(lLo) - wx - 3;
        int cx1 = px(lHi) + wx + 4;
        int cy0 = py(zHi) - wy - 3;
        int cy1 = py(zLo) + wy + 4;
        dotted(cx0, cy0, cx1, cy1, colour);

        labels.add(() -> {
            int tw = host.width(name);
            int[][] spots = {{cx1 + 3, cy1 - 9}, {cx0 - 3 - tw, cy1 - 9}, {cx1 + 3, cy0}, {cx0 - 3 - tw, cy0}};
            for (int[] spot : spots) {
                int lx = spot[0];
                int ly = spot[1];
                if (lx < x0 || ly < y0 || lx + tw > x1 || ly + 8 > y1) continue;
                if (collides(lx - 1, ly - 1, lx + tw + 1, ly + 9)) continue;
                c.text(name, lx, ly, colour);
                taken.add(new int[]{lx - 1, ly - 1, lx + tw + 1, ly + 9});
                return;
            }
        });
    }

    private void tie(Vcc4Snapshot.Tie t) {
        int ax = px(t.latA());
        int ay = py(t.lonA());
        int bx = px(t.latB());
        int by = py(t.lonB());
        LinkKind kind = LinkKind.byOrdinal(t.kind());
        boolean slack = kind.reading() == LinkKind.Reading.GAP && Float.isFinite(t.reading()) && t.reading() < -0.15f;
        switch (kind.style()) {
            case ZIGZAG -> zigzag(ax, ay, bx, by, INK);
            case BAR -> {
                line(ax, ay, bx, by, INK);
                line(ax + 1, ay, bx + 1, by, INK);
                line(ax, ay + 1, bx, by + 1, INK);
            }
            case LINE -> {
                if (slack) dashed(ax, ay, bx, by, SHADOW);
                else line(ax, ay, bx, by, INK);
            }
            case NONE -> {
                return;
            }
        }
        if (t.b() < 0) {
            line(bx - 2, by - 2, bx + 2, by + 2, WARN);
            line(bx - 2, by + 2, bx + 2, by - 2, WARN);
        }
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("link." + kind.key()));
        if (t.b() < 0) tip.add(con.tr("tie.ground"));
        else tip.add(con.tr("tie.between", t.a() == 0 ? con.tr("tie.this") : "B" + (t.a() + 1), "B" + (t.b() + 1)));
        int mx = (ax + bx) / 2;
        int my = (ay + by) / 2;
        con.hit(mx - 3, my - 3, 7, 7, null, null, tip);
    }

    private void zigzag(int ax, int ay, int bx, int by, int colour) {
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 4) {
            line(ax, ay, bx, by, colour);
            return;
        }
        double ux = (bx - ax) / len;
        double uy = (by - ay) / len;
        int teeth = Math.max(2, (int) (len / 4));
        int lastX = ax;
        int lastY = ay;
        for (int k = 1; k <= teeth; k++) {
            double f = (double) k / teeth;
            double side = k == teeth ? 0.0 : (k % 2 == 0 ? 2.0 : -2.0);
            int x = (int) Math.round(ax + (bx - ax) * f - uy * side);
            int y = (int) Math.round(ay + (by - ay) * f + ux * side);
            line(lastX, lastY, x, y, colour);
            lastX = x;
            lastY = y;
        }
    }

    private void dashed(int ax, int ay, int bx, int by, int colour) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int k = 0; k <= steps; k++) {
            if ((k / 2) % 2 == 1) continue;
            int x = ax + (bx - ax) * k / Math.max(1, steps);
            int y = ay + (by - ay) * k / Math.max(1, steps);
            c.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private void wheel(int i, int wx, int wy) {
        Vcc4Snapshot.Wheel w = s.wheels().get(i);
        int x = px(w.lateral());
        int y = py(w.longitudinal());
        boolean driven = PartIndex.drives(s, w);
        boolean lit = lit(PartIndex.Kind.WHEEL, i);
        double a = Math.toRadians(w.angle() + bodyYaw(w.body()));
        quad(x, y, wx + 1, wy + 1, a, lit ? HIGHLIGHT : OUTLINE);
        quad(x, y, wx, wy, a, driven ? WHEEL_DRIVEN : RECESSED);
        if (!w.commanded()) c.fill(x - 1, y - 1, x + 1, y + 1, HIGHLIGHT);
        int r = Math.max(wx, wy) + 1;
        taken.add(new int[]{x - r, y - r, x + r + 1, y + r + 1});
        List<String> tip = new ArrayList<>();
        String side = w.lateral() < -1.0e-3 ? con.tr("side.left") : (w.lateral() > 1.0e-3 ? con.tr("side.right") : con.tr("side.centre"));
        String where = w.body() == 0 ? "A" + (w.axle() + 1) : "B" + (w.body() + 1);
        tip.add(con.tr("tip.wheel", where, side));
        String role = w.body() == 0 ? con.tr("role." + PartIndex.roleOf(s, w.axle()).key()) : con.bodyWheelRole(w.body());
        tip.add(role + ", " + (w.governor() >= 0 ? con.tr("tip.driven_by", "G" + (w.governor() + 1)) : con.tr("tip.free")));
        if (w.commanded()) tip.add(con.tr("deg", String.format(Locale.ROOT, "%+.0f", w.angle())) + ", " + con.tr("rpm", Math.round(w.rpm())));
        Vcc4Snapshot.Body body = w.body() == 0 ? null : PartIndex.bodyOf(s, w.body());
        Vcc4Console.Page target = w.body() == 0 ? Vcc4Console.Page.WHEELS
                : (body != null && body.takesMode() ? Vcc4Console.Page.TRAILERS : Vcc4Console.Page.PARTS);
        con.hit(x - r, y - r, 2 * r + 1, 2 * r + 1, () -> con.setPage(target), null, tip);
    }

    private double bodyYaw(int body) {
        if (body == 0) return 0.0;
        for (Vcc4Snapshot.Body b : s.bodies()) if (b.index() == body) return b.yaw();
        return 0.0;
    }

    private void governor(int i) {
        Vcc4Snapshot.Governor g = s.governors().get(i);
        int x = px(g.lateral());
        int y = py(g.longitudinal());
        int colour = g.excluded() ? RECESSED : (g.foreign() ? WARN : INK);
        diamond(x, y, colour);
        taken.add(new int[]{x - 4, y - 4, x + 5, y + 5});
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("tip.governor", "G" + (i + 1)));
        List<String> drives = new ArrayList<>();
        if (g.wheels() > 0) drives.add(con.tr("n_wheels", g.wheels()));
        if (g.jets() > 0) drives.add(con.tr("n_jets", g.jets()));
        if (!drives.isEmpty()) tip.add(String.join(", ", drives) + ", " + con.tr("rpm", g.applied()));
        if (g.foreign()) tip.add(con.tr("foreign.hint"));
        con.hit(x - 4, y - 4, 9, 9, () -> con.setPage(Vcc4Console.Page.PARTS), null, tip);
        label("G" + (i + 1), x, y, 4, g.foreign() ? WARN : INK, false);
    }

    private void machine(int i) {
        Vcc4Snapshot.Machine m = s.machines().get(i);
        int x = px(m.lateral());
        int y = py(m.longitudinal());
        boolean lit = lit(PartIndex.Kind.MACHINE, i);
        boolean running = m.output() != 0f;
        int edge = lit ? HIGHLIGHT : (m.foreign() ? WARN : OUTLINE);
        c.fill(x - 3, y - 3, x + 4, y + 4, edge);
        c.fill(x - 2, y - 2, x + 3, y + 3, running ? JET_ON : FACE);
        c.fill(x, y, x + 1, y + 1, INK);

        if (running) {
            c.fill(x - 4, y - 4, x - 2, y - 2, edge);
            c.fill(x + 3, y - 4, x + 5, y - 2, edge);
            c.fill(x - 4, y + 3, x - 2, y + 5, edge);
            c.fill(x + 3, y + 3, x + 5, y + 5, edge);
        } else {
            c.fill(x, y - 5, x + 1, y - 3, edge);
            c.fill(x, y + 4, x + 1, y + 6, edge);
            c.fill(x - 5, y, x - 3, y + 1, edge);
            c.fill(x + 4, y, x + 6, y + 1, edge);
        }
        taken.add(new int[]{x - 5, y - 5, x + 6, y + 6});
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("tip.machine", "M" + (i + 1), con.machineNames(m)));
        PartInput input = PartInput.byOrdinal(m.input());
        Keymap km = Keymap.fromInts(s.keymap());
        if (input.control() == null) {
            tip.add(con.tr("input.off"));
        } else {
            String keys = km.bound(input.control()) ? con.capsText(km, input.control()) : con.tr("legend_no_key");
            tip.add(con.tr("input." + input.key()) + " (" + keys + "), " + con.tr(m.latched() ? "machine.latched" : "machine.sprung"));
        }
        if (m.foreign()) tip.add(con.tr("foreign.hint"));
        con.hit(x - 5, y - 5, 11, 11, () -> con.setPage(Vcc4Console.Page.TOOLS), null, tip);
        label("M" + (i + 1), x, y, 5, m.foreign() ? WARN : (lit ? HIGHLIGHT : INK), lit);
    }

    private void swivel(int i) {
        Vcc4Snapshot.Swivel a = s.swivels().get(i);
        int x = px(a.lateral());
        int y = py(a.longitudinal());
        boolean lit = lit(PartIndex.Kind.SWIVEL, i);
        PartInput input = PartInput.byOrdinal(a.input());
        boolean problem = input != PartInput.OFF && !a.ready();
        int[] half = {2, 4, 5, 5, 6, 5, 5, 4, 2};
        for (int k = 0; k < half.length; k++) c.fill(x - half[k], y - 4 + k, x + half[k] + 1, y - 3 + k, lit ? HIGHLIGHT : OUTLINE);
        int[] inner = {1, 3, 4, 4, 5, 4, 4, 3, 1};
        for (int k = 0; k < inner.length; k++) c.fill(x - inner[k], y - 4 + k, x + inner[k] + 1, y - 3 + k, a.ready() ? FACE : RECESSED);
        double t = Math.toRadians(a.angle() - 90);
        line(x, y, (int) Math.round(x + 4 * Math.cos(t)), (int) Math.round(y + 4 * Math.sin(t)), INK);
        taken.add(new int[]{x - 6, y - 5, x + 7, y + 6});
        Keymap km = Keymap.fromInts(s.keymap());
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("tip.swivel", "S" + (i + 1)));
        if (input.control() == null) {
            tip.add(con.tr(a.assembled() && !a.locked() && a.carries() >= 0 ? "swivel.hitch" : "input.off"));
        } else {
            String keys = km.bound(input.control()) ? con.capsText(km, input.control()) : con.tr("legend_no_key");
            tip.add(con.tr("input." + input.key()) + " (" + keys + "), " + con.tr(a.latched() ? "latched" : "sprung"));
        }
        if (a.carries() >= 0) tip.add(con.tr("swivel.carries", "B" + (a.carries() + 1)));
        tip.add(con.tr("deg", String.format(Locale.ROOT, "%+.0f", a.angle())));
        if (problem) tip.add(con.tr("swivel.not_ready"));
        con.hit(x - 6, y - 5, 13, 11, () -> con.setPage(Vcc4Console.Page.TOOLS), null, tip);
        label("S" + (i + 1), x, y, 6, problem ? WARN : (lit ? HIGHLIGHT : INK), lit);
    }

    private void link(int i) {
        Vcc4Snapshot.Link l = s.links().get(i);
        int x = px(l.lateral());
        int y = py(l.longitudinal());
        boolean lit = lit(PartIndex.Kind.LINK, i);
        boolean on = l.strength() > 0;
        LinkSource source = LinkSource.byOrdinal(l.source());
        c.fill(x - 2, y - 3, x + 3, y + 4, lit ? HIGHLIGHT : OUTLINE);
        c.fill(x - 3, y - 2, x + 4, y + 3, lit ? HIGHLIGHT : OUTLINE);
        c.fill(x - 2, y - 2, x + 3, y + 3, on ? Vcc4Console.litColour(l) : (source == LinkSource.OFF ? RECESSED : FACE));
        taken.add(new int[]{x - 3, y - 3, x + 4, y + 4});
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("tip.link_short", "L" + (i + 1)));
        tip.add(con.tr("source." + source.key()));
        con.hit(x - 3, y - 3, 7, 7, () -> con.setPage(Vcc4Console.Page.LIGHTS), null, tip);
        label("L" + (i + 1), x, y, 3, on ? INK : (lit ? HIGHLIGHT : LABEL), lit);
    }

    private void jet(int i) {
        Vcc4Snapshot.Jet j = s.jets().get(i);
        int x = px(j.lateral());
        int y = py(j.longitudinal());
        boolean lit = lit(PartIndex.Kind.JET, i);
        boolean vertical = Math.abs(j.axisUp()) >= JetMixer.VERTICAL;

        float cmd = j.direct() ? (j.ceiling() > 0f ? Math.min(1f, j.force() / j.ceiling()) : 0f) : j.command();
        boolean firing = Math.abs(cmd) > 0.02f;
        boolean cannot = j.direct() && (j.foreign() || j.supplyOf().out());
        if (vertical) {
            int[] half = {1, 3, 3, 3, 1};
            int[] halfOut = {2, 4, 4, 4, 4, 4, 2};
            for (int k = 0; k < halfOut.length; k++) c.fill(x - halfOut[k], y - 3 + k, x + halfOut[k] + 1, y - 2 + k, lit ? HIGHLIGHT : OUTLINE);
            for (int k = 0; k < half.length; k++) c.fill(x - half[k], y - 2 + k, x + half[k] + 1, y - 1 + k, FACE);
            if (firing) {
                boolean up = cmd * j.axisUp() > 0;
                if (up) c.fill(x - 1, y - 1, x + 2, y + 2, JET_ON);
                else {
                    line(x - 2, y - 2, x + 2, y + 2, JET_ON);
                    line(x - 2, y + 2, x + 2, y - 2, JET_ON);
                }
            }
        } else {
            double len = Math.hypot(j.axisRight(), j.axisForward());
            double dx = len < 1.0e-6 ? 0 : j.axisRight() / len;
            double dy = len < 1.0e-6 ? 0 : -j.axisForward() / len;
            boolean direct = j.direct();
            if (j.kindOf() == JetKind.VECTOR && Math.abs(j.nozzleX()) > 0.02f) {
                double t = Math.tan(Math.toRadians(VectorGimbal.MAX_TILT_DEG)) * j.nozzleX();
                double tx = dx - t * -dy;
                double ty = dy - t * dx;
                double tl = Math.hypot(tx, ty);
                if (tl > 1.0e-6) {
                    dx = tx / tl;
                    dy = ty / tl;
                }
            }
            if (direct) {
                line((int) Math.round(x - dx * 5), (int) Math.round(y - dy * 5), (int) Math.round(x + dx * 8),
                        (int) Math.round(y + dy * 8), SHADOW);
                double nx = x - dx * 6;
                double ny = y - dy * 6;
                line((int) Math.round(nx - dy * 4), (int) Math.round(ny + dx * 4), (int) Math.round(nx + dy * 4),
                        (int) Math.round(ny - dx * 4), OUTLINE);
            } else {
                line((int) Math.round(x - dx * 7), (int) Math.round(y - dy * 7), (int) Math.round(x + dx * 7),
                        (int) Math.round(y + dy * 7), SHADOW);
            }
            c.fill(x - 3, y - 3, x + 4, y + 4, lit ? HIGHLIGHT : OUTLINE);
            boolean out = direct ? cannot : j.governor() < 0;
            c.fill(x - 2, y - 2, x + 3, y + 3, out ? RECESSED : FACE);
            if (firing) {
                double sgn = Math.signum(cmd);
                double reach = 8 + 6 * Math.min(1.0, Math.abs(cmd));
                int ex = (int) Math.round(x + dx * sgn * reach);
                int ey = (int) Math.round(y + dy * sgn * reach);
                line((int) Math.round(x + dx * sgn * 4), (int) Math.round(y + dy * sgn * 4), ex, ey, JET_ON);

                double ax = -dx * sgn;
                double ay = -dy * sgn;
                line(ex, ey, (int) Math.round(ex + (ax * 3 - ay * 3)), (int) Math.round(ey + (ay * 3 + ax * 3)), JET_ON);
                line(ex, ey, (int) Math.round(ex + (ax * 3 + ay * 3)), (int) Math.round(ey + (ay * 3 - ax * 3)), JET_ON);
                c.fill(x - 1, y - 1, x + 2, y + 2, JET_ON);
            }
        }
        taken.add(new int[]{x - 6, y - 6, x + 7, y + 7});
        List<String> tip = new ArrayList<>();
        tip.add(con.tr("tip.jet", "J" + (i + 1)) + (j.direct() ? ": " + con.tr("jetkind." + j.kindOf().key()) : ""));
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        if (job != null) tip.add(con.tr("job." + job.key() + ".why"));
        if (j.direct()) {
            if (j.foreign()) tip.add(con.tr("jet.foreign.hint"));
            else if (j.supplyOf().out()) tip.add(con.tr("jet.supply." + j.supplyOf().key() + ".hint"));
            else tip.add(con.tr("jet.thrust", Math.round(j.force()), Math.round(j.ceiling())));
        }
        Control fires = PartIndex.primaryControl(j, s);
        Keymap km = Keymap.fromInts(s.keymap());
        if (fires != null) tip.add(con.tr("tip.fires", con.firesOn(j, km)));
        if (j.governor() < 0 && !j.direct()) tip.add(con.tr("no_governor.hint"));
        con.hit(x - 4, y - 4, 9, 9, () -> con.setPage(Vcc4Console.Page.JETS), null, tip);
        label("J" + (i + 1), x, y, 4, firing ? JET_ON : (cannot ? WARN : (lit ? HIGHLIGHT : INK)), lit);
    }

    private void label(String text, int ax, int ay, int r, int colour, boolean lit) {
        labels.add(() -> {
            int tw = host.width(text);
            int[][] spots = {
                    {ax + r + 3, ay - 4}, {ax - r - 3 - tw, ay - 4}, {ax - tw / 2, ay - r - 10}, {ax - tw / 2, ay + r + 2},
                    {ax + r + 2, ay - r - 9}, {ax + r + 2, ay + r + 1}, {ax - r - 2 - tw, ay - r - 9}, {ax - r - 2 - tw, ay + r + 1}};
            for (int[] spot : spots) {
                int lx = spot[0];
                int ly = spot[1];
                if (lx < x0 || ly < y0 || lx + tw > x1 || ly + 8 > y1) continue;
                if (collides(lx - 1, ly - 1, lx + tw + 1, ly + 9)) continue;
                if (lit) c.fill(lx - 1, ly - 1, lx + tw, ly + 8, SHADOW);
                c.text(text, lx, ly, lit ? HIGHLIGHT : colour);
                taken.add(new int[]{lx - 1, ly - 1, lx + tw + 1, ly + 9});
                return;
            }
        });
    }

    private boolean collides(int ax0, int ay0, int ax1, int ay1) {
        for (int[] t : taken) {
            if (ax0 < t[2] && ax1 > t[0] && ay0 < t[3] && ay1 > t[1]) return true;
        }
        return false;
    }

    private void quad(int cx, int cy, int hw, int hh, double angle, int colour) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        float[] xs = new float[4];
        float[] ys = new float[4];
        int[][] corners = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        for (int k = 0; k < 4; k++) {
            double dx = corners[k][0];
            double dy = corners[k][1];
            xs[k] = (float) (cx + 0.5 + dx * cos - dy * sin);
            ys[k] = (float) (cy + 0.5 + dx * sin + dy * cos);
        }
        c.quad(xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], xs[3], ys[3], colour);
    }

    private void diamond(int x, int y, int colour) {
        for (int r = 0; r <= 3; r++) {
            c.fill(x - r, y - 3 + r, x + r + 1, y - 2 + r, colour);
            c.fill(x - r, y + 3 - r, x + r + 1, y + 4 - r, colour);
        }
    }

    private void outline(int ax0, int ay0, int ax1, int ay1, int colour) {
        c.fill(ax0, ay0, ax1, ay0 + 1, colour);
        c.fill(ax0, ay1 - 1, ax1, ay1, colour);
        c.fill(ax0, ay0, ax0 + 1, ay1, colour);
        c.fill(ax1 - 1, ay0, ax1, ay1, colour);
    }

    private void dotted(int ax0, int ay0, int ax1, int ay1, int colour) {
        for (int x = ax0; x < ax1; x += 4) {
            c.fill(x, ay0, Math.min(x + 2, ax1), ay0 + 1, colour);
            c.fill(x, ay1 - 1, Math.min(x + 2, ax1), ay1, colour);
        }
        for (int y = ay0; y < ay1; y += 4) {
            c.fill(ax0, y, ax0 + 1, Math.min(y + 2, ay1), colour);
            c.fill(ax1 - 1, y, ax1, Math.min(y + 2, ay1), colour);
        }
    }

    private void line(int ax, int ay, int bx, int by, int colour) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int k = 0; k <= steps; k++) {
            int x = ax + (bx - ax) * k / Math.max(1, steps);
            int y = ay + (by - ay) * k / Math.max(1, steps);
            c.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
