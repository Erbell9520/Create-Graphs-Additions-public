package com.createstats.ground;

import java.util.ArrayList;
import java.util.List;

import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Body;
import com.createstats.ground.GroundSnapshot.Governor;
import com.createstats.ground.GroundSnapshot.Supply;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkLogic;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.WheelForce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveMathTest {
    private static final double EPS = 1.0e-6;

    static Wheel wheel(int unit, double x, double z, boolean driven, boolean steered, double angle) {
        return new Wheel(unit, -1, driven ? 0 : -1, (float) x, -0.6f, (float) z, driven, steered, 0.5f, 10f, 10f,
                (float) angle, driven ? 128f : 0f, 100f, 0f, 1f, 0.4f, false, 0);
    }

    static GroundSnapshot with(Body body, List<Wheel> wheels, List<Governor> governors, double mass, SteerPreset preset) {
        return new GroundSnapshot(100L, null, body, null, null, new GroundSnapshot.Setup(preset, true, false, false,
                DriveModel.Options.DEFAULT, 2, true), new GroundSnapshot.Mass(mass, mass, Double.NaN, true, V3.ZERO, 40),
                wheels, governors, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    static Body heading(double deg, double speed) {
        final Quat q = GroundFixtures.orientation(deg, 0.0);
        return new Body(V3.ZERO, q, Body.NORTH, q.rotate(Body.NORTH).times(speed), 0.0, speed, V3.ZERO,
                new V3(0, -11, 0));
    }

    @Test
    void headingFollowsTheNoseNorthUpEastRight() {
        assertEquals(0.0, DriveMath.read(with(heading(0, 0), List.of(), List.of(), 100, SteerPreset.FRONT)).heading, 1e-9);
        assertEquals(90.0, DriveMath.read(with(heading(90, 0), List.of(), List.of(), 100, SteerPreset.FRONT)).heading, 1e-9);
        assertEquals(215.0, DriveMath.read(with(heading(215, 0), List.of(), List.of(), 100, SteerPreset.FRONT)).heading, 1e-9);
    }

    @Test
    void pitchAndGradeFromTheOrientation() {
        final Quat q = GroundFixtures.orientation(30.0, 10.0);
        final Body b = new Body(V3.ZERO, q, Body.NORTH, V3.ZERO, 0.0, 0.0, V3.ZERO, new V3(0, -11, 0));
        final DriveReadout r = DriveMath.read(with(b, List.of(), List.of(), 100, SteerPreset.FRONT));
        assertEquals(10.0, r.pitch, 1e-9);
        assertEquals(Math.tan(Math.toRadians(10.0)) * 100.0, r.grade, 1e-9);
        assertEquals(30.0, r.heading, 1e-9);
        assertEquals(0.0, r.roll, 1e-9);
    }

    @Test
    void speedTrackAndDrift() {
        final Body b = new Body(V3.ZERO, Quat.IDENTITY, Body.NORTH, new V3(1.0, 0.0, -10.0), 0.0, 10.0, V3.ZERO,
                new V3(0, -11, 0));
        final DriveReadout r = DriveMath.read(with(b, List.of(), List.of(), 100, SteerPreset.FRONT));
        assertEquals(10.0, r.speed, EPS);
        assertEquals(36.0, r.speedKmh, EPS);
        assertEquals(Math.hypot(1.0, 10.0), r.groundSpeed, EPS);
        assertEquals(1.0, r.sideSpeed, EPS);
        assertEquals(Math.toDegrees(Math.atan2(1.0, 10.0)), r.track, EPS);
        assertEquals(Math.toDegrees(Math.atan2(1.0, 10.0)), r.drift, EPS);
    }

    @Test
    void aVehicleBarelyMovingHasNoTrack() {
        final Body b = new Body(V3.ZERO, Quat.IDENTITY, Body.NORTH, new V3(0.1, 0.0, -0.1), 0.0, 0.1, V3.ZERO,
                new V3(0, -11, 0));
        final DriveReadout r = DriveMath.read(with(b, List.of(), List.of(), 100, SteerPreset.FRONT));
        assertTrue(Double.isNaN(r.track));
        assertEquals(0.0, r.drift, 0.0);
    }

    @Test
    void turnRateAndRadiusFromTheYawRate() {
        final Body b = new Body(V3.ZERO, Quat.IDENTITY, Body.NORTH, new V3(0.0, 0.0, -5.0), 0.5, 5.0, V3.ZERO,
                new V3(0, -11, 0));
        final DriveReadout r = DriveMath.read(with(b, List.of(), List.of(), 100, SteerPreset.FRONT));
        assertEquals(-Math.toDegrees(0.5), r.turnRate, EPS);
        assertEquals(10.0, r.turnRadius, EPS);
    }

    @Test
    void gForcesInTheWorldsOwnGravity() {
        final Body b = new Body(V3.ZERO, Quat.IDENTITY, Body.NORTH, V3.ZERO, 0.0, 0.0, new V3(5.5, 0.0, -2.2),
                new V3(0, -11, 0));
        final DriveReadout r = DriveMath.read(with(b, List.of(), List.of(), 100, SteerPreset.FRONT));
        assertEquals(0.5, r.latG, EPS);
        assertEquals(0.2, r.lonG, EPS);
        assertEquals(1.0, r.vertG, EPS);
        assertEquals(Math.hypot(0.5, 0.2), r.combinedG, EPS);
    }

    @Test
    void theTurnCentreOfAnExactAckermannCarIsItsOwn() {
        final double rx = 10.0;
        final double rearZ = -2.0;
        final List<Wheel> wheels = new ArrayList<>();
        for (final double x : new double[]{-1.0, 1.0}) {
            final double angle = Math.toDegrees(Math.atan2(2.0 - rearZ, rx - x));
            wheels.add(wheel(0, x, 2.0, false, true, angle));
            wheels.add(wheel(0, x, rearZ, true, false, 0.0));
        }
        final double[] c = DriveMath.turnCentre(wheels);
        assertEquals(rx, c[0], 1e-6);
        assertEquals(rearZ, c[1], 1e-6);
        final DriveReadout r = DriveMath.read(with(heading(0, 5), wheels, List.of(), 100, SteerPreset.FRONT));
        assertEquals(Math.hypot(rx, rearZ), r.pathRadius, 1e-6);
        assertEquals(Math.hypot(rx + 1.0, 2.0 - rearZ), r.kerbRadius, 1e-6);
    }

    @Test
    void offroadsTwoDegreeStepsLandNearTheTrueCentre() {
        final double rx = 12.0;
        final List<Wheel> wheels = new ArrayList<>();
        for (final double x : new double[]{-1.1, 1.1}) {
            final double exact = Math.toDegrees(Math.atan2(4.0, rx - x));
            wheels.add(wheel(0, x, 2.0, false, true, Math.round(exact / 2.0) * 2.0));
            wheels.add(wheel(0, x, -2.0, true, false, 0.0));
        }
        final double[] c = DriveMath.turnCentre(wheels);
        assertEquals(rx, c[0], 1.5);
        assertEquals(-2.0, c[1], 0.3);
    }

    @Test
    void straightWheelsAgreeOnNoCentre() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, false, true, 0.0), wheel(0, 1, 2, false, true, 0.2),
                wheel(0, -1, -2, true, false, 0.0), wheel(0, 1, -2, true, false, 0.0));
        assertNull(DriveMath.turnCentre(wheels));
    }

    @Test
    void crabSteeringReportsTheSharedAngle() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, false, true, 12.0), wheel(0, 1, 2, false, true, 12.0),
                wheel(0, -1, -2, true, true, 12.0), wheel(0, 1, -2, true, true, 12.0));
        final DriveReadout r = DriveMath.read(with(heading(0, 2), wheels, List.of(), 100, SteerPreset.CRAB));
        assertEquals(12.0, r.crabAngle, 1e-6);
        assertTrue(Double.isNaN(r.centreX));
    }

    @Test
    void aTrackedVehiclesSidesFromItsMotion() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 1, true, false, 0), wheel(0, 1, 1, true, false, 0),
                wheel(0, -1, -1, true, false, 0), wheel(0, 1, -1, true, false, 0));
        final Body b = new Body(V3.ZERO, Quat.IDENTITY, Body.NORTH, new V3(0, 0, -2), 1.0, 2.0, V3.ZERO,
                new V3(0, -11, 0));
        final DriveReadout r = DriveMath.read(with(b, wheels, List.of(), 100, SteerPreset.SKID));
        assertEquals(2.0, r.trackWidth, 1e-6);
        assertEquals(1.0, r.leftSpeed, 1e-6);
        assertEquals(3.0, r.rightSpeed, 1e-6);
    }

    static Governor governor(float ratio) {
        return new Governor("G1", 0, 0f, 0f, 128, 128, ratio, 4, 0, false, Supply.OK, false);
    }

    @Test
    void cruiseIsWhereOffroadsDriveMeetsItsDrag() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, true, true, 0), wheel(0, 1, 2, true, true, 0),
                wheel(0, -1, -2, true, false, 0), wheel(0, 1, -2, true, false, 0));
        final GroundSnapshot s = with(heading(0, 10), wheels, List.of(governor(1f)), 180.0, SteerPreset.FRONT);
        final DriveReadout.Model m = DriveMath.model(s, 0.0, 10.0);
        final double drive = 4 * WheelForce.drive(128, 0.0, 1.0);
        final double drag = 4 * WheelForce.dragPerSpeed(WheelForce.Wheel.typical(true), 0.0);
        final double brake = 4 * WheelForce.dragPerSpeed(WheelForce.Wheel.typical(true), 1.0);
        assertEquals(drive, m.driveForce(), 1e-9);
        assertEquals(drag, m.dragPerSpeed(), 1e-9);
        assertEquals(drive / drag, m.cruise(), 1e-9);
        assertEquals(896.0 / 60.0, m.cruise(), 1e-9);
        assertEquals(m.cruise() * 0.5, m.reverse(), 1e-9);
        assertEquals(180.0 / drag, m.tau(), 1e-9);
        assertEquals(180.0 / drag * Math.log(10.0), m.toNinety(), 1e-9);
        assertEquals(180.0 / brake, m.stopTau(), 1e-9);
        assertEquals(Math.toDegrees(Math.asin(drive / (180.0 * 11.0))), m.hill(), 1e-9);
        assertEquals(180.0 / brake * (10.0 - DriveModel.HOLD_SPEED), m.stopDistance(), 1e-9);
    }

    @Test
    void cruiseDoesNotDependOnMassAtTheDefaultStrength() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, true, true, 0), wheel(0, 1, 2, true, true, 0));
        final double light = DriveMath.model(with(heading(0, 0), wheels, List.of(governor(1f)), 50, SteerPreset.FRONT), 0, 0).cruise();
        final double heavy = DriveMath.model(with(heading(0, 0), wheels, List.of(governor(1f)), 5000, SteerPreset.FRONT), 0, 0).cruise();
        assertEquals(light, heavy, 1e-12);
    }

    @Test
    void theGovernorsClampBindsThroughItsGearing() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, true, true, 0), wheel(0, 1, 2, true, true, 0));
        final GroundSnapshot s = with(heading(0, 0), wheels, List.of(governor(0.25f)), 100, SteerPreset.FRONT);
        final DriveReadout.Model m = DriveMath.model(s, 0.0, 0.0);
        assertEquals(64.0, m.mountMax(), 1e-9);
        assertEquals(2 * WheelForce.drive(64, 0, 1) / (2 * 15.0), m.cruise(), 1e-9);
    }

    @Test
    void aSlopeCutsTheCruiseByTheWeightAlongIt() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, true, true, 0), wheel(0, 1, 2, true, true, 0),
                wheel(0, -1, -2, true, false, 0), wheel(0, 1, -2, true, false, 0));
        final GroundSnapshot s = with(heading(0, 0), wheels, List.of(governor(1f)), 180, SteerPreset.FRONT);
        final DriveReadout.Model m = DriveMath.model(s, 10.0, 0.0);
        final double expect = (896.0 - 180.0 * 11.0 * Math.sin(Math.toRadians(10.0))) / 60.0;
        assertEquals(expect, m.cruiseHere(), 1e-9);
    }

    @Test
    void noDrivenWheelNoModel() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, false, true, 0), wheel(0, 1, 2, false, true, 0));
        assertFalse(DriveMath.model(with(heading(0, 0), wheels, List.of(), 100, SteerPreset.FRONT), 0, 0).known());
    }

    @Test
    void anUnknownMassModelsCruiseButNoTimes() {
        final List<Wheel> wheels = List.of(wheel(0, -1, 2, true, true, 0), wheel(0, 1, 2, true, true, 0));
        final DriveReadout.Model m = DriveMath.model(with(heading(0, 0), wheels, List.of(governor(1f)), Double.NaN,
                SteerPreset.FRONT), 0, 5);
        assertTrue(m.known());
        assertTrue(Double.isNaN(m.tau()));
        assertTrue(Double.isNaN(m.hill()));
        assertTrue(Double.isNaN(m.stopDistance()));
    }

    @Test
    void stopTimeIsTheExponentialDecayToTheHold() {
        assertEquals(0.6 * Math.log(10.0 / DriveModel.HOLD_SPEED), DriveMath.stopTime(10.0, 0.6), 1e-12);
        assertEquals(0.0, DriveMath.stopTime(0.2, 0.6), 0.0);
    }

    @Test
    void theStaticStabilityFactorOfARectangle() {
        final List<Wheel> wheels = List.of(wheel(0, -2, 2, true, true, 0), wheel(0, 2, 2, true, true, 0),
                wheel(0, -2, -2, true, false, 0), wheel(0, 2, -2, true, false, 0));
        final DriveReadout r = DriveMath.read(with(heading(0, 0), wheels, List.of(), 100, SteerPreset.FRONT));
        assertEquals(1.1, r.comHeight, 1e-6);
        assertEquals(4.0 / 2.2, r.stability, 1e-6);
        assertEquals(Math.toDegrees(Math.atan(4.0 / 2.2)), r.tipAngle, 1e-6);
    }

    @Test
    void statesFromWhoDrivesAndWhatMoves() {
        assertEquals(DriveReadout.State.DRIVING, DriveMath.read(GroundFixtures.car()).state);
        assertEquals(DriveReadout.State.PARKED, DriveMath.read(GroundFixtures.parked()).state);
        assertEquals(DriveReadout.State.TOWED, DriveMath.read(GroundFixtures.towed()).state);
        assertEquals(DriveReadout.State.RUNAWAY, DriveMath.read(GroundFixtures.runaway()).state);
        assertEquals(DriveReadout.State.OFF_CRAFT, DriveMath.read(GroundSnapshot.empty(0L)).state);
    }

    @Test
    void indicatorsBlinkInPhaseWithTheLamps() {
        final GroundSnapshot s = GroundFixtures.braking();

        assertTrue(s.lights().steer() > LinkLogic.SIGNAL_STEER);
        assertTrue(s.lights().driving());

        assertFalse(DriveMath.indicator(GroundFixtures.car(), false, 0L));
        for (long t = 0; t < 48; t++) {
            final boolean on = Math.floorMod(t / LinkLogic.BLINK_TICKS, 2L) == 0L;
            assertEquals(on, DriveMath.indicator(s, false, t), "tick " + t);
            assertFalse(DriveMath.indicator(s, true, t), "left, tick " + t);
        }
        assertFalse(DriveMath.indicator(GroundFixtures.parked(), false, 0L), "nobody drives: every lamp dark");
    }

    @Test
    void placingTurnsAUnitFrameIntoTheVehicleFrame() {
        final Unit u = new Unit(1, 0, BodyRole.TRAILER, LinkKind.HITCH, TrailerMode.ASSIST, true, false, 3f, -6f, 90f,
                90f, 0f, Float.NaN, Float.NaN, Float.NaN, 0, 4, 100f, "B2");

        final double[] ahead = DriveMath.place(u, 0.0, 1.0);
        assertEquals(4.0, ahead[0], 1e-9);
        assertEquals(-6.0, ahead[1], 1e-9);
        final double[] right = DriveMath.place(u, 1.0, 0.0);
        assertEquals(3.0, right[0], 1e-9);
        assertEquals(-7.0, right[1], 1e-9);
    }

    @Test
    void ropeReadingsBecomeWords() {
        assertEquals(DriveReadout.LinkState.SLACK, DriveMath.linkState(rope(-0.6f, 0)));
        assertEquals(DriveReadout.LinkState.TAUT, DriveMath.linkState(rope(-0.05f, 0)));
        assertEquals(DriveReadout.LinkState.STRETCHED, DriveMath.linkState(rope(0.3f, 0)));
        assertEquals(DriveReadout.LinkState.GONE, DriveMath.linkState(rope(0.3f, Unit.FLAG_LOST)));
    }

    static Unit rope(float reading, int flags) {
        return new Unit(1, 0, BodyRole.TRAILER, LinkKind.ROPE, TrailerMode.ASSIST, true, false, 0f, -8f, 0f, 0f, 0f,
                reading, Float.NaN, Float.NaN, flags, 4, 100f, "B2");
    }

    @Test
    void theFixturesAreConsistentWithTheLaw() {
        final DriveReadout r = DriveMath.read(GroundFixtures.car());
        assertEquals(r.model.cruise(), r.speed, 1e-9);
        assertEquals(0.0, r.worstSlip, 1e-9);
        assertEquals(1.0, r.leastGrip, 1e-9);
        final DriveReadout real = DriveMath.read(GroundFixtures.realistic());
        assertEquals(0.0, real.worstSlip, 1e-9);
    }
}
