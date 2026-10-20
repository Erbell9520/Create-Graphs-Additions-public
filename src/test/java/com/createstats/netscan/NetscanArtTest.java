package com.createstats.netscan;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.createstats.netscan.client.NetscanShell;
import com.createstats.netscan.client.ScanContext;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.netscan.scan.ScanNodeKind;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetscanArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 760;
    private static final int H = 500;

    @Test
    void rendersEveryPageAtEveryTier() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * (W + 24) + 24, 3 * (H + 34) + 24);
        final Graphics2D g = sheet.createGraphics();

        place(g, page(healthy(), ScannerTier.INDUSTRIAL, 0), 0, 0,
                "Map / industrial, healthy network");
        place(g, page(overstressed(), ScannerTier.INDUSTRIAL, 0), 1, 0,
                "Map / overstressed: culprit haloed, ratio edge accented");
        place(g, page(healthy(), ScannerTier.INDUSTRIAL, 1), 0, 1,
                "Inspector / speed-stress curve at the top tier");
        place(g, page(healthy(), ScannerTier.FIELD, 1), 1, 1,
                "Inspector / field grade: the curve names the tier that unlocks it");
        place(g, page(overstressed(), ScannerTier.ADVANCED, 2), 0, 2,
                "Ledger / sorted by what each machine actually costs");
        place(g, page(overstressed(), ScannerTier.ADVANCED, 3), 1, 2,
                "Network / advanced grade: trend is locked, gauge is not");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "netscan_survey.png"));
    }

    @Test
    void rendersTheStatesThatAreNotANetwork() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(3 * (W + 24) + 24, (H + 34) + 24);
        final Graphics2D g = sheet.createGraphics();

        final ScanContext unpowered = context(ScanGraph.EMPTY, ScannerTier.FIELD, 0);
        unpowered.form = ScannerForm.CLAMP;
        unpowered.powered = false;
        place(g, draw(unpowered, 0), 0, 0, "No power / clamp-on form with no wire");

        final ScanContext unclamped = context(ScanGraph.EMPTY, ScannerTier.FIELD, 0);
        unclamped.form = ScannerForm.CLAMP;
        unclamped.seedMissing = true;
        place(g, draw(unclamped, 0), 1, 0, "Not clamped / the block it faces is not kinetic");

        final ScanContext cut = context(truncated(), ScannerTier.FIELD, 0);
        place(g, draw(cut, 0), 2, 0, "Truncated / the survey says so rather than implying a total");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "netscan_states.png"));
    }

    @Test
    void rendersEveryArrangementOfTheMap() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * (W + 24) + 24, 2 * (H + 34) + 24);
        final Graphics2D g = sheet.createGraphics();

        place(g, mapIn(ScanLayoutMode.DRIVE, false), 0, 0,
                "Drive / layered by distance from the source");
        place(g, mapIn(ScanLayoutMode.RING, false), 1, 0,
                "Ring / one ring per step, source at the centre");
        place(g, mapIn(ScanLayoutMode.POWER, false), 0, 1,
                "Power / branches sized by the stress they carry");
        place(g, mapIn(ScanLayoutMode.PLAN, false), 1, 1,
                "Plan / world coordinates from above, chunk grid, north up");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "netscan_map_modes.png"));
    }

    @Test
    void rendersTheSelectedPathAndTheHoverCard() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * (W + 24) + 24, (H + 34) + 24);
        final Graphics2D g = sheet.createGraphics();
        place(g, mapIn(ScanLayoutMode.DRIVE, true), 0, 0,
                "Selection / the chain back to the source, everything else pushed back");
        place(g, mapIn(ScanLayoutMode.POWER, true), 1, 0,
                "Selection in Power / the trunk still reads as the trunk");
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "netscan_map_selection.png"));
    }

    private static BufferedImage mapIn(ScanLayoutMode mode, boolean selected) {
        final ScanGraph graph = spread();
        final ScanContext ctx = context(graph, ScannerTier.INDUSTRIAL, 0);
        ctx.mode = mode;

        ctx.selected = selected ? new BlockPos(20, 64, 12) : null;
        return draw(ctx, 0);
    }

    @Test
    void detailIsGatedByTierAndTheGateNamesItsGrade() {
        final ScanContext field = context(healthy(), ScannerTier.FIELD, 0);
        assertTrue(field.allows(1));
        assertFalse(field.allows(2), "per-machine stress is an Advanced feature");
        assertFalse(field.allows(3));

        final ScanContext advanced = context(healthy(), ScannerTier.ADVANCED, 0);
        assertTrue(advanced.allows(2));
        assertFalse(advanced.allows(3), "the trend and the projection are Industrial only");

        assertTrue(context(healthy(), ScannerTier.INDUSTRIAL, 0).allows(3));

        org.junit.jupiter.api.Assertions.assertEquals("Advanced", ScanContext.unlockedBy(2));
        org.junit.jupiter.api.Assertions.assertEquals("Industrial", ScanContext.unlockedBy(3));
    }

    @Test
    void everyTierBudgetIsAnElementCountAndTheyAscend() {
        assertTrue(ScannerTier.FIELD.elementBudget() < ScannerTier.ADVANCED.elementBudget());
        assertTrue(ScannerTier.ADVANCED.elementBudget() < ScannerTier.INDUSTRIAL.elementBudget());
        assertTrue(ScannerTier.FIELD.elementsPerTick() < ScannerTier.INDUSTRIAL.elementsPerTick());
        assertTrue(ScannerTier.FIELD.stressPerRpm() < ScannerTier.INDUSTRIAL.stressPerRpm());
    }

    @Test
    void everyPageDrawsWithoutAGraphAtAll() {
        for (int page = 0; page < 4; page++) {
            draw(context(ScanGraph.EMPTY, ScannerTier.FIELD, 0), page);
        }
    }

    private static BufferedImage page(ScanGraph graph, ScannerTier tier, int index) {
        final ScanContext ctx = context(graph, tier, index);
        if (index == 0) {
            ctx.selected = null;
        }
        return draw(ctx, index);
    }

    private static ScanContext context(ScanGraph graph, ScannerTier tier, int index) {
        final ScanContext ctx = new ScanContext(null, new BlockPos(64, 70, -12));
        ctx.tier = tier;
        ctx.form = ScannerForm.INLINE;
        ctx.graph = graph;
        ctx.powered = true;
        ctx.sweepCount = graph.size();
        if (!graph.isEmpty()) {
            ctx.selected = graph.byLoad().get(0).pos();
        }
        final List<float[]> history = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            history.add(new float[]{240.0f + i * 3.0f + (i % 7) * 6.0f, 512.0f});
        }
        ctx.history = history;
        return ctx;
    }

    private static BufferedImage draw(ScanContext ctx, int page) {
        final NetscanShell shell = new NetscanShell(ctx);
        shell.layout(W, H);

        for (int i = 0; i < 40; i++) {
            shell.update(1.0f / 60.0f);
        }
        for (int i = 0; i < page; i++) {
            shell.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1 + i + 1, 0, 0);
        }
        for (int i = 0; i < 60; i++) {
            shell.update(1.0f / 60.0f);
        }
        final RasterSurface surface = new RasterSurface(W, H, 0x14161B);
        shell.draw(surface, -1.0, -1.0);
        return surface.finish();
    }

    private static ScanNode node(int x, ScanNodeKind kind, String name, float speed, float stress,
                                 float capacity, float coefficient, boolean source, boolean over,
                                 Integer upstream) {
        return new ScanNode(at(x), name, "create:" + name.toLowerCase().replace(' ', '_'), kind,
                speed, stress, capacity, coefficient, source, over,
                upstream == null ? null : at(upstream));
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 70, 0);
    }

    private static ScanGraph build(List<ScanNode> nodes, List<ScanEdge> edges, boolean truncated) {
        final Map<BlockPos, ScanNode> map = new LinkedHashMap<>();
        for (final ScanNode n : nodes) {
            map.put(n.pos(), n);
        }
        return new ScanGraph(map, edges, 7L, 0L, truncated, Set.of());
    }

    private static ScanGraph healthy() {
        final List<ScanNode> nodes = List.of(
                node(0, ScanNodeKind.SOURCE, "Water Wheel", 16.0f, 0.0f, 512.0f, 0.0f, true, false, null),
                node(1, ScanNodeKind.TRANSFER, "Shaft", 16.0f, 0.0f, 0.0f, 0.0f, false, false, 0),
                node(2, ScanNodeKind.GEARING, "Large Cogwheel", 16.0f, 0.0f, 0.0f, 0.0f, false, false, 1),
                node(3, ScanNodeKind.GEARING, "Cogwheel", 32.0f, 0.0f, 0.0f, 0.0f, false, false, 2),
                node(4, ScanNodeKind.MACHINE, "Mechanical Press", 32.0f, 256.0f, 0.0f, 8.0f, false, false, 3),
                node(5, ScanNodeKind.MACHINE, "Millstone", 32.0f, 128.0f, 0.0f, 4.0f, false, false, 3),
                node(6, ScanNodeKind.INSTRUMENT, "Topology Scanner", 32.0f, 64.0f, 0.0f, 2.0f, false, false, 3),
                node(7, ScanNodeKind.MACHINE, "Encased Fan", 32.0f, 64.0f, 0.0f, 2.0f, false, false, 3));
        return build(nodes, List.of(
                new ScanEdge(at(0), at(1), 1.0f), new ScanEdge(at(1), at(2), 1.0f),
                new ScanEdge(at(2), at(3), 2.0f), new ScanEdge(at(3), at(4), 1.0f),
                new ScanEdge(at(3), at(5), 1.0f), new ScanEdge(at(3), at(6), 1.0f),
                new ScanEdge(at(3), at(7), 1.0f)), false);
    }

    private static ScanGraph overstressed() {
        final List<ScanNode> nodes = List.of(
                node(0, ScanNodeKind.SOURCE, "Water Wheel", 0.0f, 0.0f, 512.0f, 0.0f, true, true, null),
                node(1, ScanNodeKind.TRANSFER, "Shaft", 0.0f, 0.0f, 0.0f, 0.0f, false, true, 0),
                node(2, ScanNodeKind.GEARING, "Large Cogwheel", 0.0f, 0.0f, 0.0f, 0.0f, false, true, 1),
                node(3, ScanNodeKind.GEARING, "Cogwheel", 0.0f, 0.0f, 0.0f, 0.0f, false, true, 2),
                node(4, ScanNodeKind.MACHINE, "Encased Fan", 256.0f, 512.0f, 0.0f, 2.0f, false, true, 3),
                node(5, ScanNodeKind.MACHINE, "Mechanical Press", 64.0f, 512.0f, 0.0f, 8.0f, false, false, 3),
                node(6, ScanNodeKind.MACHINE, "Mechanical Mixer", 64.0f, 1024.0f, 0.0f, 16.0f, false, false, 3),
                node(7, ScanNodeKind.MACHINE, "Mechanical Saw", 64.0f, 256.0f, 0.0f, 4.0f, false, false, 3),
                node(8, ScanNodeKind.MACHINE, "Mechanical Drill", 64.0f, 256.0f, 0.0f, 4.0f, false, false, 4),
                node(9, ScanNodeKind.TRANSFER, "Belt", 64.0f, 0.0f, 0.0f, 0.0f, false, false, 5));
        return build(nodes, List.of(
                new ScanEdge(at(0), at(1), 1.0f), new ScanEdge(at(1), at(2), 1.0f),
                new ScanEdge(at(2), at(3), 4.0f), new ScanEdge(at(3), at(4), 1.0f),
                new ScanEdge(at(3), at(5), 1.0f), new ScanEdge(at(3), at(6), 1.0f),
                new ScanEdge(at(3), at(7), 1.0f), new ScanEdge(at(4), at(8), 1.0f),
                new ScanEdge(at(5), at(9), 1.0f)), false);
    }

    private static ScanGraph truncated() {
        final List<ScanNode> nodes = new ArrayList<>();
        final List<ScanEdge> edges = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            nodes.add(node(i, i % 4 == 0 ? ScanNodeKind.MACHINE : ScanNodeKind.TRANSFER,
                    i % 4 == 0 ? "Mechanical Press" : "Shaft", 32.0f,
                    i % 4 == 0 ? 256.0f : 0.0f, 0.0f, i % 4 == 0 ? 8.0f : 0.0f,
                    false, false, i == 0 ? null : i - 1));
            if (i > 0) {
                edges.add(new ScanEdge(at(i - 1), at(i), 1.0f));
            }
        }
        return build(nodes, edges, true);
    }

    private static ScanGraph spread() {
        final Map<BlockPos, ScanNode> map = new LinkedHashMap<>();
        final List<ScanEdge> edges = new ArrayList<>();
        final BlockPos[] where = {
                new BlockPos(0, 64, 0), new BlockPos(4, 64, 0), new BlockPos(8, 64, 0),
                new BlockPos(12, 64, 0), new BlockPos(12, 64, 6), new BlockPos(12, 64, 12),
                new BlockPos(16, 64, 12), new BlockPos(20, 64, 12), new BlockPos(12, 71, 0),
                new BlockPos(18, 71, 0), new BlockPos(24, 71, 0), new BlockPos(8, 64, -8),
                new BlockPos(4, 64, -8)};
        final ScanNodeKind[] kinds = {
                ScanNodeKind.SOURCE, ScanNodeKind.TRANSFER, ScanNodeKind.GEARING,
                ScanNodeKind.TRANSFER, ScanNodeKind.TRANSFER, ScanNodeKind.MACHINE,
                ScanNodeKind.MACHINE, ScanNodeKind.MACHINE, ScanNodeKind.GEARING,
                ScanNodeKind.MACHINE, ScanNodeKind.INSTRUMENT, ScanNodeKind.TRANSFER,
                ScanNodeKind.MACHINE};
        final String[] names = {"Water Wheel", "Shaft", "Large Cogwheel", "Shaft", "Belt",
                "Mechanical Press", "Mechanical Mixer", "Mechanical Saw", "Speed Controller",
                "Encased Fan", "Topology Scanner", "Shaft", "Millstone"};
        final float[] stress = {0, 0, 0, 0, 0, 512, 1024, 256, 0, 384, 32, 0, 48};
        final int[] upstream = {-1, 0, 1, 2, 3, 4, 5, 6, 2, 8, 9, 2, 11};
        for (int i = 0; i < where.length; i++) {
            map.put(where[i], new ScanNode(where[i], names[i],
                    "create:" + names[i].toLowerCase().replace(' ', '_'), kinds[i],
                    i >= 8 && i <= 10 ? 128.0f : 32.0f, stress[i],
                    i == 0 ? 4096.0f : 0.0f, stress[i] / 32.0f, i == 0, false,
                    upstream[i] < 0 ? null : where[upstream[i]]));
            if (upstream[i] >= 0) {
                edges.add(new ScanEdge(where[upstream[i]], where[i],
                        i == 8 ? 4.0f : 1.0f));
            }
        }
        return new ScanGraph(map, edges, 7L, 0L, false, Set.of());
    }

    private static void ensureOut() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
    }

    private static BufferedImage sheet(int w, int h) {
        final BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0A0B0E));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return sheet;
    }

    private static void place(Graphics2D g, BufferedImage img, int col, int row, String caption) {
        final int x = 24 + col * (W + 24);
        final int y = 34 + row * (H + 34);
        g.drawImage(img, x, y, null);
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(caption, x, y - 8);
    }
}
