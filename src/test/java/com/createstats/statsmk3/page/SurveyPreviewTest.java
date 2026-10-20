package com.createstats.statsmk3.page;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createstats.seismic.archive.SurveyFixtures;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.GlyphSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SURVEY_PREVIEW", matches = ".+")
class SurveyPreviewTest {
    static final File OUT = new File("mk3_survey_preview");
    private static final int CAPTION = 0x98A0AD;

    @Test
    void sheets() throws IOException {
        final String which = System.getenv("SURVEY_PREVIEW");
        final int v = nextVersion();
        if (which.contains("scan")) {
            scanSheet(v);
        }
        if (which.contains("concepts")) {
            conceptSheets(v);
        }
        if (which.contains("rec")) {
            recommendedSheets(v);
        }
        if (which.contains("final")) {
            finalSheets(v);
        }
    }

    static SurveyArt.Model cleared(SurveyFixtures.Shot day) {
        final SurveyArt.Model m = model(new SurveyFixtures.Shot("cleared", SurveyScene.EMPTY, day.now()), "");
        m.since = day.now() - 3_600L;
        m.known = 6;
        return m;
    }

    static SurveyArt.Model reading() {
        final SurveyArt.Model m = model(new SurveyFixtures.Shot("reading", SurveyScene.EMPTY, 1_000L), "");
        m.known = 3;
        m.waiting = true;
        return m;
    }

    static SurveyArt.Model withRigs(SurveyArt.Model m) {
        final java.util.Map<Long, com.createstats.blockentity.SeismicLinks.RigState> states = new java.util.HashMap<>();
        final com.createstats.seismic.SeismicRig.State[] cycle = {com.createstats.seismic.SeismicRig.State.CHARGING,
                com.createstats.seismic.SeismicRig.State.READY, com.createstats.seismic.SeismicRig.State.WAITING};
        final int[] charge = {65, 100, 20};
        int i = 0;
        for (final com.createstats.seismic.archive.SurveyScene.Rig r : m.scene.rigs) {
            if (r.linked) {
                states.put(net.minecraft.core.BlockPos.asLong(r.x, r.y, r.z),
                        new com.createstats.blockentity.SeismicLinks.RigState(cycle[i % 3].ordinal(), charge[i % 3], 64,
                                r.target, true));
                i++;
            }
        }
        m.rigStates = states;
        return m;
    }

    private void finalSheets(int v) throws IOException {
        final SurveyFixtures.Shot day = SurveyFixtures.afternoon();
        final Rect page = ownerPage();
        for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
            final SurveyArt.Model m = withRigs(model(day, tab == SurveyArt.SCAN ? "" : "c:ores/diamond"));
            if (tab == SurveyArt.DEPTH) {
                m.sliceLo = -64;
                m.sliceHi = -40;
            }
            if (tab == SurveyArt.FINDS) {
                m.focus = "";
                m.selected = 1;
            }
            final int t = tab;
            final Painter p = (s, r) -> SurveyArt.paint(s, r, m, t);
            write(String.format(Locale.ROOT, "final_tab_%s_v%02d.png", SurveyArt.TABS[tab].toLowerCase(Locale.ROOT), v),
                    SurveyArt.TABS[tab] + " tab at 1:1 on the owner's screen (2560x1600, GUI scale 3)",
                    List.of(draw(page, Tokens.CANVAS, p, 3.0f)), List.of(String.format(Locale.ROOT,
                            "%s, page %dx%d GUI units drawn at 3 px a unit, %d tris", day.name(), (int) page.w,
                            (int) page.h, tris(page, p))), 1);
        }
        final SurveyFixtures.Shot noRig = new SurveyFixtures.Shot("nothing linked",
                com.createstats.seismic.archive.SurveyScene.EMPTY, 0L);
        final SurveyArt.Model empty = model(noRig, "");
        empty.linked = false;
        final SurveyArt.Model filtered = withRigs(model(day, "c:ores/diamond"));
        filtered.hidden = java.util.Set.of("create_stats:voids", "c:ores/coal");
        filtered.sliceLo = -64;
        filtered.sliceHi = -40;
        final SurveyArt.Model pinnedPlan = withRigs(model(day, ""));
        final com.createstats.seismic.archive.SurveyScene.Body gold = day.scene().layer("c:ores/gold").bodies.get(0);
        pinnedPlan.pin = net.minecraft.core.BlockPos.asLong(gold.nearX, gold.nearY, gold.nearZ);
        pinnedPlan.pitch = 90.0;
        pinnedPlan.yaw = 0.0;
        final SurveyArt.Model section = withRigs(model(day, "c:ores/lapis"));
        section.pitch = 0.0;
        section.yaw = 90.0;
        final SurveyArt.Model zoomed = withRigs(model(day, ""));
        final com.createstats.seismic.archive.SurveyScene.Body deep = day.scene().layer("c:ores/diamond").bodies.get(0);
        zoomed.ring = deep;
        zoomed.centre = new double[] {deep.cx, deep.cy, deep.cz};
        zoomed.zoom = 3.5;
        zoomed.yaw = 300.0;
        zoomed.pitch = 22.0;
        final Object[][] states = {
                {empty, "no_rig", "No rig linked"},
                {withRigs(model(SurveyFixtures.linkedNoSurvey(), "")), "no_blow", "A rig linked, no blow yet"},
                {withRigs(model(SurveyFixtures.oneSurvey(), "")), "one_survey", "One survey: iron at radius 64"},
                {withRigs(model(day, "")), "accumulated", "Many surveys accumulated: an afternoon on the site"},
                {withRigs(model(SurveyFixtures.caves(), "")), "caves", "Cave surveys"},
                {filtered, "filtered", "Filters and a slice: diamond focused, voids and coal hidden, Y -64 to -40"},
                {withRigs(model(SurveyFixtures.live(), "c:ores/diamond")), "live", "A blow landing, its finds arriving"},
                {pinnedPlan, "pinned_plan", "Plan view, a gold find pinned to the headline"},
                {section, "section", "Section facing east, lapis focused"},
                {zoomed, "zoomed", "A find clicked: the camera on it, ringed, its card in the rail"},
                {cleared(day), "cleared", "Every blow hidden on this panel: the way back on every tab"},
                {reading(), "reading", "A panel that knows its rigs, its scene on the way"}};
        for (final Object[] st : states) {
            final SurveyArt.Model m = (SurveyArt.Model) st[0];
            final Painter p = (s, r) -> SurveyArt.paint(s, r, m, SurveyArt.SCAN);
            write(String.format(Locale.ROOT, "final_state_%s_v%02d.png", st[1], v),
                    st[2] + ", at 1:1 on the owner's screen", List.of(draw(page, Tokens.CANVAS, p, 3.0f)),
                    List.of(String.format(Locale.ROOT, "%d tris", tris(page, p))), 1);
        }

        final SurveyArt.Model worked = withRigs(model(day, ""));
        final SurveyFinds.Find head = SurveyArt.headline(worked).find();
        worked.worked = java.util.Map.of(SurveyArt.keyOf(head), day.now() - 1_200L);
        worked.selectedKey = SurveyArt.keyOf(head);
        final SurveyArt.Model fresh = withRigs(model(day, ""));
        fresh.newOnly = true;
        for (final Object[] st : new Object[][] {{worked, "worked", "A find marked worked: struck through, off the wall"},
                {fresh, "new_only", "The NEW chip: the new finds, newest first"}}) {
            final SurveyArt.Model m = (SurveyArt.Model) st[0];
            final Painter p = (s, r) -> SurveyArt.paint(s, r, m, SurveyArt.FINDS);
            write(String.format(Locale.ROOT, "final_finds_%s_v%02d.png", st[1], v), st[2] + ", at 1:1 on the owner's screen",
                    List.of(draw(page, Tokens.CANVAS, p, 3.0f)), List.of(String.format(Locale.ROOT, "%d tris",
                            tris(page, p))), 1);
        }

        final Rect small = SurveyArtTest.page(640, 360);
        final List<BufferedImage> smallTabs = new ArrayList<>();
        final List<String> smallCaps = new ArrayList<>();
        for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
            final SurveyArt.Model m = withRigs(model(day, "c:ores/diamond"));
            final int t = tab;
            smallTabs.add(draw(small, Tokens.CANVAS, (s, r) -> SurveyArt.paint(s, r, m, t), 3.0f));
            smallCaps.add(String.format(Locale.ROOT, "%s, %dx%d GUI units at 3 px a unit", SurveyArt.TABS[tab],
                    (int) small.w, (int) small.h));
        }
        write(String.format(Locale.ROOT, "final_studio_1080p_v%02d.png", v),
                "Every tab on a 1920x1080 screen at GUI scale 3, at 1:1", smallTabs, smallCaps, 2);

        final Rect big = new Rect(0, 0, 1280, 800);
        for (final com.createstats.statsmk3.paint.SurveyFacePainter.Mode mode
                : com.createstats.statsmk3.paint.SurveyFacePainter.Mode.values()) {
            final SurveyArt.Model m = withRigs(model(mode == com.createstats.statsmk3.paint.SurveyFacePainter.Mode.ECHO
                    ? SurveyFixtures.live() : day, "c:ores/diamond"));
            if (mode == com.createstats.statsmk3.paint.SurveyFacePainter.Mode.DEPTH) {
                m.sliceLo = -64;
                m.sliceHi = -40;
            }
            final Painter p = (s, r) -> com.createstats.statsmk3.paint.SurveyFacePainter.paint(s, r, m, mode,
                    com.createstats.statsmk3.paint.Lod.FULL, 0xFF05070A);
            write(String.format(Locale.ROOT, "final_face_%s_1280_v%02d.png", mode.name().toLowerCase(Locale.ROOT), v),
                    "The " + mode.name().toLowerCase(Locale.ROOT) + " face at 1280x800, one pixel a unit",
                    List.of(draw(big, 0xFF05070A, p, 1.0f)), List.of(tris(big, p) + " tris"), 1);
        }

        final List<BufferedImage> walls = new ArrayList<>();
        final List<String> wallCaps = new ArrayList<>();
        for (final com.createstats.statsmk3.paint.SurveyFacePainter.Mode mode
                : com.createstats.statsmk3.paint.SurveyFacePainter.Mode.values()) {
            for (final int[] wh : new int[][] {{4, 3}, {2, 2}, {3, 1}, {1, 1}}) {
                final Rect w = wall(wh[0], wh[1]);
                final SurveyArt.Model m = withRigs(model(mode == com.createstats.statsmk3.paint.SurveyFacePainter.Mode.ECHO
                        ? SurveyFixtures.live() : day, "c:ores/diamond"));
                final Painter p = (s, r) -> com.createstats.statsmk3.paint.SurveyFacePainter.paint(s, r, m, mode,
                        com.createstats.statsmk3.paint.Lod.FULL, 0xFF05070A);
                walls.add(draw(w, 0xFF05070A, p, 1.0f));
                wallCaps.add(String.format(Locale.ROOT, "%s %dx%d, %d tris", mode.name().toLowerCase(Locale.ROOT), wh[0],
                        wh[1], tris(w, p)));
            }
        }
        write(String.format(Locale.ROOT, "final_faces_walls_v%02d.png", v), "Every face at the walls players build",
                walls, wallCaps, 4);
    }

    static SurveyArt.Model model(SurveyFixtures.Shot shot, String focus) {
        final SurveyArt.Model m = new SurveyArt.Model();
        m.scene = shot.scene().heardAt(shot.now());
        m.now = shot.now();
        m.refX = SurveyFixtures.PANEL_X + 0.5;
        m.refY = SurveyFixtures.PANEL_Y + 0.5;
        m.refZ = SurveyFixtures.PANEL_Z + 0.5;
        m.linked = true;
        m.focus = focus;

        m.heading = 270.0;
        return m;
    }

    private void recommendedSheets(int v) throws IOException {
        final SurveyFixtures.Shot day = SurveyFixtures.afternoon();
        final Rect page = ownerPage();

        final List<BufferedImage> tabs = new ArrayList<>();
        final List<String> tabCaps = new ArrayList<>();
        for (int tab = 0; tab < SurveyArt.TABS.length; tab++) {
            final SurveyArt.Model m = model(day, "c:ores/diamond");
            if (tab == SurveyArt.DEPTH) {
                m.sliceLo = -64;
                m.sliceHi = -40;
            }
            if (tab == SurveyArt.FINDS) {
                m.focus = "";
                m.selected = 2;
            }
            final int t = tab;
            tabs.add(draw(page, Tokens.CANVAS, (s, r) -> SurveyArt.paint(s, r, m, t), 3.0f));
            tabCaps.add(String.format(Locale.ROOT, "%s tab: the owner's screen, %dx%d GUI units at GUI scale 3, %d tris",
                    SurveyArt.TABS[tab], (int) page.w, (int) page.h, tris(page, (s, r) -> SurveyArt.paint(s, r, m, t))));
        }
        write(String.format(Locale.ROOT, "rec_tabs_v%02d.png", v), "Recommended: every tab, an afternoon on the site",
                tabs, tabCaps, 1);

        final List<BufferedImage> faces = new ArrayList<>();
        final List<String> faceCaps = new ArrayList<>();
        final int[][] walls = {{4, 3}, {2, 2}, {3, 1}, {1, 1}};
        for (final com.createstats.statsmk3.paint.SurveyFacePainter.Mode mode
                : com.createstats.statsmk3.paint.SurveyFacePainter.Mode.values()) {
            for (final int[] wh : walls) {
                final Rect w = wall(wh[0], wh[1]);
                final SurveyArt.Model m = model(mode == com.createstats.statsmk3.paint.SurveyFacePainter.Mode.ECHO
                        ? SurveyFixtures.live() : day, "c:ores/diamond");
                if (mode == com.createstats.statsmk3.paint.SurveyFacePainter.Mode.DEPTH) {
                    m.sliceLo = -64;
                    m.sliceHi = -40;
                }
                final Painter p = (s, r) -> com.createstats.statsmk3.paint.SurveyFacePainter.paint(s, r, m, mode,
                        com.createstats.statsmk3.paint.Lod.FULL, 0xFF05070A);
                faces.add(draw(w, 0xFF05070A, p, 2.0f));
                faceCaps.add(String.format(Locale.ROOT, "%s %dx%d, %d tris", mode.name().toLowerCase(Locale.ROOT), wh[0],
                        wh[1], tris(w, p)));
            }
        }
        write(String.format(Locale.ROOT, "rec_faces_v%02d.png", v), "Recommended: every face, at the walls players build",
                faces, faceCaps, 4);

        final List<BufferedImage> states = new ArrayList<>();
        final List<String> stateCaps = new ArrayList<>();
        final SurveyArt.Model none = model(SurveyFixtures.linkedNoSurvey(), "");
        none.linked = false;
        final SurveyFixtures.Shot noRig = new SurveyFixtures.Shot("nothing linked", com.createstats.seismic.archive.SurveyScene.EMPTY, 0L);
        final SurveyArt.Model empty = model(noRig, "");
        empty.linked = false;
        final Object[][] shots = {{empty, "no rig linked"}, {model(SurveyFixtures.linkedNoSurvey(), ""), "a rig linked, no blow yet"},
                {model(SurveyFixtures.ownerToday(), ""), "the owner's world today"},
                {model(SurveyFixtures.caves(), ""), "cave surveys"}, {model(SurveyFixtures.live(), "c:ores/diamond"),
                "a blow landing, its finds arriving"}};
        for (final Object[] st : shots) {
            final SurveyArt.Model m = (SurveyArt.Model) st[0];
            states.add(draw(page, Tokens.CANVAS, (s, r) -> SurveyArt.paint(s, r, m, SurveyArt.SCAN), 2.0f));
            stateCaps.add((String) st[1]);
        }
        write(String.format(Locale.ROOT, "rec_states_v%02d.png", v), "Recommended: the Scan tab in every state", states,
                stateCaps, 2);
    }

    static Rect ownerPage() {
        final Rect v = com.createstats.statsmk3.view.StudioLayout.of(853, 533).viewArea(false);
        return new Rect(0, 0, Math.round(v.w), Math.round(v.h));
    }

    static Rect wall(int w, int h) {
        final float ppb = 128.0f;
        final float texel = ppb / 16.0f;
        return new Rect(0, 0, w * ppb - 4.0f * texel, h * ppb - 5.0f * texel);
    }

    static SurveyConcepts.Ctx ctx(SurveyFixtures.Shot shot, String focus) {
        return new SurveyConcepts.Ctx(shot.scene(), shot.now(), SurveyFixtures.PANEL_X + 0.5,
                SurveyFixtures.PANEL_Y + 0.5, SurveyFixtures.PANEL_Z + 0.5, focus);
    }

    private interface Painter {
        void paint(com.createstats.statsmk3.ui.PanelSurface s, Rect r);
    }

    static BufferedImage draw(Rect r, int ground, Painter p) {
        return draw(r, ground, p, 1.0f);
    }

    static BufferedImage draw(Rect r, int ground, Painter p, float k) {
        final GlyphSurface g = new GlyphSurface(Math.round(r.w * k), Math.round(r.h * k), ground & 0xFFFFFF);
        final com.createstats.statsmk3.ui.PanelSurface s = k == 1.0f ? g : new ZoomSurface(g, k);
        Shapes.rect(s, r, ground);
        p.paint(s, r);
        return g.finish();
    }

    static int tris(Rect r, Painter p) {
        final CountingSurface c = new CountingSurface();
        p.paint(c, r);
        return c.triangles;
    }

    private void conceptSheets(int v) throws IOException {
        final SurveyFixtures.Shot day = SurveyFixtures.afternoon();
        final SurveyConcepts.Ctx c = ctx(day, "c:ores/diamond");
        final Rect page = ownerPage();
        final Rect big = wall(4, 3);
        final Rect one = wall(1, 1);
        final Painter[] pages = {
                (s, r) -> SurveyConcepts.echoPage(s, r, c, 5),
                (s, r) -> SurveyConcepts.strataPage(s, r, c, -64, -40),
                (s, r) -> SurveyConcepts.specimenPage(s, r, c)};
        final Painter[] faces = {
                (s, r) -> SurveyConcepts.echoFace(s, r, c),
                (s, r) -> SurveyConcepts.strataFace(s, r, c, -64, -40),
                (s, r) -> SurveyConcepts.specimenFace(s, r, c)};
        final String[] names = {"A  Echo sounder", "B  Strata log", "C  Specimen block"};
        final String[] files = {"concept_A_echo", "concept_B_strata", "concept_C_specimen"};
        final List<BufferedImage> overview = new ArrayList<>();
        final List<String> overviewCaps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final List<BufferedImage> images = new ArrayList<>();
            final List<String> captions = new ArrayList<>();
            final BufferedImage pg = draw(page, Tokens.CANVAS, pages[i]);
            images.add(pg);
            captions.add(String.format(Locale.ROOT, "page at the owner's 853x533 studio (%dx%d), %d tris", (int) page.w,
                    (int) page.h, tris(page, pages[i])));
            images.add(draw(big, 0xFF05070A, faces[i]));
            captions.add(String.format(Locale.ROOT, "face, 4x3 wall, %d tris", tris(big, faces[i])));
            images.add(draw(one, 0xFF05070A, faces[i]));
            captions.add("face, one block");
            write(String.format(Locale.ROOT, "%s_v%02d.png", files[i], v), names[i] + ": an afternoon on the owner's site",
                    images, captions, 3);
            overview.add(lettered(pg, names[i].substring(0, 1), names[i].substring(3)));
            overviewCaps.add(names[i]);
        }

        final SurveyArt.Model rec = model(day, "c:ores/diamond");
        final BufferedImage d = draw(page, Tokens.CANVAS, (s, r) -> SurveyArt.paint(s, r, rec, SurveyArt.SCAN));
        overview.add(lettered(d, "D", "C + B + A, pick"));
        overviewCaps.add("D  Recommended: C, with B's depth log and A's echo log as tabs");
        write(String.format(Locale.ROOT, "concepts_overview_v%02d.png", v),
                "Survey page concepts, side by side (pick by letter; D is the recommendation)", overview, overviewCaps, 2);
    }

    static BufferedImage lettered(BufferedImage panel, String letter, String name) {
        final int w = panel.getWidth();
        final int h = panel.getHeight();
        final GlyphSurface s = new GlyphSurface(w, h, 0x000000);
        final BufferedImage out = s.finish();
        final Graphics2D g = out.createGraphics();
        g.drawImage(panel, 0, 0, null);
        g.dispose();
        final GlyphSurface plate = new GlyphSurface(150, 70, 0x0A0B0E);
        Shapes.rect(plate, new Rect(0, 0, 150, 70), 0xFF0A0B0E);
        plate.text(letter, 8, 6, Tokens.ACCENT, 6.0f);
        plate.text(name, 52, 18, Tokens.INK, 1.0f);
        plate.text("concept", 52, 32, Tokens.INK_3, 1.0f);
        final BufferedImage tag = plate.finish();
        final Graphics2D g2 = out.createGraphics();
        g2.drawImage(tag, 6, h - 76, null);
        g2.setColor(new Color(0xFF5C90));
        g2.drawRect(6, h - 76, 149, 69);
        g2.dispose();
        return out;
    }

    private void scanSheet(int v) throws IOException {
        final List<BufferedImage> images = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        final SurveyFixtures.Shot day = SurveyFixtures.afternoon();
        final Object[][] views = {{32.0, 30.0, "", 0f, "all alike"}, {32.0, 30.0, "c:ores/diamond", 0f, "focus diamond"},
                {32.0, 30.0, "c:ores/gold", 150f, "focus gold, callout rail"}, {0.0, 90.0, "", 0f, "plan"},
                {0.0, 0.0, "", 0f, "section, facing north"}, {215.0, 24.0, "c:ores/redstone", 150f, "from the south west"}};
        for (final Object[] view : views) {
            final SurveyKit.Scan o = options(day);
            o.yaw = (Double) view[0];
            o.pitch = (Double) view[1];
            o.focus = (String) view[2];
            o.rail = (Float) view[3];
            images.add(scan(day, o, 560, 360));
            captions.add(String.format(Locale.ROOT, "%s: %s, %d tris", day.name(), view[4], cost(day, o, 560, 360)));
        }
        for (final SurveyFixtures.Shot shot : new SurveyFixtures.Shot[] {SurveyFixtures.ownerToday(),
                SurveyFixtures.caves(), SurveyFixtures.live()}) {
            final SurveyKit.Scan o = options(shot);
            o.rail = 150f;
            images.add(scan(shot, o, 560, 360));
            captions.add(shot.name() + ", " + cost(shot, o, 560, 360) + " tris");
        }
        write(String.format(Locale.ROOT, "scan_v%02d.png", v), "The 3D scan on the owner's ground", images, captions, 3);
    }

    static SurveyKit.Scan options(SurveyFixtures.Shot shot) {
        final SurveyKit.Scan o = new SurveyKit.Scan();
        o.now = shot.now();
        o.refX = SurveyFixtures.PANEL_X + 0.5;
        o.refY = SurveyFixtures.PANEL_Y + 0.5;
        o.refZ = SurveyFixtures.PANEL_Z + 0.5;
        return o;
    }

    static BufferedImage scan(SurveyFixtures.Shot shot, SurveyKit.Scan o, int w, int h) {
        final GlyphSurface s = new GlyphSurface(w, h, Tokens.SURFACE & 0xFFFFFF);
        final Rect r = new Rect(0, 0, w, h);
        Shapes.rect(s, r, Tokens.SURFACE);
        SurveyKit.scan(s, r.inset(6.0f), shot.scene(), o);
        return s.finish();
    }

    static int cost(SurveyFixtures.Shot shot, SurveyKit.Scan o, int w, int h) {
        final CountingSurface c = new CountingSurface();
        SurveyKit.scan(c, new Rect(6, 6, w - 12, h - 12), shot.scene(), o);
        return c.triangles;
    }

    static int nextVersion() {
        int max = 0;
        final File[] files = OUT.listFiles();
        if (files != null) {
            final Pattern p = Pattern.compile("_v(\\d+)\\.png$");
            for (final File f : files) {
                final Matcher m = p.matcher(f.getName());
                if (m.find()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        return max + 1;
    }

    static void write(String name, String title, List<BufferedImage> images, List<String> captions, int cols)
            throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final File file = new File(OUT, name);
        if (file.exists()) {
            throw new IOException(file + " exists: iterations are never overwritten");
        }
        final int pad = 16;
        final int cap = 18;
        final int head = 30;
        final int[] colW = new int[cols];
        final int rows = (images.size() + cols - 1) / cols;
        final int[] rowH = new int[rows];
        for (int i = 0; i < images.size(); i++) {
            colW[i % cols] = Math.max(colW[i % cols], images.get(i).getWidth());
            rowH[i / cols] = Math.max(rowH[i / cols], images.get(i).getHeight());
        }
        int w = pad;
        for (final int c : colW) {
            w += c + pad;
        }
        int h = head + pad;
        for (final int r : rowH) {
            h += r + cap + pad;
        }
        final BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0E0F13));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(new Color(0xECEEF2));
        g.drawString(title, pad, 22);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int y = head + pad;
        for (int row = 0; row < rows; row++) {
            int x = pad;
            for (int col = 0; col < cols; col++) {
                final int i = row * cols + col;
                if (i < images.size()) {
                    g.setColor(new Color(CAPTION));
                    g.drawString(captions.get(i), x, y + 12);
                    g.drawImage(images.get(i), x, y + cap, null);
                }
                x += colW[col] + pad;
            }
            y += rowH[row] + cap + pad;
        }
        g.dispose();
        PreviewImages.write(sheet, "png", file);
        System.out.println("PREVIEW wrote " + file);
    }
}
