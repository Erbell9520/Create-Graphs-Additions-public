package com.createstats.renderer;

import com.createstats.efficiency.GradeHistory;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyDimension;
import java.util.List;
import com.createstats.data.AnnotationManager;
import com.createstats.data.Annotation;
import java.util.ArrayList;
import com.createstats.prediction.LinearRegression;
import com.createstats.prediction.PredictionResult;
import com.createstats.prediction.PredictionFormatter;
import com.createstats.prediction.PredictionEngine;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import java.util.Iterator;
import java.util.LinkedHashMap;
import com.createstats.blockentity.SourceConfig;
import java.util.Map;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.display.DisplayTheme;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.renderer.instrument.InstrumentRenderDispatcher;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TextureGraphRenderer
{
    public static final int[] CHANNEL_COLORS;
    
    private TextureGraphRenderer() {
    }
    
    public static void renderPage(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel channel, int channelIdx, int pageIdx, int totalPages) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final StatsDisplaySettings settings = be.getSettings();
        final DisplayTheme theme = settings.getDisplayTheme();
        final StatsDisplaySettings.GraphType graphType = settings.getGraphType();
        fillRect(image, 0, 0, w, h, theme.graphBg);
        drawBorder(image, 0, 0, w, h, 2, theme.border);
        if (theme == DisplayTheme.CREATE) {
            final int margin0 = Math.max(3, w / 64);
            CreateThemeDecorator.decorate(image, theme.graphBg, margin0 + 2, margin0 + 2, w - 2 * (margin0 + 2), h - 2 * (margin0 + 2));
        }
        if (channel != null && channel.isAlertTriggered()) {
            final int alertColor = -56798;
            drawBorder(image, 1, 1, w - 2, h - 2, 2, alertColor);
        }
        final int margin2 = Math.max(3, w / 64);
        final int contentX = margin2 + 2;
        final int contentY = margin2 + 2;
        final int contentW = w - 2 * (margin2 + 2);
        final int contentH = h - 2 * (margin2 + 2);
        if (graphType == StatsDisplaySettings.GraphType.EFFICIENCY) {
            renderEfficiencyFullPage(image, be, contentX, contentY, contentW, contentH, theme);
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.TOPOLOGY) {
            final int headerH = renderTopologyHeader(image, contentX, contentY, contentW, theme, image.getWidth());
            final int graphY = contentY + headerH + 2;
            final int graphH = contentH - headerH - 4;
            renderGraph(image, channel, channelIdx, graphType, contentX, graphY, contentW, graphH, theme, settings);
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            if (settings.getTrainMapRenderQuality() == StatsDisplaySettings.TrainMapRenderQuality.HIGH) {
                TrainMapPanelRenderer.renderHighQualityTrainMap(image, be, contentX, contentY, contentW, contentH, theme, settings);
            }
            else {
                TrainMapPanelRenderer.renderTrainMap(image, be, contentX, contentY, contentW, contentH, theme, settings);
            }
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.PRODUCTION) {
            renderPanelProductionSummary(image, be, contentX, contentY, contentW, contentH, theme, settings);
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.CAPACITY) {
            renderPanelCapacityAll(image, be, contentX, contentY, contentW, contentH, theme, settings);
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.INVENTORY_STATUS) {
            renderPanelInventoryStatus(image, be, contentX, contentY, contentW, contentH, theme, settings);
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
            renderPanelFactoryStatus(image, be, contentX, contentY, contentW, contentH, theme, settings);
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.ANALOG_GAUGE) {
            if (channel == null) {
                renderNoData(image, be, contentX, contentY, contentW, contentH, theme);
            }
            else {
                InstrumentRenderDispatcher.renderAnalogPanel(image, be, channel, contentX, contentY, contentW, contentH, theme, settings, pageIdx, totalPages);
            }
        }
        else if (channel == null || channel.getStore().getSampleCount() == 0) {
            renderNoData(image, be, contentX, contentY, contentW, contentH, theme);
        }
        else {
            final int headerH = renderHeader(image, be, channel, contentX, contentY, contentW, theme, settings);
            int dotsH2 = 0;
            if (totalPages > 1) {
                dotsH2 = Math.max(6, h / 32);
                renderPageDots(image, pageIdx, totalPages, contentX, contentY + contentH - dotsH2, contentW, dotsH2, theme);
            }
            final int graphY2 = contentY + headerH + 2;
            final int graphH2 = contentH - headerH - dotsH2 - 4;
            if (graphType == StatsDisplaySettings.GraphType.CAPACITY) {
                renderPanelCapacity(image, be, channel, contentX, graphY2, contentW, graphH2, theme, settings);
            }
            else {
                renderGraph(image, channel, channelIdx, graphType, contentX, graphY2, contentW, graphH2, theme, settings);
            }
            renderAnnotations(image, be, contentX, graphY2, contentW, graphH2, theme, getTextScale(image.getWidth()), settings);
            if (settings.showValueOverlay()) {
                final String label = channel.getLabel();
                final String value = channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue());
                final String overlayText = label + ": " + value;
                final int overlayScale = fitScale(overlayText, contentW - 4, Math.max(1, Math.min(2, contentW / 120)));
                final int textH = BitmapFont.textHeight(overlayScale);
                final int stripY = graphY2 + graphH2 / 2 - textH / 2 - 2;
                final int bgColor = BitmapFont.argbToAbgr(0xCC000000 | (theme.graphBg & 0xFFFFFF));
                for (int py = stripY; py < stripY + textH + 4; ++py) {
                    for (int px = contentX; px < contentX + contentW; ++px) {
                        if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) {
                            image.setPixelRGBA(px, py, bgColor);
                        }
                    }
                }
                BitmapFont.drawCentered(image, overlayText, contentX, stripY + 2, contentW, theme.text, overlayScale);
            }
        }
    }
    
    public static void renderProjectorPage(NativeImage image, HolographicProjectorBE.DataChannel projChannel, int channelIdx, int pageIdx, int totalPages, DisplayTheme theme, StatsDisplaySettings settings) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        fillRect(image, 0, 0, w, h, theme.graphBg);
        drawBorder(image, 0, 0, w, h, 2, theme.border);
        if (theme == DisplayTheme.CREATE) {
            final int m = Math.max(3, w / 64);
            CreateThemeDecorator.decorate(image, theme.graphBg, m + 2, m + 2, w - 2 * (m + 2), h - 2 * (m + 2));
        }
        final int margin = Math.max(3, w / 64);
        final int contentX = margin + 2;
        final int contentY = margin + 2;
        final int contentW = w - 2 * (margin + 2);
        final int contentH = h - 2 * (margin + 2);
        if (projChannel == null || projChannel.getStore().getSampleCount() == 0) {
            final int scale = getTextScale(w);
            BitmapFont.drawCentered(image, "No Data", contentX, contentY + contentH / 2 - BitmapFont.textHeight(scale) / 2, contentW, theme.dim, scale);
            return;
        }
        final StatsDisplayPanelBE.DataChannel adapted = StatsDisplayPanelBE.DataChannel.ofStore(projChannel.getId(), projChannel.getLabel(), projChannel.getStore());
        final int scale2 = getTextScale(w);
        final int lineH = BitmapFont.textHeight(scale2) + 2;
        final String value = projChannel.getStore().getMetricType().formatValue(projChannel.getStore().getLatestValue());
        final String trend = getTrendArrow(projChannel.getStore(), settings);
        BitmapFont.drawString(image, truncate(projChannel.getLabel(), contentW / 2, scale2), contentX, contentY, theme.text, scale2);
        BitmapFont.drawRight(image, value + " " + trend, contentX + contentW, contentY, theme.border, scale2);
        final int headerH = lineH + 4;
        int dotsH = 0;
        if (totalPages > 1) {
            dotsH = Math.max(6, h / 32);
            renderPageDots(image, pageIdx, totalPages, contentX, contentY + contentH - dotsH, contentW, dotsH, theme);
        }
        final int graphY = contentY + headerH;
        final int graphH = contentH - headerH - dotsH - 2;
        if (graphH < 8) {
            return;
        }
        renderGraph(image, adapted, channelIdx, settings.getGraphType(), contentX, graphY, contentW, graphH, theme, settings);
    }
    
    public static int renderHeader(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel channel, int x, int y, int w, DisplayTheme theme, StatsDisplaySettings settings) {
        final int baseScale = getTextScale(image.getWidth());
        String sourceName = "";
        final String channelId = channel.getId();
        final LinkedHashMap<String, SourceConfig> sources = be.getSourceRegistry();
        for (Map.Entry<String, SourceConfig> entry : sources.entrySet()) {
            final String prefix = "src_" + (String)entry.getKey();
            if (channelId.startsWith(prefix)) {
                sourceName = entry.getValue().getDisplayLabel();
                break;
            }
        }
        if (sourceName.isEmpty()) {
            sourceName = be.getSourceType();
        }
        if (!sourceName.isEmpty()) {
            final int sourceScale = fitScale(sourceName, w, baseScale);
            final String truncated = truncate(sourceName, w, sourceScale);
            BitmapFont.drawString(image, truncated, x, y, theme.border, sourceScale);
        }
        final int sourceLineH = BitmapFont.textHeight(fitScale(sourceName.isEmpty() ? " " : sourceName, w, baseScale)) + 2;
        final String label = channel.getLabel();
        final TimeSeriesStore store = channel.getStore();
        final String value = store.getMetricType().formatValue(store.getLatestValue());
        final String trend = getTrendArrow(store, settings);
        final String valueText = value + " " + trend;
        int color = TextureGraphRenderer.CHANNEL_COLORS[0];
        if (channel.isAlertTriggered()) {
            color = -48060;
        }
        final int labelScale = fitScale(label, w / 2, baseScale);
        final int valueScale = fitScale(valueText, w / 2, baseScale);
        final int lineY = y + sourceLineH;
        final String truncLabel = truncate(label, w / 2, labelScale);
        BitmapFont.drawString(image, truncLabel, x, lineY, theme.text, labelScale);
        BitmapFont.drawRight(image, valueText, x + w, lineY, color, valueScale);
        return sourceLineH + Math.max(BitmapFont.textHeight(labelScale), BitmapFont.textHeight(valueScale)) + 4;
    }
    
    private static int renderTopologyHeader(NativeImage image, int x, int y, int w, DisplayTheme theme, int imageWidth) {
        final int scale = getTextScale(imageWidth);
        final int lineH = BitmapFont.textHeight(scale) + 2;
        BitmapFont.drawString(image, "Network Topology", x, y, theme.border, scale);
        return lineH + 2;
    }
    
    public static void renderGraph(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int channelIdx, StatsDisplaySettings.GraphType type, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        if (type == StatsDisplaySettings.GraphType.LINE_CHART) {
            renderLineChart(image, channel, channelIdx, x, y, w, h, theme, settings);
            return;
        }
        if (type == StatsDisplaySettings.GraphType.BAR_CHART) {
            renderBarChart(image, channel, channelIdx, x, y, w, h, theme, settings);
            return;
        }
        if (type == StatsDisplaySettings.GraphType.GAUGE) {
            renderGauge(image, channel, channelIdx, x, y, w, h, theme, settings);
            return;
        }
        if (type == StatsDisplaySettings.GraphType.NUMERIC) {
            renderNumeric(image, channel, channelIdx, x, y, w, h, theme, settings);
            return;
        }
        if (type == StatsDisplaySettings.GraphType.CAPACITY) {
            renderCapacity(image, channel, x, y, w, h, theme, settings);
            return;
        }
        if (type == StatsDisplaySettings.GraphType.PRODUCTION) {
            renderProduction(image, channel, x, y, w, h, theme, settings);
            return;
        }
        if (type == StatsDisplaySettings.GraphType.INVENTORY_STATUS) {
            return; // handled by renderPanelInventoryStatus via renderPage()
        }
        if (type == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
            return; // handled by renderPanelFactoryStatus via renderPage()
        }
        if (type == StatsDisplaySettings.GraphType.TOPOLOGY) {
            TopologyRenderer.renderTopology(image, x, y, w, h, theme, settings);
        }
    }
    
    private static void renderLineChart(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int channelIdx, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TimeSeriesStore store = channel.getStore();
        final float[] data = getVisibleData(store, settings);
        if (data.length < 2) {
            return;
        }
        final int scale = getTextScale(image.getWidth());
        final int color = TextureGraphRenderer.CHANNEL_COLORS[channelIdx % TextureGraphRenderer.CHANNEL_COLORS.length];
        final int marginL = settings.showAxisLabels() ? Math.max(30, w / 6) : 4;
        final int marginB = settings.showAxisLabels() ? (BitmapFont.textHeight(scale) + 4) : 2;
        final int chartX = x + marginL;
        final int chartY = y;
        final int chartW = w - marginL - 2;
        final int chartH = h - marginB - 2;
        float dataMin = Float.MAX_VALUE;
        float dataMax = -3.4028235E38f;
        for (float v : data) {
            dataMin = Math.min(dataMin, v);
            dataMax = Math.max(dataMax, v);
        }
        final MetricType mt = store.getMetricType();
        float displayMin;
        float displayMax;
        if (mt == MetricType.STRESS_PERCENT || mt == MetricType.FILL_LEVEL) {
            displayMin = 0.0f;
            displayMax = 100.0f;
        }
        else if (mt == MetricType.STRESS_SU) {
            displayMin = 0.0f;
            displayMax = Math.max(5000.0f, dataMax);
            final float range = displayMax - displayMin;
            final float padding = range * 0.15f;
            displayMax += padding;
        }
        else {
            float range = dataMax - dataMin;
            if (range < 0.001f) {
                range = Math.max(1.0f, Math.abs(dataMin) * 0.1f);
            }
            final float padding = range * 0.15f;
            displayMin = dataMin - padding;
            displayMax = dataMax + padding;
            if (dataMin >= 0.0f && displayMin < 0.0f) {
                displayMin = 0.0f;
            }
        }
        float niceMin;
        float niceMax;
        if (settings.isAutoScale()) {
            final float niceStep = niceNumber((displayMax - displayMin) / 4.0f);
            niceMin = (float)(Math.floor(displayMin / niceStep) * niceStep);
            niceMax = (float)(Math.ceil(displayMax / niceStep) * niceStep);
            if (dataMin >= 0.0f && niceMin < 0.0f) {
                niceMin = 0.0f;
            }
            if (niceMax <= niceMin) {
                niceMax = niceMin + niceStep;
            }
        }
        else {
            niceMin = 0.0f;
            float ceiling = Math.max(displayMax, dataMax * 1.1f);
            if (ceiling <= 0.0f) {
                ceiling = 1.0f;
            }
            final float niceStep2 = niceNumber(ceiling / 4.0f);
            niceMax = (float)(Math.ceil(ceiling / niceStep2) * niceStep2);
            if (niceMax <= 0.0f) {
                niceMax = niceStep2;
            }
        }
        if (settings.showGrid()) {
            drawGrid(image, chartX, chartY, chartW, chartH, 4, 6, theme.grid);
        }
        drawHLine(image, chartX, chartY + chartH, chartW, theme.dim);
        drawVLine(image, chartX, chartY, chartH, theme.dim);
        PredictionResult prediction = null;
        int futureSamples = 0;
        if (settings.isPredictionEnabled()) {
            final long currentTick = System.currentTimeMillis() / 50L;
            final double alertThreshold = channel.isAlertEnabled() ? channel.getAlertThreshold() : Double.NaN;
            prediction = PredictionEngine.computePrediction(channel.getId(), store, store.getMetricType(), store.getPredictionConfig(), settings, currentTick, alertThreshold);
            if (prediction != null && prediction.isRenderable()) {
                futureSamples = prediction.predictionSamples();
            }
        }
        final float range2 = niceMax - niceMin;
        final int totalRange = (futureSamples > 0) ? (data.length + futureSamples - 1) : (data.length - 1);
        for (int i = 0; i < data.length - 1; ++i) {
            final float t1 = i / (float)totalRange;
            final float t2 = (i + 1) / (float)totalRange;
            final float v2 = Math.max(0.0f, Math.min(1.0f, (data[i] - niceMin) / range2));
            final float v3 = Math.max(0.0f, Math.min(1.0f, (data[i + 1] - niceMin) / range2));
            final int px1 = chartX + (int)(t1 * chartW);
            final int py1 = chartY + chartH - (int)(v2 * chartH);
            final int px2 = chartX + (int)(t2 * chartW);
            final int py2 = chartY + chartH - (int)(v3 * chartH);
            drawLineAA(image, px1, py1, px2, py2, color);
        }
        if (prediction != null && futureSamples > 0) {
            renderConfidenceBand(image, prediction, data, chartX, chartY, chartW, chartH, niceMin, niceMax, color, futureSamples, store.getPredictionConfig().getTCritical());
            final int nowPx = chartX + (int)((data.length - 1) / (double)totalRange * chartW);
            drawDashedLine(image, nowPx, chartY, nowPx, chartY + chartH, withAlpha(theme.accent, 136), 3, 2);
            renderPredictionLine(image, prediction, data, chartX, chartY, chartW, chartH, niceMin, niceMax, color, futureSamples);
        }
        if (settings.showAxisLabels()) {
            for (int numLabels = 5, j = 0; j < numLabels; ++j) {
                final float frac = j / (float)(numLabels - 1);
                final float val = niceMax - frac * range2;
                final int ly = chartY + (int)(frac * chartH);
                final String label = formatCompact(val, range2);
                BitmapFont.drawRight(image, label, chartX - 2, ly - BitmapFont.textHeight(scale) / 2, theme.dim, scale);
            }
        }
        if (settings.showAxisLabels()) {
            final float secondsPerSample = store.getSampleIntervalTicks() / 20.0f;
            final float totalSeconds = Math.max(0, data.length - 1) * secondsPerSample;
            final int labelY = chartY + chartH + 2;
            BitmapFont.drawRight(image, "now", chartX + chartW, labelY, theme.dim, scale);
            if (totalSeconds > 30.0f) {
                BitmapFont.drawCentered(image, formatTimeLabel(totalSeconds / 2.0f), chartX, labelY, chartW, theme.dim, scale);
            }
            if (totalSeconds > 60.0f) {
                BitmapFont.drawString(image, formatTimeLabel(totalSeconds), chartX, labelY, theme.dim, scale);
            }
        }
        if (settings.showMinMaxAvg() && data.length > 5) {
            String stats = "Min:" + formatCompact(dataMin, 0.0f) + " Max:" + formatCompact(dataMax, 0.0f) + " Avg:" + formatCompact(average(data), 0.0f);
            if (BitmapFont.measureString(stats, scale) > chartW - 2) {
                stats = "Lo:" + formatCompact(dataMin, 0.0f) + " Hi:" + formatCompact(dataMax, 0.0f) + " Avg:" + formatCompact(average(data), 0.0f);
            }
            if (BitmapFont.measureString(stats, scale) > chartW - 2) {
                stats = "Avg:" + formatCompact(average(data), 0.0f);
            }
            final int statsY = chartY + chartH - BitmapFont.textHeight(scale) - 1;
            BitmapFont.drawRight(image, stats, chartX + chartW - 1, statsY, withAlpha(theme.dim, 170), scale);
        }
        if (prediction != null) {
            if (prediction.isRenderable()) {
                final String r2Text = PredictionFormatter.formatRSquared(prediction);
                final int r2Color = PredictionFormatter.getRSquaredColor(prediction.rSquared());
                BitmapFont.drawRight(image, r2Text, chartX + chartW - 1, chartY + 1, r2Color, scale);
            }
            if (prediction.hasTimeWarning()) {
                final String warning = PredictionFormatter.formatCompact(prediction);
                final int warnColor = -13312;
                final int textY = chartY + chartH - BitmapFont.textHeight(scale) * 3 - 3;
                BitmapFont.drawCentered(image, warning, chartX, textY, chartW, warnColor, scale);
            }
        }
    }
    
    private static void renderPredictionLine(NativeImage image, PredictionResult pred, float[] data, int chartX, int chartY, int chartW, int chartH, float yMin, float yMax, int color, int futureSamples) {
        final int n = data.length;
        final int totalRange = n + futureSamples - 1;
        final float range = yMax - yMin;
        if (range < 0.001f) {
            return;
        }
        if (futureSamples < 1) {
            return;
        }
        final int predColor = withAlpha(color, 204);
        for (int segments = Math.max(1, Math.min(futureSamples, chartW / 2)), i = 0; i < segments; ++i) {
            final double xIdx0 = n - 1 + i * (double)futureSamples / segments;
            final double xIdx2 = n - 1 + (i + 1) * (double)futureSamples / segments;
            final double yPred0 = Math.max(yMin, Math.min(yMax, pred.slope() * xIdx0 + pred.intercept()));
            final double yPred2 = Math.max(yMin, Math.min(yMax, pred.slope() * xIdx2 + pred.intercept()));
            final int px0 = Math.max(chartX, Math.min(chartX + chartW, chartX + (int)(xIdx0 / totalRange * chartW)));
            final int py0 = Math.max(chartY, Math.min(chartY + chartH, chartY + chartH - (int)((yPred0 - yMin) / range * chartH)));
            final int px2 = Math.max(chartX, Math.min(chartX + chartW, chartX + (int)(xIdx2 / totalRange * chartW)));
            final int py2 = Math.max(chartY, Math.min(chartY + chartH, chartY + chartH - (int)((yPred2 - yMin) / range * chartH)));
            drawDashedLine(image, px0, py0, px2, py2, predColor, 4, 3);
        }
    }
    
    private static void renderConfidenceBand(NativeImage image, PredictionResult pred, float[] data, int chartX, int chartY, int chartW, int chartH, float yMin, float yMax, int color, int futureSamples, double tCritical) {
        final int n = data.length;
        final int totalRange = n + futureSamples - 1;
        final float range = yMax - yMin;
        if (range < 0.001f) {
            return;
        }
        final int bandArgb = withAlpha(color, 48);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final LinearRegression.RegressionCoefficients coeffs = new LinearRegression.RegressionCoefficients(pred.slope(), pred.intercept(), pred.rSquared(), pred.standardError(), pred.Sxx(), pred.xBar(), pred.sampleCount());
        final int futureStartPx = chartX + (int)((n - 1) / (double)totalRange * chartW);
        for (int futureEndPx = chartX + chartW, px = futureStartPx; px < futureEndPx && px < imgW; ++px) {
            final double xIdx = (px - chartX) / (double)chartW * totalRange;
            final double yPred = pred.slope() * xIdx + pred.intercept();
            final double halfWidth = LinearRegression.predictionIntervalHalfWidth(coeffs, xIdx, tCritical);
            final double yUpper = Math.max(yMin, Math.min(yMax, yPred + halfWidth));
            final double yLower = Math.max(yMin, Math.min(yMax, yPred - halfWidth));
            final int pyUpper = chartY + chartH - (int)((yUpper - yMin) / range * chartH);
            final int pyLower = chartY + chartH - (int)((yLower - yMin) / range * chartH);
            final int pyTop = Math.max(chartY, Math.min(pyUpper, pyLower));
            for (int pyBot = Math.min(chartY + chartH, Math.max(pyUpper, pyLower)), py = pyTop; py <= pyBot; ++py) {
                if (py >= 0 && py < imgH && px >= 0) {
                    image.setPixelRGBA(px, py, blendPixelAbgr(image, px, py, bandArgb));
                }
            }
        }
    }
    
    private static int blendPixelAbgr(NativeImage image, int px, int py, int newArgb) {
        final int newAbgr = BitmapFont.argbToAbgr(newArgb);
        final int existing = image.getPixelRGBA(px, py);
        final int newA = newAbgr >> 24 & 0xFF;
        final int invA = 255 - newA;
        final int r = ((newAbgr & 0xFF) * newA + (existing & 0xFF) * invA) / 255;
        final int g = ((newAbgr >> 8 & 0xFF) * newA + (existing >> 8 & 0xFF) * invA) / 255;
        final int b = ((newAbgr >> 16 & 0xFF) * newA + (existing >> 16 & 0xFF) * invA) / 255;
        final int a = Math.max(existing >> 24 & 0xFF, newA);
        return a << 24 | b << 16 | g << 8 | r;
    }
    
    private static void drawDashedLine(NativeImage image, int x0, int y0, int x1, int y1, int argbColor, int dashLen, int gapLen) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int dx = Math.abs(x1 - x0);
        final int dy = Math.abs(y1 - y0);
        final int sx = (x0 < x1) ? 1 : -1;
        final int sy = (y0 < y1) ? 1 : -1;
        int err = dx - dy;
        final int patternLen = dashLen + gapLen;
        int pixelCount = 0;
        while (true) {
            final boolean draw = pixelCount % patternLen < dashLen;
            if (draw && x0 >= 0 && x0 < imgW && y0 >= 0 && y0 < imgH) {
                image.setPixelRGBA(x0, y0, abgr);
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
            ++pixelCount;
        }
    }
    
    private static void renderBarChart(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int channelIdx, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TimeSeriesStore store = channel.getStore();
        final float[] data = getVisibleData(store, settings);
        if (data.length == 0) {
            return;
        }
        final int color = TextureGraphRenderer.CHANNEL_COLORS[channelIdx % TextureGraphRenderer.CHANNEL_COLORS.length];
        final int maxBars = Math.min(20, w / 6);
        final int start = Math.max(0, data.length - maxBars);
        final int count = data.length - start;
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (int i = start; i < data.length; ++i) {
            min = Math.min(min, data[i]);
            max = Math.max(max, data[i]);
        }
        if (min >= 0.0f) {
            min = 0.0f;
        }
        float range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
        }
        final int barW = Math.max(2, (w - 4) / count);
        final int gap = Math.max(1, barW / 5);
        for (int j = 0; j < count; ++j) {
            final float norm = Math.max(0.02f, Math.min(1.0f, (data[start + j] - min) / range));
            final int barH = (int)(norm * (h - 4));
            final int bx = x + 2 + j * barW;
            final int by = y + h - 2 - barH;
            fillRect(image, bx, by, barW - gap, barH, color);
        }
        drawHLine(image, x + 2, y + h - 2, w - 4, theme.dim);
        drawVLine(image, x + 2, y, h - 2, theme.dim);
    }
    
    private static void renderGauge(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int channelIdx, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TimeSeriesStore store = channel.getStore();
        final float latest = store.getLatestValue();
        int color = TextureGraphRenderer.CHANNEL_COLORS[channelIdx % TextureGraphRenderer.CHANNEL_COLORS.length];
        if (channel.isAlertTriggered()) {
            color = -48060;
        }
        final float[] data = getVisibleData(store, settings);
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        final float range = max - min;
        float norm = (range > 0.001f) ? ((latest - min) / range) : 0.5f;
        norm = Math.max(0.0f, Math.min(1.0f, norm));
        final int cx = x + w / 2;
        final int cy = y + (int)(h * 0.6f);
        final int outerR = Math.min(w / 2, (int)(h * 0.5f));
        final int innerR = (int)(outerR * 0.65f);
        drawArcFill(image, cx, cy, innerR, outerR, 3.141592653589793, 0.0, theme.grid);
        final double valueAngle = 3.141592653589793 * (1.0f - norm);
        drawArcFill(image, cx, cy, innerR, outerR, 3.141592653589793, valueAngle, color);
        final int scale = getTextScale(image.getWidth());
        final int bigScale = scale + 1;
        final String value = store.getMetricType().formatValue(latest);
        BitmapFont.drawCentered(image, value, x, cy - BitmapFont.textHeight(bigScale) / 2, w, color, bigScale);
    }
    
    private static void renderNumeric(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int channelIdx, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TimeSeriesStore store = channel.getStore();
        int color = TextureGraphRenderer.CHANNEL_COLORS[channelIdx % TextureGraphRenderer.CHANNEL_COLORS.length];
        if (channel.isAlertTriggered()) {
            color = -48060;
        }
        final String value = store.getMetricType().formatValue(store.getLatestValue());
        final String trend = getTrendArrow(store, settings);
        final String unit = store.getMetricType().getUnit();
        final int scale = getTextScale(image.getWidth());
        final int bigScale = Math.max(2, scale * 2);
        final int valueH = BitmapFont.textHeight(bigScale);
        BitmapFont.drawCentered(image, value, x, y + h / 2 - valueH, w, color, bigScale);
        BitmapFont.drawCentered(image, trend, x, y + h / 2 + 4, w, theme.dim, scale + 1);
        if (!unit.isEmpty()) {
            BitmapFont.drawCentered(image, unit, x, y + h - BitmapFont.textHeight(scale) - 2, w, theme.dim, scale);
        }
    }
    
    private static void renderCapacity(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TimeSeriesStore store = channel.getStore();
        final float latest = store.getLatestValue();
        final float[] data = getVisibleData(store, settings);
        float maxVal = -3.4028235E38f;
        for (float v : data) {
            maxVal = Math.max(maxVal, v);
        }
        if (maxVal < 0.001f) {
            maxVal = 100.0f;
        }
        final float pct = Math.min(1.0f, latest / maxVal);
        final int pctColor = (pct > 0.9f) ? -48060 : ((pct > 0.7f) ? -13312 : -16711800);
        final int scale = getTextScale(image.getWidth());
        final int bigScale = scale + 1;
        final String pctText = String.format("%.1f%%", pct * 100.0f);
        BitmapFont.drawCentered(image, pctText, x, y + h / 4, w, pctColor, bigScale + 1);
        final int barH = Math.max(8, h / 8);
        final int barY = y + h / 2;
        final int barMargin = w / 10;
        fillRect(image, x + barMargin, barY, w - 2 * barMargin, barH, theme.grid);
        fillRect(image, x + barMargin, barY, (int)((w - 2 * barMargin) * pct), barH, pctColor);
        final String valText = formatCompact(latest, 0.0f) + " / " + formatCompact(maxVal, 0.0f);
        BitmapFont.drawCentered(image, valText, x, barY + barH + 4, w, theme.text, scale);
    }

    private static void renderPanelCapacity(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel channel, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        if (channel == null) {
            renderNoData(image, be, x, y, w, h, theme);
            return;
        }
        final String sourceId = StatsDisplaySettings.extractSourceId(channel.getId());
        StatsDisplayPanelBE.DataChannel usedCh = getAutoMetric(be, sourceId, "stress_su");
        StatsDisplayPanelBE.DataChannel totalCh = getAutoMetric(be, sourceId, "capacity_su");
        StatsDisplayPanelBE.DataChannel pctCh = getAutoMetric(be, sourceId, "stress_pct");
        MetricType amountType = MetricType.STRESS_SU;
        if (usedCh == null || totalCh == null) {
            usedCh = getAutoMetric(be, sourceId, "fluid_amount");
            totalCh = getAutoMetric(be, sourceId, "tank_capacity");
            pctCh = getAutoMetric(be, sourceId, "tank_fill");
            amountType = MetricType.FLUID_MB;
        }
        if (usedCh == null || totalCh == null) {
            usedCh = getAutoMetric(be, sourceId, "stock_level");
            totalCh = getAutoMetric(be, sourceId, "stock_capacity");
            pctCh = getAutoMetric(be, sourceId, "fill_pct");
            amountType = (usedCh != null) ? usedCh.getStore().getMetricType() : MetricType.GENERIC;
        }
        if (usedCh == null || totalCh == null) {
            renderCapacity(image, channel, x, y, w, h, theme, settings);
            return;
        }
        final float used = latestValue(usedCh, 0.0f);
        final float total = Math.max(1.0f, latestValue(totalCh, 1.0f));
        final float pct = (pctCh != null) ? latestValue(pctCh, used / total * 100.0f) : (used / total * 100.0f);
        final float ratio = Math.max(0.0f, Math.min(1.0f, pct / 100.0f));
        final int pctColor = (pct > 80.0f) ? -48060 : ((pct > 50.0f) ? -13312 : -16711800);
        final int scale = getTextScale(image.getWidth());
        final int bigScale = scale + 1;
        final String pctText = String.format("%.1f%%", pct);
        BitmapFont.drawCentered(image, pctText, x, y + h / 4, w, pctColor, bigScale + 1);
        final int barH = Math.max(8, h / 8);
        final int barY = y + h / 2;
        final int barMargin = w / 10;
        final int barW = w - 2 * barMargin;
        fillRect(image, x + barMargin, barY, barW, barH, theme.grid);
        fillRect(image, x + barMargin, barY, Math.max(0, Math.min(barW, (int)(barW * ratio))), barH, pctColor);
        final String valText = formatCapacityValue(amountType, used) + " / " + formatCapacityValue(amountType, total);
        BitmapFont.drawCentered(image, valText, x, barY + barH + 4, w, theme.text, scale);
    }
    
    private static void renderProduction(NativeImage image, StatsDisplayPanelBE.DataChannel channel, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TimeSeriesStore store = channel.getStore();
        final float latest = store.getLatestValue();
        final int scale = getTextScale(image.getWidth());
        final String label = channel.getLabel();
        final String value = store.getMetricType().formatValue(latest);
        final String trend = getTrendArrow(store, settings);
        final int lineH = BitmapFont.textHeight(scale) + 3;
        BitmapFont.drawString(image, truncate(label, w, scale), x + 2, y + 4, theme.text, scale);
        BitmapFont.drawCentered(image, value + " " + trend, x, y + 4 + lineH, w, TextureGraphRenderer.CHANNEL_COLORS[0], scale + 1);
        final float[] data = getVisibleData(store, settings);
        float maxVal = -3.4028235E38f;
        for (float v : data) {
            maxVal = Math.max(maxVal, Math.abs(v));
        }
        if (maxVal < 0.001f) {
            maxVal = 1.0f;
        }
        final float pct = Math.min(1.0f, Math.abs(latest) / maxVal);
        final int barH = Math.max(6, h / 10);
        final int barY = y + 4 + lineH * 2 + 4;
        final int barMargin = w / 8;
        final int barW = w - 2 * barMargin;
        final int centerX = x + barMargin + barW / 2;
        fillRect(image, x + barMargin, barY, barW, barH, theme.grid);
        fillRect(image, centerX, barY, 1, barH, withAlpha(theme.border, 180));
        final int fillW = Math.max(0, Math.min(barW / 2, (int)(barW / 2.0f * pct)));
        final int fillColor = (latest < 0.0f) ? theme.warn : TextureGraphRenderer.CHANNEL_COLORS[0];
        if (fillW > 0) {
            if (latest < 0.0f) {
                fillRect(image, centerX - fillW, barY, fillW, barH, fillColor);
            }
            else {
                fillRect(image, centerX + 1, barY, fillW, barH, fillColor);
            }
        }
    }
    
    private static void renderPanelProductionSummary(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final int scale = getTextScale(image.getWidth());
        final int lineH = BitmapFont.textHeight(scale) + 3;
        final java.util.List<StatsDisplayPanelBE.DataChannel> channels = be.getVisibleChannels();
        // Collect production rates
        final java.util.List<float[]> entries = new java.util.ArrayList<>(); // [rate, nameHash_unused]
        final java.util.List<String> entryLabels = new java.util.ArrayList<>();
        float net = 0f;
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final TimeSeriesStore store = ch.getStore();
            if (store.getSampleCount() <= 0) continue;
            final MetricType mt = store.getMetricType();
            if (mt != MetricType.ITEMS_PER_MIN && mt != MetricType.ITEMS_PER_SEC && mt != MetricType.ITEMS_PER_HR) continue;
            final float raw = store.getLatestValue();
            final float rate = (mt == MetricType.ITEMS_PER_SEC) ? raw * 60f : (mt == MetricType.ITEMS_PER_HR) ? raw / 60f : raw;
            if (Math.abs(rate) < 0.01f) continue;
            final String label = ch.getLabel();
            if (label == null || label.isEmpty()) continue;
            net += rate;
            entries.add(new float[]{ rate });
            entryLabels.add(label);
        }
        // Header
        final String hdr = "Production";
        BitmapFont.drawCentered(image, hdr, x, y + 2, w, theme.accent, scale);
        final String netStr = String.format("NET %+.1f/m", net);
        final int netCol = net > 0.1f ? theme.accent : net < -0.1f ? theme.warn : theme.dim;
        BitmapFont.drawCentered(image, netStr, x, y + lineH + 4, w, netCol, scale);
        if (entries.isEmpty()) {
            BitmapFont.drawCentered(image, "No active flows", x, y + h / 2, w, theme.dim, scale);
            return;
        }
        // Sort by |rate| descending and show top 4
        final int n = entries.size();
        final Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(Math.abs(entries.get(b)[0]), Math.abs(entries.get(a)[0])));
        final int maxShow = Math.min(4, n);
        float maxAbs = 0.001f;
        for (int i = 0; i < maxShow; i++) maxAbs = Math.max(maxAbs, Math.abs(entries.get(order[i])[0]));
        final int rowH = Math.max(lineH + 2, (h - lineH * 2 - 8) / Math.max(1, maxShow));
        int rowY = y + lineH * 2 + 6;
        final int barMargin = w / 8;
        final int barW = w - 2 * barMargin;
        for (int i = 0; i < maxShow && rowY + rowH <= y + h; i++) {
            final int idx = order[i];
            final float rate = entries.get(idx)[0];
            final String lbl = truncate(entryLabels.get(idx), barW, scale);
            final int rateColor = rate > 0 ? theme.accent : theme.warn;
            final int fillW = Math.max(1, (int)(Math.abs(rate) / maxAbs * barW));
            final String rateStr = String.format("%+.1f", rate);
            fillRect(image, x + barMargin, rowY + lineH, barW, Math.max(3, rowH - lineH - 1), theme.grid);
            fillRect(image, x + barMargin, rowY + lineH, fillW, Math.max(3, rowH - lineH - 1), rateColor);
            BitmapFont.drawString(image, lbl, x + barMargin + 1, rowY + 1, theme.text, scale);
            BitmapFont.drawRight(image, rateStr, x + barMargin + barW - 1, rowY + 1, rateColor, scale);
            rowY += rowH + 1;
        }
        if (n > maxShow) {
            BitmapFont.drawCentered(image, "+" + (n - maxShow) + " more", x, y + h - lineH - 2, w, theme.dim, scale);
        }
    }

    private static void renderPanelCapacityAll(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final int scale = getTextScale(image.getWidth());
        final int lineH = BitmapFont.textHeight(scale) + 2;
        final java.util.List<StatsDisplayPanelBE.DataChannel> channels = be.getVisibleChannels();
        // Group channels by sourceId
        final java.util.LinkedHashMap<String, java.util.List<StatsDisplayPanelBE.DataChannel>> bySource = new java.util.LinkedHashMap<>();
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final String sid = StatsDisplaySettings.extractSourceId(ch.getId());
            bySource.computeIfAbsent(sid, k -> new java.util.ArrayList<>()).add(ch);
        }
        // Resolve capacity per source
        final java.util.List<float[]> capEntries = new java.util.ArrayList<>(); // [used, total, pct]
        final java.util.List<String> capLabels = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.List<StatsDisplayPanelBE.DataChannel>> e : bySource.entrySet()) {
            if ("legacy".equals(e.getKey())) continue;
            StatsDisplayPanelBE.DataChannel usedCh = null, totalCh = null, pctCh = null;
            for (StatsDisplayPanelBE.DataChannel ch : e.getValue()) {
                final String id = ch.getId();
                if (id.contains("stress_su")) usedCh = ch;
                else if (id.contains("capacity_su")) totalCh = ch;
                else if (id.contains("stress_pct")) pctCh = ch;
            }
            if (usedCh == null || totalCh == null) {
                usedCh = null; totalCh = null; pctCh = null;
                for (StatsDisplayPanelBE.DataChannel ch : e.getValue()) {
                    final String id = ch.getId();
                    if (id.contains("fluid_amount")) usedCh = ch;
                    else if (id.contains("tank_capacity")) totalCh = ch;
                    else if (id.contains("tank_fill")) pctCh = ch;
                }
            }
            if (usedCh == null || totalCh == null) {
                usedCh = null; totalCh = null; pctCh = null;
                for (StatsDisplayPanelBE.DataChannel ch : e.getValue()) {
                    final String id = ch.getId();
                    if (id.contains("stock_level")) usedCh = ch;
                    else if (id.contains("stock_capacity")) totalCh = ch;
                    else if (id.contains("fill_pct")) pctCh = ch;
                }
            }
            if (usedCh == null || totalCh == null) continue;
            final float used = latestValue(usedCh, 0f);
            final float total = Math.max(1f, latestValue(totalCh, 1f));
            final float pct = pctCh != null ? latestValue(pctCh, used / total * 100f) : used / total * 100f;
            final SourceConfig cfg = be.getSourceRegistry().get(e.getKey());
            capLabels.add(cfg != null ? cfg.getDisplayLabel() : e.getKey());
            capEntries.add(new float[]{ used, total, pct });
        }
        if (capEntries.isEmpty()) {
            BitmapFont.drawCentered(image, "No capacity data", x, y + h / 2, w, theme.dim, scale);
            return;
        }
        BitmapFont.drawCentered(image, "System Health", x, y + 2, w, theme.accent, scale);
        final int maxShow = Math.min(4, capEntries.size());
        final int rowH = Math.max(lineH + 4, (h - lineH - 6) / Math.max(1, maxShow));
        int rowY = y + lineH + 4;
        final int barMarginX = w / 10;
        final int barW = w - 2 * barMarginX;
        for (int i = 0; i < maxShow && rowY + rowH <= y + h; i++) {
            final float[] cap = capEntries.get(i);
            final float pct = cap[2];
            final int barColor = pct > 80f ? theme.warn : pct > 50f ? -13312 : -16711800;
            final int fillW = Math.max(0, Math.min(barW, (int)(pct / 100f * barW)));
            final String lbl = truncate(capLabels.get(i), barW, scale);
            BitmapFont.drawString(image, lbl, x + barMarginX + 1, rowY + 1, theme.text, scale);
            fillRect(image, x + barMarginX, rowY + lineH, barW, Math.max(4, rowH - lineH - 2), theme.grid);
            if (fillW > 0) fillRect(image, x + barMarginX, rowY + lineH, fillW, Math.max(4, rowH - lineH - 2), barColor);
            final String pctStr = String.format("%.0f%%", pct);
            BitmapFont.drawRight(image, pctStr, x + barMarginX + barW - 1, rowY + 1, barColor, scale);
            rowY += rowH + 1;
        }
        if (capEntries.size() > maxShow) {
            BitmapFont.drawCentered(image, "+" + (capEntries.size() - maxShow) + " more", x, y + h - lineH - 2, w, theme.dim, scale);
        }
    }

    private static void renderNoData(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme) {
        final int scale = getTextScale(image.getWidth());
        final int bigScale = scale + 1;
        final String sourceType = be.getNoDataSourceType();
        if (be.isStatsDisplayLinkOptInRequired()) {
            BitmapFont.drawCentered(image, sourceType.isEmpty() ? "Stats Display" : sourceType, x, y + h / 3, w, theme.border, bigScale);
            BitmapFont.drawCentered(image, "Select Stats Link", x, y + h / 2 - 5, w, theme.dim, scale);
            BitmapFont.drawCentered(image, "output type", x, y + h / 2 + BitmapFont.textHeight(scale) + 2, w, theme.dim, scale);
        }
        else if (!sourceType.isEmpty()) {
            BitmapFont.drawCentered(image, sourceType, x, y + h / 3, w, theme.border, bigScale);
            BitmapFont.drawCentered(image, "Awaiting Data", x, y + h / 2, w, theme.dim, scale);
        }
        else {
            BitmapFont.drawCentered(image, "Stats Display", x, y + h / 4, w, theme.border, bigScale);
            BitmapFont.drawCentered(image, "Connect via", x, y + h / 2 - 5, w, theme.dim, scale);
            BitmapFont.drawCentered(image, "Display Link", x, y + h / 2 + BitmapFont.textHeight(scale) + 2, w, theme.dim, scale);
        }
        final int dotCount = 3;
        final int dotSize = Math.max(2, scale);
        final int dotSpacing = dotSize * 3;
        final int dotsW = dotCount * dotSpacing;
        final int dotsX = x + (w - dotsW) / 2;
        final int dotsY = y + (int)(h * 0.75f);
        for (int i = 0; i < dotCount; ++i) {
            final int dotColor = theme.dim;
            fillRect(image, dotsX + i * dotSpacing, dotsY, dotSize, dotSize, dotColor);
        }
    }
    
    public static void renderPageDots(NativeImage image, int currentPage, int totalPages, int x, int y, int w, int h, DisplayTheme theme) {
        final int dotSize = Math.max(2, h / 3);
        final int dotSpacing = dotSize * 2;
        final int maxDots = Math.min(totalPages, w / dotSpacing);
        final int dotsW = maxDots * dotSpacing;
        final int startX = x + (w - dotsW) / 2;
        final int dotY = y + (h - dotSize) / 2;
        for (int i = 0; i < maxDots; ++i) {
            final int color = (i == currentPage) ? theme.accent : theme.dim;
            fillRect(image, startX + i * dotSpacing, dotY, dotSize, dotSize, color);
        }
    }
    
    public static void fillRect(NativeImage image, int x, int y, int w, int h, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int x2 = Math.min(x + w, imgW);
        final int y2 = Math.min(y + h, imgH);
        x = Math.max(0, x);
        int py;
        int px;
        for (y = (py = Math.max(0, y)); py < y2; ++py) {
            for (px = x; px < x2; ++px) {
                image.setPixelRGBA(px, py, abgr);
            }
        }
    }
    
    public static void drawHLine(NativeImage image, int x, int y, int w, int argbColor) {
        fillRect(image, x, y, w, 1, argbColor);
    }
    
    public static void drawVLine(NativeImage image, int x, int y, int h, int argbColor) {
        fillRect(image, x, y, 1, h, argbColor);
    }
    
    public static void drawBorder(NativeImage image, int x, int y, int w, int h, int thickness, int argbColor) {
        fillRect(image, x, y, w, thickness, argbColor);
        fillRect(image, x, y + h - thickness, w, thickness, argbColor);
        fillRect(image, x, y, thickness, h, argbColor);
        fillRect(image, x + w - thickness, y, thickness, h, argbColor);
    }
    
    public static void drawLine(NativeImage image, int x0, int y0, int x1, int y1, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int dx = Math.abs(x1 - x0);
        final int dy = Math.abs(y1 - y0);
        final int sx = (x0 < x1) ? 1 : -1;
        final int sy = (y0 < y1) ? 1 : -1;
        int err = dx - dy;
        while (true) {
            if (x0 >= 0 && x0 < imgW && y0 >= 0 && y0 < imgH) {
                image.setPixelRGBA(x0, y0, abgr);
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 >= dx) {
                continue;
            }
            err += dx;
            y0 += sy;
        }
    }

    public static void drawLineAA(NativeImage image, int x0, int y0, int x1, int y1, int argbColor) {
        if (x0 == x1 && y0 == y1) {
            setPixelBlended(image, x0, y0, argbColor);
            return;
        }
        if (x0 == x1) {
            drawLine(image, x0, y0, x1, y1, argbColor);
            return;
        }
        float fx0 = x0;
        float fy0 = y0;
        float fx1 = x1;
        float fy1 = y1;
        final boolean steep = Math.abs(fy1 - fy0) > Math.abs(fx1 - fx0);
        if (steep) {
            final float tx0 = fx0;
            final float ty0 = fy0;
            final float tx1 = fx1;
            final float ty1 = fy1;
            fx0 = ty0;
            fy0 = tx0;
            fx1 = ty1;
            fy1 = tx1;
        }
        if (fx0 > fx1) {
            final float tx0 = fx0;
            final float ty0 = fy0;
            fx0 = fx1;
            fy0 = fy1;
            fx1 = tx0;
            fy1 = ty0;
        }
        final float dx = fx1 - fx0;
        final float dy = fy1 - fy0;
        if (Math.abs(dx) < 1.0E-4f) {
            drawLine(image, x0, y0, x1, y1, argbColor);
            return;
        }
        final float gradient = dy / dx;
        int xEnd = Math.round(fx0);
        float yEnd = fy0 + gradient * (xEnd - fx0);
        float xGap = rfpart(fx0 + 0.5f);
        final int xPixel1 = xEnd;
        final int yPixel1 = ipart(yEnd);
        if (steep) {
            plotAA(image, yPixel1, xPixel1, argbColor, rfpart(yEnd) * xGap);
            plotAA(image, yPixel1 + 1, xPixel1, argbColor, fpart(yEnd) * xGap);
        }
        else {
            plotAA(image, xPixel1, yPixel1, argbColor, rfpart(yEnd) * xGap);
            plotAA(image, xPixel1, yPixel1 + 1, argbColor, fpart(yEnd) * xGap);
        }
        float intery = yEnd + gradient;
        xEnd = Math.round(fx1);
        yEnd = fy1 + gradient * (xEnd - fx1);
        xGap = fpart(fx1 + 0.5f);
        final int xPixel2 = xEnd;
        final int yPixel2 = ipart(yEnd);
        if (steep) {
            plotAA(image, yPixel2, xPixel2, argbColor, rfpart(yEnd) * xGap);
            plotAA(image, yPixel2 + 1, xPixel2, argbColor, fpart(yEnd) * xGap);
        }
        else {
            plotAA(image, xPixel2, yPixel2, argbColor, rfpart(yEnd) * xGap);
            plotAA(image, xPixel2, yPixel2 + 1, argbColor, fpart(yEnd) * xGap);
        }
        for (int x = xPixel1 + 1; x < xPixel2; ++x) {
            if (steep) {
                plotAA(image, ipart(intery), x, argbColor, rfpart(intery));
                plotAA(image, ipart(intery) + 1, x, argbColor, fpart(intery));
            }
            else {
                plotAA(image, x, ipart(intery), argbColor, rfpart(intery));
                plotAA(image, x, ipart(intery) + 1, argbColor, fpart(intery));
            }
            intery += gradient;
        }
    }

    private static void plotAA(NativeImage image, int x, int y, int argbColor, float coverage) {
        final int srcA = argbColor >> 24 & 0xFF;
        final int a = Math.max(0, Math.min(255, Math.round(srcA * Math.max(0.0f, Math.min(1.0f, coverage)))));
        if (a == 0) {
            return;
        }
        setPixelBlended(image, x, y, argbColor & 0xFFFFFF | a << 24);
    }

    private static int ipart(float x) {
        return (int)Math.floor(x);
    }

    private static float fpart(float x) {
        return x - (float)Math.floor(x);
    }

    private static float rfpart(float x) {
        return 1.0f - fpart(x);
    }
    
    public static void drawGrid(NativeImage image, int x, int y, int w, int h, int hDivs, int vDivs, int argbColor) {
        for (int i = 1; i < hDivs; ++i) {
            final int gy = y + h * i / hDivs;
            drawHLine(image, x, gy, w, argbColor);
        }
        for (int i = 1; i < vDivs; ++i) {
            final int gx = x + w * i / vDivs;
            drawVLine(image, gx, y, h, argbColor);
        }
    }
    
    public static void drawArcFill(NativeImage image, int cx, int cy, int innerR, int outerR, double startAngle, double endAngle, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int py = cy - outerR; py <= cy + outerR; ++py) {
            for (int px = cx - outerR; px <= cx + outerR; ++px) {
                if (px >= 0 && px < imgW && py >= 0) {
                    if (py < imgH) {
                        final int dx = px - cx;
                        final int dy = cy - py;
                        final double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist >= innerR) {
                            if (dist <= outerR) {
                                double angle = Math.atan2(dy, dx);
                                if (angle < 0.0) {
                                    angle += 6.283185307179586;
                                }
                                double sa = startAngle;
                                double ea = endAngle;
                                if (sa < 0.0) {
                                    sa += 6.283185307179586;
                                }
                                if (ea < 0.0) {
                                    ea += 6.283185307179586;
                                }
                                boolean inArc;
                                if (sa <= ea) {
                                    inArc = (angle >= ea && angle <= sa);
                                }
                                else {
                                    inArc = (angle >= ea || angle <= sa);
                                }
                                if (inArc) {
                                    image.setPixelRGBA(px, py, abgr);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    public static void fillCircle(NativeImage image, int cx, int cy, int r, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int rSq = r * r;
        for (int py = Math.max(0, cy - r); py <= Math.min(imgH - 1, cy + r); ++py) {
            for (int px = Math.max(0, cx - r); px <= Math.min(imgW - 1, cx + r); ++px) {
                final int dx = px - cx;
                final int dy = py - cy;
                if (dx * dx + dy * dy <= rSq) {
                    image.setPixelRGBA(px, py, abgr);
                }
            }
        }
    }
    
    public static void fillTriangle(NativeImage image, int x0, int y0, int x1, int y1, int x2, int y2, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        if (y0 > y1) {
            int t = x0;
            x0 = x1;
            x1 = t;
            t = y0;
            y0 = y1;
            y1 = t;
        }
        if (y0 > y2) {
            int t = x0;
            x0 = x2;
            x2 = t;
            t = y0;
            y0 = y2;
            y2 = t;
        }
        if (y1 > y2) {
            int t = x1;
            x1 = x2;
            x2 = t;
            t = y1;
            y1 = y2;
            y2 = t;
        }
        for (int y3 = Math.max(0, y0); y3 <= Math.min(imgH - 1, y2); ++y3) {
            int xLeft;
            int xRight;
            if (y3 < y1) {
                xLeft = ((y0 != y1) ? (x0 + (x1 - x0) * (y3 - y0) / (y1 - y0)) : x0);
                xRight = ((y0 != y2) ? (x0 + (x2 - x0) * (y3 - y0) / (y2 - y0)) : x0);
            }
            else {
                xLeft = ((y1 != y2) ? (x1 + (x2 - x1) * (y3 - y1) / (y2 - y1)) : x1);
                xRight = ((y0 != y2) ? (x0 + (x2 - x0) * (y3 - y0) / (y2 - y0)) : x0);
            }
            if (xLeft > xRight) {
                final int t = xLeft;
                xLeft = xRight;
                xRight = t;
            }
            for (int x3 = Math.max(0, xLeft); x3 <= Math.min(imgW - 1, xRight); ++x3) {
                image.setPixelRGBA(x3, y3, abgr);
            }
        }
    }
    
    public static void setPixelBlended(NativeImage image, int px, int py, int argbSrc) {
        if (px < 0 || px >= image.getWidth() || py < 0 || py >= image.getHeight()) {
            return;
        }
        final int srcA = argbSrc >> 24 & 0xFF;
        if (srcA == 0) {
            return;
        }
        if (srcA == 255) {
            image.setPixelRGBA(px, py, BitmapFont.argbToAbgr(argbSrc));
            return;
        }
        final int existing = image.getPixelRGBA(px, py);
        final int dstR = existing & 0xFF;
        final int dstG = existing >> 8 & 0xFF;
        final int dstB = existing >> 16 & 0xFF;
        final int srcR = argbSrc >> 16 & 0xFF;
        final int srcG = argbSrc >> 8 & 0xFF;
        final int srcB = argbSrc & 0xFF;
        final float a = srcA / 255.0f;
        final int outR = (int)(srcR * a + dstR * (1.0f - a));
        final int outG = (int)(srcG * a + dstG * (1.0f - a));
        final int outB = (int)(srcB * a + dstB * (1.0f - a));
        final int outA = 255;
        image.setPixelRGBA(px, py, outA << 24 | outB << 16 | outG << 8 | outR);
    }
    
    public static int getTextScale(int imageWidth) {
        if (imageWidth >= 768) {
            return 2;
        }
        return 1;
    }

    private static int fitScale(String text, int maxWidth, int preferredScale) {
        int scale = Math.max(1, preferredScale);
        while (scale > 1 && BitmapFont.measureString(text, scale) > maxWidth) {
            --scale;
        }
        return scale;
    }
    
    public static String truncate(String text, int maxWidth, int scale) {
        if (BitmapFont.measureString(text, scale) <= maxWidth) {
            return text;
        }
        while (text.length() > 3 && BitmapFont.measureString(text + "..", scale) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }
    
    public static String getTrendArrow(TimeSeriesStore store) {
        return getTrendArrow(store, null);
    }

    public static String getTrendArrow(TimeSeriesStore store, StatsDisplaySettings settings) {
        final float[] data = getVisibleData(store, settings);
        if (data.length < 10) {
            return "->";
        }
        final int mid = data.length - 5;
        float recent = 0.0f;
        float prev = 0.0f;
        for (int i = mid; i < data.length; ++i) {
            recent += data[i];
        }
        int j;
        int prevStart;
        for (prevStart = (j = Math.max(0, mid - 5)); j < mid; ++j) {
            prev += data[j];
        }
        recent /= 5.0f;
        prev /= Math.min(5, mid - prevStart);
        if (Math.abs(prev) < 0.001f && Math.abs(recent) < 0.001f) {
            return "->";
        }
        final float diff = (recent - prev) / (Math.abs(prev) + 0.001f);
        if (diff > 0.05f) {
            return (diff > 0.2f) ? "^^" : "^";
        }
        if (diff < -0.05f) {
            return (diff < -0.2f) ? "vv" : "v";
        }
        return "->";
    }

    private static float[] getVisibleData(TimeSeriesStore store, StatsDisplaySettings settings) {
        if (settings == null) {
            return store.getData();
        }
        return store.getDataForTimeScale(settings.getTimeRange().getSeconds());
    }

    private static float average(float[] data) {
        float sum = 0.0f;
        for (float value : data) {
            sum += value;
        }
        return sum / data.length;
    }

    private static long visibleWindowStartTick(TimeSeriesStore store, int sampleCount, long windowEndTick, int intervalTicks) {
        final long visibleDuration = Math.max(0, sampleCount - 1) * (long)intervalTicks;
        long windowStartTick = windowEndTick - visibleDuration;
        final long oldestSampleTick = store.getOldestSampleTick();
        if (oldestSampleTick > 0L) {
            windowStartTick = Math.max(oldestSampleTick, windowStartTick);
        }
        return windowStartTick;
    }
    
    public static String formatCompact(float value, float range) {
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
        if (abs >= 1.0f) {
            return String.format("%.2f", value);
        }
        return String.format("%.2f", value);
    }

    private static StatsDisplayPanelBE.DataChannel getAutoMetric(StatsDisplayPanelBE be, String sourceId, String metricId) {
        final StatsDisplayPanelBE.DataChannel exact = be.getChannel("src_" + sourceId + "_auto_" + metricId);
        if (exact != null) {
            return exact;
        }
        return null;
    }

    private static float latestValue(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore().getSampleCount() <= 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
    }

    private static String formatCapacityValue(MetricType type, float value) {
        final MetricType metricType = (type != null) ? type : MetricType.GENERIC;
        return switch (metricType) {
            case STRESS_SU -> formatCompact(value, 0.0f) + " SU";
            case FLUID_MB -> formatCompact(value, 0.0f) + " mB";
            case ENERGY_FE -> formatCompact(value, 0.0f) + " FE";
            case ENERGY_FE_PER_TICK -> formatCompact(value, 0.0f) + " FE/t";
            case ITEM_COUNT, ENGINE_COUNT, REDSTONE_POWER -> formatCompact(value, 0.0f);
            default -> metricType.formatValue(value);
        };
    }
    
    public static float niceNumber(float value) {
        if (value <= 0.0f) {
            return 1.0f;
        }
        final float exp = (float)Math.floor(Math.log10(value));
        final float fraction = value / (float)Math.pow(10.0, exp);
        float nice;
        if (fraction <= 1.5f) {
            nice = 1.0f;
        }
        else if (fraction <= 3.5f) {
            nice = 2.0f;
        }
        else if (fraction <= 7.5f) {
            nice = 5.0f;
        }
        else {
            nice = 10.0f;
        }
        return nice * (float)Math.pow(10.0, exp);
    }
    
    public static String formatTimeLabel(float seconds) {
        if (seconds < 60.0f) {
            return "" + (int)seconds;
        }
        if (seconds < 3600.0f) {
            final int min = (int)(seconds / 60.0f);
            final int sec = (int)(seconds % 60.0f);
            return (sec > 0) ? (min + "m" + sec) : ("" + min);
        }
        final int hr = (int)(seconds / 3600.0f);
        final int min2 = (int)(seconds % 3600.0f / 60.0f);
        return (min2 > 0) ? (hr + "h" + min2) : ("" + hr);
    }
    
    public static int withAlpha(int color, int alpha) {
        return alpha << 24 | (color & 0xFFFFFF);
    }
    
    private static void renderAnnotations(NativeImage image, StatsDisplayPanelBE be, int chartX, int chartY, int chartW, int chartH, DisplayTheme theme, int scale, StatsDisplaySettings settings) {
        final AnnotationManager mgr = be.getAnnotationManager();
        if (mgr.size() == 0) {
            return;
        }
        final List<StatsDisplayPanelBE.DataChannel> active = be.getActiveChannels();
        if (active.isEmpty()) {
            return;
        }
        final TimeSeriesStore refStore = active.get(0).getStore();
        final float[] data = getVisibleData(refStore, settings);
        if (data.length < 2) {
            return;
        }
        final int intervalTicks = refStore.getSampleIntervalTicks();
        final long currentTick = (be.getLevel() != null) ? be.getLevel().getGameTime() : 0L;
        long windowEndTick = refStore.getLastSampleTick();
        if (windowEndTick <= 0L) {
            windowEndTick = currentTick;
        }
        final long windowStartTick = visibleWindowStartTick(refStore, data.length, windowEndTick, intervalTicks);
        final long span = windowEndTick - windowStartTick;
        if (span <= 0L) {
            return;
        }
        final List<Annotation> visible = mgr.getInRange(windowStartTick, windowEndTick);
        final List<int[]> labelSlots = new ArrayList<int[]>();
        final int labelH = BitmapFont.textHeight(scale) + 3;
        final int maxRows = Math.max(1, chartH / 4 / labelH);
        for (Annotation a : visible) {
            final double normX = (a.getTickStamp() - windowStartTick) / (double)span;
            final int px = chartX + (int)(normX * chartW);
            if (px >= chartX) {
                if (px > chartX + chartW) {
                    continue;
                }
                drawDashedVLine(image, px, chartY, chartH, 3, 2, a.getColor());
                if (a.getType() == Annotation.AnnotationType.REGION) {
                    final double normX2 = (a.getEndTickStamp() - windowStartTick) / (double)span;
                    int px2 = chartX + (int)(normX2 * chartW);
                    px2 = Math.max(chartX, Math.min(chartX + chartW, px2));
                    drawDashedVLine(image, px2, chartY, chartH, 3, 2, a.getColor());
                    final int left = Math.min(px, px2);
                    final int right = Math.max(px, px2);
                    fillRect(image, left, chartY, right - left, chartH, withAlpha(a.getColor(), 34));
                }
                int row = 0;
                for (int[] prev : labelSlots) {
                    if (Math.abs(px - prev[0]) < 6 * scale * 8 && row == prev[1] && ++row >= maxRows) {
                        row = maxRows - 1;
                    }
                }
                labelSlots.add(new int[] { px, row });
                final int labelY = chartY + 2 + row * labelH;
                final int maxLabelW = chartX + chartW - px - 2;
                if (maxLabelW <= 6 * scale * 3) {
                    continue;
                }
                final String text = truncate(a.getLabel(), maxLabelW, scale);
                final int textW = BitmapFont.measureString(text, scale);
                final int textH = BitmapFont.textHeight(scale);
                fillRect(image, px, labelY - 1, textW + 3, textH + 2, -2013265920);
                BitmapFont.drawString(image, text, px + 1, labelY, a.getColor(), scale);
            }
        }
    }
    
    private static void drawDashedVLine(NativeImage image, int x, int y, int height, int dashLen, int gapLen, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        if (x < 0 || x >= imgW) {
            return;
        }
        final int period = dashLen + gapLen;
        for (int py = y; py < y + height; ++py) {
            if (py >= 0) {
                if (py < imgH) {
                    final int phase = (py - y) % period;
                    if (phase < dashLen) {
                        image.setPixelRGBA(x, py, abgr);
                    }
                }
            }
        }
    }
    
    private static void renderEfficiencyFullPage(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme) {
        final EfficiencyScore score = be.getLatestEfficiencyScore();
        final GradeHistory history = be.getGradeHistory();
        final int scale = getTextScale(image.getWidth());
        if (score == null) {
            BitmapFont.drawCentered(image, "Efficiency", x, y + h / 3, w, theme.border, scale + 1);
            BitmapFont.drawCentered(image, "Awaiting Data", x, y + h / 2, w, theme.dim, scale);
            return;
        }
        if ("?".equals(score.grade())) {
            BitmapFont.drawCentered(image, "Efficiency", x, y + h / 3, w, theme.border, scale + 1);
            BitmapFont.drawCentered(image, "Awaiting Stress Data", x, y + h / 2, w, theme.dim, scale);
            BitmapFont.drawCentered(image, "Use Stats Link", x, y + h / 2 + BitmapFont.textHeight(scale) + 2, w, theme.dim, scale);
            return;
        }
        final int zoneAH = (int)(h * 0.35f);
        final int gradeScale = (image.getWidth() >= 256) ? 6 : 4;
        final int gradeLetterH = BitmapFont.textHeight(gradeScale);
        final int gradeY = y + (zoneAH - gradeLetterH) / 2 - 2;
        BitmapFont.drawCentered(image, score.grade(), x, gradeY, w, score.gradeColor(), gradeScale);
        final int scoreScale = Math.max(1, gradeScale / 2);
        final String scoreText = String.format("%.1f", score.compositeScore());
        final int scoreY = gradeY + gradeLetterH + 2;
        BitmapFont.drawCentered(image, scoreText, x, scoreY, w, theme.dim, scoreScale);
        final int sepY = y + zoneAH;
        drawHLine(image, x, sepY, w, theme.dim);
        final int zoneBTop = sepY + 2;
        final int zoneBH = (int)(h * 0.4f);
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final int barTotalH = zoneBH / dims.length;
        final int barH = Math.max(3, barTotalH - 2);
        for (int i = 0; i < dims.length; ++i) {
            final EfficiencyDimension dim = dims[i];
            final float dimScore = score.getDimensionPercent(dim);
            final int barY = zoneBTop + i * barTotalH;
            final String truncLabel = truncate(dim.getDisplayName(), (int)(w * 0.33f), 1);
            BitmapFont.drawString(image, truncLabel, x, barY, theme.text, 1);
            final int barX = x + (int)(w * 0.35f);
            final int barW = (int)(w * 0.5f);
            fillRect(image, barX, barY + 1, barW, barH, theme.grid);
            final int fillW = (int)(barW * dimScore / 100.0f);
            final int fillColor = (dimScore >= 80.0f) ? dim.getBarColor() : ((dimScore >= 50.0f) ? lerpColor(-22016, dim.getBarColor(), (dimScore - 50.0f) / 30.0f) : lerpColor(-48060, -22016, dimScore / 50.0f));
            if (fillW > 0) {
                fillRect(image, barX, barY + 1, fillW, barH, fillColor);
            }
            BitmapFont.drawRight(image, String.format("%.0f", dimScore), x + w, barY, fillColor, 1);
        }
        final int sep2Y = zoneBTop + zoneBH;
        drawHLine(image, x, sep2Y, w, theme.dim);
        final int zoneCTop = sep2Y + 2;
        final int zoneCH = y + h - zoneCTop;
        final int textLineH = BitmapFont.textHeight(1) + 2;
        final int sparkH = Math.max(4, zoneCH - textLineH - 2);
        if (history != null) {
            final float[] sparkData = history.getSparklineData(w);
            if (sparkData != null && sparkData.length >= 2) {
                for (int j = 0; j < sparkData.length - 1; ++j) {
                    final float t1 = j / (float)(sparkData.length - 1);
                    final float t2 = (j + 1) / (float)(sparkData.length - 1);
                    final float v1 = Math.max(0.0f, Math.min(1.0f, sparkData[j] / 100.0f));
                    final float v2 = Math.max(0.0f, Math.min(1.0f, sparkData[j + 1] / 100.0f));
                    final int px1 = x + (int)(t1 * w);
                    final int py1 = zoneCTop + sparkH - (int)(v1 * sparkH);
                    final int px2 = x + (int)(t2 * w);
                    final int py2 = zoneCTop + sparkH - (int)(v2 * sparkH);
                    drawLineAA(image, px1, py1, px2, py2, theme.accent);
                }
            }
            final String changeText = history.getSessionChangeText();
            if (changeText != null) {
                final int changeColor = history.hasImproved() ? -16711800 : theme.warn;
                BitmapFont.drawCentered(image, changeText, x, zoneCTop + sparkH + 2, w, changeColor, 1);
            }
        }
    }
    
    private static int lerpColor(int colorA, int colorB, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        final int aA = colorA >> 24 & 0xFF;
        final int rA = colorA >> 16 & 0xFF;
        final int gA = colorA >> 8 & 0xFF;
        final int bA = colorA & 0xFF;
        final int aB = colorB >> 24 & 0xFF;
        final int rB = colorB >> 16 & 0xFF;
        final int gB = colorB >> 8 & 0xFF;
        final int bB = colorB & 0xFF;
        final int a = (int)(aA + (aB - aA) * t);
        final int r = (int)(rA + (rB - rA) * t);
        final int g = (int)(gA + (gB - gA) * t);
        final int b = (int)(bA + (bB - bA) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }
    
    static {
        CHANNEL_COLORS = new int[] { -16711800, -12276993, -39356, -13312, -47873, -12255284, -30652, -7798972, -7811841, -21880, -3372801, -5570748 };
    }

    private static void renderPanelInventoryStatus(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final int scale = getTextScale(image.getWidth());
        final int lineH = BitmapFont.textHeight(scale) + 2;
        final java.util.List<InventoryPanelEntry> entries = buildInventoryPanelEntries(be);

        // Header band
        final int headerH = lineH + 4;
        fillRect(image, x, y, w, headerH, withAlpha(theme.grid, 60));
        BitmapFont.drawString(image, "INVENTORY STATUS", x + 3, y + 2, theme.accent, scale);

        if (entries.isEmpty()) {
            BitmapFont.drawCentered(image, "No storage data", x, y + h / 2 - lineH, w, theme.dim, scale);
            BitmapFont.drawCentered(image, "Link Stockpile / Tank / Observer", x, y + h / 2 + 2, w, theme.dim, scale);
            return;
        }

        // Header summary: total observed items + total fluid
        long totalItems = 0L;
        long totalFluidMb = 0L;
        int sourceCount = entries.size();
        for (InventoryPanelEntry e : entries) {
            if (e.kind == InventoryPanelEntry.Kind.ITEMS) totalItems += (long)e.usedAmount;
            else if (e.kind == InventoryPanelEntry.Kind.FLUID) totalFluidMb += (long)e.usedAmount;
        }
        final String summary;
        if (totalFluidMb > 0L && totalItems > 0L) {
            summary = sourceCount + "src " + formatCompact((float)totalItems, 0.0f) + "i " + formatCompact((float)totalFluidMb, 0.0f) + "mB";
        } else if (totalFluidMb > 0L) {
            summary = sourceCount + " src | " + formatCompact((float)totalFluidMb, 0.0f) + " mB";
        } else {
            summary = sourceCount + " src | " + formatCompact((float)totalItems, 0.0f) + " items";
        }
        BitmapFont.drawRight(image, summary, x + w - 3, y + 2, theme.text, scale);

        // Card grid layout
        final int bodyY = y + headerH + 2;
        final int bodyH = h - headerH - 2;
        final int gap = 2;
        final int minCardW = 56 * scale;
        final int cols = Math.max(1, Math.min(3, (w - gap) / (minCardW + gap)));
        final int cardW = (w - gap * (cols + 1)) / cols;
        final int cardH = lineH * 4 + 6;
        final int rows = Math.max(1, (bodyH + gap) / (cardH + gap));
        final int maxCards = Math.min(entries.size(), cols * rows);

        for (int i = 0; i < maxCards; ++i) {
            final int col = i % cols;
            final int row = i / cols;
            final int cardX = x + gap + col * (cardW + gap);
            final int cardY = bodyY + row * (cardH + gap);
            renderInventoryCard(image, entries.get(i), cardX, cardY, cardW, cardH, lineH, scale, theme);
        }

        if (entries.size() > maxCards) {
            BitmapFont.drawRight(image, "+" + (entries.size() - maxCards) + " more",
                    x + w - 3, y + h - lineH - 1, theme.dim, scale);
        }
    }

    private static void renderInventoryCard(NativeImage image, InventoryPanelEntry entry, int cx, int cy, int cw, int ch, int lineH, int scale, DisplayTheme theme) {
        // Card chrome
        fillRect(image, cx, cy, cw, ch, withAlpha(theme.bg, 200));
        drawBorder(image, cx, cy, cw, ch, 1, theme.border);

        final int padX = 3;
        final int innerW = cw - padX * 2;
        final int rowColor = entry.hasCapacity ? (entry.percent > 90.0f ? theme.warn : entry.percent > 60.0f ? theme.accent : theme.text) : theme.dim;

        // Line 1: Source/target label  (left)  + chip (right)
        final String chip = inventoryTypeChip(entry.sourceType);
        final int chipW = chip.isEmpty() ? 0 : BitmapFont.measureString(chip, scale) + 4;
        BitmapFont.drawString(image, truncate(entry.label, innerW - chipW - 2, scale), cx + padX, cy + 2, theme.text, scale);
        if (!chip.isEmpty()) {
            final int chipX = cx + cw - padX - chipW;
            fillRect(image, chipX, cy + 2, chipW, lineH - 2, withAlpha(theme.accent, 64));
            BitmapFont.drawString(image, chip, chipX + 2, cy + 3, theme.accent, scale);
        }

        // Line 2: amount  (used / total)  - emphasised
        final String amountLine = entry.hasCapacity
                ? formatCapacityValue(entry.amountType, entry.usedAmount) + " / " + formatCapacityValue(entry.amountType, entry.totalAmount)
                : formatCapacityValue(entry.amountType, entry.usedAmount);
        BitmapFont.drawString(image, truncate(amountLine, innerW, scale), cx + padX, cy + 2 + lineH, rowColor, scale);

        // Line 3: bar
        final int barY = cy + 3 + lineH * 2;
        final int barH = Math.max(4, lineH - 4);
        fillRect(image, cx + padX, barY, innerW, barH, withAlpha(theme.grid, 200));
        if (entry.hasCapacity) {
            final int fillW = Math.max(0, Math.min(innerW, (int)(entry.percent / 100.0f * innerW)));
            if (fillW > 0) fillRect(image, cx + padX, barY, fillW, barH, rowColor);
        } else {
            BitmapFont.drawCentered(image, "amount only", cx, barY - 1, cw, theme.dim, scale);
        }
        drawBorder(image, cx + padX, barY, innerW, barH, 1, theme.border);

        // Line 4: percent + suffix
        final String footer = entry.hasCapacity
                ? String.format("%.0f%% full", entry.percent)
                : (entry.sourceType == null ? "" : entry.sourceType);
        BitmapFont.drawString(image, truncate(footer, innerW, scale), cx + padX, cy + ch - lineH - 1, theme.dim, scale);
    }

    private static String inventoryTypeChip(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return "";
        final String s = sourceType.toLowerCase();
        if (s.contains("stockpile")) return "STK";
        if (s.contains("steam")) return "BOIL";
        if (s.contains("tank")) return "TANK";
        if (s.contains("observer")) return "OBS";
        if (s.contains("vault")) return "VAU";
        return sourceType.length() > 4 ? sourceType.substring(0, 4).toUpperCase() : sourceType.toUpperCase();
    }

    private static void renderPanelFactoryStatus(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final int scale = getTextScale(image.getWidth());
        final int lineH = BitmapFont.textHeight(scale) + 2;
        final java.util.List<FactoryPanelEntry> entries = buildFactoryPanelEntries(be);
        // Header band
        final int headerH = lineH + 4;
        fillRect(image, x, y, w, headerH, withAlpha(theme.grid, 60));
        BitmapFont.drawString(image, "FACTORY STATUS", x + 3, y + 2, theme.accent, scale);
        if (entries.isEmpty()) {
            BitmapFont.drawCentered(image, "No factory data", x, y + h / 2 - lineH, w, theme.dim, scale);
            BitmapFont.drawCentered(image, "Link kinetic sources", x, y + h / 2 + 2, w, theme.dim, scale);
            return;
        }
        // Aggregate: avg stress% from real stress entries; running count over all
        float sumStress = 0.0f; int stressCount = 0; int runningCount = 0;
        for (FactoryPanelEntry e : entries) {
            if (e.running) runningCount++;
            if (e.hasStress) {
                sumStress += e.percent;
                stressCount++;
            }
        }
        final float avgStress = stressCount > 0 ? sumStress / stressCount : 0.0f;
        final boolean heroIsStress = stressCount > 0;
        final String summary = entries.size() + " src | " + runningCount + " run" + (heroIsStress ? (" | avg " + String.format("%.0f%%", avgStress)) : " | RPM only");
        BitmapFont.drawRight(image, summary, x + w - 3, y + 2, theme.dim, scale);

        // Layout: hero gauge on left taking ~45% width, satellite list on right
        final int heroW = Math.max(60, (int)(w * 0.45f));
        final int heroX = x + 2;
        final int heroY = y + headerH + 2;
        final int heroH = Math.min(h - headerH - 6, heroW); // square-ish for gauge
        renderFactoryHeroGauge(image, heroX, heroY, heroW, heroH, avgStress, runningCount > 0, heroIsStress, theme, scale);

        // Satellite right-side: top sources as chips with mini bars
        final int satX = heroX + heroW + 4;
        final int satY = heroY;
        final int satW = x + w - satX - 2;
        final int satH = heroH;
        renderFactorySatellites(image, satX, satY, satW, satH, entries, theme, scale, lineH);

        // Bottom RPM strip
        final int stripY = heroY + heroH + 3;
        final int stripH = (y + h) - stripY - 1;
        if (stripH >= lineH + 2) {
            renderFactoryRpmStrip(image, x + 2, stripY, w - 4, stripH, entries, theme, scale, lineH);
        }
    }

    private static void renderFactoryHeroGauge(NativeImage image, int x, int y, int w, int h, float pct, boolean running, boolean isStress, DisplayTheme theme, int scale) {
        // Bordered card
        drawBorder(image, x, y, w, h, 1, theme.border);
        fillRect(image, x + 1, y + 1, w - 2, h - 2, withAlpha(theme.bg, 200));
        final int lineH = BitmapFont.textHeight(scale) + 2;
        BitmapFont.drawString(image, isStress ? "STRESS LOAD" : "ACTIVITY", x + 3, y + 2, theme.accent, scale);
        // Gauge geometry: bottom-centered semicircle, leave room below for the percentage text
        final int pctScale = Math.max(scale, scale + 1);
        final int pctTextH = BitmapFont.textHeight(pctScale) + 2;
        final int cx = x + w / 2;
        final int cy = y + h - pctTextH - 4;
        final int availR = Math.min(w / 2 - 4, cy - (y + lineH + 4));
        // Compact fallback for very small cards: skip arc, just show big % centered
        if (availR < 8 || h < lineH * 4 || w < 32) {
            final String pctTxtFb = String.format("%.0f%%", Math.max(0.0f, Math.min(100.0f, pct)));
            final int pctWFb = BitmapFont.measureString(pctTxtFb, pctScale);
            BitmapFont.drawString(image, pctTxtFb, x + (w - pctWFb) / 2, y + (h - pctTextH) / 2 + lineH / 2, running ? theme.text : theme.dim, pctScale);
            return;
        }
        final int rOut = availR;
        final int rIn = Math.max(4, rOut - Math.max(3, rOut / 4));
        // Background arc (full half)
        drawSemicircleArc(image, cx, cy, rOut, rIn, -90.0f, 90.0f, withAlpha(theme.grid, 200));
        // Color zones overlay only meaningful for stress; for activity show single accent fill
        if (pct > 0.01f) {
            final float clampPct = Math.max(0.0f, Math.min(100.0f, pct));
            final float endDeg = -90.0f + (clampPct / 100.0f) * 180.0f;
            if (isStress) {
                float cursor = -90.0f;
                float green = -90.0f + 0.60f * 180.0f;
                float yellow = -90.0f + 0.85f * 180.0f;
                if (cursor < endDeg) {
                    float seg = Math.min(green, endDeg);
                    drawSemicircleArc(image, cx, cy, rOut, rIn, cursor, seg, theme.accent);
                    cursor = seg;
                }
                if (cursor < endDeg) {
                    float seg = Math.min(yellow, endDeg);
                    drawSemicircleArc(image, cx, cy, rOut, rIn, cursor, seg, theme.warn);
                    cursor = seg;
                }
                if (cursor < endDeg) {
                    drawSemicircleArc(image, cx, cy, rOut, rIn, cursor, endDeg, 0xFFFF5050);
                }
            } else {
                drawSemicircleArc(image, cx, cy, rOut, rIn, -90.0f, endDeg, theme.accent);
            }
            final int needleColor = isStress
                    ? (pct > 85.0f ? 0xFFFF6060 : (pct > 60.0f ? theme.warn : theme.accent))
                    : theme.accent;
            drawNeedle(image, cx, cy, rOut + 1, endDeg, needleColor);
            drawBorder(image, cx - 3, cy - 3, 6, 6, 1, theme.text);
            fillRect(image, cx - 2, cy - 2, 4, 4, theme.text);
        } else {
            drawBorder(image, cx - 2, cy - 2, 4, 4, 1, theme.dim);
        }
        // Big % text below hub but inside card
        final String pctTxt = String.format("%.0f%%", Math.max(0.0f, Math.min(100.0f, pct)));
        final int pctW = BitmapFont.measureString(pctTxt, pctScale);
        BitmapFont.drawString(image, pctTxt, cx - pctW / 2, y + h - pctTextH - 1, running ? theme.text : theme.dim, pctScale);
    }

    private static void renderFactorySatellites(NativeImage image, int x, int y, int w, int h, java.util.List<FactoryPanelEntry> entries, DisplayTheme theme, int scale, int lineH) {
        drawBorder(image, x, y, w, h, 1, theme.border);
        fillRect(image, x + 1, y + 1, w - 2, h - 2, withAlpha(theme.bg, 200));
        BitmapFont.drawString(image, "SOURCES", x + 3, y + 2, theme.accent, scale);
        int rowY = y + lineH + 3;
        final int rowH = lineH + 4;
        // Sort by percent descending so heavy hitters surface
        java.util.List<FactoryPanelEntry> sorted = new java.util.ArrayList<>(entries);
        sorted.sort((a, b) -> Float.compare(b.percent, a.percent));
        final int maxRows = Math.max(1, (y + h - rowY - 2) / rowH);
        int shown = 0;
        for (FactoryPanelEntry e : sorted) {
            if (shown >= maxRows) break;
            final int color = e.percent > 85.0f ? 0xFFFF6060 : (e.percent > 60.0f ? theme.warn : (e.running ? theme.accent : theme.dim));
            // Status dot
            fillCircle(image, x + 5, rowY + lineH / 2, 2, color);
            final String label = truncate(e.label, w - 38, scale);
            BitmapFont.drawString(image, label, x + 10, rowY, theme.text, scale);
            // Mini bar at right
            final int barW = 24;
            final int barX = x + w - barW - 18;
            final int barY = rowY + (rowH - 4) / 2;
            fillRect(image, barX, barY, barW, 3, withAlpha(theme.grid, 200));
            int fillW = Math.max(0, Math.min(barW, (int)(e.percent / 100.0f * barW)));
            if (fillW > 0) fillRect(image, barX, barY, fillW, 3, color);
            BitmapFont.drawRight(image, e.percentText, x + w - 3, rowY, color, scale);
            rowY += rowH;
            shown++;
        }
        if (sorted.size() > shown) {
            BitmapFont.drawRight(image, "+" + (sorted.size() - shown), x + w - 3, y + h - lineH - 1, theme.dim, scale);
        }
    }

    private static void renderFactoryRpmStrip(NativeImage image, int x, int y, int w, int h, java.util.List<FactoryPanelEntry> entries, DisplayTheme theme, int scale, int lineH) {
        drawBorder(image, x, y, w, h, 1, theme.border);
        fillRect(image, x + 1, y + 1, w - 2, h - 2, withAlpha(theme.grid, 40));
        BitmapFont.drawString(image, "RPM", x + 3, y + 2, theme.accent, scale);
        // Show comma separated detail-derived rpm chips
        int cursorX = x + 26;
        final int chipPadY = 1;
        for (FactoryPanelEntry e : entries) {
            // Extract first "RPM nnn" from detail
            final String d = e.detail == null ? "" : e.detail;
            int idx = d.indexOf("RPM ");
            if (idx < 0) continue;
            int end = d.indexOf(' ', idx + 4);
            final String rpmTxt = end < 0 ? d.substring(idx) : d.substring(idx, end);
            final String labelChip = shortChipLabel(e.label) + ":" + rpmTxt.substring(4);
            int chipW = BitmapFont.measureString(labelChip, scale) + 6;
            if (cursorX + chipW > x + w - 2) break;
            final int chipColor = e.running ? theme.accent : theme.dim;
            fillRect(image, cursorX, y + 1 + chipPadY, chipW, h - 2 - chipPadY * 2, withAlpha(chipColor, 50));
            BitmapFont.drawString(image, labelChip, cursorX + 3, y + 2, theme.text, scale);
            cursorX += chipW + 2;
        }
    }

    private static String shortChipLabel(String label) {
        if (label == null) return "?";
        int at = label.indexOf('@');
        String head = at > 0 ? label.substring(0, at).trim() : label;
        if (head.length() > 8) head = head.substring(0, 8);
        return head;
    }

    private static void drawSemicircleArc(NativeImage image, int cx, int cy, int rOut, int rIn, float startDeg, float endDeg, int color) {
        if (endDeg < startDeg) { float t = startDeg; startDeg = endDeg; endDeg = t; }
        final float step = 1.0f;
        for (float deg = startDeg; deg <= endDeg; deg += step) {
            final double rad = Math.toRadians(deg);
            final double sin = Math.sin(rad);
            final double cos = Math.cos(rad);
            for (int r = rIn; r <= rOut; ++r) {
                int px = cx + (int)Math.round(r * sin);
                int py = cy - (int)Math.round(r * cos);
                if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                    image.setPixelRGBA(px, py, color);
                }
            }
        }
    }

    private static void drawNeedle(NativeImage image, int cx, int cy, int len, float deg, int color) {
        final double rad = Math.toRadians(deg);
        final double sin = Math.sin(rad);
        final double cos = Math.cos(rad);
        final int tx = cx + (int)Math.round(len * sin);
        final int ty = cy - (int)Math.round(len * cos);
        drawLine(image, cx, cy, tx, ty, color);
        // Slight thickness: shift perpendicular
        final double pxd = cos;
        final double pyd = sin;
        final int ox = (int)Math.round(pxd);
        final int oy = (int)Math.round(pyd);
        if (ox != 0 || oy != 0) {
            drawLine(image, cx + ox, cy + oy, tx + ox, ty + oy, color);
        }
    }

    private static java.util.List<InventoryPanelEntry> buildInventoryPanelEntries(StatsDisplayPanelBE be) {
        final java.util.List<InventoryPanelEntry> entries = new java.util.ArrayList<>();
        final java.util.LinkedHashMap<String, java.util.List<StatsDisplayPanelBE.DataChannel>> bySource = new java.util.LinkedHashMap<>();
        for (StatsDisplayPanelBE.DataChannel ch : be.getVisibleChannels()) {
            final String sid = StatsDisplaySettings.extractSourceId(ch.getId());
            if (!"legacy".equals(sid)) {
                bySource.computeIfAbsent(sid, ignored -> new java.util.ArrayList<>()).add(ch);
            }
        }
        for (java.util.Map.Entry<String, java.util.List<StatsDisplayPanelBE.DataChannel>> sourceEntry : bySource.entrySet()) {
            final SourceConfig cfg = be.getSourceRegistry().get(sourceEntry.getKey());
            final String sourceLabel = cfg != null ? cfg.getObservedDisplayLabel() : sourceEntry.getKey();
            final String sourceType = cfg != null ? cfg.getSourceType() : "";
            StatsDisplayPanelBE.DataChannel usedCh = null;
            StatsDisplayPanelBE.DataChannel totalCh = null;
            StatsDisplayPanelBE.DataChannel pctCh = null;
            for (StatsDisplayPanelBE.DataChannel ch : sourceEntry.getValue()) {
                final String id = ch.getId();
                if (id.contains("fluid_amount")) usedCh = ch;
                else if (id.contains("tank_capacity")) totalCh = ch;
                else if (id.contains("tank_fill")) pctCh = ch;
            }
            if (usedCh != null && totalCh != null) {
                entries.add(capacityEntry(sourceLabel, sourceType, usedCh, totalCh, pctCh, MetricType.FLUID_MB));
                continue;
            }
            usedCh = null;
            totalCh = null;
            pctCh = null;
            for (StatsDisplayPanelBE.DataChannel ch : sourceEntry.getValue()) {
                final String id = ch.getId();
                if (id.contains("stock_level")) usedCh = ch;
                else if (id.contains("stock_capacity")) totalCh = ch;
                else if (id.contains("fill_pct")) pctCh = ch;
            }
            if (usedCh != null && totalCh != null) {
                entries.add(capacityEntry(sourceLabel, sourceType, usedCh, totalCh, pctCh, usedCh.getStore().getMetricType()));
                continue;
            }
            // Content Observer with slot_capacity: synthesise capacity bar from total_items / slot_capacity
            StatsDisplayPanelBE.DataChannel obsTotalItems = null;
            StatsDisplayPanelBE.DataChannel obsSlotCap = null;
            for (StatsDisplayPanelBE.DataChannel ch : sourceEntry.getValue()) {
                final String id = ch.getId();
                if (id.contains("total_items")) obsTotalItems = ch;
                else if (id.contains("slot_capacity")) obsSlotCap = ch;
            }
            if (obsTotalItems != null && obsSlotCap != null && latestValue(obsSlotCap, 0.0f) > 0.5f) {
                entries.add(capacityEntry(sourceLabel, sourceType, obsTotalItems, obsSlotCap, null, MetricType.ITEM_COUNT));
                continue;
            }
            for (StatsDisplayPanelBE.DataChannel ch : sourceEntry.getValue()) {
                final TimeSeriesStore store = ch.getStore();
                final String id = ch.getId();
                if (store.getSampleCount() <= 0 || store.getMetricType() != MetricType.ITEM_COUNT || id.contains("total_items") || id.contains("unique_types") || id.contains("slot_capacity")) {
                    continue;
                }
                if (!(id.contains("_item_") || id.contains("auto_item_"))) {
                    continue;
                }
                final String label = ch.getLabel() == null || ch.getLabel().isBlank() ? sourceLabel : ch.getLabel();
                entries.add(new InventoryPanelEntry(label, appendSource(formatCapacityValue(MetricType.ITEM_COUNT, store.getLatestValue()), sourceType),
                        0.0f, false, sourceType, MetricType.ITEM_COUNT, store.getLatestValue(), 0.0f, InventoryPanelEntry.Kind.ITEMS));
            }
            for (StatsDisplayPanelBE.DataChannel ch : sourceEntry.getValue()) {
                final TimeSeriesStore store = ch.getStore();
                final String id = ch.getId();
                if (store.getSampleCount() <= 0 || store.getMetricType() != MetricType.FLUID_MB || id.contains("capacity") || id.contains("water_supply") || id.contains("fluid_amount")) {
                    continue;
                }
                final String label = ch.getLabel() == null || ch.getLabel().isBlank() ? sourceLabel : ch.getLabel();
                entries.add(new InventoryPanelEntry(label, appendSource(formatCapacityValue(MetricType.FLUID_MB, store.getLatestValue()), sourceType),
                        0.0f, false, sourceType, MetricType.FLUID_MB, store.getLatestValue(), 0.0f, InventoryPanelEntry.Kind.FLUID));
            }
        }
        entries.sort((a, b) -> {
            if (a.hasCapacity != b.hasCapacity) {
                return a.hasCapacity ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        return entries;
    }

    private static InventoryPanelEntry capacityEntry(String sourceLabel, String sourceType, StatsDisplayPanelBE.DataChannel usedCh, StatsDisplayPanelBE.DataChannel totalCh, StatsDisplayPanelBE.DataChannel pctCh, MetricType amountType) {
        final float used = latestValue(usedCh, 0.0f);
        final float total = Math.max(1.0f, latestValue(totalCh, 1.0f));
        final float pct = pctCh != null ? latestValue(pctCh, used / total * 100.0f) : used / total * 100.0f;
        final String value = appendSource(formatCapacityValue(amountType, used) + " / " + formatCapacityValue(amountType, total), sourceType);
        final InventoryPanelEntry.Kind kind = (amountType == MetricType.FLUID_MB) ? InventoryPanelEntry.Kind.FLUID
                : (amountType == MetricType.ITEM_COUNT) ? InventoryPanelEntry.Kind.ITEMS : InventoryPanelEntry.Kind.OTHER;
        return new InventoryPanelEntry(sourceLabel, value, pct, true, sourceType, amountType, used, total, kind);
    }

    private static String appendSource(String value, String sourceType) {
        return sourceType == null || sourceType.isBlank() ? value : value + " | " + sourceType;
    }

    private static java.util.List<FactoryPanelEntry> buildFactoryPanelEntries(StatsDisplayPanelBE be) {
        final java.util.List<FactoryPanelEntry> entries = new java.util.ArrayList<>();
        final java.util.LinkedHashMap<String, java.util.List<StatsDisplayPanelBE.DataChannel>> bySource = new java.util.LinkedHashMap<>();
        for (StatsDisplayPanelBE.DataChannel ch : be.getVisibleChannels()) {
            final MetricType metricType = ch.getStore().getMetricType();
            if (metricType != MetricType.RPM && metricType != MetricType.STRESS_SU && metricType != MetricType.STRESS_PERCENT && metricType != MetricType.BOOLEAN_FLAG) {
                continue;
            }
            final String sid = StatsDisplaySettings.extractSourceId(ch.getId());
            if (!"legacy".equals(sid)) {
                bySource.computeIfAbsent(sid, ignored -> new java.util.ArrayList<>()).add(ch);
            }
        }
        for (java.util.Map.Entry<String, java.util.List<StatsDisplayPanelBE.DataChannel>> sourceEntry : bySource.entrySet()) {
            final SourceConfig cfg = be.getSourceRegistry().get(sourceEntry.getKey());
            final String sourceLabel = cfg != null ? cfg.getDisplayLabel() : sourceEntry.getKey();
            final String sourceType = cfg != null ? cfg.getSourceType() : "";
            final String lower = sourceType == null ? "" : sourceType.toLowerCase(java.util.Locale.ROOT);
            if (!(lower.contains("stress") || lower.contains("speed") || lower.contains("clutch") || lower.contains("gear") || lower.contains("kinetic") || lower.contains("balancer"))) {
                continue;
            }
            StatsDisplayPanelBE.DataChannel stressPctCh = findMetric(sourceEntry.getValue(), "stress_pct");
            StatsDisplayPanelBE.DataChannel stressCh = findMetric(sourceEntry.getValue(), "stress_su");
            StatsDisplayPanelBE.DataChannel capacityCh = findMetric(sourceEntry.getValue(), "capacity_su");
            StatsDisplayPanelBE.DataChannel rpmCh = findMetric(sourceEntry.getValue(), "rpm");
            final float rpm = latestValue(rpmCh, 0.0f);
            final float pct = latestValue(stressPctCh, 0.0f);
            final float stress = latestValue(stressCh, 0.0f);
            final float capacity = latestValue(capacityCh, 0.0f);
            final boolean hasStress = stressPctCh != null || (stressCh != null && capacityCh != null);
            final String detail = hasStress
                    ? ("RPM " + formatCompact(Math.abs(rpm), 0.0f) + " | " + formatCapacityValue(MetricType.STRESS_SU, stress) + "/" + formatCapacityValue(MetricType.STRESS_SU, capacity))
                    : ("RPM " + formatCompact(Math.abs(rpm), 0.0f) + " | " + sourceType);
            entries.add(new FactoryPanelEntry(sourceLabel, detail, hasStress ? pct : Math.min(100.0f, Math.abs(rpm)), Math.abs(rpm) > 0.01f, hasStress));
        }
        for (StatsDisplayPanelBE.SafetyClutchLink link : be.getSafetyClutchLinks()) {
            final float remaining = Math.max(0.0f, link.getCapacitySU() - link.getStressSU());
            entries.add(new FactoryPanelEntry("Auto Clutch @ " + link.getSourcePos().toShortString(),
                    formatCapacityValue(MetricType.STRESS_SU, link.getStressSU()) + "/" + formatCapacityValue(MetricType.STRESS_SU, link.getCapacitySU()) + " | free " + formatCapacityValue(MetricType.STRESS_SU, remaining),
                    link.getStressPercent(), link.isNetworkRunning(), true));
        }
        for (StatsDisplayPanelBE.AutoTransmissionClutchLink link : be.getAutoTransmissionClutchLinks()) {
            final float remaining = Math.max(0.0f, link.getCapacitySU() - link.getStressSU());
            entries.add(new FactoryPanelEntry("Auto Variable Clutch @ " + link.getSourcePos().toShortString(),
                    "RPM " + formatCompact(link.getTransmittedSpeed(), 0.0f) + " | free " + formatCapacityValue(MetricType.STRESS_SU, remaining),
                    link.getStressPercent(), link.isNetworkRunning(), true));
        }
        for (StatsDisplayPanelBE.LoadBalancerLink link : be.getLoadBalancerLinks()) {
            entries.add(new FactoryPanelEntry("Load Balancer @ " + link.getSourcePos().toShortString(),
                    "L/R " + formatCompact(link.getTransmittedSpeedLeft(), 0.0f) + "/" + formatCompact(link.getTransmittedSpeedRight(), 0.0f) + " RPM",
                    link.getStressPercent(), link.isNetworkRunning(), true));
        }
        entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return entries;
    }

    private static StatsDisplayPanelBE.DataChannel findMetric(java.util.List<StatsDisplayPanelBE.DataChannel> channels, String metricId) {
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final String id = channel.getId();
            if (id.endsWith("_" + metricId) || id.contains("_auto_" + metricId) || id.contains(metricId)) {
                return channel;
            }
        }
        return null;
    }

    private static final class InventoryPanelEntry {
        enum Kind { ITEMS, FLUID, OTHER }
        final String label;
        final String valueText;
        final float percent;
        final boolean hasCapacity;
        final String sourceType;
        final MetricType amountType;
        final float usedAmount;
        final float totalAmount;
        final Kind kind;

        InventoryPanelEntry(String label, String valueText, float percent, boolean hasCapacity) {
            this(label, valueText, percent, hasCapacity, "", MetricType.GENERIC, 0.0f, 0.0f, Kind.OTHER);
        }

        InventoryPanelEntry(String label, String valueText, float percent, boolean hasCapacity,
                            String sourceType, MetricType amountType, float usedAmount, float totalAmount, Kind kind) {
            this.label = label;
            this.valueText = valueText;
            this.percent = Math.max(0.0f, Math.min(100.0f, percent));
            this.hasCapacity = hasCapacity;
            this.sourceType = sourceType == null ? "" : sourceType;
            this.amountType = amountType == null ? MetricType.GENERIC : amountType;
            this.usedAmount = usedAmount;
            this.totalAmount = totalAmount;
            this.kind = kind == null ? Kind.OTHER : kind;
        }
    }

    private static final class FactoryPanelEntry {
        final String label;
        final String detail;
        final float percent;
        final String percentText;
        final boolean running;
        final boolean hasStress;

        FactoryPanelEntry(String label, String detail, float percent, boolean running) {
            this(label, detail, percent, running, false);
        }

        FactoryPanelEntry(String label, String detail, float percent, boolean running, boolean hasStress) {
            this.label = label;
            this.detail = detail;
            this.percent = Math.max(0.0f, Math.min(100.0f, percent));
            this.percentText = String.format("%.0f%%", this.percent);
            this.running = running;
            this.hasStress = hasStress;
        }
    }
}
