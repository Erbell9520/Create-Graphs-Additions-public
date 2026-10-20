package com.createstats.renderer;

import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.MiniDisplaySettings;
import com.createstats.data.MetricType;
import com.createstats.display.BattleCompactTargetHandler;
import com.createstats.display.DisplayTheme;
import com.createstats.renderer.instrument.InstrumentRenderPrimitives;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class MiniDisplayTextureManager {
    private static final Map<net.minecraft.core.BlockPos, TextureEntry> TEXTURES = new HashMap<>();
    private static final int BASE_TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 128;
    private static final int UPDATE_INTERVAL = 5;
    private static final int MAX_IDLE_TICKS = 200;
    private static final int CLEANUP_INTERVAL = 100;
    private static int cleanupCounter;

    private MiniDisplayTextureManager() {
    }

    public static ResourceLocation getOrUpdateTexture(MiniDisplayBE be) {
        final MiniDisplayBE controller = be.getControllerBE();
        final net.minecraft.core.BlockPos pos = controller.getBlockPos();
        final int textureWidth = BASE_TEXTURE_WIDTH * controller.getDisplayWidth();
        TextureEntry entry = TEXTURES.get(pos);
        if (entry == null || entry.width != textureWidth || entry.height != TEXTURE_HEIGHT) {
            if (entry != null) {
                disposeEntry(entry);
            }
            entry = createTextureEntry(controller, textureWidth, TEXTURE_HEIGHT);
            if (entry == null) {
                return null;
            }
            TEXTURES.put(pos, entry);
        }
        entry.lastAccessTick = getGameTime();
        final long currentTick = getGameTime();
        if (currentTick - entry.lastUpdateTick >= UPDATE_INTERVAL) {
            updateTexture(entry, controller);
            entry.lastUpdateTick = currentTick;
        }
        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStaleTextures();
        }
        return entry.textureLocation;
    }

    private static TextureEntry createTextureEntry(MiniDisplayBE be, int width, int height) {
        try {
            final NativeImage image = new NativeImage(width, height, false);
            final DynamicTexture dynamicTexture = new DynamicTexture(image);
            final String texName = "create_stats/mini_display_"
                    + be.getBlockPos().getX() + "_"
                    + be.getBlockPos().getY() + "_"
                    + be.getBlockPos().getZ() + "_"
                    + width + "x" + height;
            final ResourceLocation location = Minecraft.getInstance().getTextureManager().register(texName, dynamicTexture);
            final TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynamicTexture = dynamicTexture;
            entry.textureLocation = location;
            entry.lastUpdateTick = 0L;
            entry.lastAccessTick = getGameTime();
            entry.width = width;
            entry.height = height;
            updateTexture(entry, be);
            return entry;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void updateTexture(TextureEntry entry, MiniDisplayBE be) {
        final NativeImage image = entry.image;
        final int width = image.getWidth();
        final boolean wide = width > BASE_TEXTURE_WIDTH;
        final DisplayTheme theme = DisplayTheme.CREATE;
        final MiniDisplaySettings settings = be.getSettings();
        final int accent = settings.getAccentColor().getColor();
        final int background = settings.isLit()
                ? InstrumentRenderPrimitives.darken(accent, be.getDisplayState() == MiniDisplayBE.DisplayState.LIVE ? 0.22f : 0.16f)
                : 0xFF14171B;
        final int panel = settings.isLit()
                ? InstrumentRenderPrimitives.darken(accent, 0.14f)
                : 0xFF101419;
        final int border = InstrumentRenderPrimitives.darken(accent, 0.78f);
        final int dim = InstrumentRenderPrimitives.brighten(theme.dim, settings.isLit() ? 1.02f : 0.95f);
        final int innerX = 4;
        final int innerY = 4;
        final int innerW = width - 8;
        final int innerH = image.getHeight() - 8;

        TextureGraphRenderer.fillRect(image, 0, 0, width, image.getHeight(), background);
        TextureGraphRenderer.drawBorder(image, 0, 0, width, image.getHeight(), 1, border);
        TextureGraphRenderer.fillRect(image, innerX, innerY, innerW, innerH, panel);
        TextureGraphRenderer.drawBorder(image, innerX, innerY, innerW, innerH, 1, InstrumentRenderPrimitives.darken(border, 1.05f));
        final int headerStripHeight = wide ? 16 : 10;
        TextureGraphRenderer.fillRect(image, innerX + 2, innerY + 2, innerW - 4, headerStripHeight, InstrumentRenderPrimitives.darken(accent, 0.9f));
        TextureGraphRenderer.fillRect(image, innerX + 2, image.getHeight() - 16, innerW - 4, 6, InstrumentRenderPrimitives.darken(panel, 0.9f));

        final String header = isBattleSource(be)
                ? "BATTLE RELAY"
                : switch (be.getActiveSourceKind()) {
                    case ITEM_STORAGE -> "ITEM DISPLAY";
                    case FLUID_STORAGE -> "FLUID DISPLAY";
                    case BOILER -> "BOILER DISPLAY";
                    case ENERGY_STORAGE -> "ENERGY DISPLAY";
                    case POWER_GRID -> "GRID DISPLAY";
                    case KINETIC_STRESS -> "KINETIC LOAD";
                    case KINETIC_SPEED -> "SPEED READOUT";
                    case NO_SOURCE, UNKNOWN -> be.isExpanded() ? "MINI DISPLAY 2x1" : "MINI DISPLAY";
                };
        final int headerScale = 1;
        final int headerY = wide ? 10 : 8;
        final String sourceLabel = InstrumentRenderPrimitives.truncate(be.getActiveSourceType().isEmpty() ? "Idle" : be.getActiveSourceType(), wide ? 22 : 14);
        final int sourceWidth = BitmapFont.measureString(sourceLabel, 1);
        final int headerMaxWidth = Math.max(18, width - 28 - sourceWidth);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(header, headerMaxWidth, headerScale), 10, headerY, theme.text, headerScale);
        BitmapFont.drawRight(image, sourceLabel, width - 10, headerY, dim, 1);

        switch (be.getDisplayState()) {
            case NO_SOURCE -> {
                InstrumentRenderPrimitives.drawCenteredMessage(
                        image,
                        10,
                        24,
                        width - 20,
                        80,
                        "NO SOURCE",
                        settings.getSourceMode() == MiniDisplaySettings.SourceMode.LOCAL
                                ? "Mount it on a machine"
                                : "Link a compatible source",
                        theme);
                drawFooter(image, be, dim, wide);
                entry.dynamicTexture.upload();
                return;
            }
            case UNSUPPORTED -> {
                InstrumentRenderPrimitives.drawCenteredMessage(
                        image,
                        10,
                        24,
                        width - 20,
                        80,
                        "UNSUPPORTED",
                        "Needs item, fluid, or kinetic data",
                        theme);
                drawFooter(image, be, dim, wide);
                entry.dynamicTexture.upload();
                return;
            }
            case LINK_LOST -> {
                InstrumentRenderPrimitives.drawCenteredMessage(
                        image,
                        10,
                        24,
                        width - 20,
                        80,
                        "LINK LOST",
                        "Waiting for the next linked refresh",
                        theme);
                drawFooter(image, be, dim, wide);
                entry.dynamicTexture.upload();
                return;
            }
            case LINK_STALE -> InstrumentRenderPrimitives.drawBadge(image, width - 44, 20, 34, 12, "STALE", InstrumentRenderPrimitives.ALERT_AMBER);
            case LIVE -> {
            }
        }

        if (wide) {
            renderWideReadout(image, be, theme, accent, dim);
        } else {
            renderCompactReadout(image, be, theme, accent, dim);
        }

        drawFooter(image, be, dim, wide);
        entry.dynamicTexture.upload();
    }

    private static void renderCompactReadout(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        if (isBattleSource(be)) {
            renderCompactBattle(image, be, theme, dim);
            return;
        }
        switch (be.getActiveSourceKind()) {
            case ITEM_STORAGE -> renderCompactItemStorage(image, be, theme, accent, dim);
            case FLUID_STORAGE, BOILER -> renderCompactFluidStorage(image, be, theme, accent, dim);
            case ENERGY_STORAGE -> renderCompactEnergyStorage(image, be, theme, accent, dim);
            case KINETIC_STRESS -> renderCompactKineticStress(image, be, theme, accent, dim);
            case KINETIC_SPEED -> renderCompactKineticSpeed(image, be, theme, accent, dim);
            case NO_SOURCE, UNKNOWN -> InstrumentRenderPrimitives.drawCenteredMessage(image, 10, 24, 108, 80, "WAITING", "Connect a compatible source", theme);
        }
    }

    private static void renderWideReadout(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        if (isBattleSource(be)) {
            renderWideBattle(image, be, theme, dim);
            return;
        }
        switch (be.getActiveSourceKind()) {
            case ITEM_STORAGE -> renderWideItemStorage(image, be, theme, accent, dim);
            case FLUID_STORAGE, BOILER -> renderWideFluidStorage(image, be, theme, accent, dim);
            case ENERGY_STORAGE -> renderWideEnergyStorage(image, be, theme, accent, dim);
            case KINETIC_STRESS -> renderWideKineticStress(image, be, theme, accent, dim);
            case KINETIC_SPEED -> renderWideKineticSpeed(image, be, theme, accent, dim);
            case NO_SOURCE, UNKNOWN -> InstrumentRenderPrimitives.drawCenteredMessage(image, 12, 24, image.getWidth() - 24, 80, "WAITING", "Connect a compatible source", theme);
        }
    }

    private static void renderCompactItemStorage(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        drawProgressBar(image, 10, 26, 108, 12, be.getActiveFillPercent() / 100.0f, accent, "FILL", String.format("%.1f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 10, 44, 108, 28,
                be.getSettings().isShowLabels() ? "TOTAL ITEMS" : "TOTAL",
                formatWhole(be.getActiveTotalItems()), theme, accent, true);
        if (be.hasFirstDetailRow()) {
            drawDetailRow(image, 10, 78, 108, be.getFirstDetailLabel(), formatWhole(be.getFirstDetailValue()), accent, theme, dim);
        } else {
            BitmapFont.drawCentered(image, "HOLD FILTER ITEM", 10, 82, 108, dim, 1);
        }
        if (be.hasSecondDetailRow()) {
            drawDetailRow(image, 10, 96, 108, be.getSecondDetailLabel(), formatWhole(be.getSecondDetailValue()), accent, theme, dim);
        }
    }

    private static void renderCompactFluidStorage(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        drawProgressBar(image, 10, 26, 108, 12, be.getActiveFillPercent() / 100.0f, accent,
                be.getSettings().isShowLabels() ? "FILL" : "", String.format("%.1f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 10, 44, 108, 30,
                be.getSettings().isShowLabels() ? "AMOUNT" : "FLUID",
                formatFluid(be.getActiveFluidAmount()), theme, accent, true);
        if (be.getActiveFluidCapacity() > 0.0f) {
            drawDetailRow(image, 10, 80, 108, "Capacity", formatFluid(be.getActiveFluidCapacity()), accent, theme, dim);
        }
        if (be.hasFirstDetailRow()) {
            drawDetailRow(image, 10, 98, 108, normalizeFluidDetailLabel(be.getFirstDetailLabel()),
                    String.format("%.1f%%", be.getFirstDetailValue()), accent, theme, dim);
        }
    }

    private static void renderCompactEnergyStorage(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        drawProgressBar(image, 10, 26, 108, 12, be.getActiveFillPercent() / 100.0f, accent,
                be.getSettings().isShowLabels() ? "CHARGE" : "", String.format("%.1f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 10, 44, 108, 30,
                be.getSettings().isShowLabels() ? "STORED FE" : "ENERGY",
                MetricType.ENERGY_FE.formatValue(be.getActiveFluidAmount()), theme, accent, true);
        if (be.getActiveFluidCapacity() > 0.0f) {
            drawDetailRow(image, 10, 80, 108, "Capacity", MetricType.ENERGY_FE.formatValue(be.getActiveFluidCapacity()), accent, theme, dim);
        }
        if (be.hasFirstDetailRow()) {
            drawDetailRow(image, 10, 98, 108, "Net", MetricType.ENERGY_FE_PER_TICK.formatValue(be.getFirstDetailValue()), accent, theme, dim);
        }
    }

    private static void renderCompactKineticStress(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        drawProgressBar(image, 10, 26, 108, 11, be.getActiveStressPercent() / 100.0f, accent,
                "STRESS", String.format("%.1f%%", be.getActiveStressPercent()), theme, be.getSettings().isShowLabels());
        final float suProgress = be.getActiveStressCapacity() > 0.0f ? be.getActiveStressUsed() / be.getActiveStressCapacity() : 0.0f;
        drawProgressBar(image, 10, 44, 108, 11, suProgress, InstrumentRenderPrimitives.ALERT_AMBER,
                "SU", InstrumentRenderPrimitives.formatCompact(be.getActiveStressUsed()) + " / "
                        + InstrumentRenderPrimitives.formatCompact(be.getActiveStressCapacity()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawMetricCard(image, 10, 62, 50, 24, "USED",
                InstrumentRenderPrimitives.formatCompact(be.getActiveStressUsed()), theme, accent, false);
        InstrumentRenderPrimitives.drawMetricCard(image, 68, 62, 50, 24, "CAP",
                InstrumentRenderPrimitives.formatCompact(be.getActiveStressCapacity()), theme, InstrumentRenderPrimitives.SAFE_GREEN, false);
        BitmapFont.drawCentered(image, "RPM " + InstrumentRenderPrimitives.formatCompact(Math.abs(be.getActiveRpm())), 10, 96, 108, dim, 1);
    }

    private static void renderCompactKineticSpeed(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        final float normalized = Mth.clamp(Math.abs(be.getActiveRpm()) / 256.0f, 0.0f, 1.0f);
        drawProgressBar(image, 10, 30, 108, 12, normalized, accent, "RPM",
                InstrumentRenderPrimitives.formatCompact(Math.abs(be.getActiveRpm())), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 10, 50, 108, 34,
                be.getSettings().isShowLabels() ? "CURRENT SPEED" : "SPEED",
                InstrumentRenderPrimitives.formatCompact(Math.abs(be.getActiveRpm())), theme, accent, true);
        BitmapFont.drawCentered(image, InstrumentRenderPrimitives.truncate(be.getActiveSourceType(), 16), 10, 90, 108, dim, 1);
    }

    private static void renderCompactBattle(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int dim) {
        final int alertColor = battleAlertColor(be.getActiveRpm());
        drawProgressBar(image, 10, 26, 108, 12, be.getActiveFillPercent() / 100.0f, alertColor,
                be.getSettings().isShowLabels() ? "HULL" : "", String.format("%.0f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 10, 44, 108, 28,
                "ALERT", BattleCompactTargetHandler.alertLabel(be.getActiveRpm()), theme, alertColor, true);
        drawDetailRow(image, 10, 78, 108, "Observers", formatWhole(be.getActiveTotalItems()), alertColor, theme, dim);
        if (be.getActiveFluidCapacity() > 0.0f) {
            drawDetailRow(image, 10, 96, 108, "Weapons", formatWhole(be.getFirstDetailValue()) + "/" + formatWhole(be.getActiveFluidCapacity()), alertColor, theme, dim);
        } else {
            drawDetailRow(image, 10, 96, 108, "Breaches", formatWhole(be.getSecondDetailValue()), alertColor, theme, dim);
        }
    }

    private static void renderWideItemStorage(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        drawProgressBar(image, 12, 28, image.getWidth() - 24, 12, be.getActiveFillPercent() / 100.0f, accent,
                "FILL", String.format("%.1f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 12, 48, 112, 38,
                "TOTAL ITEMS", formatWhole(be.getActiveTotalItems()), theme, accent, true);
        drawMetricOrPlaceholder(image, 134, 48, 110, 18,
                be.hasFirstDetailRow() ? be.getFirstDetailLabel() : "TRACK A",
                be.hasFirstDetailRow() ? formatWhole(be.getFirstDetailValue()) : "--",
                theme, accent, dim);
        drawMetricOrPlaceholder(image, 134, 70, 110, 18,
                be.hasSecondDetailRow() ? be.getSecondDetailLabel() : "TRACK B",
                be.hasSecondDetailRow() ? formatWhole(be.getSecondDetailValue()) : "--",
                theme, accent, dim);
        BitmapFont.drawCentered(image,
                be.hasFirstDetailRow() || be.hasSecondDetailRow()
                        ? "Held filters pin rows before top-count fallback"
                        : "Hold a filter item to pin a row",
                12,
                96,
                image.getWidth() - 24,
                dim,
                1);
    }

    private static void renderWideFluidStorage(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        final float freeCapacity = Math.max(0.0f, be.getActiveFluidCapacity() - be.getActiveFluidAmount());
        drawProgressBar(image, 12, 28, image.getWidth() - 24, 12, be.getActiveFillPercent() / 100.0f, accent,
                "FILL", String.format("%.1f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 12, 48, 124, 38,
                "AMOUNT", formatFluid(be.getActiveFluidAmount()), theme, accent, true);
        drawMetricOrPlaceholder(image, 146, 48, 98, 18,
                "CAPACITY",
                be.getActiveFluidCapacity() > 0.0f ? formatFluid(be.getActiveFluidCapacity()) : "--",
                theme, InstrumentRenderPrimitives.SAFE_GREEN, dim);
        drawMetricOrPlaceholder(image, 146, 70, 98, 18,
                normalizeFluidDetailLabel(be.getFirstDetailLabel()),
                 be.hasFirstDetailRow() ? String.format("%.1f%%", be.getFirstDetailValue()) : "--",
                 theme, accent, dim);
        BitmapFont.drawCentered(image,
                be.getActiveFluidCapacity() > 0.0f ? "FREE " + formatFluid(freeCapacity) : "CAPACITY UNKNOWN",
                12,
                96,
                image.getWidth() - 24,
                dim,
                1);
    }

    private static void renderWideEnergyStorage(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        final float freeCapacity = Math.max(0.0f, be.getActiveFluidCapacity() - be.getActiveFluidAmount());
        drawProgressBar(image, 12, 28, image.getWidth() - 24, 12, be.getActiveFillPercent() / 100.0f, accent,
                "CHARGE", String.format("%.1f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 12, 48, 124, 38,
                "STORED FE", MetricType.ENERGY_FE.formatValue(be.getActiveFluidAmount()), theme, accent, true);
        drawMetricOrPlaceholder(image, 146, 48, 98, 18,
                "CAPACITY",
                be.getActiveFluidCapacity() > 0.0f ? MetricType.ENERGY_FE.formatValue(be.getActiveFluidCapacity()) : "--",
                theme, InstrumentRenderPrimitives.SAFE_GREEN, dim);
        drawMetricOrPlaceholder(image, 146, 70, 98, 18,
                be.hasFirstDetailRow() ? "NET" : "CONNECT",
                be.hasFirstDetailRow() ? MetricType.ENERGY_FE_PER_TICK.formatValue(be.getFirstDetailValue()) : (be.hasSecondDetailRow() ? formatWhole(be.getSecondDetailValue()) : "--"),
                theme, be.hasFirstDetailRow() && be.getFirstDetailValue() < 0.0f ? InstrumentRenderPrimitives.ALERT_AMBER : accent, dim);
        BitmapFont.drawCentered(image,
                be.getActiveFluidCapacity() > 0.0f ? "FREE " + MetricType.ENERGY_FE.formatValue(freeCapacity) : "CAPACITY UNKNOWN",
                12,
                96,
                image.getWidth() - 24,
                dim,
                1);
    }

    private static void renderWideKineticStress(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        final float used = be.getActiveStressUsed();
        final float capacity = be.getActiveStressCapacity();
        final float suProgress = capacity > 0.0f ? used / capacity : 0.0f;
        final float headroom = capacity - used;
        drawProgressBar(image, 12, 34, image.getWidth() - 24, 12, suProgress, InstrumentRenderPrimitives.ALERT_AMBER,
                "SU", InstrumentRenderPrimitives.formatCompact(used) + " / "
                        + InstrumentRenderPrimitives.formatCompact(capacity), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawMetricCard(image, 12, 54, 112, 24, "STRESS",
                String.format("%.1f%%", be.getActiveStressPercent()), theme, accent, false);
        InstrumentRenderPrimitives.drawMetricCard(image, 132, 54, 112, 24, "HEADROOM",
                InstrumentRenderPrimitives.formatCompact(headroom), theme,
                headroom >= 0.0f ? InstrumentRenderPrimitives.SAFE_GREEN : InstrumentRenderPrimitives.DANGER_RED, false);
        InstrumentRenderPrimitives.drawMetricCard(image, 12, 82, 72, 24, "USED",
                InstrumentRenderPrimitives.formatCompact(used), theme, InstrumentRenderPrimitives.ALERT_AMBER, false);
        InstrumentRenderPrimitives.drawMetricCard(image, 92, 82, 72, 24, "CAPACITY",
                InstrumentRenderPrimitives.formatCompact(capacity), theme, InstrumentRenderPrimitives.SAFE_GREEN, false);
        InstrumentRenderPrimitives.drawMetricCard(image, 172, 82, 72, 24, "RPM",
                InstrumentRenderPrimitives.formatCompact(Math.abs(be.getActiveRpm())), theme, InstrumentRenderPrimitives.COOL_BLUE, false);
    }

    private static void renderWideKineticSpeed(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int accent, int dim) {
        final float normalized = Mth.clamp(Math.abs(be.getActiveRpm()) / 256.0f, 0.0f, 1.0f);
        drawProgressBar(image, 12, 28, image.getWidth() - 24, 12, normalized, accent,
                "RPM", InstrumentRenderPrimitives.formatCompact(Math.abs(be.getActiveRpm())), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 12, 50, 144, 38,
                "CURRENT SPEED", InstrumentRenderPrimitives.formatCompact(Math.abs(be.getActiveRpm())), theme, accent, true);
        drawMetricOrPlaceholder(image, 166, 50, 78, 38,
                "SOURCE",
                be.getActiveSourceType().isBlank() ? "--" : InstrumentRenderPrimitives.truncate(be.getActiveSourceType(), 10),
                theme,
                InstrumentRenderPrimitives.COOL_BLUE,
                dim);
        BitmapFont.drawCentered(image, "Wide mode gives speed its own plate", 12, 96, image.getWidth() - 24, dim, 1);
    }

        private static void renderWideBattle(NativeImage image, MiniDisplayBE be, DisplayTheme theme, int dim) {
        final int alertColor = battleAlertColor(be.getActiveRpm());
        drawProgressBar(image, 12, 28, image.getWidth() - 24, 12, be.getActiveFillPercent() / 100.0f, alertColor,
            "HULL", String.format("%.0f%%", be.getActiveFillPercent()), theme, be.getSettings().isShowLabels());
        InstrumentRenderPrimitives.drawValuePlate(image, 12, 50, 92, 38,
            "ALERT", BattleCompactTargetHandler.alertLabel(be.getActiveRpm()), theme, alertColor, true);
        InstrumentRenderPrimitives.drawMetricCard(image, 110, 50, 64, 38, "OBS",
            formatWhole(be.getActiveTotalItems()), theme, InstrumentRenderPrimitives.COOL_BLUE, false);
        InstrumentRenderPrimitives.drawMetricCard(image, 180, 50, 64, 38, "BREACH",
            formatWhole(be.getSecondDetailValue()), theme,
            be.getSecondDetailValue() > 0.0f ? InstrumentRenderPrimitives.DANGER_RED : InstrumentRenderPrimitives.SAFE_GREEN, false);
        final String weaponsValue = be.getActiveFluidCapacity() > 0.0f
            ? formatWhole(be.getFirstDetailValue()) + "/" + formatWhole(be.getActiveFluidCapacity())
            : "--";
        drawMetricOrPlaceholder(image, 12, 92, 112, 24, "WEAPONS", weaponsValue, theme, alertColor, dim);
        InstrumentRenderPrimitives.drawMetricCard(image, 132, 92, 112, 24, "READY",
            String.format("%.0f%%", be.getActiveStressPercent()), theme,
            be.getActiveStressPercent() >= 100.0f ? InstrumentRenderPrimitives.SAFE_GREEN : alertColor, false);
        }

    private static void drawProgressBar(NativeImage image, int x, int y, int w, int h, float progress, int accentColor,
                                        String label, String value, DisplayTheme theme, boolean showLabel) {
        final int barBg = InstrumentRenderPrimitives.darken(theme.graphBg, 0.86f);
        TextureGraphRenderer.fillRect(image, x, y, w, h, barBg);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, Math.round(Math.max(0, w - 2) * Mth.clamp(progress, 0.0f, 1.0f)), h - 2, accentColor);
        if (showLabel && !label.isEmpty()) {
            BitmapFont.drawString(image, label, x, y - 8, theme.dim, 1);
        }
        BitmapFont.drawRight(image, value, x + w, y - 8, theme.text, 1);
    }

    private static void drawDetailRow(NativeImage image, int x, int y, int w, String label, String value, int accent, DisplayTheme theme, int dim) {
        TextureGraphRenderer.fillRect(image, x, y, w, 14, InstrumentRenderPrimitives.darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, 14, 1, theme.border);
        BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(label, Math.max(6, w / 10)), x + 4, y + 3, dim, 1);
        BitmapFont.drawRight(image, value, x + w - 4, y + 3, accent, 1);
    }

    private static void drawMetricOrPlaceholder(NativeImage image, int x, int y, int w, int h, String label, String value,
                                                DisplayTheme theme, int accent, int dim) {
        if ("--".equals(value)) {
            TextureGraphRenderer.fillRect(image, x, y, w, h, InstrumentRenderPrimitives.darken(theme.graphBg, 0.9f));
            TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
            BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(label, Math.max(5, w / 7)), x + 4, y + 3, dim, 1);
            BitmapFont.drawRight(image, value, x + w - 4, y + 3, dim, 1);
            return;
        }
        InstrumentRenderPrimitives.drawMetricCard(image, x, y, w, h, label, value, theme, accent, false);
    }

    private static void drawFooter(NativeImage image, MiniDisplayBE be, int dim, boolean wide) {
        final String status = be.getSettings().getSourceMode() == MiniDisplaySettings.SourceMode.LINKED
                ? be.getRelaySummary() + " · " + be.getLiveSummary()
                : be.getLiveSummary();
        BitmapFont.drawCentered(
                image,
                InstrumentRenderPrimitives.truncate(status, wide ? 52 : 24),
                8,
                112,
                image.getWidth() - 16,
                dim,
                1);
    }

    private static String normalizeFluidDetailLabel(String label) {
        if (label == null || label.isBlank()) {
            return "DETAIL";
        }
        return label.toLowerCase().contains("heat") ? "HEAT" : "STEAM";
    }

    private static boolean isBattleSource(MiniDisplayBE be) {
        return BattleCompactTargetHandler.isBattleSource(be.getActiveSourceType());
    }

    private static int battleAlertColor(float alertPercent) {
        if (alertPercent >= 90.0f) {
            return InstrumentRenderPrimitives.DANGER_RED;
        }
        if (alertPercent >= 75.0f) {
            return InstrumentRenderPrimitives.ALERT_AMBER;
        }
        return InstrumentRenderPrimitives.SAFE_GREEN;
    }

    private static String formatWhole(float value) {
        return Math.abs(value) >= 1000.0f
                ? InstrumentRenderPrimitives.formatCompact(value)
                : String.format("%.0f", value);
    }

    private static String formatFluid(float value) {
        return MetricType.FLUID_MB.formatValue(value);
    }

    public static void disposeAll() {
        for (TextureEntry entry : TEXTURES.values()) {
            disposeEntry(entry);
        }
        TEXTURES.clear();
    }

    public static void disposeFor(net.minecraft.core.BlockPos pos) {
        final TextureEntry entry = TEXTURES.remove(pos);
        if (entry != null) {
            disposeEntry(entry);
        }
    }

    private static void cleanupStaleTextures() {
        final long currentTick = getGameTime();
        final Iterator<Map.Entry<net.minecraft.core.BlockPos, TextureEntry>> iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<net.minecraft.core.BlockPos, TextureEntry> mapEntry = iterator.next();
            if (currentTick - mapEntry.getValue().lastAccessTick > MAX_IDLE_TICKS) {
                disposeEntry(mapEntry.getValue());
                iterator.remove();
            }
        }
    }

    private static void disposeEntry(TextureEntry entry) {
        try {
            entry.dynamicTexture.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static long getGameTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    private static class TextureEntry {
        private NativeImage image;
        private DynamicTexture dynamicTexture;
        private ResourceLocation textureLocation;
        private long lastUpdateTick;
        private long lastAccessTick;
        private int width;
        private int height;
    }
}
