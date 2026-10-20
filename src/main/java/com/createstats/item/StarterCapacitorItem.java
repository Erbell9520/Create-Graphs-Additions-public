package com.createstats.item;

import java.util.List;

import com.createstats.power.CapacitorDischarge;
import com.createstats.power.FeGoggle;
import com.createstats.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

// cleared at defaults so empty ones stack
public class StarterCapacitorItem extends BlockItem {
    private static final String[] MODES = {"Auto", "Redstone", "Hold"};

    public StarterCapacitorItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack carrying(ItemLike item, int stored, int modeOrdinal) {
        final ItemStack stack = new ItemStack(item);
        final int fe = Math.max(0, Math.min(CapacitorDischarge.CAPACITY, stored));
        if (fe > 0) {
            stack.set(ModDataComponents.STORED_FE.get(), fe);
        }
        if (modeOrdinal > 0 && modeOrdinal < MODES.length) {
            stack.set(ModDataComponents.CAPACITOR_MODE.get(), modeOrdinal);
        }
        return stack;
    }

    private static int stored(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.STORED_FE.get(), 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * stored(stack) / CapacitorDischarge.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        final float f = (float) stored(stack) / CapacitorDischarge.CAPACITY;
        final int r = Math.round(40 + (104 - 40) * f);
        final int g = Math.round(90 + (220 - 90) * f);
        final int b = Math.round(160 + (255 - 160) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        final int fe = stored(stack);
        tooltip.add(Component.literal(String.format("Stored %,d / %,d FE", fe, CapacitorDischarge.CAPACITY))
                .withStyle(fe >= CapacitorDischarge.CAPACITY ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        final int mode = stack.getOrDefault(ModDataComponents.CAPACITOR_MODE.get(), 0);
        tooltip.add(Component.literal("Mode: " + MODES[Math.max(0, Math.min(MODES.length - 1, mode))])
                .withStyle(ChatFormatting.DARK_GRAY));
        if (fe < CapacitorDischarge.CAPACITY) {
            tooltip.add(Component.literal("Full in " + FeGoggle.time(CapacitorDischarge.ticksToFull(fe)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
