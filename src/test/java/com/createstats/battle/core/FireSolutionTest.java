package com.createstats.battle.core;

import com.createstats.battle.core.FireSolution.Answer;
import com.createstats.battle.core.FireSolution.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireSolutionTest {
    private static final double[] MUZZLE = {12.5, 80.25, -40.75};

    private static final double V0 = 6.0;

    private static final double MIN = -30.0;
    private static final double MAX = 60.0;

    private static final double[][] STOCK = {{-0.025, 0.0}, {-0.025, 0.01}, {-0.05, 0.0}, {-0.05, 0.01}};

    private static final double HIT = 0.05;

    @Test
    void stillTargetsAt100To300BlocksLevelAboveAndBelowAreHitByTheSteppedShell() {
        for (double[] gk : STOCK) {
            Trajectory shell = new Trajectory(gk[0], gk[1], false);
            for (double range : new double[]{100.0, 200.0, 300.0}) {
                for (double dh : new double[]{0.0, 20.0, -20.0}) {
                    double[] target = {MUZZLE[0] + 0.6 * range, MUZZLE[1] + dh, MUZZLE[2] - 0.8 * range};
                    Answer a = FireSolution.solve(shell, V0, MUZZLE, target, null, MIN, MAX, null);
                    String what = "g " + gk[0] + " k " + gk[1] + " range " + range + " dh " + dh;
                    assertEquals(Kind.OK, a.kind(), what);
                    assertEquals(Math.toDegrees(Math.atan2(-0.6, -0.8)), a.yaw(), 1.0E-9, what);
                    double[] at = shellAt(shell, V0, a.yaw(), a.pitch(), a.flightTicks());
                    assertTrue(distance(at, target) < HIT, what + " missed by " + distance(at, target));
                    assertArrayEquals(target, a.intercept(), 1.0E-9, "a still target is its own intercept, " + what);
                    assertEquals(distance(MUZZLE, target), a.range(), 1.0E-9, what);
                    assertEquals(0.0, a.needed(), 0.0, what);
                }
            }
        }
    }

    @Test
    void aCrossingTargetIsMetWhereItWillBe() {
        double[] vel = {0.3, 0.0, 0.4};
        for (double[] gk : STOCK) {
            Trajectory shell = new Trajectory(gk[0], gk[1], false);
            double[] p0 = {MUZZLE[0] - 150.0, MUZZLE[1] + 10.0, MUZZLE[2] + 90.0};
            Answer a = FireSolution.solve(shell, V0, MUZZLE, p0, vel, MIN, MAX, null);
            String what = "g " + gk[0] + " k " + gk[1];
            assertEquals(Kind.OK, a.kind(), what);
            double t = a.flightTicks();
            double[] where = {p0[0] + vel[0] * t, p0[1] + vel[1] * t, p0[2] + vel[2] * t};
            double[] at = shellAt(shell, V0, a.yaw(), a.pitch(), t);
            assertTrue(distance(at, where) < HIT, what + " missed by " + distance(at, where));

            double yawNow = FireSolution.yawOf(p0[0] - MUZZLE[0], p0[2] - MUZZLE[2]);
            assertTrue(Math.abs(FireSolution.wrap(a.yaw(), yawNow)) > 3.0, what + " leads the crossing target");
            assertTrue(a.leadDeg() > 3.0, what);
        }

        Trajectory shell = new Trajectory(-0.05, 0.01, false);
        double[] p0 = {MUZZLE[0] + 80.0, MUZZLE[1] - 15.0, MUZZLE[2] + 180.0};
        double[] climb = {-0.2, 0.15, -0.35};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, p0, climb, MIN, MAX, null);
        assertEquals(Kind.OK, a.kind());
        double t = a.flightTicks();
        double[] where = {p0[0] + climb[0] * t, p0[1] + climb[1] * t, p0[2] + climb[2] * t};
        assertTrue(distance(shellAt(shell, V0, a.yaw(), a.pitch(), t), where) < HIT);
    }

    @Test
    void pastMaxRangeIsOutOfRangeWithTheReachAsNeeded() {
        Trajectory shell = new Trajectory(-0.05, 0.01, false);
        double reach = FireSolution.maxRange(shell, V0, MIN, MAX);
        double[] far = {MUZZLE[0] + 1.1 * reach, MUZZLE[1], MUZZLE[2]};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, far, null, MIN, MAX, null);
        assertEquals(Kind.OUT_OF_RANGE, a.kind());
        assertEquals(reach, a.needed(), 0.0, "needed is maxRange itself");
        assertEquals(FireSolution.maxRangePitch(shell, V0, MIN, MAX), a.pitch(), 0.0, "held at the best elevation");
        assertTrue(Double.isNaN(a.flightTicks()));
        assertEquals(1.1 * reach, a.range(), 1.0E-9, "range is the distance now");
        assertEquals(-90.0, a.yaw(), 1.0E-9, "due +X is yaw -90");

        double[] near = {MUZZLE[0] + reach - 0.5, MUZZLE[1], MUZZLE[2]};
        Answer in = FireSolution.solve(shell, V0, MUZZLE, near, null, MIN, MAX, null);
        assertEquals(Kind.OK, in.kind());
        assertTrue(distance(shellAt(shell, V0, in.yaw(), in.pitch(), in.flightTicks()), near) < HIT);

        double[] edge = {MUZZLE[0], MUZZLE[1], MUZZLE[2] + reach - 10.0};
        Answer fleeing = FireSolution.solve(shell, V0, MUZZLE, edge, new double[]{0.0, 0.0, 0.5}, MIN, MAX, null);
        assertEquals(Kind.OUT_OF_RANGE, fleeing.kind());
        assertEquals(reach - 10.0, fleeing.range(), 1.0E-9);
    }

    @Test
    void aTargetFortyDegreesDownWithAThirtyDegreeLimitIsOutOfArc() {
        Trajectory shell = new Trajectory(-0.05, 0.01, false);
        double d = 40.0;
        double drop = d * Math.tan(Math.toRadians(40.0));
        double[] target = {MUZZLE[0], MUZZLE[1] - drop, MUZZLE[2] + d};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, target, null, -30.0, MAX, null);
        assertEquals(Kind.OUT_OF_ARC, a.kind());
        assertEquals(-30.0, a.pitch(), 0.0, "held at the limit that was hit");
        assertEquals(0.0, a.yaw(), 1.0E-9, "due +Z is yaw 0");
        assertTrue(Double.isNaN(a.flightTicks()));
        assertEquals(Math.hypot(d, drop), a.range(), 1.0E-9);

        assertTrue(a.needed() < -30.0 && a.needed() > -40.0, "needed " + a.needed());

        assertEquals(-drop, steppedHeightAt(shell, V0, a.needed(), d), HIT);

        Answer ok = FireSolution.solve(shell, V0, MUZZLE, target, null, -45.0, MAX, null);
        assertEquals(Kind.OK, ok.kind());
        assertEquals(a.needed(), ok.pitch(), 1.0E-6);
    }

    @Test
    void aTargetAboveTheElevationLimitIsOutOfArcAtThatLimit() {
        Trajectory shell = new Trajectory(-0.025, 0.01, false);
        double[] target = {MUZZLE[0] - 150.0, MUZZLE[1] + 60.0, MUZZLE[2]};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, target, null, MIN, 20.0, null);
        assertEquals(Kind.OUT_OF_ARC, a.kind());
        assertEquals(20.0, a.pitch(), 0.0);
        assertEquals(90.0, a.yaw(), 1.0E-9, "due -X is yaw 90");
        assertTrue(a.needed() > 21.8, "needed " + a.needed());
        assertEquals(60.0, steppedHeightAt(shell, V0, a.needed(), 150.0), HIT);

        Answer again = FireSolution.solve(shell, V0, MUZZLE, target, null, MIN, 20.0, a);
        assertEquals(Kind.OUT_OF_ARC, again.kind());
        assertEquals(a.needed(), again.needed(), 1.0E-6);
    }

    @Test
    void theLowArcIsChosen() {
        Trajectory shell = new Trajectory(-0.05, 0.0, false);
        double[] target = {MUZZLE[0], MUZZLE[1], MUZZLE[2] + 500.0};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, target, null, MIN, 89.0, null);
        double low = 0.5 * Math.toDegrees(Math.asin(500.0 * 0.05 / 36.0));
        assertEquals(Kind.OK, a.kind());
        assertEquals(low, a.pitch(), 1.0E-6);

        assertEquals(0.0, steppedHeightAt(shell, V0, 90.0 - low, 500.0), 0.05);

        Trajectory dragged = new Trajectory(-0.05, 0.01, false);
        double[] mid = {MUZZLE[0], MUZZLE[1], MUZZLE[2] + 250.0};
        Answer b = FireSolution.solve(dragged, V0, MUZZLE, mid, null, MIN, 89.0, null);
        assertEquals(Kind.OK, b.kind());
        assertTrue(b.pitch() < FireSolution.maxRangePitch(dragged, V0, MIN, 89.0), "pitch " + b.pitch());
    }

    @Test
    void aWarmStartConvergesToTheSameAnswerInAtMostTwoPasses() {
        Trajectory shell = new Trajectory(-0.025, 0.01, false);
        double[] p0 = {MUZZLE[0] + 120.0, MUZZLE[1] + 15.0, MUZZLE[2] + 160.0};
        double[] vel = {-0.4, 0.05, 0.2};
        int[] passes = new int[1];
        Answer cold = FireSolution.solve(shell, V0, MUZZLE, p0, vel, MIN, MAX, null, passes);
        int coldPasses = passes[0];
        assertEquals(Kind.OK, cold.kind());

        Answer same = FireSolution.solve(shell, V0, MUZZLE, p0, vel, MIN, MAX, cold, passes);
        assertEquals(1, passes[0]);
        assertEquals(cold.pitch(), same.pitch(), 0.01);
        assertEquals(cold.yaw(), same.yaw(), 0.01);

        Answer warm = cold;
        for (int tick = 2; tick <= 40; tick += 2) {
            double[] now = {p0[0] + vel[0] * tick, p0[1] + vel[1] * tick, p0[2] + vel[2] * tick};
            warm = FireSolution.solve(shell, V0, MUZZLE, now, vel, MIN, MAX, warm, passes);
            assertTrue(passes[0] <= 2, "tick " + tick + " took " + passes[0] + " passes");
            Answer fresh = FireSolution.solve(shell, V0, MUZZLE, now, vel, MIN, MAX, null);
            assertEquals(fresh.pitch(), warm.pitch(), 0.01, "tick " + tick);
            assertEquals(fresh.yaw(), warm.yaw(), 0.01, "tick " + tick);
            assertEquals(fresh.flightTicks(), warm.flightTicks(), 0.05, "tick " + tick);
            double t = warm.flightTicks();
            double[] where = {now[0] + vel[0] * t, now[1] + vel[1] * t, now[2] + vel[2] * t};
            assertTrue(distance(shellAt(shell, V0, warm.yaw(), warm.pitch(), t), where) < HIT, "tick " + tick);
        }
        assertTrue(coldPasses > 2, "the cold start needed " + coldPasses + " passes, so the warm start saved some");
    }

    @Test
    void theSolversHeightIsTrajectorysClosedFormAndItsSlopeTheHeightsDerivative() {
        double[][] shells = {{-0.025, 0.0}, {-0.05, 0.01}, {-0.055, 0.011}};
        double[] zero = {0.0, 0.0, 0.0};
        for (double[] gk : shells) {
            Trajectory shell = new Trajectory(gk[0], gk[1], false);
            FireSolution.Shot shot = new FireSolution.Shot(shell, V0);
            for (double deg : new double[]{-40.0, -10.0, 0.0, 10.0, 25.0, 40.0, 60.0}) {
                double th = Math.toRadians(deg);
                double[] v = {V0 * Math.cos(th), V0 * Math.sin(th), 0.0};
                for (int n : new int[]{1, 7, 40, 120}) {
                    double d = shell.reach(n, v[0]);
                    double[] p = shell.position(n, zero, v, new double[3]);
                    String what = "g " + gk[0] + " k " + gk[1] + " at " + deg + " degrees, tick " + n;
                    assertEquals(p[1], shot.height(th, d), 1.0E-9, what);
                    assertEquals(n, shot.ticks, 1.0E-9, what);
                }

                for (double d : new double[]{5.0, 80.0, 200.0}) {
                    double e = 1.0E-6;
                    double up = shot.height(th + e, d);
                    double down = shot.height(th - e, d);
                    if (!Double.isFinite(up) || !Double.isFinite(down)) continue;
                    double quotient = (up - down) / (2.0 * e);
                    assertEquals(quotient, shot.slope(th, d), 1.0E-5 * Math.max(1.0, Math.abs(quotient)),
                            "slope for g " + gk[0] + " k " + gk[1] + " at " + deg + " degrees, reach " + d);
                }
            }
        }
    }

    @Test
    void yawAndPitchFollowMinecraftsConvention() {
        assertArrayEquals(new double[]{0, 0, 1}, FireSolution.direction(0, 0), 1.0E-12);
        assertArrayEquals(new double[]{-1, 0, 0}, FireSolution.direction(90, 0), 1.0E-12);
        assertArrayEquals(new double[]{0, 0, -1}, FireSolution.direction(180, 0), 1.0E-12);
        assertArrayEquals(new double[]{1, 0, 0}, FireSolution.direction(-90, 0), 1.0E-12);
        assertArrayEquals(new double[]{0, 1, 0}, FireSolution.direction(37, 90), 1.0E-12, "pitch is positive up");
        double r = Math.sqrt(0.5);
        assertArrayEquals(new double[]{0, -r, r}, FireSolution.direction(0, -45), 1.0E-12);
        assertEquals(0.0, FireSolution.yawOf(0, 1), 0.0);
        assertEquals(90.0, FireSolution.yawOf(-1, 0), 0.0);
        assertEquals(-90.0, FireSolution.yawOf(1, 0), 0.0);
        assertEquals(180.0, FireSolution.yawOf(0, -1), 0.0);
        assertEquals(180.0, FireSolution.yawOf(-0.0, -1), 0.0, "(-180, 180]: never -180");
        assertEquals(0.0, FireSolution.yawOf(0, 0), 0.0);
        assertEquals(45.0, FireSolution.pitchOf(1, 1, 0), 1.0E-12);
        assertEquals(-30.0, FireSolution.pitchOf(0, -1, Math.sqrt(3.0)), 1.0E-12);

        Trajectory straight = new Trajectory(0.0, 0.0, false);
        Answer north = FireSolution.solve(straight, 2.0, MUZZLE,
                new double[]{MUZZLE[0], MUZZLE[1] + 10.0, MUZZLE[2] + 50.0}, null, MIN, MAX, null);
        assertEquals(0.0, north.yaw(), 1.0E-9, "+Z");
        assertEquals(Math.toDegrees(Math.atan2(10.0, 50.0)), north.pitch(), 1.0E-9);
        assertEquals(0.0, north.leadDeg(), 1.0E-9);
        assertEquals(Math.hypot(10.0, 50.0) / 2.0, north.flightTicks(), 1.0E-9);
        Answer west = FireSolution.solve(straight, 2.0, MUZZLE,
                new double[]{MUZZLE[0] - 50.0, MUZZLE[1], MUZZLE[2]}, null, MIN, MAX, null);
        assertEquals(90.0, west.yaw(), 1.0E-9, "-X");
    }

    @Test
    void wrapAndAngleAreExactAtTheirEdges() {
        assertEquals(-20.0, FireSolution.wrap(170, -170), 1.0E-12);
        assertEquals(20.0, FireSolution.wrap(-170, 170), 1.0E-12);
        assertEquals(180.0, FireSolution.wrap(180, 0), 0.0);
        assertEquals(180.0, FireSolution.wrap(-180, 0), 0.0, "(-180, 180]");
        assertEquals(180.0, FireSolution.wrap(540, 0), 0.0);
        assertEquals(0.0, FireSolution.wrap(10, 370), 1.0E-12);
        assertEquals(90.0, FireSolution.angleDeg(new double[]{1, 0, 0}, new double[]{0, 3, 0}), 1.0E-12);
        assertEquals(180.0, FireSolution.angleDeg(new double[]{1, 0, 0}, new double[]{-2, 0, 0}), 1.0E-12);
        assertEquals(0.0, FireSolution.angleDeg(new double[]{1, 0, 0}, new double[]{0, 0, 0}), 0.0);

        assertEquals(Math.toDegrees(1.0E-9), FireSolution.angleDeg(new double[]{1, 0, 0}, new double[]{1, 1.0E-9, 0}),
                1.0E-15);
    }

    @Test
    void maxRangeWithoutDragIsTheParabolaTheIntegratorFlies() {
        Trajectory shell = new Trajectory(-0.05, 0.0, false);
        assertEquals(720.0, FireSolution.maxRange(shell, V0, MIN, MAX), 1.0E-9);
        assertEquals(45.0, FireSolution.maxRangePitch(shell, V0, MIN, MAX), 0.0);
        assertEquals(720.0 * Math.sin(Math.toRadians(60.0)), FireSolution.maxRange(shell, V0, MIN, 30.0), 1.0E-9,
                "capped at 30 degrees: 623.5");
        assertEquals(30.0, FireSolution.maxRangePitch(shell, V0, MIN, 30.0), 0.0);
        assertEquals(720.0 * Math.sin(Math.toRadians(100.0)), FireSolution.maxRange(shell, V0, 50.0, MAX), 1.0E-9,
                "floored at 50 degrees: 709.1");
        assertEquals(0.0, FireSolution.maxRange(shell, V0, MIN, -5.0), 0.0, "a shot never above level never returns");

        assertEquals(720.0, steppedLevelReach(shell, V0, 45.0), 0.01);

        assertEquals(1440.0, FireSolution.maxRange(new Trajectory(-0.025, 0.0, false), V0, MIN, MAX), 1.0E-9);
    }

    @Test
    void maxRangeWithDragMatchesABruteForceSearchOfTheIntegrator() {
        double[][] shells = {{-0.025, 0.01}, {-0.05, 0.01}, {-0.055, 0.011}};
        for (double[] gk : shells) {
            Trajectory shell = new Trajectory(gk[0], gk[1], false);
            double brute = 0.0;
            for (int i = 1; i < 1800; i++) {
                brute = Math.max(brute, steppedLevelReach(shell, V0, i * 0.05));
            }
            assertEquals(brute, FireSolution.maxRange(shell, V0, 0.0, 90.0), 0.05, "g " + gk[0] + " k " + gk[1]);
        }

        assertEquals(335.5, FireSolution.maxRange(new Trajectory(-0.05, 0.01, false), V0, MIN, MAX), 0.05);
    }

    @Test
    void theEndAndTheNetherChangeTheAnswerAsTheyShould() {
        Trajectory overworld = new Trajectory(-0.05, 0.01, false);
        Trajectory end = new Trajectory(-0.05 * 0.8, 0.0, false);
        Trajectory nether = new Trajectory(-0.05 * 1.1, 0.01 * 1.1, false);
        double[] target = {MUZZLE[0] + 140.0, MUZZLE[1], MUZZLE[2] + 140.0};
        Answer o = FireSolution.solve(overworld, V0, MUZZLE, target, null, MIN, MAX, null);
        Answer e = FireSolution.solve(end, V0, MUZZLE, target, null, MIN, MAX, null);
        Answer n = FireSolution.solve(nether, V0, MUZZLE, target, null, MIN, MAX, null);
        for (Answer a : new Answer[]{o, e, n}) {
            assertEquals(Kind.OK, a.kind());
        }
        assertTrue(distance(shellAt(overworld, V0, o.yaw(), o.pitch(), o.flightTicks()), target) < HIT);
        assertTrue(distance(shellAt(end, V0, e.yaw(), e.pitch(), e.flightTicks()), target) < HIT);
        assertTrue(distance(shellAt(nether, V0, n.yaw(), n.pitch(), n.flightTicks()), target) < HIT);
        assertTrue(e.pitch() < o.pitch() && o.pitch() < n.pitch(), e.pitch() + " " + o.pitch() + " " + n.pitch());
        assertTrue(FireSolution.maxRange(end, V0, MIN, MAX) > FireSolution.maxRange(overworld, V0, MIN, MAX));
        assertTrue(FireSolution.maxRange(nether, V0, MIN, MAX) < FireSolution.maxRange(overworld, V0, MIN, MAX));
    }

    @Test
    void aQuadraticDragShellStillSolvesByStepping() {
        Trajectory shell = new Trajectory(-0.05, 0.002, true);
        double[][] targets = {
                {MUZZLE[0] + 80.0, MUZZLE[1], MUZZLE[2]},
                {MUZZLE[0] - 100.0, MUZZLE[1] + 15.0, MUZZLE[2] + 110.0},
                {MUZZLE[0], MUZZLE[1] - 25.0, MUZZLE[2] - 60.0},
        };
        for (double[] target : targets) {
            Answer a = FireSolution.solve(shell, V0, MUZZLE, target, null, MIN, MAX, null);
            assertEquals(Kind.OK, a.kind());
            assertTrue(distance(shellAt(shell, V0, a.yaw(), a.pitch(), a.flightTicks()), target) < HIT);

            int[] passes = new int[1];
            Answer again = FireSolution.solve(shell, V0, MUZZLE, target, null, MIN, MAX, a, passes);
            assertEquals(1, passes[0]);
            assertEquals(a.pitch(), again.pitch(), 1.0E-6);
        }
        double[] p0 = {MUZZLE[0] + 90.0, MUZZLE[1] + 5.0, MUZZLE[2] + 60.0};
        double[] vel = {0.1, 0.0, -0.4};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, p0, vel, MIN, MAX, null);
        assertEquals(Kind.OK, a.kind());
        double t = a.flightTicks();
        double[] where = {p0[0] + vel[0] * t, p0[1] + vel[1] * t, p0[2] + vel[2] * t};
        assertTrue(distance(shellAt(shell, V0, a.yaw(), a.pitch(), t), where) < HIT);

        double brute = 0.0;
        for (int i = 1; i < 1800; i++) {
            brute = Math.max(brute, steppedLevelReach(shell, V0, i * 0.05));
        }
        double reach = FireSolution.maxRange(shell, V0, 0.0, 90.0);
        assertEquals(brute, reach, 0.05);
        Answer far = FireSolution.solve(shell, V0, MUZZLE, new double[]{MUZZLE[0], MUZZLE[1], MUZZLE[2] + reach + 5},
                null, 0.0, 90.0, null);
        assertEquals(Kind.OUT_OF_RANGE, far.kind());
    }

    @Test
    void dragOfOneOrMoreStopsTheShellInItsFirstTick() {
        Trajectory shell = new Trajectory(-0.05, 1.5, false);
        double[] near = {MUZZLE[0], MUZZLE[1], MUZZLE[2] + 2.0};
        Answer a = FireSolution.solve(shell, V0, MUZZLE, near, null, MIN, MAX, null);
        assertEquals(Kind.OK, a.kind());
        assertTrue(distance(shellAt(shell, V0, a.yaw(), a.pitch(), a.flightTicks()), near) < HIT);
        double[] far = {MUZZLE[0], MUZZLE[1], MUZZLE[2] + 4.0};
        assertEquals(Kind.OUT_OF_RANGE, FireSolution.solve(shell, V0, MUZZLE, far, null, MIN, MAX, null).kind());
    }

    private static double[] shellAt(Trajectory shell, double v0, double yaw, double pitch, double ticks) {
        double[] dir = FireSolution.direction(yaw, pitch);
        double[] x = MUZZLE.clone();
        double[] v = {dir[0] * v0, dir[1] * v0, dir[2] * v0};
        int whole = (int) Math.floor(ticks);
        for (int i = 0; i < whole; i++) {
            Trajectory.step(x, v, shell.gravity(), shell.drag(), shell.quadratic());
        }
        double[] next = x.clone();
        Trajectory.step(next, v.clone(), shell.gravity(), shell.drag(), shell.quadratic());
        double f = ticks - whole;
        return new double[]{x[0] + f * (next[0] - x[0]), x[1] + f * (next[1] - x[1]), x[2] + f * (next[2] - x[2])};
    }

    private static double steppedHeightAt(Trajectory shell, double v0, double pitch, double d) {
        double[] x = new double[3];
        double[] v = {v0 * Math.cos(Math.toRadians(pitch)), v0 * Math.sin(Math.toRadians(pitch)), 0.0};
        for (int i = 0; i < Trajectory.MAX_STEPS; i++) {
            double h0 = x[0];
            double y0 = x[1];
            Trajectory.step(x, v, shell.gravity(), shell.drag(), shell.quadratic());
            if (x[0] >= d) {
                return y0 + (d - h0) / (x[0] - h0) * (x[1] - y0);
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    private static double steppedLevelReach(Trajectory shell, double v0, double pitch) {
        double[] x = new double[3];
        double[] v = {v0 * Math.cos(Math.toRadians(pitch)), v0 * Math.sin(Math.toRadians(pitch)), 0.0};
        for (int i = 0; i < Trajectory.MAX_STEPS; i++) {
            double h0 = x[0];
            double y0 = x[1];
            Trajectory.step(x, v, shell.gravity(), shell.drag(), shell.quadratic());
            if (x[1] < 0.0) {
                return h0 + y0 / (y0 - x[1]) * (x[0] - h0);
            }
        }
        return x[0];
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
