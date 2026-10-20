package com.createstats.mixin;

import com.createstats.client.CreativeCatalogOverlay;
import com.createstats.registry.CatalogLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeCatalogTitleMixin {
    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void createStats$runningHead(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        final Component head = CreativeCatalogOverlay.runningHead();
        if (head == null) {
            return;
        }
        final Font font = Minecraft.getInstance().font;
        graphics.drawString(font, head, CatalogLayout.RUNNING_HEAD_RIGHT - font.width(head), 6,
            CreativeCatalogOverlay.runningHeadColour(), false);
    }
}
