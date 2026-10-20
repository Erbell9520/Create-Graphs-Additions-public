package com.createstats.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createstats.data.TimeSeriesStore;
import org.junit.jupiter.api.Test;

class RingBufferCacheTest {
    @Test
    void copyRecentPreservesChronologicalOrderAcrossWrap() {
        final RingBuffer buffer = new RingBuffer(3);
        buffer.add(1.0f);
        buffer.add(2.0f);
        buffer.add(3.0f);
        buffer.add(4.0f);
        final float[] destination = new float[3];

        assertEquals(3, buffer.copyRecent(destination, 3));
        assertArrayEquals(new float[]{2.0f, 3.0f, 4.0f}, destination);
    }

    @Test
    void extremaAndAverageStayCorrectAfterOverwrite() {
        final RingBuffer buffer = new RingBuffer(3);
        buffer.add(-7.0f);
        buffer.add(2.0f);
        buffer.add(9.0f);
        assertEquals(-7.0f, buffer.min());
        assertEquals(9.0f, buffer.max());

        buffer.add(4.0f);
        assertEquals(2.0f, buffer.min());
        assertEquals(9.0f, buffer.max());
        assertEquals(5.0f, buffer.average(), 1.0e-5f);
    }

    @Test
    void fullSnapshotIsReusedUntilContentsChange() {
        final RingBuffer buffer = new RingBuffer(4);
        buffer.add(1.0f);
        buffer.add(2.0f);
        final long before = buffer.revision();
        final float[] first = buffer.toArray();

        assertSame(first, buffer.toArray());
        assertEquals(before, buffer.revision());
        buffer.add(3.0f);
        assertTrue(buffer.revision() > before, "an add must move the revision forward");
        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f}, buffer.toArray());
    }

    @Test
    void separateBuffersNeverShareARevision() {
        final RingBuffer a = new RingBuffer(3);
        final RingBuffer b = new RingBuffer(3);
        assertNotEquals(a.revision(), b.revision());
        a.add(1.0f);
        b.add(2.0f);
        assertNotEquals(a.revision(), b.revision());
    }

    @Test
    void aResyncedStoreNeverRepeatsTheRevisionOfTheStoreItReplaces() {
        final TimeSeriesStore server = new TimeSeriesStore(400);
        for (int i = 0; i < 200; i++) {
            server.addSampleDirect(i);
        }
        final TimeSeriesStore first = TimeSeriesStore.load(
                server.savePreview(TimeSeriesStore.DEFAULT_SYNC_PREVIEW_SAMPLES));
        server.addSampleDirect(999.0f);
        final TimeSeriesStore second = TimeSeriesStore.load(
                server.savePreview(TimeSeriesStore.DEFAULT_SYNC_PREVIEW_SAMPLES));

        assertEquals(first.getSampleCount(), second.getSampleCount(),
                "precondition: a full preview, as in game once a channel has run for three minutes");
        assertNotEquals(first.getRevision(), second.getRevision());
    }
}
