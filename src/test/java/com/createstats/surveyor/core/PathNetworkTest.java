package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathNetworkTest {
    static final UUID LOOP = new UUID(7, 1);
    static final UUID SPUR = new UUID(7, 2);
    static final UUID CROSS = new UUID(7, 3);

    static Pathway loop() {
        Pathway p = Pathway.create(LOOP, "Loop", 3, new UUID(0, 1)).appendAll(RouteFollowerSimTest.oval(40, 20, 64)).withKind(PathKind.LOOP);

        return p.update(p.nodes().get(0).id(), n -> n.withName("Yard").withStop(Stop.DEFAULT));
    }

    static Pathway spur() {
        Pathway p = Pathway.create(SPUR, "Farm spur", 5, new UUID(0, 1)).appendAll(List.of(
                new double[]{24, 64, 0}, new double[]{32, 64, -4}, new double[]{44, 64, -12}, new double[]{60, 64, -20}));
        return p.update(p.nodes().get(3).id(), n -> n.withName("Farm").withStop(Stop.DEFAULT));
    }

    static Pathway cross() {
        Pathway p = Pathway.create(CROSS, "Quarry road", 9, new UUID(0, 1)).appendAll(List.of(
                new double[]{12, 64, -20}, new double[]{12, 64, -10}, new double[]{12, 64, 10}, new double[]{12, 64, 20}));
        return p.update(p.nodes().get(3).id(), n -> n.withName("Quarry").withStop(Stop.DEFAULT));
    }

    static PathNetwork net() {
        return PathNetwork.of(List.of(loop(), spur(), cross()));
    }

    @Test
    void theSpurIsAWayOnAndTheCrossingIsNot() {
        PathNetwork net = net();
        boolean loopToSpur = false;
        boolean anyToCross = false;
        for (PathNetwork.Transfer t : net.transfers()) {
            if (t.from().equals(LOOP) && t.to().equals(SPUR)) {
                loopToSpur = true;
                assertEquals(24.0, t.x(), 0.01);
                assertTrue(Math.abs(t.turn()) < PathNetwork.MAX_TURN, "a fork: " + t.turn());
            }
            anyToCross |= t.to().equals(CROSS) || t.from().equals(CROSS);
        }
        assertTrue(loopToSpur, "the loop forks onto the spur at (24, 0): " + net.transfers());
        assertTrue(!anyToCross, "a line crossing head-on is no way on: " + net.transfers());
    }

    @Test
    void bothThePlacesTheLinesMeetAreConflictZones() {
        PathNetwork net = net();
        boolean fork = false;
        boolean crossing = false;
        for (PathNetwork.Zone z : net.zones()) {
            if (Math.hypot(z.x() - 24, z.z()) < 1.5 && z.at().containsKey(LOOP) && z.at().containsKey(SPUR)) fork = true;
            if (Math.hypot(z.x() - 12, z.z()) < 2.5 && z.at().containsKey(LOOP) && z.at().containsKey(CROSS)) crossing = true;
        }
        assertTrue(fork, "the fork: " + net.zones());
        assertTrue(crossing, "the crossing: " + net.zones());
    }

    @Test
    void travelToTheFarmGoesRoundTheLoopAndOffAtTheFork() {
        PathNetwork net = net();
        PathNetwork.Route r = net.route(LOOP, 30.0, "Farm"::equals, null);
        assertNotNull(r, "the farm can be reached from the loop");
        assertEquals(2, r.legs().size(), "the loop, then the spur: " + r.legs());
        assertEquals(LOOP, r.legs().get(0).pathway());
        assertEquals(SPUR, r.legs().get(1).pathway());
        assertEquals(SPUR, r.station());

        PathSpline sp = net.spline(LOOP);
        assertTrue(r.legs().get(0).to() < 30.0 && sp.ahead(30.0, r.legs().get(0).to()) > sp.length() * 0.8,
                "round the loop to the fork: " + r.legs().get(0));
        assertTrue(r.seconds() > 0.0);
    }

    @Test
    void aStationOnItsOwnLineIsOneLegAndAnOccupiedOneCostsMore() {
        PathNetwork net = net();
        PathNetwork.Route free = net.route(LOOP, 5.0, "Yard"::equals, null);
        PathNetwork.Route busy = net.route(LOOP, 5.0, "Yard"::equals, (line, node) -> true);
        assertNotNull(free);
        assertEquals(1, free.legs().size());
        assertEquals(free.seconds() + PathNetwork.OCCUPIED_SECONDS, busy.seconds(), 1.0e-6, "Create's surcharge, in seconds");
    }

    @Test
    void aStationOnALineNotJoinedToThisOneCannotBeReached() {
        assertNull(net().route(LOOP, 5.0, "Quarry"::equals, null), "the quarry road only crosses the loop");
    }
}
