package com.createstats.vccmk4.core;

import com.createstats.ground.DriveMath;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtaG1StateTest {
    private static BtaG1State of(double throttle, double steer, double brake, double bps) {
        return BtaG1State.of(true, false, false, false, false, false, false, 0, throttle, steer, brake, bps);
    }

    @Test
    void oneBlockIsAMetreSoABlockASecondIsThreePointSixKilometresAnHour() {
        assertEquals(DriveMath.KMH, BtaG1State.KMH_PER_BPS, 0.0, "the Driver Helmet's convention, in one place");
        assertEquals(36.0, of(0, 0, 0, 10.0).speedKmh(), 1.0e-9);
    }

    @Test
    void theWireIsSevenBytesAndRoundTripsEveryField() {
        BtaG1State s = BtaG1State.of(true, true, true, true, true, true, true, -1, 0.5, -0.75, 0.5, -12.5);
        byte[] wire = s.encode();
        assertEquals(BtaG1State.BYTES, wire.length);
        assertEquals(s, BtaG1State.decode(wire));
        assertTrue(s.driving() && s.route() && s.braking() && s.holding() && s.parked() && s.boosting() && s.reversing());
        assertEquals(-1, s.indicate());
        assertTrue(s.speedKmh() < 0.0, "rolling backwards keeps its sign");
    }

    @Test
    void anyStateRoundTripsThroughTheWire() {
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            BtaG1State s = BtaG1State.of(random.nextBoolean(), random.nextBoolean(), random.nextBoolean(), random.nextBoolean(),
                    random.nextBoolean(), random.nextBoolean(), random.nextBoolean(), random.nextInt(3) - 1,
                    random.nextDouble() * 2.4 - 1.2, random.nextDouble() * 2.4 - 1.2, random.nextDouble() * 1.4 - 0.2,
                    (random.nextDouble() - 0.5) * 400.0);
            assertEquals(s, BtaG1State.decode(s.encode()), "state " + i);
        }
    }

    @Test
    void aFlagCanBeSetAndClearedWithoutTouchingTheRest() {
        BtaG1State s = BtaG1State.of(true, false, true, false, false, false, false, 0, 0.5, -0.5, 1.0, 10.0);
        BtaG1State seated = s.with(BtaG1State.SEATED, true).with(BtaG1State.GREETING, true);
        assertTrue(seated.seated() && seated.greeting() && seated.driving() && seated.braking());
        assertEquals(s.throttleQ(), seated.throttleQ());
        assertEquals(s.speedQ(), seated.speedQ());
        assertEquals(s, seated.with(BtaG1State.SEATED, false).with(BtaG1State.GREETING, false), "and back again");
        assertEquals(seated, BtaG1State.decode(seated.encode()), "the two new bits cross the wire");
    }

    @Test
    void eachFlagIsItsOwnBit() {
        int[] flags = {BtaG1State.DRIVING, BtaG1State.ROUTE, BtaG1State.BRAKING, BtaG1State.HOLDING, BtaG1State.PARKED,
                BtaG1State.BOOSTING, BtaG1State.REVERSING, BtaG1State.SIGNAL_LEFT, BtaG1State.SIGNAL_RIGHT,
                BtaG1State.GREETING, BtaG1State.SEATED};
        int seen = 0;
        for (int f : flags) {
            assertEquals(1, Integer.bitCount(f), "one bit each");
            assertEquals(0, seen & f, "no two the same");
            seen |= f;
        }
        assertTrue(seen < 1 << 16, "and they fit the two bytes the wire gives them");
    }

    @Test
    void sticksAreClampedAndAnythingNotANumberIsZero() {
        BtaG1State s = of(9.0, -9.0, 9.0, 0.0);
        assertEquals(1.0, s.throttle(), 1.0e-9);
        assertEquals(-1.0, s.steer(), 1.0e-9);
        assertEquals(1.0, s.brake(), 1.0e-9);
        BtaG1State neg = of(0.0, 0.0, -1.0, 0.0);
        assertEquals(0.0, neg.brake(), 0.0, "a brake cannot be negative");
        BtaG1State nan = of(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN);
        assertEquals(0.0, nan.throttle(), 0.0);
        assertEquals(0.0, nan.steer(), 0.0);
        assertEquals(0.0, nan.speedKmh(), 0.0);
    }

    @Test
    void speedNoiseAtRestIsNotAChange() {
        assertEquals(of(0, 0, 0, 0.0), of(0, 0, 0, 0.049), "a vehicle resting on its suspension sends nothing");
        assertEquals(of(0, 0, 0, 0.0), of(0, 0, 0, -0.049));
        assertNotEquals(of(0, 0, 0, 0.0), of(0, 0, 0, 0.06), "but moving is");
    }

    @Test
    void speedIsInEighthsOfAKilometreAnHourAndClampsToTheWire() {
        assertEquals(288, of(0, 0, 0, 10.0).speedQ());
        assertEquals(of(0, 0, 0, 10.0), of(0, 0, 0, 10.005), "a sliver of jitter at speed is inside the step");
        assertEquals(Short.MAX_VALUE, of(0, 0, 0, 1.0e6).speedQ(), "an absurd speed is clamped, not wrapped negative");
        assertEquals(Short.MIN_VALUE, of(0, 0, 0, -1.0e6).speedQ());
    }

    @Test
    void aShortOrMissingPacketIsAnIdleConsoleNotAnException() {
        assertEquals(BtaG1State.IDLE, BtaG1State.decode(null));
        assertEquals(BtaG1State.IDLE, BtaG1State.decode(new byte[0]));
        assertEquals(BtaG1State.IDLE, BtaG1State.decode(new byte[BtaG1State.BYTES - 1]));
    }

    @Test
    void aCorruptPacketCannotPutAControlPastItsStops() {
        byte[] wire = new byte[BtaG1State.BYTES];
        java.util.Arrays.fill(wire, (byte) 0x80);
        BtaG1State s = BtaG1State.decode(wire);
        assertTrue(Math.abs(s.throttle()) <= 1.0 && Math.abs(s.steer()) <= 1.0, "sticks stay inside -1..1");
        assertTrue(s.brake() >= 0.0 && s.brake() <= 1.0, "and the brake inside 0..1");
    }

    @Test
    void theIdleStateIsAllZeroAndEncodesToZeroBytes() {
        assertArrayEquals(new byte[BtaG1State.BYTES], BtaG1State.IDLE.encode());
        assertFalse(BtaG1State.IDLE.driving());
        assertEquals(0, BtaG1State.IDLE.indicate());
    }
}
