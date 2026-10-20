package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteDriveTest {
    static final DriveModel.Options OWNER = new DriveModel.Options(true, true, 20, 30, 256, 50, 60,
            DriveModel.Options.AS_RAMP, true, true);

    @Test
    void thePlayersRampsDoNotSlowARoute() {
        RouteDrive d = new RouteDrive();
        DriveModel.Output o = null;
        for (int t = 0; t < 5; t++) o = d.step(OWNER, 0.5, 0.0, 0.0, 0.0, false);
        assertEquals(0.5, o.throttle(), 1.0e-9, "half throttle in five ticks, not thirty (ThrottleTicks 60)");
        for (int t = 0; t < 5; t++) o = d.step(OWNER, 0.5, 1.0, 0.0, 0.0, false);
        assertEquals(1.0, o.steer(), 1.0e-9, "full stick in five ticks, not twenty (SteerTicks 20)");
    }

    @Test
    void theSlewLimitsStillHold() {
        RouteDrive d = new RouteDrive();
        DriveModel.Output o = d.step(OWNER, 1.0, -1.0, 0.0, 0.0, false);
        assertEquals(RouteDrive.THROTTLE_SLEW, o.throttle(), 1.0e-9, "rest to full is never one tick");
        assertEquals(-RouteDrive.STEER_SLEW, o.steer(), 1.0e-9);
    }

    @Test
    void aBrakeLevelIsALevelAndLeavesTheThrottleToTheRoute() {
        RouteDrive d = new RouteDrive();
        for (int t = 0; t < 10; t++) d.step(OWNER, 0.4, 0.0, 0.0, 5.0, false);
        DriveModel.Output o = d.step(OWNER, 0.4, 0.0, 0.2, 5.0, false);
        assertEquals(0.2, o.brake(), 1.0e-9);
        assertEquals(3, o.brakeSignal(), "a fifth of fifteen");
        assertTrue(o.braking());
        assertEquals(0.4, o.throttle(), 1.0e-9, "a light brake does not zero the throttle (1.14.85's drive model did)");
        assertFalse(o.holding());
        DriveModel.Output held = d.step(OWNER, 0.0, 0.0, 1.0, 0.0, true);
        assertTrue(held.holding() && held.brakeSignal() == 15, "a hold is the full brake, marked as a hold");
    }

    @Test
    void theLockShrinksWithSpeedAsTheDriversDoes() {
        RouteDrive d = new RouteDrive();
        assertEquals(DriveModel.lockAt(OWNER, 12.0), d.step(OWNER, 0.0, 0.0, 0.0, 12.0, false).lockDeg(), 1.0e-9);
        assertTrue(DriveModel.lockAt(OWNER, 12.0) < 30.0);
    }

    @Test
    void aChangeOfDirectionStartsFromRest() {
        RouteDrive d = new RouteDrive();
        for (int t = 0; t < 10; t++) d.step(OWNER, 0.5, 0.0, 0.0, 0.0, false);
        DriveModel.Output o = d.step(OWNER, -0.3, 0.0, 0.0, 0.0, false);
        assertEquals(-RouteDrive.THROTTLE_SLEW, o.throttle(), 1.0e-9, "never slides through zero with drive on");
    }

    @Test
    void aPlayersOutputReadsAsTheFullBrakeItAlwaysWas() {
        DriveModel.Output key = new DriveModel.Output(0.0, 0.0, true, false, 30.0, 1.0, false);
        assertEquals(1.0, key.brake(), 1.0e-9);
        assertEquals(15, key.brakeSignal());
        assertEquals(0, DriveModel.Output.idle(30.0).brakeSignal());
    }
}
