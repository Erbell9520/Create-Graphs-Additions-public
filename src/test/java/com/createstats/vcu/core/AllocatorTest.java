package com.createstats.vcu.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocatorTest {
    private static final BodyFrame NORTH = BodyFrame.of(new V3(0, 0, -1), V3.UNIT_Y, V3.ZERO);
    private static final V3 AHEAD = new V3(0, 0, -1);

    private static double[][] columns(List<V3> positions, List<V3> forces) {
        double[][] b = new double[Axis6.COUNT][positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            double[] w = NORTH.wrench(positions.get(i), forces.get(i));
            for (int a = 0; a < Axis6.COUNT; a++) b[a][i] = w[a];
        }
        return b;
    }

    private static double[] demand(Axis6 axis, double value) {
        double[] d = new double[Axis6.COUNT];
        d[axis.index()] = value;
        return d;
    }

    private static double[] ones() {
        double[] s = new double[Axis6.COUNT];
        java.util.Arrays.fill(s, 1.0);
        return s;
    }

    @Test
    void frameConventionsMatchAxis6() {
        assertTrue(NORTH.wrench(new V3(2, 0, 0), new V3(0, -1, 0))[Axis6.ROLL.index()] > 0);

        assertTrue(NORTH.wrench(new V3(0, 0, -2), V3.UNIT_Y)[Axis6.PITCH.index()] > 0);

        assertTrue(NORTH.wrench(new V3(0, 0, -2), V3.UNIT_X)[Axis6.YAW.index()] > 0);
    }

    @Test
    void twinEngineYawsDifferentiallyWithoutPushing() {
        double[][] b = columns(List.of(new V3(-2, 0, 0), new V3(2, 0, 0)), List.of(AHEAD, AHEAD));
        double[] lo = {-1, -1};
        double[] hi = {1, 1};
        Allocator.Result r = Allocator.fly(b, demand(Axis6.YAW, 1.0), ones(), lo, hi);
        double left = r.commands()[0];
        double right = r.commands()[1];
        assertTrue(left > 0.5, "left engine pushes to turn right, got " + left);
        assertTrue(right < -0.5, "right engine reverses, got " + right);
        assertEquals(0.0, r.achieved()[Axis6.FORWARD.index()], 1.0e-6);
    }

    @Test
    void nonReversibleYawPinsTheInsideEngineAtIdle() {
        double[][] b = columns(List.of(new V3(-2, 0, 0), new V3(2, 0, 0)), List.of(AHEAD, AHEAD));
        Allocator.Result r = Allocator.fly(b, demand(Axis6.YAW, 1.0), ones(), new double[] {0, 0}, new double[] {1, 1});
        assertEquals(0.0, r.commands()[1], 1.0e-9);
        assertTrue(r.commands()[0] > 0.0);
        assertTrue(r.achieved()[Axis6.YAW.index()] > 0.0);
    }

    @Test
    void lopsidedEnginesPushStraight() {
        List<V3> pos = List.of(new V3(-1, 0, 0), new V3(-2, 0, 0), new V3(-3, 0, 0), new V3(2, 0, 0));
        double[][] b = columns(pos, List.of(AHEAD, AHEAD, AHEAD, AHEAD));
        double[] lo = {0, 0, 0, 0};
        double[] hi = {1, 1, 1, 1};
        Allocator.Result r = Allocator.fly(b, demand(Axis6.FORWARD, 0.8), ones(), lo, hi);
        double yaw = r.achieved()[Axis6.YAW.index()];
        double forward = r.achieved()[Axis6.FORWARD.index()];

        assertTrue(forward > 2.3, "should still push close to the straight-line maximum, got " + forward);
        assertEquals(0.0, yaw / forward, 0.02, "yaw leak relative to thrust");
        assertTrue(r.commands()[3] > r.commands()[2], "the lone starboard engine works harder than the outboard port one");
    }

    @Test
    void quadRollsAndPitchesOffItsLiftRotors() {
        List<V3> pos = List.of(new V3(-1, 0, -1), new V3(1, 0, -1), new V3(-1, 0, 1), new V3(1, 0, 1));
        List<V3> up = List.of(V3.UNIT_Y, V3.UNIT_Y, V3.UNIT_Y, V3.UNIT_Y);
        double[][] b = columns(pos, up);
        double[] lo = {0, 0, 0, 0};
        double[] hi = {1, 1, 1, 1};

        double[] d = new double[Axis6.COUNT];
        d[Axis6.UP.index()] = 0.5;
        d[Axis6.ROLL.index()] = 0.3;
        Allocator.Result roll = Allocator.fly(b, d, ones(), lo, hi);
        double[] u = roll.commands();
        assertTrue(u[0] > u[1] && u[2] > u[3], "right wing down: left rotors lift more");
        assertEquals(2.0, roll.achieved()[Axis6.UP.index()], 1.0e-3, "half of four units of lift");

        d = new double[Axis6.COUNT];
        d[Axis6.UP.index()] = 0.5;
        d[Axis6.PITCH.index()] = 0.3;
        u = Allocator.fly(b, d, ones(), lo, hi).commands();
        assertTrue(u[0] > u[2] && u[1] > u[3], "nose up: front rotors lift more");
    }

    @Test
    void axisWithoutAuthorityAsksForNothing() {
        double[][] b = columns(List.of(new V3(0, 0, 1), new V3(0, 0, 2)), List.of(AHEAD, AHEAD));
        Allocator.Result r = Allocator.fly(b, demand(Axis6.YAW, 1.0), ones(), new double[] {-1, -1}, new double[] {1, 1});
        assertEquals(0.0, r.commands()[0], 1.0e-6);
        assertEquals(0.0, r.commands()[1], 1.0e-6);
    }

    @Test
    void axisScaleHandsTurningToSurfaces() {
        double[][] b = columns(List.of(new V3(-2, 0, 0), new V3(2, 0, 0)), List.of(AHEAD, AHEAD));
        double[] scale = ones();
        scale[Axis6.YAW.index()] = 0.0;
        Allocator.Result r = Allocator.fly(b, demand(Axis6.YAW, 1.0), scale, new double[] {-1, -1}, new double[] {1, 1});
        assertEquals(0.0, r.commands()[0], 1.0e-6);
        assertEquals(0.0, r.commands()[1], 1.0e-6);
    }
}
