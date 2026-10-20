package com.createstats.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import com.createstats.gui.widgets.GraphWidget;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class DashboardScreen extends Screen
{
    private final TimeSeriesStore store;
    private final MetricType metricType;
    private final StatsDisplaySettings settings;
    private GraphWidget graphWidget;
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 200;

    public DashboardScreen(TimeSeriesStore store) {
        super(Component.literal("Stats Dashboard"));
        this.store = store;
        this.metricType = ((store != null) ? store.getMetricType() : MetricType.GENERIC);
        this.settings = new StatsDisplaySettings();
    }

    protected void init() {
        super.init();
        final int left = (this.width - 280) / 2;
        final int top = (this.height - 200) / 2;
        (this.graphWidget = new GraphWidget(left + 10, top + 20, 260, 120)).setSettings(this.settings);
        this.graphWidget.setMetricType(this.metricType);
        if (this.store != null) {
            this.refreshGraphData();
        }
        this.addRenderableWidget(this.graphWidget);
        int btnY = top + 200 - 52;
        final int btnW = 60;
        final int btnH = 16;
        final int spacing = 4;
        final int startX = left + 10;
        this.addRenderableWidget(Button.builder(Component.literal(this.settings.getGraphType().getDisplayName()), btn -> {
            this.settings.cycleGraphType();
            btn.setMessage(Component.literal(this.settings.getGraphType().getDisplayName()));
            this.refreshGraphData();
        }).bounds(startX, btnY, btnW, btnH).build());
        this.addRenderableWidget(Button.builder(Component.literal("Colors"), btn -> {
            this.settings.cycleColorScheme();
            this.refreshGraphData();
        }).bounds(startX + btnW + spacing, btnY, btnW, btnH).build());
        this.addRenderableWidget(Button.builder(Component.literal(this.settings.getTimeRange().getDisplayName()), btn -> {
            this.settings.cycleTimeRange();
            btn.setMessage(Component.literal(this.settings.getTimeRange().getDisplayName()));
            this.refreshGraphData();
        }).bounds(startX + 2 * (btnW + spacing), btnY, btnW, btnH).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> this.onClose()).bounds(startX + 3 * (btnW + spacing), btnY, btnW, btnH).build());
        btnY += btnH + 6;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        final int left = (this.width - 280) / 2;
        final int top = (this.height - 200) / 2;
        graphics.fill(left, top, left + 280, top + 200, -870704594);
        graphics.fill(left, top, left + 280, top + 2, -4947652);
        graphics.fill(left, top + 200 - 2, left + 280, top + 200, -4947652);
        graphics.fill(left, top, left + 2, top + 200, -4947652);
        graphics.fill(left + 280 - 2, top, left + 280, top + 200, -4947652);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 6, -8824);
        super.render(graphics, mouseX, mouseY, partialTick);
        final float[] visibleData = this.getVisibleData();
        if (visibleData.length > 0) {
            float min = Float.MAX_VALUE;
            float max = -3.4028235E38f;
            float sum = 0.0f;
            for (float value : visibleData) {
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
            }
            final float avg = sum / visibleData.length;
            final String info = String.format("%s | %d samples | Min: %.1f | Max: %.1f | Avg: %.1f", this.metricType.getDisplayName(), visibleData.length, min, max, avg);
            graphics.drawCenteredString(this.font, info, this.width / 2, top + 200 - 14, -7829368);
        }
        else {
            graphics.drawCenteredString(this.font, "No data available", this.width / 2, top + 200 - 14, -10066330);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void refreshGraphData() {
        this.graphWidget.setSettings(this.settings);
        this.graphWidget.setData(this.getVisibleData());
    }

    private float[] getVisibleData() {
        if (this.store == null) {
            return new float[0];
        }
        return this.store.getDataForTimeScale(this.settings.getTimeRange().getSeconds());
    }

    public boolean isPauseScreen() {
        return false;
    }
}
