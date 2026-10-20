package com.createstats.mixin;

import com.createstats.client.train.ContraptionGoggleOverlay;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class GoggleOverlayRendererMixin {
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private static void createStats$contraptionGoggles(GuiGraphics graphics, DeltaTracker delta,
                                                       CallbackInfo ci) {
        if (ContraptionGoggleOverlay.render(graphics, delta)) {
            ci.cancel();
        }

        if (!ci.isCancelled() && com.createstats.powertrack.client.PowerTrackGoggles.render(graphics, delta)) {
            ci.cancel();
        }
    }
}
