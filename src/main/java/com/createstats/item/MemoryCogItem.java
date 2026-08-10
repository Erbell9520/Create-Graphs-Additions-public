package com.createstats.item;

import com.createstats.data.MetricType;
import com.createstats.data.MemoryCogData.SnapshotMetadata;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;
import com.createstats.data.MemoryCogData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public class MemoryCogItem extends Item
{
    public MemoryCogItem(Item.Properties properties) {
        super(properties);
    }
    
    public boolean isFoil(ItemStack stack) {
        return MemoryCogData.hasData(stack);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (MemoryCogData.hasData(stack)) {
            MetricType type = MemoryCogData.getMetricType(stack);
            int samples = MemoryCogData.getSampleCount(stack);
            SnapshotMetadata metadata = MemoryCogData.getMetadata(stack);
            tooltip.add(Component.translatable("item.create_stats.memory_cog.tooltip.metric", type.getDisplayName()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.create_stats.memory_cog.tooltip.samples", samples).withStyle(ChatFormatting.YELLOW));
            if (metadata.hasSourceDetails()) {
                String sourceLine = metadata.sourceLabel().isEmpty() ? metadata.sourceType() : metadata.sourceLabel();
                tooltip.add(Component.translatable("item.create_stats.memory_cog.tooltip.source", sourceLine).withStyle(ChatFormatting.AQUA));
            }
            BlockPos sourcePos = metadata.sourcePos();
            if (sourcePos != null) {
                tooltip.add(Component.translatable("item.create_stats.memory_cog.tooltip.position", sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()).withStyle(ChatFormatting.DARK_AQUA));
            }
            if (!metadata.dimensionId().isEmpty()) {
                tooltip.add(Component.literal(metadata.dimensionId()).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("item.create_stats.memory_cog.tooltip.view").withStyle(ChatFormatting.DARK_GRAY));
        }
        else {
            tooltip.add(Component.translatable("item.create_stats.memory_cog.tooltip.empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
