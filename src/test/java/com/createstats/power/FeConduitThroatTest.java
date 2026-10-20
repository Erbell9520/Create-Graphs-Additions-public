package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeConduitThroatTest {
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final int SURFACE = 4_096;
    private static final int DUCT = 16_384;

    private static FeConduitThroat leg(int supplierAtAnchor) {
        return new FeConduitThroat(
                new long[] {1L, 2L, 3L},
                new int[] {-1, 0, 1},
                new int[] {NONE, SURFACE, SURFACE},
                new int[] {supplierAtAnchor, 0, 0},
                new int[] {0, 0, 1});
    }

    @Test
    @DisplayName("a leg with no supply on it has no throat to find")
    void aLegWithNoSupplyHasNoCeilingOfItsOwn() {
        assertEquals(NONE, leg(0).ceiling());
    }

    @Test
    @DisplayName("named from the driver's anchor, the same leg is held to its cable")
    void anAnchoredLegIsHeldToItsCable() {
        assertEquals(SURFACE, leg(0).ceilingFrom(1L));
    }

    @Test
    @DisplayName("a circuit that powers itself is unchanged")
    void aSelfPoweredCircuitIsUnchanged() {
        final FeConduitThroat self = leg(1);
        assertEquals(SURFACE, self.ceiling());
        assertEquals(SURFACE, self.ceilingFrom(1L));
    }

    @Test
    @DisplayName("an anchor this circuit has never heard of falls back to its own answer")
    void anUnknownAnchorFallsBack() {
        assertEquals(NONE, leg(0).ceilingFrom(999L));
    }

    @Test
    @DisplayName("the run carries what its thinnest segment carries")
    void theThinnestSegmentWins() {
        final FeConduitThroat mixed = new FeConduitThroat(
                new long[] {1L, 2L, 3L, 4L},
                new int[] {-1, 0, 1, 2},
                new int[] {NONE, DUCT, SURFACE, SURFACE},
                new int[] {0, 0, 0, 0},
                new int[] {0, 0, 0, 1});
        assertEquals(SURFACE, mixed.ceilingFrom(1L));
    }

    @Test
    @DisplayName("two spurs off one anchor are parallel capacity, not a throat")
    void twoSpursHaveNoCommonThroat() {
        final FeConduitThroat forked = new FeConduitThroat(
                new long[] {1L, 2L, 3L, 4L, 5L},
                new int[] {-1, 0, 1, 0, 3},
                new int[] {NONE, SURFACE, SURFACE, SURFACE, SURFACE},
                new int[] {0, 0, 0, 0, 0},
                new int[] {0, 0, 1, 0, 1});
        assertEquals(NONE, forked.ceilingFrom(1L));
    }

    @Test
    @DisplayName("an empty tree answers no ceiling rather than nothing at all")
    void anEmptyTreeHasNoCeiling() {
        assertEquals(NONE, FeConduitThroat.NONE.ceiling());
        assertEquals(NONE, FeConduitThroat.NONE.ceilingFrom(1L));
    }
}
