package com.createstats.statsmk3;

import com.createstats.statsmk3.layout.GridSpec;
import com.createstats.statsmk3.layout.LayoutPresets;
import com.createstats.statsmk3.layout.Mk3Layout;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.LayoutResolver;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TileGapTest {
    private static final int[][] ASSEMBLIES = {
            {1, 1}, {2, 1}, {2, 2}, {3, 2}, {3, 3}, {4, 3}, {5, 5}, {6, 3}, {8, 4},
    };

    private static float[] faceWidthsFor(int mbW) {
        return new float[]{140.0f, 185.0f, 260.0f, 420.0f, mbW * 64.0f};
    }

    @Test
    void everyTileIsDrawableOnEveryGridAtEveryFaceSize() {
        for (final int[] assembly : ASSEMBLIES) {
            final int cols = GridSpec.colsFor(assembly[0]);
            final int rows = GridSpec.rowsFor(assembly[1]);

            for (final String preset : new String[]{"dashboard", "split", "quad", "wall"}) {
                final PanelModel model = model(preset, cols, rows, 6);
                for (final float faceW : faceWidthsFor(assembly[0])) {
                    final float faceH = faceW * assembly[1] / assembly[0];
                    final Rect face = new Rect(0.0f, 0.0f, faceW, faceH);
                    final Rect body = PanelPainter.bodyOf(face, model, Lod.FULL);
                    final float gap = PanelPainter.gapOf(face, model);
                    final String what = assembly[0] + "x" + assembly[1] + " " + preset
                            + " on a " + cols + "x" + rows + " grid at "
                            + Math.round(faceW) + "x" + Math.round(faceH);

                    if (body.isEmpty()) {
                        continue;
                    }
                    assertTrue(gap * (cols - 1) < body.w,
                            what + ": the gaps alone are wider than the body ("
                                    + gap * (cols - 1) + " of " + body.w + ")");
                    assertTrue(gap * (rows - 1) < body.h,
                            what + ": the gaps alone are taller than the body ("
                                    + gap * (rows - 1) + " of " + body.h + ")");

                    for (final Tile tile : model.layout.tiles()) {
                        final Rect r = LayoutResolver.rectOf(tile, model.layout, body, gap);
                        assertTrue(!r.isEmpty(), what + ": a tile resolved to nothing");
                        assertTrue(r.w >= 3.0f && r.h >= 3.0f,
                                what + ": a tile resolved to " + r.w + " x " + r.h);
                    }
                }
            }
        }
    }

    @Test
    void cellsGetMoreOfTheFaceThanTheGapsDo() {
        for (final int[] assembly : ASSEMBLIES) {
            final int cols = GridSpec.colsFor(assembly[0]);
            final int rows = GridSpec.rowsFor(assembly[1]);
            final PanelModel model = model("dashboard", cols, rows, 6);

            for (final float faceW : faceWidthsFor(assembly[0])) {
                final float faceH = faceW * assembly[1] / assembly[0];
                final Rect face = new Rect(0.0f, 0.0f, faceW, faceH);
                final Rect body = PanelPainter.bodyOf(face, model, Lod.FULL);
                if (body.isEmpty()) {
                    continue;
                }
                final float gap = PanelPainter.gapOf(face, model);
                assertTrue(gap * (cols - 1) <= body.w * 0.5f + 0.01f,
                        assembly[0] + "x" + assembly[1] + " at " + Math.round(faceW)
                                + ": gaps take " + Math.round(gap * (cols - 1) / body.w * 100)
                                + "% of the width");
                assertTrue(gap * (rows - 1) <= body.h * 0.5f + 0.01f,
                        assembly[0] + "x" + assembly[1] + " at " + Math.round(faceW)
                                + ": gaps take " + Math.round(gap * (rows - 1) / body.h * 100)
                                + "% of the height");
            }
        }
    }

    @Test
    void theReportedPanelDrawsSomething() {
        final PanelModel model = model("dashboard", GridSpec.colsFor(5), GridSpec.rowsFor(5), 6);
        final CountingSurface s = new CountingSurface();
        final Rect face = new Rect(0.0f, 0.0f, 145.0f, 145.0f);

        PanelPainter.paint(s, face, model, Lod.FULL);

        assertTrue(s.triangles > 1200,
                "a 5 x 5 panel in the preview dock drew only " + s.triangles
                        + " triangles, which is the ground and the lamps and no instruments");
    }

    @org.junit.jupiter.api.Test
    void writesTheDockPreviewSheet() throws java.io.IOException {
        final java.io.File out = new java.io.File("fixture_preview");
        if (!out.exists() && !out.mkdirs()) {
            throw new java.io.IOException("could not create " + out);
        }
        final int cell = 200;
        final int gap = 16;
        final java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
                40 + ASSEMBLIES.length * (cell + gap), 220,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        final java.awt.Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        for (int i = 0; i < ASSEMBLIES.length; i++) {
            final int mbW = ASSEMBLIES[i][0];
            final int mbH = ASSEMBLIES[i][1];
            final PanelModel model = model("dashboard",
                    GridSpec.colsFor(mbW), GridSpec.rowsFor(mbH), 6);

            final com.createstats.statsmk3.ui.Rect bezel =
                    com.createstats.statsmk3.view.StudioLayout.fitAspect(
                            new Rect(0.0f, 0.0f, 169.0f, 159.0f), mbW / (float) mbH);
            final RasterSurface s = new RasterSurface(Math.max(8, Math.round(bezel.w)),
                    Math.max(8, Math.round(bezel.h)));
            PanelPainter.paint(s, new Rect(0.0f, 0.0f, bezel.w, bezel.h), model, Lod.FULL);
            final java.awt.image.BufferedImage img = s.finish();

            final int x = 20 + i * (cell + gap);
            g.drawImage(img, x, 40, null);
            g.setColor(new java.awt.Color(0x98A0AD));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
            g.drawString(mbW + " x " + mbH + ", grid " + GridSpec.colsFor(mbW)
                    + " x " + GridSpec.rowsFor(mbH), x, 30);
        }
        g.dispose();
        PreviewImages.write(sheet, "png",
                new java.io.File(out, "mk3_dock_preview.png"));
    }

    private static PanelModel model(String presetId, int cols, int rows, int channels) {
        final PanelModel m = new PanelModel();
        m.title = "Stressometer 2";
        m.status = "LIVE";
        m.statusColor = Tokens.GOOD;
        m.timeRange = "5m";
        final String[] labels = {"Stress %", "Current Stress (SU)", "Capacity (SU)",
                "Remaining (SU)", "RPM", "Overstressed"};
        for (int i = 0; i < channels; i++) {
            final ChannelView ch = new ChannelView("src_a_auto_m" + i);
            ch.label = labels[i % labels.length];
            ch.unit = i == 0 ? "%" : "SU";
            ch.historyCount = 120;
            ch.history = new float[ch.historyCount];
            for (int k = 0; k < ch.historyCount; k++) {
                ch.history[k] = (float) (40.0 + i * 9 + k * 0.28 + Math.sin(k * 0.21 + i) * 7.0);
            }
            ch.value = ch.history[ch.historyCount - 1];
            ch.min = 0.0f;
            ch.max = 130.0f;
            m.channels.add(ch);
        }
        final Mk3Layout layout = LayoutPresets.build(presetId, cols, rows, channels);
        m.layout = layout;
        LayoutResolver.bind(m);
        return m;
    }
}
