package com.createstats.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastEngineTest {
    private static WeatherSnapshot snap(SimState sim, long dayTime) {
        return snap(sim, dayTime, 123456789L);
    }

    private static WeatherSnapshot snap(SimState sim, long dayTime, long seed) {
        return new WeatherSnapshot(sim, seed, dayTime, dayTime, (int) (dayTime / 24000L),
                0.8f, 0.4f, Condition.RAIN, 64, true, true);
    }

    @Test
    void alwaysReturnsFiveDays() {
        assertEquals(5, ForecastEngine.compute(
                snap(new SimState(0, 5000, 9000, false, false), 0L)).days().size());
    }

    @Test
    void aDimensionWithNoSkyForecastsNothing() {
        final WeatherSnapshot nether = new WeatherSnapshot(new SimState(0, 5000, 9000, false, false),
                1L, 0L, 0L, 0, 2.0f, 0.0f, Condition.NONE, 64, false, true);
        assertTrue(ForecastEngine.compute(nether).days().isEmpty());
        assertTrue(ForecastEngine.compute(nether).bands().isEmpty());
    }

    @Test
    void todayIsCertainWhenTheTimerCoversIt() {
        final Forecast f = ForecastEngine.compute(
                snap(new SimState(0, 40000, 30000, false, false), 0L));
        assertTrue(f.days().get(0).certain());
        assertEquals(Condition.CLEAR, f.days().get(0).condition());
        assertEquals(0, f.days().get(0).popPercent());
    }

    @Test
    void aMaximalClearSpellMakesTheWholeWeekCertain() {
        final Forecast f = ForecastEngine.compute(
                snap(new SimState(0, 180000, 180000, false, false), 0L));
        for (Forecast.DayCell d : f.days()) {
            assertTrue(d.certain(), "day " + d.dayIndex() + " should be certain");
            assertEquals(Condition.CLEAR, d.condition());
            assertEquals(0, d.popPercent());
        }
    }

    @Test
    void outlookDaysAreProbabilisticAndBounded() {
        final Forecast f = ForecastEngine.compute(
                snap(new SimState(0, 5000, 3000, false, false), 0L));
        final Forecast.DayCell far = f.days().get(4);
        assertFalse(far.certain());
        assertTrue(far.popPercent() >= 0 && far.popPercent() <= 100, "pop " + far.popPercent());
        assertEquals(0, far.popPercent() % 5, "quantised to 5 so it does not twitch");
    }

    @Test
    void isDeterministic() {
        final SimState sim = new SimState(0, 5000, 3000, false, false);
        assertEquals(ForecastEngine.compute(snap(sim, 5000L)),
                     ForecastEngine.compute(snap(sim, 5000L)));
    }

    @Test
    void differentWorldSeedsGiveDifferentOutlooks() {
        final SimState sim = new SimState(0, 5000, 3000, false, false);
        final Forecast a = ForecastEngine.compute(snap(sim, 5000L, 1L));
        final Forecast b = ForecastEngine.compute(snap(sim, 5000L, 999999L));
        boolean anyDifferent = false;
        for (int d = 1; d < 5; d++) {
            anyDifferent |= a.days().get(d).popPercent() != b.days().get(d).popPercent();
        }
        assertTrue(anyDifferent, "two world seeds produced an identical four day outlook");
    }

    @Test
    void aLongerTimerExtendsTheCertainWindow() {
        final int shortWindow = ForecastEngine.compute(
                snap(new SimState(0, 40000, 3000, false, false), 0L)).certainUntilTicks();
        final int longWindow = ForecastEngine.compute(
                snap(new SimState(0, 40000, 30000, false, false), 0L)).certainUntilTicks();
        assertTrue(longWindow > shortWindow, longWindow + " should exceed " + shortWindow);
    }

    @Test
    void bandsCoverTheStripWindowContiguously() {
        final Forecast f = ForecastEngine.compute(
                snap(new SimState(0, 5000, 3000, false, false), 0L));
        assertEquals(0, f.bands().get(0).startTick());
        for (int i = 1; i < f.bands().size(); i++) {
            assertEquals(f.bands().get(i - 1).endTick(), f.bands().get(i).startTick(),
                    "band " + i + " leaves a gap");
        }
        assertEquals(ForecastEngine.STRIP_TICKS, f.bands().get(f.bands().size() - 1).endTick());
    }

    @Test
    void rainyDaysAreCoolerThanClearOnes() {
        final Forecast wet = ForecastEngine.compute(
                snap(new SimState(0, 40000, 30000, false, true), 0L));
        final Forecast dry = ForecastEngine.compute(
                snap(new SimState(0, 40000, 30000, false, false), 0L));
        assertTrue(wet.days().get(0).highC() < dry.days().get(0).highC());
    }

    @Test
    void todaysCellShrinksAsTheDayRunsOut() {
        final SimState sim = new SimState(0, 40000, 30000, false, false);
        assertEquals(5, ForecastEngine.compute(snap(sim, 23900L)).days().size(),
                "a panel read in the last five seconds of a day still has five cells");
    }
}
