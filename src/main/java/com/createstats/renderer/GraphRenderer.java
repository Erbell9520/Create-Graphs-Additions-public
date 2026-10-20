package com.createstats.renderer;

import com.createstats.prediction.LinearRegression;
import com.createstats.prediction.PredictionResult;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GraphRenderer
{
    public static void drawLineChart(GuiGraphics graphics, float[] data, int color, int x, int y, int width, int height) {
        if (data == null || data.length < 2) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        if (range < 1.0f) {
            range = 1.0f;
            min -= 0.5f;
        }
        final float xStep = width / (float)(data.length - 1);
        for (int i = 0; i < data.length - 1; ++i) {
            final float x2 = x + i * xStep;
            final float y2 = y + height - (data[i] - min) / range * height;
            final float x3 = x + (i + 1) * xStep;
            final float y3 = y + height - (data[i + 1] - min) / range * height;
            drawLine(graphics, (int)x2, (int)y2, (int)x3, (int)y3, color);
        }
    }

    public static void drawBarChart(GuiGraphics graphics, float[] data, int color, int x, int y, int width, int height) {
        if (data == null || data.length == 0) {
            return;
        }
        float max = 0.0f;
        for (float v : data) {
            max = Math.max(max, v);
        }
        if (max < 0.001f) {
            max = 1.0f;
        }
        final int barWidth = Math.max(1, width / data.length - 1);
        final float xStep = width / (float)data.length;
        for (int i = 0; i < data.length; ++i) {
            final int barHeight = (int)(data[i] / max * height);
            final int bx = x + (int)(i * xStep);
            final int by = y + height - barHeight;
            graphics.fill(bx, by, bx + barWidth, y + height, color);
        }
    }

    public static void drawGauge(GuiGraphics graphics, float value, float maxValue, int color, int centerX, int centerY, int radius) {
        if (maxValue <= 0.0f) {
            maxValue = 1.0f;
        }
        final float ratio = Math.min(value / maxValue, 1.0f);
        drawArc(graphics, centerX, centerY, radius, 0.0f, 1.0f, 1149798536);
        drawArc(graphics, centerX, centerY, radius, 0.0f, ratio, color);
        final String text = String.format("%.0f", value);
        final int textWidth = text.length() * 3;
        graphics.drawString(Minecraft.getInstance().font, text, centerX - textWidth, centerY - 4, color, false);
    }

    public static void drawNumeric(GuiGraphics graphics, float value, String unit, int color, int x, int y, int width, int height) {
        String text;
        if (Math.abs(value) >= 1000.0f) {
            text = String.format("%.0f", value);
        }
        else if (Math.abs(value) >= 10.0f) {
            text = String.format("%.1f", value);
        }
        else {
            text = String.format("%.2f", value);
        }
        final Font font = Minecraft.getInstance().font;
        final PoseStack pose = graphics.pose();
        pose.pushPose();
        float scale = Math.min(width / (float)(font.width(text) + 4), height / 20.0f);
        scale = Math.min(scale, 4.0f);
        pose.translate(x + width / 2.0f, y + height / 2.0f - 5.0f * scale, 0.0f);
        pose.scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, text, 0, 0, color);
        pose.popPose();
        if (unit != null && !unit.isEmpty()) {
            graphics.drawCenteredString(font, unit, x + width / 2, y + height - 10, -5592406);
        }
    }

    public static void drawGrid(GuiGraphics graphics, int x, int y, int width, int height, int divisions, int gridColor) {
        final float xStep = width / (float)divisions;
        final float yStep = height / (float)divisions;
        for (int i = 1; i < divisions; ++i) {
            graphics.fill(x + (int)(i * xStep), y, x + (int)(i * xStep) + 1, y + height, gridColor);
            graphics.fill(x, y + (int)(i * yStep), x + width, y + (int)(i * yStep) + 1, gridColor);
        }
    }

    public static void drawAxes(GuiGraphics graphics, String xLabel, String yLabel, int x, int y, int width, int height, int textColor) {
        final Font font = Minecraft.getInstance().font;
        if (xLabel != null) {
            graphics.drawCenteredString(font, xLabel, x + width / 2, y + height + 2, textColor);
        }
        if (yLabel != null) {
            graphics.drawString(font, yLabel, x - font.width(yLabel) - 2, y, textColor);
        }
        graphics.fill(x, y, x + 1, y + height, textColor);
        graphics.fill(x, y + height, x + width, y + height + 1, textColor);
    }

    public static void drawMinMaxAvg(GuiGraphics graphics, float min, float max, float avg, int x, int y, int width, int height, float dataMin, float dataMax, int textColor) {
        final Font font = Minecraft.getInstance().font;
        float range = dataMax - dataMin;
        if (range < 0.001f) {
            range = 1.0f;
        }
        final int avgY = y + height - (int)((avg - dataMin) / range * height);
        for (int i = x; i < x + width; i += 4) {
            graphics.fill(i, avgY, Math.min(i + 2, x + width), avgY + 1, -1996488960);
        }
        graphics.drawString(font, String.format("Max: %.1f", max), x + width + 4, y, textColor);
        graphics.drawString(font, String.format("Avg: %.1f", avg), x + width + 4, avgY - 4, -256);
        graphics.drawString(font, String.format("Min: %.1f", min), x + width + 4, y + height - 8, textColor);
    }

    public static void drawPredictionOverlay(GuiGraphics graphics, PredictionResult prediction, float[] data, int x, int y, int width, int height, float yMin, float yMax, int lineColor, double tCritical) {
        if (prediction == null || !prediction.isRenderable()) {
            return;
        }
        final int n = data.length;
        final int futureSamples = prediction.predictionSamples();
        final int totalRange = n + futureSamples - 1;
        final float range = yMax - yMin;
        if (range < 0.001f) {
            return;
        }
        final int bandColor = 0x30000000 | (lineColor & 0xFFFFFF);
        final int futureStartX = x + (int)((n - 1) / (double)totalRange * width);
        final LinearRegression.RegressionCoefficients coeffs = new LinearRegression.RegressionCoefficients(prediction.slope(), prediction.intercept(), prediction.rSquared(), prediction.standardError(), prediction.Sxx(), prediction.xBar(), prediction.sampleCount());
        for (int px = futureStartX; px < x + width; ++px) {
            final double xIdx = (px - x) / (double)width * totalRange;
            final double yPred = prediction.slope() * xIdx + prediction.intercept();
            final double halfWidth = LinearRegression.predictionIntervalHalfWidth(coeffs, xIdx, tCritical);
            final double yUpper = Math.max(yMin, Math.min(yMax, yPred + halfWidth));
            final double yLower = Math.max(yMin, Math.min(yMax, yPred - halfWidth));
            final int pyU = y + height - (int)((yUpper - yMin) / range * height);
            final int pyL = y + height - (int)((yLower - yMin) / range * height);
            final int pyTop = Math.min(pyU, pyL);
            final int pyBot = Math.max(pyU, pyL);
            graphics.fill(px, pyTop, px + 1, pyBot, bandColor);
        }
        final int predColor = 0xCC000000 | (lineColor & 0xFFFFFF);
        for (int segments = Math.max(1, Math.min(futureSamples, width / 2)), i = 0; i < segments - 1; ++i) {
            final double xIdx2 = n - 1 + i * (double)futureSamples / segments;
            final double xIdx3 = n - 1 + (i + 1) * (double)futureSamples / segments;
            final double yPred2 = Math.max(yMin, Math.min(yMax, prediction.slope() * xIdx2 + prediction.intercept()));
            final double yPred3 = Math.max(yMin, Math.min(yMax, prediction.slope() * xIdx3 + prediction.intercept()));
            final int px2 = x + (int)(xIdx2 / totalRange * width);
            final int py0 = y + height - (int)((yPred2 - yMin) / range * height);
            final int px3 = x + (int)(xIdx3 / totalRange * width);
            final int py2 = y + height - (int)((yPred3 - yMin) / range * height);
            drawDashedLine(graphics, px2, py0, px3, py2, predColor, 6, 4);
        }
        final int nowX = x + (int)((n - 1) / (double)totalRange * width);
        drawDashedLine(graphics, nowX, y, nowX, y + height, -1996488705, 3, 2);
    }

    private static void drawDashedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int dashLen, int gapLen) {
        final int dx = Math.abs(x2 - x1);
        final int dy = Math.abs(y2 - y1);
        final int sx = (x1 < x2) ? 1 : -1;
        final int sy = (y1 < y2) ? 1 : -1;
        int err = dx - dy;
        final int patternLen = dashLen + gapLen;
        int pixelCount = 0;
        while (true) {
            final boolean draw = pixelCount % patternLen < dashLen;
            if (draw) {
                graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            }
            if (x1 == x2 && y1 == y2) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
            ++pixelCount;
        }
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        final int dx = Math.abs(x2 - x1);
        final int dy = Math.abs(y2 - y1);
        final int sx = (x1 < x2) ? 1 : -1;
        final int sy = (y1 < y2) ? 1 : -1;
        int err = dx - dy;
        while (true) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 >= dx) {
                continue;
            }
            err += dx;
            y1 += sy;
        }
    }

    private static void drawArc(GuiGraphics graphics, int cx, int cy, int radius, float startRatio, float endRatio, int color) {
        final int segments = 32;
        final double startAngle = 2.356194490192345 + startRatio * 3.141592653589793 * 1.5;
        final double endAngle = 2.356194490192345 + endRatio * 3.141592653589793 * 1.5;
        for (int i = 0; i < segments; ++i) {
            final double angle = startAngle + (endAngle - startAngle) * i / segments;
            final int px = cx + (int)(Math.cos(angle) * radius);
            final int py = cy + (int)(Math.sin(angle) * radius);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }
}
