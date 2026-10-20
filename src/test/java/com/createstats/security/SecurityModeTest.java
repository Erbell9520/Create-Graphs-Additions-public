package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityModeTest {
    @Test
    @DisplayName("AWAY_PLUS is appended, so no saved household changes meaning")
    void ordinalsAreASaveFormat() {
        assertEquals(0, SecurityMode.HOME.ordinal());
        assertEquals(1, SecurityMode.AWAY.ordinal());
        assertEquals(2, SecurityMode.CUSTOM.ordinal());
        assertEquals(3, SecurityMode.AWAY_PLUS.ordinal());
        assertEquals(4, SecurityMode.all().length);
        assertEquals(SecurityMode.AWAY_PLUS, SecurityMode.byOrdinal(3));
    }

    @Test
    @DisplayName("An ordinal from a newer jar degrades to the safest mode, not to an exception")
    void outOfRangeIsHome() {
        assertEquals(SecurityMode.HOME, SecurityMode.byOrdinal(4));
        assertEquals(SecurityMode.HOME, SecurityMode.byOrdinal(-1));
    }

    @Test
    @DisplayName("AWAY_PLUS arms every intrusion sensor and discards the per sensor flag")
    void armsEverything() {
        assertTrue(SecurityMode.AWAY_PLUS.armsIntrusion(true));
        assertTrue(SecurityMode.AWAY_PLUS.armsIntrusion(false));
        assertFalse(SecurityMode.AWAY_PLUS.usesPerSensorFlag());
    }

    @Test
    @DisplayName("AWAY_PLUS counts as away, so a trip still seals and a wrong code still tampers")
    void countsAsAway() {
        assertTrue(SecurityMode.AWAY_PLUS.isAway());
        assertTrue(SecurityMode.AWAY.isAway());
        assertFalse(SecurityMode.HOME.isAway());
        assertFalse(SecurityMode.CUSTOM.isAway());
    }

    @Test
    @DisplayName("sealsHouse is true for AWAY_PLUS alone, which is how a door tells it from AWAY")
    void onlyAwayPlusSeals() {
        assertTrue(SecurityMode.AWAY_PLUS.sealsHouse());
        assertFalse(SecurityMode.HOME.sealsHouse());
        assertFalse(SecurityMode.AWAY.sealsHouse());
        assertFalse(SecurityMode.CUSTOM.sealsHouse());
    }
}
