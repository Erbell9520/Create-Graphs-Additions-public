package com.createstats.registry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRowsTest {
    private static final String GAP = "-";

    @Test
    void eachRowStartsOnAFreshGridRow() {
        final List<String> grid = CatalogRows.pad(List.of(List.of("a", "b"), List.of("c")), GAP);

        assertEquals(2 * CatalogRows.COLUMNS, grid.size());
        assertEquals(List.of("a", "b", GAP, GAP, GAP, GAP, GAP, GAP, GAP), grid.subList(0, 9));
        assertEquals(List.of("c", GAP, GAP, GAP, GAP, GAP, GAP, GAP, GAP), grid.subList(9, 18));
    }

    @Test
    void aFullRowNeedsNoPadding() {
        final List<String> nine = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertEquals(nine, CatalogRows.pad(List.of(nine), GAP));
    }

    @Test
    void aRowWithEverythingHiddenTakesNoSpace() {
        final List<String> grid = CatalogRows.pad(List.of(List.of("a"), List.of(), List.of("b")), GAP);

        assertEquals(2 * CatalogRows.COLUMNS, grid.size());
        assertEquals("a", grid.get(0));
        assertEquals("b", grid.get(9));
    }

    @Test
    void aFamilyLongerThanARowWrapsInsteadOfLosingItems() {
        final List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add("i" + i);
        }
        final List<String> grid = CatalogRows.pad(List.of(ten), GAP);

        assertEquals(2 * CatalogRows.COLUMNS, grid.size());
        assertEquals(ten, grid.subList(0, 10));
        assertTrue(grid.subList(10, 18).stream().allMatch(GAP::equals));
    }

    @Test
    void noRowsIsAnEmptyGrid() {
        assertTrue(CatalogRows.pad(List.<List<String>>of(), GAP).isEmpty());
    }
}
