package com.createstats.vccmk4.core;

import com.createstats.vccmk4.core.BtaG1Motion.Frame;
import com.createstats.vccmk4.core.BtaG1Motion.Lamp;
import com.createstats.vccmk4.core.BtaG1Motion.Pose;
import com.createstats.vccmk4.core.BtaG1Motion.Spring;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtaG1MotionTest {
    private static final double EPS = 1.0e-9;

    private static BtaG1State driving(double throttle, double steer, double kmh) {
        return BtaG1State.of(true, false, false, false, false, false, false, 0, throttle, steer, 0.0, kmh / BtaG1State.KMH_PER_BPS)
                .with(BtaG1State.SEATED, true).with(BtaG1State.GREETING, true);
    }

    @Test
    void theNeedleStartsLowerLeftPointsUpAtSixtyAndEndsLowerRight() {
        assertEquals(120.0, BtaG1Motion.needleDegrees(BtaG1Motion.needleFraction(0.0)), EPS);
        assertEquals(0.0, BtaG1Motion.needleDegrees(BtaG1Motion.needleFraction(60.0)), EPS);
        assertEquals(-120.0, BtaG1Motion.needleDegrees(BtaG1Motion.needleFraction(120.0)), EPS);
    }

    @Test
    void theNeedlePegsAtTheEndOfItsScaleAndReadsSpeedNotDirection() {
        assertEquals(1.0, BtaG1Motion.needleFraction(500.0), EPS, "past full scale it rests on the pin");
        assertEquals(BtaG1Motion.needleFraction(36.0), BtaG1Motion.needleFraction(-36.0), EPS,
                "a speedometer does not know which way the vehicle is going, the reverse lamp does");
        assertEquals(-120.0, BtaG1Motion.needleDegrees(5.0), EPS, "and an out-of-range fraction is clamped");
        assertEquals(120.0, BtaG1Motion.needleDegrees(-5.0), EPS);
    }

    @Test
    void theWheelTurnsClockwiseForRightAndCounterClockwiseForLeft() {
        assertEquals(-BtaG1Motion.WHEEL_LOCK_DEG, BtaG1Motion.wheelDegrees(1.0), EPS);
        assertEquals(BtaG1Motion.WHEEL_LOCK_DEG, BtaG1Motion.wheelDegrees(-1.0), EPS);
        assertEquals(0.0, BtaG1Motion.wheelDegrees(0.0), EPS);
        assertEquals(-BtaG1Motion.WHEEL_LOCK_DEG, BtaG1Motion.wheelDegrees(9.0), EPS, "the stick cannot go past full");
    }

    @Test
    void theThrottleLeversAheadForDriveAndBackForReverse() {
        assertEquals(-BtaG1Motion.THROTTLE_SWING_DEG, BtaG1Motion.throttleDegrees(1.0), EPS,
                "a negative turn about the deck's x axis leans the lever toward the nose");
        assertEquals(BtaG1Motion.THROTTLE_SWING_DEG, BtaG1Motion.throttleDegrees(-1.0), EPS);
        assertEquals(0.0, BtaG1Motion.throttleDegrees(0.0), EPS);

        assertEquals(BtaG1Motion.THROTTLE_SWING_DEG / 2.0, BtaG1Motion.throttleDegrees(-0.5), EPS);
    }

    @Test
    void theBrakeLeverIsReleasedThenAServiceBrakeThenTheParkBrake() {
        assertEquals(BtaG1Motion.BRAKE_RELEASED_DEG, BtaG1Motion.brakeDegrees(0.0), EPS);
        assertEquals(BtaG1Motion.BRAKE_SERVICE_DEG, BtaG1Motion.brakeDegrees(BtaG1Motion.BRAKE_SERVICE_LEVEL), EPS);
        assertEquals(BtaG1Motion.BRAKE_PARK_DEG, BtaG1Motion.brakeDegrees(1.0), EPS);
        double last = -1000.0;
        for (int i = 0; i <= 100; i++) {
            double d = BtaG1Motion.brakeDegrees(i / 100.0);
            assertTrue(d >= last, "the lever only ever moves toward the driver as the brake goes on");
            last = d;
        }
    }

    @Test
    void theBrakeLevelIsWhatTheBrakeReallyIs() {
        BtaG1State key = BtaG1State.of(true, false, true, false, false, false, false, 0, 0.0, 0.0, 1.0, 0.0);
        assertEquals(BtaG1Motion.BRAKE_SERVICE_LEVEL, BtaG1Motion.brakeLevel(key), 0.01, "the key is a service brake");
        BtaG1State parked = BtaG1State.of(false, false, false, false, true, false, false, 0, 0.0, 0.0, 0.0, 0.0);
        assertEquals(1.0, BtaG1Motion.brakeLevel(parked), EPS, "the park brake hauls it all the way");
        BtaG1State route = BtaG1State.of(true, true, true, false, false, false, false, 0, 0.3, 0.0, 0.5, 5.0);
        assertEquals(BtaG1Motion.BRAKE_SERVICE_LEVEL * 0.5, BtaG1Motion.brakeLevel(route), 0.01,
                "a route that brakes at half strength pulls the lever half as far");
        assertEquals(0.0, BtaG1Motion.brakeLevel(driving(0.5, 0.0, 30.0)), EPS);
    }

    @Test
    void aLampsHaloIsTheBrightnessSquaredAndNeverMoreThanABloom() {
        assertEquals(0, BtaG1Motion.haloAlpha(0.0));
        assertEquals((int) Math.round(255 * BtaG1Motion.HALO_ALPHA), BtaG1Motion.haloAlpha(1.0));
        assertTrue(BtaG1Motion.haloAlpha(1.0) < 128, "a bloom stays well under half opaque, or it stops being one");
        assertEquals(BtaG1Motion.haloAlpha(1.0) / 4.0, BtaG1Motion.haloAlpha(0.5), 1.0,
                "half up is a quarter of the halo, so a swelling lamp blooms late and a dying one fades early");
        int last = -1;
        for (int i = 0; i <= 100; i++) {
            int a = BtaG1Motion.haloAlpha(i / 100.0);
            assertTrue(a >= last, "monotone");
            last = a;
        }
        assertEquals(BtaG1Motion.haloAlpha(1.0), BtaG1Motion.haloAlpha(7.0), "clamped");
        assertEquals(0, BtaG1Motion.haloAlpha(-3.0));
    }

    @Test
    void aCriticallyDampedSpringNeverOvershootsAndArrives() {
        Spring s = new Spring();
        s.snap(1.0);
        double last = 1.0;
        for (int i = 0; i < 80; i++) {
            s.step(0.0, 14.0, 1.0, BtaG1Motion.DT);
            assertTrue(s.value() >= -1.0e-12, "a hand-moved lever must not swing past where it was sent");
            assertTrue(s.value() <= last + 1.0e-12, "and it only ever closes the gap");
            last = s.value();
        }
        assertEquals(0.0, s.value(), 1.0e-6);
    }

    @Test
    void aCriticallyDampedStepMatchesTheClosedForm() {
        Spring s = new Spring();
        s.snap(1.0);
        double omega = 16.0;
        s.step(0.0, omega, 1.0, BtaG1Motion.DT);
        double expected = (1.0 + omega * BtaG1Motion.DT) * Math.exp(-omega * BtaG1Motion.DT);
        assertEquals(expected, s.value(), 1.0e-12);
    }

    @Test
    void theNeedleOvershootsByAboutTenPercentAndSettles() {
        Spring s = new Spring();
        double peak = 0.0;
        for (int i = 0; i < 200; i++) {
            s.step(1.0, BtaG1Motion.NEEDLE_OMEGA, BtaG1Motion.NEEDLE_ZETA, BtaG1Motion.DT);
            peak = Math.max(peak, s.value());
        }
        double theory = Math.exp(-Math.PI * BtaG1Motion.NEEDLE_ZETA / Math.sqrt(1.0 - BtaG1Motion.NEEDLE_ZETA * BtaG1Motion.NEEDLE_ZETA));
        assertTrue(peak > 1.0 + 0.85 * theory && peak <= 1.0 + theory + 1.0e-9,
                "the overshoot is what a real movement does (sampled, so a hair under the continuous peak): " + (peak - 1.0));
        assertEquals(1.0, s.value(), 1.0e-6, "and it rings down onto the reading");
    }

    @Test
    void theSolutionDoesNotDependOnTheStepSize() {
        Spring a = new Spring();
        Spring b = new Spring();
        a.snap(0.3);
        b.snap(0.3);
        a.step(1.0, 9.0, 0.6, 0.05);
        b.step(1.0, 9.0, 0.6, 0.025);
        b.step(1.0, 9.0, 0.6, 0.025);
        assertEquals(a.value(), b.value(), 1.0e-9, "an exact solver: two half steps are one step");
        assertEquals(a.velocity(), b.velocity(), 1.0e-9);
    }

    @Test
    void aPinKeepsTheNeedleOffTheWrongSideOfZero() {
        Spring s = new Spring();
        s.snap(0.02);
        s.step(0.0, 9.0, 0.3, 0.05);
        for (int i = 0; i < 100; i++) {
            s.step(0.0, 9.0, 0.3, 0.05);
            s.pin(0.0, 1.0);
            assertTrue(s.value() >= 0.0 && s.value() <= 1.0, "a needle rests against its pin, it does not sit off the scale");
        }
    }

    private static Set<Lamp> lit(BtaG1State s, long tick) {
        Set<Lamp> out = EnumSet.noneOf(Lamp.class);
        for (Lamp l : Lamp.values()) if (BtaG1Motion.lit(l, s, tick)) out.add(l);
        return out;
    }

    @Test
    void anEmptyStandingConsoleIsDarkExceptForItsParkLamp() {
        assertEquals(EnumSet.noneOf(Lamp.class), lit(BtaG1State.IDLE, 0));
        BtaG1State parked = BtaG1State.of(false, false, false, false, true, false, false, 0, 0, 0, 0, 0);
        assertEquals(EnumSet.of(Lamp.PARK), lit(parked, 0), "the one fact worth showing about a vehicle nobody drives");
    }

    @Test
    void aDriverGetsReadyAndWhatTheVehicleIsDoing() {
        assertEquals(EnumSet.of(Lamp.READY), lit(driving(0.0, 0.0, 0.0), 0));
        BtaG1State all = BtaG1State.of(true, true, true, true, false, true, true, 0, -0.4, 0.0, 1.0, -2.0);
        assertEquals(EnumSet.of(Lamp.READY, Lamp.AUTO, Lamp.BRAKE, Lamp.HOLD, Lamp.BOOST, Lamp.REVERSE), lit(all, 0));
    }

    @Test
    void theIndicatorsFlashEightTicksOnAndEightOffPastAQuarterOfTheStick() {
        BtaG1State left = driving(0.5, -0.3, 30.0);
        for (long t = 0; t < 8; t++) assertTrue(BtaG1Motion.lit(Lamp.LEFT, left, t), "on at tick " + t);
        for (long t = 8; t < 16; t++) assertFalse(BtaG1Motion.lit(Lamp.LEFT, left, t), "off at tick " + t);
        assertTrue(BtaG1Motion.lit(Lamp.LEFT, left, 16));
        assertFalse(BtaG1Motion.lit(Lamp.RIGHT, left, 0), "and only the side it is turning to");
        assertFalse(BtaG1Motion.lit(Lamp.LEFT, driving(0.5, -0.2, 30.0), 0), "a small correction does not flash it");
        assertTrue(BtaG1Motion.lit(Lamp.RIGHT, driving(0.5, 0.3, 30.0), 0));
    }

    @Test
    void theIndicatorsFollowTheVehiclesOwnLightsRules() {
        assertEquals(LinkLogic.SIGNAL_STEER, 0.25, EPS, "the threshold these lamps use is the lights' own");
        assertEquals(8, LinkLogic.BLINK_TICKS);
    }

    @Test
    void aRoutesIndicatorFlashesWithNoSteerAtAll() {
        BtaG1State route = BtaG1State.of(true, true, false, false, false, false, false, 1, 0.5, 0.0, 0.0, 5.0);
        assertTrue(BtaG1Motion.lit(Lamp.RIGHT, route, 0));
        assertFalse(BtaG1Motion.lit(Lamp.LEFT, route, 0));
        BtaG1State other = BtaG1State.of(true, true, false, false, false, false, false, -1, 0.5, 0.0, 0.0, 5.0);
        assertTrue(BtaG1Motion.lit(Lamp.LEFT, other, 0));
    }

    @Test
    void theSweepRisesHoldsAndFallsBack() {
        assertEquals(0.0, BtaG1Motion.sweep(0), EPS);
        assertEquals(1.0, BtaG1Motion.sweep(12), EPS);
        assertEquals(1.0, BtaG1Motion.sweep(14), EPS);
        double last = -1.0;
        for (int t = 0; t <= 12; t++) {
            assertTrue(BtaG1Motion.sweep(t) >= last, "up");
            last = BtaG1Motion.sweep(t);
        }
        for (int t = 15; t < BtaG1Motion.SELF_TEST_TICKS; t++) {
            assertTrue(BtaG1Motion.sweep(t) <= last + EPS, "and back down");
            last = BtaG1Motion.sweep(t);
        }
        assertTrue(BtaG1Motion.sweep(BtaG1Motion.SELF_TEST_TICKS - 1) < 0.02, "it ends where it began");
        assertEquals(0.0, BtaG1Motion.sweep(BtaG1Motion.SELF_TEST_TICKS), EPS);
    }

    @Test
    void theLampTestRunsAsAWaveAlongTheLamps() {
        assertTrue(BtaG1Motion.testLit(Lamp.BRAKE, 0));
        assertFalse(BtaG1Motion.testLit(Lamp.PARK, 0));
        assertTrue(BtaG1Motion.testLit(Lamp.PARK, 1));
        Lamp last = Lamp.values()[Lamp.COUNT - 1];
        assertTrue(BtaG1Motion.testLit(last, Lamp.COUNT - 1));
        assertFalse(BtaG1Motion.testLit(last, Lamp.COUNT - 1 + 12), "each lamp is lit for twelve ticks");
        assertTrue(Lamp.COUNT - 1 + 12 < BtaG1Motion.SELF_TEST_TICKS, "and the wave is over before the sweep is");
    }

    private static void run(Pose pose, BtaG1State s, int ticks, long from) {
        for (int i = 0; i < ticks; i++) pose.tick(s, from + i);
    }

    @Test
    void aPoseThatHasNeverTickedShowsARestingConsole() {
        for (float partial : new float[]{0.0f, 0.5f, 1.0f}) {
            Frame f = new Pose().frame(partial);
            assertEquals(0.0, f.wheelDeg, EPS, "the wheel is straight");
            assertEquals(0.0, f.throttleDeg, EPS, "the throttle is at neutral");
            assertEquals(BtaG1Motion.BRAKE_RELEASED_DEG, f.brakeDeg, EPS,
                    "the brake is released, which is not zero degrees: it must not stand upright for a frame");
            assertEquals(BtaG1Motion.SWEEP_START_DEG, f.needleDeg, EPS, "the needle is at 0 km/h");
            assertEquals(0.0, f.dial, EPS);
            for (Lamp l : Lamp.values()) assertEquals(0.0, f.lamp(l), EPS, "lamp " + l);
        }
    }

    @Test
    void aConsoleSomeoneIsAlreadyDrivingDoesNotBootWhenYouWalkUpToIt() {
        Pose p = new Pose();
        p.tick(driving(0.6, 0.2, 40.0), 0);
        assertFalse(p.testing());
        Frame f = p.frame(1.0f);
        assertEquals(BtaG1Motion.needleDegrees(BtaG1Motion.needleFraction(40.0)), f.needleDeg, 1.0e-6, "it starts where the vehicle is");
        assertEquals(1.0, f.dial, EPS);
        assertEquals(1.0, f.lamp(Lamp.READY), EPS);
    }

    @Test
    void sittingDownInAStandingVehicleRunsTheSweepOnceAndOnlyOnce() {
        Pose p = new Pose();
        p.tick(BtaG1State.IDLE, 0);
        run(p, BtaG1State.IDLE, 10, 1);
        assertFalse(p.testing());
        BtaG1State seated = driving(0.0, 0.0, 0.0);
        p.tick(seated, 11);
        assertTrue(p.testing());
        double lowest = 1000.0;
        boolean everyLampLit = false;
        int ticks = 1;
        while (p.testing()) {
            p.tick(seated, 11 + ticks++);
            lowest = Math.min(lowest, p.frame(1.0f).needleDeg);
            boolean all = true;
            for (Lamp l : Lamp.values()) all &= p.frame(1.0f).lamp(l) > 0.4;
            everyLampLit |= all;
            assertTrue(ticks < 100, "it must end");
        }
        assertEquals(BtaG1Motion.SELF_TEST_TICKS, ticks, "thirty two ticks");
        assertTrue(lowest < -100.0, "the needle went to the far end of its scale: " + lowest);
        assertTrue(everyLampLit, "and at one moment every lamp was lit");
        run(p, seated, 60, 200);
        assertFalse(p.testing(), "it does not run again while they stay seated");
        assertEquals(BtaG1Motion.needleDegrees(0.0), p.frame(1.0f).needleDeg, 0.5, "and the needle is back at rest");
        assertEquals(0.0, p.frame(1.0f).lamp(Lamp.HOLD), EPS);
        assertEquals(1.0, p.frame(1.0f).lamp(Lamp.READY), EPS);
    }

    @Test
    void aDriverWhoTurnedTheGreetingOffGetsNoSweepAndAChangeMidDriveDoesNotStartOne() {
        Pose p = new Pose();
        p.tick(BtaG1State.IDLE, 0);
        BtaG1State quiet = driving(0.0, 0.0, 0.0).with(BtaG1State.GREETING, false);
        p.tick(quiet, 1);
        assertFalse(p.testing(), "the owner turned the greeting off");
        Pose q = new Pose();
        q.tick(BtaG1State.IDLE, 0);
        q.tick(driving(0.0, 0.0, 0.0).with(BtaG1State.GREETING, false), 1);
        q.tick(driving(0.0, 0.0, 0.0), 2);
        assertFalse(q.testing(), "and turning it on while already seated is not taking the helm");
    }

    @Test
    void aRouteTakingTheVehicleDoesNotSweepOrEveryDepartureFromAStopWould() {
        Pose p = new Pose();
        p.tick(BtaG1State.IDLE, 0);
        BtaG1State route = BtaG1State.of(true, true, false, false, false, false, false, 0, 0.0, 0.0, 0.0, 0.0)
                .with(BtaG1State.GREETING, true);
        p.tick(route, 1);
        assertFalse(p.testing(), "no seated driver: the route lights the dial and AUTO, and that is all");
        assertTrue(BtaG1Motion.lit(Lamp.AUTO, route, 0));
    }

    @Test
    void aMovingVehicleGainingADriverDoesNotSweepBecauseANeedleThatSwingsWhileYouRollIsALie() {
        Pose p = new Pose();
        p.tick(BtaG1State.IDLE, 0);
        p.tick(driving(0.5, 0.0, 30.0), 1);
        assertFalse(p.testing());
    }

    @Test
    void theNeedleClimbsToASpeedInsteadOfJumpingToIt() {
        Pose p = new Pose();
        p.tick(driving(0.0, 0.0, 0.0), 0);
        BtaG1State fast = driving(1.0, 0.0, 60.0);
        p.tick(fast, 1);
        double first = p.frame(1.0f).needleDeg;
        assertTrue(first < 120.0 && first > 0.0, "one tick in it has started to move and has not arrived: " + first);
        run(p, fast, 100, 2);
        assertEquals(0.0, p.frame(1.0f).needleDeg, 0.3, "and it reads sixty");
    }

    @Test
    void aFrameBetweenTwoTicksIsBetweenThem() {
        Pose p = new Pose();
        p.tick(driving(0.0, 0.0, 0.0), 0);
        p.tick(driving(0.0, 1.0, 0.0), 1);
        Frame a = p.frame(0.0f);
        Frame b = p.frame(0.5f);
        Frame c = p.frame(1.0f);
        assertTrue(a.wheelDeg > b.wheelDeg && b.wheelDeg > c.wheelDeg, "a right turn takes the wheel clockwise, frame by frame");
        assertEquals((a.wheelDeg + c.wheelDeg) / 2.0, b.wheelDeg, 1.0e-9, "linearly between the two ticks");
        assertEquals(0.0, a.wheelDeg, 1.0e-9, "the older end is where it was");
    }

    @Test
    void theWheelFollowsTheStickWithoutOvershootingIt() {
        Pose p = new Pose();
        p.tick(driving(0.0, 0.0, 0.0), 0);
        BtaG1State right = driving(0.0, 1.0, 0.0);
        double last = 1.0;
        for (int i = 1; i < 80; i++) {
            p.tick(right, i);
            double w = p.frame(1.0f).wheelDeg;
            assertTrue(w <= last + 1.0e-9 && w >= -BtaG1Motion.WHEEL_LOCK_DEG - 1.0e-9, "monotone, never past the lock: " + w);
            last = w;
        }
        assertEquals(-BtaG1Motion.WHEEL_LOCK_DEG, last, 1.0e-3);
    }

    @Test
    void theBrakeLeverIsPulledQuicklyAndReturnsSlowly() {
        Pose p = new Pose();
        p.tick(driving(0.0, 0.0, 0.0), 0);
        BtaG1State braking = BtaG1State.of(true, false, true, false, false, false, false, 0, 0.0, 0.0, 1.0, 0.0);
        int pull = 0;
        double service = BtaG1Motion.BRAKE_SERVICE_DEG;
        double span = service - BtaG1Motion.BRAKE_RELEASED_DEG;
        while (p.frame(1.0f).brakeDeg < BtaG1Motion.BRAKE_RELEASED_DEG + 0.9 * span) {
            p.tick(braking, 1 + pull++);
            assertTrue(pull < 100);
        }
        run(p, braking, 60, 200);
        int release = 0;
        BtaG1State off = driving(0.0, 0.0, 0.0);
        while (p.frame(1.0f).brakeDeg > service - 0.9 * span) {
            p.tick(off, 300 + release++);
            assertTrue(release < 200);
        }
        assertTrue(release > pull, "a return spring is slower than a hand: " + pull + " ticks on, " + release + " ticks off");
    }

    @Test
    void aLampSwellsInTwoTicksAndDiesInFour() {
        Pose p = new Pose();
        p.tick(BtaG1State.IDLE, 0);
        BtaG1State boosting = BtaG1State.of(true, false, false, false, false, true, false, 0, 1.0, 0.0, 0.0, 30.0);
        run(p, driving(0.0, 0.0, 30.0), 40, 1);
        int up = 0;
        while (p.frame(1.0f).lamp(Lamp.BOOST) < 1.0) {
            p.tick(boosting, 100 + up++);
            assertTrue(up < 20);
        }
        assertEquals(2, up, "half a lamp a tick up");
        int down = 0;
        BtaG1State idle = driving(1.0, 0.0, 30.0);
        while (p.frame(1.0f).lamp(Lamp.BOOST) > 0.0) {
            p.tick(idle, 200 + down++);
            assertTrue(down < 20);
        }
        assertEquals(4, down, "a quarter of a lamp a tick down");
    }

    @Test
    void theDialLightsOverHalfASecondAndFadesOverASecond() {
        Pose p = new Pose();
        p.tick(BtaG1State.IDLE, 0);
        BtaG1State seated = driving(0.0, 0.0, 30.0);
        int up = 0;
        while (p.frame(1.0f).dial < 1.0) {
            p.tick(seated, 1 + up++);
            assertTrue(up < 100);
        }
        assertEquals(10, up);
        int down = 0;
        while (p.frame(1.0f).dial > 0.0) {
            p.tick(BtaG1State.IDLE, 100 + down++);
            assertTrue(down < 100);
        }
        assertEquals(20, down);
    }

    @Test
    void twoPosesGivenTheSameHistoryAgreeExactly() {
        Pose a = new Pose();
        Pose b = new Pose();
        BtaG1State[] script = {BtaG1State.IDLE, driving(0.3, 0.1, 5.0), driving(1.0, -0.6, 44.0), driving(-0.5, 0.0, 12.0)};
        for (int i = 0; i < 120; i++) {
            BtaG1State s = script[(i / 30) % script.length];
            a.tick(s, i);
            b.tick(s, i);
        }
        Frame fa = a.frame(0.37f);
        Frame fb = b.frame(0.37f);
        assertEquals(fa.wheelDeg, fb.wheelDeg, 0.0);
        assertEquals(fa.needleDeg, fb.needleDeg, 0.0);
        assertEquals(fa.brakeDeg, fb.brakeDeg, 0.0);
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static double python(String source, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + "\\s*=\\s*(-?[0-9.]+)").matcher(source);
        assertTrue(m.find(), "gen_bta_g1.py no longer defines " + name);
        return Double.parseDouble(m.group(1));
    }
}
