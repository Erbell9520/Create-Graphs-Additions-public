package com.createstats.vcu.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelSteerTest {
    private static List<WheelSteer.Wheel> axles(double... longitudinal) {
        List<WheelSteer.Wheel> out = new ArrayList<>();
        for (double z : longitudinal) {
            out.add(new WheelSteer.Wheel(-1.0, z));
            out.add(new WheelSteer.Wheel(1.0, z));
        }
        return out;
    }

    @Test
    void straightAheadSteersNothing() {
        for (WheelSteer.Command c : WheelSteer.solve(axles(1.5, -1.5), 0.0, 0.6, WheelSteer.Mode.FRONT)) {
            assertEquals(0.0, c.angleDeg(), 1.0e-9);
            assertEquals(0.6, c.drive(), 1.0e-9);
        }
    }

    @Test
    void carTurnsRightWithAckermann() {
        List<WheelSteer.Command> c = WheelSteer.solve(axles(1.5, -1.5), 1.0, 1.0, WheelSteer.Mode.FRONT);
        WheelSteer.Command frontLeft = c.get(0);
        WheelSteer.Command frontRight = c.get(1);
        assertTrue(frontLeft.angleDeg() > 0 && frontRight.angleDeg() > 0, "both front wheels point right");
        assertTrue(frontRight.angleDeg() > frontLeft.angleDeg(), "the inside wheel turns more");
        assertEquals(WheelSteer.MAX_ANGLE_DEG, frontRight.angleDeg(), 0.05, "full stick is full lock");
        assertEquals(0.0, c.get(2).angleDeg(), 1.0e-9);
        assertEquals(0.0, c.get(3).angleDeg(), 1.0e-9);
        assertTrue(c.get(0).drive() > c.get(1).drive(), "the outside of the turn runs faster");
        for (WheelSteer.Command cmd : c) assertTrue(cmd.drive() <= 1.0 + 1.0e-9);
    }

    @Test
    void leftTurnMirrorsRightTurn() {
        List<WheelSteer.Command> right = WheelSteer.solve(axles(1.5, -1.5), 0.5, 1.0, WheelSteer.Mode.FRONT);
        List<WheelSteer.Command> left = WheelSteer.solve(axles(1.5, -1.5), -0.5, 1.0, WheelSteer.Mode.FRONT);
        assertEquals(right.get(1).angleDeg(), -left.get(0).angleDeg(), 1.0e-9);
        assertEquals(right.get(0).angleDeg(), -left.get(1).angleDeg(), 1.0e-9);
    }

    @Test
    void sixWheelerSteersOnlyTheFrontAxle() {
        List<WheelSteer.Command> c = WheelSteer.solve(axles(2.0, 0.0, -2.0), 1.0, 1.0, WheelSteer.Mode.FRONT);
        assertTrue(c.get(0).steered() && c.get(1).steered());
        for (int i = 2; i < 6; i++) {
            assertFalse(c.get(i).steered(), "the tandem stays straight");
            assertEquals(0.0, c.get(i).angleDeg(), 1.0e-9);
        }
    }

    @Test
    void sixteenWheelerSteersItsFrontFourAxlesProgressively() {
        List<WheelSteer.Command> c = WheelSteer.solve(axles(7, 5, 3, 1, -1, -3, -5, -7), 1.0, 1.0, WheelSteer.Mode.FRONT);
        assertEquals(16, c.size());
        double previous = Double.MAX_VALUE;
        for (int axle = 0; axle < 4; axle++) {
            double inner = c.get(axle * 2 + 1).angleDeg();
            assertTrue(inner > 0, "axle " + axle + " steers right");
            assertTrue(inner < previous, "each axle further back turns less");
            previous = inner;
        }
        for (int i = 8; i < 16; i++) {
            assertEquals(0.0, c.get(i).angleDeg(), 1.0e-9, "rear four axles fixed");
        }
    }

    @Test
    void counterSteerTurnsTheRearTheOtherWay() {
        List<WheelSteer.Command> c = WheelSteer.solve(axles(3, 1, -1, -3), 1.0, 1.0, WheelSteer.Mode.ALL);
        assertTrue(c.get(1).angleDeg() > 0, "front points right");
        assertTrue(c.get(7).angleDeg() < 0, "rear points left");
        assertEquals(c.get(1).angleDeg(), -c.get(7).angleDeg(), 1.0e-9, "symmetric about mid-wheelbase");
    }

    @Test
    void skidSteerRunsTheOutsideFaster() {
        List<WheelSteer.Command> c = WheelSteer.solve(axles(1, -1), 1.0, 0.0, WheelSteer.Mode.SKID);
        assertEquals(1.0, c.get(0).drive(), 1.0e-9, "left track forward");
        assertEquals(-1.0, c.get(1).drive(), 1.0e-9, "right track back: pivot turn");
        for (WheelSteer.Command cmd : c) assertEquals(0.0, cmd.angleDeg(), 1.0e-9);
    }

    @Test
    void singleAxleCannotSteer() {
        for (WheelSteer.Command c : WheelSteer.solve(axles(0.0), 1.0, 1.0, WheelSteer.Mode.FRONT)) {
            assertFalse(c.steered());
        }
    }

    @Test
    void signalStepsMatchTheMount() {
        assertEquals(15, WheelSteer.signalSteps(30.0));
        assertEquals(15, WheelSteer.signalSteps(45.0));
        assertEquals(4, WheelSteer.signalSteps(7.0));
        assertEquals(0, WheelSteer.signalSteps(0.9));
        assertEquals(4, WheelSteer.signalSteps(-7.0));
    }
}
