package com.createstats.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class CreateStatsGuiScale {
    private static final int MAX_LOCAL_GUI_SCALE = 2;

    private CreateStatsGuiScale() {
    }

    public static boolean applies(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("com.createstats.");
    }

    public static double factor(Screen screen) {
        if (!applies(screen)) {
            return 1.0D;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return 1.0D;
        }
        final Window window = minecraft.getWindow();
        if (window == null) {
            return 1.0D;
        }
        final double guiScale = window.getGuiScale();

        final int cap = screen instanceof LocalGuiDensity density
                ? Math.max(1, density.maxEffectiveGuiScale())
                : MAX_LOCAL_GUI_SCALE;
        return guiScale > cap ? guiScale / cap : 1.0D;
    }

    public static int virtualSize(Screen screen, int value) {
        return Math.max(1, (int) Math.ceil(value * factor(screen)));
    }

    public static int virtualMouse(Screen screen, int value) {
        return (int) Math.round(value * factor(screen));
    }

    public static double virtualMouse(Screen screen, double value) {
        return value * factor(screen);
    }

    public static void pushRenderScale(Screen screen, GuiGraphics graphics) {
        final double factor = factor(screen);
        if (factor <= 1.0D || graphics == null) {
            return;
        }
        final float inverse = (float) (1.0D / factor);
        graphics.pose().pushPose();
        graphics.pose().scale(inverse, inverse, 1.0F);
    }

    public static boolean needsRenderScale(Screen screen) {
        return factor(screen) > 1.0D;
    }
}
