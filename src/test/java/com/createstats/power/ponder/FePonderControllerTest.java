package com.createstats.power.ponder;

import com.createstats.power.BatteryControllerPlan.Reason;
import com.createstats.power.FeReserveReport.Stance;
import com.createstats.power.ponder.FePonderChapters.Controller;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderControllerTest {
    private static Controller.Run run;

    private static Controller.Run run() {
        if (run == null) run = Controller.replay(Tuning.defaults(), Controller.Stock.defaults());
        return run;
    }

    private static FePonderController.Recording rec() {
        return run().rec();
    }

    private static Sheet sheet() {
        return Controller.sheet(run());
    }

    private static int at(String caption) {
        return run().warp().tick(sheet().tick(caption));
    }

    @Test
    void eachCircuitIsServedFromTheChargedBanksTheTickItsWireLands() {
        final FePonderController.Recording r = rec();
        for (int i = 0; i < 3; i++) {
            final int wired = Controller.wired(3 + i);
            assertEquals(Reason.UNWIRED, r.why[i][wired - 1], "output " + (i + 1) + " before its wire");
        }
        final int range = Controller.wired(3);
        assertEquals(Reason.SERVED, r.why[Controller.RANGE][range + 1], "the range, off the reserve, before any generation");
        assertEquals(0, r.generation[range + 1]);
        assertEquals(Stance.SURPLUS, r.stance[range + 1]);
    }

    @Test
    void generationIsSpentOnTheCircuitsFirstAndTheRestChargesTheBanks() {
        final FePonderController.Recording r = rec();
        final int from = at("power_caption");
        assertTrue(from > Controller.POWER, "the caption follows the power");
        for (int t = from; t < run().stopped(); t++) {
            assertEquals(360, r.generation[t], "one dynamo at 256 RPM: " + t);
            assertEquals(Reason.SERVED, r.why[Controller.RANGE][t], "the range first: " + t);
            final int banked = r.bankFlow[0][t] + r.bankFlow[1][t];
            assertEquals(r.generation[t] - r.delivered[t], banked, "the rest into the banks: " + t);
            assertTrue(banked > 0, t + " charges");
        }
    }

    @Test
    void theFlooredCircuitWaitsWithGenerationToSpareAndComesBackPastItsMargin() {
        final FePonderController.Recording r = rec();
        final int lamp = Controller.FLOORED_LAMP;
        final int from = at("floor_caption");
        final int back = run().back();
        assertTrue(back > from, "it is still off when the caption says so");
        for (int t = from; t < back; t++) {
            assertEquals(Reason.FLOOR, r.why[lamp][t], "held by its floor: " + t);
            assertTrue(r.generation[t] - r.delivered[t] >= r.want[lamp][t], "with generation to spare for it: " + t);
            assertEquals(0, r.level[lamp][t], "and dark: " + t);
        }
        assertTrue(r.socPct[back] >= Controller.FLOOR_PCT + com.createstats.power.BatteryControllerPlan.RECOVER_MARGIN_PCT,
                "back only past its floor and the margin: " + r.socPct[back]);
        assertTrue(r.level[lamp][back] > 0, "lit");
        assertTrue(run().warp().scene(back) < Controller.STOP, "and seen lit before the stop");
    }

    @Test
    void offGenerationTheStanceFallsThroughTheBandInOrder() {
        final FePonderController.Recording r = rec();
        final int stop = run().stopped();
        final int nominal = r.firstStance(Stance.NOMINAL, stop);
        final int conserve = r.firstStance(Stance.CONSERVE, stop);
        final int critical = r.firstStance(Stance.CRITICAL, stop);
        assertTrue(stop < nominal && nominal < conserve && conserve < critical, stop + " " + nominal + " " + conserve
                + " " + critical);
        assertEquals(Stance.SURPLUS, r.stance[at("stop_caption")], "deep, when the caption says freely");
        assertEquals(Reason.SERVED, r.why[Controller.RANGE][at("stop_caption")], "and the range served in full");
        assertEquals(critical, run().critical());
    }

    @Test
    void theReleaseShrinksAndTheRangeBrownsOutRatherThanSwitchingOff() {
        final FePonderController.Recording r = rec();
        final int from = run().droop();
        final int to = run().critical();
        int dropped = 0;
        for (int t = from + 1; t < to; t++) {
            final int change = r.flow[Controller.RANGE][t] - r.flow[Controller.RANGE][t - 1];
            assertTrue(change <= 1, "the range's share never climbs back while the charge falls: " + t);
            assertTrue(change >= -3, "and never falls in a step: " + t + " by " + change);
            dropped -= Math.min(0, change);
        }
        assertTrue(dropped > 60, "it falls a long way, smoothly: " + dropped);
        assertTrue(at("droop_caption") < to && at("droop_caption") >= from, "the caption is up while it does");
        boolean browned = false;
        for (int t = from; t < to; t++) browned |= r.level[Controller.RANGE][t] == 2 && r.starved[Controller.RANGE][t];
        assertTrue(browned, "the range is seen at a lower setting, starved, not only on or off");
    }

    @Test
    void underTheFloorATrickleIsLetOutAndTheCriticalLampStaysLit() {
        final FePonderController.Recording r = rec();
        final int from = run().critical();
        final int to = run().refilled();
        for (int t = from; t < to; t++) {
            assertEquals(Stance.CRITICAL, r.stance[t], "under the floor: " + t);
            assertTrue(r.delivered[t] <= 20, "a trickle: " + r.delivered[t] + " at " + t);
            assertEquals(1, r.level[Controller.CRITICAL_LAMP][t], "the Critical lamp lit: " + t);
        }
        assertTrue(at("critical_caption") >= from, "the caption follows the crossing");
    }

    @Test
    void backOnGenerationPartOfItRefillsTheBanksFirst() {
        final FePonderController.Recording r = rec();
        final int from = at("refill_caption");
        assertTrue(from > run().refilled());

        final int until = from + sheet().get("refill_caption").hold();
        assertTrue(until <= r.length, "the caption ends inside the replay");
        for (int t = from; t < until; t++) {
            assertTrue(r.claim[t] > 0, "a claim on generation: " + t);
            assertEquals(Reason.RECHARGE, r.why[Controller.RANGE][t], "output 1 reads Recharging reserve: " + t);
            final int banked = r.bankFlow[0][t] + r.bankFlow[1][t];
            assertEquals(r.generation[t] - r.delivered[t], banked, "what the circuits are not given goes in: " + t);
            assertTrue(banked > r.claim[t] / 2, "most of the claim, the rest being the trickle it spends: " + t);
        }

        final int floor = Controller.Stock.defaults().band().floorPct();
        for (int t = from; t < r.length && r.socPct[t] < floor; t++) {
            assertEquals(Reason.RECHARGE, r.why[Controller.RANGE][t], "under the floor, still Recharging reserve: " + t);
        }
    }

    @Test
    void theReplayIsQuick() {
        final long t0 = System.nanoTime();
        Controller.replay(Tuning.defaults(), Controller.Stock.defaults());
        final long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1_500, "the chapter replays its three passes when it opens: " + ms + " ms");
    }
}
