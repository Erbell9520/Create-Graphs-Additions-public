package com.createstats.dashboard;

import java.util.Iterator;
import com.createstats.blockentity.SourceConfig;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.renderer.BitmapFont;
import com.createstats.renderer.TextureGraphRenderer;
import com.createstats.display.DisplayTheme;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CornerIndicatorRenderer
{
    private CornerIndicatorRenderer() {
    }

    public static void render(NativeImage image, DashboardZone zone, StatsDisplayPanelBE.DataChannel channel, StatsDisplayPanelBE be, DisplayTheme theme) {
        final int x = zone.x;
        final int y = zone.y;
        final int w = zone.width;
        final int h = zone.height;
        final int lightH = Math.max(4, h / 3);
        final int lightW = w - 6;
        final int lightX = x + 3;
        final int lightY = y + 2;
        int indicatorColor = getIndicatorColor(channel);
        if (channel != null && channel.isAlertTriggered()) {
            final long pulse = System.currentTimeMillis() / 300L;
            final int alpha = (int)(180.0 + 75.0 * Math.sin(pulse * 3.141592653589793));
            indicatorColor = (alpha << 24 | (indicatorColor & 0xFFFFFF));
        }
        TextureGraphRenderer.fillRect(image, lightX, lightY, lightW, lightH, indicatorColor);
        final int labelY = lightY + lightH + 2;
        final String label = (channel != null) ? TextureGraphRenderer.truncate(channel.getLabel(), w - 4, 1) : "N/A";
        BitmapFont.drawCentered(image, label, x, labelY, w, theme.dim, 1);
        if (h >= 20 && channel != null) {
            String value = channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue());
            if (BitmapFont.measureString(value, 1) > w - 4) {
                value = TextureGraphRenderer.formatCompact(channel.getStore().getLatestValue(), 0.0f);
            }
            final int valueY = labelY + BitmapFont.textHeight(1) + 1;
            if (valueY + BitmapFont.textHeight(1) <= y + h) {
                BitmapFont.drawCentered(image, value, x, valueY, w, indicatorColor, 1);
            }
        }
    }

    public static int getIndicatorColor(StatsDisplayPanelBE.DataChannel channel) {
        if (channel == null) {
            return -12303292;
        }
        if (channel.isAlertTriggered()) {
            return -56798;
        }
        final TimeSeriesStore store = channel.getStore();
        final MetricType type = store.getMetricType();
        final float val = store.getLatestValue();
        return switch (type) {
            case BOOLEAN_FLAG -> (val > 0.5f) ? -16711800 : -10066330;
            case STRESS_PERCENT -> (val >= 95.0f) ? -56798 : ((val >= 80.0f) ? -22016 : ((val >= 50.0f) ? -256 : -16711800));
            case FILL_LEVEL -> (val >= 95.0f) ? -56798 : ((val >= 80.0f) ? -22016 : ((val <= 5.0f) ? -30652 : -16711800));
            default -> -16711800;
        };
    }

    static void renderSourceStatus(NativeImage image, DashboardZone zone, StatsDisplayPanelBE be, DisplayTheme theme) {
        boolean anyActive = false;
        for (SourceConfig src : be.getSourceRegistry().values()) {
            if (src.isActive()) {
                anyActive = true;
                break;
            }
        }
        final int color = be.getSourceRegistry().isEmpty() ? -12303292 : (anyActive ? -16711800 : -48060);
        renderLightAndLabel(image, zone, color, "SRC", theme);
    }

    static void renderChannelCount(NativeImage image, DashboardZone zone, int count, DisplayTheme theme) {
        renderLightAndLabel(image, zone, theme.accent, count + " ch", theme);
    }

    static void renderDataFreshness(NativeImage image, DashboardZone zone, StatsDisplayPanelBE be, DisplayTheme theme) {
        boolean anyActive = false;
        boolean anyInactive = false;
        for (SourceConfig src : be.getSourceRegistry().values()) {
            if (src.isActive()) {
                anyActive = true;
            }
            else {
                anyInactive = true;
            }
        }
        int color;
        if (anyActive && !anyInactive) {
            color = -16711800;
        }
        else if (anyActive) {
            color = -22016;
        }
        else if (!be.getSourceRegistry().isEmpty()) {
            color = -48060;
        }
        else {
            color = -12303292;
        }
        renderLightAndLabel(image, zone, color, "DATA", theme);
    }

    static void renderAlertCount(NativeImage image, DashboardZone zone, int alertCount, DisplayTheme theme) {
        final int color = (alertCount == 0) ? -16711800 : ((alertCount <= 2) ? -22016 : -48060);
        renderLightAndLabel(image, zone, color, alertCount + " ALR", theme);
    }

    private static void renderLightAndLabel(NativeImage image, DashboardZone zone, int color, String label, DisplayTheme theme) {
        final int lightH = Math.max(4, zone.height / 3);
        TextureGraphRenderer.fillRect(image, zone.x + 3, zone.y + 2, zone.width - 6, lightH, color);
        BitmapFont.drawCentered(image, label, zone.x, zone.y + 2 + lightH + 2, zone.width, theme.dim, 1);
    }
}
