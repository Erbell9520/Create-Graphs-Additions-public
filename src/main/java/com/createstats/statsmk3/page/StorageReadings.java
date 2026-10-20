package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StorageReadings {
    private StorageReadings() {
    }

    public static final float HIGH = 90.0f;

    public static final float LOW = 10.0f;

    public static final float EDGE = 1.0f;

    private static final float STEADY_FRACTION = 0.0004f;

    public static final int ROSTER_CAP = 9;

    public enum Kind {
        ITEMS("items", "Items", ""),

        FLUID("fluid", "Fluid", "mB"),

        ENERGY("energy", "Energy", "FE");

        public final String variant;
        public final String label;
        public final String unit;

        Kind(String variant, String label, String unit) {
            this.variant = variant;
            this.label = label;
            this.unit = unit;
        }

        public static Kind of(String variant) {
            for (final Kind k : values()) {
                if (k.variant.equals(variant)) {
                    return k;
                }
            }
            return ITEMS;
        }
    }

    public record Item(String name, float count) {
    }

    public record Vessel(Kind kind, boolean live, String content,
                         float level, float capacity, float pct, boolean hasCapacity,
                         float lowerPct, float upperPct, boolean powered, boolean hasSignal,
                         int uniqueTypes, List<Item> items,
                         float[] history, float secondsPerSample) {
        public static Vessel empty(Kind kind) {
            return new Vessel(kind, false, "", 0.0f, 0.0f, 0.0f, false, -1.0f, -1.0f, false, false,
                    0, List.of(), new float[0], 1.0f);
        }

        public boolean hasThresholds() {
            return this.lowerPct >= 0.0f && this.upperPct >= 0.0f;
        }

        public float fill() {
            return Math.max(0.0f, Math.min(1.0f, this.pct / 100.0f));
        }

        public float headroom() {
            return this.hasCapacity ? Math.max(0.0f, this.capacity - this.level) : 0.0f;
        }

        public boolean full() {
            return this.hasCapacity && this.pct >= 100.0f - EDGE;
        }

        public boolean starved() {
            return this.level <= 0.0f || (this.hasCapacity && this.pct <= EDGE);
        }

        public Float slopePerSecond() {
            if (this.history == null || this.history.length < 8) {
                return null;
            }
            final float perSample = PageArt.slope(this.history, this.history.length);
            return perSample / Math.max(0.05f, this.secondsPerSample);
        }

        public boolean steady() {
            final Float rate = slopePerSecond();
            if (rate == null) {
                return true;
            }
            final float floor = Math.max(0.01f,
                    (this.hasCapacity ? this.capacity : Math.max(1.0f, this.level)) * STEADY_FRACTION);
            return Math.abs(rate) < floor;
        }

        public Float etaSeconds() {
            if (!this.hasCapacity || steady()) {
                return null;
            }
            final Float rate = slopePerSecond();
            if (rate == null || rate == 0.0f) {
                return null;
            }
            final float room = rate > 0.0f ? headroom() : Math.max(0.0f, this.level);
            final float seconds = room / Math.abs(rate);
            return Float.isFinite(seconds) && seconds > 0.0f && seconds <= 3600.0f ? seconds : null;
        }

        public String etaEnd() {
            final Float rate = slopePerSecond();
            return rate != null && rate > 0.0f ? "full" : "empty";
        }

        public String state() {
            if (!this.live) {
                return "NO DATA";
            }
            if (full()) {
                return "FULL";
            }
            if (starved()) {
                return "EMPTY";
            }
            if (steady()) {
                return "HOLDING";
            }
            final Float rate = slopePerSecond();
            return rate != null && rate > 0.0f ? "FILLING" : "DRAINING";
        }

        public float[] percentSeries() {
            if (this.history == null || this.history.length == 0) {
                return new float[0];
            }
            if (!this.hasCapacity) {
                return new float[0];
            }
            final float[] out = new float[this.history.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = this.history[i] / Math.max(1.0e-4f, this.capacity) * 100.0f;
            }
            return out;
        }

        public String amountText() {
            return format(this.level);
        }

        public String capacityText() {
            return this.hasCapacity ? format(this.capacity) : "--";
        }

        public String format(float value) {
            final String number = this.kind == Kind.ITEMS
                    ? com.createstats.statsmk3.ui.Text.group(Math.round(value))
                    : com.createstats.statsmk3.ui.Text.value(value);
            return this.kind.unit.isEmpty() ? number : number + " " + this.kind.unit;
        }

        public String compact(float value) {
            if (this.kind != Kind.ITEMS) {
                return com.createstats.statsmk3.ui.Text.compact(value);
            }
            final float a = Math.abs(value);
            if (a >= 1_000_000.0f) {
                return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0f);
            }
            if (a >= 100_000.0f) {
                return String.format(Locale.ROOT, "%.0fk", value / 1000.0f);
            }
            return com.createstats.statsmk3.ui.Text.group(Math.round(value));
        }

        public float urgency() {
            if (!this.live) {
                return -1.0f;
            }

            final float extremity = this.hasCapacity
                    ? Math.abs(this.pct - 50.0f) * 2.0f
                    : 0.0f;
            final Float eta = etaSeconds();
            final float soon = eta == null ? 0.0f
                    : 60.0f * (1.0f - Math.min(1.0f, eta / 600.0f));
            return extremity + soon;
        }

        public int statusColor() {
            if (!this.live) {
                return Tokens.INK_3;
            }
            if (!this.hasCapacity) {
                return Tokens.INK_2;
            }
            if (this.kind == Kind.ENERGY) {
                if (this.pct <= 5.0f) {
                    return Tokens.CRITICAL;
                }
                return this.pct <= 20.0f ? Tokens.CAUTION : Tokens.GOOD;
            }
            if (full() || starved()) {
                return Tokens.CRITICAL;
            }
            return this.pct >= HIGH || this.pct <= LOW ? Tokens.CAUTION : Tokens.GOOD;
        }

        public boolean alarming() {
            if (!this.live || !this.hasCapacity) {
                return false;
            }
            if (this.kind == Kind.ENERGY) {
                return this.pct <= 5.0f;
            }
            if (full() || starved()) {
                return true;
            }
            final Float eta = etaSeconds();
            return eta != null && eta < 60.0f;
        }
    }

    public static List<String> variantsOf(SourceProbe.Unit u) {
        final List<String> out = new ArrayList<>(3);
        if (u == null) {
            return out;
        }
        if (holdsItems(u)) {
            out.add(Kind.ITEMS.variant);
        }
        if (holdsFluid(u)) {
            out.add(Kind.FLUID.variant);
        }
        if (holdsEnergy(u)) {
            out.add(Kind.ENERGY.variant);
        }
        return out;
    }

    public record Store(String name, Vessel vessel) {
    }

    public static List<Store> storesOf(StatsDisplayPanelBE panel) {
        final List<Store> out = new ArrayList<>();
        for (final SourceProbe.Unit u : SourceProbe.all(panel)) {
            final List<String> variants = variantsOf(u);
            if (variants.isEmpty()) {
                continue;
            }
            final SourceConfig config = u.config();
            final String name = nameOf(config, ambiguousType(panel, config));
            for (final String variant : variants) {
                out.add(new Store(name, read(u, variant)));
            }
        }
        return out;
    }

    public static String nameOf(SourceConfig config, boolean ambiguous) {
        if (config == null) {
            return "Store";
        }
        final String user = config.getUserLabel();
        if (user != null && !user.isEmpty()) {
            return user;
        }
        final String base = config.hasObservedTarget() && config.getObservedTargetLabel() != null
                && !config.getObservedTargetLabel().isEmpty()
                ? config.getObservedTargetLabel()
                : (config.getSourceType() == null || config.getSourceType().isEmpty()
                        ? "Store" : config.getSourceType());
        if (!ambiguous) {
            return base;
        }
        final BlockPos at = config.hasObservedTarget()
                ? config.getObservedTargetPos() : config.getSourcePos();
        return at == null ? base : base + " " + at.toShortString();
    }

    public static boolean ambiguousType(StatsDisplayPanelBE panel, SourceConfig one) {
        if (panel == null || one == null || panel.getSourceRegistry() == null) {
            return false;
        }
        final String mine = nameOf(one, false);
        int seen = 0;
        for (final SourceConfig other : panel.getSourceRegistry().values()) {
            if (other != null && mine.equals(nameOf(other, false)) && ++seen > 1) {
                return true;
            }
        }
        return false;
    }

    public static Vessel read(SourceProbe.Unit u, String variant) {
        final Kind kind = Kind.of(variant);
        if (u == null) {
            return Vessel.empty(kind);
        }
        return switch (kind) {
            case FLUID -> readFluid(u);
            case ENERGY -> readEnergy(u);
            default -> readItems(u);
        };
    }

    private static final String[] ITEM_CAPACITY = {"slot_capacity", "item_capacity"};

    private static boolean holdsItems(SourceProbe.Unit u) {
        if (u.has("total_items") || hasAny(u, ITEM_CAPACITY) || u.has("unique_types")) {
            return true;
        }
        if (!itemChannels(u).isEmpty()) {
            return true;
        }

        return u.has("stock_level") && stockKind(u) == Kind.ITEMS;
    }

    private static Vessel readItems(SourceProbe.Unit u) {
        final List<Item> items = itemsOf(u);
        final boolean stock = u.has("stock_level") && stockKind(u) == Kind.ITEMS;

        float level;
        float capacity;
        boolean hasCapacity;
        String levelMetric;
        float pct;

        if (stock) {
            level = u.value("stock_level", 0.0f);
            capacity = u.value("stock_capacity", 0.0f);
            hasCapacity = capacity > 0.0f;
            levelMetric = "stock_level";
            pct = u.has("fill_pct") ? u.value("fill_pct", 0.0f) : ratio(level, capacity);
        } else {
            level = u.has("total_items") ? u.value("total_items", 0.0f) : sum(items);

            final String capacityMetric = firstPresent(u, ITEM_CAPACITY);
            capacity = capacityMetric.isEmpty() ? 0.0f : u.value(capacityMetric, 0.0f);
            hasCapacity = capacity > 0.5f;
            levelMetric = u.has("total_items") ? "total_items" : "";
            pct = hasCapacity ? ratio(level, capacity) : 0.0f;
        }

        final int unique = u.has("unique_types")
                ? Math.round(u.value("unique_types", 0.0f)) : items.size();
        final String content = items.isEmpty() ? "" : items.get(0).name();
        final float[] history = historyOf(u, levelMetric);
        return new Vessel(Kind.ITEMS, true, content, level, capacity, pct, hasCapacity,
                thresholdPct(u, "lower_thresh", stock), thresholdPct(u, "upper_thresh", stock),
                u.flag("powered"), u.has("powered"),
                unique, items, history, secondsPerSample(u, levelMetric));
    }

    private static List<Item> itemsOf(SourceProbe.Unit u) {
        final List<Item> items = new ArrayList<>();
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> e : itemChannels(u)) {
            final StatsDisplayPanelBE.DataChannel ch = e.getValue();
            final float count = ch.getStore() == null ? 0.0f : ch.getStore().getLatestValue();
            if (count <= 0.0f) {
                continue;
            }
            final String label = ch.getLabel() == null || ch.getLabel().isEmpty()
                    ? pretty(e.getKey().substring(5)) : ch.getLabel();
            items.add(new Item(label, count));
        }
        items.sort(Comparator.comparingDouble((Item i) -> -i.count()));
        return items;
    }

    private static List<Map.Entry<String, StatsDisplayPanelBE.DataChannel>> itemChannels(
            SourceProbe.Unit u) {
        final List<Map.Entry<String, StatsDisplayPanelBE.DataChannel>> out = new ArrayList<>();
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> e : u.metrics().entrySet()) {
            if (e.getKey().startsWith("item_") && e.getValue() != null) {
                out.add(e);
            }
        }
        return out;
    }

    private static final String[] FLUID_LEVEL = {
            "reserve_fuel_amount", "fluid_amount", "fuel_amount", "oil_remaining_mb"};
    private static final String[] FLUID_CAPACITY = {
            "reserve_fuel_capacity", "tank_capacity", "fluid_capacity", "fuel_capacity"};
    private static final String[] FLUID_PCT = {
            "reserve_fuel_fill_pct", "tank_fill", "fluid_fill_pct", "fuel_fill_pct"};

    private static boolean holdsFluid(SourceProbe.Unit u) {
        if (hasAny(u, FLUID_LEVEL) || hasAny(u, FLUID_CAPACITY)) {
            return true;
        }
        if (!fluidChannels(u).isEmpty()) {
            return true;
        }
        return u.has("stock_level") && stockKind(u) == Kind.FLUID;
    }

    private static Vessel readFluid(SourceProbe.Unit u) {
        final boolean stock = u.has("stock_level") && stockKind(u) == Kind.FLUID;
        final String levelMetric = stock ? "stock_level" : firstPresent(u, FLUID_LEVEL);
        final String capacityMetric = stock ? "stock_capacity" : firstPresent(u, FLUID_CAPACITY);

        final float level = levelMetric.isEmpty() ? sumFluidChannels(u) : u.value(levelMetric, 0.0f);
        float capacity = capacityMetric.isEmpty() ? 0.0f : u.value(capacityMetric, 0.0f);
        float pct;
        if (stock && u.has("fill_pct")) {
            pct = u.value("fill_pct", 0.0f);
        } else {
            final String pctMetric = firstPresent(u, FLUID_PCT);
            pct = pctMetric.isEmpty() ? Float.NaN : u.value(pctMetric, Float.NaN);
        }
        capacity = impliedCapacity(capacity, level, pct);
        final boolean hasCapacity = capacity > 0.0f;
        if (Float.isNaN(pct)) {
            pct = ratio(level, capacity);
        }

        return new Vessel(Kind.FLUID, true, fluidName(u), level, capacity, pct, hasCapacity,
                thresholdPct(u, "lower_thresh", stock), thresholdPct(u, "upper_thresh", stock),
                u.flag("powered"), u.has("powered"),
                0, List.of(), historyOf(u, levelMetric), secondsPerSample(u, levelMetric));
    }

    private static String fluidName(SourceProbe.Unit u) {
        final String tagged = u.labelSuffix("fluid_present", "Fluid",
                u.labelSuffix("fuel_present", "Fuel", ""));
        if (!tagged.isEmpty()) {
            return tagged;
        }
        String best = "";
        float most = 0.0f;
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> e : fluidChannels(u)) {
            final StatsDisplayPanelBE.DataChannel ch = e.getValue();
            final float amount = ch.getStore() == null ? 0.0f : ch.getStore().getLatestValue();
            if (amount > most) {
                most = amount;
                final String label = ch.getLabel() == null ? "" : ch.getLabel();
                best = label.endsWith(" (mB)") ? label.substring(0, label.length() - 5) : label;
                if (best.isEmpty()) {
                    best = pretty(e.getKey().substring(6));
                }
            }
        }
        return best;
    }

    private static float sumFluidChannels(SourceProbe.Unit u) {
        float total = 0.0f;
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> e : fluidChannels(u)) {
            final TimeSeriesStore store = e.getValue().getStore();
            total += store == null ? 0.0f : store.getLatestValue();
        }
        return total;
    }

    private static List<Map.Entry<String, StatsDisplayPanelBE.DataChannel>> fluidChannels(
            SourceProbe.Unit u) {
        final List<Map.Entry<String, StatsDisplayPanelBE.DataChannel>> out = new ArrayList<>();
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> e : u.metrics().entrySet()) {
            final String id = e.getKey();

            if (id.startsWith("fluid_") && !id.equals("fluid_present") && !id.equals("fluid_id")
                    && !id.equals("fluid_amount") && !id.equals("fluid_capacity")
                    && !id.equals("fluid_fill_pct") && e.getValue() != null) {
                out.add(e);
            }
        }
        return out;
    }

    private static final String[] ENERGY_PCT = {"energy_fill_pct"};

    // a connector's energy_stored is the whole grid. add it and you count everything twice
    private static final java.util.Set<String> NOT_A_STORE = java.util.Set.of(
            "energy.grid", "energy.pdu", "energy.dynamo", "energy.alternator", "energy.motor");

    private static boolean holdsEnergy(SourceProbe.Unit u) {
        if (isAggregate(u)) {
            return false;
        }
        return u.has("energy_stored") || u.has("energy_capacity") || hasAny(u, ENERGY_PCT);
    }

    private static boolean isAggregate(SourceProbe.Unit u) {
        final SourceFamilyDefinition family =
                CustomScreenSourceFamilyCatalog.familyForSource(u.config());
        return family != null && NOT_A_STORE.contains(family.getFamilyId());
    }

    private static Vessel readEnergy(SourceProbe.Unit u) {
        final String pctMetric = firstPresent(u, ENERGY_PCT);
        float level = u.value("energy_stored", Float.NaN);
        float pct = pctMetric.isEmpty() ? Float.NaN : u.value(pctMetric, Float.NaN);
        float capacity = u.value("energy_capacity", 0.0f);

        if (Float.isNaN(level)) {
            level = Float.isNaN(pct) || capacity <= 0.0f ? 0.0f : capacity * pct / 100.0f;
        }
        capacity = impliedCapacity(capacity, level, pct);
        if (Float.isNaN(pct)) {
            pct = ratio(level, capacity);
        }
        return new Vessel(Kind.ENERGY, true, "", level, capacity, pct, capacity > 0.0f,
                -1.0f, -1.0f, u.flag("powered"), false,
                0, List.of(), historyOf(u, "energy_stored"), secondsPerSample(u, "energy_stored"));
    }

    private static Kind stockKind(SourceProbe.Unit u) {
        final TimeSeriesStore store = u.store("stock_level");
        final MetricType type = store == null ? null : store.getMetricType();
        return type == MetricType.FLUID_MB ? Kind.FLUID : Kind.ITEMS;
    }

    private static float thresholdPct(SourceProbe.Unit u, String metric, boolean stock) {
        if (!stock || !u.has(metric)) {
            return -1.0f;
        }
        final float min = u.value("min_level", 0.0f);
        final float max = u.value("stock_capacity", 0.0f);
        final float range = max - min;
        if (range <= 0.001f) {
            return -1.0f;
        }
        return Math.max(0.0f, Math.min(100.0f, (u.value(metric, min) - min) * 100.0f / range));
    }

    private static float[] historyOf(SourceProbe.Unit u, String metric) {
        if (metric == null || metric.isEmpty()) {
            return new float[0];
        }
        final TimeSeriesStore store = u.store(metric);
        return store == null ? new float[0] : store.getDataWindow(180);
    }

    private static float secondsPerSample(SourceProbe.Unit u, String metric) {
        final TimeSeriesStore store = metric == null || metric.isEmpty() ? null : u.store(metric);
        return store == null
                ? Math.max(1, TimeSeriesStore.DEFAULT_SAMPLE_INTERVAL_TICKS) / 20.0f
                : Math.max(0.05f, store.getSampleIntervalTicks() / 20.0f);
    }

    private static boolean hasAny(SourceProbe.Unit u, String[] ids) {
        return !firstPresent(u, ids).isEmpty();
    }

    private static String firstPresent(SourceProbe.Unit u, String[] ids) {
        for (final String id : ids) {
            if (u.has(id)) {
                return id;
            }
        }
        return "";
    }

    private static float impliedCapacity(float capacity, float level, float pct) {
        if (capacity > 0.0f || Float.isNaN(pct) || pct <= 0.5f || level <= 0.0f) {
            return capacity;
        }
        return level * 100.0f / pct;
    }

    private static float ratio(float level, float capacity) {
        return capacity <= 0.0f ? 0.0f : Math.max(0.0f, level / capacity * 100.0f);
    }

    private static float sum(List<Item> items) {
        float total = 0.0f;
        for (final Item i : items) {
            total += i.count();
        }
        return total;
    }

    private static String pretty(String raw) {
        final String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) {
            return "Unknown";
        }
        final int colon = s.lastIndexOf(':');
        final String tail = colon >= 0 && colon + 1 < s.length() ? s.substring(colon + 1) : s;
        return Character.toUpperCase(tail.charAt(0)) + tail.substring(1).toLowerCase(Locale.ROOT);
    }
}
