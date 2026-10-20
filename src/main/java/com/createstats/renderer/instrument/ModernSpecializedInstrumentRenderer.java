package com.createstats.renderer.instrument;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.display.DisplayTheme;
import com.createstats.display.InstrumentDensityPreset;
import com.createstats.display.InstrumentEffectPreset;
import com.createstats.display.InstrumentStyleFamily;
import com.createstats.display.InstrumentStyleOptionKeys;
import com.createstats.display.InstrumentStyleOptions;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.renderer.AnalogGaugeRenderer;
import com.createstats.renderer.BitmapFont;
import com.createstats.renderer.BrassTrackerRenderer;
import com.createstats.renderer.TextureGraphRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ModernSpecializedInstrumentRenderer
{
    private ModernSpecializedInstrumentRenderer() {
    }

    public static boolean renderPanel(NativeImage image, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        final SourceSelection source = selectSource(be, channels, preferredSourceId, renderTick);
        if (source == null) {
            return false;
        }
        final StatsDisplaySettings settings = be.getSettings();
        final DisplayTheme theme = settings.getDisplayTheme();
        final SpecializedFlags flags = resolveFlags(settings, image.getWidth() >= 640 || image.getHeight() >= 420);
        InstrumentRenderPrimitives.drawFamilyFrame(image, 0, 0, image.getWidth(), image.getHeight(), theme, settings.getInstrumentStyleFamily(), settings.getInstrumentEffectPreset(), settings.getInstrumentStyleOptions());
        return switch (source.sourceType) {
            case "Stressometer" -> {
                renderStressPanel(image, be, source, theme, settings, flags, renderTick);
                yield true;
            }
            case "Stockpile Switch" -> {
                renderStockpilePanel(image, be, source, theme, settings, flags, renderTick);
                yield true;
            }
            default -> {
                renderGenericPanel(image, be, source, theme, settings, flags, renderTick);
                yield true;
            }
        };
    }

    private static void renderStressPanel(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick) {
        final StatsDisplayPanelBE.DataChannel stressPctCh = findMetric(source, "stress_pct", "Stress %");
        final StatsDisplayPanelBE.DataChannel stressSuCh = findMetric(source, "stress_su", "Current Stress (SU)");
        final StatsDisplayPanelBE.DataChannel capacityCh = findMetric(source, "capacity_su", "Capacity (SU)");
        final StatsDisplayPanelBE.DataChannel remainingCh = findMetric(source, "remaining_su", "Remaining (SU)");
        final StatsDisplayPanelBE.DataChannel rpmCh = findMetric(source, "rpm", "RPM");
        final StatsDisplayPanelBE.DataChannel overstressedCh = findMetric(source, "overstressed", "Overstressed");
        if (stressPctCh == null && (stressSuCh == null || capacityCh == null)) {
            InstrumentRenderPrimitives.drawCenteredMessage(image, 0, 0, image.getWidth(), image.getHeight(), "KINETIC LOAD", "Waiting for stress data", theme);
            return;
        }
        final float used = valueOr(stressSuCh, 0.0f);
        final float capacity = Math.max(1.0f, valueOr(capacityCh, Math.max(used, 1.0f)));
        final float pct = (stressPctCh != null) ? valueOr(stressPctCh, 0.0f) : (used / capacity * 100.0f);
        final float remaining = (remainingCh != null) ? valueOr(remainingCh, Math.max(0.0f, capacity - used)) : Math.max(0.0f, capacity - used);
        final float rpm = valueOr(rpmCh, 0.0f);
        final boolean overstressed = valueOr(overstressedCh, 0.0f) > 0.5f;
        final StateInfo state = describeStressState(pct, overstressed);
        switch (settings.getInstrumentStyleFamily()) {
            case MECHANICAL_BRASS: {
                renderStressMechanical(image, be, source, theme, settings, flags, tick, stressPctCh, pct, used, capacity, remaining, rpm, state, overstressed);
                break;
            }
            case SCIENTIFIC_INSTRUMENT: {
                renderStressScientific(image, be, source, theme, settings, flags, tick, stressPctCh, pct, used, capacity, remaining, rpm, state, overstressed);
                break;
            }
            case CONTROL_PANEL_WALL: {
                renderStressControl(image, be, source, theme, settings, flags, tick, pct, used, capacity, remaining, rpm, state, overstressed);
                break;
            }
            default: {
                renderStressLocomotive(image, be, source, theme, settings, flags, tick, stressPctCh, pct, used, capacity, remaining, rpm, state, overstressed);
                break;
            }
        }
    }

    private static void renderStockpilePanel(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick) {
        final StockpileReadout readout = resolveStockpileReadout(source);
        if (readout == null) {
            InstrumentRenderPrimitives.drawCenteredMessage(image, 0, 0, image.getWidth(), image.getHeight(), "THRESHOLD CONTROL", "Waiting for stock data", theme);
            return;
        }
        switch (settings.getInstrumentStyleFamily()) {
            case MECHANICAL_BRASS: {
                renderStockpileMechanical(image, be, source, theme, settings, flags, tick, readout);
                break;
            }
            case SCIENTIFIC_INSTRUMENT: {
                renderStockpileScientific(image, be, source, theme, settings, flags, tick, readout);
                break;
            }
            case CONTROL_PANEL_WALL: {
                renderStockpileControl(image, be, source, theme, settings, flags, tick, readout);
                break;
            }
            default: {
                renderStockpileLocomotive(image, be, source, theme, settings, flags, tick, readout);
                break;
            }
        }
    }

    private static void renderStressLocomotive(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StatsDisplayPanelBE.DataChannel heroChannel, float pct, float used, float capacity, float remaining, float rpm, StateInfo state, boolean overstressed) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        drawHeader(image, "KINETIC LOAD", source, theme, state, pad, "CAB");
        final int rightW = Math.max(112, w / 3);
        final int heroW = Math.max(80, w - rightW - pad * 3);
        final int bodyY = yAfterHeader();
        final int bottomH = Math.max(60, h / 3);
        final int bodyH = Math.max(48, h - bodyY - bottomH - 6);
        renderSpecializedGauge(image, be, heroChannel, 0, pad + heroW / 2, bodyY + bodyH / 2, Math.max(30, Math.min(heroW / 2 - 8, bodyH / 2 - 8)), theme, settings, flags);
        final int sideX = pad * 2 + heroW;
        InstrumentRenderPrimitives.drawValuePlate(image, sideX, bodyY + 2, rightW, Math.max(34, bodyH / 3), "LOAD", String.format("%.1f%%", pct), theme, state.color, true);
        InstrumentRenderPrimitives.drawBadge(image, sideX, bodyY + Math.max(34, bodyH / 3) + 6, Math.max(54, rightW / 2 - 2), 12, state.shortLabel, state.color);
        InstrumentRenderPrimitives.drawMetricCard(image, sideX + Math.max(54, rightW / 2 - 2) + 4, bodyY + Math.max(34, bodyH / 3) + 4, Math.max(24, rightW - Math.max(54, rightW / 2 - 2) - 4), 16, "RPM", InstrumentRenderPrimitives.formatCompact(rpm), theme, theme.accent, false);
        final int cardsY = bodyY + Math.max(34, bodyH / 3) + 24;
        final int cardsH = Math.max(18, bodyH - Math.max(34, bodyH / 3) - 26);
        drawMetricTriplet(image, sideX, cardsY, rightW, cardsH, theme, "USED", InstrumentRenderPrimitives.formatCompact(used), state.color, "CAP", InstrumentRenderPrimitives.formatCompact(capacity), theme.accent, "HEAD", InstrumentRenderPrimitives.formatCompact(remaining), InstrumentRenderPrimitives.SAFE_GREEN);
        final int trackerY = h - bottomH + 4;
        BrassTrackerRenderer.drawHorizontalTexture(image, pad, trackerY + 10, w - pad * 2, Math.max(18, bottomH / 5), be.getBlockPos(), trackerSlot(source, 0), pct / 100.0f, 0.0f, 0.6f, InstrumentRenderPrimitives.SAFE_GREEN, 0.6f, InstrumentRenderPrimitives.ALERT_AMBER, 0.85f, InstrumentRenderPrimitives.DANGER_RED, state.color, overstressed, tick);
        BitmapFont.drawCentered(image, InstrumentRenderPrimitives.formatCompact(used) + " / " + InstrumentRenderPrimitives.formatCompact(capacity) + " SU", pad, trackerY, w - pad * 2, theme.text, 1);
        if (settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE && flags.showSparkline) {
            InstrumentRenderPrimitives.drawSparkline(image, pad, trackerY + Math.max(24, bottomH / 5) + 16, w - pad * 2, Math.max(30, bottomH - Math.max(24, bottomH / 5) - 20), (heroChannel != null) ? heroChannel.getStore() : source.channels.get(0).getStore(), theme, state.color, "Load trend");
        }
        else {
            drawMetricTriplet(image, pad, trackerY + Math.max(24, bottomH / 5) + 14, w - pad * 2, Math.max(22, bottomH - Math.max(24, bottomH / 5) - 18), theme, "RPM", InstrumentRenderPrimitives.formatCompact(rpm), theme.accent, "HEAD", InstrumentRenderPrimitives.formatCompact(remaining), InstrumentRenderPrimitives.SAFE_GREEN, "STATE", state.shortLabel, state.color);
        }
        if (overstressed && tick / 4L % 2L == 0L) {
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, state.color);
        }
    }

    private static void renderStressMechanical(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StatsDisplayPanelBE.DataChannel heroChannel, float pct, float used, float capacity, float remaining, float rpm, StateInfo state, boolean overstressed) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 30);
        drawHeader(image, "KINETIC LOAD", source, theme, state, pad, "BRASS");
        final int topH = Math.max(56, h / 2);
        renderSpecializedGauge(image, be, heroChannel, 0, w / 2, yAfterHeader() + topH / 2 - 8, Math.max(32, Math.min(w / 4, topH / 2 - 12)), theme, settings, flags);
        BrassTrackerRenderer.drawHorizontalTexture(image, pad, yAfterHeader() + topH - 8, w - pad * 2, Math.max(18, h / 10), be.getBlockPos(), trackerSlot(source, 0), pct / 100.0f, 0.0f, 0.6f, InstrumentRenderPrimitives.SAFE_GREEN, 0.6f, InstrumentRenderPrimitives.ALERT_AMBER, 0.85f, InstrumentRenderPrimitives.DANGER_RED, state.color, overstressed, tick);
        final int cardsY = yAfterHeader() + topH + Math.max(18, h / 10) + 8;
        drawMetricTriplet(image, pad, cardsY, w - pad * 2, Math.max(30, h - cardsY - pad), theme, "LOAD", String.format("%.1f%%", pct), state.color, "HEAD", InstrumentRenderPrimitives.formatCompact(remaining), InstrumentRenderPrimitives.SAFE_GREEN, "RPM", InstrumentRenderPrimitives.formatCompact(rpm), theme.accent);
    }

    private static void renderStressScientific(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StatsDisplayPanelBE.DataChannel heroChannel, float pct, float used, float capacity, float remaining, float rpm, StateInfo state, boolean overstressed) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        drawHeader(image, "KINETIC LOAD", source, theme, state, pad, "LAB");
        final int gaugeW = Math.max(90, w * 2 / 5);
        renderSpecializedGauge(image, be, heroChannel, 0, pad + gaugeW / 2, yAfterHeader() + h / 3 - 4, Math.max(28, Math.min(gaugeW / 2 - 8, h / 3 - 10)), theme, settings, flags);
        final int rightX = gaugeW + pad * 2;
        final int rightW = Math.max(40, w - gaugeW - pad * 3);
        InstrumentRenderPrimitives.drawValuePlate(image, rightX, yAfterHeader() + 4, rightW, Math.max(36, h / 4), "LOAD", String.format("%.1f%%", pct), theme, state.color, true);
        drawMetricTriplet(image, rightX, yAfterHeader() + Math.max(36, h / 4) + 8, rightW, Math.max(34, h / 4), theme, "USED", InstrumentRenderPrimitives.formatCompact(used), state.color, "CAP", InstrumentRenderPrimitives.formatCompact(capacity), theme.accent, "HEAD", InstrumentRenderPrimitives.formatCompact(remaining), InstrumentRenderPrimitives.SAFE_GREEN);
        InstrumentRenderPrimitives.drawSparkline(image, rightX, h - Math.max(58, h / 4), rightW, Math.max(50, h / 4 - 4), (heroChannel != null) ? heroChannel.getStore() : source.channels.get(0).getStore(), theme, state.color, "Load trend");
        if (settings.getInstrumentDensityPreset() != InstrumentDensityPreset.MINIMAL) {
            InstrumentRenderPrimitives.drawMetricCard(image, rightX, h - Math.max(58, h / 4) - 22, rightW, 18, "RPM", InstrumentRenderPrimitives.formatCompact(rpm), theme, theme.accent, true);
        }
    }

    private static void renderStressControl(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, float pct, float used, float capacity, float remaining, float rpm, StateInfo state, boolean overstressed) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        drawHeader(image, "KINETIC LOAD", source, theme, state, pad, "CTRL");
        InstrumentRenderPrimitives.drawShutterIndicator(image, w - pad - 98, 8, 98, 30, "STATE", state.shortLabel, !overstressed, theme, state.color);
        final int trackerY = controlContentTop(h);
        final int trackerH = controlTrackerHeight(h);
        BrassTrackerRenderer.drawHorizontalTexture(image, pad, trackerY, w - pad * 2, trackerH, be.getBlockPos(), trackerSlot(source, 0), pct / 100.0f, 0.0f, 0.6f, InstrumentRenderPrimitives.SAFE_GREEN, 0.6f, InstrumentRenderPrimitives.ALERT_AMBER, 0.85f, InstrumentRenderPrimitives.DANGER_RED, state.color, overstressed, tick);
        final int cardsY = trackerY + trackerH + 14;
        drawMetricTriplet(image, pad, cardsY, w - pad * 2, Math.max(24, h / 4), theme, "USED", InstrumentRenderPrimitives.formatCompact(used), state.color, "CAP", InstrumentRenderPrimitives.formatCompact(capacity), theme.accent, "HEAD", InstrumentRenderPrimitives.formatCompact(remaining), InstrumentRenderPrimitives.SAFE_GREEN);
        final int lowerY = cardsY + Math.max(24, h / 4) + 8;
        drawMetricTriplet(image, pad, lowerY, w - pad * 2, Math.max(24, h - lowerY - pad - 6), theme, "LOAD", String.format("%.1f%%", pct), state.color, "RPM", InstrumentRenderPrimitives.formatCompact(rpm), theme.accent, "MODE", state.shortLabel, state.color);
    }

    private static void renderStockpileLocomotive(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StockpileReadout readout) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        drawHeader(image, "THRESHOLD CONTROL", source, theme, readout.state(), pad, "CAB");
        final int rightW = Math.max(112, w / 3);
        final int heroW = Math.max(80, w - rightW - pad * 3);
        final int bodyY = yAfterHeader();
        final int bodyH = Math.max(48, h - bodyY - Math.max(60, h / 3) - 6);
        renderSpecializedGauge(image, be, readout.trendChannel(), 0, pad + heroW / 2, bodyY + bodyH / 2, Math.max(30, Math.min(heroW / 2 - 8, bodyH / 2 - 8)), theme, settings, flags);
        final int sideX = pad * 2 + heroW;
        InstrumentRenderPrimitives.drawValuePlate(image, sideX, bodyY + 2, rightW, Math.max(34, bodyH / 3), "FILL", String.format("%.1f%%", readout.fillPercent()), theme, readout.state().color(), true);
        InstrumentRenderPrimitives.drawBadge(image, sideX, bodyY + Math.max(34, bodyH / 3) + 6, Math.max(54, rightW / 2 - 2), 12, readout.state().shortLabel(), readout.state().color());
        InstrumentRenderPrimitives.drawMetricCard(image, sideX + Math.max(54, rightW / 2 - 2) + 4, bodyY + Math.max(34, bodyH / 3) + 4, Math.max(24, rightW - Math.max(54, rightW / 2 - 2) - 4), 16, "OUTPUT", readout.powered() ? "ON" : "OFF", theme, readout.powered() ? InstrumentRenderPrimitives.COOL_BLUE : theme.dim, false);
        final int cardsY = bodyY + Math.max(34, bodyH / 3) + 24;
        final int cardsH = Math.max(18, bodyH - Math.max(34, bodyH / 3) - 26);
        drawMetricTriplet(image, sideX, cardsY, rightW, cardsH, theme, "AMOUNT", formatMetricValue(readout.amountType(), readout.currentAmount()), theme.accent, "LOW", formatMetricValue(readout.amountType(), readout.lowerAmount()), InstrumentRenderPrimitives.SAFE_GREEN, "HIGH", formatMetricValue(readout.amountType(), readout.upperAmount()), InstrumentRenderPrimitives.ALERT_AMBER);
        final int trackerY = h - Math.max(60, h / 3) + 8;
        BrassTrackerRenderer.drawHorizontalTexture(image, pad, trackerY + 10, w - pad * 2, Math.max(18, h / 10), be.getBlockPos(), trackerSlot(source, 0), readout.fillPercent() / 100.0f, readout.lowerPercent() / 100.0f, readout.upperPercent() / 100.0f, InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.COOL_BLUE, 0.8f), readout.lowerPercent() / 100.0f, InstrumentRenderPrimitives.SAFE_GREEN, readout.upperPercent() / 100.0f, InstrumentRenderPrimitives.ALERT_AMBER, readout.powered() ? theme.accent : readout.state().color(), readout.powered(), tick);
        BitmapFont.drawCentered(image, formatMetricValue(readout.amountType(), readout.currentAmount()) + " / " + formatMetricValue(readout.amountType(), readout.capacityAmount()), pad, trackerY, w - pad * 2, theme.text, 1);
        if (settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE && flags.showSparkline) {
            InstrumentRenderPrimitives.drawSparkline(image, pad, trackerY + Math.max(24, h / 10) + 16, w - pad * 2, Math.max(30, h - trackerY - Math.max(24, h / 10) - pad - 20), readout.trendChannel().getStore(), theme, readout.state().color(), "Fill trend");
        }
        else {
            drawMetricTriplet(image, pad, trackerY + Math.max(24, h / 10) + 14, w - pad * 2, Math.max(22, h - trackerY - Math.max(24, h / 10) - pad - 18), theme, "STATE", readout.state().shortLabel(), readout.state().color(), "POWER", readout.powered() ? "ON" : "OFF", readout.powered() ? InstrumentRenderPrimitives.COOL_BLUE : theme.dim, "CAP", formatMetricValue(readout.amountType(), readout.capacityAmount()), theme.accent);
        }
    }

    private static void renderStockpileMechanical(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StockpileReadout readout) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 30);
        drawHeader(image, "THRESHOLD CONTROL", source, theme, readout.state(), pad, "BRASS");
        renderSpecializedGauge(image, be, readout.trendChannel(), 0, w / 2, yAfterHeader() + Math.max(56, h / 2) / 2 - 8, Math.max(32, Math.min(w / 4, Math.max(56, h / 2) / 2 - 12)), theme, settings, flags);
        BrassTrackerRenderer.drawHorizontalTexture(image, pad, yAfterHeader() + Math.max(56, h / 2) - 8, w - pad * 2, Math.max(18, h / 10), be.getBlockPos(), trackerSlot(source, 0), readout.fillPercent() / 100.0f, readout.lowerPercent() / 100.0f, readout.upperPercent() / 100.0f, InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.COOL_BLUE, 0.8f), readout.lowerPercent() / 100.0f, InstrumentRenderPrimitives.SAFE_GREEN, readout.upperPercent() / 100.0f, InstrumentRenderPrimitives.ALERT_AMBER, readout.powered() ? theme.accent : readout.state().color(), readout.powered(), tick);
        final int cardsY = yAfterHeader() + Math.max(56, h / 2) + Math.max(18, h / 10) + 8;
        drawMetricTriplet(image, pad, cardsY, w - pad * 2, Math.max(30, h - cardsY - pad), theme, "FILL", String.format("%.1f%%", readout.fillPercent()), readout.state().color(), "AMOUNT", formatMetricValue(readout.amountType(), readout.currentAmount()), theme.accent, "CAP", formatMetricValue(readout.amountType(), readout.capacityAmount()), InstrumentRenderPrimitives.ALERT_AMBER);
    }

    private static void renderStockpileScientific(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StockpileReadout readout) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        drawHeader(image, "THRESHOLD CONTROL", source, theme, readout.state(), pad, "LAB");
        final int gaugeW = Math.max(90, w * 2 / 5);
        renderSpecializedGauge(image, be, readout.trendChannel(), 0, pad + gaugeW / 2, yAfterHeader() + h / 3 - 4, Math.max(28, Math.min(gaugeW / 2 - 8, h / 3 - 10)), theme, settings, flags);
        final int rightX = gaugeW + pad * 2;
        final int rightW = Math.max(40, w - gaugeW - pad * 3);
        InstrumentRenderPrimitives.drawValuePlate(image, rightX, yAfterHeader() + 4, rightW, Math.max(36, h / 4), "FILL", String.format("%.1f%%", readout.fillPercent()), theme, readout.state().color(), true);
        drawMetricTriplet(image, rightX, yAfterHeader() + Math.max(36, h / 4) + 8, rightW, Math.max(34, h / 4), theme, "AMOUNT", formatMetricValue(readout.amountType(), readout.currentAmount()), theme.accent, "LOW", formatMetricValue(readout.amountType(), readout.lowerAmount()), InstrumentRenderPrimitives.SAFE_GREEN, "HIGH", formatMetricValue(readout.amountType(), readout.upperAmount()), InstrumentRenderPrimitives.ALERT_AMBER);
        InstrumentRenderPrimitives.drawSparkline(image, rightX, h - Math.max(58, h / 4), rightW, Math.max(50, h / 4 - 4), readout.trendChannel().getStore(), theme, readout.state().color(), "Fill trend");
        if (settings.getInstrumentDensityPreset() != InstrumentDensityPreset.MINIMAL) {
            InstrumentRenderPrimitives.drawMetricCard(image, rightX, h - Math.max(58, h / 4) - 22, rightW, 18, "CAPACITY", formatMetricValue(readout.amountType(), readout.capacityAmount()), theme, theme.accent, true);
        }
    }

    private static void renderStockpileControl(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick, StockpileReadout readout) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        drawHeader(image, "THRESHOLD CONTROL", source, theme, readout.state(), pad, "CTRL");
        InstrumentRenderPrimitives.drawShutterIndicator(image, w - pad - 98, 8, 98, 30, "OUTPUT", readout.powered() ? "ACTIVE" : "IDLE", readout.powered(), theme, readout.powered() ? InstrumentRenderPrimitives.COOL_BLUE : readout.state().color());
        final int trackerY = controlContentTop(h);
        final int trackerH = controlTrackerHeight(h);
        BrassTrackerRenderer.drawHorizontalTexture(image, pad, trackerY, w - pad * 2, trackerH, be.getBlockPos(), trackerSlot(source, 0), readout.fillPercent() / 100.0f, readout.lowerPercent() / 100.0f, readout.upperPercent() / 100.0f, InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.COOL_BLUE, 0.8f), readout.lowerPercent() / 100.0f, InstrumentRenderPrimitives.SAFE_GREEN, readout.upperPercent() / 100.0f, InstrumentRenderPrimitives.ALERT_AMBER, readout.powered() ? theme.accent : readout.state().color(), readout.powered(), tick);
        final int cardsY = trackerY + trackerH + 14;
        drawMetricTriplet(image, pad, cardsY, w - pad * 2, Math.max(24, h / 4), theme, "FILL", String.format("%.1f%%", readout.fillPercent()), readout.state().color(), "AMOUNT", formatMetricValue(readout.amountType(), readout.currentAmount()), theme.accent, "STATE", readout.state().shortLabel(), readout.state().color());
        final int lowerY = cardsY + Math.max(24, h / 4) + 8;
        drawMetricTriplet(image, pad, lowerY, w - pad * 2, Math.max(24, h - lowerY - pad - 6), theme, "LOW", formatMetricValue(readout.amountType(), readout.lowerAmount()), InstrumentRenderPrimitives.SAFE_GREEN, "HIGH", formatMetricValue(readout.amountType(), readout.upperAmount()), InstrumentRenderPrimitives.ALERT_AMBER, "CAP", formatMetricValue(readout.amountType(), readout.capacityAmount()), theme.accent);
    }

    private static void renderGenericPanel(NativeImage image, StatsDisplayPanelBE be, SourceSelection source, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags, long tick) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int pad = Math.max(8, w / 28);
        final StatsDisplayPanelBE.DataChannel hero = source.channels.get(0);
        final StateInfo state = new StateInfo("READY", "READY", InstrumentRenderPrimitives.accentForChannel(hero, theme, settings));
        drawHeader(image, source.sourceType.toUpperCase(), source, theme, state, pad, "GEN");
        renderSpecializedGauge(image, be, hero, 0, pad + Math.max(84, w * 2 / 5) / 2, yAfterHeader() + h / 3 - 4, Math.max(28, Math.min(Math.max(84, w * 2 / 5) / 2 - 8, h / 3 - 10)), theme, settings, flags);
        final int rightX = Math.max(84, w * 2 / 5) + pad * 2;
        final int rightW = Math.max(40, w - Math.max(84, w * 2 / 5) - pad * 3);
        InstrumentRenderPrimitives.drawValuePlate(image, rightX, yAfterHeader() + 4, rightW, Math.max(36, h / 4), hero.getLabel(), InstrumentRenderPrimitives.formatChannelValue(hero), theme, state.color, true);
        drawChannelCards(image, source.channels, 1, rightX, yAfterHeader() + Math.max(36, h / 4) + 8, rightW, Math.max(34, h - yAfterHeader() - Math.max(36, h / 4) - pad - 12), theme, settings);
    }

    private static void drawHeader(NativeImage image, String title, SourceSelection source, DisplayTheme theme, StateInfo state, int pad, String familyTag) {
        BitmapFont.drawString(image, title, pad, 8, theme.text, image.getWidth() >= 360 ? 2 : 1);
        BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(source.label, Math.max(10, (image.getWidth() - pad * 2) / 10)), pad, 22, theme.dim, 1);
        final String cycleLabel = (source.cycleCount > 1) ? ("SRC " + source.cycleIndex + "/" + source.cycleCount) : familyTag;
        final int cycleWidth = (source.cycleCount > 1) ? 64 : 52;
        InstrumentRenderPrimitives.drawBadge(image, image.getWidth() - pad - cycleWidth, 8, cycleWidth, 12, cycleLabel, (source.cycleCount > 1) ? InstrumentRenderPrimitives.COOL_BLUE : state.color);
        InstrumentRenderPrimitives.drawBadge(image, image.getWidth() - pad - cycleWidth - 58, 8, 54, 12, state.shortLabel, state.color);
    }

    private static void drawMetricTriplet(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, String label1, String value1, int accent1, String label2, String value2, int accent2, String label3, String value3, int accent3) {
        final int gap = 6;
        final int cardW = Math.max(24, (w - gap * 2) / 3);
        final int cardH = Math.max(18, h);
        InstrumentRenderPrimitives.drawMetricCard(image, x, y, cardW, cardH, label1, value1, theme, accent1, false);
        InstrumentRenderPrimitives.drawMetricCard(image, x + cardW + gap, y, cardW, cardH, label2, value2, theme, accent2, false);
        InstrumentRenderPrimitives.drawMetricCard(image, x + (cardW + gap) * 2, y, cardW, cardH, label3, value3, theme, accent3, false);
    }

    private static void drawChannelCards(NativeImage image, List<StatsDisplayPanelBE.DataChannel> channels, int startIndex, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final int renderCount = Math.min(4, Math.max(0, channels.size() - startIndex));
        if (renderCount <= 0 || h <= 12) {
            return;
        }
        final boolean wideRow = w >= 320 && h <= 84;
        final int cols = wideRow ? Math.min(renderCount, 4) : Math.min(renderCount, 2);
        final int rows = (renderCount + cols - 1) / cols;
        final int gap = 4;
        final int cardW = Math.max(26, (w - gap * (cols - 1)) / cols);
        final int cardH = Math.max(18, (h - gap * (rows - 1)) / rows);
        for (int i = 0; i < renderCount; ++i) {
            final StatsDisplayPanelBE.DataChannel channel = channels.get(startIndex + i);
            final int col = i % cols;
            final int row = i / cols;
            InstrumentRenderPrimitives.drawMetricCard(image, x + col * (cardW + gap), y + row * (cardH + gap), cardW, cardH, channel.getLabel(), InstrumentRenderPrimitives.formatChannelValue(channel), theme, InstrumentRenderPrimitives.accentForChannel(channel, theme, settings), true);
        }
    }

    private static void renderSpecializedGauge(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel channel, int offset, int cx, int cy, int radius, DisplayTheme theme, StatsDisplaySettings settings, SpecializedFlags flags) {
        if (channel == null) {
            return;
        }
        AnalogGaugeRenderer.tickGaugeNeedle(be.getBlockPos(), trackerSlot(be.getBlockPos().toShortString(), offset), channel, settings, 2);
        AnalogGaugeRenderer.renderGaugeWidget(image, cx, cy, radius, channel, trackerSlot(be.getBlockPos().toShortString(), offset), be.getBlockPos(), theme, settings, flags.showZones, flags.showGlass, flags.showRivets, true);
    }

    private static SpecializedFlags resolveFlags(StatsDisplaySettings settings, boolean largeSurface) {
        final InstrumentStyleFamily family = settings.getInstrumentStyleFamily();
        final InstrumentEffectPreset effect = settings.getInstrumentEffectPreset();
        final InstrumentStyleOptions options = settings.getInstrumentStyleOptions();
        final boolean rich = effect == InstrumentEffectPreset.RICH;
        return new SpecializedFlags(options.getOption(InstrumentStyleOptionKeys.SHOW_WARNING_ZONES, true), options.getOption(InstrumentStyleOptionKeys.SHOW_GLASS, rich && !largeSurface), options.getOption(InstrumentStyleOptionKeys.SHOW_RIVETS, (family == InstrumentStyleFamily.MECHANICAL_BRASS || rich) && !largeSurface), options.getOption(InstrumentStyleOptionKeys.SHOW_SPARKLINE, settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE && !largeSurface));
    }

    private static SourceSelection selectSource(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = groupBySource(channels);
        if (preferredSourceId != null) {
            return buildSelection(be, preferredSourceId, bySource.get(preferredSourceId), 1, 1);
        }
        final ArrayList<SourceSelection> available = new ArrayList<SourceSelection>();
        for (Map.Entry<String, SourceConfig> entry : be.getSourceRegistry().entrySet()) {
            final SourceSelection selection = buildSelection(be, entry.getKey(), bySource.remove(entry.getKey()), 0, 0);
            if (selection != null) {
                available.add(selection);
            }
        }
        for (Map.Entry<String, List<StatsDisplayPanelBE.DataChannel>> entry2 : bySource.entrySet()) {
            final SourceSelection selection2 = buildSelection(be, entry2.getKey(), entry2.getValue(), 0, 0);
            if (selection2 != null) {
                available.add(selection2);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        if (available.size() == 1) {
            final SourceSelection only = available.get(0);
            only.cycleIndex = 1;
            only.cycleCount = 1;
            return only;
        }
        final int interval = Math.max(1, be.getSettings().getCycleInterval());
        final int idx = (int)(renderTick / interval % available.size());
        final SourceSelection selected = available.get(Math.max(0, Math.min(available.size() - 1, idx)));
        selected.cycleIndex = idx + 1;
        selected.cycleCount = available.size();
        return selected;
    }

    private static SourceSelection buildSelection(StatsDisplayPanelBE be, String sourceId, List<StatsDisplayPanelBE.DataChannel> channels, int cycleIndex, int cycleCount) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        final SourceSelection selection = new SourceSelection();
        selection.sourceId = sourceId;
        selection.channels = channels;
        selection.cycleIndex = cycleIndex;
        selection.cycleCount = cycleCount;
        final SourceConfig config = be.getSourceRegistry().get(sourceId);
        selection.sourceType = (config != null) ? config.getSourceType() : (be.getSourceType().isEmpty() ? sourceId : be.getSourceType());
        selection.label = (config != null) ? config.getDisplayLabel() : selection.sourceType;
        return selection;
    }

    private static LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> groupBySource(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = new LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>>();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            bySource.computeIfAbsent(extractSourceId(channel.getId()), key -> new ArrayList()).add(channel);
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

    private static StatsDisplayPanelBE.DataChannel findMetric(SourceSelection source, String metricId, String label) {
        for (StatsDisplayPanelBE.DataChannel channel : source.channels) {
            if (channel.getId().contains("_auto_" + metricId)) {
                return channel;
            }
        }
        for (StatsDisplayPanelBE.DataChannel channel2 : source.channels) {
            if (channel2.getLabel().equalsIgnoreCase(label)) {
                return channel2;
            }
        }
        return null;
    }

    private static float valueOr(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore().getSampleCount() == 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
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

    private static StateInfo describeStressState(float pct, boolean overstressed) {
        if (overstressed || pct >= 100.0f) {
            return new StateInfo("OVERSTRESSED", "LIMIT", InstrumentRenderPrimitives.DANGER_RED);
        }
        if (pct >= 85.0f) {
            return new StateInfo("NEAR LIMIT", "WATCH", InstrumentRenderPrimitives.ALERT_AMBER);
        }
        return new StateInfo("HEALTHY", "READY", InstrumentRenderPrimitives.SAFE_GREEN);
    }

    private static StateInfo describeStockpileState(float fill, float lower, float upper, boolean powered) {
        if (fill < lower) {
            return new StateInfo("REFILLING", "LOW", InstrumentRenderPrimitives.SAFE_GREEN);
        }
        if (fill > upper) {
            return new StateInfo("READY", "FULL", InstrumentRenderPrimitives.ALERT_AMBER);
        }
        if (powered) {
            return new StateInfo("OUTPUT ON", "ACTIVE", InstrumentRenderPrimitives.COOL_BLUE);
        }
        return new StateInfo("BUFFER ZONE", "BUFFER", InstrumentRenderPrimitives.SAFE_GREEN);
    }

    private static int trackerSlot(SourceSelection source, int offset) {
        return trackerSlot(source.sourceId, offset);
    }

    private static int trackerSlot(String key, int offset) {
        final int hash = Math.abs(key.hashCode() % 700);
        return 1200 + hash + offset;
    }

    private static int yAfterHeader() {
        return 36;
    }

    private static int controlContentTop(int imageHeight) {
        return Math.max(yAfterHeader() + 12, Math.max(48, imageHeight / 11 + 16));
    }

    private static int controlTrackerHeight(int imageHeight) {
        return Math.max(18, imageHeight / 12);
    }

    private static final class SourceSelection
    {
        String sourceId;
        String sourceType;
        String label;
        List<StatsDisplayPanelBE.DataChannel> channels;
        int cycleIndex;
        int cycleCount;
    }

    private record StateInfo(String label, String shortLabel, int color) {}

    private record StockpileReadout(StatsDisplayPanelBE.DataChannel trendChannel, float fillPercent, float currentAmount, float capacityAmount, float lowerAmount, float upperAmount, float lowerPercent, float upperPercent, boolean powered, MetricType amountType, StateInfo state) {}

    private record SpecializedFlags(boolean showZones, boolean showGlass, boolean showRivets, boolean showSparkline) {}
}
