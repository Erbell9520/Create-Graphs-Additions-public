package com.createstats.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// only a class for the tooltip. looks like the e-ink panel and people kept pointing links at it
public class BlankMonitorBezelBlock extends WallPlateBlock {
    public BlankMonitorBezelBlock(Properties properties, double x0, double y0, double z0,
                                  double x1, double y1, double z1) {
        super(properties, x0, y0, z0, x1, y1, z1);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.blank_monitor_bezel");
    }
}
