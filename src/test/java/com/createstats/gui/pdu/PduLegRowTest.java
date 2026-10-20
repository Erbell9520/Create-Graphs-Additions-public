package com.createstats.gui.pdu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduLegRowTest {
    @Test
    @DisplayName("a stud with nothing on it is never dressed up as a fault")
    void unwiredBeatsEverything() {
        assertEquals(PduLegRow.State.UNWIRED, PduLegRow.stateOf(false, false, true, true));
        assertFalse(PduLegRow.State.UNWIRED.isAlarm());
    }

    @Test
    @DisplayName("a trip outranks an open breaker, because only one of them happened on its own")
    void trippedBeatsOpen() {
        assertEquals(PduLegRow.State.TRIPPED, PduLegRow.stateOf(true, false, true, false));
        assertTrue(PduLegRow.State.TRIPPED.isAlarm());
    }

    @Test
    @DisplayName("a breaker the player opened is not an alarm")
    void openIsAChoiceNotAFault() {
        assertEquals(PduLegRow.State.OPEN, PduLegRow.stateOf(true, false, false, false));
        assertFalse(PduLegRow.State.OPEN.isAlarm(),
                "a switch the player threw must not read as something going wrong");
    }

    @Test
    @DisplayName("a live leg over its conductor reports capacity, not health")
    void capacityIsReported() {
        assertEquals(PduLegRow.State.AT_CAPACITY, PduLegRow.stateOf(true, true, false, true));
        assertEquals(PduLegRow.State.LIVE, PduLegRow.stateOf(true, true, false, false));
    }

    @Test
    @DisplayName("at capacity says what to do, since the obvious fix is the wrong one")
    void capacityDetailNamesTheRealFix() {
        final String detail = PduLegRow.detail(PduLegRow.State.AT_CAPACITY, 30_000, 24_576, 6);
        assertTrue(detail.contains("upgrade the wire"),
                "this is the state where more generation cannot help: " + detail);
        assertTrue(detail.contains("30,000"));
        assertTrue(detail.contains("24,576"));
    }

    @Test
    @DisplayName("a tripped leg says how to clear it, because it will not clear itself")
    void trippedDetailSaysHowToReset() {
        assertTrue(PduLegRow.detail(PduLegRow.State.TRIPPED, 0, 24_576, 3).contains("reset"));
    }

    @Test
    @DisplayName("an isolated leg counts its devices, and does not say one devices")
    void openDetailReadsNaturally() {
        assertEquals("1 device isolated", PduLegRow.detail(PduLegRow.State.OPEN, 0, 100, 1));
        assertEquals("4 devices isolated", PduLegRow.detail(PduLegRow.State.OPEN, 0, 100, 4));
    }

    @Test
    @DisplayName("the breaker button is always the other thing, like a real one")
    void theButtonIsAlwaysTheOpposite() {
        assertTrue(PduLegRow.nextClosedState(PduLegRow.State.TRIPPED));
        assertTrue(PduLegRow.nextClosedState(PduLegRow.State.OPEN));
        assertFalse(PduLegRow.nextClosedState(PduLegRow.State.LIVE));
        assertFalse(PduLegRow.nextClosedState(PduLegRow.State.AT_CAPACITY));
        assertEquals("RESET", PduLegRow.buttonLabel(PduLegRow.State.TRIPPED));
        assertEquals("CLOSE", PduLegRow.buttonLabel(PduLegRow.State.OPEN));
        assertEquals("OPEN", PduLegRow.buttonLabel(PduLegRow.State.LIVE));
    }

    @Test
    @DisplayName("the load bar never runs past its own end, or divides by a missing conductor")
    void loadFractionIsSafe() {
        assertEquals(0.0f, PduLegRow.loadFraction(500, 0), 0.0001f);
        assertEquals(1.0f, PduLegRow.loadFraction(50_000, 24_576), 0.0001f);
        assertEquals(0.5f, PduLegRow.loadFraction(12_288, 24_576), 0.001f);
    }

    @Test
    @DisplayName("legs are named from one, because studs are things you point at")
    void legsAreOneBased() {
        assertEquals("Leg 1", PduLegRow.name(0));
        assertEquals("Leg 8", PduLegRow.name(7));
    }

    @Test
    @DisplayName("every leg has its own colour, matching the conductor in the world")
    void coloursAreDistinct() {
        final java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int leg = 0; leg < com.createstats.power.PduStuds.COUNT; leg++) {
            assertTrue(seen.add(PduLegRow.colour(leg)), "leg " + leg + " reuses a colour");
        }
    }

    @Test
    @DisplayName("a leg feeding a board says so, because a quiet board leg is not a fault")
    void aBoardLegSaysItIsABoardLeg() {
        final String line = PduLegRow.detail(PduLegRow.State.LIVE, 1024, 4096, 1, false, true, false);
        assertTrue(line.startsWith("Board on this leg."), line);

        assertTrue(line.contains("of "), line);
    }

    @Test
    @DisplayName("a held board leg explains the hold rather than looking broken")
    void aHeldBoardLegExplainsItself() {
        final String line = PduLegRow.detail(PduLegRow.State.LIVE, 0, 4096, 1, false, true, true);
        assertTrue(line.contains("No spare to export"), line);

        assertTrue(line.contains("critical still runs"), line);
    }

    @Test
    @DisplayName("a real fault still wins over the board wording")
    void faultsOutrankTheBoardWording() {
        for (PduLegRow.State state : new PduLegRow.State[]{
                PduLegRow.State.TRIPPED, PduLegRow.State.OPEN, PduLegRow.State.UNWIRED}) {
            assertEquals(PduLegRow.detail(state, 0, 4096, 1, false),
                    PduLegRow.detail(state, 0, 4096, 1, false, true, true),
                    state + " must read the same whether or not a board is behind it");
        }
    }
}
