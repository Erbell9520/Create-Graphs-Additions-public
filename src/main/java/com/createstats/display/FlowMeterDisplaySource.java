package com.createstats.display;

import com.createstats.blockentity.FlowMeterBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FlowMeterDisplaySource extends DisplaySource implements StatsLinkSource {
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof FlowMeterBE meter)) {
            return EMPTY;
        }
        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());
        lines.add(Component.translatable("create_stats.flow_meter.line.flow", meter.getFlowRateMbPerTick()));
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.flow_meter.line.fluid", meter.getFluidLabel()));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.flow_meter.line.dir", meter.getDirectionLabel()));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.flow_meter.line.total", String.format("%,d", meter.getTotalMovedMb())));
        }
        return lines;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    protected String getTranslationKey() {
        return "flow_meter";
    }
}
