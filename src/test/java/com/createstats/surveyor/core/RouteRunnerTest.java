package com.createstats.surveyor.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteRunnerTest {
    private static Pathway straight(double length) {
        return Pathway.create(UUID.randomUUID(), "t", 2, new UUID(0, 9))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{length / 2, 64, 0}, new double[]{length, 64, 0}));
    }

    private static RouteRunner.Sensors at(long tick, double x, double z, double hx, double hz, double v) {
        return new RouteRunner.Sensors(tick, x, 64, z, hx, hz, v, 0.15, false, Double.POSITIVE_INFINITY, false, "",
                Double.POSITIVE_INFINITY, 0, false, false, false, true, 0, false, 0, 0);
    }

    private static RouteRunner.Sensors with(RouteRunner.Sensors s, boolean staff, boolean keys) {
        return new RouteRunner.Sensors(s.tick(), s.x(), s.y(), s.z(), s.hx(), s.hz(), s.speed(), s.kappaPerStick(), s.skid(),
                s.obstacle(), s.obstaclePlayer(), s.obstacleWhat(), s.gapAhead(), s.speedAhead(), s.departOk(), staff, keys,
                s.calibrated(), s.appliedThrottle(), s.appliedBrake(), s.appliedSteer(), s.yawRate());
    }

    @Test
    void facingTheWrongWayOnAOneWayLineItSaysSoInsteadOfManoeuvring() {
        RouteRunner r = new RouteRunner(straight(60), new PlantModel(128));
        r.start(0, true);
        RouteRunner.Command c = r.tick(at(1, 10, 0.5, -1, 0, 0));
        assertFalse(c.drive(), "parked");
        assertEquals(RouteRunner.State.PAUSED, r.state());
        assertEquals("turn me to face the route", r.reason());
    }

    @Test
    void tooFarAwayItAsksToBeBroughtCloser() {
        RouteRunner r = new RouteRunner(straight(60), new PlantModel(128));
        r.start(0, true);
        r.tick(at(1, 10, 80, 1, 0, 0));
        assertEquals(RouteRunner.State.PAUSED, r.state());
        assertTrue(r.reason().startsWith("too far from the route"), r.reason());
    }

    @Test
    void unknownGearingCalibratesFirst() {
        RouteRunner r = new RouteRunner(straight(60), new PlantModel(128));
        r.start(0, false);
        assertEquals(RouteRunner.State.CALIBRATING, r.state());
        RouteRunner.Command c = r.tick(at(1, 5, 0, 1, 0, 0));
        assertTrue(c.drive() && !c.brake() && c.throttle() == 0.0, "no throttle, no brake: the governors creep");
        RouteRunner.Sensors done = at(2, 5, 0, 1, 0, 0);
        r.tick(done);
        assertEquals(RouteRunner.State.JOINING, r.state(), "calibrated sensors move it on");
    }

    @Test
    void driveKeysHandTheWheelToThePlayerAndResumeRejoins() {
        RouteRunner r = new RouteRunner(straight(60), new PlantModel(128));
        r.start(0, true);
        r.tick(at(1, 5, 0.2, 1, 0, 2));
        RouteRunner.Command c = r.tick(with(at(2, 5.1, 0.2, 1, 0, 2), false, true));
        assertEquals(RouteRunner.State.MANUAL, r.state());
        assertFalse(c.drive(), "the player drives");
        assertFalse(r.tick(at(3, 5.2, 0.2, 1, 0, 2)).drive(), "still theirs after they let go, until Resume");
        r.start(4, true);
        assertEquals(RouteRunner.State.JOINING, r.state());
        assertTrue(r.tick(at(5, 5.3, 0.2, 1, 0, 2)).drive());
    }

    @Test
    void aPhysicsStaffHoldsItAndItRejoinsWhereItIsPut() {
        RouteRunner r = new RouteRunner(straight(60), new PlantModel(128));
        r.start(0, true);
        r.tick(at(1, 5, 0.2, 1, 0, 2));
        RouteRunner.Command held = r.tick(with(at(2, 5, 0.2, 1, 0, 2), true, false));
        assertTrue(held.brake(), "brakes while held");
        assertEquals("held by a physics staff", r.reason());

        for (int t = 3; t < 3 + RouteRunner.STAFF_SETTLE - 1; t++) assertTrue(r.tick(at(t, 25, 0.1, 1, 0, 0)).brake());
        r.tick(at(100, 25, 0.1, 1, 0, 0));
        assertEquals(RouteRunner.State.JOINING, r.state());
        assertEquals(25.0, r.progress(), 0.5, "from where it was put");
    }

    @Test
    void anEditThatDeletesItsStretchRejoinsAhead() {
        Pathway p = straight(60);
        RouteRunner r = new RouteRunner(p, new PlantModel(128));
        r.start(0, true);
        for (int t = 1; t < 30; t++) r.tick(at(t, 20, 0.1, 1, 0, 3));
        assertTrue(r.placed());

        Pathway moved = Pathway.create(p.id(), p.name(), p.colour(), p.owner())
                .appendAll(List.of(new double[]{0, 64, 20}, new double[]{60, 64, 20}));
        r.setPathway(moved);
        assertEquals(RouteRunner.State.JOINING, r.state());
        assertFalse(r.placed());
    }

    @Test
    void aSavedWaitResumesWithItsTimeAndARunningRouteRejoins() {
        Pathway p = straight(60);
        int mid = p.nodes().get(1).id();
        p = p.update(mid, n -> n.withStop(Stop.DEFAULT));
        RouteRunner r = new RouteRunner(p, new PlantModel(128));
        RouteRunner.Saved waiting = new RouteRunner.Saved(RouteRunner.State.WAITING, "", 30, 0, mid, 500, -1, false, 3, 42.5);
        r.restore(waiting, 900);
        assertEquals(RouteRunner.State.WAITING, r.state());
        assertEquals(500, r.waitingSince());
        assertEquals(3, r.laps());
        RouteRunner r2 = new RouteRunner(p, new PlantModel(128));
        r2.restore(new RouteRunner.Saved(RouteRunner.State.RUNNING, "", 12, 0, -1, 0, -1, false, 0, -1), 900);
        assertEquals(RouteRunner.State.JOINING, r2.state(), "rejoins from wherever it is now");
        RouteRunner r3 = new RouteRunner(p, new PlantModel(128));
        r3.restore(new RouteRunner.Saved(RouteRunner.State.PAUSED, "stuck at N2", 12, 0, -1, 0, -1, false, 0, -1), 900);
        assertEquals(RouteRunner.State.PAUSED, r3.state());
        assertEquals("stuck at N2", r3.reason());
    }

    @Test
    void theStatesThatHoldChunksAreTheBriefsList() {
        for (RouteRunner.State s : RouteRunner.State.values()) {
            boolean expected = switch (s) {
                case CALIBRATING, JOINING, RUNNING, APPROACHING, WAITING, DEPARTING, BLOCKED, STUCK, HAZARD -> true;
                default -> false;
            };
            assertEquals(expected, s.holdsChunks(), s.name());
        }
        assertFalse(RouteRunner.State.PAUSED.drives());
        assertFalse(RouteRunner.State.MANUAL.drives());
        assertFalse(RouteRunner.State.FINISHED.drives());
    }

    @Test
    @Disabled("Articulated vehicles: the joint's sign is still unknown (E0, ground as-built 10.2), so a follower tuned "
            + "for them could only be tuned against a guess; the host drives them with a 'not yet driven' note instead")
    void articulatedVehiclesFollowTheLine() {
    }
}
