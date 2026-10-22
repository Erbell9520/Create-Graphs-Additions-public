package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerSyncGateTest {
    @Test
    @DisplayName("never synced is due, at every game time including zero")
    void neverIsDue() {
        for (long now : new long[] {0L, 1L, 10L, 24_000L, Long.MAX_VALUE / 2}) {
            assertTrue(CouplerSyncGate.due(now, CouplerSyncGate.NEVER, 10), "now=" + now);
        }
    }

    @Test
    @DisplayName("the old arithmetic was never due: this is the bug, kept visible")
    void theOldGateWrapped() {
        for (long now : new long[] {0L, 100L, 1_000_000L}) {
            assertTrue(now - Long.MIN_VALUE < 10, "now=" + now);
        }
    }

    @Test
    @DisplayName("not due inside the interval, due at and after it")
    void interval() {
        assertFalse(CouplerSyncGate.due(105, 100, 10));
        assertFalse(CouplerSyncGate.due(109, 100, 10));
        assertTrue(CouplerSyncGate.due(110, 100, 10));
        assertTrue(CouplerSyncGate.due(500, 100, 10));
    }

    @Test
    @DisplayName("a clock earlier than the last sync is due, not a very long wait")
    void clockWentBack() {
        assertTrue(CouplerSyncGate.due(50, 100, 10));
    }

    @Test
    @DisplayName("moved: a threshold, and reaching zero or full always counts")
    void moved() {
        assertFalse(CouplerSyncGate.moved(100, 100, 64, 0));
        assertFalse(CouplerSyncGate.moved(130, 100, 64, 0));
        assertTrue(CouplerSyncGate.moved(200, 100, 64, 0));
        assertTrue(CouplerSyncGate.moved(0, 10, 64, 0), "stopped is always worth showing");
        assertTrue(CouplerSyncGate.moved(16384, 16380, 256, 16384), "full is always worth showing");
        assertTrue(CouplerSyncGate.moved(Integer.MIN_VALUE + 1, Integer.MAX_VALUE, 64, 0), "no int overflow");
    }
}
