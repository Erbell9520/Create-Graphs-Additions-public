package com.createstats.mixin;

import com.createstats.client.CreateStatsGuiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenLocalGuiScaleMixin {
    @Unique
    private boolean createStats$localScalePushed;

    @ModifyVariable(method = "init", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int createStats$virtualInitWidth(int width) {
        return CreateStatsGuiScale.virtualSize((Screen) (Object) this, width);
    }

    @ModifyVariable(method = "init", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int createStats$virtualInitHeight(int height) {
        return CreateStatsGuiScale.virtualSize((Screen) (Object) this, height);
    }

    @ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int createStats$virtualRenderMouseX(int mouseX) {
        return CreateStatsGuiScale.virtualMouse((Screen) (Object) this, mouseX);
    }

    @ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int createStats$virtualRenderMouseY(int mouseY) {
        return CreateStatsGuiScale.virtualMouse((Screen) (Object) this, mouseY);
    }

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void createStats$pushLocalRenderScale(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        final Screen screen = (Screen) (Object) this;
        this.createStats$localScalePushed = CreateStatsGuiScale.needsRenderScale(screen);
        if (this.createStats$localScalePushed) {
            CreateStatsGuiScale.pushRenderScale(screen, graphics);
        }
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void createStats$popLocalRenderScale(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.createStats$localScalePushed && graphics != null) {
            graphics.pose().popPose();
        }
        this.createStats$localScalePushed = false;
    }
}
