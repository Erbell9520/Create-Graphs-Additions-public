package com.createstats.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

// wear in custom data not durability, durability forces stack size 1
public class InductionCoilItem extends Item {
    public static final int MAX_WEAR = 4800;
    private static final String WEAR_KEY = "Wear";

    public InductionCoilItem(Properties properties) {
        super(properties);
    }

    public static int getWear(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Mth.clamp(data.copyTag().getInt(WEAR_KEY), 0, MAX_WEAR);
    }

    public static void setWear(ItemStack stack, int wear) {
        int clamped = Mth.clamp(wear, 0, MAX_WEAR);
        if (clamped <= 0) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(WEAR_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isWorn(ItemStack stack) {
        return getWear(stack) > 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isWorn(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float remaining = Mth.clamp(1.0f - (float) getWear(stack) / MAX_WEAR, 0.0f, 1.0f);
        return Math.round(13.0f * remaining);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float remaining = Mth.clamp(1.0f - (float) getWear(stack) / MAX_WEAR, 0.0f, 1.0f);
        return Mth.hsvToRgb(remaining / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "item.create_stats.induction_coil");
    }
}
