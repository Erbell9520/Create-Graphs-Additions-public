package com.createstats.client;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.renderer.SpecializedPanelRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PinnedAeroHudOverlay {
    private static final int FLIGHT_DECK_HUD_WIDTH = 332;
    private static final int FLIGHT_DECK_HUD_HEIGHT = 212;
    private static final int AEROLINE_HUD_WIDTH = 304;
    private static final int AEROLINE_HUD_HEIGHT = 188;
    private static final int HUD_MARGIN = 8;
    private static final float MIN_HUD_OPACITY = 0.35f;
    private static final float MAX_HUD_OPACITY = 1.0f;

    @Nullable
    private static BlockPos pinnedPanelPos;
    @Nullable
    private static ResourceKey<Level> pinnedDimension;
    @Nullable
    private static Mode pinnedMode;
    private static Anchor pinnedAnchor = Anchor.BOTTOM_LEFT;
    private static float pinnedOpacity = 0.88f;

    private PinnedAeroHudOverlay() {
    }

    public static void pin(StatsDisplayPanelBE panel, Mode mode) {
        if (panel == null || mode == null) {
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
        pinnedMode = mode;
    }

    public static void clear() {
        pinnedPanelPos = null;
        pinnedDimension = null;
        pinnedMode = null;
    }

    public static void cycleAnchor() {
        pinnedAnchor = pinnedAnchor.next();
    }

    public static String anchorLabel() {
        return pinnedAnchor.label();
    }

    public static float getHudOpacity() {
        return pinnedOpacity;
    }

    public static void setHudOpacity(float opacity) {
        pinnedOpacity = Math.max(MIN_HUD_OPACITY, Math.min(MAX_HUD_OPACITY, opacity));
    }

    public static boolean isPinned(StatsDisplayPanelBE panel, Mode mode) {
        if (panel == null || mode == null || pinnedMode != mode || pinnedPanelPos == null || pinnedDimension == null) {
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

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null || pinnedMode == null) {
            return;
        }
        final StatsDisplayPanelBE panel = resolvePinnedPanel(minecraft);
        if (panel == null) {
            return;
        }
        final int hudWidth = pinnedMode == Mode.FLIGHT_DECK ? FLIGHT_DECK_HUD_WIDTH : AEROLINE_HUD_WIDTH;
        final int hudHeight = pinnedMode == Mode.FLIGHT_DECK ? FLIGHT_DECK_HUD_HEIGHT : AEROLINE_HUD_HEIGHT;
        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        final int x = switch (pinnedAnchor) {
            case TOP_LEFT, BOTTOM_LEFT -> HUD_MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - hudWidth - HUD_MARGIN;
        };
        final int y = switch (pinnedAnchor) {
            case TOP_LEFT, TOP_RIGHT -> HUD_MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - hudHeight - HUD_MARGIN;
        };
        final List<StatsDisplayPanelBE.DataChannel> channels = panel.getVisibleChannels();
        final long renderTick = minecraft.level.getGameTime();
        switch (pinnedMode) {
            case FLIGHT_DECK -> SpecializedPanelRenderer.renderPinnedAeroFlightDeckPanelGUI(
                    graphics,
                    minecraft.font,
                    x,
                    y,
                    hudWidth,
                    hudHeight,
                    panel,
                    channels,
                    renderTick
            );
            case AEROLINE -> SpecializedPanelRenderer.renderPinnedAerolineDirectorPanelGUI(
                    graphics,
                    minecraft.font,
                    x,
                    y,
                    hudWidth,
                    hudHeight,
                    panel,
                    channels,
                    renderTick,
                    deltaTracker.getGameTimeDeltaPartialTick(false)
            );
        }
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
        if (controller == null || controller.isRemoved() || controller.getLevel() == null || !pinnedPanelPos.equals(controller.getBlockPos())) {
            return null;
        }
        return controller;
    }

    public enum Mode {
        FLIGHT_DECK,
        AEROLINE
    }

    public enum Anchor {
        TOP_LEFT("Top Left"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right"),
        TOP_RIGHT("Top Right");

        private final String label;

        Anchor(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public Anchor next() {
            final Anchor[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
