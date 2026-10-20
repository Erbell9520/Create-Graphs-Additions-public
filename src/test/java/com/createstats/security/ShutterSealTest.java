package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutterSealTest {
    @Test
    @DisplayName("A fire release opens every shutter and beats everything below it")
    void fireBeatsAll() {
        assertFalse(ShutterSeal.wantsSealed(true, true, true, true, true),
                "a window is a smoke vent, and that outranks every reason to shut it");
        assertFalse(ShutterSeal.wantsSealed(true, false, false, true, false));
    }

    @Test
    @DisplayName("A sealed house shuts a bank nobody commanded and no dusk flag would have shut")
    void sealBeatsTheRest() {
        assertTrue(ShutterSeal.wantsSealed(false, true, false, false, false));
    }

    @Test
    @DisplayName("A lockdown still seals on its own, exactly as it did before the mode existed")
    void lockdownStillSeals() {
        assertTrue(ShutterSeal.wantsSealed(false, false, true, false, false));
    }

    @Test
    @DisplayName("Below the emergencies the switch and the dusk flag are an OR, not a precedence")
    void switchAndDuskAreAnOr() {
        assertTrue(ShutterSeal.wantsSealed(false, false, false, true, false),
                "a bank told to seal stays sealed through dawn");
        assertTrue(ShutterSeal.wantsSealed(false, false, false, false, true),
                "and a bank on the dusk flag seals at night with nobody pressing anything");
        assertFalse(ShutterSeal.wantsSealed(false, false, false, false, false));
    }

    @Test
    @DisplayName("Standing AWAY+ down leaves every bank exactly as its owner set it")
    void theRestoreIsFree() {
        assertTrue(ShutterSeal.wantsSealed(false, true, false, true, false));
        assertTrue(ShutterSeal.wantsSealed(false, false, false, true, false));

        assertTrue(ShutterSeal.wantsSealed(false, true, false, false, false));
        assertFalse(ShutterSeal.wantsSealed(false, false, false, false, false));

        assertTrue(ShutterSeal.wantsSealed(false, true, false, false, false));
        assertFalse(ShutterSeal.wantsSealed(false, false, false, false, false));
        assertTrue(ShutterSeal.wantsSealed(false, false, false, false, true));
    }

    @Test
    @DisplayName("The whole truth table, so no reordering of the ladder can pass by accident")
    void everyCombination() {
        for (int bits = 0; bits < 32; bits++) {
            final boolean released = (bits & 1) != 0;
            final boolean houseSealed = (bits & 2) != 0;
            final boolean lockdown = (bits & 4) != 0;
            final boolean commanded = (bits & 8) != 0;
            final boolean duskNow = (bits & 16) != 0;

            final boolean expected = !released
                    && (houseSealed || lockdown || commanded || duskNow);

            final String state = "released=" + released + " sealed=" + houseSealed
                    + " lockdown=" + lockdown + " commanded=" + commanded + " dusk=" + duskNow;
            if (expected) {
                assertTrue(ShutterSeal.wantsSealed(released, houseSealed, lockdown, commanded,
                        duskNow), state);
            } else {
                assertFalse(ShutterSeal.wantsSealed(released, houseSealed, lockdown, commanded,
                        duskNow), state);
            }
        }
    }
}
