package com.createstats.display;

import java.util.ArrayList;
import java.util.List;

import com.createstats.compat.RefinedStorageCompatHelper;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RefinedStorageDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!RefinedStorageCompatHelper.isNetworkBlockEntity(be)) {
            return EMPTY;
        }
        final RefinedStorageCompatHelper.NetworkRead read = RefinedStorageCompatHelper.read(be);
        final int maxRows = Math.max(1, stats.maxRows());
        final List<MutableComponent> lines = new ArrayList<>();

        if (!read.online()) {
            lines.add(Component.literal("Network offline").withStyle(ChatFormatting.RED));
            return lines;
        }
        lines.add(Component.literal(count(read.itemsStored()) + " items, "
                + read.itemTypes() + " types"));

        if (read.hasCapacity() && lines.size() < maxRows) {
            lines.add(Component.literal(String.format("%.1f%% full", read.fillPercent())));
        }
        for (final RefinedStorageCompatHelper.Entry entry : read.items()) {
            if (lines.size() >= maxRows) {
                break;
            }
            lines.add(Component.literal(count(entry.amount()) + " " + entry.displayName()));
        }
        return lines;
    }

    private static String count(long amount) {
        return String.format("%,d", amount);
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "refined_storage_network";
    }
}
