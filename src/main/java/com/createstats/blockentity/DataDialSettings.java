package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;

public class DataDialSettings
{
    private SourceMode sourceMode;
    private final EnumMap<SourceKind, Metric> metricsBySourceKind;

    public DataDialSettings() {
        this.sourceMode = SourceMode.LOCAL;
        this.metricsBySourceKind = new EnumMap<>(SourceKind.class);
        for (SourceKind sourceKind : SourceKind.values()) {
            final Metric defaultMetric = defaultMetricFor(sourceKind);
            if (defaultMetric != null) {
                this.metricsBySourceKind.put(sourceKind, defaultMetric);
            }
        }
    }

    public SourceMode getSourceMode() {
        return this.sourceMode;
    }

    public void cycleSourceMode() {
        this.sourceMode = this.sourceMode.next();
    }

    public Metric getMetric(SourceKind sourceKind) {
        final Metric selected = this.metricsBySourceKind.get(sourceKind);
        if (selected != null && this.isMetricSupported(selected, sourceKind)) {
            return selected;
        }
        final Metric defaultMetric = defaultMetricFor(sourceKind);
        if (defaultMetric != null) {
            this.metricsBySourceKind.put(sourceKind, defaultMetric);
        }
        return defaultMetric;
    }

    public Metric cycleMetric(SourceKind sourceKind) {
        final Metric[] options = this.getMetricOptions(sourceKind);
        if (options.length == 0) {
            return null;
        }
        final Metric current = this.getMetric(sourceKind);
        int index = 0;
        for (int i = 0; i < options.length; ++i) {
            if (options[i] == current) {
                index = i;
                break;
            }
        }
        final Metric next = options[(index + 1) % options.length];
        this.metricsBySourceKind.put(sourceKind, next);
        return next;
    }

    public boolean ensureMetricSupported(SourceKind sourceKind) {
        final Metric current = this.metricsBySourceKind.get(sourceKind);
        if (current != null && this.isMetricSupported(current, sourceKind)) {
            return false;
        }
        final Metric defaultMetric = defaultMetricFor(sourceKind);
        if (defaultMetric == null) {
            return this.metricsBySourceKind.remove(sourceKind) != null;
        }
        final boolean changed = current != defaultMetric;
        this.metricsBySourceKind.put(sourceKind, defaultMetric);
        return changed;
    }

    public boolean hasMetricOptions(SourceKind sourceKind) {
        return this.getMetricOptions(sourceKind).length > 0;
    }

    public Metric[] getMetricOptions(SourceKind sourceKind) {
        return switch (sourceKind) {
            case KINETIC_STRESS -> new Metric[] { Metric.STRESS_PERCENT, Metric.RPM };
            case KINETIC_SPEED -> new Metric[] { Metric.RPM };
            case ITEM_STORAGE, FLUID_STORAGE -> new Metric[] { Metric.FILL_PERCENT };
            case ENERGY_STORAGE -> new Metric[] { Metric.ENERGY_FILL_PERCENT, Metric.ENERGY_STORED, Metric.ENERGY_NET_RATE };
            case BOILER -> new Metric[] { Metric.BOILER_PERCENT, Metric.HEAT_PERCENT };
            case NO_SOURCE, UNKNOWN -> new Metric[0];
        };
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("SourceMode", this.sourceMode.name());
        final CompoundTag metricsTag = new CompoundTag();
        for (SourceKind sourceKind : SourceKind.values()) {
            final Metric metric = this.metricsBySourceKind.get(sourceKind);
            if (metric != null) {
                metricsTag.putString(sourceKind.name(), metric.name());
            }
        }
        tag.put("MetricsBySourceKind", metricsTag);
        return tag;
    }

    public static DataDialSettings load(CompoundTag tag) {
        final DataDialSettings settings = new DataDialSettings();
        try {
            settings.sourceMode = SourceMode.valueOf(tag.getString("SourceMode"));
        } catch (Exception ignored) {
        }
        if (tag.contains("MetricsBySourceKind")) {
            final CompoundTag metricsTag = tag.getCompound("MetricsBySourceKind");
            for (SourceKind sourceKind : SourceKind.values()) {
                if (!metricsTag.contains(sourceKind.name())) {
                    continue;
                }
                try {
                    settings.metricsBySourceKind.put(sourceKind, Metric.valueOf(metricsTag.getString(sourceKind.name())));
                } catch (Exception ignored) {
                }
            }
        }
        for (SourceKind sourceKind : SourceKind.values()) {
            settings.ensureMetricSupported(sourceKind);
        }
        return settings;
    }

    private boolean isMetricSupported(Metric metric, SourceKind sourceKind) {
        for (Metric option : this.getMetricOptions(sourceKind)) {
            if (option == metric) {
                return true;
            }
        }
        return false;
    }

    private static Metric defaultMetricFor(SourceKind sourceKind) {
        return switch (sourceKind) {
            case KINETIC_STRESS -> Metric.STRESS_PERCENT;
            case KINETIC_SPEED -> Metric.RPM;
            case ITEM_STORAGE, FLUID_STORAGE -> Metric.FILL_PERCENT;
            case ENERGY_STORAGE -> Metric.ENERGY_FILL_PERCENT;
            case BOILER -> Metric.BOILER_PERCENT;
            case NO_SOURCE, UNKNOWN -> null;
        };
    }

    public enum SourceMode {
        LOCAL("Local"),
        LINKED("Linked");

        private final String displayName;

        SourceMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public SourceMode next() {
            final SourceMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum SourceKind {
        NO_SOURCE("No source"),
        KINETIC_STRESS("Kinetic"),
        KINETIC_SPEED("Kinetic"),
        ITEM_STORAGE("Item storage"),
        FLUID_STORAGE("Fluid storage"),
        ENERGY_STORAGE("Energy storage"),
        BOILER("Boiler"),
        UNKNOWN("Unknown");

        private final String displayName;

        SourceKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    public enum Metric {
        STRESS_PERCENT("Stress %"),
        RPM("RPM"),
        FILL_PERCENT("Fill %"),
        ENERGY_FILL_PERCENT("Energy %"),
        ENERGY_STORED("Stored FE"),
        ENERGY_NET_RATE("Net FE/t"),
        BOILER_PERCENT("Boiler %"),
        HEAT_PERCENT("Heat %");

        private final String displayName;

        Metric(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
