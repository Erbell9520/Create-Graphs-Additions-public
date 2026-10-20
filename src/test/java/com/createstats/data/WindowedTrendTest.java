package com.createstats.data;

import org.junit.jupiter.api.Test;

import com.createstats.data.WindowedTrend.Window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowedTrendTest {
    private static final long SECOND = 20L;

    @Test
    void eachTierSamplesAtItsOwnInterval() {
        final WindowedTrend trend = new WindowedTrend();

        for (int second = 0; second <= 600; second++) {
            trend.record(second, second * SECOND);
        }

        assertEquals(WindowedTrend.TIER_SAMPLES, trend.sampleCount(Window.MINUTE));

        assertEquals(WindowedTrend.TIER_SAMPLES, trend.sampleCount(Window.FIVE_MINUTES));

        assertEquals(11, trend.sampleCount(Window.HOUR));
        assertTrue(trend.complete(Window.MINUTE));
        assertFalse(trend.complete(Window.HOUR));
    }

    @Test
    void offersInsideTheIntervalAreDropped() {
        final WindowedTrend trend = new WindowedTrend();
        for (int tick = 0; tick < 200; tick++) {
            trend.record(tick, tick);
        }

        assertEquals(10, trend.sampleCount(Window.MINUTE));

        assertEquals(199L, trend.latest());
    }

    @Test
    void coverageIsMeasuredAndNotAssumed() {
        final WindowedTrend trend = new WindowedTrend();
        for (int second = 0; second <= 240; second++) {
            trend.record(100L, second * SECOND);
        }

        assertEquals(240, trend.coverageSeconds(Window.HOUR));

        assertEquals(59, trend.coverageSeconds(Window.MINUTE));
    }

    @Test
    void perMinuteUsesRealElapsedTimeNotTheNominalWindow() {
        final WindowedTrend trend = new WindowedTrend();

        for (int second = 0; second <= 240; second++) {
            trend.record(1000L + second / 6L, second * SECOND);
        }

        assertEquals(10.0, trend.perMinute(Window.HOUR), 0.6);
        assertEquals(40L, trend.delta(Window.HOUR));
    }

    @Test
    void standardDeviationIsZeroForAFlatStock() {
        final WindowedTrend trend = new WindowedTrend();
        for (int second = 0; second <= 120; second++) {
            trend.record(4096L, second * SECOND);
        }
        assertEquals(0.0, trend.stdDev(Window.MINUTE), 1.0e-9);
        assertEquals(0.0, trend.relativeStdDev(Window.MINUTE), 1.0e-9);
        assertEquals(0L, trend.delta(Window.MINUTE));
    }

    @Test
    void standardDeviationIsThePopulationSpread() {
        final WindowedTrend trend = new WindowedTrend();

        for (int second = 0; second < 60; second++) {
            trend.record(second % 2 == 0 ? 100L : 200L, second * SECOND);
        }
        assertEquals(150.0, trend.mean(Window.MINUTE), 1.0e-6);
        assertEquals(50.0, trend.stdDev(Window.MINUTE), 1.0e-6);
        assertEquals(50.0 / 150.0, trend.relativeStdDev(Window.MINUTE), 1.0e-6);
    }

    @Test
    void countsPastFloatPrecisionSurviveExactly() {
        final long huge = 16_777_217L;
        final WindowedTrend trend = new WindowedTrend();
        trend.record(huge, 0L);
        trend.record(huge + 1L, SECOND);
        assertEquals(huge + 1L, trend.latest());
        assertEquals(1L, trend.delta(Window.MINUTE));
        assertEquals(huge, trend.min(Window.MINUTE));
        assertEquals(huge + 1L, trend.max(Window.MINUTE));
    }

    @Test
    void ringWrapsOldestFirst() {
        final WindowedTrend trend = new WindowedTrend();
        for (int second = 0; second < 90; second++) {
            trend.record(second, second * SECOND);
        }
        final long[] series = trend.series(Window.MINUTE);
        assertEquals(WindowedTrend.TIER_SAMPLES, series.length);

        assertEquals(30L, series[0]);
        assertEquals(89L, series[series.length - 1]);
    }

    @Test
    void aClockThatGoesBackwardsDoesNotStallTheTier() {
        final WindowedTrend trend = new WindowedTrend();
        for (int second = 0; second < 10; second++) {
            trend.record(second, 100_000L + second * SECOND);
        }
        final int before = trend.sampleCount(Window.MINUTE);

        trend.record(99L, 20L);
        trend.record(100L, 40L);
        assertTrue(trend.sampleCount(Window.MINUTE) > before);
        assertEquals(100L, trend.latest());
    }

    @Test
    void roundTripsThroughNbt() {
        final WindowedTrend trend = new WindowedTrend();
        for (int second = 0; second <= 300; second++) {
            trend.record(500L + (second % 7) * 1000L, second * SECOND);
        }
        final WindowedTrend loaded = WindowedTrend.load(trend.save());
        for (final Window w : Window.values()) {
            assertEquals(trend.sampleCount(w), loaded.sampleCount(w), w.name());
            assertEquals(trend.mean(w), loaded.mean(w), 1.0e-6, w.name());
            assertEquals(trend.stdDev(w), loaded.stdDev(w), 1.0e-6, w.name());
            assertEquals(trend.delta(w), loaded.delta(w), w.name());
        }
        assertEquals(trend.latest(), loaded.latest());
    }

    @Test
    void emptyTrendAnswersWithoutThrowing() {
        final WindowedTrend trend = new WindowedTrend();
        for (final Window w : Window.values()) {
            assertEquals(0, trend.sampleCount(w));
            assertEquals(0, trend.coverageSeconds(w));
            assertEquals(0.0, trend.mean(w), 1.0e-9);
            assertEquals(0.0, trend.stdDev(w), 1.0e-9);
            assertEquals(0.0, trend.perMinute(w), 1.0e-9);
            assertEquals(0L, trend.delta(w));
            assertEquals(0, trend.plot(w).length);
        }
        assertFalse(trend.seeded());
    }
}
