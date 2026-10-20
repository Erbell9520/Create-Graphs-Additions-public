package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduRingTest {
    @Test
    void fillsInOrderBeforeWrapping() {
        final PduRing ring = new PduRing(4);
        ring.push(10);
        ring.push(20);
        ring.push(30);
        assertEquals(3, ring.count());
        assertEquals(10, ring.at(0));
        assertEquals(30, ring.at(2));
        assertEquals(30, ring.latest());
    }

    @Test
    void countCapsAtCapacity() {
        final PduRing ring = new PduRing(3);
        for (int i = 0; i < 10; i++) {
            ring.push(i);
        }
        assertEquals(3, ring.count());
    }

    @Test
    void oldestIsOverwrittenAndOrderIsPreservedAcrossTheSeam() {
        final PduRing ring = new PduRing(3);
        ring.push(1);
        ring.push(2);
        ring.push(3);
        ring.push(4);
        ring.push(5);
        assertEquals(3, ring.at(0), "oldest held is now 3");
        assertEquals(4, ring.at(1));
        assertEquals(5, ring.at(2));
        assertEquals(5, ring.latest());
    }

    @Test
    void maxReadsAcrossTheHeldWindowOnly() {
        final PduRing ring = new PduRing(3);
        ring.push(100);
        ring.push(5);
        ring.push(5);
        ring.push(5);
        assertEquals(5, ring.max(), "the 100 has rolled off");
    }

    @Test
    void emptyRingIsSafe() {
        final PduRing ring = new PduRing(4);
        assertTrue(ring.isEmpty());
        assertEquals(0, ring.latest());
        assertEquals(0, ring.max());
        assertThrows(IndexOutOfBoundsException.class, () -> ring.at(0));
    }

    @Test
    void clearResets() {
        final PduRing ring = new PduRing(4);
        ring.push(1);
        ring.push(2);
        ring.clear();
        assertTrue(ring.isEmpty());
        assertEquals(0, ring.count());
    }
}
