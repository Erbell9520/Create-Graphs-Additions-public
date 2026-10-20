package com.createstats.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherSnapshotTest {
    private static WeatherSnapshot at(long dayTime, SimState sim, Condition local, boolean sky) {
        return new WeatherSnapshot(sim, 42L, dayTime, dayTime, (int) (dayTime / 24000L),
                0.8f, 0.4f, local, 64, sky, true);
    }

    private static final SimState CALM = new SimState(0, 1, 1, false, false);

    @Test
    void dayFractionPutsMidnightAtZeroAndNoonAtHalf() {
        assertEquals(0.25f, at(0L, CALM, Condition.CLEAR, true).dayFraction(), 0.0001f);
        assertEquals(0.5f, at(6000L, CALM, Condition.CLEAR, true).dayFraction(), 0.0001f);
        assertEquals(0.0f, at(18000L, CALM, Condition.CLEAR, true).dayFraction(), 0.0001f);
    }

    @Test
    void conditionIsTheLocalReadingOfTheGlobalState() {
        final SimState storm = new SimState(0, 500, 900, true, true);
        assertEquals(Condition.STORM, at(0L, storm, Condition.RAIN, true).condition());
        assertEquals(Condition.SNOW, at(0L, storm, Condition.SNOW, true).condition());
        assertEquals(Condition.DRY, at(0L, storm, Condition.DRY, true).condition());
        assertEquals(Condition.NONE, at(0L, storm, Condition.RAIN, false).condition());

        final SimState dryThunder = new SimState(0, 500, 900, true, false);
        assertEquals(Condition.CLEAR, at(0L, dryThunder, Condition.RAIN, true).condition(),
                "thunder with no rain renders nothing, so the panel must not say storm");
    }
}
