package com.createstats.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateModelTest {
    private static final float MEAN_POINT = 0.30f;

    private static final SimState CLEAR = new SimState(0, 5000, 9000, false, false);
    private static final SimState RAIN = new SimState(0, 5000, 9000, false, true);
    private static final SimState STORM = new SimState(0, 5000, 9000, true, true);

    private static WeatherSnapshot biome(float temp, float downfall, long dayTime, int y,
                                         SimState sim, Condition local) {
        return new WeatherSnapshot(sim, 1L, dayTime, dayTime, (int) (dayTime / 24000L),
                temp, downfall, local, y, true, true);
    }

    private static float mean(float temp, float downfall) {
        return ClimateModel.temperatureAt(biome(temp, downfall, 0L, 64, CLEAR, Condition.CLEAR),
                MEAN_POINT, 0f);
    }

    @Test
    void minecraftSnowLineIsZeroCelsius() {
        assertEquals(0.0f, mean(0.15f, 0.4f), 0.001f);
    }

    @Test
    void biomeAnchorsAreBelievable() {
        assertEquals(12.35f, mean(0.8f, 0.4f), 0.2f);
        assertEquals(35.15f, mean(2.0f, 0.0f), 0.2f);
        assertEquals(-2.85f, mean(0.0f, 0.4f), 0.2f);
        assertEquals(-16.15f, mean(-0.7f, 0.4f), 0.2f);
    }

    @Test
    void peakIsAfterNoon() {
        final WeatherSnapshot s = biome(0.8f, 0.4f, 0L, 64, CLEAR, Condition.CLEAR);
        float best = -999f;
        float bestF = 0f;
        for (int i = 0; i < 1000; i++) {
            final float f = i / 1000.0f;
            final float t = ClimateModel.temperatureAt(s, f, 0f);
            if (t > best) {
                best = t;
                bestF = f;
            }
        }
        assertTrue(bestF > 0.5f && bestF < 0.65f, "peak at dayFraction " + bestF);
    }

    @Test
    void desertSwingsHarderThanJungle() {
        assertTrue(swing(2.0f, 0.0f) > swing(0.95f, 0.9f));
    }

    private static float swing(float temp, float downfall) {
        final WeatherSnapshot s = biome(temp, downfall, 0L, 64, CLEAR, Condition.CLEAR);
        return ClimateModel.temperatureAt(s, 0.55f, 0f) - ClimateModel.temperatureAt(s, 0.05f, 0f);
    }

    @Test
    void rainCoolsAndStormCoolsMore() {
        final float dry = ClimateModel.temperatureC(
                biome(0.8f, 0.4f, 0L, 64, CLEAR, Condition.CLEAR), ClimateModel.wetnessOf(CLEAR));
        final float wet = ClimateModel.temperatureC(
                biome(0.8f, 0.4f, 0L, 64, RAIN, Condition.RAIN), ClimateModel.wetnessOf(RAIN));
        final float storm = ClimateModel.temperatureC(
                biome(0.8f, 0.4f, 0L, 64, STORM, Condition.RAIN), ClimateModel.wetnessOf(STORM));
        assertTrue(wet < dry, wet + " should be under " + dry);
        assertTrue(storm < wet, storm + " should be under " + wet);
    }

    @Test
    void heightMakesItColder() {
        assertTrue(ClimateModel.temperatureAt(
                        biome(0.8f, 0.4f, 0L, 200, CLEAR, Condition.CLEAR), MEAN_POINT, 0f)
                 < ClimateModel.temperatureAt(
                        biome(0.8f, 0.4f, 0L, 64, CLEAR, Condition.CLEAR), MEAN_POINT, 0f));
    }

    @Test
    void neverPrintsAboveZeroWhileSnowing() {
        final WeatherSnapshot s = biome(0.16f, 0.4f, 6000L, 64, RAIN, Condition.SNOW);
        assertTrue(ClimateModel.temperatureC(s, 1.0f) <= 0.0f);
    }

    @Test
    void neverPrintsBelowZeroWhileRaining() {
        final WeatherSnapshot s = biome(0.15f, 0.4f, 18000L, 64, RAIN, Condition.RAIN);
        assertTrue(ClimateModel.temperatureC(s, 1.0f) >= 1.0f);
    }

    @Test
    void humidityRisesWithDownfallAndWithRain() {
        final WeatherSnapshot desert = biome(2.0f, 0.0f, 0L, 64, CLEAR, Condition.CLEAR);
        final WeatherSnapshot jungle = biome(0.95f, 0.9f, 0L, 64, CLEAR, Condition.CLEAR);
        assertTrue(ClimateModel.humidity(jungle, 0f) > ClimateModel.humidity(desert, 0f));
        assertTrue(ClimateModel.humidity(desert, 1f) > ClimateModel.humidity(desert, 0f));
    }

    @Test
    void humidityStaysOnItsOwnScale() {
        for (float downfall : new float[]{0f, 0.5f, 1f}) {
            for (float wet : new float[]{0f, 0.7f, 1f}) {
                for (long dayTime : new long[]{0L, 6000L, 12000L, 18000L}) {
                    final float h = ClimateModel.humidity(
                            biome(0.8f, downfall, dayTime, 64, STORM, Condition.RAIN), wet);
                    assertTrue(h >= 5f && h <= 100f, "humidity off scale: " + h);
                }
            }
        }
    }

    @Test
    void fahrenheitConverts() {
        assertEquals(32.0f, ClimateModel.toFahrenheit(0f), 0.001f);
        assertEquals(68.0f, ClimateModel.toFahrenheit(20f), 0.001f);
    }
}
