package com.createstats.book.screen;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PonderLink {
    private PonderLink() {
    }

    public static boolean hasScenes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return PonderIndex.getSceneAccess().doScenesExistForId(id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void open(Screen current, ItemStack stack) {
        if (!hasScenes(stack)) {
            return;
        }
        try {
            ScreenOpener.open(current, PonderUI.of(stack));
        } catch (Throwable ignored) {
        }
    }
}
