package com.createstats.dashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import com.createstats.display.DisplayTheme;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.renderer.BitmapFont;
import com.createstats.renderer.TextureGraphRenderer;
import java.util.List;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DashboardRenderer
{
    private static final Map<Long, CachedLayout> LAYOUT_CACHE;

    private DashboardRenderer() {
    }

    public static void clearLayoutCache() {
        DashboardRenderer.LAYOUT_CACHE.clear();
    }

    public static void renderDashboard(NativeImage image, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final StatsDisplaySettings settings = be.getSettings();
        final DisplayTheme theme = settings.getDisplayTheme();
        final String mainChannelId = (settings.getDashboardMainChannelId() != null) ? settings.getDashboardMainChannelId() : "";
        final String channelSignature = buildChannelSignature(channels);
        TextureGraphRenderer.fillRect(image, 0, 0, w, h, theme.graphBg);
        TextureGraphRenderer.drawBorder(image, 0, 0, w, h, 2, theme.border);
        final boolean anyAlert = channels.stream().anyMatch(StatsDisplayPanelBE.DataChannel::isAlertTriggered);
        if (anyAlert) {
            final long pulse = System.currentTimeMillis() / 300L;
            final int alertAlpha = (int)(180.0 + 75.0 * Math.sin(pulse * 3.141592653589793));
            final int alertColor = alertAlpha << 24 | 0xFF2222;
            TextureGraphRenderer.drawBorder(image, 1, 1, w - 2, h - 2, 2, alertColor);
        }
        final long beKey = be.getBlockPos().asLong();
        DashboardLayout layout;
        final CachedLayout cached = DashboardRenderer.LAYOUT_CACHE.get(beKey);
        if (settings.isDashboardLayoutLocked() && cached != null && cached.matches(w, h, mainChannelId, channelSignature)) {
            layout = cached.layout;
        }
        else {
            layout = DashboardLayoutEngine.compute(w, h, be, channels);
            DashboardRenderer.LAYOUT_CACHE.put(beKey, new CachedLayout(w, h, mainChannelId, channelSignature, layout));
        }
        final DashboardZone header = layout.getHeaderZone();
        final DashboardZone bottom = layout.getBottomZone();
        final DashboardZone main = layout.getMainZone();
        final int headerBg = blendColor(theme.graphBg, theme.tabBg, 0.5f);
        final int bottomBg = blendColor(theme.graphBg, -16777216, 0.15f);
        if (header != null) {
            TextureGraphRenderer.fillRect(image, header.x, header.y, header.width, header.height, headerBg);
        }
        if (bottom != null) {
            TextureGraphRenderer.fillRect(image, bottom.x, bottom.y, bottom.width, bottom.height, bottomBg);
        }
        for (DashboardZone corner : layout.getCornerZones()) {
            TextureGraphRenderer.fillRect(image, corner.x, corner.y, corner.width, corner.height, theme.tabBg);
        }
        if (header != null) {
            TextureGraphRenderer.drawHLine(image, header.x, header.y + header.height, header.width, theme.dim);
        }
        if (bottom != null) {
            TextureGraphRenderer.drawHLine(image, bottom.x, bottom.y, bottom.width, theme.dim);
        }
        if (main != null && header != null && main.x > header.x + 10) {
            TextureGraphRenderer.drawVLine(image, main.x, main.y, main.height, theme.dim);
            TextureGraphRenderer.drawVLine(image, main.x + main.width, main.y, main.height, theme.dim);
        }
        if (main != null && main.assignedChannel != null) {
            final StatsDisplayPanelBE.DataChannel mainCh = main.assignedChannel;
            final int mainHeaderH = TextureGraphRenderer.renderHeader(image, be, mainCh, main.x + 4, main.y + 2, main.width - 8, theme, settings);
            final int graphY = main.y + mainHeaderH + 4;
            final int graphH = main.height - mainHeaderH - 6;
            final int channelIdx = Math.max(0, channels.indexOf(mainCh));
            TextureGraphRenderer.renderGraph(image, mainCh, channelIdx, settings.getGraphType(), main.x + 4, graphY, main.width - 8, graphH, theme, settings);
            if (settings.showValueOverlay()) {
                final String label = mainCh.getLabel();
                final String value = mainCh.getStore().getMetricType().formatValue(mainCh.getStore().getLatestValue());
                final String overlayText = label + ": " + value;
                final int overlayScale = Math.max(1, Math.min(3, main.width / 80));
                final int textH = BitmapFont.textHeight(overlayScale);
                final int stripY = graphY + graphH / 2 - textH / 2 - 2;
                final int bgColor = BitmapFont.argbToAbgr(0xCC000000 | (theme.graphBg & 0xFFFFFF));
                for (int py = stripY; py < stripY + textH + 4; ++py) {
                    for (int px = main.x; px < main.x + main.width; ++px) {
                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            image.setPixelRGBA(px, py, bgColor);
                        }
                    }
                }
                BitmapFont.drawCentered(image, overlayText, main.x, stripY + 2, main.width, theme.text, overlayScale);
            }
        }
        int sparkColorIdx = 1;
        for (DashboardZone zone : layout.getSparklineZones()) {
            if (zone.assignedChannel != null) {
                SparklineRenderer.render(image, zone, zone.assignedChannel, sparkColorIdx, theme);
                ++sparkColorIdx;
            }
            else {
                final int cy = zone.y + zone.height / 2;
                TextureGraphRenderer.drawHLine(image, zone.x + 4, cy, zone.width - 8, theme.grid);
            }
        }
        int alertCount = 0;
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            if (ch.isAlertTriggered()) {
                ++alertCount;
            }
        }
        for (DashboardZone zone2 : layout.getCornerZones()) {
            if (zone2.assignedChannel != null) {
                CornerIndicatorRenderer.render(image, zone2, zone2.assignedChannel, be, theme);
            }
            else {
                switch (zone2.type) {
                    case CORNER_TL: {
                        CornerIndicatorRenderer.renderSourceStatus(image, zone2, be, theme);
                        continue;
                    }
                    case CORNER_TR: {
                        CornerIndicatorRenderer.renderChannelCount(image, zone2, channels.size(), theme);
                        continue;
                    }
                    case CORNER_BL: {
                        CornerIndicatorRenderer.renderDataFreshness(image, zone2, be, theme);
                        continue;
                    }
                    case CORNER_BR: {
                        CornerIndicatorRenderer.renderAlertCount(image, zone2, alertCount, theme);
                        continue;
                    }
                }
            }
        }
        if (header != null) {
            final long dayTime = (be.getLevel() != null) ? be.getLevel().getDayTime() : 0L;
            final List<DashboardZone> corners = layout.getCornerZones();
            final int cornerW = corners.isEmpty() ? 0 : corners.get(0).width;
            final int clockX = header.x + cornerW + 8;
            final int clockW = header.width - 2 * (cornerW + 8);
            ClockWidget.render(image, clockX, header.y, clockW, header.height, dayTime, settings.getClockFormat(), theme);
            final int displayedCount = countDisplayedChannels(layout);
            if (channels.size() > displayedCount) {
                final String overflow = "[" + displayedCount + "/" + channels.size() + "]";
                final int trCornerW = (corners.size() > 1) ? corners.get(1).width : cornerW;
                BitmapFont.drawRight(image, overflow, header.x + header.width - trCornerW - 10, header.y + (header.height - BitmapFont.textHeight(1)) / 2, theme.dim, 1);
            }
        }
        if (bottom != null) {
            AlertTickerRenderer.render(image, bottom, be, channels, theme);
        }
    }

    static int blendColor(int c1, int c2, float factor) {
        final int r = (int)((c1 >> 16 & 0xFF) * (1.0f - factor) + (c2 >> 16 & 0xFF) * factor);
        final int g = (int)((c1 >> 8 & 0xFF) * (1.0f - factor) + (c2 >> 8 & 0xFF) * factor);
        final int b = (int)((c1 & 0xFF) * (1.0f - factor) + (c2 & 0xFF) * factor);
        final int a = (int)((c1 >> 24 & 0xFF) * (1.0f - factor) + (c2 >> 24 & 0xFF) * factor);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String buildChannelSignature(List<StatsDisplayPanelBE.DataChannel> channels) {
        final ArrayList<String> ids = new ArrayList<String>(channels.size());
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            ids.add(channel.getId());
        }
        Collections.sort(ids);
        return String.join("|", ids);
    }

    private static int countDisplayedChannels(DashboardLayout layout) {
        final HashSet<String> displayedChannelIds = new HashSet<String>();
        final DashboardZone main = layout.getMainZone();
        if (main != null && main.assignedChannel != null) {
            displayedChannelIds.add(main.assignedChannel.getId());
        }
        for (DashboardZone zone : layout.getSparklineZones()) {
            if (zone.assignedChannel != null) {
                displayedChannelIds.add(zone.assignedChannel.getId());
            }
        }
        for (DashboardZone zone2 : layout.getCornerZones()) {
            if (zone2.assignedChannel != null) {
                displayedChannelIds.add(zone2.assignedChannel.getId());
            }
        }
        return displayedChannelIds.size();
    }

    private static final class CachedLayout
    {
        private final int width;
        private final int height;
        private final String mainChannelId;
        private final String channelSignature;
        private final DashboardLayout layout;
        private CachedLayout(int width, int height, String mainChannelId, String channelSignature, DashboardLayout layout) {
            this.width = width;
            this.height = height;
            this.mainChannelId = mainChannelId;
            this.channelSignature = channelSignature;
            this.layout = layout;
        }
        private boolean matches(int width, int height, String mainChannelId, String channelSignature) {
            return this.width == width && this.height == height && this.mainChannelId.equals(mainChannelId) && this.channelSignature.equals(channelSignature);
        }
    }

    static {
        LAYOUT_CACHE = new ConcurrentHashMap<Long, CachedLayout>();
    }
}
