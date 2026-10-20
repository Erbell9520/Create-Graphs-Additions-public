package com.createstats.display;

import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.compat.DieselCompat;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.ModConfiguration;
import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AutoStatsLinkDisplaySource extends DisplaySource implements StatsLinkSource
{
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity targetBE = context.getTargetBlockEntity();
        if (this.usesAutoReadTarget(targetBE)) {
            final BlockEntity sourceBE = context.getSourceBlockEntity();
            final InstrumentSourceHelper.SourceReadout readout = sourceBE != null
                    ? InstrumentSourceHelper.readSource(sourceBE, null)
                    : (DieselCompat.isAvailable() && targetBE != null ? DieselCompatHelper.readSource(targetBE.getLevel(), context.getSourcePos(), null) : null);
            if (readout == null) {
                return AutoStatsLinkDisplaySource.EMPTY;
            }
            final String summary = this.buildSummary(readout);
            return summary.isBlank() ? AutoStatsLinkDisplaySource.EMPTY : List.of(Component.literal(summary));
        }
        return AutoStatsLinkDisplaySource.EMPTY;
    }

    public int getPassiveRefreshTicks() {
        return (int)ModConfiguration.COMMON.statsLinkPassiveRefreshTicks.get();
    }

    protected String getTranslationKey() {
        return "stats_link";
    }

    private boolean usesAutoReadTarget(BlockEntity targetBE) {
        return targetBE instanceof IAutoDataReceiver || targetBE instanceof ProductionDetectorBE;
    }

    private String buildSummary(InstrumentSourceHelper.SourceReadout readout) {
        final InstrumentSourceHelper.MetricSample primaryMetric = this.choosePrimaryMetric(readout);
        if (primaryMetric == null) {
            return readout.sourceType();
        }
        if (primaryMetric.type() == MetricType.BOOLEAN_FLAG && primaryMetric.label().contains(": ")) {
            return primaryMetric.label();
        }
        if (primaryMetric.type() == MetricType.ENERGY_FE_PER_TICK) {
            return primaryMetric.label() + ": " + MetricType.ENERGY_FE_PER_TICK.formatValue(primaryMetric.value());
        }
        return primaryMetric.label() + ": " + primaryMetric.type().formatValue(primaryMetric.value());
    }

    private InstrumentSourceHelper.MetricSample choosePrimaryMetric(InstrumentSourceHelper.SourceReadout readout) {
        if (readout.sourceKind() == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE) {
            final String[] preferredIds = new String[] { "energy_fill_pct", "energy_net_rate", "energy_output_rate", "energy_input_rate", "energy_stored", "energy_capacity" };
            for (String preferredId : preferredIds) {
                final InstrumentSourceHelper.MetricSample sample = readout.metrics().get(preferredId);
                if (sample != null) {
                    return sample;
                }
            }
        }
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            if (sample.label().contains(": ")) {
                return sample;
            }
        }
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            if (sample.type() != MetricType.BOOLEAN_FLAG) {
                return sample;
            }
        }
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            if (sample.value() > 0.5f) {
                return sample;
            }
        }
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            return sample;
        }
        return null;
    }
}
