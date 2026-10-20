package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicWaveTest {
    @Test
    @DisplayName("the front runs at 32 blocks a second, 1.6 a tick, so a radius-64 scan takes 40 ticks")
    void theFront() {
        assertEquals(1.6, SeismicWave.BLOCKS_PER_TICK, 1e-12);
        assertEquals(0.0, SeismicWave.frontAt(0));
        assertEquals(32.0, SeismicWave.frontAt(20), 1e-9);
        assertEquals(40, SeismicWave.scanTicks(64));
        assertEquals(10, SeismicWave.scanTicks(16));
        assertEquals(0, SeismicWave.scanTicks(0));
    }

    @Test
    @DisplayName("an echo from d blocks is heard 2d / v later: the far edge of a radius-64 survey at 4 seconds")
    void echoes() {
        assertEquals(80.0, SeismicWave.echoTicks(64.0), 1e-9);
        assertEquals(80, SeismicWave.echoTick(64.0));
        assertEquals(40, SeismicWave.echoTick(32.0));
        assertEquals(2, SeismicWave.echoTick(1.5), "a block just under the plate answers on the second tick");
        assertEquals(1, SeismicWave.echoTick(0.5));
        for (double d = 0.5; d <= 64.0; d += 0.5) {
            final int at = SeismicWave.echoTick(d);

            assertTrue(at >= SeismicWave.echoTicks(d) - 1e-9 && at < SeismicWave.echoTicks(d) + 1.0, "distance " + d);

            assertTrue(SeismicWave.frontAt(at) >= d, "distance " + d);
        }
        assertEquals(80, SeismicWave.listenTicks(64));
    }
}
