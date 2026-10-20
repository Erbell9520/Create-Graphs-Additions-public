package com.createstats.statsmk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelFaceRasterTest {
    private static final File OUT = new File("fixture_preview");

    private static final int[][] ASSEMBLIES = {{1, 1}, {2, 2}, {3, 2}};

    @Test
    void rendersEveryPresetAndLod() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }

        final BufferedImage sheet = new BufferedImage(1230, 2760, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int col = 0;
        int row = 0;
        final int cols1 = GridSpec.colsFor(1);
        final int rows1 = GridSpec.rowsFor(1);
        for (final LayoutPresets.Preset preset : LayoutPresets.fitting(cols1, rows1)) {
            place(g, render(preset(preset.id(), cols1, rows1, 5), Lod.FULL, 256, 256), col, row,
                    preset.name() + " / 1 x 1");
            if (++col == 4) {
                col = 0;
                row++;
            }
        }

        final int cols2 = GridSpec.colsFor(2);
        final int rows2 = GridSpec.rowsFor(2);
        for (final String id : new String[]{"dashboard", "control", "mosaic", "wall"}) {
            place(g, render(preset(id, cols2, rows2, 8), Lod.FULL, 256, 256), col, row,
                    LayoutPresets.byId(id).name() + " / 2 x 2");
            if (++col == 4) {
                col = 0;
                row++;
            }
        }

        place(g, render(preset("hero", cols1, rows1, 3), Lod.REDUCED, 256, 256), col, row,
                "REDUCED (8m away)");
        if (++col == 4) {
            col = 0;
            row++;
        }
        place(g, render(preset("hero", cols1, rows1, 3), Lod.GLYPH, 256, 256), col, row,
                "GLYPH (24m away)");
        if (++col == 4) {
            col = 0;
            row++;
        }
        place(g, render(empty(), Lod.FULL, 256, 256), col, row, "no sources");
        if (++col == 4) {
            col = 0;
            row++;
        }
        place(g, render(lostSignal(), Lod.FULL, 256, 256), col, row, "pinned signal gone");

        final int y = 34 + (row + 1) * 300;
        final int wideCols = GridSpec.colsFor(3);
        final int wideRows = GridSpec.rowsFor(2);
        g.drawImage(render(preset("dashboard", wideCols, wideRows, 9), Lod.FULL, 768, 512),
                24, y, null);
        caption(g, "Dashboard / 3 wide by 2 tall wall, " + wideCols + " x " + wideRows + " grid",
                24, y - 8);
        g.drawImage(render(handComposed(), Lod.FULL, 340, 512), 824, y, null);
        caption(g, "hand composed, mixed instruments", 824, y - 8);

        final int wallY = y + 560;
        final int wallCols = GridSpec.colsFor(3);
        final int wallRows = GridSpec.rowsFor(3);
        g.drawImage(render(preset("wall", wallCols, wallRows, 23), Lod.FULL, 576, 576),
                24, wallY, null);
        caption(g, "Signal wall / 3 x 3, " + wallCols + " x " + wallRows
                + " grid, 23 signals with long labels", 24, wallY - 8);
        g.drawImage(render(preset("mosaic", wallCols, wallRows, 23), Lod.FULL, 576, 576),
                632, wallY, null);
        caption(g, "Mosaic / same assembly, same signals", 632, wallY - 8);

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_panel_face.png"));
    }

    @Test
    void detailRampAcrossDistance() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }

        final float[] distances = {4.0f, 12.0f, 16.0f, 18.0f, 26.0f, 40.0f, 56.0f, 90.0f};
        final int cell = 208;
        final int gap = 14;

        final BufferedImage sheet = new BufferedImage(
                48 + distances.length * (cell + gap),
                70 + ASSEMBLIES.length * (cell + 54), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        caption(g, "Panel face by viewing distance. Columns are metres from the block.", 24, 22);

        for (int r = 0; r < ASSEMBLIES.length; r++) {
            final int mbW = ASSEMBLIES[r][0];
            final int mbH = ASSEMBLIES[r][1];
            final PanelModel model = preset("split",
                    GridSpec.colsFor(mbW), GridSpec.rowsFor(mbH), 5);
            final int y = 56 + r * (cell + 54);
            caption(g, mbW + " x " + mbH + " assembly, full detail to "
                    + Math.round(com.createstats.client.DisplayPrefs.fullRangeFor(mbW, mbH)) + " m",
                    24, y - 8);

            for (int i = 0; i < distances.length; i++) {
                final float d = distances[i];
                final Lod lod = Lod.forView(d * (double) d, mbW, mbH);
                final BufferedImage img = render(model, lod, cell, cell);
                final int x = 24 + i * (cell + gap);
                g.drawImage(img, x, y, null);
                caption(g, (int) d + " m", x, y + cell + 14);
            }
        }

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_detail_ramp.png"));
    }

    @Test
    void detailNeverIncreasesWithDistance() {
        for (final int[] assembly : ASSEMBLIES) {
            float previousLabels = Float.MAX_VALUE;
            int previousHistory = Integer.MAX_VALUE;
            for (float d = 1.0f; d < 140.0f; d += 0.5f) {
                final Lod lod = Lod.forView(d * (double) d, assembly[0], assembly[1]);
                assertTrue(lod.labelAlpha() <= previousLabels + 1.0e-4f,
                        "labels sharpened at " + d + " m on " + assembly[0] + "x" + assembly[1]);
                assertTrue(lod.historyBudget() <= previousHistory,
                        "history grew at " + d + " m on " + assembly[0] + "x" + assembly[1]);
                previousLabels = lod.labelAlpha();
                previousHistory = lod.historyBudget();
            }
        }

        for (float d = 1.0f; d < 140.0f; d += 0.5f) {
            final float small = Lod.forView(d * (double) d, 1, 1).labelAlpha();
            final float large = Lod.forView(d * (double) d, 3, 2).labelAlpha();
            assertTrue(large >= small - 1.0e-4f,
                    "a 3x2 wall resolved less than a 1x1 tile at " + d + " m");
        }
    }

    @Test
    void everyPresetPaintsOnEveryAssembly() {
        for (final int[] assembly : ASSEMBLIES) {
            final int cols = GridSpec.colsFor(assembly[0]);
            final int rows = GridSpec.rowsFor(assembly[1]);
            for (final LayoutPresets.Preset preset : LayoutPresets.fitting(cols, rows)) {
                for (final int channels : new int[]{0, 1, 3, 12}) {
                    final PanelModel model = preset(preset.id(), cols, rows, channels);
                    assertNoOverlap(model.layout, preset.id() + " on " + cols + "x" + rows);
                    for (final Lod lod : Lod.values()) {
                        render(model, lod, 256, 256);
                        render(model, lod, 48, 32);
                    }
                }
            }
        }
    }

    @Test
    void layoutRoundTripsAndRescales() {
        final Mk3Layout quad = LayoutPresets.build("quad", 4, 4, 4);
        assertEquals(4, quad.size(), "quad should produce four tiles");
        assertNoOverlap(quad, "quad");

        final Mk3Layout grown = quad.copy();
        assertTrue(grown.fitTo(8, 8));
        assertNoOverlap(grown, "quad rescaled to 8x8");
        int covered = 0;
        for (final Tile t : grown.tiles()) {
            covered += t.cells();
        }
        assertEquals(64, covered, "a rescaled full-face layout should still cover the whole face");

        final Mk3Layout manual = Mk3Layout.onGrid(4, 4);
        assertNotNull(manual.add(new Tile(0, 0, 2, 2)));
        assertNull(manual.add(new Tile(1, 1, 2, 2)), "an overlapping tile must be refused");
        assertNull(manual.add(new Tile(3, 3, 2, 2)), "a tile off the grid must be refused");
        assertEquals(1, manual.size());
    }

    @Test
    void duplicatePlacesCopiesWithoutOverlapping() {
        final Mk3Layout layout = Mk3Layout.onGrid(4, 4);
        final Tile first = layout.add(new Tile(0, 0, 2, 2));
        first.channelId = "src_a_auto_m0";
        first.instrumentId = "arc";

        final Tile copy = layout.duplicate(first);
        assertNotNull(copy, "there is room for a copy on a 4x4 grid");
        assertEquals("src_a_auto_m0", copy.channelId, "a copy keeps the original's binding");
        assertEquals("arc", copy.instrumentId);
        assertNoOverlap(layout, "after duplicate");

        assertNotNull(layout.duplicate(first));
        assertNotNull(layout.duplicate(first));
        assertEquals(4, layout.size());
        assertNull(layout.duplicate(first), "a full grid must refuse another copy");
        assertNoOverlap(layout, "full grid");
    }

    @Test
    void reorderChangesWhichChannelAnAutoTileGets() {
        final PanelModel model = preset("quad", 4, 4, 4);
        final Tile moved = model.layout.get(1);
        final String before = channelOf(model, moved);
        final String headBefore = channelOf(model, model.layout.get(0));

        model.layout.reorder(1, 0);
        LayoutResolver.bind(model);

        assertEquals(headBefore, channelOf(model, moved),
                "a tile moved to the front should now take the channel the front tile had");
        assertNotEquals(before, channelOf(model, moved),
                "reordering must actually change what the moved tile shows");
    }

    private static String channelOf(PanelModel model, Tile tile) {
        for (final PanelModel.Slot slot : model.slots) {
            if (slot.tile == tile) {
                return slot.channel == null ? "" : slot.channel.id;
            }
        }
        return "";
    }

    @Test
    void pinnedTileReportsLostSignalRatherThanSubstituting() {
        final PanelModel model = lostSignal();
        PanelModel.Slot lost = null;
        for (final PanelModel.Slot slot : model.slots) {
            if (slot.tile.isBound() && "src_a_auto_missing".equals(slot.tile.channelId)) {
                lost = slot;
            }
        }
        assertNotNull(lost, "the pinned tile should still be resolved into a slot");
        assertTrue(lost.lostSignal, "a pin to an absent channel must be reported as lost");
        assertNull(lost.channel, "a lost pin must not be given some other channel");

        final PanelModel pinned = preset("quad", 4, 4, 4);
        pinned.layout.get(0).channelId = pinned.channels.get(2).id;
        LayoutResolver.bind(pinned);
        int repeats = 0;
        for (final PanelModel.Slot slot : pinned.slots) {
            if (slot.channel != null && slot.channel.id.equals(pinned.channels.get(2).id)) {
                repeats++;
            }
        }
        assertEquals(1, repeats, "a pinned channel should not also fill an auto tile");
    }

    private static void assertNoOverlap(Mk3Layout layout, String what) {
        final List<Tile> tiles = layout.tiles();
        for (int i = 0; i < tiles.size(); i++) {
            for (int j = i + 1; j < tiles.size(); j++) {
                assertFalse(tiles.get(i).overlaps(tiles.get(j)), what + ": tiles overlap");
            }
            assertTrue(tiles.get(i).right() <= layout.cols() && tiles.get(i).bottom() <= layout.rows(),
                    what + ": tile outside the grid");
        }
    }

    static PanelModel preset(String presetId, int cols, int rows, int channels) {
        final PanelModel m = base(channels);
        m.layout = LayoutPresets.build(presetId, cols, rows, channels);
        LayoutResolver.bind(m);
        return m;
    }

    private static PanelModel handComposed() {
        final PanelModel m = base(5);
        final Mk3Layout layout = Mk3Layout.onGrid(4, 8);
        final Tile hero = layout.add(new Tile(0, 0, 4, 3));
        hero.instrumentId = "area";
        hero.value = true;
        hero.channelId = m.channels.get(1).id;

        final Tile ring = layout.add(new Tile(0, 3, 2, 2));
        ring.instrumentId = "ring";
        final Tile arc = layout.add(new Tile(2, 3, 2, 2));
        arc.instrumentId = "arc";
        arc.channelId = m.channels.get(4).id;

        final Tile band = layout.add(new Tile(0, 5, 4, 1));
        band.instrumentId = "spark";
        final Tile bars = layout.add(new Tile(0, 6, 4, 2));
        bars.instrumentId = "bar";

        m.layout = layout;
        LayoutResolver.bind(m);
        return m;
    }

    private static PanelModel lostSignal() {
        final PanelModel m = base(3);
        final Mk3Layout layout = LayoutPresets.build("quad", 4, 4, 3);
        layout.get(0).channelId = "src_a_auto_missing";
        layout.get(1).channelId = m.channels.get(0).id;
        m.layout = layout;
        LayoutResolver.bind(m);
        return m;
    }

    private static PanelModel base(int channels) {
        final PanelModel m = new PanelModel();
        m.instrumentId = "area";
        m.title = "Main Drive";
        m.status = "LIVE";
        m.statusColor = Tokens.GOOD;
        m.timeRange = "5m";
        m.time = 12.5f;

        final String[] labels = {"Stress %", "Current Stress (SU)", "Capacity (SU)",
                "Remaining (SU)", "RPM", "Overstressed", "Efficiency Score", "Energy %",
                "Stored FE", "Capacity FE", "Can Receive", "Can Extract", "Net FE/t", "Input FE/t",
                "Output FE/t", "Total FE In", "Total FE Out", "Accumulator Blocks", "Width",
                "Height", "Connectors", "Items / min", "Fill Level %"};
        for (int i = 0; i < channels; i++) {
            final ChannelView ch = new ChannelView("src_a_auto_m" + i);
            ch.label = labels[i % labels.length];
            ch.unit = i == 0 ? "%" : "SU";
            ch.historyCount = 120;
            ch.history = new float[ch.historyCount];
            for (int s = 0; s < ch.historyCount; s++) {
                ch.history[s] = (float) (40.0 + i * 9 + s * 0.28
                        + Math.sin(s * 0.21 + i) * 7.0
                        + (s > 90 ? 14.0 : 0.0));
            }
            ch.value = ch.history[ch.historyCount - 1];
            ch.min = 0.0f;
            ch.max = 130.0f;
            ch.alerting = i == 2;
            m.channels.add(ch);
        }
        for (int i = 0; i < m.lamps.length; i++) {
            m.lamps[i] = i == 2 ? 2 : i < channels ? 1 : 0;
        }
        m.helperFiring = true;
        return m;
    }

    static PanelModel empty() {
        final PanelModel m = new PanelModel();
        m.title = "Stats Display";
        m.status = "NO DATA";
        m.statusColor = Tokens.INK_3;
        m.layout = LayoutPresets.build("quad", 4, 4, 0);
        LayoutResolver.bind(m);
        return m;
    }

    private static BufferedImage render(PanelModel model, Lod lod, int w, int h) {
        return renderFace(model, lod, w, h);
    }

    static BufferedImage renderFace(PanelModel model, Lod lod, int w, int h) {
        final RasterSurface surface = new RasterSurface(w, h);
        PanelPainter.paint(surface, new Rect(0.0f, 0.0f, w, h), model, lod);
        return surface.finish();
    }

    private static void place(Graphics2D g, BufferedImage img, int col, int row, String caption) {
        final int x = 24 + col * 284;
        final int y = 34 + row * 300;
        g.drawImage(img, x, y, null);
        caption(g, caption, x, y - 8);
    }

    private static void caption(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(text, x, y);
    }
}
