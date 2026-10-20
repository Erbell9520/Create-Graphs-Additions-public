package com.createstats.chunkloader.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownPicksTest {
    private static final CrownLaw LAW = CrownLaw.DEFAULT;

    private static CrownPicks.Request ask(int x, int z, boolean picked) {
        return new CrownPicks.Request(true, true, true, 10, 10, x, z, picked, 5, 12);
    }

    @Test
    @DisplayName("an open chunk in the region is picked; a picked one is dropped")
    void pickAndDrop() {
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(ask(11, 12, false), LAW));
        assertEquals(CrownPicks.Verdict.DROP, CrownPicks.toggle(ask(11, 12, true), LAW));
        assertTrue(CrownPicks.Verdict.PICK.changes());
        assertTrue(CrownPicks.Verdict.DROP.changes());
        assertFalse(CrownPicks.Verdict.PLAYER_FULL.changes());
    }

    @Test
    @DisplayName("the station's own chunk can never be dropped")
    void theOwnChunkStays() {
        assertEquals(CrownPicks.Verdict.OWN_CHUNK, CrownPicks.toggle(ask(10, 10, true), LAW));
    }

    @Test
    @DisplayName("nobody's station holds nothing, and only the owner changes an owned one")
    void ownership() {
        assertEquals(CrownPicks.Verdict.NO_OWNER, CrownPicks.toggle(
                new CrownPicks.Request(false, false, true, 10, 10, 11, 10, false, 1, 0), LAW));
        assertEquals(CrownPicks.Verdict.NOT_OWNER, CrownPicks.toggle(
                new CrownPicks.Request(true, false, true, 10, 10, 11, 10, false, 1, 0), LAW));
        assertEquals(CrownPicks.Verdict.NOT_OWNER, CrownPicks.toggle(
                new CrownPicks.Request(true, false, true, 10, 10, 11, 10, true, 2, 2), LAW),
                "not even to drop one");
    }

    @Test
    @DisplayName("a station that may not hold takes no new pick, but its picks can still be dropped")
    void aBlockedStation() {
        assertEquals(CrownPicks.Verdict.STATION_DARK_FOR_GOOD, CrownPicks.toggle(
                new CrownPicks.Request(true, true, false, 10, 10, 11, 10, false, 1, 1), LAW));
        assertEquals(CrownPicks.Verdict.DROP, CrownPicks.toggle(
                new CrownPicks.Request(true, true, false, 10, 10, 11, 10, true, 2, 2), LAW));
    }

    @Test
    @DisplayName("picks stay inside the region, and never on a Sable plot")
    void insideTheRegion() {
        assertEquals(CrownPicks.Verdict.OUTSIDE_REGION, CrownPicks.toggle(ask(32, 10, false), LAW));
        assertEquals(CrownPicks.Verdict.OUTSIDE_REGION, CrownPicks.toggle(ask(10, -1, false), LAW));
        final int plot = CrownRegion.FIRST_PLOT_CHUNK;
        assertEquals(CrownPicks.Verdict.SABLE_PLOT, CrownPicks.toggle(new CrownPicks.Request(true, true, true,
                plot + 1, plot + 1, plot + 2, plot + 2, false, 1, 1), LAW));
    }

    @Test
    @DisplayName("25 a station and 50 a player, at the limit exactly")
    void theCaps() {
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 24, 49), LAW));
        assertEquals(CrownPicks.Verdict.STATION_FULL, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 25, 30), LAW));
        assertEquals(CrownPicks.Verdict.PLAYER_FULL, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 10, 50), LAW));
        assertEquals(CrownPicks.Verdict.DROP, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, true, 25, 50), LAW),
                "a full player can always drop");
    }

    @Test
    @DisplayName("KI-183: under a board, a pick its studs cannot carry is refused, at the limit exactly; no board, no limit")
    void theBoardsStuds() {
        assertEquals(CrownPicks.Verdict.BOARD_CARRY, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 24, 24, 98_304), LAW));
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 23, 23, 98_304), LAW));
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 24, 24, LAW.totalDraw(25)), LAW),
                "a draw exactly at what the studs carry fits");
        assertEquals(CrownPicks.Verdict.BOARD_CARRY, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 24, 24, LAW.totalDraw(25) - 1), LAW));
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, false, 24, 24, CrownPicks.NO_BOARD), LAW));
        assertEquals(CrownPicks.Verdict.DROP, CrownPicks.toggle(
                new CrownPicks.Request(true, true, true, 10, 10, 11, 10, true, 24, 24, 0), LAW),
                "a drop is never refused for the studs");
        assertFalse(CrownPicks.Verdict.BOARD_CARRY.changes());
    }

    @Test
    @DisplayName("the owner's \"kinda 2\": a board forecast short within the minute refuses a pick; later, or none, does not")
    void theBoardsSupply() {
        assertEquals(CrownPicks.Verdict.BOARD_SUPPLY, CrownPicks.toggle(new CrownPicks.Request(true, true, true, 10, 10, 11,
                10, false, 10, 10, 200_000, CrownBoard.SUPPLY_HOLD_TICKS - 1), LAW));
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(new CrownPicks.Request(true, true, true, 10, 10, 11,
                10, false, 10, 10, 200_000, CrownBoard.SUPPLY_HOLD_TICKS), LAW), "a forecast a minute off is a warning");
        assertEquals(CrownPicks.Verdict.PICK, CrownPicks.toggle(new CrownPicks.Request(true, true, true, 10, 10, 11,
                10, false, 10, 10, 200_000, -1), LAW));
        assertEquals(CrownPicks.Verdict.DROP, CrownPicks.toggle(new CrownPicks.Request(true, true, true, 10, 10, 11,
                10, true, 10, 10, 200_000, 0), LAW), "a drop is never refused for supply");
        assertEquals(CrownPicks.Verdict.BOARD_CARRY, CrownPicks.toggle(new CrownPicks.Request(true, true, true, 10, 10, 11,
                10, false, 24, 24, 98_304, 0), LAW), "the wire is judged first");
    }
}
