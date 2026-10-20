package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeRatesPlusTest {
    private static final int ONE_FULL_BANK = 82_000_000;

    @Test
    @DisplayName("ordinary sums are ordinary")
    void ordinarySums() {
        assertEquals(0, FeRates.plus(0, 0));
        assertEquals(1_500_000, FeRates.plus(500_000, 1_000_000));
        assertEquals(ONE_FULL_BANK * 2, FeRates.plus(ONE_FULL_BANK, ONE_FULL_BANK));
    }

    @Test
    @DisplayName("a bank farm past the int range reads as full, not as empty")
    void aBigFarmSaturates() {
        int plain = 0;
        int saturating = 0;
        for (int i = 0; i < 30; i++) {
            plain += ONE_FULL_BANK;
            saturating = FeRates.plus(saturating, ONE_FULL_BANK);
        }
        assertTrue(plain < 0, "the bug this guards is real: plain addition wrapped negative");
        assertEquals(Integer.MAX_VALUE, saturating);
    }

    @Test
    @DisplayName("a saturated reserve still reads as a healthy state of charge")
    void aSaturatedReserveIsStillHealthy() {
        int stored = 0;
        int capacity = 0;
        for (int i = 0; i < 40; i++) {
            stored = FeRates.plus(stored, ONE_FULL_BANK);
            capacity = FeRates.plus(capacity, ONE_FULL_BANK);
        }
        final int soc = (int) ((long) stored * 100L / capacity);
        assertEquals(100, soc);

        final FeReserveReport report = FeReserveBand.assess(0L, new FeReserveBand.Params(
                stored, capacity, 0, 0, 65_536, 32_768, FeReserveBand.Band.DEFAULT));
        assertEquals(FeReserveReport.Stance.SURPLUS, report.stance(),
                "a farm this deep must not report CRITICAL");
        assertTrue(report.sustainedDraw() > 0, "and must be willing to spend something");
    }

    @Test
    @DisplayName("a negative input cannot drag a total below zero")
    void negativesClampRatherThanSubtract() {
        assertEquals(0, FeRates.plus(-5, 0));
        assertEquals(0, FeRates.plus(Integer.MIN_VALUE, 10));
        assertEquals(Integer.MAX_VALUE, FeRates.plus(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
