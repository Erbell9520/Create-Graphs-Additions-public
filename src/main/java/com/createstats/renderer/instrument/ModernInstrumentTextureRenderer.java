package com.createstats.renderer.instrument;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.DisplayTheme;
import com.createstats.display.InstrumentDensityPreset;
import com.createstats.display.InstrumentEffectPreset;
import com.createstats.display.InstrumentStyleFamily;
import com.createstats.display.InstrumentStyleOptionKeys;
import com.createstats.display.InstrumentStyleOptions;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.renderer.AnalogGaugeRenderer;
import com.createstats.renderer.BitmapFont;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ModernInstrumentTextureRenderer
{
    private ModernInstrumentTextureRenderer() {
    }
    
    public static void renderAnalogPanel(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel primaryChannel, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, int pageIdx, int totalPages) {
        final List<ChannelView> channels = collectOrderedChannels(be, primaryChannel, settings);
        final int footerH = (totalPages > 1) ? Math.max(12, Math.min(20, h / 10)) : 0;
        final int mainH = Math.max(24, h - footerH);
        final StyleFlags flags = resolveFlags(settings, w >= 640 || h >= 420);
        InstrumentRenderPrimitives.drawFamilyFrame(image, x, y, w, mainH, theme, settings.getInstrumentStyleFamily(), settings.getInstrumentEffectPreset(), settings.getInstrumentStyleOptions());
        if (channels.isEmpty()) {
            InstrumentRenderPrimitives.drawCenteredMessage(image, x, y, w, mainH, "INSTRUMENT PANEL", "Waiting for channel data", theme);
            if (footerH > 0) {
                InstrumentRenderPrimitives.drawFooterDots(image, pageIdx, totalPages, x, y + mainH, w, footerH, theme);
            }
            return;
        }
        if (w < 160 || mainH < 110) {
            renderCompactPanel(image, be, channels, x, y, w, mainH, theme, settings, flags, pageIdx, totalPages);
            if (footerH > 0) {
                InstrumentRenderPrimitives.drawFooterDots(image, pageIdx, totalPages, x, y + mainH, w, footerH, theme);
            }
            return;
        }
        switch (settings.getInstrumentStyleFamily()) {
            case LOCOMOTIVE_CAB: {
                renderLocomotiveCab(image, be, channels, x, y, w, mainH, theme, settings, flags, pageIdx, totalPages);
                break;
            }
            case MECHANICAL_BRASS: {
                renderMechanicalBrass(image, be, channels, x, y, w, mainH, theme, settings, flags, pageIdx, totalPages);
                break;
            }
            case SCIENTIFIC_INSTRUMENT: {
                renderScientificInstrument(image, be, channels, x, y, w, mainH, theme, settings, flags, pageIdx, totalPages);
                break;
            }
            case CONTROL_PANEL_WALL: {
                renderControlPanelWall(image, be, channels, x, y, w, mainH, theme, settings, flags, pageIdx, totalPages);
                break;
            }
            default: {
                renderCompactPanel(image, be, channels, x, y, w, mainH, theme, settings, flags, pageIdx, totalPages);
                break;
            }
        }
        if (footerH > 0) {
            InstrumentRenderPrimitives.drawFooterDots(image, pageIdx, totalPages, x, y + mainH, w, footerH, theme);
        }
    }
    
    private static void renderCompactPanel(NativeImage image, StatsDisplayPanelBE be, List<ChannelView> channels, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags, int pageIdx, int totalPages) {
        final ChannelView hero = channels.get(0);
        final int pad = Math.max(6, w / 24);
        final int titleScale = (w >= 220) ? 2 : 1;
        final int accent = InstrumentRenderPrimitives.accentForChannel(hero.channel(), theme, settings);
        BitmapFont.drawString(image, resolveSourceName(be, hero.channel()), x + pad, y + pad - 1, flags.highContrastText ? theme.text : theme.border, 1);
        if (totalPages > 1) {
            InstrumentRenderPrimitives.drawBadge(image, x + w - pad - 34, y + pad - 2, 34, 12, (pageIdx + 1) + "/" + totalPages, accent);
        }
        BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(hero.channel().getLabel(), Math.max(8, w / 10)), x + pad, y + pad + 10, accent, titleScale);
        final int cardH = Math.max(24, h / 4);
        final int gaugeAreaH = Math.max(32, h - cardH - pad * 3 - 14);
        final int gaugeR = Math.max(20, Math.min(w / 3, gaugeAreaH / 2 - 6));
        AnalogGaugeRenderer.renderGaugeWidget(image, x + w / 2, y + pad + 20 + gaugeAreaH / 2, gaugeR, hero.channel(), hero.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, flags.showGlass, flags.showRivets, true);
        InstrumentRenderPrimitives.drawValuePlate(image, x + pad, y + h - cardH - pad, Math.max(24, w - pad * 2), cardH, "LIVE", InstrumentRenderPrimitives.formatChannelValue(hero.channel()), theme, accent, flags.highContrastText);
    }
    
    private static void renderLocomotiveCab(NativeImage image, StatsDisplayPanelBE be, List<ChannelView> channels, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags, int pageIdx, int totalPages) {
        final ChannelView hero = channels.get(0);
        final int pad = Math.max(8, w / 28);
        final int headerY = y + 4;
        final int accent = InstrumentRenderPrimitives.accentForChannel(hero.channel(), theme, settings);
        final StateBadge state = describePanelState(channels, settings);
        final boolean wideSurface = w >= 720 || w >= h * 2 + 180;
        BitmapFont.drawString(image, resolveSourceName(be, hero.channel()), x + pad, headerY + 2, -1, 1);
        BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(hero.channel().getLabel(), Math.max(10, w / 12)), x + pad, headerY + 14, accent, 2);
        InstrumentRenderPrimitives.drawBadge(image, x + w - pad - 52, headerY + 2, 52, 12, totalPages > 1 ? ((pageIdx + 1) + "/" + totalPages) : "CAB", accent);
        final int bottomH = (settings.getInstrumentDensityPreset() == InstrumentDensityPreset.MINIMAL) ? Math.max(46, h / 4) : Math.max(62, h / 3);
        final int bodyY = y + 34;
        final int bodyH = Math.max(40, h - bottomH - 38);
        final int rightW = (settings.getInstrumentDensityPreset() == InstrumentDensityPreset.MINIMAL) ? Math.max(92, w / 3) : Math.max(112, w / 3);
        final int heroW = Math.max(70, w - rightW - pad * 3);
        final int heroR = Math.max(28, Math.min(heroW / 2 - 10, bodyH / 2 - 8));
        AnalogGaugeRenderer.renderGaugeWidget(image, x + pad + heroW / 2, bodyY + bodyH / 2, heroR, hero.channel(), hero.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, flags.showGlass, flags.showRivets, true);
        final int sideX = x + pad * 2 + heroW;
        final int bigPlateH = Math.max(34, bodyH / 3);
        InstrumentRenderPrimitives.drawValuePlate(image, sideX, bodyY + 2, rightW, bigPlateH, "PRIMARY", InstrumentRenderPrimitives.formatChannelValue(hero.channel()), theme, accent, flags.highContrastText);
        final int stateRowY = bodyY + bigPlateH + 4;
        final int stateBadgeW = Math.max(54, rightW / 2 - 2);
        InstrumentRenderPrimitives.drawBadge(image, sideX, stateRowY, stateBadgeW, 12, state.label(), state.color());
        InstrumentRenderPrimitives.drawMetricCard(image, sideX + stateBadgeW + 4, stateRowY, Math.max(24, rightW - stateBadgeW - 4), 16, "LOAD", Math.round(InstrumentRenderPrimitives.normalizeChannel(hero.channel(), settings) * 100.0f) + "%", theme, accent, flags.highContrastText);
        final int supportY = stateRowY + 20;
        final int supportH = Math.max(16, bodyH - bigPlateH - 24);
        if (settings.getInstrumentDensityPreset() != InstrumentDensityPreset.MINIMAL) {
            final boolean dense = settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE;
            final int sparkH = (dense && flags.showSparkline && supportH > 80) ? Math.max(42, supportH / 3) : 0;
            final int gaugeH = Math.max(16, supportH - sparkH - ((sparkH > 0) ? 4 : 0));
            final int supportCount = Math.min(Math.max(0, channels.size() - 1), wideSurface ? (dense ? 4 : 3) : (dense ? 3 : 2));
            renderSupportGaugeColumn(image, be, channels.subList(1, channels.size()), sideX, supportY, rightW, gaugeH, supportCount, theme, settings, flags);
            if (sparkH > 0) {
                InstrumentRenderPrimitives.drawSparkline(image, sideX, supportY + gaugeH + 4, rightW, sparkH, hero.channel().getStore(), theme, accent, "Load trend");
            }
        }
        else {
            drawChannelCards(image, channels, 1, sideX, supportY, rightW, supportH, 2, theme, settings, flags);
        }
        final int bottomY = y + h - bottomH;
        final int cardCount = (settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE) ? 3 : 2;
        final int cardsH = Math.max(24, bottomH - 24);
        final int cardGap = 4;
        final int footerCardW = Math.max(30, (w - pad * 2 - cardGap * (cardCount - 1)) / cardCount);
        if (flags.showNumericCards) {
            InstrumentRenderPrimitives.drawMetricCard(image, x + pad, bottomY + 2, footerCardW, cardsH, "STATE", state.label(), theme, state.color(), true);
            InstrumentRenderPrimitives.drawMetricCard(image, x + pad + footerCardW + cardGap, bottomY + 2, footerCardW, cardsH, "PEAK", Math.round(InstrumentRenderPrimitives.normalizeChannel(hero.channel(), settings) * 100.0f) + "%", theme, accent, flags.highContrastText);
            if (cardCount > 2) {
                final String helperValue = (channels.size() > 1) ? InstrumentRenderPrimitives.formatChannelValue(channels.get(1).channel()) : InstrumentRenderPrimitives.formatCompact(InstrumentRenderPrimitives.latestValue(hero.channel(), 0.0f));
                final String helperLabel = (channels.size() > 1) ? channels.get(1).channel().getLabel() : "ACTUAL";
                InstrumentRenderPrimitives.drawMetricCard(image, x + pad + (footerCardW + cardGap) * 2, bottomY + 2, footerCardW, cardsH, helperLabel, helperValue, theme, (channels.size() > 1) ? InstrumentRenderPrimitives.accentForChannel(channels.get(1).channel(), theme, settings) : accent, flags.highContrastText);
            }
        }
        if (flags.showShutters && w >= 240) {
            final int shutterW = Math.max(76, w / 6);
            InstrumentRenderPrimitives.drawShutterIndicator(image, x + w - pad - shutterW, bottomY + 2, shutterW, cardsH, "CAB", state.label(), state.color() != InstrumentRenderPrimitives.DANGER_RED, theme, state.color());
        }
        final int trackerW = Math.max(60, (w - pad * 3) * 3 / 5);
        InstrumentRenderPrimitives.drawTracker(image, x + pad, bottomY + cardsH + 4, trackerW, Math.max(12, bottomH - cardsH - 8), hero.channel(), hero.slotIndex(), be, settings, accent, getRenderTick(be));
        if (flags.showLamps) {
            InstrumentRenderPrimitives.drawLampRow(image, x + pad + trackerW + 4, bottomY + cardsH + 2, w - trackerW - pad * 3 - 4, buildStatusLabels(), buildStatusStates(channels, settings), buildStatusColors(), theme, getRenderTick(be));
        }
        if (state.color() != InstrumentRenderPrimitives.SAFE_GREEN && getRenderTick(be) / 5L % 2L == 0L) {
            InstrumentRenderPrimitives.drawBadge(image, x + pad, headerY + 2, 56, 12, state.label(), state.color());
        }
    }
    
    private static void renderMechanicalBrass(NativeImage image, StatsDisplayPanelBE be, List<ChannelView> channels, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags, int pageIdx, int totalPages) {
        final ChannelView hero = channels.get(0);
        final int pad = Math.max(8, w / 30);
        final int accent = InstrumentRenderPrimitives.familyAccent(InstrumentStyleFamily.MECHANICAL_BRASS, theme);
        BitmapFont.drawCentered(image, "MECHANICAL BRASS", x, y + 8, w, -1, 1);
        BitmapFont.drawCentered(image, InstrumentRenderPrimitives.truncate(hero.channel().getLabel(), Math.max(10, w / 12)), x, y + 20, w, accent, 2);
        InstrumentRenderPrimitives.drawBadge(image, x + w - pad - 50, y + 8, 50, 12, totalPages > 1 ? ((pageIdx + 1) + "/" + totalPages) : "BRASS", accent);
        final int upperH = Math.max(54, h - Math.max(58, h / 3));
        final int centerX = x + w / 2;
        final int heroR = Math.max(28, Math.min(w / 4, upperH / 2 - 8));
        AnalogGaugeRenderer.renderGaugeWidget(image, centerX, y + upperH / 2 + 18, heroR, hero.channel(), hero.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, flags.showGlass, true, true);
        if (settings.getInstrumentDensityPreset() != InstrumentDensityPreset.MINIMAL && channels.size() > 1) {
            final int sideR = Math.max(18, heroR / 2);
            final int leftX = x + pad + sideR + 4;
            final int rightX = x + w - pad - sideR - 4;
            final int sideY = y + upperH / 2 + 18;
            final ChannelView left = channels.get(1);
            AnalogGaugeRenderer.renderGaugeWidget(image, leftX, sideY, sideR, left.channel(), left.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, false, flags.showRivets, false);
            BitmapFont.drawCentered(image, InstrumentRenderPrimitives.truncate(left.channel().getLabel(), 7), leftX - sideR, sideY + sideR + 4, sideR * 2, theme.dim, 1);
            if (channels.size() > 2) {
                final ChannelView right = channels.get(2);
                AnalogGaugeRenderer.renderGaugeWidget(image, rightX, sideY, sideR, right.channel(), right.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, false, flags.showRivets, false);
                BitmapFont.drawCentered(image, InstrumentRenderPrimitives.truncate(right.channel().getLabel(), 7), rightX - sideR, sideY + sideR + 4, sideR * 2, theme.dim, 1);
            }
        }
        final int bottomY = y + upperH + 6;
        drawChannelCards(image, channels, 0, x + pad, bottomY, w - pad * 2, h - upperH - 12, settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE ? 4 : 3, theme, settings, flags);
        if (flags.showLamps) {
            InstrumentRenderPrimitives.drawLampRow(image, x + pad, y + h - 18, w - pad * 2, buildStatusLabels(), buildStatusStates(channels, settings), buildStatusColors(), theme, getRenderTick(be));
        }
    }
    
    private static void renderScientificInstrument(NativeImage image, StatsDisplayPanelBE be, List<ChannelView> channels, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags, int pageIdx, int totalPages) {
        final ChannelView hero = channels.get(0);
        final int pad = Math.max(8, w / 26);
        final int accent = InstrumentRenderPrimitives.familyAccent(InstrumentStyleFamily.SCIENTIFIC_INSTRUMENT, theme);
        BitmapFont.drawString(image, resolveSourceName(be, hero.channel()), x + pad, y + 8, accent, 1);
        BitmapFont.drawString(image, "PRIMARY CHANNEL", x + pad, y + 20, theme.dim, 1);
        InstrumentRenderPrimitives.drawBadge(image, x + w - pad - 64, y + 8, 64, 12, totalPages > 1 ? ((pageIdx + 1) + "/" + totalPages) : "LAB", accent);
        final int gaugeW = Math.max(84, w * 2 / 5);
        final int gaugeR = Math.max(26, Math.min(gaugeW / 2 - 8, h / 3));
        AnalogGaugeRenderer.renderGaugeWidget(image, x + pad + gaugeW / 2, y + h / 2 - 10, gaugeR, hero.channel(), hero.slotIndex(), be.getBlockPos(), theme, settings, true, false, flags.showRivets, true);
        final int rightX = x + gaugeW + pad * 2;
        final int rightW = Math.max(40, w - gaugeW - pad * 3);
        final int plateH = Math.max(34, h / 4);
        InstrumentRenderPrimitives.drawValuePlate(image, rightX, y + 28, rightW, plateH, hero.channel().getLabel(), InstrumentRenderPrimitives.formatChannelValue(hero.channel()), theme, accent, true);
        final int cardsY = y + 28 + plateH + pad;
        final int cardsH = flags.showSparkline ? Math.max(24, h / 4) : Math.max(24, h - cardsY - pad);
        drawChannelCards(image, channels, 1, rightX, cardsY, rightW, cardsH, settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE ? 3 : 2, theme, settings, flags);
        if (flags.showSparkline) {
            InstrumentRenderPrimitives.drawSparkline(image, rightX, y + h - Math.max(52, h / 4), rightW, Math.max(46, h / 4 - 4), hero.channel().getStore(), theme, accent, "Trend");
        }
    }
    
    private static void renderControlPanelWall(NativeImage image, StatsDisplayPanelBE be, List<ChannelView> channels, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags, int pageIdx, int totalPages) {
        final ChannelView hero = channels.get(0);
        final int pad = Math.max(8, w / 28);
        final int accent = InstrumentRenderPrimitives.familyAccent(InstrumentStyleFamily.CONTROL_PANEL_WALL, theme);
        BitmapFont.drawString(image, "CONTROL WALL", x + pad, y + 8, -1, 1);
        BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(hero.channel().getLabel(), Math.max(10, w / 12)), x + pad, y + 20, accent, 2);
        if (flags.showShutters) {
            InstrumentRenderPrimitives.drawShutterIndicator(image, x + w - pad - 98, y + 8, 98, 30, "STATE", hero.channel().isAlertTriggered() ? "LIMIT" : "READY", !hero.channel().isAlertTriggered(), theme, hero.channel().isAlertTriggered() ? InstrumentRenderPrimitives.DANGER_RED : accent);
        }
        final int topY = y + 40;
        final int leftW = Math.max(90, w / 3);
        final int gaugeR = Math.max(24, Math.min(leftW / 2 - 8, h / 3 - 6));
        AnalogGaugeRenderer.renderGaugeWidget(image, x + pad + leftW / 2, topY + h / 3 - 8, gaugeR, hero.channel(), hero.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, false, flags.showRivets, true);
        final int rightX = x + leftW + pad * 2;
        final int rightW = Math.max(40, w - leftW - pad * 3);
        final int cardAreaH = Math.max(48, h / 2 - 4);
        drawChannelCards(image, channels, 0, rightX, topY, rightW, cardAreaH, settings.getInstrumentDensityPreset() == InstrumentDensityPreset.MINIMAL ? 2 : 4, theme, settings, flags);
        final int trackerY = y + h - Math.max(42, h / 4);
        final int trackerCount = Math.min(settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE ? 3 : 2, channels.size());
        final int trackerH = Math.max(12, (h - trackerY - pad - 4) / Math.max(1, trackerCount));
        for (int i = 0; i < trackerCount; ++i) {
            final ChannelView view = channels.get(i);
            final int rowY = trackerY + i * trackerH;
            BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(view.channel().getLabel(), 10), x + pad, rowY + 1, theme.dim, 1);
            InstrumentRenderPrimitives.drawTracker(image, x + pad + 72, rowY, w - pad * 2 - 72, Math.max(10, trackerH - 2), view.channel(), view.slotIndex(), be, settings, InstrumentRenderPrimitives.accentForChannel(view.channel(), theme, settings), getRenderTick(be));
        }
        if (flags.showLamps) {
            InstrumentRenderPrimitives.drawLampRow(image, x + pad, y + h - 18, w - pad * 2, buildStatusLabels(), buildStatusStates(channels, settings), buildStatusColors(), theme, getRenderTick(be));
        }
    }
    
    private static void renderSupportGaugeColumn(NativeImage image, StatsDisplayPanelBE be, List<ChannelView> channels, int x, int y, int w, int h, int count, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags) {
        final int renderCount = Math.min(count, channels.size());
        if (renderCount <= 0 || h <= 10) {
            return;
        }
        final int cols = (renderCount >= 4 && w >= 180) ? 2 : 1;
        final int rows = (renderCount + cols - 1) / cols;
        final int colGap = 6;
        final int rowGap = 4;
        final int cellW = Math.max(52, (w - colGap * (cols - 1)) / cols);
        final int cellH = Math.max(28, (h - rowGap * (rows - 1)) / rows);
        for (int i = 0; i < renderCount; ++i) {
            final ChannelView view = channels.get(i);
            final int col = i % cols;
            final int row = i / cols;
            final int cellX = x + col * (cellW + colGap);
            final int cellY = y + row * (cellH + rowGap);
            final int cy = cellY + cellH / 2 - 2;
            final int radius = Math.max(14, Math.min(cellW / 3, cellH / 2 - 5));
            AnalogGaugeRenderer.renderGaugeWidget(image, cellX + radius + 6, cy, radius, view.channel(), view.slotIndex(), be.getBlockPos(), theme, settings, flags.showZones, false, flags.showRivets, false);
            BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(view.channel().getLabel(), Math.max(6, (cellW - radius * 2 - 14) / 6)), cellX + radius * 2 + 10, cy - 6, theme.dim, 1);
            BitmapFont.drawString(image, InstrumentRenderPrimitives.truncate(InstrumentRenderPrimitives.formatChannelValue(view.channel()), Math.max(6, (cellW - radius * 2 - 14) / 6)), cellX + radius * 2 + 10, cy + 4, InstrumentRenderPrimitives.accentForChannel(view.channel(), theme, settings), 1);
        }
    }
    
    private static void drawChannelCards(NativeImage image, List<ChannelView> channels, int startIndex, int x, int y, int w, int h, int count, DisplayTheme theme, StatsDisplaySettings settings, StyleFlags flags) {
        final int renderCount = Math.min(count, Math.max(0, channels.size() - startIndex));
        if (!flags.showNumericCards || renderCount <= 0 || w <= 12 || h <= 12) {
            return;
        }
        final boolean wideRow = w >= 320 && h <= 84;
        final int cols = wideRow ? Math.min(renderCount, 4) : Math.min(renderCount, (renderCount >= 4) ? 2 : renderCount);
        final int rows = (renderCount + cols - 1) / cols;
        final int gap = 4;
        final int cardW = Math.max(26, (w - gap * (cols - 1)) / cols);
        final int cardH = Math.max(18, (h - gap * (rows - 1)) / rows);
        for (int i = 0; i < renderCount; ++i) {
            final ChannelView view = channels.get(startIndex + i);
            final int col = i % cols;
            final int row = i / cols;
            final int cardX = x + col * (cardW + gap);
            final int cardY = y + row * (cardH + gap);
            InstrumentRenderPrimitives.drawMetricCard(image, cardX, cardY, cardW, cardH, view.channel().getLabel(), InstrumentRenderPrimitives.formatChannelValue(view.channel()), theme, InstrumentRenderPrimitives.accentForChannel(view.channel(), theme, settings), flags.highContrastText);
        }
    }
    
    private static List<ChannelView> collectOrderedChannels(StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel primaryChannel, StatsDisplaySettings settings) {
        final ArrayList<ChannelView> visible = new ArrayList<ChannelView>();
        final String primarySourceId = (primaryChannel != null) ? extractSourceId(primaryChannel.getId()) : null;
        int visibleIndex = 0;
        for (StatsDisplayPanelBE.DataChannel channel : be.getActiveChannels()) {
            if (!settings.isChannelHidden(channel.getId())) {
                if (primarySourceId == null || primarySourceId.equals(extractSourceId(channel.getId()))) {
                    visible.add(new ChannelView(channel, visibleIndex));
                }
                ++visibleIndex;
            }
        }
        if (visible.isEmpty() && primaryChannel != null) {
            visible.add(new ChannelView(primaryChannel, 0));
        }
        if (primaryChannel == null || visible.size() <= 1) {
            return visible;
        }
        ChannelView primaryView = null;
        for (ChannelView view : visible) {
            if (view.channel().getId().equals(primaryChannel.getId())) {
                primaryView = view;
                break;
            }
        }
        if (primaryView == null) {
            return visible;
        }
        final ArrayList<ChannelView> ordered = new ArrayList<ChannelView>();
        ordered.add(primaryView);
        for (ChannelView view2 : visible) {
            if (view2 != primaryView) {
                ordered.add(view2);
            }
        }
        return ordered;
    }
    
    private static StyleFlags resolveFlags(StatsDisplaySettings settings, boolean largeSurface) {
        final InstrumentStyleFamily family = settings.getInstrumentStyleFamily();
        final InstrumentEffectPreset effect = settings.getInstrumentEffectPreset();
        final InstrumentStyleOptions options = settings.getInstrumentStyleOptions();
        final boolean low = effect == InstrumentEffectPreset.LOW;
        final boolean rich = effect == InstrumentEffectPreset.RICH;
        return new StyleFlags(options.getOption(InstrumentStyleOptionKeys.SHOW_WARNING_ZONES, true), options.getOption(InstrumentStyleOptionKeys.SHOW_GLASS, rich && !largeSurface), options.getOption(InstrumentStyleOptionKeys.SHOW_RIVETS, (family == InstrumentStyleFamily.MECHANICAL_BRASS || rich) && !largeSurface), options.getOption(InstrumentStyleOptionKeys.SHOW_LAMPS, !low || family == InstrumentStyleFamily.CONTROL_PANEL_WALL), options.getOption(InstrumentStyleOptionKeys.SHOW_SHUTTERS, family == InstrumentStyleFamily.LOCOMOTIVE_CAB || family == InstrumentStyleFamily.CONTROL_PANEL_WALL), options.getOption(InstrumentStyleOptionKeys.SHOW_SPARKLINE, settings.getInstrumentDensityPreset() == InstrumentDensityPreset.DENSE && !largeSurface), options.getOption(InstrumentStyleOptionKeys.SHOW_NUMERIC_CARDS, true), options.getOption(InstrumentStyleOptionKeys.HIGH_CONTRAST_TEXT, family == InstrumentStyleFamily.SCIENTIFIC_INSTRUMENT));
    }
    
    private static String resolveSourceName(StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel channel) {
        if (channel == null) {
            return be.getSourceType();
        }
        final String channelId = channel.getId();
        for (Map.Entry<String, SourceConfig> entry : be.getSourceRegistry().entrySet()) {
            if (channelId.startsWith("src_" + entry.getKey())) {
                return entry.getValue().getDisplayLabel();
            }
        }
        return be.getSourceType().isEmpty() ? "Panel Source" : be.getSourceType();
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
    
    private static String[] buildStatusLabels() {
        return new String[] { "LIVE", "WARN", "LIMIT", "MULTI" };
    }
    
    private static boolean[] buildStatusStates(List<ChannelView> channels, StatsDisplaySettings settings) {
        boolean warn = false;
        boolean limit = false;
        for (ChannelView view : channels) {
            if (view.channel().isAlertTriggered()) {
                warn = true;
            }
            final float normalized = InstrumentRenderPrimitives.normalizeChannel(view.channel(), settings);
            if (normalized >= settings.getAnalogZoneYellow()) {
                warn = true;
            }
            if (normalized >= settings.getAnalogZoneRed()) {
                limit = true;
            }
        }
        return new boolean[] { !channels.isEmpty(), warn, limit, channels.size() > 1 };
    }
    
    private static int[] buildStatusColors() {
        return new int[] { InstrumentRenderPrimitives.SAFE_GREEN, InstrumentRenderPrimitives.ALERT_AMBER, InstrumentRenderPrimitives.DANGER_RED, InstrumentRenderPrimitives.COOL_BLUE };
    }
    
    private static StateBadge describePanelState(List<ChannelView> channels, StatsDisplaySettings settings) {
        float peak = 0.0f;
        boolean triggered = false;
        for (ChannelView view : channels) {
            triggered |= view.channel().isAlertTriggered();
            peak = Math.max(peak, InstrumentRenderPrimitives.normalizeChannel(view.channel(), settings));
        }
        if (triggered || peak >= settings.getAnalogZoneRed()) {
            return new StateBadge("LIMIT", InstrumentRenderPrimitives.DANGER_RED);
        }
        if (peak >= settings.getAnalogZoneYellow()) {
            return new StateBadge("WATCH", InstrumentRenderPrimitives.ALERT_AMBER);
        }
        if (channels.size() > 1) {
            return new StateBadge("SYNC", InstrumentRenderPrimitives.SAFE_GREEN);
        }
        return new StateBadge("READY", InstrumentRenderPrimitives.SAFE_GREEN);
    }
    
    private static long getRenderTick(StatsDisplayPanelBE be) {
        return (be.getLevel() != null) ? be.getLevel().getGameTime() : 0L;
    }
    
    private record ChannelView(StatsDisplayPanelBE.DataChannel channel, int slotIndex) {}
    
    private record StateBadge(String label, int color) {}
    
    private record StyleFlags(boolean showZones, boolean showGlass, boolean showRivets, boolean showLamps, boolean showShutters, boolean showSparkline, boolean showNumericCards, boolean highContrastText) {}
}
