package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkDriveTest {
    @Test
    void carrierOneTakesTheForkToTheFarmWithoutAJolt() {
        PathNetwork net = PathNetworkTest.net();
        Pathway loop = PathNetworkTest.loop();
        Pathway spur = PathNetworkTest.spur();
        PathNetwork.Route route = net.route(loop.id(), 2.0, "Farm"::equals, null);
        assertNotNull(route);
        PathNetwork.Transfer fork = route.transfers().get(0);
        PathNode start = loop.nodes().get(0);
        MkIvSim sim = MkIvSim.carrier().at(start.x() + 2.0, start.z(), 90.0).atY(64.0).noisy(0.15, 0.02);
        RouteRunner runner = new RouteRunner(loop, OwnerCarDrivingTest.ownerPlant());
        runner.scheduled(true);
        RouteSim.World w = new RouteSim.World();
        RouteTrace t = new RouteTrace(20 * 200);
        runner.start(sim.tick, true);
        boolean switched = false;
        double speedAtFork = Double.NaN;
        double offRoad = 0.0;
        for (int i = 0; i < 20 * 180; i++) {
            if (!switched) {
                PathSpline sp = PathSpline.of(loop);
                double ahead = sp.ahead(runner.progress(), fork.fromS());
                double d = ahead > sp.length() - 3.0 ? ahead - sp.length() : ahead;
                double turn = Math.toRadians(Math.abs(fork.turn()));
                runner.capAt(fork.fromS(), Math.sqrt(Comfort.NORMAL.lateral * 4.0 / Math.max(0.05, turn)));
                double lead = fork.leadIn(MkIvSim.carrier().minRadius() * 1.1) + Math.max(0.0, sim.v) * 0.25;
                if (d <= Math.max(0.3, lead) && d > -3.0 && runner.placed()) {
                    speedAtFork = sim.v;
                    runner.handOver(spur);
                    runner.capAt(Double.NaN, 0.0);
                    runner.sendTo(spur.nodes().get(spur.nodes().size() - 1).id());
                    switched = true;
                }
            }
            RouteSim.step(sim, runner, w, t);

            offRoad = Math.max(offRoad, Math.min(off(PathSpline.of(loop), sim), off(PathSpline.of(spur), sim)));
            if (runner.state() == RouteRunner.State.WAITING) break;
        }
        RouteTrace.Measures m = t.measures();
        System.out.printf(Locale.ROOT, "fork at %.1f b/s; %s (%s); off the road at most %.2f b; %s%n", speedAtFork, runner.state(),
                runner.reason(), offRoad, m);
        assertTrue(switched, "it reached the fork");
        assertEquals(RouteRunner.State.WAITING, runner.state(), "and waits at the Farm");
        assertEquals(spur.nodes().get(3).id(), runner.waitingAt());
        assertEquals(0, m.jolts(), "no jolt at the hand-over: " + m);
        assertTrue(offRoad <= 1.0, "on the road through the fork: " + offRoad);
    }

    private static double off(PathSpline sp, MkIvSim sim) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < sp.sampleCount(); i++) {
            best = Math.min(best, Math.hypot(sp.sampleX(i) - sim.x, sp.sampleZ(i) - sim.z));
        }
        return best;
    }
}
