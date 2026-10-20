package com.createstats.util;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class CreateStatsTooltipHelper {
    private static final String HOLD_SHIFT_KEY = "create_stats.tooltip.hold_shift";

    private CreateStatsTooltipHelper() {
    }

    public static void addBlockTooltip(List<Component> tooltip, String keyPrefix) {
        addBlockTooltip(tooltip, keyPrefix, List.of());
    }

    public static void addBlockTooltip(List<Component> tooltip, String keyPrefix, List<Component> specs) {
        addBlockTooltip(tooltip, keyPrefix, specs, List.of());
    }

    public static void addBlockTooltip(List<Component> tooltip, String keyPrefix, List<Component> specs,
                                       List<Component> extraDetail) {
        tooltip.add(Component.translatable(keyPrefix + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.addAll(specs);
        if (Screen.hasShiftDown()) {
            detail(tooltip, keyPrefix + ".tooltip.placement", ChatFormatting.GRAY);
            detail(tooltip, keyPrefix + ".tooltip.usage", ChatFormatting.DARK_GRAY);
            tooltip.addAll(extraDetail);
            detail(tooltip, keyPrefix + ".tooltip.shift", ChatFormatting.DARK_GRAY);
        } else {
            tooltip.add(holdShift());
        }
    }

    public static Component note(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }

    public static void addStageLightTooltip(List<Component> tooltip, String keyPrefix) {
        tooltip.add(Component.translatable(keyPrefix + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(keyPrefix + ".tooltip.usage").withStyle(ChatFormatting.GRAY));
        if (Screen.hasShiftDown()) {
            detail(tooltip, keyPrefix + ".tooltip.placement", ChatFormatting.GRAY);
            detail(tooltip, keyPrefix + ".tooltip.shift", ChatFormatting.GRAY);
        } else {
            tooltip.add(holdShift());
        }
    }

    public static Component spec(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_AQUA);
    }

    public static Component spec(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.DARK_AQUA);
    }

    public static Component holdShift() {
        return Component.translatable(HOLD_SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static void detail(List<Component> tooltip, String key, ChatFormatting colour) {
        if (Language.getInstance().has(key)) {
            tooltip.add(Component.translatable(key).withStyle(colour));
        }
    }
}
