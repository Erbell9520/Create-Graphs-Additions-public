package com.createstats.statsmk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainFaceRasterTest {
    private static final File OUT = new File("fixture_preview");

    @BeforeEach
    void seedNetwork() {
        ClientRailNetwork.accept(network(), true);
    }

    @Test
    void rendersBoardAndMap() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }

        final int[][] sizes = {{1, 1}, {2, 2}, {4, 3}};
        final Lod[] levels = {Lod.FULL, Lod.REDUCED, Lod.GLYPH};
        final String[] levelNames = {"full", "reduced", "glyph"};
        final int cell = 232;

        final BufferedImage sheet = new BufferedImage(
                48 + levels.length * (cell + 18),
                60 + sizes.length * 2 * (cell + 44), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        caption(g, "Railway block faces. Rows alternate board and map; columns are detail level.",
                24, 22);

        int row = 0;
        for (final int[] size : sizes) {
            for (final Mk3Settings.FaceMode mode : new Mk3Settings.FaceMode[]{
                    Mk3Settings.FaceMode.TRAIN_BOARD, Mk3Settings.FaceMode.TRAIN_MAP}) {
                final int y = 48 + row * (cell + 44);
                caption(g, size[0] + " x " + size[1] + "  "
                        + (mode == Mk3Settings.FaceMode.TRAIN_BOARD ? "board" : "map"), 24, y - 8);

                for (int i = 0; i < levels.length; i++) {
                    final PanelModel model = model(mode);

                    final int w = size[0] * 64;
                    final int h = size[1] * 64;
                    final BufferedImage img = PanelFaceRasterTest.renderFace(model, levels[i], w, h);

                    final float fit = Math.min(cell / (float) w, cell / (float) h);
                    final int drawW = Math.round(w * fit);
                    final int drawH = Math.round(h * fit);
                    final int x = 24 + i * (cell + 18);
                    g.drawImage(img, x, y, drawW, drawH, null);
                    caption(g, levelNames[i] + "  (" + w + " x " + h + " px)", x, y + drawH + 14);
                }
                row++;
            }
        }

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_train_faces.png"));
    }

    @Test
    void rendersTheWallDiagram() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        com.createstats.train.map.RailHistory.clear();
        com.createstats.train.map.ClientRailMap.clear();

        final RailSnapshot snap = network();
        final com.createstats.train.map.RailMap map =
                com.createstats.train.map.RailMap.build(snap, List.of(), true, java.util.Set.of());
        for (int step = 0; step < 720; step++) {
            snap.serverTick += 10L;
            for (final RailSnapshot.TrainState t : snap.trains()) {
                if (t.derailed || !t.hasSchedule) {
                    continue;
                }
                t.distanceToDestination = Math.max(0.0,
                        t.distanceToDestination - Math.abs(t.speed) * 10.0);
                if (t.distanceToDestination <= 0.0) {
                    t.distanceToDestination = t.distanceStartedAt;
                    t.currentEntry = (t.currentEntry + 1) % Math.max(1, t.stops.size());
                    final RailSnapshot.Station next =
                            snap.resolveFilter(t.stops.get(t.currentEntry), t.dimension);
                    if (next != null) {
                        t.destinationStation = next.id;
                        t.destinationName = next.name;
                    }
                }
            }
            map.refresh(snap);
            com.createstats.train.map.RailHistory.sample(snap, map);
        }
        ClientRailNetwork.accept(snap, true);

        final int[][] sizes = {{2, 2}, {3, 2}, {5, 3}, {1, 1}};
        final int cell = 300;
        final BufferedImage sheet = new BufferedImage(
                48 + sizes.length * (cell + 18), 110 + cell, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        caption(g, "The train diagram on a wall panel, at four assembly sizes.", 24, 22);

        for (int i = 0; i < sizes.length; i++) {
            final PanelModel model = model(Mk3Settings.FaceMode.TRAIN_DIAGRAM);
            final int w = sizes[i][0] * 64;
            final int h = sizes[i][1] * 64;
            final BufferedImage img = PanelFaceRasterTest.renderFace(model, Lod.FULL, w, h);
            final float fit = Math.min(cell / (float) w, cell / (float) h);
            final int x = 24 + i * (cell + 18);
            g.drawImage(img, x, 48, Math.round(w * fit), Math.round(h * fit), null);
            caption(g, sizes[i][0] + " x " + sizes[i][1] + " blocks (" + w + " x " + h + " px)",
                    x, 48 + Math.round(h * fit) + 16);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_train_diagram_face.png"));
    }

    @Test
    void arrivalSentinelsNeverRenderAsNumbers() {
        assertEquals("no route", TrainIntel.eta(-2));
        assertEquals("--", TrainIntel.eta(-1));
        assertEquals("due", TrainIntel.eta(0), "an arriving train reads as due, matching Create");
        assertEquals("due", TrainIntel.eta(199));
        assertEquals("10s", TrainIntel.eta(200));
        assertEquals("1m 00s", TrainIntel.eta(1200));
        assertEquals("1h 00m", TrainIntel.eta(72000));

        for (int ticks = -5; ticks < 5000; ticks += 7) {
            final String text = TrainIntel.eta(ticks);
            assertFalse(text.matches("-\\d.*"),
                    "eta(" + ticks + ") rendered a negative number: " + text);
        }
    }

    @Test
    void blockedTrainsAreNotGivenAnArrivalTime() {
        final RailSnapshot snap = network();
        final RailSnapshot.Station platform = snap.stations().get(0);

        final List<TrainIntel.BoardRow> rows = TrainIntel.board(snap, platform, 10);
        assertFalse(rows.isEmpty(), "the seeded network should produce a board");

        TrainIntel.BoardRow derailed = null;
        for (final TrainIntel.BoardRow r : rows) {
            if ("Ore Hauler".equals(r.train)) {
                derailed = r;
            }
        }
        assertNotNull(derailed, "the derailed train should still be listed");
        assertEquals("derailed", derailed.blockedBy);
        assertEquals("held", derailed.eta(),
                "a derailed train must not be given a countdown it cannot meet");
    }

    @Test
    void faultsBecomeAlerts() {
        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(network());
        assertFalse(alerts.isEmpty(), "a network with a derailment should raise alerts");
        assertEquals(TrainIntel.Severity.CRITICAL, alerts.get(0).severity,
                "the worst fault must sort first");

        boolean derailment = false;
        boolean longHold = false;
        for (final TrainIntel.Alert a : alerts) {
            derailment |= a.detail.contains("Derailed");
            longHold |= a.detail.contains("Held at a signal");
        }
        assertTrue(derailment, "a derailed train must raise an alert");
        assertTrue(longHold, "a train held past the threshold must raise an alert");
    }

    @Test
    void snapshotRoundTripsThroughNbt() {
        final RailSnapshot original = network();
        final RailSnapshot copy = RailSnapshot.read(original.write());

        assertEquals(original.stations().size(), copy.stations().size());
        assertEquals(original.trains().size(), copy.trains().size());
        assertEquals(original.arrivals().size(), copy.arrivals().size());
        assertEquals(original.links().size(), copy.links().size());

        final RailSnapshot.TrainState before = original.trains().get(0);
        final RailSnapshot.TrainState after = copy.train(before.id);
        assertNotNull(after, "a train must survive the round trip under the same id");
        assertEquals(before.name, after.name);
        assertEquals(before.stops, after.stops);
        assertEquals(before.waitingForSignal, after.waitingForSignal);
        assertEquals(before.ticksWaitingForSignal, after.ticksWaitingForSignal);
        assertEquals(before.phase, after.phase);
        assertEquals(before.distanceToDestination, after.distanceToDestination, 1.0e-6);

        assertTrue(copy.links().get(0).hasPath(), "a traced link must keep its shape");
        assertEquals(original.links().get(0).path[3], copy.links().get(0).path[3], 1.0e-6f);
    }

    @Test
    void liveUpdateKeepsExistingTopology() {
        ClientRailNetwork.accept(network(), true);
        final int stations = ClientRailNetwork.get().stations().size();
        assertTrue(stations > 0);

        final RailSnapshot live = new RailSnapshot();
        live.serverTick = 500L;
        live.trains().add(ClientRailNetwork.get().trains().get(0));
        ClientRailNetwork.accept(live, false);

        assertEquals(stations, ClientRailNetwork.get().stations().size(),
                "a live-only update must carry the held topology forward");
        assertEquals(500L, ClientRailNetwork.get().serverTick);
    }

    private static PanelModel model(Mk3Settings.FaceMode mode) {
        final PanelModel m = new PanelModel();
        m.faceMode = mode;
        m.boardStation = "Central *";
        m.title = "Central";
        m.status = "LIVE";
        return m;
    }

    private static RailSnapshot network() {
        return RailFixtures.network();
    }

    private static void caption(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(text, x, y);
    }
}
