package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BurnerDitherTest {
    private static double average(BurnerDither d, double wanted, double[] dials, int ticks) {
        double sum = 0.0;
        for (int t = 0; t < ticks; t++) sum += BurnerDither.output(d.signals(wanted, dials), dials);
        return sum / ticks;
    }

    @Test
    void theAverageMatchesWhatIsWanted() {
        double[][] sets = {{500.0}, {500.0, 500.0}, {500.0, 300.0, 120.0}};
        for (double[] dials : sets) {
            double max = 0.0;
            for (double d : dials) max += d;
            for (double share = 0.1; share <= 1.0; share += 0.07) {
                BurnerDither d = new BurnerDither();
                double wanted = share * max;
                double avg = average(d, wanted, dials, 300);
                assertEquals(wanted, avg, dials[dials.length - 1] / 15.0,
                        "dials " + java.util.Arrays.toString(dials) + " wanted " + wanted);
            }
        }
    }

    @Test
    void aLitHeaterNeverDropsToZero() {
        double[] dials = {500.0, 500.0};
        BurnerDither d = new BurnerDither();
        for (int t = 0; t < 400; t++) {
            double wanted = 100.0 + 800.0 * (0.5 + 0.5 * Math.sin(t * 0.05));
            int[] s = d.signals(wanted, dials);
            for (int i = 0; i < d.lit(); i++) assertTrue(s[i] >= 1, "tick " + t + " heater " + i);
        }
    }

    @Test
    void lowDemandSwitchesWholeHeatersOffFromTheLast() {
        double[] dials = {150.0, 150.0, 150.0};
        BurnerDither d = new BurnerDither();

        int[] s = d.signals(12.0, dials);
        assertArrayEquals(new int[] {1, 0, 0}, s);
        assertEquals(1, d.lit());
    }

    @Test
    void nothingWantedIsEveryHeaterOut() {
        BurnerDither d = new BurnerDither();
        assertArrayEquals(new int[] {0, 0}, d.signals(0.0, new double[] {500.0, 500.0}));
        assertEquals(0, d.lit());
    }

    @Test
    void theSwitchPointDoesNotChatter() {
        double[] dials = {150.0, 150.0};
        BurnerDither d = new BurnerDither();

        int changes = 0;
        int lit = -1;
        for (int t = 0; t < 200; t++) {
            d.signals(20.0 + ((t & 1) == 0 ? 0.3 : -0.3), dials);
            if (lit >= 0 && d.lit() != lit) changes++;
            lit = d.lit();
        }
        assertTrue(changes <= 1, "lit count changed " + changes + " times");
    }

    @Test
    void heatersDoNotAllStepTogether() {
        double[] dials = {500.0, 500.0};
        BurnerDither d = new BurnerDither();
        int together = 0;
        int[] prev = null;
        for (int t = 0; t < 60; t++) {
            int[] s = d.signals(0.5 * 1000.0 + 16.0, dials);
            if (prev != null && s[0] > prev[0] && s[1] > prev[1]) together++;
            prev = s;
        }
        assertEquals(0, together);
    }

    @Test
    void outputIsDialTimesSignalOverFifteen() {
        assertEquals(500.0 * 7 / 15.0 + 300.0 * 15 / 15.0,
                BurnerDither.output(new int[] {7, 15}, new double[] {500.0, 300.0}), 1.0e-9);
    }
}
