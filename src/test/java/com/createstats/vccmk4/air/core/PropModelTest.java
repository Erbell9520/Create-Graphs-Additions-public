package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropModelTest {
    private static final double GAIN = 1.0;
    private static final double FLOW = 0.1;

    private static double sable(double rpm, double speedAlongPush, double pressure) {
        double airflow = FLOW * Math.abs(rpm);
        double scaling = airflow <= 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, (airflow - speedAlongPush) / airflow));
        return Math.signum(rpm) * GAIN * Math.abs(rpm) * scaling * pressure;
    }

    @Test
    void theInverseMatchesSablesFade() {
        for (double speed = 0.0; speed <= 20.0; speed += 2.5) {
            for (double force = 10.0; force <= 150.0; force += 35.0) {
                double rpm = PropModel.rpmFor(force, GAIN, FLOW, speed, 0.8, 256.0);
                if (Math.abs(rpm) >= 256.0) continue;
                assertEquals(force, sable(rpm, speed, 0.8), 1.0e-6, "speed " + speed + " force " + force);
                assertEquals(force, PropModel.force(rpm, GAIN, FLOW, speed, 0.8), 1.0e-6);
            }
        }
    }

    @Test
    void atSpeedThePropMustFirstKeepUpWithTheAir() {
        assertEquals(100.0 + 50.0, PropModel.rpmFor(50.0, GAIN, FLOW, 10.0, 1.0, 256.0), 1.0e-9);
        assertEquals(50.0, PropModel.rpmFor(50.0, GAIN, FLOW, 0.0, 1.0, 256.0), 1.0e-9);
    }

    @Test
    void pushingAgainstTheWayOfTravelPaysNoSlip() {
        assertEquals(-50.0, PropModel.rpmFor(-50.0, GAIN, FLOW, 10.0, 1.0, 256.0), 1.0e-9);
    }

    @Test
    void theStrongestPushFadesWithSpeed() {
        assertEquals(256.0, PropModel.maxForce(1, GAIN, FLOW, 0.0, 1.0, 256.0), 1.0e-9);
        assertEquals(156.0, PropModel.maxForce(1, GAIN, FLOW, 10.0, 1.0, 256.0), 1.0e-9);
        assertEquals(0.0, PropModel.maxForce(1, GAIN, FLOW, 25.6, 1.0, 256.0), 1.0e-9);
        assertEquals(256.0, PropModel.maxForce(-1, GAIN, FLOW, 10.0, 1.0, 256.0), 1.0e-9);
    }

    @Test
    void neverPastTheTopSpeed() {
        assertEquals(256.0, PropModel.rpmFor(1.0e6, GAIN, FLOW, 0.0, 1.0, 256.0), 1.0e-9);
        assertEquals(-128.0, PropModel.rpmFor(-1.0e6, GAIN, FLOW, 0.0, 1.0, 128.0), 1.0e-9);
    }

    @Test
    void nothingAskedIsNothingTurned() {
        assertEquals(0.0, PropModel.rpmFor(0.0, GAIN, FLOW, 10.0, 1.0, 256.0), 1.0e-12);
        assertEquals(0.0, PropModel.rpmFor(10.0, 0.0, FLOW, 0.0, 1.0, 256.0), 1.0e-12);
        assertTrue(PropModel.force(0.0, GAIN, FLOW, 0.0, 1.0) == 0.0);
    }
}
