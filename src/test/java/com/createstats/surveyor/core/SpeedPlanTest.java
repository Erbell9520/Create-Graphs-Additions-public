package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedPlanTest {
    private static Pathway line(List<double[]> pts) {
        return Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1)).appendAll(pts);
    }

    @Test
    void aStraightIsCruisedWithRampsAtTheEnds() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{100, 64, 0}));
        SpeedPlan plan = SpeedPlan.of(PathSpline.of(p), p);
        assertEquals(p.cruise(), plan.at(50), 1.0e-9, "cruise in the middle");
        assertEquals(SpeedPlan.CREEP, plan.at(99.5), 1.0e-9, "creeps onto the one-way end");

        double d = 10.0;
        double expected = Math.sqrt(SpeedPlan.CREEP * SpeedPlan.CREEP + 2 * p.comfort().decel * (d - SpeedPlan.APPROACH));
        assertEquals(Math.min(p.cruise(), expected), plan.at(100 - d), 0.15);
    }

    @Test
    void aBendIsTakenAtTheComfortsSidewaysAcceleration() {
        double r = 10;
        List<double[]> pts = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double phi = 2 * Math.PI * i / 24;
            pts.add(new double[]{r * Math.sin(phi), 64, -r * Math.cos(phi)});
        }
        Pathway p = line(pts).withKind(PathKind.LOOP);
        SpeedPlan plan = SpeedPlan.of(PathSpline.of(p), p);
        assertEquals(Math.sqrt(Comfort.NORMAL.lateral * r), plan.at(5.0), 0.2);
        Pathway gentle = p.withComfort(Comfort.GENTLE);
        assertTrue(SpeedPlan.of(PathSpline.of(gentle), gentle).at(5.0) < plan.at(5.0), "gentle corners slower");
    }

    @Test
    void aNodeLimitHoldsForItsStretchOnly() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{40, 64, 0}, new double[]{80, 64, 0}, new double[]{120, 64, 0}));
        int second = p.nodes().get(1).id();
        p = p.update(second, n -> n.withSpeedLimit(3.0));
        SpeedPlan plan = SpeedPlan.of(PathSpline.of(p), p);
        assertEquals(3.0, plan.at(60), 1.0e-9, "limited from node 2 to node 3");
        assertEquals(p.cruise(), plan.at(20), 1.0e-9, "not before it");
        assertTrue(plan.at(39) < p.cruise(), "and braked for ahead of it");
    }

    @Test
    void stopsAreCreptInto() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{40, 64, 0}, new double[]{80, 64, 0}));
        int mid = p.nodes().get(1).id();
        p = p.update(mid, n -> n.withStop(Stop.DEFAULT));
        SpeedPlan plan = SpeedPlan.of(PathSpline.of(p), p);
        assertEquals(SpeedPlan.CREEP, plan.at(39.0), 1.0e-9);
        assertTrue(plan.at(45.0) > SpeedPlan.CREEP, "and pulls away after it");
    }

    @Test
    void aLoopBrakesAtTheEndOfTheLapForABendJustAfterNodeOne() {
        double r = 8;
        List<double[]> pts = new java.util.ArrayList<>();
        pts.add(new double[]{0, 64, 0});
        for (int i = 1; i <= 6; i++) {
            double phi = Math.PI * i / 6.0;
            pts.add(new double[]{r * Math.sin(phi), 64, r - r * Math.cos(phi)});
        }
        for (double x = -8; x >= -60; x -= 8) pts.add(new double[]{x, 64, 2 * r});
        pts.add(new double[]{-60, 64, 0});
        pts.add(new double[]{-30, 64, 0});
        Pathway p = line(pts).withKind(PathKind.LOOP);
        PathSpline sp = PathSpline.of(p);
        SpeedPlan plan = SpeedPlan.of(sp, p);
        assertTrue(plan.at(sp.length() - 1.0) < p.cruise(), "slowing before the seam for the bend after it: " + plan.at(sp.length() - 1));
    }

    private static SpeedPlan.Terrain iceBetween(PathSpline sp, double a, double b) {
        int n = sp.sampleCount();
        double[] grip = new double[n];
        double[] nan = new double[n];
        java.util.Arrays.fill(nan, Double.NaN);
        for (int i = 0; i < n; i++) grip[i] = sp.sampleS(i) >= a && sp.sampleS(i) <= b ? Ground.SLIPPERY.grip : 1.0;
        return new SpeedPlan.Terrain(grip, nan, nan.clone(), nan.clone());
    }

    @Test
    void onIceItBrakesTenTimesAsFarOutForAStop() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{400, 64, 0}, new double[]{500, 64, 0}));
        int mid = p.nodes().get(1).id();
        p = p.update(mid, n -> n.withStop(Stop.DEFAULT)).withCruise(8.0);
        PathSpline sp = PathSpline.of(p);
        Comfort c = p.comfort();
        SpeedPlan dry = SpeedPlan.build(sp, p, p.cruise(), c, null);
        SpeedPlan ice = SpeedPlan.build(sp, p, p.cruise(), c, iceBetween(sp, 100.0, 400.0));
        double dryStart = brakingStarts(dry, p.cruise(), 400.0);
        double iceStart = brakingStarts(ice, p.cruise(), 400.0);
        System.out.printf(java.util.Locale.ROOT, "stop at 400: braking from %.1f dry, %.1f on ice%n", dryStart, iceStart);
        assertTrue((400.0 - iceStart) > 8.0 * (400.0 - dryStart), "ten times as far out, near enough: " + iceStart + " vs " + dryStart);
        assertEquals(SpeedPlan.Cause.STOP, ice.cause(ice.indexAt(300.0)), "still for the stop");
    }

    private static double brakingStarts(SpeedPlan plan, double cruise, double stop) {
        double s = stop;
        while (s > 0.0 && plan.at(s) < cruise - 0.01) s -= 0.25;
        return s;
    }

    @Test
    void aBendOnIceIsTakenAtItsGripsShareAndSaysWhy() {
        double r = 10;
        List<double[]> pts = new java.util.ArrayList<>();
        pts.add(new double[]{-40, 64, 0});
        for (int i = 0; i <= 8; i++) {
            double phi = Math.PI / 2 * i / 8.0;
            pts.add(new double[]{r * Math.sin(phi), 64, r - r * Math.cos(phi)});
        }
        pts.add(new double[]{r, 64, 50});
        Pathway p = line(pts).withCruise(10.0);
        PathSpline sp = PathSpline.of(p);
        double bend = sp.nodeS(4);
        SpeedPlan dry = SpeedPlan.build(sp, p, p.cruise(), p.comfort(), null);
        SpeedPlan ice = SpeedPlan.build(sp, p, p.cruise(), p.comfort(), iceBetween(sp, 0.0, sp.length()));
        assertEquals(dry.at(bend) * Math.sqrt(Ground.SLIPPERY.grip), ice.at(bend), 0.05, "sqrt(grip) of the dry bend's speed");
        assertEquals(SpeedPlan.Cause.SURFACE, ice.cause(ice.indexAt(bend)), "the driver says it is the ice");
    }

    @Test
    void aBumpIsCrawledAndALeanTakenSlower() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{200, 64, 0})).withCruise(10.0);
        PathSpline sp = PathSpline.of(p);
        int n = sp.sampleCount();
        double[] grip = new double[n];
        double[] nan = new double[n];
        double[] cross = new double[n];
        double[] bump = new double[n];
        java.util.Arrays.fill(grip, 1.0);
        java.util.Arrays.fill(nan, Double.NaN);
        java.util.Arrays.fill(cross, 0.0);
        java.util.Arrays.fill(bump, 0.0);
        int b = sp.sampleCount() / 3;
        int l = 2 * sp.sampleCount() / 3;
        bump[b] = 0.5;
        cross[l] = 0.4;
        SpeedPlan plan = SpeedPlan.build(sp, p, p.cruise(), p.comfort(), new SpeedPlan.Terrain(grip, nan, cross, bump));
        assertEquals(SpeedPlan.BUMP_SPEED, plan.sample(b), 1.0e-9, "over the bump at a crawl");
        assertEquals(SpeedPlan.Cause.BUMP, plan.cause(b));
        assertEquals(p.cruise() * SpeedPlan.LEAN_SHARE, plan.sample(l), 1.0e-9, "across the lean slower");
        assertEquals(SpeedPlan.Cause.LEAN, plan.cause(l));
    }

    @Test
    void aTerrainForAnotherVersionIsIgnored() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{100, 64, 0}));
        PathSpline sp = PathSpline.of(p);
        SpeedPlan.Terrain wrong = new SpeedPlan.Terrain(new double[3], new double[3], new double[3], new double[3]);
        SpeedPlan a = SpeedPlan.build(sp, p, p.cruise(), p.comfort(), null);
        SpeedPlan b = SpeedPlan.build(sp, p, p.cruise(), p.comfort(), wrong);
        for (int i = 0; i < sp.sampleCount(); i++) assertEquals(a.sample(i), b.sample(i), 1.0e-12);
    }

    @Test
    void etaAddsUpTheSeconds() {
        Pathway p = line(List.of(new double[]{0, 64, 0}, new double[]{200, 64, 0}));
        SpeedPlan plan = SpeedPlan.of(PathSpline.of(p), p);
        assertEquals(60.0 / p.cruise(), plan.seconds(40, 100), 0.05, "sixty blocks at cruise");
    }
}
