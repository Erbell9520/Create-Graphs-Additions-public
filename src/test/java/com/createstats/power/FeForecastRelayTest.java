package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeForecastRelayTest {
    private static FeForecastRelay.Upstream upstream(int... at) {
        return new FeForecastRelay.Upstream(at, 0);
    }

    @Test
    @DisplayName("behind nothing that can forecast, a box answers its price at every offset (INV-22)")
    void nothingBehindChangesNothing() {
        assertEquals(700, FeForecastRelay.through(700, 0, 0));
        assertEquals(700, FeForecastRelay.through(700, 900, 900), "a flat curve relays no drop");
        assertEquals(0, FeForecastRelay.weatherShareThrough(700, 0, 0));
        assertEquals(500, FeForecastRelay.export(500, 0));
        assertFalse(FeForecastRelay.Upstream.NONE.isPresent());
        assertEquals(0, FeForecastRelay.Upstream.NONE.at(500));
    }

    @Test
    @DisplayName("a roof feeding a meter wholly: the meter loses the whole of the roof's fall")
    void aMeterPassingTheWholeRoofLosesTheWholeDrop() {
        assertEquals(400, FeForecastRelay.through(1_000, 1_000, 400));
        assertEquals(0, FeForecastRelay.through(1_000, 1_000, 0), "dusk takes everything");
    }

    @Test
    @DisplayName("output nobody was taking goes first: a meter passing 100 of a 1,000 roof keeps its 100")
    void headroomAbsorbsTheDropFirst() {
        assertEquals(100, FeForecastRelay.through(100, 1_000, 500),
                "the roof still makes five times what the line asks for");
        assertEquals(100, FeForecastRelay.through(100, 1_000, 100), "exactly enough is still enough");
        assertEquals(60, FeForecastRelay.through(100, 1_000, 60), "below the line's ask, the line feels it");

        assertTrue(FeForecastRelay.through(100, 1_000, 500) > Math.max(0, 100 - (1_000 - 500)));
    }

    @Test
    @DisplayName("FE from sources that cannot forecast gives no headroom, so the whole drop is taken, and no more")
    void unforecastSupplyIsConservativeButBounded() {
        assertEquals(600, FeForecastRelay.through(800, 300, 100), "lost exactly the roof's own fall of 200");
        assertEquals(500, FeForecastRelay.through(800, 300, 0), "never more than the forecasting sources make");
    }

    @Test
    @DisplayName("a rising curve never makes a box pass more than the line is asking for")
    void aRisingCurveIsNotAPromise() {
        assertEquals(300, FeForecastRelay.through(300, 500, 2_000));
    }

    @Test
    @DisplayName("negative inputs are clamped rather than subtracted")
    void negativesClamp() {
        assertEquals(0, FeForecastRelay.through(-50, 100, 0));
        assertEquals(100, FeForecastRelay.through(100, -1, -1));
        assertEquals(0, FeForecastRelay.export(-5, 10));
        assertEquals(10, FeForecastRelay.export(10, -5));
    }

    @Test
    @DisplayName("the weather share is exactly the drop the box would see if the roof went dark")
    void theShareAndTheDropAgree() {
        assertEquals(300, FeForecastRelay.weatherShareThrough(800, 1_000, 500));
        assertEquals(800 - FeForecastRelay.through(800, 1_000, 1_000 - 500),
                FeForecastRelay.weatherShareThrough(800, 1_000, 500));

        assertEquals(100, FeForecastRelay.weatherShareThrough(100, 1_000, 1_000));

        assertEquals(0, FeForecastRelay.weatherShareThrough(400, 500, 0));
    }

    @Test
    @DisplayName("a board's export is its spare, so the supply's drop reaches it whole (KI-88)")
    void theExportLosesTheWholeDrop() {
        assertEquals(200, FeForecastRelay.export(500, 300));
        assertEquals(0, FeForecastRelay.export(500, 900), "a board whose supply falls stops exporting first");
        assertEquals(300, FeForecastRelay.exportWeatherShare(300, 1_000));
        assertEquals(120, FeForecastRelay.exportWeatherShare(300, 120));
    }

    @Test
    @DisplayName("an upstream sample reads its offsets, interpolates between them and reports its floor")
    void upstreamSample() {
        final FeForecastRelay.Upstream up = upstream(1_000, 900, 700, 600, 400);
        assertTrue(up.isPresent());
        assertEquals(1_000, up.now());
        assertEquals(1_000, up.at(0));
        assertEquals(700, up.at(500));
        assertEquals(800, up.at(375), "halfway between 250 and 500");
        assertEquals(400, up.at(5_000), "flat past the last offset");
        assertEquals(400, up.floor());
        assertFalse(upstream(1, 2).isPresent(), "a malformed sample is not a forecast");
    }
}
