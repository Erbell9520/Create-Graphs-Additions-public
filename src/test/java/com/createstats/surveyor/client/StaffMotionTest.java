package com.createstats.surveyor.client;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffMotionTest {
    private static double[] sights(double yawDeg) {
        double y = Math.toRadians(yawDeg);

        return new double[]{-Math.sin(y), -Math.cos(y)};
    }

    @Test
    void theCompassSightsWhereItIsAimed() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            double dx = r.nextDouble() * 2 - 1, dz = r.nextDouble() * 2 - 1;
            double n = Math.hypot(dx, dz);
            if (n < 1e-3) continue;
            double[] s = sights(StaffMotion.yaw(dx, dz));
            assertEquals(dx / n, s[0], 1e-9, "x of " + dx + "," + dz);
            assertEquals(dz / n, s[1], 1e-9, "z of " + dx + "," + dz);
        }
        assertEquals(0.0, StaffMotion.yaw(0, -1), 1e-9, "north (-z) is no turn");
        assertEquals(-90.0, StaffMotion.yaw(1, 0), 1e-9, "east (+x) is a quarter turn clockwise seen from above");
        assertEquals(90.0, StaffMotion.yaw(-1, 0), 1e-9);
    }

    @Test
    void theCompassFollowsTheShortWayRoundAndSettles() {
        double a = 170.0;
        a = StaffMotion.follow(a, -170.0, 0.02, StaffMotion.AIM_TAU);
        assertTrue(a > 170.0 || a < -170.0, "went round through 180, not back through 0: " + a);
        for (int i = 0; i < 200; i++) a = StaffMotion.follow(a, -170.0, 0.02, StaffMotion.AIM_TAU);
        assertEquals(-170.0, a, 1e-3);
        assertEquals(12.0, StaffMotion.follow(12.0, 80.0, 0.0, StaffMotion.AIM_TAU), 0.0, "no time, no move");
        assertEquals(-180.0, StaffMotion.wrap(180.0), 0.0);
        assertEquals(10.0, StaffMotion.wrap(370.0), 0.0);
        assertEquals(1.0, StaffMotion.flash(0.0), 1e-9);
        assertEquals(0.0, StaffMotion.flash(StaffMotion.FLASH_TICKS), 0.0);
        assertEquals(0.0, StaffMotion.flash(-1.0), 0.0, "never changed, or a clock behind: no flash");
    }

    @Test
    void theNeedleIsDraggedByATurnSwingsBackAndSettlesOnNorth() {
        StaffMotion.Needle needle = new StaffMotion.Needle();
        double dt = 1.0 / 60;
        for (int i = 0; i < 60; i++) needle.step(dt, 0.0);
        assertEquals(0.0, needle.heading(), 1e-9, "at rest it points north");

        double most = 0.0;
        for (int i = 0; i < 15; i++) {
            needle.step(dt, 180.0);
            most = Math.max(most, needle.heading());
        }
        assertTrue(most > 3.0, "dragged round with the box: " + most);

        double least = 0.0;
        for (int i = 0; i < 90; i++) {
            needle.step(dt, 0.0);
            least = Math.min(least, needle.heading());
        }
        assertTrue(least < -0.5, "swings back past north: " + least);
        for (int i = 0; i < 60 * 8; i++) needle.step(dt, 0.0);
        assertEquals(0.0, needle.heading(), 0.05, "settled on north after eight seconds");
    }

    @Test
    void theNeedleRingsAtItsPeriodAtAnyFrameRate() {
        double period = 2 * Math.PI / StaffMotion.NEEDLE_OMEGA;
        double damped = period / Math.sqrt(1 - StaffMotion.NEEDLE_ZETA * StaffMotion.NEEDLE_ZETA);
        for (double fps : new double[]{30, 60, 144}) {
            StaffMotion.Needle needle = new StaffMotion.Needle();
            needle.step(1.0 / fps, 0.0);
            needle.kick(StaffMotion.NEEDLE_KNOCK);
            double t = 0, prev = 0, first = -1, third = -1;
            int crossings = 0;
            while (t < 5.0 && third < 0) {
                needle.step(1.0 / fps, 0.0);
                t += 1.0 / fps;
                double now = needle.heading();
                if (t > 1.0 / fps && (prev < 0) != (now < 0)) {
                    crossings++;
                    if (crossings == 1) first = t;
                    if (crossings == 3) third = t;
                }
                prev = now;
            }
            assertTrue(third > 0, "it rings at " + fps + " fps");
            assertEquals(damped, third - first, damped * 0.08, "period at " + fps + " fps");
        }
    }

    @Test
    void aLongGapStartsTheNeedleAndTheBubbleAgainAtRest() {
        StaffMotion.Needle needle = new StaffMotion.Needle();
        needle.kick(400.0);
        needle.step(1.0 / 60, 0.0);
        assertTrue(Math.abs(needle.heading()) > 1.0);
        needle.step(2.0, 0.0);
        assertEquals(0.0, needle.heading(), 0.0, "a paused game or a staff taken out again: no flung needle");

        StaffMotion.Spring bubble = new StaffMotion.Spring(StaffMotion.BUBBLE_OMEGA, StaffMotion.BUBBLE_ZETA);
        bubble.kick(StaffMotion.BUBBLE_KNOCK);
        bubble.step(0.0, 1.0 / 60);
        assertTrue(bubble.value() > 0.0);
        assertEquals(0.3, bubble.step(0.3, 2.0), 0.0, "and the bubble starts where it rests");
    }

    @Test
    void theBubbleIsKnockedAndSettlesDeadBeat() {
        StaffMotion.Spring bubble = new StaffMotion.Spring(StaffMotion.BUBBLE_OMEGA, StaffMotion.BUBBLE_ZETA);
        bubble.kick(StaffMotion.BUBBLE_KNOCK);
        double most = 0.0;
        int reversals = 0;
        double prev = 0.0;
        double prevSlope = 1.0;
        for (int i = 0; i < 120; i++) {
            double x = bubble.step(0.0, 1.0 / 60);
            most = Math.max(most, x);
            double slope = x - prev;
            if (slope * prevSlope < 0) reversals++;
            prevSlope = slope;
            prev = x;
        }
        assertTrue(most > 0.5 && most < StaffMotion.BUBBLE_TRAVEL + 0.5, "a knock throws it along the vial: " + most);
        assertTrue(reversals <= 6, "thick spirit: two or three swings, not a buzz: " + reversals);
        assertEquals(0.0, bubble.value(), 0.01, "settled in two seconds");
    }

    @Test
    void theVanesSnapUpPastUprightAndSettleOneAfterTheOther() {
        assertEquals(90.0, StaffMotion.vane(0.0, StaffMotion.VANE_FRONT_DELAY), 0.0, "flat at the draw");
        assertEquals(90.0, StaffMotion.vane(StaffMotion.VANE_FRONT_DELAY, StaffMotion.VANE_FRONT_DELAY), 0.0);
        double end = StaffMotion.VANE_FRONT_DELAY + StaffMotion.VANE_SECONDS;
        assertEquals(0.0, StaffMotion.vane(end, StaffMotion.VANE_FRONT_DELAY), 1e-9, "upright when the flip is over");
        assertEquals(0.0, StaffMotion.vane(5.0, StaffMotion.VANE_FRONT_DELAY), 0.0);
        double past = 0.0;
        for (double t = StaffMotion.VANE_FRONT_DELAY; t < end; t += 0.002) {
            past = Math.min(past, StaffMotion.vane(t, StaffMotion.VANE_FRONT_DELAY));
        }
        assertTrue(past < -5.0 && past > -30.0, "snaps a little past upright and back: " + past);

        double mid = StaffMotion.VANE_FRONT_DELAY + 0.09;
        assertTrue(StaffMotion.vane(mid, StaffMotion.VANE_FRONT_DELAY) < 45.0);
        assertEquals(90.0, StaffMotion.vane(mid, StaffMotion.VANE_REAR_DELAY), 1e-9);
        assertEquals(0.0, StaffMotion.vane(-1.0, StaffMotion.VANE_FRONT_DELAY), 0.0, "a clock behind: up, not flat");
    }

    @Test
    void theWheelStepsAQuarterTheShortWay() {
        assertEquals(-90.0, StaffMotion.quarters(0, 1, 4), 0.0, "forward: a quarter clockwise from the holder's side");
        assertEquals(90.0, StaffMotion.quarters(1, 0, 4), 0.0, "back: a quarter the other way");
        assertEquals(-90.0, StaffMotion.quarters(3, 0, 4), 0.0, "Link to Survey is one step forward, not three back");
        assertEquals(90.0, StaffMotion.quarters(0, 3, 4), 0.0, "Survey to Link is one step back, not three forward");
        assertEquals(-180.0, StaffMotion.quarters(1, 3, 4), 0.0, "two steps: a half turn forward");
        assertEquals(0.0, StaffMotion.quarters(2, 2, 4), 0.0);

        double wheel = 0.0;
        int mode = 0;
        int[] path = {1, 2, 3, 0, 3, 1};
        for (int to : path) {
            wheel += StaffMotion.quarters(mode, to, 4);
            mode = to;
            assertEquals(0.0, StaffMotion.wrap(180.0 + 90.0 * mode + wheel - 180.0), 1e-9, "glass " + mode + " at the slit");
        }
    }

    @Test
    void theWheelOvershootsIntoTheNotchAndStops() {
        assertEquals(0.0, StaffMotion.wheel(0.0, -90.0, 0.0), 0.0, "not yet turned at the change");
        assertEquals(-90.0, StaffMotion.wheel(0.0, -90.0, StaffMotion.WHEEL_TICKS), 0.0, "there when the turn is over");
        assertEquals(-90.0, StaffMotion.wheel(0.0, -90.0, 100.0), 0.0);
        assertEquals(-90.0, StaffMotion.wheel(0.0, -90.0, -1.0), 0.0, "never changed, or a clock behind: at rest");
        double most = 0.0;
        for (double t = 0; t < StaffMotion.WHEEL_TICKS; t += 0.05) most = Math.min(most, StaffMotion.wheel(0.0, -90.0, t));
        assertTrue(most < -95.0 && most > -120.0, "rides past the notch and is pulled back: " + most);
    }

    @Test
    void theDetentRidesTheRimBetweenNotches() {
        for (int k = -4; k <= 4; k++) {
            assertEquals(0.0, StaffMotion.detent(90.0 * k), 0.0, "a notch under the tooth at every quarter: " + k);
        }
        assertEquals(StaffMotion.DETENT_LIFT, StaffMotion.detent(-45.0), 1e-9, "the most halfway");
        assertEquals(StaffMotion.DETENT_LIFT, StaffMotion.detent(45.0), 1e-9, "either way round");
        assertTrue(StaffMotion.detent(-9.0) > 0.35 * StaffMotion.DETENT_LIFT, "springs up off the notch, not a slow wave");
    }

    @Test
    void theHookHangsKeepsItsLineAndSwingsWhenThePoleMoves() {
        double length = 0.22;
        StaffMotion.Bob bob = new StaffMotion.Bob();
        bob.step(0, 1.6, 0, length, 0.0);
        double[] h = bob.hang();
        assertEquals(-1.0, h[1], 1e-12, "placed hanging straight down");

        for (int i = 0; i < 120; i++) bob.step(0, 1.6, 0, length, 1.0 / 60);
        assertEquals(-1.0, bob.hang()[1], 1e-9);

        double x = 0;
        double lag = 0;
        for (int i = 0; i < 12; i++) {
            x += 4.0 / 60;
            bob.step(x, 1.6, 0, length, 1.0 / 60);
            lag = Math.min(lag, bob.hang()[0]);
        }
        assertTrue(lag < -0.15, "hangs back behind a pole that sets off: " + lag);

        double ahead = 0;
        for (int i = 0; i < 30; i++) {
            bob.step(x, 1.6, 0, length, 1.0 / 60);
            ahead = Math.max(ahead, bob.hang()[0]);
        }
        assertTrue(ahead > 0.15, "swings on past the vertical when the pole stops: " + ahead);
        for (int i = 0; i < 60 * 6; i++) bob.step(x, 1.6, 0, length, 1.0 / 60);
        double[] settled = bob.hang();
        assertTrue(Math.abs(settled[0]) < 0.02 && settled[1] < -0.999, "settled after six seconds: " + settled[0]);
    }

    @Test
    void theHookSwingsAtAPendulumsPeriodAtAnyFrameRate() {
        double length = 0.22;
        double expected = 2 * Math.PI * Math.sqrt(length / StaffMotion.GRAVITY);
        for (double fps : new double[]{30, 60, 144}) {
            StaffMotion.Bob bob = new StaffMotion.Bob();
            bob.step(0, 0, 0, length, 0.0);

            bob.step(0.03, 0, 0, length, 1.0 / fps);
            double t = 0;
            double prev = bob.hang()[0] - 0.0;
            double firstCross = -1, thirdCross = -1;
            int crossings = 0;
            while (t < 4.0 && thirdCross < 0) {
                bob.step(0.03, 0, 0, length, 1.0 / fps);
                t += 1.0 / fps;
                double now = bob.hang()[0];
                if ((prev < 0) != (now < 0)) {
                    crossings++;
                    if (crossings == 1) firstCross = t;
                    if (crossings == 3) thirdCross = t;
                }
                prev = now;
            }
            assertTrue(thirdCross > 0, "it swings at " + fps + " fps");
            assertEquals(expected, thirdCross - firstCross, expected * 0.08, "period at " + fps + " fps");
        }
    }

    @Test
    void aLongGapStartsTheHookAgainAtRest() {
        StaffMotion.Bob bob = new StaffMotion.Bob();
        bob.step(0, 0, 0, 0.2, 0.0);
        bob.step(5.0, 0, 0, 0.2, 2.0);
        assertEquals(-1.0, bob.hang()[1], 1e-12, "a paused game or a staff taken out again: no flung hook");
    }
}
