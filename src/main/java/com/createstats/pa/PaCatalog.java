package com.createstats.pa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.AnnunciatorBE;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatsDisplayPanelBE.DataChannel;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.InstrumentSourceHelper.MetricSample;
import com.createstats.util.InstrumentSourceHelper.SourceKind;
import com.createstats.util.InstrumentSourceHelper.SourceReadout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PaCatalog {
    private PaCatalog() {
    }

    public enum RowKind {
        TARGET,

        GROUP,

        TOPIC,
    }

    public static final int FLAG_ARMED = 1 << 0;

    public static final int FLAG_TRIPPED = 1 << 1;

    public static final int FLAG_VIA_CABLE = 1 << 2;

    public static final int FLAG_ABOVE = 1 << 3;

    public static final int FLAG_HAS_VALUE = 1 << 4;

    public static final int FLAG_INHERITED = 1 << 5;

    public record Row(String key, String parentKey, int depth, RowKind kind, String title,
                      String subtitle, String subjectClip, String measureClip, MetricType type,
                      float value, int flags, float threshold, DeviceId id, int distance) {
        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public boolean bindable() {
            return this.kind == RowKind.TOPIC;
        }
    }

    private static String groupKey(BlockPos at, String tail) {
        return "#" + at.asLong() + "/" + tail;
    }

    public static List<Row> build(ServerLevel level, BlockPos centre, int radius,
                                  java.util.Set<Long> circuit) {
        final List<Row> out = new ArrayList<>();
        final DeviceIdRegistry ids = DeviceIdRegistry.get(level);
        for (PaReach.Found found : PaReach.scan(level, centre, radius, circuit)) {
            switch (found.kind()) {
                case DISPLAY -> collectDisplay(level, ids, found, out);
                case ANNUNCIATOR -> collectAnnunciator(level, ids, found, out);
                case ALARM -> collectAlarm(level, ids, found, out);
            }
        }
        return out;
    }

    private static void collectDisplay(ServerLevel level, DeviceIdRegistry ids, PaReach.Found found,
                                       List<Row> out) {
        final StatsDisplayPanelBE panel = (StatsDisplayPanelBE) found.be();
        final Map<String, List<DataChannel>> bySource = panel.getChannelsBySource();
        if (bySource.isEmpty()) {
            return;
        }
        final Map<String, SourceConfig> registry = panel.getSourceRegistry();
        final BlockPos at = found.pos();
        final DeviceId panelId = ids.idFor(at, panel.getBlockState());
        final String targetKey = groupKey(at, "t");

        int channels = 0;
        for (List<DataChannel> list : bySource.values()) {
            channels += list.size();
        }
        out.add(new Row(targetKey, "", 0, RowKind.TARGET,
                name(panel) + (panelId.isPresent() ? " " + panelId.label() : ""),
                describeReach(found) + " · " + bySource.size() + " feeding it · " + channels
                        + " readings",
                Lexicon.PANEL, Lexicon.LEVEL, MetricType.GENERIC, 0.0f,
                found.viaCable() ? FLAG_VIA_CABLE : 0, 0.0f, panelId,
                (int) Math.round(found.distance())));

        final Map<String, List<String>> byType = new LinkedHashMap<>();
        for (String sourceId : bySource.keySet()) {
            byType.computeIfAbsent(typeOf(registry, sourceId, panel), k -> new ArrayList<>())
                    .add(sourceId);
        }

        for (Map.Entry<String, List<String>> entry : byType.entrySet()) {
            final List<String> sources = entry.getValue();
            final String typeKey = groupKey(at, "y/" + entry.getKey());
            final boolean single = sources.size() == 1;

            if (!single) {
                out.add(new Row(typeKey, targetKey, 1, RowKind.GROUP, entry.getKey(),
                        sources.size() + " of them", Lexicon.MACHINE, Lexicon.LEVEL,
                        MetricType.GENERIC, 0.0f, 0, 0.0f, DeviceId.NONE,
                        (int) Math.round(found.distance())));
            }

            for (String sourceId : sources) {
                final SourceConfig config = registry.get(sourceId);
                final List<DataChannel> list = bySource.get(sourceId);
                final BlockPos sourcePos = config == null ? at : config.getSourcePos();
                final DeviceId sourceIdNum = level.isLoaded(sourcePos)
                        ? ids.idFor(sourcePos, level.getBlockState(sourcePos)) : DeviceId.NONE;
                final String subject = subjectFor(level, sourcePos);

                final String sourceKey = single ? typeKey : groupKey(at, "s/" + sourceId);
                final int depth = single ? 1 : 2;

                out.add(new Row(sourceKey, single ? targetKey : typeKey, depth, RowKind.GROUP,
                        sourceTitle(config, entry.getKey(), sourceIdNum),
                        list.size() + (list.size() == 1 ? " reading" : " readings"),
                        subject, Lexicon.LEVEL, MetricType.GENERIC, 0.0f, 0, 0.0f, sourceIdNum,
                        (int) Math.round(found.distance())));

                for (DataChannel channel : list) {
                    out.add(topicOf(at, sourceKey, depth + 1, channel, subject, sourceIdNum,
                            (int) Math.round(found.distance())));
                }
            }
        }
    }

    private static Row topicOf(BlockPos panel, String parentKey, int depth, DataChannel channel,
                               String subject, DeviceId id, int distance) {
        final TimeSeriesStore store = channel.getStore();
        final MetricType type = store == null ? MetricType.GENERIC : store.getMetricType();
        final float value = store == null || store.getSampleCount() == 0 ? 0.0f
                : store.getLatestValue();
        int flags = FLAG_HAS_VALUE;
        if (channel.isAlertEnabled()) {
            flags |= FLAG_ARMED;
            if (channel.isAlertAbove()) {
                flags |= FLAG_ABOVE;
            }
            if (channel.isAlertTriggered()) {
                flags |= FLAG_TRIPPED;
            }
        }
        final String label = channel.getLabel() == null || channel.getLabel().isEmpty()
                ? channel.getId() : channel.getLabel();
        return new Row(PaBinding.channel(panel, channel.getId()).key(), parentKey, depth,
                RowKind.TOPIC, label,
                channel.isAlertEnabled()
                        ? "alert " + (channel.isAlertAbove() ? "above " : "below ")
                                + type.formatValue(channel.getAlertThreshold())
                        : type.getDisplayName(),
                subject, PaVocabulary.measureForChannel(channel.getId(), type), type, value, flags,
                channel.getAlertThreshold(), id, distance);
    }

    private static void collectAnnunciator(ServerLevel level, DeviceIdRegistry ids,
                                           PaReach.Found found, List<Row> out) {
        final AnnunciatorBE device = (AnnunciatorBE) found.be();
        final BlockPos at = found.pos();
        final DeviceId id = ids.idFor(at, device.getBlockState());
        final String targetKey = groupKey(at, "t");
        final int rules = (int) device.getRules().stream().filter(r -> r.isLive()).count();
        final boolean alarming = device.getSeverity().isAlarming();

        out.add(new Row(targetKey, "", 0, RowKind.TARGET,
                name(device) + (id.isPresent() ? " " + id.label() : ""),
                describeReach(found) + " · " + rules + (rules == 1 ? " rule" : " rules")
                        + (alarming ? " · " + device.getFaultCount() + " firing" : ""),
                Lexicon.ALARM, Lexicon.LEVEL, MetricType.GENERIC, 0.0f,
                (found.viaCable() ? FLAG_VIA_CABLE : 0) | (alarming ? FLAG_TRIPPED : 0), 0.0f, id,
                (int) Math.round(found.distance())));

        out.add(new Row(PaBinding.device(at).key(), targetKey, 1, RowKind.TOPIC,
                "Everything it decides",
                rules == 0 ? "no rules set on it yet"
                        : "inherits its thresholds and hysteresis",
                Lexicon.ALARM, Lexicon.LEVEL, MetricType.GENERIC, 0.0f,
                FLAG_INHERITED | (rules > 0 ? FLAG_ARMED : 0) | (alarming ? FLAG_TRIPPED : 0),
                0.0f, id, (int) Math.round(found.distance())));
    }

    private static void collectAlarm(ServerLevel level, DeviceIdRegistry ids, PaReach.Found found,
                                     List<Row> out) {
        final StatsAlarmBE alarm = (StatsAlarmBE) found.be();
        final BlockPos at = found.pos();
        final DeviceId id = ids.idFor(at, alarm.getBlockState());
        final String targetKey = groupKey(at, "t");
        final boolean ringing = alarm.isActive();

        out.add(new Row(targetKey, "", 0, RowKind.TARGET,
                name(alarm) + (id.isPresent() ? " " + id.label() : ""),
                describeReach(found) + " · watches " + alarm.getConnectedPanelCount()
                        + " display" + (alarm.getConnectedPanelCount() == 1 ? "" : "s"),
                Lexicon.ALARM, Lexicon.LEVEL, MetricType.GENERIC, 0.0f,
                (found.viaCable() ? FLAG_VIA_CABLE : 0) | (ringing ? FLAG_TRIPPED : 0), 0.0f, id,
                (int) Math.round(found.distance())));

        out.add(new Row(PaBinding.device(at).key(), targetKey, 1, RowKind.TOPIC, "When it rings",
                "inherits whatever the displays it watches decide", Lexicon.ALARM, Lexicon.LEVEL,
                MetricType.GENERIC, 0.0f,
                FLAG_INHERITED | FLAG_ARMED | (ringing ? FLAG_TRIPPED : 0), 0.0f, id,
                (int) Math.round(found.distance())));
    }

    public static MetricSample sample(ServerLevel level, PaBinding binding) {
        if (!level.isLoaded(binding.pos())) {
            return null;
        }
        final BlockEntity be = level.getBlockEntity(binding.pos());
        if (be == null) {
            return null;
        }
        if (binding.kind() == PaBinding.Kind.CHANNEL) {
            if (!(be instanceof StatsDisplayPanelBE panel)) {
                return null;
            }
            final DataChannel channel = panel.getChannel(binding.metricId());
            final TimeSeriesStore store = channel == null ? null : channel.getStore();
            if (store == null || store.getSampleCount() == 0) {
                return null;
            }
            final String label = channel.getLabel() == null || channel.getLabel().isEmpty()
                    ? channel.getId() : channel.getLabel();
            return new MetricSample(channel.getId(), label, store.getLatestValue(),
                    store.getMetricType());
        }
        if (binding.kind() != PaBinding.Kind.METRIC) {
            return null;
        }
        final SourceReadout readout = InstrumentSourceHelper.readSource(be, null);
        return readout == null ? null : readout.metrics().get(binding.metricId());
    }

    public static BlockPos subjectPos(ServerLevel level, PaBinding binding) {
        if (binding.kind() != PaBinding.Kind.CHANNEL || !level.isLoaded(binding.pos())) {
            return binding.pos();
        }
        if (!(level.getBlockEntity(binding.pos()) instanceof StatsDisplayPanelBE panel)) {
            return binding.pos();
        }
        final SourceConfig config = panel.getSourceForChannel(binding.metricId());
        return config == null ? binding.pos() : config.getSourcePos();
    }

    public static DataChannel channelOf(ServerLevel level, PaBinding binding) {
        if (binding.kind() != PaBinding.Kind.CHANNEL || !level.isLoaded(binding.pos())) {
            return null;
        }
        return level.getBlockEntity(binding.pos()) instanceof StatsDisplayPanelBE panel
                ? panel.getChannel(binding.metricId()) : null;
    }

    private static String name(BlockEntity be) {
        return be.getBlockState().getBlock().getName().getString();
    }

    private static String describeReach(PaReach.Found found) {
        return found.viaCable() ? "on your circuit"
                : (int) Math.round(found.distance()) + " blocks away";
    }

    private static String typeOf(Map<String, SourceConfig> registry, String sourceId,
                                 StatsDisplayPanelBE panel) {
        final SourceConfig config = registry.get(sourceId);
        if (config != null && !config.getSourceType().isEmpty()) {
            return config.getSourceType();
        }
        final String fallback = panel.getSourceType();
        return fallback == null || fallback.isEmpty() ? "Direct input" : fallback;
    }

    private static String sourceTitle(SourceConfig config, String type, DeviceId id) {
        if (config == null) {
            return type;
        }
        final String label = config.getUserLabel();
        if (label != null && !label.isEmpty()) {
            return label;
        }
        if (config.hasObservedTarget()) {
            return config.getObservedTargetLabel() + (id.isPresent() ? " " + id.label() : "");
        }
        return type + (id.isPresent() ? " " + id.label() : "");
    }

    private static String subjectFor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Lexicon.MACHINE;
        }
        return PaVocabulary.subjectForBlock(registryPath(level.getBlockState(pos)),
                SourceKind.NO_SOURCE);
    }

    public static String registryPath(BlockState state) {
        if (state == null) {
            return "";
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "" : id.getPath();
    }

    public static SourceKind kindAt(ServerLevel level, BlockPos pos) {
        final BlockEntity be = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
        if (be == null) {
            return SourceKind.NO_SOURCE;
        }
        final SourceReadout readout = InstrumentSourceHelper.readSource(be, null);
        return readout == null ? SourceKind.NO_SOURCE : readout.sourceKind();
    }
}
