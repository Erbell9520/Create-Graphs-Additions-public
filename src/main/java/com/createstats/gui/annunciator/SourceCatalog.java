package com.createstats.gui.annunciator;

import com.createstats.alarm.AlarmCoverage;
import com.createstats.alarm.annunciator.GridFact;
import com.createstats.alarm.annunciator.RuleBinding;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

// used to scan 49^3 inside drawEditor on a click. no
public final class SourceCatalog {
    public static final int SCAN_RADIUS = 32;

    private static final int MAX_SWEEP_RADIUS = 96;

    public static int sweepRadius(int deviceRadius) {
        return Math.min(MAX_SWEEP_RADIUS, Math.max(SCAN_RADIUS, deviceRadius));
    }

    private SourceCatalog() {
    }

    public record Option(RuleBinding binding, String group, String label, String value,
                         MetricType metric, @Nullable float[] series, String search, double distSqr,
                         String note) {
        static Option of(RuleBinding binding, String group, String label, String value,
                         MetricType metric, @Nullable float[] series, double distSqr) {
            return of(binding, group, label, value, metric, series, distSqr, "");
        }

        static Option of(RuleBinding binding, String group, String label, String value,
                         MetricType metric, @Nullable float[] series, double distSqr, String note) {
            return new Option(binding, group, label, value, metric, series,
                    (label + " " + group + " " + metric.getDisplayName()).toLowerCase(Locale.ROOT),
                    distSqr, note);
        }

        public boolean withinRadius(int radius) {
            return this.distSqr <= (double) radius * radius;
        }

        public int distance() {
            return (int) Math.round(Math.sqrt(this.distSqr));
        }

        public boolean matches(RuleBinding other) {
            return other != null && this.binding.kind() == other.kind()
                    && this.binding.key().equals(other.key())
                    && Objects.equals(this.binding.target(), other.target());
        }
    }

    public static List<Option> build(@Nullable Level level, BlockPos origin, boolean includeGrid,
                                     boolean hasCircuit) {
        return build(level, origin, includeGrid, hasCircuit, SCAN_RADIUS);
    }

    public static List<Option> build(@Nullable Level level, BlockPos origin, boolean includeGrid,
                                     boolean hasCircuit, int radius) {
        final List<Option> out = new ArrayList<>();
        if (includeGrid) {
            final String group = Component.translatable("gui.create_stats.annunciator.group.grid")
                    .getString();
            for (GridFact fact : GridFact.values()) {
                out.add(Option.of(RuleBinding.grid(fact), group, fact.displayName(),
                        hasCircuit ? "" : "--", fact.metricType(), null, 0.0));
            }
        }
        if (level == null) {
            return out;
        }

        final AlarmCoverage.Index coverage = AlarmCoverage.around(level, origin);
        final Set<BlockPos> published = new HashSet<>();
        final List<BlockEntity> found = new ArrayList<>();

        final List<StatsDisplayPanelBE> panels = new ArrayList<>();
        for (BlockEntity be : AlarmCoverage.nearby(level, origin, radius)) {
            if (be instanceof StatsDisplayPanelBE panel) {
                if (panel.isController()) {
                    panels.add(panel);
                    for (SourceConfig config : panel.getSourceRegistry().values()) {
                        published.add(config.getSourcePos());
                    }
                }
                continue;
            }
            found.add(be);
        }

        panels.sort(java.util.Comparator.comparingDouble(p -> p.getBlockPos().distSqr(origin)));
        found.sort(java.util.Comparator.comparingDouble(be -> be.getBlockPos().distSqr(origin)));
        for (StatsDisplayPanelBE panel : panels) {
            final BlockPos pos = panel.getBlockPos().immutable();
            collectPanel(out, panel, pos, pos.distSqr(origin), coverage);
        }

        final List<Option> machines = new ArrayList<>();
        for (BlockEntity be : found) {
            if (published.contains(be.getBlockPos())) {
                continue;
            }
            collectMachine(machines, be, be.getBlockPos().immutable(),
                    be.getBlockPos().distSqr(origin), coverage);
        }
        out.addAll(machines);
        return out;
    }

    private static void collectPanel(List<Option> out, StatsDisplayPanelBE panel, BlockPos pos,
                                     double distSqr, AlarmCoverage.Index coverage) {
        final String group = Component.translatable("gui.create_stats.annunciator.group.panel",
                pos.getX(), pos.getY(), pos.getZ()).getString();
        for (StatsDisplayPanelBE.DataChannel channel : panel.getActiveChannels()) {
            final TimeSeriesStore store = channel.getStore();
            final MetricType metric = store == null ? MetricType.GENERIC : store.getMetricType();

            final String label = channel.getLabel() == null || channel.getLabel().isEmpty()
                    ? channel.getId() : channel.getLabel();
            final String value = store == null || store.getSampleCount() == 0 ? "--"
                    : metric.formatValue(store.getLatestValue());
            final float[] series = store == null || store.getSampleCount() < 2 ? null
                    : store.getDataWindow(64);
            final RuleBinding binding = RuleBinding.panelChannel(pos, channel.getId());
            out.add(Option.of(binding, group, label, value, metric, series, distSqr,
                    coverage.note(binding, channel.isAlertEnabled())));
        }
    }

    private static void collectMachine(List<Option> out, BlockEntity be, BlockPos pos,
                                       double distSqr, AlarmCoverage.Index coverage) {
        if (InstrumentSourceHelper.isGridWiring(be) || !InstrumentSourceHelper.supportsAutoRead(be)) {
            return;
        }
        final InstrumentSourceHelper.SourceReadout readout;
        try {
            readout = InstrumentSourceHelper.readSource(be, null);
        } catch (RuntimeException ignored) {
            return;
        }
        if (readout.metrics().isEmpty()) {
            return;
        }
        final String name = be.getBlockState().getBlock().getName().getString();
        final String group = Component.translatable("gui.create_stats.annunciator.group.machine",
                name, pos.getX(), pos.getY(), pos.getZ()).getString();
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            final RuleBinding binding = RuleBinding.machine(pos, sample.metricId());
            out.add(Option.of(binding, group, sample.label(),
                    sample.type().formatValue(sample.value()), sample.type(), null, distSqr,
                    coverage.note(binding, false)));
        }
    }

    public static List<Option> filter(List<Option> all, String query) {
        if (query == null || query.isBlank()) {
            return all;
        }
        final String needle = query.trim().toLowerCase(Locale.ROOT);
        final List<Option> out = new ArrayList<>();
        for (Option option : all) {
            if (option.search().contains(needle)) {
                out.add(option);
            }
        }
        return out;
    }

    public static String describe(List<Option> catalog, RuleBinding binding) {
        for (Option option : catalog) {
            if (option.matches(binding)) {
                return option.label();
            }
        }
        return binding.shortLabel();
    }

    public static String describeGroup(List<Option> catalog, RuleBinding binding) {
        for (Option option : catalog) {
            if (option.matches(binding)) {
                return option.group();
            }
        }
        return "";
    }
}
