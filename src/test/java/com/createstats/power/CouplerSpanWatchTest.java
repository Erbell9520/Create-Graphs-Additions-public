package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerSpanWatchTest {
    @Test
    @DisplayName("one blink never drops a span; three misses in a row do")
    void toleratesABlink() {
        int m = 0;
        m = CouplerSpanWatch.next(m, false);
        assertFalse(CouplerSpanWatch.drop(m), "a side set to none for a moment kept the span");
        m = CouplerSpanWatch.next(m, true);
        assertFalse(CouplerSpanWatch.drop(m));
        for (int i = 0; i < CouplerSpanWatch.MISSES_TO_DROP - 1; i++) {
            m = CouplerSpanWatch.next(m, false);
            assertFalse(CouplerSpanWatch.drop(m));
        }
        m = CouplerSpanWatch.next(m, false);
        assertTrue(CouplerSpanWatch.drop(m));
    }

    @Test
    @DisplayName("a hit resets the count, and the count never runs away")
    void resetAndCap() {
        int m = CouplerSpanWatch.MISSES_TO_DROP;
        assertTrue(CouplerSpanWatch.drop(m));
        m = CouplerSpanWatch.next(m, true);
        assertFalse(CouplerSpanWatch.drop(m));
        for (int i = 0; i < 1000; i++) {
            m = CouplerSpanWatch.next(m, false);
        }
        assertTrue(m == CouplerSpanWatch.MISSES_TO_DROP);
        assertFalse(CouplerSpanWatch.drop(CouplerSpanWatch.next(-7, true)));
    }
}
