package com.createstats.renderer;

import net.minecraft.core.BlockPos;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import com.createstats.renderer.instrument.InstrumentRenderPrimitives;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.display.DisplayTheme;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AnalogGaugeRenderer
{
    public static final int MIN_GAUGE_RADIUS = 30;
    public static final int MIN_LABEL_RADIUS = 50;
    public static final int TINY_GAUGE_RADIUS = 20;
    private static final int NUM_MAJOR_TICKS = 11;
    private static final int NUM_MINOR_PER_MAJ = 4;
    private static final double BASE_ANGLE_RAD = 3.9269908169872414;
    private static final float SWEEP_RAD = 4.712389f;

    private AnalogGaugeRenderer() {
    }

    public static void renderAnalogGauges(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel primaryChannel, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final List<ChannelView> channels = collectOrderedChannels(be, primaryChannel, settings);
        if (channels.isEmpty() && primaryChannel != null) {
            channels.add(new ChannelView(primaryChannel, 0));
        }
        if (channels.isEmpty()) {
            BitmapFont.drawCentered(image, "INSTRUMENTS", x, y + h / 2 - BitmapFont.textHeight(2), w, theme.text, 2);
            BitmapFont.drawCentered(image, "Waiting for channel data", x, y + h / 2 + 4, w, theme.dim, 1);
            return;
        }
        renderAdaptiveInstrumentCards(image, channels, be.getBlockPos(), x, y, w, h, theme, settings);
    }

    private static void renderAdaptiveInstrumentCards(NativeImage image, List<ChannelView> channels, BlockPos bePos, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final int count = channels.size();
        final int pad = Math.max(6, Math.min(w, h) / 42);
        final int headerH = Math.max(16, Math.min(26, h / 11));
        final int accent = blendedStatusColor(maxSmoothedNormalized(channels, bePos, settings));
        TextureGraphRenderer.fillRect(image, x, y, w, h, InstrumentRenderPrimitives.darken(theme.graphBg, 0.92f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, theme.border);
        TextureGraphRenderer.fillRect(image, x + 2, y + 2, Math.max(0, w - 4), Math.max(2, headerH / 5), accent);
        BitmapFont.drawString(image, "INSTRUMENTS", x + pad, y + Math.max(5, headerH / 2 - 3), theme.text, 1);
        final String countText = count + ((count == 1) ? " INPUT" : " INPUTS");
        BitmapFont.drawRight(image, countText, x + w - pad, y + Math.max(5, headerH / 2 - 3), theme.dim, 1);

        final int gridX = x + pad;
        final int gridY = y + headerH + pad / 2;
        final int gridW = Math.max(1, w - pad * 2);
        final int gridH = Math.max(1, h - headerH - pad);
        final CardLayout layout = layoutCards(count, gridW, gridH);
        final int gap = Math.max(5, Math.min(gridW, gridH) / 60);
        final int cardW = Math.max(18, (gridW - gap * (layout.cols() - 1)) / layout.cols());
        final int cardH = Math.max(24, (gridH - gap * (layout.rows() - 1)) / layout.rows());
        final int lastRowCount = count - (layout.rows() - 1) * layout.cols();
        for (int i = 0; i < count; ++i) {
            final int row = i / layout.cols();
            final int col = i % layout.cols();
            int rowOffset = 0;
            if (row == layout.rows() - 1 && lastRowCount > 0 && lastRowCount < layout.cols()) {
                rowOffset = (layout.cols() - lastRowCount) * (cardW + gap) / 2;
            }
            final int cardX = gridX + rowOffset + col * (cardW + gap);
            final int cardY = gridY + row * (cardH + gap);
            renderInstrumentCard(image, channels.get(i), bePos, cardX, cardY, cardW, cardH, theme, settings);
        }
    }

    private static CardLayout layoutCards(int count, int w, int h) {
        int bestCols = 1;
        int bestRows = count;
        int bestScore = Integer.MIN_VALUE;
        for (int cols = 1; cols <= Math.min(count, 4); ++cols) {
            final int rows = (count + cols - 1) / cols;
            final int cellW = Math.max(1, w / cols);
            final int cellH = Math.max(1, h / rows);
            final int ratioPenalty = Math.abs(cellW - cellH * 4 / 3);
            final int score = Math.min(cellW, cellH) * 4 + cellW - ratioPenalty - rows * 8;
            if (score > bestScore) {
                bestScore = score;
                bestCols = cols;
                bestRows = rows;
            }
        }
        if (count == 5 && w >= h) {
            bestCols = 3;
            bestRows = 2;
        }
        return new CardLayout(bestCols, bestRows);
    }

    private static void renderInstrumentCard(NativeImage image, ChannelView view, BlockPos bePos, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        if (w <= 8 || h <= 14) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel channel = view.channel();
        final float normalized = getSmoothedNormalized(bePos, view.slotIndex(), channel, settings);
        final int accent = channel != null && channel.isAlertTriggered() ? InstrumentRenderPrimitives.DANGER_RED : blendedStatusColor(normalized);
        final int pad = Math.max(4, Math.min(w, h) / 18);
        final int headerH = Math.max(14, Math.min(24, h / 7));
        final int readoutH = Math.max(20, Math.min(34, h / 5));
        TextureGraphRenderer.fillRect(image, x, y, w, h, InstrumentRenderPrimitives.darken(theme.graphBg, 0.86f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, InstrumentRenderPrimitives.darken(theme.border, 0.92f));
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, Math.max(0, w - 2), 3, accent);

        final String label = channel == null ? "Channel" : channel.getLabel();
        final int labelScale = fitScale(label, Math.max(12, w - pad * 2), w >= 176 ? 2 : 1);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(label, Math.max(8, w - pad * 2), labelScale), x + pad, y + 6, theme.text, labelScale);

        final int dialTop = y + headerH;
        final int dialBottom = y + h - readoutH - pad;
        final int dialH = Math.max(20, dialBottom - dialTop);
        final int cx = x + w / 2;
        final int cy = dialTop + dialH / 2 + Math.max(0, dialH / 14);
        final int radius = Math.max(12, Math.min((w - pad * 2) / 2, dialH / 2) - 2);
        renderModernDial(image, cx, cy, radius, normalized, accent, theme, settings);

        final GaugeRange range = resolveGaugeRange(channel, settings);
        if (radius >= 24) {
            final int scaleY = Math.min(y + h - readoutH - pad - BitmapFont.textHeight(1) - 1, cy + radius / 2 + 2);
            BitmapFont.drawString(image, formatCompact(range.min(), range.max() - range.min()), x + pad, scaleY, theme.dim, 1);
            BitmapFont.drawRight(image, formatCompact(range.max(), range.max() - range.min()), x + w - pad, scaleY, theme.dim, 1);
        }

        final int readoutY = y + h - readoutH - Math.max(3, pad / 2);
        TextureGraphRenderer.fillRect(image, x + pad, readoutY, Math.max(1, w - pad * 2), readoutH, InstrumentRenderPrimitives.darken(theme.graphBg, 0.76f));
        TextureGraphRenderer.drawBorder(image, x + pad, readoutY, Math.max(1, w - pad * 2), readoutH, 1, accent);
        final String value = channel == null ? "--" : channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue());
        final int valueScale = fitScale(value, Math.max(8, w - pad * 3), (w >= 124 && readoutH >= 28) ? 2 : 1);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, Math.max(8, w - pad * 3), valueScale), x + pad + 2, readoutY + readoutH / 2 - BitmapFont.textHeight(valueScale) / 2, Math.max(0, w - pad * 2 - 4), accent, valueScale);
    }

    private static void renderModernDial(NativeImage image, int cx, int cy, int radius, float normalized, int accent, DisplayTheme theme, StatsDisplaySettings settings) {
        final float clamped = clamp01(normalized);
        final double start = Math.toRadians(215.0);
        final double sweep = Math.toRadians(250.0);
        final double end = start - sweep;
        final int thickness = Math.max(2, radius / 14);
        final int railShadow = InstrumentRenderPrimitives.darken(theme.graphBg, 0.52f);
        final int railBase = InstrumentRenderPrimitives.darken(theme.border, 0.55f);
        drawArcStroke(image, cx + 1, cy + 1, radius + 1, start, end, thickness + 1, railShadow);
        drawArcStroke(image, cx, cy, radius, start, end, thickness, railBase);
        if (settings.isAnalogShowZones()) {
            final float yellow = clamp01(settings.getAnalogZoneYellow());
            final float red = Math.max(yellow, clamp01(settings.getAnalogZoneRed()));
            drawArcStroke(image, cx, cy, radius, start, start - sweep * yellow, thickness, InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.SAFE_GREEN, 0.82f));
            drawArcStroke(image, cx, cy, radius, start - sweep * yellow, start - sweep * red, thickness, InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.ALERT_AMBER, 0.82f));
            drawArcStroke(image, cx, cy, radius, start - sweep * red, end, thickness, InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.DANGER_RED, 0.84f));
        }
        final int liveRadius = Math.max(1, radius - thickness - 4);
        final double liveEnd = start - sweep * clamped;
        drawArcStroke(image, cx, cy, liveRadius, start, liveEnd, Math.max(2, thickness - 1), InstrumentRenderPrimitives.brighten(accent, 1.08f));
        drawArcCap(image, cx, cy, liveRadius, liveEnd, Math.max(2, thickness), accent);
        drawArcCap(image, cx, cy, radius, start, Math.max(2, thickness), InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.SAFE_GREEN, 0.92f));
        drawArcCap(image, cx, cy, radius, end, Math.max(2, thickness), InstrumentRenderPrimitives.darken(InstrumentRenderPrimitives.DANGER_RED, 0.92f));
        if (radius >= 22) {
            for (int i = 0; i <= 8; ++i) {
                final double angle = start - sweep * (i / 8.0);
                final int outerX = cx + (int)Math.round(Math.cos(angle) * (radius + 1));
                final int outerY = cy - (int)Math.round(Math.sin(angle) * (radius + 1));
                final int innerX = cx + (int)Math.round(Math.cos(angle) * (radius - thickness - (i % 2 == 0 ? 7 : 4)));
                final int innerY = cy - (int)Math.round(Math.sin(angle) * (radius - thickness - (i % 2 == 0 ? 7 : 4)));
                TextureGraphRenderer.drawLine(image, innerX, innerY, outerX, outerY, i % 2 == 0 ? theme.text : theme.dim);
            }
        }
        final double needleAngle = start - sweep * clamped;
        drawModernNeedle(image, cx, cy, radius, thickness, needleAngle, accent, theme);
    }

    private static void drawModernNeedle(NativeImage image, int cx, int cy, int radius, int thickness, double angle, int accent, DisplayTheme theme) {
        final int needleLen = Math.max(4, radius - thickness - 10);
        final int tailLen = Math.max(3, radius / 6);
        final int tipX = cx + (int)Math.round(Math.cos(angle) * needleLen);
        final int tipY = cy - (int)Math.round(Math.sin(angle) * needleLen);
        final int tailX = cx - (int)Math.round(Math.cos(angle) * tailLen);
        final int tailY = cy + (int)Math.round(Math.sin(angle) * tailLen);
        final int shadow = InstrumentRenderPrimitives.darken(theme.graphBg, 0.42f);
        TextureGraphRenderer.drawLineAA(image, cx + 1, cy + 1, tipX + 1, tipY + 1, shadow);
        TextureGraphRenderer.drawLineAA(image, tailX, tailY, cx, cy, InstrumentRenderPrimitives.darken(accent, 0.72f));
        TextureGraphRenderer.drawLineAA(image, cx, cy, tipX, tipY, InstrumentRenderPrimitives.brighten(accent, 1.18f));
        TextureGraphRenderer.drawLine(image, cx, cy, tipX, tipY, accent);
        TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(2, radius / 10), shadow);
        TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(2, radius / 14), InstrumentRenderPrimitives.darken(theme.border, 0.68f));
        TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(1, radius / 22), accent);
    }

    private static void drawArcCap(NativeImage image, int cx, int cy, int radius, double angle, int capRadius, int color) {
        final int px = cx + (int)Math.round(Math.cos(angle) * radius);
        final int py = cy - (int)Math.round(Math.sin(angle) * radius);
        TextureGraphRenderer.fillCircle(image, px, py, capRadius, color);
    }

    private static void drawArcStroke(NativeImage image, int cx, int cy, int radius, double start, double end, int thickness, int argbColor) {
        final int steps = Math.max(8, (int)(Math.abs(start - end) * radius / 2.0));
        for (int i = 0; i <= steps; ++i) {
            final double t = i / (double)steps;
            final double angle = start + (end - start) * t;
            final int px = cx + (int)Math.round(Math.cos(angle) * radius);
            final int py = cy - (int)Math.round(Math.sin(angle) * radius);
            TextureGraphRenderer.fillCircle(image, px, py, thickness, argbColor);
        }
    }

    private static float getSmoothedNormalized(BlockPos bePos, int slotIndex, StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings) {
        if (bePos == null || channel == null) {
            return 0.0f;
        }
        final NeedlePhysics needle = NeedlePhysicsRegistry.getOrCreate(bePos, slotIndex);
        return clamp01((float)(needle.getCurrentAngle() / SWEEP_RAD));
    }

    private static float maxSmoothedNormalized(List<ChannelView> channels, BlockPos bePos, StatsDisplaySettings settings) {
        float max = 0.0f;
        for (ChannelView view : channels) {
            max = Math.max(max, getSmoothedNormalized(bePos, view.slotIndex(), view.channel(), settings));
        }
        return max;
    }

    private static int blendedStatusColor(float normalized) {
        final float n = clamp01(normalized);
        if (n < 0.68f) {
            return lerpColor(InstrumentRenderPrimitives.SAFE_GREEN, InstrumentRenderPrimitives.ALERT_AMBER, n / 0.68f);
        }
        return lerpColor(InstrumentRenderPrimitives.ALERT_AMBER, InstrumentRenderPrimitives.DANGER_RED, (n - 0.68f) / 0.32f);
    }

    private static int lerpColor(int from, int to, float t) {
        final float clamped = clamp01(t);
        final int a = Math.round((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * clamped);
        final int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * clamped);
        final int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * clamped);
        final int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int fitScale(String text, int maxWidth, int preferredScale) {
        int scale = Math.max(1, preferredScale);
        while (scale > 1 && BitmapFont.measureString(text, scale) > maxWidth) {
            --scale;
        }
        return scale;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static GaugeRange resolveGaugeRange(StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings) {
        if (settings.getAnalogGaugeMin() < settings.getAnalogGaugeMax()) {
            return new GaugeRange(settings.getAnalogGaugeMin(), settings.getAnalogGaugeMax());
        }
        if (channel != null && channel.getStore().getSampleCount() > 0) {
            float min = channel.getStore().getMin();
            float max = channel.getStore().getMax();
            float range = max - min;
            if (range < 0.001f) {
                range = Math.max(1.0f, Math.abs(min) * 0.1f + 1.0f);
            }
            min = (channel.getStore().getMin() >= 0.0f) ? 0.0f : min - range * 0.1f;
            max += range * 0.1f;
            return new GaugeRange(min, max);
        }
        return new GaugeRange(0.0f, 100.0f);
    }

    public static void renderGaugeWidget(NativeImage image, int cx, int cy, int radius, StatsDisplayPanelBE.DataChannel channel, int slotIndex, BlockPos blockPos, DisplayTheme theme, StatsDisplaySettings settings, boolean showZones, boolean showGlass, boolean showRivets, boolean showOuterLabel) {
        renderSingleGauge(image, cx, cy, radius, channel, slotIndex, blockPos, getColorsForTheme(theme), settings, showZones, showGlass, showRivets, showOuterLabel);
    }

    public static void tickGaugeNeedle(BlockPos blockPos, int slotIndex, StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings, int steps) {
        if (blockPos == null || channel == null) {
            return;
        }
        final NeedlePhysics needle = NeedlePhysicsRegistry.getOrCreate(blockPos, slotIndex);
        needle.setTarget(computeNormalized(channel, settings) * 4.712389f);
        for (int i = 0; i < Math.max(1, steps); ++i) {
            needle.tick();
        }
    }

    public static GaugeLayout layoutGauges(int N, int W, int H) {
        if (N <= 0) {
            return new GaugeLayout(0, 0, 0, new int[0][0]);
        }
        if (N == 1) {
            final int r = Math.min(W, H) / 2 - 4;
            return new GaugeLayout(1, 1, r, new int[][] { { W / 2, H / 2 } });
        }
        int bestCols = 1;
        int bestRows = N;
        int bestRadius = 0;
        for (int cols = 1; cols <= N; ++cols) {
            final int rows = (N + cols - 1) / cols;
            final int cellW = W / cols;
            final int cellH = H / rows;
            final int cellSize = Math.min(cellW, cellH);
            final int radius = cellSize / 2 - 4;
            if (radius > bestRadius) {
                bestRadius = radius;
                bestCols = cols;
                bestRows = rows;
            }
        }
        final int cellW2 = W / bestCols;
        final int cellH2 = H / bestRows;
        final int[][] centers = new int[N][2];
        for (int i = 0; i < N; ++i) {
            final int col = i % bestCols;
            final int row = i / bestCols;
            centers[i][0] = col * cellW2 + cellW2 / 2;
            centers[i][1] = row * cellH2 + cellH2 / 2;
        }
        return new GaugeLayout(bestCols, bestRows, bestRadius, centers);
    }

    public static GaugeColorSet getColorsForTheme(DisplayTheme theme) {
        return switch (theme) {
            default -> throw new IncompatibleClassChangeError();
            case STEAMPUNK -> new GaugeColorSet(-1518964, -4684277, -9744870, -7638187, -660272, -15068666, -11912672, -15068666, -9744870, -3399168, -10088192, -12303292, -13421773, -7829368, 1140902980, 1154272256, 1154228736, 1090519039, 419430399);
            case DARK -> new GaugeColorSet(-2838729, -5601248, -10070768, -7833805, -15066578, -5588020, -10061944, -5588020, -10061944, -16711800, -16742332, -12303292, -13421773, -7829368, 1140902980, 1154272256, 1154228736, 1090519039, 285212671);
            case LIGHT -> new GaugeColorSet(-8943429, -11180374, -13417353, -10061910, -986902, -14540237, -7829351, -14540237, -7829351, -3399168, -10088192, -12303292, -11184811, -5592406, 1140902980, 1154272256, 1154228736, 822083583, 285212671);
            case CRT -> new GaugeColorSet(-16733696, -16746752, -16759808, -16755456, -16446971, -13369549, -14518494, -13369549, -14518494, -16711936, -16738048, -13421773, -14540254, -16711936, 1140915968, 1152057088, 1157575424, 536936192, 268500736);
            case BLUEPRINT -> new GaugeColorSet(-11167267, -12285748, -13408615, -11171653, -15392200, -2232577, -10057558, -2232577, -10057558, -11158529, -13399860, -12298906, -13417387, -7816227, 1146469375, 1154272256, 1157588548, 822083583, 285212671);
            case CREATE -> new GaugeColorSet(-3630276, -4684277, -7640812, -6653400, -15396338, -1518173, -8754350, -1518173, -8754350, -3630276, -7640812, -12962512, -13882320, -4689101, 1153997628, 1152938803, 1156075568, 818453308, 281582396);
        };
    }

    private static void renderSingleGauge(NativeImage image, int cx, int cy, int R, StatsDisplayPanelBE.DataChannel channel, int slotIndex, BlockPos bePos, GaugeColorSet c, StatsDisplaySettings settings, boolean showZones, boolean showGlass, boolean showRivets, boolean showOuterLabel) {
        if (R < 2) {
            return;
        }
        final boolean showDetails = R >= 30;
        final boolean showLabels = R >= 50;
        final boolean tiny = R < 20;
        float gaugeMin;
        float gaugeMax;
        if (settings.getAnalogGaugeMin() < settings.getAnalogGaugeMax()) {
            gaugeMin = settings.getAnalogGaugeMin();
            gaugeMax = settings.getAnalogGaugeMax();
        }
        else if (channel != null && channel.getStore().getSampleCount() > 0) {
            final float dataMin = channel.getStore().getMin();
            final float dataMax = channel.getStore().getMax();
            float range = dataMax - dataMin;
            if (range < 0.001f) {
                range = Math.max(1.0f, Math.abs(dataMin) * 0.1f);
            }
            gaugeMin = ((dataMin >= 0.0f) ? 0.0f : (dataMin - range * 0.1f));
            gaugeMax = dataMax + range * 0.1f;
        }
        else {
            gaugeMin = 0.0f;
            gaugeMax = 100.0f;
        }
        renderBezel(image, cx, cy, R, c, showRivets && showDetails);
        renderDialFace(image, cx, cy, R, c);
        if (showZones && showDetails) {
            renderWarningZones(image, cx, cy, R, c, settings.getAnalogZoneYellow(), settings.getAnalogZoneRed());
        }
        if (showDetails) {
            renderTickMarks(image, cx, cy, R, c, gaugeMin, gaugeMax);
        }
        if (showLabels) {
            renderNumberLabels(image, cx, cy, R, c, gaugeMin, gaugeMax);
            if (channel != null) {
                renderUnitLabel(image, cx, cy, R, c, channel);
            }
        }
        final NeedlePhysics needle = NeedlePhysicsRegistry.getOrCreate(bePos, slotIndex);
        final double internalAngle = needle.getCurrentAngle();
        final double displayAngleRad = 3.9269908169872414 - internalAngle;
        renderNeedle(image, cx, cy, R, displayAngleRad, c);
        if (showGlass && !tiny) {
            renderGlassReflection(image, cx, cy, R, c);
        }
        renderPivot(image, cx, cy, R, c);
        if (showOuterLabel && showLabels && channel != null) {
            final int textScale = (R >= 80) ? 2 : 1;
            final String label = channel.getLabel();
            final int labelY = cy + R + 2;
            if (labelY + BitmapFont.textHeight(textScale) < image.getHeight()) {
                BitmapFont.drawCentered(image, label, cx - R, labelY, 2 * R, c.numberLabel(), textScale);
            }
        }
    }

    private static void renderBezel(NativeImage image, int cx, int cy, int R, GaugeColorSet c, boolean showRivets) {
        TextureGraphRenderer.fillCircle(image, cx, cy, R, c.bezelHighlight());
        TextureGraphRenderer.fillCircle(image, cx, cy, (int)(R * 0.95), c.bezelMid());
        TextureGraphRenderer.fillCircle(image, cx, cy, (int)(R * 0.9), c.bezelShadow());
        drawArcFillBlended(image, cx, cy, (int)(R * 0.95), R, Math.toRadians(-45.0), Math.toRadians(135.0), c.bezelShadow());
        drawArcFillBlended(image, cx, cy, (int)(R * 0.88), (int)(R * 0.9), Math.toRadians(135.0), Math.toRadians(315.0), c.bezelHighlight());
        if (showRivets && R >= 40) {
            final int rivetR = Math.max(1, R / 30);
            for (int i = 0; i < 8; ++i) {
                final double angle = i * 3.141592653589793 / 4.0;
                final int rx = cx + (int)(0.93 * R * Math.cos(angle));
                final int ry = cy - (int)(0.93 * R * Math.sin(angle));
                TextureGraphRenderer.fillCircle(image, rx, ry, rivetR, c.bezelRivet());
            }
        }
    }

    private static void renderDialFace(NativeImage image, int cx, int cy, int R, GaugeColorSet c) {
        TextureGraphRenderer.fillCircle(image, cx, cy, (int)(R * 0.87), c.dialFace());
    }

    private static void renderWarningZones(NativeImage image, int cx, int cy, int R, GaugeColorSet c, float yellowThreshold, float redThreshold) {
        final int innerZoneR = (int)(0.72 * R);
        final int outerZoneR = (int)(0.86 * R);
        final double greenStart = 3.9269908169872414;
        final double greenEnd = 3.9269908169872414 - yellowThreshold * 4.712389f;
        drawArcFillBlended(image, cx, cy, innerZoneR, outerZoneR, greenStart, greenEnd, c.zoneGreen());
        final double yellowStart = greenEnd;
        final double yellowEnd = 3.9269908169872414 - redThreshold * 4.712389f;
        drawArcFillBlended(image, cx, cy, innerZoneR, outerZoneR, yellowStart, yellowEnd, c.zoneYellow());
        final double redStart = yellowEnd;
        final double redEnd = -0.7853981753223289;
        drawArcFillBlended(image, cx, cy, innerZoneR, outerZoneR, redStart, redEnd, c.zoneRed());
    }

    private static void renderTickMarks(NativeImage image, int cx, int cy, int R, GaugeColorSet c, float gaugeMin, float gaugeMax) {
        for (int i = 0; i < 11; ++i) {
            final float frac = i / 10.0f;
            final double angle = 3.9269908169872414 - frac * 4.712389f;
            final int x1 = cx + (int)(0.7 * R * Math.cos(angle));
            final int y1 = cy - (int)(0.7 * R * Math.sin(angle));
            final int x2 = cx + (int)(0.86 * R * Math.cos(angle));
            final int y2 = cy - (int)(0.86 * R * Math.sin(angle));
            TextureGraphRenderer.drawLine(image, x1, y1, x2, y2, c.tickMajor());
            if (R >= 50) {
                final double perpX = -Math.sin(angle);
                final double perpY = -Math.cos(angle);
                TextureGraphRenderer.drawLine(image, x1 + (int)perpX, y1 + (int)perpY, x2 + (int)perpX, y2 + (int)perpY, c.tickMajor());
            }
        }
        if (R >= 30) {
            for (int i = 0; i < 10; ++i) {
                for (int j = 1; j <= 4; ++j) {
                    final float frac2 = (i * 5 + j) / 50.0f;
                    final double angle2 = 3.9269908169872414 - frac2 * 4.712389f;
                    final int x3 = cx + (int)(0.76 * R * Math.cos(angle2));
                    final int y3 = cy - (int)(0.76 * R * Math.sin(angle2));
                    final int x4 = cx + (int)(0.86 * R * Math.cos(angle2));
                    final int y4 = cy - (int)(0.86 * R * Math.sin(angle2));
                    TextureGraphRenderer.drawLine(image, x3, y3, x4, y4, c.tickMinor());
                }
            }
        }
    }

    private static void renderNumberLabels(NativeImage image, int cx, int cy, int R, GaugeColorSet c, float gaugeMin, float gaugeMax) {
        final int textScale = (R >= 80) ? 2 : 1;
        for (int i = 0; i < 11; ++i) {
            if (R >= 50 || i % 2 == 0 || i == 10) {
                final float frac = i / 10.0f;
                final float value = gaugeMin + frac * (gaugeMax - gaugeMin);
                final String label = formatCompact(value, gaugeMax - gaugeMin);
                final double angle = 3.9269908169872414 - frac * 4.712389f;
                final int lx = cx + (int)(0.58 * R * Math.cos(angle));
                final int ly = cy - (int)(0.58 * R * Math.sin(angle));
                final int textW = BitmapFont.measureString(label, textScale);
                final int textH = BitmapFont.textHeight(textScale);
                BitmapFont.drawString(image, label, lx - textW / 2, ly - textH / 2, c.numberLabel(), textScale);
            }
        }
    }

    private static void renderUnitLabel(NativeImage image, int cx, int cy, int R, GaugeColorSet c, StatsDisplayPanelBE.DataChannel channel) {
        final String unit = channel.getStore().getMetricType().getUnit();
        if (!unit.isEmpty()) {
            final int textScale = (R >= 80) ? 2 : 1;
            BitmapFont.drawCentered(image, unit, cx - R, cy + (int)(0.3 * R), 2 * R, c.unitLabel(), textScale);
        }
    }

    private static List<ChannelView> collectOrderedChannels(StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel primaryChannel, StatsDisplaySettings settings) {
        final ArrayList<ChannelView> visible = new ArrayList<ChannelView>();
        final String primarySourceId = (primaryChannel != null) ? extractSourceId(primaryChannel.getId()) : null;
        int visibleIndex = 0;
        for (StatsDisplayPanelBE.DataChannel channel : be.getActiveChannels()) {
            if (settings.isChannelHidden(channel.getId())) {
                continue;
            }
            if (primarySourceId == null || primarySourceId.equals(extractSourceId(channel.getId()))) {
                visible.add(new ChannelView(channel, visibleIndex));
            }
            ++visibleIndex;
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

    private static void renderNeedle(NativeImage image, int cx, int cy, int R, double displayAngleRad, GaugeColorSet c) {
        final double cos = Math.cos(displayAngleRad);
        final double sin = Math.sin(displayAngleRad);
        final float tipLen = 0.86f * R;
        final float baseHalfW = Math.max(1.0f, R / 40.0f);
        final float cwDist = 0.12f * R;
        final int cwRadius = Math.max(2, R / 20);
        final int tipX = cx + (int)(tipLen * cos);
        final int tipY = cy - (int)(tipLen * sin);
        final int base1X = cx + (int)(baseHalfW * -sin);
        final int base1Y = cy - (int)(baseHalfW * cos);
        final int base2X = cx + (int)(baseHalfW * sin);
        final int base2Y = cy - (int)(baseHalfW * -cos);
        TextureGraphRenderer.fillTriangle(image, tipX, tipY, base1X, base1Y, base2X, base2Y, c.needleBody());
        TextureGraphRenderer.drawLine(image, tipX, tipY, base1X, base1Y, c.needleBorder());
        TextureGraphRenderer.drawLine(image, tipX, tipY, base2X, base2Y, c.needleBorder());
        TextureGraphRenderer.drawLine(image, base1X, base1Y, base2X, base2Y, c.needleBorder());
        final double oppAngle = displayAngleRad + 3.141592653589793;
        final int cwX = cx + (int)(cwDist * Math.cos(oppAngle));
        final int cwY = cy - (int)(cwDist * Math.sin(oppAngle));
        TextureGraphRenderer.fillCircle(image, cwX, cwY, cwRadius, c.needleCounterWeight());
    }

    private static void renderGlassReflection(NativeImage image, int cx, int cy, int R, GaugeColorSet c) {
        final int dialR = (int)(0.87 * R);
        drawCircleBlended(image, cx, cy, dialR, c.glassEdgeTint());
        drawArcFillBlended(image, cx, cy, (int)(0.2 * R), (int)(0.75 * R), Math.toRadians(120.0), Math.toRadians(210.0), c.glassReflection());
    }

    private static void renderPivot(NativeImage image, int cx, int cy, int R, GaugeColorSet c) {
        final int pivotR = Math.max(2, (int)(0.07 * R));
        TextureGraphRenderer.fillCircle(image, cx, cy, pivotR, c.pivotCenter());
        TextureGraphRenderer.fillCircle(image, cx - 1, cy - 1, Math.max(1, pivotR / 3), c.pivotHighlight());
    }

    private static void drawCircleBlended(NativeImage image, int cx, int cy, int r, int argbColor) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int rSq = r * r;
        for (int py = Math.max(0, cy - r); py <= Math.min(imgH - 1, cy + r); ++py) {
            for (int px = Math.max(0, cx - r); px <= Math.min(imgW - 1, cx + r); ++px) {
                final int dx = px - cx;
                final int dy = py - cy;
                if (dx * dx + dy * dy <= rSq) {
                    TextureGraphRenderer.setPixelBlended(image, px, py, argbColor);
                }
            }
        }
    }

    static void drawArcFillBlended(NativeImage image, int cx, int cy, int innerR, int outerR, double startAngle, double endAngle, int argbColor) {
        final int imgW = image.getWidth();
        for (int imgH = image.getHeight(), py = Math.max(0, cy - outerR); py <= Math.min(imgH - 1, cy + outerR); ++py) {
            for (int px = Math.max(0, cx - outerR); px <= Math.min(imgW - 1, cx + outerR); ++px) {
                final int dx = px - cx;
                final int dy = cy - py;
                final double distSq = dx * dx + dy * dy;
                if (distSq >= innerR * (long)innerR) {
                    if (distSq <= outerR * (long)outerR) {
                        double angle = Math.atan2(dy, dx);
                        if (angle < 0.0) {
                            angle += 6.283185307179586;
                        }
                        double sa = startAngle;
                        double ea = endAngle;
                        while (sa < 0.0) {
                            sa += 6.283185307179586;
                        }
                        while (ea < 0.0) {
                            ea += 6.283185307179586;
                        }
                        sa %= 6.283185307179586;
                        ea %= 6.283185307179586;
                        boolean inArc;
                        if (sa <= ea) {
                            inArc = (angle >= sa && angle <= ea);
                        }
                        else {
                            inArc = (angle >= ea || angle <= sa);
                        }
                        if (inArc) {
                            TextureGraphRenderer.setPixelBlended(image, px, py, argbColor);
                        }
                    }
                }
            }
        }
    }

    private static String formatCompact(float value, float range) {
        final float abs = Math.abs(value);
        if (abs >= 1000000.0f) {
            return String.format("%.1fM", value / 1000000.0f);
        }
        if (abs >= 1000.0f) {
            return String.format("%.1fk", value / 1000.0f);
        }
        if (abs >= 100.0f || range >= 100.0f) {
            return String.format("%.0f", value);
        }
        if (abs >= 10.0f) {
            return String.format("%.1f", value);
        }
        if (abs >= 1.0f) {
            return String.format("%.1f", value);
        }
        return String.format("%.2f", value);
    }

    private static float computeNormalized(StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings) {
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
        return Math.max(0.0f, Math.min(1.0f, (value - min) / range));
    }

    private record ChannelView(StatsDisplayPanelBE.DataChannel channel, int slotIndex) {}

    private record CardLayout(int cols, int rows) {}

    private record GaugeRange(float min, float max) {}
}
