package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicTraceTest {
    private static final double IMPACT = 5_000.0;

    private static double peak(double from, double to, double strength, int[] echoes) {
        double m = 0;
        for (double t = from; t < to; t += 0.05) {
            m = Math.max(m, Math.abs(SeismicTrace.at(IMPACT + t, IMPACT, strength, echoes)));
        }
        return m;
    }

    @Test
    @DisplayName("before the blow the pen only hums, and the hum is small and repeatable")
    void hum() {
        final double quiet = peak(-200, -1, 0.9, new int[0]);
        assertTrue(quiet <= SeismicTrace.HUM + 1e-9 && quiet > 0.0, "hum " + quiet);
        assertEquals(SeismicTrace.at(4_962.7, IMPACT, 0.9, null), SeismicTrace.at(4_962.7, IMPACT, 0.9, null));
    }

    @Test
    @DisplayName("the ink already on the paper stays put when a new blow lands: the hum is the clock's, not the blow's")
    void inkDoesNotMove() {
        for (double when = 4_800.0; when < 4_999.0; when += 0.37) {
            assertEquals(SeismicTrace.at(when, 0.0, 0.0, null), SeismicTrace.at(when, IMPACT, 1.0, new int[] {3, 800}),
                    "the paper before the blow, at " + when);
        }
    }

    @Test
    @DisplayName("the blow throws the pen hard; each echo spikes it at its own tick and the paper is quiet between")
    void spikes() {
        final int[] echoes = {14, 500, 41, 700};
        final double strength = SeismicTrace.blowStrength(64);
        assertTrue(peak(0, 4, strength, echoes) > 0.8, "the thump");
        assertTrue(peak(14, 16, strength, echoes) > 0.35, "the first echo");
        assertTrue(peak(41, 43, strength, echoes) > 0.5, "the second echo");
        assertTrue(peak(28, 38, strength, echoes) < 0.06, "between echoes the pen is back on its line");
        assertTrue(peak(60, 90, strength, echoes) < 0.04, "after the last echo too");
    }

    @Test
    @DisplayName("the curve never leaves the pen's stops")
    void bounded() {
        final int[] loud = {1, 850, 1, 850, 2, 850, 2, 850};
        for (double t = -5; t < 40; t += 0.1) {
            assertTrue(Math.abs(SeismicTrace.at(IMPACT + t, IMPACT, 1.0, loud)) <= 1.0);
        }
    }

    @Test
    @DisplayName("a harder blow writes taller; a bigger vein echoes taller and a farther one smaller")
    void amplitudes() {
        assertTrue(SeismicTrace.blowStrength(64) > SeismicTrace.blowStrength(16));
        assertEquals(1.0, SeismicTrace.blowStrength(64), 1e-9);
        assertTrue(SeismicTrace.echoAmplitude(40, 10) > SeismicTrace.echoAmplitude(4, 10));
        assertTrue(SeismicTrace.echoAmplitude(9, 8) > SeismicTrace.echoAmplitude(9, 60));
        for (int count = 1; count < 100_000; count *= 3) {
            for (double d = 0; d <= 64; d += 8) {
                final int a = SeismicTrace.echoAmplitude(count, d);
                assertTrue(a >= 100 && a <= 850, "amplitude " + a);
            }
        }
    }
}
