package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipFrameTest {
    @Test
    void starboardIsTheBowTurnedClockwise() {
        assertEquals(1, new ShipFrame(0).stbd(0, 1));
        assertEquals(1, new ShipFrame(1).stbd(-1, 0));
        assertEquals(1, new ShipFrame(2).stbd(0, -1));
        assertEquals(1, new ShipFrame(3).stbd(1, 0));
    }

    @Test
    void foreAndStarboardInvertBackToOffsets() {
        for (int q = 0; q < 4; q++) {
            ShipFrame f = new ShipFrame(q);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int fore = f.fore(dx, dz);
                    int stbd = f.stbd(dx, dz);
                    assertEquals(dx, f.dx(fore, stbd), "bow " + q);
                    assertEquals(dz, f.dz(fore, stbd), "bow " + q);
                }
            }
        }
    }

    @Test
    void groundIsNorthUpEastRight() {
        ShipFrame g = ShipFrame.ground();
        assertEquals(5, g.fore(5, 0), "east is to the right");
        assertEquals(-4, g.stbd(0, -4), "north is up, so negative down-coordinate");
    }

    @Test
    void turningAndRotatingAreQuarterTurnsClockwise() {
        assertEquals(1, new ShipFrame(0).turned().bow());
        assertEquals(0, new ShipFrame(3).turned().bow());
        assertArrayEquals(new int[]{0, 1}, ShipFrame.rotate(1, 0, 1), "east turns to south");
        assertArrayEquals(new int[]{-2, -3}, ShipFrame.rotate(2, 3, 2));
        assertEquals(1, ShipFrame.quarterOf(0.1, 0.9));
        assertEquals(3, ShipFrame.quarterOf(0.0, -2.0));
    }
}
