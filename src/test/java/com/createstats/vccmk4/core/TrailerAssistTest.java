package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerAssistTest {
    private static final List<WheelForce.Wheel> WHEELS = List.of(
            WheelForce.Wheel.typical(true), WheelForce.Wheel.typical(true),
            WheelForce.Wheel.typical(false), WheelForce.Wheel.typical(false));

    private static TrailerAssist.Train train(double a0, double speed, boolean holding) {
        return new TrailerAssist.Train(a0, speed, holding, 0.5, 0, 128);
    }

    @Test
    void mirrorGivesTheTractorsThrottleAndBrakeAsTheyAre() {
        TrailerAssist.Command drive = TrailerAssist.mirror(new TrailerAssist.Train(1.0, 5.0, false, 0.5, 0, 128));
        assertEquals(64.0, drive.mountSpeed(), 1e-9);
        assertEquals(0, drive.brake());
        TrailerAssist.Command brake = TrailerAssist.mirror(new TrailerAssist.Train(-3.0, 5.0, false, 0.0, 15, 128));
        assertEquals(0.0, brake.mountSpeed(), 1e-9);
        assertEquals(15, brake.brakeLeft());
        assertEquals(15, brake.brakeRight());
        assertTrue(brake.touch());
    }

    @Test
    void offTouchesNothing() {
        assertFalse(TrailerAssist.Command.OFF.touch());
    }

    @Test
    void assistDrivesTheForceItIsAsked() {
        TrailerAssist.Command c = TrailerAssist.assist(train(2.0, 5.0, false), 300.0, 0.0, WHEELS, 5.0, 256, Double.NaN);
        double expected = (300.0 + WheelForce.rolling(WHEELS) * 5.0) / WheelForce.drivePerSpeed(WHEELS);
        assertEquals(expected, c.mountSpeed(), 1e-9);
        assertEquals(0, c.brake());
        assertEquals(1.0, c.made(), 1e-9);
    }

    @Test
    void fromRestASlackRopeNeverBacksTheTrailerIntoWhatIsBehindIt() {
        double demand = 50.0;
        double slackTrim = LinkTrim.rope(100, -2.0, 0.0);
        TrailerAssist.Command c = TrailerAssist.assist(train(1.0, 0.0, false), demand, slackTrim, WHEELS, 0.0, 256, Double.NaN);
        assertEquals(0.0, c.mountSpeed(), 1e-9, "wait for the rope to come taut, do not reverse");
        assertEquals(0.0, c.pushForce(), 1e-9, "and the jets are asked for nothing against the tractor either");
        TrailerAssist.Command going = TrailerAssist.assist(train(1.0, 0.0, false), demand, 0.0, WHEELS, 0.0, 256, Double.NaN);
        assertTrue(going.mountSpeed() > 0.0, "with the rope taut it pulls away with the tractor");
    }

    @Test
    void heldAtRestTheTrailerHoldsToo() {
        TrailerAssist.Command c = TrailerAssist.assist(train(0.0, 0.0, true), 0.0, 0.0, WHEELS, 0.0, 256, Double.NaN);
        assertEquals(15, c.brakeLeft());
        assertEquals(15, c.brakeRight());
    }

    @Test
    void theSwayGuardStretchesTheTrainAndBrakesTheSideThatTurnsItBack() {
        TrailerAssist.Command calm = TrailerAssist.assist(train(0.0, 10.0, false), 0.0, 0.0, WHEELS, 10.0, 256, 0.1);
        assertFalse(calm.swaying(), "a small error is ordinary steering");
        TrailerAssist.Command clockwise = TrailerAssist.assist(train(0.0, 10.0, false), 0.0, 0.0, WHEELS, 10.0, 256, -1.2);
        assertTrue(clockwise.swaying());
        assertTrue(clockwise.brakeLeft() > clockwise.brakeRight(), "left brakes yaw it back anticlockwise");
        assertEquals(0.0, clockwise.mountSpeed(), 1e-9, "no drive against its own brakes");
        assertTrue(clockwise.swayAccel() > 0.0, "side jets are asked to yaw it anticlockwise");
        assertEquals(-TrailerAssist.SWAY_DAMPING * -1.2, clockwise.swayAccel(), 1e-9);
        assertEquals(0.0, calm.swayAccel(), 1e-9, "nothing asked of the side jets while it does not sway");
        TrailerAssist.Command anticlockwise = TrailerAssist.assist(train(0.0, 10.0, false), 0.0, 0.0, WHEELS, 10.0, 256, 1.2);
        assertTrue(anticlockwise.brakeRight() > anticlockwise.brakeLeft());
        TrailerAssist.Command slow = TrailerAssist.assist(train(0.0, 1.0, false), 0.0, 0.0, WHEELS, 1.0, 256, -1.2);
        assertFalse(slow.swaying(), "not at a crawl");
    }

    @Test
    void whatTheWheelsCannotMakeGoesToTheJets() {
        TrailerAssist.Command c = TrailerAssist.assist(train(5.0, 5.0, false), 5000.0, 0.0, WHEELS, 5.0, 100, Double.NaN);
        assertTrue(c.pushForce() > 0.0);
        assertTrue(c.made() < 1.0);
    }

    @Test
    void sideJetsTurnTheTrailerThroughItsInertiaAboutTheTowPoint() {
        double inertia = TrailerAssist.pivotInertia(400.0, 50.0, 4.0);
        assertEquals(400.0 + 50.0 * 16.0, inertia, 1e-9, "the parallel axis theorem");
        assertTrue(Double.isNaN(TrailerAssist.pivotInertia(0.0, 50.0, 1.0)));

        double torque = inertia * 2.0;
        double[] arms = {-6.0, 3.0};
        double[] ceiling = {1000.0, 1000.0};
        double[] u = ThrustShare.share(torque, ceiling, new double[]{1.0, 1.0}, arms, new boolean[]{true, true});
        assertEquals(torque, ThrustShare.delivered(u, ceiling, arms), 1e-9, "together they give exactly the torque asked");
        assertTrue(u[0] < 0.0 && u[1] > 0.0, "each pushes the way its arm turns the trailer the asked way");
        assertEquals(-u[0], u[1], 1e-9, "the same fraction of capacity each, so the far one turns more of it");
    }

    @Test
    void theWheelsCoverAThrustersRampAndBrakeOffItsFade() {
        double maxMount = 100.0;
        double wheelsMax = 2 * maxMount * WheelForce.DRIVE_PER_SPEED;
        TrailerAssist.Command cold = TrailerAssist.assist(train(2.0, 5.0, false), 1000.0, 0.0, WHEELS, 5.0, maxMount, Double.NaN, 0.0, 200.0);
        double propulsion = 1000.0 + WheelForce.rolling(WHEELS) * 5.0;
        assertEquals(propulsion - wheelsMax, cold.pushForce(), 1e-9, "the jets are asked for what the wheels cannot make");
        assertEquals(maxMount, cold.mountSpeed(), 1e-9, "and while the jets are dark the wheels give their limit");
        TrailerAssist.Command lit = TrailerAssist.assist(train(2.0, 5.0, false), 1000.0, 0.0, WHEELS, 5.0, maxMount, Double.NaN, cold.pushForce(), 200.0);
        assertEquals(cold.pushForce(), lit.pushForce(), 1e-9, "the jets' ask does not move once they make it");
        assertEquals(maxMount, lit.mountSpeed(), 1e-9, "the wheels are told exactly the rest");
        TrailerAssist.Command half = TrailerAssist.assist(train(2.0, 5.0, false), 300.0, 0.0, WHEELS, 5.0, 256, Double.NaN, 200.0, 200.0);
        double rest = (300.0 + WheelForce.rolling(WHEELS) * 5.0 - 200.0) / WheelForce.drivePerSpeed(WHEELS);
        assertEquals(rest, half.mountSpeed(), 1e-9, "with the thruster making 200 of a need the wheels could make alone, they make the rest");
        assertEquals(0.0, half.pushForce(), 1e-9, "and the jets are not asked for anything they will not be needed for");
        TrailerAssist.Command fade = TrailerAssist.assist(train(0.0, 5.0, false), 0.0, 0.0, WHEELS, 5.0, maxMount, Double.NaN, 600.0, 200.0);
        assertEquals(0.0, fade.pushForce(), 1e-9, "the tractor lifted off: the jets are cut");
        assertTrue(fade.brake() > 0, "and the wheels brake off the push the fading thruster still makes");
        assertEquals(0.0, fade.mountSpeed(), 1e-9);

        TrailerAssist.Command stopped = TrailerAssist.assist(train(0.0, 0.0, false), 0.0, 0.0, WHEELS, 0.2, maxMount, Double.NaN, 600.0, 200.0);
        assertEquals(0.0, stopped.pushForce(), 1e-9);
        assertTrue(stopped.mountSpeed() < 0.0, "the wheels lean against the fade");
    }

    @Test
    void thrustBrakingIsCappedSoTheFadeCannotReverseTheTrailer() {
        assertEquals(-200.0 * 1.0 / TrailerAssist.THRUST_BRAKE_STOP_SECONDS, TrailerAssist.jetTarget(-5000.0, 1.0, 200.0), 1e-9,
                "against the roll: no more than stops it in the fade's time");
        assertEquals(-300.0, TrailerAssist.jetTarget(-300.0, 10.0, 200.0), 1e-9, "under the cap it is asked as it is");
        assertEquals(0.0, TrailerAssist.jetTarget(-5000.0, 0.2, 200.0), 1e-9, "never thrust-brake from rest");
        assertEquals(-5000.0, TrailerAssist.jetTarget(-5000.0, -3.0, 200.0), 1e-9, "backwards with the roll is not braking");
        assertEquals(500.0, TrailerAssist.jetTarget(500.0, -3.0, 200.0), 1e-9, "a forward push while rolling back is braking, under its cap");
        assertEquals(-5000.0, TrailerAssist.jetTarget(-5000.0, 1.0, Double.NaN), 1e-9, "no mass, no cap");
        assertEquals(0.0, TrailerAssist.jetTarget(Double.NaN, 1.0, 200.0), 1e-9);
    }

    @Test
    void aTrailerWhoseLinkWentHoldsItsBrakes() {
        assertTrue(TrailerAssist.Command.HOLD.touch());
        assertEquals(15, TrailerAssist.Command.HOLD.brakeLeft());
        assertEquals(15, TrailerAssist.Command.HOLD.brakeRight());
        assertEquals(0.0, TrailerAssist.Command.HOLD.mountSpeed(), 1e-9);
    }
}
