package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveReport;
import com.createstats.power.PduMode;
import com.createstats.util.CreateStatsPowerMetrics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// not a list. "contains Alternator" was the old classifier. it was bad
@OnlyIn(Dist.CLIENT)
public final class PowerGraph {
    public static final String[] FAMILIES = {
            "energy.accumulator", "energy.capacitor_bank", "energy.alternator",
            "energy.motor", "energy.connector",
            "energy.dynamo", "energy.grid", "energy.pdu", "energy.battery_controller"
    };

    private static final float STORAGE_FLOOR = 1000.0f;

    private static final int MAX_SOURCES = 6;

    private static final int MAX_LOADS = 7;

    private PowerGraph() {
    }

    public enum Role {
        GENERATOR,

        STORAGE,

        DISTRIBUTOR,

        LOAD,

        GRID
    }

    public record Circuit(String name, float demand, float served, float ceiling,
                          State state, int devices, int floorPct) {
        public enum State {
            OK("Served"),

            IDLE("Idle"),

            RATIONED("Rationed"),

            PARTIAL("Partial"),

            FLOOR("Below floor"),

            RECHARGING("Recharging reserve"),

            SHED("Shed"),

            OVER_CAPACITY("Over capacity"),

            OPEN("Breaker off"),

            TRIPPED("Tripped");

            private final String label;

            State(String label) {
                this.label = label;
            }

            public String label() {
                return this.label;
            }

            public boolean isFault() {
                return this != OK && this != IDLE;
            }
        }

        public float headroom() {
            return this.ceiling <= 0.0f ? 0.0f : Math.max(0.0f, this.ceiling - this.served);
        }

        public float loadFraction() {
            return this.ceiling <= 0.0f ? 0.0f
                    : Math.max(0.0f, Math.min(1.0f, this.served / this.ceiling));
        }
    }

    public record Distributor(String label, boolean panel,
                              float input, float output, float demand, float served,
                              float generation, float budget, float ceiling,
                              boolean throughputLimited,
                              int devices, int servedDevices, int shedDevices,
                              String policy, float stored, float capacity,
                              int[] tierTotal, int[] tierServed,
                              List<Circuit> circuits) {
        public float shortfall() {
            return Math.max(0.0f, this.demand - this.served);
        }

        public boolean isShedding() {
            return this.shedDevices > 0 || shortfall() > 1.0f;
        }

        public boolean hasFault() {
            for (final Circuit c : this.circuits) {
                if (c.state().isFault()) {
                    return true;
                }
            }
            return this.throughputLimited;
        }

        public float fill() {
            return this.capacity > 0.0f
                    ? Math.max(0.0f, Math.min(1.0f, this.stored / this.capacity)) : 0.0f;
        }
    }

    public record Node(String label, Role role, float rate, int roll) {
        public Node(String label, Role role, float rate) {
            this(label, role, rate, 1);
        }
    }

    public record Grid(List<Node> sources, List<Node> storage, List<Node> loads,
                       List<Distributor> distributors,
                       float generated, float consumed,
                       float stored, float capacity, float measuredNet,
                       boolean hasStorage, float[] storedHistory,
                       float gridDelivered, float gridDemand, int gridNodes,
                       int cellColumns, int cellRows, float storageNet) {
        public float bandNet() {
            return this.storageNet;
        }

        public float fill() {
            return this.capacity > 0.0f
                    ? Math.max(0.0f, Math.min(1.0f, this.stored / this.capacity)) : 0.0f;
        }

        public float claimedNet() {
            return this.generated - this.consumed;
        }

        public float net() {
            return this.hasStorage ? this.measuredNet : claimedNet();
        }

        public boolean isEmpty() {
            return this.sources.isEmpty() && this.storage.isEmpty()
                    && this.loads.isEmpty() && this.distributors.isEmpty();
        }

        public boolean isManaged() {
            return !this.distributors.isEmpty();
        }

        public float throughDistributors() {
            float total = 0.0f;
            for (final Distributor d : this.distributors) {
                total += d.output();
            }
            return total;
        }

        public float direct() {
            return Math.max(0.0f, this.generated - throughDistributors());
        }

        public List<Circuit> allCircuits() {
            final List<Circuit> all = new ArrayList<>();
            for (final Distributor d : this.distributors) {
                all.addAll(d.circuits());
            }
            return all;
        }

        public String fault() {
            for (final Distributor d : this.distributors) {
                if (d.throughputLimited()) {
                    return d.label() + ": wiring is the limit, not generation";
                }
            }
            for (final Distributor d : this.distributors) {
                if (d.shedDevices() > 0) {
                    return d.label() + ": " + d.shedDevices()
                            + (d.shedDevices() == 1 ? " machine shed" : " machines shed");
                }
            }
            for (final Circuit c : allCircuits()) {
                if (c.state() == Circuit.State.TRIPPED || c.state() == Circuit.State.OVER_CAPACITY) {
                    return c.name() + ": " + c.state().label();
                }
            }
            if (this.hasStorage && this.measuredNet < -1.0f && this.capacity > 0.0f
                    && fill() < 0.15f) {
                return "Reserve nearly empty and still draining";
            }
            return null;
        }
    }

    public static final Grid EMPTY = new Grid(List.of(), List.of(), List.of(), List.of(),
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, new float[0], 0.0f, 0.0f, 0, 0, 0, 0.0f);

    public static Grid read(StatsDisplayPanelBE panel) {
        return of(SourceProbe.byFamily(panel, FAMILIES));
    }

    public static Grid of(List<SourceProbe.Unit> units) {
        if (units == null || units.isEmpty()) {
            return EMPTY;
        }

        final List<Node> sources = new ArrayList<>();
        final List<Node> loads = new ArrayList<>();
        final List<Node> storage = new ArrayList<>();
        final List<Distributor> distributors = new ArrayList<>();
        final List<SourceProbe.Unit> grids = new ArrayList<>();

        float generated = 0.0f;
        float consumed = 0.0f;
        float stored = 0.0f;
        float capacity = 0.0f;
        float measuredNet = 0.0f;
        boolean hasStorage = false;
        float storageNet = 0.0f;
        float[] history = new float[0];

        int cellColumns = 0;
        int cellRows = 0;

        for (final SourceProbe.Unit unit : units) {
            switch (roleOf(unit)) {
                case GENERATOR -> {
                    final float rate = unit.any(0.0f, "energy_output_rate", "energy_net_rate");
                    sources.add(new Node(unit.label(), Role.GENERATOR, Math.max(0.0f, rate)));
                    generated += Math.max(0.0f, rate);
                }
                case LOAD -> {
                    final float rate = unit.any(0.0f, "energy_input_rate", "energy_draw_rate");
                    loads.add(new Node(unit.label(), Role.LOAD, Math.max(0.0f, rate)));
                    consumed += Math.max(0.0f, rate);
                }
                case STORAGE -> {
                    storage.add(new Node(unit.label(), Role.STORAGE,
                            unit.value("energy_net_rate", 0.0f)));
                    stored += unit.value("energy_stored", 0.0f);
                    capacity += unit.value("energy_capacity", 0.0f);
                    measuredNet += unit.value("energy_net_rate", 0.0f);
                    storageNet += unit.value("energy_net_rate", 0.0f);
                    hasStorage = true;
                    history = longerOf(history, unit.store("energy_stored"));
                    if (cellColumns == 0) {
                        final int[] shape = cellShape(unit);
                        cellColumns = shape[0];
                        cellRows = shape[1];
                    }
                }
                case DISTRIBUTOR -> distributors.add(readDistributor(unit));
                case GRID -> grids.add(unit);
            }
        }

        for (final Distributor d : distributors) {
            if (!d.panel() && d.capacity() > 0.0f) {
                stored += d.stored();
                capacity += d.capacity();
                hasStorage = true;
            }
        }
        float controllerNet = 0.0f;
        for (final SourceProbe.Unit unit : units) {
            if (roleOf(unit) == Role.DISTRIBUTOR && !isPanel(unit)) {
                controllerNet += unit.value("energy_net_rate", 0.0f);
                history = longerOf(history, unit.store("energy_stored"));
            }
        }
        measuredNet += controllerNet;

        float gridDelivered = 0.0f;
        float gridDemand = 0.0f;
        int gridNodes = 0;
        for (final SourceProbe.Unit unit : grids) {
            gridDelivered = Math.max(gridDelivered,
                    unit.any(0.0f, "network_pushed", "energy_output_rate"));
            gridDemand = Math.max(gridDemand, unit.value("network_demand", 0.0f));
            gridNodes = Math.max(gridNodes, Math.round(unit.value("wire_nodes", 0.0f)));
        }
        if (!grids.isEmpty()) {
            final SourceProbe.Unit grid = grids.get(0);

            if (!hasStorage && grid.value("energy_capacity", 0.0f) >= STORAGE_FLOOR) {
                stored = grid.value("energy_stored", 0.0f);
                capacity = grid.value("energy_capacity", 0.0f);
                measuredNet = grid.value("energy_net_rate", 0.0f);
                hasStorage = true;
                storageNet = measuredNet;
                history = longerOf(history, grid.store("energy_stored"));
            }
            if (sources.isEmpty() && gridDelivered > 0.0f) {
                sources.add(new Node("Grid", Role.GRID, gridDelivered, Math.max(1, gridNodes)));
                generated += gridDelivered;
            }
            if (loads.isEmpty() && distributors.isEmpty() && gridDemand > 0.0f) {
                loads.add(new Node("Grid demand", Role.GRID, gridDemand, Math.max(1, gridNodes)));
                consumed += gridDemand;
            }
        }

        final List<Distributor> staged = chain(distributors, sources);
        boolean controllerChained = false;
        for (final Distributor d : distributors) {
            if (!staged.contains(d)) {
                generated += Math.max(0.0f, d.output());
                controllerChained = true;
            }
        }

        if (!controllerChained) {
            storageNet += controllerNet;
        }

        for (final Distributor d : staged) {
            if (sources.isEmpty() && d.generation() > 0.0f) {
                sources.add(new Node("Upstream supply", Role.GENERATOR, d.generation()));
                generated += d.generation();
            }
        }

        if (!staged.isEmpty()) {
            consumed = 0.0f;
            for (final Distributor d : staged) {
                consumed += d.served();
            }
        }

        return new Grid(rollUp(sources, MAX_SOURCES, "generators"),
                storage,
                rollUp(loads, MAX_LOADS, "loads"),
                staged,
                generated, consumed, stored, capacity, measuredNet, hasStorage, history,
                gridDelivered, gridDemand, gridNodes, cellColumns, cellRows, storageNet);
    }

    private static int[] cellShape(SourceProbe.Unit unit) {
        final int width = Math.round(unit.value("accumulator_width", 0.0f));
        final int height = Math.round(unit.value("accumulator_height", 0.0f));
        if (width > 0 && height > 0) {
            return new int[] { Math.min(6, width), Math.min(10, height) };
        }
        final int blocks = Math.round(unit.value("bank_blocks", 0.0f));
        final int layers = Math.round(unit.value("bank_layers", 0.0f));
        if (blocks > 0 && layers > 0) {
            return new int[] { Math.max(1, Math.min(6, (blocks + layers - 1) / layers)),
                    Math.min(10, layers) };
        }
        return new int[] { 0, 0 };
    }

    // family first, it's a registry fact
    public static Role roleOf(SourceProbe.Unit unit) {
        switch (familyOf(unit)) {
            case "energy.pdu", "energy.battery_controller":
                return Role.DISTRIBUTOR;
            case "energy.grid":
                return Role.GRID;
            case "energy.dynamo", "energy.alternator":
                return Role.GENERATOR;
            case "energy.accumulator", "energy.capacitor_bank":
                return Role.STORAGE;
            case "energy.motor":
                return Role.LOAD;
            default:
                break;
        }

        final boolean gives = unit.has("energy_can_extract") ? unit.flag("energy_can_extract")
                : unit.value("energy_output_rate", 0.0f) > 0.01f;
        final boolean takes = unit.has("energy_can_receive") ? unit.flag("energy_can_receive")
                : unit.value("energy_input_rate", 0.0f) > 0.01f;
        final boolean buffered = unit.value("energy_capacity", 0.0f) >= STORAGE_FLOOR;
        if (gives && takes) {
            return buffered ? Role.STORAGE : Role.GRID;
        }
        if (gives) {
            return Role.GENERATOR;
        }

        return Role.LOAD;
    }

    private static String familyOf(SourceProbe.Unit unit) {
        final SourceFamilyDefinition family =
                CustomScreenSourceFamilyCatalog.familyForSource(unit.config());
        return family == null ? "" : family.getFamilyId();
    }

    private static boolean isPanel(SourceProbe.Unit unit) {
        return "energy.pdu".equals(familyOf(unit));
    }

    private static Distributor readDistributor(SourceProbe.Unit unit) {
        return isPanel(unit) ? readPdu(unit) : readController(unit);
    }

    private static Distributor readPdu(SourceProbe.Unit unit) {
        final List<Circuit> circuits = new ArrayList<>();
        final int mask = Math.round(unit.value("pdu_legs_wired", 0.0f));
        for (int leg = 0; leg < 32; leg++) {
            if ((mask & (1 << leg)) == 0) {
                continue;
            }
            final String prefix = "pdu_leg" + leg + "_";
            final float demand = unit.value(prefix + "demand", 0.0f);
            final float served = unit.value(prefix + "served", 0.0f);

            circuits.add(new Circuit(
                    leg == com.createstats.power.PduStuds.COUNT ? "Back" : "Leg " + (leg + 1),
                    demand, served, unit.value(prefix + "ceiling", 0.0f),
                    legState(unit.value(prefix + "state", CreateStatsPowerMetrics.LEG_OK),
                            demand, served),
                    Math.round(unit.value(prefix + "devices", 0.0f)), 0));
        }

        final FePriority[] tiers = FePriority.values();
        final int[] total = new int[tiers.length];
        final int[] served = new int[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            total[i] = Math.round(unit.value("pdu_tier" + i + "_total", 0.0f));
            served[i] = Math.round(unit.value("pdu_tier" + i + "_served", 0.0f));
        }

        final float demand = unit.value("pdu_demand", 0.0f);
        final float servedDemand = unit.value("pdu_served_demand", 0.0f);
        return new Distributor(unit.label(), true,
                unit.value("energy_input_rate", 0.0f),
                unit.value("energy_output_rate", servedDemand),
                demand, servedDemand,
                unit.value("pdu_generation", 0.0f),
                unit.value("pdu_budget", 0.0f),
                unit.value("pdu_ceiling", 0.0f),
                unit.flag("pdu_throughput_limited"),
                Math.round(unit.value("pdu_devices", 0.0f)),
                Math.round(unit.value("pdu_served_devices", 0.0f)),
                Math.round(unit.value("pdu_shed_devices", 0.0f)),
                modeName(unit),
                unit.value("energy_stored", 0.0f), unit.value("energy_capacity", 0.0f),
                total, served, circuits);
    }

    private static Distributor readController(SourceProbe.Unit unit) {
        final List<Circuit> circuits = new ArrayList<>();
        float demand = 0.0f;
        float served = 0.0f;
        int devices = 0;
        int shed = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final String prefix = "bank_out" + i + "_";
            final Circuit.State state = outputState(unit.value(prefix + "reason", 0.0f));
            if (state == null) {
                continue;
            }
            final float want = unit.value(prefix + "demand", 0.0f);
            final float got = unit.value(prefix + "flow", 0.0f);
            final int count = Math.round(unit.value(prefix + "devices", 0.0f));
            demand += want;
            served += got;
            devices += count;
            if (state == Circuit.State.SHED || state == Circuit.State.FLOOR
                    || state == Circuit.State.RECHARGING || state == Circuit.State.OPEN) {
                shed += count;
            }
            circuits.add(new Circuit(outputName(unit, i), want, got,
                    unit.value(prefix + "ceiling", 0.0f), state, count,
                    Math.round(unit.value(prefix + "floor", 0.0f))));
        }
        final float input = unit.value("energy_input_rate", 0.0f);
        return new Distributor(unit.label(), false,
                input, unit.value("energy_output_rate", served),
                demand, served,
                input, input, 0.0f, false,
                devices, Math.max(0, devices - shed), shed,
                stanceName(unit),
                unit.value("energy_stored", 0.0f), unit.value("energy_capacity", 0.0f),
                new int[0], new int[0], circuits);
    }

    private static String outputName(SourceProbe.Unit unit, int index) {
        final String base = "Output " + (index + 1);
        final BatteryOutputMode mode = byOrdinal(BatteryOutputMode.values(),
                unit.value("bank_out" + index + "_mode", 0.0f));
        return mode == null || mode == BatteryOutputMode.DIRECT ? base : base + " " + mode.label();
    }

    private static Circuit.State legState(float raw, float demand, float served) {
        if (raw >= CreateStatsPowerMetrics.LEG_TRIPPED) {
            return Circuit.State.TRIPPED;
        }
        if (raw >= CreateStatsPowerMetrics.LEG_OPEN) {
            return Circuit.State.OPEN;
        }
        if (raw >= CreateStatsPowerMetrics.LEG_OVER_CAPACITY) {
            return Circuit.State.OVER_CAPACITY;
        }
        if (demand <= 0.01f) {
            return Circuit.State.IDLE;
        }

        if (served + 0.5f < demand) {
            return served > 0.01f ? Circuit.State.PARTIAL : Circuit.State.SHED;
        }
        return Circuit.State.OK;
    }

    private static Circuit.State outputState(float ordinal) {
        final BatteryControllerPlan.Reason reason =
                byOrdinal(BatteryControllerPlan.Reason.values(), ordinal);
        if (reason == null) {
            return Circuit.State.IDLE;
        }
        return switch (reason) {
            case SERVED -> Circuit.State.OK;
            case PARTIAL -> Circuit.State.PARTIAL;
            case OFF -> Circuit.State.OPEN;
            case FLOOR -> Circuit.State.FLOOR;
            case RECHARGE -> Circuit.State.RECHARGING;
            case RATIONED -> Circuit.State.RATIONED;
            case NO_SURPLUS, SHED -> Circuit.State.SHED;
            case IDLE -> Circuit.State.IDLE;
            case UNWIRED -> null;
        };
    }

    private static String modeName(SourceProbe.Unit unit) {
        final PduMode mode = byOrdinal(PduMode.values(), unit.value("pdu_mode", 0.0f));
        return mode == null ? "" : mode.getDisplayName();
    }

    private static String stanceName(SourceProbe.Unit unit) {
        final FeReserveReport.Stance stance =
                byOrdinal(FeReserveReport.Stance.values(), unit.value("bank_stance", -1.0f));
        return stance == null ? "" : stance.label();
    }

    private static <E> E byOrdinal(E[] values, float raw) {
        final int i = Math.round(raw);
        return i >= 0 && i < values.length ? values[i] : null;
    }

    private static List<Distributor> chain(List<Distributor> distributors, List<Node> sources) {
        if (distributors.size() < 2) {
            return distributors;
        }
        boolean hasPanel = false;
        for (final Distributor d : distributors) {
            hasPanel |= d.panel();
        }
        if (!hasPanel) {
            return distributors;
        }
        final List<Distributor> kept = new ArrayList<>(distributors.size());
        for (final Distributor d : distributors) {
            if (d.panel()) {
                kept.add(d);
            } else {
                sources.add(new Node(d.label(), Role.STORAGE, d.output()));
            }
        }
        return kept;
    }

    private static List<Node> rollUp(List<Node> nodes, int max, String noun) {
        if (nodes.size() <= max) {
            nodes.sort((a, b) -> Float.compare(b.rate(), a.rate()));
            return nodes;
        }
        nodes.sort((a, b) -> Float.compare(b.rate(), a.rate()));
        final List<Node> kept = new ArrayList<>(nodes.subList(0, max - 1));
        float rest = 0.0f;
        int count = 0;
        for (final Node n : nodes.subList(max - 1, nodes.size())) {
            rest += n.rate();
            count += Math.max(1, n.roll());
        }
        final Node first = nodes.get(0);
        kept.add(new Node(String.format(Locale.ROOT, "%d more %s", count, noun),
                first.role(), rest, count));
        return kept;
    }

    private static float[] longerOf(float[] current, TimeSeriesStore store) {
        if (store == null || store.getSampleCount() <= current.length) {
            return current;
        }
        return store.getDataWindow(160);
    }
}
