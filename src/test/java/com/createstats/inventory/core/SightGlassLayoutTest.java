package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SightGlassLayoutTest {
    @Test
    void everyGridFitsTheGlassForEverySlotCountUpToALargeChest() {
        for (int n = 1; n <= SightGlassLayout.MAX_GRID_SLOTS; n++) {
            final SightGlassLayout.Layout l = SightGlassLayout.forSlots(n);
            assertEquals(SightGlassLayout.Kind.GRID, l.kind());
            for (int i = 0; i < n; i++) {
                assertTrue(l.cellX(i) >= 2 && l.cellX(i) + l.cellW() <= SightGlassLayout.GLASS_W - 2, n + " slots, cell " + i);
                assertTrue(l.cellY(i) >= SightGlassLayout.TOP && l.cellY(i) + l.cellH() <= SightGlassLayout.GLASS_H, n + " slots");
            }

            assertTrue(l.bottom() + 7 + 9 <= SightGlassLayout.GLASS_H, n + " slots leave no room for FILL");
        }
    }

    @Test
    void aChestIsNineByThreeOfTwentyTwo() {
        final SightGlassLayout.Layout l = SightGlassLayout.forSlots(27);
        assertEquals(9, l.cols());
        assertEquals(3, l.rows());
        assertEquals(22, l.cellW());
        assertEquals(2, l.gap());
        assertEquals(5, l.cellX(0), "the comps' chest starts at (5, 20)");
        assertEquals(20, l.cellY(0));
        assertFalse(l.compactFoot(), "room for the movers and the clock");
    }

    @Test
    void aLargeChestIsNineBySixOfTwentyAndKeepsFillAndTheClockOnly() {
        final SightGlassLayout.Layout l = SightGlassLayout.forSlots(54);
        assertEquals(6, l.rows());
        assertEquals(20, l.cellW());
        assertTrue(l.compactFoot());
    }

    @Test
    void aHopperIsFiveAcrossAndADispenserThreeByThree() {
        final SightGlassLayout.Layout hopper = SightGlassLayout.forSlots(5);
        assertEquals(5, hopper.cols());
        assertEquals(1, hopper.rows());
        assertEquals(22, hopper.cellW());
        final SightGlassLayout.Layout dispenser = SightGlassLayout.forSlots(9);
        assertEquals(3, dispenser.cols());
        assertEquals(3, dispenser.rows());
    }

    @Test
    void aVaultIsTilesOfFiftyThreeByTwentyTwo() {
        final SightGlassLayout.Layout l = SightGlassLayout.forSlots(1620);
        assertEquals(SightGlassLayout.Kind.TILES, l.kind());
        assertEquals(16, l.shown());
        for (int i = 0; i < 16; i++) {
            assertTrue(l.cellX(i) >= 0 && l.cellX(i) + 53 <= SightGlassLayout.GLASS_W);
        }
        assertEquals(3, l.cellX(0));
        assertTrue(l.bottom() + 5 + 9 <= SightGlassLayout.GLASS_H);
    }
}
