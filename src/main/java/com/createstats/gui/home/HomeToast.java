package com.createstats.gui.home;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class HomeToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/recipe");
    private static final long SHOW_MS = 5000L;

    private final ItemStack icon;
    private final Component title;
    private final Component body;

    public HomeToast(ItemStack icon, Component title, Component body) {
        this.icon = icon;
        this.title = title;
        this.body = body;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        g.blitSprite(BACKGROUND, 0, 0, width(), height());
        final var font = toasts.getMinecraft().font;
        g.drawString(font, HomeKit.trim(font, title.getString(), 125), 30, 7, 0xFF500050, false);
        g.drawString(font, HomeKit.trim(font, body.getString(), 125), 30, 18, 0xFF000000, false);
        g.renderFakeItem(icon, 8, 8);
        return time >= SHOW_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
