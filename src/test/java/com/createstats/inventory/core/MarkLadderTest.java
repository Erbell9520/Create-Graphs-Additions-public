package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkLadderTest {
    @Test
    void theLaddersAreTheDesignsAndRise() {
        assertEquals(21, MarkLadder.ITEMS.size());
        assertEquals(1728L, MarkLadder.ITEMS.valueAt(10), "a chest of stacks where a player expects it");
        assertEquals(1_048_576L, MarkLadder.ITEMS.valueAt(20));
        assertEquals(21, MarkLadder.PERCENT.size());
        assertEquals(100L, MarkLadder.PERCENT.valueAt(20));
        assertEquals(18, MarkLadder.PER_MINUTE.size());
        assertEquals(960L, MarkLadder.PER_MINUTE.valueAt(17));
        for (MarkLadder l : MarkLadder.values()) {
            for (int i = 1; i < l.size(); i++) {
                assertTrue(l.valueAt(i) > l.valueAt(i - 1), l + " rises at " + i);
            }
        }
    }

    @Test
    void theDefaultsAreTheDesigns() {
        assertEquals(10L, MarkLadder.PERCENT.defaultLow());
        assertEquals(90L, MarkLadder.PERCENT.defaultHigh());
        assertEquals(64L, MarkLadder.ITEMS.defaultLow());
        assertEquals(1024L, MarkLadder.ITEMS.defaultHigh());
        assertEquals(0L, MarkLadder.PER_MINUTE.defaultLow());
        assertEquals(60L, MarkLadder.PER_MINUTE.defaultHigh());
        assertEquals(20L, MarkLadder.ENERGY_PERCENT.defaultLow());
        assertEquals(95L, MarkLadder.ENERGY_PERCENT.defaultHigh());
    }

    @Test
    void theNeedleSitsBetweenTheNotchesATypedMarkFallsBetween() {
        assertEquals(0.0, MarkLadder.ITEMS.fraction(0), 1e-9);
        assertEquals(1.0, MarkLadder.ITEMS.fraction(5_000_000), 1e-9);
        assertEquals(9.0 / 20.0, MarkLadder.ITEMS.fraction(1024), 1e-9);

        assertEquals(9.5 / 20.0, MarkLadder.ITEMS.fraction(1376), 1e-9);
        assertEquals(0.5, MarkLadder.PERCENT.fraction(50), 1e-9);
    }

    @Test
    void theBoardStartsAtTheNearestNotch() {
        assertEquals(9, MarkLadder.ITEMS.notchOf(1000));
        assertEquals(10, MarkLadder.ITEMS.notchOf(1700));
        assertEquals(18, MarkLadder.PERCENT.notchOf(91));
    }

    @Test
    void labelsAreShortAndTypedMarksAreClamped() {
        assertEquals("1,728", MarkLadder.ITEMS.label(10));
        assertEquals("16K", MarkLadder.ITEMS.label(14));
        assertEquals("1M", MarkLadder.ITEMS.label(20));
        assertEquals("45", MarkLadder.PERCENT.label(9));
        assertEquals(100L, MarkLadder.PERCENT.clampTyped(250));
        assertEquals(0L, MarkLadder.ITEMS.clampTyped(-4));
        assertEquals(9_999_999L, MarkLadder.ITEMS.clampTyped(Long.MAX_VALUE));
    }
}
