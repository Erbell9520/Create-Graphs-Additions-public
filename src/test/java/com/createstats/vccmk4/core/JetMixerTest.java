package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JetMixerTest {
    private static final JetMixer.Mode WHEELS = JetMixer.Mode.DEFAULT;
    private static final JetMixer.Mode JET_DRIVE = new JetMixer.Mode(false, true);

    private static JetMixer.Jet jet(double right, double forward, double axisRight, double axisUp, double axisForward) {
        return new JetMixer.Jet(right, 0.0, forward, axisRight, axisUp, axisForward, JetRole.AUTO);
    }

    private static JetMixer.Jet withRole(JetMixer.Jet j, JetRole role) {
        return new JetMixer.Jet(j.right(), j.up(), j.forward(), j.axisRight(), j.axisUp(), j.axisForward(), role);
    }

    private static JetMixer.Demand keys(boolean boost, double steer, double throttleKey) {
        double pushDir = throttleKey < 0 ? -1.0 : 1.0;
        return new JetMixer.Demand(boost, pushDir, steer, false, throttleKey, throttleKey, false, false, 0.0, 0.0,
                new double[4]);
    }

    private static double yaw(JetMixer.Jet j, double u) {
        return JetMixer.yawArm(j) * u;
    }

    @Test
    void theOwnersFanTurnsLeftOnAAndCtrl() {
        JetMixer.Jet fan = jet(0.0, 3.0, -1.0, 0.0, 0.0);
        assertEquals(JetMixer.Job.TURN, JetMixer.autoJob(fan));
        double left = JetMixer.command(fan, keys(true, -1.0, 0.0), WHEELS);
        assertEquals(1.0, left, 1.0e-9, "pushes left at the nose");
        assertTrue(yaw(fan, left) < 0, "nose left");
        double right = JetMixer.command(fan, keys(true, 1.0, 0.0), WHEELS);
        assertEquals(-1.0, right, 1.0e-9, "the same fan spun the other way turns right");
        assertTrue(yaw(fan, right) > 0);
    }

    @Test
    void aTurningFanWaitsForBoostUnlessTold() {
        JetMixer.Jet fan = jet(0.0, 3.0, -1.0, 0.0, 0.0);
        assertEquals(0.0, JetMixer.command(fan, keys(false, -1.0, 0.0), WHEELS), 1.0e-9, "A alone steers the wheels only");
        JetMixer.Mode alone = new JetMixer.Mode(true, false);
        assertEquals(1.0, JetMixer.command(fan, keys(false, -1.0, 0.0), alone), 1.0e-9);
        assertEquals(0.0, JetMixer.command(fan, keys(true, 0.0, 0.0), WHEELS), 1.0e-9, "Boost with no steering: nothing to turn");
    }

    @Test
    void aFanBehindTheCentreTurnsTheSameWayByPushingTheOtherWay() {
        JetMixer.Jet tail = jet(0.0, -3.0, -1.0, 0.0, 0.0);
        double u = JetMixer.command(tail, keys(true, -1.0, 0.0), WHEELS);
        assertTrue(yaw(tail, u) < 0, "a left turn swings the tail right");
        assertEquals(-1.0, u, 1.0e-9);
    }

    @Test
    void jetsAlongTheVehiclePushOnBoostWhicheverWayTheyFace() {
        JetMixer.Jet backward = jet(0.0, -2.0, 0.0, 0.0, -1.0);
        JetMixer.Jet forward = jet(0.0, -2.0, 0.0, 0.0, 1.0);
        assertEquals(JetMixer.Job.PUSH, JetMixer.autoJob(backward));
        assertEquals(-1.0, JetMixer.command(backward, keys(true, 0.0, 0.0), WHEELS), 1.0e-9, "reversed spin pushes forward");
        assertEquals(1.0, JetMixer.command(forward, keys(true, 0.0, 0.0), WHEELS), 1.0e-9);
        assertEquals(0.0, JetMixer.command(forward, keys(false, 0.0, 1.0), WHEELS), 1.0e-9, "on wheels, W alone does not fire a boost jet");
        assertEquals(-1.0, JetMixer.command(forward, keys(true, 0.0, -1.0), WHEELS), 1.0e-9, "Boost with the reverse key pushes back");
    }

    @Test
    void twinRearJetsSteerByEasingOffTheInside() {
        JetMixer.Jet left = jet(-1.0, -2.0, 0.0, 0.0, 1.0);
        JetMixer.Jet right = jet(1.0, -2.0, 0.0, 0.0, 1.0);
        double[] u = JetMixer.mix(List.of(left, right), keys(true, -1.0, 0.0), WHEELS);
        assertEquals(0.0, u[0], 1.0e-9, "inside jet off at full stick");
        assertEquals(1.0, u[1], 1.0e-9, "outside jet at full");
        assertTrue(yaw(left, u[0]) + yaw(right, u[1]) < 0, "net yaw is to the left");
        double[] half = JetMixer.mix(List.of(left, right), keys(true, -0.5, 0.0), WHEELS);
        assertEquals(0.5, half[0], 1.0e-9);
        assertEquals(1.0, half[1], 1.0e-9);
        double[] straight = JetMixer.mix(List.of(left, right), keys(true, 0.0, 0.0), WHEELS);
        assertEquals(1.0, straight[0], 1.0e-9);
        assertEquals(1.0, straight[1], 1.0e-9);
    }

    @Test
    void rollingBackwardsTheJetsFollowTheWheelsInsteadOfFightingThem() {
        JetMixer.Jet fan = jet(0.0, 3.0, -1.0, 0.0, 0.0);
        JetMixer.Demand backing = new JetMixer.Demand(true, -1.0, -1.0, true, -0.5, -1.0, false, false, 0.0, 0.0, new double[4]);
        double u = JetMixer.command(fan, backing, WHEELS);
        assertTrue(yaw(fan, u) > 0, "reversing with A swings the nose right, as the wheels do");
        assertTrue(yaw(fan, JetMixer.command(fan, backing, JET_DRIVE)) < 0, "a hovercraft turns the way the key says");
    }

    @Test
    void upAndDownJetsLiftOnLiftAndNeverOnBoost() {
        JetMixer.Jet up = jet(1.0, 1.0, 0.0, 1.0, 0.0);
        JetMixer.Jet down = jet(-1.0, 1.0, 0.0, -1.0, 0.0);
        assertEquals(JetMixer.Job.LIFT, JetMixer.autoJob(up));
        assertEquals(JetMixer.Job.LIFT, JetMixer.autoJob(down));
        assertEquals(0.0, JetMixer.command(up, keys(true, 1.0, 1.0), WHEELS), 1.0e-9, "the owner's complaint: not a boost jet");
        JetMixer.Demand lift = new JetMixer.Demand(false, 1.0, 0.0, false, 0.0, 0.0, false, false, 1.0, 0.0, new double[4]);
        assertEquals(1.0, JetMixer.command(up, lift, WHEELS), 1.0e-9);
        assertEquals(-1.0, JetMixer.command(down, lift, WHEELS), 1.0e-9, "a downward jet spins the other way to push up");
    }

    @Test
    void aSidewaysJetAtTheCentreSlides() {
        JetMixer.Jet side = jet(0.0, 0.3, 1.0, 0.0, 0.0);
        assertEquals(JetMixer.Job.SLIDE, JetMixer.autoJob(side));
        JetMixer.Demand slideLeft = new JetMixer.Demand(false, 1.0, 0.0, false, 0.0, 0.0, false, false, 0.0, -1.0, new double[4]);
        assertEquals(-1.0, JetMixer.command(side, slideLeft, WHEELS), 1.0e-9);
        assertEquals(0.0, JetMixer.command(side, keys(true, -1.0, 0.0), WHEELS), 1.0e-9, "no arm, so no part in a turn");
    }

    @Test
    void aVehicleWithNoDrivenWheelsDrivesOnItsJets() {
        JetMixer.Jet left = jet(-1.0, -2.0, 0.0, 0.0, 1.0);
        JetMixer.Jet right = jet(1.0, -2.0, 0.0, 0.0, 1.0);
        double[] go = JetMixer.mix(List.of(left, right), keys(false, 0.0, 1.0), JET_DRIVE);
        assertEquals(1.0, go[0], 1.0e-9, "W pushes");
        assertEquals(1.0, go[1], 1.0e-9);
        double[] spin = JetMixer.mix(List.of(left, right), keys(false, -1.0, 0.0), JET_DRIVE);
        assertEquals(-1.0, spin[0], 1.0e-9, "no throttle, A: the sides run opposite ways");
        assertEquals(1.0, spin[1], 1.0e-9);
        JetMixer.Demand brake = new JetMixer.Demand(false, 1.0, 0.0, false, 0.0, 1.0, false, true, 0.0, 0.0, new double[4]);
        assertEquals(0.0, JetMixer.command(left, brake, JET_DRIVE), 1.0e-9, "the brake key stops the push");
    }

    @Test
    void wheelBrakingCutsThePushButNotTheTurn() {
        JetMixer.Jet rear = jet(0.0, -2.0, 0.0, 0.0, 1.0);
        JetMixer.Jet fan = jet(0.0, 3.0, -1.0, 0.0, 0.0);
        JetMixer.Demand braking = new JetMixer.Demand(true, 1.0, -1.0, false, 0.0, 1.0, true, true, 0.0, 0.0, new double[4]);
        assertEquals(0.0, JetMixer.command(rear, braking, WHEELS), 1.0e-9);
        assertEquals(1.0, JetMixer.command(fan, braking, WHEELS), 1.0e-9, "brake, boost and steer: a handbrake turn");
    }

    @Test
    void rolesOverrideTheGeometry() {
        JetMixer.Jet fan = jet(0.0, 3.0, -1.0, 0.0, 0.0);
        assertEquals(0.0, JetMixer.command(withRole(fan, JetRole.OFF), keys(true, -1.0, 0.0), WHEELS), 1.0e-9);
        JetMixer.Jet pusher = withRole(jet(0.0, -2.0, 0.0, 0.0, 1.0), JetRole.THROTTLE);
        assertEquals(0.6, JetMixer.command(pusher, new JetMixer.Demand(false, 1.0, 0.0, false, 0.6, 1.0, false, false,
                0.0, 0.0, new double[4]), WHEELS), 1.0e-9, "a propeller car follows the throttle");
        JetMixer.Jet onKey = withRole(fan, JetRole.AUX_B);
        assertEquals(-1.0, JetMixer.command(onKey, new JetMixer.Demand(false, 1.0, 0.0, false, 0.0, 0.0, false, false,
                0.0, 0.0, new double[]{0, -1, 0, 0}), WHEELS), 1.0e-9);
        assertEquals(JetMixer.Job.PUSH, JetMixer.jobOf(withRole(fan, JetRole.PUSH)), "a forced job replaces the geometry's");
        assertEquals(null, JetMixer.jobOf(onKey));
    }

    @Test
    void commandsStayInRange() {
        JetMixer.Jet corner = jet(-2.0, -3.0, 0.5, 0.0, 0.8);
        for (double steer = -1.0; steer <= 1.0; steer += 0.25) {
            for (JetMixer.Mode m : new JetMixer.Mode[]{WHEELS, JET_DRIVE, new JetMixer.Mode(true, false)}) {
                double u = JetMixer.command(corner, keys(true, steer, 1.0), m);
                assertTrue(u >= -1.0 && u <= 1.0, "command " + u);
            }
        }
    }
}
