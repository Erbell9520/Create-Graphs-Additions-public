package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeBoardDealTest {
    private static final long NOW = 1_000L;

    private static FeBoardDeal.Offer offer(int run, int crit) {
        return new FeBoardDeal.Offer(run, crit, 0, FeReserveReport.Stance.NOMINAL, 40, false);
    }

    private static List<FeBoardDeal.Answer> split(FeBoardDeal.Offer o, FeBoardDeal.Request... r) {
        return FeBoardDeal.split(7L, false, o, List.of(r), null, null, NOW);
    }

    @Test
    void aBoardThatFitsIsGrantedInFull() {
        final FeBoardDeal.Answer a = split(offer(10_000, 10_000),
                new FeBoardDeal.Request(1L, 500, 6_000, 2_000)).get(0);
        assertEquals(500, a.critical());
        assertEquals(6_000, a.running());
        assertEquals(2_000, a.restore());
        assertEquals(FeBoardDeal.Reason.GRANTED, a.reason());
        assertEquals(0, a.shortfall());
    }

    @Test
    void twoBoardsShareOnePoolAndNeverGetItTwice() {
        final List<FeBoardDeal.Answer> both = split(offer(8_000, 8_000),
                new FeBoardDeal.Request(1L, 0, 6_000, 0),
                new FeBoardDeal.Request(2L, 0, 6_000, 0));
        assertEquals(8_000, both.get(0).total() + both.get(1).total());
        assertEquals(4_000, both.get(0).running());
        assertEquals(4_000, both.get(1).running());

        assertEquals(FeBoardDeal.Reason.SHARED, both.get(0).reason());

        assertEquals(16_000, 8_000 * 2);
    }

    @Test
    void criticalIsServedBeforeRunningAndRunningBeforeRestore() {
        final FeBoardDeal.Answer a = split(offer(3_000, 3_000),
                new FeBoardDeal.Request(1L, 1_000, 1_500, 5_000)).get(0);
        assertEquals(1_000, a.critical());
        assertEquals(1_500, a.running());
        assertEquals(500, a.restore());
    }

    @Test
    void criticalMayRideDeeperThanOrdinaryLoad() {
        final FeBoardDeal.Answer a = split(offer(0, 800),
                new FeBoardDeal.Request(1L, 600, 4_000, 0)).get(0);
        assertEquals(600, a.critical());
        assertEquals(0, a.running());
    }

    @Test
    void aShortAnswerCannotShrinkTheNextQuestion() {
        final FeBoardDeal.Answer before = split(offer(5_000, 5_000),
                new FeBoardDeal.Request(1L, 0, 9_000, 0)).get(0);
        final FeBoardDeal.Answer after = split(offer(5_000, 5_000),
                new FeBoardDeal.Request(1L, 0, 5_000, 4_000)).get(0);
        assertEquals(before.total(), after.total());
        assertEquals(5_000, after.running());
        assertEquals(0, after.restore());
    }

    @Test
    void theWireAndAHeldOutputAreNamed() {
        final List<FeBoardDeal.Answer> a = FeBoardDeal.split(7L, false, offer(50_000, 50_000),
                List.of(new FeBoardDeal.Request(1L, 0, 9_000, 0), new FeBoardDeal.Request(2L, 0, 1_000, 0)),
                new int[] {4_096, Integer.MAX_VALUE}, new boolean[] {false, true}, NOW);
        assertEquals(4_096, a.get(0).total());
        assertEquals(FeBoardDeal.Reason.WIRE, a.get(0).reason());
        assertEquals(0, a.get(1).total());
        assertEquals(FeBoardDeal.Reason.HELD, a.get(1).reason());
    }

    @Test
    void theReserveItselfIsNamedWhenItIsTheLimit() {
        final FeBoardDeal.Offer floor = new FeBoardDeal.Offer(0, 200, 0,
                FeReserveReport.Stance.CRITICAL, 4, false);
        assertEquals(FeBoardDeal.Reason.FLOOR, FeBoardDeal.split(7L, true, floor,
                List.of(new FeBoardDeal.Request(1L, 100, 8_000, 0)), null, null, NOW).get(0).reason());

        final FeBoardDeal.Offer charging = new FeBoardDeal.Offer(3_000, 3_000, 2_000,
                FeReserveReport.Stance.CONSERVE, 20, false);
        assertEquals(FeBoardDeal.Reason.CHARGING, FeBoardDeal.split(7L, false, charging,
                List.of(new FeBoardDeal.Request(1L, 0, 4_500, 0)), null, null, NOW).get(0).reason());

        final FeBoardDeal.Offer band = new FeBoardDeal.Offer(3_000, 3_000, 0,
                FeReserveReport.Stance.CONSERVE, 20, false);
        assertEquals(FeBoardDeal.Reason.BAND, FeBoardDeal.split(7L, false, band,
                List.of(new FeBoardDeal.Request(1L, 0, 4_500, 0)), null, null, NOW).get(0).reason());

        final FeBoardDeal.Offer upstream = new FeBoardDeal.Offer(3_000, 3_000, 0,
                FeReserveReport.Stance.NOMINAL, 50, true);
        assertEquals(FeBoardDeal.Reason.UPSTREAM, FeBoardDeal.split(7L, false, upstream,
                List.of(new FeBoardDeal.Request(1L, 0, 4_500, 0)), null, null, NOW).get(0).reason());

        assertEquals(FeBoardDeal.Reason.NOTHING, FeBoardDeal.split(7L, false, offer(0, 0),
                List.of(new FeBoardDeal.Request(1L, 0, 100, 0)), null, null, NOW).get(0).reason());
    }

    @Test
    void aBareBankBelowItsFloorAnswersCriticalOnly() {
        final FeReserveReport bank = FeReserveBand.assess(0L, new FeReserveBand.Params(
                705_183, 15_754_308, 4_860, 0, 1_000_000, 16_384, FeReserveBand.Band.DEFAULT));
        assertEquals(FeReserveReport.Stance.CRITICAL, bank.stance());
        final FeBoardDeal.Answer a = FeBoardDeal.split(9L, true,
                FeBoardDeal.Offer.of(bank, 0, false),
                List.of(new FeBoardDeal.Request(9L, 0, 8_704, 4_608)), null, null, NOW).get(0);

        assertEquals(4_860, a.running());
        assertEquals(0, a.restore());
        assertEquals(FeBoardDeal.Reason.FLOOR, a.reason());
        assertTrue(a.own());
    }

    @Test
    void theShareLosesNothingToRounding() {
        final int[] got = FeBoardDeal.share(new int[] {1, 1, 1}, 2);
        assertEquals(2, got[0] + got[1] + got[2]);
        final int[] big = FeBoardDeal.share(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 1_000);
        assertEquals(1_000, big[0] + big[1]);
        final int[] none = FeBoardDeal.share(new int[] {5, 5}, 0);
        assertEquals(0, none[0] + none[1]);
    }

    @Test
    void theFeedBanksShareOfAnIntakeIsMeasured() {
        assertEquals(36_261, FeBoardDeal.fromStorage(41_157, 36_000, 40_860));
        assertEquals(0, FeBoardDeal.fromStorage(5_000, 0, 5_000));
        assertEquals(5_000, FeBoardDeal.fromStorage(5_000, 9_000, 9_000));
        assertEquals(0, FeBoardDeal.fromStorage(0, 100, 100));
        assertEquals(0, FeBoardDeal.fromStorage(100, 100, 0));
    }

    @Test
    void answersFromTwoControllersAdd() {
        final FeBoardDeal.Request half = new FeBoardDeal.Request(1L, 0, 5_000, 0);
        final FeBoardDeal.Answer a = new FeBoardDeal.Answer(5L, false, half, 0, 3_000, 0,
                FeBoardDeal.Reason.BAND, FeReserveReport.Stance.CONSERVE, 20, NOW, 0);
        final FeBoardDeal.Answer b = new FeBoardDeal.Answer(6L, false, half, 0, 4_000, 0,
                FeBoardDeal.Reason.SHARED, FeReserveReport.Stance.NOMINAL, 50, NOW, 900);
        final FeBoardDeal.Answer both = FeBoardDeal.combine(List.of(a, b));
        assertEquals(7_000, both.running());
        assertEquals(10_000, both.asked().total(), "the shares add back to the whole request");
        assertEquals(3_000, both.shortfall());
        assertEquals(900, both.spare(), "and so do the spares");
        assertEquals(FeBoardDeal.Reason.BAND, both.reason());
    }

    @Test
    void aFullAnswerCarriesTheSpareTheBoardsWireCouldReach() {
        final FeBoardDeal.Request rig = new FeBoardDeal.Request(1L, 0, 9_267, 1_024);
        final FeBoardDeal.Answer a = FeBoardDeal.split(7L, false, offer(32_768, 32_768), List.of(rig),
                new int[] {49_152}, null, NOW).get(0);
        assertEquals(10_291, a.total(), "granted in full");
        assertEquals(FeBoardDeal.Reason.GRANTED, a.reason());
        assertEquals(32_768 - 10_291, a.spare(), "the pool's room left, 22,477 FE/t");

        final FeBoardDeal.Answer thin = FeBoardDeal.split(7L, false, offer(32_768, 32_768), List.of(rig),
                new int[] {11_000}, null, NOW).get(0);
        assertEquals(11_000 - 10_291, thin.spare());

        assertEquals(0, new FeBoardDeal.Answer(7L, false, rig, 0, 9_267, 1_024,
                FeBoardDeal.Reason.GRANTED, FeReserveReport.Stance.SURPLUS, 72, NOW).spare());
    }

    @Test
    void boardsSharingOnePoolAreEachToldWhatIsLeftOfIt() {
        final List<FeBoardDeal.Answer> both = split(offer(12_000, 12_000),
                new FeBoardDeal.Request(1L, 0, 4_000, 0), new FeBoardDeal.Request(2L, 0, 6_000, 0));
        assertEquals(2_000, both.get(0).spare());
        assertEquals(2_000, both.get(1).spare(), "one pool, one remainder, told to each");
        final List<FeBoardDeal.Answer> tight = split(offer(8_000, 8_000),
                new FeBoardDeal.Request(1L, 0, 4_000, 0), new FeBoardDeal.Request(2L, 0, 6_000, 0));
        assertEquals(0, tight.get(0).spare(), "a short pool has nothing to spare");
    }

    @Test
    void aHeldBoardHasNoSpare() {
        final List<FeBoardDeal.Answer> a = FeBoardDeal.split(7L, false, offer(50_000, 50_000),
                List.of(new FeBoardDeal.Request(1L, 0, 1_000, 0)), null, new boolean[] {true}, NOW);
        assertEquals(0, a.get(0).spare());
        assertEquals(FeBoardDeal.Reason.HELD, a.get(0).reason());
    }

    @Test
    void aRationingStanceGrantsNoRestoreEvenWithRoomForIt() {
        final FeBoardDeal.Offer conserve = new FeBoardDeal.Offer(20_000, 20_000, 0,
                FeReserveReport.Stance.CONSERVE, 22, false);
        final FeBoardDeal.Answer a = FeBoardDeal.split(7L, false, conserve,
                List.of(new FeBoardDeal.Request(1L, 500, 6_000, 2_000)), null, null, NOW).get(0);
        assertEquals(500, a.critical());
        assertEquals(6_000, a.running(), "what runs keeps running");
        assertEquals(0, a.restore(), "and nothing new comes back, whatever the droop has room for");
        assertEquals(FeBoardDeal.Reason.BAND, a.reason(), "the band's rule, not a shortage");

        final FeBoardDeal.Offer nominal = new FeBoardDeal.Offer(20_000, 20_000, 0,
                FeReserveReport.Stance.NOMINAL, 45, false);
        assertEquals(2_000, FeBoardDeal.split(7L, false, nominal,
                List.of(new FeBoardDeal.Request(1L, 500, 6_000, 2_000)), null, null, NOW).get(0).restore());
    }

    @Test
    void aRequestSplitBetweenControllersAddsBackToItExactly() {
        final FeBoardDeal.Request whole = new FeBoardDeal.Request(1L, 1_001, 7_003, 2_005);
        final FeBoardDeal.Request[] shares = FeBoardDeal.splitRequest(whole, new int[] {30_000, 10_000});
        assertEquals(whole.critical(), shares[0].critical() + shares[1].critical());
        assertEquals(whole.running(), shares[0].running() + shares[1].running());
        assertEquals(whole.restore(), shares[0].restore() + shares[1].restore());

        assertEquals(5_253, shares[0].running(), "three quarters to the controller that can give three times as much");
        final FeBoardDeal.Request[] even = FeBoardDeal.splitRequest(whole, new int[] {0, 0});
        assertEquals(whole.total(), even[0].total() + even[1].total(), "nothing known, equal shares");
        assertEquals(whole, FeBoardDeal.splitRequest(whole, new int[] {5})[0], "one controller, the whole request");
    }

    @Test
    void twoControllersAnsweringShareSplitsNeverGrantMoreThanWasAsked() {
        final FeBoardDeal.Request whole = new FeBoardDeal.Request(1L, 0, 10_000, 0);
        final FeBoardDeal.Request[] shares = FeBoardDeal.splitRequest(whole, new int[] {1, 1});
        final FeBoardDeal.Answer a = FeBoardDeal.split(5L, false, offer(50_000, 50_000),
                List.of(shares[0]), null, null, NOW).get(0);
        final FeBoardDeal.Answer b = FeBoardDeal.split(6L, false, offer(50_000, 50_000),
                List.of(shares[1]), null, null, NOW).get(0);
        assertEquals(10_000, FeBoardDeal.combine(List.of(a, b)).total());

        assertEquals(20_000, 2 * FeBoardDeal.split(5L, false, offer(50_000, 50_000),
                List.of(whole), null, null, NOW).get(0).total());
    }

    @Test
    void oneOutputsWireIsSharedByItsBoardsNotHandedToEach() {
        final int[] shares = FeBoardDeal.shareWire(new int[] {6_000, 2_000}, 8_192);
        assertEquals(8_192, shares[0] + shares[1], "all of the wire, between them");
        assertEquals(6_144, shares[0]);
        assertEquals(8_192, FeBoardDeal.shareWire(new int[] {100}, 8_192)[0], "a lone board has the whole wire");
        assertEquals(Integer.MAX_VALUE, FeBoardDeal.shareWire(new int[] {1, 1}, Integer.MAX_VALUE)[1]);
        assertEquals(0, FeBoardDeal.shareWire(new int[] {1, 1}, 0)[0]);
    }

    @Test
    void theLegBanksAnswerOnlyWhatTheControllerLeftAndTheTwoAdd() {
        final FeBoardDeal.Request r = new FeBoardDeal.Request(1L, 1_000, 8_000, 3_000);
        final FeBoardDeal.Answer controller = FeBoardDeal.split(5L, false, offer(6_000, 6_000),
                List.of(r), null, null, NOW).get(0);
        final FeBoardDeal.Request left = FeBoardDeal.residual(r, controller);
        assertEquals(0, left.critical());
        assertEquals(3_000, left.running());
        assertEquals(3_000, left.restore());
        final FeBoardDeal.Answer legs = FeBoardDeal.split(1L, true, offer(4_000, 4_000),
                List.of(left), null, null, NOW).get(0);
        final FeBoardDeal.Answer both = FeBoardDeal.topUp(controller, legs);
        assertEquals(r, both.asked(), "the question stays the board's own");
        assertEquals(10_000, both.total());
        assertEquals(8_000, both.running());
        assertEquals(1_000, both.restore());
        assertTrue(both.total() <= r.total(), "nothing promised twice");
        assertEquals(controller, FeBoardDeal.topUp(controller, null));
    }

    @Test
    void answersFollowTheTierTheirOutputsAreFedIn() {
        final int pool = 10_000;
        final BatteryControllerPlan.Output high = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 4_096, 4_096, 0, 0, FePriority.HIGH, false, true, 0);
        final BatteryControllerPlan.Output low = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 65_536, 65_536, 0, 0, FePriority.LOW, false, true, 0);
        final BatteryControllerPlan.Plan plan = BatteryControllerPlan.plan(List.of(high, low), 0, pool, 50);
        final int[] caps = {BatteryControllerPlan.boardCap(high, plan.grants().get(0), 0, 0),
                BatteryControllerPlan.boardCap(low, plan.grants().get(1), 0, 0)};
        final List<FeBoardDeal.Request> asks = List.of(new FeBoardDeal.Request(1L, 0, 8_000, 0),
                new FeBoardDeal.Request(2L, 0, 8_000, 0));
        final FeBoardDeal.Offer offer = offer(pool, pool);

        final List<FeBoardDeal.Answer> tiered = FeBoardDeal.split(7L, false, offer, asks, caps,
                new boolean[] {false, false}, new int[] {FePriority.HIGH.ordinal(), FePriority.LOW.ordinal()}, NOW);
        assertEquals(plan.grants().get(0).granted(), tiered.get(0).total(), "HIGH answered what it is fed");
        assertEquals(plan.grants().get(1).granted(), tiered.get(1).total(), "LOW answered what it is fed");

        final List<FeBoardDeal.Answer> flat = FeBoardDeal.split(7L, false, offer, asks, caps,
                new boolean[] {false, false}, NOW);
        assertEquals(3_387, flat.get(0).total());
        assertEquals(6_613, flat.get(1).total());
    }

    @Test
    void oneTierForEveryBoardIsTheSplitAsItWas() {
        final List<FeBoardDeal.Request> asks = List.of(new FeBoardDeal.Request(1L, 300, 5_000, 2_000),
                new FeBoardDeal.Request(2L, 100, 7_000, 1_000), new FeBoardDeal.Request(3L, 0, 3_000, 4_000));
        for (int pool : new int[] {0, 1_000, 9_000, 15_000, 40_000}) {
            final FeBoardDeal.Offer o = offer(pool, pool + 500);
            final List<FeBoardDeal.Answer> flat = FeBoardDeal.split(7L, false, o, asks, null, null, NOW);
            final List<FeBoardDeal.Answer> same = FeBoardDeal.split(7L, false, o, asks, null, null,
                    new int[] {2, 2, 2}, NOW);
            assertEquals(flat, same, "pool " + pool);
        }
    }

    @Test
    void aLowerTierIsNeverAnsweredPastAHigherOneLeftShort() {
        final java.util.Random rnd = new java.util.Random(185L);
        for (int run = 0; run < 2_000; run++) {
            final int n = 2 + rnd.nextInt(3);
            final List<FeBoardDeal.Request> asks = new java.util.ArrayList<>();
            final int[] tiers = new int[n];
            final int[] caps = new int[n];
            for (int i = 0; i < n; i++) {
                asks.add(new FeBoardDeal.Request(i, rnd.nextInt(2_000), rnd.nextInt(20_000), rnd.nextInt(10_000)));
                tiers[i] = rnd.nextInt(FePriority.values().length);
                caps[i] = rnd.nextBoolean() ? Integer.MAX_VALUE : 1 + rnd.nextInt(30_000);
            }
            final int pool = rnd.nextInt(60_000);
            final List<FeBoardDeal.Answer> a = FeBoardDeal.split(7L, false, offer(pool, pool), asks, caps,
                    null, tiers, NOW);
            for (int hi = 0; hi < n; hi++) {
                final int wantRunning = Math.min(asks.get(hi).running(), Math.max(0, caps[hi] - a.get(hi).critical()));
                if (a.get(hi).running() >= wantRunning) {
                    continue;
                }
                for (int lo = 0; lo < n; lo++) {
                    if (tiers[lo] > tiers[hi]) {
                        assertEquals(0, a.get(lo).running() + a.get(lo).restore(),
                                "run " + run + ": a lower tier answered while a higher one runs short");
                    }
                }
            }
        }
    }
}
