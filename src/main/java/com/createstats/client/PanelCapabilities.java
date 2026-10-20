package com.createstats.client;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.SourceFamilyDefinition;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class PanelCapabilities {
    public enum ChannelKind {
        CONTINUOUS("Continuous"),

        BOUNDED("Bounded"),

        DISCRETE("Stepped"),

        BOOLEAN("State"),

        ANGLE("Angle"),

        TEXT("Text"),

        GENERIC("Value");

        private final String label;

        ChannelKind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }
    }

    private PanelCapabilities() {}

    public static ChannelKind classify(StatsDisplayPanelBE.DataChannel channel) {
        if (channel == null) {
            return ChannelKind.GENERIC;
        }
        final TimeSeriesStore store = channel.getStore();
        final boolean hasSamples = store != null && store.getSampleCount() > 0;

        final String text = channel.getLastText();
        if (text != null && !text.isEmpty() && !hasSamples) {
            return ChannelKind.TEXT;
        }
        return classify(store != null ? store.getMetricType() : null);
    }

    public static ChannelKind classify(MetricType type) {
        if (type == null) {
            return ChannelKind.GENERIC;
        }
        switch (type) {
            case BOOLEAN_FLAG:
                return ChannelKind.BOOLEAN;
            case ANGLE_DEGREES:
                return ChannelKind.ANGLE;
            case REDSTONE_POWER:
            case HEAT_LEVEL:
            case ENGINE_COUNT:
                return ChannelKind.DISCRETE;
            case STRESS_PERCENT:
            case FILL_LEVEL:
            case EFFICIENCY_SCORE:
                return ChannelKind.BOUNDED;
            case GENERIC:
                return ChannelKind.GENERIC;
            default:
                return ChannelKind.CONTINUOUS;
        }
    }

    public static boolean supportsManualYRange(ChannelKind kind) {
        return kind == ChannelKind.CONTINUOUS || kind == ChannelKind.GENERIC;
    }

    public static boolean supportsPrediction(ChannelKind kind) {
        return kind == ChannelKind.CONTINUOUS;
    }

    public static boolean supportsSmoothLine(ChannelKind kind) {
        return kind == ChannelKind.CONTINUOUS || kind == ChannelKind.BOUNDED || kind == ChannelKind.GENERIC;
    }

    public static boolean supportsThreshold(ChannelKind kind) {
        return kind != ChannelKind.TEXT;
    }

    public static final class SourceCapabilities {
        private final SourceFamilyDefinition family;
        private final Set<ChannelKind> kinds;
        private final int channelCount;

        SourceCapabilities(SourceFamilyDefinition family, Set<ChannelKind> kinds, int channelCount) {
            this.family = family;
            this.kinds = kinds;
            this.channelCount = channelCount;
        }

        public SourceFamilyDefinition getFamily() {
            return this.family;
        }

        public int getChannelCount() {
            return this.channelCount;
        }

        public boolean has(ChannelKind kind) {
            return this.kinds.contains(kind);
        }

        public boolean hasAnyNumeric() {
            return this.has(ChannelKind.CONTINUOUS) || this.has(ChannelKind.BOUNDED)
                || this.has(ChannelKind.DISCRETE) || this.has(ChannelKind.ANGLE)
                || this.has(ChannelKind.GENERIC);
        }

        public boolean isMultiMetric() {
            return this.channelCount > 1;
        }

        public boolean isTextOnly() {
            return this.channelCount > 0 && this.kinds.size() == 1 && this.has(ChannelKind.TEXT);
        }
    }

    public static SourceCapabilities resolve(SourceFamilyDefinition family,
                                             List<StatsDisplayPanelBE.DataChannel> channels) {
        final EnumSet<ChannelKind> kinds = EnumSet.noneOf(ChannelKind.class);
        int count = 0;
        if (channels != null) {
            for (final StatsDisplayPanelBE.DataChannel ch : channels) {
                if (ch == null) {
                    continue;
                }
                kinds.add(classify(ch));
                ++count;
            }
        }
        return new SourceCapabilities(family, kinds, count);
    }
}
