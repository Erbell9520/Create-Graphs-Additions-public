package com.createstats.train.map;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainGraphArtTest {
    private static final File OUT = new File("fixture_preview");

    private static final int STEP_TICKS = 10;

    @BeforeEach
    void clean() {
        RailHistory.clear();
    }

    @Test
    void chainageFollowsRealTrackLength() {
        final RailMap map = RailMap.build(network(), List.of(), true, Set.of());
        final int ring = lineNamed(map, "Ring");
        final LineChainage chainage = LineChainage.of(map, ring);

        assertFalse(chainage.isEmpty());
        assertEquals(5, chainage.stopCount(),
                "a four stop circular service is drawn as five stops, with the first repeated");
        assertEquals(0.0f, chainage.chain[0], 0.01f);
        for (int i = 1; i < chainage.stopCount(); i++) {
            assertTrue(chainage.chain[i] > chainage.chain[i - 1],
                    "chainage must never go backwards along a route");
        }

        assertEquals(1730.0f, chainage.total, 1.0f);
    }

    @Test
    void aMovingTrainSitsWhereItsRemainingDistanceSaysItDoes() {
        final RailSnapshot snap = network();
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final LineChainage chainage = LineChainage.of(map, lineNamed(map, "Ring"));

        final RailSnapshot.TrainState t = snap.trains().get(0);
        t.currentStation = null;
        t.currentEntry = 1;
        t.destinationStation = named(snap, "Quarry").id;
        t.distanceStartedAt = 400.0;
        t.distanceToDestination = 100.0;

        final float at = chainage.chainageOf(t);
        assertFalse(Float.isNaN(at), "a train on the route has to land somewhere on it");

        final int leg = chainage.legIndexOf(
                map.nodeOfStation(named(snap, "Central").id),
                map.nodeOfStation(named(snap, "Quarry").id));
        assertTrue(leg >= 0, "Central and the quarry are joined on this route");
        final float fromCentral = Math.abs(at - chainage.chain[
                chainage.stops[leg] == map.nodeOfStation(named(snap, "Central").id)
                        ? leg : leg + 1]);
        assertEquals(300.0f, fromCentral, 2.0f,
                "three quarters along a four hundred block leg is three hundred blocks in");
    }

    @Test
    void aTrainOnAnotherRouteIsNotPlacedOnThisOne() {
        final RailSnapshot snap = network();
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        final LineChainage ring = LineChainage.of(map, lineNamed(map, "Ring"));

        RailSnapshot.TrainState shuttle = null;
        for (final RailSnapshot.TrainState t : snap.trains()) {
            if (t.name.startsWith("Outpost")) {
                shuttle = t;
            }
        }
        assertNotNull(shuttle);
        shuttle.currentStation = null;
        shuttle.currentEntry = 1;
        shuttle.destinationStation = named(snap, "Far Outpost").id;
        shuttle.distanceStartedAt = 460.0;
        shuttle.distanceToDestination = 200.0;

        assertTrue(Float.isNaN(ring.chainageOf(shuttle)),
                "a leg that is not part of this route must report no position rather than be "
                        + "pinned to the nearest stop");
    }

    @Test
    void samplingIsRateLimitedAndOrdered() {
        final Sim sim = new Sim(network());
        for (int i = 0; i < 200; i++) {
            sim.step();
        }
        final RailHistory.Series series = RailHistory.of(sim.trains.get(0).id);
        assertNotNull(series, "a train working a scheduled route has to be recorded");
        assertTrue(series.size() > 150, "200 steps at one sample each should have filled the ring");

        for (int i = 1; i < series.size(); i++) {
            assertTrue(series.time(i) >= series.time(i - 1),
                    "samples must come back oldest first, or every string is drawn as a scribble");
        }

        final int before = series.size();
        RailHistory.sample(sim.snapshot, sim.map);
        assertEquals(before, series.size());
    }

    @Test
    void changingARouteStartsTheRecordingAgain() {
        final Sim sim = new Sim(network());
        for (int i = 0; i < 60; i++) {
            sim.step();
        }
        final RailSnapshot.TrainState t = sim.trains.get(0);
        assertTrue(RailHistory.of(t.id).size() > 10);

        t.stops.clear();
        t.stops.add("Central");
        t.stops.add("Foundry");
        sim.rebuild();
        sim.step();

        assertTrue(RailHistory.of(t.id).size() <= 2,
                "a route change must drop the old samples rather than rescale them");
    }

    @Test
    void aCircularServiceWrapsAtTheEndOfTheStrip() {
        final Sim sim = new Sim(network());

        for (int i = 0; i < 900; i++) {
            sim.step();
        }
        final RailHistory.Series series = RailHistory.of(sim.trains.get(0).id);
        final LineChainage chainage =
                RailHistory.chainage(sim.map, lineNamed(sim.map, "Ring"));

        int wraps = 0;
        int forwards = 0;
        int backwards = 0;
        for (int i = 1; i < series.size(); i++) {
            final float delta = series.chain(i) - series.chain(i - 1);
            if (Math.abs(delta) > chainage.total * 0.55f) {
                wraps++;
            } else if (delta > 1.0f) {
                forwards++;
            } else if (delta < -1.0f) {
                backwards++;
            }
        }
        assertTrue(wraps > 0, "a train that got round the circuit has to have wrapped");
        assertTrue(forwards == 0 || backwards == 0,
                "chainage must run one way round the strip, not drift both ways: " + forwards
                        + " forward steps and " + backwards + " backward");
    }

    @Test
    void aRouteWorkedBothWaysIsOneLine() {
        final RailSnapshot snap = network();
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());

        RailSnapshot.TrainState up = null;
        RailSnapshot.TrainState down = null;
        for (final RailSnapshot.TrainState t : snap.trains()) {
            if ("Stone Express".equals(t.name)) {
                up = t;
            }
            if ("Down Freight".equals(t.name)) {
                down = t;
            }
        }
        assertNotNull(up);
        assertNotNull(down);
        assertEquals(map.lineIndexOf(up.id), map.lineIndexOf(down.id),
                "the same circuit worked in reverse is the same line");
    }

    @Test
    void oppositeWorkingsRunOppositeWaysAlongTheStrip() {
        final Sim sim = new Sim(network());
        for (int i = 0; i < 400; i++) {
            sim.step();
        }
        RailHistory.Series up = null;
        RailHistory.Series down = null;
        for (final RailSnapshot.TrainState t : sim.trains) {
            if ("Stone Express".equals(t.name)) {
                up = RailHistory.of(t.id);
            }
            if ("Down Freight".equals(t.name)) {
                down = RailHistory.of(t.id);
            }
        }
        assertNotNull(up);
        assertNotNull(down);
        assertTrue(trend(up) * trend(down) < 0.0f,
                "the two workings have to run opposite ways along the strip, or they could never "
                        + "be drawn meeting: " + trend(up) + " against " + trend(down));
    }

    private static float trend(RailHistory.Series series) {
        float sum = 0.0f;
        for (int i = 1; i < series.size(); i++) {
            final float delta = series.chain(i) - series.chain(i - 1);
            if (Math.abs(delta) < 400.0f) {
                sum += delta;
            }
        }
        return sum;
    }

    @Test
    void aForecastContinuesInTheDirectionOfTravel() {
        final Sim sim = new Sim(network());
        for (int i = 0; i < 300; i++) {
            sim.step();
        }
        final RailMap map = sim.map;
        final int ring = lineNamed(map, "Ring");
        final LineChainage chainage = RailHistory.chainage(map, ring);

        int checked = 0;
        for (final RailSnapshot.TrainState t : sim.trains) {
            if (map.lineIndexOf(t.id) != ring || t.currentStation != null) {
                continue;
            }
            final RailHistory.Series series = RailHistory.of(t.id);
            if (series == null || series.size() < 4) {
                continue;
            }
            final float travelling = series.chain(series.size() - 1) - series.chain(series.size() - 3);
            if (Math.abs(travelling) < 1.0f || Math.abs(travelling) > chainage.total * 0.55f) {
                continue;
            }
            final int at = chainage.startIndex(t);
            final int destination = map.nodeOfStation(t.destinationStation);
            final int next = chainage.stepTo(at, destination);
            assertTrue(next >= 0, t.name + ": its next call has to be the neighbouring stop");

            final float forecast = chainage.chainAt(next) - chainage.chainageOf(t);
            assertTrue(travelling * forecast > 0.0f,
                    t.name + ": the forecast runs " + (forecast > 0 ? "up" : "down")
                            + " the strip while the train is running "
                            + (travelling > 0 ? "up" : "down"));
            checked++;
        }
        assertTrue(checked >= 2, "the fixture should have had trains in motion to check");
    }

    @Test
    void drawsTheDiagramInEveryReading() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final int cellW = 560;
        final int cellH = 380;
        final int cols = 2;
        final int rows = 2;

        final BufferedImage sheet = new BufferedImage(
                40 + cols * (cellW + 20), 70 + rows * (cellH + 34), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xB8C0CC));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("Train diagram. Solid is recorded, dashed is Create's own forecast, "
                + "diamonds are meets.", 22, 28);

        final String[] captions = {
                "5 minutes, distance to scale", "Even stop spacing",
                "One train followed", "2 minute window, zoomed in"};

        for (int i = 0; i < captions.length; i++) {
            final BufferedImage panel = panel(cellW, cellH, i);
            final int x = 20 + (i % cols) * (cellW + 20);
            final int y = 50 + (i / cols) * (cellH + 34);
            g.drawImage(panel, x, y, null);
            g.setColor(new Color(0x8A93A0));
            g.drawString(captions[i], x + 2, y + cellH + 16);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "train_diagram.png"));
    }

    private BufferedImage panel(int w, int h, int variant) {
        RailHistory.clear();
        final Sim sim = new Sim(network());
        for (int i = 0; i < 1800; i++) {
            sim.step();
        }
        final TrainGraphView view = new TrainGraphView();
        view.ensureLine(sim.map);
        view.setLine(lineNamed(sim.map, "Ring"));

        switch (variant) {
            case 1 -> view.toggleSpacing();
            case 2 -> view.setSelectedTrain(sim.trains.get(1).id);
            case 3 -> view.setSpanIndex(1);
            default -> {
            }
        }
        final Rect plot = new Rect(6, 6, w - 12, h - 12);
        final RasterSurface surface = new RasterSurface(w, h, 0x111318);
        final TrainGraphArt.Frame frame = TrainGraphArt.draw(surface, plot, sim.map, view);
        assertFalse(frame.plot().isEmpty(), "the diagram should have resolved a plot");
        assertTrue(frame.plot().right() <= plot.right() + 0.01f,
                "the plot must stay inside the area it was given");
        return surface.finish();
    }

    @Test
    void drawsEveryShapeOfRake() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final String[] captions = {
                "Loaded mixed freight, driver in the second wagon",
                "Single wagon shunter",
                "Eighteen wagons, too many to draw separately",
                "Assembled but empty",
                "Running backwards, one wagon blocked",
                "Not loaded: only the carriage count is known"};
        final RailSnapshot.TrainState[] rakes = {
                rake(6, true, 0.8f, false, false),
                rake(1, true, 0.5f, false, false),
                rake(18, true, 0.6f, false, false),
                rake(5, false, 0.0f, false, false),
                rake(4, true, 0.45f, true, true),
                unloadedRake(7)};

        final int w = 420;
        final int h = 46;
        final BufferedImage sheet = new BufferedImage(w + 40, 40 + rakes.length * (h + 26),
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        for (int i = 0; i < rakes.length; i++) {
            final RasterSurface surface = new RasterSurface(w, h, 0x111318);
            TrainConsistArt.draw(surface, new Rect(8, 6, w - 16, h - 12), rakes[i],
                    TrainGraphArt.trainColor(rakes[i], rakes[i].id));
            final int y = 26 + i * (h + 26);
            g.drawImage(surface.finish(), 20, y, null);
            g.setColor(new Color(0x8A93A0));
            g.drawString(captions[i], 22, y + h + 15);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "train_consist.png"));
    }

    private static RailSnapshot.TrainState rake(int cars, boolean loaded, float fill,
                                                boolean backwards, boolean blocked) {
        final RailSnapshot.TrainState t = train("Rake " + cars + (backwards ? "R" : ""));
        t.carriages = cars;
        t.backwards = backwards;
        t.mapColor = cars % 6;
        for (int i = 0; i < cars; i++) {
            final RailSnapshot.Car car = new RailSnapshot.Car();

            car.spacing = 2 + (i % 3) * 2;
            car.twoBogeys = car.spacing > 2;
            car.conductor = i == 1;
            car.blocked = blocked && i == cars - 1;
            if (loaded) {
                car.slots = 27;

                car.usedSlots = i == cars / 2 ? 0 : Math.round(27 * fill);
                car.items = car.usedSlots * 64;
            }
            t.cars.add(car);
            t.itemCount += car.items;
            t.length += car.spacing + 1;
        }
        t.topItem = "Iron Ore";
        return t;
    }

    private static RailSnapshot.TrainState unloadedRake(int cars) {
        final RailSnapshot.TrainState t = train("Unloaded " + cars);
        t.carriages = cars;
        return t;
    }

    private static final class Sim {
        final RailSnapshot snapshot;
        final List<RailSnapshot.TrainState> trains;
        RailMap map;
        long tick;

        private final Map<UUID, Integer> dwell = new HashMap<>();

        Sim(RailSnapshot snapshot) {
            this.snapshot = snapshot;
            this.trains = snapshot.trains();
            this.tick = snapshot.serverTick;
            rebuild();
            for (final RailSnapshot.TrainState t : this.trains) {
                start(t);
            }
        }

        void rebuild() {
            this.map = RailMap.build(this.snapshot, List.of(), true, Set.of());
        }

        void step() {
            this.tick += STEP_TICKS;
            this.snapshot.serverTick = this.tick;
            for (final RailSnapshot.TrainState t : this.trains) {
                advance(t);
            }
            predict();
            this.map.refresh(this.snapshot);
            RailHistory.sample(this.snapshot, this.map);
        }

        private void advance(RailSnapshot.TrainState t) {
            final Integer waiting = this.dwell.get(t.id);
            if (waiting != null && waiting > 0) {
                this.dwell.put(t.id, waiting - 1);
                t.speed = 0.0f;
                t.phase = RailSnapshot.SchedulePhase.POST_TRANSIT;
                return;
            }
            if (t.currentStation != null) {
                t.currentEntry = (t.currentEntry + 1) % Math.max(1, t.stops.size());
                t.currentStation = null;
                start(t);
                return;
            }
            t.phase = RailSnapshot.SchedulePhase.IN_TRANSIT;
            t.ticksInTransit += STEP_TICKS;
            t.distanceToDestination -= t.speed * STEP_TICKS;
            final RailSnapshot.Station destination = this.snapshot.station(t.destinationStation);
            if (destination != null) {
                final float f = t.legProgress();
                final RailSnapshot.Station from = this.snapshot.station(originOf(t));
                if (from != null) {
                    t.x = from.x + (destination.x - from.x) * f;
                    t.z = from.z + (destination.z - from.z) * f;
                }
            }
            if (t.distanceToDestination <= 0.0) {
                t.distanceToDestination = 0.0;
                t.currentStation = t.destinationStation;
                t.speed = 0.0f;
                this.dwell.put(t.id, 6 + (Math.abs(t.name.hashCode()) % 5));
            }
        }

        private UUID originOf(RailSnapshot.TrainState t) {
            if (t.stops.isEmpty()) {
                return null;
            }
            final int previous = Math.floorMod(t.currentEntry - 1, t.stops.size());
            final RailSnapshot.Station s =
                    this.snapshot.resolveFilter(t.stops.get(previous), t.dimension);
            return s == null ? null : s.id;
        }

        private void start(RailSnapshot.TrainState t) {
            if (t.stops.isEmpty()) {
                return;
            }
            final RailSnapshot.Station next =
                    this.snapshot.resolveFilter(t.stops.get(t.currentEntry), t.dimension);
            if (next == null) {
                return;
            }
            final RailSnapshot.Station from = this.snapshot.station(originOf(t));
            double length = 400.0;
            if (from != null) {
                for (final RailSnapshot.Link l : this.snapshot.links()) {
                    if ((l.from.equals(from.id) && l.to.equals(next.id))
                            || (l.to.equals(from.id) && l.from.equals(next.id))) {
                        length = l.length;
                    }
                }
            }
            t.destinationStation = next.id;
            t.destinationName = next.name;
            t.distanceStartedAt = length;
            t.distanceToDestination = length;
            t.ticksInTransit = 0;
            t.legPlannedTicks = (int) (length / Math.max(0.05f, nominalSpeed(t)));
            t.speed = nominalSpeed(t);
        }

        private static float nominalSpeed(RailSnapshot.TrainState t) {
            return 0.55f + (Math.abs(t.name.hashCode()) % 5) * 0.09f;
        }

        private void predict() {
            this.snapshot.arrivals().clear();
            for (final RailSnapshot.TrainState t : this.trains) {
                if (t.stops.isEmpty()) {
                    continue;
                }
                double remaining = t.distanceToDestination;
                int entry = t.currentEntry;
                int ticks = (int) (remaining / Math.max(0.05f, nominalSpeed(t)));
                for (int hop = 0; hop < 6; hop++) {
                    final RailSnapshot.Arrival a = new RailSnapshot.Arrival();
                    a.trainId = t.id;
                    a.trainName = t.name;
                    a.station = t.stops.get(entry);
                    a.scheduleTitle = t.scheduleTitle;
                    a.ticks = ticks;
                    this.snapshot.arrivals().add(a);

                    final int next = (entry + 1) % t.stops.size();
                    final RailSnapshot.Station from =
                            this.snapshot.resolveFilter(t.stops.get(entry), t.dimension);
                    final RailSnapshot.Station to =
                            this.snapshot.resolveFilter(t.stops.get(next), t.dimension);
                    double length = 400.0;
                    if (from != null && to != null) {
                        for (final RailSnapshot.Link l : this.snapshot.links()) {
                            if ((l.from.equals(from.id) && l.to.equals(to.id))
                                    || (l.to.equals(from.id) && l.from.equals(to.id))) {
                                length = l.length;
                            }
                        }
                    }
                    ticks += 120 + (int) (length / Math.max(0.05f, nominalSpeed(t)));
                    entry = next;
                }
            }
        }
    }

    private static int lineNamed(RailMap map, String contains) {
        for (int i = 0; i < map.lines().size(); i++) {
            final RailMap.Line line = map.lines().get(i);
            if (line.title.contains(contains)) {
                return i;
            }
        }
        return map.lines().isEmpty() ? -1 : 0;
    }

    private static RailSnapshot.Station named(RailSnapshot snap, String prefix) {
        for (final RailSnapshot.Station s : snap.stations()) {
            if (s.name.startsWith(prefix)) {
                return s;
            }
        }
        return null;
    }

    private static RailSnapshot network() {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 20_000L;
        snap.topologyChecksum = 4242;

        final RailSnapshot.Station central = station("Central", 0, 0);
        final RailSnapshot.Station quarry = station("Quarry", 300, -180);
        final RailSnapshot.Station foundry = station("Foundry", 420, 260);
        final RailSnapshot.Station docks = station("Docks", -220, 260);
        final RailSnapshot.Station outpost = station("Far Outpost", 900, -420);
        snap.stations().addAll(List.of(central, quarry, foundry, docks, outpost));

        snap.links().add(link(central, quarry, 400.0));
        snap.links().add(link(quarry, foundry, 500.0));
        snap.links().add(link(foundry, docks, 450.0));
        snap.links().add(link(docks, central, 380.0));
        snap.links().add(link(quarry, outpost, 460.0));

        final List<String> ring = List.of("Central", "Quarry", "Foundry", "Docks");
        final List<String> names = List.of("Stone Express", "Ore Hauler", "Coal Shuttle");
        for (int i = 0; i < names.size(); i++) {
            final RailSnapshot.TrainState t = train(names.get(i));
            t.stops.addAll(ring);
            t.scheduleTitle = "Ring";

            t.mapColor = i;
            snap.trains().add(t);
        }

        snap.trains().get(1).currentEntry = 1;
        snap.trains().get(2).currentEntry = 2;

        final RailSnapshot.TrainState down = train("Down Freight");
        down.stops.addAll(List.of("Docks", "Foundry", "Quarry", "Central"));
        down.scheduleTitle = "Ring";
        down.currentEntry = 1;
        down.mapColor = 4;
        snap.trains().add(down);

        final RailSnapshot.TrainState shuttle = train("Outpost Shuttle");
        shuttle.stops.addAll(List.of("Quarry", "Far Outpost"));
        shuttle.scheduleTitle = "Outpost";
        snap.trains().add(shuttle);
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
                                          double length) {
        final RailSnapshot.Link l = new RailSnapshot.Link();
        l.from = a.id;
        l.to = b.id;
        l.length = length;

        l.path = new float[]{(float) a.x, (float) a.z, (float) b.x, (float) b.z};
        return l;
    }

    private static RailSnapshot.TrainState train(String name) {
        final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
        t.id = UUID.nameUUIDFromBytes(name.getBytes());
        t.name = name;
        t.owner = "Player";
        t.dimension = "minecraft:overworld";
        t.maxSpeed = 1.0f;
        t.carriages = 4;
        t.positionValid = true;
        t.hasSchedule = true;
        t.phase = RailSnapshot.SchedulePhase.IN_TRANSIT;
        t.mapColor = -1;
        return t;
    }

    private static List<RailSnapshot.TrainState> copy(List<RailSnapshot.TrainState> in) {
        return new ArrayList<>(in);
    }
}
