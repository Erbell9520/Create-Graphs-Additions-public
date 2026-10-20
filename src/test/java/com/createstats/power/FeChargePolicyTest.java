package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeChargePolicyTest {
    private static final FeReserveBand.Band BAND = FeReserveBand.Band.DEFAULT;
    private static final int MAX = FeChargePolicy.DEFAULT_MAX_CLAIM_PCT;
    private static final int ROOM = Integer.MAX_VALUE / 4;

    private static int claimAt(int socPct, int generation) {
        return FeChargePolicy.claim(generation, socPct, 1_000_000, BAND, MAX, ROOM);
    }

    @Test
    void aHealthyReserveClaimsNothing() {
        assertEquals(0, claimAt(BAND.healthyPct(), 10_000));
        assertEquals(0, claimAt(80, 10_000));
        assertEquals(0, claimAt(100, 10_000));
    }

    @Test
    void thereIsNoStorageToHaveAPolicyAbout() {
        assertEquals(0, FeChargePolicy.claim(10_000, 0, 0, BAND, MAX, ROOM));
    }

    @Test
    void aFullBankIsNeverClaimedAgainst() {
        assertEquals(0, FeChargePolicy.claim(10_000, 5, 1_000_000, BAND, MAX, 0));
    }

    @Test
    void aClaimNeverExceedsTheHeadroomTheBanksHave() {
        assertEquals(64, FeChargePolicy.claim(10_000, 5, 1_000_000, BAND, MAX, 64));
    }

    @Test
    void turningTheCeilingDownToZeroSwitchesTheBehaviourOff() {
        assertEquals(0, FeChargePolicy.claim(10_000, 0, 1_000_000, BAND, 0, ROOM));
    }

    @Test
    void theClaimIsContinuousAtHealthy() {
        assertEquals(0, claimAt(BAND.healthyPct(), 100_000));
        assertTrue(claimAt(BAND.healthyPct() - 1, 100_000) <= 100_000 * MAX / 100 / 40,
                "the claim just below healthy should be a sliver, not a step");
    }

    @Test
    void theClaimIsContinuousAtTheFloor() {
        final int atFloor = claimAt(BAND.floorPct(), 10_000);
        final int justBelow = claimAt(BAND.floorPct() - 1, 10_000);
        assertEquals(justBelow, atFloor);
        assertEquals(10_000 * MAX / 100, atFloor);
    }

    @Test
    void theClaimNeverRisesAsTheReserveFills() {
        int previous = Integer.MAX_VALUE;
        for (int soc = 0; soc <= 100; soc++) {
            final int claim = claimAt(soc, 10_000);
            assertTrue(claim <= previous,
                    "claim rose from " + previous + " to " + claim + " at " + soc + "%");
            previous = claim;
        }
    }

    @Test
    void aBreakEvenBaseRecovers() {
        final int generation = 10_000;
        final int claim = claimAt(4, generation);
        assertTrue(claim > 0, "a base in a hole must keep something back");
        assertEquals(generation * MAX / 100, claim);
    }

    @Test
    void theClaimIsNeverTheWholeOfGeneration() {
        final int claim = FeChargePolicy.claim(10_000, 0, 1_000_000, BAND, 100, ROOM);
        assertTrue(claim < 10_000, "claimed all " + claim + " of generation");
        assertEquals(9_999, claim);
    }

    @Test
    void nothingIsClaimedWithNothingComingIn() {
        assertEquals(0, claimAt(0, 0));
        assertEquals(0, claimAt(50, 0));
    }

    @Test
    void aDegenerateBandStillBehaves() {
        final FeReserveBand.Band squashed = new FeReserveBand.Band(50, 50, 50);
        for (int soc = 0; soc <= 100; soc++) {
            final int claim = FeChargePolicy.claim(10_000, soc, 1_000_000, squashed, MAX, ROOM);
            assertTrue(claim >= 0 && claim < 10_000, "out of range at " + soc + "%: " + claim);
        }
        assertEquals(0, FeChargePolicy.claim(10_000, 50, 1_000_000, squashed, MAX, ROOM));
    }

    @Test
    void theClaimNeverTakesWhatCriticalNeeds() {
        final int bus = 5_000;
        final int fromReserve = 549;
        final int criticalWant = 6_000;
        final int claim = Math.min(FeChargePolicy.claimable(bus, fromReserve, criticalWant),
                FeChargePolicy.claim(5_000, 8, 82_401_116, FeReserveBand.Band.DEFAULT, MAX, ROOM));
        assertEquals(0, claim, "the plan's pool, bus plus allowance, is already short of Critical");
        assertTrue(bus + fromReserve - claim >= Math.min(criticalWant, bus + fromReserve),
                "whatever there is goes to Critical before the banks");

        final int shipped = Math.min(Math.max(0, bus + 5_000 - criticalWant),
                FeChargePolicy.claim(5_000, 8, 82_401_116, FeReserveBand.Band.DEFAULT, MAX, ROOM));
        assertEquals(3_000, shipped, "60% of generation claimed below the floor");
        assertEquals(2_549, bus + fromReserve - shipped, "and the CRITICAL output handed 2,549 of its 6,000");

        assertEquals(20_000, FeChargePolicy.claimable(20_000, 1_000, 1_000));
        assertEquals(0, FeChargePolicy.claimable(-5, -5, 100));
    }
}
