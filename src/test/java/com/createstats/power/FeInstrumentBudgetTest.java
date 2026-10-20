package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeInstrumentBudgetTest {
    private static final long BOX = 17L;
    private static final long OTHER = 23L;

    private static java.util.function.LongToIntFunction cap(int fe) {
        return box -> fe;
    }

    @Test
    @DisplayName("a device behind no box at all is not constrained")
    void noBoxesMeansNoCeiling() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        assertEquals(FeGridSolver.NO_CEILING, budget.remaining(List.of(), cap(1_024), 5L));
    }

    @Test
    @DisplayName("the whole allowance is there on the first pass of a tick")
    void theFirstPassSeesTheWholeCap() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        assertEquals(1_024, budget.remaining(List.of(BOX), cap(1_024), 5L));
    }

    @Test
    @DisplayName("three passes in one tick share one allowance, and cannot beat it between them")
    void everyPassInOneTickSharesTheAllowance() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);

        int moved = 0;
        for (int pass = 0; pass < 3; pass++) {
            final int room = budget.remaining(caps, cap(1_024), 5L);
            final int took = Math.min(room, 900);
            budget.spend(caps, took, room < 900, 5L);
            moved += took;
        }
        assertEquals(1_024, moved, "three passes of 900 still only move the setting");
        assertEquals(0, budget.remaining(caps, cap(1_024), 5L));
    }

    @Test
    @DisplayName("a new tick refills it")
    void aNewTickRefills() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        budget.spend(caps, 1_024, false, 5L);
        assertEquals(0, budget.remaining(caps, cap(1_024), 5L));
        assertEquals(1_024, budget.remaining(caps, cap(1_024), 6L));
    }

    @Test
    @DisplayName("two boxes in a line: the smaller decides, and both are charged for what crossed")
    void twoBoxesInALine() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX, OTHER);
        final java.util.function.LongToIntFunction ceilings =
                box -> box == BOX ? 4_096 : 1_024;

        assertEquals(1_024, budget.remaining(caps, ceilings, 5L), "the tighter of the two");
        budget.spend(caps, 1_024, false, 5L);
        assertEquals(0, budget.remaining(caps, ceilings, 5L));

        assertEquals(3_072, budget.remaining(List.of(BOX), ceilings, 5L));
    }

    @Test
    @DisplayName("a box that has been mined stops constraining rather than shutting the branch")
    void aMissingBoxStopsConstraining() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        assertEquals(FeGridSolver.NO_CEILING,
                budget.remaining(List.of(BOX), cap(FeGridSolver.NO_CEILING), 5L));
    }

    @Test
    @DisplayName("a shut box shuts the group, and stays shut however it is asked")
    void aShutBoxShutsTheGroup() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        assertEquals(0, budget.remaining(caps, cap(0), 5L));
        budget.spend(caps, 500, false, 5L);
        assertEquals(0, budget.remaining(caps, cap(0), 5L));
    }

    @Test
    @DisplayName("nothing moved spends nothing, and the total saturates rather than wrapping")
    void spendingIsGuarded() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        budget.spend(caps, 0, false, 5L);
        budget.spend(caps, -700, false, 5L);
        assertEquals(1_024, budget.remaining(caps, cap(1_024), 5L), "neither of those crossed");

        budget.spend(caps, Integer.MAX_VALUE, false, 5L);
        budget.spend(caps, Integer.MAX_VALUE, false, 5L);
        assertEquals(0, budget.remaining(caps, cap(1_024), 5L), "and two of those do not wrap round");
    }

    @Test
    @DisplayName("a box that held a pass back says so, and stops saying it on the next tick")
    void theClipIsCarriedForTheTick() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);

        assertFalse(budget.clipped(BOX, 5L), "nothing has crossed yet");
        budget.spend(caps, 1_024, true, 5L);
        assertTrue(budget.clipped(BOX, 5L));
        assertFalse(budget.clipped(BOX, 6L), "and a new tick starts clean");
    }

    @Test
    @DisplayName("a pass that was not held back does not raise the flag")
    void anUnclippedPassRaisesNothing() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        final List<Long> caps = List.of(BOX);
        budget.spend(caps, 300, false, 5L);
        assertFalse(budget.clipped(BOX, 5L));
    }

    @Test
    @DisplayName("a clip on a line of boxes is reported by every box on it")
    void aClipMarksEveryBoxOnTheLine() {
        final FeInstrumentBudget budget = new FeInstrumentBudget();
        budget.spend(List.of(BOX, OTHER), 1_024, true, 5L);
        assertTrue(budget.clipped(BOX, 5L));
        assertTrue(budget.clipped(OTHER, 5L));
    }
}
