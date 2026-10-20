package com.createstats.gui.weather;

import com.createstats.weather.Condition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherThemeContrastTest {
    private static final int STEPS = 200;

    private static final float WORST_CASE = 4.2f;

    @Test
    void inkIsReadableAtEveryHourOfEveryCondition() {
        forEachPalette((condition, nightness, p) -> {
            assertTrue(WeatherTheme.contrast(p.ink(), p.card()) >= WORST_CASE,
                    () -> describe("ink on card", condition, nightness, p.ink(), p.card()));
            assertTrue(WeatherTheme.contrast(p.ink(), p.ground()) >= WORST_CASE,
                    () -> describe("ink on ground", condition, nightness, p.ink(), p.ground()));
        });
    }

    @Test
    void dimInkIsReadableAtEveryHourOfEveryCondition() {
        forEachPalette((condition, nightness, p) -> {
            assertTrue(WeatherTheme.contrast(p.inkDim(), p.card()) >= WORST_CASE,
                    () -> describe("inkDim on card", condition, nightness, p.inkDim(), p.card()));
            assertTrue(WeatherTheme.contrast(p.inkDim(), p.ground()) >= WORST_CASE,
                    () -> describe("inkDim on ground", condition, nightness, p.inkDim(), p.ground()));
        });
    }

    @Test
    void theFullFloorsHoldOutsideTheSwap() {
        forEachPalette((condition, nightness, p) -> {
            final float mix = WeatherTheme.surfaceMix(nightness);
            if (mix > 0f && mix < 1f) {
                return;
            }
            assertTrue(WeatherTheme.contrast(p.ink(), p.card()) >= WeatherTheme.INK_FLOOR,
                    () -> describe("ink on card", condition, nightness, p.ink(), p.card()));
            assertTrue(WeatherTheme.contrast(p.inkDim(), p.card()) >= WeatherTheme.DIM_FLOOR,
                    () -> describe("inkDim on card", condition, nightness, p.inkDim(), p.card()));
        });
    }

    @Test
    void conditionCrossfadesNeverGoBlank() {
        for (Condition from : Condition.values()) {
            for (Condition to : Condition.values()) {
                for (float nightness : new float[] {0f, 0.5f, 1f}) {
                    final WeatherTheme.Palette a = WeatherTheme.paletteFor(from, nightness);
                    final WeatherTheme.Palette b = WeatherTheme.paletteFor(to, nightness);
                    for (int i = 0; i <= STEPS; i++) {
                        final float t = i / (float) STEPS;
                        final WeatherTheme.Palette p =
                                WeatherTheme.guard(WeatherTheme.crossfade(a, b, t));
                        assertTrue(WeatherTheme.contrast(p.ink(), p.card()) >= WORST_CASE,
                                () -> from + " to " + to + " at mix " + t + ": ink "
                                        + hex(p.ink()) + " on card " + hex(p.card()) + " is "
                                        + WeatherTheme.contrast(p.ink(), p.card()) + ":1");
                    }
                }
            }
        }
    }

    @Test
    void theGuardLeavesAuthoredPalettesAlone() {
        for (WeatherTheme.Palette p : new WeatherTheme.Palette[] {
                WeatherTheme.CLEAR_DAY, WeatherTheme.CLEAR_NIGHT, WeatherTheme.RAIN,
                WeatherTheme.STORM, WeatherTheme.SNOW, WeatherTheme.DRY, WeatherTheme.NO_SKY}) {
            assertEquals(p, WeatherTheme.guard(p),
                    "the guard repainted an authored palette; a floor is now above what it achieves");
        }
    }

    @Test
    void chromeIsFullySwappedOutsideTheMiddleEighth() {
        assertEquals(0f, WeatherTheme.surfaceMix(0.46f), 1e-6f);
        assertEquals(1f, WeatherTheme.surfaceMix(0.54f), 1e-6f);
        assertEquals(0.5f, WeatherTheme.surfaceMix(0.5f), 1e-6f);

        final WeatherTheme.Palette dusk = WeatherTheme.paletteFor(Condition.CLEAR, 0.4f);
        assertEquals(WeatherTheme.CLEAR_DAY.card(), dusk.card(),
                "chrome should still be fully day at nightness 0.4");
        assertTrue(dusk.skyTop() != WeatherTheme.CLEAR_DAY.skyTop(),
                "the sky should already be sliding at nightness 0.4");
    }

    @Test
    void readableOnFindsTheShortestPushAndStopsWhenAlreadyClear() {
        final int alreadyFine = WeatherTheme.readableOn(0xFF000000, 0xFFFFFFFF, 9f);
        assertEquals(0xFF000000, alreadyFine, "an ink that already clears the floor must not move");

        final int forced = WeatherTheme.readableOn(0xFF8B8E95, 0xFF8B8E95, 9f);
        assertTrue(WeatherTheme.contrast(forced, 0xFF8B8E95) > 5f,
                "an impossible floor should still yield the most readable colour available");
    }

    private interface Check {
        void run(Condition condition, float nightness, WeatherTheme.Palette palette);
    }

    private static void forEachPalette(Check check) {
        for (Condition condition : Condition.values()) {
            for (int i = 0; i <= STEPS; i++) {
                final float nightness = i / (float) STEPS;
                check.run(condition, nightness, WeatherTheme.paletteFor(condition, nightness));
            }
        }
    }

    private static String describe(String what, Condition condition, float nightness,
                                   int ink, int surface) {
        return condition + " at nightness " + nightness + ": " + what + " is "
                + hex(ink) + " on " + hex(surface) + ", only "
                + WeatherTheme.contrast(ink, surface) + ":1";
    }

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}
