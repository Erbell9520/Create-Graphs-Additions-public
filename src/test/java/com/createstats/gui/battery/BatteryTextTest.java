package com.createstats.gui.battery;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeBoardDeal;
import com.createstats.power.FePriority;
import com.createstats.power.ReserveWords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryTextTest {
    @Test
    @DisplayName("the save's headline says the banks are covering the gap, with the numbers")
    void saveHeadline() {
        final BatteryView v = BatteryScreenPreviewTest.save();
        assertEquals("Generating 8.4k FE/t, 81% of the 10.3k going out. The banks give the other 1.9k FE/t.",
                BatteryText.now(v));
        final String foreseen = BatteryText.foreseen(v);
        assertTrue(foreseen.startsWith("Foreseen: everything served for about 3"), foreseen);

        assertTrue(foreseen.contains("then Output 3 goes short"), foreseen);
        assertFalse(foreseen.contains("~"), "vanilla's tilde reads as a caret");
        assertFalse(foreseen.toLowerCase().contains("empty"), "the band never empties the banks");
    }

    @Test
    @DisplayName("KI-99: the headline leads with the supply change, and the list names each one with its figure")
    void supplyWords() {
        final BatteryView v = BatteryScreenPreviewTest.dusk();
        final String foreseen = BatteryText.foreseen(v);
        assertTrue(foreseen.startsWith("Foreseen: solar stops in about 2 min; "), foreseen);
        final com.createstats.power.ReserveProjection.Result r = v.outlook();
        assertEquals("Burners run dry: 360 FE/t goes", ReserveWords.milestone(
                r.first(com.createstats.power.ReserveProjection.Kind.FUEL_OUT)));
        assertTrue(ReserveWords.milestone(r.first(com.createstats.power.ReserveProjection.Kind.SUN_UP))
                .startsWith("Sunrise: solar starts, up to "));
        final String assume = BatteryText.supplyAssumption(v);
        assertTrue(assume.startsWith("Supply follows its sources: 3.9k FE/t now, 540 to "), assume);
        assertFalse(assume.contains("holds at"), "the old flat assumption is gone where the sources move");
    }

    @Test
    @DisplayName("a board short of its answer names the answer's reason, short enough for a card")
    void boardReason() {
        final BatteryView v = BatteryScreenPreviewTest.trouble();
        final BatteryView.Output board = v.outputs().get(2);
        assertEquals("Board given 6.5k of 9.0k: the refill comes first", BatteryText.outputWhy(board, v));
        assertTrue(board.isShort());
        assertEquals(9_000, board.asked(), "a board output asks what its board asked, not its wire");
    }

    @Test
    @DisplayName("a held output names the floor and the charge it is judged against")
    void floorReason() {
        final BatteryView v = BatteryScreenPreviewTest.trouble();
        assertEquals("Held: banks 23% are under its 40% floor", BatteryText.outputWhy(v.outputs().get(1), v));
    }

    @Test
    @DisplayName("KI-97: a board's reserve line is the controller's forecast, not its own buffer")
    void boardOutlook() {
        assertEquals("whole for about 30 min, then metered; banks settle near 16%",
                ReserveWords.boardOutlook(36_000, 16, -1_808));
        assertEquals("already metered by the controller", ReserveWords.boardOutlook(0, -1, -1_808));
        assertEquals("generation covers everything", ReserveWords.boardOutlook(-1, -1, 0));
        assertEquals("whole for the next 30 min", ReserveWords.boardOutlook(-1, -1, -50));
    }

    @Test
    @DisplayName("figures keep a decimal up to a hundred thousand, and spans read as a player reads them")
    void numbers() {
        assertEquals("10.3k", ReserveWords.fe(10_291));
        assertEquals("812", ReserveWords.fe(812));
        assertEquals("412k", ReserveWords.fe(412_000));
        assertEquals("82.3M", ReserveWords.fe(82_348_375));
        assertEquals("45 s", ReserveWords.span(900));
        assertEquals("30 min", ReserveWords.span(36_000));
        assertEquals("1 h 05 min", ReserveWords.span(78_000));
    }

    @Test
    @DisplayName("an output with nothing on it is not short, and a switched-off one says so")
    void offAndUnwired() {
        final BatteryView v = BatteryScreenPreviewTest.save();
        final BatteryView.Output off = new BatteryView.Output(0, true, false, BatteryOutputMode.DIRECT, 0, 0,
                FePriority.NORMAL, BatteryControllerPlan.Reason.OFF, 0, 500, 8_192, 2, 0, 0, 0, 0,
                FeBoardDeal.Reason.GRANTED, 0, 0);
        assertFalse(off.isShort(), "a breaker the player opened is not a shortage");
        assertEquals("Breaker open: switched off here", BatteryText.outputWhy(off, v));
        assertFalse(v.outputs().get(0).isShort());
    }
}
