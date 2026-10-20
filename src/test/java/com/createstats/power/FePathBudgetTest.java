package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePathBudgetTest {
    private static final int DUCT = 16_384;
    private static final int CABLE = 4_096;
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final long TICK = 12L;

    private static long key(int index) {
        return 1_000L + index;
    }

    private static long[] keys(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = key(i);
        }
        return out;
    }

    private static boolean[] suppliesAt(int n, int... at) {
        final boolean[] out = new boolean[n];
        for (int one : at) {
            out[one] = true;
        }
        return out;
    }

    private static FePathBudget forkedTrunk() {
        final int[] parent = {-1,   0, 1, 2, 2, 3, 4};
        final int[] rate = {NONE, DUCT, DUCT, CABLE, CABLE, CABLE, CABLE};
        return new FePathBudget(keys(7), parent, rate, suppliesAt(7, 0));
    }

    @Test
    @DisplayName("a spur carries its own grade even though the circuit has no throat")
    void aSpurIsHeldToItsGrade() {
        final FePathBudget budget = forkedTrunk();
        assertEquals(CABLE, budget.remaining(key(5), TICK));
        assertEquals(CABLE, budget.remaining(key(6), TICK),
                "and the other spur gets its own, because they are conductors in parallel");
    }

    @Test
    @DisplayName("two machines on one spur share that spur")
    void oneSpurIsSharedAlongIt() {
        final FePathBudget budget = forkedTrunk();
        budget.spend(key(5), 3_000, TICK);
        assertEquals(1_096, budget.remaining(key(5), TICK));
        assertEquals(CABLE, budget.remaining(key(6), TICK), "the far spur is untouched");
    }

    @Test
    @DisplayName("the trunk is shared by everything beyond it and is not dragged down by a spur")
    void theTrunkIsSharedAndNotClamped() {
        final FePathBudget budget = forkedTrunk();

        budget.spend(key(5), CABLE, TICK);
        assertEquals(0, budget.remaining(key(5), TICK));
        assertEquals(CABLE, budget.remaining(key(6), TICK),
                "a Bus Duct with a thin spur hanging off it is still a Bus Duct");
    }

    @Test
    @DisplayName("the trunk's own grade binds once enough spurs are drawing")
    void theTrunkBindsInTheEnd() {
        final int[] parent = {-1, 0, 1, 1, 1, 1, 1};
        final int[] rate = {NONE, DUCT, CABLE, CABLE, CABLE, CABLE, CABLE};
        final FePathBudget budget = new FePathBudget(keys(7), parent, rate, suppliesAt(7, 0));

        for (int spur = 2; spur <= 5; spur++) {
            assertEquals(CABLE, budget.remaining(key(spur), TICK));
            budget.spend(key(spur), CABLE, TICK);
        }
        assertEquals(0, budget.remaining(key(6), TICK), "the duct is spent, so the fifth spur is dark");
    }

    @Test
    @DisplayName("the tree is rooted on the supply, not on wherever the flood started")
    void theTreeIsRootedOnTheSupply() {
        final int[] parent = {-1, 0, 1, 2};
        final int[] rate = {NONE, CABLE, DUCT, NONE};

        final FePathBudget budget = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 3));

        assertEquals(CABLE, budget.remaining(key(0), TICK), "the thin end is what binds");
        budget.spend(key(0), 1_000, TICK);
        assertEquals(CABLE - 1_000, budget.remaining(key(0), TICK));
        assertEquals(DUCT - 1_000, budget.remaining(key(1), TICK),
                "and the duct between them carried the same thousand");
    }

    @Test
    @DisplayName("a new tick refills every conductor")
    void aNewTickRefills() {
        final FePathBudget budget = forkedTrunk();
        budget.spend(key(5), CABLE, TICK);
        assertEquals(0, budget.remaining(key(5), TICK));
        assertEquals(CABLE, budget.remaining(key(5), TICK + 1));
    }

    @Test
    @DisplayName("a circuit with no supply of its own charges nothing until a manager names its anchor")
    void aDrivenLegNeedsAnAnchor() {
        final int[] parent = {-1, 0, 1, 2};
        final int[] rate = {NONE, CABLE, CABLE, CABLE};
        final FePathBudget budget = new FePathBudget(keys(4), parent, rate, suppliesAt(4));

        assertEquals(NONE, budget.remaining(key(3), TICK), "nothing to measure from yet");
        budget.rootOn(key(0));
        assertEquals(CABLE, budget.remaining(key(3), TICK), "the stud wire lands on node 0");
    }

    @Test
    @DisplayName("a manager's anchor wins over a battery sitting on the leg")
    void anAnchorWinsOverALegBattery() {
        final int[] parent = {-1, 0, 1, 2};
        final int[] rate = {NONE, DUCT, DUCT, CABLE};
        final FePathBudget budget = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 3));

        assertEquals(CABLE, budget.remaining(key(1), TICK), "measured through the bank's own tail");
        budget.rootOn(key(0));
        assertEquals(DUCT, budget.remaining(key(1), TICK), "and now through the duct it is actually on");
    }

    @Test
    @DisplayName("re-rooting clears the tick, because the edges have changed hands")
    void rerootingClearsTheTick() {
        final FePathBudget budget = forkedTrunk();
        budget.spend(key(5), CABLE, TICK);
        assertEquals(0, budget.remaining(key(5), TICK));
        budget.rootOn(key(6));
        assertEquals(CABLE, budget.remaining(key(5), TICK));
    }

    @Test
    @DisplayName("joints with no grade constrain nothing")
    void ungradedJointsAreFree() {
        final int[] parent = {-1, 0, 1};
        final int[] rate = {NONE, NONE, NONE};
        final FePathBudget budget = new FePathBudget(keys(3), parent, rate, suppliesAt(3, 0));
        assertEquals(NONE, budget.remaining(key(2), TICK));
        assertFalse(budget.hasGradedEdges(), "so the caller can skip wrapping anything at all");
    }

    @Test
    @DisplayName("a node this circuit has never heard of is not throttled")
    void anUnknownNodeIsFree() {
        final FePathBudget budget = forkedTrunk();
        assertEquals(NONE, budget.remaining(99_999L, TICK));
        budget.spend(99_999L, 1_000, TICK);
        assertEquals(CABLE, budget.remaining(key(5), TICK), "and it charged nothing on the way past");
        assertTrue(budget.hasGradedEdges());
    }

    @Test
    @DisplayName("the empty budget answers no ceiling to everything")
    void theEmptyBudgetIsFree() {
        assertEquals(NONE, FePathBudget.NONE.remaining(key(0), TICK));
        assertFalse(FePathBudget.NONE.hasGradedEdges());
    }
}
