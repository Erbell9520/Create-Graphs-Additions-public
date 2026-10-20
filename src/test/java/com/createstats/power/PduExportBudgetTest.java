package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduExportBudgetTest {
    @Test
    @DisplayName("a multi-pass serve cannot spend the allowance once per pass")
    void aMultiPassServeCannotSpendItPerPass() {
        final PduExportBudget budget = new PduExportBudget();

        int moved = 0;
        for (int pass = 0; pass < 6; pass++) {
            final int room = budget.remaining(1024, 100L);
            final int take = Math.min(room, 400);
            budget.spend(1024, 100L, take);
            moved += take;
        }
        assertEquals(1024, moved, "the whole tick's export is the allowance, not the allowance per pass");
        assertEquals(0, budget.remaining(1024, 100L));
    }

    @Test
    @DisplayName("eight outlets share one pool rather than each getting their own")
    void everyOutletSharesOnePool() {
        final PduExportBudget budget = new PduExportBudget();
        int moved = 0;
        for (int leg = 0; leg < 8; leg++) {
            final int room = budget.remaining(5000, 7L);
            final int take = Math.min(room, 5000);
            budget.spend(5000, 7L, take);
            moved += take;
        }
        assertEquals(5000, moved, "one board, one buffer, one spare figure, one allowance");
    }

    @Test
    @DisplayName("the allowance is adopted once a tick and does not shrink as it is spent")
    void theAllowanceIsAdoptedOncePerTick() {
        final PduExportBudget budget = new PduExportBudget();
        assertEquals(2000, budget.remaining(2000, 40L));
        budget.spend(2000, 40L, 1500);

        assertEquals(500, budget.remaining(500, 40L), "a mid-tick figure must be ignored");
    }

    @Test
    @DisplayName("a new tick refills, and refilling is keyed on game time rather than call order")
    void aNewTickRefills() {
        final PduExportBudget budget = new PduExportBudget();
        budget.spend(800, 10L, 800);
        assertEquals(0, budget.remaining(800, 10L));
        assertEquals(800, budget.remaining(800, 11L));

        budget.spend(800, 12L, 300);
        assertEquals(500, budget.remaining(800, 12L));
    }

    @Test
    @DisplayName("NO_CEILING passes everything through and books nothing")
    void noCeilingPassesThrough() {
        final PduExportBudget budget = new PduExportBudget();
        assertEquals(FeGridSolver.NO_CEILING, budget.remaining(FeGridSolver.NO_CEILING, 3L));
        budget.spend(FeGridSolver.NO_CEILING, 3L, 1_000_000);
        assertEquals(FeGridSolver.NO_CEILING, budget.remaining(FeGridSolver.NO_CEILING, 3L));
        assertEquals(0, budget.spent());
    }

    @Test
    @DisplayName("spending more than is left never drives the remainder negative")
    void overspendClampsAtZero() {
        final PduExportBudget budget = new PduExportBudget();
        budget.spend(100, 1L, 100_000);
        assertEquals(0, budget.remaining(100, 1L));
        assertEquals(100, budget.spent());
    }

    @Test
    @DisplayName("held means the gate stopped it, not that nothing wanted it")
    void heldSeparatesTheGateFromTheAppetite() {
        final PduExportBudget idle = new PduExportBudget();
        idle.refill(4096, 1L);
        assertFalse(idle.isHeld(4096), "an allowance equal to the wire is the wire, not a hold");

        final PduExportBudget unspent = new PduExportBudget();
        unspent.refill(512, 1L);
        assertFalse(unspent.isHeld(4096), "nothing asked, so nothing was held back");

        final PduExportBudget held = new PduExportBudget();
        held.spend(512, 1L, 512);
        assertTrue(held.isHeld(4096), "the far end emptied a gate far below the conductor");

        final PduExportBudget off = new PduExportBudget();
        off.spend(FeGridSolver.NO_CEILING, 1L, 999);
        assertFalse(off.isHeld(4096), "policy disabled in config is not a hold");
    }
}
