package com.createstats.renderer;

import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.battle.BattleWeaponSnapshot;
import com.createstats.client.PinnedAeroHudOverlay;
import com.createstats.data.MetricType;
import com.createstats.efficiency.EfficiencyDimension;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.display.VehicleForwardAuthority;
import com.createstats.display.DisplayTheme;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SpecializedPanelRenderer
{
    private static final int SAFE_GREEN = -21965;
    private static final int ALERT_AMBER = -30652;
    private static final int DANGER_RED = -48060;
    private static final int POWER_ON = -16724890;
    private static final int POWER_OFF = -11184811;
    private static final float ENERGY_FLOW_EPSILON = 0.01f;
    private static final Map<BlockPos, AerolineAnimationState> AEROLINE_ANIMATION = new HashMap<>();
    private static final int PINNED_HUD_PAD = 6;
    private static final int PINNED_HUD_GAP = 6;
    private static final int PINNED_HUD_FOOTER_H = 30;
    private static final int PINNED_HUD_BADGE_W = 90;
    private static final int COMPACT_HORIZON_LABEL_PAD = 4;
    private static boolean pinnedHudStyleActive = false;
    private static float pinnedHudStyleOpacity = 1.0f;
    
    private SpecializedPanelRenderer() {
    }

    public static void clearClientTransientState() {
        AEROLINE_ANIMATION.clear();
    }

    private static void beginPinnedHudStyle() {
        pinnedHudStyleActive = true;
        pinnedHudStyleOpacity = PinnedAeroHudOverlay.getHudOpacity();
    }

    private static void endPinnedHudStyle() {
        pinnedHudStyleActive = false;
        pinnedHudStyleOpacity = 1.0f;
    }

    private static int applyPinnedHudOpacity(int color) {
        if (!pinnedHudStyleActive) {
            return color;
        }
        final int alpha = color >>> 24;
        final int baseAlpha = alpha == 0 ? 255 : alpha;
        final int scaledAlpha = Math.max(8, Math.round(baseAlpha * pinnedHudStyleOpacity));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }
     
    public static boolean renderSpecializedPanel(NativeImage image, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = groupBySource(channels);
        final SourceSelection source = be.getSettings().isAerolineDirectorEnabled()
                ? buildAerolineDirectorSelection(be, bySource)
                : resolveSourceSelection(be, channels, preferredSourceId, renderTick, be.getSettings().isAeroFlightDeckEnabled());
        if (source == null) {
            return false;
        }
        final DisplayTheme theme = be.getSettings().getDisplayTheme();
        drawTextureChrome(image, theme);
        return switch (familyId(source)) {
            case "kinetics.stressometer" -> {
                renderStressometerTexture(image, be, source, theme, renderTick);
                yield true;
            }
            case "factory.stockpile_switch" -> {
                renderStockpileTexture(image, be, source, theme, renderTick);
                yield true;
            }
            case "energy.accumulator" -> {
                renderAccumulatorTexture(image, source, theme, renderTick);
                yield true;
            }
            case "energy.capacitor_bank" -> {
                renderCapacitorBankTexture(image, source, theme, renderTick);
                yield true;
            }
            case "fluid.flow_meter" -> {
                renderFlowMeterTexture(image, source, theme, renderTick);
                yield true;
            }
            case "heat.induction_heater" -> {
                renderInductionHeaterTexture(image, source, theme, renderTick);
                yield true;
            }
            case "diesel.portable_engine" -> {
                renderPortableEngineTexture(image, source, theme);
                yield true;
            }
            case "diesel.engine" -> {
                renderDieselEngineTexture(image, source, theme, renderTick);
                yield true;
            }
            case "diesel.process", "diesel.pumpjack", "diesel.burner" -> {
                renderDieselProcessTexture(image, source, theme, renderTick);
                yield true;
            }
            case "flight.velocity_sensor" -> {
                renderVelocitySensorTexture(image, source, theme);
                yield true;
            }
            case "flight.gimbal_sensor" -> {
                renderGimbalSensorTexture(image, source, theme);
                yield true;
            }
            case "flight.altitude_sensor" -> {
                renderAltitudeSensorTexture(image, source, theme);
                yield true;
            }
            case "flight.docking_connector" -> {
                renderDockingConnectorTexture(image, source, theme);
                yield true;
            }
            case "flight.steering_wheel" -> {
                renderSteeringWheelTexture(image, source, theme);
                yield true;
            }
            case "sensor.laser" -> {
                renderLaserSensorTexture(image, source, theme);
                yield true;
            }
            case "sensor.optical" -> {
                renderOpticalSensorTexture(image, source, theme);
                yield true;
            }
            case "flight.navigation_table" -> {
                renderNavigationTableTexture(image, source, theme);
                yield true;
            }
            case "text.linked_typewriter" -> {
                renderLinkedTypewriterTexture(image, source, theme);
                yield true;
            }
            case "flight.air_support" -> {
                renderHotAirBurnerTexture(image, source, theme);
                yield true;
            }
            case "flight.contraption_info" -> {
                renderContraptionInfoTexture(image, source, theme);
                yield true;
            }
            case "battle.bridge" -> {
                final StatsDisplayPanelBE.BattleScreenLink battleLink = findBattleScreenLink(be, source);
                if (battleLink != null && battleLink.getBattleViewMode() == 1) {
                    renderBattleTacticalV2Texture(image, be, source.sourceId, theme, renderTick);
                } else {
                    renderBattleScreenTexture(image, be, source, theme, renderTick);
                }
                yield true;
            }
            case "flight.aero_deck" -> {
                renderAeroFlightDeckTexture(image, source, theme);
                yield true;
            }
            case "flight.aeroline_director" -> {
                renderAerolineDirectorTexture(image, source, theme, renderTick);
                yield true;
            }
            default -> false;
        };
    }
    
    public static boolean renderSpecializedPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        return renderSpecializedPanelGUI(g, font, x, y, w, h, be, channels, preferredSourceId, renderTick, be.getSettings().isAeroFlightDeckEnabled());
    }

    public static boolean renderStandardSpecializedPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        return renderSpecializedPanelGUI(g, font, x, y, w, h, be, channels, preferredSourceId, renderTick, false);
    }

    public static boolean renderStressometerPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        final SourceSelection source = selectStressometerSource(be, groupBySource(channels), preferredSourceId);
        if (source == null) {
            return false;
        }
        final DisplayTheme theme = be.getSettings().getDisplayTheme();
        drawGuiChrome(g, x, y, w, h, theme);
        renderStressometerGui(g, font, x, y, w, h, be, source, theme, renderTick);
        return true;
    }

    public static boolean renderAeroFlightDeckPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, long renderTick) {
        return renderAeroFlightDeckPanelGUI(g, font, x, y, w, h, be, channels, renderTick, false);
    }

    public static boolean renderPinnedAeroFlightDeckPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, long renderTick) {
        return renderAeroFlightDeckPanelGUI(g, font, x, y, w, h, be, channels, renderTick, true);
    }

    public static boolean renderAerolineDirectorPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, long renderTick, float partialTick) {
        return renderAerolineDirectorPanelGUI(g, font, x, y, w, h, be, channels, renderTick, partialTick, false);
    }

    public static boolean renderPinnedAerolineDirectorPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, long renderTick, float partialTick) {
        return renderAerolineDirectorPanelGUI(g, font, x, y, w, h, be, channels, renderTick, partialTick, true);
    }

    private static boolean renderAeroFlightDeckPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, long renderTick, boolean compactHud) {
        return renderSpecializedPanelGUI(g, font, x, y, w, h, be, channels, null, renderTick, true, compactHud);
    }

    private static boolean renderAerolineDirectorPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, long renderTick, float partialTick, boolean compactHud) {
        final SourceSelection source = buildAerolineDirectorSelection(be, groupBySource(channels));
        if (source == null) {
            return false;
        }
        final DisplayTheme theme = be.getSettings().getDisplayTheme();
        if (compactHud) {
            beginPinnedHudStyle();
            try {
                drawPinnedHudChrome(g, x, y, w, h, theme);
                renderAerolineDirectorCompactGui(g, font, x, y, w, h, be.getBlockPos(), source, theme, renderTick, partialTick);
            } finally {
                endPinnedHudStyle();
            }
        } else {
            drawGuiChrome(g, x, y, w, h, theme);
            renderAerolineDirectorGui(g, font, x, y, w, h, be.getBlockPos(), source, theme, renderTick, partialTick);
        }
        return true;
    }

    private static boolean renderSpecializedPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick, boolean aeroFlightDeck) {
        return renderSpecializedPanelGUI(g, font, x, y, w, h, be, channels, preferredSourceId, renderTick, aeroFlightDeck, false);
    }

    private static boolean renderSpecializedPanelGUI(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick, boolean aeroFlightDeck, boolean compactHud) {
        if (be.getSettings().getGraphType() == StatsDisplaySettings.GraphType.TRAIN_MAP || be.getSettings().getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY || be.getSettings().getGraphType() == StatsDisplaySettings.GraphType.EFFICIENCY) {
            return false;
        }
        final SourceSelection source = resolveSourceSelection(be, channels, preferredSourceId, renderTick, aeroFlightDeck);
        if (source == null) {
            return false;
        }
        final DisplayTheme theme = be.getSettings().getDisplayTheme();
        if (!compactHud) {
            drawGuiChrome(g, x, y, w, h, theme);
        }
        switch (familyId(source)) {
            case "kinetics.stressometer": {
                renderStressometerGui(g, font, x, y, w, h, be, source, theme, renderTick);
                return true;
            }
            case "factory.stockpile_switch": {
                renderStockpileGui(g, font, x, y, w, h, be, source, theme, renderTick);
                return true;
            }
            case "energy.accumulator": {
                renderAccumulatorGui(g, font, x, y, w, h, source, theme, renderTick);
                return true;
            }
            case "energy.capacitor_bank": {
                renderCapacitorBankGui(g, font, x, y, w, h, source, theme, renderTick);
                return true;
            }
            case "fluid.flow_meter": {
                renderFlowMeterGui(g, font, x, y, w, h, source, theme, renderTick);
                return true;
            }
            case "heat.induction_heater": {
                renderInductionHeaterGui(g, font, x, y, w, h, source, theme, renderTick);
                return true;
            }
            case "diesel.portable_engine": {
                renderPortableEngineGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "diesel.engine": {
                renderDieselEngineGui(g, font, x, y, w, h, source, theme, renderTick);
                return true;
            }
            case "diesel.process", "diesel.pumpjack", "diesel.burner": {
                renderDieselProcessGui(g, font, x, y, w, h, source, theme, renderTick);
                return true;
            }
            case "flight.velocity_sensor": {
                renderVelocitySensorGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.gimbal_sensor": {
                renderGimbalSensorGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.altitude_sensor": {
                renderAltitudeSensorGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.docking_connector": {
                renderDockingConnectorGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.steering_wheel": {
                renderSteeringWheelGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "sensor.laser": {
                renderLaserSensorGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "sensor.optical": {
                renderOpticalSensorGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.navigation_table": {
                renderNavigationTableGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "text.linked_typewriter": {
                renderLinkedTypewriterGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.air_support": {
                renderHotAirBurnerGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "flight.contraption_info": {
                renderContraptionInfoGui(g, font, x, y, w, h, source, theme);
                return true;
            }
            case "battle.bridge": {
                final StatsDisplayPanelBE.BattleScreenLink battleLink = findBattleScreenLink(be, source);
                // [1]
                final boolean force2DConsole =
                        com.createstats.client.battle.BattleTacticalV2Viewport3D.isForce2D(be.getBlockPos());
                if (battleLink != null && battleLink.getBattleViewMode() == 1 && !force2DConsole) {
                    renderBattleTacticalV2Gui(g, x, y, w, h, be, source.sourceId, theme, renderTick);
                } else {
                    renderBattleScreenGui(g, font, x, y, w, h, be, source, theme, renderTick);
                }
                return true;
            }
            case "flight.aero_deck": {
                if (compactHud) {
                    beginPinnedHudStyle();
                    try {
                        drawPinnedHudChrome(g, x, y, w, h, theme);
                        renderAeroFlightDeckCompactGui(g, font, x, y, w, h, source, theme);
                    } finally {
                        endPinnedHudStyle();
                    }
                } else {
                    renderAeroFlightDeckGui(g, font, x, y, w, h, source, theme);
                }
                return true;
            }
            case "flight.aeroline_director": {
                renderAerolineDirectorGui(g, font, x, y, w, h, be.getBlockPos(), source, theme, renderTick, 0.0f);
                return true;
            }
            default: {
                return false;
            }
        }
    }

    private static String familyId(SourceSelection source) {
        return familyId(source != null ? source.sourceType : "");
    }

    private static SourceSelection resolveSourceSelection(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick, boolean aeroFlightDeck) {
        return aeroFlightDeck ? buildAeroFlightDeckSelection(be, groupBySource(channels)) : selectSource(be, channels, preferredSourceId, renderTick);
    }

    private static void renderPortableEngineTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final PortableEngineReadout readout = resolvePortableEngineReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for engine data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "PORTABLE ENGINE", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int trackerX = pad;
        final int trackerY = pad + BitmapFont.textHeight(titleScale) + 19;
        final int trackerW = w - pad * 2;
        final int trackerH = Math.max(18, h / 10);
        BrassTrackerRenderer.drawHorizontalTexture(image, trackerX, trackerY, trackerW, trackerH, BlockPos.ZERO, trackerSlot(source, 0), readout.burnPercent() / 100.0f, 0.0f, 0.4f, SAFE_GREEN, 0.4f, ALERT_AMBER, 0.85f, DANGER_RED, readout.state().color, readout.superheated(), 0L);
        BitmapFont.drawCentered(image, readout.burnLabel(), trackerX, trackerY - 11, trackerW, theme.text, 1);
        BitmapFont.drawCentered(image, readout.infiniteFuel() ? "INFINITE FUEL" : String.format("%.1f%% BURN", readout.burnPercent()), trackerX, trackerY + trackerH + 3, trackerW, theme.accent, 1);
        final int cardsY = trackerY + trackerH + 18;
        final int cardGap = 6;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(22, h / 8);
        drawMetricCardTexture(image, trackerX, cardsY, cardW, cardH, "SPEED", MetricType.RPM.formatValue(readout.generatedSpeed()), theme, theme.accent);
        drawMetricCardTexture(image, trackerX + cardW + cardGap, cardsY, cardW, cardH, "FUEL", readout.infiniteFuel() ? "INFINITE" : formatCompact(readout.currentBurn()), theme, readout.infiniteFuel() ? SAFE_GREEN : theme.text);
        drawMetricCardTexture(image, trackerX + (cardW + cardGap) * 2, cardsY, cardW, cardH, "HEAT", readout.superheated() ? "SUPER" : "NORMAL", theme, readout.superheated() ? DANGER_RED : SAFE_GREEN);
        final int sparkY = cardsY + cardH + 10;
        final int sparkH = Math.max(18, h - sparkY - pad - 4);
        drawSparklineTexture(image, trackerX, sparkY, trackerW, sparkH, readout.trendChannel().getStore(), theme, theme.accent, "Generated speed");
        if (readout.superheated()) {
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, DANGER_RED);
        }
    }

    private static void renderPortableEngineGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final PortableEngineReadout readout = resolvePortableEngineReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for engine data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "PORTABLE ENGINE", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int trackerX = x + pad;
        final int trackerY = y + pad + 28;
        final int trackerW = w - pad * 2;
        final int trackerH = Math.max(28, h / 8);
        BrassTrackerRenderer.drawHorizontalGui(g, trackerX, trackerY, trackerW, trackerH, BlockPos.ZERO, trackerSlot(source, 0), readout.burnPercent() / 100.0f, 0.0f, 0.4f, SAFE_GREEN, 0.4f, ALERT_AMBER, 0.85f, DANGER_RED, readout.state().color, readout.superheated(), 0L);
        g.drawCenteredString(font, readout.burnLabel(), x + w / 2, trackerY - 12, theme.text);
        g.drawCenteredString(font, readout.infiniteFuel() ? "INFINITE FUEL" : String.format("%.1f%% BURN", readout.burnPercent()), x + w / 2, trackerY + trackerH + 4, theme.accent);
        final int cardsY = trackerY + trackerH + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "SPEED", MetricType.RPM.formatValue(readout.generatedSpeed()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + cardGap, cardsY, cardW, cardH, "FUEL", readout.infiniteFuel() ? "INFINITE" : formatCompact(readout.currentBurn()), theme, readout.infiniteFuel() ? SAFE_GREEN : theme.text);
        drawMetricCardGui(g, font, x + pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "HEAT", readout.superheated() ? "SUPER" : "NORMAL", theme, readout.superheated() ? DANGER_RED : SAFE_GREEN);
        final int sparkY = cardsY + cardH + 12;
        final int sparkH = Math.max(24, y + h - sparkY - pad);
        drawSparklineGui(g, x + pad, sparkY, trackerW, sparkH, readout.trendChannel().getStore(), font, theme, theme.accent, "Generated speed");
        if (readout.superheated()) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, DANGER_RED);
        }
    }

    private static void renderDieselEngineTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long tick) {
        final DieselEngineReadout readout = resolveDieselEngineReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for diesel engine data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, source.sourceType.toUpperCase(), pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int cardH = Math.max(22, h / 8);
        final int cardsY = h - pad - cardH;
        final int diagramY = pad + BitmapFont.textHeight(titleScale) + 20;
        final int diagramH = Math.max(44, cardsY - diagramY - 8);
        final int contentW = w - pad * 2;
        drawDieselEngineFlowTexture(image, pad, diagramY, contentW, diagramH, readout, theme, tick);
        final int gap = 6;
        final int cardW = (contentW - gap * 3) / 4;
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "SHAFT", MetricType.RPM.formatValue(readout.generatedSpeed()), theme, theme.accent);
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, cardH, "OUTPUT", formatCompact(readout.generatedStress()) + " SU", theme, SAFE_GREEN);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, cardH, "RESERVE", readout.reserveCardValue(), theme, readout.fuelColor(theme));
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, cardsY, cardW, cardH, "BURN", readout.flowLabel(), theme, readout.state().color);
    }

    private static void renderDieselEngineGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme, long tick) {
        final DieselEngineReadout readout = resolveDieselEngineReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for diesel engine data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, source.sourceType.toUpperCase(), x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int cardH = Math.max(30, h / 8);
        final int cardsY = y + h - pad - cardH;
        final int diagramY = y + pad + 28;
        final int diagramH = Math.max(54, cardsY - diagramY - 12);
        final int contentW = w - pad * 2;
        drawDieselEngineFlowGui(g, font, x + pad, diagramY, contentW, diagramH, readout, theme, tick);
        final int gap = 8;
        final int cardW = (contentW - gap * 3) / 4;
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "SHAFT", MetricType.RPM.formatValue(readout.generatedSpeed()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, cardH, "OUTPUT", formatCompact(readout.generatedStress()) + " SU", theme, SAFE_GREEN);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, cardH, "RESERVE", readout.reserveCardValue(), theme, readout.fuelColor(theme));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, cardsY, cardW, cardH, "BURN", readout.flowLabel(), theme, readout.state().color);
    }

    private static void drawDieselEngineFlowTexture(NativeImage image, int x, int y, int w, int h, DieselEngineReadout readout, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int innerX = x + 8;
        final int innerY = y + 8;
        final int innerW = Math.max(24, w - 16);
        final int innerH = Math.max(28, h - 20);
        final int gap = Math.max(6, w / 38);
        final int reserveW = Math.max(48, (innerW - gap * 2) * 26 / 100);
        final int outputW = Math.max(48, (innerW - gap * 2) * 27 / 100);
        final int engineW = Math.max(58, innerW - reserveW - outputW - gap * 2);
        final int reserveX = innerX;
        final int engineX = reserveX + reserveW + gap;
        final int outputX = engineX + engineW + gap;
        final int fuelColor = readout.fuelColor(theme);
        final float reservePct = readout.reserveLinks() > 0.0f ? readout.reservePercent() : readout.fuelPercent();
        TextureGraphRenderer.drawBorder(image, reserveX, innerY, reserveW, innerH, 1, fuelColor);
        BitmapFont.drawCentered(image, readout.reserveLinks() > 0.0f ? "LINKED RESERVE" : "ENGINE TANK", reserveX, innerY + 5, reserveW, theme.dim, 1);
        final int tankGap = Math.max(2, reserveW / 18);
        final int tankW = Math.max(6, (reserveW - tankGap * 4) / 3);
        final int tankH = Math.max(14, innerH - 31);
        final int tankY = innerY + 18;
        for (int tank = 0; tank < 3; tank++) {
            final int tankX = reserveX + tankGap + tank * (tankW + tankGap);
            TextureGraphRenderer.drawBorder(image, tankX, tankY, tankW, tankH, 1, darken(theme.border, 0.8f));
            final int fillH = Math.max(0, Math.min(tankH - 2, Math.round((tankH - 2) * reservePct / 100.0f)));
            if (fillH > 0) {
                TextureGraphRenderer.fillRect(image, tankX + 1, tankY + tankH - 1 - fillH, tankW - 2, fillH, fuelColor);
            }
        }
        BitmapFont.drawCentered(image, truncate(readout.reserveLinks() > 0.0f ? readout.reserveLabel() : readout.fuelLabel(), Math.max(6, (reserveW - 4) / 6)), reserveX, innerY + innerH - 10, reserveW, theme.text, 1);

        TextureGraphRenderer.drawBorder(image, engineX, innerY, engineW, innerH, 1, readout.state().color);
        TextureGraphRenderer.fillRect(image, engineX + 4, innerY + innerH / 2 - 8, engineW - 8, 16, darken(theme.graphBg, 0.72f));
        BitmapFont.drawCentered(image, "ENGINE MANIFOLD", engineX, innerY + 5, engineW, theme.dim, 1);
        final int cylinderCount = Math.max(2, Math.min(5, Math.round(readout.engineCount())));
        final int cylinderGap = Math.max(2, engineW / 22);
        final int cylinderW = Math.max(6, (engineW - cylinderGap * (cylinderCount + 1)) / cylinderCount);
        final int baseY = innerY + innerH / 2 - 5;
        for (int i = 0; i < cylinderCount; i++) {
            final int cylinderX = engineX + cylinderGap + i * (cylinderW + cylinderGap);
            final int pistonOffset = readout.running() ? (int)Math.round(Math.sin((tick + i * 5L) * 0.35D) * 3.0D) : 0;
            TextureGraphRenderer.fillRect(image, cylinderX, baseY - 11, cylinderW, 22, darken(theme.border, 0.75f));
            TextureGraphRenderer.fillRect(image, cylinderX + 1, baseY - 2 + pistonOffset, Math.max(1, cylinderW - 2), 5, readout.running() ? fuelColor : theme.dim);
        }
        BitmapFont.drawCentered(image, truncate(readout.statusLine(), Math.max(8, (engineW - 4) / 6)), engineX, innerY + innerH - 10, engineW, readout.state().color, 1);

        TextureGraphRenderer.drawBorder(image, outputX, innerY, outputW, innerH, 1, theme.accent);
        BitmapFont.drawCentered(image, "SHAFT BUS", outputX, innerY + 5, outputW, theme.dim, 1);
        final int shaftCx = outputX + outputW / 2;
        final int shaftCy = innerY + innerH / 2;
        final int radius = Math.max(7, Math.min(outputW, innerH) / 5);
        TextureGraphRenderer.fillCircle(image, shaftCx, shaftCy, radius, darken(theme.graphBg, 0.62f));
        TextureGraphRenderer.drawBorder(image, shaftCx - radius, shaftCy - radius, radius * 2, radius * 2, 1, readout.generatedSpeed() > 0.01f ? theme.accent : theme.dim);
        final double angle = tick * 0.18D;
        for (int spoke = 0; spoke < 3; spoke++) {
            final double spokeAngle = angle + spoke * Math.PI * 2.0D / 3.0D;
            TextureGraphRenderer.drawLine(image, shaftCx, shaftCy, shaftCx + (int)Math.round(Math.cos(spokeAngle) * radius), shaftCy + (int)Math.round(Math.sin(spokeAngle) * radius), theme.accent);
        }
        BitmapFont.drawCentered(image, MetricType.RPM.formatValue(readout.generatedSpeed()), outputX, innerY + innerH - 20, outputW, theme.text, 1);
        BitmapFont.drawCentered(image, formatCompact(readout.generatedStress()) + " SU", outputX, innerY + innerH - 10, outputW, SAFE_GREEN, 1);
        drawAccumulatorFlowTexture(image, reserveX + reserveW, engineX, innerY + innerH / 2, true, readout.hasFuelFlow() || readout.reserveAmount() > 0.0f, fuelColor, theme, tick);
        drawAccumulatorFlowTexture(image, engineX + engineW, outputX, innerY + innerH / 2, true, readout.running() || readout.generatedSpeed() > 0.01f, theme.accent, theme, tick);
    }

    private static void drawDieselNodeTexture(NativeImage image, int x, int y, int w, int h, String title, String value, int color, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.82f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, color);
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, 3, darken(color, 0.86f));
        BitmapFont.drawCentered(image, title, x, y + 7, w, theme.dim, 1);
        BitmapFont.drawCentered(image, truncate(value, Math.max(4, (w - 8) / 6)), x, y + h / 2 + 1, w, color, 1);
    }

    private static void drawDieselEngineFlowGui(GuiGraphics g, Font font, int x, int y, int w, int h, DieselEngineReadout readout, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        final int innerX = x + 8;
        final int innerY = y + 8;
        final int innerW = Math.max(24, w - 16);
        final int innerH = Math.max(36, h - 20);
        final int gap = Math.max(8, w / 38);
        final int reserveW = Math.max(58, (innerW - gap * 2) * 26 / 100);
        final int outputW = Math.max(58, (innerW - gap * 2) * 27 / 100);
        final int engineW = Math.max(72, innerW - reserveW - outputW - gap * 2);
        final int reserveX = innerX;
        final int engineX = reserveX + reserveW + gap;
        final int outputX = engineX + engineW + gap;
        final int fuelColor = readout.fuelColor(theme);
        final float reservePct = readout.reserveLinks() > 0.0f ? readout.reservePercent() : readout.fuelPercent();
        borderGui(g, reserveX, innerY, reserveW, innerH, fuelColor);
        g.drawCenteredString(font, readout.reserveLinks() > 0.0f ? "LINKED RESERVE" : "ENGINE TANK", reserveX + reserveW / 2, innerY + 7, theme.dim);
        final int tankGap = Math.max(3, reserveW / 18);
        final int tankW = Math.max(8, (reserveW - tankGap * 4) / 3);
        final int tankH = Math.max(20, innerH - 35);
        final int tankY = innerY + 20;
        for (int tank = 0; tank < 3; tank++) {
            final int drawX = reserveX + tankGap + tank * (tankW + tankGap);
            borderGui(g, drawX, tankY, tankW, tankH, darken(theme.border, 0.8f));
            final int fillH = Math.max(0, Math.min(tankH - 2, Math.round((tankH - 2) * reservePct / 100.0f)));
            if (fillH > 0) {
                g.fill(drawX + 1, tankY + tankH - 1 - fillH, drawX + tankW - 1, tankY + tankH - 1, fuelColor);
            }
        }
        g.drawCenteredString(font, truncateGui(font, readout.reserveLinks() > 0.0f ? readout.reserveLabel() : readout.fuelLabel(), reserveW - 6), reserveX + reserveW / 2, innerY + innerH - font.lineHeight - 2, theme.text);

        borderGui(g, engineX, innerY, engineW, innerH, readout.state().color);
        g.fill(engineX + 4, innerY + innerH / 2 - 10, engineX + engineW - 4, innerY + innerH / 2 + 10, darken(theme.graphBg, 0.72f));
        g.drawCenteredString(font, "ENGINE MANIFOLD", engineX + engineW / 2, innerY + 7, theme.dim);
        final int cylinderCount = Math.max(2, Math.min(5, Math.round(readout.engineCount())));
        final int cylinderGap = Math.max(3, engineW / 22);
        final int cylinderW = Math.max(7, (engineW - cylinderGap * (cylinderCount + 1)) / cylinderCount);
        final int baseY = innerY + innerH / 2 - 6;
        for (int i = 0; i < cylinderCount; i++) {
            final int cylinderX = engineX + cylinderGap + i * (cylinderW + cylinderGap);
            final int pistonOffset = readout.running() ? (int)Math.round(Math.sin((tick + i * 5L) * 0.35D) * 4.0D) : 0;
            g.fill(cylinderX, baseY - 13, cylinderX + cylinderW, baseY + 13, darken(theme.border, 0.75f));
            g.fill(cylinderX + 1, baseY - 3 + pistonOffset, cylinderX + cylinderW - 1, baseY + 3 + pistonOffset, readout.running() ? fuelColor : theme.dim);
        }
        g.drawCenteredString(font, truncateGui(font, readout.statusLine(), engineW - 6), engineX + engineW / 2, innerY + innerH - font.lineHeight - 2, readout.state().color);

        borderGui(g, outputX, innerY, outputW, innerH, theme.accent);
        g.drawCenteredString(font, "SHAFT BUS", outputX + outputW / 2, innerY + 7, theme.dim);
        final int shaftCx = outputX + outputW / 2;
        final int shaftCy = innerY + innerH / 2;
        final int radius = Math.max(8, Math.min(outputW, innerH) / 5);
        fillCircleGui(g, shaftCx, shaftCy, radius, darken(theme.graphBg, 0.62f));
        final double angle = tick * 0.18D;
        for (int spoke = 0; spoke < 3; spoke++) {
            final double spokeAngle = angle + spoke * Math.PI * 2.0D / 3.0D;
            drawLineGui(g, shaftCx, shaftCy, shaftCx + (int)Math.round(Math.cos(spokeAngle) * radius), shaftCy + (int)Math.round(Math.sin(spokeAngle) * radius), theme.accent);
        }
        g.drawCenteredString(font, MetricType.RPM.formatValue(readout.generatedSpeed()), outputX + outputW / 2, innerY + innerH - font.lineHeight * 2 - 2, theme.text);
        g.drawCenteredString(font, formatCompact(readout.generatedStress()) + " SU", outputX + outputW / 2, innerY + innerH - font.lineHeight - 2, SAFE_GREEN);
        drawAccumulatorFlowGui(g, reserveX + reserveW, engineX, innerY + innerH / 2, true, readout.hasFuelFlow() || readout.reserveAmount() > 0.0f, fuelColor, theme, tick);
        drawAccumulatorFlowGui(g, engineX + engineW, outputX, innerY + innerH / 2, true, readout.running() || readout.generatedSpeed() > 0.01f, theme.accent, theme, tick);
    }

    private static void drawDieselNodeGui(GuiGraphics g, Font font, int x, int y, int w, int h, String title, String value, int color, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.82f));
        borderGui(g, x, y, w, h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 5, darken(color, 0.86f));
        g.drawCenteredString(font, title, x + w / 2, y + 8, theme.dim);
        g.drawCenteredString(font, truncateGui(font, value, w - 8), x + w / 2, y + h / 2 + 2, color);
    }

    private static void renderDieselProcessTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long tick) {
        final DieselProcessReadout readout = resolveDieselProcessReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for diesel plant data");
            return;
        }
        if ("Oil Pumpjack".equals(readout.sourceType())) {
            renderOilPumpjackTexture(image, source, readout, theme, tick);
            return;
        }
        if ("Diesel Distillation Tower".equals(readout.sourceType())) {
            renderDieselDistillationTexture(image, source, readout, theme, tick);
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, source.sourceType.toUpperCase(), pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int trackerX = pad;
        final int trackerY = pad + BitmapFont.textHeight(titleScale) + 20;
        final int trackerW = w - pad * 2;
        final int trackerH = Math.max(18, h / 10);
        BrassTrackerRenderer.drawHorizontalTexture(image, trackerX, trackerY, trackerW, trackerH, BlockPos.ZERO, trackerSlot(source, 28), readout.fillPercent() / 100.0f, 0.0f, 0.20f, DANGER_RED, 0.20f, ALERT_AMBER, 0.80f, SAFE_GREEN, readout.state().color, readout.processing(), tick);
        BitmapFont.drawCentered(image, readout.volumeLabel(), trackerX, trackerY - 11, trackerW, theme.text, 1);
        BitmapFont.drawCentered(image, readout.topLine(), trackerX, trackerY + trackerH + 3, trackerW, readout.state().color, 1);
        final int cardsY = trackerY + trackerH + 18;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        final int cardH = Math.max(22, h / 8);
        drawMetricCardTexture(image, trackerX, cardsY, cardW, cardH, "FILL", String.format("%.1f%%", readout.fillPercent()), theme, readout.fillColor(theme));
        drawMetricCardTexture(image, trackerX + cardW + gap, cardsY, cardW, cardH, "PROCESS", String.format("%.1f%%", readout.processPercent()), theme, readout.processing() ? SAFE_GREEN : theme.dim);
        drawMetricCardTexture(image, trackerX + (cardW + gap) * 2, cardsY, cardW, cardH, readout.auxLabel(), readout.auxValue(), theme, theme.accent);
        drawMetricCardTexture(image, trackerX + (cardW + gap) * 3, cardsY, cardW, cardH, "BLOCKS", formatCompact(readout.blocks()), theme, theme.text);
        final int diagramY = cardsY + cardH + 10;
        final int diagramH = Math.max(38, h - diagramY - pad);
        drawDieselProcessFlowTexture(image, trackerX, diagramY, trackerW, diagramH, readout, theme, tick);
    }

    private static void renderDieselProcessGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme, long tick) {
        final DieselProcessReadout readout = resolveDieselProcessReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for diesel plant data");
            return;
        }
        if ("Oil Pumpjack".equals(readout.sourceType())) {
            renderOilPumpjackGui(g, font, x, y, w, h, source, readout, theme, tick);
            return;
        }
        if ("Diesel Distillation Tower".equals(readout.sourceType())) {
            renderDieselDistillationGui(g, font, x, y, w, h, source, readout, theme, tick);
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, source.sourceType.toUpperCase(), x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int trackerX = x + pad;
        final int trackerY = y + pad + 28;
        final int trackerW = w - pad * 2;
        final int trackerH = Math.max(28, h / 8);
        BrassTrackerRenderer.drawHorizontalGui(g, trackerX, trackerY, trackerW, trackerH, BlockPos.ZERO, trackerSlot(source, 28), readout.fillPercent() / 100.0f, 0.0f, 0.20f, DANGER_RED, 0.20f, ALERT_AMBER, 0.80f, SAFE_GREEN, readout.state().color, readout.processing(), tick);
        g.drawCenteredString(font, readout.volumeLabel(), x + w / 2, trackerY - 12, theme.text);
        g.drawCenteredString(font, readout.topLine(), x + w / 2, trackerY + trackerH + 4, readout.state().color);
        final int cardsY = trackerY + trackerH + 18;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "FILL", String.format("%.1f%%", readout.fillPercent()), theme, readout.fillColor(theme));
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, cardH, "PROCESS", String.format("%.1f%%", readout.processPercent()), theme, readout.processing() ? SAFE_GREEN : theme.dim);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, cardH, readout.auxLabel(), readout.auxValue(), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, cardsY, cardW, cardH, "BLOCKS", formatCompact(readout.blocks()), theme, theme.text);
        final int diagramY = cardsY + cardH + 12;
        final int diagramH = Math.max(48, y + h - diagramY - pad);
        drawDieselProcessFlowGui(g, font, x + pad, diagramY, trackerW, diagramH, readout, theme, tick);
    }

    private static void renderDieselDistillationTexture(NativeImage image, SourceSelection source, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "DISTILLATION TOWER", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int gap = 6;
        final int cardH = Math.max(22, h / 8);
        final int cardsY = h - pad - cardH;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "CRUDE", readout.volumeLabel(), theme, readout.fillColor(theme));
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, cardH, "HEAT", readout.heatLabel(), theme, readout.heatColor(theme));
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, cardH, "PROCESS", String.format("%.1f%%", readout.processPercent()), theme, readout.state().color);
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, cardsY, cardW, cardH, "TOWER", readout.towerShapeLabel(), theme, theme.text);
        final int diagramY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int diagramH = Math.max(54, cardsY - diagramY - 8);
        drawDistillationTowerDiagramTexture(image, pad, diagramY, w - pad * 2, diagramH, readout, theme, tick);
    }

    private static void renderDieselDistillationGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "DISTILLATION TOWER", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int gap = 8;
        final int cardH = Math.max(30, h / 8);
        final int cardsY = y + h - pad - cardH;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "CRUDE", readout.volumeLabel(), theme, readout.fillColor(theme));
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, cardH, "HEAT", readout.heatLabel(), theme, readout.heatColor(theme));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, cardH, "PROCESS", String.format("%.1f%%", readout.processPercent()), theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, cardsY, cardW, cardH, "TOWER", readout.towerShapeLabel(), theme, theme.text);
        final int diagramY = y + pad + 28;
        final int diagramH = Math.max(74, cardsY - diagramY - 10);
        drawDistillationTowerDiagramGui(g, font, x + pad, diagramY, w - pad * 2, diagramH, readout, theme, tick);
    }

    private static void drawDistillationTowerDiagramTexture(NativeImage image, int x, int y, int w, int h, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int nodeW = Math.max(44, w / 5);
        final int nodeH = Math.max(18, h / 5);
        final int towerW = Math.max(34, w / 6);
        final int towerH = Math.max(48, h - 22);
        final int towerX = x + w / 2 - towerW / 2;
        final int towerY = y + 10;
        final int inputX = x + 8;
        final int inputY = y + h / 2 - nodeH / 2;
        final int gasX = x + w - nodeW - 8;
        final int gasY = towerY + 2;
        final int dieselY = towerY + towerH - nodeH - 2;
        drawDieselNodeTexture(image, inputX, inputY, nodeW, nodeH, "CRUDE", readout.fillPercent() > 0.0f ? String.format("%.0f%%", readout.fillPercent()) : "EMPTY", readout.fillColor(theme), theme);
        drawDieselNodeTexture(image, gasX, gasY, nodeW, nodeH, "GASOLINE", readout.processing() ? "VAPOR" : "HOLD", ALERT_AMBER, theme);
        drawDieselNodeTexture(image, gasX, dieselY, nodeW, nodeH, "DIESEL", readout.processing() ? "FLOW" : "HOLD", SAFE_GREEN, theme);
        TextureGraphRenderer.fillRect(image, towerX, towerY, towerW, towerH, darken(theme.graphBg, 0.68f));
        TextureGraphRenderer.drawBorder(image, towerX, towerY, towerW, towerH, 2, readout.state().color);
        final int trayCount = Math.max(3, Math.min(8, Math.round(readout.towerHeight())));
        for (int i = 1; i < trayCount; ++i) {
            final int trayY = towerY + i * towerH / trayCount;
            TextureGraphRenderer.fillRect(image, towerX + 3, trayY, towerW - 6, 1, darken(theme.border, 0.82f));
        }
        final int fillH = Math.round((towerH - 6) * Mth.clamp(readout.fillPercent() / 100.0f, 0.0f, 1.0f));
        if (fillH > 0) {
            TextureGraphRenderer.fillRect(image, towerX + 3, towerY + towerH - 3 - fillH, towerW - 6, fillH, darken(readout.fillColor(theme), 0.58f));
        }
        final int heatH = Math.max(4, h / 16);
        TextureGraphRenderer.fillRect(image, towerX + 5, towerY + towerH + 2, towerW - 10, heatH, readout.heatColor(theme));
        if (readout.processing()) {
            final int span = Math.max(8, towerH - 12);
            for (int i = 0; i < 8; ++i) {
                final int phase = Math.floorMod((int)(tick * 2L + i * span / 8), span);
                final int px = towerX + 6 + Math.floorMod((int)(tick + i * 7L), Math.max(1, towerW - 12));
                final int py = towerY + towerH - 8 - phase;
                TextureGraphRenderer.fillRect(image, px, py, 3, 3, i % 2 == 0 ? ALERT_AMBER : SAFE_GREEN);
            }
        }
        drawAccumulatorFlowTexture(image, inputX + nodeW, towerX, inputY + nodeH / 2, true, readout.fillPercent() > 0.0f, readout.fillColor(theme), theme, tick);
        drawAccumulatorFlowTexture(image, towerX + towerW, gasX, gasY + nodeH / 2, true, readout.processing(), ALERT_AMBER, theme, tick);
        drawAccumulatorFlowTexture(image, towerX + towerW, gasX, dieselY + nodeH / 2, true, readout.processing(), SAFE_GREEN, theme, tick);
        BitmapFont.drawCentered(image, readout.distillationLine(), towerX - 8, towerY + towerH / 2 - 5, towerW + 16, theme.text, 1);
    }

    private static void drawDistillationTowerDiagramGui(GuiGraphics g, Font font, int x, int y, int w, int h, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        final int nodeW = Math.max(58, w / 5);
        final int nodeH = Math.max(32, h / 5);
        final int towerW = Math.max(48, w / 6);
        final int towerH = Math.max(72, h - 26);
        final int towerX = x + w / 2 - towerW / 2;
        final int towerY = y + 12;
        final int inputX = x + 10;
        final int inputY = y + h / 2 - nodeH / 2;
        final int gasX = x + w - nodeW - 10;
        final int gasY = towerY + 2;
        final int dieselY = towerY + towerH - nodeH - 2;
        drawDieselNodeGui(g, font, inputX, inputY, nodeW, nodeH, "CRUDE", readout.fillPercent() > 0.0f ? String.format("%.0f%%", readout.fillPercent()) : "EMPTY", readout.fillColor(theme), theme);
        drawDieselNodeGui(g, font, gasX, gasY, nodeW, nodeH, "GASOLINE", readout.processing() ? "VAPOR" : "HOLD", ALERT_AMBER, theme);
        drawDieselNodeGui(g, font, gasX, dieselY, nodeW, nodeH, "DIESEL", readout.processing() ? "FLOW" : "HOLD", SAFE_GREEN, theme);
        g.fill(towerX, towerY, towerX + towerW, towerY + towerH, darken(theme.graphBg, 0.68f));
        borderGui(g, towerX, towerY, towerW, towerH, readout.state().color);
        final int trayCount = Math.max(3, Math.min(8, Math.round(readout.towerHeight())));
        for (int i = 1; i < trayCount; ++i) {
            final int trayY = towerY + i * towerH / trayCount;
            g.fill(towerX + 4, trayY, towerX + towerW - 4, trayY + 1, darken(theme.border, 0.82f));
        }
        final int fillH = Math.round((towerH - 8) * Mth.clamp(readout.fillPercent() / 100.0f, 0.0f, 1.0f));
        if (fillH > 0) {
            g.fill(towerX + 4, towerY + towerH - 4 - fillH, towerX + towerW - 4, towerY + towerH - 4, darken(readout.fillColor(theme), 0.58f));
        }
        final int heatH = Math.max(7, h / 16);
        g.fill(towerX + 7, towerY + towerH + 3, towerX + towerW - 7, towerY + towerH + 3 + heatH, readout.heatColor(theme));
        if (readout.processing()) {
            final int span = Math.max(10, towerH - 14);
            for (int i = 0; i < 8; ++i) {
                final int phase = Math.floorMod((int)(tick * 2L + i * span / 8), span);
                final int px = towerX + 8 + Math.floorMod((int)(tick + i * 7L), Math.max(1, towerW - 16));
                final int py = towerY + towerH - 10 - phase;
                g.fill(px, py, px + 4, py + 4, i % 2 == 0 ? ALERT_AMBER : SAFE_GREEN);
            }
        }
        drawAccumulatorFlowGui(g, inputX + nodeW, towerX, inputY + nodeH / 2, true, readout.fillPercent() > 0.0f, readout.fillColor(theme), theme, tick);
        drawAccumulatorFlowGui(g, towerX + towerW, gasX, gasY + nodeH / 2, true, readout.processing(), ALERT_AMBER, theme, tick);
        drawAccumulatorFlowGui(g, towerX + towerW, gasX, dieselY + nodeH / 2, true, readout.processing(), SAFE_GREEN, theme, tick);
        g.drawCenteredString(font, readout.distillationLine(), towerX + towerW / 2, towerY + towerH / 2 - font.lineHeight / 2, theme.text);
    }

    private static void renderOilPumpjackTexture(NativeImage image, SourceSelection source, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "OIL PUMPJACK", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int gap = 6;
        final int cardH = Math.max(22, h / 8);
        final int cardsY = h - pad - cardH;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "DEPOSIT", readout.oilLabel(), theme, readout.fillColor(theme));
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, cardH, "PIPE", readout.pipeLabel(), theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, cardH, "PUMP", readout.processing() ? "ACTIVE" : "IDLE", theme, readout.state().color);
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, cardsY, cardW, cardH, "FLOW", readout.flowNodeValue(), theme, readout.processing() ? SAFE_GREEN : theme.dim);
        final int diagramY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int diagramH = Math.max(48, cardsY - diagramY - 8);
        drawOilPumpjackDiagramTexture(image, pad, diagramY, w - pad * 2, diagramH, readout, theme, tick);
    }

    private static void renderOilPumpjackGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "OIL PUMPJACK", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int gap = 8;
        final int cardH = Math.max(30, h / 8);
        final int cardsY = y + h - pad - cardH;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "DEPOSIT", readout.oilLabel(), theme, readout.fillColor(theme));
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, cardH, "PIPE", readout.pipeLabel(), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, cardH, "PUMP", readout.processing() ? "ACTIVE" : "IDLE", theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, cardsY, cardW, cardH, "FLOW", readout.flowNodeValue(), theme, readout.processing() ? SAFE_GREEN : theme.dim);
        final int diagramY = y + pad + 28;
        final int diagramH = Math.max(70, cardsY - diagramY - 10);
        drawOilPumpjackDiagramGui(g, font, x + pad, diagramY, w - pad * 2, diagramH, readout, theme, tick);
    }

    private static void drawOilPumpjackDiagramTexture(NativeImage image, int x, int y, int w, int h, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int groundY = y + Math.max(24, h * 2 / 5);
        TextureGraphRenderer.fillRect(image, x + 1, groundY, w - 2, 3, darken(theme.border, 0.9f));
        final int bandH = Math.max(5, (y + h - groundY - 4) / 3);
        TextureGraphRenderer.fillRect(image, x + 1, groundY + 3, w - 2, bandH, darken(theme.graphBg, 0.62f));
        TextureGraphRenderer.fillRect(image, x + 1, groundY + 3 + bandH, w - 2, bandH, darken(theme.border, 0.58f));
        TextureGraphRenderer.fillRect(image, x + 1, groundY + 3 + bandH * 2, w - 2, Math.max(1, h - (groundY - y) - bandH * 2 - 4), darken(DANGER_RED, 0.38f));
        final int centerX = x + w / 2;
        final int pumpBaseX = x + Math.max(18, w / 6);
        final int pumpBaseY = groundY - 1;
        final int rodOffset = readout.processing() ? Math.round((float)Math.sin(tick * 0.22f) * 5.0f) : 0;
        final int beamPivotX = pumpBaseX + Math.max(30, w / 5);
        final int beamPivotY = groundY - Math.max(22, h / 4);
        final int horseHeadX = centerX + Math.max(28, w / 5);
        final int horseHeadY = beamPivotY + rodOffset / 2;
        drawSegmentTexture(image, pumpBaseX, pumpBaseY, beamPivotX, beamPivotY, darken(theme.border, 0.82f));
        drawSegmentTexture(image, beamPivotX - 18, pumpBaseY, beamPivotX, beamPivotY, darken(theme.border, 0.82f));
        TextureGraphRenderer.fillRect(image, pumpBaseX - 4, pumpBaseY - 2, beamPivotX - pumpBaseX + 18, 4, darken(theme.border, 0.68f));
        drawSegmentTexture(image, beamPivotX - Math.max(22, w / 10), beamPivotY + 5, horseHeadX, horseHeadY, readout.state().color);
        TextureGraphRenderer.fillRect(image, beamPivotX - 3, beamPivotY - 3, 6, 6, theme.accent);
        final int crankX = pumpBaseX + Math.max(12, w / 12);
        final int crankY = pumpBaseY - Math.max(12, h / 10);
        final int crankPhaseX = Math.round((float)Math.cos(tick * 0.22f) * 7.0f);
        final int crankPhaseY = Math.round((float)Math.sin(tick * 0.22f) * 5.0f);
        TextureGraphRenderer.drawBorder(image, crankX - 9, crankY - 9, 18, 18, 1, theme.border);
        drawSegmentTexture(image, crankX, crankY, crankX + crankPhaseX, crankY + crankPhaseY, readout.processing() ? theme.accent : theme.dim);
        drawSegmentTexture(image, crankX + crankPhaseX, crankY + crankPhaseY, beamPivotX - 10, beamPivotY + 3, readout.processing() ? ALERT_AMBER : theme.dim);
        final int pipeX = centerX + Math.max(18, w / 8);
        TextureGraphRenderer.fillRect(image, pipeX - 3, horseHeadY, 6, y + h - horseHeadY - 4, darken(theme.border, 0.74f));
        TextureGraphRenderer.fillRect(image, pipeX - 1, horseHeadY + rodOffset, 2, y + h - horseHeadY - 8 - rodOffset, readout.processing() ? readout.fillColor(theme) : theme.dim);
        final int depositW = Math.max(24, w / 4);
        final int depositH = Math.max(7, h / 10);
        final int depositX = pipeX - depositW / 2;
        final int depositY = y + h - depositH - 5;
        final float depositNorm = readout.depositVisualPercent();
        TextureGraphRenderer.drawBorder(image, depositX, depositY, depositW, depositH, 1, darken(readout.fillColor(theme), 0.8f));
        TextureGraphRenderer.fillRect(image, depositX + 1, depositY + 1, Math.max(1, Math.round((depositW - 2) * depositNorm)), depositH - 2, darken(readout.fillColor(theme), 0.72f));
        if (readout.processing()) {
            final int span = Math.max(10, y + h - horseHeadY - 16);
            for (int i = 0; i < 5; ++i) {
                final int phase = Math.floorMod((int)(tick * 2L + i * span / 5), span);
                TextureGraphRenderer.fillRect(image, pipeX - 2, y + h - 10 - phase, 4, 3, readout.fillColor(theme));
            }
        }
        BitmapFont.drawCentered(image, readout.processing() ? "EXTRACTING" : "STANDBY", x, y + 5, w, readout.state().color, 1);
        BitmapFont.drawCentered(image, readout.pipeLabel(), x, y + h - 14, w, theme.dim, 1);
    }

    private static void drawOilPumpjackDiagramGui(GuiGraphics g, Font font, int x, int y, int w, int h, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        final int groundY = y + Math.max(34, h * 2 / 5);
        g.fill(x + 1, groundY, x + w - 1, groundY + 4, darken(theme.border, 0.9f));
        final int bandH = Math.max(7, (y + h - groundY - 5) / 3);
        g.fill(x + 1, groundY + 4, x + w - 1, groundY + 4 + bandH, darken(theme.graphBg, 0.62f));
        g.fill(x + 1, groundY + 4 + bandH, x + w - 1, groundY + 4 + bandH * 2, darken(theme.border, 0.58f));
        g.fill(x + 1, groundY + 4 + bandH * 2, x + w - 1, y + h - 1, darken(DANGER_RED, 0.38f));
        final int centerX = x + w / 2;
        final int pumpBaseX = x + Math.max(28, w / 6);
        final int pumpBaseY = groundY - 1;
        final int rodOffset = readout.processing() ? Math.round((float)Math.sin(tick * 0.22f) * 7.0f) : 0;
        final int beamPivotX = pumpBaseX + Math.max(44, w / 5);
        final int beamPivotY = groundY - Math.max(34, h / 4);
        final int horseHeadX = centerX + Math.max(44, w / 5);
        final int horseHeadY = beamPivotY + rodOffset / 2;
        drawSegmentGui(g, pumpBaseX, pumpBaseY, beamPivotX, beamPivotY, darken(theme.border, 0.82f));
        drawSegmentGui(g, beamPivotX - 22, pumpBaseY, beamPivotX, beamPivotY, darken(theme.border, 0.82f));
        g.fill(pumpBaseX - 5, pumpBaseY - 3, beamPivotX + 18, pumpBaseY + 3, darken(theme.border, 0.68f));
        drawSegmentGui(g, beamPivotX - Math.max(28, w / 10), beamPivotY + 6, horseHeadX, horseHeadY, readout.state().color);
        g.fill(beamPivotX - 4, beamPivotY - 4, beamPivotX + 4, beamPivotY + 4, theme.accent);
        final int crankX = pumpBaseX + Math.max(18, w / 12);
        final int crankY = pumpBaseY - Math.max(18, h / 10);
        final int crankPhaseX = Math.round((float)Math.cos(tick * 0.22f) * 10.0f);
        final int crankPhaseY = Math.round((float)Math.sin(tick * 0.22f) * 7.0f);
        borderGui(g, crankX - 12, crankY - 12, 24, 24, theme.border);
        drawSegmentGui(g, crankX, crankY, crankX + crankPhaseX, crankY + crankPhaseY, readout.processing() ? theme.accent : theme.dim);
        drawSegmentGui(g, crankX + crankPhaseX, crankY + crankPhaseY, beamPivotX - 12, beamPivotY + 4, readout.processing() ? ALERT_AMBER : theme.dim);
        final int pipeX = centerX + Math.max(28, w / 8);
        g.fill(pipeX - 4, horseHeadY, pipeX + 4, y + h - 5, darken(theme.border, 0.74f));
        g.fill(pipeX - 1, horseHeadY + rodOffset, pipeX + 1, y + h - 9 - rodOffset, readout.processing() ? readout.fillColor(theme) : theme.dim);
        final int depositW = Math.max(44, w / 4);
        final int depositH = Math.max(12, h / 10);
        final int depositX = pipeX - depositW / 2;
        final int depositY = y + h - depositH - 7;
        final float depositNorm = readout.depositVisualPercent();
        borderGui(g, depositX, depositY, depositW, depositH, darken(readout.fillColor(theme), 0.8f));
        g.fill(depositX + 1, depositY + 1, depositX + 1 + Math.max(1, Math.round((depositW - 2) * depositNorm)), depositY + depositH - 1, darken(readout.fillColor(theme), 0.72f));
        if (readout.processing()) {
            final int span = Math.max(12, y + h - horseHeadY - 18);
            for (int i = 0; i < 5; ++i) {
                final int phase = Math.floorMod((int)(tick * 2L + i * span / 5), span);
                g.fill(pipeX - 2, y + h - 12 - phase, pipeX + 2, y + h - 8 - phase, readout.fillColor(theme));
            }
        }
        g.drawCenteredString(font, readout.processing() ? "EXTRACTING" : "STANDBY", x + w / 2, y + 7, readout.state().color);
        g.drawCenteredString(font, readout.pipeLabel(), x + w / 2, y + h - 16, theme.dim);
    }

    private static void drawSegmentTexture(NativeImage image, int x1, int y1, int x2, int y2, int color) {
        final int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int i = 0; i <= steps; ++i) {
            final int px = x1 + (x2 - x1) * i / steps;
            final int py = y1 + (y2 - y1) * i / steps;
            TextureGraphRenderer.fillRect(image, px - 1, py - 1, 3, 3, color);
        }
    }

    private static void drawSegmentGui(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        final int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int i = 0; i <= steps; ++i) {
            final int px = x1 + (x2 - x1) * i / steps;
            final int py = y1 + (y2 - y1) * i / steps;
            g.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
    }

    private static void drawDieselProcessFlowTexture(NativeImage image, int x, int y, int w, int h, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int gap = Math.max(6, w / 34);
        final int boxW = Math.max(42, (w - gap * 2 - 16) / 3);
        final int boxH = Math.max(24, h - 16);
        final int boxY = y + 8;
        final int inputX = x + 8;
        final int processX = inputX + boxW + gap;
        final int outputX = processX + boxW + gap;
        drawDieselNodeTexture(image, inputX, boxY, boxW, boxH, readout.inputNodeLabel(), readout.volumeLabel(), readout.fillColor(theme), theme);
        drawDieselNodeTexture(image, processX, boxY, boxW, boxH, readout.processNodeLabel(), readout.state().shortLabel, readout.state().color, theme);
        drawDieselNodeTexture(image, outputX, boxY, boxW, boxH, readout.outputNodeLabel(), readout.outputNodeValue(), theme.accent, theme);
        drawAccumulatorFlowTexture(image, inputX + boxW, processX, boxY + boxH / 2, true, readout.fillPercent() > 0.0f, readout.fillColor(theme), theme, tick);
        drawAccumulatorFlowTexture(image, processX + boxW, outputX, boxY + boxH / 2, true, readout.processing(), theme.accent, theme, tick);
    }

    private static void drawDieselProcessFlowGui(GuiGraphics g, Font font, int x, int y, int w, int h, DieselProcessReadout readout, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        final int gap = Math.max(8, w / 34);
        final int boxW = Math.max(54, (w - gap * 2 - 16) / 3);
        final int boxH = Math.max(34, h - 16);
        final int boxY = y + 8;
        final int inputX = x + 8;
        final int processX = inputX + boxW + gap;
        final int outputX = processX + boxW + gap;
        drawDieselNodeGui(g, font, inputX, boxY, boxW, boxH, readout.inputNodeLabel(), readout.volumeLabel(), readout.fillColor(theme), theme);
        drawDieselNodeGui(g, font, processX, boxY, boxW, boxH, readout.processNodeLabel(), readout.state().shortLabel, readout.state().color, theme);
        drawDieselNodeGui(g, font, outputX, boxY, boxW, boxH, readout.outputNodeLabel(), readout.outputNodeValue(), theme.accent, theme);
        drawAccumulatorFlowGui(g, inputX + boxW, processX, boxY + boxH / 2, true, readout.fillPercent() > 0.0f, readout.fillColor(theme), theme, tick);
        drawAccumulatorFlowGui(g, processX + boxW, outputX, boxY + boxH / 2, true, readout.processing(), theme.accent, theme, tick);
    }

    private static void renderVelocitySensorTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final VelocityReadout readout = resolveVelocityReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for velocity data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "VELOCITY SENSOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int barX = pad;
        final int barY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int barW = w - pad * 2;
        final int barH = Math.max(16, h / 10);
        drawCenteredBarTexture(image, barX, barY, barW, barH, readout.axisVelocity(), 32.0f, theme, readout.state().color);
        BitmapFont.drawCentered(image, formatSignedCompact(readout.axisVelocity()), barX, barY - 14, barW, theme.text, w >= 360 ? 2 : 1);
        BitmapFont.drawCentered(image, "Sensor-axis velocity", barX, barY + barH + 3, barW, theme.dim, 1);
        final int cardsY = barY + barH + 18;
        final int cardGap = 6;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(22, h / 8);
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "DIR", readout.state().shortLabel, theme, readout.state().color);
        drawMetricCardTexture(image, pad + cardW + cardGap, cardsY, cardW, cardH, "HORIZ", formatCompact(readout.horizontalVelocity()), theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "SIGNAL", Integer.toString(Math.round(readout.signedRedstone())), theme, POWER_ON);
        final int sparkY = cardsY + cardH + 10;
        final int sparkH = Math.max(18, h - sparkY - pad - 4);
        drawSparklineTexture(image, pad, sparkY, w - pad * 2, sparkH, readout.trendChannel().getStore(), theme, readout.state().color, "Velocity trend");
    }

    private static void renderVelocitySensorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final VelocityReadout readout = resolveVelocityReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for velocity data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "VELOCITY SENSOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int barX = x + pad;
        final int barY = y + pad + 31;
        final int barW = w - pad * 2;
        final int barH = Math.max(24, h / 9);
        drawCenteredBarGui(g, barX, barY, barW, barH, readout.axisVelocity(), 32.0f, theme, readout.state().color);
        g.drawCenteredString(font, formatSignedCompact(readout.axisVelocity()), x + w / 2, barY - 14, theme.text);
        g.drawCenteredString(font, "Sensor-axis velocity", x + w / 2, barY + barH + 4, theme.dim);
        final int cardsY = barY + barH + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "DIR", readout.state().shortLabel, theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + cardW + cardGap, cardsY, cardW, cardH, "HORIZ", formatCompact(readout.horizontalVelocity()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "SIGNAL", Integer.toString(Math.round(readout.signedRedstone())), theme, POWER_ON);
        final int sparkY = cardsY + cardH + 12;
        final int sparkH = Math.max(24, y + h - sparkY - pad);
        drawSparklineGui(g, x + pad, sparkY, barW, sparkH, readout.trendChannel().getStore(), font, theme, readout.state().color, "Velocity trend");
    }

    private static void renderGimbalSensorTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final GimbalReadout readout = resolveGimbalReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for gimbal data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "GIMBAL SENSOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int topY = pad + BitmapFont.textHeight(titleScale) + 20;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int topH = Math.max(22, h / 8);
        drawMetricCardTexture(image, pad, topY, cardW, topH, "X", formatAngle(readout.xAngle()), theme, theme.accent);
        drawMetricCardTexture(image, pad + cardW + gap, topY, cardW, topH, "Z", formatAngle(readout.zAngle()), theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, topY, cardW, topH, "TILT", formatAngle(readout.maxTilt()), theme, readout.state().color);
        final int powerY = topY + topH + 10;
        final int powerH = Math.max(18, (h - powerY - pad - gap) / 2);
        drawMetricCardTexture(image, pad, powerY, cardW, powerH, "UP", formatPower(readout.powerUp()), theme, powerColor(readout.powerUp()));
        drawMetricCardTexture(image, pad + cardW + gap, powerY, cardW, powerH, "NORTH", formatPower(readout.powerNorth()), theme, powerColor(readout.powerNorth()));
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, powerY, cardW, powerH, "EAST", formatPower(readout.powerEast()), theme, powerColor(readout.powerEast()));
        final int lowerY = powerY + powerH + gap;
        drawMetricCardTexture(image, pad, lowerY, cardW, powerH, "DOWN", formatPower(readout.powerDown()), theme, powerColor(readout.powerDown()));
        drawMetricCardTexture(image, pad + cardW + gap, lowerY, cardW, powerH, "SOUTH", formatPower(readout.powerSouth()), theme, powerColor(readout.powerSouth()));
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, lowerY, cardW, powerH, "WEST", formatPower(readout.powerWest()), theme, powerColor(readout.powerWest()));
    }

    private static void renderGimbalSensorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final GimbalReadout readout = resolveGimbalReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for gimbal data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "GIMBAL SENSOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int topY = y + pad + 30;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int topH = Math.max(28, h / 8);
        drawMetricCardGui(g, font, x + pad, topY, cardW, topH, "X", formatAngle(readout.xAngle()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + gap, topY, cardW, topH, "Z", formatAngle(readout.zAngle()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, topY, cardW, topH, "TILT", formatAngle(readout.maxTilt()), theme, readout.state().color);
        final int powerY = topY + topH + 12;
        final int powerH = Math.max(24, (y + h - powerY - pad - gap) / 2);
        drawMetricCardGui(g, font, x + pad, powerY, cardW, powerH, "UP", formatPower(readout.powerUp()), theme, powerColor(readout.powerUp()));
        drawMetricCardGui(g, font, x + pad + cardW + gap, powerY, cardW, powerH, "NORTH", formatPower(readout.powerNorth()), theme, powerColor(readout.powerNorth()));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, powerY, cardW, powerH, "EAST", formatPower(readout.powerEast()), theme, powerColor(readout.powerEast()));
        final int lowerY = powerY + powerH + gap;
        drawMetricCardGui(g, font, x + pad, lowerY, cardW, powerH, "DOWN", formatPower(readout.powerDown()), theme, powerColor(readout.powerDown()));
        drawMetricCardGui(g, font, x + pad + cardW + gap, lowerY, cardW, powerH, "SOUTH", formatPower(readout.powerSouth()), theme, powerColor(readout.powerSouth()));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, lowerY, cardW, powerH, "WEST", formatPower(readout.powerWest()), theme, powerColor(readout.powerWest()));
    }

    private static void renderAltitudeSensorTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final AltitudeReadout readout = resolveAltitudeReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for altitude data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "ALTITUDE SENSOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int mainY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int mainH = Math.max(24, h / 5);
        drawMetricCardTexture(image, pad, mainY, w - pad * 2, mainH, "HEIGHT", formatSignedCompact(readout.worldHeight()), theme, theme.accent);
        BitmapFont.drawCentered(image, "World height", pad, mainY + mainH + 3, w - pad * 2, theme.dim, 1);
        final int cardsY = mainY + mainH + 18;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap) / 2;
        final int rowH = Math.max(18, (h - cardsY - pad - gap) / 2);
        drawMetricCardTexture(image, pad, cardsY, cardW, rowH, "AIR", formatPercent(readout.airPressure()), theme, pressureColor(readout.airPressure()));
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, w - pad * 2 - cardW - gap, rowH, "SIGNAL", Integer.toString(Math.round(readout.signal())), theme, powerColor(readout.signal()));
        final int lowerY = cardsY + rowH + gap;
        drawMetricCardTexture(image, pad, lowerY, cardW, h - lowerY - pad, "NORM", formatPercent(readout.normalizedHeight()), theme, readout.state().color);
        drawMetricCardTexture(image, pad + cardW + gap, lowerY, w - pad * 2 - cardW - gap, h - lowerY - pad, "BAND", formatCoordinate(readout.lowBandHeight()) + ".." + formatCoordinate(readout.highBandHeight()), theme, theme.accent);
        if (readout.airPressure() <= 5.0f) {
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, DANGER_RED);
        }
    }

    private static void renderAltitudeSensorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final AltitudeReadout readout = resolveAltitudeReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for altitude data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "ALTITUDE SENSOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int mainY = y + pad + 31;
        final int mainH = Math.max(30, h / 5);
        drawMetricCardGui(g, font, x + pad, mainY, w - pad * 2, mainH, "HEIGHT", formatSignedCompact(readout.worldHeight()), theme, theme.accent);
        g.drawCenteredString(font, "World height", x + w / 2, mainY + mainH + 4, theme.dim);
        final int cardsY = mainY + mainH + 18;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap) / 2;
        final int rowH = Math.max(24, (y + h - cardsY - pad - gap) / 2);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, rowH, "AIR", formatPercent(readout.airPressure()), theme, pressureColor(readout.airPressure()));
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, w - pad * 2 - cardW - gap, rowH, "SIGNAL", Integer.toString(Math.round(readout.signal())), theme, powerColor(readout.signal()));
        final int lowerY = cardsY + rowH + gap;
        drawMetricCardGui(g, font, x + pad, lowerY, cardW, y + h - lowerY - pad, "NORM", formatPercent(readout.normalizedHeight()), theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + cardW + gap, lowerY, w - pad * 2 - cardW - gap, y + h - lowerY - pad, "BAND", formatCoordinate(readout.lowBandHeight()) + ".." + formatCoordinate(readout.highBandHeight()), theme, theme.accent);
        if (readout.airPressure() <= 5.0f) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, DANGER_RED);
        }
    }

    private static void renderDockingConnectorTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final DockingReadout readout = resolveDockingReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for connector data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "DOCKING CONNECTOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 96, pad + BitmapFont.textHeight(titleScale) + 1, 96, 12, readout.state().label, readout.state().color);
        final int infoY = pad + BitmapFont.textHeight(titleScale) + 19;
        BitmapFont.drawString(image, truncate(readout.sublevelText(), Math.max(12, (w - pad * 2) / 6)), pad, infoY, theme.text, 1);
        BitmapFont.drawString(image, truncate(readout.positionText(), Math.max(12, (w - pad * 2) / 6)), pad, infoY + 10, theme.dim, 1);
        final int cardsY = infoY + 22;
        final int gap = 6;
        final int footerH = 14;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int rowH = Math.max(18, (h - cardsY - footerH - pad - gap * 2) / 2);
        drawMetricCardTexture(image, pad, cardsY, cardW, rowH, "PAIR", boolText(readout.paired(), "YES", "NO"), theme, readout.paired() ? SAFE_GREEN : themeNeutral());
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, rowH, "POWER", boolText(readout.powered(), "ON", "OFF"), theme, readout.powered() ? POWER_ON : POWER_OFF);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, rowH, "LOCK", boolText(readout.locked(), "YES", "NO"), theme, readout.locked() ? SAFE_GREEN : POWER_OFF);
        final int lowerY = cardsY + rowH + gap;
        drawMetricCardTexture(image, pad, lowerY, cardW, rowH, "STATE", truncate(readout.stateText(), Math.max(4, cardW / 6)), theme, readout.state().color);
        drawMetricCardTexture(image, pad + cardW + gap, lowerY, cardW, rowH, "DIST", readout.paired() || readout.powered() ? formatCompact(readout.closestPairDistance()) : "--", theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, lowerY, cardW, rowH, "LOAD", boolText(readout.loaded(), "LIVE", "--"), theme, readout.loaded() ? SAFE_GREEN : (readout.paired() ? ALERT_AMBER : POWER_OFF));
        BitmapFont.drawCentered(image, "Cargo manifest control soon", pad, lowerY + rowH + gap + 1, w - pad * 2, theme.dim, 1);
    }

    private static void renderDockingConnectorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final DockingReadout readout = resolveDockingReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for connector data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "DOCKING CONNECTOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 112, y + pad - 2, 112, 16, readout.state().label, readout.state().color);
        final int infoY = y + pad + 26;
        g.drawString(font, truncateGui(font, readout.sublevelText(), w - pad * 2), x + pad, infoY, theme.text);
        g.drawString(font, truncateGui(font, readout.positionText(), w - pad * 2), x + pad, infoY + font.lineHeight, theme.dim);
        final int cardsY = infoY + font.lineHeight * 2 + 4;
        final int gap = 8;
        final int footerH = font.lineHeight;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int rowH = Math.max(24, (y + h - cardsY - footerH - pad - gap * 2) / 2);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, rowH, "PAIR", boolText(readout.paired(), "YES", "NO"), theme, readout.paired() ? SAFE_GREEN : themeNeutral());
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, rowH, "POWER", boolText(readout.powered(), "ON", "OFF"), theme, readout.powered() ? POWER_ON : POWER_OFF);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, rowH, "LOCK", boolText(readout.locked(), "YES", "NO"), theme, readout.locked() ? SAFE_GREEN : POWER_OFF);
        final int lowerY = cardsY + rowH + gap;
        drawMetricCardGui(g, font, x + pad, lowerY, cardW, rowH, "STATE", readout.stateText(), theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + cardW + gap, lowerY, cardW, rowH, "DIST", readout.paired() || readout.powered() ? formatCompact(readout.closestPairDistance()) : "--", theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, lowerY, cardW, rowH, "LOAD", boolText(readout.loaded(), "LIVE", "--"), theme, readout.loaded() ? SAFE_GREEN : (readout.paired() ? ALERT_AMBER : POWER_OFF));
        g.drawCenteredString(font, "Cargo manifest control soon", x + w / 2, lowerY + rowH + gap + 1, theme.dim);
    }

    private static void renderSteeringWheelTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final SteeringReadout readout = resolveSteeringReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for steering data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "STEERING WHEEL", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int barX = pad;
        final int barY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int barW = w - pad * 2;
        final int barH = Math.max(18, h / 10);
        drawCenteredBarTexture(image, barX, barY, barW, barH, readout.angle(), readout.maxAngle(), theme, readout.state().color);
        BitmapFont.drawCentered(image, formatAngle(readout.angle()), barX, barY - 14, barW, theme.text, w >= 360 ? 2 : 1);
        BitmapFont.drawCentered(image, "Current angle", barX, barY + barH + 3, barW, theme.dim, 1);
        final int cardsY = barY + barH + 18;
        final int gap = 6;
        final int footerH = 14;
        final int cardW = (w - pad * 2 - gap) / 2;
        final int cardH = Math.max(18, (h - cardsY - footerH - pad - gap * 2) / 2);
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "TARGET", formatAngle(readout.targetAngle()), theme, theme.accent);
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, w - pad * 2 - cardW - gap, cardH, "CMD", formatAngle(readout.commandAngle()), theme, readout.state().color);
        final int lowerY = cardsY + cardH + gap;
        drawMetricCardTexture(image, pad, lowerY, cardW, cardH, "GEN", MetricType.RPM.formatValue(readout.generatedSpeed()), theme, theme.accent);
        drawMetricCardTexture(image, pad + cardW + gap, lowerY, w - pad * 2 - cardW - gap, cardH, "LOG", MetricType.RPM.formatValue(readout.logicalSpeed()), theme, readout.state().color);
        BitmapFont.drawCentered(image, truncate(readout.activityLine(), Math.max(12, (w - pad * 2) / 6)), pad, lowerY + cardH + gap + 1, w - pad * 2, theme.dim, 1);
    }

    private static void renderSteeringWheelGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final SteeringReadout readout = resolveSteeringReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for steering data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "STEERING WHEEL", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int barX = x + pad;
        final int barY = y + pad + 31;
        final int barW = w - pad * 2;
        final int barH = Math.max(24, h / 9);
        drawCenteredBarGui(g, barX, barY, barW, barH, readout.angle(), readout.maxAngle(), theme, readout.state().color);
        g.drawCenteredString(font, formatAngle(readout.angle()), x + w / 2, barY - 14, theme.text);
        g.drawCenteredString(font, "Current angle", x + w / 2, barY + barH + 4, theme.dim);
        final int cardsY = barY + barH + 18;
        final int gap = 8;
        final int footerH = font.lineHeight;
        final int cardW = (w - pad * 2 - gap) / 2;
        final int cardH = Math.max(24, (y + h - cardsY - footerH - pad - gap * 2) / 2);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "TARGET", formatAngle(readout.targetAngle()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, w - pad * 2 - cardW - gap, cardH, "CMD", formatAngle(readout.commandAngle()), theme, readout.state().color);
        final int lowerY = cardsY + cardH + gap;
        drawMetricCardGui(g, font, x + pad, lowerY, cardW, cardH, "GEN", MetricType.RPM.formatValue(readout.generatedSpeed()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + gap, lowerY, w - pad * 2 - cardW - gap, cardH, "LOG", MetricType.RPM.formatValue(readout.logicalSpeed()), theme, readout.state().color);
        g.drawCenteredString(font, truncateGui(font, readout.activityLine(), w - pad * 2), x + w / 2, lowerY + cardH + gap + 1, theme.dim);
    }

    private static void renderLaserSensorTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final LaserReadout readout = resolveLaserReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for laser data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "LASER SENSOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int mainY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int mainH = Math.max(24, h / 5);
        drawMetricCardTexture(image, pad, mainY, w - pad * 2, mainH, "POWER", Integer.toString(Math.round(readout.currentPower())), theme, powerColor(readout.currentPower()));
        BitmapFont.drawCentered(image, "Current redstone output", pad, mainY + mainH + 3, w - pad * 2, theme.dim, 1);
        final int cardsY = mainY + mainH + 18;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int cardH = Math.max(24, h - cardsY - pad);
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "STATE", readout.powered() ? "LIVE" : "IDLE", theme, readout.state().color);
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, cardH, "HIT", boolText(readout.hitPresent(), "YES", "NO"), theme, readout.hitPresent() ? SAFE_GREEN : themeNeutral());
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, cardH, "RANGE", readout.hitPresent() ? formatCompact(readout.closestHitDistance()) : "--", theme, readout.hitPresent() ? theme.accent : theme.dim);
    }

    private static void renderLaserSensorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final LaserReadout readout = resolveLaserReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for laser data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "LASER SENSOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int mainY = y + pad + 31;
        final int mainH = Math.max(30, h / 5);
        drawMetricCardGui(g, font, x + pad, mainY, w - pad * 2, mainH, "POWER", Integer.toString(Math.round(readout.currentPower())), theme, powerColor(readout.currentPower()));
        g.drawCenteredString(font, "Current redstone output", x + w / 2, mainY + mainH + 4, theme.dim);
        final int cardsY = mainY + mainH + 18;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int cardH = Math.max(30, y + h - cardsY - pad);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "STATE", readout.powered() ? "LIVE" : "IDLE", theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, cardH, "HIT", boolText(readout.hitPresent(), "YES", "NO"), theme, readout.hitPresent() ? SAFE_GREEN : themeNeutral());
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, cardH, "RANGE", readout.hitPresent() ? formatCompact(readout.closestHitDistance()) : "--", theme, readout.hitPresent() ? theme.accent : theme.dim);
    }

    private static void renderOpticalSensorTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final OpticalReadout readout = resolveOpticalReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for optical data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "OPTICAL SENSOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int cardsY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap) / 2;
        final int topH = Math.max(18, h / 9);
        drawMetricCardTexture(image, pad, cardsY, cardW, topH, "MATCH", boolText(readout.hitPresent(), "YES", "NO"), theme, readout.hitPresent() ? SAFE_GREEN : themeNeutral());
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, topH, "POWER", boolText(readout.powered(), "ON", "OFF"), theme, readout.powered() ? POWER_ON : POWER_OFF);
        final int row2Y = cardsY + topH + gap;
        drawMetricCardTexture(image, pad, row2Y, cardW, topH, "HIT", readout.hitPresent() ? formatCompact(readout.hitDistance()) : "--", theme, readout.hitPresent() ? SAFE_GREEN : theme.dim);
        drawMetricCardTexture(image, pad + cardW + gap, row2Y, cardW, topH, "RAY", formatCompact(readout.rayDistance()), theme, theme.accent);
        final int blockY = row2Y + topH + 12;
        final int blockH = Math.max(22, h - blockY - pad);
        drawMetricCardTexture(image, pad, blockY, w - pad * 2, blockH, "BLOCK", readout.blockName(), theme, readout.hitPresent() ? theme.text : theme.dim);
    }

    private static void renderOpticalSensorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final OpticalReadout readout = resolveOpticalReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for optical data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "OPTICAL SENSOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int cardsY = y + pad + 31;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap) / 2;
        final int topH = Math.max(22, h / 9);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, topH, "MATCH", boolText(readout.hitPresent(), "YES", "NO"), theme, readout.hitPresent() ? SAFE_GREEN : themeNeutral());
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, topH, "POWER", boolText(readout.powered(), "ON", "OFF"), theme, readout.powered() ? POWER_ON : POWER_OFF);
        final int row2Y = cardsY + topH + gap;
        drawMetricCardGui(g, font, x + pad, row2Y, cardW, topH, "HIT", readout.hitPresent() ? formatCompact(readout.hitDistance()) : "--", theme, readout.hitPresent() ? SAFE_GREEN : theme.dim);
        drawMetricCardGui(g, font, x + pad + cardW + gap, row2Y, cardW, topH, "RAY", formatCompact(readout.rayDistance()), theme, theme.accent);
        final int blockY = row2Y + topH + 12;
        final int blockH = Math.max(24, y + h - blockY - pad);
        drawMetricCardGui(g, font, x + pad, blockY, w - pad * 2, blockH, "BLOCK", readout.blockName(), theme, readout.hitPresent() ? theme.text : theme.dim);
    }

    private static void renderNavigationTableTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final NavigationReadout readout = resolveNavigationReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for nav data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "NAVIGATION TABLE", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int barY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int barH = Math.max(18, h / 10);
        drawCenteredBarTexture(image, pad, barY, w - pad * 2, barH, readout.signedAngle(), 180.0f, theme, readout.state().color);
        BitmapFont.drawCentered(image, formatAngle(readout.signedAngle()), pad, barY - 14, w - pad * 2, theme.text, w >= 360 ? 2 : 1);
        BitmapFont.drawCentered(image, "Relative bearing", pad, barY + barH + 3, w - pad * 2, theme.dim, 1);
        final int cardsY = barY + barH + 18;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int rowH = Math.max(20, (h - cardsY - pad - gap) / 3);
        drawMetricCardTexture(image, pad, cardsY, cardW, rowH, "DIST", readout.hasTarget() ? formatCompact(readout.distanceToTarget()) : "--", theme, theme.accent);
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, rowH, "RATE", readout.hasTarget() ? formatSignedBlocksPerSecond(readout.distanceDelta()) : "--", theme, readout.distanceDelta() <= 0.0f ? SAFE_GREEN : ALERT_AMBER);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, rowH, "LAST", readout.hasTarget() ? formatCompact(readout.lastDistanceToTarget()) : "--", theme, theme.text);
        final int powerY = cardsY + rowH + gap;
        drawMetricCardTexture(image, pad, powerY, cardW, rowH, "UP", formatPower(readout.powerUp()), theme, powerColor(readout.powerUp()));
        drawMetricCardTexture(image, pad + cardW + gap, powerY, cardW, rowH, "NORTH", formatPower(readout.powerNorth()), theme, powerColor(readout.powerNorth()));
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, powerY, cardW, rowH, "EAST", formatPower(readout.powerEast()), theme, powerColor(readout.powerEast()));
        final int lowerY = powerY + rowH + gap;
        final int lowerH = Math.max(18, h - lowerY - pad);
        drawMetricCardTexture(image, pad, lowerY, cardW, lowerH, "DOWN", formatPower(readout.powerDown()), theme, powerColor(readout.powerDown()));
        drawMetricCardTexture(image, pad + cardW + gap, lowerY, cardW, lowerH, "SOUTH", formatPower(readout.powerSouth()), theme, powerColor(readout.powerSouth()));
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, lowerY, cardW, lowerH, "WEST", formatPower(readout.powerWest()), theme, powerColor(readout.powerWest()));
    }

    private static void renderNavigationTableGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final NavigationReadout readout = resolveNavigationReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for nav data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "NAVIGATION TABLE", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int barY = y + pad + 31;
        final int barH = Math.max(24, h / 10);
        drawCenteredBarGui(g, x + pad, barY, w - pad * 2, barH, readout.signedAngle(), 180.0f, theme, readout.state().color);
        g.drawCenteredString(font, formatAngle(readout.signedAngle()), x + w / 2, barY - 14, theme.text);
        g.drawCenteredString(font, "Relative bearing", x + w / 2, barY + barH + 4, theme.dim);
        final int cardsY = barY + barH + 18;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int rowH = Math.max(24, (y + h - cardsY - pad - gap) / 3);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, rowH, "DIST", readout.hasTarget() ? formatCompact(readout.distanceToTarget()) : "--", theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, rowH, "RATE", readout.hasTarget() ? formatSignedBlocksPerSecond(readout.distanceDelta()) : "--", theme, readout.distanceDelta() <= 0.0f ? SAFE_GREEN : ALERT_AMBER);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, rowH, "LAST", readout.hasTarget() ? formatCompact(readout.lastDistanceToTarget()) : "--", theme, theme.text);
        final int powerY = cardsY + rowH + gap;
        drawMetricCardGui(g, font, x + pad, powerY, cardW, rowH, "UP", formatPower(readout.powerUp()), theme, powerColor(readout.powerUp()));
        drawMetricCardGui(g, font, x + pad + cardW + gap, powerY, cardW, rowH, "NORTH", formatPower(readout.powerNorth()), theme, powerColor(readout.powerNorth()));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, powerY, cardW, rowH, "EAST", formatPower(readout.powerEast()), theme, powerColor(readout.powerEast()));
        final int lowerY = powerY + rowH + gap;
        final int lowerH = Math.max(24, y + h - lowerY - pad);
        drawMetricCardGui(g, font, x + pad, lowerY, cardW, lowerH, "DOWN", formatPower(readout.powerDown()), theme, powerColor(readout.powerDown()));
        drawMetricCardGui(g, font, x + pad + cardW + gap, lowerY, cardW, lowerH, "SOUTH", formatPower(readout.powerSouth()), theme, powerColor(readout.powerSouth()));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, lowerY, cardW, lowerH, "WEST", formatPower(readout.powerWest()), theme, powerColor(readout.powerWest()));
    }

    private static void renderLinkedTypewriterTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final LinkedTypewriterReadout readout = resolveLinkedTypewriterReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for typewriter data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "LINKED TYPEWRITER", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);

        final int boxX = pad;
        final int boxY = pad + BitmapFont.textHeight(titleScale) + 19;
        final int boxW = w - pad * 2;
        final int boxH = Math.max(36, h / 3);
        TextureGraphRenderer.fillRect(image, boxX, boxY, boxW, boxH, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, boxX, boxY, boxW, boxH, 1, readout.state().color);
        BitmapFont.drawString(image, "LIVE ENTRY", boxX + 4, boxY + 3, theme.dim, 1);
        final String[] lines = wrapText(readout.text(), Math.max(10, (boxW - 10) / 6), 3);
        if (lines.length == 0) {
            BitmapFont.drawCentered(image, "NO TEXT BUFFERED", boxX, boxY + boxH / 2 - 4, boxW, theme.dim, 1);
        } else {
            final int startY = boxY + 15 + Math.max(0, (boxH - 18 - lines.length * 10) / 2);
            for (int index = 0; index < lines.length; ++index) {
                BitmapFont.drawCentered(image, lines[index], boxX, startY + index * 10, boxW, theme.text, 1);
            }
        }

        final int cardsY = boxY + boxH + 10;
        final int cardGap = 6;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(22, h / 8);
        drawMetricCardTexture(image, boxX, cardsY, cardW, cardH, "POWER", readout.powered() ? "ONLINE" : "OFFLINE", theme, readout.powered() ? POWER_ON : theme.dim);
        drawMetricCardTexture(image, boxX + cardW + cardGap, cardsY, cardW, cardH, "CHARS", Integer.toString(Math.round(readout.typedLength())), theme, readout.hasText() ? SAFE_GREEN : theme.dim);
        drawMetricCardTexture(image, boxX + (cardW + cardGap) * 2, cardsY, cardW, cardH, "KEYS", Integer.toString(Math.round(readout.activeKeys())), theme, readout.activeKeys() > 0.5f ? ALERT_AMBER : readout.state().color);
        BitmapFont.drawCentered(image, truncate(readout.statusLine(), Math.max(16, boxW / 6)), boxX, cardsY + cardH + 8, boxW, theme.accent, 1);
    }

    private static void renderLinkedTypewriterGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final LinkedTypewriterReadout readout = resolveLinkedTypewriterReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for typewriter data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "LINKED TYPEWRITER", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);

        final int boxX = x + pad;
        final int boxY = y + pad + 28;
        final int boxW = w - pad * 2;
        final int boxH = Math.max(48, h / 3);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, darken(theme.graphBg, 0.9f));
        borderGui(g, boxX, boxY, boxW, boxH, readout.state().color);
        g.drawString(font, "Live entry", boxX + 4, boxY + 4, theme.dim);
        final String[] lines = wrapText(readout.text(), Math.max(12, boxW / 7), 3);
        if (lines.length == 0) {
            g.drawCenteredString(font, "No text buffered", x + w / 2, boxY + boxH / 2 - font.lineHeight / 2, theme.dim);
        } else {
            final int startY = boxY + 16 + Math.max(0, (boxH - 20 - lines.length * font.lineHeight) / 2);
            for (int index = 0; index < lines.length; ++index) {
                g.drawCenteredString(font, truncateGui(font, lines[index], boxW - 10), x + w / 2, startY + index * font.lineHeight, theme.text);
            }
        }

        final int cardsY = boxY + boxH + 12;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "POWER", readout.powered() ? "ONLINE" : "OFFLINE", theme, readout.powered() ? POWER_ON : theme.dim);
        drawMetricCardGui(g, font, x + pad + cardW + cardGap, cardsY, cardW, cardH, "CHARS", Integer.toString(Math.round(readout.typedLength())), theme, readout.hasText() ? SAFE_GREEN : theme.dim);
        drawMetricCardGui(g, font, x + pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "KEYS", Integer.toString(Math.round(readout.activeKeys())), theme, readout.activeKeys() > 0.5f ? ALERT_AMBER : readout.state().color);
        g.drawCenteredString(font, truncateGui(font, readout.statusLine(), boxW), x + w / 2, cardsY + cardH + 8, theme.accent);
    }

    private static void renderHotAirBurnerTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final HotAirBurnerReadout readout = resolveHotAirBurnerReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for hot air data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, source.sourceType.toUpperCase(), pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 96, pad + BitmapFont.textHeight(titleScale) + 1, 96, 12, readout.state().label, readout.state().color);
        final int cardsY = pad + BitmapFont.textHeight(titleScale) + 21;
        final int gap = 6;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        final int rowH = Math.max(18, (h - cardsY - pad - gap) / 2);
        drawMetricCardTexture(image, pad, cardsY, cardW, rowH, "SIGNAL", formatPower(readout.signalStrength()), theme, powerColor(readout.signalStrength()));
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, rowH, "OUTPUT", formatCompact(readout.gasOutput()), theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, rowH, "TARGET", formatCompact(readout.targetVolume()), theme, readout.state().color);
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, cardsY, cardW, rowH, "CAP", formatCompact(readout.capacityVolume()), theme, theme.text);
        final int row2Y = cardsY + rowH + gap;
        drawMetricCardTexture(image, pad, row2Y, cardW, rowH, "GAS", readout.gasTypeText(), theme, theme.accent);
        drawMetricCardTexture(image, pad + cardW + gap, row2Y, cardW, rowH, "FLOW", readout.flowValue(), theme, readout.flowColor());
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, row2Y, cardW, rowH, "FILL", readout.fillValue(), theme, readout.fillColor(theme));
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, row2Y, cardW, rowH, "LIFT", formatCompact(readout.balloonLift()), theme, readout.balloonLift() > 0.01f ? SAFE_GREEN : theme.dim);
        if (readout.powered() && !readout.canOutput()) {
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, ALERT_AMBER);
        }
    }

    private static void renderHotAirBurnerGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final HotAirBurnerReadout readout = resolveHotAirBurnerReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for hot air data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, source.sourceType, x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 112, y + pad - 2, 112, 16, readout.state().label, readout.state().color);
        final int cardsY = y + pad + 30;
        final int gap = 8;
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        final int rowH = Math.max(24, (y + h - cardsY - pad - gap) / 2);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, rowH, "SIGNAL", formatPower(readout.signalStrength()), theme, powerColor(readout.signalStrength()));
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, rowH, "OUTPUT", formatCompact(readout.gasOutput()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, rowH, "TARGET", formatCompact(readout.targetVolume()), theme, readout.state().color);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, cardsY, cardW, rowH, "CAP", formatCompact(readout.capacityVolume()), theme, theme.text);
        final int row2Y = cardsY + rowH + gap;
        drawMetricCardGui(g, font, x + pad, row2Y, cardW, rowH, "GAS", readout.gasTypeText(), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + gap, row2Y, cardW, rowH, "FLOW", readout.flowValue(), theme, readout.flowColor());
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, row2Y, cardW, rowH, "FILL", readout.fillValue(), theme, readout.fillColor(theme));
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, row2Y, cardW, rowH, "LIFT", formatCompact(readout.balloonLift()), theme, readout.balloonLift() > 0.01f ? SAFE_GREEN : theme.dim);
        if (readout.powered() && !readout.canOutput()) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, ALERT_AMBER);
        }
    }

    private static void renderContraptionInfoTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final ContraptionInfoReadout readout = resolveContraptionInfoReadout(source);
        if (readout == null || !readout.hasLiveForceData()) {
            renderUnsupportedTexture(image, theme, "Waiting for contraption force data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "CONTRAPTION INFO", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(14, (w - pad * 2 - 100) / 6)), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 98, pad + BitmapFont.textHeight(titleScale) + 1, 98, 12, readout.state().label, readout.state().color);
        final int contentY = pad + BitmapFont.textHeight(titleScale) + 20;
        final int gap = 6;
        final int solverH = Math.max(54, h / 3);
        drawContraptionSolverTexture(image, pad, contentY, w - pad * 2, solverH, readout, theme);
        final int cardsY = contentY + solverH + gap;
        final int footerH = Math.max(18, h / 8);
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        final int rowH = Math.max(18, (h - cardsY - footerH - pad - gap) / 2);
        drawMetricCardTexture(image, pad, cardsY, cardW, rowH, "THRUST", formatForce(readout.thrust()), theme, SAFE_GREEN);
        drawMetricCardTexture(image, pad + cardW + gap, cardsY, cardW, rowH, "BRAKE", formatForce(readout.reverseForce()), theme, ALERT_AMBER);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, cardsY, cardW, rowH, "WEIGHT", formatForce(readout.gravity()), theme, DANGER_RED);
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, cardsY, cardW, rowH, "SUPPORT", formatForce(readout.upwardForce()), theme, SAFE_GREEN);
        final int row2Y = cardsY + rowH + gap;
        drawMetricCardTexture(image, pad, row2Y, cardW, rowH, "LEVIT", formatForce(readout.levitationSupport()), theme, -16724737);
        drawMetricCardTexture(image, pad + cardW + gap, row2Y, cardW, rowH, "BALLOON", formatForce(readout.balloonSupport()), theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + gap) * 2, row2Y, cardW, rowH, "AERO", formatForce(readout.aeroSupport()), theme, -16724890);
        drawMetricCardTexture(image, pad + (cardW + gap) * 3, row2Y, cardW, rowH, "PROP", formatForce(readout.propulsionSupport()), theme, SAFE_GREEN);
        final int footerY = row2Y + rowH + gap;
        final String footer = "Net Vertical " + formatSignedForce(readout.verticalMargin()) + " • Forward Margin " + formatSignedForce(readout.thrustMargin());
        TextureGraphRenderer.fillRect(image, pad, footerY, w - pad * 2, footerH, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, pad, footerY, w - pad * 2, footerH, 1, readout.state().color);
        BitmapFont.drawString(image, "SOLVER", pad + 4, footerY + 3, theme.dim, 1);
        BitmapFont.drawCentered(image, truncate(footer, Math.max(18, (w - pad * 2 - 10) / 6)), pad, footerY + footerH / 2 - 4, w - pad * 2, theme.text, 1);
    }

    private static void renderContraptionInfoGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final ContraptionInfoReadout readout = resolveContraptionInfoReadout(source);
        if (readout == null || !readout.hasLiveForceData()) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for contraption force data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "CONTRAPTION INFO", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 112, y + pad - 2, 112, 16, readout.state().label, readout.state().color);
        final int contentY = y + pad + 30;
        final int gap = 8;
        final int solverH = Math.max(82, h / 3);
        drawContraptionSolverGui(g, font, x + pad, contentY, w - pad * 2, solverH, readout, theme);
        final int cardsY = contentY + solverH + gap;
        final int footerH = Math.max(22, h / 8);
        final int cardW = (w - pad * 2 - gap * 3) / 4;
        final int rowH = Math.max(24, (y + h - cardsY - footerH - pad - gap) / 2);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, rowH, "THRUST", formatForce(readout.thrust()), theme, SAFE_GREEN);
        drawMetricCardGui(g, font, x + pad + cardW + gap, cardsY, cardW, rowH, "BRAKE", formatForce(readout.reverseForce()), theme, ALERT_AMBER);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, cardsY, cardW, rowH, "WEIGHT", formatForce(readout.gravity()), theme, DANGER_RED);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, cardsY, cardW, rowH, "SUPPORT", formatForce(readout.upwardForce()), theme, SAFE_GREEN);
        final int row2Y = cardsY + rowH + gap;
        drawMetricCardGui(g, font, x + pad, row2Y, cardW, rowH, "LEVIT", formatForce(readout.levitationSupport()), theme, -16724737);
        drawMetricCardGui(g, font, x + pad + cardW + gap, row2Y, cardW, rowH, "BALLOON", formatForce(readout.balloonSupport()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 2, row2Y, cardW, rowH, "AERO", formatForce(readout.aeroSupport()), theme, -16724890);
        drawMetricCardGui(g, font, x + pad + (cardW + gap) * 3, row2Y, cardW, rowH, "PROP", formatForce(readout.propulsionSupport()), theme, SAFE_GREEN);
        final int footerY = row2Y + rowH + gap;
        final String footer = "Net Vertical " + formatSignedForce(readout.verticalMargin()) + " • Forward Margin " + formatSignedForce(readout.thrustMargin());
        g.fill(x + pad, footerY, x + w - pad, footerY + footerH, darken(theme.graphBg, 0.9f));
        borderGui(g, x + pad, footerY, w - pad * 2, footerH, readout.state().color);
        g.drawString(font, "Solver", x + pad + 4, footerY + 4, theme.dim);
        g.drawCenteredString(font, truncateGui(font, footer, w - pad * 2 - 12), x + w / 2, footerY + footerH / 2 - font.lineHeight / 2 + 3, theme.text);
    }

    private static void renderAeroFlightDeckTexture(NativeImage image, SourceSelection source, DisplayTheme theme) {
        final AeroFlightDeckReadout readout = resolveAeroFlightDeckReadout(source);
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "AERO FLIGHT DECK", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, readout.liveSources + "/" + Math.max(1, readout.boundSources) + " live", w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(readout.subTitle, Math.max(18, (w - pad * 2 - 100) / 6)), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state.label, readout.state.color);
        final int lampY = pad + BitmapFont.textHeight(titleScale) + 18;
        drawAeroSourceHealthTexture(image, pad, lampY, w - pad * 2, 12, readout, theme);
        final int lineY = lampY + 13;
        BitmapFont.drawString(image, truncate(readout.deckLine, Math.max(16, (w - pad * 2) / 6)), pad, lineY, theme.accent, 1);
        final int cardsY = lineY + 12;
        final int gap = 6;
        final int footerH = Math.max(28, h / 5);
        final int rowH = Math.max(18, (h - cardsY - footerH - pad - gap * 2) / 3);
        final int wideW = Math.max(84, (w - pad * 2 - gap * 2) / 2);
        final int rightX = pad + wideW + gap;
        final int rightW = w - pad - rightX;
        final int topSmallW = Math.max(28, (rightW - gap) / 2);
        drawAeroDriveSectionTexture(image, pad, cardsY, wideW, rowH, readout, source, theme);
        drawMetricCardTexture(image, rightX, cardsY, topSmallW, rowH, "ALTITUDE", aeroAltitudeValue(readout), theme, aeroAltitudeColor(readout, theme));
        drawMetricCardTexture(image, rightX + topSmallW + gap, cardsY, rightW - topSmallW - gap, rowH, aeroRangeLabel(readout), aeroRangeValue(readout), theme, aeroRangeColor(readout, theme));
        final int row2Y = cardsY + rowH + gap;
        final int orientW = wideW;
        final int orientH = rowH * 2 + gap;
        drawAeroOrientationTexture(image, pad, row2Y, orientW, orientH, readout, theme);
        final int sideCardW = Math.max(28, (rightW - gap) / 2);
        drawAeroMarginSectionTexture(image, rightX, row2Y, sideCardW, rowH, "VERT MARGIN",
            aeroVerticalValue(readout), aeroVerticalTag(readout), aeroVerticalNumeric(readout), aeroVerticalScale(readout), theme, aeroVerticalColor(readout, theme));
        drawAeroMarginSectionTexture(image, rightX + sideCardW + gap, row2Y, rightW - sideCardW - gap, rowH, "FORWARD",
            aeroForwardValue(readout), aeroForwardTag(readout), aeroForwardNumeric(readout), aeroForwardScale(readout), theme, aeroForwardColor(readout, theme));
        final int row3Y = row2Y + rowH + gap;
        drawAeroBridgeOrdersTexture(image, rightX, row3Y, rightW, rowH, readout, theme);
        final int footerY = row2Y + orientH + gap;
        drawAeroEngineStripTexture(image, pad, footerY, wideW, footerH, source, readout, theme);
        drawAeroShipStatusTexture(image, rightX, footerY, rightW, footerH, readout, theme);
    }

    private static void renderAeroFlightDeckGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final AeroFlightDeckReadout readout = resolveAeroFlightDeckReadout(source);
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "AERO FLIGHT DECK", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, readout.subTitle, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state.label, readout.state.color);
        final int lampY = y + pad + 22;
        drawAeroSourceHealthGui(g, font, x + pad, lampY, w - pad * 2, 14, readout, theme);
        g.drawString(font, truncateGui(font, readout.deckLine, w - pad * 2), x + pad, lampY + 14, theme.accent);
        final int cardsY = lampY + 28;
        final int gap = 8;
        final int footerH = Math.max(22, h / 7);
        final int rowH = Math.max(24, (y + h - cardsY - footerH - pad - gap * 2) / 3);
        final int wideW = Math.max(104, (w - pad * 2 - gap * 2) / 2);
        final int rightX = x + pad + wideW + gap;
        final int rightW = x + w - pad - rightX;
        final int topSmallW = Math.max(40, (rightW - gap) / 2);
        drawAeroDriveSectionGui(g, font, x + pad, cardsY, wideW, rowH, readout, source, theme);
        drawMetricCardGui(g, font, rightX, cardsY, topSmallW, rowH, "ALTITUDE", aeroAltitudeValue(readout), theme, aeroAltitudeColor(readout, theme));
        drawMetricCardGui(g, font, rightX + topSmallW + gap, cardsY, rightW - topSmallW - gap, rowH, aeroRangeLabel(readout), aeroRangeValue(readout), theme, aeroRangeColor(readout, theme));
        final int row2Y = cardsY + rowH + gap;
        final int orientW = wideW;
        final int orientH = rowH * 2 + gap;
        drawAeroOrientationGui(g, font, x + pad, row2Y, orientW, orientH, readout, theme);
        final int sideCardW = Math.max(40, (rightW - gap) / 2);
        drawAeroMarginSectionGui(g, font, rightX, row2Y, sideCardW, rowH, "VERT MARGIN",
            aeroVerticalValue(readout), aeroVerticalTag(readout), aeroVerticalNumeric(readout), aeroVerticalScale(readout), theme, aeroVerticalColor(readout, theme));
        drawAeroMarginSectionGui(g, font, rightX + sideCardW + gap, row2Y, rightW - sideCardW - gap, rowH, "FORWARD",
            aeroForwardValue(readout), aeroForwardTag(readout), aeroForwardNumeric(readout), aeroForwardScale(readout), theme, aeroForwardColor(readout, theme));
        final int row3Y = row2Y + rowH + gap;
        drawAeroBridgeOrdersGui(g, font, rightX, row3Y, rightW, rowH, readout, theme);
        final int footerY = row2Y + orientH + gap;
        drawAeroEngineStripGui(g, font, x + pad, footerY, wideW, footerH, readout, source, theme);
        drawAeroShipStatusGui(g, font, rightX, footerY, rightW, footerH, readout, theme);
    }

    private static void renderAeroFlightDeckCompactGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme) {
        final AeroFlightDeckReadout readout = resolveAeroFlightDeckReadout(source);
        final int pad = PINNED_HUD_PAD;
        final int gap = PINNED_HUD_GAP;
        final int badgeW = Math.min(PINNED_HUD_BADGE_W, Math.max(74, w / 3));
        final int titleY = y + pad - 1;
        g.drawString(font, "FLIGHT DECK", x + pad, titleY, applyPinnedHudOpacity(theme.text));
        drawBadgeGui(g, font, x + w - pad - badgeW, titleY - 1, badgeW, 14, readout.state.label, readout.state.color);
        g.drawString(font, truncateGui(font, readout.deckLine, w - pad * 2), x + pad, titleY + font.lineHeight + 2, applyPinnedHudOpacity(theme.accent));
        final String statusLine = readout.boundSources() <= 0
                ? "No aero feeds armed"
                : readout.liveSources() + " of " + readout.boundSources() + " feeds live";
        g.drawString(font, truncateGui(font, statusLine, w - pad * 2), x + pad, titleY + (font.lineHeight + 2) * 2, applyPinnedHudOpacity(theme.dim));

        final int footerH = PINNED_HUD_FOOTER_H;
        final int footerY = y + h - pad - footerH;
        final int bodyY = titleY + (font.lineHeight + 2) * 3 + 5;
        final int bodyH = Math.max(64, footerY - gap - bodyY);
        final int rightW = Math.max(110, Math.min(122, (w - pad * 2) / 3));
        final int leftW = w - pad * 2 - gap - rightW;
        final int leftX = x + pad;
        final int rightX = leftX + leftW + gap;
        final int topH = Math.max(48, (bodyH - gap) / 2);
        final int bottomY = bodyY + topH + gap;
        final int bottomH = Math.max(48, bodyH - topH - gap);
        final int topRightH = Math.max(24, (topH - gap) / 2);
        final int bottomRightH = Math.max(24, (bottomH - gap) / 2);

        drawAeroDriveCompactGui(g, font, leftX, bodyY, leftW, topH, readout, theme);
        drawPinnedReadoutCardGui(g, font, rightX, bodyY, rightW, topRightH, "Altitude", buildPinnedAeroAltitudeValue(readout), theme, aeroAltitudeColor(readout, theme), 1.15f);
        drawPinnedReadoutCardGui(g, font, rightX, bodyY + topRightH + gap, rightW, topRightH, buildPinnedAeroRangeLabel(readout), aeroRangeValue(readout), theme, aeroRangeColor(readout, theme), 1.15f);
        drawAeroOrientationCompactGui(g, font, leftX, bottomY, leftW, bottomH, readout, theme);
        drawPinnedReadoutCardGui(g, font, rightX, bottomY, rightW, bottomRightH, "Support", aeroVerticalValue(readout), theme, aeroVerticalColor(readout, theme), 1.1f);
        drawPinnedReadoutCardGui(g, font, rightX, bottomY + bottomRightH + gap, rightW, bottomRightH, "Thrust", aeroForwardValue(readout), theme, aeroForwardColor(readout, theme), 1.1f);

        final int footerCardW = (w - pad * 2 - gap) / 2;
        drawPinnedAeroEngineStripGui(g, font, x + pad, footerY, footerCardW, footerH, readout, theme);
        drawPinnedReadoutCardGui(g, font, x + pad + footerCardW + gap, footerY, w - pad * 2 - footerCardW - gap, footerH, "Guidance", buildPinnedAeroGuidanceValue(readout), theme, aeroNavColor(readout, theme), 0.95f);
    }

    private static void drawAeroDriveCompactGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.9f)));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "Speed", x + 4, y + 3, applyPinnedHudOpacity(theme.dim));
        final float valueScale = 1.9f;
        final String speedValue = truncateGui(font, aeroSpeedValue(readout), Math.max(20, Math.round((w - 12) / valueScale)));
        drawScaledCenteredStringGui(g, font, speedValue, x + w / 2, y + 14, valueScale, readout.velocity != null ? theme.text : theme.dim);
        g.drawCenteredString(font, truncateGui(font, buildPinnedAeroSpeedDetail(readout), w - 10), x + w / 2, y + h - font.lineHeight - 4, applyPinnedHudOpacity(theme.accent));
    }

    private static void drawAeroOrientationCompactGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.9f)));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "Attitude", x + 4, y + 3, applyPinnedHudOpacity(theme.dim));
        final int innerX = x + 4;
        final int innerY = y + 14;
        final int innerW = w - 8;
        final int innerH = Math.max(22, h - 30);
        final int centerX = innerX + innerW / 2;
        final int centerY = innerY + innerH / 2;
        if (readout.gimbal == null) {
            g.drawCenteredString(font, "Attitude offline", centerX, centerY - font.lineHeight / 2, applyPinnedHudOpacity(theme.text));
            g.drawCenteredString(font, "Bind a gimbal feed", centerX, centerY + font.lineHeight + 1, applyPinnedHudOpacity(theme.dim));
        } else {
            final float pitch = readout.gimbal.flightDeckPitch();
            final float bank = readout.gimbal.flightDeckBank();
            final float bankRad = (float) Math.toRadians(bank);
            final float cos = (float) Math.cos(bankRad);
            final float sin = (float) Math.sin(bankRad);
            final float normalX = -sin;
            final float normalY = cos;
            final float pitchScale = Math.max(0.8f, innerH / 38.0f);
            g.drawString(font, "Pitch " + aeroPitchValue(readout), innerX, innerY - 10, applyPinnedHudOpacity(theme.text));
            final String bankText = "Bank " + aeroBankValue(readout);
            g.drawString(font, bankText, innerX + innerW - font.width(bankText), innerY - 10, applyPinnedHudOpacity(theme.text));
            for (int step = -30; step <= 30; step += 5) {
                final float offset = (pitch - step) * pitchScale;
                final float lineCenterX = centerX + normalX * offset;
                final float lineCenterY = centerY + normalY * offset;
                final boolean major = step % 10 == 0;
                final float length = step == 0 ? innerW * 0.66f : (major ? innerW * 0.40f : innerW * 0.22f);
                final int x1 = Math.round(lineCenterX - cos * length * 0.5f);
                final int y1 = Math.round(lineCenterY - sin * length * 0.5f);
                final int x2 = Math.round(lineCenterX + cos * length * 0.5f);
                final int y2 = Math.round(lineCenterY + sin * length * 0.5f);
                drawLineGui(g, x1, y1, x2, y2, step == 0 ? theme.accent : (major ? theme.text : theme.dim));
            }
            final float trimNorm = aeroTrimCue(readout);
            final int trimY = centerY - Math.round(trimNorm * Math.max(3, innerH / 2 - 8));
            drawLineGui(g, centerX - 11, trimY, centerX + 11, trimY, aeroVerticalColor(readout, theme));
            final float planNorm = Math.max(-1.0f, Math.min(1.0f, aeroPlanAngle(readout) / 90.0f));
            final int cueX = centerX + Math.round(planNorm * Math.max(5, innerW / 2 - 12));
            drawLineGui(g, cueX, centerY + 3, cueX, innerY + innerH - 4, aeroPlanColor(readout, theme));
            drawLineGui(g, centerX - 12, centerY, centerX - 4, centerY, theme.text);
            drawLineGui(g, centerX + 4, centerY, centerX + 12, centerY, theme.text);
            drawLineGui(g, centerX, centerY - 4, centerX, centerY + 4, theme.text);
            g.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, applyPinnedHudOpacity(theme.text));
        }
        g.drawCenteredString(font, truncateGui(font, buildPinnedAeroCourseValue(readout), w - 8), x + w / 2, y + h - font.lineHeight - 4, applyPinnedHudOpacity(aeroPlanColor(readout, theme)));
    }

    private static void drawPinnedAeroEngineStripGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.9f)));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "Propulsion", x + 4, y + 3, applyPinnedHudOpacity(theme.dim));
        if (readout.engine == null) {
            g.drawCenteredString(font, "No engine feed", x + w / 2, y + h / 2 - font.lineHeight / 2 + 3, applyPinnedHudOpacity(theme.dim));
            return;
        }
        final int accent = readout.engine.state().color;
        final String rpm = truncateGui(font, MetricType.RPM.formatValue(readout.engine.generatedSpeed()), Math.max(18, w / 2 - 8));
        g.drawString(font, rpm, x + w - 4 - font.width(rpm), y + 3, applyPinnedHudOpacity(theme.text));
        final int barX = x + 5;
        final int barY = y + 14;
        final int barW = Math.max(8, w - 10);
        final int barH = 5;
        final float fill = readout.engine.infiniteFuel() ? 1.0f : Math.max(0.0f, Math.min(1.0f, readout.engine.burnPercent() / 100.0f));
        g.fill(barX, barY, barX + barW, barY + barH, applyPinnedHudOpacity(darken(theme.bg, 0.72f)));
        g.fill(barX, barY, barX + Math.max(1, Math.round(barW * fill)), barY + barH, applyPinnedHudOpacity(accent));
        g.fill(barX, barY, barX + barW, barY + 1, applyPinnedHudOpacity(darken(theme.border, 0.86f)));
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, applyPinnedHudOpacity(darken(theme.border, 0.86f)));
        final int markerX = barX + Math.max(0, Math.min(barW - 2, Math.round((barW - 2) * fill)));
        g.fill(markerX, barY - 1, markerX + 2, barY + barH + 1, applyPinnedHudOpacity(theme.text));
        final String detail = readout.engine.state().shortLabel + " | " + aeroEngineFuelText(readout.engine, true);
        g.drawCenteredString(font, truncateGui(font, detail, w - 8), x + w / 2, y + h - font.lineHeight - 2, applyPinnedHudOpacity(accent));
    }

    private static void drawAeroSourceHealthTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final String[] labels = new String[]{"ENG", "VEL", "TRM", "ALT", "NAV", "OBS", "SUP"};
        final boolean[] live = new boolean[]{
                readout.engine != null,
                readout.velocity != null,
                readout.gimbal != null || (readout.contraption != null && readout.contraption.hasLiveForceData()),
                readout.altitude != null,
                readout.navigation != null && readout.navigation.hasTarget(),
                (readout.laser != null && readout.laser.hitPresent()) || (readout.optical != null && readout.optical.hitPresent()),
                (readout.contraption != null && readout.contraption.hasLiveForceData()) || readout.liftEmitter != null
        };
        final int[] colors = new int[]{
                readout.engine != null ? readout.engine.state().color : POWER_OFF,
                readout.velocity != null ? readout.velocity.state().color : POWER_OFF,
                readout.gimbal != null ? readout.gimbal.state().color : ((readout.contraption != null && readout.contraption.hasLiveForceData()) ? readout.contraption.state().color : POWER_OFF),
                readout.altitude != null ? readout.altitude.state().color : POWER_OFF,
                readout.navigation != null ? readout.navigation.state().color : POWER_OFF,
                readout.laser != null && readout.laser.hitPresent() ? readout.laser.state().color : ((readout.optical != null && readout.optical.hitPresent()) ? readout.optical.state().color : POWER_OFF),
                readout.contraption != null && readout.contraption.hasLiveForceData() ? readout.contraption.state().color : (readout.liftEmitter != null ? readout.liftEmitter.state().color : POWER_OFF)
        };
        final int step = Math.max(18, w / labels.length);
        for (int i = 0; i < labels.length; ++i) {
            final int cellX = x + i * step;
            final int lampX = cellX + 4;
            BrassTrackerRenderer.drawLampTexture(image, lampX, y + h / 2, 4, live[i], colors[i], 0L);
            BitmapFont.drawString(image, labels[i], lampX + 8, y + 2, live[i] ? theme.text : theme.dim, 1);
        }
    }

    private static void drawAeroSourceHealthGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final String[] labels = new String[]{"ENG", "VEL", "TRM", "ALT", "NAV", "OBS", "SUP"};
        final boolean[] live = new boolean[]{
                readout.engine != null,
                readout.velocity != null,
                readout.gimbal != null || (readout.contraption != null && readout.contraption.hasLiveForceData()),
                readout.altitude != null,
                readout.navigation != null && readout.navigation.hasTarget(),
                (readout.laser != null && readout.laser.hitPresent()) || (readout.optical != null && readout.optical.hitPresent()),
                (readout.contraption != null && readout.contraption.hasLiveForceData()) || readout.liftEmitter != null
        };
        final int[] colors = new int[]{
                readout.engine != null ? readout.engine.state().color : POWER_OFF,
                readout.velocity != null ? readout.velocity.state().color : POWER_OFF,
                readout.gimbal != null ? readout.gimbal.state().color : ((readout.contraption != null && readout.contraption.hasLiveForceData()) ? readout.contraption.state().color : POWER_OFF),
                readout.altitude != null ? readout.altitude.state().color : POWER_OFF,
                readout.navigation != null ? readout.navigation.state().color : POWER_OFF,
                readout.laser != null && readout.laser.hitPresent() ? readout.laser.state().color : ((readout.optical != null && readout.optical.hitPresent()) ? readout.optical.state().color : POWER_OFF),
                readout.contraption != null && readout.contraption.hasLiveForceData() ? readout.contraption.state().color : (readout.liftEmitter != null ? readout.liftEmitter.state().color : POWER_OFF)
        };
        final int step = Math.max(22, w / labels.length);
        for (int i = 0; i < labels.length; ++i) {
            final int cellX = x + i * step;
            final int lampX = cellX + 6;
            BrassTrackerRenderer.drawLampGui(g, lampX, y + h / 2, 4, live[i], colors[i], 0L);
            g.drawString(font, labels[i], lampX + 8, y + 1, live[i] ? theme.text : theme.dim);
        }
    }

    private static void drawAeroDriveSectionTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout readout, SourceSelection source, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "DRIVE / SPEED");
        final String speedValue = aeroSpeedValue(readout);
        final int speedColor = aeroSpeedColor(readout, theme);
        final int scale = w >= 128 ? 2 : 1;
        BitmapFont.drawCentered(image, truncate(speedValue, Math.max(5, w / (6 * scale))), x, contentY + 2, w, speedColor, scale);
        final String detail = aeroDriveDetail(readout);
        BitmapFont.drawCentered(image, truncate(detail, Math.max(10, (w - 8) / 6)), x, contentY + BitmapFont.textHeight(scale) + 5, w, theme.dim, 1);
        final String burnText = readout.engine != null ? aeroEngineFuelText(readout.engine, true) : "No engine feed";
        BitmapFont.drawRight(image, truncate(burnText, Math.max(6, w / 8)), x + w - 4, y + 3, readout.engine != null ? readout.engine.state().color : theme.dim, 1);
        if (readout.velocity != null && readout.velocity.trendChannel() != null) {
            final int chartX = x + 4;
            final int chartY = Math.max(contentY + BitmapFont.textHeight(scale) + 16, y + h / 2);
            final int chartH = Math.max(6, y + h - chartY - 4);
            drawSparklineTextureOnly(image, chartX, chartY, w - 8, chartH, readout.velocity.trendChannel().getStore(), speedColor, theme.grid);
        }
    }

    private static void drawAeroDriveSectionGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, SourceSelection source, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "DRIVE / SPEED");
        final String speedValue = aeroSpeedValue(readout);
        final int speedColor = aeroSpeedColor(readout, theme);
        g.drawCenteredString(font, truncateGui(font, speedValue, w - 8), x + w / 2, contentY + 4, speedColor);
        final String detail = aeroDriveDetail(readout);
        g.drawCenteredString(font, truncateGui(font, detail, w - 8), x + w / 2, contentY + font.lineHeight + 5, theme.dim);
        final String burnText = readout.engine != null ? aeroEngineFuelText(readout.engine, true) : "No engine feed";
        g.drawString(font, truncateGui(font, burnText, w - 10), x + w - 4 - font.width(truncateGui(font, burnText, w - 10)), y + 3, readout.engine != null ? readout.engine.state().color : theme.dim);
        if (readout.velocity != null && readout.velocity.trendChannel() != null) {
            final int chartX = x + 4;
            final int chartY = Math.max(contentY + font.lineHeight * 2 + 2, y + h / 2);
            final int chartH = Math.max(8, y + h - chartY - 4);
            drawSparklineGuiOnly(g, chartX, chartY, w - 8, chartH, readout.velocity.trendChannel().getStore(), speedColor, theme.grid);
        }
    }

    private static void drawAeroMarginSectionTexture(NativeImage image, int x, int y, int w, int h, String title, String value, String tag, float numericValue, float maxAbs, DisplayTheme theme, int accentColor) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, title);
        if (tag != null && !tag.isEmpty()) {
            BitmapFont.drawRight(image, tag, x + w - 4, y + 3, theme.dim, 1);
        }
        BitmapFont.drawCentered(image, truncate(value, Math.max(5, w / 6)), x, contentY + 1, w, accentColor, 1);
        final int barY = contentY + 12;
        final int barH = Math.max(6, y + h - barY - 4);
        drawCenteredBarTexture(image, x + 4, barY, w - 8, barH, numericValue, maxAbs, theme, accentColor);
    }

    private static void drawAeroMarginSectionGui(GuiGraphics g, Font font, int x, int y, int w, int h, String title, String value, String tag, float numericValue, float maxAbs, DisplayTheme theme, int accentColor) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, title);
        if (tag != null && !tag.isEmpty()) {
            g.drawString(font, tag, x + w - 4 - font.width(tag), y + 3, theme.dim);
        }
        g.drawCenteredString(font, truncateGui(font, value, w - 8), x + w / 2, contentY + 1, accentColor);
        final int barY = contentY + font.lineHeight + 1;
        final int barH = Math.max(8, y + h - barY - 4);
        drawCenteredBarGui(g, x + 4, barY, w - 8, barH, numericValue, maxAbs, theme, accentColor);
    }

    private static void drawAeroBridgeOrdersTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "BRIDGE ORDERS");
        final int innerX = x + 4;
        final int innerY = contentY + 2;
        final int innerW = w - 8;
        final int innerH = Math.max(10, y + h - innerY - 4);
        final int splitW = Math.max(24, (innerW - 4) / 2);
        drawMetricCardTexture(image, innerX, innerY, splitW, innerH, "HELM", aeroHelmValue(readout), theme, aeroHelmColor(readout, theme));
        drawMetricCardTexture(image, innerX + splitW + 4, innerY, innerW - splitW - 4, innerH, "NAV", aeroNavValue(readout), theme, aeroNavColor(readout, theme));
    }

    private static void drawAeroBridgeOrdersGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "BRIDGE ORDERS");
        final int innerX = x + 4;
        final int innerY = contentY + 2;
        final int innerW = w - 8;
        final int innerH = Math.max(12, y + h - innerY - 4);
        final int splitW = Math.max(30, (innerW - 6) / 2);
        drawMetricCardGui(g, font, innerX, innerY, splitW, innerH, "HELM", aeroHelmValue(readout), theme, aeroHelmColor(readout, theme));
        drawMetricCardGui(g, font, innerX + splitW + 6, innerY, innerW - splitW - 6, innerH, "NAV", aeroNavValue(readout), theme, aeroNavColor(readout, theme));
    }

    private static void drawAeroEngineStripTexture(NativeImage image, int x, int y, int w, int h, SourceSelection source, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "PROPULSION");
        if (readout.engine == null) {
            BitmapFont.drawCentered(image, "No engine feed", x, y + h / 2 - 4, w, theme.dim, 1);
            return;
        }
        final int trackerX = x + 4;
        final int trackerY = contentY + 1;
        final int trackerW = w - 8;
        final int trackerH = Math.max(8, h / 3);
        BrassTrackerRenderer.drawHorizontalTexture(image, trackerX, trackerY, trackerW, trackerH, BlockPos.ZERO, trackerSlot(source, 0),
            readout.engine.burnPercent() / 100.0f, 0.0f, 0.4f, SAFE_GREEN, 0.4f, ALERT_AMBER, 0.85f, DANGER_RED, readout.engine.state().color, readout.engine.superheated(), 0L);
        BitmapFont.drawString(image, readout.engine.state().shortLabel, x + 4, y + 3, readout.engine.state().color, 1);
        BitmapFont.drawRight(image, MetricType.RPM.formatValue(readout.engine.generatedSpeed()), x + w - 4, y + 3, theme.text, 1);
        BitmapFont.drawCentered(image, aeroEngineFuelText(readout.engine, false),
            x, trackerY + trackerH + 2, w, theme.accent, 1);
    }

    private static void drawAeroEngineStripGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, SourceSelection source, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "PROPULSION");
        if (readout.engine == null) {
            g.drawCenteredString(font, "No engine feed", x + w / 2, y + h / 2 - font.lineHeight / 2, theme.dim);
            return;
        }
        final int trackerX = x + 4;
        final int trackerY = contentY + 2;
        final int trackerW = w - 8;
        final int trackerH = Math.max(10, h / 3);
        BrassTrackerRenderer.drawHorizontalGui(g, trackerX, trackerY, trackerW, trackerH, BlockPos.ZERO, trackerSlot(source, 0),
            readout.engine.burnPercent() / 100.0f, 0.0f, 0.4f, SAFE_GREEN, 0.4f, ALERT_AMBER, 0.85f, DANGER_RED, readout.engine.state().color, readout.engine.superheated(), 0L);
        g.drawString(font, readout.engine.state().shortLabel, x + 4, y + 3, readout.engine.state().color);
        final String rpm = MetricType.RPM.formatValue(readout.engine.generatedSpeed());
        g.drawString(font, rpm, x + w - 4 - font.width(rpm), y + 3, theme.text);
        final String burn = aeroEngineFuelText(readout.engine, false);
        g.drawCenteredString(font, burn, x + w / 2, trackerY + trackerH + 3, theme.accent);
    }

    private static void drawAeroShipStatusTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "SHIP STATUS");
        final int innerX = x + 4;
        final int innerW = w - 8;
        final int gap = 4;
        final int cardH = Math.max(12, h / 2 - 2);
        final int cardW = Math.max(24, (innerW - gap) / 2);
        drawMetricCardTexture(image, innerX, contentY + 1, cardW, cardH, "MASS", aeroMassValue(readout), theme, aeroMassColor(readout, theme));
        drawMetricCardTexture(image, innerX + cardW + gap, contentY + 1, innerW - cardW - gap, cardH, "SUPPORT", aeroLiftValue(readout), theme, aeroLiftColor(readout, theme));
        BitmapFont.drawCentered(image, truncate(readout.footerText, Math.max(10, (w - 8) / 6)), x, y + h - 10, w, theme.dim, 1);
    }

    private static void drawAeroShipStatusGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "SHIP STATUS");
        final int innerX = x + 4;
        final int innerW = w - 8;
        final int gap = 6;
        final int cardH = Math.max(16, h / 2 - 2);
        final int cardW = Math.max(28, (innerW - gap) / 2);
        drawMetricCardGui(g, font, innerX, contentY + 1, cardW, cardH, "MASS", aeroMassValue(readout), theme, aeroMassColor(readout, theme));
        drawMetricCardGui(g, font, innerX + cardW + gap, contentY + 1, innerW - cardW - gap, cardH, "SUPPORT", aeroLiftValue(readout), theme, aeroLiftColor(readout, theme));
        g.drawCenteredString(font, truncateGui(font, readout.footerText, w - 8), x + w / 2, y + h - font.lineHeight - 2, theme.dim);
    }

    
    private static void renderStressometerTexture(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, long tick) {
        final StatsDisplayPanelBE.DataChannel stressPctCh = findMetric(source, "stress_pct", "Stress %");
        final StatsDisplayPanelBE.DataChannel stressSuCh = findMetric(source, "stress_su", "Current Stress (SU)");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(source, "capacity_su", "Capacity (SU)");
        final StatsDisplayPanelBE.DataChannel remainingCh = findMetric(source, "remaining_su", "Remaining (SU)");
        final StatsDisplayPanelBE.DataChannel rpmCh = findMetric(source, "rpm", "RPM");
        final StatsDisplayPanelBE.DataChannel overstressedCh = findMetric(source, "overstressed", "Overstressed");
        if (stressPctCh == null && (stressSuCh == null || capacityCh == null)) {
            renderUnsupportedTexture(image, theme, "Waiting for stress data");
            return;
        }
        final float used = valueOr(stressSuCh, 0.0f);
        final float capacity = Math.max(1.0f, valueOr(capacityCh, Math.max(used, 1.0f)));
        final float pct = (stressPctCh != null) ? valueOr(stressPctCh, 0.0f) : (used / capacity * 100.0f);
        final float remaining = (remainingCh != null) ? valueOr(remainingCh, Math.max(0.0f, capacity - used)) : Math.max(0.0f, capacity - used);
        final float rpm = valueOr(rpmCh, 0.0f);
        final boolean overstressed = valueOr(overstressedCh, 0.0f) > 0.5f;
        final StateInfo state = describeStressState(pct, overstressed);
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "KINETIC LOAD", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 86, pad + BitmapFont.textHeight(titleScale) + 1, 86, 12, state.label, state.color);
        final int trackerY = pad + BitmapFont.textHeight(titleScale) + 18;
        final int trackerH = Math.max(18, h / 10);
        final int trackerX = pad;
        final int trackerW = w - pad * 2;
        final int carriageColor = state.color;
        final int slot = trackerSlot(source, 0);
        BrassTrackerRenderer.drawHorizontalTexture(image, trackerX, trackerY, trackerW, trackerH, be.getBlockPos(), slot, pct / 100.0f, 0.0f, 0.6f, SAFE_GREEN, 0.6f, ALERT_AMBER, 0.85f, DANGER_RED, carriageColor, overstressed, tick);
        BitmapFont.drawCentered(image, formatCompact(used) + " / " + formatCompact(capacity) + " SU", trackerX, trackerY - 11, trackerW, theme.text, 1);
        BitmapFont.drawCentered(image, String.format("%.1f%% LOAD", pct), trackerX, trackerY + trackerH + 3, trackerW, theme.accent, 1);
        final int cardsY = trackerY + trackerH + 18;
        final int cardGap = 6;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(22, h / 8);
        drawMetricCardTexture(image, trackerX, cardsY, cardW, cardH, "RPM", formatCompact(rpm), theme, theme.accent);
        drawMetricCardTexture(image, trackerX + cardW + cardGap, cardsY, cardW, cardH, "HEADROOM", formatCompact(remaining), theme, SAFE_GREEN);
        drawMetricCardTexture(image, trackerX + (cardW + cardGap) * 2, cardsY, cardW, cardH, "STATE", state.shortLabel, theme, state.color);
        final int lowerY = cardsY + cardH + 10;
        final int lowerH = Math.max(36, h - lowerY - pad - 4);
        renderStressLowerTexture(image, trackerX, lowerY, trackerW, lowerH, theme, be.getLatestEfficiencyScore(), (stressSuCh != null) ? stressSuCh.getStore() : null, capacity, carriageColor);
        if (overstressed) {
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, theme.warn);
        }
    }
    
    private static void renderStockpileTexture(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, long tick) {
        final StockpileReadout readout = resolveStockpileReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for stock data");
            return;
        }
        final float fill = readout.fillPercent();
        final boolean powered = readout.powered();
        final StateInfo state = readout.state();
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "THRESHOLD CONTROL", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 86, pad + BitmapFont.textHeight(titleScale) + 1, 86, 12, state.label, state.color);
        final int lampCx = w - pad - 10;
        final int lampCy = pad + BitmapFont.textHeight(titleScale) + 23;
        BrassTrackerRenderer.drawLampTexture(image, lampCx, lampCy, 5, powered, POWER_ON, tick);
        BitmapFont.drawRight(image, powered ? "OUTPUT ON" : "OUTPUT OFF", lampCx - 10, lampCy - 3, powered ? POWER_ON : theme.dim, 1);
        final int trackerY = pad + BitmapFont.textHeight(titleScale) + 28;
        final int trackerH = Math.max(18, h / 10);
        final int trackerX = pad;
        final int trackerW = w - pad * 2;
        BrassTrackerRenderer.drawHorizontalTexture(image, trackerX, trackerY, trackerW, trackerH, be.getBlockPos(), trackerSlot(source, 0), fill / 100.0f, readout.lowerPercent() / 100.0f, readout.upperPercent() / 100.0f, darken(POWER_ON, 0.8f), readout.lowerPercent() / 100.0f, -16724737, readout.upperPercent() / 100.0f, ALERT_AMBER, powered ? theme.accent : state.color, powered, tick);
        BitmapFont.drawCentered(image, String.format("%.1f%% FILL", fill), trackerX, trackerY - 11, trackerW, theme.text, 1);
        BitmapFont.drawCentered(image, formatMetricValue(readout.amountType(), readout.currentAmount()) + " / " + formatMetricValue(readout.amountType(), readout.capacityAmount()), trackerX, trackerY + trackerH + 3, trackerW, theme.dim, 1);
        final int cardsY = trackerY + trackerH + 18;
        final int cardGap = 6;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(22, h / 8);
        drawMetricCardTexture(image, trackerX, cardsY, cardW, cardH, "AMOUNT", formatMetricValue(readout.amountType(), readout.currentAmount()), theme, theme.accent);
        drawMetricCardTexture(image, trackerX + cardW + cardGap, cardsY, cardW, cardH, "LOWER", formatMetricValue(readout.amountType(), readout.lowerAmount()), theme, -16724737);
        drawMetricCardTexture(image, trackerX + (cardW + cardGap) * 2, cardsY, cardW, cardH, "UPPER", formatMetricValue(readout.amountType(), readout.upperAmount()), theme, ALERT_AMBER);
        final int sparkY = cardsY + cardH + 10;
        final int sparkH = Math.max(16, h - sparkY - pad - 4);
        drawSparklineTexture(image, trackerX, sparkY, trackerW, sparkH, readout.trendChannel().getStore(), theme, state.color, "Fill trend");
        if (powered) {
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, theme.accent);
        }
    }

    private static void renderAccumulatorTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long tick) {
        final AccumulatorReadout readout = resolveAccumulatorReadout(source);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for accumulator data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "MODULAR ACCUMULATOR", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        final int batteryX = pad;
        final int batteryY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int batteryW = w - pad * 2;
        final int batteryH = Math.max(24, Math.min(42, h / 4));
        drawAccumulatorBatteryTexture(image, batteryX, batteryY, batteryW, batteryH, readout, theme, tick);
        BitmapFont.drawCentered(image, String.format("%.1f%% CHARGE", readout.fillPercent()), batteryX, batteryY - 11, batteryW, theme.text, 1);
        BitmapFont.drawCentered(image, formatEnergy(readout.stored()) + " / " + formatEnergy(readout.capacity()), batteryX, batteryY + batteryH + 3, batteryW, theme.dim, 1);
        final int diagramY = batteryY + batteryH + 18;
        final int diagramH = Math.max(42, h - diagramY - pad);
        drawAccumulatorFlowDiagramTexture(image, pad, diagramY, w - pad * 2, diagramH, readout, theme, tick);
    }
    
    private static void renderStressometerGui(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, long tick) {
        final StatsDisplayPanelBE.DataChannel stressPctCh = findMetric(source, "stress_pct", "Stress %");
        final StatsDisplayPanelBE.DataChannel stressSuCh = findMetric(source, "stress_su", "Current Stress (SU)");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(source, "capacity_su", "Capacity (SU)");
        final StatsDisplayPanelBE.DataChannel remainingCh = findMetric(source, "remaining_su", "Remaining (SU)");
        final StatsDisplayPanelBE.DataChannel rpmCh = findMetric(source, "rpm", "RPM");
        final StatsDisplayPanelBE.DataChannel overstressedCh = findMetric(source, "overstressed", "Overstressed");
        if (stressPctCh == null && (stressSuCh == null || capacityCh == null)) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for stress data");
            return;
        }
        final float used = valueOr(stressSuCh, 0.0f);
        final float capacity = Math.max(1.0f, valueOr(capacityCh, Math.max(used, 1.0f)));
        final float pct = (stressPctCh != null) ? valueOr(stressPctCh, 0.0f) : (used / capacity * 100.0f);
        final float remaining = (remainingCh != null) ? valueOr(remainingCh, Math.max(0.0f, capacity - used)) : Math.max(0.0f, capacity - used);
        final float rpm = valueOr(rpmCh, 0.0f);
        final boolean overstressed = valueOr(overstressedCh, 0.0f) > 0.5f;
        final StateInfo state = describeStressState(pct, overstressed);
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "KINETIC LOAD", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, state.label, state.color);
        final int trackerX = x + pad;
        final int trackerY = y + pad + 28;
        final int trackerW = w - pad * 2;
        final int trackerH = Math.max(28, h / 8);
        BrassTrackerRenderer.drawHorizontalGui(g, trackerX, trackerY, trackerW, trackerH, be.getBlockPos(), trackerSlot(source, 0), pct / 100.0f, 0.0f, 0.6f, SAFE_GREEN, 0.6f, ALERT_AMBER, 0.85f, DANGER_RED, state.color, overstressed, tick);
        g.drawCenteredString(font, formatCompact(used) + " / " + formatCompact(capacity) + " SU", x + w / 2, trackerY - 12, theme.text);
        g.drawCenteredString(font, String.format("%.1f%% LOAD", pct), x + w / 2, trackerY + trackerH + 4, theme.accent);
        final int cardsY = trackerY + trackerH + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "RPM", formatCompact(rpm), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + cardGap, cardsY, cardW, cardH, "HEADROOM", formatCompact(remaining), theme, SAFE_GREEN);
        drawMetricCardGui(g, font, x + pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "STATE", state.shortLabel, theme, state.color);
        final int lowerY = cardsY + cardH + 12;
        final int lowerH = Math.max(54, y + h - lowerY - pad);
        renderStressLowerGui(g, font, x + pad, lowerY, trackerW, lowerH, theme, be.getLatestEfficiencyScore(), (stressSuCh != null) ? stressSuCh.getStore() : null, capacity, state.color);
        if (overstressed && tick / 4L % 2L == 0L) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, theme.warn);
        }
    }
    
    private static void renderStockpileGui(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, long tick) {
        final StockpileReadout readout = resolveStockpileReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for stock data");
            return;
        }
        final float fill = readout.fillPercent();
        final boolean powered = readout.powered();
        final StateInfo state = readout.state();
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "THRESHOLD CONTROL", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 150), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, state.label, state.color);
        BrassTrackerRenderer.drawLampGui(g, x + w - pad - 18, y + pad + 24, 7, powered, POWER_ON, tick);
        g.drawString(font, powered ? "OUTPUT ON" : "OUTPUT OFF", x + w - pad - 98, y + pad + 19, powered ? POWER_ON : theme.dim);
        final int trackerX = x + pad;
        final int trackerY = y + pad + 34;
        final int trackerW = w - pad * 2;
        final int trackerH = Math.max(28, h / 8);
        BrassTrackerRenderer.drawHorizontalGui(g, trackerX, trackerY, trackerW, trackerH, be.getBlockPos(), trackerSlot(source, 0), fill / 100.0f, readout.lowerPercent() / 100.0f, readout.upperPercent() / 100.0f, darken(POWER_ON, 0.8f), readout.lowerPercent() / 100.0f, -16724737, readout.upperPercent() / 100.0f, ALERT_AMBER, powered ? theme.accent : state.color, powered, tick);
        g.drawCenteredString(font, String.format("%.1f%% FILL", fill), x + w / 2, trackerY - 12, theme.text);
        g.drawCenteredString(font, formatMetricValue(readout.amountType(), readout.currentAmount()) + " / " + formatMetricValue(readout.amountType(), readout.capacityAmount()), x + w / 2, trackerY + trackerH + 4, theme.dim);
        final int cardsY = trackerY + trackerH + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "AMOUNT", formatMetricValue(readout.amountType(), readout.currentAmount()), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + cardW + cardGap, cardsY, cardW, cardH, "LOWER", formatMetricValue(readout.amountType(), readout.lowerAmount()), theme, -16724737);
        drawMetricCardGui(g, font, x + pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "UPPER", formatMetricValue(readout.amountType(), readout.upperAmount()), theme, ALERT_AMBER);
        final int sparkY = cardsY + cardH + 12;
        final int sparkH = Math.max(24, y + h - sparkY - pad);
        drawSparklineGui(g, x + pad, sparkY, trackerW, sparkH, readout.trendChannel().getStore(), font, theme, state.color, "Fill trend");
        if (powered && tick / 4L % 2L == 0L) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, theme.accent);
        }
    }

    private static void renderAccumulatorGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme, long tick) {
        final AccumulatorReadout readout = resolveAccumulatorReadout(source);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for accumulator data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "MODULAR ACCUMULATOR", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 122), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        final int batteryX = x + pad;
        final int batteryY = y + pad + 34;
        final int batteryW = w - pad * 2;
        final int batteryH = Math.max(34, Math.min(54, h / 4));
        drawAccumulatorBatteryGui(g, font, batteryX, batteryY, batteryW, batteryH, readout, theme, tick);
        g.drawCenteredString(font, String.format("%.1f%% CHARGE", readout.fillPercent()), x + w / 2, batteryY - 12, theme.text);
        g.drawCenteredString(font, formatEnergy(readout.stored()) + " / " + formatEnergy(readout.capacity()), x + w / 2, batteryY + batteryH + 4, theme.dim);
        final int diagramY = batteryY + batteryH + 18;
        final int diagramH = Math.max(66, y + h - diagramY - pad);
        drawAccumulatorFlowDiagramGui(g, font, x + pad, diagramY, w - pad * 2, diagramH, readout, theme, tick);
    }

    // ===== Capacitor Bank specialized renderer =====

    /**
     * Everything the Capacitor Bank page draws, resolved once so the texture and GUI variants can
     * never disagree about what they are showing.
     */
    private record CapacitorBankReadout(float stored, float capacity, float fillPct, int rate,
                                        int blocks, int layers, int enduranceTicks) {

        boolean charging() {
            return rate > 0;
        }

        boolean draining() {
            return rate < 0;
        }

        String stateLabel() {
            if (stored <= 0.0f) {
                return "DEPLETED";
            }
            if (fillPct >= 99.5f) {
                return "FULL";
            }
            return charging() ? "CHARGING" : draining() ? "DISCHARGING" : "HOLDING";
        }

        int stateColor(DisplayTheme theme) {
            if (stored <= 0.0f) {
                return 0xFFE04A4A;
            }
            if (charging()) {
                return 0xFF40D080;
            }
            if (draining()) {
                return 0xFFE0A030;
            }
            return theme.accent;
        }

        /** Charge colour ramp, matching the block's own face and the FE HUD language. */
        int chargeColor() {
            if (fillPct < 20.0f) {
                return 0xFFFF4A4A;
            }
            if (fillPct < 50.0f) {
                return 0xFFFFBA3C;
            }
            return 0xFF58DC7A;
        }
    }

    private static CapacitorBankReadout resolveCapacitorBankReadout(SourceSelection source) {
        final float stored = valueOr(findMetric(source, "energy_stored", "Stored FE"), -1.0f);
        if (stored < 0.0f) {
            return null;
        }
        final float capacity = Math.max(1.0f, valueOr(findMetric(source, "energy_capacity", "Capacity FE"), 1.0f));
        final float fillPct = valueOr(findMetric(source, "energy_fill_pct", "Energy %"), stored * 100.0f / capacity);
        final int rate = Math.round(valueOr(findMetric(source, "energy_net_rate", "Net FE/t"), 0.0f));
        final int blocks = Math.max(1, Math.round(valueOr(findMetric(source, "bank_blocks", "Pooled Blocks"), 1.0f)));
        final int layers = Math.max(1, Math.round(valueOr(findMetric(source, "bank_layers", "Stack Layers"), 1.0f)));
        final int endurance = Math.round(valueOr(findMetric(source, "energy_endurance", "Endurance"), 0.0f));
        return new CapacitorBankReadout(stored, capacity, fillPct, rate, blocks, layers, endurance);
    }

    private static void renderCapacitorBankTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long tick) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final CapacitorBankReadout r = resolveCapacitorBankReadout(source);
        if (r == null) {
            BitmapFont.drawCentered(image, "Waiting for bank data", 0, h / 2, w, theme.dim, 1);
            return;
        }
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "CAPACITOR BANK", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        final int subY = pad + BitmapFont.textHeight(titleScale) + 3;
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / 6)), pad, subY, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 96, subY - 2, 96, 12, r.stateLabel(), r.stateColor(theme));

        // cell stack diagram down the left: one segment per block layer, filling bottom up,
        // which is exactly how the real structure charges.
        final int bodyY = subY + 20;
        final int bodyH = Math.max(40, h - bodyY - pad - 46);
        final int stackW = Math.max(28, Math.min(64, w / 7));
        drawCellStackTexture(image, pad, bodyY, stackW, bodyH, r, theme, tick);

        // flow rail. Capped rather than a fraction of the body: on a large multiblock panel a
        // proportional rail became a huge empty band with two words floating in it.
        final int railX = pad + stackW + 10;
        final int railW = w - railX - pad;
        final int railH = Mth.clamp(bodyH / 6, 16, 34);
        drawEnergyRailTexture(image, railX, bodyY, railW, railH, r, theme, tick);

        // headline figures under the rail.
        final int figY = bodyY + railH + 8;
        BitmapFont.drawString(image, String.format("%.1f%%", r.fillPct()), railX, figY, r.chargeColor(), 2);
        BitmapFont.drawString(image, formatEnergy(r.stored()) + "  /  " + formatEnergy(r.capacity()),
                railX, figY + BitmapFont.textHeight(2) + 2, theme.text, 1);

        // metric cards along the bottom.
        final int cardsY = Math.max(figY + BitmapFont.textHeight(2) + 16, bodyY + bodyH - 30);

        // [2]
        final int graphY = figY + BitmapFont.textHeight(2) + 18;
        final int graphH = cardsY - graphY - 8;
        if (graphH > 18) {
            drawBankDistributionTexture(image, railX, graphY, railW, graphH, r, theme, tick);
        }
        final int gap = 6;
        final int cardW = (w - railX - pad - gap * 2) / 3;
        final int cardH = Math.max(24, h / 9);
        drawMetricCardTexture(image, railX, cardsY, cardW, cardH, "FLOW",
                (r.rate() >= 0 ? "+" : "-") + formatEnergy(Math.abs(r.rate())) + "/t",
                theme, r.stateColor(theme));
        drawMetricCardTexture(image, railX + cardW + gap, cardsY, cardW, cardH,
                r.charging() ? "FULL IN" : "EMPTY IN",
                r.enduranceTicks() > 0 ? fmClock(r.enduranceTicks()) : "--:--", theme, theme.accent);
        drawMetricCardTexture(image, railX + (cardW + gap) * 2, cardsY, cardW, cardH, "CELLS",
                r.blocks() + " / " + r.layers() + "L", theme, theme.accent);
    }

    private static void renderCapacitorBankGui(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               SourceSelection source, DisplayTheme theme, long tick) {
        final CapacitorBankReadout r = resolveCapacitorBankReadout(source);
        if (r == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for bank data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "CAPACITOR BANK", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 122), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, r.stateLabel(), r.stateColor(theme));

        final int bodyY = y + pad + 28;
        final int bodyH = Math.max(70, h - (bodyY - y) - pad - 52);
        final int stackW = Math.max(34, Math.min(84, w / 7));
        drawCellStackGui(g, font, x + pad, bodyY, stackW, bodyH, r, theme, tick);

        final int railX = x + pad + stackW + 14;
        final int railW = x + w - pad - railX;
        final int railH = Math.max(24, bodyH / 4);
        drawEnergyRailGui(g, railX, bodyY, railW, railH, r, theme, tick);

        final int figY = bodyY + railH + 10;
        g.pose().pushPose();
        g.pose().translate(railX, figY, 0.0f);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, String.format("%.1f%%", r.fillPct()), 0, 0, r.chargeColor());
        g.pose().popPose();
        g.drawString(font, formatEnergy(r.stored()) + "  /  " + formatEnergy(r.capacity()),
                railX, figY + 22, theme.text);

        // Rolling throughput sparkline: the page's live element, so a bank under load reads as
        // moving hardware rather than a static number.
        final int sparkY = figY + 36;
        final int sparkH = Math.max(22, bodyY + bodyH - sparkY - 4);
        if (sparkH > 16) {
            drawThroughputSparkGui(g, railX, sparkY, railW, sparkH, r, theme, tick);
        }

        final int cardsY = bodyY + bodyH + 8;
        final int gap = 8;
        final int cardW = (x + w - pad - railX - gap * 2) / 3;
        final int cardH = Math.max(28, h / 9);
        drawMetricCardGui(g, font, railX, cardsY, cardW, cardH, "FLOW",
                (r.rate() >= 0 ? "+" : "-") + formatEnergy(Math.abs(r.rate())) + "/t",
                theme, r.stateColor(theme));
        drawMetricCardGui(g, font, railX + cardW + gap, cardsY, cardW, cardH,
                r.charging() ? "FULL IN" : "EMPTY IN",
                r.enduranceTicks() > 0 ? fmClock(r.enduranceTicks()) : "--:--", theme, theme.accent);
        drawMetricCardGui(g, font, railX + (cardW + gap) * 2, cardsY, cardW, cardH, "CELLS",
                r.blocks() + " / " + r.layers() + "L", theme, theme.accent);
    }

    /**
     * The cell stack: a scale drawing of the actual structure, one segment per block layer, filling
     * from the bottom. The segment holding the charge front glows, so the diagram breathes.
     */
    private static void drawCellStackTexture(NativeImage image, int x, int y, int w, int h,
                                             CapacitorBankReadout r, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, theme.graphBg);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int layers = Math.max(1, r.layers());
        final float filled = r.fillPct() / 100.0f * layers;
        final int inset = 3;
        final int cellH = Math.max(3, (h - inset * 2) / layers);
        // [3]
        final float pulse = r.fillPct() < 20.0f ? 0.7f + 0.3f * (float) Math.sin(tick * 0.05) : 1.0f;
        for (int i = 0; i < layers; i++) {
            final int cy = y + h - inset - (i + 1) * cellH;
            final float amount = Mth.clamp(filled - i, 0.0f, 1.0f);
            final int cellW = w - inset * 2;
            TextureGraphRenderer.fillRect(image, x + inset, cy + 1, cellW, cellH - 2, 0xFF141A22);
            if (amount > 0.0f) {
                final boolean front = amount < 1.0f;
                final int colour = front ? scaleColor(r.chargeColor(), pulse) : r.chargeColor();
                TextureGraphRenderer.fillRect(image, x + inset, cy + 1, Math.max(1, Math.round(cellW * amount)), cellH - 2, colour);
            }
        }
        BitmapFont.drawCentered(image, layers + " LAYER", x, y + h + 3, w, theme.dim, 1);
    }

    private static void drawCellStackGui(GuiGraphics g, Font font, int x, int y, int w, int h,
                                         CapacitorBankReadout r, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, theme.graphBg);
        outlineGui(g, x, y, w, h, theme.border);
        final int layers = Math.max(1, r.layers());
        final float filled = r.fillPct() / 100.0f * layers;
        final int inset = 4;
        final int cellH = Math.max(4, (h - inset * 2) / layers);
        final float pulse = r.fillPct() < 20.0f ? 0.7f + 0.3f * (float) Math.sin(tick * 0.05) : 1.0f;
        for (int i = 0; i < layers; i++) {
            final int cy = y + h - inset - (i + 1) * cellH;
            final float amount = Mth.clamp(filled - i, 0.0f, 1.0f);
            final int cellW = w - inset * 2;
            g.fill(x + inset, cy + 1, x + inset + cellW, cy + cellH - 1, 0xFF141A22);
            if (amount > 0.0f) {
                final boolean front = amount < 1.0f;
                final int colour = front ? scaleColor(r.chargeColor(), pulse) : r.chargeColor();
                g.fill(x + inset, cy + 1, x + inset + Math.max(1, Math.round(cellW * amount)), cy + cellH - 1, colour);
            }
        }
    }

    /** [4] */
    private static void drawEnergyRailTexture(NativeImage image, int x, int y, int w, int h,
                                              CapacitorBankReadout r, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, theme.graphBg);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        if (r.rate() == 0) {
            BitmapFont.drawCentered(image, "NO FLOW", x, y + h / 2 - 3, w, theme.dim, 1);
            return;
        }
        final int colour = r.stateColor(theme);
        final int packetW = Math.max(6, w / 14);
        final int spacing = packetW * 3;
        final float speed = Mth.clamp(Math.abs(r.rate()) / 8192.0f, 0.05f, 0.35f) * 1.5f;
        final int dir = r.charging() ? 1 : -1;
        final int offset = (int) ((tick * speed * dir) % spacing + spacing) % spacing;
        for (int px = -spacing + offset; px < w; px += spacing) {
            final int x0 = Math.max(x + 2, x + px);
            final int x1 = Math.min(x + w - 2, x + px + packetW);
            if (x1 > x0) {
                TextureGraphRenderer.fillRect(image, x0, y + 3, x1 - x0, h - 6, colour);
            }
        }
    }

    private static void drawEnergyRailGui(GuiGraphics g, int x, int y, int w, int h,
                                          CapacitorBankReadout r, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, theme.graphBg);
        outlineGui(g, x, y, w, h, theme.border);
        if (r.rate() == 0) {
            return;
        }
        final int colour = r.stateColor(theme);
        final int packetW = Math.max(8, w / 14);
        final int spacing = packetW * 3;
        final float speed = Mth.clamp(Math.abs(r.rate()) / 8192.0f, 0.05f, 0.35f) * 1.5f;
        final int dir = r.charging() ? 1 : -1;
        final int offset = (int) ((tick * speed * dir) % spacing + spacing) % spacing;
        for (int px = -spacing + offset; px < w; px += spacing) {
            final int x0 = Math.max(x + 2, x + px);
            final int x1 = Math.min(x + w - 2, x + px + packetW);
            if (x1 > x0) {
                g.fill(x0, y + 3, x1, y + h - 3, colour);
            }
        }
    }

    /** [5] */
    private static void drawThroughputSparkGui(GuiGraphics g, int x, int y, int w, int h,
                                               CapacitorBankReadout r, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, theme.graphBg);
        outlineGui(g, x, y, w, h, theme.border);
        for (int i = 1; i < 4; i++) {
            final int gy = y + h * i / 4;
            g.fill(x + 1, gy, x + w - 1, gy + 1, theme.grid);
        }
        final float now = Mth.clamp(r.fillPct() / 100.0f, 0.0f, 1.0f);
        // Projection horizon: the endurance if we have one, else a flat line.
        final int horizon = r.enduranceTicks() > 0 ? r.enduranceTicks() : 0;
        final int colour = r.chargeColor();
        int previousY = -1;
        for (int px = 0; px < w - 2; px++) {
            final float t = px / (float) Math.max(1, w - 3);
            final float projected;
            if (horizon <= 0 || r.rate() == 0) {
                projected = now;
            } else if (r.charging()) {
                projected = Mth.clamp(now + (1.0f - now) * t, 0.0f, 1.0f);
            } else {
                projected = Mth.clamp(now - now * t, 0.0f, 1.0f);
            }
            final int py = y + h - 2 - Math.round((h - 4) * projected);
            g.fill(x + 1 + px, py, x + 2 + px, y + h - 1, scaleColor(colour, 0.10f));
            if (previousY >= 0) {
                final int lo = Math.min(previousY, py);
                final int hi = Math.max(previousY, py);
                g.fill(x + 1 + px, lo, x + 2 + px, hi + 1, colour);
            }
            previousY = py;
        }
        // No sweeping marker. It moved constantly, said nothing, and drew the eye away from the
        // one thing on this panel that does change on its own: the charge level.
    }

    /** Four fills rather than a filled rect with a hole, so nothing overlaps. */
    private static void outlineGui(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /** [6] */
    private static void drawBankDistributionTexture(NativeImage image, int x, int y, int w, int h,
                                                    CapacitorBankReadout r, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, theme.graphBg);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "CHARGE BY LAYER", x + 4, y + 3, theme.dim, 1);
        final int gy = y + BitmapFont.textHeight(1) + 7;
        final int gh = h - (gy - y) - 5;
        if (gh <= 4) {
            return;
        }
        final int layers = Math.max(1, r.layers());
        final int gap = Math.max(1, Math.min(6, w / (layers * 6)));
        final int colW = Math.max(2, (w - 8 - gap * (layers - 1)) / layers);
        final float pulse = r.fillPct() < 20.0f ? 0.75f + 0.25f * (float) Math.sin(tick * 0.05) : 1.0f;
        for (int i = 0; i < layers; i++) {
            final int cx = x + 4 + i * (colW + gap);
            final float amount = Mth.clamp(r.fillPct() / 100.0f * layers - i, 0.0f, 1.0f);
            TextureGraphRenderer.fillRect(image, cx, gy, colW, gh, 0xFF141A22);
            if (amount > 0.0f) {
                final int barH = Math.max(1, Math.round(gh * amount));
                final boolean front = amount < 1.0f;
                final int colour = front ? scaleColor(r.chargeColor(), pulse) : r.chargeColor();
                TextureGraphRenderer.fillRect(image, cx, gy + gh - barH, colW, barH, colour);
            }
        }
    }

    private static String fmClock(int ticks) {
        final int seconds = Math.max(0, ticks) / 20;
        if (seconds >= 3600) {
            return String.format("%dh%02dm", seconds / 3600, (seconds % 3600) / 60);
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static int scaleColor(int argb, float factor) {
        final int a = (argb >>> 24) & 0xFF;
        final int rr = Math.round(((argb >> 16) & 0xFF) * factor);
        final int gg = Math.round(((argb >> 8) & 0xFF) * factor);
        final int bb = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
    }

    // ===== Flow Meter (Living Fluid Conduit) specialized renderer =====

    private static void renderFlowMeterTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long tick) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int rate = Math.round(valueOr(findMetric(source, "flow_rate_mbt", "Flow Rate"), 0.0f));
        final long total = (long) valueOr(findMetric(source, "flow_total_mb", "Total Moved"), 0.0f);
        final int dirSign = Math.round(valueOr(findMetric(source, "flow_direction", "Direction"), 0.0f));
        final StatsDisplayPanelBE.DataChannel fluidCh = findMetric(source, "fluid_id", "Fluid");
        final String fluidId = (fluidCh != null && fluidCh.getLabel() != null) ? fluidCh.getLabel() : "";
        final boolean hasFluid = fluidId.contains(":");
        final int fluidColor = hasFluid ? FluidPalette.colorForFluidId(fluidId) : 0xFF335066;
        final String fluidName = hasFluid ? FluidPalette.displayNameForFluidId(fluidId) : "No Fluid";
        final boolean flowing = rate > 0 && hasFluid;
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "FLOW METER", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        final String statusLabel = !flowing ? "IDLE" : (dirSign < 0 ? "REVERSED" : "FLOWING");
        final int statusColor = !flowing ? theme.dim : (dirSign < 0 ? 0xFFE0A030 : 0xFF40D080);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, statusLabel, statusColor);
        final int condX = pad;
        final int condY = pad + BitmapFont.textHeight(titleScale) + 24;
        final int condW = w - pad * 2;
        final int condH = Math.max(26, Math.min(54, h / 4));
        drawFlowConduitTexture(image, condX, condY, condW, condH, fluidColor, rate, dirSign, flowing, tick);
        BitmapFont.drawCentered(image, rate + " mB/t   " + (rate * 20) + " mB/s", condX, condY + condH + 4, condW, flowing ? theme.text : theme.dim, 1);
        final int cardsY = condY + condH + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(26, h / 8);
        drawMetricCardTexture(image, pad, cardsY, cardW, cardH, "FLUID", truncate(fluidName, Math.max(5, cardW / 6)), theme, fluidColor);
        drawMetricCardTexture(image, pad + cardW + cardGap, cardsY, cardW, cardH, "TOTAL", fmMb(total), theme, theme.accent);
        drawMetricCardTexture(image, pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "DIR", fmDir(dirSign, flowing), theme, statusColor);
    }

    private static void renderFlowMeterGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme, long tick) {
        final int rate = Math.round(valueOr(findMetric(source, "flow_rate_mbt", "Flow Rate"), 0.0f));
        final long total = (long) valueOr(findMetric(source, "flow_total_mb", "Total Moved"), 0.0f);
        final int dirSign = Math.round(valueOr(findMetric(source, "flow_direction", "Direction"), 0.0f));
        final StatsDisplayPanelBE.DataChannel fluidCh = findMetric(source, "fluid_id", "Fluid");
        final String fluidId = (fluidCh != null && fluidCh.getLabel() != null) ? fluidCh.getLabel() : "";
        final boolean hasFluid = fluidId.contains(":");
        final int fluidColor = hasFluid ? FluidPalette.colorForFluidId(fluidId) : 0xFF335066;
        final String fluidName = hasFluid ? FluidPalette.displayNameForFluidId(fluidId) : "No Fluid";
        final boolean flowing = rate > 0 && hasFluid;
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "FLOW METER", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        final String statusLabel = !flowing ? "IDLE" : (dirSign < 0 ? "REVERSED" : "FLOWING");
        final int statusColor = !flowing ? theme.dim : (dirSign < 0 ? 0xFFE0A030 : 0xFF40D080);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, statusLabel, statusColor);
        final int condX = x + pad;
        final int condY = y + pad + 32;
        final int condW = w - pad * 2;
        final int condH = Math.max(34, Math.min(72, h / 4));
        g.drawCenteredString(font, rate + " mB/t", x + w / 2, condY - 13, theme.text);
        drawFlowConduitGui(g, condX, condY, condW, condH, fluidColor, fluidId, rate, dirSign, flowing, tick);
        g.drawCenteredString(font, (rate * 20) + " mB/s", x + w / 2, condY + condH + 4, theme.dim);
        final int cardsY = condY + condH + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap * 2) / 3;
        final int cardH = Math.max(30, h / 8);
        drawMetricCardGui(g, font, x + pad, cardsY, cardW, cardH, "FLUID", truncateGui(font, fluidName, cardW - 8), theme, fluidColor);
        drawMetricCardGui(g, font, x + pad + cardW + cardGap, cardsY, cardW, cardH, "TOTAL", fmMb(total), theme, theme.accent);
        drawMetricCardGui(g, font, x + pad + (cardW + cardGap) * 2, cardsY, cardW, cardH, "DIRECTION", fmDir(dirSign, flowing), theme, statusColor);
        final int trendY = cardsY + cardH + 12;
        final int trendH = Math.max(26, y + h - trendY - pad);
        final StatsDisplayPanelBE.DataChannel rateCh = findMetric(source, "flow_rate_mbt", "Flow Rate");
        if (rateCh != null && trendH > 16) {
            drawSparklineGui(g, x + pad, trendY, condW, trendH, rateCh.getStore(), font, theme, fluidColor, "Throughput");
        }
        if (flowing && tick / 5L % 2L == 0L) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, fluidColor);
        }
    }

    private static void drawFlowConduitTexture(NativeImage image, int x, int y, int w, int h, int fluidColor, int rate, int dirSign, boolean flowing, long tick) {
        fmFillTex(image, x, y, w, h, 0xFF0B1622);
        final int inset = 3;
        final int ix = x + inset;
        final int iy = y + inset;
        final int iw = w - inset * 2;
        final int ih = h - inset * 2;
        if (iw <= 0 || ih <= 0) {
            return;
        }
        fmFillTex(image, ix, iy, iw, ih, fmShade(fluidColor, 0.7f));
        if (flowing) {
            final int streak = BitmapFont.argbToAbgr(fmShade(fluidColor, 1.4f));
            final int speed = Math.max(1, Math.min(7, 1 + rate / 35));
            final int dir = dirSign < 0 ? -1 : 1;
            final int spacing = 16;
            final int off = (int) ((tick * speed) % spacing);
            for (int s = -spacing; s < iw + spacing; s += spacing) {
                final int base = s + (dir > 0 ? off : spacing - off);
                for (int k = 0; k < ih; k++) {
                    final int px = ix + base + (dir > 0 ? k / 2 : -(k / 2));
                    final int py = iy + k;
                    if (px >= ix && px < ix + iw && py >= iy && py < iy + ih) {
                        image.setPixelRGBA(px, py, streak);
                    }
                }
            }
        }
        fmFillTex(image, ix, iy, iw, 1, fmShade(fluidColor, 1.6f));
        fmBorderTex(image, x, y, w, h, 0xFF2A4A66);
    }

    private static void drawFlowConduitGui(GuiGraphics g, int x, int y, int w, int h, int fluidColor, String fluidId, int rate, int dirSign, boolean flowing, long tick) {
        g.fill(x, y, x + w, y + h, 0xFF0B1622);
        final int inset = 3;
        final int ix = x + inset;
        final int iy = y + inset;
        final int iw = w - inset * 2;
        final int ih = h - inset * 2;
        if (iw <= 0 || ih <= 0) {
            return;
        }
        g.fill(ix, iy, ix + iw, iy + ih, fmAlpha(fmShade(fluidColor, 0.85f), 0xCC));
        final net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = FluidPalette.stillSpriteForFluidId(fluidId);
        if (sprite != null) {
            g.enableScissor(ix, iy, ix + iw, iy + ih);
            final float cr = ((fluidColor >> 16) & 0xFF) / 255.0f;
            final float cg = ((fluidColor >> 8) & 0xFF) / 255.0f;
            final float cb = (fluidColor & 0xFF) / 255.0f;
            g.setColor(cr, cg, cb, 1.0f);
            final int tile = 16;
            final int speed = flowing ? Math.max(1, Math.min(8, 1 + rate / 30)) : 0;
            final int dir = dirSign < 0 ? -1 : 1;
            int scroll = (int) ((tick * speed * dir) % tile);
            if (scroll < 0) {
                scroll += tile;
            }
            for (int px = ix - tile - scroll; px < ix + iw + tile; px += tile) {
                for (int py = iy; py < iy + ih; py += tile) {
                    g.blit(px, py, 0, tile, tile, sprite);
                }
            }
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            g.disableScissor();
        }
        g.fill(ix, iy, ix + iw, iy + 1, fmAlpha(fmShade(fluidColor, 1.6f), 0xAA));
        borderGui(g, x, y, w, h, 0xFF2A4A66);
    }

    private static void fmFillTex(NativeImage image, int x, int y, int w, int h, int argb) {
        final int abgr = BitmapFont.argbToAbgr(argb);
        final int iw = image.getWidth();
        final int ih = image.getHeight();
        final int x1 = Math.min(iw, x + w);
        final int y1 = Math.min(ih, y + h);
        for (int yy = Math.max(0, y); yy < y1; yy++) {
            for (int xx = Math.max(0, x); xx < x1; xx++) {
                image.setPixelRGBA(xx, yy, abgr);
            }
        }
    }

    private static void fmBorderTex(NativeImage image, int x, int y, int w, int h, int argb) {
        fmFillTex(image, x, y, w, 1, argb);
        fmFillTex(image, x, y + h - 1, w, 1, argb);
        fmFillTex(image, x, y, 1, h, argb);
        fmFillTex(image, x + w - 1, y, 1, h, argb);
    }

    private static int fmShade(int argb, float f) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Math.max(0, Math.min(255, (int) (((argb >> 16) & 0xFF) * f)));
        final int gg = Math.max(0, Math.min(255, (int) (((argb >> 8) & 0xFF) * f)));
        final int b = Math.max(0, Math.min(255, (int) ((argb & 0xFF) * f)));
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private static int fmAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0xFFFFFF);
    }

    private static String fmDir(int dirSign, boolean flowing) {
        if (!flowing) {
            return "IDLE";
        }
        return dirSign < 0 ? "REVERSE" : "FORWARD";
    }

    private static String fmMb(long v) {
        final long a = Math.abs(v);
        if (a >= 1000000L) {
            return String.format("%.1fM", v / 1000000.0);
        }
        if (a >= 1000L) {
            return String.format("%.1fk", v / 1000.0);
        }
        return Long.toString(v);
    }

    /** [7] */
    private static final java.util.Set<String> SPECIALIZED_FAMILIES = java.util.Set.of(
            "kinetics.stressometer", "factory.stockpile_switch", "energy.accumulator",
            "energy.capacitor_bank", "fluid.flow_meter", "heat.induction_heater",
            "diesel.portable_engine", "diesel.engine", "diesel.process", "diesel.pumpjack",
            "diesel.burner", "flight.velocity_sensor", "flight.gimbal_sensor",
            "flight.altitude_sensor", "flight.docking_connector", "flight.steering_wheel",
            "sensor.laser", "sensor.optical", "flight.navigation_table", "text.linked_typewriter",
            "flight.air_support", "flight.contraption_info", "battle.bridge", "flight.aero_deck",
            "flight.aeroline_director");

    /** True when this source's family has a specialized page to show. */
    public static boolean hasSpecializedPage(String sourceType) {
        return SPECIALIZED_FAMILIES.contains(familyId(sourceType));
    }

    private static SourceSelection selectSource(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = groupBySource(channels);
        if (preferredSourceId != null) {
            final SourceSelection preferred = buildSelection(be, preferredSourceId, bySource.get(preferredSourceId), 0, 1);
            attachDieselFuelReserves(be, bySource, preferred);
            return preferred;
        }
        final ArrayList<SourceSelection> supported = new ArrayList<SourceSelection>();
        for (Map.Entry<String, SourceConfig> entry : be.getSourceRegistry().entrySet()) {
            final SourceSelection selection = buildSelection(be, entry.getKey(), bySource.get(entry.getKey()), supported.size(), 0);
            if (selection != null) {
                attachDieselFuelReserves(be, bySource, selection);
                supported.add(selection);
            }
        }
        // [8]
        if (supported.size() > 1) {
            final ArrayList<SourceSelection> drawable = new ArrayList<SourceSelection>();
            for (SourceSelection selection : supported) {
                if (selection != null && SPECIALIZED_FAMILIES.contains(familyId(selection))) {
                    drawable.add(selection);
                }
            }
            // If nothing has a page, leave the list alone so behaviour is unchanged.
            if (!drawable.isEmpty()) {
                supported.clear();
                supported.addAll(drawable);
                for (int i = 0; i < supported.size(); i++) {
                    supported.get(i).cycleIndex = i;
                }
            }
        }
        if (supported.isEmpty()) {
            final SourceSelection legacy = buildLegacySelection(be, bySource.get("legacy"));
            if (legacy != null) {
                supported.add(legacy);
            }
        }
        if (supported.isEmpty()) {
            return null;
        }
        if (supported.size() == 1) {
            final SourceSelection sel = supported.get(0);
            sel.cycleCount = 1;
            sel.cycleIndex = 1;
            return sel;
        }
        final int interval = be.getSettings().getCycleInterval();
        final int idx = (interval > 0) ? (int)(renderTick / interval % supported.size()) : 0;
        final SourceSelection selected = supported.get(Math.max(0, Math.min(supported.size() - 1, idx)));
        selected.cycleCount = supported.size();
        selected.cycleIndex = idx + 1;
        return selected;
    }

    private static SourceSelection selectStressometerSource(StatsDisplayPanelBE be, LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource, String preferredSourceId) {
        if (preferredSourceId != null && !preferredSourceId.isEmpty()) {
            final SourceSelection preferred = buildSelection(be, preferredSourceId, bySource.get(preferredSourceId), 1, 1);
            if (isStressometerSelection(preferred)) {
                preferred.cycleIndex = 1;
                preferred.cycleCount = 1;
                return preferred;
            }
        }
        for (Map.Entry<String, SourceConfig> entry : be.getSourceRegistry().entrySet()) {
            final SourceSelection selection = buildSelection(be, entry.getKey(), bySource.get(entry.getKey()), 1, 1);
            if (isStressometerSelection(selection)) {
                selection.cycleIndex = 1;
                selection.cycleCount = 1;
                return selection;
            }
        }
        final SourceSelection legacy = buildLegacySelection(be, bySource.get("legacy"));
        if (isStressometerSelection(legacy)) {
            legacy.cycleIndex = 1;
            legacy.cycleCount = 1;
            return legacy;
        }
        return null;
    }

    private static boolean isStressometerSelection(SourceSelection selection) {
        if (selection == null) {
            return false;
        }
        if ("Stressometer".equals(selection.sourceType)) {
            return true;
        }
        final StatsDisplayPanelBE.DataChannel stressPctCh = findMetric(selection, "stress_pct", "Stress %");
        final StatsDisplayPanelBE.DataChannel stressSuCh = findMetric(selection, "stress_su", "Current Stress (SU)");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(selection, "capacity_su", "Capacity (SU)");
        return stressPctCh != null || (stressSuCh != null && capacityCh != null);
    }

    private static SourceSelection buildAeroFlightDeckSelection(StatsDisplayPanelBE be, LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource) {
        final SourceSelection deck = new SourceSelection();
        deck.sourceId = "aero_flight_deck";
        deck.sourceType = "Aero Flight Deck";
        deck.label = "Simulated aero composite";
        deck.settings = be.getSettings();
        deck.channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        deck.cycleIndex = 1;
        deck.cycleCount = 1;
        deck.aeroSourcesByType = new LinkedHashMap<String, SourceSelection>();
        final ArrayList<SourceSelection> candidates = new ArrayList<SourceSelection>();
        for (Map.Entry<String, SourceConfig> entry : be.getSourceRegistry().entrySet()) {
            final String sourceType = entry.getValue().getSourceType();
            if (!isAeroSourceType(sourceType)) {
                continue;
            }
            final SourceSelection child = buildSelection(be, entry.getKey(), bySource.get(entry.getKey()), 0, 1);
            if (child == null) {
                continue;
            }
            attachDieselFuelReserves(be, bySource, child);
            candidates.add(child);
            ++deck.aeroSourceCount;
            if (hasLiveSamples(child.channels)) {
                ++deck.aeroLiveSourceCount;
            }
        }
        final SourceSelection authority = chooseAeroAuthority(be, candidates);
        deck.authorityLabel = authority != null ? authority.label : "";
        for (SourceSelection child : candidates) {
            final SourceSelection current = deck.aeroSourcesByType.get(child.sourceType);
            if (current == null || isBetterAeroCandidate(authority, child, current)) {
                deck.aeroSourcesByType.put(child.sourceType, child);
            }
        }
        for (SourceSelection selected : deck.aeroSourcesByType.values()) {
            deck.channels.addAll(selected.channels);
        }
        return deck;
    }

    private static SourceSelection buildAerolineDirectorSelection(StatsDisplayPanelBE be, LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource) {
        final SourceSelection director = buildAeroFlightDeckSelection(be, bySource);
        if (director == null) {
            return null;
        }
        director.sourceId = "aeroline_director";
        director.sourceType = "Aeroline Director";
        director.label = "Aeroline Director";
        return director;
    }
    
    private static SourceSelection buildSelection(StatsDisplayPanelBE be, String sourceId, List<StatsDisplayPanelBE.DataChannel> channels, int cycleIndex, int cycleCount) {
        final SourceConfig config = be.getSourceRegistry().get(sourceId);
        final String sourceType = (config != null) ? config.getSourceType() : be.getSourceType();
        if (!isSupported(sourceType)) {
            return null;
        }
        final boolean allowEmptyChannels = canRenderSourceWithoutChannels(be, config);
        if ((channels == null || channels.isEmpty()) && !allowEmptyChannels) {
            return null;
        }
        final SourceSelection selection = new SourceSelection();
        selection.sourceId = sourceId;
        selection.sourceType = sourceType;
        selection.label = ((config != null) ? config.getDisplayLabel() : sourceType);
        selection.sourcePos = (config != null) ? config.getSourcePos() : BlockPos.ZERO;
        selection.live = hasLiveSamples(channels) || allowEmptyChannels;
        selection.settings = be.getSettings();
        selection.channels = (channels != null) ? channels : new ArrayList<StatsDisplayPanelBE.DataChannel>();
        selection.cycleIndex = cycleIndex;
        selection.cycleCount = cycleCount;
        return selection;
    }

    private static void attachDieselFuelReserves(StatsDisplayPanelBE be, LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource, SourceSelection selection) {
        if (selection == null || !isDieselEngineSourceType(selection.sourceType)) {
            return;
        }
        selection.dieselReserveSources.clear();
        for (Map.Entry<String, SourceConfig> entry : be.getSourceRegistry().entrySet()) {
            final String reserveSourceId = entry.getKey();
            final SourceConfig reserveConfig = entry.getValue();
            if (reserveSourceId.equals(selection.sourceId) || reserveConfig == null || !isDieselFuelReserveSourceType(reserveConfig.getSourceType())) {
                continue;
            }
            final List<StatsDisplayPanelBE.DataChannel> reserveChannels = bySource.get(reserveSourceId);
            if (reserveChannels == null || reserveChannels.isEmpty()) {
                continue;
            }
            final SourceSelection reserve = new SourceSelection();
            reserve.sourceId = reserveSourceId;
            reserve.sourceType = reserveConfig.getSourceType();
            reserve.label = reserveConfig.getDisplayLabel();
            reserve.sourcePos = reserveConfig.getSourcePos();
            reserve.live = hasLiveSamples(reserveChannels);
            reserve.settings = be.getSettings();
            reserve.channels = reserveChannels;
            selection.dieselReserveSources.add(reserve);
        }
    }

    public static boolean canRenderSourceWithoutChannels(StatsDisplayPanelBE be, String sourceId) {
        if (be == null || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        return canRenderSourceWithoutChannels(be, be.getSourceRegistry().get(sourceId));
    }

    private static boolean canRenderSourceWithoutChannels(StatsDisplayPanelBE be, SourceConfig config) {
        return be != null
                && config != null
                && isBattleSourceType(config.getSourceType())
                && config.getSourcePos() != null
                && findBattleScreenLink(be, config.getSourcePos()) != null;
    }
    
    private static SourceSelection buildLegacySelection(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels) {
        if (channels == null || channels.isEmpty() || !isSupported(be.getSourceType())) {
            return null;
        }
        final SourceSelection selection = new SourceSelection();
        selection.sourceId = "legacy";
        selection.sourceType = be.getSourceType();
        selection.label = be.getSourceType();
        selection.sourcePos = be.getBlockPos();
        selection.live = hasLiveSamples(channels);
        selection.settings = be.getSettings();
        selection.channels = channels;
        selection.cycleIndex = 1;
        selection.cycleCount = 1;
        return selection;
    }
    
    /** [9] */
    private static boolean isSupported(String sourceType) {
        return SPECIALIZED_FAMILIES.contains(familyId(sourceType));
    }

    private static boolean isBattleSourceType(String sourceType) {
        return "battle.bridge".equals(familyId(sourceType));
    }

    public static boolean isAeroSourceType(String sourceType) {
        return switch (familyId(sourceType)) {
            case "diesel.portable_engine", "diesel.engine", "flight.velocity_sensor", "flight.gimbal_sensor",
                 "flight.altitude_sensor", "flight.docking_connector", "flight.steering_wheel", "sensor.laser",
                 "sensor.optical", "flight.navigation_table", "text.linked_typewriter", "flight.air_support",
                 "flight.contraption_info" -> true;
            default -> false;
        };
    }

    private static boolean isDieselEngineSourceType(String sourceType) {
        return "diesel.engine".equals(familyId(sourceType));
    }

    private static boolean isDieselProcessSourceType(String sourceType) {
        final String familyId = familyId(sourceType);
        return "diesel.process".equals(familyId) || "diesel.pumpjack".equals(familyId) || "diesel.burner".equals(familyId);
    }

    private static boolean isDieselFuelReserveSourceType(String sourceType) {
        return "diesel.reserve".equals(familyId(sourceType));
    }

    private static String familyId(String sourceType) {
        return CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId();
    }
    
    private static LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> groupBySource(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = new LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>>();
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final String sourceId = extractSourceId(ch.getId());
            bySource.computeIfAbsent(sourceId, k -> new ArrayList()).add(ch);
        }
        return bySource;
    }
    
    private static String extractSourceId(String channelId) {
        if (!channelId.startsWith("src_")) {
            return "legacy";
        }
        final String rest = channelId.substring(4);
        int idx = rest.indexOf("_auto_");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        idx = rest.indexOf("_line");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        return rest;
    }
    
    // ===== Induction Heater (kinetic forge) specialized renderer =====

    private record InductionHeaterReadout(int tier, String tierLabel, int tierColor, float chargePct,
                                          float drawSu, float rpm, boolean hasCoil, float coilHealthPct,
                                          float coilSeconds, boolean cooking, boolean cookDone, float cookPct,
                                          float cookSeconds, String cookItem, int cookCount) {}

    private static int heaterTierColor(int tier) {
        return switch (tier) {
            case 3 -> 0xFF33D6FF; // superheated - aqua
            case 2 -> 0xFFFFB020; // kindled - gold
            case 1 -> 0xFFE0552F; // warm - ember red
            default -> 0xFF6A7078; // idle - slate
        };
    }

    private static String heaterTimeText(float seconds) {
        if (seconds < -0.5f) {
            return "\u221E";
        }
        final int total = Math.max(0, Math.round(seconds));
        return String.format("%d:%02d", total / 60, total % 60);
    }

    private static InductionHeaterReadout resolveInductionHeaterReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel heatCh = findMetric(source, "heat_level", "Heat Level");
        final StatsDisplayPanelBE.DataChannel chargeCh = findMetric(source, "charge_pct", "Charge %");
        final StatsDisplayPanelBE.DataChannel drawCh = findMetric(source, "draw_su", "SU Draw");
        final StatsDisplayPanelBE.DataChannel rpmCh = findMetric(source, "rpm", "RPM");
        if (heatCh == null && chargeCh == null && drawCh == null) {
            return null;
        }
        final int tier = Math.max(0, Math.min(3, Math.round(valueOr(heatCh, 0.0f))));
        final String tierLabel = switch (tier) {
            case 3 -> "SUPERHEATED";
            case 2 -> "KINDLED";
            case 1 -> "WARM";
            default -> "IDLE";
        };
        final StatsDisplayPanelBE.DataChannel coilPresentCh = findMetric(source, "coil_present", "Coil");
        final StatsDisplayPanelBE.DataChannel coilHealthCh = findMetric(source, "coil_health_pct", "Coil Health");
        final StatsDisplayPanelBE.DataChannel coilSecCh = findMetric(source, "coil_seconds", "Coil Time");
        final StatsDisplayPanelBE.DataChannel cookingCh = findMetric(source, "cooking", "Cooking");
        final StatsDisplayPanelBE.DataChannel cookDoneCh = findMetric(source, "cook_done", "Ready");
        final StatsDisplayPanelBE.DataChannel cookPctCh = findMetric(source, "cook_pct", "Cook %");
        final StatsDisplayPanelBE.DataChannel cookSecCh = findMetric(source, "cook_seconds", "Cook Time");
        final StatsDisplayPanelBE.DataChannel cookItemCh = findMetric(source, "cook_item", "Cook Item");
        return new InductionHeaterReadout(tier, tierLabel, heaterTierColor(tier),
                valueOr(chargeCh, 0.0f), valueOr(drawCh, 0.0f), valueOr(rpmCh, 0.0f),
                valueOr(coilPresentCh, 0.0f) > 0.5f, valueOr(coilHealthCh, 0.0f), valueOr(coilSecCh, -1.0f),
                valueOr(cookingCh, 0.0f) > 0.5f, valueOr(cookDoneCh, 0.0f) > 0.5f,
                valueOr(cookPctCh, 0.0f), valueOr(cookSecCh, -1.0f),
                cookItemCh != null ? labelOr(cookItemCh, "") : "", Math.max(0, Math.round(valueOr(cookItemCh, 0.0f))));
    }

    private static boolean heaterHasCook(InductionHeaterReadout r) {
        return r.cookCount() > 0 && !"Empty".equalsIgnoreCase(r.cookItem());
    }

    private static int heaterHealthColor(float pct) {
        return pct > 50.0f ? 0xFF62D962 : pct > 20.0f ? 0xFFFFB020 : 0xFFE04030;
    }

    // in-world texture variant

    private static void renderInductionHeaterTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long tick) {
        final InductionHeaterReadout r = resolveInductionHeaterReadout(source);
        if (r == null) {
            renderUnsupportedTexture(image, theme, "Waiting for heater data");
            return;
        }
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, "INDUCTION HEATER", pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, cycleText(source), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(source.label, Math.max(12, (w - pad * 2) / (6 * Math.max(1, titleScale)))),
                pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 104, pad + BitmapFont.textHeight(titleScale) + 1, 104, 12, r.tierLabel(), r.tierColor());

        final int topY = pad + BitmapFont.textHeight(titleScale) + 22;
        final int coreR = Math.max(22, Math.min((h - topY - 62) / 2, w / 6));
        final int coreCx = pad + coreR + 6;
        final int coreCy = topY + coreR + 2;
        drawInductionCoilTexture(image, coreCx, coreCy, coreR, r, theme, tick);
        BitmapFont.drawCentered(image, String.format("%.0f%%", r.chargePct()), coreCx - coreR, coreCy + coreR + 6, coreR * 2, r.tierColor(), 1);

        final int infoX = coreCx + coreR + 16;
        final int infoW = w - pad - infoX;
        if (infoW >= 60) {
            int iy = topY + 2;
            BitmapFont.drawString(image, "SU DRAW", infoX, iy, theme.dim, 1);
            BitmapFont.drawString(image, formatCompact(r.drawSu()) + " SU", infoX, iy + 11, theme.text, (infoW >= 130) ? 2 : 1);
            iy += (infoW >= 130) ? 34 : 24;
            BitmapFont.drawString(image, "@ " + Math.round(r.rpm()) + " RPM", infoX, iy, theme.dim, 1);
            iy += 16;
            drawHeaterMeterTexture(image, infoX, iy, infoW, 6, r.chargePct() / 100.0f, r.tierColor(), theme);
            BitmapFont.drawString(image, "THERMAL CHARGE", infoX, iy + 9, theme.dim, 1);
        }

        final int cardsY = coreCy + coreR + 18;
        final int cardGap = 8;
        final int cardW = (w - pad * 2 - cardGap) / 2;
        final int cardH = Math.max(28, h - cardsY - pad);
        if (cardH >= 24 && cardW >= 40) {
            drawHeaterCoilCardTexture(image, pad, cardsY, cardW, cardH, r, theme);
            drawHeaterCookCardTexture(image, pad + cardW + cardGap, cardsY, cardW, cardH, r, theme, tick);
        }
    }

    private static void drawHeaterMeterTexture(NativeImage image, int x, int y, int w, int h, float frac, int color, DisplayTheme theme) {
        frac = Math.max(0.0f, Math.min(1.0f, frac));
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.8f));
        TextureGraphRenderer.fillRect(image, x, y, Math.round(w * frac), h, color);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, darken(theme.border, 0.9f));
    }

    private static void drawHeaterCoilCardTexture(NativeImage image, int x, int y, int w, int h, InductionHeaterReadout r, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.92f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "INDUCTION COIL", x + 4, y + 4, theme.dim, 1);
        if (!r.hasCoil()) {
            BitmapFont.drawCentered(image, "NO COIL", x, y + h / 2 - 3, w, 0xFFB84040, 1);
            return;
        }
        final int healthColor = heaterHealthColor(r.coilHealthPct());
        BitmapFont.drawString(image, String.format("%.0f%%", r.coilHealthPct()), x + 4, y + 15, healthColor, 1);
        drawHeaterMeterTexture(image, x + 4, y + 26, w - 8, 6, r.coilHealthPct() / 100.0f, healthColor, theme);
        final String time = r.coilSeconds() < -0.5f ? "holds" : heaterTimeText(r.coilSeconds()) + " left";
        BitmapFont.drawString(image, time, x + 4, y + 36, theme.text, 1);
    }

    private static void drawHeaterCookCardTexture(NativeImage image, int x, int y, int w, int h, InductionHeaterReadout r, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.92f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "COOKING", x + 4, y + 4, theme.dim, 1);
        if (!heaterHasCook(r)) {
            BitmapFont.drawCentered(image, "EMPTY PLATE", x, y + h / 2 - 3, w, theme.dim, 1);
            return;
        }
        BitmapFont.drawString(image, truncate(r.cookCount() + "x " + r.cookItem(), Math.max(6, (w - 8) / 6)), x + 4, y + 15, theme.text, 1);
        if (r.cookDone()) {
            BitmapFont.drawString(image, "READY", x + 4, y + 26, (tick / 8L % 2L == 0L) ? 0xFF62D962 : 0xFF3AA53A, 1);
        } else {
            drawHeaterMeterTexture(image, x + 4, y + 26, w - 8, 6, r.cookPct() / 100.0f, r.tierColor(), theme);
            final String time = r.cookSeconds() < -0.5f ? "no heat" : heaterTimeText(r.cookSeconds()) + " left";
            BitmapFont.drawString(image, String.format("%.0f%%  %s", r.cookPct(), time), x + 4, y + 36, theme.dim, 1);
        }
    }

    private static void drawInductionCoilTexture(NativeImage image, int cx, int cy, int radius, InductionHeaterReadout r, DisplayTheme theme, long tick) {
        final float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.18);
        TextureGraphRenderer.fillCircle(image, cx, cy, radius + 3, darken(theme.graphBg, 0.62f));
        if (r.tier() >= 1) {
            final int glowR = Math.max(4, (int) (radius * (0.35f + 0.15f * pulse)));
            final int glowA = Math.min(220, Math.round(70 + 110 * pulse * (r.tier() / 3.0f)));
            TextureGraphRenderer.fillCircle(image, cx, cy, glowR, TextureGraphRenderer.withAlpha(r.tierColor(), glowA));
        }
        final int copper = 0xFFB87333;
        final int rings = Math.max(3, radius / 6);
        for (int i = 0; i < rings; i++) {
            final int rr = radius - 3 - i * ((radius - 6) / rings);
            if (rr < 4) {
                break;
            }
            drawHeaterRingTexture(image, cx, cy, rr, darken(copper, (i % 2 == 0) ? 0.75f : 0.5f));
        }
        final float chargeFrac = Math.max(0.0f, Math.min(1.0f, r.chargePct() / 100.0f));
        if (chargeFrac > 0.0f) {
            final double sweep = chargeFrac * 2 * Math.PI;
            final int steps = Math.max(12, (int) (sweep * radius));
            for (int s = 0; s <= steps; s++) {
                final double th = sweep * s / steps;
                for (int ro = 0; ro < 3; ro++) {
                    final int px = cx + (int) Math.round(Math.sin(th) * (radius - ro));
                    final int py = cy - (int) Math.round(Math.cos(th) * (radius - ro));
                    TextureGraphRenderer.setPixelBlended(image, px, py, r.tierColor());
                }
            }
        }
        final int coreColor = r.tier() >= 1 ? brighten(r.tierColor(), 1.0f + 0.3f * pulse) : darken(theme.border, 0.85f);
        TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(3, radius / 6), coreColor);
        if (r.tier() >= 2) {
            for (int i = 0; i < 8; i++) {
                final double a = i * (Math.PI / 4.0) + tick * 0.05;
                final int span = (int) ((tick * 2 + i * 7) % Math.max(6, radius));
                final int hx = cx + (int) (Math.cos(a) * (radius * 0.5));
                final int hy = cy - span;
                TextureGraphRenderer.setPixelBlended(image, hx, hy, TextureGraphRenderer.withAlpha(r.tierColor(), Math.max(30, 180 - span * 6)));
            }
        }
    }

    private static void drawHeaterRingTexture(NativeImage image, int cx, int cy, int rr, int color) {
        final int steps = Math.max(16, (int) (2 * Math.PI * rr));
        for (int s = 0; s < steps; s++) {
            final double th = 2 * Math.PI * s / steps;
            final int px = cx + (int) Math.round(Math.cos(th) * rr);
            final int py = cy + (int) Math.round(Math.sin(th) * rr);
            TextureGraphRenderer.setPixelBlended(image, px, py, color);
        }
    }

    // config-GUI variant (full dashboard)

    private static void renderInductionHeaterGui(GuiGraphics g, Font font, int x, int y, int w, int h, SourceSelection source, DisplayTheme theme, long tick) {
        final InductionHeaterReadout r = resolveInductionHeaterReadout(source);
        if (r == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for heater data");
            return;
        }
        final int pad = Math.max(10, w / 28);
        g.drawString(font, "INDUCTION HEATER", x + pad, y + pad - 2, theme.text);
        g.drawString(font, truncateGui(font, source.label, w - pad * 2 - 130), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 122, y + pad - 2, 122, 16, r.tierLabel(), r.tierColor());

        final int coreR = Math.max(28, Math.min(h / 5, w / 8));
        final int coreCx = x + w / 2;
        final int coreCy = y + pad + 26 + coreR;
        drawInductionCoilGui(g, coreCx, coreCy, coreR, r, theme, tick);
        g.drawCenteredString(font, String.format("%.0f%% CHARGE", r.chargePct()), coreCx, coreCy + coreR + 6, r.tierColor());
        g.drawCenteredString(font, formatCompact(r.drawSu()) + " SU  @  " + Math.round(r.rpm()) + " RPM", coreCx, coreCy + coreR + 18, theme.text);

        final int midY = coreCy + coreR + 34;
        final int midH = Math.max(40, (y + h - midY - pad) - 30);
        final int colGap = 10;
        final int colW = (w - pad * 2 - colGap) / 2;
        drawHeaterCoilPanelGui(g, font, x + pad, midY, colW, midH, r, theme);
        drawHeaterCookPanelGui(g, font, x + pad + colW + colGap, midY, colW, midH, r, theme, tick);

        final int trendY = midY + midH + 10;
        final int trendH = Math.max(20, y + h - trendY - pad);
        final StatsDisplayPanelBE.DataChannel drawCh = findMetric(source, "draw_su", "SU Draw");
        if (drawCh != null && trendH > 16) {
            drawSparklineGui(g, x + pad, trendY, w - pad * 2, trendH, drawCh.getStore(), font, theme, r.tierColor(), "SU draw");
        }
        if (r.tier() >= 3 && tick / 5L % 2L == 0L) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, r.tierColor());
        }
    }

    private static void drawHeaterCoilPanelGui(GuiGraphics g, Font font, int x, int y, int w, int h, InductionHeaterReadout r, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "INDUCTION COIL", x + 5, y + 4, theme.dim);
        if (!r.hasCoil()) {
            g.drawCenteredString(font, "NO COIL", x + w / 2, y + h / 2 - 4, 0xFFB84040);
            g.drawCenteredString(font, "Warm only", x + w / 2, y + h / 2 + 8, theme.dim);
            return;
        }
        final int healthColor = heaterHealthColor(r.coilHealthPct());
        g.drawString(font, String.format("%.0f%% health", r.coilHealthPct()), x + 5, y + 18, healthColor);
        final int barY = y + 32;
        g.fill(x + 5, barY, x + w - 5, barY + 8, darken(theme.graphBg, 0.75f));
        g.fill(x + 5, barY, x + 5 + Math.round((w - 10) * Math.max(0.0f, Math.min(1.0f, r.coilHealthPct() / 100.0f))), barY + 8, healthColor);
        borderGui(g, x + 5, barY, w - 10, 8, darken(theme.border, 0.9f));
        final String time = r.coilSeconds() < -0.5f ? "Holds (not wearing)" : heaterTimeText(r.coilSeconds()) + " remaining";
        g.drawString(font, time, x + 5, barY + 14, theme.text);
    }

    private static void drawHeaterCookPanelGui(GuiGraphics g, Font font, int x, int y, int w, int h, InductionHeaterReadout r, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "COOKING", x + 5, y + 4, theme.dim);
        if (!heaterHasCook(r)) {
            g.drawCenteredString(font, "Empty plate", x + w / 2, y + h / 2 - 4, theme.dim);
            return;
        }
        g.drawString(font, truncateGui(font, r.cookCount() + "x " + r.cookItem(), w - 10), x + 5, y + 18, theme.text);
        final int barY = y + 32;
        if (r.cookDone()) {
            final int c = (tick / 8L % 2L == 0L) ? 0xFF62D962 : 0xFF3AA53A;
            g.fill(x + 5, barY, x + w - 5, barY + 8, c);
            borderGui(g, x + 5, barY, w - 10, 8, 0xFF62D962);
            g.drawString(font, "READY TO COLLECT", x + 5, barY + 14, 0xFF62D962);
        } else {
            g.fill(x + 5, barY, x + w - 5, barY + 8, darken(theme.graphBg, 0.75f));
            g.fill(x + 5, barY, x + 5 + Math.round((w - 10) * Math.max(0.0f, Math.min(1.0f, r.cookPct() / 100.0f))), barY + 8, r.tierColor());
            borderGui(g, x + 5, barY, w - 10, 8, darken(theme.border, 0.9f));
            final String time = r.cookSeconds() < -0.5f ? "paused (no heat)" : heaterTimeText(r.cookSeconds()) + " left";
            g.drawString(font, String.format("%.0f%%  %s", r.cookPct(), time), x + 5, barY + 14, theme.dim);
        }
    }

    private static void drawInductionCoilGui(GuiGraphics g, int cx, int cy, int radius, InductionHeaterReadout r, DisplayTheme theme, long tick) {
        final float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.18);
        fillDiscGui(g, cx, cy, radius + 3, darken(theme.graphBg, 0.6f));
        if (r.tier() >= 1) {
            final int glowR = Math.max(4, (int) (radius * (0.4f + 0.2f * pulse)));
            fillDiscGui(g, cx, cy, glowR, heaterWithAlpha(r.tierColor(), Math.min(210, Math.round(60 + 120 * pulse * (r.tier() / 3.0f)))));
        }
        final int copper = 0xFFB87333;
        final int rings = Math.max(3, radius / 7);
        for (int i = 0; i < rings; i++) {
            final int rr = radius - 3 - i * ((radius - 6) / rings);
            if (rr < 4) {
                break;
            }
            drawRingGui(g, cx, cy, rr, darken(copper, (i % 2 == 0) ? 0.8f : 0.55f));
        }
        final float chargeFrac = Math.max(0.0f, Math.min(1.0f, r.chargePct() / 100.0f));
        if (chargeFrac > 0.0f) {
            final double sweep = chargeFrac * 2 * Math.PI;
            final int steps = Math.max(24, (int) (sweep * radius / 2));
            for (int s = 0; s <= steps; s++) {
                final double th = sweep * s / steps;
                final int px = cx + (int) Math.round(Math.sin(th) * radius);
                final int py = cy - (int) Math.round(Math.cos(th) * radius);
                g.fill(px - 1, py - 1, px + 2, py + 2, r.tierColor());
            }
        }
        fillDiscGui(g, cx, cy, Math.max(3, radius / 6), r.tier() >= 1 ? r.tierColor() : darken(theme.border, 0.85f));
    }

    private static void fillDiscGui(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            final int dx = (int) Math.sqrt((double) radius * radius - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawRingGui(GuiGraphics g, int cx, int cy, int rr, int color) {
        final int steps = Math.max(24, (int) (2 * Math.PI * rr));
        for (int s = 0; s < steps; s++) {
            final double th = 2 * Math.PI * s / steps;
            final int px = cx + (int) Math.round(Math.cos(th) * rr);
            final int py = cy + (int) Math.round(Math.sin(th) * rr);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static int heaterWithAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private static StatsDisplayPanelBE.DataChannel findMetric(SourceSelection source, String metricId, String label) {
        return findMetric(source, metricId, new String[] { label });
    }

    private static StatsDisplayPanelBE.DataChannel findMetric(SourceSelection source, String metricId, String... labels) {
        if (source == null || source.channels == null || source.channels.isEmpty()) {
            return null;
        }
        for (StatsDisplayPanelBE.DataChannel ch : source.channels) {
            if (ch.getId().contains("_auto_" + metricId)) {
                return ch;
            }
        }
        for (StatsDisplayPanelBE.DataChannel ch2 : source.channels) {
            for (String label : labels) {
                if (ch2.getLabel().equalsIgnoreCase(label)) {
                    return ch2;
                }
            }
        }
        return null;
    }

    private static SourceSelection getAeroSource(SourceSelection source, String sourceType) {
        if (source == null) {
            return null;
        }
        if (sourceType.equals(source.sourceType)) {
            return source;
        }
        return source.aeroSourcesByType.get(sourceType);
    }

    private static boolean hasLiveSamples(List<StatsDisplayPanelBE.DataChannel> channels) {
        if (channels == null) {
            return false;
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel != null && channel.getStore().getSampleCount() > 0) {
                return true;
            }
        }
        return false;
    }

    private static int selectionSignalScore(SourceSelection source) {
        if (source == null || source.channels == null) {
            return 0;
        }
        int score = source.channels.size() * 8;
        for (StatsDisplayPanelBE.DataChannel channel : source.channels) {
            if (channel != null) {
                score += Math.min(64, channel.getStore().getSampleCount());
            }
        }
        return score;
    }

    private static SourceSelection chooseAeroAuthority(StatsDisplayPanelBE be, List<SourceSelection> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        final String preferredSourceId = be.getSettings().getPreferredSourceId();
        if (preferredSourceId != null && !preferredSourceId.isBlank()) {
            for (SourceSelection candidate : candidates) {
                if (preferredSourceId.equals(candidate.sourceId)) {
                    return candidate;
                }
            }
        }
        SourceSelection best = null;
        int bestScore = Integer.MIN_VALUE;
        for (SourceSelection candidate : candidates) {
            final int score = selectionSignalScore(candidate) + aeroAuthorityWeight(candidate.sourceType) + (candidate.live ? 120 : 0);
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isBetterAeroCandidate(SourceSelection authority, SourceSelection candidate, SourceSelection current) {
        return aeroCandidateScore(authority, candidate) > aeroCandidateScore(authority, current);
    }

    private static int aeroCandidateScore(SourceSelection authority, SourceSelection source) {
        if (source == null) {
            return Integer.MIN_VALUE;
        }
        int score = selectionSignalScore(source) + (source.live ? 120 : 0);
        if (authority != null && authority.sourcePos != null && source.sourcePos != null) {
            final int distancePenalty = (int)Math.min(400.0, authority.sourcePos.distSqr(source.sourcePos));
            score -= distancePenalty;
            if (authority.sourceId.equals(source.sourceId)) {
                score += 200;
            }
        }
        return score;
    }

    private static int aeroAuthorityWeight(String sourceType) {
        if ("Gimbal Sensor".equals(sourceType)) {
            return 600;
        }
        if ("Velocity Sensor".equals(sourceType)) {
            return 420;
        }
        if ("Navigation Table".equals(sourceType) || "Docking Connector".equals(sourceType) || "Steering Wheel".equals(sourceType)) {
            return 260;
        }
        if ("Contraption Info".equals(sourceType)) {
            return 220;
        }
        return 0;
    }
    
    private static void drawTextureChrome(NativeImage image, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        TextureGraphRenderer.fillRect(image, 0, 0, w, h, theme.graphBg);
        TextureGraphRenderer.drawBorder(image, 0, 0, w, h, 2, theme.border);
        if (theme == DisplayTheme.CREATE) {
            final int margin = Math.max(3, w / 64) + 2;
            CreateThemeDecorator.decorate(image, theme.graphBg, margin, margin, w - margin * 2, h - margin * 2);
        }
    }

    // phase 5: Battle Tactical V2 smoothing & visual stability
    private static final class BattleTacticalV2SmoothingState {
        final Map<String, Float> observerHp = new HashMap<String, Float>();
        float globalHp = -1f;
        String lastHeroKey = "";
        long lastTouchedTick = 0L;
    }

    private static final Map<BlockPos, BattleTacticalV2SmoothingState> BATTLE_TACTICAL_V2_SMOOTHING =
            new HashMap<BlockPos, BattleTacticalV2SmoothingState>();
    private static long battleTacticalV2LastSweep = 0L;
    private static final float BATTLE_TACTICAL_V2_LERP = 0.45f;
    private static final long BATTLE_TACTICAL_V2_SMOOTHING_TTL_TICKS = 600L;

    /** [10] */
    private static final Map<BlockPos, BattleTacticalV2GuiCache> BATTLE_TACTICAL_V2_GUI_CACHE = new HashMap<>();

    private static final class BattleTacticalV2GuiCache {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation textureId;
        int width;
        int height;
        String sourceId;
        long lastBakedTick;

        void dispose() {
            if (textureId != null) {
                try { Minecraft.getInstance().getTextureManager().release(textureId); } catch (Throwable ignored) {}
            }
            if (texture != null) {
                try { texture.close(); } catch (Throwable ignored) {}
            }
            image = null;
            texture = null;
            textureId = null;
            width = -1;
            height = -1;
            lastBakedTick = Long.MIN_VALUE;
        }
    }

    private static double battleTacticalV2Smooth(float oldVal, double target) {
        if (oldVal < 0f) return target;
        return oldVal + (target - oldVal) * BATTLE_TACTICAL_V2_LERP;
    }

    /** Pulse alpha for CRITICAL elements; renderTick-driven so it animates over panel rebuild cadence. */
    private static int battleTacticalV2PulseAlpha(BattleTacticalV2State state, long renderTick) {
        if (state != BattleTacticalV2State.CRITICAL) return 255;
        final double phase = ((renderTick % 40L) / 40.0) * Math.PI * 2.0;
        final double s = (Math.sin(phase) + 1.0) * 0.5;
        return (int) (170 + s * 85);
    }

    /** [11] */
    public static void renderBattleTacticalV2Texture(NativeImage image,
                                                     StatsDisplayPanelBE be,
                                                     String sourceId,
                                                     DisplayTheme theme,
                                                     long renderTick) {
        drawTextureChrome(image, theme);
        final int w = image.getWidth();
        final int h = image.getHeight();

        final BattleTacticalV2Readout readout = resolveBattleTacticalV2Readout(be, sourceId, renderTick);
        if (readout == null) {
            BitmapFont.drawCentered(image, "Waiting for", 0, h / 2 - 10, w, theme.dim, 1);
            BitmapFont.drawCentered(image, "battle feed", 0, h / 2 + 2, w, theme.dim, 1);
            return;
        }
        if (readout.cards.isEmpty()) {
            BitmapFont.drawCentered(image, "No linked", 0, h / 2 - 10, w, theme.dim, 1);
            BitmapFont.drawCentered(image, "sensors", 0, h / 2 + 2, w, theme.dim, 1);
            return;
        }

        if (w <= 96) {
            renderBattleTacticalV2Micro(image, w, h, readout, theme, renderTick);
        } else if (w <= 192) {
            renderBattleTacticalV2Strip(image, w, h, readout, theme, renderTick);
        } else {
            renderBattleTacticalV2Scene(image, w, h, readout, theme, renderTick);
        }

        // [12]
        if (readout.cards != null && !readout.cards.isEmpty()) {
            for (BattleTacticalV2Card card : readout.cards) {
                if (card.observerPos == null) continue;
                com.createstats.client.battle.ClientObserverVoxelCache.ensureFresh(card.observerPos);
            }
        }
    }

    /** [13] */
    private static void renderBattleTacticalV2Gui(GuiGraphics g, int x, int y, int w, int h,
                                                  StatsDisplayPanelBE be, String sourceId,
                                                  DisplayTheme theme, long renderTick) {
        if (w <= 0 || h <= 0) {
            return;
        }
        final BlockPos panelKey = be.getBlockPos();
        BattleTacticalV2GuiCache cache = BATTLE_TACTICAL_V2_GUI_CACHE.get(panelKey);
        final String normalizedSourceId = sourceId == null ? "" : sourceId;
        if (cache != null && (cache.width != w || cache.height != h
                || !normalizedSourceId.equals(cache.sourceId == null ? "" : cache.sourceId))) {
            cache.dispose();
            cache = null;
        }
        if (cache == null) {
            cache = new BattleTacticalV2GuiCache();
            try {
                cache.image = new NativeImage(w, h, true); // calloc, zero-initialised so unwritten/translucent regions don't reveal garbage memory
                cache.texture = new DynamicTexture(cache.image);
                cache.texture.setFilter(false, false); // NEAREST, avoid sampling bleed across the whole screen
                cache.textureId = ResourceLocation.fromNamespaceAndPath(
                        "create_stats",
                        "battle_tactical_v2_gui/" + Math.abs(panelKey.asLong()));
                Minecraft.getInstance().getTextureManager().register(cache.textureId, cache.texture);
                cache.width = w;
                cache.height = h;
                cache.sourceId = normalizedSourceId;
                cache.lastBakedTick = Long.MIN_VALUE;
                BATTLE_TACTICAL_V2_GUI_CACHE.put(panelKey.immutable(), cache);
            } catch (Throwable t) {
                cache.dispose();
                return;
            }
        }
        if (renderTick - cache.lastBakedTick >= 5L && cache.image != null && cache.texture != null) {
            try {
                renderBattleTacticalV2Texture(cache.image, be, normalizedSourceId, theme, renderTick);
                cache.texture.upload();
                cache.lastBakedTick = renderTick;
            } catch (Throwable ignored) {
                // keep previous bake on failure
            }
        }
        if (cache.textureId != null) {
            g.blit(cache.textureId, x, y, 0f, 0f, w, h, w, h);
        }
    }

    private static void renderBattleTacticalV2Strip(NativeImage image,
                                                    int w,
                                                    int h,
                                                    BattleTacticalV2Readout readout,
                                                    DisplayTheme theme,
                                                    long renderTick) {
        final int pad = Math.max(2, w / 48);

        // Header band
        final int headerH = 10;
        TextureGraphRenderer.fillRect(image, pad, pad, w - pad * 2, headerH,
                TextureGraphRenderer.withAlpha(theme.bg, 200));
        BitmapFont.drawString(image, "BATTLE TAC", pad + 2, pad + 2, theme.text, 1);
        final String countText = readout.cards.size() + " SNS";
        final int pipColor;
        if (readout.freshnessTicks <= 40L) pipColor = SAFE_GREEN;
        else if (readout.freshnessTicks <= 200L) pipColor = ALERT_AMBER;
        else pipColor = DANGER_RED;
        final int pipX = w - pad - 4;
        TextureGraphRenderer.fillRect(image, pipX, pad + 3, 3, 3, pipColor);
        BitmapFont.drawRight(image, countText, pipX - 2, pad + 2, theme.dim, 1);

        // Card rows
        final int rowsTop = pad + headerH + 2;
        final int footerReserve = (readout.cards.size() > 3) ? 9 : 0;
        final int available = Math.max(0, h - rowsTop - pad - footerReserve);
        final int rowGap = 1;
        final int maxRows = Math.min(readout.cards.size(), 3);
        if (maxRows <= 0 || available < 10) return;
        final int rowH = Math.max(10, Math.min(16, (available - rowGap * (maxRows - 1)) / maxRows));

        for (int i = 0; i < maxRows; i++) {
            final BattleTacticalV2Card card = readout.cards.get(i);
            final int ry = rowsTop + i * (rowH + rowGap);
            // Row background
            TextureGraphRenderer.fillRect(image, pad, ry, w - pad * 2, rowH,
                    TextureGraphRenderer.withAlpha(theme.bg, 160));
            TextureGraphRenderer.drawBorder(image, pad, ry, w - pad * 2, rowH, 1, theme.border);

            // Category chip
            final int chipX = pad + 2;
            final int chipY = ry + 2;
            final int chipW = 12;
            final int chipH = rowH - 4;
            TextureGraphRenderer.fillRect(image, chipX, chipY, chipW, chipH,
                    TextureGraphRenderer.withAlpha(card.state.color, 80));
            BitmapFont.drawCentered(image, battleTacticalV2CategoryChip(card.category),
                    chipX, chipY + (chipH - 5) / 2, chipW, card.state.color, 1);

            // Name
            final int nameX = chipX + chipW + 3;
            final int pctText = (int) Math.round(card.healthPercent);
            final String pctStr = pctText + "%";
            final int pctW = BitmapFont.measureString(pctStr, 1);
            final int barRightReserve = pctW + 4;
            final int nameMaxW = Math.max(8, (w - pad - barRightReserve) - nameX - 52);
            final String name = truncate(card.name, Math.max(3, nameMaxW / 4));
            BitmapFont.drawString(image, name, nameX, ry + 2, theme.text, 1);

            // Integrity bar
            final int barH = Math.max(3, rowH / 3);
            final int barY = ry + rowH - barH - 2;
            final int barX = nameX;
            final int barW = (w - pad - barRightReserve) - barX;
            if (barW > 4) {
                TextureGraphRenderer.fillRect(image, barX, barY, barW, barH,
                        TextureGraphRenderer.withAlpha(theme.bg, 220));
                TextureGraphRenderer.drawBorder(image, barX, barY, barW, barH, 1, theme.border);
                final int fillW = Math.max(0, Math.min(barW - 2,
                        Math.round((float) (Math.max(0.0, Math.min(100.0, card.healthPercent)) / 100.0) * (barW - 2))));
                if (fillW > 0) {
                    TextureGraphRenderer.fillRect(image, barX + 1, barY + 1, fillW, barH - 2,
                            TextureGraphRenderer.withAlpha(card.state.color, battleTacticalV2PulseAlpha(card.state, renderTick)));
                }
            }

            // Percent (right-aligned)
            BitmapFont.drawRight(image, pctStr, w - pad - 2, ry + (rowH - 5) / 2, card.state.color, 1);
        }

        if (footerReserve > 0) {
            final int more = readout.cards.size() - maxRows;
            BitmapFont.drawString(image, "+" + more + " more",
                    pad + 2, h - pad - 7, theme.dim, 1);
        }
    }

    private static void renderBattleTacticalV2Micro(NativeImage image,
                                                    int w,
                                                    int h,
                                                    BattleTacticalV2Readout readout,
                                                    DisplayTheme theme,
                                                    long renderTick) {
        final BattleTacticalV2Card hero = readout.cards.get(0);

        // Hero name + integrity %
        final int pad = Math.max(2, w / 32);
        final int nameY = Math.max(2, h / 16);
        final String name = truncate(hero.name, Math.max(4, (w - pad * 2 - 18) / 4));
        BitmapFont.drawString(image, name, pad, nameY, theme.text, 1);
        final String pctText = Math.round(hero.healthPercent) + "%";
        BitmapFont.drawRight(image, pctText, w - pad, nameY, hero.state.color, 1);

        // Hero integrity bar (full width minus padding)
        final int barH = Math.max(4, h / 9);
        final int barY = nameY + 10;
        final int barX = pad;
        final int barW = w - pad * 2;
        TextureGraphRenderer.fillRect(image, barX, barY, barW, barH, TextureGraphRenderer.withAlpha(theme.bg, 220));
        TextureGraphRenderer.drawBorder(image, barX, barY, barW, barH, 1, theme.border);
        final int fillW = Math.max(0, Math.min(barW - 2,
                Math.round((float) (Math.max(0.0, Math.min(100.0, hero.healthPercent)) / 100.0) * (barW - 2))));
        if (fillW > 0) {
            TextureGraphRenderer.fillRect(image, barX + 1, barY + 1, fillW, barH - 2,
                    TextureGraphRenderer.withAlpha(hero.state.color, battleTacticalV2PulseAlpha(hero.state, renderTick)));
        }

        // OTHERS label + state dots row
        final int othersY = barY + barH + 4;
        if (othersY + 10 <= h - 2) {
            final int otherCount = readout.cards.size() - 1;
            if (otherCount > 0) {
                BitmapFont.drawString(image, "OTHERS", pad, othersY, theme.dim, 1);
                final int dotY = othersY + 8;
                final int dotSize = 3;
                final int dotGap = 2;
                final int maxDots = Math.min(otherCount, Math.max(1, (w - pad * 2 + dotGap) / (dotSize + dotGap)));
                for (int i = 0; i < maxDots; i++) {
                    final BattleTacticalV2Card card = readout.cards.get(1 + i);
                    final int dx = pad + i * (dotSize + dotGap);
                    TextureGraphRenderer.fillRect(image, dx, dotY, dotSize, dotSize, card.state.color);
                }
                if (otherCount > maxDots) {
                    final int more = otherCount - maxDots;
                    BitmapFont.drawRight(image, "+" + more, w - pad, dotY - 1, theme.dim, 1);
                }
            } else {
                BitmapFont.drawString(image, "SOLO", pad, othersY, theme.dim, 1);
            }
        }

        // Freshness indicator (bottom-right corner pip)
        final int pipX = w - 4;
        final int pipY = h - 4;
        final int pipColor;
        if (readout.freshnessTicks <= 40L) {
            pipColor = SAFE_GREEN;
        } else if (readout.freshnessTicks <= 200L) {
            pipColor = ALERT_AMBER;
        } else {
            pipColor = DANGER_RED;
        }
        TextureGraphRenderer.fillRect(image, pipX - 2, pipY - 2, 2, 2, pipColor);
    }

    /** [14] */
    private static void renderBattleTacticalV2Scene(NativeImage image,
                                                    int w,
                                                    int h,
                                                    BattleTacticalV2Readout readout,
                                                    DisplayTheme theme,
                                                    long renderTick) {
        final int pad = Math.max(3, w / 64);
        final BattleTacticalV2State globalSt = battleStateForIntegrity(readout.globalIntegrity);

        boolean anyCritical = false;
        boolean anyWeapon = false;
        for (BattleTacticalV2Card c : readout.cards) {
            if (c.state == BattleTacticalV2State.CRITICAL) anyCritical = true;
            if (c.weaponDetected) anyWeapon = true;
        }

        // Animated console backdrop: faint grid + drifting scanlines.
        battleConsoleBackdrop(image, pad, pad, w - pad * 2, h - pad * 2, theme, renderTick);

        // header strip
        final int headerH = 14;
        final int hx = pad, hy = pad, hw = w - pad * 2;
        TextureGraphRenderer.fillRect(image, hx, hy, hw, headerH, TextureGraphRenderer.withAlpha(theme.bg, 215));
        TextureGraphRenderer.drawBorder(image, hx, hy, hw, headerH, 1, theme.border);
        // Travelling accent tick across the header for a "live" feel.
        final int travel = (int) ((renderTick * 2L) % Math.max(1, hw - 2));
        TextureGraphRenderer.fillRect(image, hx + 1 + travel, hy + 1, 2, headerH - 2,
                TextureGraphRenderer.withAlpha(theme.accent, 55));
        BitmapFont.drawString(image, "BATTLE TACTICAL", hx + 4, hy + 4, theme.text, 1);
        // Freshness pip + sensor count on the right.
        final int pipColor;
        if (readout.freshnessTicks <= 40L) pipColor = SAFE_GREEN;
        else if (readout.freshnessTicks <= 200L) pipColor = ALERT_AMBER;
        else pipColor = DANGER_RED;
        final int pipX = hx + hw - 6;
        TextureGraphRenderer.fillRect(image, pipX, hy + 5, 4, 4, pipColor);
        BitmapFont.drawRight(image, readout.cards.size() + " SNS", pipX - 4, hy + 4, theme.dim, 1);

        // body regions
        final int bodyTop = pad + headerH + 3;
        final int bodyBottom = h - pad;
        final int bodyH = bodyBottom - bodyTop;
        if (bodyH < 24) return;

        final int weaponDockH = anyWeapon ? Math.min(20, bodyH / 5) : 0;
        final int weaponDockY = bodyBottom - weaponDockH;
        final int afterWeaponBottom = weaponDockH > 0 ? weaponDockY - 3 : bodyBottom;

        final int compH = Math.min(28, Math.max(0, (afterWeaponBottom - bodyTop) / 4));
        final int compY = compH > 0 ? afterWeaponBottom - compH : afterWeaponBottom;
        final int upperBottom = compH > 0 ? compY - 3 : afterWeaponBottom;
        final int upperH = upperBottom - bodyTop;
        if (upperH < 20) return;

        // [15]
        final boolean portrait = w < (int) (upperH * 1.25);
        if (portrait) {
            final int gaugePanelH = Math.min(upperH / 2, w - pad * 2);
            final int innerTop = battlePanel(image, pad, bodyTop, w - pad * 2, gaugePanelH, "INTEGRITY", theme);
            final int availH = (bodyTop + gaugePanelH - 3) - innerTop;
            final int gaugeR = Math.max(12, Math.min((w - pad * 2) / 2 - 6, availH / 2 - 4));
            renderBattleIntegrityGauge(image, w / 2, innerTop + availH / 2, gaugeR,
                    readout, globalSt, theme, renderTick);
            final int ladderY = bodyTop + gaugePanelH + 3;
            if (upperBottom - ladderY >= 18) {
                renderBattleTacticalV2Ladder(image, pad, ladderY, w - pad * 2,
                        upperBottom - ladderY, readout, theme, renderTick);
            }
        } else {
            final int gaugePanelW = Math.max(56, Math.min((w - pad * 2) * 2 / 5, upperH + 12));
            final int innerTop = battlePanel(image, pad, bodyTop, gaugePanelW, upperH, "INTEGRITY", theme);
            final int availH = (bodyTop + upperH - 3) - innerTop;
            final int gaugeR = Math.max(12, Math.min(gaugePanelW / 2 - 6, availH / 2 - 4));
            renderBattleIntegrityGauge(image, pad + gaugePanelW / 2, innerTop + availH / 2, gaugeR,
                    readout, globalSt, theme, renderTick);
            final int ladderX = pad + gaugePanelW + 4;
            renderBattleTacticalV2Ladder(image, ladderX, bodyTop, (w - pad) - ladderX,
                    upperH, readout, theme, renderTick);
        }

        // structural composition bar
        if (compH > 0) {
            renderBattleCompositionStrip(image, pad, compY, w - pad * 2, compH, readout, theme, renderTick);
        }

        // weapon dock
        if (weaponDockH > 0) {
            renderBattleTacticalV2WeaponDock(image, pad, weaponDockY, w - pad * 2, weaponDockH, readout, theme, renderTick);
        }

        // hud chrome
        battleConsoleCornerBrackets(image, pad, bodyTop - 1, w - pad * 2, bodyBottom - (bodyTop - 1),
                TextureGraphRenderer.withAlpha(theme.accent, 150));
        if (anyCritical || globalSt == BattleTacticalV2State.CRITICAL) {
            final int a = battleTacticalV2PulseAlpha(BattleTacticalV2State.CRITICAL, renderTick);
            TextureGraphRenderer.drawBorder(image, pad - 1, pad - 1,
                    w - (pad - 1) * 2, h - (pad - 1) * 2, 1, TextureGraphRenderer.withAlpha(DANGER_RED, a));
        }
    }

    private static BattleTacticalV2State battleStateForIntegrity(double integrity) {
        if (integrity < 25.0) return BattleTacticalV2State.CRITICAL;
        if (integrity < 60.0) return BattleTacticalV2State.WARNING;
        return BattleTacticalV2State.NOMINAL;
    }

    /** Solid dim console fill with a single subtle drifting scanline, no busy grid. */
    private static void battleConsoleBackdrop(NativeImage image, int x, int y, int w, int h,
                                              DisplayTheme theme, long renderTick) {
        if (w <= 2 || h <= 2) return;
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 150));
        // One faint drifting scanline for a hint of motion; deliberately NOT a full grid.
        final int scan = TextureGraphRenderer.withAlpha(theme.accent, 18);
        final int s1 = y + (int) ((renderTick) % Math.max(1, h));
        TextureGraphRenderer.drawHLine(image, x, s1, w, scan);
    }

    /** Filled, bordered compartment with an optional title. Returns the inner content top Y. */
    private static int battlePanel(NativeImage image, int x, int y, int w, int h,
                                   String title, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 185));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        if (title != null && !title.isEmpty()) {
            BitmapFont.drawString(image, title, x + 3, y + 2, theme.dim, 1);
            return y + 11;
        }
        return y + 2;
    }

    /** Small L-shaped brackets at the four corners of a region for a HUD look. */
    private static void battleConsoleCornerBrackets(NativeImage image, int x, int y, int w, int h, int color) {
        if (w < 12 || h < 12) return;
        final int len = Math.min(8, Math.min(w, h) / 4);
        // top-left
        TextureGraphRenderer.drawHLine(image, x, y, len, color);
        TextureGraphRenderer.drawVLine(image, x, y, len, color);
        // top-right
        TextureGraphRenderer.drawHLine(image, x + w - len, y, len, color);
        TextureGraphRenderer.drawVLine(image, x + w - 1, y, len, color);
        // bottom-left
        TextureGraphRenderer.drawHLine(image, x, y + h - 1, len, color);
        TextureGraphRenderer.drawVLine(image, x, y + h - len, len, color);
        // bottom-right
        TextureGraphRenderer.drawHLine(image, x + w - len, y + h - 1, len, color);
        TextureGraphRenderer.drawVLine(image, x + w - 1, y + h - len, len, color);
    }

    /**
     * Blended ring/arc helper with a predictable convention: angles are measured in
     * degrees clockwise from the top (12 o'clock). {@code sweepDeg} extends clockwise.
     */
    private static void battleArc(NativeImage image, int cx, int cy, int rInner, int rOuter,
                                  double startDeg, double sweepDeg, int argb) {
        if (rOuter <= 0 || sweepDeg <= 0.0) return;
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int py = cy - rOuter; py <= cy + rOuter; py++) {
            if (py < 0 || py >= imgH) continue;
            for (int px = cx - rOuter; px <= cx + rOuter; px++) {
                if (px < 0 || px >= imgW) continue;
                final int dx = px - cx;
                final int dy = py - cy;
                final double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < rInner - 0.5 || dist > rOuter + 0.5) continue;
                double ang = Math.toDegrees(Math.atan2(dx, -dy));
                if (ang < 0.0) ang += 360.0;
                double rel = ((ang - startDeg) % 360.0 + 360.0) % 360.0;
                if (rel <= sweepDeg) {
                    TextureGraphRenderer.setPixelBlended(image, px, py, argb);
                }
            }
        }
    }

    /** Circular integrity gauge with value arc, radial ticks and an animated radar sweep. */
    private static void renderBattleIntegrityGauge(NativeImage image, int cx, int cy, int r,
                                                   BattleTacticalV2Readout readout,
                                                   BattleTacticalV2State globalSt,
                                                   DisplayTheme theme, long renderTick) {
        final double integrity = Math.max(0.0, Math.min(100.0, readout.globalIntegrity));
        final int color = globalSt.color;
        final int ringOuter = r;
        final int ringInner = Math.max(3, r - Math.max(3, r / 5));

        // Dim full background ring.
        battleArc(image, cx, cy, ringInner, ringOuter, 0.0, 360.0, TextureGraphRenderer.withAlpha(theme.border, 110));
        // Faint glow band just outside the ring (stronger when critical).
        final int glowA = globalSt == BattleTacticalV2State.CRITICAL
                ? battleTacticalV2PulseAlpha(globalSt, renderTick) / 4 : 30;
        battleArc(image, cx, cy, ringOuter, ringOuter + 2, 0.0, 360.0, TextureGraphRenderer.withAlpha(color, glowA));

        // Value arc (clockwise from top).
        final double sweep = 360.0 * (integrity / 100.0);
        battleArc(image, cx, cy, ringInner, ringOuter, 0.0, sweep, TextureGraphRenderer.withAlpha(color, 235));
        // Bright leading cap.
        if (sweep > 2.0) {
            battleArc(image, cx, cy, ringInner, ringOuter, Math.max(0.0, sweep - 6.0), 6.0,
                    TextureGraphRenderer.withAlpha(brighten(color, 1.4f), 255));
        }

        // Radial tick marks every 30 degrees.
        final int tickColor = TextureGraphRenderer.withAlpha(theme.dim, 150);
        for (int k = 0; k < 12; k++) {
            final double a = Math.toRadians(k * 30.0);
            final double sinA = Math.sin(a), cosA = Math.cos(a);
            final int x0 = cx + (int) Math.round(sinA * (ringOuter + 1));
            final int y0 = cy - (int) Math.round(cosA * (ringOuter + 1));
            final int x1 = cx + (int) Math.round(sinA * (ringOuter - 2));
            final int y1 = cy - (int) Math.round(cosA * (ringOuter - 2));
            TextureGraphRenderer.drawLine(image, x0, y0, x1, y1, tickColor);
        }

        // Animated radar sweep line inside the ring.
        final double sweepDeg = (renderTick * 5L) % 360L;
        final double sweepRad = Math.toRadians(sweepDeg);
        final int sx = cx + (int) Math.round(Math.sin(sweepRad) * (ringInner - 1));
        final int sy = cy - (int) Math.round(Math.cos(sweepRad) * (ringInner - 1));
        battleArc(image, cx, cy, 1, ringInner - 1, sweepDeg - 26.0, 26.0,
                TextureGraphRenderer.withAlpha(theme.accent, 32));
        TextureGraphRenderer.drawLineAA(image, cx, cy, sx, sy, TextureGraphRenderer.withAlpha(theme.accent, 200));
        TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(1, r / 12), TextureGraphRenderer.withAlpha(theme.accent, 220));

        // Center readout.
        final String pct = (int) Math.round(integrity) + "%";
        final int valScale = (ringInner >= 18 && BitmapFont.measureString(pct, 2) <= ringInner * 2 - 4) ? 2 : 1;
        final int valH = BitmapFont.textHeight(valScale);
        BitmapFont.drawCentered(image, pct, cx - ringInner, cy - valH, ringInner * 2, color, valScale);
        final String label = globalSt.name();
        final int labelA = battleTacticalV2PulseAlpha(globalSt, renderTick);
        BitmapFont.drawCentered(image, label, cx - ringInner, cy + 2, ringInner * 2,
                TextureGraphRenderer.withAlpha(color, labelA), 1);
    }

    /** [16] */
    private static void renderBattleCompositionStrip(NativeImage image, int x, int y, int w, int h,
                                                     BattleTacticalV2Readout readout,
                                                     DisplayTheme theme, long renderTick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 170));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "STRUCTURE", x + 3, y + 2, theme.dim, 1);

        long present = 0, missing = 0, added = 0;
        boolean has = false;
        for (BattleTacticalV2Card c : readout.cards) {
            if (c.observerPos == null) continue;
            com.createstats.battle.DamageObserverVoxelMap m =
                    com.createstats.client.battle.ClientObserverVoxelCache.get(c.observerPos);
            if (m == null) continue;
            present += m.presentCount();
            missing += m.missingCount();
            added += m.newCount();
            has = true;
        }
        final long intact = Math.max(0L, present - added);
        final long total = intact + missing + added;

        final int barX = x + 3;
        final int barY = y + 11;
        final int barW = w - 6;
        final int barH = Math.max(4, h - 14);
        if (barW < 8 || barH < 3) return;

        if (!has || total <= 0L) {
            TextureGraphRenderer.fillRect(image, barX, barY, barW, barH, TextureGraphRenderer.withAlpha(theme.bg, 200));
            BitmapFont.drawCentered(image, "NO STRUCTURE DATA", barX, barY + (barH - 5) / 2, barW, theme.dim, 1);
            return;
        }

        TextureGraphRenderer.fillRect(image, barX, barY, barW, barH, TextureGraphRenderer.withAlpha(theme.bg, 210));
        int segX = barX;
        final int wIntact = (int) (barW * intact / total);
        final int wLost = (int) (barW * missing / total);
        final int wAdded = barW - wIntact - wLost;
        if (wIntact > 0) { TextureGraphRenderer.fillRect(image, segX, barY, wIntact, barH, SAFE_GREEN); segX += wIntact; }
        if (wLost > 0) { TextureGraphRenderer.fillRect(image, segX, barY, wLost, barH, DANGER_RED); segX += wLost; }
        if (wAdded > 0) { TextureGraphRenderer.fillRect(image, segX, barY, wAdded, barH, theme.accent); }

        // Travelling shimmer highlight across the bar.
        final int shimX = barX + (int) ((renderTick * 3L) % Math.max(1, barW));
        TextureGraphRenderer.fillRect(image, shimX, barY, 1, barH, TextureGraphRenderer.withAlpha(0xFFFFFFFF, 70));
        TextureGraphRenderer.drawBorder(image, barX, barY, barW, barH, 1, theme.border);

        // Compact legend on the right of the title row.
        final String legend = intact + "/" + missing + "/" + added;
        BitmapFont.drawRight(image, legend, x + w - 4, y + 2, theme.dim, 1);
    }

    private static void renderBattleTacticalV2Ladder(NativeImage image,
                                                     int x, int y, int w, int h,
                                                     BattleTacticalV2Readout readout,
                                                     DisplayTheme theme,
                                                     long renderTick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 140));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "SENSORS", x + 3, y + 2, theme.dim, 1);

        final int listTop = y + 11;
        final int listH = h - 13;
        final int rowH = 14;
        final int rowGap = 1;
        final int maxRows = Math.max(0, (listH + rowGap) / (rowH + rowGap));
        final int rows = Math.min(maxRows, readout.cards.size());

        for (int i = 0; i < rows; i++) {
            final BattleTacticalV2Card card = readout.cards.get(i);
            final int ry = listTop + i * (rowH + rowGap);
            TextureGraphRenderer.fillRect(image, x + 2, ry, w - 4, rowH,
                    TextureGraphRenderer.withAlpha(theme.bg, 180));
            TextureGraphRenderer.drawBorder(image, x + 2, ry, w - 4, rowH, 1, theme.border);

            // Category chip
            final int chipX = x + 4;
            final int chipY = ry + 2;
            final int chipW = 12;
            final int chipH = rowH - 4;
            TextureGraphRenderer.fillRect(image, chipX, chipY, chipW, chipH,
                    TextureGraphRenderer.withAlpha(card.state.color, 90));
            BitmapFont.drawCentered(image, battleTacticalV2CategoryChip(card.category),
                    chipX, chipY + (chipH - 5) / 2, chipW, card.state.color, 1);

            // Name (top line)
            final int nameX = chipX + chipW + 3;
            final String pctStr = (int) Math.round(card.healthPercent) + "%";
            final int pctW = BitmapFont.measureString(pctStr, 1);
            final int nameMaxChars = Math.max(3, ((x + w - 4 - pctW - 6) - nameX) / 4);
            BitmapFont.drawString(image, truncate(card.name, nameMaxChars), nameX, ry + 2, theme.text, 1);
            BitmapFont.drawRight(image, pctStr, x + w - 5, ry + 2, card.state.color, 1);

            // Integrity bar (bottom line)
            final int barH = 3;
            final int barY = ry + rowH - barH - 2;
            final int barX = nameX;
            final int barW = (x + w - 5) - barX;
            if (barW > 4) {
                TextureGraphRenderer.fillRect(image, barX, barY, barW, barH,
                        TextureGraphRenderer.withAlpha(theme.bg, 220));
                final int fillW = Math.max(0, Math.min(barW - 2,
                        Math.round((float) (Math.max(0.0, Math.min(100.0, card.healthPercent)) / 100.0) * (barW - 2))));
                if (fillW > 0) {
                    TextureGraphRenderer.fillRect(image, barX + 1, barY + 1, fillW, barH - 2,
                            TextureGraphRenderer.withAlpha(card.state.color, battleTacticalV2PulseAlpha(card.state, renderTick)));
                }
            }
        }

        if (readout.cards.size() > rows) {
            BitmapFont.drawRight(image, "+" + (readout.cards.size() - rows),
                    x + w - 4, y + h - 8, theme.dim, 1);
        }
    }

    private static void renderBattleTacticalV2DamageMap(NativeImage image,
                                                        int x, int y, int w, int h,
                                                        BattleTacticalV2Readout readout,
                                                        DisplayTheme theme,
                                                        long renderTick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 160));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "DAMAGE MAP", x + 3, y + 2, theme.dim, 1);

        final int mapX = x + 4;
        final int mapY = y + 11;
        final int mapW = w - 8;
        final int mapH = h - 13;
        if (mapW < 16 || mapH < 16) return;

        // Collect XZ bounds across observers (and bridge, if available)
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        boolean hasAny = false;
        if (readout.bridgePos != null) {
            minX = Math.min(minX, readout.bridgePos.getX());
            maxX = Math.max(maxX, readout.bridgePos.getX());
            minZ = Math.min(minZ, readout.bridgePos.getZ());
            maxZ = Math.max(maxZ, readout.bridgePos.getZ());
            hasAny = true;
        }
        for (BattleTacticalV2Card c : readout.cards) {
            if (c.anchor == null) continue;
            minX = Math.min(minX, c.anchor.getX());
            maxX = Math.max(maxX, c.anchor.getX());
            minZ = Math.min(minZ, c.anchor.getZ());
            maxZ = Math.max(maxZ, c.anchor.getZ());
            hasAny = true;
        }
        if (!hasAny) {
            BitmapFont.drawCentered(image, "no position data", mapX, mapY + mapH / 2 - 3, mapW, theme.dim, 1);
            return;
        }

        // Square aspect, pad bounds so the longer axis dominates
        int rangeX = Math.max(1, maxX - minX);
        int rangeZ = Math.max(1, maxZ - minZ);
        final int range = Math.max(rangeX, rangeZ) + 8;
        final int cx = (minX + maxX) / 2;
        final int cz = (minZ + maxZ) / 2;
        final int boundMinX = cx - range / 2;
        final int boundMinZ = cz - range / 2;

        // Draw bridge as outlined diamond
        if (readout.bridgePos != null) {
            final int bx = mapX + ((readout.bridgePos.getX() - boundMinX) * mapW) / range;
            final int bz = mapY + ((readout.bridgePos.getZ() - boundMinZ) * mapH) / range;
            TextureGraphRenderer.fillRect(image, bx - 2, bz - 2, 5, 5, theme.text);
            TextureGraphRenderer.fillRect(image, bx - 1, bz - 1, 3, 3, TextureGraphRenderer.withAlpha(theme.bg, 220));
            TextureGraphRenderer.fillRect(image, bx, bz, 1, 1, theme.text);
        }

        // Draw observer squares
        for (BattleTacticalV2Card c : readout.cards) {
            if (c.anchor == null) continue;
            final int ox = mapX + ((c.anchor.getX() - boundMinX) * mapW) / range;
            final int oz = mapY + ((c.anchor.getZ() - boundMinZ) * mapH) / range;
            final int size = Math.max(2, Math.min(6, c.diameter / 2));
            final int half = size / 2;
            // Outer (state color)
            TextureGraphRenderer.fillRect(image, ox - half, oz - half, size, size,
                    TextureGraphRenderer.withAlpha(c.state.color, battleTacticalV2PulseAlpha(c.state, renderTick)));
            TextureGraphRenderer.drawBorder(image, ox - half, oz - half, size, size, 1, c.state.color);
        }
    }

    /** [17] */
    private static boolean renderBattleTacticalV2VoxelSilhouette(NativeImage image,
                                                                  int x, int y, int w, int h,
                                                                  BattleTacticalV2Readout readout,
                                                                  DisplayTheme theme,
                                                                  long renderTick) {
        // Collect voxel maps for every linked observer with a fresh cache entry.
        java.util.List<com.createstats.battle.DamageObserverVoxelMap> maps = new java.util.ArrayList<>();
        for (BattleTacticalV2Card c : readout.cards) {
            if (c == null || c.observerPos == null) continue;
            com.createstats.battle.DamageObserverVoxelMap m =
                    com.createstats.client.battle.ClientObserverVoxelCache.get(c.observerPos);
            if (m != null) maps.add(m);
        }
        if (maps.isEmpty()) return false;

        // Frame + label.
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 160));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "STRUCTURE", x + 3, y + 2, theme.dim, 1);

        final int mapX = x + 2;
        final int mapY = y + 11;
        final int mapW = w - 4;
        final int mapH = h - 13;
        if (mapW < 24 || mapH < 24) return true; // rendered the frame even if too small for voxels

        // Merged bounding box & centroid.
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (com.createstats.battle.DamageObserverVoxelMap m : maps) {
            minX = Math.min(minX, m.minCorner.getX());
            minY = Math.min(minY, m.minCorner.getY());
            minZ = Math.min(minZ, m.minCorner.getZ());
            maxX = Math.max(maxX, m.minCorner.getX() + m.dx);
            maxY = Math.max(maxY, m.minCorner.getY() + m.dy);
            maxZ = Math.max(maxZ, m.minCorner.getZ() + m.dz);
        }
        if (!Double.isFinite(minX)) return true;
        final double cx = (minX + maxX) * 0.5;
        final double cy = (minY + maxY) * 0.5;
        final double cz = (minZ + maxZ) * 0.5;
        final double extent = Math.max(1.0, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));

        // Iso projection params. Yaw advances ~0.5 deg per tick (rolls back into view).
        final double yawDeg = ((renderTick * 0.5) % 360.0);
        final double yaw = Math.toRadians(yawDeg);
        final double pitch = Math.toRadians(28.0);
        final double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        final double cosP = Math.cos(pitch), sinP = Math.sin(pitch);

        // Fit scale: leave a 6px margin inside the area.
        final double targetPx = Math.min(mapW, mapH) - 6;
        final double pxPerVoxel = Math.max(1.0, targetPx / (extent * 1.35));
        final int dotSize = Math.max(1, (int) Math.round(pxPerVoxel * 0.95));
        final int centerSx = mapX + mapW / 2;
        final int centerSy = mapY + mapH / 2;

        // Stream all voxels into a flat array sorted by depth, painter's algorithm.
        int total = 0;
        for (com.createstats.battle.DamageObserverVoxelMap m : maps) total += m.dx * m.dy * m.dz;
        // For very large structures, downscale capacity (we'll skip air voxels naturally).
        final int cap = Math.min(total, 200_000);
        final float[] sxArr = new float[cap];
        final float[] syArr = new float[cap];
        final float[] depthArr = new float[cap];
        final int[] colorArr = new int[cap];
        int n = 0;

        // Theme-based default colors.
        final int colMissing = TextureGraphRenderer.withAlpha(DANGER_RED, 230);
        final int colNew = TextureGraphRenderer.withAlpha(0xFF60a8ff, 230);
        final int colPresent = TextureGraphRenderer.withAlpha(theme.text, 230);

        for (com.createstats.battle.DamageObserverVoxelMap map : maps) {
            final int mnX = map.minCorner.getX();
            final int mnY = map.minCorner.getY();
            final int mnZ = map.minCorner.getZ();
            final int[] indices = map.paletteIndices;
            for (int lz = 0; lz < map.dz; lz++) {
                for (int ly = 0; ly < map.dy; ly++) {
                    for (int lx = 0; lx < map.dx; lx++) {
                        if (n >= cap) break;
                        final int flat = map.indexFor(lx, ly, lz);
                        final int paletteIdx = indices[flat];
                        final boolean baseline = map.baselineMask.get(flat);
                        if (paletteIdx == 0 && !baseline) continue;
                        final int color;
                        if (paletteIdx == 0) color = colMissing;
                        else color = baseline ? colPresent : colNew;

                        // Centered world-coord relative to centroid.
                        final double vx = (mnX + lx) - cx;
                        final double vy = (mnY + ly) - cy;
                        final double vz = (mnZ + lz) - cz;
                        // Rotate around Y, then iso pitch.
                        final double rxw = vx * cosY - vz * sinY;
                        final double rzw = vx * sinY + vz * cosY;
                        final double rxs = rxw;
                        final double rys = -vy * cosP - rzw * sinP;   // up on screen = -screen_y
                        final double depth = rzw * cosP - vy * sinP;
                        sxArr[n] = (float) (centerSx + rxs * pxPerVoxel);
                        syArr[n] = (float) (centerSy + rys * pxPerVoxel);
                        depthArr[n] = (float) depth;
                        colorArr[n] = color;
                        n++;
                    }
                }
            }
        }

        // Indirect sort by depth ascending (back to front).
        final Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Float.compare(depthArr[a], depthArr[b]));

        // Paint.
        for (int k = 0; k < n; k++) {
            final int i = idx[k];
            final int px = Math.round(sxArr[i]);
            final int py = Math.round(syArr[i]);
            // Clip to map area.
            int rx = px - dotSize / 2;
            int ry = py - dotSize / 2;
            int rw = dotSize;
            int rh = dotSize;
            if (rx < mapX) { rw -= (mapX - rx); rx = mapX; }
            if (ry < mapY) { rh -= (mapY - ry); ry = mapY; }
            if (rx + rw > mapX + mapW) rw = (mapX + mapW) - rx;
            if (ry + rh > mapY + mapH) rh = (mapY + mapH) - ry;
            if (rw > 0 && rh > 0) {
                TextureGraphRenderer.fillRect(image, rx, ry, rw, rh, colorArr[i]);
            }
        }

        // Small legend in the bottom-right corner.
        final int legX = mapX + mapW - 30;
        final int legY = mapY + mapH - 10;
        TextureGraphRenderer.fillRect(image, legX, legY, 3, 3, colMissing);
        BitmapFont.drawString(image, "D", legX + 5, legY - 2, theme.dim, 1);
        TextureGraphRenderer.fillRect(image, legX + 14, legY, 3, 3, colNew);
        BitmapFont.drawString(image, "N", legX + 19, legY - 2, theme.dim, 1);

        // Rotation indicator -- tiny arc in top-right corner.
        final String yawTxt = (int) yawDeg + "°";
        BitmapFont.drawRight(image, yawTxt, mapX + mapW - 2, mapY + 1, theme.dim, 1);
        return true;
    }

    private static void renderBattleTacticalV2WeaponDock(NativeImage image,
                                                         int x, int y, int w, int h,
                                                         BattleTacticalV2Readout readout,
                                                         DisplayTheme theme,
                                                         long renderTick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, TextureGraphRenderer.withAlpha(theme.bg, 180));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "WPN", x + 3, y + 2, theme.dim, 1);

        final int slotsX = x + 22;
        final int slotsY = y + 2;
        final int slotsW = w - 24;
        final int slotsH = h - 4;
        final int slotW = 10;
        final int slotGap = 2;
        final int maxSlots = Math.max(0, (slotsW + slotGap) / (slotW + slotGap));

        int slot = 0;
        int weaponTotal = 0;
        for (BattleTacticalV2Card c : readout.cards) {
            if (!c.weaponDetected) continue;
            weaponTotal++;
            if (slot >= maxSlots) continue;
            final int sx = slotsX + slot * (slotW + slotGap);
            final int color;
            if (c.weaponDestroyed) color = DANGER_RED;
            else if (c.weaponReady) color = SAFE_GREEN;
            else color = ALERT_AMBER;
            TextureGraphRenderer.fillRect(image, sx, slotsY, slotW, slotsH,
                    TextureGraphRenderer.withAlpha(color, 110));
            TextureGraphRenderer.drawBorder(image, sx, slotsY, slotW, slotsH, 1, color);
            BitmapFont.drawCentered(image, "WP", sx, slotsY + (slotsH - 5) / 2, slotW, color, 1);
            slot++;
        }
        if (weaponTotal > maxSlots) {
            BitmapFont.drawRight(image, "+" + (weaponTotal - maxSlots), x + w - 3, y + h - 8, theme.dim, 1);
        }
    }

    private static BattleTacticalV2Readout resolveBattleTacticalV2Readout(StatsDisplayPanelBE be,
                                                                          String sourceId,
                                                                          long renderTick) {
        if (be == null) {
            return null;
        }
        StatsDisplayPanelBE.BattleScreenLink targetLink = null;
        BlockPos targetPos = null;
        final SourceConfig src = (sourceId == null || sourceId.isEmpty()) ? null : be.getSourceRegistry().get(sourceId);
        if (src != null && src.getSourcePos() != null) {
            targetPos = src.getSourcePos();
        }
        for (StatsDisplayPanelBE.BattleScreenLink link : be.getBattleScreenLinks()) {
            if (link == null) {
                continue;
            }
            if (targetLink == null) {
                targetLink = link;
            }
            if (targetPos != null && targetPos.equals(link.getSourcePos())) {
                targetLink = link;
                break;
            }
        }
        if (targetLink == null) {
            return null;
        }
        final List<BattleObserverSnapshot> observers = targetLink.getObservers();

        // Acquire / create smoothing state for this panel.
        final BlockPos panelPos = be.getBlockPos();
        BattleTacticalV2SmoothingState smoothing = BATTLE_TACTICAL_V2_SMOOTHING.get(panelPos);
        if (smoothing == null) {
            smoothing = new BattleTacticalV2SmoothingState();
            BATTLE_TACTICAL_V2_SMOOTHING.put(panelPos, smoothing);
        }
        smoothing.lastTouchedTick = renderTick;

        final List<BattleTacticalV2Card> cards = new ArrayList<BattleTacticalV2Card>(observers == null ? 0 : observers.size());
        if (observers != null) {
            for (BattleObserverSnapshot obs : observers) {
                if (obs == null) {
                    continue;
                }
                final String key = obs.sourcePos() != null ? obs.sourcePos().asLong() + "|" + obs.name() : obs.name();
                final Float prev = smoothing.observerHp.get(key);
                final double rawHp = Math.max(0.0, Math.min(100.0, obs.healthPercent()));
                final double smoothedHp = battleTacticalV2Smooth(prev == null ? -1f : prev, rawHp);
                smoothing.observerHp.put(key, (float) smoothedHp);
                cards.add(buildBattleTacticalV2Card(obs, smoothedHp));
            }
        }
        // Stable hero: small bias for previously-promoted hero to avoid jittery swaps on close scores.
        if (smoothing.lastHeroKey != null && !smoothing.lastHeroKey.isEmpty()) {
            for (int i = 0; i < cards.size(); i++) {
                final BattleTacticalV2Card c = cards.get(i);
                if (smoothing.lastHeroKey.equals(c.name)) {
                    cards.set(i, new BattleTacticalV2Card(c.name, c.healthPercent, c.state, c.severityScore + 30,
                            c.category, c.anchor, c.diameter, c.weaponDetected, c.weaponReady, c.weaponDestroyed,
                            c.observerPos));
                    break;
                }
            }
        }
        // Sort worst-first: descending severity score, ties by ascending integrity.
        cards.sort((a, b) -> {
            final int byScore = Integer.compare(b.severityScore, a.severityScore);
            if (byScore != 0) {
                return byScore;
            }
            return Double.compare(a.healthPercent, b.healthPercent);
        });
        if (!cards.isEmpty()) {
            smoothing.lastHeroKey = cards.get(0).name;
        }

        final long freshnessTicks = Math.max(0L, renderTick - targetLink.getLastDataTick());
        double sum = 0.0;
        int n = 0;
        for (BattleTacticalV2Card c : cards) { sum += c.healthPercent; n++; }
        final double rawGlobal = (n > 0) ? (sum / n) : 100.0;
        final double smoothedGlobal = battleTacticalV2Smooth(smoothing.globalHp, rawGlobal);
        smoothing.globalHp = (float) smoothedGlobal;

        // Periodic sweep of stale smoothing entries (every ~200 ticks).
        if (renderTick - battleTacticalV2LastSweep > 200L) {
            battleTacticalV2LastSweep = renderTick;
            BATTLE_TACTICAL_V2_SMOOTHING.entrySet().removeIf(e ->
                    renderTick - e.getValue().lastTouchedTick > BATTLE_TACTICAL_V2_SMOOTHING_TTL_TICKS);
        }

        return new BattleTacticalV2Readout(cards, freshnessTicks, targetLink.getSourcePos(), smoothedGlobal);
    }

    private static BattleTacticalV2Card buildBattleTacticalV2Card(BattleObserverSnapshot obs, double smoothedHp) {
        final double rawHp = Math.max(0.0, Math.min(100.0, obs.healthPercent()));
        final double displayHp = Math.max(0.0, Math.min(100.0, smoothedHp));
        final boolean isWeapon = obs.category() == DamageObserverSettings.ObserverCategory.WEAPONS
                || (obs.weapon() != null && obs.weapon().detected());
        final boolean weaponDestroyed = isWeapon && rawHp <= 0.5;
        final boolean recentBreach = obs.baselineBlocks() > 0
                && obs.missingBlocks() > Math.max(1, obs.baselineBlocks() / 20);
        int score;
        BattleTacticalV2State state;
        if (weaponDestroyed) {
            score = 1000;
            state = BattleTacticalV2State.CRITICAL;
        } else if (rawHp < 25.0) {
            score = 800;
            state = BattleTacticalV2State.CRITICAL;
        } else if (rawHp < 50.0) {
            score = 600;
            state = BattleTacticalV2State.WARNING;
        } else if (rawHp < 80.0) {
            score = 400;
            state = BattleTacticalV2State.WARNING;
        } else {
            score = (int) (100.0 - rawHp);
            state = BattleTacticalV2State.NOMINAL;
        }
        if (recentBreach) {
            score += 200;
            if (state == BattleTacticalV2State.NOMINAL) {
                state = BattleTacticalV2State.WARNING;
            }
        }
        String label = obs.name();
        if (label == null || label.isBlank()) {
            label = obs.category() != null ? obs.category().name() : "SENSOR";
        }
        final boolean weaponReady = isWeapon && obs.weapon() != null && obs.weapon().detected() && !weaponDestroyed
                && obs.weapon().readiness() != null
                && (obs.weapon().readiness().equalsIgnoreCase("ready")
                    || obs.weapon().readiness().equalsIgnoreCase("loaded")
                    || obs.weapon().readiness().equalsIgnoreCase("armed"));
        return new BattleTacticalV2Card(label, displayHp, state, score, obs.category(),
                obs.anchor(), obs.diameter(),
                isWeapon && obs.weapon() != null && obs.weapon().detected(), weaponReady, weaponDestroyed,
                obs.sourcePos());
    }

    private static String battleTacticalV2CategoryChip(DamageObserverSettings.ObserverCategory cat) {
        if (cat == null) return "--";
        switch (cat) {
            case HULL: return "HU";
            case LIFT: return "LF";
            case POWER: return "PW";
            case WEAPONS: return "WP";
            case CONTROL: return "CT";
            case CARGO: return "CG";
            default: return "--";
        }
    }

    private enum BattleTacticalV2State {
        NOMINAL(SAFE_GREEN),
        WARNING(ALERT_AMBER),
        CRITICAL(DANGER_RED);

        final int color;
        BattleTacticalV2State(int color) { this.color = color; }
    }

    private static final class BattleTacticalV2Card {
        final String name;
        final double healthPercent;
        final BattleTacticalV2State state;
        final int severityScore;
        final DamageObserverSettings.ObserverCategory category;
        final BlockPos anchor;
        final int diameter;
        final boolean weaponDetected;
        final boolean weaponReady;
        final boolean weaponDestroyed;
        final BlockPos observerPos; // server position of the Damage Observer BE (== snapshot.sourcePos()); used for voxel-map requests.
        BattleTacticalV2Card(String name, double healthPercent, BattleTacticalV2State state, int severityScore,
                             DamageObserverSettings.ObserverCategory category, BlockPos anchor, int diameter,
                             boolean weaponDetected, boolean weaponReady, boolean weaponDestroyed,
                             BlockPos observerPos) {
            this.name = name;
            this.healthPercent = healthPercent;
            this.state = state;
            this.severityScore = severityScore;
            this.category = category;
            this.anchor = anchor;
            this.diameter = diameter;
            this.weaponDetected = weaponDetected;
            this.weaponReady = weaponReady;
            this.weaponDestroyed = weaponDestroyed;
            this.observerPos = observerPos;
        }
    }

    private static final class BattleTacticalV2Readout {
        final List<BattleTacticalV2Card> cards;
        final long freshnessTicks;
        final BlockPos bridgePos;
        final double globalIntegrity;
        BattleTacticalV2Readout(List<BattleTacticalV2Card> cards, long freshnessTicks, BlockPos bridgePos,
                                double globalIntegrity) {
            this.cards = cards;
            this.freshnessTicks = freshnessTicks;
            this.bridgePos = bridgePos;
            this.globalIntegrity = globalIntegrity;
        }
    }

    private static void drawGuiChrome(GuiGraphics g, int x, int y, int w, int h, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, theme.graphBg);
        borderGui(g, x, y, w, h, theme.border);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, brighten(theme.border, 1.08f));
    }

    private static void drawPinnedHudChrome(GuiGraphics g, int x, int y, int w, int h, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.94f)));
        borderGui(g, x, y, w, h, darken(theme.border, 0.96f));
    }
    
    private static void drawMetricCardTexture(NativeImage image, int x, int y, int w, int h, String label, String value, DisplayTheme theme, int accentColor) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.92f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, label, x + 4, y + 3, theme.dim, 1);
        final int scale = (w >= 86) ? 2 : 1;
        BitmapFont.drawCentered(image, truncate(value, Math.max(5, w / (6 * scale))), x, y + h / 2 - BitmapFont.textHeight(scale) / 2 + 3, w, accentColor, scale);
    }
    
    private static void drawMetricCardGui(GuiGraphics g, Font font, int x, int y, int w, int h, String label, String value, DisplayTheme theme, int accentColor) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.92f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, label, x + 4, y + 3, theme.dim);
        g.drawCenteredString(font, truncateGui(font, value, w - 8), x + w / 2, y + h / 2 - font.lineHeight / 2 + 3, accentColor);
    }

    private static void drawAccumulatorBatteryTexture(NativeImage image, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 2, theme.border);
        final int terminalW = Math.max(4, w / 34);
        TextureGraphRenderer.fillRect(image, x + w, y + h / 3, terminalW, Math.max(5, h / 3), theme.border);
        final int innerX = x + 4;
        final int innerY = y + 4;
        final int innerW = Math.max(1, w - 8);
        final int innerH = Math.max(1, h - 8);
        final float fillNorm = Math.max(0.0f, Math.min(1.0f, readout.fillPercent() / 100.0f));
        final int fillW = Math.round(innerW * fillNorm);
        if (fillW > 0) {
            TextureGraphRenderer.fillRect(image, innerX, innerY, fillW, innerH, darken(readout.state().color, 0.78f));
        }
        for (int i = 1; i < 4; i++) {
            final int sx = innerX + i * innerW / 4;
            TextureGraphRenderer.fillRect(image, sx, innerY, 1, innerH, darken(theme.border, 0.8f));
        }
        if (readout.hasEnergyFlow()) {
            final int flowColor = readout.flowColor();
            final int span = Math.max(6, innerW - 4);
            for (int i = 0; i < 4; i++) {
                final int phase = Math.floorMod((int)(tick * 3L + i * span / 4), span);
                final int px = readout.isDischargingMode() ? innerX + innerW - 3 - phase : innerX + 2 + phase;
                TextureGraphRenderer.fillRect(image, px - 1, y + h / 2 - 1, 3, 3, flowColor);
            }
        }
        BitmapFont.drawCentered(image, formatEnergyRate(readout.netRate()), x, y + h / 2 - 4, w, readout.flowColor(), 1);
    }

    private static void drawAccumulatorBatteryGui(GuiGraphics g, Font font, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.88f));
        borderGui(g, x, y, w, h, theme.border);
        final int terminalW = Math.max(5, w / 34);
        g.fill(x + w, y + h / 3, x + w + terminalW, y + h / 3 + Math.max(8, h / 3), theme.border);
        final int innerX = x + 5;
        final int innerY = y + 5;
        final int innerW = Math.max(1, w - 10);
        final int innerH = Math.max(1, h - 10);
        final float fillNorm = Math.max(0.0f, Math.min(1.0f, readout.fillPercent() / 100.0f));
        final int fillW = Math.round(innerW * fillNorm);
        if (fillW > 0) {
            g.fill(innerX, innerY, innerX + fillW, innerY + innerH, darken(readout.state().color, 0.78f));
        }
        for (int i = 1; i < 4; i++) {
            final int sx = innerX + i * innerW / 4;
            g.fill(sx, innerY, sx + 1, innerY + innerH, darken(theme.border, 0.8f));
        }
        if (readout.hasEnergyFlow()) {
            final int flowColor = readout.flowColor();
            final int span = Math.max(6, innerW - 4);
            for (int i = 0; i < 4; i++) {
                final int phase = Math.floorMod((int)(tick * 3L + i * span / 4), span);
                final int px = readout.isDischargingMode() ? innerX + innerW - 3 - phase : innerX + 2 + phase;
                g.fill(px - 2, y + h / 2 - 2, px + 3, y + h / 2 + 3, flowColor);
            }
        }
        g.drawCenteredString(font, formatEnergyRate(readout.netRate()), x + w / 2, y + h / 2 - font.lineHeight / 2, readout.flowColor());
    }

    private static void drawAccumulatorFlowDiagramTexture(NativeImage image, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int headerH = Math.max(12, Math.min(18, h / 7));
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, headerH, darken(theme.graphBg, 0.78f));
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, 2, readout.flowColor());
        TextureGraphRenderer.fillRect(image, x + 1, y + headerH - 2, w - 2, 2, darken(readout.flowColor(), 0.82f));
        BitmapFont.drawString(image, "IN " + formatEnergyRate(readout.inputRate()), x + 5, y + 4, readout.isInputActive() ? SAFE_GREEN : theme.dim, 1);
        BitmapFont.drawCentered(image, readout.state().shortLabel + " " + formatEnergyRate(readout.netRate()), x, y + 4, w, readout.flowColor(), 1);
        BitmapFont.drawRight(image, "OUT " + formatEnergyRate(readout.outputRate()), x + w - 5, y + 4, readout.isOutputActive() ? DANGER_RED : theme.dim, 1);

        final int bodyY = y + headerH + 4;
        final int bodyH = Math.max(32, h - headerH - 10);
        final int gap = Math.max(5, w / 54);
        final int railW = Math.max(44, Math.min(72, w / 5));
        final int leftX = x + 6;
        final int rightX = x + w - railW - 6;
        final int coreX = leftX + railW + gap;
        final int coreW = Math.max(42, rightX - coreX - gap);
        final int laneInY = bodyY + Math.max(12, bodyH / 3);
        final int laneOutY = bodyY + Math.max(20, bodyH * 2 / 3);

        drawAccumulatorRailTexture(image, leftX, bodyY, railW, bodyH, "INPUT", formatEnergy(readout.totalIn()), formatEnergyRate(readout.inputRate()), readout.inputRate(), SAFE_GREEN, true, theme, tick);
        drawAccumulatorCoreBankTexture(image, coreX, bodyY, coreW, bodyH, readout, theme);
        drawAccumulatorRailTexture(image, rightX, bodyY, railW, bodyH, "OUTPUT", formatEnergy(readout.totalOut()), formatEnergyRate(readout.outputRate()), readout.outputRate(), DANGER_RED, false, theme, tick);

        drawAccumulatorFlowTexture(image, leftX + railW, coreX, laneInY, true, readout.isInputActive(), SAFE_GREEN, theme, tick);
        drawAccumulatorFlowTexture(image, coreX + coreW, rightX, laneOutY, true, readout.isOutputActive(), DANGER_RED, theme, tick);

        drawAccumulatorPortStripTexture(image, coreX + 5, bodyY + bodyH - 10, coreW - 10, 7, readout, theme);
    }

    private static void drawAccumulatorRailTexture(NativeImage image, int x, int y, int w, int h, String label, String total, String rate, float rateValue, int color, boolean downward, DisplayTheme theme, long tick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.82f));
        final boolean active = rateValue > ENERGY_FLOW_EPSILON;
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, active ? color : theme.border);
        if (active) {
            TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, 3, darken(color, 0.9f));
        }
        BitmapFont.drawCentered(image, label, x, y + 4, w, theme.dim, 1);
        BitmapFont.drawCentered(image, truncate(rate, Math.max(5, w / 6)), x, y + 17, w, active ? color : theme.dim, 1);
        final int busX = x + w / 2;
        final int busTop = y + 31;
        final int busH = Math.max(8, h - 48);
        TextureGraphRenderer.fillRect(image, busX - 1, busTop, 2, busH, darken(theme.border, 0.68f));
        if (active) {
            final int span = Math.max(8, busH - 3);
            for (int pulse = 0; pulse < 5; pulse++) {
                final int phase = Math.floorMod((int)(tick * 3L + pulse * span / 5), span);
                final int py = downward ? busTop + phase : busTop + span - phase;
                TextureGraphRenderer.fillRect(image, busX - 3, py - 1, 6, 4, color);
            }
        }
        BitmapFont.drawCentered(image, truncate(total, Math.max(5, w / 6)), x, y + h - 11, w, color, 1);
    }

    private static void drawAccumulatorCoreBankTexture(NativeImage image, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.77f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, readout.flowColor());
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, 3, darken(readout.flowColor(), 0.9f));
        BitmapFont.drawString(image, readout.state().shortLabel, x + 5, y + 4, readout.flowColor(), 1);
        BitmapFont.drawRight(image, "B " + formatCompact(readout.blocks()), x + w - 5, y + 4, theme.dim, 1);
        final int gridX = x + 8;
        final int gridY = y + 18;
        final int gridW = Math.max(8, w - 16);
        final int gridH = Math.max(10, h - 43);
        final int cols = Math.max(3, Math.min(7, gridW / 18));
        final int rows = Math.max(2, Math.min(6, gridH / 13));
        final int cellGap = 2;
        final int cellW = Math.max(3, (gridW - cellGap * (cols - 1)) / cols);
        final int cellH = Math.max(4, (gridH - cellGap * (rows - 1)) / rows);
        final int totalCells = cols * rows;
        final int activeCells = Math.round(totalCells * Math.max(0.0f, Math.min(1.0f, readout.fillPercent() / 100.0f)));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                final int logicalIndex = (rows - 1 - row) * cols + col;
                final int cellColor = logicalIndex < activeCells ? darken(readout.state().color, 0.82f) : darken(theme.grid, 0.72f);
                final int cellX = gridX + col * (cellW + cellGap);
                final int cellY = gridY + row * (cellH + cellGap);
                TextureGraphRenderer.fillRect(image, cellX, cellY, cellW, cellH, cellColor);
                TextureGraphRenderer.drawBorder(image, cellX, cellY, cellW, cellH, 1, darken(theme.border, 0.64f));
            }
        }
        BitmapFont.drawCentered(image, String.format("%.1f%%", readout.fillPercent()), x, y + h - 22, w, readout.state().color, 1);
        BitmapFont.drawCentered(image, truncate(formatEnergy(readout.stored()), Math.max(5, w / 6)), x, y + h - 11, w, theme.text, 1);
    }

    private static void drawAccumulatorFlowTexture(NativeImage image, int startX, int endX, int y, boolean toRight, boolean active, int color, DisplayTheme theme, long tick) {
        final int left = Math.min(startX, endX);
        final int right = Math.max(startX, endX);
        final int width = right - left;
        if (width <= 0) {
            return;
        }
        TextureGraphRenderer.fillRect(image, left, y - 1, width, 2, active ? darken(color, 0.72f) : darken(theme.grid, 0.7f));
        if (!active) {
            return;
        }
        final int span = Math.max(8, width - 2);
        for (int pulse = 0; pulse < 4; pulse++) {
            final int phase = Math.floorMod((int)(tick * 4L + pulse * span / 4), span);
            final int pulseX = toRight ? left + 1 + phase : right - 2 - phase;
            TextureGraphRenderer.fillRect(image, pulseX - 1, y - 3, 4, 6, color);
        }
    }

    private static void drawAccumulatorPortStripTexture(NativeImage image, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.74f));
        final int activePorts = Math.max(0, Math.min(10, Math.round(readout.connectors())));
        final int gap = Math.max(4, w / 14);
        final int startX = x + Math.max(2, (w - gap * 9) / 2);
        for (int port = 0; port < 10; port++) {
            final int color = port < activePorts ? SAFE_GREEN : darken(theme.grid, 0.8f);
            TextureGraphRenderer.fillRect(image, startX + port * gap, y + Math.max(1, h / 2 - 1), 3, 3, color);
        }
    }

    private static void drawAccumulatorFlowDiagramGui(GuiGraphics g, Font font, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        final int headerH = Math.max(18, Math.min(24, h / 6));
        g.fill(x + 1, y + 1, x + w - 1, y + headerH, darken(theme.graphBg, 0.78f));
        g.fill(x + 1, y + 1, x + w - 1, y + 4, readout.flowColor());
        g.fill(x + 1, y + headerH - 3, x + w - 1, y + headerH, darken(readout.flowColor(), 0.82f));
        g.drawString(font, "IN " + formatEnergyRate(readout.inputRate()), x + 6, y + 6, readout.isInputActive() ? SAFE_GREEN : theme.dim);
        g.drawCenteredString(font, readout.state().shortLabel + " " + formatEnergyRate(readout.netRate()), x + w / 2, y + 6, readout.flowColor());
        final String outRate = "OUT " + formatEnergyRate(readout.outputRate());
        g.drawString(font, outRate, x + w - font.width(outRate) - 6, y + 6, readout.isOutputActive() ? DANGER_RED : theme.dim);

        final int bodyY = y + headerH + 6;
        final int bodyH = Math.max(48, h - headerH - 12);
        final int gap = Math.max(8, w / 50);
        final int railW = Math.max(74, Math.min(110, w / 5));
        final int leftX = x + 8;
        final int rightX = x + w - railW - 8;
        final int coreX = leftX + railW + gap;
        final int coreW = Math.max(70, rightX - coreX - gap);
        final int laneInY = bodyY + Math.max(20, bodyH / 3);
        final int laneOutY = bodyY + Math.max(32, bodyH * 2 / 3);

        drawAccumulatorRailGui(g, font, leftX, bodyY, railW, bodyH, "INPUT", formatEnergy(readout.totalIn()), formatEnergyRate(readout.inputRate()), readout.inputRate(), SAFE_GREEN, true, theme, tick);
        drawAccumulatorCoreBankGui(g, font, coreX, bodyY, coreW, bodyH, readout, theme);
        drawAccumulatorRailGui(g, font, rightX, bodyY, railW, bodyH, "OUTPUT", formatEnergy(readout.totalOut()), formatEnergyRate(readout.outputRate()), readout.outputRate(), DANGER_RED, false, theme, tick);

        drawAccumulatorFlowGui(g, leftX + railW, coreX, laneInY, true, readout.isInputActive(), SAFE_GREEN, theme, tick);
        drawAccumulatorFlowGui(g, coreX + coreW, rightX, laneOutY, true, readout.isOutputActive(), DANGER_RED, theme, tick);

        drawAccumulatorPortStripGui(g, coreX + 8, bodyY + bodyH - 16, coreW - 16, 10, readout, theme);
    }

    private static void drawAccumulatorRailGui(GuiGraphics g, Font font, int x, int y, int w, int h, String label, String total, String rate, float rateValue, int color, boolean downward, DisplayTheme theme, long tick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.82f));
        final boolean active = rateValue > ENERGY_FLOW_EPSILON;
        borderGui(g, x, y, w, h, active ? color : theme.border);
        if (active) {
            g.fill(x + 1, y + 1, x + w - 1, y + 5, darken(color, 0.9f));
        }
        g.drawCenteredString(font, label, x + w / 2, y + 6, theme.dim);
        g.drawCenteredString(font, truncateGui(font, rate, w - 8), x + w / 2, y + 20, active ? color : theme.dim);
        final int busX = x + w / 2;
        final int busTop = y + 38;
        final int busH = Math.max(12, h - 62);
        g.fill(busX - 1, busTop, busX + 1, busTop + busH, darken(theme.border, 0.68f));
        if (active) {
            final int span = Math.max(8, busH - 4);
            for (int pulse = 0; pulse < 5; pulse++) {
                final int phase = Math.floorMod((int)(tick * 3L + pulse * span / 5), span);
                final int py = downward ? busTop + phase : busTop + span - phase;
                g.fill(busX - 4, py - 1, busX + 5, py + 4, color);
            }
        }
        g.drawCenteredString(font, truncateGui(font, total, w - 8), x + w / 2, y + h - font.lineHeight - 6, color);
    }

    private static void drawAccumulatorCoreBankGui(GuiGraphics g, Font font, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.77f));
        borderGui(g, x, y, w, h, readout.flowColor());
        g.fill(x + 1, y + 1, x + w - 1, y + 5, darken(readout.flowColor(), 0.9f));
        g.drawString(font, readout.state().shortLabel, x + 8, y + 6, readout.flowColor());
        final String blocks = "Blocks " + formatCompact(readout.blocks());
        g.drawString(font, blocks, x + w - font.width(blocks) - 8, y + 6, theme.dim);
        final int gridX = x + 10;
        final int gridY = y + 24;
        final int gridW = Math.max(12, w - 20);
        final int gridH = Math.max(16, h - 58);
        final int cols = Math.max(3, Math.min(7, gridW / 25));
        final int rows = Math.max(2, Math.min(6, gridH / 18));
        final int cellGap = 3;
        final int cellW = Math.max(5, (gridW - cellGap * (cols - 1)) / cols);
        final int cellH = Math.max(6, (gridH - cellGap * (rows - 1)) / rows);
        final int totalCells = cols * rows;
        final int activeCells = Math.round(totalCells * Math.max(0.0f, Math.min(1.0f, readout.fillPercent() / 100.0f)));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                final int logicalIndex = (rows - 1 - row) * cols + col;
                final int cellColor = logicalIndex < activeCells ? darken(readout.state().color, 0.82f) : darken(theme.grid, 0.72f);
                final int cellX = gridX + col * (cellW + cellGap);
                final int cellY = gridY + row * (cellH + cellGap);
                g.fill(cellX, cellY, cellX + cellW, cellY + cellH, cellColor);
                borderGui(g, cellX, cellY, cellW, cellH, darken(theme.border, 0.64f));
            }
        }
        g.drawCenteredString(font, String.format("%.1f%%", readout.fillPercent()), x + w / 2, y + h - font.lineHeight * 2 - 7, readout.state().color);
        g.drawCenteredString(font, truncateGui(font, formatEnergy(readout.stored()), w - 12), x + w / 2, y + h - font.lineHeight - 5, theme.text);
    }

    private static void drawAccumulatorFlowGui(GuiGraphics g, int startX, int endX, int y, boolean toRight, boolean active, int color, DisplayTheme theme, long tick) {
        final int left = Math.min(startX, endX);
        final int right = Math.max(startX, endX);
        final int width = right - left;
        if (width <= 0) {
            return;
        }
        g.fill(left, y - 1, right, y + 1, active ? darken(color, 0.72f) : darken(theme.grid, 0.7f));
        if (!active) {
            return;
        }
        final int span = Math.max(8, width - 2);
        for (int pulse = 0; pulse < 4; pulse++) {
            final int phase = Math.floorMod((int)(tick * 4L + pulse * span / 4), span);
            final int pulseX = toRight ? left + 1 + phase : right - 2 - phase;
            g.fill(pulseX - 2, y - 3, pulseX + 3, y + 4, color);
        }
    }

    private static void drawAccumulatorPortStripGui(GuiGraphics g, int x, int y, int w, int h, AccumulatorReadout readout, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.74f));
        final int activePorts = Math.max(0, Math.min(10, Math.round(readout.connectors())));
        final int gap = Math.max(6, w / 14);
        final int startX = x + Math.max(2, (w - gap * 9) / 2);
        for (int port = 0; port < 10; port++) {
            final int color = port < activePorts ? SAFE_GREEN : darken(theme.grid, 0.8f);
            g.fill(startX + port * gap, y + Math.max(2, h / 2 - 1), startX + port * gap + 4, y + Math.max(6, h / 2 + 3), color);
        }
    }
    
    private static void drawBadgeTexture(NativeImage image, int x, int y, int w, int h, String text, int color) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(color, 0.65f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, color);
        BitmapFont.drawCentered(image, truncate(text, Math.max(4, w / 6)), x, y + 2, w, -1, 1);
    }
    
    private static void drawBadgeGui(GuiGraphics g, Font font, int x, int y, int w, int h, String text, int color) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(color, 0.65f)));
        borderGui(g, x, y, w, h, color);
        g.drawCenteredString(font, truncateGui(font, text, w - 6), x + w / 2, y + (h - font.lineHeight) / 2, applyPinnedHudOpacity(-1));
    }

    private static void drawScaledCenteredStringGui(GuiGraphics g, Font font, String text, int centerX, int topY, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(centerX, topY, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawCenteredString(font, text, 0, 0, applyPinnedHudOpacity(color));
        g.pose().popPose();
    }

    private static void drawPinnedMetricCardGui(GuiGraphics g, Font font, int x, int y, int w, int h, String label, String value, String detail, DisplayTheme theme, int accentColor, float valueScale) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.9f)));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, label, x + 4, y + 3, applyPinnedHudOpacity(theme.dim));
        final String scaledValue = truncateGui(font, value, Math.max(18, Math.round((w - 12) / Math.max(1.0f, valueScale))));
        final int detailOffset = (detail == null || detail.isEmpty()) ? 0 : font.lineHeight + 2;
        final int scaledHeight = Math.round(font.lineHeight * valueScale);
        final int valueY = Math.max(y + 12, y + (h - scaledHeight - detailOffset) / 2);
        drawScaledCenteredStringGui(g, font, scaledValue, x + w / 2, valueY, valueScale, accentColor);
        if (detail != null && !detail.isEmpty()) {
            g.drawCenteredString(font, truncateGui(font, detail, w - 8), x + w / 2, y + h - font.lineHeight - 4, applyPinnedHudOpacity(mixColor(theme.dim, theme.text, 0.35f)));
        }
    }

    private static void drawPinnedMarginCardGui(GuiGraphics g, Font font, int x, int y, int w, int h, String label, String value, float numericValue, float maxAbs, DisplayTheme theme, int accentColor) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.9f)));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, label, x + 4, y + 3, applyPinnedHudOpacity(theme.dim));
        final String scaledValue = truncateGui(font, value, Math.max(16, Math.round((w - 12) / 1.18f)));
        drawScaledCenteredStringGui(g, font, scaledValue, x + w / 2, y + 14, 1.18f, accentColor);
        final int barY = y + h - 11;
        drawCenteredBarGui(g, x + 5, barY, w - 10, 5, numericValue, maxAbs, theme, accentColor);
    }

    private static void drawPinnedReadoutCardGui(GuiGraphics g, Font font, int x, int y, int w, int h, String label, String value, DisplayTheme theme, int accentColor, float valueScale) {
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.9f)));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, label, x + 4, y + 3, applyPinnedHudOpacity(theme.dim));
        final String scaledValue = truncateGui(font, value, Math.max(14, Math.round((w - 12) / Math.max(1.0f, valueScale))));
        final int scaledHeight = Math.round(font.lineHeight * valueScale);
        final int valueY = Math.max(y + 11, y + (h - scaledHeight) / 2 + 3);
        drawScaledCenteredStringGui(g, font, scaledValue, x + w / 2, valueY, valueScale, accentColor);
    }
    
    private static void drawSparklineTexture(NativeImage image, int x, int y, int w, int h, TimeSeriesStore store, DisplayTheme theme, int lineColor, String label) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, label, x + 3, y + 2, theme.dim, 1);
        final int chartX = x + 3;
        final int chartY = y + 12;
        final int chartW = w - 6;
        final int chartH = Math.max(6, h - 15);
        drawSparklineTextureOnly(image, chartX, chartY, chartW, chartH, store, lineColor, theme.grid);
    }
    
    private static void drawSparklineGui(GuiGraphics g, int x, int y, int w, int h, TimeSeriesStore store, Font font, DisplayTheme theme, int lineColor, String label) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.88f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, label, x + 4, y + 3, theme.dim);
        final int chartX = x + 4;
        final int chartY = y + 14;
        final int chartW = w - 8;
        final int chartH = Math.max(8, h - 18);
        drawSparklineGuiOnly(g, chartX, chartY, chartW, chartH, store, lineColor, theme.grid);
    }
    
    private static void drawSparklineTextureOnly(NativeImage image, int x, int y, int w, int h, TimeSeriesStore store, int lineColor, int gridColor) {
        final float[] data = store.getDataForTimeScale(Math.max(30, Math.min(300, w / 2)));
        if (data.length < 2) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        if (range < 0.001f) {
            range = Math.max(1.0f, Math.abs(max) * 0.1f + 1.0f);
            min -= range * 0.5f;
            max = min + range;
        }
        final int midY = y + h / 2;
        TextureGraphRenderer.drawLine(image, x, midY, x + w, midY, darken(gridColor, 0.95f));
        int prevX = x;
        int prevY = mapToY(data[0], min, max, y, h);
        for (int i = 1; i < data.length; ++i) {
            final int px = x + i * (w - 1) / (data.length - 1);
            final int py = mapToY(data[i], min, max, y, h);
            TextureGraphRenderer.drawLine(image, prevX, prevY, px, py, lineColor);
            prevX = px;
            prevY = py;
        }
        TextureGraphRenderer.fillCircle(image, prevX, prevY, 2, lineColor);
    }
    
    private static void drawSparklineGuiOnly(GuiGraphics g, int x, int y, int w, int h, TimeSeriesStore store, int lineColor, int gridColor) {
        final float[] data = store.getDataForTimeScale(Math.max(30, Math.min(300, w / 2)));
        if (data.length < 2) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        if (range < 0.001f) {
            range = Math.max(1.0f, Math.abs(max) * 0.1f + 1.0f);
            min -= range * 0.5f;
            max = min + range;
        }
        g.fill(x, y + h / 2, x + w, y + h / 2 + 1, darken(gridColor, 0.95f));
        int prevX = x;
        int prevY = mapToY(data[0], min, max, y, h);
        for (int i = 1; i < data.length; ++i) {
            final int px = x + i * (w - 1) / (data.length - 1);
            final int py = mapToY(data[i], min, max, y, h);
            drawLineGui(g, prevX, prevY, px, py, lineColor);
            prevX = px;
            prevY = py;
        }
        g.fill(prevX - 1, prevY - 1, prevX + 2, prevY + 2, lineColor);
    }

    private static void renderStressLowerTexture(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, EfficiencyScore score, TimeSeriesStore stressSuStore, float capacity, int accentColor) {
        final int gap = Math.max(6, w / 48);
        int leftW = Math.max(92, (w - gap) / 2);
        int rightW = w - leftW - gap;
        if (rightW < 84) {
            rightW = 84;
            leftW = Math.max(92, w - rightW - gap);
        }
        final int rightX = x + leftW + gap;
        int statsH = Math.max(36, Math.min(56, h / 3));
        final int minGraphH = 26;
        if (h - statsH - gap < minGraphH) {
            statsH = Math.max(24, h - gap - minGraphH);
        }
        final int graphY = y + statsH + gap;
        final int graphH = Math.max(minGraphH, h - statsH - gap);
        final StressHistorySnapshot history = summarizeStressHistory(stressSuStore, rightW - 8);
        drawStressGradeTexture(image, x, y, leftW, h, theme, score);
        drawStressSummaryTexture(image, rightX, y, rightW, statsH, theme, history);
        drawStressHistoryTexture(image, rightX, graphY, rightW, graphH, theme, accentColor, history, capacity);
    }

    private static void renderStressLowerGui(GuiGraphics g, Font font, int x, int y, int w, int h, DisplayTheme theme, EfficiencyScore score, TimeSeriesStore stressSuStore, float capacity, int accentColor) {
        final int gap = Math.max(8, w / 42);
        int leftW = Math.max(120, (w - gap) / 2);
        int rightW = w - leftW - gap;
        if (rightW < 96) {
            rightW = 96;
            leftW = Math.max(120, w - rightW - gap);
        }
        final int rightX = x + leftW + gap;
        int statsH = Math.max(50, Math.min(84, h / 3));
        final int minGraphH = 44;
        if (h - statsH - gap < minGraphH) {
            statsH = Math.max(34, h - gap - minGraphH);
        }
        final int graphY = y + statsH + gap;
        final int graphH = Math.max(minGraphH, h - statsH - gap);
        final StressHistorySnapshot history = summarizeStressHistory(stressSuStore, rightW - 8);
        drawStressGradeGui(g, font, x, y, leftW, h, theme, score);
        drawStressSummaryGui(g, font, rightX, y, rightW, statsH, theme, history);
        drawStressHistoryGui(g, font, rightX, graphY, rightW, graphH, theme, accentColor, history, capacity);
    }

    private static void drawStressGradeTexture(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, EfficiencyScore score) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "HEALTH GRADE");
        if (score == null || "?".equals(score.grade())) {
            BitmapFont.drawCentered(image, "Warming up grade", x, y + h / 2 - 6, w, theme.text, 1);
            BitmapFont.drawCentered(image, "Need a live stress sample", x, y + h / 2 + 6, w, theme.dim, 1);
            return;
        }
        final int gradeScale = (w >= 160 && h >= 100) ? 4 : ((w >= 120 && h >= 84) ? 3 : 2);
        final int gradeLetterH = BitmapFont.textHeight(gradeScale);
        final int gradeY = contentY + 5;
        BitmapFont.drawCentered(image, score.grade(), x, gradeY, w, score.gradeColor(), gradeScale);
        final int scoreY = gradeY + gradeLetterH + 2;
        BitmapFont.drawCentered(image, String.format("%.1f / 100", score.compositeScore()), x, scoreY, w, theme.text, 1);
        final String desc = describeGrade(score.grade());
        final int descY = scoreY + 10;
        if (!desc.isEmpty()) {
            BitmapFont.drawCentered(image, desc, x, descY, w, theme.dim, 1);
        }
        final int barsTop = descY + 12;
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final int availableH = Math.max(24, y + h - barsTop - 4);
        final int rowH = Math.max(8, availableH / dims.length);
        final int labelW = Math.max(34, (int)(w * 0.32f));
        final int barX = x + labelW;
        final int barW = Math.max(18, w - labelW - 26);
        final int barH = Math.max(4, rowH - 3);
        for (int i = 0; i < dims.length; ++i) {
            final EfficiencyDimension dim = dims[i];
            final float pct = score.getDimensionPercent(dim);
            final int barY = barsTop + i * rowH;
            final int fillColor = dimensionScoreColor(dim, pct);
            BitmapFont.drawString(image, truncate(dim.getDisplayName(), Math.max(4, (labelW - 6) / 6)), x + 4, barY + 1, theme.text, 1);
            TextureGraphRenderer.fillRect(image, barX, barY + 2, barW, barH, theme.grid);
            final int fillW = Math.max(0, Math.round(barW * pct / 100.0f));
            if (fillW > 0) {
                TextureGraphRenderer.fillRect(image, barX, barY + 2, fillW, barH, fillColor);
            }
            BitmapFont.drawRight(image, String.format("%.0f%%", pct), x + w - 4, barY + 1, fillColor, 1);
        }
    }

    private static void drawStressGradeGui(GuiGraphics g, Font font, int x, int y, int w, int h, DisplayTheme theme, EfficiencyScore score) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "HEALTH GRADE");
        if (score == null || "?".equals(score.grade())) {
            g.drawCenteredString(font, "Warming up grade", x + w / 2, y + h / 2 - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "Need a live stress sample", x + w / 2, y + h / 2 + font.lineHeight + 2, theme.dim);
            return;
        }
        final float gradeScale = (w >= 220 && h >= 160) ? 3.0f : 2.0f;
        final int gradeTop = contentY + 8;
        g.pose().pushPose();
        g.pose().translate(x + w / 2.0f, gradeTop, 0.0f);
        g.pose().scale(gradeScale, gradeScale, 1.0f);
        g.drawCenteredString(font, score.grade(), 0, 0, score.gradeColor());
        g.pose().popPose();
        final int gradeH = Math.round(font.lineHeight * gradeScale);
        final int scoreY = gradeTop + gradeH + 4;
        g.drawCenteredString(font, String.format("%.1f / 100", score.compositeScore()), x + w / 2, scoreY, theme.text);
        final String desc = describeGrade(score.grade());
        final int descY = scoreY + font.lineHeight + 1;
        if (!desc.isEmpty()) {
            g.drawCenteredString(font, desc, x + w / 2, descY, theme.dim);
        }
        final int barsTop = descY + font.lineHeight + 7;
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final int availableH = Math.max(40, y + h - barsTop - 6);
        final int rowH = Math.max(12, availableH / dims.length);
        final int labelW = Math.max(54, w / 3);
        final int barX = x + labelW;
        final int barW = Math.max(28, w - labelW - 40);
        final int barH = Math.max(6, rowH - 5);
        for (int i = 0; i < dims.length; ++i) {
            final EfficiencyDimension dim = dims[i];
            final float pct = score.getDimensionPercent(dim);
            final int barY = barsTop + i * rowH;
            final int fillColor = dimensionScoreColor(dim, pct);
            g.drawString(font, truncateGui(font, dim.getDisplayName(), labelW - 6), x + 4, barY + 1, theme.text);
            g.fill(barX, barY + 2, barX + barW, barY + 2 + barH, theme.grid);
            final int fillW = Math.max(0, Math.round(barW * pct / 100.0f));
            if (fillW > 0) {
                g.fill(barX, barY + 2, barX + fillW, barY + 2 + barH, fillColor);
            }
            borderGui(g, barX, barY + 2, barW, barH, theme.border);
            g.drawString(font, String.format("%.0f%%", pct), x + w - 4 - font.width(String.format("%.0f%%", pct)), barY + 1, fillColor);
        }
    }

    private static void drawStressSummaryTexture(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, StressHistorySnapshot snapshot) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "RECENT SU");
        final int innerX = x + 3;
        final int innerY = contentY + 2;
        final int innerW = w - 6;
        final int innerH = Math.max(14, y + h - innerY - 3);
        final int gap = 4;
        final int cardW = Math.max(22, (innerW - gap) / 2);
        final int cardH = Math.max(12, (innerH - gap) / 2);
        drawMetricCardTexture(image, innerX, innerY, cardW, cardH, "PEAK", snapshot.sampleCount() > 0 ? formatCompact(snapshot.peak()) : "--", theme, ALERT_AMBER);
        drawMetricCardTexture(image, innerX + cardW + gap, innerY, innerW - cardW - gap, cardH, "AVG", snapshot.sampleCount() > 0 ? formatCompact(snapshot.average()) : "--", theme, theme.accent);
        drawMetricCardTexture(image, innerX, innerY + cardH + gap, cardW, innerH - cardH - gap, "RANGE", snapshot.sampleCount() > 0 ? formatCompact(snapshot.range()) : "--", theme, theme.text);
        drawMetricCardTexture(image, innerX + cardW + gap, innerY + cardH + gap, innerW - cardW - gap, innerH - cardH - gap, "DELTA", snapshot.sampleCount() >= 2 ? formatSignedCompact(snapshot.delta()) : "--", theme, deltaColor(snapshot.delta(), snapshot.sampleCount()));
    }

    private static void drawStressSummaryGui(GuiGraphics g, Font font, int x, int y, int w, int h, DisplayTheme theme, StressHistorySnapshot snapshot) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "RECENT SU");
        final int innerX = x + 4;
        final int innerY = contentY + 2;
        final int innerW = w - 8;
        final int innerH = Math.max(20, y + h - innerY - 4);
        final int gap = 6;
        final int cardW = Math.max(30, (innerW - gap) / 2);
        final int cardH = Math.max(18, (innerH - gap) / 2);
        drawMetricCardGui(g, font, innerX, innerY, cardW, cardH, "PEAK", snapshot.sampleCount() > 0 ? formatCompact(snapshot.peak()) : "--", theme, ALERT_AMBER);
        drawMetricCardGui(g, font, innerX + cardW + gap, innerY, innerW - cardW - gap, cardH, "AVG", snapshot.sampleCount() > 0 ? formatCompact(snapshot.average()) : "--", theme, theme.accent);
        drawMetricCardGui(g, font, innerX, innerY + cardH + gap, cardW, innerH - cardH - gap, "RANGE", snapshot.sampleCount() > 0 ? formatCompact(snapshot.range()) : "--", theme, theme.text);
        drawMetricCardGui(g, font, innerX + cardW + gap, innerY + cardH + gap, innerW - cardW - gap, innerH - cardH - gap, "DELTA", snapshot.sampleCount() >= 2 ? formatSignedCompact(snapshot.delta()) : "--", theme, deltaColor(snapshot.delta(), snapshot.sampleCount()));
    }

    private static void drawStressHistoryTexture(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, int accentColor, StressHistorySnapshot snapshot, float capacity) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "SU HISTORY");
        final String windowText = formatDurationCompact(snapshot.windowSeconds());
        if (!windowText.isEmpty()) {
            BitmapFont.drawRight(image, windowText, x + w - 4, y + 3, theme.dim, 1);
        }
        final int chartX = x + 4;
        final int chartY = contentY + 2;
        final int chartW = w - 8;
        final int chartH = Math.max(8, y + h - chartY - 3);
        if (snapshot.sampleCount() < 2) {
            BitmapFont.drawCentered(image, "Warming up history", x, y + h / 2 - 6, w, theme.text, 1);
            BitmapFont.drawCentered(image, "Need more SU samples", x, y + h / 2 + 6, w, theme.dim, 1);
            return;
        }
        drawStressHistoryGraphTexture(image, chartX, chartY, chartW, chartH, snapshot.data(), capacity, accentColor, ALERT_AMBER, theme.grid);
    }

    private static void drawStressHistoryGui(GuiGraphics g, Font font, int x, int y, int w, int h, DisplayTheme theme, int accentColor, StressHistorySnapshot snapshot, float capacity) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "SU HISTORY");
        final String windowText = formatDurationCompact(snapshot.windowSeconds());
        if (!windowText.isEmpty()) {
            g.drawString(font, windowText, x + w - 4 - font.width(windowText), y + 3, theme.dim);
        }
        final int chartX = x + 4;
        final int chartY = contentY + 2;
        final int chartW = w - 8;
        final int chartH = Math.max(12, y + h - chartY - 3);
        if (snapshot.sampleCount() < 2) {
            g.drawCenteredString(font, "Warming up history", x + w / 2, y + h / 2 - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "Need more SU samples", x + w / 2, y + h / 2 + font.lineHeight + 2, theme.dim);
            return;
        }
        drawStressHistoryGraphGui(g, chartX, chartY, chartW, chartH, snapshot.data(), capacity, accentColor, ALERT_AMBER, theme.grid);
    }

    private static void drawStressHistoryGraphTexture(NativeImage image, int x, int y, int w, int h, float[] data, float capacity, int lineColor, int referenceColor, int gridColor) {
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (capacity > 0.0f) {
            min = Math.min(min, capacity);
            max = Math.max(max, capacity);
        }
        float range = max - min;
        if (range < 0.001f) {
            range = Math.max(1.0f, Math.abs(max) * 0.1f + 1.0f);
            min -= range * 0.5f;
            max = min + range;
        }
        for (int i = 1; i < 4; ++i) {
            final int gy = y + i * (h - 1) / 4;
            TextureGraphRenderer.drawLine(image, x, gy, x + w, gy, darken(gridColor, 0.95f));
        }
        if (capacity > 0.0f) {
            final int capY = mapToY(capacity, min, max, y, h);
            TextureGraphRenderer.drawLine(image, x, capY, x + w, capY, darken(referenceColor, 0.9f));
        }
        int prevX = x;
        int prevY = mapToY(data[0], min, max, y, h);
        for (int i = 1; i < data.length; ++i) {
            final int px = x + i * (w - 1) / (data.length - 1);
            final int py = mapToY(data[i], min, max, y, h);
            TextureGraphRenderer.drawLine(image, prevX, prevY, px, py, lineColor);
            prevX = px;
            prevY = py;
        }
        TextureGraphRenderer.fillCircle(image, prevX, prevY, 2, lineColor);
    }

    private static void drawStressHistoryGraphGui(GuiGraphics g, int x, int y, int w, int h, float[] data, float capacity, int lineColor, int referenceColor, int gridColor) {
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (capacity > 0.0f) {
            min = Math.min(min, capacity);
            max = Math.max(max, capacity);
        }
        float range = max - min;
        if (range < 0.001f) {
            range = Math.max(1.0f, Math.abs(max) * 0.1f + 1.0f);
            min -= range * 0.5f;
            max = min + range;
        }
        for (int i = 1; i < 4; ++i) {
            final int gy = y + i * (h - 1) / 4;
            g.fill(x, gy, x + w, gy + 1, darken(gridColor, 0.95f));
        }
        if (capacity > 0.0f) {
            final int capY = mapToY(capacity, min, max, y, h);
            g.fill(x, capY, x + w, capY + 1, darken(referenceColor, 0.9f));
        }
        int prevX = x;
        int prevY = mapToY(data[0], min, max, y, h);
        for (int i = 1; i < data.length; ++i) {
            final int px = x + i * (w - 1) / (data.length - 1);
            final int py = mapToY(data[i], min, max, y, h);
            drawLineGui(g, prevX, prevY, px, py, lineColor);
            prevX = px;
            prevY = py;
        }
        g.fill(prevX - 1, prevY - 1, prevX + 2, prevY + 2, lineColor);
    }

    private static int drawSectionTexture(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, String title) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, 1, brighten(theme.border, 1.08f));
        BitmapFont.drawString(image, title, x + 4, y + 3, theme.dim, 1);
        return y + 13;
    }

    private static int drawSectionGui(GuiGraphics g, Font font, int x, int y, int w, int h, DisplayTheme theme, String title) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, brighten(theme.border, 1.08f));
        g.drawString(font, title, x + 4, y + 3, theme.dim);
        return y + 15;
    }

    private static StressHistorySnapshot summarizeStressHistory(TimeSeriesStore store, int graphWidth) {
        if (store == null) {
            return new StressHistorySnapshot(new float[0], 0.0f, 0.0f, 0.0f, 0.0f, 0, 0);
        }
        final int timeScaleSeconds = Math.max(45, Math.min(240, Math.max(40, graphWidth) / 2));
        final float[] data = store.getDataForTimeScale(timeScaleSeconds);
        if (data.length == 0) {
            return new StressHistorySnapshot(data, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0);
        }
        float min = data[0];
        float max = data[0];
        float sum = 0.0f;
        for (float value : data) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        final float delta = (data.length >= 2) ? (data[data.length - 1] - data[0]) : 0.0f;
        final int windowSeconds = Math.max(0, data.length - 1) * Math.max(1, store.getSampleIntervalTicks()) / 20;
        return new StressHistorySnapshot(data, max, sum / data.length, max - min, delta, windowSeconds, data.length);
    }

    private static String describeGrade(String grade) {
        return switch (grade) {
            case "A+" -> "Factory singing";
            case "A" -> "Very healthy";
            case "A-" -> "Running strong";
            case "B+" -> "Good load";
            case "B" -> "Steady";
            case "B-" -> "Usable";
            case "C+" -> "Working hard";
            case "C" -> "Tight margin";
            case "C-" -> "Shaky";
            case "D+" -> "Strained";
            case "D" -> "Near failure";
            case "D-" -> "Barely holding";
            case "F" -> "Overstressed";
            default -> "";
        };
    }

    private static String formatSignedCompact(float value) {
        if (value > 0.0f) {
            return "+" + formatCompact(value);
        }
        if (value < 0.0f) {
            return "-" + formatCompact(-value);
        }
        return "0";
    }

    private static String formatBlocksPerSecond(float value) {
        return formatCompact(value) + " b/s";
    }

    private static String formatSignedBlocksPerSecond(float value) {
        return formatSignedCompact(value) + " b/s";
    }

    private static String formatDurationCompact(int seconds) {
        if (seconds <= 0) {
            return "";
        }
        if (seconds >= 3600) {
            return String.format("%dh %dm", seconds / 3600, seconds % 3600 / 60);
        }
        if (seconds >= 60) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        }
        return seconds + "s";
    }

    private static int dimensionScoreColor(EfficiencyDimension dim, float pct) {
        if (pct >= 80.0f) {
            return dim.getBarColor();
        }
        if (pct >= 50.0f) {
            return mixColor(-22016, dim.getBarColor(), (pct - 50.0f) / 30.0f);
        }
        return mixColor(-48060, -22016, pct / 50.0f);
    }

    private static int deltaColor(float delta, int sampleCount) {
        if (sampleCount < 2) {
            return -1;
        }
        if (Math.abs(delta) < 0.001f) {
            return SAFE_GREEN;
        }
        return (delta > 0.0f) ? ALERT_AMBER : SAFE_GREEN;
    }

    private static void drawCenteredBarTexture(NativeImage image, int x, int y, int w, int h, float value, float maxAbs, DisplayTheme theme, int accentColor) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        final int centerX = x + w / 2;
        TextureGraphRenderer.fillRect(image, centerX, y + 2, 1, Math.max(1, h - 4), theme.border);
        final float normalized = Math.max(-1.0f, Math.min(1.0f, value / Math.max(0.001f, maxAbs)));
        final int fill = Math.round((w / 2 - 3) * Math.abs(normalized));
        if (fill <= 0) {
            return;
        }
        final int fillX = normalized >= 0.0f ? centerX + 1 : centerX - fill;
        TextureGraphRenderer.fillRect(image, fillX, y + 3, fill, Math.max(1, h - 6), accentColor);
    }

    private static void drawCenteredBarGui(GuiGraphics g, int x, int y, int w, int h, float value, float maxAbs, DisplayTheme theme, int accentColor) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.88f));
        borderGui(g, x, y, w, h, theme.border);
        final int centerX = x + w / 2;
        g.fill(centerX, y + 2, centerX + 1, y + h - 2, theme.border);
        final float normalized = Math.max(-1.0f, Math.min(1.0f, value / Math.max(0.001f, maxAbs)));
        final int fill = Math.round((w / 2 - 3) * Math.abs(normalized));
        if (fill <= 0) {
            return;
        }
        final int fillX = normalized >= 0.0f ? centerX + 1 : centerX - fill;
        g.fill(fillX, y + 3, fillX + fill, y + h - 3, accentColor);
    }

    private static void drawCenteredVerticalBarGui(GuiGraphics g, int x, int y, int w, int h, float value, float maxAbs, DisplayTheme theme, int accentColor) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.88f));
        borderGui(g, x, y, w, h, theme.border);
        final int centerY = y + h / 2;
        g.fill(x + 2, centerY, x + w - 2, centerY + 1, theme.border);
        final float normalized = Math.max(-1.0f, Math.min(1.0f, value / Math.max(0.001f, maxAbs)));
        final int fill = Math.round((h / 2 - 3) * Math.abs(normalized));
        if (fill <= 0) {
            return;
        }
        if (normalized >= 0.0f) {
            g.fill(x + 3, centerY - fill, x + w - 3, centerY, accentColor);
        }
        else {
            g.fill(x + 3, centerY + 1, x + w - 3, centerY + 1 + fill, accentColor);
        }
    }

    private static void renderAerolineDirectorTexture(NativeImage image, SourceSelection source, DisplayTheme theme, long renderTick) {
        final AerolineDirectorReadout readout = resolveAerolineDirectorReadout(source);
        final AeroFlightDeckReadout deck = readout.deck();
        final AerolineAnimatedFrame frame = captureAerolineFrame(deck);
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 30);
        final int titleScale = w >= 360 ? 2 : 1;
        final int modeBadgeW = Math.max(84, Math.min(118, w / 4));
        final int titleH = BitmapFont.textHeight(titleScale);
        BitmapFont.drawString(image, "AEROLINE DIRECTOR", pad, pad, theme.text, titleScale);
        final int subtitleY = pad + titleH + 3;
        BitmapFont.drawString(image, truncate(readout.subtitle(), Math.max(14, (w - pad * 2 - modeBadgeW - 12) / 6)), pad, subtitleY, theme.dim, 1);
        drawBadgeTexture(image, w - pad - modeBadgeW, pad + 1, modeBadgeW, 12, readout.modeLabel(), readout.state().color);
        final int statusY = subtitleY + 11;
        drawAerolineStatusStripTexture(image, pad, statusY, w - pad * 2, theme, deck);
        final int footerH = Math.max(52, h / 4);
        final int bodyY = statusY + 14;
        final int bodyH = Math.max(52, h - pad - footerH - bodyY - 4);
        drawAerolineDirectorWindowTexture(image, pad, bodyY, w - pad * 2, bodyH, readout, frame, theme, renderTick);
        drawAerolineFooterTexture(image, pad, h - pad - footerH, w - pad * 2, footerH, readout, deck, theme);
    }

    private static void renderAerolineDirectorGui(GuiGraphics g, Font font, int x, int y, int w, int h, BlockPos panelPos, SourceSelection source, DisplayTheme theme, long renderTick, float partialTick) {
        final AerolineDirectorReadout readout = resolveAerolineDirectorReadout(source);
        final AeroFlightDeckReadout deck = readout.deck();
        final AerolineAnimationState animation = AEROLINE_ANIMATION.computeIfAbsent(panelPos.immutable(), key -> new AerolineAnimationState());
        animation.capture(deck, renderTick);
        final AerolineAnimatedFrame frame = animation.sample(partialTick);
        final int pad = Math.max(8, w / 48);
        final int titleY = y + pad;
        final int modeBadgeW = Math.max(86, Math.min(126, w / 4));
        g.drawString(font, "AEROLINE DIRECTOR", x + pad, titleY, theme.text);
        final int subtitleY = titleY + font.lineHeight + 2;
        g.drawString(font, truncateGui(font, readout.subtitle(), Math.max(40, w - modeBadgeW - pad * 3)), x + pad, subtitleY, theme.dim);
        drawBadgeGui(g, font, x + w - pad - modeBadgeW, titleY - 1, modeBadgeW, 14, readout.modeLabel(), readout.state().color);
        final int statusY = subtitleY + font.lineHeight + 1;
        drawAerolineStatusStripGui(g, font, x + pad, statusY, w - pad * 2, theme, deck);
        final int footerH = Math.max(62, h / 4);
        final int bodyY = statusY + 15;
        final int bodyH = Math.max(60, y + h - footerH - pad - bodyY);
        drawAerolineDirectorWindowGui(g, font, x + pad, bodyY, w - pad * 2, bodyH, readout, frame, theme, renderTick + (long) partialTick);
        drawAerolineFooterGui(g, font, x + pad, y + h - footerH - pad, w - pad * 2, footerH, readout, deck, theme);
    }

    private static void renderAerolineDirectorCompactGui(GuiGraphics g, Font font, int x, int y, int w, int h, BlockPos panelPos, SourceSelection source, DisplayTheme theme, long renderTick, float partialTick) {
        final AerolineDirectorReadout readout = resolveAerolineDirectorReadout(source);
        final AeroFlightDeckReadout deck = readout.deck();
        final AerolineAnimationState animation = AEROLINE_ANIMATION.computeIfAbsent(panelPos.immutable(), key -> new AerolineAnimationState());
        animation.capture(deck, renderTick);
        final AerolineAnimatedFrame frame = animation.sample(partialTick);
        final int pad = PINNED_HUD_PAD;
        final int gap = PINNED_HUD_GAP;
        final int badgeW = Math.min(PINNED_HUD_BADGE_W, Math.max(78, w / 3));
        final int titleY = y + pad - 1;
        g.drawString(font, truncateGui(font, sentenceCase(readout.state().label), w - pad * 2 - badgeW - gap), x + pad, titleY, applyPinnedHudOpacity(readout.state().color));
        drawBadgeGui(g, font, x + w - pad - badgeW, titleY - 1, badgeW, 14, sentenceCase(readout.modeLabel()), readout.state().color);
        g.drawString(font, truncateGui(font, sentenceCase(readout.orderText()), w - pad * 2), x + pad, titleY + font.lineHeight + 2, applyPinnedHudOpacity(theme.text));
        g.drawString(font, truncateGui(font, buildPinnedAerolineStatusLine(deck), w - pad * 2), x + pad, titleY + (font.lineHeight + 2) * 2, applyPinnedHudOpacity(theme.dim));

        final int footerH = 38;
        final int footerY = y + h - pad - footerH;
        final int bodyY = titleY + (font.lineHeight + 2) * 3 + 5;
        final int bodyH = Math.max(68, footerY - gap - bodyY);
        drawAerolineCompactHorizonGui(g, font, x + pad, bodyY, w - pad * 2, bodyH, readout, frame, theme);

        final int cardW = (w - pad * 2 - gap * 2) / 3;
        final int footerX = x + pad;
        drawPinnedMetricCardGui(g, font, footerX, footerY, cardW, footerH, "Turn", buildPinnedAeroCourseValue(deck), buildPinnedAerolineCourseDetail(deck), theme, aeroPlanColor(deck, theme), 1.0f);
        drawPinnedMetricCardGui(g, font, footerX + cardW + gap, footerY, cardW, footerH, "Altitude", buildPinnedAerolineAltitudeValue(deck), buildPinnedAerolineAltitudeDetail(deck), theme, aeroAltitudeColor(deck, theme), 1.1f);
        drawPinnedMetricCardGui(g, font, footerX + (cardW + gap) * 2, footerY, w - pad * 2 - (cardW + gap) * 2, footerH, "Climb", buildPinnedAerolineClimbValue(deck), buildPinnedAerolineClimbDetail(deck), theme, aeroVerticalVelocityColor(deck, theme), 1.1f);
    }

    private static void drawAerolineCompactHorizonGui(GuiGraphics g, Font font, int x, int y, int w, int h, AerolineDirectorReadout readout, AerolineAnimatedFrame frame, DisplayTheme theme) {
        final AeroFlightDeckReadout deck = readout.deck();
        g.fill(x, y, x + w, y + h, applyPinnedHudOpacity(darken(theme.graphBg, 0.88f)));
        borderGui(g, x, y, w, h, theme.border);
        final int centerX = x + w / 2;
        final int topStripH = Math.max(14, Math.min(20, h / 5));
        final int horizonY = y + topStripH + 4;
        final int horizonH = Math.max(30, h - topStripH - 8);
        g.drawString(font, "Pitch " + aeroPitchValue(deck), x + 4, y + 3, applyPinnedHudOpacity(theme.text));
        final String bankText = "Bank " + aeroBankValue(deck);
        g.drawString(font, bankText, x + w - 4 - font.width(bankText), y + 3, applyPinnedHudOpacity(theme.text));
        final int bankBaseY = y + topStripH - 2;
        final int bankSpan = Math.max(16, w / 2 - 20);
        for (int tick = -90; tick <= 90; tick += 10) {
            final int tickX = centerX + Math.round(bankSpan * tick / 90.0f);
            final int tickH = tick % 30 == 0 ? 5 : 3;
            g.fill(tickX, bankBaseY - tickH, tickX + 1, bankBaseY + 1, applyPinnedHudOpacity(theme.border));
        }
        final int bankMarkerX = centerX + Math.round(bankSpan * Math.max(-1.0f, Math.min(1.0f, frame.bank() / 90.0f)));
        g.fill(bankMarkerX - 1, y + 2, bankMarkerX + 2, y + topStripH - 2, applyPinnedHudOpacity(deck.gimbal != null ? deck.gimbal.state().color : theme.dim));
        g.fill(x + 2, horizonY, x + w - 2, horizonY + horizonH, applyPinnedHudOpacity(darken(theme.graphBg, 0.82f)));
        if (deck.gimbal == null) {
            g.drawCenteredString(font, "Attitude offline", centerX, horizonY + horizonH / 2 - font.lineHeight / 2, applyPinnedHudOpacity(theme.text));
            g.drawCenteredString(font, "Bind a live gimbal feed", centerX, horizonY + horizonH / 2 + font.lineHeight + 2, applyPinnedHudOpacity(theme.dim));
            return;
        }
        final int centerY = horizonY + horizonH / 2;
        final int lineColor = mixColor(theme.accent, theme.text, 0.35f);
        final int directorColor = mixColor(theme.accent, SAFE_GREEN, 0.2f);
        final float bankRad = (float) Math.toRadians(frame.bank());
        final float cos = (float) Math.cos(bankRad);
        final float sin = (float) Math.sin(bankRad);
        final float normalX = -sin;
        final float normalY = cos;
        final float pitchScale = Math.max(0.9f, horizonH / 42.0f);
        for (int step = -40; step <= 40; step += 5) {
            final float offset = (frame.pitch() - step) * pitchScale;
            final float lineCenterX = centerX + normalX * offset;
            final float lineCenterY = centerY + normalY * offset;
            final boolean major = step % 10 == 0;
            final float length = step == 0 ? w * 0.64f : (major ? w * 0.40f : w * 0.22f);
            final int x1 = Math.round(lineCenterX - cos * length * 0.5f);
            final int y1 = Math.round(lineCenterY - sin * length * 0.5f);
            final int x2 = Math.round(lineCenterX + cos * length * 0.5f);
            final int y2 = Math.round(lineCenterY + sin * length * 0.5f);
            drawLineGui(g, x1, y1, x2, y2, step == 0 ? theme.accent : (major ? lineColor : theme.dim));
        }
        final int directorX = centerX + Math.round(frame.planNorm() * Math.max(8, w / 2 - 24));
        final int directorY = centerY - Math.round(frame.trimNorm() * Math.max(6, horizonH / 2 - 18));
        drawLineGui(g, directorX - 12, directorY, directorX + 12, directorY, directorColor);
        drawLineGui(g, directorX, directorY - 10, directorX, directorY + 10, directorColor);
        drawLineGui(g, centerX - 16, centerY, centerX - 4, centerY, theme.text);
        drawLineGui(g, centerX + 4, centerY, centerX + 16, centerY, theme.text);
        drawLineGui(g, centerX, centerY - 5, centerX, centerY + 5, theme.text);
        g.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, applyPinnedHudOpacity(theme.text));
        final String rangeText = truncateGui(font, buildPinnedAerolineRangeBanner(readout), w - 12);
        g.drawCenteredString(font, rangeText, centerX, horizonY + COMPACT_HORIZON_LABEL_PAD, applyPinnedHudOpacity(aeroRangeColor(deck, theme)));
        final String cueLabel = truncateGui(font, buildPinnedAerolineCueLabel(deck), Math.max(32, w / 2 - 12));
        g.drawString(font, cueLabel, x + 4, horizonY + horizonH - font.lineHeight - COMPACT_HORIZON_LABEL_PAD, applyPinnedHudOpacity(theme.dim));
        final String cueValue = truncateGui(font, buildPinnedAerolineCueValue(deck), Math.max(28, w / 2 - 12));
        g.drawString(font, cueValue, x + w - 4 - font.width(cueValue), horizonY + horizonH - font.lineHeight - COMPACT_HORIZON_LABEL_PAD, applyPinnedHudOpacity(aeroPlanColor(deck, theme)));
    }

    private static void drawAerolineStatusStripTexture(NativeImage image, int x, int y, int w, DisplayTheme theme, AeroFlightDeckReadout deck) {
        final String[] labels = new String[] { "ATT", "NAV", "DOCK", "HELM", "RNG", "LIFT" };
        final int[] colors = aerolineStatusColors(deck, theme);
        final int gap = 4;
        final int cellW = Math.max(26, (w - gap * (labels.length - 1)) / labels.length);
        int drawX = x;
        for (int i = 0; i < labels.length; ++i) {
            TextureGraphRenderer.fillRect(image, drawX, y, cellW, 11, darken(theme.graphBg, 0.9f));
            TextureGraphRenderer.drawBorder(image, drawX, y, cellW, 11, 1, theme.border);
            TextureGraphRenderer.fillCircle(image, drawX + 5, y + 5, 2, colors[i]);
            BitmapFont.drawString(image, labels[i], drawX + 11, y + 2, colors[i] == POWER_OFF ? theme.dim : theme.text, 1);
            drawX += cellW + gap;
        }
    }

    private static void drawAerolineStatusStripGui(GuiGraphics g, Font font, int x, int y, int w, DisplayTheme theme, AeroFlightDeckReadout deck) {
        final String[] labels = new String[] { "ATT", "NAV", "DOCK", "HELM", "RNG", "LIFT" };
        final int[] colors = aerolineStatusColors(deck, theme);
        final int gap = 4;
        final int cellW = Math.max(28, (w - gap * (labels.length - 1)) / labels.length);
        int drawX = x;
        for (int i = 0; i < labels.length; ++i) {
            g.fill(drawX, y, drawX + cellW, y + 12, darken(theme.graphBg, 0.9f));
            borderGui(g, drawX, y, cellW, 12, theme.border);
            g.fill(drawX + 4, y + 4, drawX + 9, y + 9, colors[i]);
            g.drawString(font, labels[i], drawX + 12, y + 2, colors[i] == POWER_OFF ? theme.dim : theme.text);
            drawX += cellW + gap;
        }
    }

    private static void drawAerolineFooterTexture(NativeImage image, int x, int y, int w, int h, AerolineDirectorReadout readout, AeroFlightDeckReadout deck, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawCentered(image, truncate(readout.orderText(), Math.max(14, (w - 10) / 6)), x, y + 3, w, readout.state().color, 1);
        BitmapFont.drawCentered(image, truncate(readout.footerText(), Math.max(14, (w - 10) / 6)), x, y + 13, w, theme.dim, 1);
        final int colGap = 8;
        final int colW = Math.max(24, (w - colGap - 12) / 2);
        final String[][] rows = buildAerolineLegendRows(deck);
        int rowY = y + 25;
        for (String[] row : rows) {
            BitmapFont.drawString(image, truncate(row[0], Math.max(8, colW / 6)), x + 6, rowY, theme.text, 1);
            BitmapFont.drawString(image, truncate(row[1], Math.max(8, colW / 6)), x + 6 + colW + colGap, rowY, theme.text, 1);
            rowY += 10;
        }
    }

    private static void drawAerolineFooterGui(GuiGraphics g, Font font, int x, int y, int w, int h, AerolineDirectorReadout readout, AeroFlightDeckReadout deck, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawCenteredString(font, truncateGui(font, readout.orderText(), w - 10), x + w / 2, y + 4, readout.state().color);
        g.drawCenteredString(font, truncateGui(font, readout.footerText(), w - 10), x + w / 2, y + 4 + font.lineHeight + 1, theme.dim);
        final int colGap = 8;
        final int colW = Math.max(40, (w - colGap - 12) / 2);
        final String[][] rows = buildAerolineLegendRows(deck);
        int rowY = y + 6 + (font.lineHeight + 1) * 2;
        for (String[] row : rows) {
            g.drawString(font, truncateGui(font, row[0], colW), x + 6, rowY, theme.text);
            g.drawString(font, truncateGui(font, row[1], colW), x + 6 + colW + colGap, rowY, theme.text);
            rowY += font.lineHeight + 1;
        }
    }

    private static int[] aerolineStatusColors(AeroFlightDeckReadout deck, DisplayTheme theme) {
        return new int[] {
                deck.gimbal != null ? deck.gimbal.state().color : POWER_OFF,
                deck.navigation != null && deck.navigation.hasTarget() ? deck.navigation.state().color : POWER_OFF,
                deck.docking != null ? deck.docking.state().color : POWER_OFF,
                deck.steering != null ? deck.steering.state().color : POWER_OFF,
                aeroHasTargetLock(deck) ? aeroRangeColor(deck, theme) : POWER_OFF,
                (deck.contraption != null && deck.contraption.hasLiveForceData()) ? deck.contraption.state().color : (deck.liftEmitter != null ? deck.liftEmitter.state().color : POWER_OFF)
        };
    }

    private static void drawAerolineDirectorWindowTexture(NativeImage image, int x, int y, int w, int h, AerolineDirectorReadout readout, AerolineAnimatedFrame frame, DisplayTheme theme, long renderTick) {
        final AeroFlightDeckReadout deck = readout.deck();
        final int bracket = Math.max(10, Math.min(18, w / 10));
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.84f));
        TextureGraphRenderer.drawLine(image, x, y + bracket, x, y, theme.border);
        TextureGraphRenderer.drawLine(image, x, y, x + bracket, y, theme.border);
        TextureGraphRenderer.drawLine(image, x + w - bracket, y, x + w - 1, y, theme.border);
        TextureGraphRenderer.drawLine(image, x + w - 1, y, x + w - 1, y + bracket, theme.border);
        TextureGraphRenderer.drawLine(image, x, y + h - bracket, x, y + h - 1, theme.border);
        TextureGraphRenderer.drawLine(image, x, y + h - 1, x + bracket, y + h - 1, theme.border);
        TextureGraphRenderer.drawLine(image, x + w - bracket, y + h - 1, x + w - 1, y + h - 1, theme.border);
        TextureGraphRenderer.drawLine(image, x + w - 1, y + h - bracket, x + w - 1, y + h - 1, theme.border);
        final int topStripH = Math.max(14, h / 8);
        final int instrumentH = Math.max(22, h / 4);
        final int horizonY = y + topStripH + 4;
        final int horizonH = Math.max(26, h - topStripH - instrumentH - 10);
        final int instrumentY = y + h - instrumentH - 4;
        final int centerX = x + w / 2;
        final int centerY = horizonY + horizonH / 2;
        BitmapFont.drawString(image, "P " + aeroPitchValue(deck), x + 4, y + 3, theme.text, 1);
        BitmapFont.drawCentered(image, truncate(readout.modeLabel(), Math.max(10, w / 6)), x, y + 3, w, readout.state().color, 1);
        BitmapFont.drawRight(image, "B " + aeroBankValue(deck), x + w - 4, y + 3, theme.text, 1);
        final int bankBaseY = y + topStripH - 2;
        final int bankSpan = Math.max(14, w / 2 - 18);
        for (int tick = -90; tick <= 90; tick += 10) {
            final int tickX = centerX + Math.round(bankSpan * tick / 90.0f);
            final int tickH = tick % 30 == 0 ? 4 : 2;
            TextureGraphRenderer.fillRect(image, tickX, bankBaseY - tickH, 1, tickH + 1, theme.border);
        }
        final int bankMarkerX = centerX + Math.round(bankSpan * Math.max(-1.0f, Math.min(1.0f, frame.bank() / 90.0f)));
        TextureGraphRenderer.fillRect(image, bankMarkerX - 1, y + 2, 3, Math.max(4, topStripH - 3), deck.gimbal != null ? deck.gimbal.state().color : theme.dim);
        TextureGraphRenderer.fillRect(image, x + 2, horizonY, w - 4, horizonH, darken(theme.graphBg, 0.8f));
        drawAerolineHorizonTexture(image, x + 2, horizonY, w - 4, horizonH, frame, deck, readout, theme);
        final int gap = 4;
        final int sideW = Math.max(38, (w - gap * 2) / 4);
        final int centerW = Math.max(42, w - sideW * 2 - gap * 2);
        drawAerolineTargetScopeTexture(image, x + 2, instrumentY, sideW, instrumentH, deck, theme, renderTick);
        drawAerolineVelocityScopeTexture(image, x + 2 + sideW + gap, instrumentY, centerW, instrumentH, deck, theme);
        drawAerolineAltitudeScopeTexture(image, x + w - sideW - 2, instrumentY, sideW, instrumentH, deck, theme);
    }

    private static void drawAerolineDirectorWindowGui(GuiGraphics g, Font font, int x, int y, int w, int h, AerolineDirectorReadout readout, AerolineAnimatedFrame frame, DisplayTheme theme, long renderTick) {
        final AeroFlightDeckReadout deck = readout.deck();
        final int bracket = Math.max(10, Math.min(18, w / 10));
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.84f));
        drawLineGui(g, x, y + bracket, x, y, theme.border);
        drawLineGui(g, x, y, x + bracket, y, theme.border);
        drawLineGui(g, x + w - bracket, y, x + w - 1, y, theme.border);
        drawLineGui(g, x + w - 1, y, x + w - 1, y + bracket, theme.border);
        drawLineGui(g, x, y + h - bracket, x, y + h - 1, theme.border);
        drawLineGui(g, x, y + h - 1, x + bracket, y + h - 1, theme.border);
        drawLineGui(g, x + w - bracket, y + h - 1, x + w - 1, y + h - 1, theme.border);
        drawLineGui(g, x + w - 1, y + h - bracket, x + w - 1, y + h - 1, theme.border);
        final int topStripH = Math.max(16, h / 8);
        final int instrumentH = Math.max(44, h / 4);
        final int horizonY = y + topStripH + 4;
        final int horizonH = Math.max(30, h - topStripH - instrumentH - 10);
        final int instrumentY = y + h - instrumentH - 4;
        final int centerX = x + w / 2;
        g.drawString(font, "P " + aeroPitchValue(deck), x + 4, y + 3, theme.text);
        g.drawCenteredString(font, readout.modeLabel(), centerX, y + 3, readout.state().color);
        final String bankText = "B " + aeroBankValue(deck);
        g.drawString(font, bankText, x + w - 4 - font.width(bankText), y + 3, theme.text);
        final int bankBaseY = y + topStripH - 2;
        final int bankSpan = Math.max(14, w / 2 - 18);
        for (int tick = -90; tick <= 90; tick += 10) {
            final int tickX = centerX + Math.round(bankSpan * tick / 90.0f);
            final int tickH = tick % 30 == 0 ? 5 : 3;
            g.fill(tickX, bankBaseY - tickH, tickX + 1, bankBaseY + 1, theme.border);
        }
        final int bankMarkerX = centerX + Math.round(bankSpan * Math.max(-1.0f, Math.min(1.0f, frame.bank() / 90.0f)));
        g.fill(bankMarkerX - 1, y + 2, bankMarkerX + 2, y + topStripH - 2, deck.gimbal != null ? deck.gimbal.state().color : theme.dim);
        g.fill(x + 2, horizonY, x + w - 2, horizonY + horizonH, darken(theme.graphBg, 0.8f));
        drawAerolineHorizonGui(g, font, x + 2, horizonY, w - 4, horizonH, frame, deck, readout, theme);
        final int gap = 6;
        final int sideW = Math.max(66, (w - gap * 2) / 4);
        final int centerW = Math.max(88, w - sideW * 2 - gap * 2);
        drawAerolineTargetScopeGui(g, font, x + 2, instrumentY, sideW, instrumentH, deck, theme, renderTick);
        drawAerolineVelocityScopeGui(g, font, x + 2 + sideW + gap, instrumentY, centerW, instrumentH, deck, theme);
        drawAerolineAltitudeScopeGui(g, font, x + w - sideW - 2, instrumentY, sideW, instrumentH, deck, theme);
    }

    private static void drawAerolineHorizonTexture(NativeImage image, int x, int y, int w, int h, AerolineAnimatedFrame frame, AeroFlightDeckReadout deck, AerolineDirectorReadout readout, DisplayTheme theme) {
        final int centerX = x + w / 2;
        final int centerY = y + h / 2;
        final int lineColor = mixColor(theme.accent, theme.text, 0.35f);
        final int directorColor = mixColor(theme.accent, SAFE_GREEN, 0.2f);
        final float bankRad = (float) Math.toRadians(frame.bank());
        final float cos = (float) Math.cos(bankRad);
        final float sin = (float) Math.sin(bankRad);
        final float normalX = -sin;
        final float normalY = cos;
        final float pitchScale = Math.max(0.9f, h / 42.0f);
        if (deck.gimbal == null) {
            BitmapFont.drawCentered(image, "ATTITUDE OFFLINE", x, centerY - 6, w, theme.text, 1);
            BitmapFont.drawCentered(image, "Bind a live gimbal feed to arm the director", x, centerY + 6, w, theme.dim, 1);
            return;
        }
        for (int step = -40; step <= 40; step += 5) {
            final float offset = (frame.pitch() - step) * pitchScale;
            final float lineCenterX = centerX + normalX * offset;
            final float lineCenterY = centerY + normalY * offset;
            final boolean major = step % 10 == 0;
            final float length = step == 0 ? w * 0.74f : (major ? w * 0.46f : w * 0.26f);
            final int x1 = Math.round(lineCenterX - cos * length * 0.5f);
            final int y1 = Math.round(lineCenterY - sin * length * 0.5f);
            final int x2 = Math.round(lineCenterX + cos * length * 0.5f);
            final int y2 = Math.round(lineCenterY + sin * length * 0.5f);
            TextureGraphRenderer.drawLine(image, x1, y1, x2, y2, step == 0 ? theme.accent : (major ? lineColor : theme.dim));
        }
        final int directorX = centerX + Math.round(frame.planNorm() * Math.max(8, w / 2 - 22));
        final int directorY = centerY - Math.round(frame.trimNorm() * Math.max(6, h / 2 - 18));
        TextureGraphRenderer.drawLine(image, directorX - 12, directorY, directorX + 12, directorY, directorColor);
        TextureGraphRenderer.drawLine(image, directorX, directorY - 10, directorX, directorY + 10, directorColor);
        TextureGraphRenderer.drawLine(image, centerX - 16, centerY, centerX - 4, centerY, theme.text);
        TextureGraphRenderer.drawLine(image, centerX + 4, centerY, centerX + 16, centerY, theme.text);
        TextureGraphRenderer.drawLine(image, centerX, centerY - 5, centerX, centerY + 5, theme.text);
        TextureGraphRenderer.fillRect(image, centerX - 1, centerY - 1, 3, 3, theme.text);
        BitmapFont.drawCentered(image, truncate(readout.rangeText(), Math.max(10, w / 6)), x, y + 4, w, aeroRangeColor(deck, theme), 1);
        BitmapFont.drawString(image, truncate(aeroPlanSource(deck), Math.max(7, w / 12)), x + 4, y + h - 18, theme.dim, 1);
        BitmapFont.drawRight(image, aeroPlanAngleValue(deck), x + w - 4, y + h - 18, aeroPlanColor(deck, theme), 1);
    }

    private static void drawAerolineHorizonGui(GuiGraphics g, Font font, int x, int y, int w, int h, AerolineAnimatedFrame frame, AeroFlightDeckReadout deck, AerolineDirectorReadout readout, DisplayTheme theme) {
        final int centerX = x + w / 2;
        final int centerY = y + h / 2;
        final int lineColor = mixColor(theme.accent, theme.text, 0.35f);
        final int directorColor = mixColor(theme.accent, SAFE_GREEN, 0.2f);
        final float bankRad = (float) Math.toRadians(frame.bank());
        final float cos = (float) Math.cos(bankRad);
        final float sin = (float) Math.sin(bankRad);
        final float normalX = -sin;
        final float normalY = cos;
        final float pitchScale = Math.max(0.9f, h / 42.0f);
        if (deck.gimbal == null) {
            g.drawCenteredString(font, "ATTITUDE OFFLINE", centerX, centerY - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "Bind a live gimbal feed to arm the director", centerX, centerY + font.lineHeight + 2, theme.dim);
            return;
        }
        for (int step = -40; step <= 40; step += 5) {
            final float offset = (frame.pitch() - step) * pitchScale;
            final float lineCenterX = centerX + normalX * offset;
            final float lineCenterY = centerY + normalY * offset;
            final boolean major = step % 10 == 0;
            final float length = step == 0 ? w * 0.74f : (major ? w * 0.46f : w * 0.26f);
            final int x1 = Math.round(lineCenterX - cos * length * 0.5f);
            final int y1 = Math.round(lineCenterY - sin * length * 0.5f);
            final int x2 = Math.round(lineCenterX + cos * length * 0.5f);
            final int y2 = Math.round(lineCenterY + sin * length * 0.5f);
            drawLineGui(g, x1, y1, x2, y2, step == 0 ? theme.accent : (major ? lineColor : theme.dim));
        }
        final int directorX = centerX + Math.round(frame.planNorm() * Math.max(8, w / 2 - 22));
        final int directorY = centerY - Math.round(frame.trimNorm() * Math.max(6, h / 2 - 18));
        drawLineGui(g, directorX - 12, directorY, directorX + 12, directorY, directorColor);
        drawLineGui(g, directorX, directorY - 10, directorX, directorY + 10, directorColor);
        drawLineGui(g, centerX - 16, centerY, centerX - 4, centerY, theme.text);
        drawLineGui(g, centerX + 4, centerY, centerX + 16, centerY, theme.text);
        drawLineGui(g, centerX, centerY - 5, centerX, centerY + 5, theme.text);
        g.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, theme.text);
        g.drawCenteredString(font, truncateGui(font, readout.rangeText(), w - 20), centerX, y + 6, aeroRangeColor(deck, theme));
        g.drawString(font, aeroPlanSource(deck), x + 4, y + h - font.lineHeight - 4, theme.dim);
        final String planText = aeroPlanAngleValue(deck);
        g.drawString(font, planText, x + w - 4 - font.width(planText), y + h - font.lineHeight - 4, aeroPlanColor(deck, theme));
    }

    private static void drawAerolineTargetScopeTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout deck, DisplayTheme theme, long renderTick) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "TARGET", x + 4, y + 3, theme.dim, 1);
        final int cx = x + w / 2;
        final int cy = y + h / 2 + 3;
        final int radius = Math.max(10, Math.min(w, h - 6) / 2 - 5);
        drawScopeCircleTexture(image, cx, cy, radius, theme.grid);
        TextureGraphRenderer.drawLine(image, cx - radius, cy, cx + radius, cy, darken(theme.grid, 0.96f));
        TextureGraphRenderer.drawLine(image, cx, cy - radius, cx, cy + radius, darken(theme.grid, 0.96f));
        final float sweep = (renderTick % 80L) / 80.0f;
        final double sweepAngle = sweep * Math.PI * 2.0;
        TextureGraphRenderer.drawLine(image, cx, cy, cx + (int) Math.round(Math.cos(sweepAngle) * radius), cy + (int) Math.round(Math.sin(sweepAngle) * radius), darken(theme.accent, 0.8f));
        if (aeroHasTargetLock(deck)) {
            final double angle = Math.toRadians(aeroPlanAngle(deck) - 90.0f);
            final float distNorm = Math.max(0.18f, Math.min(1.0f, aeroTargetDistance(deck) / 64.0f));
            final int blipX = cx + Math.round((float) Math.cos(angle) * radius * distNorm * 0.82f);
            final int blipY = cy + Math.round((float) Math.sin(angle) * radius * distNorm * 0.82f);
            TextureGraphRenderer.fillCircle(image, blipX, blipY, 2, aeroPlanColor(deck, theme));
            drawCenteredBarTexture(image, x + 8, y + h - 18, w - 16, 3, aeroPlanAngle(deck), 180.0f, theme, aeroPlanColor(deck, theme));
            BitmapFont.drawCentered(image, truncate(aeroPlanSource(deck) + " " + aeroPlanAngleValue(deck), Math.max(7, w / 6)), x, y + h - 10, w, aeroPlanColor(deck, theme), 1);
        } else {
            BitmapFont.drawCentered(image, "SCAN", x, cy - 4, w, theme.text, 1);
            BitmapFont.drawCentered(image, "NO LOCK", x, y + h - 10, w, theme.dim, 1);
        }
    }

    private static void drawAerolineTargetScopeGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout deck, DisplayTheme theme, long renderTick) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "TARGET", x + 4, y + 3, theme.dim);
        final int cx = x + w / 2;
        final int cy = y + h / 2 + 4;
        final int radius = Math.max(14, Math.min(w, h - 8) / 2 - 6);
        drawScopeCircleGui(g, cx, cy, radius, theme.grid);
        drawLineGui(g, cx - radius, cy, cx + radius, cy, darken(theme.grid, 0.96f));
        drawLineGui(g, cx, cy - radius, cx, cy + radius, darken(theme.grid, 0.96f));
        final float sweep = (renderTick % 80L) / 80.0f;
        final double sweepAngle = sweep * Math.PI * 2.0;
        drawLineGui(g, cx, cy, cx + (int) Math.round(Math.cos(sweepAngle) * radius), cy + (int) Math.round(Math.sin(sweepAngle) * radius), darken(theme.accent, 0.8f));
        if (aeroHasTargetLock(deck)) {
            final double angle = Math.toRadians(aeroPlanAngle(deck) - 90.0f);
            final float distNorm = Math.max(0.18f, Math.min(1.0f, aeroTargetDistance(deck) / 64.0f));
            final int blipX = cx + Math.round((float) Math.cos(angle) * radius * distNorm * 0.82f);
            final int blipY = cy + Math.round((float) Math.sin(angle) * radius * distNorm * 0.82f);
            g.fill(blipX - 2, blipY - 2, blipX + 3, blipY + 3, aeroPlanColor(deck, theme));
            drawCenteredBarGui(g, x + 8, y + h - 21, w - 16, 4, aeroPlanAngle(deck), 180.0f, theme, aeroPlanColor(deck, theme));
            g.drawCenteredString(font, truncateGui(font, aeroPlanSource(deck) + " " + aeroPlanAngleValue(deck), w - 8), cx, y + h - font.lineHeight - 3, aeroPlanColor(deck, theme));
        } else {
            g.drawCenteredString(font, "SCAN", cx, cy - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "NO LOCK", cx, y + h - font.lineHeight - 3, theme.dim);
        }
    }

    private static void drawAerolineVelocityScopeTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout deck, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawCentered(image, "MOTION", x, y + 3, w, theme.dim, 1);
        final int barW = Math.max(18, w - 10);
        final int barH = Math.max(5, (h - 20) / 2);
        final int scale = Math.max(5, Math.round(aeroVelocityScale(deck)));
        final int topY = y + 14;
        drawCenteredBarTexture(image, x + 5, topY, barW, barH, aeroVerticalVelocity(deck), scale, theme, aeroVerticalVelocityColor(deck, theme));
        BitmapFont.drawString(image, "V/S", x + 6, topY + 1, theme.text, 1);
        BitmapFont.drawRight(image, aeroVerticalVelocityValue(deck), x + w - 6, topY + 1, aeroVerticalVelocityColor(deck, theme), 1);
        final int botY = topY + barH + 4;
        drawCenteredBarTexture(image, x + 5, botY, barW, barH, aeroHeightRate(deck), scale, theme, aeroHeightRateColor(deck, theme));
        BitmapFont.drawString(image, "H RATE", x + 6, botY + 1, theme.text, 1);
        BitmapFont.drawRight(image, aeroHeightRateValue(deck), x + w - 6, botY + 1, aeroHeightRateColor(deck, theme), 1);
    }

    private static void drawAerolineVelocityScopeGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout deck, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawCenteredString(font, "MOTION", x + w / 2, y + 3, theme.dim);
        final int barW = Math.max(32, w - 12);
        final int barH = Math.max(10, (h - 24) / 2);
        final float scale = aeroVelocityScale(deck);
        final int topY = y + 16;
        drawCenteredBarGui(g, x + 6, topY, barW, barH, aeroVerticalVelocity(deck), scale, theme, aeroVerticalVelocityColor(deck, theme));
        g.drawString(font, "V/S", x + 8, topY + 2, theme.text);
        final String vertical = aeroVerticalVelocityValue(deck);
        g.drawString(font, vertical, x + w - 8 - font.width(vertical), topY + 2, aeroVerticalVelocityColor(deck, theme));
        final int botY = topY + barH + 5;
        drawCenteredBarGui(g, x + 6, botY, barW, barH, aeroHeightRate(deck), scale, theme, aeroHeightRateColor(deck, theme));
        g.drawString(font, "H RATE", x + 8, botY + 2, theme.text);
        final String horizontal = aeroHeightRateValue(deck);
        g.drawString(font, horizontal, x + w - 8 - font.width(horizontal), botY + 2, aeroHeightRateColor(deck, theme));
    }

    private static void drawAerolineAltitudeScopeTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout deck, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, "ALT/RANGE", x + 4, y + 3, theme.dim, 1);
        final int cx = x + w / 2;
        final int cy = y + h - 6;
        final int radius = Math.max(8, Math.min(w / 2 - 5, h - 16));
        drawScopeArcTexture(image, cx, cy, radius, 210.0f, 330.0f, theme.grid);
        drawScopeArcTexture(image, cx, cy, radius, 210.0f, 210.0f + 120.0f * aeroAltitudeNorm(deck), aeroAltitudeColor(deck, theme));
        final double markerAngle = Math.toRadians(210.0f + 120.0f * aeroAltitudeNorm(deck));
        TextureGraphRenderer.fillCircle(image, cx + (int) Math.round(Math.cos(markerAngle) * radius), cy + (int) Math.round(Math.sin(markerAngle) * radius), 2, aeroAltitudeColor(deck, theme));
        BitmapFont.drawCentered(image, truncate(aeroAltitudeValue(deck), Math.max(6, w / 6)), x, y + h / 2 - 4, w, aeroAltitudeColor(deck, theme), 1);
        BitmapFont.drawCentered(image, truncate(aeroRangeLabel(deck) + " " + aeroRangeValue(deck), Math.max(8, w / 6)), x, y + h - 11, w, aeroRangeColor(deck, theme), 1);
    }

    private static void drawAerolineAltitudeScopeGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout deck, DisplayTheme theme) {
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.9f));
        borderGui(g, x, y, w, h, theme.border);
        g.drawString(font, "ALT/RANGE", x + 4, y + 3, theme.dim);
        final int cx = x + w / 2;
        final int cy = y + h - 8;
        final int radius = Math.max(12, Math.min(w / 2 - 6, h - 18));
        drawScopeArcGui(g, cx, cy, radius, 210.0f, 330.0f, theme.grid);
        drawScopeArcGui(g, cx, cy, radius, 210.0f, 210.0f + 120.0f * aeroAltitudeNorm(deck), aeroAltitudeColor(deck, theme));
        final double markerAngle = Math.toRadians(210.0f + 120.0f * aeroAltitudeNorm(deck));
        final int markerX = cx + (int) Math.round(Math.cos(markerAngle) * radius);
        final int markerY = cy + (int) Math.round(Math.sin(markerAngle) * radius);
        g.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, aeroAltitudeColor(deck, theme));
        g.drawCenteredString(font, aeroAltitudeValue(deck), cx, y + h / 2 - font.lineHeight / 2, aeroAltitudeColor(deck, theme));
        g.drawCenteredString(font, truncateGui(font, aeroRangeLabel(deck) + " " + aeroRangeValue(deck), w - 8), cx, y + h - font.lineHeight - 3, aeroRangeColor(deck, theme));
    }

    private static void drawScopeCircleTexture(NativeImage image, int centerX, int centerY, int radius, int color) {
        drawScopeArcTexture(image, centerX, centerY, radius, 0.0f, 360.0f, color);
    }

    private static void drawScopeCircleGui(GuiGraphics g, int centerX, int centerY, int radius, int color) {
        drawScopeArcGui(g, centerX, centerY, radius, 0.0f, 360.0f, color);
    }

    private static void drawScopeArcTexture(NativeImage image, int centerX, int centerY, int radius, float startDegrees, float endDegrees, int color) {
        int prevX = Integer.MIN_VALUE;
        int prevY = Integer.MIN_VALUE;
        final float min = Math.min(startDegrees, endDegrees);
        final float max = Math.max(startDegrees, endDegrees);
        for (float angle = min; angle <= max + 0.1f; angle += 6.0f) {
            final double radians = Math.toRadians(angle);
            final int px = centerX + (int) Math.round(Math.cos(radians) * radius);
            final int py = centerY + (int) Math.round(Math.sin(radians) * radius);
            if (prevX != Integer.MIN_VALUE) {
                TextureGraphRenderer.drawLine(image, prevX, prevY, px, py, color);
            }
            prevX = px;
            prevY = py;
        }
    }

    private static void drawScopeArcGui(GuiGraphics g, int centerX, int centerY, int radius, float startDegrees, float endDegrees, int color) {
        int prevX = Integer.MIN_VALUE;
        int prevY = Integer.MIN_VALUE;
        final float min = Math.min(startDegrees, endDegrees);
        final float max = Math.max(startDegrees, endDegrees);
        for (float angle = min; angle <= max + 0.1f; angle += 6.0f) {
            final double radians = Math.toRadians(angle);
            final int px = centerX + (int) Math.round(Math.cos(radians) * radius);
            final int py = centerY + (int) Math.round(Math.sin(radians) * radius);
            if (prevX != Integer.MIN_VALUE) {
                drawLineGui(g, prevX, prevY, px, py, color);
            }
            prevX = px;
            prevY = py;
        }
    }

    private static void drawContraptionSolverTexture(NativeImage image, int x, int y, int w, int h, ContraptionInfoReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "FORCE SOLVER");
        final int innerX = x + 4;
        final int innerY = contentY + 1;
        final int innerW = w - 8;
        final int innerH = Math.max(24, y + h - innerY - 4);
        final int gap = 6;
        final int leftW = Math.max(58, (int) (innerW * 0.58f));
        final int rightW = innerW - leftW - gap;
        final int vectorH = Math.max(9, (innerH - gap) / 2);
        drawCenteredBarTexture(image, innerX, innerY, leftW, vectorH, readout.verticalMargin(), readout.verticalScale(), theme, readout.verticalMargin() >= 0.0f ? SAFE_GREEN : DANGER_RED);
        BitmapFont.drawString(image, "VERT MARGIN", innerX + 4, innerY + 2, theme.dim, 1);
        BitmapFont.drawRight(image, formatSignedForce(readout.verticalMargin()), innerX + leftW - 4, innerY + 2, readout.verticalMargin() >= 0.0f ? SAFE_GREEN : DANGER_RED, 1);
        BitmapFont.drawCentered(image, "SUPPORT " + formatForce(readout.upwardForce()) + " / WEIGHT " + formatForce(readout.gravity()), innerX, innerY + vectorH - 9, leftW, theme.text, 1);
        final int vector2Y = innerY + vectorH + gap;
        drawCenteredBarTexture(image, innerX, vector2Y, leftW, vectorH, readout.thrustMargin(), readout.forwardScale(), theme, readout.thrustMargin() >= 0.0f ? SAFE_GREEN : ALERT_AMBER);
        BitmapFont.drawString(image, "FWD MARGIN", innerX + 4, vector2Y + 2, theme.dim, 1);
        BitmapFont.drawRight(image, formatSignedForce(readout.thrustMargin()), innerX + leftW - 4, vector2Y + 2, readout.thrustMargin() >= 0.0f ? SAFE_GREEN : ALERT_AMBER, 1);
        BitmapFont.drawCentered(image, "THRUST " + formatForce(readout.thrust()) + " / BRAKE " + formatForce(readout.reverseForce()), innerX, vector2Y + vectorH - 9, leftW, theme.text, 1);
        final int rightX = innerX + leftW + gap;
        final int cardH = Math.max(12, (innerH - gap * 2) / 3);
        drawMetricCardTexture(image, rightX, innerY, rightW, cardH, "MASS", formatMass(readout.totalMass()), theme, theme.text);
        drawMetricCardTexture(image, rightX, innerY + cardH + gap, rightW, cardH, "AXIS", readout.forwardAxisTag(), theme, readout.forwardAxisColor(theme));
        drawMetricCardTexture(image, rightX, innerY + (cardH + gap) * 2, rightW, cardH, "STATE", readout.state().shortLabel, theme, readout.state().color);
    }

    private static void drawContraptionSolverGui(GuiGraphics g, Font font, int x, int y, int w, int h, ContraptionInfoReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "FORCE SOLVER");
        final int innerX = x + 4;
        final int innerY = contentY + 1;
        final int innerW = w - 8;
        final int innerH = Math.max(34, y + h - innerY - 4);
        final int gap = 8;
        final int leftW = Math.max(88, (int) (innerW * 0.58f));
        final int rightW = innerW - leftW - gap;
        final int vectorH = Math.max(16, (innerH - gap) / 2);
        drawCenteredBarGui(g, innerX, innerY, leftW, vectorH, readout.verticalMargin(), readout.verticalScale(), theme, readout.verticalMargin() >= 0.0f ? SAFE_GREEN : DANGER_RED);
        g.drawString(font, "VERT MARGIN", innerX + 4, innerY + 3, theme.dim);
        final String verticalText = formatSignedForce(readout.verticalMargin());
        g.drawString(font, verticalText, innerX + leftW - 4 - font.width(verticalText), innerY + 3, readout.verticalMargin() >= 0.0f ? SAFE_GREEN : DANGER_RED);
        g.drawCenteredString(font, truncateGui(font, "SUPPORT " + formatForce(readout.upwardForce()) + " / WEIGHT " + formatForce(readout.gravity()), leftW - 8), innerX + leftW / 2, innerY + vectorH - font.lineHeight - 1, theme.text);
        final int vector2Y = innerY + vectorH + gap;
        drawCenteredBarGui(g, innerX, vector2Y, leftW, vectorH, readout.thrustMargin(), readout.forwardScale(), theme, readout.thrustMargin() >= 0.0f ? SAFE_GREEN : ALERT_AMBER);
        g.drawString(font, "FWD MARGIN", innerX + 4, vector2Y + 3, theme.dim);
        final String forwardText = formatSignedForce(readout.thrustMargin());
        g.drawString(font, forwardText, innerX + leftW - 4 - font.width(forwardText), vector2Y + 3, readout.thrustMargin() >= 0.0f ? SAFE_GREEN : ALERT_AMBER);
        g.drawCenteredString(font, truncateGui(font, "THRUST " + formatForce(readout.thrust()) + " / BRAKE " + formatForce(readout.reverseForce()), leftW - 8), innerX + leftW / 2, vector2Y + vectorH - font.lineHeight - 1, theme.text);
        final int rightX = innerX + leftW + gap;
        final int cardH = Math.max(18, (innerH - gap * 2) / 3);
        drawMetricCardGui(g, font, rightX, innerY, rightW, cardH, "MASS", formatMass(readout.totalMass()), theme, theme.text);
        drawMetricCardGui(g, font, rightX, innerY + cardH + gap, rightW, cardH, "AXIS", readout.forwardAxisTag(), theme, readout.forwardAxisColor(theme));
        drawMetricCardGui(g, font, rightX, innerY + (cardH + gap) * 2, rightW, cardH, "STATE", readout.state().shortLabel, theme, readout.state().color);
    }

    private static void drawAeroOrientationTexture(NativeImage image, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "TRIM STATION");
        final int innerX = x + 4;
        final int innerY = contentY + 1;
        final int innerW = w - 8;
        final int innerH = Math.max(22, y + h - innerY - 4);
        final int gap = 4;
        final int bankH = Math.max(9, innerH / 5);
        final int cueH = Math.max(10, innerH / 4);
        final int windowY = innerY + bankH + gap;
        final int windowH = Math.max(10, innerH - bankH - cueH - gap * 2);
        final int cueY = windowY + windowH + gap;
        final float pitch = (readout.gimbal != null) ? readout.gimbal.flightDeckPitch() : 0.0f;
        final float bank = (readout.gimbal != null) ? readout.gimbal.flightDeckBank() : 0.0f;
        final float bankRad = (float) Math.toRadians(bank);
        final float cos = (float) Math.cos(bankRad);
        final float sin = (float) Math.sin(bankRad);
        final float normalX = -sin;
        final float normalY = cos;
        final float pitchScale = Math.max(0.65f, windowH / 42.0f);
        final int tiltColor = (readout.gimbal != null) ? readout.gimbal.state().color : theme.dim;
        final int planColor = aeroPlanColor(readout, theme);
        final int centerX = innerX + innerW / 2;
        final int centerY = windowY + windowH / 2;

        TextureGraphRenderer.fillRect(image, innerX, innerY, innerW, bankH, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, innerX, innerY, innerW, bankH, 1, theme.border);
        BitmapFont.drawString(image, "P " + aeroPitchValue(readout), innerX + 3, innerY + 2, theme.text, 1);
        BitmapFont.drawRight(image, "B " + aeroBankValue(readout), innerX + innerW - 3, innerY + 2, theme.text, 1);
        final int bankBaseY = innerY + bankH - 3;
        final int bankSpan = Math.max(8, innerW / 2 - 12);
        for (int tick = -90; tick <= 90; tick += 5) {
            final int tickX = centerX + Math.round(bankSpan * tick / 90.0f);
            final boolean major = tick % 30 == 0;
            final boolean mid = tick % 10 == 0;
            final int tickH = major ? 4 : (mid ? 3 : 2);
            TextureGraphRenderer.drawLine(image, tickX, bankBaseY - tickH, tickX, bankBaseY, theme.border);
        }
        final int bankMarkerX = centerX + Math.round(bankSpan * Math.max(-1.0f, Math.min(1.0f, bank / 90.0f)));
        TextureGraphRenderer.fillRect(image, bankMarkerX - 1, innerY + 2, 3, Math.max(3, bankH - 5), tiltColor);

        TextureGraphRenderer.fillRect(image, innerX, windowY, innerW, windowH, darken(theme.graphBg, 0.86f));
        TextureGraphRenderer.drawBorder(image, innerX, windowY, innerW, windowH, 1, theme.border);
        if (readout.gimbal == null) {
            BitmapFont.drawCentered(image, "NO GIMBAL", innerX, centerY - 8, innerW, theme.text, 1);
            BitmapFont.drawCentered(image, "Attitude offline", innerX, centerY + 4, innerW, theme.dim, 1);
        } else {
            for (int step = -40; step <= 40; step += 5) {
                final float offset = (pitch - step) * pitchScale;
                final float lineCenterX = centerX + normalX * offset;
                final float lineCenterY = centerY + normalY * offset;
                final boolean major = step % 10 == 0;
                final float length = (step == 0) ? innerW * 0.72f : (major ? innerW * 0.44f : innerW * 0.26f);
                final int x1 = Math.round(lineCenterX - cos * length * 0.5f);
                final int y1 = Math.round(lineCenterY - sin * length * 0.5f);
                final int x2 = Math.round(lineCenterX + cos * length * 0.5f);
                final int y2 = Math.round(lineCenterY + sin * length * 0.5f);
                TextureGraphRenderer.drawLine(image, x1, y1, x2, y2, step == 0 ? theme.accent : (major ? theme.text : theme.dim));
            }
            final float trimNorm = aeroTrimCue(readout);
            final int trimY = centerY - Math.round(trimNorm * Math.max(2, windowH / 2 - 4));
            TextureGraphRenderer.drawLine(image, centerX - 8, trimY, centerX + 8, trimY, aeroVerticalColor(readout, theme));
            final float planNorm = Math.max(-1.0f, Math.min(1.0f, aeroPlanAngle(readout) / 90.0f));
            final int cueX = centerX + Math.round(planNorm * Math.max(4, innerW / 2 - 8));
            TextureGraphRenderer.drawLine(image, cueX, centerY + 2, cueX, windowY + windowH - 3, planColor);
            TextureGraphRenderer.drawLine(image, centerX - 8, centerY, centerX - 2, centerY, theme.text);
            TextureGraphRenderer.drawLine(image, centerX + 2, centerY, centerX + 8, centerY, theme.text);
            TextureGraphRenderer.drawLine(image, centerX, centerY - 1, centerX, centerY + 2, theme.text);
            TextureGraphRenderer.fillRect(image, centerX - 1, centerY - 1, 3, 3, theme.text);
        }

        TextureGraphRenderer.fillRect(image, innerX, cueY, innerW, cueH, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, innerX, cueY, innerW, cueH, 1, theme.border);
        BitmapFont.drawString(image, aeroPlanSource(readout), innerX + 3, cueY + 2, theme.dim, 1);
        BitmapFont.drawRight(image, aeroPlanAngleValue(readout), innerX + innerW - 3, cueY + 2, planColor, 1);
        final int barY = cueY + 9;
        final int barH = Math.max(2, cueY + cueH - barY - 2);
        drawCenteredBarTexture(image, innerX + 3, barY, innerW - 6, barH, aeroPlanAngle(readout), 180.0f, theme, planColor);
    }

    private static void drawAeroOrientationGui(GuiGraphics g, Font font, int x, int y, int w, int h, AeroFlightDeckReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "TRIM STATION");
        final int innerX = x + 4;
        final int innerY = contentY + 1;
        final int innerW = w - 8;
        final int innerH = Math.max(28, y + h - innerY - 4);
        final int gap = 6;
        final int bankH = Math.max(14, innerH / 5);
        final int cueH = Math.max(14, innerH / 4);
        final int windowY = innerY + bankH + gap;
        final int windowH = Math.max(14, y + h - windowY - cueH - gap - 4);
        final int cueY = windowY + windowH + gap;
        final float pitch = (readout.gimbal != null) ? readout.gimbal.flightDeckPitch() : 0.0f;
        final float bank = (readout.gimbal != null) ? readout.gimbal.flightDeckBank() : 0.0f;
        final float bankRad = (float) Math.toRadians(bank);
        final float cos = (float) Math.cos(bankRad);
        final float sin = (float) Math.sin(bankRad);
        final float normalX = -sin;
        final float normalY = cos;
        final float pitchScale = Math.max(0.85f, windowH / 42.0f);
        final int tiltColor = (readout.gimbal != null) ? readout.gimbal.state().color : theme.dim;
        final int planColor = aeroPlanColor(readout, theme);
        final int centerX = innerX + innerW / 2;
        final int centerY = windowY + windowH / 2;

        g.fill(innerX, innerY, innerX + innerW, innerY + bankH, darken(theme.graphBg, 0.88f));
        borderGui(g, innerX, innerY, innerW, bankH, theme.border);
        g.drawString(font, "P " + aeroPitchValue(readout), innerX + 3, innerY + 2, theme.text);
        final String bankText = "B " + aeroBankValue(readout);
        g.drawString(font, bankText, innerX + innerW - 3 - font.width(bankText), innerY + 2, theme.text);
        final int bankBaseY = innerY + bankH - 3;
        final int bankSpan = Math.max(10, innerW / 2 - 14);
        for (int tick = -90; tick <= 90; tick += 5) {
            final int tickX = centerX + Math.round(bankSpan * tick / 90.0f);
            final boolean major = tick % 30 == 0;
            final boolean mid = tick % 10 == 0;
            final int tickH = major ? 5 : (mid ? 4 : 2);
            g.fill(tickX, bankBaseY - tickH, tickX + 1, bankBaseY + 1, theme.border);
        }
        final int bankMarkerX = centerX + Math.round(bankSpan * Math.max(-1.0f, Math.min(1.0f, bank / 90.0f)));
        g.fill(bankMarkerX - 1, innerY + 2, bankMarkerX + 2, innerY + bankH - 2, tiltColor);

        g.fill(innerX, windowY, innerX + innerW, windowY + windowH, darken(theme.graphBg, 0.86f));
        borderGui(g, innerX, windowY, innerW, windowH, theme.border);
        if (readout.gimbal == null) {
            g.drawCenteredString(font, "NO GIMBAL", innerX + innerW / 2, centerY - font.lineHeight, theme.text);
            g.drawCenteredString(font, "Attitude offline", innerX + innerW / 2, centerY + 4, theme.dim);
        } else {
            for (int step = -40; step <= 40; step += 5) {
                final float offset = (pitch - step) * pitchScale;
                final float lineCenterX = centerX + normalX * offset;
                final float lineCenterY = centerY + normalY * offset;
                final boolean major = step % 10 == 0;
                final float length = (step == 0) ? innerW * 0.72f : (major ? innerW * 0.44f : innerW * 0.26f);
                final int x1 = Math.round(lineCenterX - cos * length * 0.5f);
                final int y1 = Math.round(lineCenterY - sin * length * 0.5f);
                final int x2 = Math.round(lineCenterX + cos * length * 0.5f);
                final int y2 = Math.round(lineCenterY + sin * length * 0.5f);
                drawLineGui(g, x1, y1, x2, y2, step == 0 ? theme.accent : (major ? theme.text : theme.dim));
            }
            final float trimNorm = aeroTrimCue(readout);
            final int trimY = centerY - Math.round(trimNorm * Math.max(3, windowH / 2 - 6));
            drawLineGui(g, centerX - 10, trimY, centerX + 10, trimY, aeroVerticalColor(readout, theme));
            final float planNorm = Math.max(-1.0f, Math.min(1.0f, aeroPlanAngle(readout) / 90.0f));
            final int cueX = centerX + Math.round(planNorm * Math.max(5, innerW / 2 - 10));
            drawLineGui(g, cueX, centerY + 2, cueX, windowY + windowH - 4, planColor);
            drawLineGui(g, centerX - 10, centerY, centerX - 3, centerY, theme.text);
            drawLineGui(g, centerX + 3, centerY, centerX + 10, centerY, theme.text);
            drawLineGui(g, centerX, centerY - 2, centerX, centerY + 2, theme.text);
            g.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, theme.text);
        }

        g.fill(innerX, cueY, innerX + innerW, cueY + cueH, darken(theme.graphBg, 0.88f));
        borderGui(g, innerX, cueY, innerW, cueH, theme.border);
        final String planSource = aeroPlanSource(readout);
        g.drawString(font, planSource, innerX + 3, cueY + 2, theme.dim);
        final String planAngle = aeroPlanAngleValue(readout);
        g.drawString(font, planAngle, innerX + innerW - 3 - font.width(planAngle), cueY + 2, planColor);
        final int barY = cueY + font.lineHeight + 1;
        final int barH = Math.max(4, cueY + cueH - barY - 2);
        drawCenteredBarGui(g, innerX + 3, barY, innerW - 6, barH, aeroPlanAngle(readout), 180.0f, theme, planColor);
    }

    private static String formatAngle(float angle) {
        return String.format("%.1f°", angle);
    }

    private static String formatForce(float value) {
        return formatCompact(value);
    }

    private static String formatSignedForce(float value) {
        return (value > 0.0f ? "+" : (value < 0.0f ? "-" : "")) + formatCompact(Math.abs(value));
    }

    private static String formatMass(float value) {
        return formatCompact(value);
    }

    private static String formatPower(float power) {
        return Integer.toString(Math.round(power));
    }

    private static String formatPercent(float value) {
        return String.format("%.1f%%", value);
    }

    private static String formatCoordinate(float value) {
        return Integer.toString(Math.round(value));
    }

    private static String boolText(boolean value, String on, String off) {
        return value ? on : off;
    }

    private static int pressureColor(float airPressure) {
        if (airPressure <= 5.0f) {
            return DANGER_RED;
        }
        if (airPressure <= 35.0f) {
            return ALERT_AMBER;
        }
        return SAFE_GREEN;
    }

    private static int powerColor(float power) {
        if (power >= 12.0f) {
            return POWER_ON;
        }
        if (power > 0.0f) {
            return ALERT_AMBER;
        }
        return POWER_OFF;
    }

    private static int themeNeutral() {
        return -11184811;
    }

    private static int mixColor(int c1, int c2, float t) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, t));
        final int a = (int)((c1 >>> 24 & 0xFF) * (1.0f - clamped) + (c2 >>> 24 & 0xFF) * clamped);
        final int r = (int)((c1 >> 16 & 0xFF) * (1.0f - clamped) + (c2 >> 16 & 0xFF) * clamped);
        final int g = (int)((c1 >> 8 & 0xFF) * (1.0f - clamped) + (c2 >> 8 & 0xFF) * clamped);
        final int b = (int)((c1 & 0xFF) * (1.0f - clamped) + (c2 & 0xFF) * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static record StressHistorySnapshot(float[] data, float peak, float average, float range, float delta, int windowSeconds, int sampleCount) {}

    private static record PortableEngineReadout(StatsDisplayPanelBE.DataChannel trendChannel, float currentBurn, float totalBurn, float burnPercent, float generatedSpeed, boolean infiniteFuel, boolean superheated, StateInfo state) {
        private String burnLabel() {
            return this.infiniteFuel ? "∞ / ∞ t" : MetricType.BURN_TIME.formatValue(this.currentBurn) + " / " + MetricType.BURN_TIME.formatValue(Math.max(this.totalBurn, this.currentBurn));
        }
    }

    private static record DieselEngineReadout(StatsDisplayPanelBE.DataChannel trendChannel, float fuelAmount, float fuelCapacity, float fuelPercent,
                                              float reserveAmount, float reserveCapacity, float reservePercent, float reserveLinks, boolean reserveMismatch, String reserveFuelLabel,
                                              float generatedSpeed, float generatedStress, float burnRate, float remainingTicks, float engineCount,
                                              boolean running, boolean enabled, boolean validFuel, boolean redstoneDisabled, StateInfo state) {
        private String fuelLabel() {
            if (this.fuelCapacity > 0.0f) {
                return formatCompact(this.fuelAmount) + " / " + formatCompact(this.fuelCapacity) + " mB";
            }
            return formatCompact(this.fuelAmount) + " mB";
        }

        private String reserveLabel() {
            if (this.reserveLinks <= 0.0f) {
                return "No linked reserve";
            }
            if (this.reserveCapacity > 0.0f) {
                return this.reserveFuelLabel + " " + formatCompact(this.reserveAmount) + " / " + formatCompact(this.reserveCapacity) + " mB";
            }
            return this.reserveFuelLabel + " " + formatCompact(this.reserveAmount) + " mB";
        }

        private String reserveCardValue() {
            if (this.reserveLinks <= 0.0f) {
                return "NO LINK";
            }
            if (this.reserveMismatch) {
                return "CHECK";
            }
            return formatPercent(this.reservePercent);
        }

        private String supplyLabel() {
            final float totalAmount = this.fuelAmount + this.reserveAmount;
            final float totalCapacity = this.fuelCapacity + this.reserveCapacity;
            if (totalCapacity > 0.0f) {
                return formatCompact(totalAmount) + " / " + formatCompact(totalCapacity) + " mB";
            }
            return formatCompact(totalAmount) + " mB";
        }

        private float supplyPercent() {
            final float totalCapacity = this.fuelCapacity + this.reserveCapacity;
            return totalCapacity > 0.0f ? Math.max(0.0f, Math.min(100.0f, (this.fuelAmount + this.reserveAmount) * 100.0f / totalCapacity)) : this.fuelPercent;
        }

        private String statusLine() {
            if (!this.enabled) {
                return "Engine controls disabled";
            }
            if (this.redstoneDisabled) {
                return "Redstone lockout";
            }
            if (!this.validFuel || this.reserveMismatch) {
                return "Fuel type mismatch";
            }
            if (this.reserveLinks > 0.0f && this.reserveAmount > 0.0f) {
                return "Reserve linked into engine feed";
            }
            if (this.fuelAmount > 0.0f) {
                return "Local tank feeding engine";
            }
            return "No fuel reserve linked";
        }

        private String flowLabel() {
            if (this.burnRate > 0.0f) {
                return formatCompact(this.burnRate) + "/t";
            }
            if (this.remainingTicks > 0.0f) {
                return MetricType.BURN_TIME.formatValue(this.remainingTicks);
            }
            return this.running ? "LIVE" : "IDLE";
        }

        private boolean hasFuelFlow() {
            return this.running || this.burnRate > 0.0f || this.remainingTicks > 0.0f;
        }

        private int fuelColor(DisplayTheme theme) {
            if (!this.validFuel) {
                return DANGER_RED;
            }
            if (this.reserveMismatch) {
                return ALERT_AMBER;
            }
            if (this.fuelPercent > 0.0f && this.fuelPercent < 20.0f) {
                return ALERT_AMBER;
            }
            if (this.fuelPercent > 0.0f || this.reserveAmount > 0.0f) {
                return SAFE_GREEN;
            }
            return theme.dim;
        }
    }

    private static record DieselProcessReadout(StatsDisplayPanelBE.DataChannel trendChannel, String sourceType, float fluidAmount, float fluidCapacity,
                                               float fillPercent, float processPercent, boolean processing, float heatLevel, float oilRemaining,
                                               float pipeLength, float towerWidth, float towerHeight, float valvePercent, float redstoneOutput, float blocks, StateInfo state) {
        private String volumeLabel() {
            if (this.fluidCapacity > 0.0f) {
                return formatCompact(this.fluidAmount) + " / " + formatCompact(this.fluidCapacity) + " mB";
            }
            if (this.oilRemaining > 0.0f) {
                return formatCompact(this.oilRemaining) + " mB oil";
            }
            return formatCompact(this.fluidAmount) + " mB";
        }

        private String topLine() {
            if ("Oil Pumpjack".equals(this.sourceType) && this.oilRemaining > 0.0f) {
                return formatCompact(this.oilRemaining) + " mB remaining";
            }
            if (this.processing) {
                return String.format("%.1f%% PROCESS", this.processPercent);
            }
            return String.format("%.1f%% FILL", this.fillPercent);
        }

        private String auxLabel() {
            if ("Diesel Burner".equals(this.sourceType)) {
                return "VALVE";
            }
            if ("Oil Pumpjack".equals(this.sourceType)) {
                return "PIPE";
            }
            return "HEAT";
        }

        private String auxValue() {
            if ("Diesel Burner".equals(this.sourceType)) {
                return String.format("%.1f%%", this.valvePercent);
            }
            if ("Oil Pumpjack".equals(this.sourceType)) {
                return formatCompact(this.pipeLength);
            }
            return formatCompact(this.heatLevel);
        }

        private String heatLabel() {
            if (this.heatLevel <= 0.0f) {
                return "COLD";
            }
            return "LEVEL " + Math.round(this.heatLevel);
        }

        private int heatColor(DisplayTheme theme) {
            if (this.heatLevel <= 0.0f) {
                return theme.dim;
            }
            if (this.heatLevel < 2.0f) {
                return ALERT_AMBER;
            }
            return SAFE_GREEN;
        }

        private String towerShapeLabel() {
            final int width = Math.max(1, Math.round(this.towerWidth));
            final int height = Math.max(1, Math.round(this.towerHeight));
            return width + "x" + height;
        }

        private String distillationLine() {
            if (this.processing) {
                return "GAS + DIESEL";
            }
            if (this.heatLevel <= 0.0f && this.fillPercent > 0.0f) {
                return "NEEDS HEAT";
            }
            return "SEPARATING";
        }

        private String inputNodeLabel() {
            return "Oil Pumpjack".equals(this.sourceType) ? "DEPOSIT" : "INPUT";
        }

        private String processNodeLabel() {
            if ("Diesel Distillation Tower".equals(this.sourceType)) {
                return "TOWER";
            }
            if ("Diesel Burner".equals(this.sourceType)) {
                return "BURNER";
            }
            if ("Oil Barrel".equals(this.sourceType)) {
                return "BARREL";
            }
            return "PUMP";
        }

        private String outputNodeLabel() {
            if ("Diesel Burner".equals(this.sourceType)) {
                return "HEAT";
            }
            if ("Oil Pumpjack".equals(this.sourceType)) {
                return "CRUDE";
            }
            return "OUTPUT";
        }

        private String outputNodeValue() {
            if ("Diesel Burner".equals(this.sourceType)) {
                return formatCompact(this.heatLevel);
            }
            return this.processing ? "FLOW" : "HOLD";
        }

        private String oilLabel() {
            return this.oilRemaining > 0.0f ? formatCompact(this.oilRemaining) + " mB" : "DRY";
        }

        private String pipeLabel() {
            return this.pipeLength > 0.0f ? formatCompact(this.pipeLength) + " m" : "NO PIPE";
        }

        private String flowNodeValue() {
            return this.processing ? "CRUDE" : "HOLD";
        }

        private float depositVisualPercent() {
            if (this.oilRemaining <= 0.0f) {
                return 0.0f;
            }
            return Mth.clamp(this.oilRemaining / 10000.0f, 0.08f, 1.0f);
        }

        private int fillColor(DisplayTheme theme) {
            if ("Oil Pumpjack".equals(this.sourceType)) {
                if (this.oilRemaining <= 0.0f) {
                    return theme.dim;
                }
                return this.oilRemaining < 1000.0f ? ALERT_AMBER : SAFE_GREEN;
            }
            if (this.fillPercent <= 0.0f) {
                return theme.dim;
            }
            if (this.fillPercent < 20.0f) {
                return ALERT_AMBER;
            }
            return SAFE_GREEN;
        }
    }

    private static record FuelReserveAggregate(float amount, float capacity, float fillPercent, float links, boolean mismatch, String label) {
    }

    private static record VelocityReadout(StatsDisplayPanelBE.DataChannel trendChannel, float axisVelocity, float horizontalVelocity, float redstoneStrength, float signedRedstone,
                                          float headingYaw, float forwardX, float forwardZ, String headingSource, float headingConfidence, boolean velocityAssistReady, StateInfo state) {
        private boolean hasForwardVector() {
            return !Float.isNaN(this.forwardX) && !Float.isNaN(this.forwardZ) && this.forwardX * this.forwardX + this.forwardZ * this.forwardZ > 1.0E-4f;
        }
    }

    private static record GimbalReadout(float xAngle, float zAngle, float attitudePitch, float attitudeBank, float attitudeYaw, float forwardX, float forwardZ,
                                        String headingSource, float headingConfidence, float powerDown, float powerUp, float powerNorth, float powerSouth, float powerWest, float powerEast, StateInfo state) {
        private float maxTilt() {
            return Math.max(Math.abs(this.xAngle), Math.abs(this.zAngle));
        }

        private boolean estimatedPitch() {
            return Float.isNaN(this.attitudePitch);
        }

        private boolean estimatedBank() {
            return Float.isNaN(this.attitudeBank);
        }

        private float flightDeckPitch() {
            return Float.isNaN(this.attitudePitch) ? this.xAngle : this.attitudePitch;
        }

        private float flightDeckBank() {
            return Float.isNaN(this.attitudeBank) ? this.zAngle : this.attitudeBank;
        }

        private boolean hasHeadingReference() {
            return !Float.isNaN(this.attitudeYaw);
        }

        private float flightDeckYaw() {
            return this.hasHeadingReference() ? this.attitudeYaw : 0.0f;
        }

        private boolean hasForwardVector() {
            return !Float.isNaN(this.forwardX) && !Float.isNaN(this.forwardZ)
                    && this.forwardX * this.forwardX + this.forwardZ * this.forwardZ > 1.0E-4f;
        }
    }

    private static record AltitudeReadout(float worldHeight, float normalizedHeight, float lowBandHeight, float highBandHeight, float airPressure, float signal, float heightRate, StateInfo state) {}

    private static record DockingReadout(boolean paired, boolean loaded, boolean locked, boolean extended, boolean powered, float closestPairDistance, String stateText, float pairX, float pairY, float pairZ, String sublevelText, String positionText, StateInfo state) {}

    private static record HotAirBurnerReadout(String gasTypeText, float signalStrength, float gasOutput, float targetVolume, float capacityVolume,
                                              boolean powered, boolean canOutput, boolean balloonPresent, float balloonFillPercent,
                                              float balloonTargetPercent, float predictedPercent, float balloonLift, float outputPercent,
                                              float efficiencyPercent, StateInfo state) {
        private String flowValue() {
            if (this.canOutput) {
                return this.efficiencyPercent > 0.5f ? "E " + formatPercent(this.efficiencyPercent) : "OPEN";
            }
            return this.powered ? "BLOCK" : "IDLE";
        }

        private int flowColor() {
            if (this.canOutput) {
                return SAFE_GREEN;
            }
            return this.powered ? ALERT_AMBER : POWER_OFF;
        }

        private String fillValue() {
            if (this.balloonPresent) {
                return formatPercent(this.balloonFillPercent);
            }
            if (this.predictedPercent > 0.01f) {
                return formatPercent(this.predictedPercent);
            }
            return "--";
        }

        private int fillColor(DisplayTheme theme) {
            if (this.balloonPresent || this.predictedPercent > 0.01f) {
                return SAFE_GREEN;
            }
            return theme.dim;
        }
    }

    private static record ContraptionInfoReadout(float propulsion, float gravity, float drag, float levitation, float balloonLift, float lift,
                                                 float magneticForce, float otherForce, float support, float forwardThrust, float netVertical,
                                                 float netForward, float brakingForce, float downforce, float propulsionSupport,
                                                 float levitationSupport, float balloonSupport, float aeroSupport, float magneticSupport,
                                                 float otherSupport, float totalMass, String forwardAxisMode, float axisConfidence, String headingSource, StateInfo state) {
        private float upwardForce() {
            return this.support;
        }

        private float verticalMargin() {
            return this.netVertical;
        }

        private float thrustMargin() {
            return this.netForward;
        }

        private float thrust() {
            return this.forwardThrust;
        }

        private float reverseForce() {
            return this.brakingForce;
        }

        private float verticalScale() {
            return Math.max(1.0f, Math.max(Math.max(this.upwardForce(), this.gravity), this.downforce));
        }

        private float forwardScale() {
            return Math.max(1.0f, Math.max(this.thrust(), this.reverseForce()));
        }

        private String forwardAxisTag() {
            return switch (this.forwardAxisMode) {
                case "NOSE", "BODY", "VELO", "MOTION", "CACHED" -> this.forwardAxisMode;
                case "UNRESOLVED" -> "UNRES";
                default -> this.forwardAxisMode == null || this.forwardAxisMode.isBlank() ? "--" : this.forwardAxisMode;
            };
        }

        private int forwardAxisColor(DisplayTheme theme) {
            return switch (this.forwardAxisMode) {
                case "NOSE" -> SAFE_GREEN;
                case "BODY" -> SAFE_GREEN;
                case "VELO" -> theme.accent;
                case "MOTION" -> ALERT_AMBER;
                case "CACHED" -> theme.accent;
                case "UNRESOLVED" -> POWER_OFF;
                default -> theme.dim;
            };
        }

        private float dominantForce() {
            return Math.max(1.0f, Math.max(Math.max(this.propulsion, this.gravity),
                    Math.max(Math.max(Math.max(this.drag, this.levitation), Math.max(this.balloonLift, this.lift)),
                            Math.max(Math.max(this.magneticForce, this.otherForce), Math.max(this.support, Math.max(this.thrust(), this.reverseForce()))))));
        }

        private boolean hasLiveForceData() {
            return this.totalMass > 0.01f || this.dominantForce() > 1.01f;
        }
    }

    private static record SteeringReadout(float angle, float targetAngle, float commandAngle, float generatedSpeed, float logicalSpeed, boolean held, boolean inUse, StateInfo state) {
        private float maxAngle() {
            return Math.max(45.0f, Math.max(Math.abs(this.angle), Math.max(Math.abs(this.targetAngle), Math.abs(this.commandAngle))));
        }

        private String activityLine() {
            if (this.held) {
                return "Operator hands on wheel";
            }
            if (this.inUse) {
                return "Wheel is reserved for helm control";
            }
            return "Wheel is waiting for pilot input";
        }
    }

    private static record LaserReadout(float currentPower, float closestHitDistance, boolean hitPresent, boolean powered, StateInfo state) {}

    private static record OpticalReadout(float rayDistance, float hitDistance, boolean hitPresent, boolean powered, String blockName, StateInfo state) {}

    private static record NavigationReadout(float distanceToTarget, float lastDistanceToTarget, float distanceDelta, float relativeAngle, float signedAngle,
                                            float targetDx, float targetDz,
                                            float powerDown, float powerUp, float powerNorth, float powerSouth, float powerWest, float powerEast,
                                            boolean hasTarget, StateInfo state) {}

    private static record HeadingReadout(float yaw, float forwardX, float forwardZ, String sourceLabel, float confidence, boolean fromGimbal, boolean velocityAssistReady) {
        private boolean hasForwardVector() {
            return !Float.isNaN(this.forwardX) && !Float.isNaN(this.forwardZ) && this.forwardX * this.forwardX + this.forwardZ * this.forwardZ > 1.0E-4f;
        }
    }

    public static record VehicleSettingsStatus(StatsDisplaySettings.NoseSetting noseSetting, StatsDisplaySettings.VehiclePreset selectedPreset,
                                               StatsDisplaySettings.VehiclePreset effectivePreset, String headingSource, String headingDetail,
                                               boolean headingAvailable, boolean velocityAssistReady, String velocityDetail, float previewYaw,
                                               String authorityLabel) {}

    private static record VehicleBehaviorProfile(float holdAngle, float bankCueAngle, float hardCueAngle, float closeRangeDistance, float closeRangeSpeed,
                                                 float rangeSpeedDivider, float minCruiseSpeed, float maxCruiseSpeed,
                                                 float highRangeCruiseFloor, float lowRangeCruiseFloor) {}

    private static record LinkedTypewriterReadout(String text, boolean hasText, boolean powered, float typedLength, float activeKeys, StateInfo state) {
        private String statusLine() {
            if (this.powered && this.hasText) {
                return "Operator linked • live text buffered";
            }
            if (this.powered) {
                return "Operator linked • awaiting input";
            }
            if (this.hasText) {
                return "Stored entry available for later flight-deck views";
            }
            return "No buffered entry";
        }
    }

    private static record AeroFlightDeckReadout(PortableEngineReadout engine, VelocityReadout velocity, GimbalReadout gimbal, AltitudeReadout altitude,
                                                DockingReadout docking, SteeringReadout steering, LaserReadout laser, OpticalReadout optical,
                                                NavigationReadout navigation, HotAirBurnerReadout liftEmitter, ContraptionInfoReadout contraption,
                                                LinkedTypewriterReadout typewriter, HeadingReadout heading, StatsDisplaySettings.VehiclePreset effectivePreset,
                                                int boundSources, int liveSources, StateInfo state,
                                                String subTitle, String deckLine, String footerText) {}

    private static record AerolineDirectorReadout(AeroFlightDeckReadout deck, String modeLabel, String subtitle, String orderText,
                                                  String footerText, String rangeText, StateInfo state) {}

    private static record AerolineAnimatedFrame(float pitch, float bank, float trimNorm, float planNorm) {}

    private static final class AerolineAnimationState {
        private long lastTick = Long.MIN_VALUE;
        private boolean initialized;
        private boolean previousLive;
        private boolean currentLive;
        private float previousPitch;
        private float currentPitch;
        private float previousBank;
        private float currentBank;
        private float previousTrimNorm;
        private float currentTrimNorm;
        private float previousPlanNorm;
        private float currentPlanNorm;

        private void capture(AeroFlightDeckReadout deck, long renderTick) {
            if (this.lastTick == renderTick) {
                return;
            }
            final AerolineAnimatedFrame frame = captureAerolineFrame(deck);
            final boolean live = deck.gimbal() != null;
            if (!this.initialized) {
                this.previousPitch = frame.pitch();
                this.currentPitch = frame.pitch();
                this.previousBank = frame.bank();
                this.currentBank = frame.bank();
                this.previousTrimNorm = frame.trimNorm();
                this.currentTrimNorm = frame.trimNorm();
                this.previousPlanNorm = frame.planNorm();
                this.currentPlanNorm = frame.planNorm();
                this.previousLive = live;
                this.currentLive = live;
                this.lastTick = renderTick;
                this.initialized = true;
                return;
            }
            final boolean discontinuity = live != this.currentLive || Math.abs(this.currentPlanNorm - frame.planNorm()) > 1.1f;
            this.previousPitch = discontinuity ? frame.pitch() : this.currentPitch;
            this.currentPitch = frame.pitch();
            this.previousBank = discontinuity ? frame.bank() : this.currentBank;
            this.currentBank = frame.bank();
            this.previousTrimNorm = discontinuity ? frame.trimNorm() : this.currentTrimNorm;
            this.currentTrimNorm = frame.trimNorm();
            this.previousPlanNorm = discontinuity ? frame.planNorm() : this.currentPlanNorm;
            this.currentPlanNorm = frame.planNorm();
            this.previousLive = this.currentLive;
            this.currentLive = live;
            this.lastTick = renderTick;
        }

        private AerolineAnimatedFrame sample(float partialTick) {
            if (!this.initialized || !this.currentLive || !this.previousLive) {
                return new AerolineAnimatedFrame(this.currentPitch, this.currentBank, this.currentTrimNorm, this.currentPlanNorm);
            }
            final float t = Mth.clamp(partialTick, 0.0f, 1.0f);
            return new AerolineAnimatedFrame(
                    Mth.lerp(t, this.previousPitch, this.currentPitch),
                    Mth.lerp(t, this.previousBank, this.currentBank),
                    Mth.lerp(t, this.previousTrimNorm, this.currentTrimNorm),
                    Mth.lerp(t, this.previousPlanNorm, this.currentPlanNorm)
            );
        }
    }

    private static void renderBattleScreenTexture(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, long renderTick) {
        final BattleReadout readout = resolveBattleReadout(be, source, renderTick);
        if (readout == null) {
            renderUnsupportedTexture(image, theme, "Waiting for battle feed");
            return;
        }
        final StatsDisplaySettings.BattleBoardPreset preset = resolveBattleBoardPreset(be);
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 30);
        final int titleScale = (w >= 360) ? 2 : 1;
        BitmapFont.drawString(image, battleBoardTitle(preset), pad, pad, theme.text, titleScale);
        BitmapFont.drawRight(image, truncate(readout.headerRight(), Math.max(8, (w - pad * 2) / 8)), w - pad, pad, theme.dim, 1);
        BitmapFont.drawString(image, truncate(readout.sourceLine(), Math.max(14, (w - pad * 2 - 100) / 6)), pad, pad + BitmapFont.textHeight(titleScale) + 3, theme.dim, 1);
        drawBadgeTexture(image, w - pad - 92, pad + BitmapFont.textHeight(titleScale) + 1, 92, 12, readout.state().label, readout.state().color);
        BitmapFont.drawString(image, truncate(readout.statusLine(), Math.max(14, (w - pad * 2) / 6)), pad, pad + BitmapFont.textHeight(titleScale) + 14, theme.accent, 1);

        final int contentTop = pad + BitmapFont.textHeight(titleScale) + 26;
        final int gap = 6;
        final int availableBodyH = Math.max(0, h - contentTop - pad);
        final boolean showFooter = availableBodyH >= 96;
        final int footerH = showFooter ? Math.max(30, Math.min(h / 4, Math.max(30, availableBodyH / 3))) : 0;
        final int contentH = showFooter ? Math.max(56, availableBodyH - footerH - gap) : Math.max(48, availableBodyH);
        int leftW = Math.max(118, (w - pad * 2 - gap) * 11 / 18);
        int rightW = w - pad * 2 - gap - leftW;
        if (rightW < 82) {
            rightW = 82;
            leftW = Math.max(110, w - pad * 2 - gap - rightW);
        }
        final int rightX = pad + leftW + gap;
        switch (preset) {
            case WEAPONS_BOARD -> {
                drawBattleWeaponHeroTexture(image, pad, contentTop, leftW, contentH, readout, theme);
                drawBattleMetricGridTexture(image, rightX, contentTop, rightW, contentH, battleWeaponSummaryCards(readout, theme), theme);
            }
            case DAMAGE_TRENDS -> {
                drawBattleCategoryHeroTexture(image, pad, contentTop, leftW, contentH, readout, theme, renderTick, "INTEGRITY MAP", battleDamageHeroLine(readout), DamageObserverSettings.ObserverCategory.values());
                drawBattleMetricGridTexture(image, rightX, contentTop, rightW, contentH, battleDamageCards(readout, theme), theme);
            }
            case CARGO_AND_SYSTEMS -> {
                drawBattleCategoryHeroTexture(image, pad, contentTop, leftW, contentH, readout, theme, renderTick, "SYSTEMS OVERVIEW", battleSystemsLine(readout), new DamageObserverSettings.ObserverCategory[] {
                        DamageObserverSettings.ObserverCategory.POWER,
                        DamageObserverSettings.ObserverCategory.CONTROL,
                        DamageObserverSettings.ObserverCategory.LIFT,
                        DamageObserverSettings.ObserverCategory.CARGO
                });
                drawBattleMetricGridTexture(image, rightX, contentTop, rightW, contentH, battleSystemCards(readout, theme), theme);
            }
            default -> {
                drawBattleHeroTexture(image, pad, contentTop, leftW, contentH, readout, theme, renderTick);
                drawBattleMetricGridTexture(image, rightX, contentTop, rightW, contentH, readout, theme);
            }
        }

        if (showFooter) {
            final int footerY = contentTop + contentH + gap;
            int trendW = Math.max(96, (w - pad * 2 - gap) * (preset == StatsDisplaySettings.BattleBoardPreset.DAMAGE_TRENDS ? 13 : 11) / 18);
            int detailW = w - pad * 2 - gap - trendW;
            if (detailW < 88) {
                detailW = 88;
                trendW = Math.max(96, w - pad * 2 - gap - detailW);
            }
            drawBattleTrendTexture(image, pad, footerY, trendW, footerH, readout, theme);
            drawBattleFocusSectionTexture(image, pad + trendW + gap, footerY, detailW, footerH, readout, theme);
        }
    }

    private static void renderBattleScreenGui(GuiGraphics g, Font font, int x, int y, int w, int h, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, long renderTick) {
        final BattleReadout readout = resolveBattleReadout(be, source, renderTick);
        if (readout == null) {
            drawUnsupportedGui(g, font, x, y, w, h, theme, "Waiting for battle feed");
            return;
        }
        final StatsDisplaySettings.BattleBoardPreset preset = resolveBattleBoardPreset(be);
        final int pad = Math.max(10, w / 28);
        final String headerRight = truncateGui(font, readout.headerRight(), Math.max(36, w / 4));
        g.drawString(font, battleBoardTitle(preset), x + pad, y + pad - 2, theme.text);
        g.drawString(font, headerRight, x + w - pad - font.width(headerRight), y + pad - 2, theme.dim);
        g.drawString(font, truncateGui(font, readout.sourceLine(), w - pad * 2 - 120), x + pad, y + pad + 10, theme.dim);
        drawBadgeGui(g, font, x + w - pad - 108, y + pad - 2, 108, 16, readout.state().label, readout.state().color);
        g.drawString(font, truncateGui(font, readout.statusLine(), w - pad * 2), x + pad, y + pad + 22, theme.accent);

        final int contentTop = y + pad + 34;
        final int gap = 8;
        final int availableBodyH = Math.max(0, y + h - contentTop - pad);
        final boolean showFooter = availableBodyH >= 112;
        final int footerH = showFooter ? Math.max(28, Math.min(h / 5, Math.max(28, availableBodyH / 4))) : 0;
        final int contentH = showFooter ? Math.max(72, availableBodyH - footerH - gap) : Math.max(56, availableBodyH);
        int leftW = Math.max(136, (w - pad * 2 - gap) * 11 / 18);
        int rightW = w - pad * 2 - gap - leftW;
        if (rightW < 102) {
            rightW = 102;
            leftW = Math.max(122, w - pad * 2 - gap - rightW);
        }
        final int rightX = x + pad + leftW + gap;
        switch (preset) {
            case WEAPONS_BOARD -> {
                drawBattleWeaponHeroGui(g, font, x + pad, contentTop, leftW, contentH, readout, theme);
                drawBattleMetricGridGui(g, font, rightX, contentTop, rightW, contentH, battleWeaponSummaryCards(readout, theme), theme);
            }
            case DAMAGE_TRENDS -> {
                drawBattleCategoryHeroGui(g, font, x + pad, contentTop, leftW, contentH, readout, theme, renderTick, "INTEGRITY MAP", battleDamageHeroLine(readout), DamageObserverSettings.ObserverCategory.values());
                drawBattleMetricGridGui(g, font, rightX, contentTop, rightW, contentH, battleDamageCards(readout, theme), theme);
            }
            case CARGO_AND_SYSTEMS -> {
                drawBattleCategoryHeroGui(g, font, x + pad, contentTop, leftW, contentH, readout, theme, renderTick, "SYSTEMS OVERVIEW", battleSystemsLine(readout), new DamageObserverSettings.ObserverCategory[] {
                        DamageObserverSettings.ObserverCategory.POWER,
                        DamageObserverSettings.ObserverCategory.CONTROL,
                        DamageObserverSettings.ObserverCategory.LIFT,
                        DamageObserverSettings.ObserverCategory.CARGO
                });
                drawBattleMetricGridGui(g, font, rightX, contentTop, rightW, contentH, battleSystemCards(readout, theme), theme);
            }
            default -> {
                drawBattleHeroGui(g, font, x + pad, contentTop, leftW, contentH, readout, theme, renderTick);
                drawBattleMetricGridGui(g, font, rightX, contentTop, rightW, contentH, readout, theme);
            }
        }

        if (showFooter) {
            final int footerY = contentTop + contentH + gap;
            int trendW = Math.max(116, (w - pad * 2 - gap) * (preset == StatsDisplaySettings.BattleBoardPreset.DAMAGE_TRENDS ? 13 : 11) / 18);
            int detailW = w - pad * 2 - gap - trendW;
            if (detailW < 104) {
                detailW = 104;
                trendW = Math.max(108, w - pad * 2 - gap - detailW);
            }
            drawBattleTrendGui(g, font, x + pad, footerY, trendW, footerH, readout, theme);
            drawBattleFocusSectionGui(g, font, x + pad + trendW + gap, footerY, detailW, footerH, readout, theme);
        }
    }

    private static void drawBattleHeroTexture(NativeImage image, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme, long renderTick) {
        drawBattleCategoryHeroTexture(image, x, y, w, h, readout, theme, renderTick, "TACTICAL OVERVIEW", battleHeroLine(readout), DamageObserverSettings.ObserverCategory.values());
    }

    private static void drawBattleCategoryHeroTexture(NativeImage image, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme, long renderTick, String title, String headerLine, DamageObserverSettings.ObserverCategory[] categories) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, title);
        BitmapFont.drawString(image, truncate(headerLine, Math.max(10, (w - 8) / 6)), x + 4, contentY + 1, readout.state().color, 1);
        final int gridY = contentY + 12;
        final int gap = 4;
        final int cols = 2;
        final int rows = Math.max(1, (categories.length + cols - 1) / cols);
        final int cellW = Math.max(36, (w - 8 - gap) / cols);
        final int cellH = Math.max(18, (y + h - gridY - 4 - gap * Math.max(0, rows - 1)) / rows);
        for (int index = 0; index < categories.length; ++index) {
            final int col = index % 2;
            final int row = index / 2;
            final int cellX = x + 4 + col * (cellW + gap);
            final int cellY = gridY + row * (cellH + gap);
            final BattleCategorySummary summary = findBattleCategory(readout, categories[index]);
            final boolean focused = readout.focusObserver() != null && readout.focusObserver().category() == summary.category();
            drawBattleCategoryCellTexture(image, cellX, cellY, cellW, cellH, summary, focused, theme, renderTick);
        }
    }

    private static void drawBattleHeroGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme, long renderTick) {
        drawBattleCategoryHeroGui(g, font, x, y, w, h, readout, theme, renderTick, "TACTICAL OVERVIEW", battleHeroLine(readout), DamageObserverSettings.ObserverCategory.values());
    }

    private static void drawBattleCategoryHeroGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme, long renderTick, String title, String headerLine, DamageObserverSettings.ObserverCategory[] categories) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, title);
        g.drawString(font, truncateGui(font, headerLine, w - 8), x + 4, contentY + 1, readout.state().color);
        final int gridY = contentY + font.lineHeight + 2;
        final int gap = 6;
        final int cols = 2;
        final int rows = Math.max(1, (categories.length + cols - 1) / cols);
        final int cellW = Math.max(48, (w - 8 - gap) / cols);
        final int cellH = Math.max(22, (y + h - gridY - 4 - gap * Math.max(0, rows - 1)) / rows);
        for (int index = 0; index < categories.length; ++index) {
            final int col = index % 2;
            final int row = index / 2;
            final int cellX = x + 4 + col * (cellW + gap);
            final int cellY = gridY + row * (cellH + gap);
            final BattleCategorySummary summary = findBattleCategory(readout, categories[index]);
            final boolean focused = readout.focusObserver() != null && readout.focusObserver().category() == summary.category();
            drawBattleCategoryCellGui(g, font, cellX, cellY, cellW, cellH, summary, focused, theme, renderTick);
        }
    }

    private static void drawBattleWeaponHeroTexture(NativeImage image, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "WEAPON POSTURE");
        BitmapFont.drawString(image, truncate(battleWeaponsLine(readout), Math.max(10, (w - 8) / 6)), x + 4, contentY + 1, battleWeaponSummaryColor(readout), 1);
        if (readout.link().getWeaponObserverCount() <= 0) {
            BitmapFont.drawCentered(image, "No weapon observers", x, y + h / 2 - 6, w, theme.text, 1);
            BitmapFont.drawCentered(image, "Link WEAPONS zones to unlock this board", x, y + h / 2 + 6, w, theme.dim, 1);
            return;
        }
        final int innerY = contentY + 12;
        drawBattleMetricGridTexture(image, x + 4, innerY, w - 8, Math.max(22, y + h - innerY - 4), battleWeaponFocusCards(readout, theme), theme);
    }

    private static void drawBattleWeaponHeroGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "WEAPON POSTURE");
        g.drawString(font, truncateGui(font, battleWeaponsLine(readout), w - 8), x + 4, contentY + 1, battleWeaponSummaryColor(readout));
        if (readout.link().getWeaponObserverCount() <= 0) {
            g.drawCenteredString(font, "No weapon observers", x + w / 2, y + h / 2 - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "Link WEAPONS zones to unlock this board", x + w / 2, y + h / 2 + font.lineHeight + 2, theme.dim);
            return;
        }
        final int innerY = contentY + font.lineHeight + 2;
        drawBattleMetricGridGui(g, font, x + 4, innerY, w - 8, Math.max(26, y + h - innerY - 4), battleWeaponFocusCards(readout, theme), theme);
    }

    private static void drawBattleCategoryCellTexture(NativeImage image, int x, int y, int w, int h, BattleCategorySummary summary, boolean focused, DisplayTheme theme, long renderTick) {
        final int accent = battleCategoryColor(summary, theme);
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, focused ? mixColor(theme.accent, accent, 0.45f) : darken(accent, 0.92f));
        if (focused) {
            TextureGraphRenderer.drawBorder(image, x + 1, y + 1, w - 2, h - 2, 1, mixColor(theme.text, accent, 0.35f));
        }
        if (summary.observerCount() > 0 && (summary.missingBlocks() > 0 || summary.newBlocks() > 0)) {
            final boolean pulse = (renderTick / 10L % 2L) == 0L;
            TextureGraphRenderer.fillRect(image, x + 1, y + 1, w - 2, 2, pulse ? accent : darken(accent, 0.65f));
        }
        BitmapFont.drawString(image, truncate(summary.category().name(), Math.max(4, (w - 8) / 6)), x + 3, y + 3, theme.dim, 1);
        if (summary.observerCount() > 0) {
            BitmapFont.drawRight(image, Integer.toString(summary.observerCount()), x + w - 3, y + 3, theme.text, 1);
        }
        final int scale = (w >= 78 && h >= 28) ? 2 : 1;
        final String value = summary.observerCount() > 0 ? battlePercentShort(summary.healthPercent()) : "--";
        BitmapFont.drawCentered(image, truncate(value, Math.max(4, w / (6 * scale))), x, y + h / 2 - BitmapFont.textHeight(scale) / 2 + 1, w, summary.observerCount() > 0 ? accent : theme.dim, scale);
        BitmapFont.drawCentered(image, truncate(battleCategoryDetail(summary), Math.max(6, (w - 8) / 6)), x, y + h - 10, w, focused ? theme.text : theme.dim, 1);
    }

    private static void drawBattleCategoryCellGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleCategorySummary summary, boolean focused, DisplayTheme theme, long renderTick) {
        final int accent = battleCategoryColor(summary, theme);
        g.fill(x, y, x + w, y + h, darken(theme.graphBg, 0.88f));
        borderGui(g, x, y, w, h, focused ? mixColor(theme.accent, accent, 0.45f) : darken(accent, 0.92f));
        if (focused) {
            borderGui(g, x + 1, y + 1, w - 2, h - 2, mixColor(theme.text, accent, 0.35f));
        }
        if (summary.observerCount() > 0 && (summary.missingBlocks() > 0 || summary.newBlocks() > 0)) {
            final boolean pulse = (renderTick / 10L % 2L) == 0L;
            g.fill(x + 1, y + 1, x + w - 1, y + 3, pulse ? accent : darken(accent, 0.65f));
        }
        g.drawString(font, truncateGui(font, summary.category().name(), w - 8), x + 3, y + 3, theme.dim);
        if (summary.observerCount() > 0) {
            final String countText = Integer.toString(summary.observerCount());
            g.drawString(font, countText, x + w - 3 - font.width(countText), y + 3, theme.text);
        }
        final String value = summary.observerCount() > 0 ? battlePercentShort(summary.healthPercent()) : "--";
        g.drawCenteredString(font, truncateGui(font, value, w - 8), x + w / 2, y + h / 2 - font.lineHeight / 2 + 1, summary.observerCount() > 0 ? accent : theme.dim);
        g.drawCenteredString(font, truncateGui(font, battleCategoryDetail(summary), w - 8), x + w / 2, y + h - font.lineHeight - 2, focused ? theme.text : theme.dim);
    }

    private static void drawBattleMetricGridTexture(NativeImage image, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        drawBattleMetricGridTexture(image, x, y, w, h, battleOverviewCards(readout, theme), theme);
    }

    private static void drawBattleMetricGridTexture(NativeImage image, int x, int y, int w, int h, BattleMetricCardSpec[] cards, DisplayTheme theme) {
        final int gap = 4;
        final int cardW = Math.max(28, (w - gap) / 2);
        final int cardH = Math.max(16, (h - gap * 2) / 3);
        for (int index = 0; index < Math.min(6, cards.length); ++index) {
            final int col = index % 2;
            final int row = index / 2;
            final int cardX = x + col * (cardW + gap);
            final int cardY = y + row * (cardH + gap);
            final int drawW = (col == 0) ? cardW : (w - cardW - gap);
            final int drawH = (row == 2) ? (h - cardH * 2 - gap * 2) : cardH;
            final BattleMetricCardSpec card = cards[index];
            drawMetricCardTexture(image, cardX, cardY, drawW, drawH, card.label(), card.value(), theme, card.color());
        }
    }

    private static void drawBattleMetricGridGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        drawBattleMetricGridGui(g, font, x, y, w, h, battleOverviewCards(readout, theme), theme);
    }

    private static void drawBattleMetricGridGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleMetricCardSpec[] cards, DisplayTheme theme) {
        final int gap = 6;
        final int cardW = Math.max(42, (w - gap) / 2);
        final int cardH = Math.max(22, (h - gap * 2) / 3);
        for (int index = 0; index < Math.min(6, cards.length); ++index) {
            final int col = index % 2;
            final int row = index / 2;
            final int cardX = x + col * (cardW + gap);
            final int cardY = y + row * (cardH + gap);
            final int drawW = (col == 0) ? cardW : (w - cardW - gap);
            final int drawH = (row == 2) ? (h - cardH * 2 - gap * 2) : cardH;
            final BattleMetricCardSpec card = cards[index];
            drawMetricCardGui(g, font, cardX, cardY, drawW, drawH, card.label(), card.value(), theme, card.color());
        }
    }

    private static void drawBattleTrendTexture(NativeImage image, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, "INTEGRITY TREND");
        final String windowText = battleTrendWindow(readout.healthTrend());
        if (!windowText.isEmpty()) {
            BitmapFont.drawRight(image, truncate(windowText, Math.max(6, w / 7)), x + w - 4, y + 3, theme.dim, 1);
        }
        if (readout.healthTrend() == null || readout.healthTrend().getStore().getSampleCount() < 2) {
            BitmapFont.drawCentered(image, "Warming up trend", x, y + h / 2 - 6, w, theme.text, 1);
            BitmapFont.drawCentered(image, "Need more battle samples", x, y + h / 2 + 6, w, theme.dim, 1);
            return;
        }
        final int chartX = x + 4;
        final int chartY = contentY + 2;
        final int chartW = w - 8;
        final int chartH = Math.max(8, y + h - chartY - 3);
        drawSparklineTextureOnly(image, chartX, chartY, chartW, chartH, readout.healthTrend().getStore(), readout.state().color, theme.grid);
        BitmapFont.drawRight(image, battlePercentShort(valueOr(readout.healthTrend(), 0.0f)), x + w - 4, y + h - 10, readout.state().color, 1);
    }

    private static void drawBattleTrendGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, "INTEGRITY TREND");
        final String windowText = battleTrendWindow(readout.healthTrend());
        if (!windowText.isEmpty()) {
            g.drawString(font, windowText, x + w - 4 - font.width(windowText), y + 3, theme.dim);
        }
        if (readout.healthTrend() == null || readout.healthTrend().getStore().getSampleCount() < 2) {
            g.drawCenteredString(font, "Warming up trend", x + w / 2, y + h / 2 - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "Need more battle samples", x + w / 2, y + h / 2 + font.lineHeight + 2, theme.dim);
            return;
        }
        final int chartX = x + 4;
        final int chartY = contentY + 2;
        final int chartW = w - 8;
        final int chartH = Math.max(12, y + h - chartY - 4);
        drawSparklineGuiOnly(g, chartX, chartY, chartW, chartH, readout.healthTrend().getStore(), readout.state().color, theme.grid);
        final String latest = battlePercentShort(valueOr(readout.healthTrend(), 0.0f));
        g.drawString(font, latest, x + w - 4 - font.width(latest), y + h - font.lineHeight - 2, readout.state().color);
    }

    private static void drawBattleFocusSectionTexture(NativeImage image, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        final BattleObserverSnapshot detailObserver = readout.weaponObserver() != null ? readout.weaponObserver() : readout.focusObserver();
        final boolean weaponMode = detailObserver != null && detailObserver.weapon().detected();
        final String title = weaponMode ? "WEAPON FOCUS" : "FOCUS DETAIL";
        final int contentY = drawSectionTexture(image, x, y, w, h, theme, title);
        final String header = weaponMode ? battleWeaponHeader(detailObserver) : battleObserverName(detailObserver);
        if (!header.isEmpty()) {
            BitmapFont.drawRight(image, truncate(header, Math.max(6, w / 7)), x + w - 4, y + 3, theme.dim, 1);
        }
        if (detailObserver == null) {
            BitmapFont.drawCentered(image, "Awaiting observer", x, y + h / 2 - 6, w, theme.text, 1);
            BitmapFont.drawCentered(image, "Display Link live feed needed", x, y + h / 2 + 6, w, theme.dim, 1);
            return;
        }
        final int gap = 4;
        final int innerX = x + 4;
        final int innerY = contentY + 2;
        final int innerW = w - 8;
        final int innerH = Math.max(12, y + h - innerY - 3);
        final int cardW = Math.max(22, (innerW - gap * 2) / 3);
        if (weaponMode) {
            final BattleWeaponSnapshot weapon = detailObserver.weapon();
            drawMetricCardTexture(image, innerX, innerY, cardW, innerH, "READY", weapon.readiness(), theme, battleWeaponColor(weapon));
            drawMetricCardTexture(image, innerX + cardW + gap, innerY, cardW, innerH, "LOAD", weapon.loadedState(), theme, battleWeaponLoadColor(weapon));
            drawMetricCardTexture(image, innerX + (cardW + gap) * 2, innerY, innerW - (cardW + gap) * 2, innerH, "AMMO", battleAmmoText(weapon), theme, battleAmmoColor(weapon));
        } else {
            drawMetricCardTexture(image, innerX, innerY, cardW, innerH, "ZONE", detailObserver.category().name(), theme, battleHealthColor(detailObserver.healthPercent(), detailObserver.missingBlocks()));
            drawMetricCardTexture(image, innerX + cardW + gap, innerY, cardW, innerH, "INTEGRITY", battlePercentShort(detailObserver.healthPercent()), theme, battleHealthColor(detailObserver.healthPercent(), detailObserver.missingBlocks()));
            drawMetricCardTexture(image, innerX + (cardW + gap) * 2, innerY, innerW - (cardW + gap) * 2, innerH, "DELTA", battleDeltaText(detailObserver), theme, detailObserver.missingBlocks() > 0 ? DANGER_RED : (detailObserver.newBlocks() > 0 ? ALERT_AMBER : theme.dim));
        }
    }

    private static void drawBattleFocusSectionGui(GuiGraphics g, Font font, int x, int y, int w, int h, BattleReadout readout, DisplayTheme theme) {
        final BattleObserverSnapshot detailObserver = readout.weaponObserver() != null ? readout.weaponObserver() : readout.focusObserver();
        final boolean weaponMode = detailObserver != null && detailObserver.weapon().detected();
        final String title = weaponMode ? "WEAPON FOCUS" : "FOCUS DETAIL";
        final int contentY = drawSectionGui(g, font, x, y, w, h, theme, title);
        final String header = weaponMode ? battleWeaponHeader(detailObserver) : battleObserverName(detailObserver);
        if (!header.isEmpty()) {
            final String trimmed = truncateGui(font, header, Math.max(36, w / 2));
            g.drawString(font, trimmed, x + w - 4 - font.width(trimmed), y + 3, theme.dim);
        }
        if (detailObserver == null) {
            g.drawCenteredString(font, "Awaiting observer", x + w / 2, y + h / 2 - font.lineHeight / 2, theme.text);
            g.drawCenteredString(font, "Display Link live feed needed", x + w / 2, y + h / 2 + font.lineHeight + 2, theme.dim);
            return;
        }
        final int gap = 6;
        final int innerX = x + 4;
        final int innerY = contentY + 2;
        final int innerW = w - 8;
        final int innerH = Math.max(18, y + h - innerY - 4);
        final int cardW = Math.max(32, (innerW - gap * 2) / 3);
        if (weaponMode) {
            final BattleWeaponSnapshot weapon = detailObserver.weapon();
            drawMetricCardGui(g, font, innerX, innerY, cardW, innerH, "READY", weapon.readiness(), theme, battleWeaponColor(weapon));
            drawMetricCardGui(g, font, innerX + cardW + gap, innerY, cardW, innerH, "LOAD", weapon.loadedState(), theme, battleWeaponLoadColor(weapon));
            drawMetricCardGui(g, font, innerX + (cardW + gap) * 2, innerY, innerW - (cardW + gap) * 2, innerH, "AMMO", battleAmmoText(weapon), theme, battleAmmoColor(weapon));
        } else {
            drawMetricCardGui(g, font, innerX, innerY, cardW, innerH, "ZONE", detailObserver.category().name(), theme, battleHealthColor(detailObserver.healthPercent(), detailObserver.missingBlocks()));
            drawMetricCardGui(g, font, innerX + cardW + gap, innerY, cardW, innerH, "INTEGRITY", battlePercentShort(detailObserver.healthPercent()), theme, battleHealthColor(detailObserver.healthPercent(), detailObserver.missingBlocks()));
            drawMetricCardGui(g, font, innerX + (cardW + gap) * 2, innerY, innerW - (cardW + gap) * 2, innerH, "DELTA", battleDeltaText(detailObserver), theme, detailObserver.missingBlocks() > 0 ? DANGER_RED : (detailObserver.newBlocks() > 0 ? ALERT_AMBER : theme.dim));
        }
    }

    private static BattleReadout resolveBattleReadout(StatsDisplayPanelBE be, SourceSelection source, long renderTick) {
        final StatsDisplayPanelBE.BattleScreenLink link = findBattleScreenLink(be, source);
        if (link == null) {
            return null;
        }
        final List<BattleObserverSnapshot> observers = link.getObservers();
        final BattleObserverSnapshot focusObserver = priorityBattleObserver(observers);
        final BattleObserverSnapshot weaponObserver = priorityWeaponObserver(observers);
        final BattleCategorySummary[] categories = buildBattleCategorySummaries(observers);
        final StatsDisplayPanelBE.DataChannel healthTrend = findMetric(source, "battle_overall_health_pct", "Battle Overall Integrity %");
        final float cargoFillPercent = computeBattleCargoFillPercent(observers);
        final int readyWeaponCount = countReadyWeapons(observers);
        final int loadedWeaponCount = countLoadedWeapons(observers);
        final long freshnessTicks = Math.max(0L, renderTick - link.getLastDataTick());
        final StateInfo state = describeBattleState(link, freshnessTicks, readyWeaponCount, loadedWeaponCount);
        String sourceLine = source != null ? source.label : "";
        if (sourceLine == null || sourceLine.isBlank()) {
            sourceLine = link.getDisplayName();
        }
        if (sourceLine == null || sourceLine.isBlank()) {
            sourceLine = "Battle Bridge";
        }
        final String headerRight = buildBattleHeaderRight(link, readyWeaponCount);
        final String statusLine = battleFreshnessText(freshnessTicks) + " / " + battleFocusLine(focusObserver, weaponObserver);
        return new BattleReadout(link, focusObserver, weaponObserver, healthTrend, categories, state, sourceLine, headerRight, statusLine, cargoFillPercent, readyWeaponCount, loadedWeaponCount, freshnessTicks);
    }

    private static StatsDisplaySettings.BattleBoardPreset resolveBattleBoardPreset(StatsDisplayPanelBE be) {
        final StatsDisplaySettings.BattleBoardPreset selected = (be != null && be.getSettings() != null && be.getSettings().getBattleBoardPreset() != null)
                ? be.getSettings().getBattleBoardPreset()
                : StatsDisplaySettings.BattleBoardPreset.STRATEGIC_OVERVIEW;
        if (be == null) {
            return selected;
        }
        final int panelArea = Math.max(1, be.getMultiblockWidth()) * Math.max(1, be.getMultiblockHeight());
        if (panelArea <= 2 && selected != StatsDisplaySettings.BattleBoardPreset.STRATEGIC_OVERVIEW) {
            return StatsDisplaySettings.BattleBoardPreset.STRATEGIC_OVERVIEW;
        }
        return selected;
    }

    private static String battleBoardTitle(StatsDisplaySettings.BattleBoardPreset preset) {
        return switch (preset) {
            case WEAPONS_BOARD -> "WEAPONS BOARD";
            case DAMAGE_TRENDS -> "INTEGRITY TRENDS";
            case CARGO_AND_SYSTEMS -> "CARGO + SYSTEMS";
            default -> "BATTLE BOARD";
        };
    }

    private static BattleCategorySummary findBattleCategory(BattleReadout readout, DamageObserverSettings.ObserverCategory category) {
        if (readout == null || category == null || readout.categories() == null) {
            return new BattleCategorySummary(category != null ? category : DamageObserverSettings.ObserverCategory.HULL, 0, 0.0, 0, 0, 0.0f, 0, 0);
        }
        for (BattleCategorySummary summary : readout.categories()) {
            if (summary.category() == category) {
                return summary;
            }
        }
        return new BattleCategorySummary(category, 0, 0.0, 0, 0, 0.0f, 0, 0);
    }

    private static BattleMetricCardSpec[] battleOverviewCards(BattleReadout readout, DisplayTheme theme) {
        return new BattleMetricCardSpec[] {
                new BattleMetricCardSpec("INTEGRITY", battlePercentShort(readout.link().getOverallHealthPercent()), battleHealthColor(readout.link().getOverallHealthPercent(), readout.link().getTotalMissingBlocks())),
                new BattleMetricCardSpec("OBS", Integer.toString(readout.link().getObserverCount()), theme.accent),
                new BattleMetricCardSpec("BREACH", Integer.toString(readout.link().getTotalMissingBlocks()), readout.link().getTotalMissingBlocks() > 0 ? DANGER_RED : SAFE_GREEN),
                new BattleMetricCardSpec("REBUILD", Integer.toString(readout.link().getTotalNewBlocks()), readout.link().getTotalNewBlocks() > 0 ? ALERT_AMBER : theme.dim),
                new BattleMetricCardSpec("CARGO", readout.cargoFillPercent() > 0.0f ? battlePercentShort(readout.cargoFillPercent()) : "--", battleCargoColor(readout.cargoFillPercent())),
                new BattleMetricCardSpec("WEAPONS", readout.link().getWeaponObserverCount() > 0 ? readout.readyWeaponCount() + "/" + readout.link().getWeaponObserverCount() : "--", battleWeaponSummaryColor(readout))
        };
    }

    private static BattleMetricCardSpec[] battleWeaponFocusCards(BattleReadout readout, DisplayTheme theme) {
        final BattleObserverSnapshot observer = readout.weaponObserver();
        final BattleWeaponSnapshot weapon = observer != null ? observer.weapon() : BattleWeaponSnapshot.empty();
        return new BattleMetricCardSpec[] {
                new BattleMetricCardSpec("READY", observer != null ? weapon.readiness() : "--", battleWeaponColor(weapon)),
                new BattleMetricCardSpec("LOAD", observer != null ? weapon.loadedState() : "--", battleWeaponLoadColor(weapon)),
                new BattleMetricCardSpec("AMMO", observer != null ? battleAmmoText(weapon) : "--", battleAmmoColor(weapon)),
                new BattleMetricCardSpec("RPM", observer != null && weapon.fireRateRpm() > 0 ? weapon.fireRateRpm() + "RPM" : "--", observer != null && weapon.fireRateRpm() > 0 ? theme.accent : theme.dim),
                new BattleMetricCardSpec("INTEGRITY", observer != null ? battlePercentShort(observer.healthPercent()) : "--", observer != null ? battleHealthColor(observer.healthPercent(), observer.missingBlocks()) : theme.dim),
                new BattleMetricCardSpec("DELTA", observer != null ? battleDeltaText(observer) : "--", observer != null && observer.missingBlocks() > 0 ? DANGER_RED : (observer != null && observer.newBlocks() > 0 ? ALERT_AMBER : theme.dim))
        };
    }

    private static BattleMetricCardSpec[] battleWeaponSummaryCards(BattleReadout readout, DisplayTheme theme) {
        return new BattleMetricCardSpec[] {
                new BattleMetricCardSpec("OBS", Integer.toString(readout.link().getWeaponObserverCount()), theme.accent),
                new BattleMetricCardSpec("READY", readout.link().getWeaponObserverCount() > 0 ? readout.readyWeaponCount() + "/" + readout.link().getWeaponObserverCount() : "--", battleWeaponSummaryColor(readout)),
                new BattleMetricCardSpec("LOADED", readout.link().getWeaponObserverCount() > 0 ? readout.loadedWeaponCount() + "/" + readout.link().getWeaponObserverCount() : "--", readout.loadedWeaponCount() > 0 ? POWER_ON : themeNeutral()),
                new BattleMetricCardSpec("POWER", battleCategoryPercent(findBattleCategory(readout, DamageObserverSettings.ObserverCategory.POWER)), battleCategoryColor(findBattleCategory(readout, DamageObserverSettings.ObserverCategory.POWER), theme)),
                new BattleMetricCardSpec("CONTROL", battleCategoryPercent(findBattleCategory(readout, DamageObserverSettings.ObserverCategory.CONTROL)), battleCategoryColor(findBattleCategory(readout, DamageObserverSettings.ObserverCategory.CONTROL), theme)),
                new BattleMetricCardSpec("HULL", battleCategoryPercent(findBattleCategory(readout, DamageObserverSettings.ObserverCategory.HULL)), battleCategoryColor(findBattleCategory(readout, DamageObserverSettings.ObserverCategory.HULL), theme))
        };
    }

    private static BattleMetricCardSpec[] battleDamageCards(BattleReadout readout, DisplayTheme theme) {
        final BattleObserverSnapshot focus = readout.focusObserver();
        return new BattleMetricCardSpec[] {
                new BattleMetricCardSpec("INTEGRITY", battlePercentShort(readout.link().getOverallHealthPercent()), battleHealthColor(readout.link().getOverallHealthPercent(), readout.link().getTotalMissingBlocks())),
                new BattleMetricCardSpec("BREACH", Integer.toString(readout.link().getTotalMissingBlocks()), readout.link().getTotalMissingBlocks() > 0 ? DANGER_RED : SAFE_GREEN),
                new BattleMetricCardSpec("REBUILD", Integer.toString(readout.link().getTotalNewBlocks()), readout.link().getTotalNewBlocks() > 0 ? ALERT_AMBER : theme.dim),
                new BattleMetricCardSpec("HOTSPOT", focus != null ? focus.category().name() : "--", focus != null ? battleHealthColor(focus.healthPercent(), focus.missingBlocks()) : theme.dim),
                new BattleMetricCardSpec("ALERT", readout.state().shortLabel, readout.state().color),
                new BattleMetricCardSpec("FRESH", battleFreshnessShort(readout.freshnessTicks()), readout.freshnessTicks() <= 40L ? theme.accent : ALERT_AMBER)
        };
    }

    private static BattleMetricCardSpec[] battleSystemCards(BattleReadout readout, DisplayTheme theme) {
        final BattleCategorySummary power = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.POWER);
        final BattleCategorySummary control = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.CONTROL);
        final BattleCategorySummary lift = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.LIFT);
        final BattleCategorySummary cargo = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.CARGO);
        final BattleCategorySummary hull = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.HULL);
        return new BattleMetricCardSpec[] {
                new BattleMetricCardSpec("CARGO", readout.cargoFillPercent() > 0.0f ? battlePercentShort(readout.cargoFillPercent()) : battleCategoryPercent(cargo), battleCargoColor(readout.cargoFillPercent() > 0.0f ? readout.cargoFillPercent() : cargo.cargoFillPercent())),
                new BattleMetricCardSpec("POWER", battleCategoryPercent(power), battleCategoryColor(power, theme)),
                new BattleMetricCardSpec("CONTROL", battleCategoryPercent(control), battleCategoryColor(control, theme)),
                new BattleMetricCardSpec("LIFT", battleCategoryPercent(lift), battleCategoryColor(lift, theme)),
                new BattleMetricCardSpec("HULL", battleCategoryPercent(hull), battleCategoryColor(hull, theme)),
                new BattleMetricCardSpec("OBS", Integer.toString(readout.link().getObserverCount()), theme.accent)
        };
    }

    private static StatsDisplayPanelBE.BattleScreenLink findBattleScreenLink(StatsDisplayPanelBE be, SourceSelection source) {
        if (be == null || source == null) {
            return null;
        }
        final BlockPos expectedPos = source.sourcePos != null ? source.sourcePos : BlockPos.ZERO;
        return findBattleScreenLink(be, expectedPos);
    }

    private static StatsDisplayPanelBE.BattleScreenLink findBattleScreenLink(StatsDisplayPanelBE be, BlockPos expectedPos) {
        if (be == null) {
            return null;
        }
        final BlockPos battlePos = expectedPos != null ? expectedPos : BlockPos.ZERO;
        StatsDisplayPanelBE.BattleScreenLink fallback = null;
        for (StatsDisplayPanelBE.BattleScreenLink link : be.getBattleScreenLinks()) {
            if (link == null) {
                continue;
            }
            if (fallback == null) {
                fallback = link;
            }
            if (battlePos.equals(link.getSourcePos())) {
                return link;
            }
        }
        return fallback;
    }

    private static BattleObserverSnapshot priorityBattleObserver(List<BattleObserverSnapshot> observers) {
        if (observers == null || observers.isEmpty()) {
            return null;
        }
        BattleObserverSnapshot weaponAlert = null;
        int bestWeaponScore = Integer.MIN_VALUE;
        for (BattleObserverSnapshot observer : observers) {
            if (!observer.weapon().detected() || observer.category() != DamageObserverSettings.ObserverCategory.WEAPONS) {
                continue;
            }
            final int score = battleWeaponRiskScore(observer);
            if (weaponAlert == null || score > bestWeaponScore || (score == bestWeaponScore && observer.healthPercent() < weaponAlert.healthPercent())) {
                weaponAlert = observer;
                bestWeaponScore = score;
            }
        }
        if (weaponAlert != null && bestWeaponScore > 0) {
            return weaponAlert;
        }
        BattleObserverSnapshot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (BattleObserverSnapshot observer : observers) {
            final int score = observer.missingBlocks() * 4 + observer.newBlocks() + (int)Math.round(100.0 - observer.healthPercent());
            if (best == null || score > bestScore) {
                best = observer;
                bestScore = score;
            }
        }
        return best;
    }

    private static BattleObserverSnapshot priorityWeaponObserver(List<BattleObserverSnapshot> observers) {
        if (observers == null || observers.isEmpty()) {
            return null;
        }
        BattleObserverSnapshot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (BattleObserverSnapshot observer : observers) {
            if (!observer.weapon().detected()) {
                continue;
            }
            final int score = battleWeaponRiskScore(observer);
            if (best == null || score > bestScore || (score == bestScore && observer.healthPercent() < best.healthPercent())) {
                best = observer;
                bestScore = score;
            }
        }
        return best;
    }

    private static BattleCategorySummary[] buildBattleCategorySummaries(List<BattleObserverSnapshot> observers) {
        final DamageObserverSettings.ObserverCategory[] categories = DamageObserverSettings.ObserverCategory.values();
        final BattleCategorySummary[] summaries = new BattleCategorySummary[categories.length];
        for (int i = 0; i < categories.length; ++i) {
            summaries[i] = summarizeBattleCategory(categories[i], observers);
        }
        return summaries;
    }

    private static BattleCategorySummary summarizeBattleCategory(DamageObserverSettings.ObserverCategory category, List<BattleObserverSnapshot> observers) {
        int observerCount = 0;
        int missingBlocks = 0;
        int newBlocks = 0;
        int weaponObservers = 0;
        int readyWeapons = 0;
        double totalHealth = 0.0;
        double cargoFill = 0.0;
        int cargoSamples = 0;
        if (observers != null) {
            for (BattleObserverSnapshot observer : observers) {
                if (observer.category() != category) {
                    continue;
                }
                observerCount++;
                totalHealth += observer.healthPercent();
                missingBlocks += observer.missingBlocks();
                newBlocks += observer.newBlocks();
                if (observer.weapon().detected()) {
                    weaponObservers++;
                    if (observer.weapon().missingBlocks() == 0 && "READY".equals(observer.weapon().readiness())) {
                        readyWeapons++;
                    }
                }
                final float observerCargoFill = computeObserverCargoFillPercent(observer);
                if (observerCargoFill > 0.0f) {
                    cargoFill += observerCargoFill;
                    cargoSamples++;
                }
            }
        }
        return new BattleCategorySummary(
                category,
                observerCount,
                observerCount > 0 ? totalHealth / observerCount : 0.0,
                missingBlocks,
                newBlocks,
                cargoSamples > 0 ? (float)(cargoFill / cargoSamples) : 0.0f,
                weaponObservers,
                readyWeapons);
    }

    private static float computeBattleCargoFillPercent(List<BattleObserverSnapshot> observers) {
        if (observers == null || observers.isEmpty()) {
            return 0.0f;
        }
        double total = 0.0;
        int samples = 0;
        for (BattleObserverSnapshot observer : observers) {
            final float value = computeObserverCargoFillPercent(observer);
            if (value <= 0.0f) {
                continue;
            }
            total += value;
            samples++;
        }
        return samples > 0 ? (float)(total / samples) : 0.0f;
    }

    private static float computeObserverCargoFillPercent(BattleObserverSnapshot observer) {
        if (observer == null) {
            return 0.0f;
        }
        double total = 0.0;
        int samples = 0;
        if (observer.cargoItemCapacity() > 0L) {
            total += Math.max(0.0, Math.min(100.0, observer.cargoItems() * 100.0 / observer.cargoItemCapacity()));
            samples++;
        }
        if (observer.cargoFluidCapacity() > 0L) {
            total += Math.max(0.0, Math.min(100.0, observer.cargoFluidAmount() * 100.0 / observer.cargoFluidCapacity()));
            samples++;
        }
        if (observer.cargoEnergyCapacity() > 0L) {
            total += Math.max(0.0, Math.min(100.0, observer.cargoEnergyStored() * 100.0 / observer.cargoEnergyCapacity()));
            samples++;
        }
        return samples > 0 ? (float)(total / samples) : 0.0f;
    }

    private static int countReadyWeapons(List<BattleObserverSnapshot> observers) {
        int count = 0;
        if (observers == null) {
            return 0;
        }
        for (BattleObserverSnapshot observer : observers) {
            if (!observer.weapon().detected()) {
                continue;
            }
            if (observer.weapon().missingBlocks() == 0 && "READY".equals(observer.weapon().readiness())) {
                count++;
            }
        }
        return count;
    }

    private static int countLoadedWeapons(List<BattleObserverSnapshot> observers) {
        int count = 0;
        if (observers == null) {
            return 0;
        }
        for (BattleObserverSnapshot observer : observers) {
            if (observer.weapon().detected() && "LOADED".equals(observer.weapon().loadedState())) {
                count++;
            }
        }
        return count;
    }

    private static String buildBattleHeaderRight(StatsDisplayPanelBE.BattleScreenLink link, int readyWeaponCount) {
        if (link.getWeaponObserverCount() > 0) {
            return link.getObserverCount() + " OBS / " + readyWeaponCount + " READY";
        }
        return link.getObserverCount() + " OBS";
    }

    private static String battleHeroLine(BattleReadout readout) {
        if (readout == null) {
            return "Awaiting battle observers";
        }
        return battleFocusLine(readout.focusObserver(), readout.weaponObserver());
    }

    private static String battleWeaponsLine(BattleReadout readout) {
        if (readout.link().getWeaponObserverCount() <= 0) {
            return "No weapon channels linked";
        }
        return readout.readyWeaponCount() + "/" + readout.link().getWeaponObserverCount() + " ready • " + readout.loadedWeaponCount() + " loaded";
    }

    private static String battleDamageHeroLine(BattleReadout readout) {
        return readout.link().getTotalMissingBlocks() + " lost • +" + readout.link().getTotalNewBlocks() + " added • " + readout.link().getObserverCount() + " observers";
    }

    private static String battleSystemsLine(BattleReadout readout) {
        final BattleCategorySummary power = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.POWER);
        final BattleCategorySummary control = findBattleCategory(readout, DamageObserverSettings.ObserverCategory.CONTROL);
        return "Cargo " + (readout.cargoFillPercent() > 0.0f ? battlePercentShort(readout.cargoFillPercent()) : "--") + " • Power " + battleCategoryPercent(power) + " • Control " + battleCategoryPercent(control);
    }

    private static String battleFocusLine(BattleObserverSnapshot focusObserver, BattleObserverSnapshot weaponObserver) {
        if (weaponObserver != null && battleWeaponRiskScore(weaponObserver) > 0) {
            return "Weapon alert: " + battleObserverName(weaponObserver);
        }
        if (focusObserver == null) {
            return "Awaiting live observers";
        }
        return "Focus: " + battleObserverName(focusObserver);
    }

    private static String battleFreshnessText(long freshnessTicks) {
        if (freshnessTicks <= 10L) {
            return "Link live";
        }
        if (freshnessTicks <= 80L) {
            return "Fresh " + freshnessTicks + "t";
        }
        return "Stale " + formatDurationCompact((int)(freshnessTicks / 20L));
    }

    private static String battleTrendWindow(StatsDisplayPanelBE.DataChannel trendChannel) {
        if (trendChannel == null || trendChannel.getStore().getSampleCount() < 2) {
            return "";
        }
        final int seconds = Math.max(0, trendChannel.getStore().getSampleCount() - 1) * Math.max(1, trendChannel.getStore().getSampleIntervalTicks()) / 20;
        return formatDurationCompact(seconds);
    }

    private static String battleCategoryDetail(BattleCategorySummary summary) {
        if (summary.observerCount() <= 0) {
            return "No link";
        }
        if (summary.category() == DamageObserverSettings.ObserverCategory.WEAPONS && summary.weaponObservers() > 0) {
            return summary.readyWeapons() + "/" + summary.weaponObservers() + " ready";
        }
        if (summary.category() == DamageObserverSettings.ObserverCategory.CARGO && summary.cargoFillPercent() > 0.0f) {
            return battlePercentShort(summary.cargoFillPercent()) + " hold";
        }
        if (summary.missingBlocks() > 0 || summary.newBlocks() > 0) {
            return "M" + summary.missingBlocks() + " / +" + summary.newBlocks();
        }
        return "Stable";
    }

    private static String battleObserverName(BattleObserverSnapshot observer) {
        if (observer == null) {
            return "";
        }
        final String name = observer.name();
        return (name == null || name.isBlank()) ? observer.category().getDisplayName() : name;
    }

    private static String battleWeaponHeader(BattleObserverSnapshot observer) {
        if (observer == null) {
            return "";
        }
        final BattleWeaponSnapshot weapon = observer.weapon();
        if (weapon.displayName() != null && !weapon.displayName().isBlank()) {
            return weapon.displayName();
        }
        if (weapon.kind() != null && !weapon.kind().isBlank() && !"NONE".equals(weapon.kind())) {
            return weapon.kind();
        }
        return battleObserverName(observer);
    }

    private static String battleDeltaText(BattleObserverSnapshot observer) {
        if (observer == null) {
            return "--";
        }
        if (observer.missingBlocks() <= 0 && observer.newBlocks() <= 0) {
            return "STABLE";
        }
        return "M" + observer.missingBlocks() + "/+" + observer.newBlocks();
    }

    private static String battlePercentShort(double value) {
        return Math.round(value) + "%";
    }

    private static String battleCategoryPercent(BattleCategorySummary summary) {
        return summary != null && summary.observerCount() > 0 ? battlePercentShort(summary.healthPercent()) : "--";
    }

    private static String battleFreshnessShort(long freshnessTicks) {
        if (freshnessTicks <= 5L) {
            return "LIVE";
        }
        if (freshnessTicks <= 40L) {
            return freshnessTicks + "t";
        }
        return formatDurationCompact((int)(freshnessTicks / 20L));
    }

    private static StateInfo describeBattleState(StatsDisplayPanelBE.BattleScreenLink link, long freshnessTicks, int readyWeaponCount, int loadedWeaponCount) {
        if (link.getObserverCount() <= 0) {
            return new StateInfo("NO FEED", "WAIT", ALERT_AMBER);
        }
        if (freshnessTicks > 80L) {
            return new StateInfo("STALE LINK", "STALE", ALERT_AMBER);
        }
        if (link.getOverallHealthPercent() <= 40.0 || link.getTotalMissingBlocks() >= 20) {
            return new StateInfo("CRITICAL", "ALERT", DANGER_RED);
        }
        if (link.getWeaponObserverCount() > 0 && readyWeaponCount <= 0 && loadedWeaponCount <= 0) {
            return new StateInfo("WEAPON OFFLINE", "ARM", DANGER_RED);
        }
        if (link.getTotalMissingBlocks() > 0 || link.getTotalNewBlocks() > 0 || link.getOverallHealthPercent() < 80.0 || readyWeaponCount < link.getWeaponObserverCount()) {
            return new StateInfo("WATCH", "WARN", ALERT_AMBER);
        }
        return new StateInfo("STABLE", "LIVE", SAFE_GREEN);
    }

    private static int battleCategoryColor(BattleCategorySummary summary, DisplayTheme theme) {
        if (summary.observerCount() <= 0) {
            return theme.dim;
        }
        return battleHealthColor(summary.healthPercent(), summary.missingBlocks());
    }

    private static int battleHealthColor(double healthPercent, int missingBlocks) {
        if (healthPercent <= 40.0 || missingBlocks >= 20) {
            return DANGER_RED;
        }
        if (healthPercent <= 75.0 || missingBlocks > 0) {
            return ALERT_AMBER;
        }
        return SAFE_GREEN;
    }

    private static int battleCargoColor(float cargoFillPercent) {
        if (cargoFillPercent <= 0.0f) {
            return themeNeutral();
        }
        if (cargoFillPercent < 25.0f) {
            return DANGER_RED;
        }
        if (cargoFillPercent < 60.0f) {
            return ALERT_AMBER;
        }
        return SAFE_GREEN;
    }

    private static int battleWeaponSummaryColor(BattleReadout readout) {
        if (readout.link().getWeaponObserverCount() <= 0) {
            return themeNeutral();
        }
        if (readout.readyWeaponCount() >= readout.link().getWeaponObserverCount()) {
            return SAFE_GREEN;
        }
        if (readout.readyWeaponCount() > 0 || readout.loadedWeaponCount() > 0) {
            return ALERT_AMBER;
        }
        return DANGER_RED;
    }

    private static int battleWeaponRiskScore(BattleObserverSnapshot observer) {
        if (observer == null || !observer.weapon().detected()) {
            return 0;
        }
        final BattleWeaponSnapshot weapon = observer.weapon();
        int score = weapon.missingBarrelBlocks() * 18 + weapon.missingBreechBlocks() * 22 + weapon.missingMountBlocks() * 18 + weapon.missingLoaderBlocks() * 8;
        score += weapon.missingBlocks() * 5 + observer.missingBlocks() * 3;
        score += switch (weapon.readiness()) {
            case "OFFLINE" -> 36;
            case "DAMAGED" -> 28;
            case "LOADING" -> 8;
            case "UNKNOWN" -> 4;
            default -> 0;
        };
        if ("EMPTY".equals(weapon.loadedState())) {
            score += 3;
        }
        return score;
    }

    private static int battleWeaponColor(BattleWeaponSnapshot weapon) {
        if (weapon == null) {
            return themeNeutral();
        }
        return switch (weapon.readiness()) {
            case "READY" -> SAFE_GREEN;
            case "LOADING", "UNKNOWN" -> ALERT_AMBER;
            case "DAMAGED", "OFFLINE" -> DANGER_RED;
            default -> themeNeutral();
        };
    }

    private static int battleWeaponLoadColor(BattleWeaponSnapshot weapon) {
        if (weapon == null) {
            return themeNeutral();
        }
        return switch (weapon.loadedState()) {
            case "LOADED" -> POWER_ON;
            case "LOADING" -> ALERT_AMBER;
            case "EMPTY", "UNKNOWN" -> themeNeutral();
            default -> POWER_OFF;
        };
    }

    private static int battleAmmoColor(BattleWeaponSnapshot weapon) {
        if (weapon == null) {
            return themeNeutral();
        }
        if (weapon.ammoCreative()) {
            return SAFE_GREEN;
        }
        if (weapon.ammoRounds() > 0 || "LOADED".equals(weapon.loadedState())) {
            return POWER_ON;
        }
        if (weapon.ammoContainers() > 0) {
            return ALERT_AMBER;
        }
        return themeNeutral();
    }

    private static String battleAmmoText(BattleWeaponSnapshot weapon) {
        if (weapon == null) {
            return "--";
        }
        if (weapon.ammoCreative()) {
            return "INF";
        }
        if (weapon.ammoCapacity() > 0) {
            return weapon.ammoRounds() + "/" + weapon.ammoCapacity();
        }
        if ("LOADED".equals(weapon.loadedState())) {
            return "HOT";
        }
        if (weapon.ammoRounds() > 0) {
            return Integer.toString(weapon.ammoRounds());
        }
        return "--";
    }

    private static record BattleCategorySummary(
            DamageObserverSettings.ObserverCategory category,
            int observerCount,
            double healthPercent,
            int missingBlocks,
            int newBlocks,
            float cargoFillPercent,
            int weaponObservers,
            int readyWeapons) {}

        private static record BattleMetricCardSpec(String label, String value, int color) {}

    private static record BattleReadout(
            StatsDisplayPanelBE.BattleScreenLink link,
            BattleObserverSnapshot focusObserver,
            BattleObserverSnapshot weaponObserver,
            StatsDisplayPanelBE.DataChannel healthTrend,
            BattleCategorySummary[] categories,
            StateInfo state,
            String sourceLine,
            String headerRight,
            String statusLine,
            float cargoFillPercent,
            int readyWeaponCount,
            int loadedWeaponCount,
            long freshnessTicks) {}

    private static void renderUnsupportedTexture(NativeImage image, DisplayTheme theme, String message) {
        drawTextureChrome(image, theme);
        final int scale = (image.getWidth() >= 300) ? 2 : 1;
        BitmapFont.drawCentered(image, "SPECIALIZED MODE", 0, image.getHeight() / 2 - BitmapFont.textHeight(scale) - 3, image.getWidth(), theme.text, scale);
        BitmapFont.drawCentered(image, message, 0, image.getHeight() / 2 + 4, image.getWidth(), theme.dim, 1);
    }
    
    private static void drawUnsupportedGui(GuiGraphics g, Font font, int x, int y, int w, int h, DisplayTheme theme, String message) {
        drawGuiChrome(g, x, y, w, h, theme);
        g.drawCenteredString(font, "SPECIALIZED MODE", x + w / 2, y + h / 2 - font.lineHeight - 2, theme.text);
        g.drawCenteredString(font, message, x + w / 2, y + h / 2 + 4, theme.dim);
    }
    
    private static StateInfo describeStressState(float pct, boolean overstressed) {
        if (overstressed || pct >= 100.0f) {
            return new StateInfo("OVERSTRESSED", "CRITICAL", DANGER_RED);
        }
        if (pct >= 85.0f) {
            return new StateInfo("NEAR LIMIT", "LIMIT", DANGER_RED);
        }
        if (pct >= 60.0f) {
            return new StateInfo("HIGH LOAD", "HIGH", ALERT_AMBER);
        }
        return new StateInfo("HEALTHY", "STABLE", SAFE_GREEN);
    }
    
    private static StateInfo describePortableEngineState(float currentBurn, boolean infiniteFuel, boolean superheated, float generatedSpeed) {
        if (superheated) {
            return new StateInfo("SUPERHEATED", "SUPER", DANGER_RED);
        }
        if (infiniteFuel) {
            return new StateInfo("INFINITE", "INF", SAFE_GREEN);
        }
        if (currentBurn > 0.0f || Math.abs(generatedSpeed) > 0.01f) {
            return new StateInfo("BURNING", "LIVE", ALERT_AMBER);
        }
        return new StateInfo("IDLE", "IDLE", themeNeutral());
    }

    private static StateInfo describeDieselEngineState(boolean running, boolean enabled, boolean validFuel, boolean redstoneDisabled, float fuelPercent, float generatedSpeed) {
        if (redstoneDisabled) {
            return new StateInfo("REDSTONE STOP", "STOP", DANGER_RED);
        }
        if (!validFuel || fuelPercent <= 0.0f) {
            return new StateInfo("NO FUEL", "FUEL", DANGER_RED);
        }
        if (fuelPercent < 20.0f) {
            return new StateInfo("LOW FUEL", "LOW", ALERT_AMBER);
        }
        if (running || Math.abs(generatedSpeed) > 0.01f) {
            return new StateInfo("GENERATING", "LIVE", SAFE_GREEN);
        }
        if (enabled) {
            return new StateInfo("ARMED", "READY", ALERT_AMBER);
        }
        return new StateInfo("IDLE", "IDLE", themeNeutral());
    }

    private static StateInfo describeDieselProcessState(String sourceType, boolean processing, float fillPercent, float heatLevel, float oilRemaining, float valvePercent) {
        if ("Oil Pumpjack".equals(sourceType)) {
            if (oilRemaining > 0.0f && processing) {
                return new StateInfo("PUMPING", "FLOW", SAFE_GREEN);
            }
            if (oilRemaining > 0.0f) {
                return new StateInfo("PRIMED", "OIL", ALERT_AMBER);
            }
            return new StateInfo("DRY", "DRY", themeNeutral());
        }
        if ("Diesel Burner".equals(sourceType)) {
            if (heatLevel > 0.0f) {
                return new StateInfo("BURNING", "HEAT", SAFE_GREEN);
            }
            if (fillPercent > 0.0f || valvePercent > 0.0f) {
                return new StateInfo("READY", "FUEL", ALERT_AMBER);
            }
            return new StateInfo("EMPTY", "EMPTY", themeNeutral());
        }
        if (processing) {
            return new StateInfo("PROCESSING", "FLOW", SAFE_GREEN);
        }
        if (fillPercent > 0.0f && heatLevel <= 0.0f && "Diesel Distillation Tower".equals(sourceType)) {
            return new StateInfo("NEEDS HEAT", "HEAT", ALERT_AMBER);
        }
        if (fillPercent > 0.0f) {
            return new StateInfo("STORED", "HOLD", SAFE_GREEN);
        }
        return new StateInfo("EMPTY", "EMPTY", themeNeutral());
    }

    private static StateInfo describeVelocityState(float axisVelocity, float horizontalVelocity, float signedRedstone) {
        final float safeHorizontal = Float.isNaN(horizontalVelocity) ? 0.0f : horizontalVelocity;
        if (safeHorizontal <= 0.01f && Math.abs(axisVelocity) <= 0.01f) {
            return new StateInfo(Math.abs(signedRedstone) > 0.5f ? "HELD" : "STILL", "STILL", themeNeutral());
        }
        if (axisVelocity > 0.05f || signedRedstone > 0.5f) {
            return new StateInfo("FORWARD", "FWD", SAFE_GREEN);
        }
        if (axisVelocity < -0.05f || signedRedstone < -0.5f) {
            return new StateInfo("REVERSE", "REV", ALERT_AMBER);
        }
        return new StateInfo("LATERAL", "LAT", ALERT_AMBER);
    }

    private static StateInfo describeGimbalState(float xAngle, float zAngle) {
        final float maxTilt = Math.max(Math.abs(xAngle), Math.abs(zAngle));
        if (maxTilt >= 45.0f) {
            return new StateInfo("HARD BANK", "BANK", DANGER_RED);
        }
        if (maxTilt >= 20.0f) {
            return new StateInfo("TRACKING", "TRACK", ALERT_AMBER);
        }
        return new StateInfo("LEVEL", "LEVEL", SAFE_GREEN);
    }

    private static StateInfo describeAltitudeState(float airPressure) {
        if (airPressure <= 5.0f) {
            return new StateInfo("VACUUM", "VAC", DANGER_RED);
        }
        if (airPressure <= 35.0f) {
            return new StateInfo("THIN AIR", "THIN", ALERT_AMBER);
        }
        if (airPressure <= 70.0f) {
            return new StateInfo("HIGH ALT", "HIGH", ALERT_AMBER);
        }
        return new StateInfo("STABLE", "OK", SAFE_GREEN);
    }

    private static StateInfo describeDockingState(String stateText, boolean powered, boolean paired, boolean loaded, boolean locked, boolean extended) {
        if ("Locked".equalsIgnoreCase(stateText)) {
            return new StateInfo("LOCKED", "LOCK", SAFE_GREEN);
        }
        if ("Locking".equalsIgnoreCase(stateText)) {
            return new StateInfo("LOCKING", "LOCK", ALERT_AMBER);
        }
        if ("Unpowered".equalsIgnoreCase(stateText) && !powered) {
            return new StateInfo("UNPOWERED", "OFF", POWER_OFF);
        }
        if ("Extended".equalsIgnoreCase(stateText)) {
            return new StateInfo("EXTENDED", "SCAN", ALERT_AMBER);
        }
        if (locked) {
            return new StateInfo("LOCKED", "LOCK", SAFE_GREEN);
        }
        if (paired && loaded) {
            return new StateInfo("DOCKED", "PAIR", SAFE_GREEN);
        }
        if (paired) {
            return new StateInfo("LINKED", "LINK", ALERT_AMBER);
        }
        if (extended) {
            return new StateInfo("SEARCHING", "SCAN", ALERT_AMBER);
        }
        return new StateInfo("RETRACTED", "RET", themeNeutral());
    }

    private static StateInfo describeHotAirBurnerState(boolean powered, boolean canOutput, boolean balloonPresent, float outputPercent, float balloonFillPercent, float balloonTargetPercent, float balloonLift) {
        if (!powered && !balloonPresent) {
            return new StateInfo("IDLE", "IDLE", themeNeutral());
        }
        if (powered && !canOutput) {
            return new StateInfo("BLOCKED", "HOLD", ALERT_AMBER);
        }
        if (canOutput && !balloonPresent) {
            return new StateInfo("VENTING", "FLOW", SAFE_GREEN);
        }
        if (balloonPresent && balloonFillPercent >= Math.max(5.0f, balloonTargetPercent - 2.0f)) {
            return new StateInfo("TRIMMED", "TRIM", SAFE_GREEN);
        }
        if (balloonPresent && (outputPercent >= 60.0f || balloonLift > 0.01f)) {
            return new StateInfo("LIFTING", "LIFT", SAFE_GREEN);
        }
        if (balloonPresent) {
            return new StateInfo("BOUND", "BND", ALERT_AMBER);
        }
        return new StateInfo(powered ? "HEATING" : "STANDBY", powered ? "HEAT" : "STBY", powered ? ALERT_AMBER : themeNeutral());
    }

    private static StateInfo describeContraptionInfoState(float gravity, float support, float verticalMargin, float thrust, float forwardMargin,
                                                          float reverseForce, float magneticForce, float totalMass,
                                                          boolean directionalResolved, boolean forwardResolved) {
        if (totalMass <= 0.01f && Math.max(Math.max(support, gravity), Math.max(Math.max(thrust, reverseForce), magneticForce)) <= 0.01f) {
            return new StateInfo("NO CONTRAPTION", "WAIT", themeNeutral());
        }
        if (!directionalResolved) {
            return new StateInfo("UNRESOLVED", "UNRES", ALERT_AMBER);
        }
        final float verticalThreshold = Math.max(25.0f, gravity * 0.12f);
        final float forwardThreshold = Math.max(25.0f, Math.max(thrust, reverseForce) * 0.15f);
        final boolean ascending = verticalMargin >= verticalThreshold;
        final boolean sinking = verticalMargin <= -verticalThreshold;
        final boolean thrusting = forwardResolved && forwardMargin >= forwardThreshold;
        final boolean braking = forwardResolved && forwardMargin <= -forwardThreshold;
        if (!ascending && !sinking && !thrusting && !braking && magneticForce >= Math.max(50.0f, Math.max(thrust, reverseForce) * 0.8f)) {
            return new StateInfo("MAG LOCK", "LOCK", SAFE_GREEN);
        }
        if (ascending || sinking || thrusting || braking) {
            final String verticalLabel = ascending ? "ASCEND" : (sinking ? "SINK" : "");
            final String verticalShort = ascending ? "ASC" : (sinking ? "SNK" : "");
            final String forwardLabel = thrusting ? "THRUST" : (braking ? "BRAKE" : "");
            final String forwardShort = thrusting ? "THR" : (braking ? "BRK" : "");
            final String label = verticalLabel.isEmpty() ? forwardLabel : (forwardLabel.isEmpty() ? verticalLabel : verticalLabel + " + " + forwardLabel);
            final String shortLabel = verticalShort.isEmpty() ? forwardShort : (forwardShort.isEmpty() ? verticalShort : verticalShort + "+" + forwardShort);
            final int color = sinking ? DANGER_RED : (ascending ? SAFE_GREEN : ALERT_AMBER);
            return new StateInfo(label, shortLabel, color);
        }
        return new StateInfo("TRIMMED", "TRIM", SAFE_GREEN);
    }

    private static StateInfo describeSteeringState(float angle, float targetAngle, float commandAngle, float generatedSpeed, float logicalSpeed, boolean held, boolean inUse) {
        if (held) {
            return new StateInfo("HELD", "HAND", ALERT_AMBER);
        }
        if (inUse) {
            return new StateInfo("RESERVED", "LINK", ALERT_AMBER);
        }
        if (Math.abs(generatedSpeed) > 0.01f || Math.abs(logicalSpeed) > 0.01f) {
            return new StateInfo("TURNING", "LIVE", ALERT_AMBER);
        }
        if (Math.abs(commandAngle - targetAngle) > 0.5f) {
            return new StateInfo("QUEUED", "QUEUE", ALERT_AMBER);
        }
        if (Math.abs(angle - targetAngle) > 1.0f) {
            return new StateInfo("TRACKING", "TRK", ALERT_AMBER);
        }
        if (Math.abs(angle) <= 1.0f && Math.abs(targetAngle) <= 1.0f && Math.abs(commandAngle) <= 1.0f) {
            return new StateInfo("CENTERED", "CTR", SAFE_GREEN);
        }
        return new StateInfo("SET", "SET", SAFE_GREEN);
    }

    private static StateInfo describeLaserState(boolean powered, boolean hitPresent, float currentPower) {
        if (powered && hitPresent && currentPower >= 12.0f) {
            return new StateInfo("STRONG HIT", "LOCK", SAFE_GREEN);
        }
        if (powered && hitPresent) {
            return new StateInfo("TRACKING", "HIT", SAFE_GREEN);
        }
        if (powered) {
            return new StateInfo("POWERED", "LIVE", ALERT_AMBER);
        }
        return new StateInfo("CLEAR", "IDLE", themeNeutral());
    }

    private static StateInfo describeOpticalState(boolean powered, boolean hitPresent) {
        if (powered && hitPresent) {
            return new StateInfo("MATCHED", "LOCK", SAFE_GREEN);
        }
        if (hitPresent) {
            return new StateInfo("SEEING", "VIEW", ALERT_AMBER);
        }
        if (powered) {
            return new StateInfo("POWERED", "ON", ALERT_AMBER);
        }
        return new StateInfo("CLEAR", "CLR", themeNeutral());
    }

    private static StateInfo describeNavigationState(boolean hasTarget, float distanceDelta, float signedAngle, float maxPower) {
        if (!hasTarget) {
            return new StateInfo("NO TARGET", "NONE", themeNeutral());
        }
        if (distanceDelta <= -0.25f) {
            return new StateInfo("CLOSING", "IN", SAFE_GREEN);
        }
        if (distanceDelta >= 0.25f) {
            return new StateInfo("OPENING", "OUT", ALERT_AMBER);
        }
        if (Math.abs(signedAngle) <= 10.0f && maxPower > 0.0f) {
            return new StateInfo("ALIGNED", "SET", SAFE_GREEN);
        }
        return new StateInfo("TRACKING", "NAV", Math.abs(signedAngle) > 90.0f ? ALERT_AMBER : SAFE_GREEN);
    }

    private static StateInfo describeLinkedTypewriterState(boolean powered, boolean hasText) {
        if (powered && hasText) {
            return new StateInfo("LIVE ENTRY", "LIVE", SAFE_GREEN);
        }
        if (powered) {
            return new StateInfo("READY", "ARMED", ALERT_AMBER);
        }
        if (hasText) {
            return new StateInfo("BUFFERED", "TEXT", SAFE_GREEN);
        }
        return new StateInfo("IDLE", "IDLE", themeNeutral());
    }

    private static StateInfo describeAeroFlightDeckState(PortableEngineReadout engine, VelocityReadout velocity, AltitudeReadout altitude, DockingReadout docking,
                                                         SteeringReadout steering, LaserReadout laser, OpticalReadout optical, NavigationReadout navigation,
                                                         HotAirBurnerReadout liftEmitter, ContraptionInfoReadout contraption, int boundSources) {
        if (boundSources <= 0) {
            return new StateInfo("NO SIGNAL", "WAIT", themeNeutral());
        }
        if (engine != null && engine.superheated()) {
            return new StateInfo("OVERHEAT", "HOT", DANGER_RED);
        }
        if (engine != null && !engine.infiniteFuel() && engine.burnPercent() > 0.0f && engine.burnPercent() < 20.0f) {
            return new StateInfo("LOW FUEL", "FUEL", ALERT_AMBER);
        }
        if (docking != null && docking.locked()) {
            return new StateInfo("DOCKED", "LOCK", SAFE_GREEN);
        }
        if (contraption != null && contraption.hasLiveForceData()) {
            final float verticalGuard = Math.max(25.0f, contraption.gravity() * 0.12f);
            if (contraption.verticalMargin() <= -verticalGuard || contraption.verticalMargin() >= verticalGuard) {
                return contraption.state();
            }
        }
        if ((laser != null && laser.hitPresent()) || (optical != null && optical.hitPresent())) {
            return new StateInfo("CONTACT", "PING", ALERT_AMBER);
        }
        if (liftEmitter != null && liftEmitter.powered() && !liftEmitter.canOutput()) {
            return liftEmitter.state();
        }
        if (navigation != null && navigation.hasTarget()) {
            return navigation.state();
        }
        if (steering != null && (steering.held() || steering.inUse())) {
            return steering.state();
        }
        if (velocity != null && (Math.abs(velocity.horizontalVelocity()) > 0.01f || Math.abs(velocity.axisVelocity()) > 0.01f)) {
            return velocity.state();
        }
        if (altitude != null && altitude.airPressure() <= 5.0f) {
            return altitude.state();
        }
        if (contraption != null && contraption.hasLiveForceData()) {
            return contraption.state();
        }
        if (liftEmitter != null) {
            return liftEmitter.state();
        }
        return new StateInfo("READY", "AERO", SAFE_GREEN);
    }

    private static String buildAeroDeckLine(PortableEngineReadout engine, VelocityReadout velocity, AltitudeReadout altitude, DockingReadout docking,
                                            SteeringReadout steering, NavigationReadout navigation, LaserReadout laser, OpticalReadout optical,
                                            HotAirBurnerReadout liftEmitter, ContraptionInfoReadout contraption, int liveSources) {
        if (liveSources <= 0) {
            return "Bind motion, helm, lift, and force sources to light the bridge.";
        }
        if (docking != null && docking.locked()) {
            return "Dock lock confirmed • connector holding position.";
        }
        if (contraption != null && contraption.hasLiveForceData()) {
            final float verticalGuard = Math.max(25.0f, contraption.gravity() * 0.12f);
            if (contraption.verticalMargin() <= -verticalGuard) {
                return "Net vertical " + formatSignedForce(contraption.verticalMargin()) + " • support is losing to gravity.";
            }
            if (contraption.verticalMargin() >= verticalGuard) {
                return "Net vertical " + formatSignedForce(contraption.verticalMargin()) + " • craft is climbing with margin.";
            }
            final float thrustGuard = Math.max(25.0f, contraption.forwardScale() * 0.15f);
            if (contraption.thrustMargin() <= -thrustGuard) {
                return "Braking forces are beating thrust by " + formatSignedForce(contraption.thrustMargin()) + ".";
            }
        }
        if (liftEmitter != null && liftEmitter.powered() && !liftEmitter.canOutput()) {
            return shortGasLabel(liftEmitter.gasTypeText()) + " feed is commanded but blocked • inspect balloon and plumbing.";
        }
        if (navigation != null && navigation.hasTarget()) {
            return "Nav " + navigation.state().shortLabel + " • " + formatCompact(navigation.distanceToTarget()) + " out at " + formatAngle(navigation.signedAngle()) + ".";
        }
        if (laser != null && laser.hitPresent()) {
            return "Laser contact at " + formatCompact(laser.closestHitDistance()) + " • watch the bow.";
        }
        if (optical != null && optical.hitPresent()) {
            return "Optical contact on " + optical.blockName() + " at " + formatCompact(optical.hitDistance()) + ".";
        }
        if (steering != null && (steering.held() || steering.inUse())) {
            return steering.activityLine() + ".";
        }
        if (velocity != null && (Math.abs(velocity.horizontalVelocity()) > 0.01f || Math.abs(velocity.axisVelocity()) > 0.01f)) {
            final float shipSpeed = velocity.horizontalVelocity() > 0.01f ? velocity.horizontalVelocity() : Math.abs(velocity.axisVelocity());
            if (contraption != null && contraption.hasLiveForceData()) {
                return "Cruising " + formatCompact(shipSpeed) + " horizontal • thrust margin " + formatSignedForce(contraption.thrustMargin()) + ".";
            }
            return "Cruising " + formatCompact(shipSpeed) + " horizontal with " + (engine != null ? engine.state().shortLabel : "quiet") + " drive.";
        }
        if (contraption != null && contraption.hasLiveForceData()) {
            return "Force trim vertical " + formatSignedForce(contraption.verticalMargin()) + " • thrust " + formatSignedForce(contraption.thrustMargin()) + ".";
        }
        if (altitude != null) {
            return "Holding " + formatCoordinate(altitude.worldHeight()) + "y • control band " + formatCoordinate(altitude.lowBandHeight()) + ".." + formatCoordinate(altitude.highBandHeight()) + ".";
        }
        if (engine != null) {
            return "Drive line " + engine.state().label + " • " + aeroEngineFuelText(engine, false) + ".";
        }
        if (velocity != null || altitude != null) {
            final String speed = velocity != null ? formatCompact(velocity.horizontalVelocity() > 0.01f ? velocity.horizontalVelocity() : Math.abs(velocity.axisVelocity())) : "--";
            final String altitudeText = altitude != null ? formatCoordinate(altitude.worldHeight()) + "y" : "--";
            return "Cruise steady • " + speed + " horizontal at " + altitudeText + ".";
        }
        return "Flight deck assembled • systems are live.";
    }

    private static String buildAeroDeckSubtitle(GimbalReadout gimbal, HeadingReadout heading, ContraptionInfoReadout contraption, StatsDisplaySettings.VehiclePreset preset, int boundSources, int liveSources) {
        if (boundSources <= 0) {
            return "No aero feeds bound yet";
        }
        if (heading == null || !heading.hasForwardVector()) {
            return liveSources + "/" + boundSources + " live • heading resolver waiting for a feed";
        }
        if (gimbal == null && (contraption == null || !contraption.hasLiveForceData())) {
            return liveSources + "/" + boundSources + " live • " + heading.sourceLabel() + " fallback active";
        }
        if (contraption == null || !contraption.hasLiveForceData()) {
            return liveSources + "/" + boundSources + " live • " + heading.sourceLabel() + " • " + preset.getDisplayName();
        }
        return liveSources + "/" + boundSources + " connected • " + heading.sourceLabel() + " • " + preset.getDisplayName();
    }

    private static String buildAeroFooter(LinkedTypewriterReadout typewriter, HeadingReadout heading, StatsDisplaySettings.VehiclePreset preset, ContraptionInfoReadout contraption, HotAirBurnerReadout liftEmitter,
                                          NavigationReadout navigation, DockingReadout docking, int boundSources, int liveSources) {
        if (typewriter != null && typewriter.hasText() && !typewriter.text().isEmpty()) {
            return "Operator note • " + typewriter.text();
        }
        if (docking != null && (docking.locked() || docking.paired() || docking.extended())) {
            final String dockDetail = docking.locked() ? "LOCKED"
                : (docking.paired() ? (docking.loaded() ? "DOCKED" : "LINKED") : (docking.extended() ? "SEARCH" : docking.state().shortLabel));
            return "Dock " + docking.state().label + " • " + dockDetail;
        }
        if (contraption != null && contraption.hasLiveForceData()) {
            return "Support " + formatSignedForce(contraption.verticalMargin()) + " • Forward " + formatSignedForce(contraption.thrustMargin()) + " • Mass " + formatMass(contraption.totalMass());
        }
        if (liftEmitter != null && (liftEmitter.powered() || liftEmitter.balloonPresent())) {
            return shortGasLabel(liftEmitter.gasTypeText()) + " " + liftEmitter.flowValue() + " • target " + formatCompact(liftEmitter.targetVolume()) + " / cap " + formatCompact(liftEmitter.capacityVolume());
        }
        if (navigation != null && navigation.hasTarget()) {
            return "Navigation " + navigation.state().label + " • range " + formatCompact(navigation.distanceToTarget()) + " • bearing " + formatAngle(navigation.signedAngle());
        }
        if (typewriter != null && typewriter.powered()) {
            return typewriter.statusLine();
        }
        if (boundSources <= 0) {
            return "Bind Simulated aeronautics outputs; missing systems gracefully stay on standby.";
        }
        if (liveSources <= 0) {
            return "Sources are wired, but the deck is still waiting for fresh samples.";
        }
        if (heading != null && heading.hasForwardVector()) {
            return preset.getDisplayName() + " preset • heading via " + heading.sourceLabel();
        }
        return "Shipboard composite is live; absent systems stay parked at --.";
    }

    private static String buildAerolineMode(AeroFlightDeckReadout deck) {
        if (deck.boundSources() <= 0) {
            return "STANDBY";
        }
        if (deck.docking() != null && deck.docking().locked()) {
            return "DOCKED";
        }
        if (deck.docking() != null && (deck.docking().paired() || deck.docking().extended() || deck.docking().powered()) && deck.docking().closestPairDistance() > 0.01f) {
            return deck.docking().closestPairDistance() <= 6.0f ? "DOCK" : "APPROACH";
        }
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            if (deck.navigation().distanceToTarget() <= 24.0f || Math.abs(aeroPlanAngle(deck)) <= 12.0f) {
                return "APPROACH";
            }
            return "INTERCEPT";
        }
        if (deck.velocity() != null && (Math.abs(deck.velocity().axisVelocity()) > 0.05f || Math.abs(deck.velocity().horizontalVelocity()) > 0.05f)) {
            return "CRUISE";
        }
        if (deck.steering() != null && (deck.steering().held() || deck.steering().inUse() || Math.abs(deck.steering().targetAngle()) > 1.0f || Math.abs(deck.steering().angle()) > 1.0f)) {
            return "CRUISE";
        }
        return "HOLD";
    }

    private static StateInfo describeAerolineState(AeroFlightDeckReadout deck) {
        if (deck.boundSources() <= 0) {
            return new StateInfo("STANDBY", "WAIT", themeNeutral());
        }
        if (deck.heading() == null || !deck.heading().hasForwardVector()) {
            return new StateInfo("HEADING OFFLINE", "HDG", ALERT_AMBER);
        }
        if (deck.engine() != null && deck.engine().superheated()) {
            return new StateInfo("OVERHEAT", "HOT", DANGER_RED);
        }
        if (deck.docking() != null && deck.docking().locked()) {
            return deck.docking().state();
        }
        if (deck.liftEmitter() != null && deck.liftEmitter().powered() && !deck.liftEmitter().canOutput()) {
            return deck.liftEmitter().state();
        }
        if (deck.contraption() != null && deck.contraption().hasLiveForceData()) {
            final float verticalGuard = Math.max(25.0f, deck.contraption().gravity() * 0.12f);
            if (deck.contraption().verticalMargin() <= -verticalGuard) {
                return new StateInfo("LOW SUPPORT", "SINK", DANGER_RED);
            }
            final float thrustGuard = Math.max(25.0f, deck.contraption().forwardScale() * 0.15f);
            if (deck.contraption().thrustMargin() <= -thrustGuard) {
                return new StateInfo("DRAGGING", "DRAG", ALERT_AMBER);
            }
        }
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            return deck.navigation().state();
        }
        if (deck.steering() != null && (deck.steering().held() || deck.steering().inUse())) {
            return deck.steering().state();
        }
        return deck.state();
    }

    private static String buildAerolineSubtitle(AeroFlightDeckReadout deck) {
        if (deck.boundSources() <= 0) {
            return "Bind heading plus nav, dock, or helm to arm the director";
        }
        if (deck.heading() == null || !deck.heading().hasForwardVector()) {
            return deck.liveSources() + "/" + deck.boundSources() + " connected • heading feed offline";
        }
        if (deck.gimbal() == null) {
            return deck.liveSources() + "/" + deck.boundSources() + " connected • " + deck.heading().sourceLabel() + " fallback active";
        }
        if (deck.navigation() == null && deck.docking() == null && deck.steering() == null) {
            return deck.liveSources() + "/" + deck.boundSources() + " connected • waiting for guidance authority";
        }
        return deck.liveSources() + "/" + deck.boundSources() + " fused aero feeds • " + deck.effectivePreset().getDisplayName();
    }

    private static String buildAerolineHeadingOrder(AeroFlightDeckReadout deck) {
        if (deck.heading() == null || !deck.heading().hasForwardVector()) {
            return "REACQUIRE HEADING";
        }
        final VehicleBehaviorProfile profile = profileForPreset(deck.effectivePreset());
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            final float absAngle = Math.abs(aeroPlanAngle(deck));
            if (absAngle <= profile.holdAngle()) {
                return "HOLD THE LINE";
            }
            if (absAngle >= profile.hardCueAngle()) {
                return "ROLL HARD INTO CUE";
            }
            if (absAngle >= profile.bankCueAngle()) {
                return "BANK INTO THE NAV CUE";
            }
            return "EASE ONTO THE CUE";
        }
        if (deck.steering() != null) {
            if (deck.steering().held()) {
                return "HELM IN HAND";
            }
            if (deck.steering().inUse()) {
                return "HELM RESERVED";
            }
            if (Math.abs(deck.steering().targetAngle() - deck.steering().angle()) > 2.0f || Math.abs(deck.steering().commandAngle() - deck.steering().targetAngle()) > 1.0f) {
                return "TRACK THE HELM ORDER";
            }
        }
        if (deck.docking() != null && deck.docking().closestPairDistance() > 0.01f) {
            return "KEEP THE FRAME LEVEL";
        }
        return "HOLD ATTITUDE";
    }

    private static String buildAerolineVerticalOrder(AeroFlightDeckReadout deck) {
        if (deck.liftEmitter() != null && deck.liftEmitter().powered() && !deck.liftEmitter().canOutput()) {
            return "CLEAR THE GAS FEED";
        }
        if (deck.contraption() != null && deck.contraption().hasLiveForceData()) {
            final float verticalGuard = Math.max(25.0f, deck.contraption().gravity() * 0.12f);
            if (deck.contraption().verticalMargin() <= -verticalGuard) {
                return "ADD SUPPORT";
            }
            if (deck.contraption().verticalMargin() >= verticalGuard) {
                return "BLEED SUPPORT";
            }
        }
        if (deck.altitude() != null) {
            if (deck.altitude().worldHeight() < deck.altitude().lowBandHeight() - 1.0f) {
                return "CLIMB INTO BAND";
            }
            if (deck.altitude().worldHeight() > deck.altitude().highBandHeight() + 1.0f) {
                return "DESCEND INTO BAND";
            }
        }
        return "HOLD LEVEL";
    }

    private static String buildAerolineDriveOrder(AeroFlightDeckReadout deck) {
        final VehicleBehaviorProfile profile = profileForPreset(deck.effectivePreset());
        if (deck.docking() != null && deck.docking().locked()) {
            return "HOLD LOCK";
        }
        if (deck.docking() != null && deck.docking().closestPairDistance() > 0.01f && (deck.docking().powered() || deck.docking().paired())) {
            if (deck.docking().closestPairDistance() < 4.0f) {
                return "CREEP AHEAD";
            }
            if (deck.docking().closestPairDistance() < 16.0f) {
                return "CLOSE SLOW";
            }
            return "CLOSE THE GAP";
        }
        if (deck.navigation() != null && deck.navigation().hasTarget() && deck.velocity() != null) {
            final float targetDistance = deck.navigation().distanceToTarget();
            final float actualSpeed = aeroActualSpeed(deck);
            final float cautionSpeed = targetDistance <= profile.closeRangeDistance() ? profile.closeRangeSpeed() : Mth.clamp(targetDistance / profile.rangeSpeedDivider(), profile.minCruiseSpeed(), profile.maxCruiseSpeed());
            final float cruiseFloor = targetDistance > 192.0f ? profile.highRangeCruiseFloor() : profile.lowRangeCruiseFloor();
            if (actualSpeed >= cautionSpeed) {
                return "BLEED SPEED";
            }
            if (actualSpeed <= cruiseFloor && targetDistance > 48.0f) {
                return "ADD DRIVE";
            }
            if (actualSpeed > 0.1f) {
                return "STEADY DRIVE";
            }
        }
        if (deck.contraption() != null && deck.contraption().hasLiveForceData()) {
            final float thrustGuard = Math.max(25.0f, deck.contraption().forwardScale() * 0.15f);
            if (deck.contraption().thrustMargin() <= -thrustGuard) {
                return "FEED THRUST";
            }
        }
        if (deck.velocity() != null && (Math.abs(deck.velocity().axisVelocity()) > 0.01f || Math.abs(deck.velocity().horizontalVelocity()) > 0.01f)) {
            return "STEADY DRIVE";
        }
        return "STAY READY";
    }

    private static String buildAerolineOrder(AeroFlightDeckReadout deck, String modeLabel) {
        return switch (modeLabel) {
            case "STANDBY" -> "BIND GUIDANCE FEEDS • ARM THE DIRECTOR";
            case "DOCKED" -> "HOLD LOCK • KEEP THE FRAME STILL";
            case "DOCK" -> buildAerolineDriveOrder(deck) + " • " + buildAerolineVerticalOrder(deck);
            case "APPROACH", "INTERCEPT" -> buildAerolineHeadingOrder(deck) + " • " + buildAerolineDriveOrder(deck);
            case "CRUISE" -> buildAerolineHeadingOrder(deck) + " • " + buildAerolineVerticalOrder(deck);
            default -> buildAerolineVerticalOrder(deck) + " • " + buildAerolineHeadingOrder(deck);
        };
    }

    private static String buildAerolineFooter(AeroFlightDeckReadout deck) {
        if (deck.typewriter() != null && deck.typewriter().hasText() && !deck.typewriter().text().isEmpty()) {
            return "NOTE • " + deck.typewriter().text();
        }
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            return aeroSpeedRateSummary(deck);
        }
        if (deck.docking() != null && deck.steering() != null) {
            return "DOCK " + aeroDockValue(deck) + " • HELM " + aeroHelmValue(deck);
        }
        if (deck.contraption() != null && deck.contraption().hasLiveForceData()) {
            return "SUP " + aeroVerticalValue(deck) + " • DRIVE " + aeroForwardValue(deck);
        }
        if (deck.altitude() != null) {
            return "ALT " + formatCoordinate(deck.altitude().worldHeight()) + "Y • AIR " + formatPercent(deck.altitude().airPressure());
        }
        if (deck.heading() != null && deck.heading().hasForwardVector()) {
            return deck.effectivePreset().getDisplayName() + " • heading via " + deck.heading().sourceLabel();
        }
        return deck.deckLine();
    }

    private static String[][] buildAerolineLegendRows(AeroFlightDeckReadout deck) {
        final String navMeaning = deck.navigation() != null && deck.navigation().hasTarget() ? "NAV nav-table target" : "NAV guidance cue";
        return new String[][] {
                { (deck.heading() != null && deck.heading().fromGimbal()) ? "ATT gimbal attitude" : "HDG velocity/body heading", navMeaning },
                { "DOCK docking connector", "HELM steering order" },
                { "RNG target/contact range", "SPD speed • RATE closure" }
        };
    }

    private static String buildAerolineRangeText(AeroFlightDeckReadout deck) {
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            return "Range " + formatCompact(deck.navigation().distanceToTarget()) + " • " + aeroRangeTrendText(deck);
        }
        final String value = aeroRangeValue(deck);
        if (!"--".equals(value)) {
            return aeroRangeLabel(deck) + " " + value;
        }
        return deck.liveSources() + "/" + deck.boundSources() + " live";
    }

    private static String buildPinnedAeroAltitudeDetail(AeroFlightDeckReadout readout) {
        if (readout.altitude != null) {
            return "Band " + formatCoordinate(readout.altitude.lowBandHeight()) + ".." + formatCoordinate(readout.altitude.highBandHeight()) + "Y";
        }
        return "Waiting on altitude";
    }

    private static String buildPinnedAeroAltitudeValue(AeroFlightDeckReadout readout) {
        final String altitude = aeroAltitudeValue(readout);
        return "--".equals(altitude) ? altitude : altitude + "Y";
    }

    private static String buildPinnedAeroRangeDetail(AeroFlightDeckReadout readout) {
        if (readout.laser != null && readout.laser.hitPresent()) {
            return "Laser contact ahead";
        }
        if (readout.optical != null && readout.optical.hitPresent()) {
            return sentenceCase(truncate(readout.optical.blockName(), 16)) + " in view";
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return "Navigation target";
        }
        if (readout.optical != null) {
            return "Optical scan live";
        }
        return "No target range";
    }

    private static String buildPinnedAeroRangeLabel(AeroFlightDeckReadout readout) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return "Target";
        }
        if ((readout.laser != null && readout.laser.hitPresent()) || (readout.optical != null && readout.optical.hitPresent())) {
            return "Contact";
        }
        return "Range";
    }

    private static String buildPinnedAeroPropulsionValue(AeroFlightDeckReadout readout) {
        if (readout.engine == null) {
            return "No engine feed";
        }
        return aeroEngineFuelText(readout.engine, false);
    }

    private static String aeroEngineFuelText(PortableEngineReadout engine, boolean compact) {
        if (engine == null) {
            return "No engine feed";
        }
        if (engine.infiniteFuel()) {
            return compact ? "INF FUEL" : "INFINITE FUEL";
        }
        final boolean burnTimerBacked = engine.currentBurn() > 0.0f || engine.totalBurn() > 0.0f;
        return formatPercent(engine.burnPercent()) + (burnTimerBacked ? " burn" : " fuel");
    }

    private static String buildPinnedAeroPropulsionDetail(AeroFlightDeckReadout readout) {
        if (readout.engine == null) {
            return sentenceCase(readout.state.label);
        }
        return sentenceCase(readout.engine.state().label) + " • " + MetricType.RPM.formatValue(readout.engine.generatedSpeed());
    }

    private static String buildPinnedAeroGuidanceValue(AeroFlightDeckReadout readout) {
        if (readout.docking != null && readout.docking.locked()) {
            return "Dock locked";
        }
        if (readout.docking != null && readout.docking.closestPairDistance() > 0.01f) {
            return "Dock " + formatCompact(readout.docking.closestPairDistance()) + " away";
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return buildPinnedAeroCourseValue(readout);
        }
        if (readout.steering != null && readout.steering.held()) {
            return "Helm in hand";
        }
        if (readout.steering != null && readout.steering.inUse()) {
            return "Helm reserved";
        }
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return sentenceCase(readout.contraption.state().label);
        }
        return "Systems steady";
    }

    private static String buildPinnedAeroGuidanceDetail(AeroFlightDeckReadout readout) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return "Target " + formatCompact(readout.navigation.distanceToTarget()) + " away";
        }
        if (readout.laser != null && readout.laser.hitPresent()) {
            return "Obstacle " + formatCompact(readout.laser.closestHitDistance()) + " ahead";
        }
        if (readout.optical != null && readout.optical.hitPresent()) {
            return sentenceCase(truncate(readout.optical.blockName(), 16)) + " " + formatCompact(readout.optical.hitDistance()) + " away";
        }
        return sentenceCase(truncate(readout.footerText, 26));
    }

    private static String buildPinnedAeroCourseValue(AeroFlightDeckReadout readout) {
        final float angle = aeroPlanAngle(readout);
        if (Math.abs(angle) <= 5.0f) {
            return "Hold line";
        }
        return (angle > 0.0f ? "Right " : "Left ") + formatDeckAngle(Math.abs(angle));
    }

    private static String buildPinnedAeroSpeedDetail(AeroFlightDeckReadout readout) {
        if (readout.velocity == null) {
            return "Waiting on motion";
        }
        if (readout.engine == null) {
            return "Forward motion";
        }
        return sentenceCase(readout.engine.state().label);
    }

    private static String buildPinnedAerolineStatusLine(AeroFlightDeckReadout deck) {
        if (deck.heading() == null || !deck.heading().hasForwardVector()) {
            return "Heading feed offline";
        }
        if (deck.gimbal() == null) {
            return "Velocity heading fallback active";
        }
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            return "Target " + formatCompact(deck.navigation().distanceToTarget()) + " • " + aeroSpeedRateSummary(deck);
        }
        if (deck.docking() != null && deck.docking().closestPairDistance() > 0.01f) {
            return "Dock " + formatCompact(deck.docking().closestPairDistance()) + " away";
        }
        if (deck.laser() != null && deck.laser().hitPresent()) {
            return "Laser contact " + formatCompact(deck.laser().closestHitDistance()) + " ahead";
        }
        if (deck.optical() != null && deck.optical().hitPresent()) {
            return sentenceCase(truncate(deck.optical().blockName(), 18)) + " " + formatCompact(deck.optical().hitDistance()) + " away";
        }
        if (deck.boundSources() <= 0) {
            return "Bind guidance feeds to arm the director";
        }
        return deck.liveSources() + " of " + deck.boundSources() + " feeds live";
    }

    private static String buildPinnedAerolineCourseDetail(AeroFlightDeckReadout deck) {
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            return "Target " + formatCompact(deck.navigation().distanceToTarget()) + " away";
        }
        if (deck.docking() != null && deck.docking().closestPairDistance() > 0.01f) {
            return "Dock approach";
        }
        return sentenceCase(buildAerolineHeadingOrder(deck));
    }

    private static String buildPinnedAerolineAltitudeValue(AeroFlightDeckReadout deck) {
        final String altitude = aeroAltitudeValue(deck);
        return "--".equals(altitude) ? altitude : altitude + "Y";
    }

    private static String buildPinnedAerolineAltitudeDetail(AeroFlightDeckReadout deck) {
        if (deck.altitude() != null) {
            return "Band " + formatCoordinate(deck.altitude().lowBandHeight()) + ".." + formatCoordinate(deck.altitude().highBandHeight()) + "Y";
        }
        return "Waiting on altitude";
    }

    private static String buildPinnedAerolineClimbValue(AeroFlightDeckReadout deck) {
        final String value = aeroVerticalVelocityValue(deck);
        return "--".equals(value) ? value : value + " b/s";
    }

    private static String buildPinnedAerolineClimbDetail(AeroFlightDeckReadout deck) {
        if (deck.contraption() != null && deck.contraption().hasLiveForceData()) {
            return sentenceCase(buildAerolineVerticalOrder(deck));
        }
        if (deck.altitude() != null) {
            return "Height rate " + aeroHeightRateValue(deck);
        }
        return "No lift data";
    }

    private static String buildPinnedAerolineRangeBanner(AerolineDirectorReadout readout) {
        final AeroFlightDeckReadout deck = readout.deck();
        if (deck.navigation() != null && deck.navigation().hasTarget()) {
            return "Range " + formatCompact(deck.navigation().distanceToTarget()) + " • " + aeroRangeTrendText(deck);
        }
        if (deck.docking() != null && deck.docking().closestPairDistance() > 0.01f) {
            return "Dock " + formatCompact(deck.docking().closestPairDistance());
        }
        if (deck.laser() != null && deck.laser().hitPresent()) {
            return "Contact " + formatCompact(deck.laser().closestHitDistance());
        }
        if (deck.optical() != null && deck.optical().hitPresent()) {
            return "Contact " + formatCompact(deck.optical().hitDistance());
        }
        return buildAerolineRangeText(deck);
    }

    private static String buildPinnedAerolineCueLabel(AeroFlightDeckReadout deck) {
        return sentenceCase(aeroPlanSource(deck));
    }

    private static String buildPinnedAerolineCueValue(AeroFlightDeckReadout deck) {
        final String value = aeroPlanAngleValue(deck);
        return "--".equals(value) ? sentenceCase(buildAerolineHeadingOrder(deck)) : value;
    }

    private static String sentenceCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        final String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String aeroSpeedValue(AeroFlightDeckReadout readout) {
        if (readout.velocity != null) {
            return formatCompact(aeroActualSpeed(readout));
        }
        return "--";
    }

    private static float aeroActualSpeed(AeroFlightDeckReadout readout) {
        if (readout.velocity == null) {
            return 0.0f;
        }
        final float horizontalVelocity = readout.velocity.horizontalVelocity();
        if (!Float.isNaN(horizontalVelocity) && horizontalVelocity > 0.01f) {
            return horizontalVelocity;
        }
        return Math.abs(readout.velocity.axisVelocity());
    }

    private static String aeroSpeedWithUnits(AeroFlightDeckReadout readout) {
        return readout.velocity != null ? formatBlocksPerSecond(aeroActualSpeed(readout)) : "--";
    }

    private static String aeroRangeTrendText(AeroFlightDeckReadout readout) {
        if (readout.navigation == null || !readout.navigation.hasTarget()) {
            return "No range rate";
        }
        final float distanceDelta = readout.navigation.distanceDelta();
        if (Math.abs(distanceDelta) < 0.05f) {
            return "Holding 0 b/s";
        }
        return (distanceDelta < 0.0f ? "Closing " : "Opening ") + formatBlocksPerSecond(Math.abs(distanceDelta));
    }

    private static String aeroSpeedRateSummary(AeroFlightDeckReadout readout) {
        if (readout.velocity != null && readout.navigation != null && readout.navigation.hasTarget()) {
            return "Speed " + aeroSpeedWithUnits(readout) + " • " + aeroRangeTrendText(readout);
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return aeroRangeTrendText(readout);
        }
        if (readout.velocity != null) {
            return "Speed " + aeroSpeedWithUnits(readout);
        }
        return sentenceCase(readout.state().label);
    }

    private static int aeroSpeedColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.velocity != null) {
            return readout.velocity.state().color;
        }
        return theme.dim;
    }

    private static String aeroAltitudeValue(AeroFlightDeckReadout readout) {
        return (readout.altitude != null) ? formatCoordinate(readout.altitude.worldHeight()) : "--";
    }

    private static int aeroAltitudeColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        return (readout.altitude != null) ? readout.altitude.state().color : theme.dim;
    }

    private static String aeroRangeValue(AeroFlightDeckReadout readout) {
        if (readout.laser != null && readout.laser.hitPresent()) {
            return formatCompact(readout.laser.closestHitDistance());
        }
        if (readout.optical != null && readout.optical.hitPresent()) {
            return formatCompact(readout.optical.hitDistance());
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return formatCompact(readout.navigation.distanceToTarget());
        }
        if (readout.optical != null) {
            return formatCompact(readout.optical.rayDistance());
        }
        return "--";
    }

    private static int aeroRangeColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.laser != null && readout.laser.hitPresent()) {
            return readout.laser.state().color;
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return readout.navigation.state().color;
        }
        if (readout.optical != null) {
            return readout.optical.state().color;
        }
        return theme.dim;
    }

    private static String aeroRangeLabel(AeroFlightDeckReadout readout) {
        if (readout.laser != null && readout.laser.hitPresent()) {
            return "PROX";
        }
        if (readout.optical != null && readout.optical.hitPresent()) {
            return "OPTIC";
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return "NAV DIST";
        }
        if (readout.optical != null) {
            return "SCAN";
        }
        return "RANGE";
    }

    private static String aeroDriveDetail(AeroFlightDeckReadout readout) {
        if (readout.engine != null) {
            return readout.engine.state().label + " • " + MetricType.RPM.formatValue(readout.engine.generatedSpeed());
        }
        if (readout.velocity != null) {
            return readout.velocity.state().label + " motion";
        }
        return "No drive telemetry";
    }

    private static String aeroMassValue(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return formatMass(readout.contraption.totalMass());
        }
        return "--";
    }

    private static int aeroMassColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        return (readout.contraption != null && readout.contraption.hasLiveForceData()) ? theme.text : theme.dim;
    }

    private static String aeroLiftValue(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return formatForce(readout.contraption.upwardForce());
        }
        if (readout.liftEmitter != null && readout.liftEmitter.balloonLift() > 0.01f) {
            return formatForce(readout.liftEmitter.balloonLift());
        }
        return "--";
    }

    private static int aeroLiftColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.upwardForce() >= readout.contraption.gravity() ? SAFE_GREEN : ALERT_AMBER;
        }
        if (readout.liftEmitter != null) {
            return readout.liftEmitter.balloonLift() > 0.01f ? SAFE_GREEN : readout.liftEmitter.state().color;
        }
        return theme.dim;
    }

    private static String aeroPitchValue(AeroFlightDeckReadout readout) {
        return (readout.gimbal != null) ? (readout.gimbal.estimatedPitch() ? "~" : "") + formatDeckAngle(readout.gimbal.flightDeckPitch()) : "--";
    }

    private static String aeroBankValue(AeroFlightDeckReadout readout) {
        return (readout.gimbal != null) ? (readout.gimbal.estimatedBank() ? "~" : "") + formatDeckAngle(readout.gimbal.flightDeckBank()) : "--";
    }

    private static float aeroTrimCue(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            final float scale = Math.max(50.0f, readout.contraption.gravity() * 0.18f);
            return Math.max(-1.0f, Math.min(1.0f, readout.contraption.verticalMargin() / scale));
        }
        return 0.0f;
    }

    private static float aeroPlanAngle(AeroFlightDeckReadout readout) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            if (readout.heading != null && readout.heading.hasForwardVector()) {
                final float tx = readout.navigation.targetDx();
                final float tz = readout.navigation.targetDz();
                final float targetLengthSq = tx * tx + tz * tz;
                if (targetLengthSq > 1.0E-4f) {
                    final float invTargetLength = Mth.invSqrt(targetLengthSq);
                    final float nx = tx * invTargetLength;
                    final float nz = tz * invTargetLength;
                    final float fx = readout.heading.forwardX();
                    final float fz = readout.heading.forwardZ();
                    final float cross = fx * nz - fz * nx;
                    final float dot = fx * nx + fz * nz;
                    return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(cross, dot)));
                }
            }
            return readout.navigation.signedAngle();
        }
        if (readout.steering != null) {
            if (Math.abs(readout.steering.targetAngle()) > 0.5f) {
                return readout.steering.targetAngle();
            }
            if (Math.abs(readout.steering.angle()) > 0.5f) {
                return readout.steering.angle();
            }
        }
        return 0.0f;
    }

    private static String aeroPlanAngleValue(AeroFlightDeckReadout readout) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return formatDeckAngle(aeroPlanAngle(readout));
        }
        if (readout.steering != null) {
            return formatDeckAngle(aeroPlanAngle(readout));
        }
        return "--";
    }

    private static String aeroPlanSource(AeroFlightDeckReadout readout) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return "NAV CUE";
        }
        if (readout.steering != null && (readout.steering.held() || readout.steering.inUse())) {
            return "HELM CUE";
        }
        if (readout.steering != null) {
            return "BOW CUE";
        }
        return "PLAN CUE";
    }

    private static int aeroPlanColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return readout.navigation.state().color;
        }
        if (readout.steering != null) {
            return readout.steering.state().color;
        }
        return theme.dim;
    }

    private static AerolineAnimatedFrame captureAerolineFrame(AeroFlightDeckReadout readout) {
        final float pitch = readout.gimbal != null ? readout.gimbal.flightDeckPitch() : 0.0f;
        final float bank = readout.gimbal != null ? readout.gimbal.flightDeckBank() : 0.0f;
        final float trimNorm = aeroTrimCue(readout);
        final float planNorm = Mth.clamp(aeroPlanAngle(readout) / 90.0f, -1.0f, 1.0f);
        return new AerolineAnimatedFrame(pitch, bank, trimNorm, planNorm);
    }

    private static boolean aeroHasTargetLock(AeroFlightDeckReadout readout) {
        return (readout.navigation != null && readout.navigation.hasTarget())
                || (readout.docking != null && (readout.docking.paired() || readout.docking.powered()) && readout.docking.closestPairDistance() > 0.01f)
                || (readout.laser != null && readout.laser.hitPresent())
                || (readout.optical != null && readout.optical.hitPresent());
    }

    private static float aeroTargetDistance(AeroFlightDeckReadout readout) {
        if (readout.laser != null && readout.laser.hitPresent()) {
            return readout.laser.closestHitDistance();
        }
        if (readout.optical != null && readout.optical.hitPresent()) {
            return readout.optical.hitDistance();
        }
        if (readout.docking != null && (readout.docking.paired() || readout.docking.powered()) && readout.docking.closestPairDistance() > 0.01f) {
            return readout.docking.closestPairDistance();
        }
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return readout.navigation.distanceToTarget();
        }
        if (readout.optical != null) {
            return readout.optical.rayDistance();
        }
        return 0.0f;
    }

    private static float aeroAltitudeNorm(AeroFlightDeckReadout readout) {
        return readout.altitude != null ? Mth.clamp(readout.altitude.normalizedHeight() / 100.0f, 0.0f, 1.0f) : 0.0f;
    }

    private static float aeroVerticalVelocity(AeroFlightDeckReadout readout) {
        return readout.velocity != null ? readout.velocity.axisVelocity() : 0.0f;
    }

    private static float aeroHeightRate(AeroFlightDeckReadout readout) {
        return readout.altitude != null ? readout.altitude.heightRate() : 0.0f;
    }

    private static float aeroVelocityScale(AeroFlightDeckReadout readout) {
        final float vertical = Math.abs(aeroVerticalVelocity(readout));
        final float heightRate = Math.abs(aeroHeightRate(readout));
        return Math.max(5.0f, Math.max(vertical, heightRate) * 1.25f);
    }

    private static String aeroVerticalVelocityValue(AeroFlightDeckReadout readout) {
        return readout.velocity != null ? formatSignedCompact(readout.velocity.axisVelocity()) : "--";
    }

    private static String aeroHeightRateValue(AeroFlightDeckReadout readout) {
        return readout.altitude != null ? formatSignedCompact(readout.altitude.heightRate()) : "--";
    }

    private static int aeroVerticalVelocityColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.velocity == null) {
            return theme.dim;
        }
        return readout.velocity.axisVelocity() >= 0.0f ? SAFE_GREEN : ALERT_AMBER;
    }

    private static int aeroHeightRateColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.altitude == null) {
            return theme.dim;
        }
        return readout.altitude.heightRate() >= 0.0f ? SAFE_GREEN : ALERT_AMBER;
    }

    private static String aeroVerticalValue(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return formatSignedForce(readout.contraption.verticalMargin());
        }
        if (readout.liftEmitter != null && readout.liftEmitter.balloonLift() > 0.01f) {
            return formatCompact(readout.liftEmitter.balloonLift());
        }
        if (readout.altitude != null) {
            return formatPercent(readout.altitude.normalizedHeight());
        }
        return "--";
    }

    private static String aeroVerticalTag(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return "NV";
        }
        if (readout.liftEmitter != null && readout.liftEmitter.balloonLift() > 0.01f) {
            return "BLN";
        }
        if (readout.altitude != null) {
            return "ALT";
        }
        return "";
    }

    private static float aeroVerticalNumeric(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.verticalMargin();
        }
        if (readout.liftEmitter != null && readout.liftEmitter.balloonLift() > 0.01f) {
            return readout.liftEmitter.balloonLift();
        }
        if (readout.altitude != null) {
            return readout.altitude.normalizedHeight() - 50.0f;
        }
        return 0.0f;
    }

    private static float aeroVerticalScale(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.verticalScale();
        }
        if (readout.liftEmitter != null && readout.liftEmitter.balloonLift() > 0.01f) {
            return Math.max(25.0f, readout.liftEmitter.balloonLift());
        }
        if (readout.altitude != null) {
            return 50.0f;
        }
        return 1.0f;
    }

    private static int aeroVerticalColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.verticalMargin() >= 0.0f ? SAFE_GREEN : DANGER_RED;
        }
        if (readout.liftEmitter != null) {
            return readout.liftEmitter.state().color;
        }
        if (readout.altitude != null) {
            return readout.altitude.state().color;
        }
        return theme.dim;
    }

    private static String aeroForwardValue(AeroFlightDeckReadout readout) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return formatSignedForce(readout.contraption.thrustMargin());
        }
        return "--";
    }

    private static String aeroForwardTag(AeroFlightDeckReadout readout) {
        return (readout.contraption != null && readout.contraption.hasLiveForceData()) ? readout.contraption.forwardAxisTag() : "";
    }

    private static float aeroForwardNumeric(AeroFlightDeckReadout readout) {
        return (readout.contraption != null && readout.contraption.hasLiveForceData()) ? readout.contraption.thrustMargin() : 0.0f;
    }

    private static float aeroForwardScale(AeroFlightDeckReadout readout) {
        return (readout.contraption != null && readout.contraption.hasLiveForceData()) ? readout.contraption.forwardScale() : 1.0f;
    }

    private static int aeroForwardColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.thrustMargin() >= 0.0f ? SAFE_GREEN : ALERT_AMBER;
        }
        return theme.dim;
    }

    private static String aeroHelmValue(AeroFlightDeckReadout readout) {
        if (readout.steering == null) {
            return "--";
        }
        if (readout.steering.held()) {
            return "HAND " + formatDeckAngle(readout.steering.angle());
        }
        if (readout.steering.inUse()) {
            return readout.steering.state().shortLabel;
        }
        return formatDeckAngle(readout.steering.angle()) + "->" + formatDeckAngle(readout.steering.targetAngle());
    }

    private static int aeroHelmColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        return (readout.steering != null) ? readout.steering.state().color : theme.dim;
    }

    private static String aeroDockValue(AeroFlightDeckReadout readout) {
        if (readout.docking == null) {
            return "--";
        }
        if (readout.docking.locked()) {
            return "LOCKED";
        }
        if ((readout.docking.powered() || readout.docking.paired()) && readout.docking.closestPairDistance() > 0.01f) {
            return readout.docking.state().shortLabel + " " + formatCompact(readout.docking.closestPairDistance());
        }
        if (readout.docking.paired()) {
            return readout.docking.loaded() ? "DOCKED" : "LINKED";
        }
        return readout.docking.extended() ? "SEARCH" : "CLEAR";
    }

    private static String aeroNavValue(AeroFlightDeckReadout readout) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return readout.navigation.state().shortLabel + " " + formatDeckAngle(aeroPlanAngle(readout));
        }
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.state().shortLabel;
        }
        return "--";
    }

    private static int aeroNavColor(AeroFlightDeckReadout readout, DisplayTheme theme) {
        if (readout.navigation != null && readout.navigation.hasTarget()) {
            return readout.navigation.state().color;
        }
        if (readout.contraption != null && readout.contraption.hasLiveForceData()) {
            return readout.contraption.state().color;
        }
        return theme.dim;
    }

    private static String formatDeckAngle(float angle) {
        return Math.round(angle) + "°";
    }

    private static String shortGasLabel(String gasTypeText) {
        if (gasTypeText == null || gasTypeText.isEmpty()) {
            return "GAS";
        }
        final String normalized = gasTypeText.trim().toLowerCase();
        if (normalized.contains("steam")) {
            return "STM";
        }
        if (normalized.contains("hot")) {
            return "HOT";
        }
        if (gasTypeText.length() <= 4) {
            return gasTypeText.toUpperCase();
        }
        return gasTypeText.substring(0, 4).toUpperCase();
    }

    private static StateInfo describeStockpileState(float fill, float lower, float upper, boolean powered) {
        if (fill < lower) {
            return new StateInfo("REFILLING", "LOW", -16724737);
        }
        if (fill > upper) {
            return new StateInfo("READY", "FULL", ALERT_AMBER);
        }
        if (powered) {
            return new StateInfo("OUTPUT ON", "ACTIVE", POWER_ON);
        }
        return new StateInfo("BUFFER ZONE", "BUFFER", SAFE_GREEN);
    }
    
    private static String cycleText(SourceSelection selection) {
        if (selection.cycleCount > 1) {
            return selection.cycleIndex + "/" + selection.cycleCount;
        }
        return selection.sourceType;
    }
    
    private static int trackerSlot(SourceSelection selection, int offset) {
        final int hash = Math.abs(selection.sourceId.hashCode() % 700);
        return 900 + hash + offset;
    }
    
    private static float valueOr(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore().getSampleCount() == 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
    }

    private static String labelOr(StatsDisplayPanelBE.DataChannel channel, String fallback) {
        if (channel == null || channel.getLabel() == null || channel.getLabel().isEmpty()) {
            return fallback;
        }
        return channel.getLabel();
    }

    private static String stripPrefix(String value, String prefix) {
        if (value == null) {
            return "";
        }
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
    
    private static PortableEngineReadout resolvePortableEngineReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel currentBurnCh = findMetric(source, "burn_time", "Current Burn");
        final StatsDisplayPanelBE.DataChannel totalBurnCh = findMetric(source, "burn_total", "Total Burn");
        final StatsDisplayPanelBE.DataChannel burnPctCh = findMetric(source, "burn_pct", "Burn %");
        final StatsDisplayPanelBE.DataChannel infiniteCh = findMetric(source, "fuel_infinite", "Infinite Fuel");
        final StatsDisplayPanelBE.DataChannel superheatedCh = findMetric(source, "superheated", "Superheated");
        final StatsDisplayPanelBE.DataChannel speedCh = findMetric(source, "generated_speed", "Generated Speed");
        if (currentBurnCh == null && totalBurnCh == null && burnPctCh == null && speedCh == null) {
            return null;
        }
        final float currentBurn = valueOr(currentBurnCh, 0.0f);
        final float totalBurn = Math.max(currentBurn, valueOr(totalBurnCh, currentBurn));
        final boolean infiniteFuel = valueOr(infiniteCh, 0.0f) > 0.5f;
        final boolean superheated = valueOr(superheatedCh, 0.0f) > 0.5f;
        final float burnPercent = infiniteFuel ? 100.0f : (burnPctCh != null ? valueOr(burnPctCh, 0.0f) : (totalBurn > 0.0f ? currentBurn * 100.0f / totalBurn : 0.0f));
        final float generatedSpeed = valueOr(speedCh, 0.0f);
        final StateInfo state = describePortableEngineState(currentBurn, infiniteFuel, superheated, generatedSpeed);
        final StatsDisplayPanelBE.DataChannel trendChannel = speedCh != null ? speedCh : (burnPctCh != null ? burnPctCh : currentBurnCh);
        return new PortableEngineReadout(trendChannel != null ? trendChannel : source.channels.get(0), currentBurn, totalBurn, burnPercent, generatedSpeed, infiniteFuel, superheated, state);
    }

    private static DieselEngineReadout resolveDieselEngineReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel fuelPctCh = findMetric(source, "fuel_fill_pct", "Fuel %");
        final StatsDisplayPanelBE.DataChannel burnPctCh = findMetric(source, "burn_pct", "Burn %");
        final StatsDisplayPanelBE.DataChannel fuelAmountCh = findMetric(source, "fuel_amount", "Fuel Amount (mB)");
        final StatsDisplayPanelBE.DataChannel fuelCapacityCh = findMetric(source, "fuel_capacity", "Fuel Capacity (mB)");
        final StatsDisplayPanelBE.DataChannel speedCh = findMetric(source, "generated_speed", "Generated Speed", "Generated RPM");
        final StatsDisplayPanelBE.DataChannel rpmCh = findMetric(source, "generated_rpm", "Generated RPM");
        final StatsDisplayPanelBE.DataChannel stressCh = findMetric(source, "generated_stress_su", "Generated Stress (SU)");
        final StatsDisplayPanelBE.DataChannel burnRateCh = findMetric(source, "fuel_burn_rate", "Burn Rate");
        final StatsDisplayPanelBE.DataChannel remainingCh = findMetric(source, "remaining_burn_ticks", "Remaining Burn", "Current Burn");
        final StatsDisplayPanelBE.DataChannel engineCountCh = findMetric(source, "engine_count", "Engine Blocks");
        final StatsDisplayPanelBE.DataChannel runningCh = findMetric(source, "running", "Running");
        final StatsDisplayPanelBE.DataChannel enabledCh = findMetric(source, "enabled", "Enabled");
        final StatsDisplayPanelBE.DataChannel validFuelCh = findMetric(source, "valid_fuel", "Valid Fuel");
        final StatsDisplayPanelBE.DataChannel redstoneCh = findMetric(source, "redstone_disabled", "Redstone Disabled");
        if (fuelPctCh == null && burnPctCh == null && fuelAmountCh == null && speedCh == null && rpmCh == null && stressCh == null) {
            return null;
        }
        final float fuelAmount = valueOr(fuelAmountCh, 0.0f);
        final float fuelCapacity = valueOr(fuelCapacityCh, fuelAmount);
        final float fuelPercent = fuelPctCh != null ? valueOr(fuelPctCh, 0.0f)
                : (burnPctCh != null ? valueOr(burnPctCh, 0.0f) : (fuelCapacity > 0.0f ? fuelAmount * 100.0f / fuelCapacity : 0.0f));
        final float generatedSpeed = valueOr(speedCh, valueOr(rpmCh, 0.0f));
        final float generatedStress = valueOr(stressCh, 0.0f);
        final float burnRate = valueOr(burnRateCh, 0.0f);
        final float remainingTicks = valueOr(remainingCh, 0.0f);
        final float engineCount = Math.max(1.0f, valueOr(engineCountCh, 1.0f));
        final boolean running = valueOr(runningCh, 0.0f) > 0.5f || generatedSpeed > 0.01f;
        final boolean enabled = enabledCh == null || valueOr(enabledCh, 1.0f) > 0.5f;
        final boolean validFuel = validFuelCh == null || valueOr(validFuelCh, fuelAmount > 0.0f ? 1.0f : 0.0f) > 0.5f;
        final boolean redstoneDisabled = valueOr(redstoneCh, 0.0f) > 0.5f;
        final FuelReserveAggregate reserve = aggregateFuelReserve(source);
        final StateInfo state = describeDieselEngineState(running, enabled, validFuel, redstoneDisabled, fuelPercent, generatedSpeed);
        final StatsDisplayPanelBE.DataChannel trendChannel = speedCh != null ? speedCh : (rpmCh != null ? rpmCh : (fuelPctCh != null ? fuelPctCh : fuelAmountCh));
        return new DieselEngineReadout(trendChannel != null ? trendChannel : source.channels.get(0), fuelAmount, fuelCapacity, fuelPercent,
                reserve.amount(), reserve.capacity(), reserve.fillPercent(), reserve.links(), reserve.mismatch(), reserve.label(),
                generatedSpeed, generatedStress, burnRate, remainingTicks, engineCount, running, enabled, validFuel, redstoneDisabled, state);
    }

    private static FuelReserveAggregate aggregateFuelReserve(SourceSelection source) {
        if (source == null || source.dieselReserveSources == null || source.dieselReserveSources.isEmpty()) {
            return new FuelReserveAggregate(0.0f, 0.0f, 0.0f, 0.0f, false, "Fuel");
        }
        float amount = 0.0f;
        float capacity = 0.0f;
        float links = 0.0f;
        boolean mismatch = false;
        String label = "Fuel";
        boolean mixedLabel = false;
        for (SourceSelection reserveSource : source.dieselReserveSources) {
            final StatsDisplayPanelBE.DataChannel amountCh = findMetric(reserveSource, "reserve_fuel_amount", "Fuel Reserve (mB)");
            final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(reserveSource, "reserve_fuel_capacity", "Reserve Capacity (mB)");
            final StatsDisplayPanelBE.DataChannel mismatchCh = findMetric(reserveSource, "reserve_fuel_mismatch", "Reserve Mismatch");
            final StatsDisplayPanelBE.DataChannel linksCh = findMetric(reserveSource, "reserve_links", "Reserve Links");
            if (amountCh == null && capacityCh == null) {
                continue;
            }
            final float sourceAmount = valueOr(amountCh, 0.0f);
            amount += sourceAmount;
            capacity += Math.max(sourceAmount, valueOr(capacityCh, sourceAmount));
            links += Math.max(1.0f, valueOr(linksCh, 1.0f));
            mismatch |= valueOr(mismatchCh, 0.0f) > 0.5f;
            final String sourceLabel = reserveFuelLabel(amountCh, reserveSource.label);
            if (links <= 1.0f) {
                label = sourceLabel;
            } else if (!label.equals(sourceLabel)) {
                mixedLabel = true;
            }
        }
        if (mixedLabel) {
            label = "Mixed Fuel";
        }
        final float fillPercent = capacity > 0.0f ? Math.max(0.0f, Math.min(100.0f, amount * 100.0f / capacity)) : 0.0f;
        return new FuelReserveAggregate(amount, capacity, fillPercent, links, mismatch, label);
    }

    private static String reserveFuelLabel(StatsDisplayPanelBE.DataChannel amountCh, String fallback) {
        String label = labelOr(amountCh, fallback != null && !fallback.isEmpty() ? fallback : "Fuel Reserve");
        label = label.replace(" Reserve (mB)", "").replace(" (mB)", "").trim();
        return label.isEmpty() ? "Fuel" : label;
    }

    private static PortableEngineReadout chooseEngineReadout(PortableEngineReadout... readouts) {
        PortableEngineReadout best = null;
        float bestScore = -1.0f;
        for (PortableEngineReadout readout : readouts) {
            if (readout == null) {
                continue;
            }
            final float score = Math.abs(readout.generatedSpeed()) * 2.0f + readout.burnPercent() + Math.min(100.0f, readout.currentBurn());
            if (best == null || score > bestScore) {
                best = readout;
                bestScore = score;
            }
        }
        return best;
    }

    private static DieselProcessReadout resolveDieselProcessReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel fillCh = findMetric(source, "fluid_fill_pct", "Fluid %", "Tank Fill %");
        final StatsDisplayPanelBE.DataChannel tankFillCh = findMetric(source, "tank_fill", "Tank Fill %");
        final StatsDisplayPanelBE.DataChannel amountCh = findMetric(source, "fluid_amount", "Fluid Amount (mB)");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(source, "fluid_capacity", "Fluid Capacity (mB)", "Tank Capacity (mB)");
        final StatsDisplayPanelBE.DataChannel processCh = findMetric(source, "process_progress_pct", "Process %");
        final StatsDisplayPanelBE.DataChannel processingCh = findMetric(source, "processing", "Processing");
        final StatsDisplayPanelBE.DataChannel heatCh = findMetric(source, "heat_level", "Heat Level", "Burner Heat");
        final StatsDisplayPanelBE.DataChannel burnerHeatCh = findMetric(source, "burner_heat", "Burner Heat");
        final StatsDisplayPanelBE.DataChannel oilCh = findMetric(source, "oil_remaining_mb", "Oil Remaining (mB)");
        final StatsDisplayPanelBE.DataChannel pumpStartedCh = findMetric(source, "pumpjack_started", "Pumpjack Started");
        final StatsDisplayPanelBE.DataChannel pumpValidCh = findMetric(source, "pumpjack_valid", "Pipe Valid");
        final StatsDisplayPanelBE.DataChannel pipeCh = findMetric(source, "pipe_length", "Pipe Length");
        final StatsDisplayPanelBE.DataChannel valveCh = findMetric(source, "valve_pct", "Valve %");
        final StatsDisplayPanelBE.DataChannel towerWidthCh = findMetric(source, "tower_width", "Tower Width");
        final StatsDisplayPanelBE.DataChannel towerHeightCh = findMetric(source, "tower_height", "Tower Height");
        final StatsDisplayPanelBE.DataChannel blocksCh = findMetric(source, "tower_blocks", "Tower Tanks", "Barrel Blocks");
        final StatsDisplayPanelBE.DataChannel barrelBlocksCh = findMetric(source, "barrel_blocks", "Barrel Blocks");
        if (fillCh == null && tankFillCh == null && amountCh == null && processCh == null && heatCh == null && oilCh == null && valveCh == null) {
            return null;
        }
        final float fluidAmount = valueOr(amountCh, 0.0f);
        final float fluidCapacity = valueOr(capacityCh, fluidAmount);
        final float fillPercent = fillCh != null ? valueOr(fillCh, 0.0f)
                : (tankFillCh != null ? valueOr(tankFillCh, 0.0f) : (fluidCapacity > 0.0f ? fluidAmount * 100.0f / fluidCapacity : 0.0f));
        final float processPercent = valueOr(processCh, 0.0f);
        final String sourceType = source != null ? source.sourceType : "Diesel Plant";
        final float oilRemaining = valueOr(oilCh, 0.0f);
        final boolean pumpjackRunning = "Oil Pumpjack".equals(sourceType)
            && valueOr(pumpStartedCh, 0.0f) > 0.5f
            && valueOr(pumpValidCh, 1.0f) > 0.5f
            && oilRemaining > 0.0f;
        final boolean processing = valueOr(processingCh, 0.0f) > 0.5f || processPercent > 0.0f || pumpjackRunning;
        final float heatLevel = valueOr(heatCh, valueOr(burnerHeatCh, 0.0f));
        final float pipeLength = valueOr(pipeCh, 0.0f);
        final float towerWidth = Math.max(1.0f, valueOr(towerWidthCh, 1.0f));
        final float towerHeight = Math.max(1.0f, valueOr(towerHeightCh, valueOr(blocksCh, 1.0f)));
        final float valvePercent = valueOr(valveCh, 0.0f);
        final float redstoneOutput = 0.0f;
        final float blocks = Math.max(1.0f, valueOr(blocksCh, valueOr(barrelBlocksCh, 1.0f)));
        final StateInfo state = describeDieselProcessState(sourceType, processing, fillPercent, heatLevel, oilRemaining, valvePercent);
        final StatsDisplayPanelBE.DataChannel trendChannel = processCh != null ? processCh : (fillCh != null ? fillCh : (tankFillCh != null ? tankFillCh : (amountCh != null ? amountCh : oilCh)));
        return new DieselProcessReadout(trendChannel != null ? trendChannel : source.channels.get(0), sourceType, fluidAmount, fluidCapacity,
            fillPercent, processPercent, processing, heatLevel, oilRemaining, pipeLength, towerWidth, towerHeight, valvePercent, redstoneOutput, blocks, state);
    }

    private static VelocityReadout resolveVelocityReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel velocityCh = findMetric(source, "adjusted_velocity", "Adjusted Velocity");
        final StatsDisplayPanelBE.DataChannel horizontalCh = findMetric(source, "horizontal_velocity", "Horizontal Velocity");
        final StatsDisplayPanelBE.DataChannel redstoneCh = findMetric(source, "redstone_strength", "Redstone Strength");
        final StatsDisplayPanelBE.DataChannel signedRedstoneCh = findMetric(source, "signed_redstone", "Signed Redstone");
        if (velocityCh == null && horizontalCh == null && redstoneCh == null && signedRedstoneCh == null) {
            return null;
        }
        final float velocity = valueOr(velocityCh, 0.0f);
        final float horizontalVelocity = horizontalCh != null ? valueOr(horizontalCh, 0.0f) : Float.NaN;
        final float redstoneStrength = valueOr(redstoneCh, 0.0f);
        final float signedRedstone = valueOr(signedRedstoneCh, velocity >= 0.0f ? redstoneStrength : -redstoneStrength);
        final StateInfo state = describeVelocityState(velocity, horizontalVelocity, signedRedstone);
        final StatsDisplayPanelBE.DataChannel trendChannel = horizontalCh != null ? horizontalCh : (velocityCh != null ? velocityCh : redstoneCh);
        final StatsDisplayPanelBE.DataChannel headingYawCh = findMetric(source, "heading_yaw", "Heading Yaw");
        final StatsDisplayPanelBE.DataChannel forwardXCh = findMetric(source, "forward_x", "Forward X");
        final StatsDisplayPanelBE.DataChannel forwardZCh = findMetric(source, "forward_z", "Forward Z");
        final StatsDisplayPanelBE.DataChannel headingSourceCh = findMetric(source, "heading_source", "Heading Source");
        final StatsDisplayPanelBE.DataChannel headingConfidenceCh = findMetric(source, "heading_confidence", "Heading Confidence");
        final StatsDisplayPanelBE.DataChannel velocityFallbackCh = findMetric(source, "velocity_fallback", "Velocity Assist Ready");
        return new VelocityReadout(
                trendChannel != null ? trendChannel : source.channels.get(0),
                velocity,
                horizontalVelocity,
                redstoneStrength,
                signedRedstone,
                headingYawCh != null ? valueOr(headingYawCh, 0.0f) : Float.NaN,
                forwardXCh != null ? valueOr(forwardXCh, 0.0f) : Float.NaN,
                forwardZCh != null ? valueOr(forwardZCh, 0.0f) : Float.NaN,
                stripPrefix(labelOr(headingSourceCh, "Heading Source: Unavailable"), "Heading Source: "),
                valueOr(headingConfidenceCh != null ? headingConfidenceCh : headingSourceCh, 0.0f),
                valueOr(velocityFallbackCh, 0.0f) > 0.5f,
                state
        );
    }

    private static GimbalReadout resolveGimbalReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel xCh = findMetric(source, "x_angle", "X Angle");
        final StatsDisplayPanelBE.DataChannel zCh = findMetric(source, "z_angle", "Z Angle");
        if (xCh == null && zCh == null) {
            return null;
        }
        final float xAngle = valueOr(xCh, 0.0f);
        final float zAngle = valueOr(zCh, 0.0f);
        final StatsDisplayPanelBE.DataChannel attitudePitchCh = findMetric(source, "attitude_pitch", "Attitude Pitch");
        final StatsDisplayPanelBE.DataChannel attitudeBankCh = findMetric(source, "attitude_bank", "Attitude Bank");
        final StatsDisplayPanelBE.DataChannel attitudeYawCh = findMetric(source, "attitude_yaw", "Attitude Yaw");
        final StatsDisplayPanelBE.DataChannel forwardXCh = findMetric(source, "forward_x", "Forward X");
        final StatsDisplayPanelBE.DataChannel forwardZCh = findMetric(source, "forward_z", "Forward Z");
        final StatsDisplayPanelBE.DataChannel headingSourceCh = findMetric(source, "heading_source", "Heading Source");
        final StatsDisplayPanelBE.DataChannel headingConfidenceCh = findMetric(source, "heading_confidence", "Heading Confidence");
        return new GimbalReadout(
                xAngle,
                zAngle,
                attitudePitchCh != null ? valueOr(attitudePitchCh, xAngle) : Float.NaN,
                attitudeBankCh != null ? valueOr(attitudeBankCh, zAngle) : Float.NaN,
                attitudeYawCh != null ? valueOr(attitudeYawCh, 0.0f) : Float.NaN,
                forwardXCh != null ? valueOr(forwardXCh, 0.0f) : Float.NaN,
                forwardZCh != null ? valueOr(forwardZCh, 0.0f) : Float.NaN,
                stripPrefix(labelOr(headingSourceCh, "Heading Source: Body +Z"), "Heading Source: "),
                valueOr(headingConfidenceCh != null ? headingConfidenceCh : headingSourceCh, 0.0f),
                valueOr(findMetric(source, "power_down", "Power Down"), 0.0f),
                valueOr(findMetric(source, "power_up", "Power Up"), 0.0f),
                valueOr(findMetric(source, "power_north", "Power North"), 0.0f),
                valueOr(findMetric(source, "power_south", "Power South"), 0.0f),
                valueOr(findMetric(source, "power_west", "Power West"), 0.0f),
                valueOr(findMetric(source, "power_east", "Power East"), 0.0f),
                describeGimbalState(xAngle, zAngle)
        );
    }

    private static AltitudeReadout resolveAltitudeReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel heightCh = findMetric(source, "world_height", "World Height");
        final StatsDisplayPanelBE.DataChannel normalizedCh = findMetric(source, "normalized_height", "Normalized Height");
        final StatsDisplayPanelBE.DataChannel bandMinCh = findMetric(source, "band_min_height", "Band Min Height");
        final StatsDisplayPanelBE.DataChannel bandMaxCh = findMetric(source, "band_max_height", "Band Max Height");
        final StatsDisplayPanelBE.DataChannel pressureCh = findMetric(source, "air_pressure", "Air Pressure");
        final StatsDisplayPanelBE.DataChannel signalCh = findMetric(source, "signal", "Signal");
        if (heightCh == null && normalizedCh == null && bandMinCh == null && bandMaxCh == null && pressureCh == null && signalCh == null) {
            return null;
        }
        final float airPressure = valueOr(pressureCh, 0.0f);
        final float heightRate = heightCh != null && heightCh.getStore().getSampleCount() >= 2 ? heightCh.getStore().getRateOfChange() : 0.0f;
        return new AltitudeReadout(
                valueOr(heightCh, 0.0f),
                valueOr(normalizedCh, 0.0f),
                valueOr(bandMinCh, 0.0f),
                valueOr(bandMaxCh, 0.0f),
                airPressure,
                valueOr(signalCh, 0.0f),
                heightRate,
                describeAltitudeState(airPressure)
        );
    }

    private static DockingReadout resolveDockingReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel pairedCh = findMetric(source, "paired", "Paired");
        final StatsDisplayPanelBE.DataChannel lockedCh = findMetric(source, "locked", "Locked");
        final StatsDisplayPanelBE.DataChannel loadedCh = findMetric(source, "pair_loaded", "Pair Loaded");
        final StatsDisplayPanelBE.DataChannel extendedCh = findMetric(source, "extended", "Extended");
        final StatsDisplayPanelBE.DataChannel poweredCh = findMetric(source, "powered", "Powered");
        final StatsDisplayPanelBE.DataChannel distanceCh = findMetric(source, "closest_pair_distance", "Closest Pair Distance");
        final StatsDisplayPanelBE.DataChannel stateCh = findMetric(source, "connector_state", "State");
        final StatsDisplayPanelBE.DataChannel sublevelCh = findMetric(source, "pair_sublevel", "Sublevel");
        final StatsDisplayPanelBE.DataChannel positionCh = findMetric(source, "pair_position", "Position");
        if (pairedCh == null && lockedCh == null && loadedCh == null && extendedCh == null && poweredCh == null && distanceCh == null && stateCh == null && sublevelCh == null && positionCh == null) {
            return null;
        }
        final boolean paired = valueOr(pairedCh, 0.0f) > 0.5f;
        final boolean loaded = valueOr(loadedCh, 0.0f) > 0.5f;
        final boolean locked = valueOr(lockedCh, 0.0f) > 0.5f;
        final boolean extended = valueOr(extendedCh, 0.0f) > 0.5f;
        final boolean powered = valueOr(poweredCh, 0.0f) > 0.5f;
        final String stateText = stripPrefix(labelOr(stateCh, "State: --"), "State: ");
        return new DockingReadout(
                paired,
                loaded,
                locked,
                extended,
                powered,
                valueOr(distanceCh, 0.0f),
                stateText,
                valueOr(findMetric(source, "pair_x", "Pair X"), 0.0f),
                valueOr(findMetric(source, "pair_y", "Pair Y"), 0.0f),
                valueOr(findMetric(source, "pair_z", "Pair Z"), 0.0f),
                labelOr(sublevelCh, "Sublevel: --"),
                labelOr(positionCh, "Position: --"),
                describeDockingState(stateText, powered, paired, loaded, locked, extended)
        );
    }

    private static HotAirBurnerReadout resolveHotAirBurnerReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel signalCh = findMetric(source, "signal_strength", "Signal Strength");
        final StatsDisplayPanelBE.DataChannel outputCh = findMetric(source, "gas_output", "Gas Output");
        final StatsDisplayPanelBE.DataChannel targetVolumeCh = findMetric(source, "gas_target_volume", "Target Volume");
        final StatsDisplayPanelBE.DataChannel capacityVolumeCh = findMetric(source, "gas_capacity_volume", "Capacity");
        final StatsDisplayPanelBE.DataChannel outputPctCh = findMetric(source, "output_pct", "Output %");
        final StatsDisplayPanelBE.DataChannel poweredCh = findMetric(source, "powered", "Powered");
        final StatsDisplayPanelBE.DataChannel canOutputCh = findMetric(source, "can_output", "Can Output");
        final StatsDisplayPanelBE.DataChannel predictedPctCh = findMetric(source, "predicted_pct", "Predicted Fill %");
        final StatsDisplayPanelBE.DataChannel efficiencyCh = findMetric(source, "emitter_efficiency", "Emitter Efficiency");
        final StatsDisplayPanelBE.DataChannel gasTypeCh = findMetric(source, "gas_type", "Gas");
        final StatsDisplayPanelBE.DataChannel balloonCh = findMetric(source, "balloon_present", "Balloon Attached");
        final StatsDisplayPanelBE.DataChannel fillPctCh = findMetric(source, "balloon_fill_pct", "Balloon Fill %");
        final StatsDisplayPanelBE.DataChannel targetPctCh = findMetric(source, "balloon_target_pct", "Balloon Target %");
        final StatsDisplayPanelBE.DataChannel liftCh = findMetric(source, "balloon_lift", "Balloon Lift");
        if (signalCh == null && outputCh == null && targetVolumeCh == null && capacityVolumeCh == null && outputPctCh == null && poweredCh == null && canOutputCh == null && predictedPctCh == null && efficiencyCh == null && gasTypeCh == null && balloonCh == null && fillPctCh == null && targetPctCh == null && liftCh == null) {
            return null;
        }
        final float signalStrength = valueOr(signalCh, 0.0f);
        final float gasOutput = valueOr(outputCh, 0.0f);
        final float targetVolume = valueOr(targetVolumeCh, 0.0f);
        final float capacityVolume = valueOr(capacityVolumeCh, 0.0f);
        final float outputPercent = valueOr(outputPctCh, 0.0f);
        final boolean powered = valueOr(poweredCh, signalStrength) > 0.5f;
        final boolean canOutput = valueOr(canOutputCh, 0.0f) > 0.5f;
        final boolean balloonPresent = valueOr(balloonCh, 0.0f) > 0.5f;
        final float balloonFillPercent = valueOr(fillPctCh, 0.0f);
        final float balloonTargetPercent = valueOr(targetPctCh, 0.0f);
        final float predictedPercent = valueOr(predictedPctCh, 0.0f);
        final float balloonLift = valueOr(liftCh, 0.0f);
        final float efficiencyPercent = valueOr(efficiencyCh, 0.0f);
        final String gasTypeText = stripPrefix(labelOr(gasTypeCh, "Gas: --"), "Gas: ");
        return new HotAirBurnerReadout(
                gasTypeText,
                signalStrength,
                gasOutput,
                targetVolume,
                capacityVolume,
                powered,
                canOutput,
                balloonPresent,
                balloonFillPercent,
                balloonTargetPercent,
                predictedPercent,
                balloonLift,
                outputPercent,
                efficiencyPercent,
                describeHotAirBurnerState(powered, canOutput, balloonPresent, outputPercent, balloonFillPercent, balloonTargetPercent, balloonLift)
        );
    }

    private static ContraptionInfoReadout resolveContraptionInfoReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel propulsionCh = findMetric(source, "propulsion", "Propulsion Force", "Propulsion (pN)");
        final StatsDisplayPanelBE.DataChannel gravityCh = findMetric(source, "gravity", "Weight Load", "Gravity (pN)");
        final StatsDisplayPanelBE.DataChannel dragCh = findMetric(source, "drag", "Drag Force", "Drag (pN)");
        final StatsDisplayPanelBE.DataChannel levitationCh = findMetric(source, "levitation", "Levitation Force", "Levitation (pN)");
        final StatsDisplayPanelBE.DataChannel balloonLiftCh = findMetric(source, "balloon_lift", "Balloon Buoyancy", "Balloon Lift (pN)");
        final StatsDisplayPanelBE.DataChannel liftCh = findMetric(source, "lift", "Aerodynamic Lift", "Lift (pN)");
        final StatsDisplayPanelBE.DataChannel magneticCh = findMetric(source, "magnetic_force", "Magnetic Force", "Magnetic Force (pN)");
        final StatsDisplayPanelBE.DataChannel otherForceCh = findMetric(source, "other_force", "Other Force");
        final StatsDisplayPanelBE.DataChannel supportCh = findMetric(source, "support", "Support Force");
        final StatsDisplayPanelBE.DataChannel thrustCh = findMetric(source, "forward_thrust", "Forward Thrust");
        final StatsDisplayPanelBE.DataChannel verticalMarginCh = findMetric(source, "vertical_margin", "Net Vertical");
        final StatsDisplayPanelBE.DataChannel forwardMarginCh = findMetric(source, "forward_margin", "Forward Margin");
        final StatsDisplayPanelBE.DataChannel reverseCh = findMetric(source, "reverse_force", "Braking Force");
        final StatsDisplayPanelBE.DataChannel downforceCh = findMetric(source, "downforce", "Downforce");
        final StatsDisplayPanelBE.DataChannel propSupportCh = findMetric(source, "propulsion_support", "Prop Support");
        final StatsDisplayPanelBE.DataChannel levitationSupportCh = findMetric(source, "levitation_support", "Levitation Support");
        final StatsDisplayPanelBE.DataChannel balloonSupportCh = findMetric(source, "balloon_support", "Balloon Support");
        final StatsDisplayPanelBE.DataChannel aeroSupportCh = findMetric(source, "aero_support", "Aero Support");
        final StatsDisplayPanelBE.DataChannel magneticSupportCh = findMetric(source, "magnetic_support", "Magnetic Support");
        final StatsDisplayPanelBE.DataChannel otherSupportCh = findMetric(source, "other_support", "Other Support");
        final StatsDisplayPanelBE.DataChannel axisModeCh = findMetric(source, "axis_mode", "Axis");
        final StatsDisplayPanelBE.DataChannel directionalCoverageCh = findMetric(source, "directional_coverage", "Directional Share");
        final StatsDisplayPanelBE.DataChannel massCh = findMetric(source, "total_mass", "Contraption Mass", "Total Mass (kpg)");
        if (propulsionCh == null && gravityCh == null && dragCh == null && levitationCh == null && balloonLiftCh == null && liftCh == null
                && magneticCh == null && otherForceCh == null && supportCh == null && thrustCh == null && verticalMarginCh == null
                && forwardMarginCh == null && reverseCh == null && downforceCh == null && massCh == null) {
            return null;
        }
        final float propulsion = valueOr(propulsionCh, 0.0f);
        final float gravity = valueOr(gravityCh, 0.0f);
        final float drag = valueOr(dragCh, 0.0f);
        final float levitation = valueOr(levitationCh, 0.0f);
        final float balloonLift = valueOr(balloonLiftCh, 0.0f);
        final float lift = valueOr(liftCh, 0.0f);
        final float magneticForce = valueOr(magneticCh, 0.0f);
        final float otherForce = valueOr(otherForceCh, 0.0f);
        final float support = valueOr(supportCh, levitation + balloonLift + lift);
        final float thrust = valueOr(thrustCh, propulsion);
        final float verticalMargin = valueOr(verticalMarginCh, support - gravity);
        final float forwardMargin = valueOr(forwardMarginCh, thrust - drag);
        final float reverseForce = valueOr(reverseCh, drag);
        final float downforce = valueOr(downforceCh, 0.0f);
        final float propulsionSupport = valueOr(propSupportCh, 0.0f);
        final float levitationSupport = valueOr(levitationSupportCh, levitation);
        final float balloonSupport = valueOr(balloonSupportCh, balloonLift);
        final float aeroSupport = valueOr(aeroSupportCh, lift);
        final float magneticSupport = valueOr(magneticSupportCh, 0.0f);
        final float otherSupport = valueOr(otherSupportCh, 0.0f);
        final float totalMass = valueOr(massCh, 0.0f);
        final float directionalCoverage = valueOr(directionalCoverageCh, 100.0f);
        final String forwardAxisMode = stripPrefix(labelOr(axisModeCh, "Axis: LEGACY"), "Axis: ");
        final float axisConfidence = valueOr(axisModeCh, "LEGACY".equals(forwardAxisMode) ? 0.0f : 100.0f);
        final String headingSource = stripPrefix(labelOr(findMetric(source, "heading_source", "Heading Source"), "Heading Source: " + forwardAxisMode), "Heading Source: ");
        return new ContraptionInfoReadout(
                propulsion,
                gravity,
                drag,
                levitation,
                balloonLift,
                lift,
                magneticForce,
                otherForce,
                support,
                thrust,
                verticalMargin,
                forwardMargin,
                reverseForce,
                downforce,
                propulsionSupport,
                levitationSupport,
                balloonSupport,
                aeroSupport,
                magneticSupport,
                otherSupport,
                totalMass,
                forwardAxisMode,
                axisConfidence,
                headingSource,
                describeContraptionInfoState(gravity, support, verticalMargin, thrust, forwardMargin, reverseForce, magneticForce, totalMass,
                        directionalCoverage >= 99.5f, !"UNRESOLVED".equals(forwardAxisMode))
        );
    }

    private static HotAirBurnerReadout chooseLiftEmitterReadout(HotAirBurnerReadout first, HotAirBurnerReadout second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return liftEmitterScore(second) > liftEmitterScore(first) ? second : first;
    }

    private static float liftEmitterScore(HotAirBurnerReadout readout) {
        if (readout == null) {
            return -1.0f;
        }
        float score = 0.0f;
        if (readout.canOutput()) {
            score += 100.0f;
        }
        if (readout.powered()) {
            score += 25.0f;
        }
        if (readout.balloonPresent()) {
            score += 10.0f;
        }
        score += readout.balloonLift();
        score += readout.gasOutput();
        score += readout.targetVolume() * 0.25f;
        score += readout.capacityVolume() * 0.1f;
        return score;
    }

    private static SteeringReadout resolveSteeringReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel angleCh = findMetric(source, "angle", "Angle");
        final StatsDisplayPanelBE.DataChannel targetCh = findMetric(source, "target_angle", "Target");
        final StatsDisplayPanelBE.DataChannel commandCh = findMetric(source, "pending_target", "Pending Target");
        final StatsDisplayPanelBE.DataChannel generatedCh = findMetric(source, "generated_speed", "Generated Speed");
        final StatsDisplayPanelBE.DataChannel logicalCh = findMetric(source, "logical_speed", "Logical Speed");
        final StatsDisplayPanelBE.DataChannel heldCh = findMetric(source, "held", "Held");
        final StatsDisplayPanelBE.DataChannel inUseCh = findMetric(source, "in_use", "In Use");
        if (angleCh == null && targetCh == null && commandCh == null && generatedCh == null && logicalCh == null && heldCh == null && inUseCh == null) {
            return null;
        }
        final float angle = valueOr(angleCh, 0.0f);
        final float targetAngle = valueOr(targetCh, angle);
        final float commandAngle = valueOr(commandCh, targetAngle);
        final float generatedSpeed = valueOr(generatedCh, 0.0f);
        final float logicalSpeed = valueOr(logicalCh, 0.0f);
        final boolean held = valueOr(heldCh, 0.0f) > 0.5f;
        final boolean inUse = valueOr(inUseCh, 0.0f) > 0.5f;
        return new SteeringReadout(angle, targetAngle, commandAngle, generatedSpeed, logicalSpeed, held, inUse, describeSteeringState(angle, targetAngle, commandAngle, generatedSpeed, logicalSpeed, held, inUse));
    }

    private static LaserReadout resolveLaserReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel powerCh = findMetric(source, "current_power", "Current Power");
        final StatsDisplayPanelBE.DataChannel distanceCh = findMetric(source, "closest_hit_distance", "Closest Hit Distance");
        final StatsDisplayPanelBE.DataChannel hitCh = findMetric(source, "hit_present", "Hit Present");
        final StatsDisplayPanelBE.DataChannel poweredCh = findMetric(source, "powered", "Powered");
        if (powerCh == null && distanceCh == null && hitCh == null && poweredCh == null) {
            return null;
        }
        final float currentPower = valueOr(powerCh, 0.0f);
        final boolean hitPresent = valueOr(hitCh, 0.0f) > 0.5f;
        final boolean powered = valueOr(poweredCh, currentPower) > 0.5f;
        final float closestHitDistance = valueOr(distanceCh, 0.0f);
        return new LaserReadout(currentPower, closestHitDistance, hitPresent, powered, describeLaserState(powered, hitPresent, currentPower));
    }

    private static OpticalReadout resolveOpticalReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel rayCh = findMetric(source, "ray_distance", "Ray Distance");
        final StatsDisplayPanelBE.DataChannel hitDistanceCh = findMetric(source, "hit_block_distance", "Hit Distance");
        final StatsDisplayPanelBE.DataChannel hitCh = findMetric(source, "hit_present", "Hit Present");
        final StatsDisplayPanelBE.DataChannel poweredCh = findMetric(source, "powered", "Powered");
        final StatsDisplayPanelBE.DataChannel blockCh = findMetric(source, "hit_block", "Block");
        if (rayCh == null && hitDistanceCh == null && hitCh == null && poweredCh == null && blockCh == null) {
            return null;
        }
        final boolean hitPresent = valueOr(hitCh, 0.0f) > 0.5f;
        final boolean powered = valueOr(poweredCh, 0.0f) > 0.5f;
        final String blockLabel = labelOr(blockCh, "Block: --");
        return new OpticalReadout(
                valueOr(rayCh, 0.0f),
                valueOr(hitDistanceCh, 0.0f),
                hitPresent,
                powered,
                stripPrefix(blockLabel, "Block: "),
                describeOpticalState(powered, hitPresent)
        );
    }

    private static NavigationReadout resolveNavigationReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel distanceCh = findMetric(source, "distance_to_target", "Distance to Target");
        final StatsDisplayPanelBE.DataChannel lastDistanceCh = findMetric(source, "last_distance_to_target", "Last Distance");
        final StatsDisplayPanelBE.DataChannel deltaCh = findMetric(source, "distance_delta", "Distance Delta");
        final StatsDisplayPanelBE.DataChannel angleCh = findMetric(source, "relative_angle", "Relative Angle");
        if (distanceCh == null && lastDistanceCh == null && deltaCh == null && angleCh == null) {
            return null;
        }
        final float distanceToTarget = valueOr(distanceCh, 0.0f);
        final float lastDistanceToTarget = valueOr(lastDistanceCh, distanceToTarget);
        final float distanceDelta = valueOr(deltaCh, distanceToTarget - lastDistanceToTarget);
        final float relativeAngle = valueOr(angleCh, 0.0f);
        final float signedAngle = relativeAngle > 180.0f ? relativeAngle - 360.0f : relativeAngle;
        final float targetDx = valueOr(findMetric(source, "target_dx", "Target Delta X"), 0.0f);
        final float targetDz = valueOr(findMetric(source, "target_dz", "Target Delta Z"), 0.0f);
        final float powerDown = valueOr(findMetric(source, "power_down", "Power Down"), 0.0f);
        final float powerUp = valueOr(findMetric(source, "power_up", "Power Up"), 0.0f);
        final float powerNorth = valueOr(findMetric(source, "power_north", "Power North"), 0.0f);
        final float powerSouth = valueOr(findMetric(source, "power_south", "Power South"), 0.0f);
        final float powerWest = valueOr(findMetric(source, "power_west", "Power West"), 0.0f);
        final float powerEast = valueOr(findMetric(source, "power_east", "Power East"), 0.0f);
        final float maxPower = Math.max(Math.max(powerDown, powerUp), Math.max(Math.max(powerNorth, powerSouth), Math.max(powerWest, powerEast)));
        final boolean hasTarget = distanceToTarget > 0.01f || lastDistanceToTarget > 0.01f || maxPower > 0.0f;
        return new NavigationReadout(
                distanceToTarget,
                lastDistanceToTarget,
                distanceDelta,
                relativeAngle,
                signedAngle,
                targetDx,
                targetDz,
                powerDown,
                powerUp,
                powerNorth,
                powerSouth,
                powerWest,
                powerEast,
                hasTarget,
                describeNavigationState(hasTarget, distanceDelta, signedAngle, maxPower)
        );
    }

    private static LinkedTypewriterReadout resolveLinkedTypewriterReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel textCh = findMetric(source, "typed_text", "Text");
        final StatsDisplayPanelBE.DataChannel lengthCh = findMetric(source, "typed_length", "Typed Length");
        final StatsDisplayPanelBE.DataChannel keysCh = findMetric(source, "active_keys", "Active Keys");
        final StatsDisplayPanelBE.DataChannel poweredCh = findMetric(source, "powered", "Powered");
        if (textCh == null && lengthCh == null && keysCh == null && poweredCh == null) {
            return null;
        }
        final float typedLength = valueOr(lengthCh, 0.0f);
        final float activeKeys = valueOr(keysCh, 0.0f);
        final boolean hasText = typedLength > 0.5f || valueOr(textCh, 0.0f) > 0.5f;
        final boolean powered = valueOr(poweredCh, 0.0f) > 0.5f;
        final String label = labelOr(textCh, "Text: --");
        final String text = stripPrefix(label, "Text: ").trim();
        return new LinkedTypewriterReadout("--".equals(text) ? "" : text, hasText, powered, typedLength, activeKeys, describeLinkedTypewriterState(powered, hasText));
    }

    private static AeroFlightDeckReadout resolveAeroFlightDeckReadout(SourceSelection source) {
        final PortableEngineReadout engine = chooseEngineReadout(
            resolvePortableEngineReadout(getAeroSource(source, "Portable Engine")),
            resolvePortableEngineReadout(getAeroSource(source, "Huge Diesel Engine")),
            resolvePortableEngineReadout(getAeroSource(source, "Modular Diesel Engine")),
            resolvePortableEngineReadout(getAeroSource(source, "Diesel Engine")),
            resolvePortableEngineReadout(getAeroSource(source, "Diesel Engine Group"))
        );
        final VelocityReadout velocity = resolveVelocityReadout(getAeroSource(source, "Velocity Sensor"));
        final GimbalReadout gimbal = resolveGimbalReadout(getAeroSource(source, "Gimbal Sensor"));
        final AltitudeReadout altitude = resolveAltitudeReadout(getAeroSource(source, "Altitude Sensor"));
        final DockingReadout docking = resolveDockingReadout(getAeroSource(source, "Docking Connector"));
        final SteeringReadout steering = resolveSteeringReadout(getAeroSource(source, "Steering Wheel"));
        final LaserReadout laser = resolveLaserReadout(getAeroSource(source, "Laser Sensor"));
        final OpticalReadout optical = resolveOpticalReadout(getAeroSource(source, "Optical Sensor"));
        final NavigationReadout navigation = resolveNavigationReadout(getAeroSource(source, "Navigation Table"));
        final HotAirBurnerReadout liftEmitter = chooseLiftEmitterReadout(
                resolveHotAirBurnerReadout(getAeroSource(source, "Hot Air Burner")),
                resolveHotAirBurnerReadout(getAeroSource(source, "Steam Vent"))
        );
        final ContraptionInfoReadout contraption = resolveContraptionInfoReadout(getAeroSource(source, "Contraption Info"));
        final LinkedTypewriterReadout typewriter = resolveLinkedTypewriterReadout(getAeroSource(source, "Linked Typewriter"));
        final HeadingReadout heading = resolveHeadingReadout(gimbal, velocity);
        final StatsDisplaySettings.VehiclePreset effectivePreset = resolveEffectiveVehiclePreset(source.settings, engine, velocity, altitude, liftEmitter, steering);
        final int boundSources = Math.max(0, source.aeroSourceCount);
        final int liveSources = Math.max(0, source.aeroLiveSourceCount);
        final StateInfo state = describeAeroFlightDeckState(engine, velocity, altitude, docking, steering, laser, optical, navigation, liftEmitter, contraption, boundSources);
        return new AeroFlightDeckReadout(
                engine,
                velocity,
                gimbal,
                altitude,
                docking,
                steering,
                laser,
                optical,
                navigation,
                liftEmitter,
                contraption,
                typewriter,
                heading,
                effectivePreset,
                boundSources,
                liveSources,
                state,
                buildAeroDeckSubtitle(gimbal, heading, contraption, effectivePreset, boundSources, liveSources),
                buildAeroDeckLine(engine, velocity, altitude, docking, steering, navigation, laser, optical, liftEmitter, contraption, liveSources),
                buildAeroFooter(typewriter, heading, effectivePreset, contraption, liftEmitter, navigation, docking, boundSources, liveSources)
        );
    }

    private static AerolineDirectorReadout resolveAerolineDirectorReadout(SourceSelection source) {
        final AeroFlightDeckReadout deck = resolveAeroFlightDeckReadout(source);
        final String modeLabel = buildAerolineMode(deck);
        return new AerolineDirectorReadout(
                deck,
                modeLabel,
                buildAerolineSubtitle(deck),
                buildAerolineOrder(deck, modeLabel),
                buildAerolineFooter(deck),
                buildAerolineRangeText(deck),
                describeAerolineState(deck)
        );
    }

    public static VehicleSettingsStatus describeVehicleSettings(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels) {
        final SourceSelection source = buildAeroFlightDeckSelection(be, groupBySource(channels));
        final StatsDisplaySettings settings = be.getSettings();
        if (source == null) {
            return new VehicleSettingsStatus(settings.getNoseSetting(), settings.getVehiclePreset(), settings.getVehiclePreset(),
                    "Unavailable", "No aero heading feeds are bound yet.", false, false, "Add a gimbal or velocity sensor to unlock heading fallbacks.", 0.0f, "");
        }
        final AeroFlightDeckReadout deck = resolveAeroFlightDeckReadout(source);
        final HeadingReadout heading = deck.heading();
        final String headingSource = (heading != null && heading.hasForwardVector()) ? heading.sourceLabel() : "Unavailable";
        final String headingDetail = (heading != null && heading.hasForwardVector())
                ? formatCardinalHeading(heading.yaw()) + " • " + deck.effectivePreset().getDisplayName() + " preset"
                : "Waiting for pose, velocity, or cached heading authority.";
        final boolean velocityAssist = (heading != null && heading.velocityAssistReady()) || (deck.velocity() != null && deck.velocity().velocityAssistReady());
        final String velocityDetail = velocityAssist
                ? ((heading != null && VehicleForwardAuthority.ForwardSource.VELOCITY_AXIS.getLabel().equalsIgnoreCase(heading.sourceLabel())) ? "Using velocity-axis fallback; reverse motion will hold heading." : "Velocity assist is ready if pose authority drops.")
                : "Velocity assist unavailable.";
        final float previewYaw = (heading != null && heading.hasForwardVector()) ? heading.yaw() : 0.0f;
        return new VehicleSettingsStatus(settings.getNoseSetting(), settings.getVehiclePreset(), deck.effectivePreset(), headingSource, headingDetail,
                heading != null && heading.hasForwardVector(), velocityAssist, velocityDetail, previewYaw, source.authorityLabel);
    }

    private static HeadingReadout resolveHeadingReadout(GimbalReadout gimbal, VelocityReadout velocity) {
        if (gimbal != null && gimbal.hasForwardVector()) {
            return new HeadingReadout(gimbal.flightDeckYaw(), gimbal.forwardX(), gimbal.forwardZ(), gimbal.headingSource(), gimbal.headingConfidence(), true, velocity != null && velocity.velocityAssistReady());
        }
        if (velocity != null && velocity.hasForwardVector()) {
            return new HeadingReadout(Float.isNaN(velocity.headingYaw()) ? 0.0f : velocity.headingYaw(), velocity.forwardX(), velocity.forwardZ(), velocity.headingSource(), velocity.headingConfidence(), false, velocity.velocityAssistReady());
        }
        return null;
    }

    private static StatsDisplaySettings.VehiclePreset resolveEffectiveVehiclePreset(StatsDisplaySettings settings, PortableEngineReadout engine, VelocityReadout velocity,
                                                                                    AltitudeReadout altitude, HotAirBurnerReadout liftEmitter, SteeringReadout steering) {
        final StatsDisplaySettings.VehiclePreset selected = settings != null ? settings.getVehiclePreset() : StatsDisplaySettings.VehiclePreset.AUTO;
        if (selected != StatsDisplaySettings.VehiclePreset.AUTO) {
            return selected;
        }
        if (liftEmitter != null && (liftEmitter.balloonPresent() || liftEmitter.canOutput() || liftEmitter.gasOutput() > 0.01f)) {
            return StatsDisplaySettings.VehiclePreset.AIRSHIP;
        }
        final float speed = velocity != null && !Float.isNaN(velocity.horizontalVelocity()) ? velocity.horizontalVelocity() : 0.0f;
        if (steering != null && engine != null && speed >= 6.0f) {
            return StatsDisplaySettings.VehiclePreset.PLANE;
        }
        if (altitude != null && speed <= 6.0f) {
            return StatsDisplaySettings.VehiclePreset.HELICOPTER;
        }
        if (engine != null) {
            return StatsDisplaySettings.VehiclePreset.PLANE;
        }
        return StatsDisplaySettings.VehiclePreset.CUSTOM;
    }

    private static VehicleBehaviorProfile profileForPreset(StatsDisplaySettings.VehiclePreset preset) {
        return switch (preset) {
            case PLANE -> new VehicleBehaviorProfile(4.0f, 12.0f, 35.0f, 20.0f, 3.0f, 20.0f, 8.0f, 40.0f, 2.5f, 1.2f);
            case HELICOPTER -> new VehicleBehaviorProfile(6.0f, 18.0f, 48.0f, 18.0f, 1.5f, 32.0f, 3.0f, 18.0f, 0.9f, 0.3f);
            case AIRSHIP -> new VehicleBehaviorProfile(8.0f, 20.0f, 54.0f, 16.0f, 1.0f, 40.0f, 2.0f, 12.0f, 0.5f, 0.2f);
            case HYBRID -> new VehicleBehaviorProfile(5.0f, 15.0f, 42.0f, 20.0f, 2.0f, 26.0f, 4.0f, 24.0f, 1.5f, 0.6f);
            case CUSTOM, AUTO -> new VehicleBehaviorProfile(5.0f, 15.0f, 45.0f, 24.0f, 2.0f, 24.0f, 6.0f, 32.0f, 2.0f, 0.75f);
        };
    }

    private static String formatCardinalHeading(float yaw) {
        final String[] cardinals = { "E", "NE", "N", "NW", "W", "SW", "S", "SE" };
        final int index = Math.floorMod(Math.round(Mth.wrapDegrees(yaw) / 45.0f), cardinals.length);
        return Math.round(Mth.wrapDegrees(yaw)) + "° " + cardinals[index];
    }

    private static StockpileReadout resolveStockpileReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel fillCh = findMetric(source, "fill_pct", "Fill Level %");
        if (fillCh == null) {
            return null;
        }
        final StatsDisplayPanelBE.DataChannel stockCh = findMetric(source, "stock_level", "Stock Level");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(source, "stock_capacity", "Capacity");
        final StatsDisplayPanelBE.DataChannel minCh = findMetric(source, "min_level", "Minimum Level");
        final StatsDisplayPanelBE.DataChannel lowerCh = findMetric(source, "lower_thresh", "Lower Threshold");
        final StatsDisplayPanelBE.DataChannel upperCh = findMetric(source, "upper_thresh", "Upper Threshold");
        final StatsDisplayPanelBE.DataChannel poweredCh = findMetric(source, "powered", "Signal Output");
        final float fill = valueOr(fillCh, 0.0f);
        final float min = valueOr(minCh, 0.0f);
        final float current = valueOr(stockCh, 0.0f);
        final float lower = valueOr(lowerCh, min);
        final float upper = valueOr(upperCh, lower);
        final float capacity = Math.max(Math.max(valueOr(capacityCh, current), current), Math.max(upper, min));
        final float lowerPercent = percentOfRange(lower, min, capacity);
        final float upperPercent = percentOfRange(upper, min, capacity);
        final boolean powered = valueOr(poweredCh, 0.0f) > 0.5f;
        final MetricType amountType = (stockCh != null) ? stockCh.getStore().getMetricType() : ((capacityCh != null) ? capacityCh.getStore().getMetricType() : MetricType.GENERIC);
        final StateInfo state = describeStockpileState(fill, lowerPercent, upperPercent, powered);
        final StatsDisplayPanelBE.DataChannel trendChannel = (fillCh.getStore().getSampleCount() > 0) ? fillCh : stockCh;
        return new StockpileReadout((trendChannel != null) ? trendChannel : fillCh, fill, current, capacity, lower, upper, lowerPercent, upperPercent, powered, amountType, state);
    }

    private static AccumulatorReadout resolveAccumulatorReadout(SourceSelection source) {
        final StatsDisplayPanelBE.DataChannel fillCh = findMetric(source, "energy_fill_pct", "Energy %", "Charge %");
        final StatsDisplayPanelBE.DataChannel storedCh = findMetric(source, "energy_stored", "Stored FE");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(source, "energy_capacity", "Capacity FE");
        if (fillCh == null && storedCh == null) {
            return null;
        }
        final StatsDisplayPanelBE.DataChannel netCh = findMetric(source, "energy_net_rate", "Net FE/t");
        final StatsDisplayPanelBE.DataChannel inputRateCh = findMetric(source, "energy_input_rate", "Input FE/t");
        final StatsDisplayPanelBE.DataChannel outputRateCh = findMetric(source, "energy_output_rate", "Output FE/t");
        final StatsDisplayPanelBE.DataChannel totalInCh = findMetric(source, "energy_total_in", "Total FE In");
        final StatsDisplayPanelBE.DataChannel totalOutCh = findMetric(source, "energy_total_out", "Total FE Out");
        final StatsDisplayPanelBE.DataChannel connectorCh = findMetric(source, "connector_count", "Connectors", "Connections");
        final StatsDisplayPanelBE.DataChannel blocksCh = findMetric(source, "accumulator_size", "Accumulator Blocks");
        final float stored = valueOr(storedCh, 0.0f);
        final float capacity = Math.max(0.0f, valueOr(capacityCh, Math.max(stored, 0.0f)));
        final float fill = fillCh != null ? valueOr(fillCh, capacity > 0.0f ? stored * 100.0f / capacity : 0.0f) : (capacity > 0.0f ? stored * 100.0f / capacity : 0.0f);
        final float netRate = valueOr(netCh, 0.0f);
        float inputRate = valueOr(inputRateCh, Math.max(0.0f, netRate));
        float outputRate = valueOr(outputRateCh, Math.max(0.0f, -netRate));
        final float totalIn = valueOr(totalInCh, 0.0f);
        final float totalOut = valueOr(totalOutCh, 0.0f);
        if (inputRate <= ENERGY_FLOW_EPSILON) {
            inputRate = Math.max(inputRate, rateFromTotalChannel(inputRateCh, totalInCh));
        }
        if (outputRate <= ENERGY_FLOW_EPSILON) {
            outputRate = Math.max(outputRate, rateFromTotalChannel(outputRateCh, totalOutCh));
        }
        if (inputRateCh == null && outputRate > ENERGY_FLOW_EPSILON) {
            inputRate = Math.max(inputRate, netRate + outputRate);
        }
        if (outputRateCh == null && inputRate > ENERGY_FLOW_EPSILON) {
            outputRate = Math.max(outputRate, inputRate - netRate);
        }
        final float connectors = valueOr(connectorCh, 0.0f);
        final float blocks = valueOr(blocksCh, 0.0f);
        final StateInfo state = describeAccumulatorState(fill, netRate, inputRate, outputRate, capacity);
        final StatsDisplayPanelBE.DataChannel trendChannel = fillCh != null ? fillCh : storedCh;
        return new AccumulatorReadout(trendChannel, fill, stored, capacity, netRate, inputRate, outputRate, totalIn, totalOut, connectors, blocks, state);
    }

    private static StateInfo describeAccumulatorState(float fillPercent, float netRate, float inputRate, float outputRate, float capacity) {
        final boolean receiving = inputRate > ENERGY_FLOW_EPSILON || netRate > ENERGY_FLOW_EPSILON;
        final boolean extracting = outputRate > ENERGY_FLOW_EPSILON || netRate < -ENERGY_FLOW_EPSILON;
        if (receiving && extracting) {
            if (outputRate > inputRate + ENERGY_FLOW_EPSILON || netRate < -ENERGY_FLOW_EPSILON) {
                return new StateInfo("DISCHARGING", "DISCH", DANGER_RED);
            }
            if (inputRate > outputRate + ENERGY_FLOW_EPSILON || netRate > ENERGY_FLOW_EPSILON) {
                return new StateInfo("CHARGING", "CHARGE", SAFE_GREEN);
            }
            return new StateInfo("HOLDING FLOW", "HOLD", POWER_ON);
        }
        if (extracting) {
            return new StateInfo("DISCHARGING", "DISCH", DANGER_RED);
        }
        if (receiving) {
            return new StateInfo("CHARGING", "CHARGE", SAFE_GREEN);
        }
        if (capacity <= 0.0f) {
            return new StateInfo("CAPACITY UNKNOWN", "UNKNOWN", themeNeutral());
        }
        if (fillPercent >= 99.5f) {
            return new StateInfo("FULL", "FULL", SAFE_GREEN);
        }
        if (fillPercent <= 10.0f) {
            return new StateInfo("LOW", "LOW", DANGER_RED);
        }
        return new StateInfo("HOLDING", "HOLD", themeNeutral());
    }
    
    private static float percentOfRange(float value, float min, float max) {
        final float range = max - min;
        if (range <= 0.001f) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(100.0f, (value - min) * 100.0f / range));
    }
    
    private static String formatMetricValue(MetricType type, float value) {
        return ((type != null) ? type : MetricType.GENERIC).formatValue(value);
    }

    private static String formatEnergy(float value) {
        return MetricType.ENERGY_FE.formatValue(value);
    }

    private static String formatEnergyRate(float value) {
        return MetricType.ENERGY_FE_PER_TICK.formatValue(value);
    }

    private static float rateFromTotalChannel(StatsDisplayPanelBE.DataChannel rateChannel, StatsDisplayPanelBE.DataChannel totalChannel) {
        if (rateChannel != null && rateChannel.getStore().getSampleCount() > 0) {
            final float latestRate = Math.max(0.0f, rateChannel.getStore().getLatestValue());
            if (latestRate > ENERGY_FLOW_EPSILON) {
                return latestRate;
            }
        }
        if (totalChannel == null || totalChannel.getStore().getSampleCount() < 2) {
            return 0.0f;
        }
        return Math.max(0.0f, totalChannel.getStore().getRateOfChange() / 20.0f);
    }
    
    private static int mapToY(float value, float min, float max, int y, int h) {
        final float norm = (value - min) / Math.max(0.001f, max - min);
        return y + h - 1 - Math.round(Math.max(0.0f, Math.min(1.0f, norm)) * Math.max(1, h - 2));
    }
    
    private static String formatCompact(float value) {
        final float abs = Math.abs(value);
        if (abs >= 1000000.0f) {
            return String.format("%.1fM", value / 1000000.0f);
        }
        if (abs >= 1000.0f) {
            return String.format("%.1fk", value / 1000.0f);
        }
        if (abs >= 100.0f) {
            return String.format("%.0f", value);
        }
        if (abs >= 10.0f) {
            return String.format("%.1f", value);
        }
        return String.format("%.2f", value);
    }
    
    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(1, maxChars - 2)) + "..";
    }

    private static String[] wrapText(String value, int maxChars, int maxLines) {
        if (value == null) {
            return new String[0];
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        final List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : trimmed.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            final String candidate = current.length() == 0 ? word : current + " " + word;
            if (candidate.length() <= maxChars || current.length() == 0) {
                current = new StringBuilder(candidate.length() <= maxChars ? candidate : truncate(word, maxChars));
                continue;
            }
            lines.add(current.toString());
            if (lines.size() >= maxLines) {
                break;
            }
            current = new StringBuilder(word.length() <= maxChars ? word : truncate(word, maxChars));
        }
        if (lines.size() < maxLines && current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.size() > maxLines) {
            return lines.subList(0, maxLines).toArray(String[]::new);
        }
        if (lines.size() == maxLines) {
            lines.set(maxLines - 1, truncate(lines.get(maxLines - 1), maxChars));
        }
        return lines.toArray(String[]::new);
    }
    
    private static String truncateGui(Font font, String value, int maxPixels) {
        if (value == null) {
            return "";
        }
        if (font.width(value) <= maxPixels) {
            return value;
        }
        while (value.length() > 1 && font.width(value + "..") > maxPixels) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "..";
    }
    
    private static void drawLineGui(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        final int drawColor = applyPinnedHudOpacity(color);
        final int dx = Math.abs(x2 - x1);
        final int dy = Math.abs(y2 - y1);
        final int sx = (x1 < x2) ? 1 : -1;
        final int sy = (y1 < y2) ? 1 : -1;
        int err = dx - dy;
        while (true) {
            g.fill(x1, y1, x1 + 1, y1 + 1, drawColor);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            final int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private static void fillCircleGui(GuiGraphics g, int cx, int cy, int radius, int color) {
        final int drawColor = applyPinnedHudOpacity(color);
        final int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            final int dx = (int)Math.sqrt(Math.max(0, r2 - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, drawColor);
        }
    }
    
    private static void borderGui(GuiGraphics g, int x, int y, int w, int h, int color) {
        final int drawColor = applyPinnedHudOpacity(color);
        g.fill(x, y, x + w, y + 1, drawColor);
        g.fill(x, y + h - 1, x + w, y + h, drawColor);
        g.fill(x, y, x + 1, y + h, drawColor);
        g.fill(x + w - 1, y, x + w, y + h, drawColor);
    }
    
    private static int brighten(int color, float factor) {
        return scaleRgb(color, factor);
    }
    
    private static int darken(int color, float factor) {
        return scaleRgb(color, factor);
    }
    
    private static int scaleRgb(int color, float factor) {
        final int a = color >>> 24;
        final int alpha = (a == 0) ? 255 : a;
        final int r = Math.max(0, Math.min(255, Math.round((color >> 16 & 0xFF) * factor)));
        final int g = Math.max(0, Math.min(255, Math.round((color >> 8 & 0xFF) * factor)));
        final int b = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return alpha << 24 | r << 16 | g << 8 | b;
    }
    
    private static final class StateInfo
    {
        final String label;
        final String shortLabel;
        final int color;
        
        StateInfo(String label, String shortLabel, int color) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.color = color;
        }
    }
    
    private static record StockpileReadout(StatsDisplayPanelBE.DataChannel trendChannel, float fillPercent, float currentAmount, float capacityAmount, float lowerAmount, float upperAmount, float lowerPercent, float upperPercent, boolean powered, MetricType amountType, StateInfo state) {}

    private static record AccumulatorReadout(StatsDisplayPanelBE.DataChannel trendChannel, float fillPercent, float stored, float capacity, float netRate, float inputRate, float outputRate, float totalIn, float totalOut, float connectors, float blocks, StateInfo state) {
        boolean isInputActive() {
            return this.inputRate > ENERGY_FLOW_EPSILON || this.netRate > ENERGY_FLOW_EPSILON;
        }

        boolean isOutputActive() {
            return this.outputRate > ENERGY_FLOW_EPSILON || this.netRate < -ENERGY_FLOW_EPSILON;
        }

        boolean isChargingMode() {
            return this.isInputActive() && (!this.isOutputActive() || this.inputRate > this.outputRate + ENERGY_FLOW_EPSILON || this.netRate > ENERGY_FLOW_EPSILON);
        }

        boolean isDischargingMode() {
            return this.isOutputActive() && (!this.isInputActive() || this.outputRate > this.inputRate + ENERGY_FLOW_EPSILON || this.netRate < -ENERGY_FLOW_EPSILON);
        }

        boolean hasEnergyFlow() {
            return this.isInputActive() || this.isOutputActive();
        }

        int flowColor() {
            if (this.isDischargingMode()) {
                return DANGER_RED;
            }
            if (this.isChargingMode()) {
                return SAFE_GREEN;
            }
            if (this.hasEnergyFlow()) {
                return POWER_ON;
            }
            return this.state.color;
        }
    }
    
    private static final class SourceSelection
    {
        String sourceId;
        String sourceType;
        String label;
        BlockPos sourcePos;
        boolean live;
        String authorityLabel;
        StatsDisplaySettings settings;
        List<StatsDisplayPanelBE.DataChannel> channels;
        List<SourceSelection> dieselReserveSources = new ArrayList<SourceSelection>();
        LinkedHashMap<String, SourceSelection> aeroSourcesByType = new LinkedHashMap<String, SourceSelection>();
        int aeroSourceCount;
        int aeroLiveSourceCount;
        int cycleIndex;
        int cycleCount;
    }
}
