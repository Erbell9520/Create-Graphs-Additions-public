package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryControllerPlanTest {
    private static final int RATE = 32_768;

    private static BatteryControllerPlan.Output out(int demand, int floorPct) {
        return new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, demand, RATE,
                0, floorPct, FePriority.NORMAL, false);
    }

    private static BatteryControllerPlan.Output out(int demand, int floorPct, FePriority tier) {
        return new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, demand, RATE,
                0, floorPct, tier, false);
    }

    @Test
    void everyoneIsServedWhenThereIsEnough() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(out(100, 0), out(200, 0), out(300, 0)), 600, 10_000, 80);

        assertEquals(600, plan.totalGranted());
        for (BatteryControllerPlan.Grant grant : plan.grants()) {
            assertEquals(BatteryControllerPlan.Reason.SERVED, grant.reason());
        }
    }

    @Test
    void anUnwiredOrSwitchedOffOutputTakesNothing() {
        BatteryControllerPlan.Output unwired = new BatteryControllerPlan.Output(false, true,
                BatteryOutputMode.DIRECT, 500, RATE, 0, 0, FePriority.NORMAL, false);
        BatteryControllerPlan.Output off = new BatteryControllerPlan.Output(true, false,
                BatteryOutputMode.DIRECT, 500, RATE, 0, 0, FePriority.NORMAL, false);

        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(unwired, off, out(100, 0)), 1_000, 10_000, 90);

        assertEquals(BatteryControllerPlan.Reason.UNWIRED, plan.grants().get(0).reason());
        assertEquals(BatteryControllerPlan.Reason.OFF, plan.grants().get(1).reason());
        assertEquals(100, plan.totalGranted(), "only the live circuit drew anything");
    }

    @Test
    void theBaseShedsItselfFromTheBottomUpAsTheBankDrains() {
        List<BatteryControllerPlan.Output> outputs =
                List.of(out(100, 0), out(100, 40), out(100, 70));

        BatteryControllerPlan.Plan full = BatteryControllerPlan.plan(outputs, 300, 10_000, 90);
        assertEquals(300, full.totalGranted(), "all three run on a full bank");

        BatteryControllerPlan.Plan half = BatteryControllerPlan.plan(outputs, 300, 10_000, 55);
        assertEquals(200, half.totalGranted(), "the 70 percent circuit drops first");
        assertEquals(BatteryControllerPlan.Reason.FLOOR, half.grants().get(2).reason());
        assertTrue(half.grants().get(2).held());

        BatteryControllerPlan.Plan low = BatteryControllerPlan.plan(outputs, 300, 10_000, 20);
        assertEquals(100, low.totalGranted(), "and the 40 percent circuit follows it");
        assertEquals(BatteryControllerPlan.Reason.SERVED, low.grants().get(0).reason());
        assertEquals(BatteryControllerPlan.Reason.FLOOR, low.grants().get(1).reason());
        assertEquals(BatteryControllerPlan.Reason.FLOOR, low.grants().get(2).reason());
    }

    @Test
    void aFlooredOutputDoesNotFlapOnTheThresholdItTrippedAt() {
        BatteryControllerPlan.Output held = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 100, RATE, 0, 40, FePriority.NORMAL, true);

        BatteryControllerPlan.Plan atThreshold = BatteryControllerPlan.plan(List.of(held), 100, 10_000, 40);
        assertEquals(BatteryControllerPlan.Reason.FLOOR, atThreshold.grants().get(0).reason(),
                "exactly at the floor is not enough to come back");

        BatteryControllerPlan.Plan recovered = BatteryControllerPlan.plan(List.of(held), 100, 10_000,
                40 + BatteryControllerPlan.RECOVER_MARGIN_PCT);
        assertEquals(BatteryControllerPlan.Reason.SERVED, recovered.grants().get(0).reason(),
                "clearing the margin lets it back in");
        assertFalse(recovered.grants().get(0).held());

        BatteryControllerPlan.Plan fresh = BatteryControllerPlan.plan(List.of(out(100, 40)), 100, 10_000, 40);
        assertEquals(BatteryControllerPlan.Reason.SERVED, fresh.grants().get(0).reason());
    }

    @Test
    void aSurplusOutputNeverTouchesStoredCharge() {
        BatteryControllerPlan.Output dump = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.SURPLUS, 500, RATE, 0, 0, FePriority.NORMAL, false);

        BatteryControllerPlan.Plan noGeneration = BatteryControllerPlan.plan(
                List.of(dump), 0, 50_000, 100);
        assertEquals(0, noGeneration.totalGranted(), "a full bank is not surplus");
        assertEquals(BatteryControllerPlan.Reason.NO_SURPLUS, noGeneration.grants().get(0).reason());

        BatteryControllerPlan.Plan someGeneration = BatteryControllerPlan.plan(
                List.of(dump), 200, 50_000, 100);
        assertEquals(200, someGeneration.totalGranted(), "only what the generators are making");
        assertEquals(0, someGeneration.fromReserve());
    }

    @Test
    void aSurplusOutputBelowAHigherTierGetsOnlyTheGenerationThatTierLeft() {
        final BatteryControllerPlan.Output direct = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 5_500, 65_536, 0, 0, FePriority.HIGH, false);
        final BatteryControllerPlan.Output dump = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.SURPLUS, 2_000, 65_536, 0, 0, FePriority.LOW, false);
        final BatteryControllerPlan.Plan with = BatteryControllerPlan.plan(List.of(direct, dump), 1_000, 6_000, 80);
        final BatteryControllerPlan.Plan without = BatteryControllerPlan.plan(List.of(direct), 1_000, 6_000, 80);
        assertEquals(0, with.grants().get(1).granted(), "nothing live is left for the surplus output");
        assertEquals(BatteryControllerPlan.Reason.NO_SURPLUS, with.grants().get(1).reason());
        assertEquals(without.fromReserve(), with.fromReserve(), "a surplus output never moves the banks");

        final BatteryControllerPlan.Output small = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 600, 65_536, 0, 0, FePriority.HIGH, false);
        final BatteryControllerPlan.Plan spare = BatteryControllerPlan.plan(List.of(small, dump), 1_000, 6_000, 80);
        assertEquals(400, spare.grants().get(1).granted());
        assertEquals(0, spare.fromReserve());
    }

    @Test
    void aSurplusOutputNeverSpendsTheRefillTheClaimSetAside() {
        final BatteryControllerPlan.Output dump = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.SURPLUS, 2_000, 65_536, 0, 0, FePriority.NORMAL, false);

        final BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(dump), 1_000, 5_400, 30, 600);
        assertEquals(400, plan.grants().get(0).granted(), "only generation less the claim");
    }

    @Test
    void aLimitedOutputNeverExceedsItsRateEvenOnAFullBank() {
        BatteryControllerPlan.Output capped = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.LIMIT, 5_000, RATE, 250, 0, FePriority.NORMAL, false);

        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(capped), 5_000, 50_000, 100);
        assertEquals(250, plan.totalGranted());
        assertEquals(BatteryControllerPlan.Reason.SERVED, plan.grants().get(0).reason(),
                "serving the capped ask in full is served, not partial");
    }

    @Test
    void tiersAreServedInOrderWhenThereIsNotEnoughForEveryone() {
        List<BatteryControllerPlan.Output> outputs = List.of(
                out(400, 0, FePriority.OPTIONAL),
                out(400, 0, FePriority.CRITICAL),
                out(400, 0, FePriority.NORMAL));

        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(outputs, 500, 500, 60);

        assertEquals(400, plan.grants().get(1).granted(), "critical is served first and in full");
        assertEquals(100, plan.grants().get(2).granted(), "normal gets what is left");
        assertEquals(0, plan.grants().get(0).granted(), "optional gets nothing");
        assertEquals(BatteryControllerPlan.Reason.SHED, plan.grants().get(0).reason());
        assertEquals(500, plan.totalGranted(), "and never more than the pool held");
    }

    @Test
    void equalsShareRatherThanTheFirstOneDrinkingThePool() {
        List<BatteryControllerPlan.Output> outputs =
                List.of(out(1_000, 0), out(1_000, 0), out(1_000, 0));

        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(outputs, 900, 900, 60);

        assertEquals(900, plan.totalGranted());
        for (BatteryControllerPlan.Grant grant : plan.grants()) {
            assertTrue(grant.granted() > 0, "no circuit at the same tier may be starved by list order");
        }
    }

    @Test
    void generationIsSpentBeforeStoredCharge() {
        BatteryControllerPlan.Plan covered = BatteryControllerPlan.plan(
                List.of(out(300, 0)), 500, 10_000, 80);
        assertEquals(300, covered.fromGeneration());
        assertEquals(0, covered.fromReserve(), "generation covered it, so the bank is untouched");

        BatteryControllerPlan.Plan shortfall = BatteryControllerPlan.plan(
                List.of(out(800, 0)), 300, 10_000, 80);
        assertEquals(300, shortfall.fromGeneration());
        assertEquals(500, shortfall.fromReserve(), "only the gap comes out of the bank");
    }

    @Test
    void nothingIsGrantedBeyondTheWiringItWouldTravelDown() {
        BatteryControllerPlan.Output thin = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 20_000, 512, 0, 0, FePriority.NORMAL, false);

        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(thin), 20_000, 50_000, 100);
        assertEquals(512, plan.totalGranted(), "the wire on the terminal is the ceiling");
    }

    private static BatteryControllerPlan.Output panel(int demand, int floorPct, int criticalDemand) {
        return new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, demand, RATE,
                0, floorPct, FePriority.NORMAL, false, true, criticalDemand);
    }

    @Test
    void aCircuitWithAPanelOnItIsRationedRatherThanCutAtTheFloor() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(panel(2_000, 40, 350)), 0, 10_000, 15);

        BatteryControllerPlan.Grant grant = plan.grants().get(0);
        assertEquals(BatteryControllerPlan.Reason.RATIONED, grant.reason());
        assertEquals(350, grant.granted(), "exactly the panel's declared critical load, no more");
        assertTrue(grant.held(), "and it is still recorded as held, so the hysteresis applies");
    }

    @Test
    void aRationedCircuitIsNotMarkedWithheld() {
        assertFalse(BatteryControllerPlan.Reason.RATIONED.isWithheld());
        assertTrue(BatteryControllerPlan.Reason.FLOOR.isWithheld());
    }

    @Test
    void aPanelWithNoCriticalLoadIsStillCutLikeAnyOtherCircuit() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(panel(2_000, 40, 0)), 0, 10_000, 15);
        assertEquals(BatteryControllerPlan.Reason.FLOOR, plan.grants().get(0).reason());
        assertEquals(0, plan.totalGranted());
    }

    @Test
    void aPanelAboveItsFloorIsServedNormally() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(panel(2_000, 40, 350)), 2_000, 10_000, 80);
        assertEquals(BatteryControllerPlan.Reason.SERVED, plan.grants().get(0).reason());
        assertEquals(2_000, plan.totalGranted(), "the whole branch, not just its critical tier");
    }

    @Test
    void rationingIsStillBoundedByTheWire() {
        BatteryControllerPlan.Output thin = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 9_000, 200, 0, 40, FePriority.NORMAL, false, true, 5_000);
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(thin), 0, 50_000, 10);
        assertEquals(200, plan.totalGranted(), "a declared critical load cannot exceed its conductor");
    }

    @Test
    void anEmptyBoardDoesNothingRatherThanThrowing() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(), 1_000, 1_000, 100);
        assertEquals(0, plan.totalGranted());
        assertTrue(plan.grants().isEmpty());
    }

    @Test
    void aCircuitMeteredToRefillTheBanksSaysSoRatherThanReadingAsShed() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(out(1_000, 0)), 1_000, 400, 20, 600);

        assertEquals(400, plan.grants().get(0).granted());
        assertEquals(BatteryControllerPlan.Reason.RECHARGE, plan.grants().get(0).reason());
    }

    @Test
    void withNoClaimInForceAShortfallIsStillPlainlyShed() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(out(1_000, 0)), 1_000, 400, 20, 0);

        assertEquals(BatteryControllerPlan.Reason.PARTIAL, plan.grants().get(0).reason());
    }

    @Test
    void theClaimIsBlamedOnTheHighestTiersItActuallyCost() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(out(500, 0, FePriority.HIGH), out(500, 0, FePriority.LOW)),
                1_000, 0, 20, 500);

        assertEquals(BatteryControllerPlan.Reason.RECHARGE, plan.grants().get(0).reason());
        assertEquals(BatteryControllerPlan.Reason.SHED, plan.grants().get(1).reason());
    }

    @Test
    void aBigEnoughClaimExplainsEveryCircuitItStarved() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(out(500, 0, FePriority.HIGH), out(500, 0, FePriority.LOW)),
                1_000, 0, 20, 1_000);

        assertEquals(BatteryControllerPlan.Reason.RECHARGE, plan.grants().get(0).reason());
        assertEquals(BatteryControllerPlan.Reason.RECHARGE, plan.grants().get(1).reason());
    }

    @Test
    void aServedCircuitIsNeverBlamedOnTheClaim() {
        BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(
                List.of(out(100, 0), out(200, 0)), 1_000, 10_000, 20, 400);

        for (BatteryControllerPlan.Grant grant : plan.grants()) {
            assertEquals(BatteryControllerPlan.Reason.SERVED, grant.reason());
        }
    }

    @Test
    void rechargeCountsAsWithheldSoItsMachinesActuallyStop() {
        assertTrue(BatteryControllerPlan.Reason.RECHARGE.isWithheld());
        assertFalse(BatteryControllerPlan.Reason.RATIONED.isWithheld());
    }

    @Test
    void aBoardsCriticalOnANormalOutputOutranksAHighOutputsMachines() {
        final BatteryControllerPlan.Output lamps = out(6_000, 0, FePriority.HIGH);
        final BatteryControllerPlan.Output board = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 8_000, RATE, 0, 0, FePriority.NORMAL, false, true, 5_000);

        final BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(lamps, board), 7_000, 7_000, 50);
        assertEquals(5_000, plan.grants().get(1).granted(),
                "the board's declared Critical first, whatever its output's tier");
        assertEquals(2_000, plan.grants().get(0).granted(), "the HIGH lamps get what is left");

        assertEquals(7_000, plan.totalGranted());
    }

    @Test
    void aBoardsRemainderStillWaitsForItsOwnTier() {
        final BatteryControllerPlan.Output lamps = out(6_000, 0, FePriority.HIGH);
        final BatteryControllerPlan.Output board = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 8_000, RATE, 0, 0, FePriority.NORMAL, false, true, 5_000);
        final BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(lamps, board), 20_000, 13_000, 50);
        assertEquals(6_000, plan.grants().get(0).granted(), "HIGH is served in full");
        assertEquals(7_000, plan.grants().get(1).granted(), "the board: its Critical, then 2,000 at NORMAL");
        assertEquals(BatteryControllerPlan.Reason.PARTIAL, plan.grants().get(1).reason());
    }

    @Test
    void theClaimsGuardCountsABoardsCriticalOnAnyOutput() {
        final BatteryControllerPlan.Output critical = out(3_000, 0, FePriority.CRITICAL);
        final BatteryControllerPlan.Output board = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 8_000, RATE, 0, 0, FePriority.NORMAL, false, true, 5_000);
        final BatteryControllerPlan.Output ordinary = out(4_000, 0, FePriority.NORMAL);
        assertEquals(8_000, BatteryControllerPlan.criticalWant(List.of(critical, board, ordinary)),
                "the CRITICAL output and the board's declared Critical, nothing else");
        final BatteryControllerPlan.Output limited = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.LIMIT, 3_000, RATE, 1_000, 0, FePriority.CRITICAL, false);
        assertEquals(1_000, BatteryControllerPlan.criticalWant(List.of(limited)), "held to its rate cap");
    }

    @Test
    void theOfferCeilingIsEveryLiveOutputsWireTogether() {
        final BatteryControllerPlan.Output a = out(1_000, 0);
        final BatteryControllerPlan.Output capped = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.LIMIT, 1_000, RATE, 5_000, 0, FePriority.NORMAL, false);
        final BatteryControllerPlan.Output off = new BatteryControllerPlan.Output(true, false,
                BatteryOutputMode.DIRECT, 1_000, RATE, 0, 0, FePriority.NORMAL, false);
        assertEquals(RATE + 5_000, BatteryControllerPlan.wiredCeiling(List.of(a, capped, off)));
    }

    @Test
    void aBoardsCapFollowsItsOutputsRulesNotItsGrant() {
        final BatteryControllerPlan.Output direct = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 9_000, 16_384, 4_000, 0, FePriority.NORMAL, false, true, 1_000);
        final BatteryControllerPlan.Grant served = new BatteryControllerPlan.Grant(300,
                BatteryControllerPlan.Reason.SERVED, false);
        assertEquals(16_384 - 2_000, BatteryControllerPlan.boardCap(direct, served, 50_000, 2_000),
                "the wire less the other loads, never the grant, and a stale limit is ignored in DIRECT");
        final BatteryControllerPlan.Grant rationed = new BatteryControllerPlan.Grant(1_000,
                BatteryControllerPlan.Reason.RATIONED, true);
        assertEquals(1_000, BatteryControllerPlan.boardCap(direct, rationed, 50_000, 0),
                "below the output's own floor the board may run its Critical and nothing more");
        final BatteryControllerPlan.Output surplusOnly = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.SURPLUS, 9_000, 16_384, 0, 0, FePriority.NORMAL, false, true, 0);
        assertEquals(3_000, BatteryControllerPlan.boardCap(surplusOnly, served, 3_000, 0),
                "a surplus output is never promised stored charge");
        final BatteryControllerPlan.Output limitZero = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.LIMIT, 9_000, 16_384, 0, 0, FePriority.NORMAL, false, true, 0);
        assertEquals(0, BatteryControllerPlan.boardCap(limitZero,
                new BatteryControllerPlan.Grant(0, BatteryControllerPlan.Reason.OFF, false), 50_000, 0),
                "a rate cap of 0 is 0, not no cap");
        assertEquals(0, BatteryControllerPlan.boardCap(direct,
                new BatteryControllerPlan.Grant(0, BatteryControllerPlan.Reason.FLOOR, true), 50_000, 0));
    }

    @Test
    void theFourArgumentFormIsTheSameDecisionWithNoClaim() {
        List<BatteryControllerPlan.Output> outputs = List.of(out(500, 0), out(700, 0));
        BatteryControllerPlan.Plan withoutClaim =
                BatteryControllerPlan.plan(outputs, 1_200, 900, 50);
        BatteryControllerPlan.Plan explicitZero =
                BatteryControllerPlan.plan(outputs, 1_200, 900, 50, 0);

        assertEquals(withoutClaim.totalGranted(), explicitZero.totalGranted());
        for (int i = 0; i < outputs.size(); i++) {
            assertEquals(withoutClaim.grants().get(i).reason(), explicitZero.grants().get(i).reason());
        }
    }
}
