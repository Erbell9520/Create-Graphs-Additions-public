package com.createstats.mixin;

import com.createstats.client.CreateStatsGuiScale;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerLocalGuiScaleMixin {
    @Redirect(method = "lambda$onPress$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(DDI)Z"), require = 0, expect = 1)
    private static boolean createStats$localMouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        return screen.mouseClicked(CreateStatsGuiScale.virtualMouse(screen, mouseX), CreateStatsGuiScale.virtualMouse(screen, mouseY), button);
    }

    @Redirect(method = "lambda$onPress$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(DDI)Z"), require = 0, expect = 1)
    private static boolean createStats$localMouseReleased(Screen screen, double mouseX, double mouseY, int button) {
        return screen.mouseReleased(CreateStatsGuiScale.virtualMouse(screen, mouseX), CreateStatsGuiScale.virtualMouse(screen, mouseY), button);
    }

    @Redirect(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"), require = 0, expect = 1)
    private boolean createStats$localMouseScrolled(Screen screen, double mouseX, double mouseY, double scrollX, double scrollY) {
        return screen.mouseScrolled(CreateStatsGuiScale.virtualMouse(screen, mouseX), CreateStatsGuiScale.virtualMouse(screen, mouseY), scrollX, scrollY);
    }

    @Redirect(method = "lambda$handleAccumulatedMovement$11", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(DDIDD)Z"), require = 0, expect = 1)
    private boolean createStats$localMouseDragged(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
        final double factor = CreateStatsGuiScale.factor(screen);
        return screen.mouseDragged(mouseX * factor, mouseY * factor, button, dragX * factor, dragY * factor);
    }
}
