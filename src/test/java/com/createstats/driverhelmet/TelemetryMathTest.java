package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryMathTest {
    private static final double EPS = 1e-9;

    @Test
    void aWheelRollingWithoutSlipTurnsAtItsRollingRpm() {
        final double r = 0.75;
        final double rpm = 60.0;
        final double speed = TelemetryMath.surfaceSpeed(rpm, r);
        assertEquals(2 * Math.PI * r, speed, EPS, "one turn a second is one circumference a second");
        assertEquals(rpm, TelemetryMath.rollingRpm(speed, r), 1e-9);
        assertEquals(0.0, TelemetryMath.slip(rpm, speed, r), 1e-9, "matched: no slip");
    }

    @Test
    void wheelspinIsPositiveAndTheGroundOutrunningADrivenWheelIsNegative() {
        final double r = 0.75;
        final double tyre = TelemetryMath.surfaceSpeed(120.0, r);
        assertEquals(0.5, TelemetryMath.slip(120.0, tyre / 2.0, r), 1e-9, "tyre twice the ground: half of it is slip");
        assertTrue(TelemetryMath.slip(60.0, tyre, r) < 0.0);
        assertEquals(1.0, TelemetryMath.slip(200.0, 0.0, r), 1e-9, "spinning on the spot is all slip");
    }

    @Test
    void creepingOffTheLineDoesNotReadAsSpin() {
        final double creep = TelemetryMath.slip(2.0, 0.0, 0.75);
        assertTrue(creep < 0.4, "two RPM off the line is not full wheelspin: " + creep);
        assertEquals(0.0, TelemetryMath.slip(60.0, 1.0, 0.0), EPS, "no tyre, no figure");
    }

    @Test
    void onFullGripTheWheelRollsWithTheGroundSoSlipIsZeroWhateverItsRpm() {
        final double r = 0.5;
        final double applied = 64.0;
        final double ground = applied * 1.75 / (0.075 * 200.0);
        assertEquals(-0.55, TelemetryMath.slip(applied, ground, r), 0.01, "the old figure, never zero");
        final double rolling = TelemetryMath.rollingRpm(ground, r);
        final double grip = TelemetryMath.grip(1.0, false);
        final double actual = TelemetryMath.actualRpm(TelemetryMath.freeRpm(applied, 0), rolling, grip, false);
        assertEquals(rolling, actual, 1e-9, "full grip: it turns at the ground's speed");
        assertEquals(0.0, TelemetryMath.slip(actual, ground, r), 1e-9);
        assertEquals(1.0, TelemetryMath.grip(1.6, false), 1e-9, "Offroad caps the push at full friction");
    }

    @Test
    void onASlipperyBlockTheWheelSpinsUnderPowerAndDragsUnderTheBrake() {
        final double r = 0.5;
        final double ground = 4.0;
        final double rolling = TelemetryMath.rollingRpm(ground, r);
        final double ice = 0.19;

        final double spinning = TelemetryMath.actualRpm(200.0, rolling, ice, false);
        assertEquals(200.0 + ice * (rolling - 200.0), spinning, 1e-9);
        assertTrue(TelemetryMath.slip(spinning, ground, r) > 0.3, "it spins");

        assertEquals(0.0, TelemetryMath.freeRpm(64.0, TelemetryMath.FULL_BRAKE), 1e-9);
        assertEquals(64.0 * 10.0 / 15.0, TelemetryMath.freeRpm(-64.0, 5), 1e-9, "a third of the brake, a third off");
        assertEquals(64.0, TelemetryMath.freeRpm(64.0, 0), 1e-9);
        final double braked = TelemetryMath.actualRpm(0.0, rolling, ice, false);
        assertEquals(-(1.0 - ice), TelemetryMath.slip(braked, ground, r), 1e-9, "on ice it drags");
        assertEquals(0.0, TelemetryMath.slip(TelemetryMath.actualRpm(0.0, rolling, 1.0, false), ground, r), 1e-9,
                "on stone it rolls: Offroad never locks a wheel on full grip");
    }

    @Test
    void aLiftedTyreSpinsFreeGripsNothingAndCarriesNothing() {
        final double r = 0.5;
        assertTrue(TelemetryMath.airborne(0.65), "Offroad's lifted marker");
        assertEquals(0.0, TelemetryMath.grip(1.0, true), 0.0);
        assertEquals(64.0, TelemetryMath.actualRpm(64.0, 30.0, 1.0, true), 1e-9, "free at its RPM");
        assertEquals(0.0, TelemetryMath.load(0.65, r), 0.0);
        assertFalse(TelemetryMath.airborne(0.9), "a measured distance");
        assertFalse(TelemetryMath.airborne(0.65 + 1e-9));
        assertFalse(TelemetryMath.airborne(Double.NaN));
        assertTrue(Double.isNaN(TelemetryMath.grip(Double.NaN, false)), "no reading, no figure");
    }

    @Test
    void loadIsHowFarTheSpringIsPressed() {
        final double r = 0.75;

        assertEquals(0.0, TelemetryMath.load(0.65 + r + 0.2, r), 1e-9, "past full reach: carrying nothing");
        assertEquals(1.0, TelemetryMath.load(r - 0.2, r), 1e-9, "bottomed out");
        final double half = TelemetryMath.load(r - 0.10833333333333334 + 0.325, r);
        assertEquals(0.5, half, 1e-9);
    }

    @Test
    void verticalGIsOneAtRestNoneInFreeFallAndASpikeOnLanding() {
        final double[] up = {0, 1, 0};
        final double[] gravity = {0, -9.81, 0};
        assertEquals(1.0, TelemetryMath.verticalG(new double[]{0, 0, 0}, up, gravity), 1e-9);
        assertEquals(0.0, TelemetryMath.verticalG(gravity, up, gravity), 1e-9, "falling with the world");
        assertEquals(3.0, TelemetryMath.verticalG(new double[]{0, 19.62, 0}, up, gravity), 1e-9);
        assertEquals(1.0, TelemetryMath.verticalG(new double[]{0, 0, 0}, up, new double[]{0, -32.0, 0}), 1e-9,
                "one g is the world's own gravity");
    }

    @Test
    void accelerationSplitsIntoLateralAndLongitudinalInTheVehiclesFrame() {
        final double[] north = {0, 0, -1};
        final double[] up = {0, 1, 0};
        final double g = 9.81;
        final double[] braking = TelemetryMath.gForce(new double[]{0, 0, 9.81}, north, up, g);
        assertEquals(0.0, braking[0], 1e-9);
        assertEquals(-1.0, braking[1], 1e-9, "decelerating heading north is minus one g longitudinal");
        final double[] turning = TelemetryMath.gForce(new double[]{4.905, 0, 0}, north, up, g);
        assertEquals(0.5, turning[0], 1e-9, "pushed east while heading north: half a g to the right");
        final double[] strongGravity = TelemetryMath.gForce(new double[]{0, 0, -32.0}, north, up, 32.0);
        assertEquals(1.0, strongGravity[1], 1e-9, "one g is the world's own gravity, whatever it is");
    }

    @Test
    void pitchIsNoseUpAndRollIsRightSideDown() {
        final double s = Math.sin(Math.toRadians(10));
        final double c = Math.cos(Math.toRadians(10));
        assertEquals(10.0, TelemetryMath.pitch(new double[]{0, s, -c}), 1e-9);

        assertEquals(10.0, TelemetryMath.roll(new double[]{0, 0, -1}, new double[]{s, c, 0}), 1e-9);
        assertEquals(0.0, TelemetryMath.roll(new double[]{0, 0, -1}, new double[]{0, 1, 0}), 1e-9);
    }
}
