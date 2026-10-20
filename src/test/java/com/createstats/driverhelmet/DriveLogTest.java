package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriveLogTest {
    @Test
    void thePeakIsTheLargestGOfTheLastTenSecondsAndThenLetsGo() {
        final DriveLog log = new DriveLog();
        log.tick(0, 1.4, 0.0, false);
        for (long t = 1; t < DriveLog.WINDOW_TICKS; t++) {
            log.tick(t, 0.3, 0.0, false);
        }
        assertEquals(1.4, log.peak(), 1e-6, "still inside ten seconds");
        log.tick(DriveLog.WINDOW_TICKS, 0.3, 0.0, false);
        assertEquals(0.3, log.peak(), 1e-6, "ten seconds on, it has left the window");
    }

    @Test
    void anImpactIsRecordedAsItGrowsAndKeptForTheTrip() {
        final DriveLog log = new DriveLog();
        assertEquals(-1, log.impactAge(0), "none yet");
        log.tick(100, 0.2, DriveLog.IMPACT_G - 0.1, false);
        assertEquals(0.0, log.lastImpact(), 0.0, "under the threshold is driving, not a hit");
        log.tick(101, 0.2, 6.0, false);
        log.tick(102, 0.2, 9.5, false);
        log.tick(103, 0.2, 3.0, false);
        assertEquals(9.5, log.lastImpact(), 1e-9, "the event's peak");
        assertEquals(2, log.impactAge(103), "aged from where it began");
        log.tick(104, 0.2, 1.0, false);
        log.tick(200, 0.2, 7.0, false);
        log.tick(201, 0.2, 0.5, false);
        assertEquals(7.0, log.lastImpact(), 1e-9, "a new hit replaces the last");
        assertEquals(9.5, log.tripImpact(), 1e-9, "the trip keeps the hardest");
    }

    @Test
    void airtimeCountsWithEveryTyreUpAndABumpIsNotAJump() {
        final DriveLog log = new DriveLog();
        log.tick(10, 0.0, 0.0, true);
        log.tick(12, 0.0, 0.0, true);
        assertEquals(0.1, log.airNow(12), 1e-9);
        log.tick(13, 0.0, 0.0, false);
        assertEquals(0.0, log.lastAir(), 0.0, "three ticks is a bump");
        assertEquals(0.0, log.airNow(13), 0.0);
        log.tick(20, 0.0, 0.0, true);
        log.tick(44, 0.0, 0.0, false);
        assertEquals(1.2, log.lastAir(), 1e-9, "twenty-four ticks in the air");
    }
}
