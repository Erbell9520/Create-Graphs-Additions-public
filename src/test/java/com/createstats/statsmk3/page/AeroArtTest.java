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

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFixtures;
import com.createstats.aerial.AerialFixtures.Scene;
import com.createstats.aerial.AerialRealistic;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AeroArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int BG = 0x111318;

    private static AerialFrame frame(AerialSnapshot s) {
        return AerialFrame.of(s);
    }

    private static AerialFrame stale(AerialSnapshot s) {
        final AirframeReadout r = AirframeMath.read(s);
        return new AerialFrame(r, AerialAlerts.evaluate(r, List.of(), 6.0), 6.0);
    }

    static void view(PanelSurface s, Rect area, AerialFrame f, int tab) {
        AeroView.paintPage(s, area, f, tab, AeroSkin.DECK);
    }

    static void view(PanelSurface s, Rect area, AerialFrame f, int tab, AeroSkin k) {
        AeroView.paintPage(s, area, f, tab, k);
    }

    private static BufferedImage page(AerialFrame f, int tab, int w, int h) {
        final RasterSurface s = new RasterSurface(w, h, BG);
        view(s, new Rect(12.0f, 10.0f, w - 24.0f, h - 20.0f), f, tab);
        return s.finish();
    }

    @Test
    void rendersEveryTab() throws IOException {
        final AerialFrame cruise = frame(AerialFixtures.cruise());
        final List<BufferedImage> images = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        for (int tab = 0; tab < AeroBridge.TABS.length; tab++) {
            images.add(page(tab == 5 ? frame(AerialFixtures.docking()) : cruise, tab, 900, 560));
            captions.add(AeroBridge.TABS[tab] + (tab == 5 ? " / docking" : " / cruise"));
        }
        write("mk3_aero_pages.png", images, captions, 2);
    }

    @Test
    void rendersTheSinkingShip() throws IOException {
        final AerialFrame sinking = frame(AerialFixtures.sinking());
        write("mk3_aero_pages_sinking.png",
                List.of(page(sinking, AeroView.BRIDGE, 900, 560), page(sinking, AeroView.LIFT, 900, 560),
                        page(sinking, AeroView.ENGINES, 900, 560), page(stale(AerialFixtures.cruise()), AeroView.BRIDGE, 900, 560)),
                List.of("Bridge / sinking", "Lift / sinking", "Engines / sinking", "Bridge / feed lost"), 2);
    }

    @Test
    void rendersCrampedTabs() throws IOException {
        final AerialFrame cruise = frame(AerialFixtures.cruise());
        final List<BufferedImage> images = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        for (int tab = 0; tab < AeroBridge.TABS.length; tab++) {
            images.add(page(cruise, tab, 600, 430));
            captions.add(AeroBridge.TABS[tab] + " / 600 x 430");
        }
        write("mk3_aero_pages_cramped.png", images, captions, 3);
    }

    static Rect wall(int w, int h, float scale) {
        final float ppb = 64.0f / scale;
        final float texel = ppb / 16.0f;
        return new Rect(2.0f * texel, 2.0f * texel, w * ppb - 4.0f * texel, h * ppb - 5.0f * texel);
    }

    static Rect console(float scale) {
        final float ppb = 64.0f / scale;
        return new Rect(2.0f / 16.0f * 14.0f / 16.0f * ppb, 1.0f / 16.0f * 11.0f / 16.0f * ppb,
                12.0f / 16.0f * 14.0f / 16.0f * ppb, 11.0f / 16.0f * 11.0f / 16.0f * ppb);
    }

    static PanelModel model(Mk3Settings.FaceMode mode, AerialFrame f, boolean linked) {
        final PanelModel m = new PanelModel();
        m.faceMode = mode;
        m.aero = f;
        m.aeroLinked = linked;
        return m;
    }

    private static BufferedImage face(Rect rect, PanelModel m, Lod lod) {
        final int w = Math.round(rect.w);
        final int h = Math.round(rect.h);
        final RasterSurface s = new RasterSurface(w, h, 0x000000);
        PanelPainter.paint(s, new Rect(0.0f, 0.0f, rect.w, rect.h), m, lod);
        return s.finish();
    }

    @Test
    void rendersTheFaces() throws IOException {
        final AerialFrame cruise = frame(AerialFixtures.cruise());
        final AerialFrame sinking = frame(AerialFixtures.sinking());
        final float def = Mk3Settings.DEFAULT_FACE_SCALE;
        final List<BufferedImage> images = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        final int[][] walls = {{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {4, 3}, {5, 3}};
        for (final int[] wh : walls) {
            images.add(face(wall(wh[0], wh[1], def), model(Mk3Settings.FaceMode.AERO_DECK, cruise, true), Lod.FULL));
            captions.add(String.format(Locale.ROOT, "deck %dx%d", wh[0], wh[1]));
        }
        images.add(face(wall(4, 3, def), model(Mk3Settings.FaceMode.AERO_DECK, sinking, true), Lod.FULL));
        captions.add("deck 4x3 sinking");
        images.add(face(wall(1, 1, def), model(Mk3Settings.FaceMode.AERO_DECK, sinking, true), Lod.FULL));
        captions.add("deck 1x1 sinking");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_MAP, cruise, true), Lod.FULL));
        captions.add("map 2x2");
        images.add(face(wall(4, 3, def), model(Mk3Settings.FaceMode.AERO_MAP, cruise, true), Lod.FULL));
        captions.add("map 4x3");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_BUDGET, cruise, true), Lod.FULL));
        captions.add("budget 2x2");
        images.add(face(wall(4, 3, def), model(Mk3Settings.FaceMode.AERO_BUDGET, sinking, true), Lod.FULL));
        captions.add("budget 4x3 sinking");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_TELEGRAPH, cruise, true), Lod.FULL));
        captions.add("telegraph 2x2");
        images.add(face(wall(4, 3, def), model(Mk3Settings.FaceMode.AERO_VOYAGE, frame(AerialFixtures.full(Scene.CRUISE)),
                true), Lod.FULL));
        captions.add("voyage 4x3");
        images.add(face(wall(4, 3, def), model(Mk3Settings.FaceMode.AERO_SCOPE, cruise, true), Lod.FULL));
        captions.add("scope 4x3");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_DECK, stale(AerialFixtures.cruise()), true),
                Lod.FULL));
        captions.add("deck 2x2 feed lost");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_DECK, null, true), Lod.FULL));
        captions.add("deck 2x2 waiting");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_DECK, null, false), Lod.FULL));
        captions.add("deck 2x2 unlinked");
        images.add(face(wall(2, 2, def), model(Mk3Settings.FaceMode.AERO_DECK, sinking, true), Lod.GLYPH));
        captions.add("deck 2x2 from afar");
        images.add(face(wall(3, 2, 1.0f), model(Mk3Settings.FaceMode.AERO_DECK, cruise, true), Lod.FULL));
        captions.add("deck 3x2 at 100%");
        images.add(face(console(def), model(Mk3Settings.FaceMode.AERO_DECK, cruise, true), Lod.FULL));
        captions.add("console");
        write("mk3_aero_faces.png", images, captions, 4);
    }

    private static List<Rect> faces(List<String> names) {
        final List<Rect> out = new ArrayList<>();
        for (final float scale : Mk3Settings.FACE_SCALES) {
            for (final int[] wh : new int[][]{{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {4, 3}, {5, 3}}) {
                out.add(wall(wh[0], wh[1], scale));
                names.add(String.format(Locale.ROOT, "%dx%d at %d%%", wh[0], wh[1], Math.round(scale * 100.0f)));
            }
            out.add(console(scale));
            names.add(String.format(Locale.ROOT, "console at %d%%", Math.round(scale * 100.0f)));
        }
        return out;
    }

    @Test
    void nothingOnAnAeroFaceIsDrawnOutsideIt() {
        final List<String> names = new ArrayList<>();
        final List<Rect> faces = faces(names);
        final AerialFrame[] frames = {frame(AerialFixtures.cruise()), frame(AerialFixtures.sinking()),
                frame(AerialFixtures.docking()), stale(AerialFixtures.sinking()), null,
                frame(AerialFixtures.full(Scene.CRUISE)), frame(AerialFixtures.full(Scene.MOORED)),
                frame(AerialRealistic.scene(Scene.CRUISE)), frame(AerialRealistic.scene(Scene.SINKING)),
                frame(AerialRealistic.scene(Scene.MOORED)), stale(AerialRealistic.scene(Scene.CRUISE)),
                frame(AerialFixtures.full(Scene.SINKING)), frame(AerialFixtures.anchored(AerialRealistic.scene(Scene.CRUISE))),
                frame(AerialFixtures.weighing(AerialRealistic.scene(Scene.MOORED)))};
        final String[] frameNames = {"cruise", "sinking", "docking", "stale", "no feed", "full cruise", "full moored",
                "owner cruise", "owner sinking", "owner moored", "owner stale", "full sinking (a warning)",
                "owner anchored", "owner weighing"};
        final Lod[] lods = Lod.values();
        final String[] lodNames = {"full", "reduced", "glyph"};
        final Mk3Settings.FaceMode[] modes = {Mk3Settings.FaceMode.AERO_DECK, Mk3Settings.FaceMode.AERO_MAP,
                Mk3Settings.FaceMode.AERO_BUDGET, Mk3Settings.FaceMode.AERO_TELEGRAPH, Mk3Settings.FaceMode.AERO_VOYAGE,
                Mk3Settings.FaceMode.AERO_SCOPE};
        final List<String> failures = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            final Rect rect = faces.get(i);
            for (int l = 0; l < lods.length; l++) {
                for (final Mk3Settings.FaceMode mode : modes) {
                    for (int fi = 0; fi < frames.length; fi++) {
                        final AeroExtentSurface s = new AeroExtentSurface(rect);
                        PanelPainter.paint(s, rect, model(mode, frames[fi], true), lods[l]);
                        final float shapes = s.shapes.over(rect);
                        final float text = s.text.over(rect);
                        if (shapes > 1.0f || text > 0.0f) {
                            failures.add(String.format(Locale.ROOT, "  %s / %s / %s / %s: shapes %.1f, text %.1f past%s",
                                    names.get(i), lodNames[l], mode, frameNames[fi], shapes, text,
                                    text > 0.0f ? " (\"" + s.escapedText + "\")" : ""));
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside an Aero face:\n"
                + String.join("\n", failures.subList(0, Math.min(60, failures.size()))));
    }

    @Test
    void nothingOnTheFlightPageIsDrawnOutsideIt() {
        final AerialFrame[] frames = {frame(AerialFixtures.cruise()), frame(AerialFixtures.sinking()),
                frame(AerialFixtures.docking()), stale(AerialFixtures.cruise()), frame(AerialFixtures.full(Scene.CRUISE)),
                frame(AerialFixtures.full(Scene.MOORED)), frame(AerialRealistic.scene(Scene.CRUISE)),
                frame(AerialRealistic.scene(Scene.SINKING)), frame(AerialRealistic.scene(Scene.MOORED)),
                frame(AerialFixtures.full(Scene.SINKING)), frame(AerialFixtures.anchored(AerialRealistic.scene(Scene.CRUISE)))};

        final int[][] sizes = {{497, 247}, {585, 349}, {683, 401}, {1024, 555}, {1040, 680}, {900, 560}, {700, 480},
                {560, 400}};
        final List<String> failures = new ArrayList<>();
        for (final int[] wh : sizes) {
            final Rect area = new Rect(0.0f, 0.0f, wh[0], wh[1]);

            for (int tab = 0; tab < AeroBridge.TABS.length; tab++) {
                for (int fi = 0; fi < frames.length; fi++) {
                    final AeroExtentSurface s = new AeroExtentSurface(area);
                    view(s, area, frames[fi], tab);
                    final float shapes = s.shapes.over(area);
                    final float text = s.text.over(area);
                    if (shapes > 1.0f || text > 0.0f) {
                        failures.add(String.format(Locale.ROOT, "  %dx%d / %s / frame %d: shapes %.1f, text %.1f%s",
                                wh[0], wh[1], AeroBridge.TABS[tab], fi, shapes, text,
                                text > 0.0f ? " (\"" + s.escapedText + "\")" : ""));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside the Flight page:\n"
                + String.join("\n", failures.subList(0, Math.min(60, failures.size()))));
    }

    private static void write(String name, List<BufferedImage> images, List<String> captions, int cols)
            throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        int cellW = 0;
        int cellH = 0;
        for (final BufferedImage im : images) {
            cellW = Math.max(cellW, im.getWidth());
            cellH = Math.max(cellH, im.getHeight());
        }
        final int rows = (images.size() + cols - 1) / cols;
        final int pad = 16;
        final int cap = 18;
        final BufferedImage sheet = new BufferedImage(pad + cols * (cellW + pad), pad + rows * (cellH + cap + pad),
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0E0F13));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < images.size(); i++) {
            final int x = pad + (i % cols) * (cellW + pad);
            final int y = pad + (i / cols) * (cellH + cap + pad);
            g.setColor(new Color(0x98A0AD));
            g.drawString(captions.get(i), x, y + 12);
            g.drawImage(images.get(i), x, y + cap, null);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, name));
    }
}
