package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTraceTest {
    static RouteTrace sine(int ticks, double mean, double amp, double period, boolean joltAt200) {
        RouteTrace t = new RouteTrace(ticks);
        for (int i = 0; i < ticks; i++) {
            t.begin(1000 + i);
            double v = mean + amp * Math.sin(2 * Math.PI * i * 0.05 / period);
            if (joltAt200 && i >= 200) v -= 2.0;
            t.put(RouteTrace.Ch.STATE, RouteRunner.State.RUNNING.ordinal());
            t.put(RouteTrace.Ch.SPEED, v);
            t.put(RouteTrace.Ch.PLAN, mean);
            t.put(RouteTrace.Ch.TARGET, mean);
            t.put(RouteTrace.Ch.TARGET_ACCEL, 0.0);
            t.put(RouteTrace.Ch.YAW, 0.0);
            t.put(RouteTrace.Ch.LATERAL, 0.1);
            t.put(RouteTrace.Ch.PATH_KAPPA, 0.0);
            t.put(RouteTrace.Ch.OUT_STEER, 0.0);
            t.put(RouteTrace.Ch.OUT_BRAKE, 0.0);
            t.put(RouteTrace.Ch.WHEEL_SENT, i % 20 < 10 ? 0.0 : 2.0);
            t.put(RouteTrace.Ch.X, 222.5 + i * 0.3);
            t.put(RouteTrace.Ch.S, i * 0.3);
            t.end();
            if (i == 100) t.note(1000 + i, "blocked by a player");
        }
        return t;
    }

    @Test
    void itsBytesRoundTrip() {
        RouteTrace t = sine(1200, 6.0, 0.5, 3.0, false);
        RouteTrace back = RouteTrace.load(t.save());
        assertEquals(t.size(), back.size());
        for (int i = 0; i < t.size(); i += 37) {
            assertEquals(t.tick(i), back.tick(i));
            assertEquals(t.get(RouteTrace.Ch.SPEED, i), back.get(RouteTrace.Ch.SPEED, i), 1.0 / 500 + 1.0e-6);
            assertEquals(t.get(RouteTrace.Ch.X, i), back.get(RouteTrace.Ch.X, i), 1.0e-3, "positions as floats");
            assertTrue(Double.isNaN(back.get(RouteTrace.Ch.GAP, i)), "a channel never written stays unknown");
        }
        assertEquals(1, back.notes().size());
        assertTrue(t.save().length < 40_000, "a minute is kilobytes, not a megabyte: " + t.save().length);
    }

    @Test
    void aDamagedTraceLoadsEmpty() {
        byte[] b = sine(300, 6.0, 0.5, 3.0, false).save();
        b[b.length / 2] ^= 0x10;
        assertEquals(0, RouteTrace.load(b).size());
        assertEquals(0, RouteTrace.load(new byte[3]).size());
        assertEquals(0, RouteTrace.load(null).size());
    }

    @Test
    void theRingKeepsTheLastMinute() {
        RouteTrace t = sine(1500, 6.0, 0.0, 3.0, false);
        RouteTrace ring = new RouteTrace();
        for (int i = 0; i < t.size(); i++) {
            ring.begin(t.tick(i));
            ring.put(RouteTrace.Ch.SPEED, t.get(RouteTrace.Ch.SPEED, i));
            ring.end();
        }
        assertEquals(RouteTrace.CAPACITY, ring.size());
        assertEquals(t.tick(t.size() - RouteTrace.CAPACITY), ring.tick(0), "the oldest kept is a minute back");
    }

    @Test
    void aSurgeIsCountedAndASteadyCruiseIsNot() {
        RouteTrace.Measures surging = sine(2400, 5.0, 1.5, 3.5, false).measures(0);
        assertTrue(surging.surgesPerMinute() > 20, "a 3.5 s surge: " + surging);
        RouteTrace.Measures calm = sine(2400, 6.0, 0.02, 3.5, false).measures(0);
        assertEquals(0.0, calm.surgesPerMinute(), "a ripple under the band is not a surge: " + calm);
        assertEquals(0.02 / Math.sqrt(2), calm.speedErrorRms(), 0.005);
    }

    @Test
    void aOneTickDropIsAJolt() {
        RouteTrace.Measures m = sine(400, 6.0, 0.0, 3.0, true).measures(0);
        assertEquals(1, m.jolts(), "two b/s in a tick is 40 b/s^2, over two g at Sable's eleven: " + m);
        assertTrue(m.joltPeakG() > 3.5);
    }

    @Test
    void wheelsSteppingBackAndForthOnAStraightAreHunting() {
        RouteTrace.Measures m = sine(1200, 6.0, 0.0, 3.0, false).measures(0);

        assertTrue(m.wheelHuntPerSecond() > 1.5, "hunting: " + m);
    }
}
