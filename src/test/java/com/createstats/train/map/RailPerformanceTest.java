package com.createstats.train.map;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.createstats.train.smart.RailSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailPerformanceTest {
    @BeforeEach
    void clean() {
        RailHistory.clear();
    }

    @Test
    void evenGapsCostNoExtraWait() {
        for (final float gap : new float[]{30.0f, 120.0f, 900.0f}) {
            final RailPerformance.Waits w =
                    RailPerformance.waits(new float[]{gap, gap, gap, gap});
            assertEquals(gap, w.mean(), 0.01f);
            assertEquals(0.0f, w.cv(), 0.0001f, "even gaps have no spread");
            assertEquals(gap * 0.5f, w.actual(), 0.01f,
                    "on an even service the wait really is half the gap");
            assertEquals(0.0f, w.excess(), 0.01f);
            assertEquals(RailPerformance.Grade.REGULAR, RailPerformance.grade(w.cv()));
        }
    }

    @Test
    void aConvoyCostsTheWaitItLooksLikeItCosts() {
        final RailPerformance.Waits bunched =
                RailPerformance.waits(new float[]{60.0f, 840.0f, 60.0f, 840.0f});
        assertEquals(450.0f, bunched.mean(), 0.01f);
        assertEquals(225.0f, bunched.scheduled(), 0.01f);
        assertEquals(394.0f, bunched.actual(), 0.5f, "E[h squared] over twice E[h]");
        assertEquals(169.0f, bunched.excess(), 1.0f);
        assertEquals(RailPerformance.Grade.BUNCHED, RailPerformance.grade(bunched.cv()));

        final RailPerformance.Waits even =
                RailPerformance.waits(new float[]{450.0f, 450.0f, 450.0f, 450.0f});
        assertEquals(bunched.mean(), even.mean(), 0.01f,
                "the same number of trains an hour, by construction");
        assertTrue(bunched.actual() > even.actual() + 150.0f,
                "identical frequency, and the ragged one costs over two extra minutes");
    }

    @Test
    void theActualWaitNeverBeatsTheEvenCase() {
        final float[][] cases = {
                {10.0f, 600.0f}, {300.0f, 305.0f, 295.0f}, {1.0f, 1.0f, 1.0f, 4000.0f},
                {120.0f, 60.0f, 240.0f, 90.0f, 130.0f}};
        for (final float[] gaps : cases) {
            final RailPerformance.Waits w = RailPerformance.waits(gaps);
            assertTrue(w.actual() >= w.scheduled() - 0.01f,
                    "irregularity can only ever add waiting, never remove it");
            assertTrue(w.excess() >= 0.0f);
        }
    }

    @Test
    void nothingMeasuredProducesNothingClaimed() {
        assertEquals(0.0f, RailPerformance.waits(new float[0]).actual(), 0.0001f);
        assertEquals(0.0f, RailPerformance.waits(null).excess(), 0.0001f);
        assertEquals(0.0f, RailPerformance.waits(new float[]{0.0f, 0.0f}).mean(), 0.0001f);
    }

    @Test
    void durationsReadTheWayARailwaySaysThem() {
        assertEquals("-", RailPerformance.clock(0.0f));
        assertEquals("45s", RailPerformance.clock(45.0f));
        assertEquals("2m", RailPerformance.clock(120.0f));
        assertEquals("2m 30s", RailPerformance.clock(150.0f));
        assertEquals("1h 1m", RailPerformance.clock(3660.0f));
    }

    @Test
    void trainsRoundARingAreSeenPassingTheCutPoint() {
        final Ring ring = new Ring(2);
        ring.run(2400);

        final RailPerformance.Report report = RailPerformance.of(ring.map, ring.line());
        assertNotNull(report);
        assertEquals(2, report.trains());
        assertTrue(report.crossings() >= 2,
                "two trains going round for two minutes should pass one place, got "
                        + report.crossings());
        assertTrue(report.movingShare() > 0.0f, "the state split has to add up to something");
        assertTrue(report.movingShare() + report.dwellShare() + report.heldShare()
                + report.faultShare() <= 1.0001f, "the shares are shares");
    }

    @Test
    void theWrapAtTheEndOfALapIsNotAPassing() {
        final Ring ring = new Ring(1);
        ring.run(6000);

        final RailPerformance.Report report = RailPerformance.of(ring.map, ring.line());
        assertNotNull(report);
        for (final float gap : report.headways()) {
            assertTrue(gap > 1.0f,
                    "a gap of " + gap + "s means the wrap was counted as a train going past");
        }
        if (report.cycleTime() > 0.0f) {
            assertTrue(report.cycleTime() > 5.0f, "a lap of a whole ring takes longer than that");
        }
    }

    @Test
    void anUnrecordedServiceIsUnknownRatherThanGood() {
        final Ring ring = new Ring(2);
        final RailPerformance.Report report = RailPerformance.of(ring.map, ring.line());
        assertNotNull(report);
        assertEquals(RailPerformance.Grade.UNKNOWN, report.grade());
        assertEquals(0.0f, report.excessWait(), 0.0001f);
        assertTrue(RailPerformance.describe(report).contains("not enough"));
    }

    private static final class Ring {
        private static final int STEP = 10;
        private static final int LEG_TICKS = 200;

        final RailSnapshot snapshot = new RailSnapshot();
        final List<RailSnapshot.Station> stops;
        RailMap map;
        private long tick = 10_000L;

        Ring(int trains) {
            this.stops = List.of(station("Alpha", 0, 0), station("Bravo", 400, 0),
                    station("Charlie", 400, 400), station("Delta", 0, 400));
            this.snapshot.stations().addAll(this.stops);
            for (int i = 0; i < this.stops.size(); i++) {
                this.snapshot.links().add(
                        link(this.stops.get(i), this.stops.get((i + 1) % this.stops.size())));
            }
            for (int i = 0; i < trains; i++) {
                final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
                t.id = UUID.nameUUIDFromBytes(("ring" + i).getBytes());
                t.name = "Ring " + (i + 1);
                t.dimension = "minecraft:overworld";
                t.hasSchedule = true;
                t.speed = 0.5f;
                t.maxSpeed = 1.0f;
                t.carriages = 2;
                t.positionValid = true;
                t.phase = RailSnapshot.SchedulePhase.IN_TRANSIT;
                for (final RailSnapshot.Station s : this.stops) {
                    t.stops.add(s.name);
                }

                t.currentEntry = (i * this.stops.size()) / trains;
                setLeg(t, t.currentEntry, 0.0f);
                this.snapshot.trains().add(t);
            }
            this.snapshot.serverTick = this.tick;
            this.map = RailMap.build(this.snapshot, List.of(), true, Set.of());
        }

        int line() {
            return this.map.lines().isEmpty() ? -1 : 0;
        }

        void run(int ticks) {
            for (int elapsed = 0; elapsed < ticks; elapsed += STEP) {
                this.tick += STEP;
                this.snapshot.serverTick = this.tick;
                for (final RailSnapshot.TrainState t : this.snapshot.trains()) {
                    advance(t);
                }
                this.map.refresh(this.snapshot);
                RailHistory.sample(this.snapshot, this.map);
            }
        }

        private void advance(RailSnapshot.TrainState t) {
            t.distanceToDestination -= t.distanceStartedAt * (STEP / (float) LEG_TICKS);
            if (t.distanceToDestination > 0.0) {
                t.currentStation = null;
                return;
            }
            t.currentEntry = (t.currentEntry + 1) % this.stops.size();
            setLeg(t, t.currentEntry, 0.0f);
        }

        private void setLeg(RailSnapshot.TrainState t, int entry, float progress) {
            final RailSnapshot.Station to = this.stops.get(entry);
            t.destinationStation = to.id;
            t.destinationName = to.name;
            t.currentStation = null;
            t.distanceStartedAt = 400.0;
            t.distanceToDestination = 400.0 * (1.0f - progress);
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

        private static RailSnapshot.Link link(RailSnapshot.Station a, RailSnapshot.Station b) {
            final RailSnapshot.Link l = new RailSnapshot.Link();
            l.from = a.id;
            l.to = b.id;
            l.length = 400.0;
            l.path = new float[0];
            return l;
        }
    }
}
