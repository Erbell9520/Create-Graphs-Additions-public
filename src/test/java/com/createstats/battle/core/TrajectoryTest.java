package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryTest {
    private static final double[] DRAGS = {0.0, 0.01, 0.011};
    private static final double[] GRAVITIES = {-0.025, -0.05, -0.04};

    private static final double[] X0 = {0.5, 71.25, -7.75};
    private static final double[] V0 = {3.2, 1.7, -2.4};

    private static final double[] X0_FAR = {20_480_013.5, 71.25, -20_480_007.75};

    @Test
    void closedFormEqualsTheIntegratorOver200Ticks() {
        for (double k : DRAGS) {
            for (double g : GRAVITIES) {
                Trajectory t = new Trajectory(g, k, false);
                assertTrue(t.closedForm(), "linear drag below 1 takes the closed form");
                double[] x = X0.clone();
                double[] v = V0.clone();
                double[] px = new double[3];
                double[] pv = new double[3];
                for (int n = 1; n <= 200; n++) {
                    Trajectory.step(x, v, g, k, false);
                    t.position(n, X0, V0, px);
                    t.velocity(n, V0, pv);
                    for (int a = 0; a < 3; a++) {
                        assertEquals(x[a], px[a], 1.0E-9, "position axis " + a + " at tick " + n + " for k " + k + " g " + g);
                        assertEquals(v[a], pv[a], 1.0E-9, "velocity axis " + a + " at tick " + n + " for k " + k + " g " + g);
                    }
                }
            }
        }
    }

    @Test
    void farFromTheOriginOnlyRoundingSeparatesThem() {
        Trajectory t = new Trajectory(-0.025, 0.01, false);
        double[] x = X0_FAR.clone();
        double[] v = V0.clone();
        double[] px = new double[3];
        for (int n = 1; n <= 200; n++) {
            Trajectory.step(x, v, -0.025, 0.01, false);
            t.position(n, X0_FAR, V0, px);
            for (int a = 0; a < 3; a++) {
                assertEquals(x[a], px[a], 1.0E-6, "axis " + a + " at tick " + n + " at plot-sized coordinates");
            }
        }
    }

    @Test
    void tickZeroIsTheStartingPoint() {
        Trajectory t = new Trajectory(-0.05, 0.01, false);
        double[] out = t.position(0, X0, V0, new double[3]);
        assertEquals(X0[0], out[0]);
        assertEquals(X0[1], out[1]);
        assertEquals(X0[2], out[2]);
    }

    @Test
    void reachMatchesTheHorizontalPositionAndInvertsExactly() {
        for (double k : DRAGS) {
            Trajectory t = new Trajectory(-0.025, k, false);
            double v0h = Math.hypot(V0[0], V0[2]);
            double[] p = new double[3];
            for (int n = 1; n <= 200; n++) {
                t.position(n, X0, V0, p);
                double h = Math.hypot(p[0] - X0[0], p[2] - X0[2]);
                assertEquals(h, t.reach(n, v0h), 1.0E-7, "reach at tick " + n + " for k " + k);
                assertEquals(n, t.ticksToReach(t.reach(n, v0h), v0h), 1.0E-7, "inverse at tick " + n + " for k " + k);
            }
        }
    }

    @Test
    void pastTheDragBoundThereIsNoTimeToReach() {
        Trajectory t = new Trajectory(-0.025, 0.01, false);
        double bound = t.reachBound(3.0);
        assertEquals((1.0 - 0.005) * 3.0 / 0.01, bound, 1.0E-9);
        assertTrue(Double.isNaN(t.ticksToReach(bound, 3.0)), "the bound itself is never reached");
        assertTrue(Double.isNaN(t.ticksToReach(bound + 1.0, 3.0)), "past the bound is out of range");
        assertFalse(Double.isNaN(t.ticksToReach(bound - 1.0, 3.0)), "just inside the bound is reachable");
        assertTrue(Double.isInfinite(new Trajectory(-0.025, 0.0, false).reachBound(3.0)), "no drag, no bound");
    }

    @Test
    void quadraticDragStepsTheIntegrator() {
        Trajectory t = new Trajectory(-0.05, 0.002, true);
        assertFalse(t.closedForm(), "quadratic drag has no closed form here");
        double[] x = X0.clone();
        double[] v = V0.clone();
        for (int n = 1; n <= 50; n++) {
            Trajectory.step(x, v, -0.05, 0.002, true);
        }
        double[] p = t.position(50, X0, V0, new double[3]);
        for (int a = 0; a < 3; a++) {
            assertEquals(x[a], p[a], 0.0, "the fallback is the integrator itself");
        }
    }

    @Test
    void dragOfOneOrMoreIsCappedLikeCbc() {
        double[] x = {0, 100, 0};
        double[] v = {2, 0, 0};
        Trajectory.step(x, v, -0.05, 1.5, false);
        assertEquals(0.0, v[0], 1.0E-12, "horizontal speed is removed, never reversed");
        Trajectory t = new Trajectory(-0.05, 1.5, false);
        assertFalse(t.closedForm(), "the cap is not linear, so this steps");
    }
}
