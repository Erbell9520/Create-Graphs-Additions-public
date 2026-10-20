package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeExportReportTest {
    private static final int CAPACITY = 512_000;
    private static final int FLOOR_PCT = 15;
    private static final int RESTORE_PCT = 45;

    private static FeExportReport report(int pct, int allowance, boolean shedding) {
        final int stored = Math.round(CAPACITY * (pct / 100.0f));
        return new FeExportReport(42L, allowance, 0, stored, CAPACITY, FLOOR_PCT,
                PduExportPolicy.stanceOf(stored, CAPACITY, FLOOR_PCT, RESTORE_PCT, shedding),
                shedding);
    }

    @Test
    @DisplayName("a board that cannot run its own machines says so, whatever its buffer reads")
    void sheddingIsReportedEvenOnAHealthyBuffer() {
        final FeExportReport busy = report(80, 11_000, true);
        assertTrue(busy.holdCharging(), "the far end must not be banking this");
        assertEquals(FeReserveReport.Stance.NOMINAL, busy.stance(),
                "not SURPLUS: a board switching its own machines off is not in surplus");

        final FeExportReport healthy = report(80, 11_000, false);
        assertFalse(healthy.holdCharging());
        assertEquals(FeReserveReport.Stance.SURPLUS, healthy.stance());
    }

    @Test
    @DisplayName("the stance names where the charge is, in the same words the controller uses")
    void theStanceMatchesTheReserveVocabulary() {
        assertEquals(FeReserveReport.Stance.CRITICAL, report(10, 0, false).stance());
        assertEquals(FeReserveReport.Stance.CRITICAL, report(15, 0, false).stance(),
                "exactly at the floor is not above it");
        assertEquals(FeReserveReport.Stance.CONSERVE, report(30, 200, false).stance());
        assertEquals(FeReserveReport.Stance.SURPLUS, report(60, 4096, false).stance());
    }

    @Test
    @DisplayName("a puller is capped at the allowance rather than left to discover it")
    void cappedIsTheWholePoint() {
        final FeExportReport tight = report(20, 300, true);
        assertEquals(300, tight.capped(32_768), "stop asking for the port rating");
        assertEquals(120, tight.capped(120), "and asking for less than the allowance is fine");

        assertEquals(32_768, FeExportReport.NONE.capped(32_768));
        assertFalse(FeExportReport.NONE.isPresent());
    }

    @Test
    @DisplayName("rationing is CONSERVE and below, which is when the far end should be shedding")
    void rationingIsTheSignalToShed() {
        assertFalse(report(80, 4096, false).isRationing());
        assertTrue(report(30, 200, false).isRationing());
        assertTrue(report(5, 0, false).isRationing());
        assertFalse(FeExportReport.NONE.isRationing(), "absent is not rationing");
    }

    @Test
    @DisplayName("the state of charge survives a board bigger than an int can hold")
    void socIsLongSafe() {
        final FeExportReport huge = new FeExportReport(1L, 0, 0, Integer.MAX_VALUE,
                Integer.MAX_VALUE, 15, FeReserveReport.Stance.SURPLUS, false);
        assertEquals(100, huge.socPct());
        assertEquals(0, FeExportReport.NONE.socPct(), "no buffer is not a divide by zero");
    }
}
