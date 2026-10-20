package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundSteerTest {
    private static List<AxleLayout.WheelSpec> axles(double... longitudinal) {
        List<AxleLayout.WheelSpec> out = new ArrayList<>();
        for (double z : longitudinal) {
            out.add(new AxleLayout.WheelSpec(-1.0, z));
            out.add(new AxleLayout.WheelSpec(1.0, z));
        }
        return out;
    }

    private static List<GroundSteer.Command> solve(List<AxleLayout.WheelSpec> wheels, SteerPreset preset,
                                                   double steer, double throttle) {
        List<AxleLayout.Axle> layout = AxleLayout.cluster(wheels);
        return GroundSteer.solve(wheels, layout, preset.roles(layout.size()), null, preset, steer, throttle,
                GroundSteer.MOUNT_LOCK_DEG);
    }

    @Test
    void straightAheadSteersNothing() {
        for (GroundSteer.Command c : solve(axles(1.5, -1.5), SteerPreset.FRONT, 0.0, 0.6)) {
            assertEquals(0.0, c.angleDeg(), 1.0e-9);
            assertEquals(0.6, c.drive(), 1.0e-9);
            assertTrue(c.commanded());
        }
    }

    @Test
    void carTurnsRightWithAckermann() {
        List<GroundSteer.Command> c = solve(axles(1.5, -1.5), SteerPreset.FRONT, 1.0, 1.0);
        GroundSteer.Command frontLeft = c.get(0);
        GroundSteer.Command frontRight = c.get(1);
        assertTrue(frontLeft.angleDeg() > 0 && frontRight.angleDeg() > 0, "both front wheels point right");
        assertTrue(frontRight.angleDeg() > frontLeft.angleDeg(), "the inside wheel turns more");
        assertEquals(GroundSteer.MOUNT_LOCK_DEG, frontRight.angleDeg(), 0.05, "full stick is full lock");
        assertEquals(0.0, c.get(2).angleDeg(), 1.0e-9);
        assertEquals(0.0, c.get(3).angleDeg(), 1.0e-9);
        assertTrue(c.get(0).drive() > c.get(1).drive(), "the outside of the turn runs faster");
        for (GroundSteer.Command cmd : c) assertTrue(cmd.drive() <= 1.0 + 1.0e-9);
    }

    @Test
    void leftTurnMirrorsRightTurn() {
        List<GroundSteer.Command> right = solve(axles(1.5, -1.5), SteerPreset.FRONT, 0.5, 1.0);
        List<GroundSteer.Command> left = solve(axles(1.5, -1.5), SteerPreset.FRONT, -0.5, 1.0);
        assertEquals(right.get(1).angleDeg(), -left.get(0).angleDeg(), 1.0e-9);
        assertEquals(right.get(0).angleDeg(), -left.get(1).angleDeg(), 1.0e-9);
    }

    @Test
    void sixBySixFrontOnly() {
        List<GroundSteer.Command> c = solve(axles(2.0, 0.0, -2.0), SteerPreset.FRONT, 1.0, 1.0);
        assertTrue(c.get(0).angleDeg() > 0 && c.get(1).angleDeg() > 0);
        for (int i = 2; i < 6; i++) assertEquals(0.0, c.get(i).angleDeg(), 1.0e-9, "middle and rear fixed");
    }

    @Test
    void sixBySixTwinFrontSteersTwoAxlesTheFrontOneMore() {
        List<GroundSteer.Command> c = solve(axles(2.0, 0.0, -2.0), SteerPreset.TWIN_FRONT, 1.0, 1.0);
        assertTrue(c.get(0).angleDeg() > 0 && c.get(1).angleDeg() > 0, "front axle steers");
        assertTrue(c.get(2).angleDeg() > 0 && c.get(3).angleDeg() > 0, "second axle steers the same way");
        assertTrue(c.get(1).angleDeg() > c.get(3).angleDeg(), "the axle further from the pivot turns more");
        assertEquals(0.0, c.get(4).angleDeg(), 1.0e-9, "rear axle is the pivot");
        assertEquals(0.0, c.get(5).angleDeg(), 1.0e-9);
    }

    @Test
    void sixBySixFrontAndRearCounterSteer() {
        List<GroundSteer.Command> c = solve(axles(2.0, 0.0, -2.0), SteerPreset.FRONT_REAR, 1.0, 1.0);
        assertTrue(c.get(0).angleDeg() > 0, "front points into the turn");
        assertEquals(0.0, c.get(2).angleDeg(), 1.0e-9, "middle axle is the pivot");
        assertEquals(0.0, c.get(3).angleDeg(), 1.0e-9);
        assertTrue(c.get(4).angleDeg() < 0, "rear counter-steers");
        assertEquals(c.get(1).angleDeg(), -c.get(5).angleDeg(), 1.0e-6, "symmetric about the middle axle");
    }

    @Test
    void frontAndRearOnTwoAxlesIsAllWheelSteer() {
        List<GroundSteer.Command> c = solve(axles(1.5, -1.5), SteerPreset.FRONT_REAR, 1.0, 1.0);
        assertTrue(c.get(0).angleDeg() > 0);
        assertTrue(c.get(2).angleDeg() < 0, "rear counter-steers about mid-wheelbase");
    }

    @Test
    void allWheelSteerTurnsTighterThanFrontOnly() {
        List<AxleLayout.WheelSpec> w = axles(3.0, 1.0, -1.0, -3.0);
        double frontOnly = turnRadius(w, SteerPreset.FRONT);
        double all = turnRadius(w, SteerPreset.ALL);
        assertTrue(all < frontOnly, "all axles steering turns tighter: " + all + " vs " + frontOnly);
    }

    private static double turnRadius(List<AxleLayout.WheelSpec> wheels, SteerPreset preset) {
        List<AxleLayout.Axle> layout = AxleLayout.cluster(wheels);
        AxleRole[] roles = preset.roles(layout.size());
        double pivot = GroundSteer.pivot(layout, roles);
        List<GroundSteer.Command> c = GroundSteer.solve(wheels, layout, roles, null, preset, 1.0, 1.0,
                GroundSteer.MOUNT_LOCK_DEG);
        double along = wheels.get(0).longitudinal() - pivot;
        return along / Math.tan(Math.toRadians(c.get(0).angleDeg())) + wheels.get(0).lateral();
    }

    @Test
    void crabPointsEveryWheelTheSameWayAndDrivesEvenly() {
        List<GroundSteer.Command> c = solve(axles(2.0, 0.0, -2.0), SteerPreset.CRAB, 0.5, 0.8);
        for (GroundSteer.Command cmd : c) {
            assertEquals(15.0, cmd.angleDeg(), 1.0e-9, "half stick is half lock, no Ackermann");
            assertEquals(0.8, cmd.drive(), 1.0e-9);
        }
    }

    @Test
    void skidSteersNothingAndRunsTheSidesApart() {
        List<GroundSteer.Command> c = solve(axles(1.5, -1.5), SteerPreset.SKID, 1.0, 0.5);
        for (GroundSteer.Command cmd : c) assertEquals(0.0, cmd.angleDeg(), 1.0e-9);
        assertEquals(1.0, c.get(0).drive(), 1.0e-9, "left side speeds up for a right turn");
        assertEquals(-0.5, c.get(1).drive(), 1.0e-9, "right side runs backwards");
        List<GroundSteer.Command> pivotTurn = solve(axles(1.5, -1.5), SteerPreset.SKID, 1.0, 0.0);
        assertEquals(1.0, pivotTurn.get(0).drive(), 1.0e-9, "idle throttle plus stick is a pivot turn");
        assertEquals(-1.0, pivotTurn.get(1).drive(), 1.0e-9);
    }

    @Test
    void freeAxleGetsNoCommandButStillDrives() {
        List<AxleLayout.WheelSpec> w = axles(1.5, -1.5);
        List<AxleLayout.Axle> layout = AxleLayout.cluster(w);
        AxleRole[] roles = {AxleRole.STEER, AxleRole.FREE};
        List<GroundSteer.Command> c = GroundSteer.solve(w, layout, roles, null, SteerPreset.CUSTOM, 1.0, 1.0, 30.0);
        assertTrue(c.get(0).commanded());
        assertFalse(c.get(2).commanded(), "a free axle is left to its own redstone");
        assertTrue(c.get(2).drive() > 0.0, "but it still drives");
    }

    @Test
    void undrivenAxleRollsFree() {
        List<AxleLayout.WheelSpec> w = axles(1.5, -1.5);
        List<AxleLayout.Axle> layout = AxleLayout.cluster(w);
        boolean[] driven = {false, true};
        List<GroundSteer.Command> c = GroundSteer.solve(w, layout, SteerPreset.FRONT.roles(2), driven,
                SteerPreset.FRONT, 0.0, 1.0, 30.0);
        assertEquals(0.0, c.get(0).drive(), 1.0e-9);
        assertEquals(1.0, c.get(2).drive(), 1.0e-9);
    }

    @Test
    void reducedLockLimitsEveryWheel() {
        List<AxleLayout.WheelSpec> w = axles(1.5, -1.5);
        List<AxleLayout.Axle> layout = AxleLayout.cluster(w);
        List<GroundSteer.Command> c = GroundSteer.solve(w, layout, SteerPreset.FRONT.roles(2), null,
                SteerPreset.FRONT, 1.0, 1.0, 12.0);
        for (GroundSteer.Command cmd : c) assertTrue(Math.abs(cmd.angleDeg()) <= 12.0 + 1.0e-9);
        assertEquals(12.0, c.get(1).angleDeg(), 0.05, "the inside wheel reaches the reduced lock");
    }

    @Test
    void sixteenWheelerFrontHalfSteersProgressively() {
        List<AxleLayout.WheelSpec> w = axles(7, 5, 3, 1, -1, -3, -5, -7);
        List<AxleLayout.Axle> layout = AxleLayout.cluster(w);
        AxleRole[] roles = {AxleRole.STEER, AxleRole.STEER, AxleRole.STEER, AxleRole.STEER,
                AxleRole.FIXED, AxleRole.FIXED, AxleRole.FIXED, AxleRole.FIXED};
        List<GroundSteer.Command> c = GroundSteer.solve(w, layout, roles, null, SteerPreset.CUSTOM, 1.0, 1.0, 30.0);
        double previous = Double.MAX_VALUE;
        for (int axle = 0; axle < 4; axle++) {
            double inner = c.get(axle * 2 + 1).angleDeg();
            assertTrue(inner > 0.0 && inner < previous, "axle " + axle + " turns less than the one ahead");
            previous = inner;
        }
        for (int i = 8; i < 16; i++) assertEquals(0.0, c.get(i).angleDeg(), 1.0e-9);
    }

    @Test
    void pivotIsTheMiddleOfTheFixedAxles() {
        List<AxleLayout.WheelSpec> w = axles(2.0, 0.0, -2.0);
        List<AxleLayout.Axle> layout = AxleLayout.cluster(w);
        assertEquals(-1.0, GroundSteer.pivot(layout, SteerPreset.FRONT.roles(3)), 1.0e-9, "tandem: between middle and rear");
        assertEquals(-2.0, GroundSteer.pivot(layout, SteerPreset.TWIN_FRONT.roles(3)), 1.0e-9);
        assertEquals(0.0, GroundSteer.pivot(layout, SteerPreset.FRONT_REAR.roles(3)), 1.0e-9);
        assertEquals(0.0, GroundSteer.pivot(layout, SteerPreset.ALL.roles(3)), 1.0e-9, "no fixed axle: mid-wheelbase");
    }

    @Test
    void signalStepsRoundToTwoDegrees() {
        assertEquals(0, GroundSteer.signalSteps(0.0));
        assertEquals(1, GroundSteer.signalSteps(1.5));
        assertEquals(15, GroundSteer.signalSteps(30.0));
        assertEquals(15, GroundSteer.signalSteps(-45.0));
    }
}
