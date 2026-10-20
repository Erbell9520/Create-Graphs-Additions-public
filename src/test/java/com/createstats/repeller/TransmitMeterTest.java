package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransmitMeterTest {
    private static int run(TransmitMeter m, int radius, double spent, int ticks) {
        int changes = 0;
        for (int t = 0; t < ticks; t++) {
            if (m.tick(radius, spent)) {
                changes++;
            }
        }
        return changes;
    }

    private static int runNoisy(TransmitMeter m, int radius, double supply, double noise, int ticks, long[] seed) {
        int changes = 0;
        for (int t = 0; t < ticks; t++) {
            seed[0] = seed[0] * 6364136223846793005L + 1442695040888963407L;
            final double wobble = (Long.remainderUnsigned(seed[0] >>> 33, 2001) / 10_000.0 - 0.1) * (noise / 0.1);
            if (m.tick(radius, supply * (1.0 + wobble))) {
                changes++;
            }
        }
        return changes;
    }

    @Test
    void aFullyPaidRepellerPublishesExactlyItsSetRadius() {
        final TransmitMeter m = new TransmitMeter();
        run(m, 64, RepellerDraw.drawFor(64), 300);
        assertEquals(64.0, m.publishedRadius(), 0.0);
        assertEquals(1.0, m.powerFraction(64), 1e-3);
    }

    @Test
    void aHalfFedSixtyFourSettlesWithinABlockOfFortyFive() {
        final TransmitMeter m = new TransmitMeter();
        run(m, 64, 1_024, 300);
        run(m, 64, 512, 400);
        assertEquals(45.25, m.publishedRadius(), TransmitMeter.SETTLE_MIN,
                "the settle brings the readout onto the figure the supply pays for");
        assertEquals(0.5, m.powerFraction(64), 0.01);
    }

    @Test
    void aGridsNoiseBarelyMovesThePublishedRadius() {
        final double[][] cases = {{512, 64}, {900, 64}, {1_000, 64}, {128, 32}, {40, 16}, {16, 8}};
        for (long s = 1; s < 40; s++) {
            for (double[] c : cases) {
                final TransmitMeter m = new TransmitMeter();
                final long[] seed = {s * 7919};
                final int radius = (int) c[1];
                runNoisy(m, radius, c[0], 0.1, 800, seed);
                final int changes = runNoisy(m, radius, c[0], 0.1, 4_000, seed);
                assertTrue(changes <= 2, changes + " republishes at " + c[0] + " FE/t, radius " + radius);
                double truth = Math.min(radius, RepellerDraw.radiusPaidBy(c[0]));
                if (truth >= radius - TransmitMeter.SNAP) {
                    truth = radius;
                }
                assertEquals(truth, m.publishedRadius(), 1.1);
            }
        }
    }

    @Test
    void theSphereGrowsInVisibleStepsAfterTheRadiusIsRaised() {
        final TransmitMeter m = new TransmitMeter();
        run(m, 16, RepellerDraw.drawFor(16), 300);
        assertEquals(16.0, m.publishedRadius(), 0.0);
        int steps = 0;
        int reached = -1;
        for (int t = 0; t < 400; t++) {
            if (m.tick(32, RepellerDraw.drawFor(32))) {
                steps++;
            }
            if (reached < 0 && m.publishedRadius() == 32.0) {
                reached = t;
            }
        }
        assertEquals(32.0, m.publishedRadius(), 0.0);
        assertTrue(reached > 0 && reached <= 80, "reached 32 after " + reached + " ticks");
        assertTrue(steps >= 3 && steps <= 12, "grew in " + steps + " steps");
    }

    @Test
    void loweringTheRadiusIsImmediate() {
        final TransmitMeter m = new TransmitMeter();
        run(m, 32, RepellerDraw.drawFor(32), 300);
        assertTrue(m.tick(16, RepellerDraw.drawFor(16)));
        assertEquals(16.0, m.publishedRadius(), 0.0);
    }

    @Test
    void starvedItFadesToNothingAndSaysSo() {
        final TransmitMeter m = new TransmitMeter();
        run(m, 64, RepellerDraw.drawFor(64), 300);
        run(m, 64, 0, 200);
        assertEquals(0.0, m.publishedRadius(), 0.0, "a starved repeller covers nothing, and the index must hear it");
    }

    @Test
    void aResetIsImmediateAndQuiet() {
        final TransmitMeter m = new TransmitMeter();
        run(m, 40, RepellerDraw.drawFor(40), 300);
        m.reset();
        assertEquals(0.0, m.publishedRadius(), 0.0);
        assertEquals(0.0, m.smoothedFePerTick(), 0.0);
        assertFalse(m.tick(40, 0), "nothing going out after a reset is not a change");
    }

    @Test
    void aRestoredReadingCarriesOnWhereItStopped() {
        final TransmitMeter m = new TransmitMeter();
        m.restore(RepellerDraw.drawFor(48), 48.0);
        assertFalse(m.tick(48, RepellerDraw.drawFor(48)), "a reloaded repeller does not grow from zero");
        assertEquals(48.0, m.publishedRadius(), 0.0);
    }
}
