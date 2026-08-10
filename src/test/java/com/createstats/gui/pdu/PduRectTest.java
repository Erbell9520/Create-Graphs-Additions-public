package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduRectTest {

    @Test
    void containsIsInclusiveOnTheTopLeft() {
        final PduRect r = new PduRect(10, 20, 30, 40);
        assertTrue(r.contains(10, 20), "the top left corner is inside");
    }

    @Test
    void containsIsExclusiveOnTheBottomRight() {
        final PduRect r = new PduRect(10, 20, 30, 40);
        // right() is 40 and bottom() is 60; those belong to the next control, not this one.
        assertFalse(r.contains(40, 30), "the right edge belongs to the neighbour");
        assertFalse(r.contains(20, 60), "the bottom edge belongs to the neighbour");
        assertTrue(r.contains(39, 59), "one pixel inside each far edge is still inside");
    }

    @Test
    void adjacentRectsNeverBothClaimTheSeam() {
        final PduRect left = new PduRect(0, 0, 10, 10);
        final PduRect right = new PduRect(10, 0, 10, 10);
        assertFalse(left.contains(10, 5), "the seam is not the left rect's");
        assertTrue(right.contains(10, 5), "the seam is the right rect's");
    }

    @Test
    void derivedEdgesAndCentreAreCorrect() {
        final PduRect r = new PduRect(10, 20, 30, 40);
        assertEquals(40, r.right());
        assertEquals(60, r.bottom());
        assertEquals(25, r.centerX());
        assertEquals(40, r.centerY());
    }

    @Test
    void insetShrinksSymmetrically() {
        final PduRect r = new PduRect(10, 20, 30, 40).inset(4);
        assertEquals(14, r.x());
        assertEquals(24, r.y());
        assertEquals(22, r.w());
        assertEquals(32, r.h());
    }

    @Test
    void insetNeverProducesNegativeSize() {
        // A card padded harder than it is wide must clamp to zero, not fold inside out into a
        // negative rectangle that a scissor call would then reject.
        final PduRect r = new PduRect(0, 0, 6, 6).inset(8);
        assertEquals(0, r.w());
        assertEquals(0, r.h());
    }
}
