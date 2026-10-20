package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayClockTest {
    @Test
    @DisplayName("night is the window the surface spawns in, on any day of the clock")
    void nightWindow() {
        assertFalse(DayClock.isNight(0), "dawn");
        assertFalse(DayClock.isNight(6000), "noon");
        assertFalse(DayClock.isNight(13187), "the last tick of dusk");
        assertTrue(DayClock.isNight(13188), "dark enough");
        assertTrue(DayClock.isNight(18000), "midnight");
        assertTrue(DayClock.isNight(22811));
        assertFalse(DayClock.isNight(22812), "light again");
        assertTrue(DayClock.isNight(3 * 24000 + 18000), "the third night is a night");
        assertTrue(DayClock.isNight(-6000), "a clock before zero still has a time of day");
    }

    @Test
    @DisplayName("the countdown reaches night and stops at zero through it, then counts to the next one")
    void countdown() {
        assertEquals(13188, DayClock.untilNight(0));
        assertEquals(7188, DayClock.untilNight(6000));
        assertEquals(1, DayClock.untilNight(13187));
        assertEquals(0, DayClock.untilNight(13188));
        assertEquals(0, DayClock.untilNight(20000));
        assertEquals(24000 - 23000 + 13188, DayClock.untilNight(23000), "past tonight: tomorrow's");
        assertEquals(2812, DayClock.untilDay(20000));
        assertEquals(0, DayClock.untilDay(6000));
    }
}
