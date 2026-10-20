package com.createstats.gui.battery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryHistoryTest {
    @Test
    void anEmptyRingReadsAsEmpty() {
        final BatteryHistory h = new BatteryHistory();
        assertTrue(h.isEmpty());
        assertEquals(0, h.count());
        assertEquals(0, h.peakRate());
        assertEquals(0, h.meanNetRate());
        assertEquals(0, h.meanSocPct());
        assertEquals(0, h.latestSocPct());
        assertEquals(0, h.socChronological().length);
    }

    @Test
    void samplesComeBackInTheOrderTheyWentIn() {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < 5; i++) {
            h.record(i * 10, i * 100, i * 50);
        }
        assertEquals(5, h.count());
        for (int i = 0; i < 5; i++) {
            assertEquals(i * 10, h.socAt(i));
            assertEquals(i * 100, h.inAt(i));
            assertEquals(i * 50, h.outAt(i));
        }
        assertEquals(40, h.latestSocPct());
    }

    @Test
    void theOldestSamplesFallOffTheEndOnceItIsFull() {
        final BatteryHistory h = new BatteryHistory();

        for (int i = 0; i < BatteryHistory.SIZE + BatteryHistory.SIZE / 2; i++) {
            h.record(i % 101, i, i * 2);
        }
        assertEquals(BatteryHistory.SIZE, h.count());

        final int firstHeld = BatteryHistory.SIZE / 2;
        assertEquals(firstHeld, h.inAt(0));
        assertEquals(firstHeld * 2, h.outAt(0));
        final int lastPushed = BatteryHistory.SIZE + BatteryHistory.SIZE / 2 - 1;
        assertEquals(lastPushed, h.inAt(h.count() - 1));
    }

    @Test
    void chronologicalMatchesIndexedReadsAcrossAWrap() {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < BatteryHistory.SIZE + 37; i++) {
            h.record(i % 101, i, i * 3);
        }
        final byte[] soc = h.socChronological();
        final int[] in = h.inChronological();
        final int[] out = h.outChronological();
        assertEquals(h.count(), soc.length);
        for (int i = 0; i < h.count(); i++) {
            assertEquals(h.socAt(i), soc[i]);
            assertEquals(h.inAt(i), in[i]);
            assertEquals(h.outAt(i), out[i]);
        }
    }

    @Test
    void aFullSendRebuildsTheRingExactly() {
        final BatteryHistory server = new BatteryHistory();
        for (int i = 0; i < BatteryHistory.SIZE + 11; i++) {
            server.record(i % 101, i, i * 2);
        }
        final BatteryHistory client = new BatteryHistory();
        client.replace(server.socChronological(), server.inChronological(),
                server.outChronological(), server.count());

        assertEquals(server.count(), client.count());
        for (int i = 0; i < server.count(); i++) {
            assertEquals(server.socAt(i), client.socAt(i));
            assertEquals(server.inAt(i), client.inAt(i));
            assertEquals(server.outAt(i), client.outAt(i));
        }
    }

    @Test
    void aDeltaAppendsToWhatIsAlreadyThere() {
        final BatteryHistory h = new BatteryHistory();
        h.record(50, 100, 100);
        h.pushRaw((byte) 51, 200, 150);
        assertEquals(2, h.count());
        assertEquals(51, h.latestSocPct());
        assertEquals(200, h.inAt(1));
    }

    @Test
    void chargeIsClampedToAPercentage() {
        final BatteryHistory h = new BatteryHistory();
        h.record(150, 0, 0);
        h.record(-20, 0, 0);
        assertEquals(100, h.socAt(0));
        assertEquals(0, h.socAt(1));
    }

    @Test
    void aRateFarPastAShortIsHeldExactly() {
        final BatteryHistory h = new BatteryHistory();
        h.record(50, 98_304, 98_304);
        assertEquals(98_304, h.inAt(0));
        assertEquals(98_304, h.outAt(0));
        assertEquals(98_304, h.peakRate());
    }

    @Test
    void theNetRateIsTheSlopeOfTheWholeWindow() {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < 10; i++) {
            h.record(50, 1_000, 400);
        }
        assertEquals(600, h.meanNetRate());
    }

    @Test
    void aDrainingWindowReadsNegative() {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < 10; i++) {
            h.record(50, 200, 900);
        }
        assertTrue(h.meanNetRate() < 0);
        assertEquals(-700, h.meanNetRate());
    }

    @Test
    void aBigWindowOfBigRatesDoesNotOverflow() {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < BatteryHistory.SIZE; i++) {
            h.record(50, Integer.MAX_VALUE / 2, 0);
        }
        assertEquals(Integer.MAX_VALUE / 2, h.meanNetRate());
    }

    @Test
    void readingPastTheEndIsRefusedRatherThanReturningStaleMemory() {
        final BatteryHistory h = new BatteryHistory();
        h.record(50, 100, 100);
        assertThrows(IndexOutOfBoundsException.class, () -> h.socAt(1));
        assertThrows(IndexOutOfBoundsException.class, () -> h.inAt(-1));
    }

    @Test
    void clearingEmptiesItCompletely() {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < 20; i++) {
            h.record(i, i, i);
        }
        h.clear();
        assertTrue(h.isEmpty());
        assertEquals(0, h.count());
    }

    @Test
    void everyRangeIndexResolvesToOneOfTheThree() {
        assertEquals(0, BatteryHistory.clampRange(-5));
        assertEquals(0, BatteryHistory.clampRange(0));
        assertEquals(2, BatteryHistory.clampRange(2));
        assertEquals(2, BatteryHistory.clampRange(99));
        assertEquals(3, BatteryHistory.CADENCE.length);
    }
}
