package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFollowerSimTest {
    private static final UUID OWNER = new UUID(1, 2);

    static List<double[]> oval(double straight, double r, double y) {
        List<double[]> pts = new ArrayList<>();
        for (double x = 0; x < straight; x += 8.0) pts.add(new double[]{x, y, 0});
        for (int i = 0; i < 6; i++) {
            double phi = Math.PI * i / 6.0;
            pts.add(new double[]{straight + r * Math.sin(phi), y, r - r * Math.cos(phi)});
        }
        for (double x = straight; x > 0; x -= 8.0) pts.add(new double[]{x, y, 2 * r});
        for (int i = 0; i < 6; i++) {
            double phi = Math.PI * i / 6.0;
            pts.add(new double[]{-r * Math.sin(phi), y, r + r * Math.cos(phi)});
        }
        return pts;
    }

    static List<double[]> figureEight(double a) {
        List<double[]> pts = new ArrayList<>();
        int n = 40;
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            double lift = Math.max(0.0, 1.0 - Math.abs(PathSpline.wrapAngle(t - Math.PI)) / 1.2);
            pts.add(new double[]{a * Math.sin(t), 64.0 + 6.0 * lift, 0.5 * a * Math.sin(2 * t)});
        }
        return pts;
    }

    static List<double[]> hairpin(double r) {
        List<double[]> pts = new ArrayList<>();
        for (double x = -30; x < 0; x += 6) pts.add(new double[]{x, 64, 0});
        for (int i = 0; i <= 8; i++) {
            double phi = Math.PI * i / 8.0;
            pts.add(new double[]{r * Math.sin(phi), 64, r - r * Math.cos(phi)});
        }
        for (double x = -6; x >= -30; x -= 6) pts.add(new double[]{x, 64, 2 * r});
        return pts;
    }

    static List<double[]> sBend(double r) {
        List<double[]> pts = new ArrayList<>();
        for (double x = -24; x < 0; x += 6) pts.add(new double[]{x, 64, 0});
        for (int i = 0; i <= 5; i++) {
            double phi = Math.PI / 2 * i / 5.0;
            pts.add(new double[]{r * Math.sin(phi), 64, r - r * Math.cos(phi)});
        }
        for (int i = 1; i <= 5; i++) {
            double phi = Math.PI / 2 * i / 5.0;
            pts.add(new double[]{r + r - r * Math.cos(phi), 64, r + r * Math.sin(phi)});
        }
        for (double x = 2 * r + 6; x <= 2 * r + 30; x += 6) pts.add(new double[]{x, 64, 2 * r});
        return pts;
    }

    static Pathway pathway(List<double[]> pts, PathKind kind) {
        Pathway p = Pathway.create(UUID.randomUUID(), "test", 3, OWNER).appendAll(pts);
        return kind == PathKind.LOOP ? p.withKind(PathKind.LOOP) : p;
    }

    static PlantModel knownPlant(MkIvSim sim) {
        return new PlantModel(sim.k, sim.tau, sim.tau / 5.0, 1000, 100, 0.0);
    }

    static final class Result {
        double sumSq;
        int n;
        double maxErr;
        int sideChanges;
        double seconds;
        double worstOverPlan;
        double maxSpeed;
        final List<String> notes = new ArrayList<>();

        double rms() {
            return n == 0 ? 0.0 : Math.sqrt(sumSq / n);
        }

        double changesPerSecond() {
            return seconds <= 0 ? 0.0 : sideChanges / seconds;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "rms %.3f max %.3f sideChanges %d in %.0f s (%.3f/s) over plan %.2f top %.1f",
                    rms(), maxErr, sideChanges, seconds, changesPerSecond(), worstOverPlan, maxSpeed);
        }
    }

    static Result drive(MkIvSim sim, RouteRunner runner, int ticks) {
        Result r = new Result();
        runner.start(sim.tick, true);
        int lastSide = 0;
        int settled = 0;
        for (int t = 0; t < ticks; t++) {
            RouteRunner.Command c = runner.tick(sim.sensors(Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, 0.0, true));
            sim.step(c);
            RouteRunner.State st = runner.state();
            boolean following = st == RouteRunner.State.RUNNING || st == RouteRunner.State.APPROACHING;
            if (!following) {
                settled = 0;
                continue;
            }
            if (++settled < 60) continue;
            double e = Math.abs(runner.lateral());
            r.sumSq += e * e;
            r.n++;
            r.maxErr = Math.max(r.maxErr, e);
            r.seconds += MkIvSim.DT;
            double plan = runner.plan().at(runner.progress());
            r.worstOverPlan = Math.max(r.worstOverPlan, sim.v - (plan * 1.1 + 0.2));
            r.maxSpeed = Math.max(r.maxSpeed, sim.v);
            double stick = sim.out.steer();
            int side = stick > 0.05 ? 1 : (stick < -0.05 ? -1 : 0);
            if (side != 0 && lastSide != 0 && side != lastSide) r.sideChanges++;
            if (side != 0) lastSide = side;
        }
        return r;
    }

    @Test
    void anOvalIsFollowedTightlyAndWithoutWeaving() {
        MkIvSim sim = MkIvSim.car().at(0, 0.3, 90);
        Pathway p = pathway(oval(40, 12, 64), PathKind.LOOP);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        Result r = drive(sim, runner, 20 * 150);
        System.out.println("oval: " + r);
        assertTrue(r.n > 1000, "it joined and drove");
        assertTrue(r.rms() < 0.5, "RMS cross-track " + r);
        assertTrue(r.maxErr < 1.2, "max cross-track " + r);
        assertTrue(r.worstOverPlan <= 0.0, "never over the plan by a tenth " + r);
        assertTrue(r.changesPerSecond() < 0.25, "no weaving " + r);
        assertTrue(runner.laps() >= 2, "laps " + runner.laps());
    }

    @Test
    void aFigureOfEightWithABridgeNeverJumpsLevels() {
        MkIvSim sim = MkIvSim.car().at(0, 0, 90).atY(64);

        double[] t0 = {30 * 1.0, 0, 15 * 1.0};
        double len = Math.hypot(t0[0], t0[2]);
        sim.theta = Math.atan2(t0[0] / len, -t0[2] / len);
        Pathway p = pathway(figureEight(30), PathKind.LOOP);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        PathSpline sp = PathSpline.of(p);
        runner.start(sim.tick, true);
        double lastS = Double.NaN;
        int ticks = 20 * 150;
        double worstJump = 0.0;
        double sumSq = 0.0;
        int n = 0;
        double maxErr = 0.0;
        for (int t = 0; t < ticks; t++) {
            RouteRunner.Command c = runner.tick(sim.sensors(Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, 0, true));
            sim.step(c);
            if (runner.placed()) sim.y = sp.y(runner.progress());
            if (runner.state() == RouteRunner.State.RUNNING || runner.state() == RouteRunner.State.APPROACHING) {
                double s = runner.progress();
                if (!Double.isNaN(lastS)) {
                    double jump = Math.abs(sp.signedDelta(lastS, s));
                    worstJump = Math.max(worstJump, jump);
                }
                lastS = s;
                double e = Math.abs(runner.lateral());
                sumSq += e * e;
                n++;
                maxErr = Math.max(maxErr, e);
            } else {
                lastS = Double.NaN;
            }
        }
        double rms = Math.sqrt(sumSq / Math.max(1, n));
        System.out.printf(java.util.Locale.ROOT, "figure eight: rms %.3f max %.3f worst jump %.3f laps %d%n", rms, maxErr, worstJump, runner.laps());
        assertTrue(n > 1000, "drove");
        assertTrue(worstJump < 1.0, "progress never jumps to the other branch: " + worstJump);
        assertTrue(runner.laps() >= 1, "laps " + runner.laps());
        assertTrue(rms < 0.5 && maxErr < 1.2, "tracking rms " + rms + " max " + maxErr);
    }

    @Test
    void aHairpinAtTheTightestComfortableRadiusIsHeld() {
        MkIvSim sim = MkIvSim.car().at(-30, 0, 90);
        double rMin = sim.minRadius();
        Pathway p = pathway(hairpin(rMin * 1.3), PathKind.ONE_WAY);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        Result r = drive(sim, runner, 20 * 60);
        System.out.println("hairpin (R " + String.format(java.util.Locale.ROOT, "%.1f", rMin * 1.3) + "): " + r + " state " + runner.state());
        assertTrue(r.maxErr < 1.2, "max cross-track " + r);
        assertTrue(r.rms() < 0.5, "rms " + r);
        assertEquals(RouteRunner.State.FINISHED, runner.state(), "drove it to the end");
    }

    @Test
    void anSBendIsFollowedWithOneChangeOfSide() {
        MkIvSim sim = MkIvSim.car().at(-24, 0, 90);
        Pathway p = pathway(sBend(10), PathKind.ONE_WAY);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        Result r = drive(sim, runner, 20 * 40);
        System.out.println("s-bend: " + r);
        assertTrue(r.maxErr < 1.2 && r.rms() < 0.5, "tracking " + r);
        assertTrue(r.sideChanges <= 3, "one real change of side, not a weave: " + r);
    }

    @Test
    void aTruckOnTheOvalAndAnUnlearnedCarBothTrack() {
        MkIvSim truck = MkIvSim.truck().at(0, 0.3, 90);
        Pathway p = pathway(oval(40, 14, 64), PathKind.LOOP);
        Result t = drive(truck, new RouteRunner(p, knownPlant(truck)), 20 * 120);
        System.out.println("truck oval: " + t);
        assertTrue(t.rms() < 0.5 && t.maxErr < 1.2, "truck " + t);

        MkIvSim car = MkIvSim.car().at(0, 0.3, 90);
        PlantModel fresh = new PlantModel(128);
        RouteRunner runner = new RouteRunner(p, fresh);
        Result c = drive(car, runner, 20 * 120);
        System.out.println("unlearned car oval: " + c + " learned K " + fresh.k() + " tau " + fresh.tau());
        assertTrue(fresh.learned(), "it learned its plant while driving");
        assertEquals(car.k, fresh.k(), car.k * 0.2, "K within a fifth");
        assertTrue(c.rms() < 0.5 && c.maxErr < 1.2, "unlearned car " + c);
        assertTrue(c.worstOverPlan <= 0.0, "never over the plan " + c);
    }

    @Test
    void aSkidSteeredTankFollowsTheOval() {
        MkIvSim tank = MkIvSim.tank().at(0, 0.3, 90);
        Pathway p = pathway(oval(40, 14, 64), PathKind.LOOP);
        PlantModel plant = knownPlant(tank);
        plant.seedSkid(2.0 * tank.k / tank.track);
        Result r = drive(tank, new RouteRunner(p, plant), 20 * 150);
        System.out.println("tank oval: " + r);
        assertTrue(r.rms() < 0.6 && r.maxErr < 1.4, "tank " + r);
        assertTrue(r.changesPerSecond() < 0.5, "tank does not weave " + r);
    }

    @Test
    void aStopIsStoppedOnAndLeftWhenItsRulesSayGo() {
        MkIvSim sim = MkIvSim.car().at(0, 0.3, 90);
        Pathway p = pathway(oval(40, 12, 64), PathKind.LOOP);

        int stopId = p.nodes().get(3).id();
        p = p.update(stopId, n -> n.withStop(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(3))))));
        PathSpline sp = PathSpline.of(p);
        double stopS = sp.nodeS(3);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        runner.start(0, true);
        int arrivals = 0;
        double worstMiss = 0.0;
        int waited = 0;
        boolean left = false;
        for (int t = 0; t < 20 * 120; t++) {
            boolean waiting = runner.state() == RouteRunner.State.WAITING;
            int w = waiting ? (int) (sim.tick - runner.waitingSince()) : 0;
            Stop stop = p.node(stopId).stop();
            boolean go = waiting && StopRules.evaluate(stop, w, StopRules.EMPTY).go();
            RouteRunner.Command c = runner.tick(sim.sensors(Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, 0, go));
            boolean nowWaiting = runner.state() == RouteRunner.State.WAITING;
            if (nowWaiting && !waiting) {
                arrivals++;
                double miss = Math.abs(sp.signedDelta(stopS, runner.progress()));
                worstMiss = Math.max(worstMiss, miss);
            }
            if (nowWaiting) {
                waited++;
                assertTrue(Math.abs(sim.v) < 0.5, "held still while waiting: " + sim.v);
            }
            if (waiting && !nowWaiting) left = true;
            sim.step(c);
        }
        System.out.printf(java.util.Locale.ROOT, "stop: arrivals %d worst miss %.3f waited %d ticks%n", arrivals, worstMiss, waited);
        assertTrue(arrivals >= 2, "stopped every lap: " + arrivals);
        assertTrue(worstMiss <= 0.75, "within three quarters of a block: " + worstMiss);
        assertTrue(left, "and left again");
        assertTrue(waited >= (arrivals - 1) * 58, "waited its three seconds each time (the last may be cut off by the end of the run): " + waited);
    }

    @Test
    void aFollowerKeepsItsDistanceAndQueuesForATakenStop() {
        Pathway p = pathway(oval(40, 12, 64), PathKind.LOOP);
        int stopId = p.nodes().get(3).id();
        p = p.update(stopId, n -> n.withStop(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(8))))));
        PathSpline sp = PathSpline.of(p);
        MkIvSim a = MkIvSim.car().at(8, 0.2, 90);
        MkIvSim b = MkIvSim.car().at(-2, 12 * 2 - 0.2, 270);

        b.at(-4, 0.2, 90);
        RouteRunner ra = new RouteRunner(p, knownPlant(a));
        RouteRunner rb = new RouteRunner(p, knownPlant(b));
        ra.start(0, true);
        rb.start(0, true);
        double front = 2.5;
        double rear = 2.5;
        double worstGap = Double.POSITIVE_INFINITY;
        int queued = 0;
        for (int t = 0; t < 20 * 150; t++) {
            TrafficGap.Car[] cars = {
                    new TrafficGap.Car(ra.progress(), front, rear, a.v),
                    new TrafficGap.Car(rb.progress(), front, rear, b.v)};
            TrafficGap.Room roomA = ra.placed() && rb.placed() ? TrafficGap.roomOf(sp, cars, 0) : TrafficGap.Room.CLEAR;
            TrafficGap.Room roomB = ra.placed() && rb.placed() ? TrafficGap.roomOf(sp, cars, 1) : TrafficGap.Room.CLEAR;
            RouteRunner.Command ca = runner(ra, a, p, stopId, roomA, b.v);
            RouteRunner.Command cb = runner(rb, b, p, stopId, roomB, a.v);
            a.step(ca);
            b.step(cb);
            if (ra.placed() && rb.placed() && t > 40) {
                worstGap = Math.min(worstGap, Math.min(roomA.gap(), roomB.gap()));
                if (rb.state() != RouteRunner.State.WAITING && Math.abs(b.v) < 0.1 && ra.state() == RouteRunner.State.WAITING) queued++;
            }
        }
        System.out.printf(java.util.Locale.ROOT, "queue: worst gap %.2f, ticks queued behind a waiting car %d, laps %d and %d%n",
                worstGap, queued, ra.laps(), rb.laps());
        assertTrue(worstGap > RouteRunner.STANDOFF - 1.0, "never closer than the standoff, give or take a block: " + worstGap);
        assertTrue(queued > 20, "queued behind the car at the stop: " + queued);
        assertTrue(ra.laps() >= 1 && rb.laps() >= 1, "both lapped: " + ra.laps() + " " + rb.laps());
    }

    private static RouteRunner.Command runner(RouteRunner r, MkIvSim sim, Pathway p, int stopId, TrafficGap.Room room, double otherSpeed) {
        boolean waiting = r.state() == RouteRunner.State.WAITING;
        int w = waiting ? (int) (sim.tick - r.waitingSince()) : 0;
        boolean go = waiting && StopRules.evaluate(p.node(stopId).stop(), w, StopRules.EMPTY).go();
        RouteRunner.Sensors s = sim.sensors(Double.POSITIVE_INFINITY, false, room.gap(), otherSpeed, go);
        return r.tick(s);
    }

    @Test
    void aPlayerInTheWayStopsItShortForAsLongAsTheyStand() {
        MkIvSim sim = MkIvSim.car().at(0, 0.3, 90);
        Pathway p = pathway(oval(60, 12, 64), PathKind.LOOP);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        runner.start(0, true);

        double playerX = 40.0;
        double front = 2.5;
        double closest = Double.POSITIVE_INFINITY;
        int blocked = 0;
        for (int t = 0; t < 20 * 90; t++) {
            boolean there = t < 20 * 70;
            double obstacle = Double.POSITIVE_INFINITY;
            if (there && Math.abs(sim.z) < 3 && sim.x < playerX && Math.abs(Math.toDegrees(sim.theta) - 90) < 45) {
                obstacle = playerX - (sim.x + front);
                closest = Math.min(closest, obstacle);
            }
            RouteRunner.Command c = runner.tick(sim.sensors(obstacle, true, Double.POSITIVE_INFINITY, 0, true));
            if (runner.state() == RouteRunner.State.BLOCKED) blocked++;
            sim.step(c);
        }
        System.out.printf(java.util.Locale.ROOT, "player: closest %.2f, blocked %d ticks, final state %s%n", closest, blocked, runner.state());
        assertTrue(closest > 0.3, "never touched them: " + closest);
        assertTrue(blocked > 20 * 30, "waited while they stood there: " + blocked);
        assertTrue(runner.state() != RouteRunner.State.BLOCKED && runner.state() != RouteRunner.State.PAUSED,
                "and went on once they stepped aside: " + runner.state());
    }

    @Test
    void aNodeDraggedUnderARunningVehicleIsFollowed() {
        MkIvSim sim = MkIvSim.car().at(0, 0.3, 90);
        Pathway p = pathway(oval(40, 12, 64), PathKind.LOOP);
        RouteRunner runner = new RouteRunner(p, knownPlant(sim));
        runner.start(0, true);
        for (int t = 0; t < 20 * 20; t++) sim.step(runner.tick(sim.sensors(Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, 0, true)));

        PathNode n = p.nodes().get(p.nodes().size() / 2 + 2);
        Pathway moved = p.move(n.id(), n.x(), n.y(), n.z() + 3.0);
        runner.setPathway(moved);
        double worstAtOnce = 0.0;
        double maxErr = 0.0;
        for (int t = 0; t < 20 * 60; t++) {
            sim.step(runner.tick(sim.sensors(Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, 0, true)));
            assertTrue(runner.state() != RouteRunner.State.LOST && runner.state() != RouteRunner.State.PAUSED, "never lost: " + runner.state());
            double e = Math.abs(runner.lateral());
            if (t < 40) worstAtOnce = Math.max(worstAtOnce, e);
            else maxErr = Math.max(maxErr, e);
        }
        System.out.printf(java.util.Locale.ROOT, "dragged node: %.3f as the line moved, then max %.3f, state %s%n", worstAtOnce, maxErr, runner.state());
        assertTrue(maxErr < 1.2, "follows the new shape once back on it: " + maxErr);
    }
}
