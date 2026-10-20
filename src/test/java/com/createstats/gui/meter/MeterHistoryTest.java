package com.createstats.gui.meter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeterHistoryTest {
    @Test
    void partialFillBeforeTheFirstWrapReadsInOrder() {
        final MeterHistory h = new MeterHistory();
        h.record(10);
        h.record(20);
        h.record(30);
        assertEquals(3, h.count());
        assertEquals(10, h.at(0));
        assertEquals(30, h.at(2));
        assertEquals(30, h.latest());
    }

    @Test
    void aFullRingReadsOldestToNewestAcrossTheSeam() {
        final MeterHistory h = new MeterHistory();

        for (int i = 0; i < MeterHistory.SIZE + MeterHistory.SIZE / 2; i++) {
            h.record(i);
        }
        assertEquals(MeterHistory.SIZE, h.count());
        final int oldest = MeterHistory.SIZE / 2;
        assertEquals(oldest, h.at(0));
        assertEquals(oldest + MeterHistory.SIZE - 1, h.at(MeterHistory.SIZE - 1));
        assertEquals(h.at(MeterHistory.SIZE - 1), h.latest());
    }

    @Test
    void readingPastTheHeldCountThrowsRatherThanReturningAStaleSlot() {
        final MeterHistory h = new MeterHistory();
        h.record(1);
        assertThrows(IndexOutOfBoundsException.class, () -> h.at(1));
        assertThrows(IndexOutOfBoundsException.class, () -> h.at(-1));
    }

    @Test
    void peakIsAMagnitudeSoASupplySpikeStillSetsTheAxis() {
        final MeterHistory h = new MeterHistory();
        h.record(40);
        h.record(-900);
        h.record(120);
        assertEquals(900, h.peakMagnitude());
    }

    @Test
    void meanKeepsItsSignSoAChargingBatteryDoesNotReadAsALoad() {
        final MeterHistory h = new MeterHistory();
        h.record(-100);
        h.record(-300);
        h.record(-200);
        assertEquals(-200, h.mean());
    }

    @Test
    void meanOfAnEvenSwingIsZeroRatherThanTheMagnitude() {
        final MeterHistory h = new MeterHistory();
        h.record(500);
        h.record(-500);
        assertEquals(0, h.mean());
        assertEquals(500, h.peakMagnitude());
    }

    @Test
    void anEmptyRingAnswersZeroRatherThanThrowing() {
        final MeterHistory h = new MeterHistory();
        assertTrue(h.isEmpty());
        assertEquals(0, h.mean());
        assertEquals(0, h.peakMagnitude());
        assertEquals(0, h.latest());
    }

    @Test
    void replaceTakesASnapshotWholeAndClearsWhatWasThere() {
        final MeterHistory h = new MeterHistory();
        for (int i = 0; i < 50; i++) {
            h.record(999);
        }
        h.replace(new int[]{1, 2, 3}, 3);
        assertEquals(3, h.count());
        assertEquals(1, h.at(0));
        assertEquals(3, h.latest());
    }

    @Test
    void replaceNeverReadsPastTheArrayItWasGiven() {
        final MeterHistory h = new MeterHistory();

        h.replace(new int[]{7, 8}, 500);
        assertEquals(2, h.count());
        assertEquals(8, h.latest());
    }

    @Test
    void clearEmptiesTheRingWithoutLeavingASeam() {
        final MeterHistory h = new MeterHistory();
        for (int i = 0; i < MeterHistory.SIZE + 5; i++) {
            h.record(i);
        }
        h.clear();
        assertTrue(h.isEmpty());
        h.record(42);
        assertEquals(1, h.count());
        assertEquals(42, h.at(0));
        assertFalse(h.isEmpty());
    }

    @Test
    void rangesClampSoAMalformedPacketCannotIndexOutOfBounds() {
        assertEquals(0, MeterHistory.clampRange(-3));
        assertEquals(MeterHistory.CADENCE.length - 1, MeterHistory.clampRange(99));
        assertEquals(MeterHistory.RANGE_5M, MeterHistory.clampRange(MeterHistory.RANGE_5M));
    }

    @Test
    void theThreeRangesSpanOneFiveAndFifteenMinutes() {
        assertEquals(60, MeterHistory.SIZE * MeterHistory.CADENCE[MeterHistory.RANGE_1M] / 20);
        assertEquals(300, MeterHistory.SIZE * MeterHistory.CADENCE[MeterHistory.RANGE_5M] / 20);
        assertEquals(900, MeterHistory.SIZE * MeterHistory.CADENCE[MeterHistory.RANGE_15M] / 20);
    }
}
