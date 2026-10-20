package com.createstats.statsmk3.page;

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

import com.createstats.netscan.ScannerForm;
import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanNodeKind;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopoRosterArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 980;
    private static final int H = 600;

    private static final class Probe implements PeerRoster.Delegate {
        private final Map<String, TopoArt.Reading> readings;
        private final boolean ambiguous;
        private final ScanLayoutMode mode;

        Probe(Map<String, TopoArt.Reading> readings, boolean ambiguous, ScanLayoutMode mode) {
            this.readings = readings;
            this.ambiguous = ambiguous;
            this.mode = mode;
        }

        TopoArt.Reading read(Peers.Peer p) {
            return this.readings.getOrDefault(p.key, TopoArt.Reading.absent());
        }

        TopoArt.PeerArt art(Peers.Peer p) {
            final TopoArt.Reading r = read(p);
            return new TopoArt.PeerArt(p.numeral, p.label(this.ambiguous), p.bearing, p.series,
                    false, r.live() ? r.pct() : 0.0f, r,
                    ScanLayout.layout(r.graph(), this.mode));
        }

        @Override
        public void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                              boolean hovered, float alpha) {
            TopoArt.paintPeer(c, art(peer), r, big, mid, hovered, this.mode, 0.0f);
        }

        @Override
        public float urgency(Peers.Peer peer) {
            final TopoArt.Reading r = read(peer);
            return r.live() ? r.pct() : 85.0f;
        }

        @Override
        public int statusColor(Peers.Peer peer) {
            return TopoArt.statusColor(read(peer));
        }

        @Override
        public float[] compareSeries(Peers.Peer peer) {
            return TopoArt.percentSeries(read(peer));
        }

        @Override
        public boolean alarming(Peers.Peer peer) {
            final TopoArt.Reading r = read(peer);
            return r.live() ? r.over() || r.pct() >= TopoArt.CRITICAL_AT : r.present();
        }
    }

    @Test
    void rendersTheRosterAtEverySize() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * (W + 24) + 24, 2 * (H + 34) + 24);
        final Graphics2D g = sheet.createGraphics();

        place(g, render(1, PeerRoster.Mode.ROSTER, ScanLayoutMode.DRIVE), 0, 0,
                "One scanner / the whole page is the survey");
        place(g, render(2, PeerRoster.Mode.ROSTER, ScanLayoutMode.DRIVE), 1, 0,
                "Two scanners / both present, neither merged into the other");
        place(g, render(4, PeerRoster.Mode.ROSTER, ScanLayoutMode.POWER), 0, 1,
                "Four scanners in Power / ranked by load, the dead one is not hidden");
        place(g, render(4, PeerRoster.Mode.FOCUS, ScanLayoutMode.DRIVE), 1, 1,
                "Focus / one survey at hero size, the rest on the rail");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "topology_roster.png"));
    }

    @Test
    void rendersCompareAndTheEmptyPage() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * (W + 24) + 24, (H + 34) + 24);
        final Graphics2D g = sheet.createGraphics();
        place(g, render(4, PeerRoster.Mode.COMPARE, ScanLayoutMode.DRIVE), 0, 0,
                "Compare / load now, since only an Industrial scanner records a trend");
        place(g, renderEmpty(), 1, 0,
                "Nothing linked / the page names the fix rather than the absence");
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "topology_roster_modes.png"));
    }

    @Test
    void twoScannersOnOneNetworkAreSaidToBeRatherThanMerged() {
        final List<TopoArt.PeerArt> arts = arts(4);
        final List<int[]> groups = TopoArt.twins(arts);
        assertEquals(1, groups.size(), "one redundant pair");
        assertEquals("N1 and N3", TopoArt.describe(arts, groups.get(0)));

        assertEquals(4, arts.size());
    }

    @Test
    void surveysOnDifferentNetworksAreNeverGrouped() {
        final List<TopoArt.PeerArt> arts = new ArrayList<>(List.of(arts(4).get(0), arts(4).get(1)));
        assertTrue(TopoArt.twins(arts).isEmpty(), "different network ids, different networks");
    }

    @Test
    void anInstrumentThatIsNotReadingSaysWhichKindOfNotReading() {
        assertEquals("Scanner missing", TopoArt.Reading.absent().excuse());
        assertEquals("No rotation", reading(false, true, ScanGraph.EMPTY, ScannerForm.INLINE).excuse());
        assertEquals("No power", reading(false, true, ScanGraph.EMPTY, ScannerForm.CLAMP).excuse());
        assertEquals("Not clamped", reading(true, false, ScanGraph.EMPTY, ScannerForm.CLAMP).excuse());
        assertEquals("No network found", reading(true, true, ScanGraph.EMPTY, ScannerForm.INLINE).excuse());
        assertEquals("", reading(true, true, healthy(1024.0f, 0.5f, 11L), ScannerForm.INLINE).excuse());
    }

    @Test
    void aDeadInstrumentOutranksAHealthyNetwork() {
        final Probe probe = new Probe(readings(4), true, ScanLayoutMode.DRIVE);
        final List<Peers.Peer> peers = peers(4);
        assertTrue(probe.urgency(peers.get(3)) > probe.urgency(peers.get(1)),
                "the unpowered scanner ranks above the quiet network");
        assertTrue(probe.alarming(peers.get(3)), "and it is worth interrupting for");
        assertFalse(probe.alarming(peers.get(1)));
    }

    @Test
    void everyStateOfEveryPeerDrawsWithoutAGraph() {
        for (final ScanLayoutMode mode : ScanLayoutMode.values()) {
            for (int n = 0; n <= 4; n++) {
                render(n, PeerRoster.Mode.ROSTER, mode);
            }
        }
    }

    private static BufferedImage render(int n, PeerRoster.Mode mode, ScanLayoutMode layout) {
        PeerFocusMemory.clear();
        final Probe probe = new Probe(readings(n), n > 1, layout);
        final PeerRoster roster = new PeerRoster("topology", new BlockPos(0, 64, 0));
        roster.setArea(new Rect(24.0f, 24.0f + TopoArt.HEADER_H,
                W - 48.0f, H - 48.0f - TopoArt.HEADER_H));
        roster.setPeers(peers(n), probe);
        for (int i = 0; i < 90; i++) {
            roster.update(1.0f / 60.0f);
        }
        if (mode == PeerRoster.Mode.FOCUS && n > 1) {
            roster.focusOn(roster.peers().get(0));
        } else if (mode == PeerRoster.Mode.COMPARE) {
            roster.toCompare();
        }
        for (int i = 0; i < 120; i++) {
            roster.setPeers(roster.peers(), probe);
            roster.update(1.0f / 60.0f);
        }

        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        final Rect area = new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f);
        final List<TopoArt.PeerArt> arts = new ArrayList<>();
        for (final Peers.Peer p : roster.peers()) {
            arts.add(probe.art(p));
        }
        final List<int[]> groups = TopoArt.twins(arts);
        TopoArt.header(c(s), area, arts,
                groups.isEmpty() ? "" : TopoArt.describe(arts, groups.get(0)) + " survey the same network",
                240.0f);
        if (mode == PeerRoster.Mode.COMPARE) {
            final List<TopoArt.PeerArt> compared = new ArrayList<>();
            for (final Peers.Peer p : roster.compared()) {
                compared.add(probe.art(p));
            }
            TopoArt.compareChart(s, roster.compareChartRect(), compared);
        }
        roster.draw(s, probe);
        return s.finish();
    }

    private static PanelSurface c(RasterSurface s) {
        return s;
    }

    private static BufferedImage renderEmpty() {
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        final Rect area = new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f);
        TopoArt.header(s, area, List.of(), "", 240.0f);
        TopoArt.empty(s, area);
        return s.finish();
    }

    private static final String[] NAMES = {"Main line", "Andesite bay", "Kiln shafts", "Press hall"};
    private static final int[][] AT = {{14, 1, -6}, {-22, 0, 9}, {3, 8, 40}, {31, -2, 2}};

    private static final long[] NETWORK = {41L, 77L, 41L, 108L};
    private static final float[] CAPACITY = {4096, 1024, 4096, 512};
    private static final float[] LOAD = {0.94f, 0.31f, 0.94f, 1.12f};

    private static List<Peers.Peer> peers(int n) {
        final List<Peers.Peer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final BlockPos at = new BlockPos(AT[i][0], 64 + AT[i][1], AT[i][2]);
            final SourceProbe.Unit unit = MachinePageArtTest.unit("Topology Scanner", NAMES[i],
                    MachinePageArtTest.metric("elements", 40.0f),
                    MachinePageArtTest.metric("stress_su", CAPACITY[i] * LOAD[i]),
                    MachinePageArtTest.metric("capacity_su", CAPACITY[i]));
            unit.config().setSourcePos(at);
            out.add(new Peers.Peer(unit, i, Peers.bearing(new BlockPos(0, 64, 0), at)));
        }
        return out;
    }

    private static Map<String, TopoArt.Reading> readings(int n) {
        final Map<String, TopoArt.Reading> out = new LinkedHashMap<>();
        final List<Peers.Peer> peers = peers(n);
        for (int i = 0; i < n; i++) {
            final TopoArt.Reading r = i == 3
                    ? reading(false, true, ScanGraph.EMPTY, ScannerForm.CLAMP)
                    : reading(true, true, healthy(CAPACITY[i], LOAD[i], NETWORK[i]),
                            i % 2 == 0 ? ScannerForm.INLINE : ScannerForm.CLAMP);
            out.put(peers.get(i).key, r);
        }
        return out;
    }

    private static List<TopoArt.PeerArt> arts(int n) {
        final Probe probe = new Probe(readings(n), n > 1, ScanLayoutMode.DRIVE);
        final List<TopoArt.PeerArt> out = new ArrayList<>();
        for (final Peers.Peer p : peers(n)) {
            out.add(probe.art(p));
        }
        return out;
    }

    private static TopoArt.Reading reading(boolean powered, boolean seeded, ScanGraph graph,
                                           ScannerForm form) {
        return new TopoArt.Reading(true, powered, seeded, false, graph.size(), graph,
                ScannerTier.ADVANCED, form, new float[0]);
    }

    private static ScanGraph healthy(float capacity, float load, long networkId) {
        final Map<BlockPos, ScanNode> nodes = new LinkedHashMap<>();
        final List<ScanEdge> edges = new ArrayList<>();
        final float perMachine = capacity * load / 4.0f;
        final BlockPos[] at = new BlockPos[10];
        for (int i = 0; i < at.length; i++) {
            at[i] = new BlockPos(i * 3, 64 + (i > 5 ? 4 : 0), i > 5 ? 6 : 0);
        }
        final ScanNodeKind[] kinds = {ScanNodeKind.SOURCE, ScanNodeKind.TRANSFER,
                ScanNodeKind.GEARING, ScanNodeKind.TRANSFER, ScanNodeKind.MACHINE,
                ScanNodeKind.MACHINE, ScanNodeKind.TRANSFER, ScanNodeKind.MACHINE,
                ScanNodeKind.MACHINE, ScanNodeKind.INSTRUMENT};
        final String[] names = {"Water Wheel", "Shaft", "Large Cogwheel", "Shaft",
                "Mechanical Press", "Mechanical Mixer", "Belt", "Millstone", "Encased Fan",
                "Topology Scanner"};
        final int[] upstream = {-1, 0, 1, 2, 3, 3, 3, 6, 6, 2};
        for (int i = 0; i < at.length; i++) {
            final float stress = kinds[i].isLoad() ? perMachine : 0.0f;
            nodes.put(at[i], new ScanNode(at[i], names[i], "create:x", kinds[i], 32.0f, stress,
                    i == 0 ? capacity : 0.0f, stress / 32.0f, i == 0,
                    load > 1.0f && kinds[i].isLoad(), upstream[i] < 0 ? null : at[upstream[i]]));
            if (upstream[i] >= 0) {
                edges.add(new ScanEdge(at[upstream[i]], at[i], i == 2 ? 2.0f : 1.0f));
            }
        }
        return new ScanGraph(nodes, edges, networkId, 0L, false, Set.of());
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
