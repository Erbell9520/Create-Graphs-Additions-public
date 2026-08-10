package com.createstats.renderer.instrument;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.DisplayTheme;
import com.createstats.display.InstrumentEffectPreset;
import com.createstats.display.InstrumentStyleFamily;
import com.createstats.display.InstrumentStyleOptionKeys;
import com.createstats.display.InstrumentStyleOptions;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.renderer.BitmapFont;
import com.createstats.renderer.BrassTrackerRenderer;
import com.createstats.renderer.TextureGraphRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InstrumentRenderPrimitives
{
    public static final int SAFE_GREEN = 0xFF2FB36D;
    public static final int ALERT_AMBER = 0xFFE3A43B;
    public static final int DANGER_RED = 0xFFD9594C;
    public static final int COOL_BLUE = 0xFF5AA5D6;
    public static final int BRASS = 0xFFC78B2A;
    public static final int BRASS_DARK = 0xFF6E5527;
    public static final int IRON = 0xFF2E3338;
    
    private InstrumentRenderPrimitives() {
    }
    
    public static void drawFamilyFrame(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, InstrumentStyleFamily family, InstrumentEffectPreset effect, InstrumentStyleOptions options) {
        final int base = darken(theme.graphBg, 0.95f);
        TextureGraphRenderer.fillRect(image, x, y, w, h, base);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, Math.max(0, w - 2), 2, brighten(theme.border, 1.08f));
        final boolean showTrim = options.getOption(InstrumentStyleOptionKeys.SHOW_DECOR_TRIM, effect != InstrumentEffectPreset.LOW);
        final boolean showRivets = options.getOption(InstrumentStyleOptionKeys.SHOW_RIVETS, effect == InstrumentEffectPreset.RICH || family == InstrumentStyleFamily.MECHANICAL_BRASS);
        switch (family) {
            case LOCOMOTIVE_CAB: {
                final int headerH = Math.max(12, h / 10);
                TextureGraphRenderer.fillRect(image, x + 2, y + 2, Math.max(0, w - 4), headerH, darken(BRASS, 0.9f));
                TextureGraphRenderer.drawBorder(image, x + 2, y + 2, Math.max(0, w - 4), headerH, 1, brighten(BRASS, 1.1f));
                TextureGraphRenderer.fillRect(image, x + 4, y + headerH + 5, Math.max(0, w - 8), 2, BRASS_DARK);
                if (showTrim) {
                    TextureGraphRenderer.fillRect(image, x + 4, y + h - Math.max(10, h / 8), Math.max(0, w - 8), 2, darken(BRASS, 0.84f));
                }
                break;
            }
            case MECHANICAL_BRASS: {
                TextureGraphRenderer.fillRect(image, x + 2, y + 2, Math.max(0, w - 4), Math.max(0, h - 4), darken(BRASS_DARK, 0.8f));
                TextureGraphRenderer.drawBorder(image, x + 3, y + 3, Math.max(0, w - 6), Math.max(0, h - 6), 1, brighten(BRASS, 1.12f));
                if (showTrim) {
                    final int midY = y + h / 2;
                    TextureGraphRenderer.fillRect(image, x + 6, midY, Math.max(0, w - 12), 2, darken(BRASS, 0.9f));
                }
                break;
            }
            case SCIENTIFIC_INSTRUMENT: {
                final int gridColor = darken(COOL_BLUE, 0.7f);
                for (int gx = x + 10; gx < x + w - 10; gx += Math.max(14, w / 8)) {
                    TextureGraphRenderer.fillRect(image, gx, y + 4, 1, Math.max(0, h - 8), gridColor);
                }
                for (int gy = y + 10; gy < y + h - 10; gy += Math.max(14, h / 7)) {
                    TextureGraphRenderer.fillRect(image, x + 4, gy, Math.max(0, w - 8), 1, gridColor);
                }
                TextureGraphRenderer.fillRect(image, x + 2, y + 2, Math.max(0, w - 4), 8, darken(COOL_BLUE, 0.9f));
                break;
            }
            case CONTROL_PANEL_WALL: {
                final int bandH = Math.max(10, h / 11);
                TextureGraphRenderer.fillRect(image, x + 2, y + 2, Math.max(0, w - 4), bandH, darken(theme.accent, 0.85f));
                TextureGraphRenderer.fillRect(image, x + 2, y + h - bandH - 2, Math.max(0, w - 4), bandH, darken(IRON, 0.95f));
                TextureGraphRenderer.fillRect(image, x + w / 3, y + bandH + 6, 1, Math.max(0, h - bandH * 2 - 12), theme.grid);
                TextureGraphRenderer.fillRect(image, x + (w * 2) / 3, y + bandH + 6, 1, Math.max(0, h - bandH * 2 - 12), theme.grid);
                break;
            }
            default: {
                break;
            }
        }
        if (showRivets) {
            final int rivetR = Math.max(1, Math.min(w, h) / 36);
            drawRivet(image, x + 4, y + 4, rivetR);
            drawRivet(image, x + w - 5, y + 4, rivetR);
            drawRivet(image, x + 4, y + h - 5, rivetR);
            drawRivet(image, x + w - 5, y + h - 5, rivetR);
        }
    }
    
    public static void drawMetricCard(NativeImage image, int x, int y, int w, int h, String label, String value, DisplayTheme theme, int accentColor, boolean highContrastText) {
        final int bg = darken(theme.graphBg, 0.9f);
        TextureGraphRenderer.fillRect(image, x, y, w, h, bg);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        TextureGraphRenderer.fillRect(image, x, y, w, 2, accentColor);
        BitmapFont.drawString(image, truncate(label, Math.max(4, w / 6)), x + 4, y + 3, highContrastText ? theme.text : theme.dim, 1);
        final int scale = (w >= 92 && h >= 28) ? 2 : 1;
        BitmapFont.drawCentered(image, truncate(value, Math.max(4, w / (6 * scale))), x + 2, y + h / 2 - BitmapFont.textHeight(scale) / 2 + 3, Math.max(0, w - 4), accentColor, scale);
    }
    
    public static void drawValuePlate(NativeImage image, int x, int y, int w, int h, String label, String value, DisplayTheme theme, int accentColor, boolean highContrastText) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.86f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, accentColor);
        BitmapFont.drawString(image, truncate(label, Math.max(5, w / 6)), x + 4, y + 4, highContrastText ? theme.text : theme.dim, 1);
        final int scale = (w >= 120 && h >= 36) ? 2 : 1;
        BitmapFont.drawCentered(image, truncate(value, Math.max(4, w / (6 * scale))), x + 2, y + h / 2 - BitmapFont.textHeight(scale) / 2 + 4, Math.max(0, w - 4), accentColor, scale);
    }
    
    public static void drawBadge(NativeImage image, int x, int y, int w, int h, String text, int color) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(color, 0.7f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, color);
        BitmapFont.drawCentered(image, truncate(text, Math.max(4, w / 6)), x + 1, y + (h - BitmapFont.textHeight(1)) / 2, Math.max(0, w - 2), -1, 1);
    }
    
    public static void drawLampRow(NativeImage image, int x, int y, int w, String[] labels, boolean[] states, int[] colors, DisplayTheme theme, long tick) {
        if (labels.length == 0) {
            return;
        }
        final int lampCount = Math.min(labels.length, Math.min(states.length, colors.length));
        final int spacing = Math.max(20, w / Math.max(1, lampCount));
        final int startX = x + Math.max(8, (w - spacing * lampCount) / 2) + spacing / 2;
        for (int i = 0; i < lampCount; ++i) {
            final int cx = startX + i * spacing;
            BrassTrackerRenderer.drawLampTexture(image, cx, y + 6, 5, states[i], colors[i], tick);
            BitmapFont.drawCentered(image, truncate(labels[i], 6), cx - 12, y + 15, 24, theme.dim, 1);
        }
    }
    
    public static void drawShutterIndicator(NativeImage image, int x, int y, int w, int h, String label, String text, boolean active, DisplayTheme theme, int accentColor) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(IRON, 0.92f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, truncate(label, Math.max(4, w / 6)), x + 4, y + 3, theme.dim, 1);
        final int slotX = x + 4;
        final int slotY = y + 12;
        final int slotW = Math.max(8, w - 8);
        final int slotH = Math.max(8, h - 16);
        TextureGraphRenderer.fillRect(image, slotX, slotY, slotW, slotH, darken(theme.graphBg, 0.84f));
        TextureGraphRenderer.drawBorder(image, slotX, slotY, slotW, slotH, 1, darken(theme.border, 0.92f));
        final int openingW = Math.max(8, Math.round((slotW - 2) * (active ? 0.72f : 0.3f)));
        TextureGraphRenderer.fillRect(image, slotX + 1, slotY + 1, openingW, slotH - 2, darken(accentColor, 0.76f));
        TextureGraphRenderer.fillRect(image, slotX + 1 + openingW, slotY + 1, Math.max(1, slotW - 2 - openingW), slotH - 2, darken(IRON, 0.78f));
        BitmapFont.drawCentered(image, truncate(text, Math.max(4, slotW / 6)), slotX + 2, slotY + (slotH - BitmapFont.textHeight(1)) / 2, Math.max(0, slotW - 4), active ? -1 : theme.dim, 1);
    }
    
    public static void drawTracker(NativeImage image, int x, int y, int w, int h, StatsDisplayPanelBE.DataChannel channel, int slotIndex, StatsDisplayPanelBE be, StatsDisplaySettings settings, int accentColor, long tick) {
        final float normalized = normalizeChannel(channel, settings);
        final float yellow = clamp01(settings.getAnalogZoneYellow());
        final float red = clamp01(settings.getAnalogZoneRed());
        BrassTrackerRenderer.drawHorizontalTexture(image, x, y, w, h, be.getBlockPos(), slotIndex, normalized, yellow, red, accentColor, yellow, ALERT_AMBER, red, DANGER_RED, accentColor, channel != null && channel.isAlertTriggered(), tick);
    }
    
    public static void drawSparkline(NativeImage image, int x, int y, int w, int h, TimeSeriesStore store, DisplayTheme theme, int lineColor, String label) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.88f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        BitmapFont.drawString(image, truncate(label, Math.max(4, w / 6)), x + 3, y + 2, theme.dim, 1);
        final int chartX = x + 3;
        final int chartY = y + 12;
        final int chartW = Math.max(6, w - 6);
        final int chartH = Math.max(6, h - 15);
        final float[] data = store.getDataForTimeScale(Math.max(30, Math.min(300, chartW / 2)));
        if (data.length < 2) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : data) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        float range = max - min;
        if (range < 0.001f) {
            range = Math.max(1.0f, Math.abs(max) * 0.1f + 1.0f);
            min -= range * 0.5f;
            max = min + range;
        }
        final int midY = chartY + chartH / 2;
        TextureGraphRenderer.drawLine(image, chartX, midY, chartX + chartW, midY, darken(theme.grid, 0.95f));
        int prevX = chartX;
        int prevY = mapToY(data[0], min, max, chartY, chartH);
        for (int i = 1; i < data.length; ++i) {
            final int px = chartX + i * (chartW - 1) / (data.length - 1);
            final int py = mapToY(data[i], min, max, chartY, chartH);
            TextureGraphRenderer.drawLine(image, prevX, prevY, px, py, lineColor);
            prevX = px;
            prevY = py;
        }
        TextureGraphRenderer.fillCircle(image, prevX, prevY, 2, lineColor);
    }
    
    public static void drawCenteredMessage(NativeImage image, int x, int y, int w, int h, String title, String message, DisplayTheme theme) {
        final int titleScale = (w >= 220) ? 2 : 1;
        BitmapFont.drawCentered(image, title, x, y + h / 2 - BitmapFont.textHeight(titleScale) - 4, w, theme.text, titleScale);
        BitmapFont.drawCentered(image, message, x, y + h / 2 + 4, w, theme.dim, 1);
    }
    
    public static void drawFooterDots(NativeImage image, int pageIdx, int totalPages, int x, int y, int w, int h, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darken(theme.graphBg, 0.9f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        TextureGraphRenderer.renderPageDots(image, pageIdx, totalPages, x + 4, y + 1, Math.max(0, w - 8), Math.max(0, h - 2), theme);
    }
    
    public static float latestValue(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore().getSampleCount() == 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
    }
    
    public static String formatChannelValue(StatsDisplayPanelBE.DataChannel channel) {
        if (channel == null || channel.getStore().getSampleCount() == 0) {
            return "--";
        }
        return channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue());
    }
    
    public static float normalizeChannel(StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings) {
        if (channel == null || channel.getStore().getSampleCount() == 0) {
            return 0.0f;
        }
        final float value = channel.getStore().getLatestValue();
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.5f;
        }
        float min = settings.getAnalogGaugeMin();
        float max = settings.getAnalogGaugeMax();
        if (min >= max) {
            min = channel.getStore().getMin();
            max = channel.getStore().getMax();
            float range = max - min;
            if (range < 0.001f) {
                range = Math.max(1.0f, Math.abs(min) * 0.1f);
            }
            min = ((channel.getStore().getMin() >= 0.0f) ? 0.0f : (min - range * 0.1f));
            max += range * 0.1f;
        }
        final float range = max - min;
        if (range < 0.001f) {
            return 0.5f;
        }
        return clamp01((value - min) / range);
    }
    
    public static int accentForChannel(StatsDisplayPanelBE.DataChannel channel, DisplayTheme theme, StatsDisplaySettings settings) {
        if (channel == null) {
            return theme.accent;
        }
        if (channel.isAlertTriggered()) {
            return DANGER_RED;
        }
        final float normalized = normalizeChannel(channel, settings);
        if (normalized >= clamp01(settings.getAnalogZoneRed())) {
            return DANGER_RED;
        }
        if (normalized >= clamp01(settings.getAnalogZoneYellow())) {
            return ALERT_AMBER;
        }
        return theme.accent;
    }
    
    public static int familyAccent(InstrumentStyleFamily family, DisplayTheme theme) {
        return switch (family) {
            case MECHANICAL_BRASS -> BRASS;
            case LOCOMOTIVE_CAB -> brighten(BRASS, 1.04f);
            case SCIENTIFIC_INSTRUMENT -> COOL_BLUE;
            case CONTROL_PANEL_WALL -> brighten(theme.accent, 1.08f);
            default -> theme.accent;
        };
    }
    
    public static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(1, maxChars - 2)) + "..";
    }
    
    public static String formatCompact(float value) {
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
    
    public static int brighten(int color, float factor) {
        return scaleRgb(color, factor);
    }
    
    public static int darken(int color, float factor) {
        return scaleRgb(color, factor);
    }
    
    private static void drawRivet(NativeImage image, int cx, int cy, int radius) {
        TextureGraphRenderer.fillCircle(image, cx, cy, radius + 1, IRON);
        TextureGraphRenderer.fillCircle(image, cx, cy, radius, BRASS);
    }
    
    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
    
    private static int mapToY(float value, float min, float max, int y, int h) {
        final float norm = (value - min) / Math.max(0.001f, max - min);
        return y + h - 1 - Math.round(clamp01(norm) * Math.max(1, h - 2));
    }
    
    private static int scaleRgb(int color, float factor) {
        final int a = color >>> 24;
        final int alpha = (a == 0) ? 255 : a;
        final int r = Math.max(0, Math.min(255, Math.round((color >> 16 & 0xFF) * factor)));
        final int g = Math.max(0, Math.min(255, Math.round((color >> 8 & 0xFF) * factor)));
        final int b = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return alpha << 24 | r << 16 | g << 8 | b;
    }
}
