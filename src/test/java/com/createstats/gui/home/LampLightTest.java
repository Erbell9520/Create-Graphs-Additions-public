package com.createstats.gui.home;

import com.createstats.power.HomeSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LampLightTest {
    @Test
    @DisplayName("only a lit lamp pools light: off, starved, shed and out of range are dark on the floor")
    void onlyLitLampsPool() {
        assertTrue(LampLight.pools(HomeSegment.State.ON));
        for (HomeSegment.State s : HomeSegment.State.values()) {
            if (s != HomeSegment.State.ON) {
                assertFalse(LampLight.pools(s), s + " pools no light");
            }
        }
    }

    @Test
    @DisplayName("the dimmer's top notch is the full pool and its lowest a tenth: (mode + 1) / modes, never mode / modes")
    void dimmerMapsToTheFullRange() {
        assertEquals(1f, LampLight.level(9, 10), 1e-6f, "mode 9 of 10 is 100%");
        assertEquals(0.1f, LampLight.level(0, 10), 1e-6f, "mode 0 of 10 is 10%");
        assertEquals(0.5f, LampLight.level(4, 10), 1e-6f);
        assertEquals(1f, LampLight.level(0, 1), 1e-6f, "a fixture with one mode burns at full");
        assertEquals(1f, LampLight.level(0, 0), 1e-6f, "and one with none");
        assertEquals(1f, LampLight.level(12, 10), 1e-6f, "a mode past the end is clamped, never over full");
    }
}
