package com.createstats.book.specimen;

import com.createstats.book.content.Configs;
import com.createstats.gui.battery.BatteryCanvas;
import com.createstats.gui.battery.BatteryHistory;
import com.createstats.gui.battery.BatteryPages;
import com.createstats.gui.battery.BatteryView;
import com.createstats.gui.pdu.PduRect;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.BiofuelValue;
import com.createstats.power.BurnerFuelOutlook;
import com.createstats.power.CapacitorDischarge;
import com.createstats.power.DynamoTier;
import com.createstats.power.FeBankMerge;
import com.createstats.power.FeBoardDeal;
import com.createstats.power.FeChargePolicy;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveBand;
import com.createstats.power.FeReserveReport;
import com.createstats.power.ReserveProjection;
import com.createstats.power.SolarCurve;
import com.createstats.power.SolarTier;
import com.createstats.power.SupplyForecast;

import java.util.ArrayList;
import java.util.List;

public final class BatteryPlates {
    private BatteryPlates() {
    }

    public static final String NOW = "battery_controller.now";

    public static final String BAND = "battery_controller.band";
    public static final String FORECAST = "battery_controller.forecast";
    public static final String RULES = "battery_controller.rules";

    public static final List<String> IDS = List.of(NOW, BAND, FORECAST, RULES);

    public static final int W = 462;
    public static final int H = 312;

    public static final int SELECTED = 1;

    public static final int RANGE = BatteryHistory.RANGE_5M;

    public static boolean isPlate(String id) {
        return IDS.contains(id);
    }

    public static BatteryPages.Tab tabOf(String id) {
        return FORECAST.equals(id) ? BatteryPages.Tab.FORECAST : RULES.equals(id) ? BatteryPages.Tab.RULES
                : BatteryPages.Tab.NOW;
    }

    public static int selected(String id) {
        return NOW.equals(id) ? SELECTED : -1;
    }

    public static int portRate() {
        return Configs.batteryPortRate();
    }

    public static List<String> tabLabels() {
        final List<String> out = new ArrayList<>();
        for (final BatteryPages.Tab t : BatteryPages.Tab.values()) {
            out.add(t.label);
        }
        return out;
    }

    public static BatteryPages.State state(String id, int mouseX, int mouseY, List<BatteryPages.Hit> hits,
                                           List<BatteryPages.Region> regions) {
        return new BatteryPages.State(tabOf(id), selected(id), mouseX, mouseY, history(), RANGE, portRate(), hits,
                regions);
    }

    public static void draw(BatteryCanvas c, String id, int mouseX, int mouseY) {
        BatteryPages.draw(c, new PduRect(0, 0, W, H), view(), state(id, mouseX, mouseY, new ArrayList<>(), null));
    }

    public static synchronized SpecimenAnatomy anatomy(String id) {
        return ANATOMY.computeIfAbsent(id, BatteryPlates::measure);
    }

    private static final java.util.Map<String, SpecimenAnatomy> ANATOMY = new java.util.HashMap<>();

    private static SpecimenAnatomy measure(String id) {
        final List<BatteryPages.Hit> hits = new ArrayList<>();
        final List<BatteryPages.Region> regions = new ArrayList<>();
        BatteryPages.draw(MEASURE, new PduRect(0, 0, W, H), view(), state(id, -10_000, -10_000, hits, regions));
        final List<SpecimenAnatomy.Hot> out = new ArrayList<>();
        final BatteryPages.Tab tab = tabOf(id);

        out.add(hot("tab", hits(hits, BatteryPages.A_TAB, tab.ordinal()), 0.5));
        if (BAND.equals(id)) {
            out.add(hot("ruler", region(regions, "ruler"), 0.5));
            out.add(hot("handles", hits(hits, BatteryPages.A_BAND_HANDLE, -1), 0.5));
            out.add(hot("pins", hits(hits, BatteryPages.A_FLOOR_PIN, -1), 0.5));
            out.add(hot("unit", hits(hits, com.createstats.network.BatteryControllerSetPacket.ACTION_BAND_UNIT, -1),
                    0.5));
            out.add(hot("edges", hits(hits, BatteryPages.A_BAND_TRACK, -1), 0.3));
            out.add(hot("caps", hits(hits, BatteryPages.A_CAP_TRACK, -1), 0.6));
            out.add(hot("footer", region(regions, "footer"), 0.85));
            return new SpecimenAnatomy(id, W, H, out);
        }
        switch (tab) {
            case NOW -> {
                out.add(hot("stance", region(regions, "stance"), 0.5));
                out.add(hot("headline", region(regions, "headline"), 0.15));
                out.add(hot("balance", region(regions, "balance"), 0.15));
                out.add(hot("inputs", region(regions, "input.0"), 0.25));
                out.add(hot("switch", hits(hits, com.createstats.network.BatteryControllerSetPacket.ACTION_INPUT_ENABLED,
                        0), 0.5));
                out.add(hot("band", region(regions, "ruler"), 0.5));
                out.add(hot("outputs", hits(hits, BatteryPages.A_SELECT, 0), 0.75));
                out.add(hot("breaker", hits(hits,
                        com.createstats.network.BatteryControllerSetPacket.ACTION_OUTPUT_ENABLED, SELECTED), 0.5));
                out.add(hot("mode", hits(hits, com.createstats.network.BatteryControllerSetPacket.ACTION_OUTPUT_MODE,
                        SELECTED), 0.5));
                out.add(hot("tier", hits(hits,
                        com.createstats.network.BatteryControllerSetPacket.ACTION_OUTPUT_PRIORITY, SELECTED), 0.5));
                out.add(hot("floor", hits(hits, BatteryPages.A_FLOOR_TRACK, SELECTED), 0.3));
                out.add(hot("limit", hits(hits, BatteryPages.A_LIMIT_TRACK, SELECTED), 0.7));
                out.add(hot("footer", region(regions, "footer"), 0.85));
            }
            case FORECAST -> {
                out.add(hot("plot", region(regions, "plot"), 0.15));
                out.add(hot("now", region(regions, "now"), 0.5));
                out.add(hot("marker", region(regions, "marker"), 0.5));
                out.add(hot("strip", region(regions, "strip"), 0.85));
                out.add(hot("range", hits(hits, BatteryPages.A_RANGE, -1), 0.5));
                out.add(hot("next", region(regions, "next"), 0.15));
                out.add(hot("assumes", region(regions, "assumes"), 0.85));
                out.add(hot("footer", region(regions, "footer"), 0.85));
            }
            case RULES -> {
                out.add(hot("step1", region(regions, "step1"), 0.05));
                out.add(hot("bandbar", region(regions, "bandbar"), 0.5));
                out.add(hot("step2", region(regions, "step2"), 0.05));
                out.add(hot("step3", region(regions, "step3"), 0.05));
                out.add(hot("step4", region(regions, "step4"), 0.05));
                out.add(hot("step5", region(regions, "step5"), 0.05));
                out.add(hot("footer", region(regions, "footer"), 0.85));
            }
        }
        return new SpecimenAnatomy(id, W, H, out);
    }

    private static List<PduRect> hits(List<BatteryPages.Hit> hits, int action, int index) {
        final List<PduRect> out = new ArrayList<>();
        for (final BatteryPages.Hit h : hits) {
            if (h.action() == action && (index < 0 || h.index() == index)) {
                out.add(h.r());
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the Battery Controller's page drew no control for action " + action);
        }
        return out;
    }

    private static List<PduRect> region(List<BatteryPages.Region> regions, String id) {
        for (final BatteryPages.Region r : regions) {
            if (r.id().equals(id)) {
                return List.of(r.r());
            }
        }
        throw new IllegalStateException("the Battery Controller's page noted no region " + id);
    }

    private static SpecimenAnatomy.Hot hot(String id, List<PduRect> rects, double across) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (final PduRect r : rects) {
            x0 = Math.min(x0, r.x());
            y0 = Math.min(y0, r.y());
            x1 = Math.max(x1, r.right());
            y1 = Math.max(y1, r.bottom());
        }
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(W, x1);
        y1 = Math.min(H, y1);
        final int ax = x0 + (int) Math.round((x1 - x0 - 1) * across);
        return new SpecimenAnatomy.Hot(id, x0, y0, x1 - x0, y1 - y0, ax, y0 + (y1 - y0) / 2, false);
    }

    private static final BatteryCanvas MEASURE = new BatteryCanvas() {
        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
        }

        @Override
        public void text(String s, int x, int y, int argb) {
        }

        @Override
        public int width(String s) {
            return VanillaFont.width(s);
        }

        @Override
        public void bold(String s, int x, int y, int argb) {
        }

        @Override
        public int boldWidth(String s) {
            return VanillaFont.boldWidth(s);
        }
    };

    static final int BANKS = 2;
    static final int BANK_BLOCKS = 27;

    static final int CHARGE_PCT = 66;

    static final int ROOF = 27;

    static final long DAY_TIME = SolarCurve.DAYLIGHT_TICKS - 4_800;

    static final int BURNER_TICKS = 9_600;

    static final int MACHINES = 900;
    static final int LIGHTS = 2_600;
    static final int BOARD = 1_400;

    static final int BOARD_CRITICAL = 400;

    static final int LIGHTS_LIMIT = 2_048;
    static final int LIGHTS_FLOOR = 40;

    static final int WIRE = 16_384;

    static final int[] DEVICES = {14, 6, 1};

    private static BatteryView view;
    private static BatteryHistory history;

    public static synchronized BatteryView view() {
        if (view == null) {
            view = example();
        }
        return view;
    }

    public static synchronized BatteryHistory history() {
        if (history == null) {
            final BatteryView v = view();
            final BatteryHistory h = new BatteryHistory();
            final int samples = BatteryHistory.SIZE;
            final double perSample = BatteryHistory.CADENCE[RANGE] * (double) Math.max(0, v.bankDrawn() - v.bankCharged())
                    / Math.max(1, v.capacity());
            for (int i = 0; i < samples; i++) {
                final int back = samples - 1 - i;
                final int soc = (int) Math.round(100.0 * ((double) v.stored() / Math.max(1, v.capacity()) + back * perSample));

                final int in = solar(-back * BatteryHistory.CADENCE[RANGE]) + steadyAndBurner();
                h.record(Math.min(100, soc), in, v.delivered());
            }
            history = h;
        }
        return history;
    }

    static int solar(int ahead) {
        final long t = Math.max(0L, DAY_TIME + ahead);
        return SolarCurve.fePerTick(SolarTier.REINFORCED.peakFePerTick(), ROOF, t, SolarCurve.Sky.CLEAR,
                SolarCurve.FULL_SKY_LIGHT) * ROOF;
    }

    static int steadyAndBurner() {
        return dynamo() + BiofuelValue.OUTPUT_FE_PER_TICK;
    }

    static int dynamo() {
        return (int) Math.round(DynamoTier.STANDARD.fePerRpm() * com.createstats.block.KineticDynamoBlock.TOP_RPM);
    }

    private static BatteryView example() {
        final int capacity = FeBankMerge.capacity(BANK_BLOCKS, Configs.bankBlockCapacity(), Configs.bankMergeExponent())
                * BANKS;
        final int stored = (int) ((long) capacity * CHARGE_PCT / 100L);
        final int solarNow = solar(0);
        final int generation = solarNow + steadyAndBurner();
        final int port = portRate();
        final int ride = Configs.batteryRideTicks();
        final int claimPct = Configs.batteryClaimPct();
        final int discharge = port * BatteryControllerPorts.OUTPUTS;
        final int charge = port * BatteryControllerPorts.INPUTS;

        final List<BatteryControllerPlan.Output> plan = List.of(
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, MACHINES, WIRE, 0, 0,
                        FePriority.NORMAL, false),
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.LIMIT, LIGHTS, WIRE, LIGHTS_LIMIT,
                        LIGHTS_FLOOR, FePriority.LOW, false),
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, BOARD, WIRE, 0, 0,
                        FePriority.NORMAL, false, true, BOARD_CRITICAL));

        final int criticalWant = BatteryControllerPlan.criticalWant(plan);
        final FeReserveBand.Band band = FeReserveBand.sized(Configs.batteryBand(), Configs.batteryMinutes(), capacity,
                criticalWant, discharge, ride);
        final int soc = (int) ((long) stored * 100L / capacity);
        final FeReserveReport report = FeReserveBand.assess(0L, new FeReserveBand.Params(stored, capacity, generation,
                0, Math.min(discharge, stored), Math.max(1, BatteryControllerPlan.wiredCeiling(plan)), band, ride, null,
                criticalWant));
        final int fromReserve = Math.min(Math.max(report.sustainedDraw(), report.criticalDraw()), stored);
        int energy = generation;
        final int claimable = FeChargePolicy.claimable(energy, fromReserve, criticalWant);
        final int claim = Math.min(claimable, FeChargePolicy.claim(generation, soc, capacity, band, claimPct,
                Configs.bankTransferRate() * BANKS));
        final BatteryControllerPlan.Plan decided = BatteryControllerPlan.plan(plan, generation,
                Math.max(0, energy + fromReserve - claim), soc, claim);

        int drawn = 0;
        int delivered = 0;
        for (final BatteryControllerPlan.Grant g : decided.grants()) {
            if (g.granted() <= 0) {
                continue;
            }
            if (energy < g.granted()) {
                drawn += g.granted() - energy;
                energy = g.granted();
            }
            energy -= g.granted();
            delivered += g.granted();
        }
        final int charged = Math.min(Math.max(0, energy), charge);

        final List<BatteryView.Input> inputs = List.of(
                new BatteryView.Input(0, true, 0, BANKS, 0, 0, 0, false, Math.max(drawn, charged)),
                new BatteryView.Input(1, true, solarNow, 0, ROOF, 0, 0, false, 0),
                new BatteryView.Input(2, true, steadyAndBurner(), 0, 2, 0, 0, false, 0));
        final List<BatteryView.Output> outputs = new ArrayList<>(3);
        for (int i = 0; i < plan.size(); i++) {
            final BatteryControllerPlan.Output o = plan.get(i);
            final BatteryControllerPlan.Grant g = decided.grants().get(i);
            final boolean board = o.managed();
            outputs.add(new BatteryView.Output(i, true, true, o.mode(), o.limit(), o.floorPct(), o.priority(),
                    g.reason(), g.granted(), o.demand(), o.ceiling(), DEVICES[i], board ? 1 : 0, o.criticalDemand(),
                    board ? o.demand() : 0, board ? g.granted() : 0,
                    board && g.granted() < o.demand() ? FeBoardDeal.Reason.SHARED : FeBoardDeal.Reason.GRANTED,
                    i == 0 ? 1 : 0, 0));
        }

        final SupplyForecast.Result supply = new SupplyForecast.Builder(BatteryControllerPorts.INPUTS,
                ReserveProjection.HORIZON_TICKS)
                .port(1, solarNow, WIRE)
                .add(1, SupplyForecast.Kind.SUN, BatteryPlates::solar)
                .port(2, steadyAndBurner(), WIRE)
                .add(2, SupplyForecast.Kind.FUEL, ahead -> BurnerFuelOutlook.outputIn(BiofuelValue.OUTPUT_FE_PER_TICK,
                        BURNER_TICKS, false, ahead))
                .add(2, SupplyForecast.Kind.STEADY, ahead -> dynamo())
                .build();
        final ReserveProjection.Result outlook = ReserveProjection.project(new ReserveProjection.Setup(stored, capacity,
                generation, plan, band, ride, discharge, charge, claimPct, port, true, 0, 0, supply));

        return new BatteryView(stored, capacity, generation, delivered, drawn, charged, charged - drawn, claim,
                report.stance(), report.floorPct(), report.ceilingPct(), band.healthyPct(), band.floorPct(), 0, 0,
                report.sustainedDraw(), report.criticalDraw(), claimPct, 1, CapacitorDischarge.CAPACITY,
                CapacitorDischarge.CAPACITY, 0, 0, 0, false, inputs, outputs, outlook, criticalWant);
    }
}
