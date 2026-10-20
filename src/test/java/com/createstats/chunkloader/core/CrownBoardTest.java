package com.createstats.chunkloader.core;

import com.createstats.power.FeStationFeed;
import com.createstats.power.PduReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownBoardTest {
    private static FeStationFeed feed(long board, int legA, int legB, int carried, PduReason reason, long expires) {
        return outlook(board, legA, legB, carried, reason, -1, 0, expires);
    }

    private static FeStationFeed outlook(long board, int legA, int legB, int carried, PduReason reason, int shortIn,
                                         int banks, long expires) {
        return new FeStationFeed(board, legA, legB, legA >= 0, legB >= 0, legA >= 0 ? 50_000 : 0,
                legB >= 0 ? 40_000 : 0, carried, 100_707, reason, shortIn, banks, expires);
    }

    @Test
    @DisplayName("one board on both studs: its studs, its carry, and the picks judged against it")
    void oneBoardBothStuds() {
        final CrownBoard b = CrownBoard.of(new FeStationFeed[] {feed(1, 0, 1, 131_072, PduReason.RUNNING, 20), null},
                10, true, true);
        assertTrue(b.present());
        assertTrue(b.covers());
        assertEquals(0, b.legA());
        assertEquals(1, b.legB());
        assertEquals(131_072, b.pickCarry());
        assertEquals(90_000, b.fed());
    }

    @Test
    @DisplayName("a stale report is no board at all, which is exactly a crown on a plain grid")
    void staleIsNothing() {
        final CrownBoard b = CrownBoard.of(new FeStationFeed[] {feed(1, 0, 1, 131_072, PduReason.RUNNING, 9), null},
                10, true, true);
        assertSame(CrownBoard.NONE, b);
        assertEquals(CrownPicks.NO_BOARD, b.pickCarry());
    }

    @Test
    @DisplayName("a board on one stud and a plain grid on the other: shown, never used to refuse a pick")
    void aGridOnTheOtherStud() {
        final CrownBoard b = CrownBoard.of(new FeStationFeed[] {feed(1, 2, -1, 65_536, PduReason.RUNNING, 20), null},
                10, true, true);
        assertTrue(b.present());
        assertFalse(b.covers());
        assertEquals(CrownPicks.NO_BOARD, b.pickCarry());
        final CrownBoard alone = CrownBoard.of(new FeStationFeed[] {feed(1, 2, -1, 65_536, PduReason.RUNNING, 20), null},
                10, true, false);
        assertTrue(alone.covers(), "one wire, on the board: the board speaks for all of it");
        assertEquals(65_536, alone.pickCarry());
    }

    @Test
    @DisplayName("two boards, one stud each: both studs covered, their carries added, the worse verdict kept")
    void twoBoards() {
        final CrownBoard b = CrownBoard.of(new FeStationFeed[] {
                feed(1, 0, -1, 65_536, PduReason.RUNNING, 20),
                feed(2, -1, 4, 32_768, PduReason.WIRE_SHORT, 20)}, 10, true, true);
        assertTrue(b.covers());
        assertEquals(0, b.legA());
        assertEquals(4, b.legB());
        assertEquals(98_304, b.pickCarry());
        assertEquals(PduReason.WIRE_SHORT, b.reason());
        final CrownBoard open = CrownBoard.of(new FeStationFeed[] {
                feed(1, 0, -1, 65_536, PduReason.OVER_BUDGET, 20),
                feed(2, -1, 4, 0, PduReason.BREAKER_OPEN, 20)}, 10, true, true);
        assertEquals(PduReason.BREAKER_OPEN, open.reason(), "off at a breaker outranks shed");
    }

    @Test
    @DisplayName("what goes to the client comes back the same, and a short array is no board")
    void packing() {
        final CrownBoard b = new CrownBoard(0, 1, true, false, 65_536, 0, 65_536, 100_707, PduReason.WIRE_SHORT, true,
                6_000, 35_000);
        assertEquals(b, CrownBoard.unpack(b.pack()));
        assertSame(CrownBoard.NONE, CrownBoard.unpack(new int[3]));
        assertFalse(b.discrete().equals(new CrownBoard(0, 1, true, true, 50_354, 50_353, 131_072, 100_707,
                PduReason.RUNNING, true, 6_000, 35_000).discrete()), "a stud opening goes out at once");
    }

    @Test
    @DisplayName("the supply outlook: the banks paying, the soonest forecast shortfall, and a pick judged on it only when covered")
    void theSupplyOutlook() {
        final CrownBoard paying = CrownBoard.of(new FeStationFeed[] {
                outlook(1, 0, 1, 131_072, PduReason.RUNNING, 6_000, -54_000, 20), null}, 10, true, true);
        assertTrue(paying.onBanks());
        assertEquals(54_000, paying.banks(), "the banks' rate is negative while they pay; shown as what they pay");
        assertEquals(6_000, paying.pickShortIn());
        final CrownBoard charging = CrownBoard.of(new FeStationFeed[] {
                outlook(1, 0, 1, 131_072, PduReason.RUNNING, -1, 20_000, 20), null}, 10, true, true);
        assertFalse(charging.onBanks(), "banks charging are not paying");
        assertEquals(-1, charging.pickShortIn());
        final CrownBoard soonest = CrownBoard.of(new FeStationFeed[] {
                outlook(1, 0, -1, 65_536, PduReason.RUNNING, 9_000, -1_000, 20),
                outlook(2, -1, 3, 65_536, PduReason.RUNNING, 4_000, -2_000, 20)}, 10, true, true);
        assertEquals(4_000, soonest.shortIn(), "the soonest of two boards");
        assertEquals(3_000, soonest.banks());
        final CrownBoard grid = CrownBoard.of(new FeStationFeed[] {
                outlook(1, 0, -1, 65_536, PduReason.RUNNING, 600, -1_000, 20), null}, 10, true, true);
        assertEquals(-1, grid.pickShortIn(), "a plain grid on the other stud has supply no board forecasts");
        assertFalse(new CrownBoard(0, 1, true, true, 0, 0, 0, 0, PduReason.RUNNING, true, 6_000, 1).discrete().equals(
                new CrownBoard(0, 1, true, true, 0, 0, 0, 0, PduReason.RUNNING, true, 600, 1).discrete()),
                "a forecast falling inside the minute goes out at once");
    }
}
