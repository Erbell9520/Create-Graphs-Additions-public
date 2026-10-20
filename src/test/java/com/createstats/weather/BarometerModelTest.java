package com.createstats.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BarometerModelTest {
    private static WeatherSnapshot snap(SimState sim, int y) {
        return new WeatherSnapshot(sim, 5L, 0L, 0L, 0, 0.8f, 0.4f, Condition.RAIN, y, true, true);
    }

    private static float p(SimState sim, int y) {
        final WeatherSnapshot s = snap(sim, y);
        return BarometerModel.pressure(s, ForecastEngine.compute(s));
    }

    @Test
    void neverRisesAsRainApproaches() {
        float previous = Float.MAX_VALUE;
        for (int ticks : new int[]{40000, 20000, 12000, 8000, 6000, 4000, 2000, 500}) {
            final float now = p(new SimState(0, 200000, ticks, false, false), 64);
            assertTrue(now <= previous + 0.001f,
                    "pressure rose as rain got closer, at rainTime " + ticks);
            previous = now;
        }
    }

    @Test
    void movesMeaningfullyInsideTheLookahead() {
        assertTrue(p(new SimState(0, 200000, 500, false, false), 64)
                 < p(new SimState(0, 200000, 6000, false, false), 64) - 5f,
                "the needle should visibly fall over the last few hours, not twitch at the end");
    }

    @Test
    void isLowerInARainThanInTheClear() {
        assertTrue(p(new SimState(0, 200000, 20000, false, true), 64)
                 < p(new SimState(0, 200000, 200000, false, false), 64));
    }

    @Test
    void isLowestInAStorm() {
        assertTrue(p(new SimState(0, 20000, 20000, true, true), 64)
                 < p(new SimState(0, 200000, 20000, false, true), 64));
    }

    @Test
    void fallsWithAltitude() {
        final SimState clear = new SimState(0, 200000, 200000, false, false);
        assertTrue(p(clear, 200) < p(clear, 64));
        assertTrue(p(clear, 64) < p(clear, -50));
    }

    @Test
    void staysOnTheDialFace() {
        for (int y : new int[]{-60, 64, 319}) {
            for (SimState sim : new SimState[]{
                    new SimState(0, 200000, 200000, false, false),
                    new SimState(0, 1, 1, true, true),
                    new SimState(0, 20000, 20000, true, true),
                    new SimState(500, 1, 1, false, false)}) {
                final float v = p(sim, y);
                assertTrue(v >= BarometerModel.MIN && v <= BarometerModel.MAX,
                        "off the face: " + v + " at y " + y);
            }
        }
    }

    @Test
    void aDimensionWithNoSkyReadsNothing() {
        final WeatherSnapshot nether = new WeatherSnapshot(new SimState(0, 1, 1, false, false),
                1L, 0L, 0L, 0, 2.0f, 0.0f, Condition.NONE, 64, false, true);
        assertTrue(Float.isNaN(BarometerModel.pressure(nether, ForecastEngine.compute(nether))));
    }
}
