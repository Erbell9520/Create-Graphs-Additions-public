package com.createstats.flight.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurePursuitTest {
    private static final double LD = 10.0;
    private static final double R = 4.0;
    private static final double DZ = 3.0;

    @Test
    void deadzoneCommandsNoSteering() {
        assertEquals(0.0, PurePursuit.steer(0.0, LD, R, DZ), 1e-9);
        assertEquals(0.0, PurePursuit.steer(2.5, LD, R, DZ), 1e-9);
        assertEquals(0.0, PurePursuit.steer(-3.0, LD, R, DZ), 1e-9, "the deadzone boundary is inclusive");
    }

    @Test
    void steeringSignFollowsHeadingError() {
        assertTrue(PurePursuit.steer(30.0, LD, R, DZ) > 0.0);
        assertTrue(PurePursuit.steer(-30.0, LD, R, DZ) < 0.0);
    }

    @Test
    void steeringMagnitudeIncreasesWithHeadingErrorUpTo90() {
        double s20 = PurePursuit.steer(20.0, LD, R, DZ);
        double s45 = PurePursuit.steer(45.0, LD, R, DZ);
        double s80 = PurePursuit.steer(80.0, LD, R, DZ);
        assertTrue(s20 < s45, "45 deg should steer harder than 20 deg");
        assertTrue(s45 < s80, "80 deg should steer harder than 45 deg");
        assertTrue(s20 > 0.0 && s80 <= 1.0);
    }

    @Test
    void sharpButUnder90ErrorSaturatesToFullLock() {
        assertEquals(1.0, PurePursuit.steer(85.0, 6.0, R, DZ), 1e-9);
    }

    @Test
    void abeamOrBehindTargetCommandsFullLockTowardIt() {
        assertEquals(1.0, PurePursuit.steer(120.0, LD, R, DZ), 1e-9);
        assertEquals(-1.0, PurePursuit.steer(-160.0, LD, R, DZ), 1e-9);
        assertEquals(1.0, PurePursuit.steer(90.0, LD, R, DZ), 1e-9, "exactly abeam locks over");
    }

    @Test
    void shorterLookaheadSteersHarderForTheSameError() {
        double near = PurePursuit.steer(30.0, 6.0, R, DZ);
        double far = PurePursuit.steer(30.0, 18.0, R, DZ);
        assertTrue(near > far, "a closer lookahead point should demand a sharper turn");
    }

    @Test
    void outputIsAlwaysWithinUnitRange() {
        for (double err = -180.0; err <= 180.0; err += 7.0) {
            for (double ld = 2.0; ld <= 24.0; ld += 4.0) {
                double s = PurePursuit.steer(err, ld, R, DZ);
                assertTrue(s >= -1.0 && s <= 1.0, "steering out of range at err=" + err + " ld=" + ld + ": " + s);
            }
        }
    }
}
