package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Signals {
    private static final Map<TimeSeriesStore, Map<Integer, CachedWindow>> WINDOW_CACHE =
            new WeakHashMap<>();

    private static final class CachedWindow {
        long revision = Long.MIN_VALUE;
        float interval = Float.NaN;
        float[] samples = new float[0];
        Stats normal;
        Stats stale;
    }

    private Signals() {
    }

    public enum Kind {
        LOAD("Load", "%"),

        RATE("Rate", "/t"),

        SPEED("Speed", "rpm"),

        AMOUNT("Amount", "n"),

        STATE("State", "on"),

        OTHER("Value", "?");

        private final String title;
        private final String glyph;

        Kind(String title, String glyph) {
            this.title = title;
            this.glyph = glyph;
        }

        public String title() {
            return this.title;
        }

        public String glyph() {
            return this.glyph;
        }
    }

    public enum Regime {
        NO_DATA("No signal"),
        STALE("Stale"),
        STEADY("Steady"),
        RISING("Rising"),
        FALLING("Falling"),
        SWINGING("Swinging");

        private final String word;

        Regime(String word) {
            this.word = word;
        }

        public String word() {
            return this.word;
        }
    }

    public static final int[] WINDOWS = {60, 300, 0};
    public static final String[] WINDOW_LABELS = {"1m", "5m", "All"};

    public static final int FULL_WINDOW = 0;

    public static final int STALE_INTERVALS = 5;

    public static final class Signal {
        public final StatsDisplayPanelBE.DataChannel channel;
        public final String id;
        public final String sourceId;

        public final String group;

        public final String name;
        public final String unit;
        public final MetricType type;
        public final Kind kind;
        public final BlockPos pos;

        public final int series;

        public final int index;

        Signal(StatsDisplayPanelBE.DataChannel channel, String sourceId, String group,
               String name, MetricType type, BlockPos pos, int index) {
            this.channel = channel;
            this.id = channel == null ? "" : channel.getId();
            this.sourceId = sourceId == null ? "" : sourceId;
            this.group = group == null ? "" : group;
            this.name = name == null ? "" : name;
            this.type = type == null ? MetricType.GENERIC : type;
            this.unit = this.type.getUnit() == null ? "" : this.type.getUnit();
            this.kind = kindOf(this.type);
            this.pos = pos;
            this.series = Tokens.seriesFor(this.id);
            this.index = index;
        }

        public TimeSeriesStore store() {
            return this.channel == null ? null : this.channel.getStore();
        }

        public int sampleCount() {
            final TimeSeriesStore s = store();
            return s == null ? 0 : s.getSampleCount();
        }

        public float value() {
            final TimeSeriesStore s = store();
            return s == null ? 0.0f : s.getLatestValue();
        }

        public float interval() {
            final TimeSeriesStore s = store();
            return s == null ? 1.0f : Math.max(1, s.getSampleIntervalTicks()) / 20.0f;
        }

        public boolean armed() {
            return this.channel != null && this.channel.isAlertEnabled();
        }

        public boolean firing() {
            return this.channel != null && this.channel.isAlertEnabled()
                    && this.channel.isAlertTriggered();
        }

        public float threshold() {
            return this.channel == null ? 0.0f : this.channel.getAlertThreshold();
        }

        public boolean above() {
            return this.channel == null || this.channel.isAlertAbove();
        }

        // no level = nothing stale, don't accuse a live source
        public boolean stale(long nowTick) {
            final TimeSeriesStore s = store();
            if (s == null || nowTick <= 0L || s.getSampleCount() == 0) {
                return false;
            }
            final long last = s.getLastSampleTick();
            if (last <= 0L) {
                return false;
            }
            return nowTick - last > (long) STALE_INTERVALS * Math.max(1, s.getSampleIntervalTicks());
        }

        public String full() {
            return this.group.isEmpty() ? this.name : this.group + " / " + this.name;
        }

        public String tag() {
            return this.unit.isEmpty() ? this.kind.glyph() : this.unit;
        }
    }

    public static final class Group {
        public final String title;

        public final BlockPos pos;
        public final List<Signal> signals = new ArrayList<>();

        Group(String title, BlockPos pos) {
            this.title = title;
            this.pos = pos;
        }
    }

    public enum By {
        MACHINE, KIND
    }

    public static List<Signal> of(StatsDisplayPanelBE panel) {
        final List<Signal> out = new ArrayList<>();
        if (panel == null) {
            return out;
        }
        final Map<String, StatsDisplayPanelBE.DataChannel> all = safeChannels(panel);
        final Map<String, SourceConfig> registry = safeRegistry(panel);

        int i = 0;
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> e : all.entrySet()) {
            final StatsDisplayPanelBE.DataChannel ch = e.getValue();
            if (ch == null || ch.getStore() == null || ch.getStore().getSampleCount() == 0) {
                continue;
            }
            final SourceConfig config = registry.get(sourceIdOf(e.getKey()));
            out.add(signal(ch, config, SourceNames.ambiguous(registry.values(), config), i++));
        }
        return out;
    }

    public static Signal signal(StatsDisplayPanelBE.DataChannel channel, SourceConfig config,
                                boolean ambiguous, int index) {
        final String group = config == null ? "" : SourceNames.label(config, false, ambiguous);
        return new Signal(channel, config == null ? sourceIdOf(channel.getId())
                : config.getSourceId(), group, signalName(channel, config, group),
                channel.getStore() == null ? null : channel.getStore().getMetricType(),
                config == null ? null : config.getSourcePos(), index);
    }

    static String signalName(StatsDisplayPanelBE.DataChannel ch, SourceConfig config, String group) {
        String label = ch == null || ch.getLabel() == null || ch.getLabel().isEmpty()
                ? prettyMetric(ch == null ? "" : ch.getId()) : ch.getLabel();
        label = stripPrefix(label, group);
        if (config != null) {
            label = stripPrefix(label, config.getSourceType());
            label = stripPrefix(label, config.getUserLabel());
        }
        return label.isEmpty() ? prettyMetric(ch == null ? "" : ch.getId()) : label;
    }

    private static String stripPrefix(String label, String prefix) {
        if (label == null || prefix == null || prefix.isEmpty() || label.length() <= prefix.length()) {
            return label == null ? "" : label;
        }
        if (!label.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return label;
        }
        final String rest = label.substring(prefix.length()).trim();

        return rest.isEmpty() ? label : rest;
    }

    static String prettyMetric(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return "Signal";
        }
        final int auto = channelId.indexOf("_auto_");
        final String raw = auto >= 0 ? channelId.substring(auto + 6) : channelId;
        final String s = raw.replace('_', ' ').trim();
        return s.isEmpty() ? "Signal" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String sourceIdOf(String channelId) {
        if (channelId != null && channelId.startsWith("src_")) {
            final int auto = channelId.indexOf("_auto_");
            if (auto > 4) {
                return channelId.substring(4, auto);
            }
        }
        return channelId == null ? "" : channelId;
    }

    private static Map<String, StatsDisplayPanelBE.DataChannel> safeChannels(
            StatsDisplayPanelBE panel) {
        try {
            final Map<String, StatsDisplayPanelBE.DataChannel> all = panel.getAllChannels();
            return all == null ? new LinkedHashMap<>() : all;
        } catch (final RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, SourceConfig> safeRegistry(StatsDisplayPanelBE panel) {
        try {
            final Map<String, SourceConfig> registry = panel.getSourceRegistry();
            return registry == null ? new LinkedHashMap<>() : registry;
        } catch (final RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    public static final String DIRECT_GROUP = "Direct signals";

    public static List<Group> grouped(List<Signal> signals, By by) {
        final Map<String, Group> map = new LinkedHashMap<>();
        if (signals == null) {
            return new ArrayList<>();
        }
        if (by == By.KIND) {
            for (final Kind k : Kind.values()) {
                for (final Signal s : signals) {
                    if (s.kind == k) {
                        map.computeIfAbsent(k.name(), key -> new Group(k.title(), null))
                                .signals.add(s);
                    }
                }
            }
            return new ArrayList<>(map.values());
        }
        for (final Signal s : signals) {
            final String key = s.group.isEmpty() ? "" : s.sourceId;
            final Group g = map.computeIfAbsent(key,
                    k -> new Group(s.group.isEmpty() ? DIRECT_GROUP : s.group, s.pos));
            g.signals.add(s);
        }
        for (final Group g : map.values()) {
            g.signals.sort(rank());
        }

        final List<Group> out = new ArrayList<>(map.values());
        out.sort(Comparator.comparingInt(g -> DIRECT_GROUP.equals(g.title) ? 1 : 0));
        return out;
    }

    public static Comparator<Signal> rank() {
        return Comparator.<Signal>comparingInt(s -> s.kind.ordinal())
                .thenComparingInt(s -> s.index);
    }

    public static List<Signal> filter(List<Signal> signals, String query) {
        final List<Signal> out = new ArrayList<>();
        if (signals == null) {
            return out;
        }
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return new ArrayList<>(signals);
        }
        for (final Signal s : signals) {
            if (s.name.toLowerCase(Locale.ROOT).contains(q)
                    || s.group.toLowerCase(Locale.ROOT).contains(q)
                    || s.unit.toLowerCase(Locale.ROOT).contains(q)
                    || s.kind.title().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(s);
            }
        }
        return out;
    }

    public static Signal byId(List<Signal> signals, String id) {
        if (signals == null || id == null || id.isEmpty()) {
            return null;
        }
        for (final Signal s : signals) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static Kind kindOf(MetricType type) {
        if (type == null) {
            return Kind.OTHER;
        }
        return switch (type) {
            case STRESS_PERCENT, FILL_LEVEL, EFFICIENCY_SCORE -> Kind.LOAD;
            case ITEMS_PER_MIN, ITEMS_PER_SEC, ITEMS_PER_HR, FLUID_MB_PER_TICK,
                 ENERGY_FE_PER_TICK, WATER_SUPPLY -> Kind.RATE;
            case RPM, VELOCITY, ANGLE_DEGREES -> Kind.SPEED;
            case STRESS_SU, FLUID_MB, ENERGY_FE, ITEM_COUNT, BURN_TIME, ENDURANCE_TICKS
                    -> Kind.AMOUNT;
            case BOOLEAN_FLAG, REDSTONE_POWER, HEAT_LEVEL, ENGINE_COUNT -> Kind.STATE;
            default -> Kind.OTHER;
        };
    }

    public static final class Stats {
        public int count;
        public float lo;
        public float hi;
        public float latest;
        public float median;
        public float p10;
        public float p90;

        public float drift;

        public float perMinute;

        public float seconds;
        public Regime regime = Regime.NO_DATA;

        public float spread() {
            return Math.max(0.0f, this.p90 - this.p10);
        }

        public boolean usable() {
            return this.count > 0;
        }
    }

    public static Stats describe(float[] window, float intervalSeconds, boolean stale) {
        final Stats out = new Stats();
        if (window == null || window.length == 0) {
            out.regime = stale ? Regime.STALE : Regime.NO_DATA;
            return out;
        }
        final int n = window.length;
        out.count = n;
        out.latest = window[n - 1];
        out.seconds = Math.max(0.0f, (n - 1) * Math.max(0.05f, intervalSeconds));

        final float[] sorted = window.clone();
        java.util.Arrays.sort(sorted);
        out.lo = sorted[0];
        out.hi = sorted[n - 1];
        out.median = percentile(sorted, 0.5f);
        out.p10 = percentile(sorted, 0.1f);
        out.p90 = percentile(sorted, 0.9f);

        out.drift = window[n - 1] - window[0];
        out.perMinute = out.seconds <= 0.001f ? 0.0f : out.drift / out.seconds * 60.0f;
        out.regime = classify(out, stale);
        return out;
    }

    // a drift is a trend only if it's bigger than the noise
    private static Regime classify(Stats s, boolean stale) {
        if (stale) {
            return Regime.STALE;
        }
        if (s.count == 0) {
            return Regime.NO_DATA;
        }
        if (s.count < 4) {
            return Regime.STEADY;
        }
        final float spread = s.spread();
        final float scale = Math.max(Math.max(Math.abs(s.median), spread), 1.0e-4f);
        final float trend = Math.max(spread * 0.75f, scale * 0.02f);
        if (Math.abs(s.drift) > trend) {
            return s.drift > 0.0f ? Regime.RISING : Regime.FALLING;
        }
        return spread > scale * 0.10f ? Regime.SWINGING : Regime.STEADY;
    }

    static float percentile(float[] sorted, float p) {
        if (sorted.length == 0) {
            return 0.0f;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        final float pos = Math.max(0.0f, Math.min(1.0f, p)) * (sorted.length - 1);
        final int i = (int) Math.floor(pos);
        final int j = Math.min(sorted.length - 1, i + 1);
        return sorted[i] + (sorted[j] - sorted[i]) * (pos - i);
    }

    public static Stats stats(Signal signal, int windowSeconds, long nowTick) {
        if (signal == null || signal.store() == null) {
            return new Stats();
        }
        final CachedWindow cached = cachedWindow(signal, windowSeconds);
        if (!signal.stale(nowTick)) {
            if (cached.normal == null) {
                cached.normal = describe(cached.samples, cached.interval, false);
            }
            return cached.normal;
        }
        if (cached.stale == null) {
            cached.stale = copyOf(cached.normal == null
                    ? describe(cached.samples, cached.interval, false) : cached.normal);
            cached.stale.regime = Regime.STALE;
        }
        return cached.stale;
    }

    public static float[] window(Signal signal, int windowSeconds) {
        if (signal == null || signal.store() == null || signal.store().getSampleCount() == 0) {
            return new float[0];
        }
        return cachedWindow(signal, windowSeconds).samples;
    }

    private static CachedWindow cachedWindow(Signal signal, int windowSeconds) {
        final TimeSeriesStore store = signal.store();
        synchronized (WINDOW_CACHE) {
            final Map<Integer, CachedWindow> byWindow =
                    WINDOW_CACHE.computeIfAbsent(store, ignored -> new LinkedHashMap<>());
            final CachedWindow cached = byWindow.computeIfAbsent(windowSeconds,
                    ignored -> new CachedWindow());
            final long revision = store.getRevision();
            final float interval = signal.interval();
            if (cached.revision != revision
                    || Float.floatToIntBits(cached.interval) != Float.floatToIntBits(interval)) {
                final int count = windowSeconds <= 0
                        ? store.getSampleCount()
                        : store.getSampleCountForSeconds(windowSeconds);
                cached.samples = new float[count];
                store.copyRecent(cached.samples, count);
                cached.revision = revision;
                cached.interval = interval;
                cached.normal = null;
                cached.stale = null;
            }
            return cached;
        }
    }

    private static Stats copyOf(Stats source) {
        final Stats out = new Stats();
        out.count = source.count;
        out.lo = source.lo;
        out.hi = source.hi;
        out.latest = source.latest;
        out.median = source.median;
        out.p10 = source.p10;
        out.p90 = source.p90;
        out.drift = source.drift;
        out.perMinute = source.perMinute;
        out.seconds = source.seconds;
        out.regime = source.regime;
        return out;
    }

    public static int crossings(float[] window, float threshold, boolean above) {
        if (window == null || window.length < 2) {
            return 0;
        }
        boolean inside = above ? window[0] >= threshold : window[0] <= threshold;

        int count = inside ? 1 : 0;
        for (int i = 1; i < window.length; i++) {
            final boolean now = above ? window[i] >= threshold : window[i] <= threshold;
            if (now && !inside) {
                count++;
            }
            inside = now;
        }
        return count;
    }

    public static float proximity(Signal signal, Stats stats) {
        if (signal == null || stats == null || !stats.usable() || !signal.armed()) {
            return 0.0f;
        }
        final float distance = signal.above()
                ? signal.threshold() - stats.latest
                : stats.latest - signal.threshold();
        if (distance <= 0.0f) {
            return 1.0f;
        }
        final float scale = Math.max(Math.max(stats.spread(), Math.abs(signal.threshold()) * 0.05f),
                1.0e-3f);
        return Math.max(0.0f, Math.min(1.0f, 1.0f - distance / (scale * 3.0f)));
    }

    public static float suggest(Stats stats, boolean above) {
        if (stats == null || !stats.usable()) {
            return 0.0f;
        }
        final float band = stats.spread();
        final float margin = Math.max(band * 0.35f,
                Math.max(Math.abs(stats.median) * 0.05f, 0.5f));
        return above ? stats.p90 + margin : stats.p10 - margin;
    }

    public static String ago(float seconds) {
        final int s = Math.max(0, Math.round(seconds));
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            final int m = s / 60;
            final int rest = s % 60;
            return rest == 0 ? m + "m" : m + "m " + rest + "s";
        }
        return s / 3600 + "h " + s % 3600 / 60 + "m";
    }

    public static String withUnit(float value, String unit) {
        if (unit == null || unit.isEmpty()) {
            return Text.value(value);
        }
        return "%".equals(unit) ? Text.value(value) + "%" : Text.value(value) + " " + unit;
    }

    public static String rate(float perMinute, String unit) {
        final float a = Math.abs(perMinute);
        if (a < 0.005f) {
            return "flat";
        }
        if (a >= 600.0f) {
            return Text.delta(perMinute / 60.0f, 1) + (unit.isEmpty() ? "" : " " + unit) + "/s";
        }
        return Text.delta(perMinute, a >= 10.0f ? 0 : 2)
                + (unit.isEmpty() ? "" : " " + unit) + "/min";
    }
}
