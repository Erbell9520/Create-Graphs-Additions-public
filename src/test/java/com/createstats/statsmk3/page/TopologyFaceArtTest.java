package com.createstats.statsmk3.page;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.createstats.netscan.ScannerForm;
import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanNodeKind;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.TopologyFacePainter;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TopologyFaceArtTest {
    private static final File OUT = new File("fixture_preview");

    @Test
    void rendersTheFaceAtEverySize() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final int[][] sizes = {{1, 1}, {2, 2}, {4, 3}};
        final Lod[] levels = {Lod.FULL, Lod.REDUCED, Lod.GLYPH};
        final int cell = 250;

        final BufferedImage sheet = new BufferedImage(48 + levels.length * (cell + 40),
                80 + sizes.length * (cell + 60), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        for (int row = 0; row < sizes.length; row++) {
            final int y = 48 + row * (cell + 60);
            for (int i = 0; i < levels.length; i++) {
                final int fw = 64 * sizes[row][0] - 16;
                final int fh = 64 * sizes[row][1] - 20;
                final RasterSurface s = new RasterSurface(fw, fh, 0x05070A);
                TopologyFacePainter.paint(s, new Rect(0.0f, 0.0f, fw, fh),
                        model(row == 2 ? 2 : 1), levels[i]);
                g.drawImage(s.finish(), 24 + i * (cell + 40), y, null);
            }
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "topology_face.png"));
    }

    @Test
    void everyNotAReadingStateDrawsWithoutThrowing() {
        for (final Lod lod : Lod.values()) {
            assertDoesNotThrow(() -> TopologyFacePainter.paint(
                    new RasterSurface(200, 180, 0x05070A), new Rect(0.0f, 0.0f, 200.0f, 180.0f),
                    empty(), lod));
            assertDoesNotThrow(() -> TopologyFacePainter.paint(
                    new RasterSurface(200, 180, 0x05070A), new Rect(0.0f, 0.0f, 200.0f, 180.0f),
                    unpowered(), lod));
        }
    }

    @Test
    void theFaceIsOnlyResolvedForItsOwnMode() {
        final PanelModel m = model(1);
        assertEquals(Mk3Settings.FaceMode.TOPOLOGY, m.faceMode);
        assertFalse(m.topology.isEmpty(), "a topology model carries its surveys");
    }

    private static PanelModel model(int surveys) {
        final PanelModel m = new PanelModel();
        m.faceMode = Mk3Settings.FaceMode.TOPOLOGY;
        m.time = 4.0f;
        for (int i = 0; i < surveys; i++) {
            m.topology.add(new TopoArt.Reading(true, true, true, false, 10,
                    drivetrain(4096.0f, i == 0 ? 0.86f : 0.34f, 40L + i), ScannerTier.INDUSTRIAL,
                    ScannerForm.INLINE, new float[0]));
        }
        return m;
    }

    private static PanelModel empty() {
        final PanelModel m = new PanelModel();
        m.faceMode = Mk3Settings.FaceMode.TOPOLOGY;
        return m;
    }

    private static PanelModel unpowered() {
        final PanelModel m = new PanelModel();
        m.faceMode = Mk3Settings.FaceMode.TOPOLOGY;
        m.topology.add(new TopoArt.Reading(true, false, true, false, 0, ScanGraph.EMPTY,
                ScannerTier.FIELD, ScannerForm.INLINE, new float[0]));
        return m;
    }

    private static ScanGraph drivetrain(float capacity, float load, long networkId) {
        final Map<BlockPos, ScanNode> nodes = new LinkedHashMap<>();
        final List<ScanEdge> edges = new ArrayList<>();
        final float perMachine = capacity * load / 4.0f;
        final ScanNodeKind[] kinds = {ScanNodeKind.SOURCE, ScanNodeKind.TRANSFER, ScanNodeKind.GEARING,
                ScanNodeKind.TRANSFER, ScanNodeKind.MACHINE, ScanNodeKind.MACHINE,
                ScanNodeKind.TRANSFER, ScanNodeKind.MACHINE, ScanNodeKind.MACHINE,
                ScanNodeKind.INSTRUMENT};
        final String[] names = {"Water Wheel", "Shaft", "Large Cogwheel", "Shaft", "Mechanical Press",
                "Mechanical Mixer", "Belt", "Millstone", "Encased Fan", "Topology Scanner"};
        final int[] upstream = {-1, 0, 1, 2, 3, 3, 3, 6, 6, 2};
        final BlockPos[] at = new BlockPos[10];
        for (int i = 0; i < at.length; i++) {
            at[i] = new BlockPos(i * 3, 64 + (i > 5 ? 3 : 0), i > 5 ? 5 : 0);
            final float stress = kinds[i].isLoad() ? perMachine : 0.0f;
            nodes.put(at[i], new ScanNode(at[i], names[i], "create:x", kinds[i], 32.0f, stress,
                    i == 0 ? capacity : 0.0f, stress / 32.0f, i == 0, false,
                    upstream[i] < 0 ? null : at[upstream[i]]));
            if (upstream[i] >= 0) {
                edges.add(new ScanEdge(at[upstream[i]], at[i], i == 2 ? 2.0f : 1.0f));
            }
        }
        return new ScanGraph(nodes, edges, networkId, 0L, false, Set.of());
    }
}
