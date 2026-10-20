package com.createstats.train.map;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailMapArtTest {
    private static final File OUT = new File("fixture_preview");

    @Test
    void adjacentPlatformsSharingAStemFoldIntoOneStation() {
        final RailMap map = RailMap.build(network(), List.of(), true, Set.of());

        RailMap.Node yard = null;
        for (final RailMap.Node n : map.nodes()) {
            if (n.name.toLowerCase().startsWith("quarry")) {
                yard = n;
            }
        }
        assertNotNull(yard, "the three quarry platforms should have produced a node");
        assertEquals(3, yard.members.size(), "all three quarry platforms belong to one complex");
        assertTrue(yard.isGroup());
        assertEquals("Quarry Platform", yard.name,
                "a complex is named by what its members share, not by the pattern that matched");
    }

    @Test
    void sleepingServicesGiveUpTheirSlotInTheBundle() {
        final RailSnapshot snap = busyNetwork();
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());

        int running = 0;
        int dormant = 0;
        for (final RailMap.Line line : map.lines()) {
            switch (line.tier(snap)) {
                case RailMap.Line.TIER_RUNNING -> running++;
                case RailMap.Line.TIER_DORMANT -> dormant++;
                default -> {
                }
            }
        }
        assertTrue(running > 0 && running < map.lines().size() / 3,
                "the fixture is meant to be mostly asleep, got " + running + " running of "
                        + map.lines().size());
        assertTrue(dormant > 1, "unscheduled stock should read as dormant, not merely idle");

        int strokes = 0;
        int services = 0;
        int narrowed = 0;
        for (final RailMap.Edge e : map.edges()) {
            int slots = 0;
            int sleeping = 0;
            for (final int index : e.lines) {
                if (map.lines().get(index).tier(snap) == RailMap.Line.TIER_DORMANT) {
                    sleeping++;
                } else {
                    slots++;
                }
            }
            if (sleeping > 0) {
                slots++;
            }
            assertTrue(slots <= e.lines.size(), "collapsing must never widen a bundle");

            if (sleeping > 1) {
                narrowed++;
                assertTrue(slots < e.lines.size(),
                        "a run with " + sleeping + " sleeping services still wants "
                                + slots + " of " + e.lines.size() + " strokes");
            }
            strokes += slots;
            services += e.lines.size();
        }
        assertTrue(narrowed > 0,
                "the fixture should put two sleeping services on the same run somewhere");
        assertTrue(strokes < services,
                "sleeping services should cost fewer strokes than they do lines, got " + strokes
                        + " strokes for " + services + " services");
    }

    @Test
    void aDashOnlyEverMeansAnUnsurveyedRun() {
        final RailMap map = RailMap.build(busyNetwork(), List.of(), true, Set.of());
        assertTrue(map.lines().size() > 12,
                "the palette has to be under pressure for this to prove anything");
        for (int i = 0; i < map.lines().size(); i++) {
            assertNotEquals(0, RailMapArt.lineColor(i) & 0xFFFFFF,
                    "every service index must map to a colour");
        }

        final Set<Integer> palette = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 12; i++) {
            palette.add(RailMapArt.lineColor(i));
        }
        assertEquals(12, palette.size(), "the ramp must not repeat inside one turn of it");
        assertEquals(RailMapArt.lineColor(0), RailMapArt.lineColor(12),
                "and it wraps at twelve rather than at six");
    }

    @Test
    void distantPlatformsSharingAStemStaySeparate() {
        final RailSnapshot snap = new RailSnapshot();
        snap.stations().add(station("Iron Works 1", 0, 0));
        snap.stations().add(station("Iron Works 2", 2000, 2000));
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());

        assertEquals(2, map.nodes().size(),
                "a shared name across two kilometres is a coincidence, not a station complex");
    }

    @Test
    void savedPatternsWinOverAutomaticGrouping() {
        final RailMap map = RailMap.build(network(), List.of("Docks *"), true, Set.of());
        RailMap.Node docks = null;
        for (final RailMap.Node n : map.nodes()) {
            if (n.name.startsWith("Docks")) {
                docks = n;
            }
        }
        assertNotNull(docks);
        assertEquals(2, docks.members.size(), "the pattern should have collected both dock berths");
    }

    @Test
    void expandingAGroupRestoresItsPlatforms() {
        final RailMap folded = RailMap.build(network(), List.of(), true, Set.of());
        final RailMap opened = RailMap.build(network(), List.of(), true, Set.of("quarry platform"));
        assertTrue(opened.nodes().size() > folded.nodes().size(),
                "opening a complex must put its platforms back on the map");
    }

    @Test
    void sharedTrackCarriesEveryServiceThatUsesIt() {
        final RailMap map = RailMap.build(network(), List.of(), true, Set.of());

        int busiest = 0;
        for (final RailMap.Edge e : map.edges()) {
            busiest = Math.max(busiest, e.lines.size());
        }
        assertTrue(busiest >= 2,
                "the seeded network shares a trunk between services, so some edge carries two");

        for (final RailMap.Edge e : map.edges()) {
            for (int i = 1; i < e.lines.size(); i++) {
                assertTrue(e.lines.get(i - 1) < e.lines.get(i),
                        "line order on an edge must be stable, or the parallel lines swap places "
                                + "between frames");
            }
        }
    }

    @Test
    void multiLineStationsAreInterchanges() {
        final RailMap map = RailMap.build(network(), List.of(), true, Set.of());
        RailMap.Node central = null;
        for (final RailMap.Node n : map.nodes()) {
            if ("Central".equals(n.name)) {
                central = n;
            }
        }
        assertNotNull(central);
        assertTrue(central.isInterchange(), "Central is served by more than one line");
    }

    @Test
    void rosterSignatureIgnoresMovementAndCatchesFleetChanges() {
        final RailSnapshot snap = network();
        final int before = RailMap.rosterSignature(snap);

        snap.trains().get(0).x += 64.0;
        snap.trains().get(0).speed = 0.9f;
        assertEquals(before, RailMap.rosterSignature(snap),
                "a train moving must not trigger a refold");

        final RailSnapshot.TrainState added = train("New Loco", 0.0f, 0, 0);
        added.stops.add("Central");
        snap.trains().add(added);
        assertFalse(before == RailMap.rosterSignature(snap),
                "a new train must trigger a refold or it would never appear");
    }

    @Test
    void rosterSignatureCatchesAStationRenamedInPlace() {
        final RailSnapshot snap = network();
        final int before = RailMap.rosterSignature(snap);

        snap.stations().get(0).name = "Grand Central";
        assertFalse(before == RailMap.rosterSignature(snap),
                "renaming a platform must trigger a refold or the old name never clears");
    }

    @Test
    void rosterSignatureCatchesAssemblingToggle() {
        final RailSnapshot snap = network();
        final int before = RailMap.rosterSignature(snap);

        snap.stations().get(0).assembling = !snap.stations().get(0).assembling;
        assertFalse(before == RailMap.rosterSignature(snap),
                "an assembling toggle must trigger a refold");
    }

    @Test
    void refreshCarriesLivePlatformStateWithoutRefolding() {
        final RailSnapshot first = network();
        final RailMap map = RailMap.build(first, List.of(), true, Set.of());
        final int nodes = map.nodes().size();

        final RailSnapshot second = network();
        final RailSnapshot.Station central = second.stations().get(0);
        final UUID standing = second.trains().get(0).id;
        central.presentTrain = standing;

        map.refresh(second);
        assertEquals(nodes, map.nodes().size(), "a refresh must not change the folding");

        RailMap.Node found = null;
        for (final RailMap.Node n : map.nodes()) {
            if ("Central".equals(n.name)) {
                found = n;
            }
        }
        assertNotNull(found);
        assertEquals(standing, found.presentTrain(),
                "the refreshed map must see the newly occupied platform");
    }

    @Test
    void schematicRoutesAreRestrictedToEightDirections() {
        final float[][] buffer = new float[1][];
        final float[][] cases = {
                {0, 0, 100, 30}, {0, 0, 30, 100}, {0, 0, -80, 40}, {0, 0, 25, -95},
                {0, 0, 50, 50}, {0, 0, 100, 0}, {0, 0, 0, 100}, {0, 0, -70, -20}};

        for (final float[] c : cases) {
            final int points = MapLayout.octilinear(c[0], c[1], c[2], c[3], buffer);
            for (int i = 0; i < points - 1; i++) {
                final float dx = buffer[0][(i + 1) * 2] - buffer[0][i * 2];
                final float dy = buffer[0][(i + 1) * 2 + 1] - buffer[0][i * 2 + 1];
                if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) {
                    continue;
                }
                final boolean axis = Math.abs(dx) < 0.01f || Math.abs(dy) < 0.01f;
                final boolean diagonal = Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.01f;
                assertTrue(axis || diagonal,
                        "segment " + i + " of " + c[2] + "," + c[3] + " ran at " + dx + "," + dy);
            }

            assertEquals(c[2], buffer[0][(points - 1) * 2], 0.01f);
            assertEquals(c[3], buffer[0][(points - 1) * 2 + 1], 0.01f);
        }
    }

    @Test
    void geographicModeNeverInventsADogleg() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.Station a = station("Alpha", 0, 0);
        final RailSnapshot.Station b = station("Beta", 400, 260);
        snap.stations().addAll(List.of(a, b));

        snap.links().add(link(a, b, 500.0, new float[0]));

        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final MapLayout layout = new MapLayout(map);
        final float[][] buffer = new float[1][];
        final RailMap.Edge edge = map.edges().get(0);

        final int points = layout.edgePath(edge, MapLayout.Mode.GEOGRAPHIC,
                MapLayout.Mode.GEOGRAPHIC, 1.0f, buffer);
        assertEquals(2, points, "an untraced run on the geographic map is a straight line");
        for (int i = 0; i < points - 1; i++) {
            final float dx = buffer[0][(i + 1) * 2] - buffer[0][i * 2];
            final float dy = buffer[0][(i + 1) * 2 + 1] - buffer[0][i * 2 + 1];
            final boolean octilinear = Math.abs(dx) < 0.01f || Math.abs(dy) < 0.01f
                    || Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.01f;
            assertFalse(octilinear,
                    "the geographic view must not snap a real connection onto eight bearings");
        }

        assertTrue(layout.edgePath(edge, MapLayout.Mode.SCHEMATIC, MapLayout.Mode.SCHEMATIC,
                1.0f, buffer) > 2, "the schematic still routes octilinearly");
    }

    @Test
    void movingTrainsRideTheDrawnLine() {
        for (final MapLayout.Mode mode : new MapLayout.Mode[]{
                MapLayout.Mode.GEOGRAPHIC, MapLayout.Mode.SCHEMATIC}) {
            final RailSnapshot snap = network();

            final RailSnapshot.TrainState moving = snap.trains().get(0);
            moving.currentStation = null;
            moving.positionValid = false;
            moving.currentEntry = 1;

            final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
            final MapLayout layout = new MapLayout(map);
            final MapView view = new MapView();
            view.setViewport(new Rect(0, 0, 400, 400));
            view.setMode(mode);
            view.fit(map, layout, true);
            for (int i = 0; i < 240; i++) {
                view.update(1.0f / 60.0f, map, layout);
            }

            final MapView.Motion m = view.motion(moving.id);
            assertNotNull(m, "a train with a destination has to be placed somewhere");

            final int from = map.originNodeOf(moving);
            final int to = map.nodeOfStation(moving.destinationStation);
            assertTrue(from >= 0 && to >= 0 && from != to,
                    "the origin has to be recoverable from the schedule, or the leg has no length");

            final RailMap.Edge edge = map.edgeBetween(from, to);
            assertNotNull(edge, "the seeded network joins those two stops");
            final float[][] buffer = new float[1][];
            final int points = layout.edgePath(edge, mode, mode, 1.0f, buffer);

            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < points - 1; i++) {
                nearest = Math.min(nearest, distanceToSegment(m.x, m.y,
                        buffer[0][i * 2], buffer[0][i * 2 + 1],
                        buffer[0][(i + 1) * 2], buffer[0][(i + 1) * 2 + 1]));
            }
            assertTrue(nearest < 2.0, mode + ": the train is " + Math.round(nearest)
                    + " units off the line it is drawn as running on");

            final double toEnd = Math.hypot(m.x - layout.x(to, mode), m.y - layout.y(to, mode));
            assertTrue(toEnd > 1.0, mode + ": a train mid leg must not sit on its destination");
        }
    }

    private static double distanceToSegment(float px, float py, float x0, float y0, float x1,
                                            float y1) {
        final double dx = x1 - x0;
        final double dy = y1 - y0;
        final double lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1.0e-6) {
            return Math.hypot(px - x0, py - y0);
        }
        final double t = Math.max(0.0, Math.min(1.0,
                ((px - x0) * dx + (py - y0) * dy) / lengthSq));
        return Math.hypot(px - (x0 + dx * t), py - (y0 + dy * t));
    }

    @Test
    void pinnedStopsStayWhereTheyWerePut() {
        final RailMap map = RailMap.build(network(), List.of(), true, Set.of());
        final MapLayout layout = new MapLayout(map);

        int moved = -1;
        for (int i = 0; i < map.nodes().size(); i++) {
            if ("Central".equals(map.nodes().get(i).name)) {
                moved = i;
            }
        }
        assertTrue(moved >= 0);
        final float geoX = layout.x(moved, MapLayout.Mode.GEOGRAPHIC);
        layout.pin(moved, 1234.0f, -567.0f);

        assertEquals(1234.0f, layout.x(moved, MapLayout.Mode.SCHEMATIC), 0.01f);
        assertEquals(-567.0f, layout.y(moved, MapLayout.Mode.SCHEMATIC), 0.01f);
        assertEquals(geoX, layout.x(moved, MapLayout.Mode.GEOGRAPHIC), 0.01f,
                "arranging the diagram must never move a station on the true coordinate view");

        final RailMap refolded = RailMap.build(network(), List.of(), true, Set.of());
        final MapLayout after = new MapLayout(refolded);
        after.restorePins(layout.pins());
        int again = -1;
        for (int i = 0; i < refolded.nodes().size(); i++) {
            if ("Central".equals(refolded.nodes().get(i).name)) {
                again = i;
            }
        }
        assertEquals(1234.0f, after.x(again, MapLayout.Mode.SCHEMATIC), 0.01f);

        after.clearPins();
        assertFalse(after.anyPinned(), "resetting must actually let the layout have its own way back");
    }

    @Test
    void trackGeometrySurvivesTheWire() {
        final RailSnapshot snap = network();
        final RailSnapshot back = RailSnapshot.read(snap.write());

        assertEquals(snap.tracks().size(), back.tracks().size(),
                "every run of rail has to arrive");
        for (int i = 0; i < snap.tracks().size(); i++) {
            final RailSnapshot.Track sent = snap.tracks().get(i);
            final RailSnapshot.Track got = back.tracks().get(i);
            assertEquals(sent.dimension, got.dimension);
            assertEquals(sent.path.length, got.path.length);
            for (int p = 0; p < sent.path.length; p++) {
                assertEquals(sent.path[p], got.path[p], 0.13f,
                        "point " + p + " of run " + i + " moved on the way across");
            }
        }
    }

    @Test
    void parallelOffsetKeepsItsDistanceThroughABend() {
        final float[] path = {0, 0, 100, 0, 100, 100};
        final float[] out = new float[6];
        MapLayout.offsetPath(path, 3, 6.0f, out);

        for (int i = 0; i < 3; i++) {
            final float dx = out[i * 2] - path[i * 2];
            final float dy = out[i * 2 + 1] - path[i * 2 + 1];
            final float moved = (float) Math.sqrt(dx * dx + dy * dy);
            assertTrue(moved > 3.0f && moved < 18.0f,
                    "point " + i + " moved " + moved + ", which is not a parallel line");
        }
    }

    @Test
    void drawsEveryModeWithoutFalling() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final int cell = 420;
        final int rows = 3;
        final int cols = 3;

        final BufferedImage sheet = new BufferedImage(
                40 + cols * (cell + 20), 64 + rows * (cell + 34), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xB8C0CC));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("Rail map. Real rail underneath, folded complexes, bundled services.", 22, 28);

        final String[] captions = {
                "Geographic, fitted", "Schematic, fitted", "Geographic, no rail layer",
                "Geographic, zoomed", "One service focused", "Schematic, arranged by hand",
                "40 services, 6 running", "The same, dimming off", "40 services, schematic"};

        for (int i = 0; i < captions.length; i++) {
            final BufferedImage panel = panel(cell, i);
            final int x = 20 + (i % cols) * (cell + 20);
            final int y = 46 + (i / cols) * (cell + 34);
            g.drawImage(panel, x, y, null);
            g.setColor(new Color(0x8A93A0));
            g.drawString(captions[i], x + 2, y + cell + 16);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "rail_map.png"));
    }

    @Test
    void aWaitForCycleDrawsAnOverlayThatOrdinaryTrafficDoesNot() {
        final CountingSurface locked = render(deadlockNetwork(true));
        final CountingSurface busy = render(deadlockNetwork(false));

        assertTrue(TrainIntel.stalls(deadlockNetwork(true)).size() == 1,
                "the fixture has to actually deadlock for this to prove anything");
        assertTrue(TrainIntel.stalls(deadlockNetwork(false)).isEmpty(),
                "and the control has to not");

        assertTrue(locked.triangles > busy.triangles + 20,
                "a deadlock draws arrows and a ring; the same held trains without one draw neither ("
                        + locked.triangles + " vs " + busy.triangles + ")");
        assertTrue(locked.textCalls > busy.textCalls, "and it names the fault");
    }

    @Test
    void framingAStallCentresOnItsTrainsAndZoomsIn() {
        final RailSnapshot snap = deadlockNetwork(true);
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final MapLayout layout = new MapLayout(map);
        final MapView view = new MapView();
        view.setViewport(new Rect(0, 0, 600, 400));
        view.fit(map, layout, true);
        for (int i = 0; i < 60; i++) {
            view.update(1.0f / 60.0f, map, layout);
        }
        final float scaleBefore = view.scale();

        final java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (final RailSnapshot.TrainState t : snap.trains()) {
            ids.add(t.id);
        }
        view.frameTrains(ids, 0.6f);
        for (int i = 0; i < 150; i++) {
            view.update(1.0f / 60.0f, map, layout);
        }

        assertEquals(0.0f, view.layoutX(300.0f), 45.0f, "the knot ends up under the middle of the map");
        assertEquals(0.0f, view.layoutY(200.0f), 45.0f);
        assertTrue(view.scale() > scaleBefore, "and the camera zooms in to read it");
    }

    @Test
    void drawsADeadlockForEyeballing() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final int size = 560;
        final RailSnapshot snap = deadlockNetwork(true);

        final RailSnapshot.TrainState wreck = train("Schotterzug", 0.0f, -150, 0);
        wreck.derailed = true;
        wreck.stops.addAll(List.of("West", "Kreuz"));
        final RailSnapshot.TrainState q1 = train("Personenzug", 0.0f, -108, 0);
        final RailSnapshot.TrainState q2 = train("Postzug", 0.0f, -74, 0);
        q1.waitingForSignal = true;
        q1.ticksWaitingForSignal = 1400;
        q1.blockedByTrain = wreck.id;
        q2.waitingForSignal = true;
        q2.ticksWaitingForSignal = 1200;
        q2.blockedByTrain = q1.id;
        snap.trains().addAll(List.of(wreck, q1, q2));

        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final MapLayout layout = new MapLayout(map);
        final MapView view = new MapView();
        final Rect plot = new Rect(0, 0, size, size);
        view.setViewport(plot);
        view.fit(map, layout, true);
        for (int i = 0; i < 90; i++) {
            view.update(1.0f / 60.0f, map, layout);
        }
        final RasterSurface surface = new RasterSurface(size, size, 0x111318);
        RailMapArt.draw(surface, plot, map, layout, view);
        PreviewImages.write(surface.finish(), "png", new File(OUT, "rail_map_deadlock.png"));
    }

    private static CountingSurface render(RailSnapshot snap) {
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final MapLayout layout = new MapLayout(map);
        final MapView view = new MapView();
        final Rect plot = new Rect(0, 0, 480, 480);
        view.setViewport(plot);
        view.fit(map, layout, true);
        for (int i = 0; i < 90; i++) {
            view.update(1.0f / 60.0f, map, layout);
        }
        final CountingSurface surface = new CountingSurface();
        RailMapArt.draw(surface, plot, map, layout, view);
        return surface;
    }

    private static RailSnapshot deadlockNetwork(boolean cyclic) {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 5000L;
        snap.topologyChecksum = 55;

        final RailSnapshot.Station hub = station("Kreuz", 0, 0);
        final RailSnapshot.Station n = station("Nord", 0, -160);
        final RailSnapshot.Station e = station("Ost", 160, 0);
        final RailSnapshot.Station w = station("West", -160, 0);
        snap.stations().addAll(List.of(hub, n, e, w));
        snap.links().add(link(hub, n, 160.0, new float[]{0, 0, 0, -160}));
        snap.links().add(link(hub, e, 160.0, new float[]{0, 0, 160, 0}));
        snap.links().add(link(hub, w, 160.0, new float[]{0, 0, -160, 0}));

        final RailSnapshot.TrainState a = train("Kohlezug", 0.0f, -14, -40);
        final RailSnapshot.TrainState b = train("Erzzug", 0.0f, 40, 12);
        final RailSnapshot.TrainState c = train("Holzzug", 0.0f, -40, 10);
        for (final RailSnapshot.TrainState t : List.of(a, b, c)) {
            t.waitingForSignal = true;
            t.ticksWaitingForSignal = 1000;
            t.stops.addAll(List.of("Kreuz", "Nord", "Ost", "West"));
            t.currentEntry = 1;
            snap.trains().add(t);
        }
        if (cyclic) {
            a.blockedByTrain = b.id;
            b.blockedByTrain = c.id;
            c.blockedByTrain = a.id;
        }
        return snap;
    }

    private BufferedImage panel(int size, int variant) {
        final RailSnapshot snap = variant >= 6 ? busyNetwork() : network();
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final MapLayout layout = new MapLayout(map);
        final MapView view = new MapView();
        final Rect plot = new Rect(0, 0, size, size);

        view.setViewport(plot);

        view.setPlayer(-90.0f, 110.0f, -135.0f);
        if (variant == 1 || variant == 5 || variant == 8) {
            view.setMode(MapLayout.Mode.SCHEMATIC);
        }
        if (variant == 7) {
            view.toggleDimIdle();
        }
        if (variant == 2) {
            view.toggleTrack();
        }
        view.fit(map, layout, true);
        if (variant == 3) {
            view.zoomAt(size * 0.5, size * 0.5, 2.4f);
        }
        if (variant == 4) {
            view.selectLine(0);
        }
        if (variant == 5) {
            for (int i = 0; i < map.nodes().size(); i++) {
                final String name = map.nodes().get(i).name;
                if ("Foundry".equals(name)) {
                    layout.pin(i, layout.snapX(layout.x(i, MapLayout.Mode.SCHEMATIC) + 60.0f),
                            layout.snapY(layout.y(i, MapLayout.Mode.SCHEMATIC) + 40.0f));
                }
                if ("Far Outpost".equals(name)) {
                    layout.pin(i, layout.snapX(layout.x(i, MapLayout.Mode.SCHEMATIC)),
                            layout.snapY(layout.y(i, MapLayout.Mode.SCHEMATIC) - 80.0f));
                }
            }
            view.fit(map, layout, true);
        }

        for (int i = 0; i < 90; i++) {
            view.update(1.0f / 60.0f, map, layout);
        }

        final RasterSurface surface = new RasterSurface(size, size, 0x111318);
        RailMapArt.draw(surface, plot, map, layout, view);
        return surface.finish();
    }

    private static RailSnapshot busyNetwork() {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 1000L;
        snap.topologyChecksum = 91;

        final int stops = 24;
        final RailSnapshot.Station[] all = new RailSnapshot.Station[stops];
        for (int i = 0; i < stops; i++) {
            final double angle = i * (Math.PI * 2.0 / stops);
            final double radius = 220.0 + (i % 5) * 90.0 + (i == 7 ? 900.0 : 0.0);
            all[i] = station("Stop " + (i + 1), Math.cos(angle) * radius,
                    Math.sin(angle) * radius);
            snap.stations().add(all[i]);
        }
        final Set<Long> linked = new java.util.LinkedHashSet<>();
        for (int i = 0; i < stops; i++) {
            snap.links().add(link(all[i], all[(i + 1) % stops], 260.0, new float[0]));
            linked.add(pair(i, (i + 1) % stops));
        }

        for (int service = 0; service < 40; service++) {
            final boolean running = service % 6 == 0;
            final RailSnapshot.TrainState t = train("Working " + (service + 1),
                    running ? 0.7f : 0.0f, 0.0, 0.0);
            t.scheduleTitle = service % 7 == 0 ? "Shared name" : "Service " + (service + 1);

            t.hasSchedule = service % 3 != 0;

            final int start = service % stops;
            final int stride = 1 + service % 7;
            for (int s = 0; s < 4 + service % 3; s++) {
                t.stops.add(all[(start + s * stride) % stops].name);
            }

            if (service % 4 != 0) {
                for (int s = 0; s + 1 < t.stops.size(); s++) {
                    final int a = (start + s * stride) % stops;
                    final int b = (start + (s + 1) * stride) % stops;
                    if (a != b && linked.add(pair(a, b))) {
                        snap.links().add(link(all[a], all[b], 260.0 * stride, new float[0]));
                    }
                }
            }
            snap.trains().add(t);
        }
        return snap;
    }

    private static long pair(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    private static RailSnapshot network() {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 1000L;
        snap.topologyChecksum = 77;

        final RailSnapshot.Station central = station("Central", 0, 0);
        final RailSnapshot.Station q1 = station("Quarry Platform 1", 300, -180);
        final RailSnapshot.Station q2 = station("Quarry Platform 2", 316, -168);
        final RailSnapshot.Station q3 = station("Quarry Platform 3", 332, -156);
        final RailSnapshot.Station dock1 = station("Docks Berth A", -220, 260);
        final RailSnapshot.Station dock2 = station("Docks Berth B", -244, 276);
        final RailSnapshot.Station foundry = station("Foundry", 260, 300);
        final RailSnapshot.Station depot = station("North Depot", -300, -260);
        depot.assembling = true;
        final RailSnapshot.Station outpost = station("Far Outpost", 700, 120);

        snap.stations().addAll(List.of(central, q1, q2, q3, dock1, dock2, foundry, depot, outpost));

        snap.links().add(link(central, q1, 400.0, new float[]{
                0, 0, 80, -40, 160, -90, 240, -140, 300, -180}));
        snap.links().add(link(central, dock1, 380.0, new float[]{
                0, 0, -60, 70, -120, 150, -180, 210, -220, 260}));
        snap.links().add(link(central, foundry, 430.0, new float[]{
                0, 0, 90, 90, 170, 190, 230, 260, 260, 300}));
        snap.links().add(link(central, depot, 420.0, new float[0]));
        snap.links().add(link(q1, outpost, 460.0, new float[]{
                300, -180, 420, -120, 540, -20, 640, 60, 700, 120}));

        snap.tracks().add(track(0, 0, 80, -40, 160, -90, 240, -140, 300, -180));
        snap.tracks().add(track(300, -180, 316, -168, 332, -156));
        snap.tracks().add(track(0, 0, -60, 70, -120, 150, -180, 210, -220, 260));
        snap.tracks().add(track(-220, 260, -244, 276));
        snap.tracks().add(track(0, 0, 90, 90, 170, 190, 230, 260, 260, 300));
        snap.tracks().add(track(0, 0, -90, -70, -170, -140, -240, -200, -300, -260));
        snap.tracks().add(track(300, -180, 420, -120, 540, -20, 640, 60, 700, 120));

        snap.tracks().add(track(160, -90, 176, -74, 236, -124, 240, -140));
        snap.tracks().add(track(260, 300, 330, 300, 386, 274));

        final RailSnapshot.TrainState stone = train("Stone Express", 0.72f, 150, -85);
        stone.destinationStation = q1.id;
        stone.destinationName = q1.name;
        stone.currentStation = null;
        stone.distanceToDestination = 180.0;
        stone.distanceStartedAt = 400.0;
        stone.stops.addAll(List.of("Central", "Quarry Platform 1", "Foundry"));

        stone.currentEntry = 1;
        stone.scheduleTitle = "Stone circuit";
        snap.trains().add(stone);

        final RailSnapshot.TrainState ore = train("Ore Hauler", 0.55f, 240, -140);
        ore.destinationStation = q1.id;
        ore.destinationName = q1.name;
        ore.distanceToDestination = 90.0;
        ore.distanceStartedAt = 400.0;
        ore.stops.addAll(List.of("Central", "Quarry Platform 1", "Far Outpost"));
        ore.currentEntry = 1;
        ore.scheduleTitle = "Ore run";
        snap.trains().add(ore);

        final RailSnapshot.TrainState coal = train("Coal Shuttle", 0.0f, -120, 150);
        coal.waitingForSignal = true;
        coal.ticksWaitingForSignal = 900;
        coal.destinationStation = dock1.id;
        coal.destinationName = dock1.name;
        coal.distanceToDestination = 160.0;
        coal.distanceStartedAt = 380.0;
        coal.stops.addAll(List.of("Central", "Docks Berth A"));
        coal.currentEntry = 1;
        coal.scheduleTitle = "Coal run";
        snap.trains().add(coal);

        final RailSnapshot.TrainState broken = train("Scrap Train", 0.0f, 200, 220);
        broken.derailed = true;
        broken.stops.addAll(List.of("Foundry", "Central"));
        snap.trains().add(broken);

        q2.presentTrain = broken.id;
        central.imminentTrain = stone.id;

        snap.arrivals().add(arrival(stone, "Central", 640));
        snap.arrivals().add(arrival(ore, "Quarry Platform 1", 180));
        snap.arrivals().add(arrival(coal, "Docks Berth A", 2400));
        return snap;
    }

    private static RailSnapshot.Station station(String name, double x, double z) {
        final RailSnapshot.Station s = new RailSnapshot.Station();
        s.id = UUID.nameUUIDFromBytes(name.getBytes());
        s.name = name;
        s.dimension = "minecraft:overworld";
        s.x = x;
        s.z = z;
        return s;
    }

    private static RailSnapshot.Link link(RailSnapshot.Station a, RailSnapshot.Station b,
                                          double length, float[] path) {
        final RailSnapshot.Link l = new RailSnapshot.Link();
        l.from = a.id;
        l.to = b.id;
        l.length = length;
        l.path = path;
        return l;
    }

    private static RailSnapshot.Track track(float... path) {
        final RailSnapshot.Track t = new RailSnapshot.Track();
        t.dimension = "minecraft:overworld";
        t.path = path;
        return t;
    }

    private static RailSnapshot.TrainState train(String name, float speed, double x, double z) {
        final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
        t.id = UUID.nameUUIDFromBytes(name.getBytes());
        t.name = name;
        t.owner = "Player";
        t.dimension = "minecraft:overworld";
        t.speed = speed;
        t.maxSpeed = 1.0f;
        t.carriages = 4;
        t.x = x;
        t.z = z;
        t.positionValid = true;
        t.hasSchedule = true;
        t.phase = RailSnapshot.SchedulePhase.IN_TRANSIT;
        return t;
    }

    private static RailSnapshot.Arrival arrival(RailSnapshot.TrainState t, String station,
                                                int ticks) {
        final RailSnapshot.Arrival a = new RailSnapshot.Arrival();
        a.trainId = t.id;
        a.trainName = t.name;
        a.station = station;
        a.scheduleTitle = t.scheduleTitle;
        a.ticks = ticks;
        return a;
    }
}
