package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.createstats.blockentity.SeismicLinks;
import com.createstats.seismic.SeismicRig;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.seismic.archive.SurveyFixtures;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.paint.FaceType;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.paint.SurveyFacePainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioLayout;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyArtTest {
    static final int BUDGET = 9_446;

    private static final String DIAMOND = "c:ores/diamond";
    private static List<SurveyArt.Model> models;
    private static List<String> names;

    @BeforeAll
    static void surveys() {
        models = new ArrayList<>();
        names = new ArrayList<>();
        final SurveyFixtures.Shot day = SurveyFixtures.afternoon();
        add(model(new SurveyFixtures.Shot("nothing linked", SurveyScene.EMPTY, 0L), "", false), "no rig linked");
        add(model(SurveyFixtures.linkedNoSurvey(), "", true), "a rig, no blow");
        add(model(SurveyFixtures.ownerToday(), "", true), "the owner's world today");
        add(model(day, "", true), "an afternoon");
        add(model(day, DIAMOND, true), "an afternoon, diamond focused");
        final SurveyArt.Model sliced = model(day, DIAMOND, true);
        sliced.sliceLo = -64;
        sliced.sliceHi = -40;
        sliced.hidden = Set.of("create_stats:voids");
        add(sliced, "an afternoon, sliced, voids hidden");
        final SurveyArt.Model moved = model(day, "", true);
        moved.yaw = 290.0;
        moved.pitch = 6.0;
        moved.zoom = 4.0;
        moved.centre = new double[] {164.5, -30.0, -353.5};
        add(moved, "an afternoon, zoomed onto the deep rig");
        final SurveyArt.Model plan = model(day, "", true);
        plan.pitch = 90.0;
        plan.yaw = 0.0;
        add(plan, "an afternoon, plan");
        final SurveyArt.Model section = model(day, "", true);
        section.pitch = 0.0;
        add(section, "an afternoon, section");
        final SurveyArt.Model pinned = model(day, "", true);
        final SurveyScene.Body first = day.scene().layers.get(0).bodies.get(0);
        pinned.pin = BlockPos.asLong(first.nearX, first.nearY, first.nearZ);
        pinned.since = day.now() - 30_000L;
        add(pinned, "an afternoon, a find pinned, older blows hidden");
        add(model(SurveyFixtures.live(), DIAMOND, true), "a blow landing");
        add(model(SurveyFixtures.caves(), "", true), "caves");
        final SurveyFixtures.Shot crowd = new SurveyFixtures.Shot("crowded", crowded(), CROWD_T + 600L);
        add(model(crowd, "", true), "a crowded site");
        add(model(crowd, "c:ores/iron", true), "a crowded site, iron focused");
        final SurveyArt.Model crowdPlan = model(crowd, "", true);
        crowdPlan.pitch = 90.0;
        crowdPlan.yaw = 0.0;
        add(crowdPlan, "a crowded site, plan");
    }

    private static final long CROWD_T = 5_000_000L;

    static SurveyScene crowded() {
        final java.util.Random rnd = new java.util.Random(42L);
        final String[] ores = {"c:ores/gold", "c:ores/diamond", "c:ores/iron", "c:ores/redstone", "c:ores/emerald",
                "c:ores/copper", "c:ores/lapis", "c:ores/coal"};
        final List<SurveyScene.Layer> layers = new ArrayList<>();
        for (final String t : ores) {
            layers.add(new SurveyScene.Layer(t, bodies(rnd, t, 0, 300, 12, 1)));
        }
        final List<SurveyScene.Body> wet = new ArrayList<>(bodies(rnd, com.createstats.seismic.SeismicCatalog.LIQUIDS, com.createstats.seismic.SeismicCatalog.KIND_LAVA, 30, 300, 2));
        wet.addAll(bodies(rnd, com.createstats.seismic.SeismicCatalog.LIQUIDS, com.createstats.seismic.SeismicCatalog.KIND_WATER, 30, 300, 2));
        layers.add(new SurveyScene.Layer(com.createstats.seismic.SeismicCatalog.LIQUIDS, wet));
        layers.add(new SurveyScene.Layer(com.createstats.seismic.SeismicCatalog.VOIDS, bodies(rnd, com.createstats.seismic.SeismicCatalog.VOIDS, 0, 60, 400, 2)));
        final List<SurveyScene.Bowl> bowls = new ArrayList<>();
        final List<SurveyScene.Rig> rigs = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            final int x = 100 + rnd.nextInt(200);
            final int z = -500 + rnd.nextInt(200);
            final String target = ores[i % ores.length];
            rigs.add(new SurveyScene.Rig(x, 70, z, i % 3 != 0, target, CROWD_T - i * 100L, 2));
            for (int k = 0; k < 2; k++) {
                bowls.add(new SurveyScene.Bowl(bowls.size() + 1, x, 70, z, k == 0 ? target : ores[(i + 3) % ores.length],
                        64, 64.0f, 64.0f, CROWD_T - i * 100L - k * 50L, -64, 0L, 0L, true, false, 20, 200, 6.0f));
            }
        }
        final int nx = 26;
        final int nz = 26;
        final short[] heights = new short[nx * nz];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = (short) (64 + rnd.nextInt(16));
        }
        return new SurveyScene(1L, CROWD_T, layers, bowls, rigs,
                new SurveyScene.Surface(Math.floorDiv(100, 8), Math.floorDiv(-500, 8), nx, nz, heights));
    }

    private static List<SurveyScene.Body> bodies(java.util.Random rnd, String target, int kind, int count, int most,
                                                 int grain) {
        final List<SurveyScene.Body> out = new ArrayList<>();
        for (int v = 0; v < count; v++) {
            final int x = 100 + rnd.nextInt(200);
            final int y = -60 + rnd.nextInt(120);
            final int z = -500 + rnd.nextInt(200);
            final int n = 1 + rnd.nextInt(most);
            final int side = Math.max(2, (int) Math.ceil(Math.cbrt(n)) + 1);
            final java.util.LinkedHashSet<Integer> cells = new java.util.LinkedHashSet<>();
            while (cells.size() < n) {
                cells.add(SurveyScene.cell(rnd.nextInt(side), rnd.nextInt(side), rnd.nextInt(side)));
            }
            final int[] c = cells.stream().mapToInt(Integer::intValue).toArray();
            final int e = side * grain;
            out.add(new SurveyScene.Body(target, kind, n * grain * grain * grain, grain, x, y, z, c, x, y, z, x + e,
                    y + e, z + e, x + e * 0.5f, y + e * 0.5f, z + e * 0.5f, CROWD_T - v * 7L, CROWD_T - v * 7L, 40, x, y,
                    z, 30.0f + v));
        }
        out.sort((a, b) -> Integer.compare(b.count, a.count));
        return out;
    }

    private static void add(SurveyArt.Model m, String name) {
        models.add(m);
        names.add(name);
    }

    static SurveyArt.Model model(SurveyFixtures.Shot shot, String focus, boolean linked) {
        final SurveyArt.Model m = new SurveyArt.Model();
        m.scene = shot.scene().heardAt(shot.now());
        m.now = shot.now();
        m.refX = SurveyFixtures.PANEL_X + 0.5;
        m.refY = SurveyFixtures.PANEL_Y + 0.5;
        m.refZ = SurveyFixtures.PANEL_Z + 0.5;
        m.linked = linked;
        m.focus = focus;

        m.heading = 90.0;

        final Map<Long, SeismicLinks.RigState> states = new HashMap<>();
        final SeismicRig.State[] cycle = {SeismicRig.State.READY, SeismicRig.State.CHARGING, SeismicRig.State.WAITING,
                SeismicRig.State.SHED, SeismicRig.State.NO_GROUND};
        int i = 0;
        for (final SurveyScene.Rig r : shot.scene().rigs) {
            if (r.linked) {
                states.put(BlockPos.asLong(r.x, r.y, r.z), new SeismicLinks.RigState(cycle[i % cycle.length].ordinal(),
                        45, 64, r.target, true));
                i++;
            }
        }
        m.rigStates = states;
        return m;
    }

    static Rect page(int guiW, int guiH) {
        final Rect v = StudioLayout.of(guiW, guiH).viewArea(false);
        return new Rect(0.0f, 0.0f, Math.round(v.w), Math.round(v.h));
    }

    static final int[][] SCREENS = {{853, 533}, {640, 360}, {960, 540}, {1280, 720}, {1920, 1080}};

    static PanelModel panel(Mk3Settings.FaceMode mode, SurveyArt.Model m) {
        final PanelModel p = new PanelModel();
        p.faceMode = mode;
        p.survey = m;
        return p;
    }

    static final Mk3Settings.FaceMode[] MODES = {Mk3Settings.FaceMode.SURVEY_SCAN, Mk3Settings.FaceMode.SURVEY_FINDS,
            Mk3Settings.FaceMode.SURVEY_DEPTH, Mk3Settings.FaceMode.SURVEY_ECHO};

    private static List<Rect> faces(List<String> faceNames) {
        final List<Rect> out = new ArrayList<>();
        for (final float scale : Mk3Settings.FACE_SCALES) {
            for (final int[] wh : new int[][] {{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 1}, {3, 2}, {4, 3}, {5, 3}, {6, 2}}) {
                out.add(DriveArtTest.wall(wh[0], wh[1], scale));
                faceNames.add(String.format(Locale.ROOT, "%dx%d at %d%%", wh[0], wh[1], Math.round(scale * 100.0f)));
            }
            out.add(DriveArtTest.console(scale));
            faceNames.add(String.format(Locale.ROOT, "console at %d%%", Math.round(scale * 100.0f)));
        }
        return out;
    }

    @Test
    void nothingOnTheSurveyPageIsDrawnOutsideIt() {
        final List<String> failures = new ArrayList<>();
        for (final int[] wh : SCREENS) {
            final Rect area = page(wh[0], wh[1]);
            for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
                for (int i = 0; i < models.size(); i++) {
                    final AeroExtentSurface s = new AeroExtentSurface(area);
                    SurveyArt.paint(s, area, models.get(i), tab);
                    final float shapes = s.shapes.over(area);
                    final float text = s.text.over(area);
                    if (shapes > 1.0f || text > 0.0f) {
                        failures.add(String.format(Locale.ROOT, "  %dx%d / %s / %s: shapes %.1f, text %.1f%s", wh[0],
                                wh[1], SurveyArt.TABS[tab], names.get(i), shapes, text,
                                text > 0.0f ? " (\"" + s.escapedText + "\")" : ""));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside the Survey page:\n"
                + String.join("\n", failures.subList(0, Math.min(60, failures.size()))));
    }

    @Test
    void nothingOnASurveyFaceIsDrawnOutsideIt() {
        final List<String> faceNames = new ArrayList<>();
        final List<Rect> faces = faces(faceNames);
        final List<String> failures = new ArrayList<>();
        for (int f = 0; f < faces.size(); f++) {
            final Rect rect = faces.get(f);
            for (final Lod lod : Lod.values()) {
                for (final Mk3Settings.FaceMode mode : MODES) {
                    for (int i = 0; i < models.size(); i++) {
                        final AeroExtentSurface s = new AeroExtentSurface(rect);
                        PanelPainter.paint(s, rect, panel(mode, models.get(i)), lod);
                        final float shapes = s.shapes.over(rect);
                        final float text = s.text.over(rect);
                        if (shapes > 1.0f || text > 0.0f) {
                            failures.add(String.format(Locale.ROOT, "  %s / %s / %s / %s: shapes %.1f, text %.1f%s",
                                    faceNames.get(f), lod, mode, names.get(i), shapes, text,
                                    text > 0.0f ? " (\"" + s.escapedText + "\")" : ""));
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside a Survey face:\n"
                + String.join("\n", failures.subList(0, Math.min(60, failures.size()))));
    }

    @Test
    void everyTabAndFaceStaysWithinTheTriangleBudget() {
        final List<String> failures = new ArrayList<>();
        final StringBuilder report = new StringBuilder("Survey triangle budget (limit " + BUDGET + "):\n");
        final CountingSurface c = new CountingSurface();
        for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
            int worst = 0;
            String where = "";
            for (final int[] wh : SCREENS) {
                final Rect area = page(wh[0], wh[1]);
                for (int i = 0; i < models.size(); i++) {
                    c.reset();
                    SurveyArt.paint(c, area, models.get(i), tab);
                    if (c.triangles > worst) {
                        worst = c.triangles;
                        where = wh[0] + "x" + wh[1] + ", " + names.get(i);
                    }
                    if (c.triangles >= BUDGET) {
                        failures.add(String.format(Locale.ROOT, "  %s tab, %dx%d, %s: %d", SurveyArt.TABS[tab], wh[0],
                                wh[1], names.get(i), c.triangles));
                    }
                }
            }
            report.append(String.format(Locale.ROOT, "  page %-8s worst %5d  (%s)%n", SurveyArt.TABS[tab], worst, where));
        }
        final List<String> faceNames = new ArrayList<>();
        final List<Rect> faces = faces(faceNames);
        for (final Mk3Settings.FaceMode mode : MODES) {
            int worst = 0;
            String where = "";
            for (int f = 0; f < faces.size(); f++) {
                for (int i = 0; i < models.size(); i++) {
                    c.reset();
                    PanelPainter.paint(c, faces.get(f), panel(mode, models.get(i)), Lod.FULL);
                    if (c.triangles > worst) {
                        worst = c.triangles;
                        where = faceNames.get(f) + ", " + names.get(i);
                    }
                    if (c.triangles >= BUDGET) {
                        failures.add(String.format(Locale.ROOT, "  %s, %s, %s: %d", mode, faceNames.get(f), names.get(i),
                                c.triangles));
                    }
                }
            }
            report.append(String.format(Locale.ROOT, "  face %-12s worst %5d  (%s)%n", mode, worst, where));
        }
        System.out.println(report);
        assertTrue(failures.isEmpty(), failures.size() + " over the budget:\n"
                + String.join("\n", failures.subList(0, Math.min(40, failures.size()))));
    }

    @Test
    void noSurveyArtEverPrintsANumberItDoesNotHave() {
        final List<String> failures = new ArrayList<>();
        final Rect area = page(853, 533);
        for (int i = 0; i < models.size(); i++) {
            final DriveArtTest.WordSurface s = new DriveArtTest.WordSurface(area);
            for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
                SurveyArt.paint(s, area, models.get(i), tab);
            }
            for (final Mk3Settings.FaceMode mode : MODES) {
                PanelPainter.paint(s, DriveArtTest.wall(4, 3, 0.5f), panel(mode, models.get(i)), Lod.FULL);
                PanelPainter.paint(s, DriveArtTest.wall(2, 1, 0.5f), panel(mode, models.get(i)), Lod.FULL);
            }
            for (final String w : s.words) {
                if (w.contains("NaN") || w.contains("Infinity") || w.contains("null") || w.indexOf((char) 0x2014) >= 0
                        || w.contains("-2147483648") || w.contains("9223372036854775")) {
                    failures.add("  " + names.get(i) + ": \"" + w + "\"");
                }
            }
        }
        assertTrue(failures.isEmpty(), "an unknown printed as a number, or a banned dash:\n" + String.join("\n", failures));
    }

    @Test
    void noTypeUnderTheFloor() {
        final List<String> failures = new ArrayList<>();
        for (final int[] wh : SCREENS) {
            final Rect area = page(wh[0], wh[1]);
            for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
                for (int i = 0; i < models.size(); i++) {
                    final ScaleSurface s = new ScaleSurface();
                    SurveyArt.paint(s, area, models.get(i), tab);
                    if (s.min < Text.MICRO - 1.0e-4f) {
                        failures.add(String.format(Locale.ROOT, "  page %dx%d / %s / %s: %.3f (\"%s\")", wh[0], wh[1],
                                SurveyArt.TABS[tab], names.get(i), s.min, s.smallest));
                    }
                }
            }
        }
        final List<String> faceNames = new ArrayList<>();
        final List<Rect> faces = faces(faceNames);
        for (int f = 0; f < faces.size(); f++) {
            final float floor = FaceType.of(faces.get(f)).micro;
            for (final Mk3Settings.FaceMode mode : MODES) {
                for (int i = 0; i < models.size(); i++) {
                    final ScaleSurface s = new ScaleSurface();
                    PanelPainter.paint(s, faces.get(f), panel(mode, models.get(i)), Lod.FULL);
                    if (s.min < floor - 1.0e-4f) {
                        failures.add(String.format(Locale.ROOT, "  %s / %s / %s: %.3f under %.3f (\"%s\")",
                                faceNames.get(f), mode, names.get(i), s.min, floor, s.smallest));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " strings under the floor:\n"
                + String.join("\n", failures.subList(0, Math.min(40, failures.size()))));
    }

    @Test
    void everyControlIsHitWhereItIsDrawn() {
        final Rect area = page(853, 533);
        final SurveyArt.Model day = models.get(names.indexOf("an afternoon"));
        final SurveyArt.Model sliced = models.get(names.indexOf("an afternoon, sliced, voids hidden"));
        final Map<Integer, Set<SurveyArt.Kind>> want = Map.of(
                SurveyArt.SCAN, EnumSet.of(SurveyArt.Kind.TAB, SurveyArt.Kind.SHOW_FACE, SurveyArt.Kind.SCAN_FIELD,
                        SurveyArt.Kind.VIEW_3D, SurveyArt.Kind.VIEW_PLAN, SurveyArt.Kind.VIEW_SECTION,
                        SurveyArt.Kind.LEGEND_ROW, SurveyArt.Kind.LEGEND_SWATCH),
                SurveyArt.DEPTH, EnumSet.of(SurveyArt.Kind.LOG_PLOT, SurveyArt.Kind.CLEAR_BAND),
                SurveyArt.FINDS, EnumSet.of(SurveyArt.Kind.CHIP, SurveyArt.Kind.LEDGER_ROW, SurveyArt.Kind.PIN,
                        SurveyArt.Kind.SHOW_IN_SCAN),
                SurveyArt.HISTORY, EnumSet.of(SurveyArt.Kind.HIDE_BEFORE, SurveyArt.Kind.FORGET));
        final List<String> failures = new ArrayList<>();
        for (final Map.Entry<Integer, Set<SurveyArt.Kind>> e : want.entrySet()) {
            final SurveyArt.Hits hits = new SurveyArt.Hits();
            SurveyArt.paint(new CountingSurface(), area, e.getKey() == SurveyArt.DEPTH ? sliced : day, e.getKey(), hits);
            final Set<SurveyArt.Kind> seen = EnumSet.noneOf(SurveyArt.Kind.class);
            for (final SurveyArt.Hit h : hits.all()) {
                seen.add(h.kind());
                final Rect r = h.rect();
                if (r.x < area.x - 3.5f || r.y < area.y - 2.5f || r.right() > area.right() + 3.5f
                        || r.bottom() > area.bottom() + 0.5f || r.w <= 0.0f || r.h <= 0.0f) {
                    failures.add("  " + SurveyArt.TABS[e.getKey()] + ": " + h.kind() + " at " + r);
                }
            }
            for (final SurveyArt.Kind k : e.getValue()) {
                if (!seen.contains(k)) {
                    failures.add("  " + SurveyArt.TABS[e.getKey()] + " offers no " + k);
                }
            }
            if (e.getKey() == SurveyArt.SCAN) {
                int rows = 0;
                for (final SurveyArt.Hit h : hits.all()) {
                    rows += h.kind() == SurveyArt.Kind.LEGEND_ROW ? 1 : 0;
                }
                if (rows < Math.min(8, day.scene.targetsSurveyed().size())) {
                    failures.add("  the legend shows " + rows + " of " + day.scene.targetsSurveyed().size()
                            + " targets on the owner's screen");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void aClearedPanelKeepsItsWayBack() {
        final SurveyArt.Model m = model(new SurveyFixtures.Shot("cleared", SurveyScene.EMPTY, 9_000L), "", true);
        m.since = 6_000L;
        m.known = 3;
        for (final int[] wh : SCREENS) {
            final Rect area = page(wh[0], wh[1]);
            for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
                final SurveyArt.Hits hits = new SurveyArt.Hits();
                SurveyArt.paint(new CountingSurface(), area, m, tab, hits);
                assertTrue(hits.first(SurveyArt.Kind.SHOW_ALL) != null,
                        "no way back on " + SurveyArt.TABS[tab] + " at " + wh[0] + "x" + wh[1]);
            }
        }
        final DriveArtTest.WordSurface w = new DriveArtTest.WordSurface(DriveArtTest.wall(2, 2, 0.5f));
        PanelPainter.paint(w, DriveArtTest.wall(2, 2, 0.5f), panel(Mk3Settings.FaceMode.SURVEY_SCAN, m), Lod.FULL);
        assertTrue(String.join(" ", w.words).contains("hidden"), "the face says why it is bare: " + w.words);
    }

    @Test
    void aPanelWaitingForItsSceneSaysSo() {
        final SurveyArt.Model m = model(new SurveyFixtures.Shot("waiting", SurveyScene.EMPTY, 9_000L), "", false);
        m.known = 2;
        m.waiting = true;
        final DriveArtTest.WordSurface w = new DriveArtTest.WordSurface(page(853, 533));
        SurveyArt.paint(w, page(853, 533), m, SurveyArt.SCAN);
        final String words = String.join(" ", w.words);
        assertTrue(words.contains("Reading the record") && !words.contains("No Seismic Scanner linked")
                && !words.contains("No blow"), words);
    }

    @Test
    void theWallsSmallestFormsNameTheOre() {
        final List<String> failures = new ArrayList<>();
        final SurveyArt.Model m = models.get(names.indexOf("an afternoon"));
        final String name = SurveyArt.upper(SurveyArt.headline(m).target(), SurveyArt.headline(m).kind());
        for (final float scale : Mk3Settings.FACE_SCALES) {
            for (final int[] wh : new int[][] {{1, 1}, {2, 1}, {3, 1}, {4, 1}, {6, 2}, {1, 2}}) {
                final Rect rect = DriveArtTest.wall(wh[0], wh[1], scale);
                final FaceType t = FaceType.of(rect);
                final Rect b = rect.inset(t.pad);
                for (final Lod lod : Lod.values()) {
                    if (!(lod.isGlyph() || b.w < 150.0f || b.h < 104.0f)) {
                        continue;
                    }
                    final ScaleSurface s = new ScaleSurface();
                    final java.util.List<String> big = new ArrayList<>();
                    final PanelSurface rec = new ScaleSurface() {
                        @Override
                        public void text(String str, float x, float y, int color, float sc) {
                            if (str != null && str.contains(name) && sc >= t.row - 1.0e-4f) {
                                big.add(str);
                            }
                        }
                    };
                    PanelPainter.paint(rec, rect, panel(Mk3Settings.FaceMode.SURVEY_FINDS, m), lod);
                    if (big.isEmpty()) {
                        failures.add(String.format(Locale.ROOT, "  %dx%d at %d%% / %s: no %s at row size", wh[0], wh[1],
                                Math.round(scale * 100.0f), lod, name));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " faces name the ore only by colour:\n"
                + String.join("\n", failures.subList(0, Math.min(30, failures.size()))));
    }

    @Test
    void theLedgerReadsInOrder() {
        for (final SurveyArt.Model m : models) {
            final List<SurveyArt.Row> rows = SurveyArt.finds(m);
            for (int i = 1; i < rows.size(); i++) {
                assertTrue(Math.round(rows.get(i - 1).find().horiz()) <= Math.round(rows.get(i).find().horiz()),
                        "row " + i + " out of order");
            }
        }
        final SurveyArt.Model day = models.get(names.indexOf("an afternoon, diamond focused"));
        assertTrue(SurveyArt.finds(day).get(0).find().body() == SurveyArt.headline(day).find().body(),
                "the first row and the headline are the same find");
    }

    @Test
    void aWorkedFindLeavesTheWallAndStaysInTheLedger() {
        final SurveyArt.Model m = model(SurveyFixtures.afternoon(), "c:ores/diamond", true);
        final SurveyFinds.Find first = SurveyArt.headline(m).find();
        m.worked = Map.of(SurveyArt.keyOf(first), m.now - 100L);
        final SurveyFinds.Find next = SurveyArt.headline(m).find();
        assertTrue(next != null && next.body() != first.body(), "the headline moved on to a find still in the ground");
        boolean listed = false;
        for (final SurveyArt.Row r : SurveyArt.finds(m)) {
            listed |= r.find().body() == first.body();
        }
        assertTrue(listed, "the worked find stays in the ledger");
    }

    @Test
    void theHeadlineFollowsThePanelNotTheLastBlow() {
        final SurveyArt.Model m = models.get(names.indexOf("an afternoon"));
        assertTrue(SurveyArt.headlineTarget(m).equals(SurveyArt.legendOrder(m.scene).get(0))
                || m.scene.layer(SurveyArt.legendOrder(m.scene).get(0)) == null, SurveyArt.headlineTarget(m));
    }

    @Test
    void eachTabPutsUpItsOwnFace() {
        final SurveyFacePainter.Mode[] want = {SurveyFacePainter.Mode.SCAN, SurveyFacePainter.Mode.DEPTH,
                SurveyFacePainter.Mode.FINDS, SurveyFacePainter.Mode.ECHO};
        for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
            final Mk3Settings.FaceMode mode = SurveyArt.faceFor(tab);
            assertTrue(mode.isSurvey(), SurveyArt.TABS[tab]);
            assertTrue(SurveyFacePainter.modeOf(mode) == want[tab], SurveyArt.TABS[tab] + " shows " + mode);
        }
    }

    static class ScaleSurface implements PanelSurface {
        float min = Float.MAX_VALUE;
        String smallest = "";

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            if (s != null && !s.isBlank() && (color >>> 24) != 0 && scale < this.min) {
                this.min = scale;
                this.smallest = s;
            }
        }

        @Override
        public void textCenter(String s, float centerX, float y, int color, float scale) {
            text(s, centerX, y, color, scale);
        }

        @Override
        public void textRight(String s, float rightX, float y, int color, float scale) {
            text(s, rightX, y, color, scale);
        }

        @Override
        public float textWidth(String s, float scale) {
            return CountingSurface.advance(s, scale);
        }

        @Override
        public void clip(Rect r) {
        }

        @Override
        public void unclip() {
        }
    }
}
