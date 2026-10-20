package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelForceTest {
    @Test
    void driveIsAFlatForcePerRpmCutByTheBrake() {
        assertEquals(1.75 * 64, WheelForce.drive(64, 0.0, 1.0), 1e-9);
        assertEquals(0.0, WheelForce.drive(64, 1.0, 1.0), 1e-9, "full brake cuts the drive to nothing");
        assertEquals(1.75 * 64 * 0.5, WheelForce.drive(64, 0.5, 1.0), 1e-9);
        assertEquals(1.75 * 64 * 0.4, WheelForce.drive(64, 0.0, 0.4), 1e-9, "slippery ground drives less");
    }

    @Test
    void theDefaultSuspensionCapsTheDragWhateverTheLoad() {
        WheelForce.Wheel heavy = new WheelForce.Wheel(true, 1.0, WheelForce.capMass(400.0, 10.0));
        WheelForce.Wheel light = new WheelForce.Wheel(true, 1.0, WheelForce.capMass(4.0, 10.0));
        assertEquals(0.075 * 20 * 10, WheelForce.dragPerSpeed(heavy, 0.0), 1e-9, "15 per block per second at strength 10");
        assertEquals(0.375 * 20 * 10, WheelForce.dragPerSpeed(heavy, 1.0), 1e-9, "five times that at full brake");
        assertEquals(0.075 * 20 * 4, WheelForce.dragPerSpeed(light, 0.0), 1e-9, "a load under the cap drags in proportion");
    }

    @Test
    void capMassFallsBackToTheStrengthWhenTheNormalMassIsUnknown() {
        assertEquals(10.0, WheelForce.capMass(Double.NaN, 10.0));
        assertEquals(10.0, WheelForce.capMass(50.0, 0.0), "a strength that could not be read is the default");
        assertEquals(3.0, WheelForce.capMass(3.0, 180.0));
    }

    @Test
    void sumsOverAWheelSet() {
        List<WheelForce.Wheel> set = List.of(WheelForce.Wheel.typical(true), WheelForce.Wheel.typical(false));
        assertEquals(30.0, WheelForce.rolling(set), 1e-9);
        assertEquals(2 * 0.3 * 20 * 10, WheelForce.brakeCapacity(set), 1e-9);
        assertEquals(1.75, WheelForce.drivePerSpeed(set), 1e-9, "only driven wheels drive");
    }

    @Test
    void normalMassIsTheWholeMassAtTheCentreAndLessAwayFromIt() {
        double[] inertia = {40, 0, 0, 0, 50, 0, 0, 0, 60};
        assertEquals(10.0, WheelForce.normalMass(10.0, inertia, 0, 0, 0), 1e-9);
        double off = WheelForce.normalMass(10.0, inertia, 2.0, -1.0, 3.0);

        double expected = 1.0 / (9.0 / 40.0 + 4.0 / 60.0 + 0.1);
        assertEquals(expected, off, 1e-9);
        assertTrue(off < 10.0);
        assertTrue(Double.isNaN(WheelForce.normalMass(10.0, new double[9], 1, 0, 1)), "a singular tensor gives no answer");
    }

    @Test
    void theInverseOfAGeneralMatrixIsAnInverse() {
        double[] m = {4, 1, 0.5, 1, 5, 0.25, 0.5, 0.25, 6};
        double[] inv = WheelForce.invert3(m);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) sum += m[k * 3 + r] * inv[c * 3 + k];
                assertEquals(r == c ? 1.0 : 0.0, sum, 1e-9, "M * M^-1 at " + r + "," + c);
            }
        }
        assertNull(WheelForce.invert3(new double[]{1, 2, 3, 2, 4, 6, 1, 1, 1}));
    }
}
