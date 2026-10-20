package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverBehaviourTest {
    static PlantModel plant(MkIvSim sim) {
        return new PlantModel(sim.k, sim.tau, sim.tau / 5.2, 1000, 100, 0.0);
    }

    static Pathway straight(double length) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (double x = 0; x <= length; x += 10.0) pts.add(new double[]{x, 64, 0});
        return RouteFollowerSimTest.pathway(pts, PathKind.ONE_WAY);
    }

    @Test
    void aRejoinFromSixBlocksOffAtFortyFiveDegreesNeverStopsOrSurges() {
        MkIvSim sim = MkIvSim.carrier().at(10, 6.0, 135).noisy(0.15, 0.02);
        sim.v = 3.0;
        Pathway p = RouteFollowerSimTest.pathway(RouteFollowerSimTest.oval(60, 22, 64), PathKind.LOOP);
        RouteRunner runner = new RouteRunner(p, plant(sim));
        RouteTrace t = RouteSim.drive(sim, runner, 20 * 40);
        double slowest = Double.POSITIVE_INFINITY;
        int rejoined = -1;
        int signChanges = 0;
        int sign = 0;
        for (int i = 20; i < t.size(); i++) {
            double v = t.get(RouteTrace.Ch.SPEED, i);
            slowest = Math.min(slowest, v);

            if (rejoined >= 0 && i > rejoined + 20) continue;
            double a = (t.get(RouteTrace.Ch.SPEED, i) - t.get(RouteTrace.Ch.SPEED, i - 5)) * 4.0;
            if (a > 0.3) {
                if (sign < 0) signChanges++;
                sign = 1;
            } else if (a < -0.3) {
                if (sign > 0) signChanges++;
                sign = -1;
            }
            if (rejoined < 0 && Math.abs(t.get(RouteTrace.Ch.LATERAL, i)) < 0.5 && Math.abs(t.get(RouteTrace.Ch.HEADING, i)) < 10.0) {
                rejoined = i;
            }
        }
        System.out.printf(Locale.ROOT, "rejoin: slowest %.2f b/s, on the line after %.1f s, %d surges%n", slowest,
                rejoined / 20.0, signChanges);
        assertTrue(slowest > 1.0, "never stops: " + slowest);
        assertTrue(rejoined > 0 && rejoined < 20 * 15, "back on the line within fifteen seconds: " + rejoined / 20.0);
        assertTrue(signChanges <= 2, "no surge on the way back: " + signChanges);
    }

    @Test
    void aBlockOnTheLineIsAHazardAtOnceWithWhereItIsAndNoHorn() {
        MkIvSim sim = MkIvSim.carrier().at(0, 0.0, 90);
        Pathway p = straight(120);
        RouteRunner runner = new RouteRunner(p, plant(sim));
        RouteSim.World w = new RouteSim.World();
        double blockX = 60.0;
        w.obstacles = (s, r) -> blockX - (s.x + 3.0) < 30.0 ? blockX - (s.x + 3.0) : Double.POSITIVE_INFINITY;
        w.block = true;
        RouteTrace t = new RouteTrace(20 * 60);
        runner.start(0, true);
        boolean horn = false;
        double worstDecel = 0.0;
        double lastV = 0.0;
        for (int i = 0; i < 20 * 60; i++) {
            RouteRunner.Command c = RouteSim.step(sim, runner, w, t);
            horn |= c.horn();
            if (i > 0) worstDecel = Math.max(worstDecel, (lastV - sim.v) * 20.0);
            lastV = sim.v;
        }
        System.out.printf(Locale.ROOT, "block: state %s (%s), front stopped %.2f short, worst decel %.2f%n", runner.state(),
                runner.reason(), blockX - (sim.x + 3.0), worstDecel);
        assertEquals(RouteRunner.State.HAZARD, runner.state(), "a block is a hazard, not a wait");
        assertTrue(runner.reason().contains("stone"), "and it says what it is: " + runner.reason());
        assertFalse(horn, "never honked at");
        double gap = blockX - (sim.x + 3.0);
        assertTrue(gap > 0.5 && gap < RouteRunner.OBSTACLE_MARGIN + 1.0, "stopped short of it, not far back: " + gap);
        assertTrue(worstDecel <= Comfort.NORMAL.decel * 1.25 + 0.1, "at the comfort's rate, seen from 30 b: " + worstDecel);
        assertEquals(0, t.measures().jolts(), "no jolt");
    }

    @Test
    void aPlayerInTheWayGetsOneShortTootNotAHonkLoop() {
        MkIvSim sim = MkIvSim.carrier().at(0, 0.0, 90);
        Pathway p = straight(120);
        RouteRunner runner = new RouteRunner(p, plant(sim));
        RouteSim.World w = new RouteSim.World();
        w.obstacles = (s, r) -> 40.0 - (s.x + 3.0) < 25.0 ? 40.0 - (s.x + 3.0) : Double.POSITIVE_INFINITY;
        w.player = true;
        runner.start(0, true);
        int toots = 0;
        for (int i = 0; i < 20 * 30; i++) if (RouteSim.step(sim, runner, w, null).horn()) toots++;
        assertEquals(RouteRunner.State.BLOCKED, runner.state());
        assertEquals(1, toots, "one short toot in thirty seconds: " + toots);
    }

    @Test
    void aHazardAheadIsStoppedShortOfAndNamed() {
        MkIvSim sim = MkIvSim.carrier().at(0, 0.0, 90);
        Pathway p = straight(120);
        RouteRunner runner = new RouteRunner(p, plant(sim));
        RouteSim.World w = new RouteSim.World();
        w.hazard = (s, r) -> 60.0 - (s.x + 3.0) < 60.0 ? 60.0 - (s.x + 3.0) : Double.POSITIVE_INFINITY;
        w.hazardWhat = "water at N7";
        RouteTrace t = new RouteTrace(20 * 60);
        runner.start(0, true);
        double furthest = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 20 * 60; i++) {
            RouteSim.step(sim, runner, w, t);
            furthest = Math.max(furthest, sim.x + 3.0);
        }
        System.out.printf(Locale.ROOT, "hazard: state %s (%s), front got to %.2f of 60%n", runner.state(), runner.reason(), furthest);
        assertEquals(RouteRunner.State.HAZARD, runner.state());
        assertTrue(furthest < 60.0, "never into the water: " + furthest);
        assertTrue(furthest > 57.5, "but stopped at the edge, not miles back: " + furthest);
        assertEquals("water at N7", runner.reason());
        assertEquals(0, t.measures().jolts());
    }

    @Test
    void stuckTriesSomethingDifferentWithinFiveSeconds() {
        MkIvSim sim = MkIvSim.carrier().at(0, 0.0, 90);
        Pathway p = straight(120);
        RouteRunner runner = new RouteRunner(p, plant(sim));
        runner.start(0, true);
        RouteSim.World w = new RouteSim.World();
        int stuckAt = -1;
        int firstTry = -1;
        boolean reversed = false;

        sim.wall = s -> s.x + 3.0 >= 20.0;
        for (int i = 0; i < 20 * 40; i++) {
            RouteRunner.Command c = RouteSim.step(sim, runner, w, null);
            if (stuckAt < 0 && runner.state() == RouteRunner.State.STUCK) stuckAt = i;
            if (stuckAt >= 0 && firstTry < 0 && (c.throttle() >= 0.99 || c.throttle() < 0.0)) firstTry = i;
            reversed |= c.throttle() < 0.0;
        }
        System.out.printf(Locale.ROOT, "stuck at %.1f s, first different try %.1f s later, reversed %s, final %s (%s)%n",
                stuckAt / 20.0, (firstTry - stuckAt) / 20.0, reversed, runner.state(), runner.reason());
        assertTrue(stuckAt > 0, "noticed it was stuck");
        assertTrue(firstTry >= 0 && firstTry - stuckAt <= 20 * 5, "a first different try within five seconds");
        assertTrue(reversed, "backed off to try another line (short reverses allowed)");
        assertEquals(RouteRunner.State.PAUSED, runner.state(), "then parked and called");
        assertTrue(runner.reason().startsWith("stuck at"), runner.reason());
    }
}
