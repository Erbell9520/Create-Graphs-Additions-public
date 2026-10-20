package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.SourceFamilyDefinition;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SourceProbe {
    private static final String AUTO = "_auto_";

    private SourceProbe() {
    }

    public static final class Unit {
        private final SourceConfig config;
        private final Map<String, StatsDisplayPanelBE.DataChannel> metrics;

        Unit(SourceConfig config, Map<String, StatsDisplayPanelBE.DataChannel> metrics) {
            this.config = config;
            this.metrics = metrics;
        }

        public SourceConfig config() {
            return this.config;
        }

        public String type() {
            return this.config == null || this.config.getSourceType() == null
                    ? "" : this.config.getSourceType();
        }

        public String label() {
            if (this.config == null) {
                return "Source";
            }
            final String user = this.config.getUserLabel();
            return user != null && !user.isEmpty() ? user : type();
        }

        public boolean has(String metricId) {
            return this.metrics.containsKey(metricId);
        }

        public float value(String metricId, float fallback) {
            final StatsDisplayPanelBE.DataChannel ch = this.metrics.get(metricId);
            if (ch == null || ch.getStore() == null || ch.getStore().getSampleCount() == 0) {
                return fallback;
            }
            return ch.getStore().getLatestValue();
        }

        public float value(String metricId) {
            return value(metricId, 0.0f);
        }

        public float any(float fallback, String... metricIds) {
            for (final String id : metricIds) {
                if (has(id)) {
                    return value(id, fallback);
                }
            }
            return fallback;
        }

        public boolean flag(String metricId) {
            return value(metricId, 0.0f) > 0.5f;
        }

        public TimeSeriesStore store(String metricId) {
            final StatsDisplayPanelBE.DataChannel ch = this.metrics.get(metricId);
            return ch == null ? null : ch.getStore();
        }

        public StatsDisplayPanelBE.DataChannel channel(String metricId) {
            return this.metrics.get(metricId);
        }

        public String labelOf(String metricId, String fallback) {
            final StatsDisplayPanelBE.DataChannel ch = this.metrics.get(metricId);
            final String label = ch == null ? null : ch.getLabel();
            return label == null || label.isEmpty() ? fallback : label;
        }

        public String labelSuffix(String metricId, String prefix, String fallback) {
            final String label = labelOf(metricId, "");
            if (label.regionMatches(true, 0, prefix, 0, prefix.length())
                    && label.length() > prefix.length()) {
                return label.substring(prefix.length()).trim();
            }
            return fallback;
        }

        public Map<String, StatsDisplayPanelBE.DataChannel> metrics() {
            return this.metrics;
        }

        public boolean isEmpty() {
            return this.metrics.isEmpty();
        }
    }

    public static List<Unit> byFamily(StatsDisplayPanelBE panel, String... familyIds) {
        return collect(panel, config -> {
            final SourceFamilyDefinition family =
                    CustomScreenSourceFamilyCatalog.familyForSource(config);
            if (family == null) {
                return false;
            }
            for (final String id : familyIds) {
                if (id.equals(family.getFamilyId())) {
                    return true;
                }
            }
            return false;
        });
    }

    // by capability not family, a whitelist of every storage block is hopeless
    public static List<Unit> all(StatsDisplayPanelBE panel) {
        return collect(panel, config -> true);
    }

    public static List<Unit> byGroup(StatsDisplayPanelBE panel,
                                     SourceFamilyDefinition.SourceFamilyGroup group) {
        return collect(panel, config -> {
            final SourceFamilyDefinition family =
                    CustomScreenSourceFamilyCatalog.familyForSource(config);
            return family != null && family.getGroup() == group;
        });
    }

    public static List<Unit> byTypeContains(StatsDisplayPanelBE panel, String... fragments) {
        return collect(panel, config -> {
            final String type = config.getSourceType() == null
                    ? "" : config.getSourceType().toLowerCase(Locale.ROOT);
            for (final String fragment : fragments) {
                if (type.contains(fragment.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        });
    }

    public static Unit first(List<Unit> units) {
        return units.isEmpty() ? null : units.get(0);
    }

    public static boolean hasFamily(StatsDisplayPanelBE panel, String... familyIds) {
        return !byFamily(panel, familyIds).isEmpty();
    }

    private interface Filter {
        boolean accepts(SourceConfig config);
    }

    private static List<Unit> collect(StatsDisplayPanelBE panel, Filter filter) {
        final List<Unit> units = new ArrayList<>();
        if (panel == null) {
            return units;
        }
        final Map<String, SourceConfig> registry = panel.getSourceRegistry();
        if (registry == null || registry.isEmpty()) {
            return units;
        }
        final Map<String, StatsDisplayPanelBE.DataChannel> all = panel.getAllChannels();
        if (all == null) {
            return units;
        }
        for (final Map.Entry<String, SourceConfig> entry : registry.entrySet()) {
            final SourceConfig config = entry.getValue();
            if (config == null || !filter.accepts(config)) {
                continue;
            }
            units.add(new Unit(config, metricsOf(all, entry.getKey())));
        }
        return units;
    }

    private static Map<String, StatsDisplayPanelBE.DataChannel> metricsOf(
            Map<String, StatsDisplayPanelBE.DataChannel> all, String sourceId) {
        final String prefix = "src_" + sourceId + AUTO;
        final Map<String, StatsDisplayPanelBE.DataChannel> metrics = new LinkedHashMap<>();
        for (final Map.Entry<String, StatsDisplayPanelBE.DataChannel> entry : all.entrySet()) {
            final String id = entry.getKey();
            if (id != null && id.startsWith(prefix)) {
                metrics.put(id.substring(prefix.length()), entry.getValue());
            }
        }
        return metrics;
    }
}
