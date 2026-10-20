package com.createstats.display;

import com.createstats.CreateStats;
import com.createstats.blockentity.EInkPanelBE;
import com.createstats.data.MetricParser;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// parse create's text, take our metrics. both paths
public class EInkPanelDisplayTarget extends DisplayTarget {
    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        final EInkPanelBE panel = resolvePanel(context);
        if (panel == null) {
            return;
        }
        final BlockEntity source = context.getSourceBlockEntity();
        final BlockPos sourcePos = context.getSourcePos();

        if (source != null) {
            try {
                if (readCompat(panel, sourcePos, source, context)) {
                    return;
                }
                if (InstrumentSourceHelper.supportsAutoRead(source)) {
                    final InstrumentSourceHelper.SourceReadout readout =
                            InstrumentSourceHelper.readSource(source, null);
                    if (!readout.metrics().isEmpty()) {
                        panel.replaceAutoData(sourcePos, readout.sourceType(), readout.metrics());
                        return;
                    }
                }
            } catch (Exception e) {
                CreateStats.LOGGER.debug("E-Ink panel auto-read failed for {}: {}",
                        source.getClass().getSimpleName(), e.getMessage());
            }
        }

        final StringBuilder combined = new StringBuilder();
        for (MutableComponent component : text) {
            if (combined.length() > 0) {
                combined.append(' ');
            }
            combined.append(component.getString());
        }
        final String displayText = combined.toString().trim();
        if (displayText.isEmpty()) {
            return;
        }
        final MetricParser.ParseResult parsed = MetricParser.parseWithType(displayText);

        final String mapped = StatsDisplayTarget.getStatsDisplayLinkSourceType(source);
        panel.acceptTextLine(line, displayText, parsed.value(), parsed.type(),
                StatsDisplayTarget.isGenericContainerType(mapped)
                        ? source.getBlockState().getBlock().getName().getString() : mapped);
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(1, 24, this);
    }

    private static boolean readCompat(EInkPanelBE panel, BlockPos sourcePos, BlockEntity source,
                                      DisplayLinkContext context) {
        if (com.createstats.compat.CreateAdditionCompat.isAvailable()
                && com.createstats.compat.CreateAdditionCompatHelper.emitAutoData(panel, sourcePos, source)) {
            return true;
        }
        if (com.createstats.compat.NewAgeCompat.isAvailable()
                && com.createstats.compat.NewAgeCompatHelper.emitAutoData(panel, sourcePos, source)) {
            return true;
        }
        if (com.createstats.compat.SimulatedCompat.isAvailable()
                && com.createstats.compat.SimulatedCompatHelper.emitAutoData(panel, sourcePos, source)) {
            return true;
        }
        return com.createstats.compat.DieselCompat.isAvailable()
                && com.createstats.compat.DieselCompatHelper.emitAutoData(panel, sourcePos, source,
                        context.sourceConfig());
    }

    @Nullable
    private static EInkPanelBE resolvePanel(DisplayLinkContext context) {
        if (context.getTargetBlockEntity() instanceof EInkPanelBE panel) {
            return panel;
        }
        final Level level = context.level();
        final BlockPos targetPos = context.getTargetPos();
        if (level == null || targetPos == null) {
            return null;
        }
        return level.getBlockEntity(targetPos) instanceof EInkPanelBE panel ? panel : null;
    }
}
