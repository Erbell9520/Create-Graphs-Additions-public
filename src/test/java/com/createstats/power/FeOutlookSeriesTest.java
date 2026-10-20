package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeOutlookSeriesTest {
    private static final int DAY = FeSupplyMemory.DAY_TICKS;
    private static final int PEAK = 400;

    private static FeSupplyMemory warmSolar() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        for (long t = 0; t < 4L * DAY; t += 20) {
            memory.observe(t, (int) Math.round(PEAK * SolarCurve.sunFactor(t)));
        }
        return memory;
    }

    @Test
    @DisplayName("a fresh reading is run down by its age, exactly as the vanilla timer runs")
    void aFreshReadingRunsDown() {
        assertEquals(4_000, FeSupplyOutlook.liveCountdown(4_000, 1_000, 1_000));
        assertEquals(3_980, FeSupplyOutlook.liveCountdown(4_000, 1_000, 1_020));
    }

    @Test
    @DisplayName("a reading older than the trust window is not believed: the board falls back to steady weather")
    void aStaleReadingIsUnknown() {
        final int trust = FeSupplyOutlook.COUNTDOWN_TRUST_TICKS;
        assertEquals(4_000 - trust, FeSupplyOutlook.liveCountdown(4_000, 0, trust));
        assertEquals(FeSupplyOutlook.UNKNOWN_CHANGE, FeSupplyOutlook.liveCountdown(4_000, 0, trust + 1));

        assertEquals(FeSupplyOutlook.UNKNOWN_CHANGE, FeSupplyOutlook.liveCountdown(4_000, 0, 12_000));
    }

    @Test
    @DisplayName("a countdown that has run out is unknown, not zero: the flip already happened")
    void anExpiredCountdownIsUnknown() {
        assertEquals(FeSupplyOutlook.UNKNOWN_CHANGE, FeSupplyOutlook.liveCountdown(100, 0, 100));
        assertEquals(FeSupplyOutlook.UNKNOWN_CHANGE, FeSupplyOutlook.liveCountdown(100, 0, 150));
        assertEquals(1, FeSupplyOutlook.liveCountdown(100, 0, 99));
    }

    @Test
    @DisplayName("no reading, or a reading from the future, is unknown")
    void noReadingIsUnknown() {
        assertEquals(FeSupplyOutlook.UNKNOWN_CHANGE, FeSupplyOutlook.liveCountdown(-1, 0, 0));
        assertEquals(FeSupplyOutlook.UNKNOWN_CHANGE, FeSupplyOutlook.liveCountdown(4_000, 500, 100));
    }

    @Test
    @DisplayName("a cold board draws no curve, for the reason it projects no shortfall")
    void aColdBoardHasNoCurve() {
        assertSame(FeSupplyOutlook.Series.NONE, FeSupplyOutlook.series(new FeSupplyMemory(), null, 0, 0,
                DAY, 500, FeSupplyOutlook.Weather.steady(SolarCurve.Sky.CLEAR)));
    }

    @Test
    @DisplayName("the curve is the projection's own arithmetic: integrating it gives the projected total")
    void theCurveIsTheProjection() {
        final FeSupplyMemory memory = warmSolar();
        final FeSupplyOutlook.Weather sky = FeSupplyOutlook.Weather.steady(SolarCurve.Sky.CLEAR);
        final int step = FeSupplyOutlook.STEP_TICKS;
        final FeSupplyOutlook.Series series = FeSupplyOutlook.series(memory, memory, 50, 3_000, 12_000, step, sky);
        final FeSupplyOutlook.Result result = FeSupplyOutlook.project(memory, memory, 50, 3_000, 12_000, sky, 0);
        long integrated = 0;
        for (int r : series.rate()) {
            if (r > 0) {
                integrated += (long) r * step;
            }
        }
        assertEquals(result.projectedFe(), integrated);
        assertEquals(48, series.steps());
    }

    @Test
    @DisplayName("rain lowers the weather-sensitive curve and leaves the clear-sky curve where it was")
    void rainShowsAsTheGapToClear() {
        final FeSupplyMemory memory = warmSolar();
        final FeSupplyOutlook.Series wet = FeSupplyOutlook.series(memory, memory, 0, 3_000, DAY, 500,
                FeSupplyOutlook.Weather.steady(SolarCurve.Sky.RAIN));

        assertEquals((int) Math.floor(wet.clear()[6] * SolarCurve.Sky.RAIN.factor()), wet.rate()[6]);
        assertTrue(wet.clear()[6] > wet.rate()[6]);
    }

    @Test
    @DisplayName("a dynamo board's curve does not move in the rain, because only the solar share is discounted")
    void aDynamoIgnoresRain() {
        final FeSupplyMemory dynamo = new FeSupplyMemory();
        for (long t = 0; t < 2L * DAY; t += 20) {
            dynamo.observe(t, 360);
        }
        final FeSupplyOutlook.Series wet = FeSupplyOutlook.series(dynamo, new FeSupplyMemory(), 0, 0, DAY, 500,
                FeSupplyOutlook.Weather.steady(SolarCurve.Sky.THUNDER));
        for (int i = 0; i < wet.steps(); i++) {
            assertEquals(360, wet.rate()[i]);
            assertEquals(360, wet.clear()[i]);
        }
    }

    @Test
    @DisplayName("hours the board has not watched are marked cold, so the chart can say so")
    void coldHoursAreMarked() {
        final FeSupplyMemory memory = new FeSupplyMemory();

        for (int day = 0; day < 2; day++) {
            for (long t = 0; t < DAY / 2; t += 20) {
                memory.observe((long) day * DAY + t, 300);
            }
        }
        assertTrue(memory.isWarm());
        final FeSupplyOutlook.Series series = FeSupplyOutlook.series(memory, null, 0, 0, DAY, 500,
                FeSupplyOutlook.Weather.steady(SolarCurve.Sky.CLEAR));
        assertFalse(series.isCold(0));
        assertTrue(series.isCold(30), "step 30 is day time 15,000, which was never watched");
        assertEquals(0, series.rate()[30], "and it contributes nothing, the projection's own choice");
    }

    @Test
    @DisplayName("the series never exceeds the cold mask's width")
    void theSeriesIsBounded() {
        final FeSupplyOutlook.Series series = FeSupplyOutlook.series(warmSolar(), null, 0, 0, DAY, 100,
                FeSupplyOutlook.Weather.steady(SolarCurve.Sky.CLEAR));
        assertEquals(FeSupplyOutlook.MAX_SERIES_STEPS, series.steps());
    }
}
