package com.createstats.client.coupler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeMotionTest {
    private static final double EPS = 1e-9;

    @Test
    void thePaceFollowsTheCrossingOnALogScaleAndParksAtZero() {
        assertEquals(Double.POSITIVE_INFINITY, ExchangeMotion.periodTicks(0));
        assertEquals(160.0, ExchangeMotion.periodTicks(64), 1e-6);
        assertEquals(160.0, ExchangeMotion.periodTicks(1), 1e-6, "a trickle below 64 FE/t still turns, slowest");
        assertEquals(12.0, ExchangeMotion.periodTicks(8192), 1e-6);
        assertEquals(12.0, ExchangeMotion.periodTicks(1_000_000), 1e-6);

        assertEquals(25.2, ExchangeMotion.periodTicks(2048), 0.1);
        double last = Double.POSITIVE_INFINITY;
        for (int fe = 64; fe <= 8192; fe += 64) {
            final double p = ExchangeMotion.periodTicks(fe);
            assertTrue(p <= last, "faster for more FE at " + fe);
            last = p;
        }
    }

    @Test
    void thePinStaysInItsSlotThroughTheSwing() {
        for (int k = -2; k <= 2; k++) {
            for (int i = 0; i <= 1000; i++) {
                final double q = k + ExchangeMotion.SWING * i / 1000.0;
                final double crank = Math.toRadians(ExchangeMotion.driverAngle(q));
                final double driver = Math.toRadians(ExchangeMotion.DRIVER_AT);
                final double py = ExchangeMotion.STAR_C * Math.cos(driver) + ExchangeMotion.STAR_R * Math.cos(crank);
                final double pz = ExchangeMotion.STAR_C * Math.sin(driver) + ExchangeMotion.STAR_R * Math.sin(crank);
                final double pin = Math.toDegrees(Math.atan2(pz, py));
                final double off = ((pin - ExchangeMotion.starAngle(q)) % 90.0 + 90.0) % 90.0;
                assertTrue(Math.min(off, 90.0 - off) < 1e-6, "pin off its slot by " + off + " at q " + q);
            }
        }
    }

    @Test
    void theStarTurnsAQuarterAnIndexAndHoldsThroughTheDwell() {
        for (int k = -3; k <= 3; k++) {
            assertEquals(90.0 * k, ExchangeMotion.starAngle(k), EPS);
            assertEquals(90.0 * k + 90.0, ExchangeMotion.starAngle(k + ExchangeMotion.SWING), 1e-6);
            assertEquals(90.0 * k + 90.0, ExchangeMotion.starAngle(k + 0.6), EPS);
        }
        double last = ExchangeMotion.starAngle(-2.0);
        for (int i = 1; i <= 40_000; i++) {
            final double s = ExchangeMotion.starAngle(-2.0 + i / 10_000.0);
            assertTrue(s >= last - EPS && s - last < 0.5, "the star never jumps or runs back");
            last = s;
        }
    }

    @Test
    void noCassetteChangesChargeByAJumpInEitherMode() {
        for (boolean pull : new boolean[] {true, false}) {
            final double[] last = new double[4];
            for (int i = 0; i < 4; i++) {
                last[i] = ExchangeMotion.cassetteLevel(i, 0.6, pull);
            }
            final int steps = 20_000;
            for (int s = 1; s <= steps; s++) {
                final double q = 0.6 + (pull ? 1 : -1) * 3.0 * s / steps;
                for (int i = 0; i < 4; i++) {
                    final double level = ExchangeMotion.cassetteLevel(i, q, pull);
                    assertTrue(Math.abs(level - last[i]) < 0.01,
                            "cassette " + i + " jumped from " + last[i] + " to " + level + " at q " + q
                                    + (pull ? " pulling" : " pushing"));
                    last[i] = level;
                }
            }
        }
    }

    @Test
    void theSourceFillsTheSinkEmptiesAndTheTopRidesFull() {
        final double early = ExchangeMotion.CONTACT_START + 0.001;
        final double late = ExchangeMotion.CONTACT_END - 0.001;
        for (int i = 0; i < 4; i++) {
            final int station = ExchangeMotion.station(i, ExchangeMotion.starAngle(1.0 + early));
            final double a = ExchangeMotion.cassetteLevel(i, 1.0 + early, true);
            final double b = ExchangeMotion.cassetteLevel(i, 1.0 + late, true);
            switch (station) {
                case ExchangeMotion.NORTH -> assertTrue(a < 0.01 && b > 0.99, "their dock charges");
                case ExchangeMotion.SOUTH -> assertTrue(a > 0.99 && b < 0.01, "our dock drains");
                case ExchangeMotion.TOP -> assertTrue(a == 1.0 && b == 1.0, "full over the top");
                default -> assertTrue(a == 0.0 && b == 0.0, "empty underneath");
            }
        }
    }

    @Test
    void aLeverFlipInTheDwellChangesNoCassette() {
        for (int s = 0; s <= 1000; s++) {
            final double q = 2.0 + ExchangeMotion.CONTACT_START + (ExchangeMotion.CONTACT_END
                    - ExchangeMotion.CONTACT_START) * s / 1000.0;
            for (int i = 0; i < 4; i++) {
                assertEquals(ExchangeMotion.cassetteLevel(i, q, true), ExchangeMotion.cassetteLevel(i, q, false), 1e-9,
                        "cassette " + i + " at q " + q);
            }
        }
        assertEquals(1.0 + ExchangeMotion.SWING, ExchangeMotion.CONTACT_START + ExchangeMotion.CONTACT_END, EPS,
                "the contact window is symmetric about the reversal");
    }

    @Test
    void theShoesAreUpForTheWholeSwingAndNeverSinkBelowThePads() {
        for (int i = 0; i <= 100_000; i++) {
            final double t = i / 100_000.0 * 0.999999;
            final double lift = ExchangeMotion.shoeLift(t);
            assertTrue(lift >= 0.0 && lift <= 1.0, "lift " + lift + " at " + t);
            if (t < ExchangeMotion.SWING * 0.85) {
                assertEquals(1.0, lift, EPS, "up while the drum turns, at " + t);
            }
            if (t >= ExchangeMotion.CONTACT_START && t < ExchangeMotion.CONTACT_END) {
                assertEquals(0.0, lift, EPS, "on the pads through the contact window, at " + t);
            }
        }
        assertEquals(1.0, ExchangeMotion.shoeLift(0.0), EPS);
        assertEquals(0.0, ExchangeMotion.shoeLift(ExchangeMotion.SWING), EPS);
        assertEquals(ExchangeMotion.shoeLift(0.0), ExchangeMotion.shoeLift(0.99999999), 1e-6, "continuous at the wrap");
    }

    @Test
    void theAnimatorCountsEveryIndexAndFinishesOneItBegan() {
        final ExchangeMotion.Animator a = new ExchangeMotion.Animator();
        a.advance(0.0, 1, 2048, true);
        for (int t = 1; t <= 1008; t++) {
            a.advance(t, 1, 2048, true);
        }
        final int expected = (int) Math.floor((1008.0 / ExchangeMotion.periodTicks(2048)));
        final int swings = a.takeSwingsBegun();
        assertTrue(Math.abs(swings - expected) <= 1, swings + " swings for about " + expected);
        assertTrue(Math.abs(a.takeSeated() - swings) <= 1);
        assertTrue(Math.abs(a.takeBroken() - swings) <= 1);
        assertEquals(0, a.takeSwingsBegun(), "taking resets the count");

        final double before = a.q();
        a.advance(4008.0, 1, 2048, true);
        assertEquals(before, a.q(), EPS);
        assertEquals(0, a.takeSwingsBegun());

        final ExchangeMotion.Animator b = new ExchangeMotion.Animator();
        b.advance(0.0, -1, 8192, true);
        double t = 0.0;
        while (ExchangeMotion.periodFraction(b.q(), false) > 0.1 || ExchangeMotion.periodFraction(b.q(), false) < 0.05) {
            t += 0.25;
            b.advance(t, -1, 8192, true);
        }
        for (int i = 0; i < 400; i++) {
            t += 1.0;
            b.advance(t, -1, 0, true);
        }
        final double parked = ExchangeMotion.periodFraction(b.q(), false);
        assertEquals(ExchangeMotion.CONTACT_START, parked, 1e-6, "parks where the shoes have settled");
        final double q = b.q();
        b.advance(t + 50.0, 0, 0, false);
        assertEquals(q, b.q(), EPS, "a parked drum stays parked");
    }

    @Test
    void theCrackleIsSlightAtATrickleAndFullAtTheRating() {
        assertEquals(0.0f, ExchangeMotion.crackleVolume(0));
        assertEquals(Math.pow(10.0, -0.5), ExchangeMotion.crackleVolume(64), 1e-6);
        assertEquals(1.0f, ExchangeMotion.crackleVolume(8192), 1e-6);
    }

    @Test
    void theLeverOvershootsItsDetentAndSettlesOnIt() {
        double max = -1e9;
        for (int i = 0; i <= 70; i++) {
            max = Math.max(max, ExchangeMotion.leverThrow(0.0, 45.0, i / 10.0));
        }
        assertTrue(max > 45.0 && max < 52.0, "a small overshoot: " + max);
        assertEquals(45.0, ExchangeMotion.leverThrow(0.0, 45.0, 7.0), EPS);
        assertEquals(-45.0, ExchangeMotion.leverThrow(45.0, -45.0, 30.0), EPS);
        assertEquals(0.0, ExchangeMotion.leverAngle(0), EPS);
    }
}
