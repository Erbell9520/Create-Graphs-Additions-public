package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduSupplySurveyRelayTest {
    private static int[] flat(int v) {
        final int[] out = new int[PduSupplySurvey.FORECAST_OFFSETS.length];
        java.util.Arrays.fill(out, v);
        return out;
    }

    @Test
    @DisplayName("a meter relaying a falling roof gives the board a drop, where before it gave none")
    void aRelayedRoofReachesTheAdmitBudget() {
        final PduSupplySurvey.Builder b = new PduSupplySurvey.Builder();
        b.addSource(1L, 800);

        b.addForecast(1L, new int[] {800, 700, 550, 400, 300}, false, 800);
        final PduSupplySurvey.Result r = b.build();
        assertEquals(500, r.expectedDropFePerTick());
        assertEquals(800, r.weatherSensitiveNow());

        final PduSupplySurvey.Result before = new PduSupplySurvey.Builder().addSource(1L, 800).build();
        assertEquals(0, before.expectedDropFePerTick());
        assertEquals(0, before.weatherSensitiveNow());
    }

    @Test
    @DisplayName("a relay with nothing forecastable behind it changes nothing (INV-22)")
    void aFlatRelayChangesNothing() {
        final PduSupplySurvey.Builder b = new PduSupplySurvey.Builder();
        b.addSource(1L, 600);
        b.addForecast(1L, flat(600), false, 0);
        final PduSupplySurvey.Result r = b.build();
        assertEquals(0, r.expectedDropFePerTick());
        assertEquals(0, r.weatherSensitiveNow());
        assertEquals(600, r.knownGeneration());
    }

    @Test
    @DisplayName("a relay can never claim more of the roof than it was priced at")
    void theRelayedShareIsHeldToThePrice() {
        final PduSupplySurvey.Builder b = new PduSupplySurvey.Builder();
        b.addSource(1L, 250);
        b.addForecast(1L, flat(250), false, 900);
        assertEquals(250, b.build().weatherSensitiveNow());
    }

    @Test
    @DisplayName("a relayed share sums beside a panel's own, and a sensitive source's relayed figure is ignored")
    void relayedAndOwnSharesSum() {
        final PduSupplySurvey.Builder b = new PduSupplySurvey.Builder();
        b.addSource(1L, 300);
        b.addForecast(1L, flat(300), true, 999);
        b.addSource(2L, 500);
        b.addForecast(2L, flat(500), false, 200);
        final PduSupplySurvey.Result r = b.build();
        assertEquals(300 + 200, r.weatherSensitiveNow());
        assertTrue(r.weatherSensitiveNow() <= r.knownGeneration());
    }
}
