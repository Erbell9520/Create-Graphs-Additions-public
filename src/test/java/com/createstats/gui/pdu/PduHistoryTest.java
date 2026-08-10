package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduHistoryTest {

    @Test
    void partialFillBeforeTheFirstWrapReadsInOrder() {
        final PduHistory h = new PduHistory();
        h.record(10, 100, 0);
        h.record(20, 200, 1);
        h.record(30, 300, 0);
        assertEquals(3, h.count());
        assertEquals(10, h.supplyAt(0));
        assertEquals(300, h.demandAt(2));
        assertEquals(1, h.shedAt(1));
    }

    @Test
    void wrapsAtTheBoundaryKeepingChronologicalOrderNotStorageOrder() {
        final PduHistory h = new PduHistory();
        // One more than a full buffer, so slot 0 has been overwritten and the oldest held is sample 1.
        for (int i = 0; i < PduHistory.SIZE + 1; i++) {
            h.record(i, i * 2, 0);
        }
        assertEquals(PduHistory.SIZE, h.count(), "count caps at capacity");
        assertEquals(1, h.supplyAt(0), "the very first sample has rolled off");
        assertEquals(PduHistory.SIZE, h.supplyAt(PduHistory.SIZE - 1), "the newest is last");
    }

    @Test
    void aFullOverwriteReadsBackTheSecondFillOnly() {
        final PduHistory h = new PduHistory();
        for (int i = 0; i < PduHistory.SIZE; i++) {
            h.record(1, 1, 0);
        }
        for (int i = 0; i < PduHistory.SIZE; i++) {
            h.record(9, 9, 0);
        }
        assertEquals(9, h.supplyAt(0), "the first fill is entirely overwritten");
        assertEquals(9, h.supplyAt(PduHistory.SIZE - 1));
    }

    @Test
    void feAboveTheShortMaxClampsRatherThanOverflowing() {
        final PduHistory h = new PduHistory();
        h.record(1_000_000, 2_000_000, 0);
        assertEquals(PduHistory.MAX_FE, h.supplyAt(0), "supply clamps to the short max");
        assertEquals(PduHistory.MAX_FE, h.demandAt(0), "demand clamps to the short max");
        assertTrue(h.supplyAt(0) > 0, "a clamped value must not wrap to a negative short");
    }

    @Test
    void shedCountClampsToAByte() {
        final PduHistory h = new PduHistory();
        h.record(0, 0, 5000);
        assertEquals(PduHistory.MAX_SHED, h.shedAt(0));
    }

    @Test
    void chronologicalCopyMatchesTheReadOrder() {
        final PduHistory h = new PduHistory();
        for (int i = 0; i < PduHistory.SIZE + 5; i++) {
            h.record(i, 0, 0);
        }
        final short[] chrono = h.supplyChronological();
        assertEquals(h.count(), chrono.length);
        for (int i = 0; i < chrono.length; i++) {
            assertEquals(h.supplyAt(i), chrono[i]);
        }
    }

    @Test
    void replaceFromASnapshotRestoresTheBuffer() {
        final PduHistory h = new PduHistory();
        h.replace(new short[]{5, 6, 7}, new short[]{50, 60, 70}, new byte[]{0, 1, 0}, 3);
        assertEquals(3, h.count());
        assertEquals(5, h.supplyAt(0));
        assertEquals(70, h.demandAt(2));
    }

    @Test
    void emptyBufferIsSafe() {
        final PduHistory h = new PduHistory();
        assertTrue(h.isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> h.supplyAt(0));
    }
}
