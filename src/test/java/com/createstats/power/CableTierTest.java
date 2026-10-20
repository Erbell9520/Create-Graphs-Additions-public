package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableTierTest {
    @Test
    @DisplayName("a wider grade always carries more, and a heavier carrier is always wider")
    void widthAndCapacityRiseTogether() {
        final CableTier[] tiers = CableTier.values();
        for (int i = 1; i < tiers.length; i++) {
            final CableTier lower = tiers[i - 1];
            final CableTier higher = tiers[i];
            assertTrue(higher.multiplier() > lower.multiplier(),
                    higher + " carries no more than " + lower);
            assertTrue(higher.halfWidthPx() > lower.halfWidthPx(),
                    higher + " is drawn no wider than " + lower);
        }
    }

    @Test
    @DisplayName("no grade's section stands more than three pixels off its face")
    void noGradeIsChunky() {
        for (CableTier tier : CableTier.values()) {
            assertTrue(tier.thicknessPx() <= 3.0f + 1.0e-4f,
                    tier + " stands " + tier.thicknessPx() + " px off its face; the trunk at 5 px read as a kerb");
        }
    }

    @Test
    @DisplayName("the shipped line-up is half, one, two and four times the base rate")
    void theLineUp() {
        assertEquals(4_096, CableTier.SURFACE.rate(8_192));
        assertEquals(8_192, CableTier.TRAY.rate(8_192));
        assertEquals(16_384, CableTier.DUCT.rate(8_192));
        assertEquals(32_768, CableTier.TRUNK.rate(8_192));
    }

    @Test
    @DisplayName("the trunk is the fourth and last grade, appended after the duct")
    void theTrunkIsAppended() {
        assertEquals(3, CableTier.TRUNK.ordinal());
        assertEquals(4, CableTier.values().length);
        assertEquals(CableTier.TRUNK, CableTier.byOrdinal(CableTier.TRUNK.ordinal()));
    }

    @Test
    @DisplayName("rate scales with the configured base, so retuning the grid retunes cable with it")
    void rateFollowsTheBase() {
        assertEquals(2_000, CableTier.DUCT.rate(1_000));
        assertEquals(0, CableTier.DUCT.rate(0));
        assertEquals(0, CableTier.DUCT.rate(-5), "a nonsense base must not become a negative rate");
    }

    @Test
    @DisplayName("any nonzero flow gets a visible load step")
    void nonzeroFlowGetsAVisibleLoadStep() {
        assertEquals(0, FeRates.loadStep(0, 1_024, 15));
        assertEquals(1, FeRates.loadStep(1, 1_024, 15));
        assertEquals(1, FeRates.loadStep(64, 1_024, 15));
        assertEquals(15, FeRates.loadStep(4_096, 4_096, 15));
        assertEquals(15, FeRates.loadStep(8_192, 4_096, 15));
    }

    @Test
    @DisplayName("an unknown ordinal decodes to the grade every existing world already has")
    void decodeIsSafe() {
        assertEquals(CableTier.SURFACE, CableTier.byOrdinal(-1));
        assertEquals(CableTier.SURFACE, CableTier.byOrdinal(99));
        assertEquals(CableTier.DUCT, CableTier.byOrdinal(CableTier.DUCT.ordinal()));
    }
}
