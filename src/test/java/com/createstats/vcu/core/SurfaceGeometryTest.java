package com.createstats.vcu.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceGeometryTest {
    private static final BodyFrame NORTH = BodyFrame.of(new V3(0, 0, -1), V3.UNIT_Y, V3.ZERO);
    private static final V3 EAST = V3.UNIT_X;
    private static final V3 WEST = new V3(-1, 0, 0);

    @Test
    void positiveDeflectionOnARightAileronLiftsTheRightWing() {
        double[] c = SurfaceGeometry.column(NORTH, new V3(3.5, 0, 0), EAST, new V3(4, 0, 0.6));
        assertTrue(c[Axis6.UP.index()] > 0, "trailing edge drops, lift rises");
        assertTrue(c[Axis6.ROLL.index()] < 0, "a lifted right wing is negative roll");
    }

    private static double[] only(Axis6 axis, double value) {
        double[] d = new double[Axis6.COUNT];
        d[axis.index()] = value;
        return d;
    }

    private static double liftChange(double[] gains, double[] column, double[] demand) {
        return SurfaceGeometry.deflection(gains, demand) * column[Axis6.UP.index()];
    }

    @Test
    void aileronPairRollsTheRightWay() {
        double[] left = SurfaceGeometry.column(NORTH, new V3(-3.5, 0, 0), WEST, new V3(-4, 0, 0.6));
        double[] right = SurfaceGeometry.column(NORTH, new V3(3.5, 0, 0), EAST, new V3(4, 0, 0.6));
        double[][] g = SurfaceGeometry.gains(new double[][] {left, right});
        assertEquals(1.0, Math.abs(g[0][Axis6.ROLL.index()]), 1.0e-9, "full stick is full travel");
        assertEquals(1.0, Math.abs(g[1][Axis6.ROLL.index()]), 1.0e-9);

        double[] rollRight = only(Axis6.ROLL, 1.0);
        assertTrue(liftChange(g[1], right, rollRight) < 0);
        assertTrue(liftChange(g[0], left, rollRight) > 0);
    }

    @Test
    void elevatorPitchesTheNoseAndRudderYawsIt() {
        double[] elevator = SurfaceGeometry.column(NORTH, new V3(0.5, 0, 5), EAST, new V3(1, 0, 5.8));
        double[] rudder = SurfaceGeometry.column(NORTH, new V3(0, 1.5, 5), V3.UNIT_Y, new V3(0, 2, 5.8));
        assertTrue(elevator[Axis6.PITCH.index()] < 0, "trailing edge down lifts the tail, nose drops");
        assertTrue(rudder[Axis6.YAW.index()] > 0, "trailing edge right pushes the tail left, nose right");

        double[][] g = SurfaceGeometry.gains(new double[][] {elevator, rudder});
        assertEquals(0.0, g[0][Axis6.YAW.index()], 1.0e-9);
        assertEquals(-1.0, g[0][Axis6.PITCH.index()], 1.0e-9);
        assertEquals(1.0, g[1][Axis6.YAW.index()], 1.0e-9);

        assertTrue(liftChange(g[0], elevator, only(Axis6.PITCH, 1.0)) < 0);
    }

    @Test
    void rudderStaysOffRollWhenThereAreAilerons() {
        double[] left = SurfaceGeometry.column(NORTH, new V3(-3.5, 0, 0), WEST, new V3(-4, 0, 0.6));
        double[] right = SurfaceGeometry.column(NORTH, new V3(3.5, 0, 0), EAST, new V3(4, 0, 0.6));
        double[] elevator = SurfaceGeometry.column(NORTH, new V3(0.5, 0, 5), EAST, new V3(1, 0, 5.8));
        double[] rudder = SurfaceGeometry.column(NORTH, new V3(0, 1.5, 5), V3.UNIT_Y, new V3(0, 2, 5.8));
        double[][] g = SurfaceGeometry.gains(new double[][] {left, right, elevator, rudder});
        assertEquals(0.0, g[3][Axis6.ROLL.index()], 1.0e-9, "a rudder is not an aileron");
        assertEquals(0.0, g[2][Axis6.ROLL.index()], 1.0e-9, "nor is the elevator");
        assertEquals(0.0, g[0][Axis6.PITCH.index()], 1.0e-9, "ailerons leave pitch to the elevator");
        assertEquals(0.0, g[0][Axis6.YAW.index()], 1.0e-9);
    }

    @Test
    void threeChannelPlaneTurnsOnTheRollKeysThroughItsRudder() {
        double[] elevator = SurfaceGeometry.column(NORTH, new V3(0, 0, 5), EAST, new V3(0.5, 0, 5.8));
        double[] rudder = SurfaceGeometry.column(NORTH, new V3(0, 1.5, 5), V3.UNIT_Y, new V3(0, 2, 5.8));
        double[][] g = SurfaceGeometry.gains(new double[][] {elevator, rudder});
        assertTrue(Math.abs(g[1][Axis6.ROLL.index()]) > 0.5);
    }

    @Test
    void elevonsMixRollAndPitchWithNoSpecialCase() {
        double[] left = SurfaceGeometry.column(NORTH, new V3(-3.5, 0, 2), WEST, new V3(-4, 0, 2.6));
        double[] right = SurfaceGeometry.column(NORTH, new V3(3.5, 0, 2), EAST, new V3(4, 0, 2.6));
        double[][] g = SurfaceGeometry.gains(new double[][] {left, right});

        double[] rollRight = only(Axis6.ROLL, 1.0);
        assertTrue(liftChange(g[1], right, rollRight) < 0, "roll: right tip loses lift");
        assertTrue(liftChange(g[0], left, rollRight) > 0, "roll: left tip gains lift");

        double[] noseUp = only(Axis6.PITCH, 1.0);
        assertTrue(liftChange(g[0], left, noseUp) < 0, "pitch: both push the tail down");
        assertTrue(liftChange(g[1], right, noseUp) < 0);
    }

    @Test
    void facingTheOtherWayFlipsTheSignNotTheEffect() {
        double[] outboard = SurfaceGeometry.column(NORTH, new V3(3.5, 0, 0), EAST, new V3(4, 0, 0.6));
        double[] inboard = SurfaceGeometry.column(NORTH, new V3(4.5, 0, 0), WEST, new V3(4, 0, 0.6));
        assertEquals(outboard[Axis6.ROLL.index()], -inboard[Axis6.ROLL.index()], 1.0e-9);
    }

    @Test
    void allMovingTailStillPitches() {
        double[] c = SurfaceGeometry.column(NORTH, new V3(0, 0, 5), EAST, new V3(0.1, 0, 5));
        assertTrue(Math.abs(c[Axis6.PITCH.index()]) > 1.0);
        double[][] g = SurfaceGeometry.gains(new double[][] {c});
        assertEquals(1.0, Math.abs(g[0][Axis6.PITCH.index()]), 1.0e-9);
        List.of(Axis6.FORWARD, Axis6.UP, Axis6.RIGHT)
                .forEach(a -> assertEquals(0.0, g[0][a.index()], 0.0, "surfaces never push"));
    }
}
