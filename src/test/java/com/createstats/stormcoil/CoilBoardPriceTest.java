package com.createstats.stormcoil;

import com.createstats.power.FeDeviceDemand;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduLegPlan;
import com.createstats.power.PduLoadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilBoardPriceTest {
    private static final int STUD_7 = 6;
    private static final int NORMAL = FePriority.NORMAL.ordinal();

    @Test
    @DisplayName("ready and idle costs nothing; recharging or fighting costs a full volley a second, at every stage")
    void thePrice() {
        final int[] arcRates = new int[4];
        for (CoilStage st : CoilStage.values()) {
            final CoilStores.Terms t = CoilLaw.terms(st, true, CoilLaw.DEFAULT_BASE);
            final int arc = Math.min(t.inCap(), CoilLaw.volleyRate(st.zapCost, st.targets()));
            arcRates[st.ordinal()] = arc;
            final CoilStores full = stores(t.capacitorSize(), 0);
            assertEquals(0, full.ratedDraw(t, false), st + ": ready and idle holds no budget");
            assertEquals(arc, full.ratedDraw(t, true), st + ": a fight still going, full or not");
            assertEquals(arc, stores(t.capacitorSize() - 1, 0).ratedDraw(t, false), st + ": recharging");
            assertEquals(arc, stores(0, 0).ratedDraw(t, false),
                    st + ": empty costs what nearly full costs, so a shed coil never looks dearer the longer it waits");
            assertTrue(arc <= t.inCap(), st + ": never more than IN may take");
        }
        assertEquals(List.of(1000, 4000, 12000, 32000), Arrays.stream(arcRates).boxed().toList());
    }

    @Test
    @DisplayName("nothing while lightning pays the arcs, in Storm only, or above a smaller stage's capacitor")
    void whenItCostsNothing() {
        final CoilStores.Terms t = CoilLaw.terms(CoilStage.FOUR, true, CoilLaw.DEFAULT_BASE);
        assertEquals(0, stores(0, t.zapCost()).ratedDraw(t, true), "the storm bank pays the next arc whole");
        assertEquals(32000, stores(0, t.zapCost() - 1).ratedDraw(t, true), "one FE short of an arc, IN pays");
        final CoilStores.Terms stormOnly = CoilLaw.terms(CoilStage.FOUR, false, CoilLaw.DEFAULT_BASE);
        assertEquals(0, stores(0, 0).ratedDraw(stormOnly, true), "Storm only takes nothing from IN");
        final CoilStores.Terms one = CoilLaw.terms(CoilStage.ONE, true, CoilLaw.DEFAULT_BASE);
        assertEquals(0, stores(CoilStage.FOUR.capacitorSize(), 0).ratedDraw(one, false),
                "lowered from stage 4, a capacitor above the new size is ready, not recharging");
        final CoilStores.Terms thin = CoilLaw.terms(CoilStage.FOUR, true, 1024);
        assertEquals(8192, stores(0, 0).ratedDraw(thin, false), "a pack's thinner base holds the price to IN's rate");
    }

    @Test
    @DisplayName("a fight lasts ten seconds past its last arc, and no arc yet is never a fight")
    void engaged() {
        for (long now : new long[] {0L, 1_000_000_000_000L, Long.MAX_VALUE}) {
            assertFalse(CoilLaw.engaged(now, Long.MIN_VALUE), "now - Long.MIN_VALUE wraps; it must not read as recent");
        }
        assertTrue(CoilLaw.engaged(1000, 1000));
        assertTrue(CoilLaw.engaged(1000, 801));
        assertFalse(CoilLaw.engaged(1000, 800), CoilLaw.ENGAGED_TICKS + " ticks on, the fight is over");
        assertFalse(CoilLaw.engaged(1000, 1001), "an arc in the future is not one");
    }

    @Test
    @DisplayName("the board's 1.15.13 price for a fighting coil was the arc; held to its node it is IN's rate")
    void theBoardPricedTheArc() {
        final CoilStores.Terms t = CoilLaw.terms(CoilStage.FOUR, true, CoilLaw.DEFAULT_BASE);
        final CoilStores s = new CoilStores();
        final PduLoadModel model = new PduLoadModel();
        final long key = 42L;
        long lastZap = Long.MIN_VALUE;
        int arcs = 0;
        int maxPrice = 0;
        for (long now = 0; now < 400; now++) {
            model.sample(key, s.capacitor(), t.capacitorSize(), now);
            model.delivered(key, s.receiveIn(t.inCap(), false, now, t));

            if (now % CoilLaw.ZAP_INTERVAL == 0 && s.payZap(t.zapCost())) {
                lastZap = now;
                arcs++;
            }
            maxPrice = Math.max(maxPrice, s.ratedDraw(t, CoilLaw.engaged(now, lastZap)));
        }
        assertTrue(arcs >= 18, "the bench must actually fight: " + arcs + " arcs");
        final int learned = model.steady(key);
        assertTrue(learned >= 100_000, "1.15.13's price, kept visible: an arc read as one tick's consumption, " + learned);
        assertEquals(t.inCap(), FeDeviceDemand.learned(learned, t.inCap(), s.capacitor(), t.capacitorSize(), 40).steady(),
                "any device's learned price stops at what its node carries in a tick");
        assertEquals(32000, maxPrice, "the price the coil states now: a full volley a second, under IN's 32,768");
    }

    @Test
    @DisplayName("alone on the owner's Shielded stud: the old price is refused for wire, the station is fed")
    void theOwnersStud() {
        final PduLegPlan.Leg[] legs = legs(32_768);

        final PduLegPlan.Screened old = PduLegPlan.screen(
                List.of(new PduLegPlan.Device(1L, STUD_7, NORMAL, 52_847, FeLock.FORCE_ON)), legs);
        assertTrue(old.isAtCapacity(STUD_7), "the old shape: refused for wire, and sixty ticks of that is a trip");
        final PduLegPlan.Screened now = PduLegPlan.screen(
                List.of(PduLegPlan.Device.station(1L, STUD_7, NORMAL, 32000, FeLock.FORCE_ON, new int[0])), legs);
        assertFalse(now.isAtCapacity(STUD_7));
        assertEquals(32000, now.stations().get(1L).fed(), "a stage 4 coil's full volley fits the owner's Shielded stud");
        assertEquals(1, now.survivors().size());
    }

    @Test
    @DisplayName("over a Copper stud two coils share what it carries, and the leg's own machine keeps its wire")
    void aStationNeverTrips() {
        final PduLegPlan.Leg[] legs = legs(8192);

        final PduLegPlan.Screened old = PduLegPlan.screen(List.of(
                new PduLegPlan.Device(1L, STUD_7, NORMAL, 32000, FeLock.NONE),
                new PduLegPlan.Device(2L, STUD_7, NORMAL, 1000, FeLock.NONE)), legs);
        assertTrue(old.isAtCapacity(STUD_7));
        final PduLegPlan.Screened both = PduLegPlan.screen(List.of(
                PduLegPlan.Device.station(1L, STUD_7, NORMAL, 32000, FeLock.NONE, new int[0]),
                PduLegPlan.Device.station(2L, STUD_7, NORMAL, 1000, FeLock.NONE, new int[0])), legs);
        assertFalse(both.isAtCapacity(STUD_7), "a station is never the cause of a trip (INV-31)");
        assertEquals(8192, both.stations().get(1L).fed() + both.stations().get(2L).fed(), "the stud's whole rating");
        assertEquals(1000, both.stations().get(2L).fed(), "the cheaper coil is fed whole first");
        final PduLegPlan.Screened mixed = PduLegPlan.screen(List.of(
                new PduLegPlan.Device(3L, STUD_7, NORMAL, 7000, FeLock.NONE),
                PduLegPlan.Device.station(1L, STUD_7, NORMAL, 32000, FeLock.NONE, new int[0])), legs);
        assertFalse(mixed.isAtCapacity(STUD_7));
        assertTrue(mixed.survivors().stream().anyMatch(d -> d.key() == 3L && d.demand() == 7000),
                "the leg's own machine runs at its whole cost");
        assertEquals(1192, mixed.stations().get(1L).fed(), "the coil gets what that machine leaves");
    }

    private static CoilStores stores(int capacitor, int bank) {
        final CoilStores s = new CoilStores();
        s.restore(capacitor, bank);
        return s;
    }

    private static PduLegPlan.Leg[] legs(int ceiling) {
        final PduLegPlan.Leg[] legs = new PduLegPlan.Leg[8];
        Arrays.fill(legs, PduLegPlan.Leg.open(ceiling));
        return legs;
    }
}
