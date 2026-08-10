package com.createstats.dashboard;

import com.createstats.data.TimeSeriesStore;
import com.createstats.renderer.BitmapFont;
import com.createstats.renderer.TextureGraphRenderer;
import com.createstats.display.DisplayTheme;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SparklineRenderer
{
    private SparklineRenderer() {
    }
    
    public static void render(NativeImage image, DashboardZone zone, StatsDisplayPanelBE.DataChannel channel, int channelColorIndex, DisplayTheme theme) {
        final int x = zone.x;
        final int y = zone.y;
        final int w = zone.width;
        final int h = zone.height;
        final int color = TextureGraphRenderer.CHANNEL_COLORS[channelColorIndex % TextureGraphRenderer.CHANNEL_COLORS.length];
        final TimeSeriesStore store = channel.getStore();
        if (zone.sparklineIndex % 2 == 1) {
            final int tintBg = TextureGraphRenderer.withAlpha(theme.grid, 34);
            TextureGraphRenderer.fillRect(image, x, y, w, h, tintBg);
        }
        final int labelScale = 1;
        final int labelH = BitmapFont.textHeight(labelScale);
        final String label = TextureGraphRenderer.truncate(channel.getLabel(), w / 2, labelScale);
        BitmapFont.drawString(image, label, x + 2, y + 1, theme.text, labelScale);
        final String trend = TextureGraphRenderer.getTrendArrow(store);
        final int trendX = x + w - BitmapFont.measureString(trend, labelScale) - 2;
        BitmapFont.drawString(image, trend, trendX, y + 1, theme.dim, labelScale);
        if (channel.isAlertTriggered()) {
            final long pulse = System.currentTimeMillis() / 300L;
            final int alpha = (int)(180.0 + 75.0 * Math.sin(pulse * 3.141592653589793));
            final int dotColor = alpha << 24 | 0xFF2222;
            TextureGraphRenderer.fillRect(image, trendX - 5, y + 2, 3, 3, dotColor);
        }
        final int valueScale = 1;
        final int valueH = BitmapFont.textHeight(valueScale);
        final int graphY = y + labelH + 3;
        final int graphH = h - labelH - valueH - 6;
        final int graphX = x + 2;
        final int graphW = w - 4;
        if (graphH > 4 && graphW > 4) {
            final float[] data = store.getData();
            if (data != null && data.length >= 2) {
                float dataMin = Float.MAX_VALUE;
                float dataMax = -3.4028235E38f;
                for (float v : data) {
                    dataMin = Math.min(dataMin, v);
                    dataMax = Math.max(dataMax, v);
                }
                float range = dataMax - dataMin;
                if (range < 0.001f) {
                    range = Math.max(1.0f, Math.abs(dataMin) * 0.1f);
                }
                for (int step = Math.max(1, data.length / graphW), i = 0; i < graphW - 1 && i * step < data.length - 1; ++i) {
                    final int di1 = Math.min(i * step, data.length - 1);
                    final int di2 = Math.min((i + 1) * step, data.length - 1);
                    final float v2 = Math.max(0.0f, Math.min(1.0f, (data[di1] - dataMin) / range));
                    final float v3 = Math.max(0.0f, Math.min(1.0f, (data[di2] - dataMin) / range));
                    final int px1 = graphX + i;
                    final int py1 = graphY + graphH - (int)(v2 * graphH);
                    final int px2 = graphX + i + 1;
                    final int py2 = graphY + graphH - (int)(v3 * graphH);
                    TextureGraphRenderer.drawLine(image, px1, py1, px2, py2, color);
                }
            }
        }
        final String value = store.getMetricType().formatValue(store.getLatestValue());
        final int valueColor = channel.isAlertTriggered() ? -48060 : color;
        BitmapFont.drawRight(image, value, x + w - 2, y + h - valueH - 1, valueColor, valueScale);
    }
}
