package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFairingTest {
    static double carrierRadius() {
        return MkIvSim.carrier().minRadius() * 1.25;
    }

    static double[] tightest(PathSpline sp) {
        double best = Double.POSITIVE_INFINITY;
        double at = 0.0;
        for (double s = 0.0; s < sp.length(); s += 1.0) {
            double r = PathFairing.tightest(sp, s);
            if (r < best) {
                best = r;
                at = s;
            }
        }
        return new double[]{best, at};
    }

    static double[] planRange(Pathway p) {
        SpeedPlan plan = SpeedPlan.of(PathSpline.of(p), p);
        double lo = Double.POSITIVE_INFINITY;
        double hi = 0.0;
        for (int i = 0; i < plan.size(); i++) {
            lo = Math.min(lo, plan.sample(i));
            hi = Math.max(hi, plan.sample(i));
        }
        return new double[]{lo, hi};
    }

    @Test
    void thePaintedLineIsFairedInOneAction() {
        Pathway before = OwnerPathways.painted();
        PathFairing.Result r = PathFairing.fair(before);
        Pathway after = r.pathway();
        PathSpline a = PathSpline.of(before);
        PathSpline b = PathSpline.of(after);
        double[] pa = planRange(before);
        double[] pb = planRange(after);
        System.out.printf(Locale.ROOT, "painted: %d nodes, tightest radius %.2f, plan %.2f..%.2f%n", r.before(), tightest(a)[0], pa[0], pa[1]);
        System.out.printf(Locale.ROOT, "faired:  %d nodes, tightest radius %.2f, plan %.2f..%.2f (worst %.2f b off, lambda %.1f)%n",
                r.after(), tightest(b)[0], pb[0], pb[1], r.worstDeviation(), r.lambda());
        assertEquals(before.version() + 1, after.version(), "one edit");
        assertTrue(r.worstDeviation() <= PathFairing.TOLERANCE + 1.0e-9, "the fit within the tolerance");

        for (int k = 0; k < before.nodes().size(); k++) {
            if (!PathFairing.anchor(before, k)) continue;
            PathNode n = before.nodes().get(k);
            PathNode m = after.node(n.id());
            assertTrue(m != null && m.x() == n.x() && m.z() == n.z() && m.corner() == n.corner(), "anchor " + n.id() + " kept");
        }
        double worst = 0.0;
        for (int i = 0; i < b.sampleCount(); i += 2) {
            PathProjection.Result q = PathProjection.global(a, b.sampleX(i), b.sampleY(i), b.sampleZ(i), 0.0, 0.0, 4.0, 4.0, 180.0);
            worst = Math.max(worst, q.found() ? Math.abs(q.lateral()) : 99.0);
        }
        assertTrue(worst <= PathFairing.TOLERANCE + PathFairing.FIT + 0.1, "the stored line stays on the old one: " + worst);
        assertTrue(pb[0] >= pa[0] - 1.0e-9, "the plan's slowest part no slower: " + pb[0] + " against " + pa[0]);
    }

    @Test
    void aTightCornerWithRoomIsOpenedToTheCarsTurn() {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (double x = -40; x < -2; x += 10) pts.add(new double[]{x, 64, 0});
        pts.add(new double[]{-1.5, 64, 0});
        pts.add(new double[]{0, 64, 1.5});
        for (double z = 3; z <= 40; z += 10) pts.add(new double[]{0, 64, z});
        Pathway p = RouteFollowerSimTest.pathway(pts, PathKind.ONE_WAY);
        double[] before = tightest(PathSpline.of(p));
        PathFairing.Widened w = PathFairing.widen(p, before[1], carrierRadius(), null);
        assertNotNull(w, "a corner with room can be opened");
        double[] after = tightest(PathSpline.of(w.pathway()));
        System.out.printf(Locale.ROOT, "corner: radius %.2f widened to %.2f (asked %.2f)%n", before[0], after[0], carrierRadius());
        assertTrue(after[0] >= carrierRadius() * 0.9, "open to the car's turn: " + after[0]);
    }

    @Test
    void aHairpinBetweenCloseLegsIsDeclined() {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (double x = -30; x < 0; x += 10) pts.add(new double[]{x, 64, 0});
        pts.add(new double[]{3, 64, 1});
        pts.add(new double[]{3, 64, 5});
        for (double x = 0; x >= -30; x -= 10) pts.add(new double[]{x, 64, 6});
        Pathway p = RouteFollowerSimTest.pathway(pts, PathKind.ONE_WAY);
        double[] before = tightest(PathSpline.of(p));
        PathFairing.Widened w = PathFairing.widen(p, before[1], carrierRadius(), null);
        System.out.printf(Locale.ROOT, "hairpin: radius %.2f; widen %s%n", before[0], w == null ? "declined" : String.format(Locale.ROOT, "to %.2f", w.radius()));
        assertTrue(w == null || w.radius() < carrierRadius() * 0.95, "no fix claims to have opened it");
    }

    @Test
    void theTeardropBecomesABalloonTheCarCanTurn() {
        Pathway p = PathFairing.fair(OwnerPathways.painted()).pathway();
        PathSpline sp = PathSpline.of(p);
        double own = MkIvSim.carrier().minRadius();
        TightBends.Bend tear = null;
        for (TightBends.Bend b : TightBends.of(p, sp, own).bends()) if (tear == null || b.cut() > tear.cut()) tear = b;
        assertNotNull(tear, "Carrier 1 cannot follow the painted teardrop");
        double mid = tear.middle(sp);
        PathFairing.Widened w = PathFairing.widen(p, mid, own * 1.1, null);
        System.out.printf(Locale.ROOT, "teardrop: radius %.2f, cut %.1f; widen %s%n", tear.radius(), tear.cut(),
                w == null ? "declined" : String.format(Locale.ROOT, "to %.2f", w.radius()));
        assertNotNull(w, "a balloon fits");
        assertTrue(w.radius() >= own * 0.95, "within a hand of the car's turn: " + w.radius());
        PathSpline ws = PathSpline.of(w.pathway());
        for (TightBends.Bend b : TightBends.of(w.pathway(), ws, own).bends()) {
            double m = b.middle(ws);
            assertTrue(Math.hypot(ws.x(m) - sp.x(mid), ws.z(m) - sp.z(mid)) > 8.0, "no bend it cannot follow left there: " + b);
        }
        assertTrue(PathFairing.shift(sp, ws) <= PathFairing.MAX_SHIFT, "and the line moves no further than Fair allows");
    }

    @Test
    void carrierOneDrivesThePaintedLineFairedForIt() {
        PathFairing.Drivable d = PathFairing.fairFor(OwnerPathways.painted(), MkIvSim.carrier().minRadius(), null);
        Pathway p = d.pathway();
        System.out.printf(Locale.ROOT, "fair for Carrier 1: %d bends opened, %d left, tightest %.2f%n", d.opened(), d.left().size(),
                tightest(PathSpline.of(p))[0]);
        assertTrue(d.left().isEmpty(), "every bend it cannot follow opened: " + d.left());
        PathNode n1 = p.nodes().get(0);
        PathNode n2 = p.nodes().get(1);
        double heading = Math.toDegrees(PathSpline.heading(n2.x() - n1.x(), n2.z() - n1.z()));
        MkIvSim sim = MkIvSim.carrier().at(n1.x(), n1.z(), heading).atY(63.0).noisy(0.15, 0.02);
        RouteRunner runner = new RouteRunner(p, OwnerCarDrivingTest.ownerPlant());
        double lap = PathSpline.of(p).length();
        RouteTrace t = RouteSim.drive(sim, runner, (int) (20 * (3 * lap / 3.0 + 60)));
        RouteTrace.Measures m = t.measures();
        System.out.println("Carrier 1, Pathway 1 faired for it: " + runner.state() + " (" + runner.reason() + "); " + m);
        assertTrue(runner.state().drives(), "still driving: " + runner.state() + " " + runner.reason());
        assertEquals(0, m.jolts(), "no jolt: " + m);
        assertTrue(m.surgesPerMinute() <= 2.0, "a steady speed round it: " + m);
        assertTrue(m.crossTrackMax() <= 1.0, "on the line: " + m);
    }

    @Test
    void carrierOneStopsShortOfTheTeardropAndSaysWhy() {
        Pathway p = PathFairing.fair(OwnerPathways.painted()).pathway();
        PathNode n1 = p.nodes().get(0);
        PathNode n2 = p.nodes().get(1);
        double heading = Math.toDegrees(PathSpline.heading(n2.x() - n1.x(), n2.z() - n1.z()));
        MkIvSim sim = MkIvSim.carrier().at(n1.x(), n1.z(), heading).atY(63.0).noisy(0.15, 0.02);
        RouteRunner runner = new RouteRunner(p, OwnerCarDrivingTest.ownerPlant());
        RouteTrace t = RouteSim.drive(sim, runner, 20 * 150);
        RouteTrace.Measures m = t.measures();
        System.out.println("Carrier 1, Pathway 1 faired: " + runner.state() + " (" + runner.reason() + "); " + m);
        assertEquals(RouteRunner.State.HAZARD, runner.state(), "it stops short rather than getting lost in the teardrop");
        assertTrue(runner.reason().contains("tighter than it can turn"), runner.reason());
        assertEquals(0, m.jolts(), "no jolt: " + m);
        assertTrue(m.surgesPerMinute() <= 2.0, "steady on the way: " + m);
        assertTrue(m.crossTrackMax() <= 1.0, "on the line until it stops: " + m);
    }

    @Test
    void aStraightStaysStraightAndAnchorsHold() {
        Pathway p = Pathway.create(new java.util.UUID(1, 1), "t", 3, new java.util.UUID(2, 2));
        java.util.Random rnd = new java.util.Random(7);
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int i = 0; i <= 40; i++) pts.add(new double[]{i * 2.0, 64.0, (rnd.nextDouble() - 0.5) * 0.8});
        p = p.appendAll(pts);
        PathFairing.Result r = PathFairing.fair(p);
        PathSpline sp = PathSpline.of(r.pathway());
        double worstZ = 0.0;
        for (int i = 0; i < sp.sampleCount(); i++) worstZ = Math.max(worstZ, Math.abs(sp.sampleZ(i)));
        System.out.printf(Locale.ROOT, "wobbly straight: %d nodes to %d, worst |z| %.2f%n", r.before(), r.after(), worstZ);
        assertTrue(r.after() < r.before() / 2, "a wobbly straight needs few nodes: " + r.after());
        assertTrue(worstZ <= 0.45, "and lies within the wobble: " + worstZ);
        assertEquals(p.nodes().get(0).x(), r.pathway().nodes().get(0).x(), "its ends held");
        assertEquals(p.nodes().get(p.nodes().size() - 1).x(), r.pathway().nodes().get(r.pathway().nodes().size() - 1).x());
    }

    @Test
    void aWideEnoughBendIsLeftAlone() {
        Pathway p = RouteFollowerSimTest.pathway(RouteFollowerSimTest.oval(40, 20, 64), PathKind.LOOP);
        PathSpline sp = PathSpline.of(p);
        double bend = sp.nodeS(8);
        assertEquals(null, PathFairing.widen(p, bend, 6.0, null), "nothing to widen");
    }
}
