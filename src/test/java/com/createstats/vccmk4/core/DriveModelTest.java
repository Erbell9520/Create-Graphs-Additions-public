package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveModelTest {
    private static final DriveModel.Options O = DriveModel.Options.DEFAULT.withThrottleTicks(0);

    private static DriveModel.Inputs in(double throttle, double steer, boolean brake, boolean boost, double speed) {
        return new DriveModel.Inputs(throttle, steer, brake, boost, speed);
    }

    @Test
    void steerRampsToFullLockAndCentresFaster() {
        DriveModel m = new DriveModel();
        double last = 0.0;
        int ticks = 0;
        while (m.step(O, in(0, 1, false, false, 0)).steer() < 1.0 - 1.0e-9) {
            double now = m.steerPos();
            assertTrue(now > last, "monotonic");
            last = now;
            if (++ticks > 100) throw new AssertionError("never reached full lock");
        }
        assertEquals(O.steerTicks() - 1, ticks, "full lock in steerTicks ticks");
        int back = 0;
        while (m.step(O, in(0, 0, false, false, 0)).steer() > 1.0e-9) {
            if (++back > 100) throw new AssertionError("never centred");
        }
        assertTrue(back + 1 < ticks, "centring is quicker than winding on: " + (back + 1) + " vs " + ticks);
        assertEquals(0.0, m.steerPos(), 1.0e-9, "and lands exactly on centre");
    }

    @Test
    void reverseKeyBrakesFirstThenReverses() {
        DriveModel m = new DriveModel();
        DriveModel.Output rolling = m.step(O, in(-1, 0, false, false, 8.0));
        assertTrue(rolling.braking(), "S while rolling forward is the brake");
        assertEquals(0.0, rolling.throttle(), 1.0e-9);
        DriveModel.Output stopped = m.step(O, in(-1, 0, false, false, 0.4));
        assertFalse(stopped.braking());
        assertEquals(-0.5, stopped.throttle(), 1.0e-9, "reverse at the reverse percentage");
        DriveModel.Output backingUp = m.step(O, in(1, 0, false, false, -6.0));
        assertTrue(backingUp.braking(), "W while rolling backwards is the brake too");
    }

    @Test
    void immediateReverseWhenTheOptionIsOff() {
        DriveModel m = new DriveModel();
        DriveModel.Output out = m.step(O.withBrakeThenReverse(false), in(-1, 0, false, false, 8.0));
        assertFalse(out.braking());
        assertEquals(-0.5, out.throttle(), 1.0e-9);
    }

    @Test
    void brakeKeyAlwaysBrakesAndKillsBoost() {
        DriveModel m = new DriveModel();
        DriveModel.Output out = m.step(O, in(1, 0, true, true, 5.0));
        assertTrue(out.braking());
        assertEquals(0.0, out.throttle(), 1.0e-9);
        assertFalse(out.boosting(), "jets stay off under braking");
    }

    @Test
    void boostPushesTheWayTheThrottleSays() {
        DriveModel m = new DriveModel();
        DriveModel.Output forward = m.step(O, in(1, 0, false, true, 5.0));
        assertTrue(forward.boosting());
        assertEquals(1.0, forward.pushDir(), 1.0e-9);
        assertTrue(m.step(O, in(0, 0, false, true, 5.0)).boosting(), "boost alone fires the jets");
        DriveModel.Output reversing = m.step(O, in(-1, 0, false, true, 0.0));
        assertTrue(reversing.boosting(), "stopped, with the reverse key: jets push backwards");
        assertEquals(-1.0, reversing.pushDir(), 1.0e-9);
        assertFalse(m.step(O, in(-1, 0, false, true, 8.0)).boosting(), "rolling forward, the reverse key brakes and the jets rest");
    }

    @Test
    void speedSensitiveLockShrinksWithSpeedAndHasAFloor() {
        DriveModel m = new DriveModel();
        DriveModel.Options o = O.withSpeedSteer(true);
        assertEquals(30.0, m.step(o, in(0, 0, false, false, 3.0)).lockDeg(), 1.0e-9, "slow: full lock");
        double mid = m.step(o, in(0, 0, false, false, 18.0)).lockDeg();
        assertTrue(mid < 30.0 && mid > 30.0 * DriveModel.SPEED_STEER_FLOOR);
        assertEquals(30.0 * DriveModel.SPEED_STEER_FLOOR, m.step(o, in(0, 0, false, false, 80.0)).lockDeg(), 1.0e-9);
        assertEquals(30.0, m.step(O, in(0, 0, false, false, 80.0)).lockDeg(), 1.0e-9, "off: never shrinks");
    }

    @Test
    void optionsAreClampedIntoRange() {
        DriveModel.Options o = new DriveModel.Options(true, false, 999, -5, 100000, 0, 500, 500, true, true).sane();
        assertEquals(DriveModel.Options.MAX_STEER_TICKS, o.steerTicks());
        assertEquals(DriveModel.Options.MIN_LOCK, o.lockDeg());
        assertEquals(DriveModel.Options.MAX_RPM, o.maxRpm());
        assertEquals(DriveModel.Options.MIN_REVERSE, o.reversePercent());
        assertEquals(DriveModel.Options.MAX_THROTTLE_TICKS, o.throttleTicks());
        assertEquals(DriveModel.Options.MAX_THROTTLE_TICKS, o.releaseTicks());
        assertEquals(60, DriveModel.Options.MAX_THROTTLE_TICKS, "the owner's three seconds, for the ramp and the release");
        assertEquals(0, O.withThrottleTicks(-3).throttleTicks(), "instant is the floor");
        assertEquals(DriveModel.Options.AS_RAMP, O.withReleaseTicks(-9).releaseTicks(), "below instant is as ramp, not nonsense");
        assertEquals(20, O.withLockDeg(20).lockDeg());
        assertEquals(64, O.withMaxRpm(64).maxRpm());
    }

    @Test
    void throttleBuildsUpOverTheRamp() {
        DriveModel m = new DriveModel();
        DriveModel.Options o = O.withThrottleTicks(4);
        double[] seen = new double[5];
        for (int i = 0; i < 5; i++) seen[i] = m.step(o, in(1, 0, false, false, 0.0)).throttle();
        assertEquals(0.25, seen[0], 1.0e-9, "a quarter of the way on the first tick");
        assertEquals(0.5, seen[1], 1.0e-9);
        assertEquals(1.0, seen[3], 1.0e-9, "full throttle after the ramp's ticks");
        assertEquals(1.0, seen[4], 1.0e-9, "and it stays there");
    }

    @Test
    void lettingGoEasesOffOverTheRampByDefault() {
        DriveModel m = new DriveModel();
        DriveModel.Options o = DriveModel.Options.DEFAULT.withThrottleTicks(4);
        assertEquals(DriveModel.Options.AS_RAMP, o.releaseTicks(), "the release follows the ramp unless set");
        for (int i = 0; i < 10; i++) m.step(o, in(1, 0, false, false, 5.0));
        double[] seen = new double[5];
        for (int i = 0; i < 5; i++) seen[i] = m.step(o, in(0, 0, false, false, 5.0)).throttle();
        assertEquals(0.75, seen[0], 1.0e-9, "a quarter off on the first tick, as the ramp put a quarter on");
        assertEquals(0.5, seen[1], 1.0e-9);
        assertEquals(0.25, seen[2], 1.0e-9);
        assertEquals(0.0, seen[3], 1.0e-9, "at rest after the ramp's ticks");
        assertEquals(0.0, seen[4], 1.0e-9, "and it stays there");
    }

    @Test
    void theReleaseCanBeSetApartFromTheRamp() {
        DriveModel.Options o = DriveModel.Options.DEFAULT.withThrottleTicks(20).withReleaseTicks(8);
        assertEquals(8, o.effectiveReleaseTicks());
        assertEquals(8, o.withThrottleTicks(40).effectiveReleaseTicks(), "a release of its own stays put when the ramp moves");
        assertEquals(40, o.withReleaseTicks(DriveModel.Options.AS_RAMP).withThrottleTicks(40).effectiveReleaseTicks(),
                "as ramp moves with it");

        DriveModel m = new DriveModel();
        DriveModel.Options instantUp = O.withReleaseTicks(8);
        assertEquals(1.0, m.step(instantUp, in(1, 0, false, false, 5.0)).throttle(), 1.0e-9, "an instant ramp");
        assertEquals(0.875, m.step(instantUp, in(0, 0, false, false, 5.0)).throttle(), 1.0e-9, "with a slow release: an eighth a tick");

        DriveModel n = new DriveModel();
        DriveModel.Options instantRelease = O.withThrottleTicks(10).withReleaseTicks(0);
        for (int i = 0; i < 20; i++) n.step(instantRelease, in(1, 0, false, false, 5.0));
        assertEquals(0.0, n.step(instantRelease, in(0, 0, false, false, 5.0)).throttle(), 1.0e-9,
                "instant is the old letting go");
    }

    @Test
    void reverseEasesOffAtTheSameRateOfTravel() {
        DriveModel m = new DriveModel();
        DriveModel.Options o = O.withThrottleTicks(10);
        for (int i = 0; i < 20; i++) m.step(o, in(-1, 0, false, false, -2.0));
        assertEquals(-0.5, m.throttlePos(), 1.0e-9, "reverse at the reverse percentage");
        assertEquals(-0.4, m.step(o, in(0, 0, false, false, -2.0)).throttle(), 1.0e-9, "a tenth of full travel a tick");
        for (int i = 0; i < 4; i++) m.step(o, in(0, 0, false, false, -2.0));
        assertEquals(0.0, m.throttlePos(), 1.0e-9, "half of full travel back in half the ticks");
    }

    @Test
    void noRampMovesTheDriveFasterThanItsTicksAllow() {
        for (int ticks = 2; ticks <= DriveModel.Options.MAX_THROTTLE_TICKS; ticks += 2) {
            DriveModel.Options o = O.withThrottleTicks(ticks).withReleaseTicks(ticks);
            DriveModel m = new DriveModel();
            double last = 0.0;
            int n = 0;
            while (last < 1.0) {
                double now = m.step(o, in(1, 0, false, false, 5.0)).throttle();
                assertTrue(now - last <= 1.0 / ticks + 1.0e-9, ticks + " ticks: rose " + (now - last));
                last = now;
                if (++n > ticks) throw new AssertionError(ticks + " ticks: not at full after " + n);
            }
            assertEquals(ticks, n, "rest to full in exactly its ticks");
            n = 0;
            while (last > 0.0) {
                double now = m.step(o, in(0, 0, false, false, 5.0)).throttle();
                assertTrue(last - now <= 1.0 / ticks + 1.0e-9, ticks + " ticks: dropped " + (last - now));
                last = now;
                if (++n > ticks) throw new AssertionError(ticks + " ticks: still driving after " + n);
            }
            assertEquals(ticks, n, "full to rest in exactly its ticks");
        }
    }

    @Test
    void aBrakeCutsTheDriveEvenHalfwayThroughARelease() {
        DriveModel.Options o = O.withThrottleTicks(20);
        DriveModel m = new DriveModel();
        for (int i = 0; i < 30; i++) m.step(o, in(1, 0, false, false, 6.0));
        for (int i = 0; i < 3; i++) m.step(o, in(0, 0, false, false, 6.0));
        assertEquals(0.85, m.throttlePos(), 1.0e-9, "three twentieths off");
        DriveModel.Output braked = m.step(o, in(0, 0, true, false, 6.0));
        assertTrue(braked.braking());
        assertEquals(0.0, braked.throttle(), 1.0e-9, "the brake key cuts it this tick");

        DriveModel r = new DriveModel();
        for (int i = 0; i < 30; i++) r.step(o, in(1, 0, false, false, 6.0));
        r.step(o, in(0, 0, false, false, 6.0));
        DriveModel.Output reverseKey = r.step(o, in(-1, 0, false, false, 6.0));
        assertTrue(reverseKey.braking(), "S while rolling forward is the brake");
        assertEquals(0.0, reverseKey.throttle(), 1.0e-9, "and it cuts the drive the same tick");

        DriveModel h = new DriveModel();
        for (int i = 0; i < 30; i++) h.step(o, in(1, 0, false, false, 0.2));
        DriveModel.Output out = null;
        for (int i = 0; i < DriveModel.HOLD_DELAY; i++) out = h.step(o, in(0, 0, false, false, 0.1));
        assertTrue(out.holding(), "a vehicle at rest is held while the release is still running");
        assertEquals(0.0, out.throttle(), 1.0e-9, "and the hold cuts what is left of the drive");
    }

    @Test
    void aSilentClientClosesTheThrottleAndKeepsTheStickAndTheHold() {
        DriveModel.Options o = O.withThrottleTicks(20);
        DriveModel m = new DriveModel();
        for (int i = 0; i < 30; i++) m.step(o, in(1, 1, false, false, 0.2));
        double stick = m.steerPos();
        m.closeThrottle();
        assertEquals(0.0, m.throttlePos(), 1.0e-9, "closed, not eased off");
        assertEquals(stick, m.steerPos(), 1.0e-9, "the stick is left to the steering");
        DriveModel.Output out = null;
        for (int i = 0; i < DriveModel.HOLD_DELAY; i++) {
            m.closeThrottle();
            out = m.step(o, in(0, 0, false, false, 0.1));
            assertEquals(0.0, out.throttle(), 1.0e-9, "and it stays closed while the silence lasts: " + i);
        }
        assertTrue(out.holding(), "a vehicle stopped this way is still held");
    }

    @Test
    void theReleaseStepperWalksThroughAsRamp() {
        DriveModel.Options d = DriveModel.Options.DEFAULT;
        int as = DriveModel.Options.AS_RAMP;
        assertEquals(6, d.throttleTicks(), "these read against the shipped 6-tick ramp");
        assertEquals(8, d.releaseStep(2), "plus from as ramp is the ramp's ticks and a step");
        assertEquals(4, d.releaseStep(-2), "minus is the ramp's ticks less a step");
        assertEquals(as, d.withReleaseTicks(4).releaseStep(2), "back onto the ramp's ticks is as ramp again");
        assertEquals(as, d.withReleaseTicks(8).releaseStep(-2));
        assertEquals(12, d.withReleaseTicks(10).releaseStep(2), "away from the ramp it is plain ticks");
        assertEquals(0, d.withReleaseTicks(2).releaseStep(-2), "down to instant");
        assertEquals(0, d.withReleaseTicks(0).releaseStep(-2), "and no further");
        assertEquals(60, d.withReleaseTicks(60).releaseStep(2), "nor past the top");
        assertEquals(as, d.withThrottleTicks(0).releaseStep(-2), "as ramp on an instant ramp stays as ramp");
        assertEquals(as, d.withThrottleTicks(60).releaseStep(2), "and on the longest");
        assertEquals(as, d.withThrottleTicks(11).withReleaseTicks(10).releaseStep(2),
                "stepping past an odd ramp lands on it rather than skipping it");
        assertEquals(12, d.withThrottleTicks(10).withReleaseTicks(10).releaseStep(2),
                "a release of its own that the ramp was moved onto steps on, not back into as ramp");
    }

    @Test
    void brakingAndChangingDirectionAreNeverRamped() {
        DriveModel m = new DriveModel();
        DriveModel.Options o = O.withThrottleTicks(10).withBrakeThenReverse(false);
        for (int i = 0; i < 20; i++) m.step(o, in(1, 0, false, false, 3.0));
        assertEquals(0.0, m.step(o, in(1, 0, true, false, 3.0)).throttle(), 1.0e-9, "the brake cuts the drive this tick");
        for (int i = 0; i < 20; i++) m.step(o, in(1, 0, false, false, 3.0));
        double back = m.step(o, in(-1, 0, false, false, 3.0)).throttle();
        assertTrue(back < 0.0 && back > -0.2, "reversing starts from rest, not from full forward: " + back);
    }

    @Test
    void anInstantRampIsTheOldBehaviour() {
        DriveModel m = new DriveModel();
        assertEquals(1.0, m.step(O.withThrottleTicks(0), in(1, 0, false, false, 0.0)).throttle(), 1.0e-9);
    }

    @Test
    void aVehicleAtRestWithNothingAskedIsHeld() {
        DriveModel m = new DriveModel();
        DriveModel.Output out = null;
        for (int i = 0; i < DriveModel.HOLD_DELAY; i++) {
            out = m.step(O, in(0, 0, false, false, 0.1));
            if (i < DriveModel.HOLD_DELAY - 1) assertFalse(out.holding(), "not before the delay: " + i);
        }
        assertTrue(out.holding());
        assertTrue(out.braking(), "holding is braking");
        assertEquals('H', out.gear());
        DriveModel.Output go = m.step(O, in(1, 0, false, false, 0.1));
        assertFalse(go.holding(), "a drive key lets go the same tick");
        assertFalse(go.braking());
        assertTrue(go.throttle() > 0.0);
    }

    @Test
    void theHoldWaitsForRestAndForEveryKey() {
        DriveModel m = new DriveModel();
        for (int i = 0; i < 20; i++) assertFalse(m.step(O, in(0, 0, false, false, 4.0)).holding(), "rolling is not at rest");
        DriveModel.Inputs skid = new DriveModel.Inputs(0, 1, false, false, 0.0, true, true);
        for (int i = 0; i < 20; i++) assertFalse(m.step(O, skid).holding(), "a turn on the spot is asked of it");
        DriveModel.Inputs jets = new DriveModel.Inputs(0, 0, false, false, 0.0, false, false);
        for (int i = 0; i < 20; i++) assertFalse(m.step(O, jets).holding(), "the jets drive it, the wheels do not hold it");
        for (int i = 0; i < 20; i++) assertFalse(m.step(O.withHoldWhenStopped(false), in(0, 0, false, false, 0.0)).holding());
        for (int i = 0; i < 20; i++) assertFalse(m.step(O, in(0, 0, false, true, 0.0)).holding(), "Boost from rest");
    }

    @Test
    void gearLettersSayWhatTheWheelsAreDoing() {
        assertEquals('N', DriveModel.Output.idle(30).gear());
        DriveModel m = new DriveModel();
        DriveModel.Options o = O.withThrottleTicks(0);
        assertEquals('D', m.step(o, in(1, 0, false, false, 2.0)).gear());
        assertEquals('B', m.step(o, in(-1, 0, false, false, 6.0)).gear(), "brake-then-reverse brakes first");
        assertEquals('R', m.step(o, in(-1, 0, false, false, 0.2)).gear());
    }

    @Test
    void resetCentresTheStick() {
        DriveModel m = new DriveModel();
        for (int i = 0; i < 20; i++) m.step(O, in(0, 1, false, false, 0));
        m.reset();
        assertEquals(0.0, m.steerPos(), 1.0e-9);
    }
}
