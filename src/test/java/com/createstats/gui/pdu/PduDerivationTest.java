package com.createstats.gui.pdu;

import com.createstats.power.PduMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The working shown on the Flow tab. */
class PduDerivationTest {

    /** A healthy rig: 15 ranges on HIGH plus 3 lamps, buffer near full. */
    private static PduDerivation.Inputs healthy() {
        return new PduDerivation.Inputs(PduMode.AUTO, true, 3843, 0, 8192, 8192, 3843, 3843,
                18, 0, 508157, 512000, 0.15f, true, false);
    }

    private static String joined(List<PduDerivation.Row> rows) {
        final StringBuilder sb = new StringBuilder();
        for (PduDerivation.Row r : rows) {
            sb.append(r.label()).append('|').append(r.value()).append('|').append(r.note()).append('\n');
        }
        return sb.toString();
    }

    private static PduDerivation.Row find(List<PduDerivation.Row> rows, String labelStartsWith) {
        for (PduDerivation.Row r : rows) {
            if (r.label().startsWith(labelStartsWith)) {
                return r;
            }
        }
        throw new AssertionError("no row starting with " + labelStartsWith + " in\n" + joined(rows));
    }

    @Test
    @DisplayName("an unwired panel says so and stops, rather than printing a page of zeroes")
    void unwiredStopsEarly() {
        final PduDerivation.Inputs in = new PduDerivation.Inputs(PduMode.AUTO, false, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 512000, 0.15f, false, false);
        final List<PduDerivation.Row> rows = PduDerivation.derive(in);
        assertEquals(1, rows.size());
        assertEquals(PduDerivation.Kind.VERDICT, rows.get(0).kind());
    }

    @Test
    @DisplayName("outside Active Auto the budget working is not shown, because there is no budget")
    void manualDoesNotInventABudget() {
        final PduDerivation.Inputs in = new PduDerivation.Inputs(PduMode.MANUAL, true, 3843, 100, 8192,
                0, 3843, 0, 18, 0, 508157, 512000, 0.15f, false, false);
        final String text = joined(PduDerivation.derive(in));
        assertFalse(text.contains("Run budget"), "manual mode must not print an Auto budget:\n" + text);
        assertTrue(text.contains("Active Auto is not running"));
    }

    @Test
    @DisplayName("a full buffer explains its own zero intake instead of looking broken")
    void fullBufferExplainsZeroIntake() {
        final PduDerivation.Row row = find(PduDerivation.derive(healthy()), "Flowing in now");
        assertEquals("0 FE/t", row.value());
        assertTrue(row.note().contains("buffer is full"), "note was: " + row.note());
    }

    @Test
    @DisplayName("the reserve's share of the budget is broken out, since it is not synced on its own")
    void reserveContributionIsRecovered() {
        // Budget 8,192 against generation 3,843, so 4,349 of it is the buffer paying.
        final PduDerivation.Row row = find(PduDerivation.derive(healthy()), "from the reserve");
        assertEquals("4,349 FE/t", row.value());
    }

    @Test
    @DisplayName("the reserve line is omitted when the branch really is living off generation")
    void noReserveLineWhenGenerationCoversIt() {
        final PduDerivation.Inputs in = new PduDerivation.Inputs(PduMode.AUTO, true, 4000, 4000, 8192,
                4000, 3843, 3843, 18, 0, 200000, 512000, 0.15f, false, false);
        assertFalse(joined(PduDerivation.derive(in)).contains("from the reserve"));
    }

    @Test
    @DisplayName("a shed branch names the wiring or the supply, because the two have opposite fixes")
    void verdictNamesTheRightCulprit() {
        final PduDerivation.Inputs wire = new PduDerivation.Inputs(PduMode.AUTO, true, 9000, 8192, 2048,
                2048, 6000, 2000, 4, 14, 400000, 512000, 0.15f, true, true);
        assertTrue(joined(PduDerivation.derive(wire)).contains("wiring is the limit"));

        final PduDerivation.Inputs supply = new PduDerivation.Inputs(PduMode.AUTO, true, 500, 500, 8192,
                500, 6000, 480, 2, 16, 40000, 512000, 0.15f, false, false);
        final String text = joined(PduDerivation.derive(supply));
        assertTrue(text.contains("Supply is the limit"));
        assertTrue(text.contains("Build more generation"), "the note must say what to do:\n" + text);
    }

    @Test
    @DisplayName("a healthy branch says everything is running")
    void healthyBranchSaysSo() {
        assertTrue(joined(PduDerivation.derive(healthy())).contains("Everything on the branch is running"));
    }

    @Test
    @DisplayName("shed demand is the difference between wanted and admitted, never negative")
    void shedDemandCannotGoNegative() {
        // servedDemand above demand is possible for a tick, since one is an EMA and the other is not.
        final PduDerivation.Inputs odd = new PduDerivation.Inputs(PduMode.AUTO, true, 4000, 4000, 8192,
                4000, 100, 900, 3, 1, 200000, 512000, 0.15f, true, false);
        assertEquals("0 FE/t", find(PduDerivation.derive(odd), "shed (").value());
    }

    @Test
    @DisplayName("over budget is stated as such rather than as a negative headroom")
    void overBudgetReadsAsOverBudget() {
        final PduDerivation.Inputs over = new PduDerivation.Inputs(PduMode.AUTO, true, 1000, 1000, 8192,
                1000, 4000, 3000, 10, 2, 300000, 512000, 0.15f, false, false);
        final PduDerivation.Row row = find(PduDerivation.derive(over), "Over budget by");
        assertEquals("2,000 FE/t", row.value());
    }

    @Test
    @DisplayName("an empty branch is not reported as a supply failure")
    void emptyBranchIsNotBlamedOnSupply() {
        final PduDerivation.Inputs empty = new PduDerivation.Inputs(PduMode.AUTO, true, 3843, 0, 8192,
                8192, 0, 0, 0, 0, 508157, 512000, 0.15f, true, false);
        assertTrue(joined(PduDerivation.derive(empty)).contains("No devices on the branch"));
    }
}
