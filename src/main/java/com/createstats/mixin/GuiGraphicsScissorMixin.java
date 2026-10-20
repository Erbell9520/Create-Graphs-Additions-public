package com.createstats.mixin;

import com.createstats.client.CreateStatsGuiScale;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsScissorMixin {
    @Redirect(
        method = "applyScissor",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScale()D")
    )
    private double createStats$clampScissorGuiScale(Window window) {
        final double realScale = window.getGuiScale();
        final Minecraft minecraft = Minecraft.getInstance();
        final Screen screen = minecraft == null ? null : minecraft.screen;
        final double factor = CreateStatsGuiScale.factor(screen);
        return factor > 1.0D ? realScale / factor : realScale;
    }
}
