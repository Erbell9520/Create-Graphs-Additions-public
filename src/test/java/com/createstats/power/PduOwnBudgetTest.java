package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PduOwnBudgetTest {
    private static final int CAP = 512_000;
    private static final int FLOOR = 76_800;
    private static final int MARK = 230_400;
    private static final int WIRE = 16_384;

    private static PduOwnBudget.Inputs at(int energy, int gen) {
        return new PduOwnBudget.Inputs(gen, gen, 51, WIRE, energy, FLOOR, 600, energy > MARK, 0, 0, 5_000);
    }

    @Test
    void theAdmitBudgetIsGenerationAtEveryBufferLevel() {
        for (int energy : new int[] {FLOOR, MARK - 1, MARK + 1, 400_000, CAP}) {
            assertEquals(2_149, PduOwnBudget.of(at(energy, 2_200)).admit(), "buffer " + energy);
        }
    }

    @Test
    void runAndCriticalAreAsTheyWere() {
        final PduOwnBudget.Budgets full = PduOwnBudget.of(at(CAP, 2_200));
        assertEquals(WIRE - 51, full.run(), "above the mark a running device keeps the wire");
        assertEquals(WIRE, full.critical());
        final PduOwnBudget.Budgets lean = PduOwnBudget.of(at(150_000, 2_200));
        assertEquals(Math.min(WIRE, 2_200 - 51 + (150_000 - FLOOR) / 600), lean.run());
        assertEquals(Math.min(WIRE, 2_200 + (150_000 - FLOOR) / 600), lean.critical());
    }

    @Test
    void theRestoreMarginIsMeasuredAgainstTheUncappedAdmitSupply() {
        final PduOwnBudget.Budgets clear = PduOwnBudget.of(at(CAP, 2_200));
        assertEquals(clear.admit(), clear.restoreSupply());
        assertEquals(220, PduAutoController.restoreMargin(220, clear.admit(), clear.restoreSupply()));

        final PduOwnBudget.Budgets wired = PduOwnBudget.of(at(CAP, 40_000));
        assertEquals(WIRE, wired.admit());
        assertEquals(0, PduAutoController.restoreMargin(4_000, wired.admit(), wired.restoreSupply()));
    }

    @Test
    void theForecastsDropComesOffTheAdmitBudgetOnly() {
        final PduOwnBudget.Inputs dusk = new PduOwnBudget.Inputs(3_000, 1_000, 51, WIRE, 400_000, FLOOR, 600,
                true, 0, 0, 5_000);
        final PduOwnBudget.Budgets b = PduOwnBudget.of(dusk);
        assertEquals(1_000 - 51, b.admit());
        assertEquals(WIRE - 51, b.run(), "a running device is never pulsed for a sunset (PR-04 item 8)");
    }
}
