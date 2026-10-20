package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduExportShareTest {
    private static int round(PduExportBudget budget, int allowance, long now, int[] want,
                             int cursor) {
        final int room = budget.remaining(allowance, now);
        if (room >= FeGridSolver.NO_CEILING) {
            int all = 0;
            for (int w : want) {
                all += w;
            }
            return all;
        }
        final int[] share = PduLegShare.split(want, room, cursor);
        int moved = 0;
        for (int i = 0; i < want.length; i++) {
            final int took = Math.min(want[i], share[i]);
            moved += took;
            want[i] -= took;
        }
        budget.spend(allowance, now, moved);
        return moved;
    }

    @Test
    @DisplayName("five tier rounds and a fill pass cannot export five tick's worth of spare")
    void roundsCannotEachSpendTheWholeAllowance() {
        final PduExportBudget budget = new PduExportBudget();
        final int spare = 2048;
        int moved = 0;
        for (int band = 0; band < 6; band++) {
            moved += round(budget, spare, 500L, new int[]{100_000}, band);
        }
        assertEquals(spare, moved, "the tick's spare is the tick's spare, however many passes there are");
    }

    @Test
    @DisplayName("three boards on three legs share one spare figure rather than getting one each")
    void boardsShareOnePool() {
        final PduExportBudget budget = new PduExportBudget();
        final int spare = 900;
        final int[] want = {5000, 5000, 5000};
        final int moved = round(budget, spare, 7L, want, 0);
        assertEquals(spare, moved);
        assertEquals(0, budget.remaining(spare, 7L));
    }

    @Test
    @DisplayName("no board is starved by another board's appetite")
    void noBoardIsStarved() {
        final PduExportBudget budget = new PduExportBudget();
        final int[] want = {100_000, 400, 400};
        final int[] before = want.clone();
        round(budget, 1500, 3L, want, 0);
        for (int i = 1; i < want.length; i++) {
            assertTrue(want[i] < before[i], "board " + i + " was served nothing");
        }
    }

    @Test
    @DisplayName("what one round leaves unspent is still there for the next one")
    void unspentRoomCarries() {
        final PduExportBudget budget = new PduExportBudget();
        final int spare = 1000;
        assertEquals(300, round(budget, spare, 9L, new int[]{300}, 0));
        assertEquals(700, budget.remaining(spare, 9L), "a modest first round must not forfeit the rest");
        assertEquals(700, round(budget, spare, 9L, new int[]{100_000}, 1));
    }

    @Test
    @DisplayName("a new tick starts a new allowance, so export is a rate and not a total")
    void aNewTickIsANewAllowance() {
        final PduExportBudget budget = new PduExportBudget();
        int moved = 0;
        for (long tick = 0; tick < 4; tick++) {
            moved += round(budget, 512, tick, new int[]{100_000}, 0);
        }
        assertEquals(4 * 512, moved);
    }

    @Test
    @DisplayName("with the policy off the gate does nothing and the wire is the only limit")
    void policyOffPassesThrough() {
        final PduExportBudget budget = new PduExportBudget();
        assertEquals(9000, round(budget, FeGridSolver.NO_CEILING, 1L, new int[]{9000}, 0));
        assertEquals(9000, round(budget, FeGridSolver.NO_CEILING, 1L, new int[]{9000}, 1));
    }
}
