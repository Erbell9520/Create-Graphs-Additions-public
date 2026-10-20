package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyBowlTest {
    @Test
    @DisplayName("every vertex lies on the radius and at or below the plate's surface")
    void onTheBoundary() {
        for (int r = 16; r <= 64; r += 16) {
            final List<double[]> dashes = SurveyBowl.dashes(r, 64);
            assertTrue(dashes.size() > 40, "a bowl of " + dashes.size() + " dashes");
            for (final double[] d : dashes) {
                assertEquals(6, d.length, "a dash is two points");
                for (int k = 0; k < 6; k += 3) {
                    assertEquals(r, Math.sqrt(d[k] * d[k] + d[k + 1] * d[k + 1] + d[k + 2] * d[k + 2]), 1e-9);
                    assertTrue(d[k + 1] <= 1e-9, "a vertex above the surface");
                }
            }
        }
    }

    @Test
    @DisplayName("nothing to draw for no radius")
    void empty() {
        assertTrue(SurveyBowl.dashes(0, 64).isEmpty());
    }
}
