package com.createstats.chunkloader.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownPlanTest {
    @Test
    @DisplayName("never past 3 x 3 x 3, the base at its bottom centre")
    void neverPastThreeCubed() {
        for (int n = 0; n <= 25; n++) {
            for (CrownPlan.Cell c : CrownPlan.cellsFor(n)) {
                assertTrue(Math.abs(c.right()) <= 1 && Math.abs(c.front()) <= 1 && c.up() >= 0 && c.up() <= 2,
                        "outside the 3 x 3 x 3 at " + n + ": " + c);
            }
        }
        assertEquals(21, CrownPlan.cellsFor(25).size(), "the ULTIMATE claims 21 of the 27 cells");
    }

    @Test
    @DisplayName("monotone in the count: more chunks never give a cell back")
    void monotoneInTheCount() {
        for (int n = 0; n < 25; n++) {
            assertTrue(new HashSet<>(CrownPlan.cellsFor(n + 1)).containsAll(CrownPlan.cellsFor(n)),
                    "the form at " + (n + 1) + " drops a cell the form at " + n + " had");
        }
    }

    @Test
    @DisplayName("the cell behind the studs is never claimed, and at ground level only the base and the horns are")
    void theBackCellIsNeverClaimed() {
        for (int n = 0; n <= 25; n++) {
            final List<CrownPlan.Cell> cells = CrownPlan.cellsFor(n);
            assertFalse(cells.contains(CrownPlan.BEHIND_STUDS), "the HV Lines' way in, at " + n);
            for (CrownPlan.Cell c : cells) {
                if (c.up() == 0) {
                    assertTrue(c.front() == 0 && (c.right() == 0 || Math.abs(c.right()) == 1),
                            "only the base and the two horn cells at ground level, at " + n + ": " + c);
                }
            }
        }
    }

    @Test
    @DisplayName("the counts: one block to 5, then the stem layer at 6, the corners at 11, the top at 16")
    void theStepsAreConceptBs() {
        for (int n = 1; n <= 5; n++) {
            assertEquals(List.of(new CrownPlan.Cell(0, 0, 0)), CrownPlan.cellsFor(n), "one block at " + n);
        }
        assertEquals(8, CrownPlan.cellsFor(6).size(), "base, the cell above, four beside it, two horns");
        assertEquals(8, CrownPlan.cellsFor(10).size());
        assertEquals(12, CrownPlan.cellsFor(11).size(), "and the four corners at that height");
        assertEquals(12, CrownPlan.cellsFor(15).size());
        assertEquals(21, CrownPlan.cellsFor(16).size(), "and the whole top layer");
        assertTrue(CrownPlan.cellsFor(6).contains(new CrownPlan.Cell(1, 0, 0)));
        assertTrue(CrownPlan.cellsFor(6).contains(new CrownPlan.Cell(-1, 0, 0)));
        assertEquals(new CrownPlan.Cell(0, 0, 0), CrownPlan.cellsFor(25).get(0), "the base first");
    }

    @Test
    @DisplayName("every claimed cell's chain reaches the base through cells claimed at the same count")
    void everyChainReachesTheBase() {
        for (int n = 1; n <= 25; n++) {
            final Set<CrownPlan.Cell> claimed = new HashSet<>(CrownPlan.cellsFor(n));
            for (CrownPlan.Cell c : claimed) {
                CrownPlan.Cell at = c;
                int steps = 0;
                final int distance = Math.abs(c.right()) + c.up() + Math.abs(c.front());
                while (!at.isBase()) {
                    final int[] step = CrownPlan.parentStep(at);
                    assertNotNull(step);
                    assertEquals(1, Math.abs(step[0]) + Math.abs(step[1]) + Math.abs(step[2]), "one face step");
                    at = new CrownPlan.Cell(at.right() + step[0], at.up() + step[1], at.front() + step[2]);
                    assertTrue(claimed.contains(at), "the chain from " + c + " leaves the claim at " + n + ": " + at);
                    assertTrue(++steps <= distance, "a chain that wanders from " + c);
                }
                assertEquals(distance, steps, "every step goes toward the base, from " + c);
            }
        }
        assertNull(CrownPlan.parentStep(new CrownPlan.Cell(0, 0, 0)));
    }

    @Test
    @DisplayName("something in the way: the form stops at the largest count whose cells are free, the hold does not")
    void theFormStopsWhereSomethingIsInTheWay() {
        assertEquals(0, CrownPlan.formCount(0, c -> true));
        assertEquals(25, CrownPlan.formCount(25, c -> true));
        assertEquals(3, CrownPlan.formCount(3, c -> false), "one block needs nothing round it");
        final CrownPlan.Cell corner = new CrownPlan.Cell(1, 1, 1);
        assertEquals(10, CrownPlan.formCount(13, c -> !c.equals(corner)), "a corner blocked: it stops at 10");
        assertEquals(corner, CrownPlan.firstBlocked(13, c -> !c.equals(corner)));
        final CrownPlan.Cell horn = new CrownPlan.Cell(-1, 0, 0);
        assertEquals(5, CrownPlan.formCount(25, c -> !c.equals(horn)), "a horn cell blocked: one block");
        assertEquals(15, CrownPlan.formCount(25, c -> c.up() < 2), "the top layer blocked: 15");
        assertNull(CrownPlan.firstBlocked(25, c -> true));
        assertNull(CrownPlan.firstBlocked(4, c -> false), "nothing can block a one-block form");
    }
}
