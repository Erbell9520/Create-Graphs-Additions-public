package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusRangeTest {
    @Test
    void theBandGrowsWithTheSquareOfTheRange() {
        assertEquals(1.25, FocusRange.sigma(20), 1e-9);
        assertEquals(25.25, FocusRange.sigma(100), 1e-9);
        assertEquals(4 * (FocusRange.sigma(100) - FocusRange.FLOOR), FocusRange.sigma(200) - FocusRange.FLOOR, 1e-9);
    }

    @Test
    void theReadingStaysInsideItsBand() {
        for (double range : new double[]{2, 20, 64, 150}) {
            for (int i = 0; i < 400; i++) {
                double t = i * 0.37;
                double r = FocusRange.reading(range, 0x5EED5EEDL, t);
                assertTrue(Math.abs(r - range) <= FocusRange.sigma(range) + 1e-9, "range " + range + " t " + t);
                assertTrue(r >= FocusRange.FLOOR);
            }
        }
    }

    @Test
    void theWanderIsSmoothAndDiffersBetweenCameras() {
        double a = FocusRange.wander(1234L, 10.0);
        double b = FocusRange.wander(1234L, 10.05);
        assertTrue(Math.abs(a - b) < 0.3, "a twentieth of a second moves the focus a little");
        assertNotEquals(FocusRange.wander(1L, 3.0), FocusRange.wander(987654321L, 3.0));
    }
}
