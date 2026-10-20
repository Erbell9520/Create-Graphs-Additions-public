package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LampGlowTest {
    private static final int GREEN = 1;
    private static final int AMBER = -1;

    @Test
    @DisplayName("a lamp comes up over a few ticks, not in one")
    void warmsUp() {
        final LampGlow lamp = new LampGlow();
        lamp.tick(GREEN);
        assertEquals(GREEN, lamp.colour());
        assertTrue(lamp.glow(1f) > 0f && lamp.glow(1f) < 0.5f, "first tick " + lamp.glow(1f));
        int ticks = 1;
        while (lamp.glow(1f) < 1f && ticks < 20) {
            lamp.tick(GREEN);
            ticks++;
        }
        assertTrue(ticks >= 3 && ticks <= 5, "full in " + ticks + " ticks");
    }

    @Test
    @DisplayName("it dies away over a few ticks, and between frames it eases")
    void diesAway() {
        final LampGlow lamp = new LampGlow();
        for (int i = 0; i < 6; i++) {
            lamp.tick(GREEN);
        }
        lamp.tick(0);
        assertTrue(lamp.glow(1f) > 0.5f, "one tick after going out it is still bright");
        assertTrue(lamp.glow(0.5f) > lamp.glow(1f) && lamp.glow(0.5f) < lamp.glow(0f), "eased between ticks");
        for (int i = 0; i < 6; i++) {
            lamp.tick(0);
        }
        assertEquals(0f, lamp.glow(1f), 1e-6);
        assertEquals(0, lamp.colour());
    }

    @Test
    @DisplayName("a lamp changing colour goes out first, and never shows the new colour while the old one burns")
    void darkBetweenColours() {
        final LampGlow lamp = new LampGlow();
        for (int i = 0; i < 6; i++) {
            lamp.tick(GREEN);
        }
        boolean wentDark = false;
        for (int i = 0; i < 12; i++) {
            lamp.tick(AMBER);

            wentDark |= lamp.glow(1f) < LampGlow.DARK;
            if (lamp.colour() == AMBER && !wentDark) {
                fail("amber showed before green went out");
            }
        }
        assertEquals(AMBER, lamp.colour());
        assertEquals(1f, lamp.glow(1f), 1e-6, "and then comes up amber");
    }

    private static void fail(String why) {
        throw new AssertionError(why);
    }
}
