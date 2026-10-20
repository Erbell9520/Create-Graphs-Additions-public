package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicGeometryTest {
    private static double[] turn(int quarter, double x, double z) {
        return switch (quarter) {
            case 1 -> new double[] {16 - z, x};
            case 2 -> new double[] {16 - x, 16 - z};
            case 3 -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    @Test
    @DisplayName("toNorth undoes the blockstate's turn for every facing")
    void toNorthInvertsTheTurn() {
        for (int q = 0; q < 4; q++) {
            for (double x = 0.5; x < 16; x += 2.25) {
                for (double z = 0.25; z < 16; z += 3.5) {
                    final double[] w = turn(q, x, z);
                    final double[] n = SeismicGeometry.toNorth(q, w[0], w[1]);
                    assertEquals(x, n[0], 1e-9, "facing " + q);
                    assertEquals(z, n[1], 1e-9, "facing " + q);
                }
            }
        }
    }

    @Test
    @DisplayName("the lever's box answers on every facing, and the rest of the rig does not")
    void leverBox() {
        final double[] b = SeismicGeometry.LEVER_BOX;
        final double cx = (b[0] + b[3]) / 2;
        final double cy = (b[1] + b[4]) / 2;
        final double cz = (b[2] + b[5]) / 2;
        for (int q = 0; q < 4; q++) {
            final double[] w = turn(q, cx, cz);
            assertTrue(SeismicGeometry.onLever(q, w[0], cy, w[1]), "the lever's middle, facing " + q);
            final double[] weight = turn(q, 8.0, 5.0);
            assertFalse(SeismicGeometry.onLever(q, weight[0], 4.0, weight[1]), "the weight is not the lever");
            final double[] recorder = turn(q, 2.5, 0.5);
            assertFalse(SeismicGeometry.onLever(q, recorder[0], 5.0, recorder[1]), "the recorder is not the lever");
        }
    }

    @Test
    @DisplayName("the drum winds once round over the whole lift, its front going down, so its eight teeth click once per eighth")
    void clicks() {
        assertEquals(SeismicGeometry.RATCHET_REST_DEG, SeismicGeometry.drumDegrees(0.0));
        assertEquals(SeismicGeometry.RATCHET_REST_DEG - 360.0, SeismicGeometry.drumDegrees(1.0), 1e-9,
                "one whole turn, the winding way (negative about x: the rope comes onto the drum's front)");
        assertTrue(SeismicGeometry.drumDegrees(0.5) < SeismicGeometry.drumDegrees(0.25));
        assertEquals(0, SeismicGeometry.tooth(0.0));
        assertEquals(1, SeismicGeometry.tooth(0.125));
        assertEquals(7, SeismicGeometry.tooth(0.99));
        assertEquals(8, SeismicGeometry.tooth(1.0));
        int clicks = 0;
        int last = 0;
        for (double h = 0; h <= 1.0 + 1e-9; h += 0.001) {
            final int t = SeismicGeometry.tooth(h);
            if (t > last) {
                clicks++;
            }
            last = t;
        }
        assertEquals(8, clicks);
        assertEquals(13.0, SeismicGeometry.weightRise(1.0), 1e-9);
        assertEquals(13.0, SeismicGeometry.weightRise(2.0), 1e-9, "never above the top of the lift");
    }

    @Test
    @DisplayName("the pawl rests against a face, rides up the next tooth's back, holds on its land and drops off as the count ticks over")
    void thePawlRides() {
        final double eighth = 1.0 / SeismicGeometry.TEETH;
        assertEquals(0.0, SeismicGeometry.pawlRide(0.0));
        assertEquals(0.0, SeismicGeometry.pawlRide(eighth * SeismicGeometry.RIDE_FROM * 0.5), "resting against the face");
        assertEquals(0.0, SeismicGeometry.pawlRide(eighth * SeismicGeometry.RIDE_FROM), 1e-9);
        assertEquals(1.0, SeismicGeometry.pawlRide(eighth * SeismicGeometry.RIDE_TOP), 1e-6, "up on the tooth's land");
        assertEquals(1.0, SeismicGeometry.pawlRide(eighth * (SeismicGeometry.RIDE_TO - 1e-4)), 1e-6, "still on the land");
        assertEquals(0.0, SeismicGeometry.pawlRide(eighth * SeismicGeometry.RIDE_TO), "off the land's edge in one step: the click");
        assertTrue(SeismicGeometry.pawlRide(eighth * (1.0 - 1e-5)) < 0.01, "down the steep face");
        double prev = 0.0;
        for (double f = SeismicGeometry.RIDE_FROM; f < SeismicGeometry.RIDE_TO; f += 0.01) {
            final double r = SeismicGeometry.pawlRide(3 * eighth + f * eighth);
            assertTrue(r >= prev - 1e-12, "up the back and along the land without slipping at " + f);
            prev = r;
        }

        final double justBefore = 5 * eighth - 1e-6;
        assertTrue(SeismicGeometry.pawlRide(justBefore) < 0.01);
        assertEquals(4, SeismicGeometry.tooth(justBefore));
        assertEquals(5, SeismicGeometry.tooth(5 * eighth));
    }

    @Test
    @DisplayName("close up, only a look at the recorder recalls the map: not the lever, the mast or the base's back")
    void theRecorderIsTheRecall() {
        assertTrue(SeismicGeometry.onRecorder(0, 2.6, 4.5, 0.25), "the recorder's face, facing north");
        assertFalse(SeismicGeometry.onRecorder(0, 15.5, 9.0, 6.0), "the lever");
        assertFalse(SeismicGeometry.onRecorder(0, 8.0, 12.0, 3.0), "the mast");
        assertFalse(SeismicGeometry.onRecorder(0, 8.0, 4.0, 15.75), "the base's back");

        assertTrue(SeismicGeometry.onRecorder(1, 15.75, 4.5, 2.6));
        assertFalse(SeismicGeometry.onRecorder(1, 2.6, 4.5, 0.25));
    }

    @Test
    @DisplayName("the sheave turns with the rope: the lift over its pitch radius, negative about z")
    void theSheaveTurns() {
        assertEquals(0.0, SeismicGeometry.sheaveDegrees(0.0), 0.0);
        assertEquals(2.0, SeismicGeometry.SHEAVE_PITCH_R, 1e-12, "the runs at x 8 and 12 go round a 2 pixel radius");
        assertEquals(-Math.toDegrees(13.0 / 2.0), SeismicGeometry.sheaveDegrees(1.0), 1e-9);
    }
}
