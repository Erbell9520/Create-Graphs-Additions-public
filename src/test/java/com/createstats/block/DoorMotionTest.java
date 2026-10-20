package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorMotionTest {
    @Test
    @DisplayName("every opening declares a positive travel time")
    void everyOpeningHasATravelTime() {
        for (final DoorOpening style : DoorOpening.values()) {
            assertTrue(style.ticks() > 0, style + " has a travel time of " + style.ticks());
        }
    }

    @Test
    @DisplayName("every opening declares a curve")
    void everyOpeningHasACurve() {
        for (final DoorOpening style : DoorOpening.values()) {
            assertNotNull(style.ease(), style + " has no curve");
        }
    }

    @Test
    @DisplayName("nothing is quick enough to strobe or slow enough to be a wait")
    void travelTimesAreWithinReason() {
        for (final DoorOpening style : DoorOpening.values()) {
            assertTrue(style.ticks() >= 10, style + " is too quick at " + style.ticks() + " ticks");
            assertTrue(style.ticks() <= 40, style + " is too slow at " + style.ticks() + " ticks");
        }
    }

    @Test
    @DisplayName("the further a leaf travels, the longer it is given")
    void longerTravelGetsLongerTime() {
        assertTrue(DoorOpening.STACK_SLIDE.ticks() > DoorOpening.SLIDE.ticks(),
                "the stack slide crosses two blocks and must be given longer than a plain slide");
        assertTrue(DoorOpening.FOLD_RETRACT.ticks() > DoorOpening.FOLD_PARK.ticks(),
                "the retracting fold creases and then slides, so it must outlast the parking fold");
        assertTrue(DoorOpening.SWING.ticks() < DoorOpening.SLIDE.ticks(),
                "a swing sweeps a quarter turn and should be the quickest thing on the line");
    }

    @Test
    @DisplayName("only the shutter uses the gravity curve, and it is the only one that needs it")
    void onlyTheShutterFalls() {
        for (final DoorOpening style : DoorOpening.values()) {
            if (style == DoorOpening.ROLL_UP) {
                assertTrue(style.ease() == DoorEase.DRUM, "ROLL_UP should be on the drum curve");
            } else if (style == DoorOpening.SWING) {
                assertTrue(style.ease() == DoorEase.SWING, "SWING should be on the swing curve");
            } else {
                assertTrue(style.ease() == DoorEase.SERVO,
                        style + " is driven by a motor and should be on the servo curve");
            }
        }
    }

    @Test
    @DisplayName("a pair opening and its lone fallback are both fully specified")
    void fallbacksAreSpecifiedToo() {
        for (final DoorOpening style : DoorOpening.values()) {
            final DoorOpening lone = style.single();
            assertTrue(lone.ticks() > 0,
                    style + " falls back to " + lone + ", which has no travel time");
            assertNotNull(lone.ease(), style + " falls back to " + lone + ", which has no curve");
        }
    }
}
