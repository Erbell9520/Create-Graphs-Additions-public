package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReserveProjectionTest {
    private static final int CAP = 82_401_116;
    private static final int RATE = 65_536;

    private static BatteryControllerPlan.Output board(int demand) {
        return new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, demand, 32_768, 0, 0,
                FePriority.NORMAL, false, true, 0);
    }

    private static BatteryControllerPlan.Output unwired() {
        return new BatteryControllerPlan.Output(false, true, BatteryOutputMode.DIRECT, 0, 0, 0, 0,
                FePriority.NORMAL, false);
    }

    private static ReserveProjection.Setup save(int stored, int storeRoom) {
        return new ReserveProjection.Setup(stored, CAP, 8_418,
                List.of(unwired(), unwired(), board(10_291)), FeReserveBand.Band.DEFAULT,
                FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, FeChargePolicy.DEFAULT_MAX_CLAIM_PCT, 32_768,
                true, storeRoom, 3_072);
    }

    private static ReserveProjection.Result longRun(ReserveProjection.Setup s) {
        return ReserveProjection.project(s, 144_000, ReserveProjection.STEP_TICKS, 1_200);
    }

    @Test
    @DisplayName("KI-96: the save's controller goes short near 19% after about half an hour, and never empties")
    void theSavesControllerGoesShortAndSettlesRatherThanEmptying() {
        final ReserveProjection.Result r = longRun(save(82_348_375, 0));
        assertFalse(r.shortNow(), "everything is served at 99.9%");
        final int shortAt = r.ticksToShort();

        assertTrue(shortAt > 30_000 && shortAt < 42_000, "first short at " + shortAt);
        final ReserveProjection.Milestone first = r.first(ReserveProjection.Kind.SHORT);
        assertEquals(2, first.output(), "the board on output 3 is the one that goes short");
        assertTrue(first.socPct() >= 15 && first.socPct() <= 22, "goes short at " + first.socPct() + "%");

        final ReserveProjection.Milestone rest = r.first(ReserveProjection.Kind.SETTLES);
        assertNotNull(rest, "the bank comes to rest inside the horizon");
        assertTrue(rest.value() >= 12 && rest.value() <= 19, "settles at " + rest.value() + "%");
        for (byte soc : r.trace()) {
            assertTrue(soc >= FeReserveBand.DEFAULT_FLOOR_PCT, "never drawn under the band floor: " + soc);
        }

        final int oldEmptyIn = 82_348_375 / 1_808;
        assertEquals(45_546, oldEmptyIn, "\"Empty in ~37:57\"");
        assertTrue(shortAt < oldEmptyIn, "the first circuit goes short well before the old empty");
    }

    @Test
    @DisplayName("KI-96: the stance steps down through the band on the way")
    void theStanceStepsThroughTheBand() {
        final ReserveProjection.Result r = longRun(save(82_348_375, 0));
        final List<Integer> stances = r.milestones().stream()
                .filter(m -> m.kind() == ReserveProjection.Kind.STANCE).map(ReserveProjection.Milestone::value)
                .toList();
        assertEquals(List.of(FeReserveReport.Stance.NOMINAL.ordinal(), FeReserveReport.Stance.CONSERVE.ordinal()),
                stances, "SURPLUS to NOMINAL at 60%, NOMINAL to CONSERVE at 30%, never CRITICAL");
    }

    @Test
    @DisplayName("a surplus fills the banks and nothing goes short")
    void aSurplusFills() {
        final ReserveProjection.Setup s = new ReserveProjection.Setup(CAP / 2, CAP, 20_000,
                List.of(board(10_000), unwired(), unwired()), FeReserveBand.Band.DEFAULT,
                FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, 60, 32_768, true, 0, 0);
        final ReserveProjection.Result r = ReserveProjection.project(s);
        assertEquals(-1, r.ticksToShort());
        final ReserveProjection.Milestone full = r.first(ReserveProjection.Kind.FULL);
        assertNotNull(full);

        assertTrue(Math.abs(full.tick() - 4_120) < 200, "full at " + full.tick());
        assertNull(r.first(ReserveProjection.Kind.SETTLES), "a full bank is not settling anywhere");
    }

    @Test
    @DisplayName("KI-95: a capacitor refills out of the spare before the banks do")
    void storesFillBeforeTheBanks() {
        final ReserveProjection.Setup s = new ReserveProjection.Setup(CAP / 2, CAP, 13_000,
                List.of(board(10_000), unwired(), unwired()), FeReserveBand.Band.DEFAULT,
                FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, 60, 32_768, true,
                CapacitorDischarge.CAPACITY, CapacitorDischarge.CHARGE_FE_PER_TICK);
        final ReserveProjection.Result r = ReserveProjection.project(s);
        final ReserveProjection.Milestone stores = r.first(ReserveProjection.Kind.STORES_FULL);
        assertNotNull(stores, "the capacitor fills");

        assertTrue(Math.abs(stores.tick() - CapacitorDischarge.CAPACITY / 3_000) < 200, "stores full at "
                + stores.tick());
        assertEquals(50, r.trace()[1], "the banks do not move while the capacitor takes the spare");
        assertTrue(r.trace()[r.trace().length - 1] > 50, "and fill once it is full");
    }

    @Test
    @DisplayName("KI-95: the store budget is the spare, plus a deep bank's allowance only while nothing is short")
    void storeBudgetRule() {
        final FeReserveReport.Stance surplus = FeReserveReport.Stance.SURPLUS;
        assertEquals(10_500, ReserveProjection.storeBudget(500, 10_000, surplus, false, true));
        assertEquals(500, ReserveProjection.storeBudget(500, 10_000, surplus, true, true),
                "never a battery out of the bank while a machine goes short");
        assertEquals(500, ReserveProjection.storeBudget(500, 10_000, FeReserveReport.Stance.NOMINAL, false, true),
                "below healthy the claim is refilling that bank; the capacitor gets the spare only");
        assertEquals(0, ReserveProjection.storeBudget(500, 10_000, surplus, false, false),
                "a board above in brownout outranks every battery below it");
    }

    @Test
    @DisplayName("an output's own floor is a milestone, named by output")
    void anOutputFloorTrips() {
        final BatteryControllerPlan.Output floored = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 4_000, 32_768, 0, 70, FePriority.LOW, false);
        final ReserveProjection.Setup s = new ReserveProjection.Setup(CAP * 9 / 10, CAP, 1_000,
                List.of(floored, board(3_000), unwired()), FeReserveBand.Band.DEFAULT,
                FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, 60, 32_768, true, 0, 0);
        final ReserveProjection.Result r = ReserveProjection.project(s);
        final ReserveProjection.Milestone floor = r.first(ReserveProjection.Kind.FLOOR, 0);
        assertNotNull(floor, "output 1's 70% floor trips");
        assertTrue(floor.socPct() <= 70 && floor.socPct() >= 68, "at " + floor.socPct());
    }

    @Test
    @DisplayName("the wire form decodes to the same milestones")
    void packRoundTrips() {
        final ReserveProjection.Result r = ReserveProjection.project(save(82_348_375, 0));
        final ReserveProjection.Result back = ReserveProjection.Result.unpack(r.trace(), r.packMilestones(),
                r.shortNow());
        assertEquals(r.milestones(), back.milestones());
        assertArrayEquals(r.trace(), back.trace());
        assertEquals(ReserveProjection.SAMPLES, r.trace().length);
        assertTrue(ReserveProjection.Result.unpack(null, new int[]{1, 99, 0, 0, 0}, false).milestones().isEmpty(),
                "an unknown kind is dropped, not thrown");
    }

    @Test
    @DisplayName("KI-117: the claim is judged on the player's band, never on one a board's hold raised")
    void theClaimReadsTheConfiguredBandNotTheHeldOne() {
        final FeReserveBand.Band held = new FeReserveBand.Band(70, 30, 60);
        final List<BatteryControllerPlan.Output> outs = List.of(board(10_000), unwired(), unwired());
        final ReserveProjection.Result r = ReserveProjection.project(new ReserveProjection.Setup(
                (int) (CAP * 65L / 100), CAP, 10_000, outs, held, FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, 60,
                32_768, true, 0, 0, SupplyForecast.Result.NONE, FeReserveBand.Band.DEFAULT, null, 0));
        assertFalse(r.shortNow(), "no claim on a bank above the player's healthy mark");

        final ReserveProjection.Result shipped = ReserveProjection.project(new ReserveProjection.Setup(
                (int) (CAP * 65L / 100), CAP, 10_000, outs, held, FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, 60,
                32_768, true, 0, 0, SupplyForecast.Result.NONE, held, null, 0));
        assertTrue(shipped.shortNow(), "\"An output is short now\" for a claim the controller never makes");
    }

    @Test
    @DisplayName("KI-110: below the floor the forecast no longer banks what a CRITICAL output needed")
    void theClaimsBoundLeavesCriticalItsWant() {
        final BatteryControllerPlan.Output critical = new BatteryControllerPlan.Output(true, true,
                BatteryOutputMode.DIRECT, 6_000, 32_768, 0, 0, FePriority.CRITICAL, false);
        final ReserveProjection.Result r = ReserveProjection.project(new ReserveProjection.Setup(
                CAP * 8 / 100, CAP, 5_000, List.of(critical, unwired(), unwired()), FeReserveBand.Band.DEFAULT,
                FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE, 60, 32_768, true, 0, 0));

        assertEquals(7, r.trace()[1], "the banks carry Critical, 8% to 7% in thirty seconds");

        assertFalse(r.shortNow(), "and the output is served in full, 5,000 made and 1,000 from the banks");
    }

    @Test
    @DisplayName("no storage, no projection")
    void noStorage() {
        final ReserveProjection.Setup s = new ReserveProjection.Setup(0, 0, 1_000, List.of(board(10)),
                FeReserveBand.Band.DEFAULT, 1_200, RATE, RATE, 60, 32_768, true, 0, 0);
        assertEquals(0, ReserveProjection.project(s).trace().length);
    }
}
