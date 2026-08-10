package com.createstats.gui.pdu;

import com.createstats.power.PduMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduStatusLineTest {

    private static final int STEADY = Integer.MAX_VALUE;

    @Test
    void noBranchIsCaughtBeforeAnyDerivedNumber() {
        final String s = PduStatusLine.derive(PduMode.AUTO, false, false, false, false, 0, 0, STEADY, 1.0f, 15);
        assertTrue(s.toLowerCase().contains("wired"), s);
    }

    @Test
    void darkBranchReportsCoasting() {
        final String s = PduStatusLine.derive(PduMode.AUTO, true, true, false, false, 0, 0, STEADY, 0.0f, 15);
        assertTrue(s.contains("Not receiving power"), s);
    }

    @Test
    void offModeSaysSoRatherThanPrintingAutoNumbers() {
        final String s = PduStatusLine.derive(PduMode.OFF, true, false, false, false, 0, 0, STEADY, 0.5f, 15);
        assertTrue(s.startsWith("Distribution off"), s);
    }

    @Test
    void wireLimitIsNamedApartFromAGenerationShortfall() {
        final String s = PduStatusLine.derive(PduMode.AUTO, true, false, false, true, 2, 0, STEADY, 0.5f, 15);
        assertTrue(s.contains("Branch wiring is the limit"), s);
        assertTrue(s.contains("Upgrade the wire"), s);
    }

    @Test
    void sheddingBelowTheFloorNamesTheFloor() {
        final String s = PduStatusLine.derive(PduMode.AUTO, true, false, false, false, 3, 0, STEADY, 0.10f, 15);
        assertTrue(s.contains("Shedding 3 loads"), s);
        assertTrue(s.contains("reserve floor"), s);
    }

    @Test
    void sheddingAboveTheFloorNamesSupply() {
        final String s = PduStatusLine.derive(PduMode.AUTO, true, false, false, false, 1, 0, STEADY, 0.80f, 15);
        assertTrue(s.contains("Shedding 1 load "), s);
        assertTrue(s.contains("within supply"), s);
    }

    @Test
    void servedButDrainingCallsOutTheTimeToEmpty() {
        final String s = PduStatusLine.derive(PduMode.AUTO, true, false, false, false, 0, 0, 20 * 20, 0.5f, 15);
        assertTrue(s.contains("draining"), s);
        assertTrue(s.contains("20s to empty"), s);
    }

    @Test
    void surplusReportsTheSpare() {
        final String s = PduStatusLine.derive(PduMode.AUTO, true, false, true, false, 0, 320, STEADY, 0.9f, 15);
        assertEquals("All loads served, 320 FE/t spare.", s);
    }

    @Test
    void everythingServedWithNoSpareIsThePlainCase() {
        final String s = PduStatusLine.derive(PduMode.MANUAL, true, false, false, false, 0, 0, STEADY, 0.9f, 15);
        assertEquals("All loads served.", s);
    }

    @Test
    void durationFormatsAcrossScales() {
        assertEquals("45s", PduStatusLine.formatDuration(45 * 20));
        assertEquals("4m 20s", PduStatusLine.formatDuration((4 * 60 + 20) * 20));
        assertEquals("1h 3m", PduStatusLine.formatDuration((3600 + 3 * 60) * 20));
    }

    @Test
    void enduranceReadsChargingSteadyOrTimeToEmpty() {
        assertEquals("steady", PduStatusLine.formatEndurance(STEADY, false, 1.0f));
        assertEquals("charging", PduStatusLine.formatEndurance(STEADY, true, 0.5f));
        assertEquals("2m 0s to empty", PduStatusLine.formatEndurance(120 * 20, false, 0.5f));
    }

    @Test
    void aFullBufferExplainsWhyNothingIsFlowingIn() {
        // The reported contradiction: a large Supply headline over an intake of zero. Both are true and
        // the panel has to say why, or it reads as a fault.
        assertEquals("buffer full, intake paused", PduStatusLine.formatSupplySubtitle(0, 0.99f));
    }

    @Test
    void anEmptyBufferWithNoIntakeIsNotBlamedOnBeingFull() {
        assertEquals("nothing flowing in", PduStatusLine.formatSupplySubtitle(0, 0.20f));
    }

    @Test
    void realIntakeIsReportedAsSuch() {
        assertEquals("3,843 FE/t flowing in", PduStatusLine.formatSupplySubtitle(3843, 0.5f));
    }
}
