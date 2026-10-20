package com.createstats.client;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SpecializedHudWidgetCatalog;
import com.createstats.display.StressometerHudState;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.train.TrainMapData;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class StressometerHudDataResolver {
    private static final String AERO_FLIGHT_DECK_SOURCE_ID = "aero_flight_deck";
    private static final String AEROLINE_DIRECTOR_SOURCE_ID = "aeroline_director";

    private StressometerHudDataResolver() {
    }

    public static StressometerHudReadout resolve(StatsDisplayPanelBE panel, StressometerHudState state, boolean allowSampleFallback) {
        if (panel == null || state == null) {
            return allowSampleFallback ? StressometerHudReadout.sample() : empty(StressometerHudReadout.Status.NO_SOURCE);
        }

        if (state.getBindingMode() == StressometerHudState.SourceBindingMode.FIXED
                && state.resolvePreferredSourceId(panel.getSourceRegistry()) == null) {
            return empty(StressometerHudReadout.Status.SOURCE_MISSING);
        }
        final SourceConfig source = selectSource(panel, state);
        if (source == null) {
            return allowSampleFallback ? StressometerHudReadout.sample() : empty(StressometerHudReadout.Status.NO_SOURCE);
        }

        final List<StatsDisplayPanelBE.DataChannel> channels = channelsForSource(panel, source.getSourceId());
        final StatsDisplayPanelBE.DataChannel stressPctCh = findMetric(channels, "stress_pct", "Stress %");
        final StatsDisplayPanelBE.DataChannel stressSuCh = findMetric(channels, "stress_su", "Current Stress (SU)");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(channels, "capacity_su", "Capacity (SU)");
        final StatsDisplayPanelBE.DataChannel remainingCh = findMetric(channels, "remaining_su", "Remaining (SU)");
        final StatsDisplayPanelBE.DataChannel rpmCh = findMetric(channels, "rpm", "RPM");
        final StatsDisplayPanelBE.DataChannel overstressedCh = findMetric(channels, "overstressed", "Overstressed");
        if (stressPctCh == null && (stressSuCh == null || capacityCh == null)) {
            return allowSampleFallback ? StressometerHudReadout.sample() : empty(StressometerHudReadout.Status.NO_DATA);
        }

        final float used = valueOr(stressSuCh, 0.0f);
        final float capacity = Math.max(1.0f, valueOr(capacityCh, Math.max(used, 1.0f)));
        final float pct = (stressPctCh != null) ? valueOr(stressPctCh, 0.0f) : (used / capacity * 100.0f);
        final float remaining = (remainingCh != null) ? valueOr(remainingCh, Math.max(0.0f, capacity - used)) : Math.max(0.0f, capacity - used);
        final float rpm = valueOr(rpmCh, 0.0f);
        final boolean overstressed = valueOr(overstressedCh, 0.0f) > 0.5f || pct >= 100.0f;
        return new StressometerHudReadout(StressometerHudReadout.Status.OK, source.getDisplayLabel(), used, capacity, pct, remaining, rpm, overstressed, animationSlotFor(source.getSourceId()));
    }

    public static HudSourceWidgetReadout resolveSourceWidget(StatsDisplayPanelBE panel, StressometerHudState.PartWidget widget, boolean allowSampleFallback) {
        if (panel == null || widget == null) {
            final String title = widget != null ? widget.getPart().label() : "HUD Widget";
            final String detail = widget != null && !widget.getSourceType().isEmpty() ? "Expected: " + widget.getSourceType() : "No linked panel";
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_SOURCE, title, "Missing", "NO SOURCE", detail, 0xFFFFB84A);
        }
        if (widget.getPart() == StressometerHudState.Part.TRAIN_INFO) {
            return resolveTrainWidget(panel, widget.getPart(), allowSampleFallback);
        }
        if (widget.getPart() == StressometerHudState.Part.GRADE) {
            return resolveGradeWidget(panel, allowSampleFallback);
        }
        if (widget.getPart() == StressometerHudState.Part.NETWORK_EFFICIENCY) {
            return resolveEfficiencyWidget(panel, allowSampleFallback);
        }

        final SourceConfig source = selectSourceForWidget(panel, widget);
        final SpecializedHudWidgetCatalog.Definition definition = source != null
                ? SpecializedHudWidgetCatalog.definitionFor(source.getSourceType(), widget.getPart())
                : null;
        if (source == null) {
            final String title = widget.getMetricLabel().isEmpty() ? widget.getPart().label() : widget.getMetricLabel();
            final String detail = widget.getSourceType().isEmpty() ? "Source missing" : "Expected: " + widget.getSourceType();
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_SOURCE, title, "Source missing", "NO SOURCE", detail, 0xFFFFB84A);
        }

        final HudSourceWidgetReadout linkedReadout = resolveLinkedSourceWidget(panel, source, widget);
        if (linkedReadout != null) {
            return linkedReadout;
        }

        final List<StatsDisplayPanelBE.DataChannel> channels = channelsForSource(panel, source.getSourceId());
        final StatsDisplayPanelBE.DataChannel channel = selectMetricChannel(channels, source.getSourceType(), definition, widget.getMetricKey());
        if (channel == null) {
            final String title = definition != null ? definition.label() : widget.getPart().label();
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_DATA, title, source.getSourceType(), "NO DATA", "Waiting for " + source.getDisplayLabel(), 0xFFFFB84A);
        }
        final String value = valueText(channel);
        final String title = definition != null ? definition.label() : widget.getPart().label();
        final String detail = buildDetail(channels, definition, channel, source.getDisplayLabel());
        return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, title, source.getDisplayLabel(), value, detail, colorFor(source.getSourceType(), channel.getLabel(), valueOr(channel, 0.0f)));
    }

    private static StressometerHudReadout empty(StressometerHudReadout.Status status) {
        return new StressometerHudReadout(status, "", 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, false, 0);
    }

    private static SourceConfig selectSource(StatsDisplayPanelBE panel, StressometerHudState state) {
        final Map<String, SourceConfig> registry = panel.getSourceRegistry();
        if (registry == null || registry.isEmpty()) {
            return null;
        }
        final String preferredSourceId = state.resolvePreferredSourceId(registry);
        if (preferredSourceId != null && !preferredSourceId.isEmpty()) {
            final SourceConfig preferred = registry.get(preferredSourceId);
            if (isStressometer(preferred)) {
                return preferred;
            }
        }
        SourceConfig firstStressometer = null;
        for (SourceConfig source : registry.values()) {
            if (!isStressometer(source)) {
                continue;
            }
            if (firstStressometer == null) {
                firstStressometer = source;
            }
            if (hasStressometerData(panel, source.getSourceId())) {
                return source;
            }
        }
        return firstStressometer;
    }

    private static boolean isStressometer(SourceConfig source) {
        return source != null && "Stressometer".equals(source.getSourceType());
    }

    private static boolean hasStressometerData(StatsDisplayPanelBE panel, String sourceId) {
        final List<StatsDisplayPanelBE.DataChannel> channels = channelsForSource(panel, sourceId);
        return findMetric(channels, "stress_pct", "Stress %") != null
                || (findMetric(channels, "stress_su", "Current Stress (SU)") != null
                && findMetric(channels, "capacity_su", "Capacity (SU)") != null);
    }

    public static List<StatsDisplayPanelBE.DataChannel> channelsForSource(StatsDisplayPanelBE panel, String sourceId) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        if (panel == null || sourceId == null || sourceId.isEmpty()) {
            return channels;
        }
        if (isAeroCompositeSourceId(sourceId)) {
            return channelsForAeroComposite(panel);
        }
        final String prefix = "src_" + sourceId + "_";
        final Map<String, StatsDisplayPanelBE.DataChannel> allChannels = panel.getAllChannels();
        if (allChannels == null || allChannels.isEmpty()) {
            return channels;
        }
        for (StatsDisplayPanelBE.DataChannel channel : allChannels.values()) {
            if (channel != null
                    && channel.getId() != null
                    && channel.getId().startsWith(prefix)
                    && ((channel.getStore() != null && channel.getStore().getSampleCount() > 0)
                    || (channel.getLastText() != null && !channel.getLastText().isEmpty()))) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private static List<StatsDisplayPanelBE.DataChannel> channelsForAeroComposite(StatsDisplayPanelBE panel) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        final Map<String, SourceConfig> registry = panel.getSourceRegistry();
        final Map<String, StatsDisplayPanelBE.DataChannel> allChannels = panel.getAllChannels();
        if (registry == null || registry.isEmpty() || allChannels == null || allChannels.isEmpty()) {
            return channels;
        }
        for (SourceConfig source : registry.values()) {
            if (source == null || !isAeroFeedSourceType(source.getSourceType())) {
                continue;
            }
            final String prefix = "src_" + source.getSourceId() + "_";
            for (StatsDisplayPanelBE.DataChannel channel : allChannels.values()) {
                if (channel != null
                        && channel.getId() != null
                        && channel.getId().startsWith(prefix)
                        && !panel.getSettings().isChannelHidden(channel.getId())
                        && ((channel.getStore() != null && channel.getStore().getSampleCount() > 0)
                        || (channel.getLastText() != null && !channel.getLastText().isEmpty()))) {
                    channels.add(channel);
                }
            }
        }
        return channels;
    }

    static SourceConfig resolveWidgetSource(StatsDisplayPanelBE panel, StressometerHudState.PartWidget widget) {
        return panel == null || widget == null ? null : selectSourceForWidget(panel, widget);
    }

    static StatsDisplayPanelBE.DataChannel findWidgetChannel(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        if (channels == null || channels.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            final StatsDisplayPanelBE.DataChannel byMetric = findMetric(channels, key, key);
            if (byMetric != null) {
                return byMetric;
            }
        }
        return null;
    }

    static float latestValue(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        return valueOr(channel, fallback);
    }

    public static boolean hasSourceWidgetData(StatsDisplayPanelBE panel, SourceConfig source) {
        if (panel == null || source == null) {
            return false;
        }
        if (!channelsForSource(panel, source.getSourceId()).isEmpty()) {
            return true;
        }
        return switch (source.getSourceType()) {
            case "Train Station" -> findTrainStationLink(panel, source) != null;
            default -> false;
        };
    }

    private static boolean isAeroCompositeSourceId(String sourceId) {
        return AERO_FLIGHT_DECK_SOURCE_ID.equals(sourceId) || AEROLINE_DIRECTOR_SOURCE_ID.equals(sourceId);
    }

    private static boolean isAeroCompositeSourceType(String sourceType) {
        return "Aero Flight Deck".equals(sourceType) || "Aeroline Director".equals(sourceType);
    }

    private static boolean isAeroFeedSourceType(String sourceType) {
        return "Portable Engine".equals(sourceType)
                || "Diesel Engine".equals(sourceType)
                || "Modular Diesel Engine".equals(sourceType)
                || "Huge Diesel Engine".equals(sourceType)
                || "Diesel Engine Group".equals(sourceType)
                || "Velocity Sensor".equals(sourceType)
                || "Gimbal Sensor".equals(sourceType)
                || "Altitude Sensor".equals(sourceType)
                || "Docking Connector".equals(sourceType)
                || "Steering Wheel".equals(sourceType)
                || "Laser Sensor".equals(sourceType)
                || "Optical Sensor".equals(sourceType)
                || "Navigation Table".equals(sourceType)
                || "Linked Typewriter".equals(sourceType)
                || "Hot Air Burner".equals(sourceType)
                || "Steam Vent".equals(sourceType)
                || "Contraption Info".equals(sourceType);
    }

    private static final org.slf4j.Logger METRIC_LOG = com.mojang.logging.LogUtils.getLogger();
    private static final java.util.Set<String> LEGACY_FUZZY_LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    static StatsDisplayPanelBE.DataChannel findMetricExact(List<StatsDisplayPanelBE.DataChannel> channels, String metricId, String label) {
        if (channels == null || channels.isEmpty() || metricId == null || metricId.isEmpty()) {
            return null;
        }
        final String idLower = metricId.toLowerCase(java.util.Locale.ROOT);
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel == null || channel.getId() == null) continue;
            final String chId = channel.getId().toLowerCase(java.util.Locale.ROOT);
            if (chId.equals(idLower) || chId.endsWith("_" + idLower)) {
                return channel;
            }
        }
        if (label != null && !label.isEmpty()) {
            for (StatsDisplayPanelBE.DataChannel channel : channels) {
                if (channel != null && channel.getLabel() != null && label.equalsIgnoreCase(channel.getLabel())) {
                    return channel;
                }
            }
        }
        return null;
    }

    static StatsDisplayPanelBE.DataChannel findMetricLegacy(List<StatsDisplayPanelBE.DataChannel> channels, String metricId) {
        if (channels == null || channels.isEmpty() || metricId == null || metricId.isEmpty()) {
            return null;
        }
        final String normalized = metricId.replace("_", " ").toLowerCase(java.util.Locale.ROOT);
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel != null && channel.getLabel() != null && channel.getLabel().toLowerCase(java.util.Locale.ROOT).contains(normalized)) {
                final String chId = channel.getId() != null ? channel.getId() : "?";
                final String key = metricId + "->" + chId;
                if (LEGACY_FUZZY_LOGGED.add(key)) {
                    METRIC_LOG.debug("[create_stats] HUD legacy fuzzy metric match: '{}' → channel '{}' (label '{}')", metricId, chId, channel.getLabel());
                }
                return channel;
            }
        }
        return null;
    }

    private static StatsDisplayPanelBE.DataChannel findMetric(List<StatsDisplayPanelBE.DataChannel> channels, String metricId, String label) {
        final StatsDisplayPanelBE.DataChannel exact = findMetricExact(channels, metricId, label);
        if (exact != null) return exact;
        return findMetricLegacy(channels, metricId);
    }

    private static float valueOr(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore() == null || channel.getStore().getSampleCount() <= 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
    }

    private static int animationSlotFor(String sourceId) {
        return sourceId != null ? Math.floorMod(sourceId.hashCode(), 1024) : 0;
    }

    private static SourceConfig selectSourceForWidget(StatsDisplayPanelBE panel, StressometerHudState.PartWidget widget) {
        final Map<String, SourceConfig> registry = panel.getSourceRegistry();
        if (widget != null && (isAeroCompositeSourceId(widget.getSourceId()) || isAeroCompositeSourceType(widget.getSourceType()))) {
            return syntheticAeroCompositeSource(panel, widget.getSourceType().isEmpty() ? "Aero Flight Deck" : widget.getSourceType(), widget.getSourceId());
        }
        if (registry == null || registry.isEmpty()) {
            return null;
        }
        if (!widget.getSourceId().isEmpty()) {
            final SourceConfig exact = registry.get(widget.getSourceId());
            if (exact != null) {
                return exact;
            }
        }
        SourceConfig typeMatch = null;
        for (SourceConfig source : registry.values()) {
            if (source == null) {
                continue;
            }
            if (widget.getSourcePos() != Long.MIN_VALUE && source.getSourcePos() != null && source.getSourcePos().asLong() == widget.getSourcePos()) {
                return source;
            }
            if (!widget.getSourceType().isEmpty() && widget.getSourceType().equals(source.getSourceType())) {
                if (typeMatch != null) {
                    return null;
                }
                typeMatch = source;
            }
        }
        return typeMatch;
    }

    private static SourceConfig syntheticAeroCompositeSource(StatsDisplayPanelBE panel, String sourceType, String sourceId) {
        final String type = "Aeroline Director".equals(sourceType) ? "Aeroline Director" : "Aero Flight Deck";
        final String id = AEROLINE_DIRECTOR_SOURCE_ID.equals(sourceId) || "Aeroline Director".equals(type)
                ? AEROLINE_DIRECTOR_SOURCE_ID
                : AERO_FLIGHT_DECK_SOURCE_ID;
        final SourceConfig source = new SourceConfig(id, panel != null && panel.getBlockPos() != null ? panel.getBlockPos() : BlockPos.ZERO, type);
        source.setUserLabel(type);
        return source;
    }

    private static HudSourceWidgetReadout resolveTrainWidget(StatsDisplayPanelBE panel, StressometerHudState.Part part, boolean allowSampleFallback) {
        final TrainMapData trainMapData = panel.getTrainMapData();
        if (trainMapData == null || trainMapData.getTrains().isEmpty()) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_DATA, part.label(), "No trains", "NO DATA", "No train map data", 0xFFFFB84A);
        }
        TrainMapData.TrainEntry selected = trainMapData.getTrains().get(0);
        for (TrainMapData.TrainEntry train : trainMapData.getTrains()) {
            if (Math.abs(train.speed) > Math.abs(selected.speed)) {
                selected = train;
            }
        }
        final String status = selected.waitingForSignal ? "Signal wait" : (selected.stopped ? "Stopped" : "Moving");
        final String destination = selected.nextDestination == null || selected.nextDestination.isEmpty() ? selected.currentStationName : selected.nextDestination;
        if (part == StressometerHudState.Part.TRAIN_STATION_STATUS) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, "Station Status", status, selected.currentStationName == null || selected.currentStationName.isEmpty() ? "No station" : selected.currentStationName, selected.name, 0xFF33E07A);
        }
        if (part == StressometerHudState.Part.SIGNAL_WAIT) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, "Signal Wait", status, selected.waitingForSignal ? (selected.ticksWaitingForSignal + " ticks") : "CLEAR", selected.name, selected.waitingForSignal ? 0xFFFFB84A : 0xFF33E07A);
        }
        if (part == StressometerHudState.Part.SCHEDULE_DESTINATION) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, "Schedule / Destination", status, destination == null || destination.isEmpty() ? "No destination" : destination, selected.scheduleStops.isEmpty() ? selected.name : String.join(" > ", selected.scheduleStops.subList(0, Math.min(3, selected.scheduleStops.size()))), 0xFF33E07A);
        }
        return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, part.label(), status, selected.name, String.format(java.util.Locale.ROOT, "%.1f spd -> %s", selected.speed, destination == null || destination.isEmpty() ? "No destination" : destination), 0xFF33E07A);
    }

    private static HudSourceWidgetReadout resolveLinkedSourceWidget(StatsDisplayPanelBE panel, SourceConfig source, StressometerHudState.PartWidget widget) {
        if ("Train Station".equals(source.getSourceType())) {
            return resolveTrainStationLinkWidget(panel, source, widget.getPart());
        }
        return null;
    }

    private static HudSourceWidgetReadout resolveTrainStationLinkWidget(StatsDisplayPanelBE panel, SourceConfig source, StressometerHudState.Part part) {
        final StatsDisplayPanelBE.TrainStationLink link = findTrainStationLink(panel, source);
        if (link == null) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_DATA, part.label(), source.getDisplayLabel(), "NO DATA", "Waiting for station link data", 0xFFFFB84A);
        }
        final String stationName = link.getStationName() == null || link.getStationName().isEmpty() ? source.getDisplayLabel() : link.getStationName();
        return switch (part) {
            case TRAIN_STATION_STATUS -> new HudSourceWidgetReadout(
                    HudSourceWidgetReadout.Status.OK,
                    "Station Status",
                    stationName,
                    link.isAssembling() ? "ASSEMBLING" : (link.getCurrentTrainName().isEmpty() ? "IDLE" : "ACTIVE"),
                    link.getSummaryStatus(),
                    link.isAssembling() ? 0xFFFFB84A : 0xFF33E07A
            );
            case INBOUND_TRAINS -> new HudSourceWidgetReadout(
                    HudSourceWidgetReadout.Status.OK,
                    "Inbound Trains",
                    stationName,
                    link.getNextTrainName().isEmpty() ? "NO INBOUND" : link.getNextTrainName(),
                    buildInboundDetail(link),
                    link.isNextTrainWaitingForSignal() ? 0xFFFFB84A : 0xFF33E07A
            );
            case SCHEDULE_DESTINATION -> new HudSourceWidgetReadout(
                    HudSourceWidgetReadout.Status.OK,
                    "Schedule / Destination",
                    stationName,
                    link.getCurrentTrainName().isEmpty() ? "NO DOCKED TRAIN" : link.getCurrentTrainName(),
                    link.getCurrentCargoSummary(),
                    0xFF33E07A
            );
            case SIGNAL_WAIT -> new HudSourceWidgetReadout(
                    HudSourceWidgetReadout.Status.OK,
                    "Signal Wait",
                    stationName,
                    link.isNextTrainWaitingForSignal() ? "WAITING" : "CLEAR",
                    buildSignalDetail(link),
                    link.isNextTrainWaitingForSignal() ? 0xFFFFB84A : 0xFF33E07A
            );
            default -> null;
        };
    }

    private static String buildInboundDetail(StatsDisplayPanelBE.TrainStationLink link) {
        final ArrayList<String> parts = new ArrayList<String>();
        if (link.getNextTrainEtaSeconds() >= 0) {
            final int eta = link.getNextTrainEtaSeconds();
            parts.add("ETA " + (eta >= 60 ? (eta / 60) + "m " + (eta % 60) + "s" : eta + "s"));
        }
        if (link.getNextTrainDistanceBlocks() >= 0) {
            parts.add(link.getNextTrainDistanceBlocks() + "m away");
        }
        if (!link.getNextCargoSummary().isEmpty()) {
            parts.add(link.getNextCargoSummary());
        }
        return parts.isEmpty() ? link.getSummaryStatus() : String.join(" • ", parts);
    }

    private static String buildSignalDetail(StatsDisplayPanelBE.TrainStationLink link) {
        if (!link.getNextTrainName().isEmpty()) {
            return link.getNextTrainName();
        }
        return link.getSummaryStatus();
    }

    private static StatsDisplayPanelBE.TrainStationLink findTrainStationLink(StatsDisplayPanelBE panel, SourceConfig source) {
        if (panel == null || source == null) {
            return null;
        }
        for (StatsDisplayPanelBE.TrainStationLink link : panel.getTrainStationLinks()) {
            if (link == null) {
                continue;
            }
            if (source.getSourcePos() != null && source.getSourcePos().equals(link.getSourcePos())) {
                return link;
            }
            if (source.getSourceId() != null && source.getSourceId().equals(link.getId())) {
                return link;
            }
        }
        return null;
    }

    private static HudSourceWidgetReadout resolveGradeWidget(StatsDisplayPanelBE panel, boolean allowSampleFallback) {
        final EfficiencyScore score = panel.getLatestEfficiencyScore();
        if (score == null || "?".equals(score.grade())) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_DATA, "Network Grade", "Warming up", "NO DATA", "No efficiency score yet", 0xFFFFB84A);
        }
        return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, "Network Grade", "Network", score.grade(), String.format(java.util.Locale.ROOT, "%.1f%% efficiency", score.compositeScore() * 100.0f), score.gradeColor());
    }

    private static HudSourceWidgetReadout resolveEfficiencyWidget(StatsDisplayPanelBE panel, boolean allowSampleFallback) {
        final EfficiencyScore score = panel.getLatestEfficiencyScore();
        if (score == null || "?".equals(score.grade())) {
            return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.NO_DATA, "Network Efficiency", "Warming up", "NO DATA", "No efficiency score yet", 0xFFFFB84A);
        }
        return new HudSourceWidgetReadout(HudSourceWidgetReadout.Status.OK, "Network Efficiency", score.grade(), String.format(java.util.Locale.ROOT, "%.1f%%", score.compositeScore() * 100.0f), "Composite score", score.gradeColor());
    }

    private static StatsDisplayPanelBE.DataChannel selectMetricChannel(List<StatsDisplayPanelBE.DataChannel> channels, String sourceType, SpecializedHudWidgetCatalog.Definition definition, String metricKey) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        final LinkedHashSet<String> candidateKeys = new LinkedHashSet<String>();
        if (metricKey != null && !metricKey.isEmpty() && !"auto".equals(metricKey)) {
            candidateKeys.add(metricKey);
        }
        if (definition != null) {
            candidateKeys.add(definition.primaryKey());
            for (String key : definition.keys()) {
                candidateKeys.add(key);
            }
        }
        // exact matches first
        for (String key : candidateKeys) {
            if (key == null || key.isEmpty()) continue;
            final StatsDisplayPanelBE.DataChannel exact = findMetricExact(channels, key, key);
            if (exact != null) {
                return exact;
            }
        }
        // fuzzy only after every exact id failed
        for (String key : candidateKeys) {
            if (key == null || key.isEmpty()) continue;
            final StatsDisplayPanelBE.DataChannel legacy = findMetricLegacy(channels, key);
            if (legacy != null) {
                return legacy;
            }
        }
        if ("Tunnel".equals(sourceType)) {
            StatsDisplayPanelBE.DataChannel singleLiveChannel = null;
            int liveChannels = 0;
            for (StatsDisplayPanelBE.DataChannel channel : channels) {
                if (channel != null && channel.getStore() != null && channel.getStore().getSampleCount() > 0) {
                    singleLiveChannel = channel;
                    ++liveChannels;
                }
            }
            if (liveChannels == 1) {
                return singleLiveChannel;
            }
        }
        return null;
    }

    private static String buildDetail(List<StatsDisplayPanelBE.DataChannel> channels, SpecializedHudWidgetCatalog.Definition definition, StatsDisplayPanelBE.DataChannel primary, String fallback) {
        if (definition == null || definition.keys().length <= 1) {
            return fallback;
        }
        final StringBuilder detail = new StringBuilder();
        for (String key : definition.keys()) {
            final StatsDisplayPanelBE.DataChannel channel = findMetric(channels, key, key);
            if (channel == null || channel == primary) {
                continue;
            }
            if (!detail.isEmpty()) {
                detail.append("  ");
            }
            detail.append(shortLabel(channel, key)).append(" ").append(valueText(channel));
        }
        return detail.isEmpty() ? fallback : detail.toString();
    }

    private static String shortLabel(StatsDisplayPanelBE.DataChannel channel, String fallback) {
        final String label = channel.getLabel();
        if (label != null && !label.isEmpty()) {
            final int colon = label.indexOf(':');
            final String clean = colon >= 0 ? label.substring(0, colon) : label;
            return clean.length() > 12 ? clean.substring(0, 12) : clean;
        }
        return fallback.length() > 12 ? fallback.substring(0, 12) : fallback;
    }

    private static String valueText(StatsDisplayPanelBE.DataChannel channel) {
        if (channel.getLastText() != null && !channel.getLastText().isEmpty() && (channel.getStore() == null || channel.getStore().getSampleCount() <= 0)) {
            return channel.getLastText();
        }
        final float value = valueOr(channel, 0.0f);
        if (channel.getStore() != null && channel.getStore().getSampleCount() > 0 && channel.getStore().getMetricType() != null) {
            return channel.getStore().getMetricType().formatValue(value);
        }
        final String label = channel.getLabel() != null ? channel.getLabel().toLowerCase(java.util.Locale.ROOT) : "";
        final String id = channel.getId() != null ? channel.getId().toLowerCase(java.util.Locale.ROOT) : "";
        if (label.contains("%") || id.contains("pct") || id.contains("fill") || id.contains("gauge")) {
            return String.format(java.util.Locale.ROOT, "%.1f%%", value);
        }
        if (id.contains("powered") || id.contains("running") || id.contains("overstressed") || label.contains("redstone")) {
            return value > 0.5f ? "ON" : "OFF";
        }
        if (Math.abs(value) >= 1000.0f) {
            return String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0f);
        }
        if (Math.abs(value) >= 100.0f) {
            return String.format(java.util.Locale.ROOT, "%.0f", value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static int colorFor(String sourceType, String label, float value) {
        final String text = ((sourceType != null ? sourceType : "") + " " + (label != null ? label : "")).toLowerCase(java.util.Locale.ROOT);
        if ((text.contains("stress") || text.contains("fill") || text.contains("heat")) && value >= 85.0f) {
            return value >= 100.0f ? 0xFFFF4D4D : 0xFFFFB84A;
        }
        if (text.contains("redstone") || text.contains("powered") || text.contains("running")) {
            return value > 0.5f ? 0xFF33E07A : 0xFFFFB84A;
        }
        return 0xFF33E07A;
    }
}
