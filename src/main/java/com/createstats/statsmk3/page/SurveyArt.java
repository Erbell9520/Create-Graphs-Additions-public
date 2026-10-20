package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.createstats.blockentity.SeismicLinks;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicRig;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.archive.ScanCamera;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.seismic.archive.SurveyText;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.SurveySettings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SurveyArt {
    private SurveyArt() {
    }

    public static final String[] TABS = {"Scan", "Depth", "Finds", "History"};
    public static final int SCAN = 0;
    public static final int DEPTH = 1;
    public static final int FINDS = 2;
    public static final int HISTORY = 3;

    public static final float STRIP_H = 40.0f;
    public static final float TABS_H = 18.0f;
    public static final float LEGEND_W = 172.0f;
    public static final float ROW_H = 13.0f;
    static final float CHIP_H = 13.0f;

    public static final float DEFAULT_YAW = SurveySettings.DEFAULT_YAW;
    public static final float DEFAULT_PITCH = SurveySettings.DEFAULT_PITCH;

    public static final class Model {
        public SurveyScene scene = SurveyScene.EMPTY;
        public long now;
        public double refX;
        public double refY;
        public double refZ;
        public boolean linked;

        public String focus = "";
        public Set<String> hidden = Set.of();
        public double yaw = DEFAULT_YAW;
        public double pitch = DEFAULT_PITCH;
        public double zoom = 1.0;

        public double[] centre;

        public double sliceLo = Double.NaN;
        public double sliceHi = Double.NaN;

        public int selected = -1;

        public long pin = Long.MIN_VALUE;

        public Map<Long, SeismicLinks.RigState> rigStates = Map.of();

        public long since = Long.MIN_VALUE;

        public Hit hover;

        public int ledgerScroll;
        public int logScroll;
        public int legendScroll;
        public int rigScroll;

        public Mk3Settings.FaceMode faceMode = Mk3Settings.FaceMode.INSTRUMENTS;

        public SurveyScene.Body ring;

        public int known;
        public boolean waiting;

        public Map<Long, Integer> rigNumbers = Map.of();

        public long armed = Long.MIN_VALUE;

        public long selectedKey = Long.MIN_VALUE;

        public boolean offWall;

        public Map<Long, Long> worked = Map.of();

        public double heading = Double.NaN;

        public int chipScroll;

        public boolean newOnly;

        Hits hits;

        public Kind sentKind;
        public long sentKey;
    }

    public static Model modelOf(StatsDisplayPanelBE controller, Mk3Settings st, long tick) {
        return modelOf(controller, st, st.survey, tick);
    }

    public static Model modelOf(StatsDisplayPanelBE controller, Mk3Settings st, SurveySettings view, long tick) {
        final Model m = new Model();
        final SeismicLinks links = controller.seismicLinks();
        m.scene = heard(links.scene(), tick);
        m.now = tick;
        final BlockPos at = controller.getBlockPos();
        m.refX = at.getX() + 0.5;
        m.refY = at.getY() + 0.5;
        m.refZ = at.getZ() + 0.5;
        m.linked = links.hasLinks();
        m.known = links.knownRigs().size();
        m.waiting = m.known > 0 && !links.received();
        m.rigNumbers = links.rigNumbers();
        m.worked = links.worked();
        final net.minecraft.world.level.block.state.BlockState state = controller.getBlockState();
        if (state.hasProperty(com.createstats.block.StatsDisplayPanelBlock.FACING)) {
            m.heading = (state.getValue(com.createstats.block.StatsDisplayPanelBlock.FACING).toYRot() + 180.0) % 360.0;
        }
        m.rigStates = links.rigStates();
        m.since = links.since();
        m.faceMode = st.faceMode;
        final SurveySettings v = view;
        m.focus = v.focus == null ? "" : v.focus;
        m.hidden = hiddenSet(v.hidden);
        m.yaw = v.yaw;
        m.pitch = v.pitch;
        m.zoom = v.zoom;
        m.centre = v.hasCentre() ? new double[] {v.cx, v.cy, v.cz} : null;
        m.sliceLo = v.hasSlice() ? v.sliceLo : Double.NaN;
        m.sliceHi = v.hasSlice() ? v.sliceHi : Double.NaN;
        m.pin = v.pin;
        return m;
    }

    private static SurveyScene heardFrom;
    private static long heardTick = Long.MIN_VALUE;
    private static SurveyScene heard;

    static synchronized SurveyScene heard(SurveyScene scene, long tick) {
        if (scene != heardFrom || tick != heardTick || heard == null) {
            heardFrom = scene;
            heardTick = tick;
            heard = scene.heardAt(tick);
        }
        return heard;
    }

    public static Set<String> hiddenSet(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Set.of();
        }
        final Set<String> out = new HashSet<>();
        for (final String s : csv.split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    public enum Kind {
        TAB, SHOW_FACE, FRAME_WALL, WORKED, RELEASE, NEW_FINDS, VIEW_3D, VIEW_PLAN, VIEW_SECTION, VIEW_RESET, SCAN_FIELD, LEGEND_ROW, LEGEND_SWATCH, LOG_PLOT,
        CLEAR_BAND, CHIP, LEDGER_ROW, PIN, SHOW_IN_SCAN, FORGET, HIDE_BEFORE, SHOW_ALL
    }

    public record Hit(Kind kind, int index, String target, long key, Rect rect) {
        public boolean is(Hit o) {
            return o != null && o.kind == kind && o.index == index && o.key == key && o.target.equals(target);
        }
    }

    public static class Hits {
        final List<Hit> list = new ArrayList<>();

        public void add(Kind kind, int index, String target, long key, Rect rect) {
            this.list.add(new Hit(kind, index, target == null ? "" : target, key, rect));
        }

        public void clear() {
            this.list.clear();
        }

        public Hit at(double x, double y) {
            for (int i = this.list.size() - 1; i >= 0; i--) {
                if (this.list.get(i).rect().contains(x, y)) {
                    return this.list.get(i);
                }
            }
            return null;
        }

        public Hit first(Kind kind) {
            for (final Hit h : this.list) {
                if (h.kind() == kind) {
                    return h;
                }
            }
            return null;
        }

        public List<Hit> all() {
            return Collections.unmodifiableList(this.list);
        }
    }

    private static final Hits NONE = new Hits() {
        @Override
        public void add(Kind kind, int index, String target, long key, Rect rect) {
        }
    };

    static Rect chip(PanelSurface s, Hits hits, Model m, Kind kind, int index, String target, long key, float x, float y,
                     String label, boolean on) {
        final String shown = m.sentKind == kind && m.sentKey == key ? "Sent" : label;
        final float w = s.textWidth(shown, Text.MICRO) + 12.0f;
        final Rect r = new Rect(x, y, w, CHIP_H);
        final boolean hover = m.hover != null && m.hover.kind() == kind && m.hover.index() == index
                && m.hover.key() == key && m.hover.target().equals(target == null ? "" : target);
        if (on || hover) {
            Shapes.roundedRect(s, r, 4.0f, on ? Tokens.OVERLAY : Tokens.RAISED);
        }
        Shapes.roundedRectOutline(s, r, 4.0f, 1.0f, on || hover ? Tokens.LINE_HI : Tokens.LINE);
        s.text(shown, r.x + 6.0f, r.y + (r.h - Text.lineHeight(Text.MICRO)) * 0.5f + 0.5f,
                on || hover ? Tokens.INK : Tokens.INK_2, Text.MICRO);
        hits.add(kind, index, target, key, r);
        return r;
    }

    public static void frame(PanelSurface s, Rect area, Model m, int tab, Hits hits) {
        strip(s, area.top(STRIP_H), m);
        final float y = area.y + STRIP_H + 4.0f;
        Shapes.rect(s, area.x, y + TABS_H - 1.0f, area.w, 1.0f, Tokens.LINE);
        float x = area.x + 2.0f;
        for (int i = 0; i < TABS.length; i++) {
            final float w = s.textWidth(TABS[i], Text.META) + 16.0f;
            final Rect t = new Rect(x, y, w, TABS_H);
            final boolean on = i == tab;
            final boolean hover = m.hover != null && m.hover.kind() == Kind.TAB && m.hover.index() == i;
            s.textCenterV(TABS[i], t.centerX(), t.centerY() - 1.0f, on || hover ? Tokens.INK : Tokens.INK_3, Text.META);
            if (on) {
                Shapes.rect(s, t.x + 4.0f, t.bottom() - 2.0f, t.w - 8.0f, 2.0f, Tokens.ACCENT);
            }
            hits.add(Kind.TAB, i, "", 0L, t);
            x += w + 2.0f;
        }
        if (!m.scene.isEmpty()) {
            final boolean on = m.faceMode == faceFor(tab);
            final String label = on ? "On the face" : "Show on face";
            final float w = s.textWidth(label, Text.MICRO) + 12.0f;
            final Rect face = chip(s, hits, m, Kind.SHOW_FACE, tab, "", 0L, area.right() - w - 2.0f,
                    y + (TABS_H - CHIP_H) * 0.5f - 1.0f, label, on);
            if (on && m.offWall) {
                final String put = "Frame the wall like this";
                final float pw = s.textWidth(put, Text.MICRO) + 12.0f;
                chip(s, hits, m, Kind.FRAME_WALL, tab, "", 0L, face.x - pw - 4.0f, face.y, put, false);
            }
        }
    }

    public static Mk3Settings.FaceMode faceFor(int tab) {
        return switch (tab) {
            case DEPTH -> Mk3Settings.FaceMode.SURVEY_DEPTH;
            case FINDS -> Mk3Settings.FaceMode.SURVEY_FINDS;
            case HISTORY -> Mk3Settings.FaceMode.SURVEY_ECHO;
            default -> Mk3Settings.FaceMode.SURVEY_SCAN;
        };
    }

    public static Rect content(Rect area) {
        return new Rect(area.x, area.y + STRIP_H + TABS_H + 10.0f, area.w, area.h - STRIP_H - TABS_H - 10.0f);
    }

    static void strip(PanelSurface s, Rect r, Model m) {
        Shapes.card(s, r, 6.0f, Tokens.RAISED, Tokens.LINE);
        final Rect in = r.inset(10.0f, 5.0f, 10.0f, 5.0f);
        final SurveyScene sc = m.scene;
        int linked = 0;
        for (final SurveyScene.Rig rig : sc.rigs) {
            if (rig.linked) {
                linked++;
            }
        }
        s.text("Survey", in.x, in.y + 1.0f, Tokens.INK, Text.SUB);
        final String rigs = sc.rigs.isEmpty() ? (m.linked ? "rigs linked" : "no rig linked")
                : sc.rigs.size() + (sc.rigs.size() == 1 ? " rig" : " rigs") + (linked == sc.rigs.size() ? ""
                : ", " + linked + " linked");
        s.text(rigs, in.x, in.y + 18.0f, Tokens.INK_2, Text.META);
        final float x0 = in.x + 104.0f;
        final float chipW = 84.0f;
        final float room = in.right() - chipW - 10.0f - x0;
        final float wide = Math.max(110.0f, room * 0.55f);
        final Headline h = headline(m);
        if (h.target() != null) {
            final float lh = Text.lineHeight(Text.MICRO);
            SurveyKit.swatch(s, new Rect(x0, in.y + 2.0f + (lh - 5.0f) * 0.5f, 5.0f, 5.0f), h.target(), h.kind());
            s.text(h.label(), x0 + 9.0f, in.y + 2.0f, Tokens.INK_3, Text.MICRO);
            s.text(Text.truncate(s, h.find() == null ? "none found" : h.find().where(), wide - 6.0f, Text.SUB), x0,
                    in.y + 13.0f, h.find() == null ? Tokens.INK_3 : Tokens.INK, Text.SUB);
        }
        final float cell = (room - wide) / 2.0f;
        if (cell >= 48.0f) {
            figure(s, x0 + wide, in, cell, "BLOWS", String.valueOf(sc.bowls.size()));
            figure(s, x0 + wide + cell, in, cell, "LAST BLOW",
                    sc.bowls.isEmpty() ? "--" : SurveyFinds.ago(m.now, sc.newestBlow()));
        }
        final Rect chip = new Rect(in.right() - chipW, in.y + 6.0f, chipW, in.h - 12.0f);
        final SurveyScene.Bowl last = sc.bowls.isEmpty() ? null : sc.bowls.get(sc.bowls.size() - 1);
        if (last != null && m.now - last.at >= 0 && m.now - last.at <= SeismicWave.listenTicks(last.reach)) {
            Shapes.roundedRect(s, chip, 4.0f, Tokens.INK);
            s.textCenterV("LISTENING", chip.centerX(), chip.centerY(), Tokens.CANVAS, Text.MICRO);
        } else if (last != null) {
            final int fresh = freshCount(m);
            Shapes.roundedRectOutline(s, chip, 4.0f, 1.0f, Tokens.LINE_HI);
            if (fresh > 0) {
                hitsOf(m).add(Kind.NEW_FINDS, 0, "", 0L, chip);
            }
            s.textCenterV(fresh == 0 ? "NOTHING NEW" : fresh + " NEW", chip.centerX(), chip.centerY(),
                    fresh == 0 ? Tokens.INK_3 : Tokens.INK, Text.MICRO);
        }
    }

    static Hits hitsOf(Model m) {
        return m.hits == null ? NONE : m.hits;
    }

    public static int freshCount(Model m) {
        int n = 0;
        for (final SurveyScene.Layer l : m.scene.layers) {
            for (final SurveyScene.Body b : l.bodies) {
                if (b.newest != Long.MIN_VALUE && m.now >= b.newest && m.now - (b.newest + b.echo) <= SeismicLinks.NEW_TICKS) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void figure(PanelSurface s, float x, Rect in, float w, String label, String value) {
        s.text(label, x, in.y + 2.0f, Tokens.INK_3, Text.MICRO);
        s.text(Text.truncate(s, value, w - 6.0f, Text.SUB), x, in.y + 13.0f, Tokens.INK, Text.SUB);
    }

    public record Headline(String target, int kind, SurveyFinds.Find find, String label) {
    }

    public static Headline headline(Model m) {
        if (m.pin != Long.MIN_VALUE) {
            final SurveyFinds.Find p = pinned(m);
            if (p != null) {
                return new Headline(p.body().target, p.body().kind, p, "PINNED " + upper(p.body().target, p.body().kind));
            }
        }
        final String target = headlineTarget(m);
        if (target.isEmpty()) {
            return new Headline(null, 0, null, "");
        }
        final SurveyFinds.Find f = nearestOpen(m, target);
        final int kind = f == null ? defaultKind(target) : f.body().kind;
        return new Headline(target, kind, f, "NEAREST " + upper(target, kind));
    }

    public static boolean worked(Model m, SurveyScene.Body b) {
        return !m.worked.isEmpty() && m.worked.containsKey(BlockPos.asLong(b.nearX, b.nearY, b.nearZ));
    }

    public static SurveyFinds.Find nearestOpen(Model m, String target) {
        final SurveyScene.Layer l = m.scene.layer(target);
        if (l == null) {
            return null;
        }
        SurveyFinds.Find best = null;
        for (final SurveyScene.Body b : l.bodies) {
            if (worked(m, b)) {
                continue;
            }
            final SurveyFinds.Find f = SurveyFinds.place(b, b.nearX, b.nearY, b.nearZ, m.refX, m.refZ);
            if (best == null || SurveyFinds.closer(f, best) < 0) {
                best = f;
            }
        }
        return best;
    }

    public static String upper(String target, int kind) {
        return SurveyFinds.findName(target, kind).toUpperCase(Locale.ROOT);
    }

    public static SurveyFinds.Find pinned(Model m) {
        final SurveyScene.Body best = bodyAt(m.scene, m.pin);
        return best == null ? null : SurveyFinds.place(best, best.nearX, best.nearY, best.nearZ, m.refX, m.refZ);
    }

    public static long keyOf(SurveyFinds.Find f) {
        return BlockPos.asLong(f.x(), f.y(), f.z());
    }

    public static SurveyScene.Body bodyAt(SurveyScene scene, long key) {
        if (key == Long.MIN_VALUE) {
            return null;
        }
        final int px = BlockPos.getX(key);
        final int py = BlockPos.getY(key);
        final int pz = BlockPos.getZ(key);
        SurveyScene.Body best = null;
        double bestD = 2.5 * 2.5;
        for (final SurveyScene.Layer l : scene.layers) {
            for (final SurveyScene.Body b : l.bodies) {
                if (px < b.minX - 2 || px > b.maxX + 2 || py < b.minY - 2 || py > b.maxY + 2 || pz < b.minZ - 2
                        || pz > b.maxZ + 2) {
                    continue;
                }
                for (final int c : b.cells) {
                    final double dx = b.cellX(c) + b.grain * 0.5 - (px + 0.5);
                    final double dy = b.cellY(c) + b.grain * 0.5 - (py + 0.5);
                    final double dz = b.cellZ(c) + b.grain * 0.5 - (pz + 0.5);
                    final double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    public static String headlineTarget(Model m) {
        if (!m.focus.isEmpty()) {
            return m.focus;
        }
        // most valuable target, not whoever struck last

        for (final String t : legendOrder(m.scene)) {
            final SurveyScene.Layer l = m.scene.layer(t);
            if (l != null && !l.bodies.isEmpty() && !m.hidden.contains(t) && nearestOpen(m, t) != null) {
                return t;
            }
        }
        final List<SurveyScene.Bowl> b = m.scene.bowls;
        return b.isEmpty() ? "" : b.get(b.size() - 1).target;
    }

    static int defaultKind(String target) {
        return SeismicCatalog.LIQUIDS.equals(target) ? SeismicCatalog.KIND_LAVA : 0;
    }

    public static SurveyKit.Scan scanOptions(Model m, float micro) {
        final SurveyKit.Scan o = new SurveyKit.Scan();
        o.now = m.now;
        o.refX = m.refX;
        o.refY = m.refY;
        o.refZ = m.refZ;
        o.focus = m.focus;
        o.hidden = m.hidden;
        o.yaw = m.yaw;
        o.pitch = m.pitch;
        o.zoom = m.zoom;
        o.centre = m.centre;
        o.sliceLo = m.sliceLo;
        o.sliceHi = m.sliceHi;
        o.micro = micro;
        o.label = Math.max(micro, Text.META * 0.9f);

        final Headline h = headline(m);
        o.toward = h.find() == null ? null : new double[] {h.find().x() + 0.5, h.find().y() + 0.5, h.find().z() + 0.5};
        o.rigNumbers = m.rigNumbers;

        final SurveyScene.Body digTo = m.ring != null ? m.ring : m.pin != Long.MIN_VALUE ? bodyAt(m.scene, m.pin) : null;
        o.dig = digTo == null ? null : new int[] {digTo.nearX, digTo.nearY, digTo.nearZ};

        o.terrainStep = 2;
        return o;
    }

    public static void paint(PanelSurface s, Rect area, Model m, int tab) {
        paint(s, area, m, tab, NONE);
    }

    public static void paint(PanelSurface s, Rect area, Model m, int tab, Hits hits) {
        m.hits = hits;
        frame(s, area, m, tab, hits);
        final Rect body = content(area);
        if (m.scene.isEmpty()) {
            empty(s, body, m, hits);
            return;
        }
        switch (tab) {
            case DEPTH -> depth(s, body, m, hits);
            case FINDS -> finds(s, body, m, hits);
            case HISTORY -> history(s, body, m, hits);
            default -> scan(s, body, m, hits);
        }
    }

    static void empty(PanelSurface s, Rect r, Model m, Hits hits) {
        Shapes.card(s, r, 8.0f, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r.inset(24.0f);
        final float lh = Text.lineHeight(Text.BODY);
        float y = in.y + in.h * 0.28f;
        final String head;
        final String[] steps;
        if (m.waiting) {
            // never "no rig" while the scene is still on its way
            head = "Reading the record";
            steps = new String[] {"Every blow your rigs have struck is on its way to this panel."};
        } else if (m.since != Long.MIN_VALUE) {
            head = "Every blow before " + SurveyFinds.ago(m.now, m.since) + " ago is hidden here";
            steps = new String[] {"The world's record still holds them; only this panel's scan, faces and channels "
                    + "left them out."};
        } else if (m.known > 0 || m.linked) {
            head = "No blow struck yet";
            steps = new String[] {"Tune the rig to a target and charge it.", "Pull its lever: every vein its echoes find "
                    + "lands here, and stays.", "Blows a rig struck before it was linked show here too."};
        } else {
            head = "No Seismic Scanner linked";
            steps = new String[] {"Put a Display Link on a Seismic Scanner and click it onto this panel.",
                    "Every blow that rig strikes is kept, here and in the world's own record.",
                    "Link more rigs and their surveys join one scan."};
        }
        s.textCenter(Text.truncate(s, head, in.w, Text.SUB), in.centerX(), y, Tokens.INK, Text.SUB);
        y += Text.lineHeight(Text.SUB) + 10.0f;
        for (final String line : steps) {
            s.textCenter(Text.truncate(s, line, in.w, Text.BODY), in.centerX(), y, Tokens.INK_2, Text.BODY);
            y += lh + 5.0f;
        }
        if (m.since != Long.MIN_VALUE && !m.waiting) {
            final String undo = "Show every blow";
            final float w = s.textWidth(undo, Text.MICRO) + 12.0f;
            chip(s, hits, m, Kind.SHOW_ALL, 0, "", 0L, in.centerX() - w * 0.5f, y + 6.0f, undo, false);
        }
    }

    public record ScanLayout(Rect card, Rect field, Rect legend, Rect footer) {
    }

    public static ScanLayout scanLayout(Rect body) {
        final float legendW = Math.min(LEGEND_W, body.w * 0.34f);
        final Rect card = new Rect(body.x, body.y, body.w - legendW - 8.0f, body.h);
        final Rect field = card.inset(6.0f, CHIP_H + 10.0f, 6.0f, 18.0f);
        return new ScanLayout(card, field, new Rect(card.right() + 8.0f, body.y, legendW, body.h),
                new Rect(card.x + 8.0f, card.bottom() - 15.0f, card.w - 16.0f, 12.0f));
    }

    static void scan(PanelSurface s, Rect body, Model m, Hits hits) {
        final ScanLayout l = scanLayout(body);
        Shapes.card(s, l.card(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        final SurveyKit.Scan o = pageScan(m);
        SurveyKit.scan(s, l.field(), m.scene, o);
        hits.add(Kind.SCAN_FIELD, 0, "", 0L, l.field());
        viewChips(s, new Rect(l.card().x + 8.0f, l.card().y + 6.0f, l.card().w - 16.0f, CHIP_H), m, hits);
        footer(s, l.footer(), m);
        if (m.ring != null) {
            Shapes.card(s, l.legend(), 8.0f, Tokens.SURFACE, Tokens.LINE);
            final Rect in = l.legend().inset(8.0f);
            final String back = "Back to the legend";
            final Rect release = chip(s, hits, m, Kind.RELEASE, 0, "", 0L, in.x, in.y, back, false);
            final SurveyScene.Body b = m.ring;
            picked(s, new Rect(in.x, release.bottom() + 8.0f, in.w, in.bottom() - release.bottom() - 8.0f), m,
                    new Row(SurveyFinds.place(b, b.nearX, b.nearY, b.nearZ, m.refX, m.refZ), b.target), hits);
            return;
        }
        legend(s, l.legend(), m, Text.MICRO, Text.META, true, hits);
    }

    public static SurveyKit.Scan pageScan(Model m) {
        final SurveyKit.Scan o = scanOptions(m, Text.MICRO);
        o.maxCallouts = 3;
        o.selected = m.ring;
        // coal goes first on a crowded site, never a diamond

        o.budget = 2_000;
        o.cloud = 1_000;
        return o;
    }

    static void viewChips(PanelSurface s, Rect r, Model m, Hits hits) {
        final boolean plan = m.pitch >= 89.5;
        final boolean section = m.pitch <= 0.5;
        float x = r.x;
        x = chip(s, hits, m, Kind.VIEW_3D, 0, "", 0L, x, r.y, "3D", !plan && !section).right() + 3.0f;
        x = chip(s, hits, m, Kind.VIEW_PLAN, 0, "", 0L, x, r.y, "Plan", plan).right() + 3.0f;
        x = chip(s, hits, m, Kind.VIEW_SECTION, 0, "", 0L, x, r.y, "Section", section).right() + 8.0f;
        final boolean moved = m.centre != null || Math.abs(m.zoom - 1.0) > 1.0e-3
                || (!plan && !section && (Math.abs(m.yaw - DEFAULT_YAW) > 0.5 || Math.abs(m.pitch - DEFAULT_PITCH) > 0.5));
        if (moved) {
            x = chip(s, hits, m, Kind.VIEW_RESET, 0, "", 0L, x, r.y, "Reset view", false).right() + 3.0f;
        }
        if (!Double.isNaN(m.sliceLo)) {
            x = chip(s, hits, m, Kind.CLEAR_BAND, 0, "", 0L, x, r.y, "Clear band", false).right() + 3.0f;
        }

        final String hint = "Drag turns, right drag slides, wheel zooms, click a find, double click resets";
        s.text(Text.truncate(s, hint, r.right() - x - 4.0f, Text.MICRO), x + 4.0f,
                r.y + (r.h - Text.lineHeight(Text.MICRO)) * 0.5f + 0.5f, Tokens.INK_3, Text.MICRO);
    }

    static void footer(PanelSurface s, Rect r, Model m) {
        final String slice = Double.isNaN(m.sliceLo) ? ""
                : String.format(Locale.ROOT, "Showing Y %d to %d.  ", (int) m.sliceLo, (int) m.sliceHi);
        final String text = slice + "Ground outside the rigs' bowls is unread, not empty.";
        s.text(Text.truncate(s, text, r.w, Text.MICRO), r.x, r.y + 2.0f, Tokens.INK_3, Text.MICRO);
    }

    public static String counted(String target, int kind, int n) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return n + " " + SurveyFinds.plural(target, n);
        }
        return n + " " + SurveyFinds.noun(target, kind) + " " + SurveyFinds.plural(target, n);
    }

    static void legend(PanelSurface s, Rect r, Model m, float micro, float name, boolean card, Hits hits) {
        if (card) {
            Shapes.card(s, r, 8.0f, Tokens.SURFACE, Tokens.LINE);
        }
        final Rect in = card ? r.inset(8.0f) : r;
        final float rowH = Text.lineHeight(name) + Text.lineHeight(micro) + 7.0f;
        float y = in.y + 2.0f;
        final List<String> order = legendOrder(m.scene);

        final int fits = Math.max(1, (int) ((in.h - 2.0f) / rowH));
        final boolean over = order.size() > fits;
        final int rows = over ? Math.max(1, (int) ((in.h - 2.0f - Text.lineHeight(micro) - 4.0f) / rowH)) : fits;
        final int first = Math.max(0, Math.min(m.legendScroll, order.size() - rows));
        for (int i = first; i < order.size() && i < first + rows; i++) {
            legendRow(s, new Rect(in.x, y, in.w, rowH - 2.0f), m, order.get(i), micro, name, i, hits);
            y += rowH;
        }
        if (over) {
            final int below = order.size() - first - rows;
            final String more = (first > 0 ? first + " above, " : "") + (below > 0 ? below + " more, " : "") + "scroll";
            s.text(Text.truncate(s, more, in.w, micro), in.x, y + 1.0f, Tokens.INK_3, micro);
        }
        if (card && !over && y + Text.lineHeight(micro) * 2.0f < in.bottom()) {
            s.text("Click to focus; shift click to", in.x, in.bottom() - Text.lineHeight(micro) * 2.0f - 2.0f,
                    Tokens.INK_3, micro);
            s.text("show it alone; click a swatch to hide.", in.x, in.bottom() - Text.lineHeight(micro) - 1.0f,
                    Tokens.INK_3, micro);
        }
    }

    public static List<String> legendOrder(SurveyScene sc) {
        final List<String> targets = new ArrayList<>(sc.targetsSurveyed());
        targets.sort((a, b) -> SeismicCatalog.compare(b, a));
        return targets;
    }

    static void legendRow(PanelSurface s, Rect row, Model m, String t, float micro, float name, int index, Hits hits) {
        final SurveyScene.Layer l = m.scene.layer(t);
        final boolean focus = t.equals(m.focus);
        final boolean hidden = m.hidden.contains(t);
        final Rect hitRow = row.inset(-3.0f, -2.0f, -3.0f, 0.0f);
        final boolean hover = m.hover != null && m.hover.kind() == Kind.LEGEND_ROW && m.hover.target().equals(t);
        if (focus || hover) {
            Shapes.roundedRect(s, hitRow, 4.0f, focus ? Tokens.RAISED : Tokens.alpha(Tokens.RAISED, 0.6f));
        }
        final float sw = Math.max(5.0f, Text.lineHeight(name) * 0.62f);
        final float sy = row.y + (Text.lineHeight(name) - sw) * 0.5f;
        final Rect swatch = new Rect(row.x, sy, sw * (SeismicCatalog.LIQUIDS.equals(t) ? 1.2f : 1.0f), sw);
        if (hidden) {
            Shapes.roundedRectOutline(s, swatch, 1.0f, 1.0f, Tokens.alpha(SurveyKit.colour(t, defaultKind(t)), 0.5f));
        } else {
            SurveyKit.swatch(s, swatch, t, defaultKind(t));
        }
        final String count = l == null ? "none" : l.bodies.size() + " " + SurveyFinds.plural(t, l.bodies.size());
        final float countW = s.textWidth(count, micro);
        final float tx = row.x + sw * 1.2f + 6.0f;
        s.text(Text.truncate(s, SurveyFinds.station(t), row.right() - tx - countW - 6.0f, name), tx, row.y,
                hidden ? Tokens.INK_3 : focus || hover ? Tokens.INK : Tokens.INK_2, name);
        s.textRight(count, row.right(), row.y + Text.lineHeight(name) - Text.lineHeight(micro), Tokens.INK_3, micro);
        final String near;
        if (hidden) {
            near = "hidden";
        } else if (l == null) {
            near = "none in the ground read";
        } else {
            final SurveyFinds.Find f = SurveyFinds.nearest(m.scene, t, -1, m.refX, m.refZ);
            near = f == null ? "" : "nearest " + f.where();
        }
        s.text(Text.truncate(s, near, row.right() - tx, micro), tx, row.y + Text.lineHeight(name) + 2.0f,
                l == null || hidden ? Tokens.INK_3 : Tokens.INK_2, micro);
        hits.add(Kind.LEGEND_ROW, index, t, 0L, hitRow);
        hits.add(Kind.LEGEND_SWATCH, index, t, 0L, swatch.inset(-3.0f));
    }

    static final int[] LEVELS = {63, 0, -54, -64};
    static final String[] LEVEL_NAMES = {"sea level", "deepslate", "lava lakes", "bedrock"};

    public record DepthLayout(Rect logCard, Rect log, Rect scanCard, Rect band) {
    }

    public static DepthLayout depthLayout(Rect body) {
        final float logW = Math.min(240.0f, body.w * 0.42f);
        final Rect logCard = new Rect(body.x, body.y, logW, body.h);
        final Rect right = new Rect(logCard.right() + 8.0f, body.y, body.w - logW - 8.0f, body.h);
        final float bandH = 56.0f;
        return new DepthLayout(logCard, logCard.inset(8.0f), new Rect(right.x, right.y, right.w, right.h - bandH - 8.0f),
                new Rect(right.x, right.bottom() - bandH, right.w, bandH));
    }

    static void depth(PanelSurface s, Rect body, Model m, Hits hits) {
        final DepthLayout l = depthLayout(body);
        Shapes.card(s, l.logCard(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        strataLog(s, l.log(), m, Text.MICRO);
        hits.add(Kind.LOG_PLOT, 0, "", 0L, logPlot(l.log(), Text.MICRO, legendOrder(m.scene).size()));
        Shapes.card(s, l.scanCard(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        final SurveyKit.Scan o = scanOptions(m, Text.MICRO);
        o.callouts = false;
        o.pitch = Math.min(o.pitch, 18.0);

        o.budget = 1_000;
        o.cloud = 600;
        SurveyKit.scan(s, l.scanCard().inset(6.0f), m.scene, o);
        bandCard(s, l.band(), m, Text.MICRO, hits);
    }

    public static void strataLog(PanelSurface s, Rect r, Model m, float micro) {
        final SurveyScene sc = m.scene;
        final int top = logTop(sc);
        final int bottom = sc.extent()[1];
        final List<String> targets = legendOrder(sc);
        final float lh = Text.lineHeight(micro);
        final Rect plot = logPlot(r, micro, targets.size());
        final int headRows = headRows(plot.w / Math.max(1, targets.size()), micro);
        final float span = Math.max(1, top - bottom);
        if (!Double.isNaN(m.sliceLo)) {
            final float y0 = yOf(plot, top, span, m.sliceHi + 1.0);
            final float y1 = yOf(plot, top, span, m.sliceLo);

            final float bx = plot.x - 2.0f;
            Shapes.rect(s, bx, y0, r.right() - bx, y1 - y0, Tokens.alpha(Tokens.ACCENT_DIM, 0.8f));
            Shapes.rect(s, bx, y0, r.right() - bx, 1.0f, Tokens.alpha(Tokens.ACCENT, 0.8f));
            Shapes.rect(s, bx, y1 - 1.0f, r.right() - bx, 1.0f, Tokens.alpha(Tokens.ACCENT, 0.8f));
        }
        for (int y = (int) Math.ceil(bottom / 16.0) * 16; y <= top; y += 16) {
            final float py = yOf(plot, top, span, y);
            Shapes.rect(s, plot.x, py, plot.w, 1.0f, Tokens.alpha(Tokens.LINE, 0.9f));
            s.textRight(String.valueOf(y), plot.x - 4.0f, py - lh * 0.5f, Tokens.INK_3, micro);
        }
        for (int i = 0; i < LEVELS.length; i++) {
            final int y = LEVELS[i];
            if (y < bottom || y > top) {
                continue;
            }
            final float py = yOf(plot, top, span, y);
            for (float x = plot.x; x < plot.right() - 2.0f; x += 6.0f) {
                Shapes.rect(s, x, py, 3.0f, 1.0f, Tokens.alpha(Tokens.INK_3, 0.75f));
            }
            if (py + 2.0f + lh <= r.bottom()) {
                s.textRight(LEVEL_NAMES[i], plot.right(), py + 2.0f, Tokens.alpha(Tokens.INK_3, 0.9f), micro);
            }
        }
        final int lanes = Math.max(1, targets.size());
        final float laneW = plot.w / lanes;
        for (int i = 0; i < targets.size(); i++) {
            final String t = targets.get(i);
            final float lx = plot.x + i * laneW;
            final int kind = defaultKind(t);
            final boolean hidden = m.hidden.contains(t);
            final boolean dim = !m.focus.isEmpty() && !m.focus.equals(t);

            final String head = station(t);
            final String shortHead = code(t);
            final float sw = Math.min(laneW - 2.0f, 5.0f);
            if (SeismicCatalog.LIQUIDS.equals(t)) {
                Shapes.roundedRect(s, new Rect(lx + (laneW - sw) * 0.5f, r.y, sw * 0.6f, sw), 1.0f,
                        SurveyKit.colour(t, SeismicCatalog.KIND_LAVA));
                Shapes.roundedRect(s, new Rect(lx + (laneW - sw) * 0.5f + sw * 0.5f, r.y, sw * 0.5f, sw), 1.0f,
                        SurveyKit.colour(t, SeismicCatalog.KIND_WATER));
            } else {
                Shapes.roundedRect(s, new Rect(lx + (laneW - sw) * 0.5f, r.y, sw, sw), 1.0f, SurveyKit.colour(t, kind));
            }

            final float room = headRows == 1 ? laneW - 1.0f : laneW * 2.0f - 2.0f;
            if (s.textWidth(shortHead, micro) <= room) {
                s.textCenter(shortHead, lx + laneW * 0.5f, r.y + sw + 2.0f + (headRows == 2 ? (i % 2) * lh : 0.0f),
                        hidden ? Tokens.INK_3 : Tokens.INK_2, micro);
            }
            final SurveyScene.Layer l = sc.layer(t);
            if (l == null || hidden) {
                continue;
            }
            core(s, plot, top, span, lx, laneW, l, m, dim);
        }
    }

    static final float CORE_ROW = 3.0f;

    static void core(PanelSurface s, Rect plot, int top, float span, float lx, float laneW, SurveyScene.Layer l,
                     Model m, boolean dim) {
        final int rows = Math.max(1, (int) (plot.h / CORE_ROW));
        final double rowBlocks = span / rows;
        final double[] amount = new double[rows];
        final double[] lava = new double[rows];
        for (final SurveyScene.Body b : l.bodies) {
            final double blocks = (double) b.grain * b.grain * b.grain;
            for (final int c : b.cells) {
                final double y = b.cellY(c) + b.grain * 0.5;
                final int row = (int) Math.max(0, Math.min(rows - 1, Math.floor((top - y) / rowBlocks)));
                amount[row] += blocks;
                if (b.kind == SeismicCatalog.KIND_LAVA) {
                    lava[row] += blocks;
                }
            }
        }
        double most = 0.0;
        for (final double a : amount) {
            most = Math.max(most, a);
        }
        if (most <= 0.0) {
            return;
        }
        final boolean liquids = SeismicCatalog.LIQUIDS.equals(l.target);
        final int cls = SurveyKit.classOf(l.target, 0);
        final float rowH = plot.h / rows;
        final float room = Math.max(1.5f, laneW - 4.0f);
        int run = -1;
        float runW = 0.0f;
        int runColour = 0;
        for (int row = 0; row <= rows; row++) {
            float w = 0.0f;
            int colour = 0;
            if (row < rows && amount[row] > 0.0) {
                w = (float) (1.5 + (room - 1.5) * Math.log1p(amount[row]) / Math.log1p(most));
                w = Math.round(w * 2.0f) / 2.0f;
                final double hiY = top - row * rowBlocks;
                final double loY = top - (row + 1) * rowBlocks;
                final boolean in = Double.isNaN(m.sliceLo) || (hiY >= m.sliceLo && loY <= m.sliceHi + 1.0);
                final int kind = liquids ? (lava[row] * 2.0 >= amount[row] ? SeismicCatalog.KIND_LAVA
                        : SeismicCatalog.KIND_WATER) : 0;
                final float a = (in ? (cls == SurveyKit.ORE ? 0.9f : 0.42f) : 0.18f) * (dim ? 0.45f : 1.0f);
                colour = Tokens.alpha(SurveyKit.colour(l.target, kind), a);
            }
            if (run >= 0 && (w != runW || colour != runColour)) {
                Shapes.rect(s, lx + (laneW - runW) * 0.5f, plot.y + run * rowH, runW, (row - run) * rowH, runColour);
                run = -1;
            }
            if (run < 0 && w > 0.0f) {
                run = row;
                runW = w;
                runColour = colour;
            }
        }
    }

    static int logTop(SurveyScene sc) {
        int top = Integer.MIN_VALUE;
        for (final SurveyScene.Bowl b : sc.bowls) {
            top = Math.max(top, b.y);
        }
        return top == Integer.MIN_VALUE ? 64 : top;
    }

    static String code(String target) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return "VOID";
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return "LIQ";
        }
        final String name = SurveyFinds.station(target).toUpperCase(Locale.ROOT);
        return name.length() <= 4 ? name : name.substring(0, 3);
    }

    public static Rect logPlot(Rect r, float micro, int lanes) {
        final float lh = Text.lineHeight(micro);
        final int rows = headRows((r.w - 28.0f) / Math.max(1, lanes), micro);
        return new Rect(r.x + 28.0f, r.y + lh * rows + 10.0f, r.w - 28.0f, r.h - lh * rows - 12.0f);
    }

    static int headRows(float laneW, float micro) {
        return laneW - 1.0f >= 24.0f * micro ? 1 : 2;
    }

    public static double logY(Rect plot, SurveyScene sc, double py) {
        final int top = logTop(sc);
        final float span = Math.max(1, top - sc.extent()[1]);
        final double t = Math.max(0.0, Math.min(1.0, (py - plot.y) / plot.h));
        return top - t * span;
    }

    private static float yOf(Rect plot, int top, float span, double y) {
        return (float) (plot.y + Math.max(0.0, Math.min(1.0, (top - y) / span)) * plot.h);
    }

    static void bandCard(PanelSurface s, Rect r, Model m, float micro, Hits hits) {
        Shapes.card(s, r, 8.0f, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r.inset(8.0f);
        if (Double.isNaN(m.sliceLo)) {
            s.text(Text.truncate(s, "Drag on the log to pick a band of depth.", in.w, Text.BODY), in.x, in.y + 4.0f,
                    Tokens.INK_2, Text.BODY);
            s.text(Text.truncate(s, "The scan beside it shows only that band; scroll on the log to move it.", in.w,
                    micro), in.x, in.y + 6.0f + Text.lineHeight(Text.BODY), Tokens.INK_3, micro);
            return;
        }
        s.text(String.format(Locale.ROOT, "Y %d TO %d", (int) m.sliceLo, (int) m.sliceHi), in.x, in.y, Tokens.INK_3,
                micro);
        final String clear = "Clear band";
        chip(s, hits, m, Kind.CLEAR_BAND, 0, "", 0L, in.right() - s.textWidth(clear, Text.MICRO) - 12.0f, in.y - 3.0f,
                clear, false);
        float x = in.x;
        float y = in.y + Text.lineHeight(micro) + 5.0f;
        final float entryH = Text.lineHeight(Text.META) + Text.lineHeight(micro) + 6.0f;
        int shown = 0;
        int left = 0;
        // liquids first, lava is the thing that kills you
        final List<String> order = new ArrayList<>(legendOrder(m.scene));
        if (order.remove(SeismicCatalog.LIQUIDS)) {
            order.add(0, SeismicCatalog.LIQUIDS);
        }
        for (final String t : order) {
            final SurveyScene.Layer l = m.scene.layer(t);
            if (l == null || m.hidden.contains(t)) {
                continue;
            }
            int n = 0;
            SurveyScene.Body near = null;
            for (final SurveyScene.Body b : l.bodies) {
                if (b.maxY >= m.sliceLo && b.minY <= m.sliceHi) {
                    n++;
                    if (near == null || b.nearDist < near.nearDist) {
                        near = b;
                    }
                }
            }
            if (n == 0) {
                continue;
            }
            final SurveyFinds.Find f = SurveyKit.inBand(near, m.sliceLo, m.sliceHi, m.refX, m.refY, m.refZ);
            final String a = counted(t, near.kind, n);
            final String b = "nearest " + f.where();
            final float w = Math.max(s.textWidth(a, Text.META), s.textWidth(b, micro)) + 16.0f;
            if (x + w > in.right() && x > in.x) {
                if (y + entryH * 2.0f > in.bottom() + 2.0f) {
                    left++;
                    continue;
                }
                x = in.x;
                y += entryH;
            }
            SurveyKit.swatch(s, new Rect(x, y + 2.0f, 5.0f, 5.0f), t, near.kind);
            s.text(Text.truncate(s, a, in.right() - x - 9.0f, Text.META), x + 9.0f, y, Tokens.INK, Text.META);
            s.text(Text.truncate(s, b, in.right() - x - 9.0f, micro), x + 9.0f, y + Text.lineHeight(Text.META) + 2.0f,
                    Tokens.INK_2, micro);
            x += w + 6.0f;
            shown++;
        }
        if (shown == 0) {
            s.text("Nothing found in this band.", in.x, y, Tokens.INK_3, Text.META);
        } else if (left > 0) {
            s.textRight("+" + left + " more", in.right(), in.y, Tokens.INK_3, micro);
        }
    }

    public record Row(SurveyFinds.Find find, String target) {
    }

    public static List<Row> finds(Model m) {
        final List<Row> out = new ArrayList<>();
        for (final SurveyScene.Layer l : m.scene.layers) {
            if (m.hidden.contains(l.target) || (!m.focus.isEmpty() && !m.focus.equals(l.target))) {
                continue;
            }
            for (final SurveyScene.Body b : l.bodies) {
                out.add(new Row(SurveyFinds.place(b, b.nearX, b.nearY, b.nearZ, m.refX, m.refZ), l.target));
            }
        }
        if (m.newOnly) {
            out.removeIf(r -> {
                final SurveyScene.Body b = r.find().body();
                return b.newest == Long.MIN_VALUE || m.now < b.newest
                        || m.now - (b.newest + b.echo) > SeismicLinks.NEW_TICKS;
            });
            out.sort((a, b) -> Long.compare(b.find().body().newest + b.find().body().echo,
                    a.find().body().newest + a.find().body().echo));
            return out;
        }

        out.sort((a, b) -> SurveyFinds.closer(a.find(), b.find()));
        return out;
    }

    public static int pickedRow(Model m, List<Row> rows) {
        if (rows.isEmpty()) {
            return -1;
        }
        if (m.selectedKey != Long.MIN_VALUE) {
            final SurveyScene.Body b = bodyAt(m.scene, m.selectedKey);
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).find().body() == b) {
                    return i;
                }
            }
        }
        final Headline h = headline(m);
        if (h.find() != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).find().body() == h.find().body()) {
                    return i;
                }
            }
        }
        return 0;
    }

    public record FindsLayout(Rect table, Rect chips, Rect ledger, Rect pick) {
    }

    public static FindsLayout findsLayout(Rect body) {
        final float pickW = Math.min(200.0f, body.w * 0.36f);
        final Rect table = new Rect(body.x, body.y, body.w - pickW - 8.0f, body.h);
        final Rect in = table.inset(8.0f);
        return new FindsLayout(table, new Rect(in.x, in.y, in.w, CHIP_H), new Rect(in.x, in.y + CHIP_H + 7.0f, in.w,
                in.h - CHIP_H - 7.0f), new Rect(table.right() + 8.0f, body.y, pickW, body.h));
    }

    public static int ledgerRows(Rect ledger) {
        return Math.max(1, (int) ((ledger.h - Text.lineHeight(Text.MICRO) * 2.0f - 9.0f) / ROW_H));
    }

    static void finds(PanelSurface s, Rect body, Model m, Hits hits) {
        final FindsLayout l = findsLayout(body);
        Shapes.card(s, l.table(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        final List<Row> rows = finds(m);
        filterChips(s, l.chips(), m, hits);
        ledger(s, l.ledger(), m, rows, m.ledgerScroll, hits);
        Shapes.card(s, l.pick(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        final int sel = pickedRow(m, rows);
        picked(s, l.pick().inset(8.0f), m, sel < 0 ? null : rows.get(sel), hits);
    }

    public static void filterChips(PanelSurface s, Rect r, Model m, Hits hits) {
        final List<String> targets = legendOrder(m.scene);
        float x = r.x;
        if (m.newOnly) {
            x = chip(s, hits, m, Kind.NEW_FINDS, 1, "", 0L, x, r.y, "New finds only (click for all)", true).right()
                    + 4.0f;
        }
        final int skip = Math.max(0, Math.min(m.chipScroll, Math.max(0, targets.size() - 1)));
        for (int i = -1; i < targets.size(); i++) {
            if (i >= 0 && i < skip) {
                continue;
            }
            final String t = i < 0 ? "" : targets.get(i);
            final String label = i < 0 ? "All" : SurveyFinds.station(t).toUpperCase(Locale.ROOT);
            final float w = s.textWidth(label, Text.MICRO) + (i < 0 ? 12.0f : 19.0f);

            final float rest = i == targets.size() - 1 ? 0.0f : s.textWidth("+" + (targets.size() - i - 1), Text.MICRO)
                    + 6.0f;
            if (x + w + rest > r.right()) {
                s.text("+" + (targets.size() - i), x + 2.0f, r.y + (r.h - Text.lineHeight(Text.MICRO)) * 0.5f,
                        Tokens.INK_3, Text.MICRO);
                break;
            }
            final Rect c = new Rect(x, r.y, w, r.h);
            final boolean on = t.equals(m.focus);
            final boolean hover = m.hover != null && m.hover.kind() == Kind.CHIP && m.hover.index() == i;
            if (on || hover) {
                Shapes.roundedRect(s, c, 4.0f, on ? Tokens.OVERLAY : Tokens.RAISED);
            }
            Shapes.roundedRectOutline(s, c, 4.0f, 1.0f, on || hover ? Tokens.LINE_HI : Tokens.LINE);
            float tx = c.x + 6.0f;
            if (i >= 0) {
                SurveyKit.swatch(s, new Rect(tx, c.centerY() - 2.5f, 5.0f, 5.0f), t, defaultKind(t));
                tx += 8.0f;
            }
            s.text(label, tx, c.y + (c.h - Text.lineHeight(Text.MICRO)) * 0.5f + 0.5f, on || hover ? Tokens.INK
                    : Tokens.INK_2, Text.MICRO);
            hits.add(Kind.CHIP, i, t, 0L, c);
            x += w + 4.0f;
        }
    }

    public static void ledger(PanelSurface s, Rect r, Model m, List<Row> rows, int scroll, Hits hits) {
        final float micro = Text.MICRO;
        final float[] col = ledgerColumns(r);
        final float lh = Text.lineHeight(micro);
        s.text("FIND", col[0], r.y, Tokens.INK_3, micro);
        s.textRight("BLOCKS", col[2] - 6.0f, r.y, Tokens.INK_3, micro);
        s.text("ALONG THE GROUND", col[2], r.y, Tokens.INK_3, micro);
        s.textRight("Y", col[4] - 6.0f, r.y, Tokens.INK_3, micro);
        s.textRight("READ", r.right(), r.y, Tokens.INK_3, micro);
        Shapes.rect(s, r.x, r.y + lh + 2.0f, r.w, 1.0f, Tokens.LINE);
        float y = r.y + lh + 6.0f;
        final int first = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - 1)));
        final int picked = pickedRow(m, rows);
        for (int i = first; i < rows.size(); i++) {
            if (i - first >= ledgerRows(r)) {
                break;
            }
            final Row row = rows.get(i);
            final SurveyFinds.Find f = row.find();
            final boolean sel = i == picked;
            final Rect band = new Rect(r.x - 4.0f, y - 2.0f, r.w + 8.0f, ROW_H);
            final boolean hover = m.hover != null && m.hover.kind() == Kind.LEDGER_ROW && m.hover.index() == i;
            if (sel || hover) {
                Shapes.roundedRect(s, band, 3.0f, sel ? Tokens.RAISED : Tokens.alpha(Tokens.RAISED, 0.55f));
            }
            SurveyKit.swatch(s, new Rect(col[0], y + 1.5f, 5.0f, 5.0f), row.target(), f.body().kind);
            final String name = upper(row.target(), f.body().kind);
            final boolean done = worked(m, f.body());
            s.text(Text.truncate(s, name, col[1] - col[0] - 10.0f, Text.META), col[0] + 9.0f, y - 1.0f,
                    done ? Tokens.INK_3 : sel ? Tokens.INK : Tokens.INK_2, Text.META);
            if (done) {
                Shapes.rect(s, col[0] + 9.0f, y + 3.0f, Math.min(col[1] - col[0] - 10.0f, s.textWidth(name, Text.META)),
                        1.0f, Tokens.INK_3);
            }
            s.textRight(SurveyFinds.thousands(f.body().count), col[2] - 6.0f, y, Tokens.INK_2, micro);
            arrow(s, col[2] + 4.0f, y + 3.5f, 3.6f, f.bearing(), f.horiz() < 1.5 ? Tokens.INK_3 : Tokens.INK_2);
            final String where = f.horiz() < 1.5 ? "below" : Math.round(f.horiz()) + " m " + SurveyFinds.compass(f.bearing());
            s.text(where, col[2] + 11.0f, y, Tokens.INK, micro);
            s.textRight(String.valueOf(f.y()), col[4] - 6.0f, y, Tokens.INK, micro);
            final long lit = f.body().newest == Long.MIN_VALUE ? Long.MIN_VALUE : f.body().newest + f.body().echo;

            final boolean fresh = lit != Long.MIN_VALUE && m.now >= f.body().newest
                    && m.now - lit <= SeismicLinks.NEW_TICKS;
            final Long when = m.worked.get(keyOf(f));
            s.textRight(when != null ? "worked " + SurveyFinds.ago(m.now, when) : SurveyFinds.ago(m.now, lit), r.right(), y,
                    when != null ? Tokens.INK_3 : fresh ? Tokens.INK : Tokens.INK_3, micro);
            hits.add(Kind.LEDGER_ROW, i, row.target(), keyOf(f), band);
            y += ROW_H;
        }
        if (rows.isEmpty()) {
            s.text("Nothing found of the targets shown.", r.x, y, Tokens.INK_3, Text.META);
        } else if (first > 0 || first + ledgerRows(r) < rows.size()) {
            final String more = String.format(Locale.ROOT, "%d to %d of %d, scroll for more", first + 1,
                    Math.min(rows.size(), first + ledgerRows(r)), rows.size());
            s.textRight(more, r.right(), r.bottom() - lh, Tokens.INK_3, micro);
        }
    }

    public static float[] ledgerColumns(Rect r) {
        return new float[] {r.x, r.x + r.w * 0.30f, r.x + r.w * 0.44f, r.x + r.w * 0.66f, r.x + r.w * 0.80f};
    }

    public static void arrow(PanelSurface s, float cx, float cy, float rad, double bearing, int colour) {
        final double b = Math.toRadians(bearing);
        final float dx = (float) Math.sin(b);
        final float dy = (float) -Math.cos(b);
        final float tx = cx + dx * rad;
        final float ty = cy + dy * rad;
        final float bx = cx - dx * rad * 0.7f;
        final float by = cy - dy * rad * 0.7f;
        final float nx = -dy * rad * 0.55f;
        final float ny = dx * rad * 0.55f;
        s.tri(tx, ty, colour, bx + nx, by + ny, colour, bx - nx, by - ny, colour);
    }

    static void picked(PanelSurface s, Rect r, Model m, Row row, Hits hits) {
        if (row == null) {
            s.text("No find picked.", r.x, r.y, Tokens.INK_3, Text.META);
            return;
        }
        final SurveyFinds.Find f = row.find();
        final SurveyScene.Body b = f.body();
        SurveyKit.swatch(s, new Rect(r.x, r.y + 2.0f, 7.0f, 7.0f), row.target(), b.kind);
        s.text(Text.truncate(s, upper(row.target(), b.kind), r.w - 12.0f, Text.SUB), r.x + 12.0f, r.y, Tokens.INK,
                Text.SUB);
        float y = r.y + Text.lineHeight(Text.SUB) + 4.0f;
        final String size = SurveyFinds.thousands(b.count) + (b.count == 1 ? " block" : " blocks") + ", Y " + b.minY
                + (b.maxY != b.minY ? " to " + b.maxY : "");
        s.text(Text.truncate(s, size, r.w, Text.META), r.x, y, Tokens.INK_2, Text.META);
        y += Text.lineHeight(Text.META) + 6.0f;
        s.text("FROM THIS PANEL", r.x, y, Tokens.INK_3, Text.MICRO);
        y += Text.lineHeight(Text.MICRO) + 2.0f;
        s.text(f.horiz() < 1.5 ? "Straight below" : Math.round(f.horiz()) + " m " + SurveyFinds.compass(f.bearing()),
                r.x, y, Tokens.INK, Text.TITLE);
        arrow(s, r.right() - 10.0f, y + 8.0f, 7.0f, f.bearing(), Tokens.INK);
        y += Text.lineHeight(Text.TITLE) + 2.0f;
        s.text("dig to Y " + f.y(), r.x, y, Tokens.INK_2, Text.META);
        y += Text.lineHeight(Text.META) + 6.0f;
        final long lit = b.newest == Long.MIN_VALUE ? Long.MIN_VALUE : b.newest + b.echo;
        final boolean stale = m.now - b.newest > 72_000L * 4;
        s.text(Text.truncate(s, "Read " + SurveyFinds.ago(m.now, lit) + " ago" + (b.oldest != b.newest
                ? ", first " + SurveyFinds.ago(m.now, b.oldest) + " ago" : "") + (stale ? "; strike again to refresh" : ""),
                r.w, Text.MICRO), r.x, y, stale ? Tokens.CAUTION : Tokens.INK_3, Text.MICRO);
        y += Text.lineHeight(Text.MICRO) + 6.0f;
        final long key = BlockPos.asLong(f.x(), f.y(), f.z());
        final boolean pinned = m.pin != Long.MIN_VALUE && pinned(m) != null && pinned(m).body() == b;
        final float bx = chip(s, hits, m, Kind.PIN, 0, row.target(), key, r.x, y, pinned ? "Pinned to the face"
                : "Pin to the face", pinned).right() + 4.0f;
        chip(s, hits, m, Kind.SHOW_IN_SCAN, 0, row.target(), key, bx, y, "Show in scan", false);
        y += CHIP_H + 4.0f;
        final boolean done = worked(m, b);
        chip(s, hits, m, Kind.WORKED, 0, row.target(), key, r.x, y, done ? "Worked: take the mark off" : "Mark worked",
                done);
        y += CHIP_H + 6.0f;

        final Rect near = new Rect(r.x, y, r.w, r.bottom() - y);
        if (near.h < 60.0f) {
            return;
        }
        Shapes.roundedRect(s, near, 6.0f, Tokens.CANVAS);
        final SurveyKit.Scan o = scanOptions(m, Text.MICRO);
        o.focus = row.target();
        o.room = false;
        o.callouts = false;
        o.ruler = false;
        o.rigs = false;
        o.ground = Tokens.CANVAS;
        o.selected = b;
        o.centre = null;
        o.zoom = 1.0;
        final double reach = Math.max(12.0, Math.max(b.maxX - b.minX, b.maxZ - b.minZ) + 16.0);
        o.frame = new double[] {b.cx - reach, b.cy - reach * 0.6, b.cz - reach, b.cx + reach, b.cy + reach * 0.6,
                b.cz + reach};
        o.terrain = false;
        o.bowls = false;
        o.glow = false;
        o.budget = 900;
        o.cloud = 500;

        SurveyKit.scan(s, near.inset(4.0f), closeUp(m.scene, b, o.frame), o);
    }

    private static SurveyScene closeUpFrom;
    private static SurveyScene.Body closeUpOf;
    private static SurveyScene closeUp;

    static synchronized SurveyScene closeUp(SurveyScene scene, SurveyScene.Body b, double[] f) {
        if (closeUpFrom == scene && closeUpOf == b && closeUp != null) {
            return closeUp;
        }
        final List<SurveyScene.Body> near = new ArrayList<>();
        final SurveyScene.Layer l = scene.layer(b.target);
        if (l != null) {
            for (final SurveyScene.Body o : l.bodies) {
                if (o.maxX >= f[0] && o.minX <= f[3] && o.maxY >= f[1] && o.minY <= f[4] && o.maxZ >= f[2]
                        && o.minZ <= f[5]) {
                    near.add(o);
                }
            }
        }
        closeUpFrom = scene;
        closeUpOf = b;
        closeUp = new SurveyScene(scene.stamp, scene.builtAt, List.of(new SurveyScene.Layer(b.target, near)), List.of(),
                List.of(), SurveyScene.Surface.NONE);
        return closeUp;
    }

    public record HistoryLayout(Rect logCard, Rect mapCard, Rect rigsCard) {
    }

    public static HistoryLayout historyLayout(Rect body) {
        final float sideW = Math.min(224.0f, body.w * 0.40f);
        final Rect log = new Rect(body.x, body.y, body.w - sideW - 8.0f, body.h);
        final float mapH = Math.max(110.0f, body.h * 0.56f);
        final Rect map = new Rect(log.right() + 8.0f, body.y, sideW, mapH);
        return new HistoryLayout(log, map, new Rect(map.x, map.bottom() + 8.0f, sideW, body.h - mapH - 8.0f));
    }

    static void history(PanelSurface s, Rect body, Model m, Hits hits) {
        final HistoryLayout l = historyLayout(body);
        Shapes.card(s, l.logCard(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        echoLog(s, l.logCard().inset(8.0f), m, Text.MICRO, m.logScroll, hits);
        Shapes.card(s, l.mapCard(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        coverage(s, l.mapCard().inset(8.0f), m);
        Shapes.card(s, l.rigsCard(), 8.0f, Tokens.SURFACE, Tokens.LINE);
        rigList(s, l.rigsCard().inset(8.0f), m, hits);
    }

    public static void echoLog(PanelSurface s, Rect r, Model m, float micro, int scroll, Hits hits) {
        s.text("EVERY BLOW, NEWEST FIRST", r.x, r.y, Tokens.INK_3, micro);
        final boolean cleared = m.since != Long.MIN_VALUE;
        final String cmd = cleared ? "Show every blow" : "Hide blows so far (this panel)";
        chip(s, hits, m, cleared ? Kind.SHOW_ALL : Kind.HIDE_BEFORE, 0, "", 0L,
                r.right() - s.textWidth(cmd, Text.MICRO) - 12.0f, r.y - 3.0f, cmd, cleared);
        final float lh = Text.lineHeight(micro);
        final float rowH = lh + 19.0f;
        float y = r.y + lh + 8.0f;
        final List<SurveyScene.Bowl> bowls = new ArrayList<>(m.scene.bowls);
        Collections.reverse(bowls);
        final int first = Math.max(0, Math.min(scroll, Math.max(0, bowls.size() - 1)));
        for (int i = first; i < bowls.size(); i++) {
            final SurveyScene.Bowl b = bowls.get(i);
            if (y + rowH > r.bottom()) {
                break;
            }
            final int kind = defaultKind(b.target);
            final int rig = rigNumber(m, b.x, b.y, b.z);
            SurveyKit.swatch(s, new Rect(r.x, y + 1.5f, 5.0f, 5.0f), b.target, kind);
            final String head = SurveyFinds.station(b.target) + ", R" + rig + ", " + b.reach + " m"
                    + (b.done && b.front + 1.0e-3f < b.reach ? ", cut short" : b.full ? ", book full" : "");
            final String tail = (b.veins == 0 ? "nothing found" : b.veins + " " + SurveyFinds.plural(b.target, b.veins))
                    + ", " + SurveyFinds.ago(m.now, b.at);
            final float tw = s.textWidth(tail, micro);
            s.text(Text.truncate(s, head, r.w - tw - 18.0f, Text.META), r.x + 9.0f, y - 1.0f, Tokens.INK_2, Text.META);
            s.textRight(tail, r.right(), y, Tokens.INK_3, micro);
            final Rect strip = new Rect(r.x + 9.0f, y + lh + 3.0f, r.w - 9.0f, 13.0f);
            seismogram(s, strip, m, b, 1.0f);
            y += rowH;
        }
        if (cleared && m.scene.bowls.isEmpty()) {
            s.text("Every blow before " + SurveyFinds.ago(m.now, m.since) + " ago is hidden on this panel.", r.x, y,
                    Tokens.INK_3, Text.META);
        }
    }

    static void seismogram(PanelSurface s, Rect strip, Model m, SurveyScene.Bowl b, float hair) {
        Shapes.rect(s, strip.x, strip.bottom() - 1.0f, strip.w, 1.0f, Tokens.LINE_HI);
        Shapes.rect(s, strip.x, strip.y, Math.max(1.0f, hair * 1.5f), strip.h - 1.0f, Tokens.alpha(Tokens.INK_2, 0.8f));
        final float listen = SeismicWave.listenTicks(b.reach);

        final int cols = Math.max(1, (int) ((strip.w - 4.0f) / 3.0f));
        final float[] tall = new float[cols];
        final int[] colour = new int[cols];
        for (final SurveyScene.Layer l : m.scene.layers) {
            if (!l.target.equals(b.target)) {
                continue;
            }
            for (final SurveyScene.Body body : l.bodies) {
                if (body.newest != b.at) {
                    continue;
                }
                final int col = Math.min(cols - 1, (int) (Math.min(1.0f, body.echo / listen) * cols));
                final float h = (float) Math.min(strip.h - 1.0f, 2.0 + Math.log(1 + body.count) * 2.4);
                if (h > tall[col]) {
                    tall[col] = h;
                    colour[col] = Tokens.alpha(SurveyKit.colour(l.target, body.kind), 0.9f);
                }
            }
        }
        final float colW = (strip.w - 4.0f) / cols;
        for (int c = 0; c < cols; c++) {
            if (tall[c] > 0.0f) {
                Shapes.rect(s, strip.x + 2.0f + c * colW, strip.bottom() - 1.0f - tall[c], Math.max(1.0f, hair),
                        tall[c], colour[c]);
            }
        }
    }

    public static int rigNumber(Model m, int x, int y, int z) {
        final Integer n = m.rigNumbers.get(com.createstats.seismic.archive.ArchiveBlow.key(x, y, z));
        if (n != null) {
            return n;
        }
        for (int i = 0; i < m.scene.rigs.size(); i++) {
            final SurveyScene.Rig r = m.scene.rigs.get(i);
            if (r.x == x && r.y == y && r.z == z) {
                return i + 1;
            }
        }
        return 0;
    }

    static int rigIndex(SurveyScene sc, SurveyScene.Bowl b) {
        for (int i = 0; i < sc.rigs.size(); i++) {
            final SurveyScene.Rig r = sc.rigs.get(i);
            if (r.x == b.x && r.y == b.y && r.z == b.z) {
                return i;
            }
        }
        return 0;
    }

    static void coverage(PanelSurface s, Rect r, Model m) {
        s.text("GROUND READ", r.x, r.y, Tokens.INK_3, Text.MICRO);
        final Rect plot = new Rect(r.x, r.y + Text.lineHeight(Text.MICRO) + 6.0f, r.w,
                r.h - Text.lineHeight(Text.MICRO) * 2.0f - 14.0f);
        final SurveyKit.Scan o = scanOptions(m, Text.MICRO);
        o.yaw = 0.0;
        o.pitch = 90.0;
        o.zoom = 1.0;
        o.centre = null;
        o.callouts = false;
        o.ruler = false;
        o.terrain = true;
        o.terrainStep = 2;
        o.bowls = false;
        o.budget = 0;
        o.cloud = 0;

        o.here = false;
        o.hidden = Set.copyOf(m.scene.targetsSurveyed());
        final ScanCamera cam = SurveyKit.scan(s, plot, m.scene, o);
        final double[] c = new double[3];
        final long newest = m.scene.newestBlow();

        final java.util.Map<String, SurveyScene.Bowl> widest = new java.util.LinkedHashMap<>();
        final java.util.Map<String, Long> latest = new java.util.HashMap<>();
        for (final SurveyScene.Bowl b : m.scene.bowls) {
            final String key = b.x + "," + b.y + "," + b.z + "," + b.target;
            final SurveyScene.Bowl was = widest.get(key);
            if (was == null || b.recorded > was.recorded) {
                widest.put(key, b);
            }
            latest.merge(key, b.at, Math::max);
        }
        final int segs = Math.max(12, SurveyKit.MAX_LIP_SEGMENTS * 3 / Math.max(1, widest.size()));
        for (final java.util.Map.Entry<String, SurveyScene.Bowl> e : widest.entrySet()) {
            final SurveyScene.Bowl b = e.getValue();
            cam.project(b.x + 0.5, b.y, b.z + 0.5, c);
            final double rad = b.recorded * cam.scale;
            final long age = Math.max(0L, newest - latest.get(e.getKey()));
            final float a = (float) Math.max(0.25, 1.0 - age / (72_000.0 * 6.0));
            SurveyKit.ellipse(s, plot, c[0], c[1], rad, rad, 0.0, Math.PI * 2.0, 1.0f,
                    Tokens.alpha(SurveyKit.colour(b.target, defaultKind(b.target)), 0.75f * a), 0, segs);
        }
        cam.project(m.refX, m.refY, m.refZ, c);
        final boolean here = plot.inset(4.0f).contains(c[0], c[1]);
        if (here) {
            SurveyKit.ellipse(s, plot, c[0], c[1], 3.0, 3.0, 0.0, Math.PI * 2.0, 1.0f, Tokens.ACCENT, 0);
        }
        long oldest = Long.MAX_VALUE;
        for (final SurveyScene.Bowl b : m.scene.bowls) {
            oldest = Math.min(oldest, b.at);
        }
        final float ly = r.bottom() - Text.lineHeight(Text.MICRO);
        float lx = r.x;
        if (here) {
            SurveyKit.ellipse(s, r, lx + 4.5, ly + Text.lineHeight(Text.MICRO) * 0.5 - 0.5, 2.5, 2.5, 0.0, Math.PI * 2.0,
                    1.0f, Tokens.ACCENT, 0);
            s.text("this panel", lx + 10.0f, ly, Tokens.INK_2, Text.MICRO);
            lx += 10.0f + s.textWidth("this panel   ", Text.MICRO);
        }
        s.text(Text.truncate(s, m.scene.bowls.size() + " blows, the oldest " + SurveyFinds.ago(m.now, oldest) + " ago",
                r.right() - lx, Text.MICRO), lx, ly, Tokens.INK_3, Text.MICRO);
    }

    static void rigList(PanelSurface s, Rect r, Model m, Hits hits) {
        s.text("RIGS", r.x, r.y, Tokens.INK_3, Text.MICRO);
        float y = r.y + Text.lineHeight(Text.MICRO) + 5.0f;
        final float rowH = Text.lineHeight(Text.META) + 5.0f;
        int n = 0;
        for (final SurveyScene.Rig rig : m.scene.rigs) {
            n++;
            if (n <= m.rigScroll) {
                continue;
            }
            if (y + rowH > r.bottom()) {
                s.textRight("+" + (m.scene.rigs.size() - n + 1) + ", scroll", r.right(), y, Tokens.INK_3, Text.MICRO);
                break;
            }
            final long key = BlockPos.asLong(rig.x, rig.y, rig.z);
            final SeismicLinks.RigState st = m.rigStates.get(key);
            final String target = st != null && !st.target().isEmpty() ? st.target() : rig.target;
            final int kind = defaultKind(target);
            if (!target.isEmpty()) {
                SurveyKit.swatch(s, new Rect(r.x, y + 2.0f, 5.0f, 5.0f), target, kind);
            }
            s.text("R" + rigNumber(m, rig.x, rig.y, rig.z), r.x + 9.0f, y, rig.linked ? Tokens.INK : Tokens.INK_3,
                    Text.META);
            final float nameX = r.x + 9.0f + s.textWidth("R00 ", Text.META);

            final double gx = rig.x + 0.5 - m.refX;
            final double gz = rig.z + 0.5 - m.refZ;
            final String at = Math.round(Math.sqrt(gx * gx + gz * gz)) + " m "
                    + SurveyFinds.compass(SurveyFinds.bearing(m.refX, m.refZ, rig.x + 0.5, rig.z + 0.5));
            final String name = (target.isEmpty() ? "untuned" : station(target)) + (st != null ? " " + st.radius()
                    + " m" : "") + ", " + at;
            if (!rig.linked) {
                final boolean armed = m.armed == key;
                final String forget = armed ? "Click again to forget" : "Forget";
                final Rect c = chip(s, hits, m, Kind.FORGET, n - 1, "", key, r.right() - s.textWidth(forget, Text.MICRO)
                        - 12.0f, y - 2.0f, forget, armed);
                s.text(Text.truncate(s, name + ", remembered", c.x - nameX - 4.0f, Text.MICRO), nameX, y + 1.0f,
                        Tokens.INK_3, Text.MICRO);
            } else {
                final String state = stateWords(st);
                final float sw = s.textWidth(state, Text.MICRO);
                s.text(Text.truncate(s, name, r.right() - nameX - sw - 8.0f, Text.MICRO), nameX, y + 1.0f, Tokens.INK_2,
                        Text.MICRO);
                s.textRight(state, r.right(), y + 1.0f, stateInk(st), Text.MICRO);
            }
            y += rowH;
        }
    }

    public static String station(String target) {
        return SurveyFinds.station(target).toUpperCase(Locale.ROOT);
    }

    public static String stateWords(SeismicLinks.RigState st) {
        if (st == null) {
            return "linked";
        }
        return switch (SeismicRig.State.of(st.state())) {
            case READY -> "ready";
            case CHARGING -> "charging " + st.chargePct() + "%";
            case WAITING -> "no power, " + st.chargePct() + "%";
            case SHED -> "shed, " + st.chargePct() + "%";
            case NO_GROUND -> "no ground";
            case DROPPING -> "dropping";
            default -> "untuned";
        };
    }

    static int stateInk(SeismicLinks.RigState st) {
        if (st == null) {
            return Tokens.INK_3;
        }
        return switch (SeismicRig.State.of(st.state())) {
            case READY -> Tokens.GOOD;
            case CHARGING, DROPPING -> Tokens.INK;
            case WAITING, SHED -> Tokens.CAUTION;
            case NO_GROUND -> Tokens.CRITICAL;
            default -> Tokens.INK_3;
        };
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
