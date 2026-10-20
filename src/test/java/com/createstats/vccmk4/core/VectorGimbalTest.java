package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorGimbalTest {
    private static final double[][] FACINGS = {
            {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static double[] propulsionRight(double[] forward) {
        double[] reference = Math.abs(forward[1]) > 0.999 ? new double[]{0, 0, 1} : new double[]{0, 1, 0};
        double[] right = VectorGimbal.cross(forward, reference);
        double len = VectorGimbal.length(right);
        if (len * len < 1.0e-8) return new double[]{1, 0, 0};
        right = new double[]{right[0] / len, right[1] / len, right[2] / len};
        if (Math.abs(forward[1]) > 0.999) right = new double[]{-right[0], -right[1], -right[2]};
        return right;
    }

    @Test
    void theRightAxisMatchesPropulsionsForEveryFacing() {
        for (double[] f : FACINGS) {
            assertArrayEquals(propulsionRight(f), VectorGimbal.right(f), 1e-12, "facing " + f[0] + "," + f[1] + "," + f[2]);
        }
        assertArrayEquals(new double[]{1, 0, 0}, VectorGimbal.right(new double[]{0, 0, -1}), 1e-12, "north's right is east");
        assertArrayEquals(new double[]{0, 0, 1}, VectorGimbal.right(new double[]{1, 0, 0}), 1e-12, "east's right is south");

        assertArrayEquals(new double[]{-1, 0, 0}, VectorGimbal.right(new double[]{0, 1, 0}), 1e-12, "up's right is west");
        assertArrayEquals(new double[]{1, 0, 0}, VectorGimbal.right(new double[]{0, -1, 0}), 1e-12, "down's right is east");
    }

    @Test
    void aRightTurnTiltsARearThrusterPositiveAndAForwardOneNegative() {
        double[] f = {0, 0, -1};
        double[] r = VectorGimbal.right(f);

        double sidewaysRight = -r[0];
        double sidewaysForward = r[2];
        double armBehind = JetMixer.yawArm(new JetMixer.Jet(0.0, 0.0, -3.0, sidewaysRight, 0.0, sidewaysForward, JetRole.AUTO));
        double armAhead = JetMixer.yawArm(new JetMixer.Jet(0.0, 0.0, 3.0, sidewaysRight, 0.0, sidewaysForward, JetRole.AUTO));
        assertTrue(armBehind > 0, "behind the centre a positive x yaws the nose right");
        assertTrue(armAhead < 0, "ahead of it the same tilt yaws it left");
        assertEquals(0.6f, VectorGimbal.steer(0.6, 1.0, armBehind), 1e-6);
        assertEquals(-0.6f, VectorGimbal.steer(0.6, 1.0, armAhead), 1e-6);
        double[] pushed = VectorGimbal.direction(f, VectorGimbal.steer(0.6, 1.0, armBehind), 0);
        assertTrue(pushed[0] < 0, "and the thrust really gains a westward component, pushing the rear west");
    }

    @Test
    void aCentredNozzleThrustsAlongTheFacingAndAFullTiltThirtyDegreesOff() {
        for (double[] f : FACINGS) {
            assertArrayEquals(f, VectorGimbal.direction(f, 0, 0), 1e-12);
            double[] tilted = VectorGimbal.direction(f, 1, 0);
            double dot = f[0] * tilted[0] + f[1] * tilted[1] + f[2] * tilted[2];
            assertEquals(Math.cos(Math.toRadians(30)), dot, 1e-9, "forward force at full tilt is cos 30");
            double[] r = VectorGimbal.right(f);
            double side = r[0] * tilted[0] + r[1] * tilted[1] + r[2] * tilted[2];
            assertEquals(-0.5, side, 1e-9, "a positive x tilts the thrust away from the right axis");
            assertEquals(1.0, VectorGimbal.length(tilted), 1e-9);
        }

        double[] west = VectorGimbal.direction(new double[]{0, 0, -1}, 1, 0);
        assertTrue(west[0] < 0);

        double[] pitched = VectorGimbal.direction(new double[]{0, 0, -1}, 0, 1);
        assertTrue(pitched[1] < 0, "y positive pushes thrust downward (down signal), per f - y t u");
    }

    @Test
    void smallTiltsAreNearlyFreeSidewaysForce() {
        assertEquals(0.5, VectorGimbal.sideways(1.0), 1e-9);
        assertEquals(Math.cos(Math.toRadians(30)), VectorGimbal.forward(1.0), 1e-9);
        double theta = Math.atan(0.2 * Math.tan(Math.toRadians(30)));
        assertEquals(Math.sin(theta), VectorGimbal.sideways(0.2), 1e-12);
        assertTrue(1.0 - VectorGimbal.forward(0.2) < 0.01, "a fifth of the tilt costs under a percent of push");
    }

    @Test
    void theCoordinateSnapsToFifteenths() {
        assertEquals(0.2f, VectorGimbal.snap(0.2), 1e-6);
        assertEquals(4f / 15f, VectorGimbal.snap(0.25), 1e-6);
        assertEquals(1f, VectorGimbal.snap(1.7), 1e-6);
        assertEquals(-1f, VectorGimbal.snap(-3), 1e-6);
        assertEquals(0f, VectorGimbal.snap(Double.NaN), 1e-6);
    }

    @Test
    void steeringTiltsOnlyAThrusterThatIsPushingAndByTheSignOfItsArm() {
        assertEquals(0f, VectorGimbal.steer(1.0, 0.0, 3.0), 1e-9, "not pushing: never tilted just to steer");
        assertEquals(1f, VectorGimbal.steer(1.0, 0.5, 3.0), 1e-9, "a positive x turns the nose right here");
        assertEquals(-1f, VectorGimbal.steer(1.0, 0.5, -3.0), 1e-9, "behind the centre the same tilt turns it left, so the other way");
        assertEquals(-0.6f, VectorGimbal.steer(-0.6, 1.0, 3.0), 1e-6);
        assertEquals(0f, VectorGimbal.steer(1.0, 1.0, 0.1), 1e-9, "through the centre of mass a tilt turns nothing");
        assertEquals(0f, VectorGimbal.steer(0.0, 1.0, 3.0), 1e-9);
    }
}
