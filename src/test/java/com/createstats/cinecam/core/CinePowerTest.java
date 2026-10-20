package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinePowerTest {
    private static final int FILMING = 3;
    private static final int IDLE = 1;
    private static final int CAP = CinePower.batteryFor(FILMING);
    private static final int REBOOT = 25;

    @Test
    void theBatteryFilmsForAnHour() {
        assertEquals(216_000, CAP, "the profile's default: the owner's hour at 3 FE/t");
        assertEquals(CinePower.HOUR_TICKS, CinePower.ticksAt(CAP, FILMING), "an hour of filming");
        assertEquals(3L * CinePower.HOUR_TICKS, CinePower.ticksAt(CAP, IDLE), "three hours standing by at 1 FE/t");
        assertEquals(Long.MAX_VALUE, CinePower.ticksAt(CAP, 0), "a draw of nothing runs for ever");
        assertEquals(0L, CinePower.ticksAt(-5, FILMING), "nothing held runs nothing");
        assertEquals(Integer.MAX_VALUE, CinePower.batteryFor(Integer.MAX_VALUE), "saturates, never wraps (FE-REF INV-14)");
    }

    @Test
    void theGaugeRoundsDown() {
        assertEquals("1 h", CinePower.duration(CinePower.HOUR_TICKS));
        assertEquals("3 h", CinePower.duration(3L * CinePower.HOUR_TICKS));
        assertEquals("1 h 12 min", CinePower.duration(CinePower.HOUR_TICKS + 12 * 1200));
        assertEquals("59 min", CinePower.duration(CinePower.HOUR_TICKS - 1), "never a minute it does not hold");
        assertEquals("51 min", CinePower.duration(51 * 1200 + 1199));
        assertEquals("under 1 min", CinePower.duration(1199));
        assertEquals("under 1 min", CinePower.duration(0));
        assertEquals("no limit", CinePower.duration(Long.MAX_VALUE));
    }

    @Test
    void anEmptyCameraCannotRunThoughAFreshOneReadsPowered() {
        assertFalse(CinePower.canRun(true, true, 0, CAP, FILMING, REBOOT), "powered flag, empty battery");
        assertTrue(CinePower.canRun(true, true, FILMING, CAP, FILMING, REBOOT), "running, and one filming tick in hand");
        assertFalse(CinePower.canRun(true, true, FILMING - 1, CAP, FILMING, REBOOT), "running, but not a whole tick left");
    }

    @Test
    void aCutOffCameraWaitsForTheRebootThreshold() {
        assertFalse(CinePower.canRun(true, false, CAP / 4 - 1, CAP, FILMING, REBOOT), "flat, charged to just under 25%");
        assertTrue(CinePower.canRun(true, false, CAP / 4, CAP, FILMING, REBOOT), "back at 25%, 54,000 FE");
        assertTrue(CinePower.canRun(false, false, 0, CAP, FILMING, REBOOT), "free power runs on nothing");
    }

    @Test
    void aChargerFillsOneCameraUpToItsBattery() {
        assertEquals(500, CinePower.accepts(0, CAP, 500, 1));
        assertEquals(100, CinePower.accepts(CAP - 100, CAP, 500, 1), "only the room left");
        assertEquals(0, CinePower.accepts(CAP, CAP, 500, 1), "full");
        assertEquals(0, CinePower.accepts(0, CAP, 500, 2), "never a stack: each would gain what one paid for");
        assertEquals(0, CinePower.accepts(0, CAP, 0, 1), "nothing offered");
        assertEquals(0, CinePower.accepts(0, CAP, -7, 1), "a negative offer is nothing");
        assertEquals(0, CinePower.accepts(CAP + 50, CAP, 500, 1), "over full after a smaller config takes nothing");
    }
}
