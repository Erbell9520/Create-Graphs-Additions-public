package com.createstats.dashboard;

import com.createstats.data.TimeSeriesStore;
import java.util.Iterator;
import com.createstats.blockentity.SourceConfig;
import java.util.ArrayList;
import com.createstats.renderer.BitmapFont;
import com.createstats.ModConfiguration;
import com.createstats.display.DisplayTheme;
import java.util.List;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AlertTickerRenderer
{
    private AlertTickerRenderer() {
    }

    public static void render(NativeImage image, DashboardZone zone, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, DisplayTheme theme) {
        if (!(boolean)ModConfiguration.COMMON.dashboardTickerEnabled.get()) {
            return;
        }
        final String tickerText = buildTickerText(be, channels);
        final int tickerColor = getTickerColor(be, channels, theme);
        final int scale = 1;
        final int textH = BitmapFont.textHeight(scale);
        final int cornerSize = Math.min(zone.height - 4, 24);
        final int tickerX = zone.x + cornerSize + 8;
        final int tickerW = zone.width - 2 * (cornerSize + 8);
        final int tickerY = zone.y + (zone.height - textH) / 2;
        if (tickerW <= 0) {
            return;
        }
        final int totalTextWidth = BitmapFont.measureString(tickerText, scale);
        if (totalTextWidth <= tickerW) {
            BitmapFont.drawCentered(image, tickerText, tickerX, tickerY, tickerW, tickerColor, scale);
        }
        else {
            final int scrollRange = totalTextWidth + tickerW;
            final int tickerSpeed = (int)ModConfiguration.COMMON.dashboardTickerSpeed.get();
            final int scrollOffset = (int)(System.currentTimeMillis() / (50 / Math.max(1, tickerSpeed)) % scrollRange);
            int cx;
            final int textX = cx = tickerX + tickerW - scrollOffset;
            for (int i = 0; i < tickerText.length(); ++i) {
                final int charW = 6 * scale;
                if (cx + charW > tickerX && cx < tickerX + tickerW) {
                    BitmapFont.drawChar(image, tickerText.charAt(i), cx, tickerY, tickerColor, scale);
                }
                cx += charW;
                if (cx > tickerX + tickerW) {
                    break;
                }
            }
        }
    }

    static String buildTickerText(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels) {
        final List<String> messages = new ArrayList<String>();
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            if (ch.isAlertTriggered()) {
                final TimeSeriesStore store = ch.getStore();
                final String msg = "!! " + ch.getLabel() + ": " + store.getMetricType().formatValue(store.getLatestValue()) + (ch.isAlertAbove() ? " >= " : " <= ") + store.getMetricType().formatValue(ch.getAlertThreshold());
                messages.add(msg);
            }
        }
        for (SourceConfig src : be.getSourceRegistry().values()) {
            if (!src.isActive()) {
                messages.add("-- " + src.getDisplayLabel() + " disconnected");
            }
        }
        return messages.isEmpty() ? "All systems nominal" : String.join("  ···  ", messages);
    }

    private static int getTickerColor(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, DisplayTheme theme) {
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            if (ch.isAlertTriggered()) {
                return -48060;
            }
        }
        for (SourceConfig src : be.getSourceRegistry().values()) {
            if (!src.isActive()) {
                return theme.warn;
            }
        }
        return theme.accent;
    }
}
