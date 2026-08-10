package com.createstats.client;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.CustomModuleBinding;
import com.createstats.display.CustomModuleInstance;
import com.createstats.display.CustomScreenLayout;
import com.createstats.display.CustomScreenModuleType;
import com.createstats.renderer.SpecializedPanelRenderer;
import com.createstats.display.StatsDisplaySettings;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class PinnedBattleTacticalV2HudOverlay {

    private static final int HUD_MARGIN = 8;
    private static final int DEFAULT_HUD_WIDTH = 192;
    private static final int DEFAULT_HUD_HEIGHT = 144;
    private static final long REBUILD_INTERVAL_TICKS = 5L;
    private static final float MIN_HUD_OPACITY = 0.35f;
    private static final float MAX_HUD_OPACITY = 1.0f;

    @Nullable
    private static BlockPos pinnedPanelPos;
    @Nullable
    private static ResourceKey<Level> pinnedDimension;
    @Nullable
    private static String pinnedSourceId;
    private static PinnedAeroHudOverlay.Anchor anchor = PinnedAeroHudOverlay.Anchor.BOTTOM_RIGHT;
    private static float opacity = 0.88f;

    @Nullable
    private static NativeImage cachedImage;
    @Nullable
    private static DynamicTexture cachedTexture;
    @Nullable
    private static ResourceLocation cachedTextureId;
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;
    private static long lastBakedTick = Long.MIN_VALUE;

    private PinnedBattleTacticalV2HudOverlay() {
    }

    /** Resolve the first BATTLE_TACTICAL_V2 module in a panel's layout, returning its bound source id (or empty string). */
    @Nullable
    public static String findBattleTacticalV2SourceId(@Nullable StatsDisplayPanelBE panel) {
        if (panel == null) return null;
        final StatsDisplaySettings settings = panel.getSettings();
        if (settings == null || !settings.isCustomScreenLayoutEnabled()) return null;
        final CustomScreenLayout layout = settings.getCustomScreenLayout();
        if (layout == null) return null;
        final String targetType = CustomScreenModuleType.BATTLE_TACTICAL_V2.getSerializedName();
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null || !targetType.equals(module.getModuleType())) continue;
            for (CustomModuleBinding binding : module.getBindings()) {
                if (binding == null) continue;
                if (binding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE
                        && binding.getSourceId() != null && !binding.getSourceId().isEmpty()) {
                    return binding.getSourceId();
                }
            }
            return ""; // module present but no explicit source, let renderer fall back to first link
        }
        return null;
    }

    public static boolean hasBattleTacticalV2Module(@Nullable StatsDisplayPanelBE panel) {
        return findBattleTacticalV2SourceId(panel) != null;
    }

    public static void pin(StatsDisplayPanelBE panel, String sourceId) {
        if (panel == null) return;
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) return;
        final Level level = controller.getLevel();
        if (level == null) return;
        pinnedPanelPos = controller.getBlockPos().immutable();
        pinnedDimension = level.dimension();
        pinnedSourceId = sourceId == null ? "" : sourceId;
        disposeCache();
        lastBakedTick = Long.MIN_VALUE;
    }

    public static void clear() {
        pinnedPanelPos = null;
        pinnedDimension = null;
        pinnedSourceId = null;
        disposeCache();
        lastBakedTick = Long.MIN_VALUE;
    }

    public static boolean isPinned(@Nullable StatsDisplayPanelBE panel) {
        if (panel == null || pinnedPanelPos == null || pinnedDimension == null) return false;
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) return false;
        final Level level = controller.getLevel();
        return level != null
                && pinnedDimension.equals(level.dimension())
                && pinnedPanelPos.equals(controller.getBlockPos());
    }

    public static void cycleAnchor() {
        anchor = anchor.next();
    }

    public static String anchorLabel() {
        return anchor.label();
    }

    public static float getHudOpacity() {
        return opacity;
    }

    public static void setHudOpacity(float value) {
        opacity = Math.max(MIN_HUD_OPACITY, Math.min(MAX_HUD_OPACITY, value));
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null || pinnedPanelPos == null || pinnedDimension == null) {
            return;
        }
        final StatsDisplayPanelBE panel = resolvePinnedPanel(minecraft);
        if (panel == null) {
            return;
        }
        final int hudW = DEFAULT_HUD_WIDTH;
        final int hudH = DEFAULT_HUD_HEIGHT;
        ensureCache(hudW, hudH);
        final long gameTick = minecraft.level.getGameTime();
        if (gameTick - lastBakedTick >= REBUILD_INTERVAL_TICKS
                && cachedImage != null && cachedTexture != null) {
            try {
                SpecializedPanelRenderer.renderBattleTacticalV2Texture(
                        cachedImage,
                        panel,
                        pinnedSourceId == null ? "" : pinnedSourceId,
                        panel.getSettings().getDisplayTheme(),
                        gameTick);
                cachedTexture.upload();
                lastBakedTick = gameTick;
            } catch (Throwable ignored) {
                // If rendering fails, fall through to whatever's in the cached texture.
            }
        }
        if (cachedTextureId == null) {
            return;
        }
        final int screenW = minecraft.getWindow().getGuiScaledWidth();
        final int screenH = minecraft.getWindow().getGuiScaledHeight();
        final int x = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> HUD_MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - hudW - HUD_MARGIN;
        };
        final int y = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> HUD_MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - hudH - HUD_MARGIN;
        };
        graphics.setColor(1f, 1f, 1f, opacity);
        graphics.blit(cachedTextureId, x, y, 0f, 0f, hudW, hudH, hudW, hudH);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    @Nullable
    private static StatsDisplayPanelBE resolvePinnedPanel(Minecraft minecraft) {
        if (minecraft.level == null || pinnedPanelPos == null || pinnedDimension == null) return null;
        if (!pinnedDimension.equals(minecraft.level.dimension())) return null;
        final BlockEntity blockEntity = minecraft.level.getBlockEntity(pinnedPanelPos);
        if (!(blockEntity instanceof StatsDisplayPanelBE panel)) return null;
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null || controller.isRemoved() || controller.getLevel() == null
                || !pinnedPanelPos.equals(controller.getBlockPos())) return null;
        if (!hasBattleTacticalV2Module(controller)) return null;
        return controller;
    }

    private static void ensureCache(int w, int h) {
        if (cachedImage != null && cachedTexture != null && cachedTextureId != null
                && cachedWidth == w && cachedHeight == h) {
            return;
        }
        disposeCache();
        try {
            cachedImage = new NativeImage(w, h, false);
            cachedTexture = new DynamicTexture(cachedImage);
            cachedTexture.setFilter(false, false); // NEAREST, avoid bleed/blur on the HUD overlay
            cachedTextureId = ResourceLocation.fromNamespaceAndPath(
                    "create_stats", "pinned_battle_tactical_v2_hud");
            Minecraft.getInstance().getTextureManager().register(cachedTextureId, cachedTexture);
            cachedWidth = w;
            cachedHeight = h;
            lastBakedTick = Long.MIN_VALUE;
        } catch (Throwable t) {
            disposeCache();
        }
    }

    private static void disposeCache() {
        if (cachedTextureId != null) {
            try { Minecraft.getInstance().getTextureManager().release(cachedTextureId); } catch (Throwable ignored) {}
        }
        if (cachedTexture != null) {
            try { cachedTexture.close(); } catch (Throwable ignored) {}
        }
        // NativeImage is owned by DynamicTexture; closing the texture closes the image.
        cachedImage = null;
        cachedTexture = null;
        cachedTextureId = null;
        cachedWidth = -1;
        cachedHeight = -1;
    }
}
