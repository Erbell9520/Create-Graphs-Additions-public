package com.createstats.gui.meter.plate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldGestureTest {
    @Test
    void firesOnTheTwentiethTickAndNotBefore() {
        final HoldGesture h = new HoldGesture();
        h.press();
        for (int i = 1; i < HoldGesture.TICKS; i++) {
            assertFalse(h.tick(), "fired early at tick " + i);
        }
        assertTrue(h.tick());
    }

    @Test
    void lettingGoEarlyDoesNothing() {
        final HoldGesture h = new HoldGesture();
        h.press();
        for (int i = 0; i < HoldGesture.TICKS - 1; i++) {
            h.tick();
        }
        h.release();
        for (int i = 0; i < 100; i++) {
            assertFalse(h.tick(), "a released hold must never fire");
        }
    }

    @Test
    void onePressFiresOnce() {
        final HoldGesture h = new HoldGesture();
        h.press();
        int fired = 0;
        for (int i = 0; i < HoldGesture.TICKS * 5; i++) {
            if (h.tick()) {
                fired++;
            }
        }
        assertEquals(1, fired, "a held button must not keep resetting a live meter");
    }

    @Test
    void progressRunsZeroToOne() {
        final HoldGesture h = new HoldGesture();
        assertEquals(0.0f, h.progress(0.5f));
        h.press();
        assertEquals(0.5f / HoldGesture.TICKS, h.progress(0.5f), 1e-6);
        for (int i = 0; i < 10; i++) {
            h.tick();
        }
        assertEquals(0.5f, h.progress(0.0f), 1e-6);
    }
}
