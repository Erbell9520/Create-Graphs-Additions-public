package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeSupplyBudgetTest {
    @Test
    @DisplayName("a circuit with a source of its own has no ceiling at all")
    void anUncappedSupplyMeansNoCeiling() {
        assertEquals(FeGridSolver.NO_CEILING, FeSupplyBudget.ceilingOf(true, 1_024));
        assertEquals(FeGridSolver.NO_CEILING, FeSupplyBudget.ceilingOf(false),
                "and a circuit with no capping box on it is the ordinary case");
    }

    @Test
    @DisplayName("two boxes feeding one branch let both their settings through")
    void severalCappingSuppliesAreSummed() {
        assertEquals(1_536, FeSupplyBudget.ceilingOf(false, 1_024, 512));
    }

    @Test
    @DisplayName("a shut box contributes nothing, which shuts the branch")
    void aShutBoxContributesNothing() {
        assertEquals(0, FeSupplyBudget.ceilingOf(false, 0));
    }

    @Test
    @DisplayName("the allowance is spent once across every caller, and refills on a new tick")
    void theAllowanceIsSharedAndRefills() {
        final FeSupplyBudget budget = new FeSupplyBudget();
        final int ceiling = 1_024;

        assertEquals(1_024, budget.remaining(ceiling, 5L));

        budget.spend(ceiling, 5L, 900);
        assertEquals(124, budget.remaining(ceiling, 5L), "the seam sees what the solve left");

        budget.spend(ceiling, 5L, 9_999);
        assertEquals(0, budget.remaining(ceiling, 5L), "and never goes below nothing");

        assertEquals(1_024, budget.remaining(ceiling, 6L), "a new tick refills it");
    }

    @Test
    @DisplayName("a ceiling that moves mid-tick does not refill the allowance")
    void aMovedDialDoesNotRefillTheTick() {
        final FeSupplyBudget budget = new FeSupplyBudget();
        budget.remaining(1_024, 5L);
        budget.spend(1_024, 5L, 1_024);
        assertEquals(0, budget.remaining(4_096, 5L), "still spent, whatever the dial now says");
        assertEquals(4_096, budget.remaining(4_096, 6L), "and the new figure applies next tick");
    }

    @Test
    @DisplayName("FE pushed in from outside is credited, because it never crossed a box")
    void foreignArrivalsAreCredited() {
        final FeSupplyBudget budget = new FeSupplyBudget();
        final int ceiling = 1_024;

        budget.spend(ceiling, 5L, 1_024);
        assertEquals(0, budget.remaining(ceiling, 5L));

        budget.credit(ceiling, 5L, 500);
        assertEquals(500, budget.remaining(ceiling, 5L));

        assertEquals(1_024, budget.remaining(ceiling, 6L), "it does not carry over");
    }

    @Test
    @DisplayName("nothing is spent or credited on a circuit that has no ceiling")
    void anUnlimitedCircuitIsUntouched() {
        final FeSupplyBudget budget = new FeSupplyBudget();
        budget.spend(FeGridSolver.NO_CEILING, 5L, 9_999);
        budget.credit(FeGridSolver.NO_CEILING, 5L, 9_999);
        assertEquals(FeGridSolver.NO_CEILING, budget.remaining(FeGridSolver.NO_CEILING, 5L));
    }
}
