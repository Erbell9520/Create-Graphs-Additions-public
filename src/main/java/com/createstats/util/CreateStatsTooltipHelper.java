package com.createstats.util;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CreateStatsTooltipHelper {
    private static final String HOLD_SHIFT_KEY = "create_stats.tooltip.hold_shift";

    private CreateStatsTooltipHelper() {
    }

    public static void addBlockTooltip(List<Component> tooltip, String keyPrefix) {
        tooltip.add(Component.translatable(keyPrefix + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(keyPrefix + ".tooltip.placement").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(keyPrefix + ".tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable(keyPrefix + ".tooltip.shift").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(HOLD_SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Compact tooltip for the Stage Light blocks: a one-line description, the LambDynamicLights
     * requirement highlighted in bright yellow so players do not miss it, and the rest behind Shift.
     */
    public static void addStageLightTooltip(List<Component> tooltip, String keyPrefix) {
        tooltip.add(Component.translatable(keyPrefix + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(keyPrefix + ".tooltip.usage").withStyle(ChatFormatting.YELLOW));
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable(keyPrefix + ".tooltip.shift").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(HOLD_SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
