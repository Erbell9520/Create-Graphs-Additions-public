package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasLawTest {
    @Test
    void farFromTargetTheTimeConstantIsNineSeconds() {
        double cap = 1000.0;
        double gap = 1000.0;
        double x = gap / (cap * 0.05);
        assertEquals(gap / 180.0 * (1.0 + 5.0 / (1.0 + 3.0 * x * x)), GasLaw.nudge(gap, cap), 1.0e-12);
        assertEquals(gap / 180.0, GasLaw.nudge(gap, cap), gap / 180.0 * 0.01);
    }

    @Test
    void nearTargetItIsSixTimesFaster() {
        double gap = 1.0e-3;
        assertEquals(6.0 / 180.0, GasLaw.nudge(gap, 1000.0) / gap, 1.0e-6);
        assertEquals(30.0, GasLaw.nearTicks(), 1.0e-12);
    }

    @Test
    void fillingAndEmptyingAreTheSame() {
        for (double g = -900.0; g <= 900.0; g += 37.0) {
            assertEquals(-GasLaw.nudge(g, 900.0), GasLaw.nudge(-g, 900.0), 1.0e-12);
        }
    }

    @Test
    void nudgeIsStrictlyIncreasing() {
        double prev = Double.NEGATIVE_INFINITY;
        for (double g = -4500.0; g <= 4500.0; g += 0.5) {
            double n = GasLaw.nudge(g, 900.0);
            assertTrue(n > prev, "not increasing at " + g);
            prev = n;
        }
    }

    @Test
    void gapForInvertsNudge() {
        for (double step = -20.0; step <= 20.0; step += 0.37) {
            double gap = GasLaw.gapFor(step, 900.0);
            assertEquals(step, GasLaw.nudge(gap, 900.0), 1.0e-9, "step " + step);
        }
    }

    @Test
    void aBalloonReachesMostOfAFarTargetInAboutNineSeconds() {
        double gas = 0.0;
        for (int t = 0; t < 180; t++) gas = GasLaw.step(gas, 900.0, 900.0);

        assertTrue(gas > 900.0 * (1.0 - Math.exp(-1.0)), "gas " + gas);
        assertTrue(gas < 900.0, "gas " + gas);
    }

    @Test
    void holdsIsGasTimesStrengthTimesPressure() {
        assertEquals(600.0, GasLaw.holds(400.0, 1.5, 1.0), 1.0e-12);
        assertEquals(300.0, GasLaw.holds(400.0, 1.5, 0.5), 1.0e-12);
    }
}
