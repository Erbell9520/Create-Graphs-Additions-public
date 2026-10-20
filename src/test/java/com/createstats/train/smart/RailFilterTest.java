package com.createstats.train.smart;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailFilterTest {
    @BeforeEach
    @AfterEach
    void clean() {
        RailFilter.reset();
        RailFilter.get().showEverything();
        RailFilter.get().stationRules().clear();
        RailFilter.get().trainRules().clear();
    }

    @Test
    void anInactiveFilterHandsBackTheSameNetwork() {
        final RailSnapshot snap = network();
        assertSame(snap, RailFilter.get().apply(snap),
                "an inactive filter must not copy the network sixty times a second");
    }

    @Test
    void hidingAGroupRemovesEveryPlatformItNames() {
        final RailFilter filter = RailFilter.get();
        filter.toggle(filter.offerStation("Quarry *"));

        final RailSnapshot out = filter.apply(network());
        for (final RailSnapshot.Station s : out.stations()) {
            assertFalse(s.name.startsWith("Quarry"), s.name + " should have been hidden");
        }
        assertNotNull(nameOf(out, "Central"), "hiding the quarry must not take anything else");
    }

    @Test
    void aLinkToAHiddenStationGoesWithIt() {
        final RailFilter filter = RailFilter.get();
        filter.toggle(filter.offerStation("Quarry *"));

        final RailSnapshot out = filter.apply(network());
        for (final RailSnapshot.Link l : out.links()) {
            assertNotNull(out.station(l.from), "a link must not point at a hidden station");
            assertNotNull(out.station(l.to), "a link must not point at a hidden station");
        }
    }

    @Test
    void hidingAStationLeavesTheTrackWhereItIs() {
        final RailSnapshot in = network();
        final RailFilter filter = RailFilter.get();
        filter.toggle(filter.offerStation("Quarry *"));

        assertEquals(in.tracks().size(), filter.apply(in).tracks().size(),
                "the rail layer is geography, not a property of the stations on it");
    }

    @Test
    void arrivalsFollowWhateverTheyReferTo() {
        final RailFilter filter = RailFilter.get();
        filter.toggle(filter.offerTrain("Ore *"));

        final RailSnapshot out = filter.apply(network());
        for (final RailSnapshot.Arrival a : out.arrivals()) {
            assertNotNull(out.train(a.trainId), "an arrival for a hidden train has to go");
        }
        assertNull(out.train(idOf("Ore Hauler")));
    }

    @Test
    void stateFiltersKeepWhatTheySay() {
        final RailFilter filter = RailFilter.get();

        filter.setHideDocked(true);
        for (final RailSnapshot.TrainState t : filter.apply(network()).trains()) {
            assertFalse(t.docked(), t.name + " is at a platform and should be hidden");
        }
        filter.setHideDocked(false);

        filter.setOnlyProblems(true);
        final List<RailSnapshot.TrainState> problems = filter.apply(network()).trains();
        assertFalse(problems.isEmpty(), "the fixture has a derailed train");
        for (final RailSnapshot.TrainState t : problems) {
            assertTrue(t.unhealthy() || t.waitingForSignal || t.schedulePaused,
                    t.name + " is fine and should not be in the problem list");
        }
    }

    @Test
    void patternsMatchTheWayAPlayerExpects() {
        assertTrue(RailFilter.matches("Quarry *", "Quarry Platform 2"));
        assertTrue(RailFilter.matches("quarry *", "Quarry Platform 2"));
        assertTrue(RailFilter.matches("* Yard", "North Yard"));
        assertTrue(RailFilter.matches("*depot*", "The Depot Sidings"));
        assertTrue(RailFilter.matches("Central", "central"));
        assertFalse(RailFilter.matches("Quarry *", "Quarrymans Arms Halt".substring(9)));
        assertFalse(RailFilter.matches("", "Anything"));
    }

    @Test
    void offeringARuleTwiceIsOneRule() {
        final RailFilter filter = RailFilter.get();
        final RailFilter.Rule first = filter.offerStation("Docks *");
        filter.toggle(first);
        final RailFilter.Rule again = filter.offerStation("docks *");

        assertSame(first, again, "a rule offered again is the same rule");
        assertTrue(again.hidden, "and re-offering it must not quietly switch it back on");
        assertEquals(1, filter.stationRules().size());
    }

    @Test
    void showAllPutsEverythingBack() {
        final RailFilter filter = RailFilter.get();
        filter.toggle(filter.offerStation("Quarry *"));
        filter.setOnlyProblems(true);
        assertTrue(filter.active());

        filter.showEverything();
        assertFalse(filter.active());
        assertEquals(network().stations().size(), filter.apply(network()).stations().size());
    }

    private static UUID idOf(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    private static RailSnapshot.Station nameOf(RailSnapshot snap, String name) {
        for (final RailSnapshot.Station s : snap.stations()) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }

    private static RailSnapshot network() {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 500L;
        final RailSnapshot.Station central = station("Central", 0, 0);
        final RailSnapshot.Station q1 = station("Quarry Platform 1", 300, -180);
        final RailSnapshot.Station q2 = station("Quarry Platform 2", 316, -168);
        final RailSnapshot.Station docks = station("Docks Berth A", -220, 260);
        snap.stations().addAll(List.of(central, q1, q2, docks));

        snap.links().add(link(central, q1, 400.0));
        snap.links().add(link(central, docks, 380.0));
        snap.links().add(link(q1, q2, 30.0));

        final RailSnapshot.Track track = new RailSnapshot.Track();
        track.dimension = "minecraft:overworld";
        track.path = new float[]{0, 0, 300, -180};
        snap.tracks().add(track);

        final RailSnapshot.TrainState ore = train("Ore Hauler");
        ore.currentStation = q1.id;
        snap.trains().add(ore);

        final RailSnapshot.TrainState stone = train("Stone Express");
        stone.speed = 0.7f;
        snap.trains().add(stone);

        final RailSnapshot.TrainState wreck = train("Scrap Train");
        wreck.derailed = true;
        snap.trains().add(wreck);

        snap.arrivals().add(arrival(ore, "Quarry Platform 1", 120));
        snap.arrivals().add(arrival(stone, "Central", 600));
        return snap;
    }

    private static RailSnapshot.Station station(String name, double x, double z) {
        final RailSnapshot.Station s = new RailSnapshot.Station();
        s.id = idOf(name);
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
        return l;
    }

    private static RailSnapshot.TrainState train(String name) {
        final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
        t.id = idOf(name);
        t.name = name;
        t.dimension = "minecraft:overworld";
        t.hasSchedule = true;
        t.carriages = 3;
        return t;
    }

    private static RailSnapshot.Arrival arrival(RailSnapshot.TrainState t, String station,
                                                int ticks) {
        final RailSnapshot.Arrival a = new RailSnapshot.Arrival();
        a.trainId = t.id;
        a.trainName = t.name;
        a.station = station;
        a.ticks = ticks;
        return a;
    }
}
