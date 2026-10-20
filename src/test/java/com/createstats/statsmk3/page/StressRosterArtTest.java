package com.createstats.statsmk3.page;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressRosterArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 980;
    private static final int H = 600;

    private static final class Probe implements PeerRoster.Delegate {
        private final boolean ambiguous;

        Probe(boolean ambiguous) {
            this.ambiguous = ambiguous;
        }

        @Override
        public void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                              boolean hovered, float alpha) {
            StressArt.paintPeer(c, art(peer, this.ambiguous), r, big, mid, hovered, 0.0f);
        }

        @Override
        public float urgency(Peers.Peer peer) {
            return StressArt.readingOf(peer.unit).pct();
        }

        @Override
        public int statusColor(Peers.Peer peer) {
            return StressArt.statusColor(StressArt.readingOf(peer.unit));
        }

        @Override
        public float[] compareSeries(Peers.Peer peer) {
            return StressArt.percentSeries(StressArt.readingOf(peer.unit));
        }

        @Override
        public boolean alarming(Peers.Peer peer) {
            final StressArt.Reading r = StressArt.readingOf(peer.unit);
            return r.live() && (r.overstressed() || r.pct() >= StressArt.CRITICAL_AT);
        }
    }

    static StressArt.PeerArt art(Peers.Peer p, boolean ambiguous) {
        final StressArt.Reading r = StressArt.readingOf(p.unit);
        return new StressArt.PeerArt(p.numeral, p.label(ambiguous), p.bearing, p.series,
                false, r.pct(), r.pct(), Math.min(100.0f, r.pct() + 4.0f), r.used() * 1.04f, r);
    }

    @Test
    void rendersRosterAtEveryCount() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * W + 72, 2 * H + 96);
        final Graphics2D g = sheet.createGraphics();

        place(g, render(4, PeerRoster.Mode.ROSTER, false), 0, 0, "Roster / four networks");
        place(g, render(9, PeerRoster.Mode.ROSTER, false), 1, 0, "Roster / nine networks");
        place(g, render(2, PeerRoster.Mode.ROSTER, false), 0, 1,
                "Roster / two networks, which is where the roster starts");
        place(g, render(1, PeerRoster.Mode.FOCUS, false), 1, 1,
                "One network / opens focused, exactly as the page always did");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_stress_roster.png"));
    }

    @Test
    void rendersFocusCompareAndMorph() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * W + 72, 2 * H + 96);
        final Graphics2D g = sheet.createGraphics();

        place(g, render(4, PeerRoster.Mode.FOCUS, false), 0, 0, "Focus / hero plus the chip rail");
        place(g, render(4, PeerRoster.Mode.COMPARE, false), 1, 0, "Compare / three on one axis");
        place(g, render(4, PeerRoster.Mode.FOCUS, true), 0, 1,
                "Mid morph / roster card growing into the hero");
        place(g, renderCramped(), 1, 1, "Focus / cramped to 300px tall");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_stress_focus.png"));
    }

    @Test
    void ranksByLoadWorstFirst() {
        final PeerRoster roster = seeded(6, PeerRoster.Mode.ROSTER, false);
        float previous = Float.MAX_VALUE;
        for (final Peers.Peer p : ordered(roster)) {
            final float pct = StressArt.readingOf(p.unit).pct();
            assertTrue(pct <= previous + 0.001f,
                    "roster must be ranked worst first, saw " + pct + " after " + previous);
            previous = pct;
        }
    }

    @Test
    void onlyTheHeroReachesHeroDetail() {
        for (final PeerRoster.Mode mode : PeerRoster.Mode.values()) {
            final PeerRoster roster = seeded(4, mode, false);
            final Rect hero = roster.heroRect();
            final float heroArea = hero.w * hero.h;
            for (final Peers.Peer p : roster.peers()) {
                final Rect r = roster.rectOf(p);
                final float big = PeerRoster.ramp((r.w * r.h) / heroArea, 0.45f, 0.88f);
                final boolean isHero = mode == PeerRoster.Mode.FOCUS
                        && p.sourceId.equals(roster.focused().sourceId);
                if (!isHero) {
                    assertTrue(big < 0.35f, mode + " " + p.numeral
                            + " is not the hero but reached hero detail, big=" + big);
                }
            }
        }
    }

    @Test
    void everyChipFitsTheRail() {
        final PeerRoster roster = seeded(9, PeerRoster.Mode.FOCUS, false);
        final Rect area = roster.heroRect();
        for (final Peers.Peer p : roster.peers()) {
            final Rect r = roster.rectOf(p);
            assertTrue(r.bottom() <= area.bottom() + 0.6f,
                    p.numeral + " chip runs past the rail: " + r);
            assertTrue(r.h >= 30.0f, p.numeral + " chip collapsed to " + r.h);
        }
    }

    @Test
    void twinsNeedEveryNetworkWideReading() {
        final List<Peers.Peer> peers = new ArrayList<>();
        peers.add(peer(0, "A", 2048.0f, 1780.0f, 64.0f));
        peers.add(peer(1, "B", 512.0f, 141.0f, 32.0f));
        peers.add(peer(2, "C", 2048.0f, 1780.0f, 96.0f));
        peers.add(peer(3, "D", 4096.0f, 1780.0f, 16.0f));

        final List<int[]> groups = Peers.twins(peers, "capacity_su", "stress_su");
        assertEquals(1, groups.size(), "exactly one pair shares a network");
        assertEquals(2, groups.get(0).length);
        assertEquals(0, groups.get(0)[0]);
        assertEquals(2, groups.get(0)[1]);
        assertEquals("N1 and N3", Peers.describe(peers, groups.get(0)));
    }

    @Test
    void bearingUsesMinecraftCompass() {
        final net.minecraft.core.BlockPos here = new net.minecraft.core.BlockPos(0, 64, 0);
        assertEquals("10m N", Peers.bearing(here, new net.minecraft.core.BlockPos(0, 64, -10)));
        assertEquals("10m E", Peers.bearing(here, new net.minecraft.core.BlockPos(10, 64, 0)));
        assertEquals("14m SE, y+3", Peers.bearing(here, new net.minecraft.core.BlockPos(10, 67, 10)));
    }

    @Test
    void focusIsRemembered() {
        PeerFocusMemory.clear();
        final net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(4, 70, 9);
        PeerFocusMemory.remember(pos, "stress", "src_a");
        assertEquals("src_a", PeerFocusMemory.recall(pos, "stress"));
        assertEquals("", PeerFocusMemory.recall(pos, "heat"));
        assertEquals("", PeerFocusMemory.recall(new net.minecraft.core.BlockPos(0, 0, 0), "stress"));
        PeerFocusMemory.remember(pos, "stress", "");
        assertEquals("", PeerFocusMemory.recall(pos, "stress"));
    }

    @Test
    void nineUpStaysWithinTheTriangleBudget() {
        final com.createstats.statsmk3.CountingSurface counter =
                new com.createstats.statsmk3.CountingSurface();
        final PeerRoster roster = seeded(9, PeerRoster.Mode.ROSTER, false);
        paint(counter, roster, new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f));
        assertTrue(counter.triangles < 9446, "nine up roster drew " + counter.triangles
                + " triangles, over the Instruments page budget");

        counter.reset();
        final PeerRoster focus = seeded(9, PeerRoster.Mode.FOCUS, false);
        paint(counter, focus, new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f));

        assertTrue(counter.triangles < 9446, "nine up focus drew " + counter.triangles);
    }

    @Test
    void twoPeersOpenTheRoster() {
        PeerFocusMemory.clear();
        assertEquals(PeerRoster.Mode.ROSTER, seeded(2, null, false).mode());
        assertEquals(PeerRoster.Mode.FOCUS, seeded(1, null, false).mode());
    }

    private static final float[] CAPS = {2048, 512, 8192, 2048, 1024, 4096, 256, 3072, 1536};
    private static final float[] LOAD = {0.86f, 0.28f, 0.955f, 0.84f, 0.62f, 0.44f, 0.71f, 0.33f, 0.52f};
    private static final float[] RPM = {64, 32, 128, 96, 48, 16, 192, 64, 32};
    private static final String[] NAMES = {"", "Kiln line", "", "Bulk mixers", "Andesite bay",
            "", "Crushing", "Press hall", "Sawmill row"};
    private static final int[][] AT = {{18, 2, -12}, {-7, 0, 1}, {4, -4, 31}, {12, 2, -9},
            {-20, 1, 14}, {2, -2, -9}, {44, 6, 3}, {11, 0, 11}, {0, 3, 5}};

    static Peers.Peer peer(int i, String label, float cap, float used, float rpm) {
        return peer(i, label, cap, used, rpm, new net.minecraft.core.BlockPos(
                AT[i % AT.length][0], 64 + AT[i % AT.length][1], AT[i % AT.length][2]));
    }

    static Peers.Peer peer(int i, String label, float cap, float used, float rpm,
                           net.minecraft.core.BlockPos at) {
        final float[] history = new float[120];
        for (int k = 0; k < history.length; k++) {
            history[k] = used * (0.94f + 0.06f * k / history.length)
                    + (float) Math.sin(k * 0.21) * used * 0.02f;
        }
        final SourceProbe.Unit unit = MachinePageArtTest.unit("Stressometer", label,
                MachinePageArtTest.series("stress_su", history),
                MachinePageArtTest.metric("capacity_su", cap),
                MachinePageArtTest.metric("remaining_su", Math.max(0.0f, cap - used)),
                MachinePageArtTest.metric("rpm", rpm),
                MachinePageArtTest.metric("overstressed", used >= cap ? 1.0f : 0.0f));

        unit.config().setSourcePos(at);
        return new Peers.Peer(unit, i, Peers.bearing(new net.minecraft.core.BlockPos(0, 64, 0), at));
    }

    private static List<Peers.Peer> peers(int n) {
        final List<Peers.Peer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final String label = NAMES[i].isEmpty() ? "" : NAMES[i];
            final Peers.Peer p = peer(i, label.isEmpty() ? "unnamed" + i : label,
                    CAPS[i], CAPS[i] * LOAD[i], RPM[i]);
            if (label.isEmpty()) {
                p.unit.config().setUserLabel("");
            }
            out.add(p);
        }
        return out;
    }

    private static PeerRoster seeded(int n, PeerRoster.Mode mode, boolean midMorph) {
        PeerFocusMemory.clear();
        final Probe probe = new Probe(n > 1);
        final PeerRoster roster = new PeerRoster("stress", new net.minecraft.core.BlockPos(0, 64, 0));
        roster.setArea(new Rect(24.0f, 24.0f + StressArt.HEADER_H,
                W - 48.0f, H - 48.0f - StressArt.HEADER_H));
        roster.setPeers(peers(n), probe);

        for (int i = 0; i < 90; i++) {
            roster.update(1.0f / 60.0f);
        }
        if (mode == PeerRoster.Mode.FOCUS && n > 1) {
            roster.focusOn(roster.peers().get(2));
        } else if (mode == PeerRoster.Mode.COMPARE) {
            roster.toCompare();
        }
        final int frames = midMorph ? 7 : 120;
        for (int i = 0; i < frames; i++) {
            roster.setPeers(roster.peers(), probe);
            roster.update(1.0f / 60.0f);
        }
        return roster;
    }

    private static List<Peers.Peer> ordered(PeerRoster roster) {
        final List<Peers.Peer> out = new ArrayList<>(roster.peers());
        out.sort((a, b) -> {
            final Rect ra = roster.rectOf(a);
            final Rect rb = roster.rectOf(b);
            final int row = Float.compare(ra.y, rb.y);
            return row != 0 ? row : Float.compare(ra.x, rb.x);
        });
        return out;
    }

    private static BufferedImage render(int n, PeerRoster.Mode mode, boolean midMorph) {
        final PeerRoster roster = seeded(n, mode, midMorph);
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        final Rect area = new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f);
        paint(s, roster, area);
        return s.finish();
    }

    private static BufferedImage renderCramped() {
        final int h = 300;
        PeerFocusMemory.clear();
        final Probe probe = new Probe(true);
        final PeerRoster roster = new PeerRoster("stress", new net.minecraft.core.BlockPos(0, 64, 0));
        roster.setArea(new Rect(24.0f, 24.0f + StressArt.HEADER_H,
                W - 48.0f, h - 48.0f - StressArt.HEADER_H));
        roster.setPeers(peers(4), probe);
        roster.focusOn(roster.peers().get(0));
        for (int i = 0; i < 120; i++) {
            roster.setPeers(roster.peers(), probe);
            roster.update(1.0f / 60.0f);
        }
        final RasterSurface s = new RasterSurface(W, h, 0x111318);
        paint(s, roster, new Rect(24.0f, 24.0f, W - 48.0f, h - 48.0f));
        final BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x111318));
        g.fillRect(0, 0, W, H);
        g.drawImage(s.finish(), 0, 0, null);
        g.dispose();
        return out;
    }

    private static void paint(PanelSurface s, PeerRoster roster, Rect area) {
        final boolean ambiguous = roster.size() > 1;
        final List<StressArt.PeerArt> arts = new ArrayList<>();
        for (final Peers.Peer p : roster.peers()) {
            arts.add(art(p, ambiguous));
        }
        StressArt.header(s, area, arts,
                roster.size() > 3 ? "N1 and N4 report the same network" : "",
                roster.size() > 1 ? 224.0f : 0.0f);
        if (roster.size() > 1) {
            roster.drawCoach(s, roster.size() + " stress networks are linked to this panel.",
                    "Ranked by load. Click any to open it, or Compare to overlay them.");
        }
        if (roster.mode() == PeerRoster.Mode.COMPARE) {
            final List<StressArt.PeerArt> compared = new ArrayList<>();
            for (final Peers.Peer p : roster.compared()) {
                final StressArt.PeerArt a = art(p, ambiguous);
                compared.add(new StressArt.PeerArt(a.numeral(), a.label(), a.bearing(), a.series(),
                        true, a.needlePct(), a.printedPct(), a.peakPct(), a.peakUsed(), a.data()));
            }
            StressArt.compareChart(s, roster.compareChartRect(), compared);
        }
        roster.draw(s, new Probe(ambiguous));
    }

    private static void ensureOut() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
    }

    private static BufferedImage sheet(int w, int h) {
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x07080B));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void place(Graphics2D g, BufferedImage img, int col, int row, String caption) {
        final int x = 24 + col * (W + 24);
        final int y = 40 + row * (H + 48);
        g.drawImage(img, x, y, null);
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(caption, x, y - 8);
    }
}
