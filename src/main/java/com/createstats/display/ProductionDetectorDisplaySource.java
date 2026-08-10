package com.createstats.display;

import com.createstats.blockentity.ProductionDetectorBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ProductionDetectorDisplaySource extends DisplaySource
{
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof ProductionDetectorBE detector)) {
            return EMPTY;
        }
        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());
        lines.add(Component.translatable("create_stats.production_detector.display.amount", String.format("%,d", detector.getTotalItemsSeen())));
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.production_detector.display.target", detector.getTrackingSummaryLabel()));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.production_detector.display.types", detector.getTrackedItemTypeCount()));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.production_detector.display.state", detector.getTargetStatusSummary()));
        }
        return lines;
    }

    protected String getTranslationKey() {
        return "inventory_display";
    }
}
