package com.createstats.powertrack;

import com.createstats.power.FeRates;
import com.createstats.powertrack.core.RailPower;
import com.createstats.util.CreateStatsTooltipHelper;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class PowerTrackBlock extends TrackBlock {
    public PowerTrackBlock(Properties properties, TrackMaterial material) {
        super(properties, material);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final int base = FeRates.base();
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.power_track", List.of(
                CreateStatsTooltipHelper.spec("block.create_stats.power_track.tooltip.rate",
                        FeRates.perTick(RailPower.sectionCeiling(base)),
                        FeRates.perTick(RailPower.collectorRate(base))),
                Component.translatable("block.create_stats.power_track.tooltip.station")
                        .withStyle(ChatFormatting.GOLD)));
    }
}
