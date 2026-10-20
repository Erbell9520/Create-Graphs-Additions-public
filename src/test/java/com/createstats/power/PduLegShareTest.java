package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduLegShareTest {
    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    @Test
    @DisplayName("a hungry leg cannot starve the legs after it")
    void aHungryLegCannotStarveTheOnesAfterIt() {
        final int[] want = {8000, 500, 500, 500};
        final int[] budget = PduLegShare.split(want, 5120, 0);

        assertTrue(budget[1] > 0, "leg 1 starved");
        assertTrue(budget[2] > 0, "leg 2 starved");
        assertTrue(budget[3] > 0, "leg 3 starved");
        assertEquals(5120, sum(budget), "the whole pool is still spent");
    }

    @Test
    @DisplayName("the small legs are served in full and the greedy one takes the rest")
    void theSmallLegsAreServedInFullAndTheGreedyOneTakesTheRest() {
        final int[] want = {8000, 500, 500, 500};
        final int[] budget = PduLegShare.split(want, 5120, 0);

        assertEquals(500, budget[1]);
        assertEquals(500, budget[2]);
        assertEquals(500, budget[3]);
        assertEquals(5120 - 1500, budget[0], "the mop-up pass hands the remainder back");
    }

    @Test
    @DisplayName("plenty for everyone means nobody is rationed")
    void plentyForEveryoneMeansNobodyIsRationed() {
        final int[] want = {800, 500, 300};
        final int[] budget = PduLegShare.split(want, 100_000, 0);

        assertEquals(800, budget[0]);
        assertEquals(500, budget[1]);
        assertEquals(300, budget[2]);
        assertEquals(1600, sum(budget), "no leg is given more than it asked for");
    }

    @Test
    @DisplayName("a leg asking for nothing is allotted nothing")
    void aLegAskingForNothingIsAllottedNothing() {
        final int[] want = {0, 1000, 0, 1000};
        final int[] budget = PduLegShare.split(want, 1200, 0);

        assertEquals(0, budget[0]);
        assertEquals(0, budget[2]);
        assertEquals(1200, budget[1] + budget[3]);
    }

    @Test
    @DisplayName("equal legs and an odd pool: the rounding rotates instead of always favouring one")
    void theRoundingRotates() {
        final int[] want = {1000, 1000, 1000};
        final boolean[] everShort = new boolean[3];
        for (int tick = 0; tick < 3; tick++) {
            final int[] budget = PduLegShare.split(want, 1000, tick);
            for (int leg = 0; leg < 3; leg++) {
                if (budget[leg] < 334) {
                    everShort[leg] = true;
                }
            }
        }
        assertTrue(everShort[0] && everShort[1] && everShort[2],
                "the same leg took the short share on every tick");
    }

    @Test
    @DisplayName("a pool smaller than the leg count still reaches somebody")
    void aTinyPoolStillReachesSomebody() {
        final int[] want = {100, 100, 100, 100, 100, 100, 100, 100, 100};
        final int[] budget = PduLegShare.split(want, 3, 0);

        assertEquals(3, sum(budget), "a share that rounded to zero would stall the whole board");
    }

    @Test
    @DisplayName("the split never hands out more than the pool")
    void theSplitNeverOverspends() {
        for (int cursor = 0; cursor < 9; cursor++) {
            final int[] want = {5000, 0, 120, 9, 40_000, 1, 0, 700, 65};
            final int[] budget = PduLegShare.split(want, 4096, cursor);
            assertTrue(sum(budget) <= 4096, "overspent at cursor " + cursor);
            for (int leg = 0; leg < want.length; leg++) {
                assertTrue(budget[leg] <= want[leg], "leg " + leg + " got more than it asked for");
            }
        }
    }

    @Test
    @DisplayName("two uncapped legs do not overflow into a negative want")
    void twoUncappedLegsDoNotOverflow() {
        final int uncapped = PduLegShare.addRates(FeGridSolver.NO_CEILING, FeGridSolver.NO_CEILING);
        assertEquals(Integer.MAX_VALUE, uncapped);

        final int[] budget = PduLegShare.split(new int[]{uncapped, 1000}, 6000, 0);
        assertTrue(budget[0] > 0, "the uncapped leg asked for a negative amount and got nothing");
        assertEquals(1000, budget[1]);
    }

    @Test
    @DisplayName("no pool, no legs, no crash")
    void degenerateInputs() {
        assertEquals(0, sum(PduLegShare.split(new int[]{1000, 1000}, 0, 0)));
        assertEquals(0, PduLegShare.split(new int[0], 500, 0).length);
        assertEquals(0, sum(PduLegShare.split(new int[]{0, 0, 0}, 500, 2)));
    }

    private static int[] tiers(FePriority... ranks) {
        final int[] out = new int[ranks.length];
        for (int i = 0; i < ranks.length; i++) {
            out[i] = ranks[i].ordinal();
        }
        return out;
    }

    @Test
    @DisplayName("a ranked leg is served before a lesser one, and not fair shared against it")
    void banksAreServedInOrder() {
        final int[] want = {1000, 1000};
        final int[] rank = tiers(FePriority.OPTIONAL, FePriority.CRITICAL);
        final int[] budget = PduLegShare.split(want, rank, 600, 0);

        assertEquals(600, budget[1], "the critical leg takes what it needs first");
        assertEquals(0, budget[0], "and the optional leg gets the remainder, which is nothing");
    }

    @Test
    @DisplayName("what a ranked leg cannot use falls through to the next band")
    void theRemainderFallsThrough() {
        final int[] want = {1000, 200};
        final int[] budget = PduLegShare.split(want, tiers(FePriority.LOW, FePriority.CRITICAL),
                600, 0);

        assertEquals(200, budget[1], "the critical leg is small and takes only what it wants");
        assertEquals(400, budget[0], "the rest is not hoarded on its behalf");
    }

    @Test
    @DisplayName("legs of equal rank are still equals")
    void oneBandIsStillAFairShare() {
        final int[] want = {8000, 500, 500};
        final int[] rank = tiers(FePriority.HIGH, FePriority.HIGH, FePriority.HIGH);
        final int[] budget = PduLegShare.split(want, rank, 5120, 0);

        assertEquals(500, budget[1], "the fair share applies unchanged inside a band");
        assertEquals(500, budget[2]);
        assertEquals(5120 - 1000, budget[0]);
    }

    @Test
    @DisplayName("ranking a leg cannot promote the devices on it, because they are not here")
    void aRankOrdersLegsAndNeverDevices() {
        final int[] optionalRound = {0, 400};
        final int[] rank = tiers(FePriority.CRITICAL, FePriority.OPTIONAL);
        final int[] budget = PduLegShare.split(optionalRound, rank, 400, 0);

        assertEquals(0, budget[0], "a critical leg with nothing of this class asks for nothing");
        assertEquals(400, budget[1], "so the whole round goes to the leg that does");
    }

    @Test
    @DisplayName("a null rank array is every leg equal, which is how a board starts")
    void unrankedIsAllEqual() {
        final int[] want = {1000, 1000};
        assertEquals(PduLegShare.split(want, 600, 0)[0], PduLegShare.split(want, null, 600, 0)[0]);
    }

    @Test
    @DisplayName("ranked or not, the split never overspends")
    void rankedSplitsNeverOverspend() {
        for (int cursor = 0; cursor < 9; cursor++) {
            final int[] want = {5000, 0, 120, 9, 40_000, 1, 0, 700, 65};
            final int[] rank = {2, 0, 4, 1, 3, 2, 0, 1, 4};
            final int[] budget = PduLegShare.split(want, rank, 4096, cursor);
            assertTrue(sum(budget) <= 4096, "overspent at cursor " + cursor);
            for (int leg = 0; leg < want.length; leg++) {
                assertTrue(budget[leg] <= want[leg], "leg " + leg + " got more than it asked for");
            }
        }
    }
}
