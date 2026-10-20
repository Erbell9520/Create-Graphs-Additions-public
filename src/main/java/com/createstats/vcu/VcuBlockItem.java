package com.createstats.vcu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class VcuBlockItem extends BlockItem {
    public VcuBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(VehicleControlUnitBlock.NAME);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Flies or drives any Aeronautics craft from the parts it is built from")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Place it facing where the craft goes. Right-click: take or release the helm")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak-right-click: change the key layout. Test build")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
