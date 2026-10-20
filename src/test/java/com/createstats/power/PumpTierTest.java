package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PumpTierTest {
    @Test
    @DisplayName("each grade moves exactly half its pressure, the Create pipe transfer rate")
    void flowIsHalfThePressure() {
        for (final PumpTier tier : PumpTier.values()) {
            assertEquals(tier.maxPressure() / 2, tier.maxFlowMbPerTick(),
                    tier + " does not move half its pressure, so it no longer matches a Create pump");
        }
    }

    @Test
    @DisplayName("the shipped line-up is a 128 RPM and a 256 RPM Create pump")
    void theLineUp() {
        assertEquals(128, PumpTier.MK_I.maxPressure());
        assertEquals(64, PumpTier.MK_I.maxFlowMbPerTick());
        assertEquals(256, PumpTier.MK_II.maxPressure());
        assertEquals(128, PumpTier.MK_II.maxFlowMbPerTick());
    }

    @Test
    @DisplayName("the higher grade always moves more")
    void higherGradeMovesMore() {
        assertTrue(PumpTier.MK_II.maxFlowMbPerTick() > PumpTier.MK_I.maxFlowMbPerTick(),
                "MK II carries no more than MK I");
        assertTrue(PumpTier.MK_II.maxPressure() > PumpTier.MK_I.maxPressure(),
                "MK II pushes no harder than MK I");
    }

    @Test
    @DisplayName("ordinal decode is safe and round-trips, defaulting out-of-range to MK I")
    void ordinalDecodeIsSafe() {
        for (final PumpTier tier : PumpTier.values()) {
            assertEquals(tier, PumpTier.byOrdinal(tier.ordinal()));
        }
        assertEquals(PumpTier.MK_I, PumpTier.byOrdinal(-1));
        assertEquals(PumpTier.MK_I, PumpTier.byOrdinal(99));
    }
}
