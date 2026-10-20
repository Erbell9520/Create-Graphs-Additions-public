package com.createstats.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeScaleTest {
    private static final double D1 = 1.5;
    private static final double D2 = 2.0;
    private static final double D3 = 4.0;

    private static final int ROWS = 3;
    private static final int COLS = 9;
    private static final int MAX_ROWS = 36;
    private static final int MAX_STACK = 768;

    @Test
    @DisplayName("The shipped ladder is the one this file does its arithmetic in")
    void ladderIsWhatThisFileAssumes() {
        assertEquals(D1, UpgradeDegree.FIRST.defaultMultiplier());
        assertEquals(D2, UpgradeDegree.SECOND.defaultMultiplier());
        assertEquals(D3, UpgradeDegree.THIRD.defaultMultiplier());
        assertEquals(D1 * D2 * D3, UpgradeScale.FULL_SET, "a full set is the product of the three");
        assertEquals(MAX_ROWS, (int) Math.round(ROWS * UpgradeScale.FULL_SET),
                "the row ceiling is exactly what a full set earns");
        assertEquals(MAX_STACK, (int) Math.round(64 * UpgradeScale.FULL_SET),
                "the depth ceiling is exactly what a full set earns on an ordinary stack");
    }

    private static int rows(double... installed) {
        return UpgradeScale.rows(ROWS, UpgradeScale.product(installed), MAX_ROWS);
    }

    private static int cap(int base, double... installed) {
        return UpgradeScale.stackCap(base, UpgradeScale.product(installed), MAX_STACK);
    }

    @Test
    @DisplayName("An empty rack changes nothing")
    void empty() {
        assertEquals(1.0, UpgradeScale.product(), "a product over nothing is one, never zero");
        assertEquals(ROWS, rows());
        assertEquals(64, cap(64));
    }

    @Test
    @DisplayName("Each degree on its own grows the grid by whole rows")
    void singleDegrees() {
        assertEquals(5, rows(D1));
        assertEquals(6, rows(D2));
        assertEquals(12, rows(D3));
    }

    @Test
    @DisplayName("Degrees multiply, and a full set is the ceiling")
    void cumulative() {
        assertEquals(7, rows(D1, D1), "two of the first degree is x2.25, not x3");

        assertEquals(9, rows(D1, D2));
        assertEquals(36, rows(D1, D2, D3), "a full set is x12, which is 36 rows of nine");
        assertEquals(MAX_ROWS, rows(D1, D2, D3));
    }

    @Test
    @DisplayName("Duplicates cannot beat a full set, so nobody has to be told not to build them")
    void duplicatesClamp() {
        assertEquals(MAX_ROWS, rows(D3, D3, D3));
        assertEquals(MAX_ROWS, rows(D2, D3, D3));
    }

    @Test
    @DisplayName("Nothing can shrink a container below the size it was built")
    void neverShrinks() {
        assertEquals(ROWS, rows(0.5), "a multiplier under one is not a licence to take rows away");
        assertEquals(ROWS, UpgradeScale.rows(ROWS, -5.0, MAX_ROWS));
        assertEquals(ROWS, UpgradeScale.rows(ROWS, Double.NaN, MAX_ROWS),
                "NaN must not round to zero rows");
        assertEquals(ROWS, UpgradeScale.rows(ROWS, D3, 1),
                "a config ceiling below the base cannot shrink every locker in a world");
    }

    @Test
    @DisplayName("A degree set to zero in config does nothing, rather than voiding the container")
    void zeroedDegree() {
        assertEquals(1.0, UpgradeScale.product(0.0, 0.0));
        assertEquals(ROWS, rows(0.0));
        assertEquals(6, rows(0.0, D2), "the misconfigured one is skipped, the other still counts");
    }

    @Test
    @DisplayName("Stack depth is a multiple of what the item stacks to, not a flat number")
    void depthFollowsTheItem() {
        assertEquals(96, cap(64, D1));
        assertEquals(128, cap(64, D2));
        assertEquals(256, cap(64, D3));
        assertEquals(MAX_STACK, cap(64, D1, D2, D3));

        assertEquals(32, cap(16, D2));
        assertEquals(64, cap(16, D3));
    }

    @Test
    @DisplayName("An item that does not stack is never deepened")
    void unstackableStaysUnstackable() {
        assertEquals(1, cap(1, D3));
        assertEquals(1, cap(1, D1, D2, D3));
    }

    @Test
    @DisplayName("Depth clamps to the ceiling the same way rows do")
    void depthClamps() {
        assertEquals(MAX_STACK, cap(64, D3, D3, D3));
        assertEquals(64, UpgradeScale.stackCap(64, D3, 16),
                "a ceiling below the item's own stack size cannot shrink it");
    }

    @Test
    @DisplayName("How many rows it takes to hold what is in the box")
    void rowsToHold() {
        assertEquals(0, UpgradeScale.rowsToHold(-1, COLS), "an empty container needs no rows");
        assertEquals(1, UpgradeScale.rowsToHold(0, COLS));
        assertEquals(1, UpgradeScale.rowsToHold(COLS - 1, COLS));
        assertEquals(2, UpgradeScale.rowsToHold(COLS, COLS), "one past a row is two rows");
        assertEquals(MAX_ROWS, UpgradeScale.rowsToHold(MAX_ROWS * COLS - 1, COLS),
                "the last slot of the largest locker needs exactly its own rows");
        assertEquals(0, UpgradeScale.rowsToHold(5, 0), "a grid with no width holds nothing");
    }

    @Test
    @DisplayName("An upgrade can come out of a box that would still hold everything")
    void removalAllowed() {
        assertFalse(UpgradeScale.stranded(3, COLS, ROWS, UpgradeScale.product(D1, D2), MAX_ROWS));
        assertFalse(UpgradeScale.stranded(-1, COLS, ROWS, 1.0, MAX_ROWS),
                "an empty locker never blocks a removal");
    }

    @Test
    @DisplayName("An upgrade is refused when taking it out would strand items past the end")
    void removalRefused() {
        assertTrue(UpgradeScale.stranded(100, COLS, ROWS, 1.0, MAX_ROWS));

        assertFalse(UpgradeScale.stranded(44, COLS, ROWS, D1, MAX_ROWS));
        assertTrue(UpgradeScale.stranded(45, COLS, ROWS, D1, MAX_ROWS));
    }

    @Test
    @DisplayName("A stack upgrade is refused when a slot is standing deeper than it would allow")
    void depthRefused() {
        assertFalse(UpgradeScale.tooDeep(0, 0, 1.0, MAX_STACK),
                "nothing overstacked, nothing to refuse");
        assertFalse(UpgradeScale.tooDeep(64, 64, 1.0, MAX_STACK),
                "an ordinary stack survives losing every stack upgrade");
        assertTrue(UpgradeScale.tooDeep(MAX_STACK, 64, 1.0, MAX_STACK));

        assertTrue(UpgradeScale.tooDeep(MAX_STACK, 64, UpgradeScale.product(D1, D2), MAX_STACK));
        assertFalse(UpgradeScale.tooDeep(192, 64, UpgradeScale.product(D1, D2), MAX_STACK));
    }
}
