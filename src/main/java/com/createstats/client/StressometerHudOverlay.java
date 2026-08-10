package com.createstats.client;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.StressometerHudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class StressometerHudOverlay {
    @Nullable
    private static BlockPos pinnedPanelPos;
    @Nullable
    private static ResourceKey<Level> pinnedDimension;

    private StressometerHudOverlay() {
    }

    public static void pin(StatsDisplayPanelBE panel) {
        if (panel == null) {
            return;
        }
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) {
            return;
        }
        final Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        pinnedPanelPos = controller.getBlockPos().immutable();
        pinnedDimension = level.dimension();
    }

    public static void clear() {
        pinnedPanelPos = null;
        pinnedDimension = null;
    }

    public static boolean isPinned(StatsDisplayPanelBE panel) {
        if (panel == null || pinnedPanelPos == null || pinnedDimension == null) {
            return false;
        }
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) {
            return false;
        }
        final Level level = controller.getLevel();
        return level != null
                && pinnedDimension.equals(level.dimension())
                && pinnedPanelPos.equals(controller.getBlockPos());
    }

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null || pinnedPanelPos == null || pinnedDimension == null) {
            return;
        }
        final StatsDisplayPanelBE panel = resolvePinnedPanel(minecraft);
        if (panel == null) {
            return;
        }
        final StressometerHudState hudState = panel.getSettings().getPanelHudSettings().getStressometerHud();
        if (!hudState.isEnabled()) {
            return;
        }
        final float partialTick = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 0f;
        renderForPanel(graphics, panel, hudState, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), minecraft.level.getGameTime(), partialTick);
    }

    public static boolean renderPreview(GuiGraphics graphics, StatsDisplayPanelBE panel, StressometerHudState hudState) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || panel == null || hudState == null) {
            return false;
        }
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) {
            return false;
        }
        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        return StressometerHudWidgetRenderer.renderLayoutPreview(graphics, controller, hudState, 0, 0, screenWidth, screenHeight, minecraft.level.getGameTime(), 0f);
    }

    private static boolean renderForPanel(GuiGraphics graphics, StatsDisplayPanelBE panel, StressometerHudState hudState, int screenWidth, int screenHeight, long renderTick, float partialTick) {
        return StressometerHudWidgetRenderer.renderLayoutLive(graphics, panel, hudState, 0, 0, screenWidth, screenHeight, renderTick, partialTick);
    }

    @Nullable
    private static StatsDisplayPanelBE resolvePinnedPanel(Minecraft minecraft) {
        if (minecraft.level == null || pinnedPanelPos == null || pinnedDimension == null) {
            return null;
        }
        if (!pinnedDimension.equals(minecraft.level.dimension())) {
            return null;
        }
        final BlockEntity blockEntity = minecraft.level.getBlockEntity(pinnedPanelPos);
        if (!(blockEntity instanceof StatsDisplayPanelBE panel)) {
            return null;
        }
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) {
            return null;
        }
        if (controller.isRemoved() || controller.getLevel() == null || !pinnedPanelPos.equals(controller.getBlockPos())) {
            return null;
        }
        return controller;
    }
}
