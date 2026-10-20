package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SightLineTest {
    private static final double TX = 0.5;
    private static final double TY = CineRig.TRUNNION_Y / 16.0;
    private static final double TZ = 0.5;

    @Test
    void theTrunnionIsInsideTheUpperCellWhichIsWhy1_14_99SawNothing() {
        assertTrue(TY > 1.0 && TY < 2.0, "the trunnion stands in the upper block: " + TY);
    }

    @Test
    void levelSightLinesLeaveThroughTheSideOfTheCell() {
        assertEquals(0.5, SightLine.exit(TX, TY, TZ, 0, 0, -1), 1e-12, "north, half a block");
        assertEquals(0.5, SightLine.exit(TX, TY, TZ, 1, 0, 0), 1e-12, "east");
        assertEquals(Math.sqrt(0.5), SightLine.exit(TX, TY, TZ, Math.sqrt(0.5), 0, Math.sqrt(0.5)), 1e-12,
                "diagonally, through the corner");
    }

    @Test
    void steepSightLinesLeaveThroughTheTopOrTheBottomOfTheTwoCells() {
        assertEquals(2.0 - TY, SightLine.exit(TX, TY, TZ, 0, 1, 0), 1e-12, "straight up, out of the upper cell's top");
        assertEquals(TY, SightLine.exit(TX, TY, TZ, 0, -1, 0), 1e-12, "straight down, through both cells");
    }

    @Test
    void theStartPointIsAlwaysOutsideTheCells() {
        for (int yaw = 0; yaw < 360; yaw += 15) {
            for (int pitch = -90; pitch <= 90; pitch += 10) {
                double p = Math.toRadians(pitch);
                double a = Math.toRadians(yaw);
                double dx = Math.cos(p) * Math.sin(a);
                double dy = Math.sin(p);
                double dz = -Math.cos(p) * Math.cos(a);
                double t = SightLine.exit(TX, TY, TZ, dx, dy, dz) + SightLine.CLEAR;
                double x = TX + dx * t;
                double y = TY + dy * t;
                double z = TZ + dz * t;
                boolean inside = x > 0 && x < 1 && y > 0 && y < SightLine.CELLS_HIGH && z > 0 && z < 1;
                assertFalse(inside, "yaw " + yaw + " pitch " + pitch + " starts inside at " + x + ", " + y + ", " + z);
                assertTrue(t < 2.1, "and never far from the lens: " + t);
            }
        }
    }

    @Test
    void aHitCountsOnlyAtTheAimOrOnTheTargetShip() {
        assertTrue(SightLine.reached(true, false, Double.MAX_VALUE));
        assertTrue(SightLine.reached(false, true, 40.0), "the target ship's own hull is the ship seen");
        assertTrue(SightLine.reached(false, false, 1.2), "the ground the target stands on");
        assertFalse(SightLine.reached(false, false, 1.6), "a hill in between");
        assertFalse(SightLine.reached(false, false, 2.9e7), "a ship's plot-space hit, never brought back, is no sighting");
    }
}
