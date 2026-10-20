package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CineHeadTest {
    @Test
    void theObjectivePointsWhereTheHeadIsAimed() {
        Random r = new Random(11);
        for (int i = 0; i < 1000; i++) {
            double facing = 90.0 * r.nextInt(4);
            double bearing = r.nextDouble() * 360.0;
            double pitch = CineHead.PITCH_MIN + r.nextDouble() * (CineHead.PITCH_MAX - CineHead.PITCH_MIN);
            double yaw = CineHead.yawFor(bearing, facing);
            double[] d = CineHead.sight(facing, yaw, pitch);
            assertEquals(0.0, CineHead.wrap180(CineHead.bearing(d[0], d[2]) - bearing), 1e-6, "bearing");
            assertEquals(pitch, CineHead.elevation(d[0], d[1], d[2]), 1e-6, "elevation");
        }
    }

    @Test
    void facingNorthAtRestLooksNorthAndFacingEastLooksEast() {
        double[] n = CineHead.sight(0, 0, 0);
        assertEquals(0.0, n[0], 1e-9);
        assertEquals(-1.0, n[2], 1e-9);
        double[] e = CineHead.sight(90, 0, 0);
        assertEquals(1.0, e[0], 1e-9);
        assertEquals(0.0, e[2], 1e-9);

        double[] w = CineHead.sight(0, 90, 0);
        assertEquals(-1.0, w[0], 1e-9);
    }

    @Test
    void theHeadNeverTurnsFasterThanItsGearing() {
        double yaw = 0.0;
        double dt = 0.05;
        for (int i = 0; i < 20; i++) {
            double next = CineHead.follow(yaw, 179.0, dt, CineHead.TAU_TRACK, CineHead.AZ_RATE, true);
            assertTrue(Math.abs(next - yaw) <= CineHead.AZ_RATE * dt + 1e-9, "step " + i);
            yaw = next;
        }

        assertTrue(yaw < 179.0 - 50.0);
    }

    @Test
    void theRingGearTurnsTheShortWayRound() {
        double yaw = 170.0;
        for (int i = 0; i < 200; i++) yaw = CineHead.follow(yaw, -170.0, 0.05, CineHead.TAU_TRACK, CineHead.AZ_RATE, true);

        assertEquals(190.0, yaw, 1e-3);
    }

    @Test
    void theFollowSettlesOnTheAim() {
        double p = 0.0;
        for (int i = 0; i < 100; i++) p = CineHead.follow(p, 30.0, 0.05, CineHead.TAU_TRACK, CineHead.EL_RATE, false);
        assertEquals(30.0, p, 1e-6);
    }

    @Test
    void theHandwheelsAreGearedToTheHead() {
        assertEquals(-43.5, CineHead.azimuthWheel(10.0), 1e-9);
        assertEquals(-30.0, CineHead.elevationWheel(10.0), 1e-9);
    }

    @Test
    void theBodyStaysInsideItsTravel() {
        assertEquals(CineHead.PITCH_MAX, CineHead.clampPitch(89.0));
        assertEquals(CineHead.PITCH_MIN, CineHead.clampPitch(-60.0));
    }

    @Test
    void theGenevaWheelMovesOnlyWhileThePinIsInASlot() {
        assertEquals(0.0, CineRig.geneva(0.0), 1e-9);
        assertEquals(0.0, CineRig.geneva(130.0), 1e-9);
        assertEquals(-45.0, CineRig.geneva(180.0), 1e-9);
        assertEquals(-90.0, CineRig.geneva(230.0), 1e-9);
        assertEquals(-90.0, CineRig.geneva(360.0), 1e-9);
        assertEquals(-180.0, CineRig.geneva(720.0), 1e-9);

        double last = 1.0;
        for (double a = 0; a <= 720.0; a += 1.0) {
            double w = CineRig.geneva(a);
            assertTrue(w <= last + 1e-9, "at " + a);
            last = w;
        }
    }

    @Test
    void theLegsLandWhereTheModelsWereDrawn() {
        assertEquals(24.775140568831926, CineRig.LEG_SPLAY, 1e-9);
        assertEquals(10.022474744293447, CineRig.LEG_LEN, 1e-9);
        assertEquals(7.288952323610127, CineRig.BRACE_TILT, 1e-9);
    }
}
