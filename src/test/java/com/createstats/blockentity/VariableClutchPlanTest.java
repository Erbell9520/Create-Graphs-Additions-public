package com.createstats.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableClutchPlanTest {
    private static float outputRpm(float maxRpm, float sourceSpeed) {
        return VariableClutchPlan.rpmCapModifier(maxRpm, sourceSpeed) * sourceSpeed;
    }

    @Test
    void aCapBelowTheInputThrottlesToTheCapNotToACrawl() {
        assertEquals(128f, outputRpm(128f, 256f), 0.01f);
        assertEquals(64f, outputRpm(64f, 200f), 0.01f);
    }

    @Test
    void aCapAboveTheInputBoostsAllTheWayToTheCap() {
        assertEquals(256f, outputRpm(256f, 100f), 0.01f);
        assertEquals(256f, outputRpm(256f, 192f), 0.01f);
    }

    @Test
    void theOutputNeverExceedsTheCapInFloatMath() {
        for (int src = 1; src <= 256; src++) {
            for (int cap = 1; cap <= 256; cap += 7) {
                float out = VariableClutchPlan.rpmCapModifier(cap, src) * src;
                assertTrue(out <= cap, "src " + src + " cap " + cap + " gave " + out);
            }
        }
    }

    @Test
    void aFractionalCapIsFlooredToAWholeRpm() {
        assertEquals(100f, outputRpm(100.9f, 50f), 0.01f);
    }

    @Test
    void noSourceMeansNeutral() {
        assertEquals(1f, VariableClutchPlan.rpmCapModifier(128f, 0f));
        assertEquals(1f, VariableClutchPlan.rpmCapModifier(128f, Float.NaN));
    }
}
