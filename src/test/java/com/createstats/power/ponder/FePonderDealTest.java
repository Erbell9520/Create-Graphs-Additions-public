package com.createstats.power.ponder;

import com.createstats.power.FeBoardDeal.Reason;
import com.createstats.power.FePriority;
import com.createstats.power.ponder.FePonderChapters.Controller;
import com.createstats.power.ponder.FePonderChapters.Deal;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderDealTest {
    private static Deal.Run run;

    private static Deal.Run run() {
        if (run == null) run = Deal.replay(Tuning.defaults(), Controller.Stock.defaults(), 1L, 2L, 3L);
        return run;
    }

    private static int at(String caption) {
        return run().warp().tick(Deal.sheet(run()).tick(caption));
    }

    @Test
    void eachBoardAsksForItsCriticalWhatItRunsAndWhatItWouldRestore() {
        final FePonderDeal.Recording r = run().rec();
        final int t = at("ask_caption");

        final int upkeep = Tuning.defaults().upkeep();
        assertEquals(256, r.askCritical[Deal.BOARD_1][t]);
        assertEquals(1 + upkeep, r.askRunning[Deal.BOARD_1][t]);
        assertEquals(1, r.askCritical[Deal.BOARD_2][t]);
        assertEquals(256 + upkeep, r.askRunning[Deal.BOARD_2][t]);
        assertEquals(0, r.askRestore[Deal.BOARD_1][t] + r.askRestore[Deal.BOARD_2][t], "nothing shed yet");
    }

    @Test
    void theControllerAnswersEachInFullWhileTheReserveIsDeep() {
        final FePonderDeal.Recording r = run().rec();
        final int t = at("answer_caption");
        for (int b = 0; b < 2; b++) {
            assertEquals(Reason.GRANTED, r.reason[b][t], "board " + (b + 1));
            assertEquals(r.asked(b, t), r.granted(b, t));
        }
    }

    @Test
    void theForecastNamesTheMomentABoardGoesShortBeforeItComes() {
        final FePonderDeal.Recording r = run().rec();
        final int t = at("forecast_caption");
        final int in = r.shortIn[t];
        assertTrue(in > 0, "a shortfall ahead, not now: " + in);
        final int predicted = t + in;
        final int split = run().split();
        assertTrue(Math.abs(predicted - split) <= FePonderDeal.OUTLOOK_TICKS,
                "the forecast (" + predicted + ") comes true within a refresh of the split (" + split + ")");
        assertTrue(Deal.sheet(run()).tick("forecast_caption") < run().warp().scene(split), "and it is read first");
    }

    @Test
    void shortOfBothItSharesCriticalFirst() {
        final FePonderDeal.Recording r = run().rec();
        final int split = run().split();
        assertTrue(r.admitted[Deal.BOARD_2][Deal.RANGE_2][split - 1], "board 2's range ran until the split");
        assertTrue(!r.admitted[Deal.BOARD_2][Deal.RANGE_2][split], "and stops at it");
        final int from = at("split_caption");
        assertEquals(Reason.SHARED, r.reason[Deal.BOARD_2][from], "board 2's answer: shared with the other boards");
        final int end = run().warp().tick(Deal.sheet(run()).length() - 1);
        for (int t = split; t <= end; t++) {
            assertTrue(r.working[Deal.BOARD_1][Deal.RANGE_1][t], "board 1's Critical range runs on: " + t);
            assertTrue(r.working[Deal.BOARD_2][Deal.LAMP_2][t], "and board 2's Critical lamp: " + t);
        }
        assertEquals(FePriority.CRITICAL, r.setup.boards().get(Deal.BOARD_1).devices().get(Deal.RANGE_1).tier());
        assertEquals(FePriority.NORMAL, r.setup.boards().get(Deal.BOARD_2).devices().get(Deal.RANGE_2).tier());
    }

    @Test
    void underTheFloorItAnswersCriticalOnly() {
        final FePonderDeal.Recording r = run().rec();
        final int t = at("floor_caption");
        for (int b = 0; b < 2; b++) {
            assertEquals(Reason.FLOOR, r.reason[b][t], "board " + (b + 1));
            assertEquals(0, r.gotRunning[b][t] + r.gotRestore[b][t], "nothing but Critical: board " + (b + 1));
        }
        assertEquals(com.createstats.power.FeReserveReport.Stance.CRITICAL, r.stance[t]);
    }

    @Test
    void theCaptionsFollowTheirMoments() {
        final Sheet sheet = Deal.sheet(run());
        assertTrue(sheet.tick("split_caption") > run().warp().scene(run().split()));
        assertTrue(sheet.tick("floor_caption") > run().warp().scene(run().floor()));
    }

    @Test
    void theReplayIsQuick() {
        final long t0 = System.nanoTime();
        Deal.replay(Tuning.defaults(), Controller.Stock.defaults(), 1L, 2L, 3L);
        final long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1_500, "the chapter replays when it opens: " + ms + " ms");
    }
}
