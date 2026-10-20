package com.createstats.gui.widgets;

import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.Font;
import com.createstats.display.StatsColorScheme;
import com.createstats.renderer.GraphRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.createstats.data.MetricType;
import com.createstats.display.StatsDisplaySettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.components.AbstractWidget;

@OnlyIn(Dist.CLIENT)
public class GraphWidget extends AbstractWidget
{
    private float[] data;
    private StatsDisplaySettings settings;
    private MetricType metricType;

    public GraphWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.metricType = MetricType.GENERIC;
        this.settings = new StatsDisplaySettings();
        this.data = new float[0];
    }

    public void setData(float[] data) {
        this.data = ((data != null) ? data : new float[0]);
    }

    public void setSettings(StatsDisplaySettings settings) {
        this.settings = settings;
    }

    public void setMetricType(MetricType type) {
        this.metricType = type;
    }

    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        final StatsColorScheme scheme = this.settings.getColorScheme();
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, scheme.getBackgroundColor());
        final int borderColor = scheme.getTextColor();
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
        graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
        graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);
        final int innerX = this.getX() + 4;
        final int innerY = this.getY() + 4;
        final int innerW = this.width - 8;
        final int innerH = this.height - 8;
        if (innerW <= 0 || innerH <= 0) {
            return;
        }
        if (this.data == null || this.data.length == 0) {
            final Font font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, "No data available", this.getX() + this.width / 2, this.getY() + this.height / 2 - 4, -10066330);
            return;
        }
        if (this.settings.showGrid()) {
            GraphRenderer.drawGrid(graphics, innerX, innerY, innerW, innerH, 4, scheme.getGridColor());
        }
        final int lineColor = this.settings.getEffectiveLineColor();
        switch (this.settings.getGraphType()) {
            case LINE_CHART: {
                if (this.data.length >= 2) {
                    GraphRenderer.drawLineChart(graphics, this.data, lineColor, innerX, innerY, innerW, innerH);
                    break;
                }
                break;
            }
            case BAR_CHART: {
                if (this.data.length > 0) {
                    GraphRenderer.drawBarChart(graphics, this.data, lineColor, innerX, innerY, innerW, innerH);
                    break;
                }
                break;
            }
            case GAUGE: {
                final float latest = (this.data.length > 0) ? this.data[this.data.length - 1] : 0.0f;
                float max = 0.0f;
                for (float v : this.data) {
                    max = Math.max(max, v);
                }
                if (max <= 0.0f) {
                    max = 100.0f;
                }
                GraphRenderer.drawGauge(graphics, latest, max * 1.2f, lineColor, innerX + innerW / 2, innerY + innerH / 2, Math.min(innerW, innerH) / 2 - 2);
                break;
            }
            case NUMERIC: {
                final float latest = (this.data.length > 0) ? this.data[this.data.length - 1] : 0.0f;
                GraphRenderer.drawNumeric(graphics, latest, this.metricType.getUnit(), lineColor, innerX, innerY, innerW, innerH);
                break;
            }
        }
        if (this.settings.showAxisLabels()) {
            GraphRenderer.drawAxes(graphics, "Time", this.metricType.getUnit(), innerX, innerY, innerW, innerH, scheme.getTextColor());
        }
        if (this.settings.showMinMaxAvg() && this.data.length >= 2) {
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;
            float sum = 0.0f;
            for (float v2 : this.data) {
                min = Math.min(min, v2);
                max = Math.max(max, v2);
                sum += v2;
            }
            final float avg = sum / this.data.length;
            if (max - min > 0.001f) {
                GraphRenderer.drawMinMaxAvg(graphics, min, max, avg, innerX, innerY, innerW, innerH, min, max, scheme.getTextColor());
            }
        }
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
