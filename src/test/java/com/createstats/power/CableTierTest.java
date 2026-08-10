package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class CableTierTest {

    @Test
    @DisplayName("a fatter grade always carries more, and a heavier carrier is always fatter")
    void widthAndCapacityRiseTogether() {
        final CableTier[] tiers = CableTier.values();
        for (int i = 1; i < tiers.length; i++) {
            final CableTier lower = tiers[i - 1];
            final CableTier higher = tiers[i];
            assertTrue(higher.multiplier() > lower.multiplier(),
                    higher + " carries no more than " + lower);
            assertTrue(higher.thicknessPx() > lower.thicknessPx(),
                    higher + " stands off no further than " + lower);
            assertTrue(higher.halfWidthPx() > lower.halfWidthPx(),
                    higher + " is drawn no wider than " + lower);
        }
    }

    @Test
    @DisplayName("the shipped line-up is one, three and six times the base rate")
    void theLineUp() {
        assertEquals(8_192, CableTier.SURFACE.rate(8_192));
        assertEquals(24_576, CableTier.TRAY.rate(8_192));
        assertEquals(49_152, CableTier.DUCT.rate(8_192));
    }

    @Test
    @DisplayName("rate scales with the configured base, so retuning the grid retunes cable with it")
    void rateFollowsTheBase() {
        assertEquals(3_000, CableTier.TRAY.rate(1_000));
        assertEquals(0, CableTier.DUCT.rate(0));
        assertEquals(0, CableTier.DUCT.rate(-5), "a nonsense base must not become a negative rate");
    }

    @Test
    @DisplayName("an unknown ordinal decodes to the grade every existing world already has")
    void decodeIsSafe() {
        assertEquals(CableTier.SURFACE, CableTier.byOrdinal(-1));
        assertEquals(CableTier.SURFACE, CableTier.byOrdinal(99));
        assertEquals(CableTier.DUCT, CableTier.byOrdinal(CableTier.DUCT.ordinal()));
    }
}
