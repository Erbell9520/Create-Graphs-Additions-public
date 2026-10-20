package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiterLadderTest {
    @Test
    @DisplayName("the ladder is strictly ascending and starts at a genuine zero")
    void ladderAscends() {
        assertEquals(0, LimiterLadder.feAt(0), "the bottom of the range is shut, not a floor");
        int previous = -1;
        for (int notch = 0; notch <= LimiterLadder.MAX_NOTCH; notch++) {
            final int fe = LimiterLadder.feAt(notch);
            assertTrue(fe > previous, "notch " + notch + " must be above the one below it");
            previous = fe;
        }
    }

    @Test
    @DisplayName("no two neighbouring notches are more than a factor of two apart")
    void ladderHasNoGaps() {
        for (int notch = 2; notch <= LimiterLadder.MAX_NOTCH; notch++) {
            final int lower = LimiterLadder.feAt(notch - 1);
            final int upper = LimiterLadder.feAt(notch);
            assertTrue(upper <= lower * 2,
                    "notch " + notch + " (" + upper + ") is more than double " + lower);
        }
    }

    @Test
    @DisplayName("clamp is total: any int a packet can carry lands inside the ladder")
    void clampIsTotal() {
        assertEquals(0, LimiterLadder.clamp(Integer.MIN_VALUE));
        assertEquals(0, LimiterLadder.clamp(-1));
        assertEquals(LimiterLadder.MAX_NOTCH, LimiterLadder.clamp(Integer.MAX_VALUE));
        assertEquals(LimiterLadder.MAX_NOTCH, LimiterLadder.clamp(LimiterLadder.MAX_NOTCH + 1));
        assertEquals(7, LimiterLadder.clamp(7));
    }

    @Test
    @DisplayName("a notch survives the trip out to a track position and back")
    void fractionRoundTrips() {
        for (int notch = 0; notch <= LimiterLadder.MAX_NOTCH; notch++) {
            assertEquals(notch,
                    LimiterLadder.notchForFraction(LimiterLadder.fractionForNotch(notch)),
                    "notch " + notch + " did not survive the round trip");
        }
    }

    @Test
    @DisplayName("track positions outside the bar are clamped rather than wrapped")
    void fractionClamps() {
        assertEquals(0, LimiterLadder.notchForFraction(-4.0));
        assertEquals(LimiterLadder.MAX_NOTCH, LimiterLadder.notchForFraction(4.0));
    }

    @Test
    @DisplayName("every notch is its own nearest match, and the ends saturate")
    void nearestNotchIsExact() {
        for (int notch = 0; notch <= LimiterLadder.MAX_NOTCH; notch++) {
            assertEquals(notch, LimiterLadder.nearestNotch(LimiterLadder.feAt(notch)),
                    "notch " + notch + " should match its own figure");
        }
        assertEquals(0, LimiterLadder.nearestNotch(-1));
        assertEquals(LimiterLadder.MAX_NOTCH, LimiterLadder.nearestNotch(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("nearest is by ratio, not by difference")
    void nearestNotchUsesRatio() {
        assertEquals(LimiterLadder.feAt(LimiterLadder.nearestNotch(3000)), 3072);
        assertTrue(LimiterLadder.feAt(LimiterLadder.nearestNotch(600_000)) >= 524_288);
    }

    @Test
    @DisplayName("presets are all real notches")
    void presetsAreInRange() {
        for (int preset : LimiterLadder.PRESET_NOTCHES) {
            assertEquals(preset, LimiterLadder.clamp(preset), "preset " + preset + " is off the ladder");
        }
        assertEquals(0, LimiterLadder.PRESET_NOTCHES[0], "the first preset is the shut one");
        assertEquals(LimiterLadder.MAX_NOTCH,
                LimiterLadder.PRESET_NOTCHES[LimiterLadder.PRESET_NOTCHES.length - 1],
                "the last preset is the top of the ladder");
    }

    @Test
    @DisplayName("the default setting is the mod's base connector rate")
    void defaultIsTheBaseRate() {
        assertEquals(8192, LimiterLadder.feAt(LimiterLadder.DEFAULT_NOTCH));
    }

    @Test
    @DisplayName("compact never widens a figure into something misreadable")
    void compactIsReadable() {
        assertEquals("0", LimiterLadder.compact(0));
        assertEquals("999", LimiterLadder.compact(999));
        assertEquals("1k", LimiterLadder.compact(1000));
        assertEquals("1.5k", LimiterLadder.compact(1536));
        assertEquals("1.05M", LimiterLadder.compact(1_048_576));
    }

    @Test
    @DisplayName("ALWAYS ignores the signal entirely")
    void alwaysIgnoresRedstone() {
        for (int strength = 0; strength <= 15; strength++) {
            assertEquals(4096, LimiterMode.ALWAYS.apply(4096, strength));
        }
    }

    @Test
    @DisplayName("the two switch modes are exact opposites of each other")
    void switchModesAreOpposites() {
        for (int strength = 0; strength <= 15; strength++) {
            final int opens = LimiterMode.SIGNAL_OPENS.apply(4096, strength);
            final int shuts = LimiterMode.SIGNAL_SHUTS.apply(4096, strength);
            assertEquals(4096, opens + shuts, "exactly one of the two passes at strength " + strength);
        }
    }

    @Test
    @DisplayName("analog is shut at zero, whole at fifteen, and never above the setting")
    void analogSpansTheSignal() {
        assertEquals(0, LimiterMode.ANALOG.apply(4096, 0));
        assertEquals(4096, LimiterMode.ANALOG.apply(4096, 15));
        int previous = -1;
        for (int strength = 0; strength <= 15; strength++) {
            final int allowed = LimiterMode.ANALOG.apply(4096, strength);
            assertTrue(allowed >= previous, "analog must not fall as the signal rises");
            assertTrue(allowed <= 4096, "analog must never exceed the setting");
            previous = allowed;
        }
    }

    @Test
    @DisplayName("the top of the ladder on analog does not overflow int range")
    void analogSurvivesTheTopOfTheLadder() {
        final int top = LimiterLadder.feAt(LimiterLadder.MAX_NOTCH);
        for (int strength = 0; strength <= 15; strength++) {
            assertTrue(LimiterMode.ANALOG.apply(top, strength) >= 0,
                    "analog went negative at strength " + strength);
        }
        assertEquals(top, LimiterMode.ANALOG.apply(top, 15));
    }

    @Test
    @DisplayName("a signal outside 0 to 15 is clamped rather than trusted")
    void signalIsClamped() {
        assertEquals(4096, LimiterMode.ANALOG.apply(4096, 99));
        assertEquals(0, LimiterMode.ANALOG.apply(4096, -3));
        assertEquals(0, LimiterMode.SIGNAL_OPENS.apply(4096, -3));
    }

    @Test
    @DisplayName("byOrdinal is total, so a hostile packet cannot throw")
    void byOrdinalIsTotal() {
        assertEquals(LimiterMode.ALWAYS, LimiterMode.byOrdinal(-1));
        assertEquals(LimiterMode.ALWAYS, LimiterMode.byOrdinal(99));
        assertEquals(LimiterMode.ANALOG, LimiterMode.byOrdinal(LimiterMode.ANALOG.ordinal()));
    }

    @Test
    @DisplayName("next cycles through every mode and comes back")
    void nextCycles() {
        LimiterMode mode = LimiterMode.ALWAYS;
        for (int i = 0; i < LimiterMode.values().length; i++) {
            mode = mode.next();
        }
        assertEquals(LimiterMode.ALWAYS, mode);
    }
}
