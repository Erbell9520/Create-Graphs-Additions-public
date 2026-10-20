package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyForecastTest {
    private static final int CAP = 20_000_000;
    private static final int RATE = 65_536;
    private static final int HORIZON = ReserveProjection.HORIZON_TICKS;

    private static java.util.function.IntUnaryOperator roof(int peak, long dayTime) {
        return ahead -> SolarCurve.fePerTickIn(peak, 1, dayTime, ahead, SolarCurve.Sky.CLEAR, 1.0, true);
    }

    private static BatteryControllerPlan.Output load(int demand) {
        return new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, demand, 32_768, 0, 0,
                FePriority.NORMAL, false);
    }

    private static BatteryControllerPlan.Output unwired() {
        return new BatteryControllerPlan.Output(false, true, BatteryOutputMode.DIRECT, 0, 0, 0, 0,
                FePriority.NORMAL, false);
    }

    private static ReserveProjection.Setup setup(int stored, int generation, int demand, SupplyForecast.Result supply) {
        return new ReserveProjection.Setup(stored, CAP, generation, List.of(load(demand), unwired(), unwired()),
                FeReserveBand.Band.DEFAULT, FeReserveBand.DEFAULT_RIDE_TICKS, RATE, RATE,
                FeChargePolicy.DEFAULT_MAX_CLAIM_PCT, 32_768, true, 0, 0, supply);
    }

    @Test
    @DisplayName("an input carries what it carries now, moved by its sources: headroom first, a rise up to the wire")
    void deliverable() {
        assertEquals(1_000, SupplyForecast.deliverable(1_000, 8_192, 1_000, 1_000), "nothing changes");
        assertEquals(100, SupplyForecast.deliverable(100, 8_192, 5_000, 2_000),
                "a roof making 5k into an input taking 100 has 4.9k to lose before the input notices");
        assertEquals(0, SupplyForecast.deliverable(100, 8_192, 5_000, 0), "the whole roof going takes the input");
        assertEquals(700, SupplyForecast.deliverable(1_000, 8_192, 1_000, 700));
        assertEquals(3_000, SupplyForecast.deliverable(1_000, 3_000, 0, 5_000), "a sunrise is held to the wire");
        assertEquals(600, SupplyForecast.deliverable(600, 8_192, 0, 0), "no forecasting sources: flat");
    }

    @Test
    @DisplayName("INV-22: an input of sources that cannot predict forecasts exactly what it carries, every sample")
    void aFlatFeedChangesNothing() {
        final SupplyForecast.Result r = new SupplyForecast.Builder(3, HORIZON)
                .port(0, 540, 8_192).add(0, SupplyForecast.Kind.STEADY, ahead -> 540)
                .port(1, 1_200, 8_192)
                .build();
        for (int f : r.total()) {
            assertEquals(1_740, f);
        }
        assertTrue(r.changes().isEmpty());

        final ReserveProjection.Result withSupply = ReserveProjection.project(setup(CAP / 2, 1_740, 3_000, r));
        final ReserveProjection.Result without = ReserveProjection.project(setup(CAP / 2, 1_740, 3_000, null));
        assertEquals(without.milestones(), withSupply.milestones());
        assertEquals(java.util.Arrays.toString(without.trace()), java.util.Arrays.toString(withSupply.trace()));
    }

    @Test
    @DisplayName("KI-99: a roof seen at late afternoon sets, and rises again before the half hour is out")
    void theSunSetsAndRises() {
        final int now = roof(4_000, 10_000).applyAsInt(0);
        final SupplyForecast.Result r = new SupplyForecast.Builder(3, HORIZON)
                .port(0, now, 16_384).add(0, SupplyForecast.Kind.SUN, roof(4_000, 10_000))
                .build();
        final SupplyForecast.Change down = r.first(SupplyForecast.What.SUN_DOWN);
        final SupplyForecast.Change up = r.first(SupplyForecast.What.SUN_UP);
        assertNotNull(down);
        assertNotNull(up);
        assertTrue(down.tick() > 1_700 && down.tick() <= 2_100, "sunset at " + down.tick());
        assertTrue(up.tick() > 13_900 && up.tick() <= 14_400, "sunrise at " + up.tick());
        assertTrue(up.fePerTick() > 3_000, "the next day's peak is inside the horizon: " + up.fePerTick());
        assertEquals(0, r.at(8_000), "night");
        assertTrue(r.at(20_000) > r.at(15_000), "the morning climbs");
        assertEquals(now, r.at(0), "now is what the input carries now");
    }

    @Test
    @DisplayName("KI-99: at night the forecast names sunrise and the banks turn back up; the old one drained to the end")
    void aNightThatEnds() {
        final SupplyForecast.Result supply = new SupplyForecast.Builder(3, HORIZON)
                .port(0, 0, 16_384).add(0, SupplyForecast.Kind.SUN, roof(4_000, 18_000))
                .build();
        final ReserveProjection.Result r = ReserveProjection.project(setup(CAP * 3 / 4, 0, 1_500, supply));
        final ReserveProjection.Milestone sunrise = r.first(ReserveProjection.Kind.SUN_UP);
        assertNotNull(sunrise, "sunrise is named");
        assertTrue(sunrise.tick() >= 6_000 && sunrise.tick() <= 6_300, "at " + sunrise.tick());
        final byte[] trace = r.trace();
        int low = 0;
        for (int i = 1; i < trace.length; i++) {
            if (trace[i] < trace[low]) {
                low = i;
            }
        }
        assertTrue(trace[trace.length - 1] > trace[low], "the banks refill after sunrise");
        assertTrue(low * ReserveProjection.SAMPLE_TICKS >= 6_000, "and bottom out after it, not before");

        final ReserveProjection.Result flat = ReserveProjection.project(setup(CAP * 3 / 4, 0, 1_500, null));
        assertNull(flat.first(ReserveProjection.Kind.SUN_UP));
        final byte[] old = flat.trace();
        assertTrue(old[old.length - 1] < trace[trace.length - 1], "the flat forecast never saw the morning");
    }

    @Test
    @DisplayName("KI-100: a hand-fed burner runs dry when its fuel does, and the forecast says so")
    void aBurnerRunsDry() {
        final SupplyForecast.Result r = new SupplyForecast.Builder(3, HORIZON)
                .port(1, 360, 8_192)
                .add(1, SupplyForecast.Kind.FUEL, ahead -> BurnerFuelOutlook.outputIn(180, 7_200, false, ahead))
                .add(1, SupplyForecast.Kind.FUEL, ahead -> BurnerFuelOutlook.outputIn(180, 7_800, false, ahead))
                .build();
        final List<SupplyForecast.Change> out = r.changes();
        assertEquals(1, out.size(), "two burners out within a minute are one event: " + out);
        assertEquals(SupplyForecast.What.FUEL_OUT, out.get(0).what());
        assertEquals(360, out.get(0).fePerTick());
        assertEquals(7_200, out.get(0).tick());
        assertEquals(0, r.at(9_000));

        final ReserveProjection.Result p = ReserveProjection.project(setup(CAP / 2, 360, 360, r));
        final ReserveProjection.Milestone dry = p.first(ReserveProjection.Kind.FUEL_OUT);
        assertNotNull(dry);
        assertEquals(360, dry.value());
        final byte[] trace = p.trace();
        assertTrue(trace[trace.length - 1] < trace[0], "the banks carry the load once the fire is out");
        final ReserveProjection.Result flat = ReserveProjection.project(setup(CAP / 2, 360, 360, null));
        assertEquals(flat.trace()[0], flat.trace()[flat.trace().length - 1], "the old forecast burned for ever");
    }

    @Test
    @DisplayName("KI-100: a burner a hopper keeps fed forecasts flat, fuel or not")
    void aFedBurnerIsFlat() {
        final SupplyForecast.Result r = new SupplyForecast.Builder(3, HORIZON)
                .port(0, 180, 8_192)
                .add(0, SupplyForecast.Kind.FUEL, ahead -> BurnerFuelOutlook.outputIn(180, 300, true, ahead))
                .build();
        assertTrue(r.changes().isEmpty());
        assertEquals(180, r.at(HORIZON));
    }

    @Test
    @DisplayName("the supply the projection used is what it reports, one value per trace sample")
    void supplyTraceMatches() {
        final int now = roof(4_000, 10_000).applyAsInt(0);
        final SupplyForecast.Result supply = new SupplyForecast.Builder(3, HORIZON)
                .port(0, now, 16_384).add(0, SupplyForecast.Kind.SUN, roof(4_000, 10_000))
                .build();
        final ReserveProjection.Result r = ReserveProjection.project(setup(CAP / 2, now, 1_000, supply));
        assertEquals(r.trace().length, r.supply().length);
        assertEquals(now, r.supply()[0]);
        assertEquals(0, r.supply()[20], "ten minutes on it is night");
        final ReserveProjection.Result back = ReserveProjection.Result.unpack(r.trace(), r.packMilestones(),
                r.shortNow(), r.supply());
        assertEquals(r.milestones(), back.milestones(), "the new kinds survive the wire");
    }
}
