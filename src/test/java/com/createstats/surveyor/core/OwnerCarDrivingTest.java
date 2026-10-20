package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerCarDrivingTest {
    static PlantModel ownerPlant() {
        return new PlantModel(12.03, 0.665, 0.127, 9809, 235, 0.66);
    }

    static PlantModel heavyPlant() {
        return new PlantModel(11.18, 1.66, 0.32, 4962, 173, 0.0);
    }

    static RouteTrace.Measures report(String what, RouteTrace t) {
        RouteTrace.Measures m = t.measures();
        System.out.println(what + ": " + m);
        return m;
    }

    static void assertSmooth(RouteTrace.Measures m, Comfort comfort) {
        assertEquals(0, m.jolts(), "no jolt in normal running: " + m);
        assertTrue(m.surgesPerMinute() <= 2.0, "at most two surges a minute at steady cruise: " + m);
        assertTrue(m.speedErrorRms() <= 0.3, "speed within 0.3 b/s RMS at steady cruise: " + m);
        assertTrue(m.worstSlowing() <= comfort.decel * 1.25 + 1.0e-9, "slowing within the comfort's rate: " + m);
        assertTrue(m.sideChangesPerSecond() < 0.25, "no weaving: " + m);
    }

    @Test
    void carrierOneOnPathwayThreeIsCalm() {
        MkIvSim sim = MkIvSim.carrier().at(268.0, -2.5, 270.0).atY(63.0).noisy(0.15, 0.02);
        Pathway p = OwnerPathways.plaza();
        RouteRunner runner = new RouteRunner(p, ownerPlant());
        RouteTrace t = RouteSim.drive(sim, runner, 20 * 180);
        RouteTrace.Measures m = report("Carrier 1, Pathway 3", t);
        printSpeeds(t, 20 * 20, 20 * 30);
        assertSmooth(m, p.comfort());
        assertTrue(m.brakeApplications() <= 2, "brakes only where the plan slows hard, if at all: " + m);
        assertTrue(m.wheelHuntPerSecond() <= 0.5 || m.straightSeconds() < 1.0, "quiet wheels on straights: " + m);
        assertTrue(m.crossTrackRms() <= 0.35 && m.crossTrackMax() <= 1.0, "on the line: " + m);
    }

    @Test
    void carrierOneAtTwelveOnPathwayTwoHasNoJolt() {
        Pathway p = OwnerPathways.lake();
        MkIvSim sim = MkIvSim.carrier().at(p.nodes().get(0).x() + 2.0, p.nodes().get(0).z() - 0.7, 70.9).atY(63.0).noisy(0.15, 0.02);
        RouteRunner runner = new RouteRunner(p, ownerPlant());
        RouteTrace t = RouteSim.drive(sim, runner, 20 * 150);
        RouteTrace.Measures m = report("Carrier 1, Pathway 2 (no lake in the sim)", t);
        printSpeeds(t, 0, 20 * 60);
        assertSmooth(m, p.comfort());
    }

    @Test
    void theHeavierDayIsCalmToo() {
        MkIvSim sim = MkIvSim.carrierHeavy().at(268.0, -2.5, 270.0).atY(63.0).noisy(0.15, 0.02);
        Pathway p = OwnerPathways.plaza();
        RouteRunner runner = new RouteRunner(p, heavyPlant());
        RouteTrace t = RouteSim.drive(sim, runner, 20 * 180);
        RouteTrace.Measures m = report("Carrier 1 heavy, Pathway 3", t);
        assertSmooth(m, p.comfort());
    }

    static void printSpeeds(RouteTrace t, int from, int to) {
        StringBuilder b = new StringBuilder("  t   speed  goal  thr   brk  state/mode\n");
        for (int i = from; i < Math.min(to, t.size()); i += 10) {
            b.append(String.format(Locale.ROOT, "%5.1f %5.2f %5.2f %5.2f %4.2f %s/%s%n", i / 20.0, t.get(RouteTrace.Ch.SPEED, i),
                    t.get(RouteTrace.Ch.TARGET, i), t.get(RouteTrace.Ch.OUT_THROTTLE, i), t.get(RouteTrace.Ch.OUT_BRAKE, i),
                    RouteRunner.State.byOrdinal((int) t.get(RouteTrace.Ch.STATE, i)),
                    RouteRunner.Mode.byOrdinal((int) t.get(RouteTrace.Ch.MODE, i))));
        }
        System.out.print(b);
    }
}
