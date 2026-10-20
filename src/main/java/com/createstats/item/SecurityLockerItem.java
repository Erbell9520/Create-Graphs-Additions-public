package com.createstats.item;

import com.createstats.registry.ModDataComponents;
import com.createstats.security.LockerSeal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

// never stacks, same reason as shulker boxes
public class SecurityLockerItem extends BlockItem {
    public SecurityLockerItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final LockerSeal seal = stack.get(ModDataComponents.LOCKER_SEAL.get());
        if (seal != null) {
            tooltip.add(Component.translatable("tooltip.create_stats.locker_sealed")
                    .withStyle(ChatFormatting.GOLD));
            if (seal.hasOwner()) {
                tooltip.add(Component.translatable("tooltip.create_stats.locker_sealed_owner",
                        seal.owner()).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("tooltip.create_stats.locker_sealed_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
