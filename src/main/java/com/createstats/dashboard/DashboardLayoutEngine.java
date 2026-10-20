package com.createstats.dashboard;

import java.util.Iterator;
import java.util.Comparator;
import com.createstats.data.MetricType;
import java.util.Collection;
import java.util.ArrayList;
import com.createstats.display.StatsDisplaySettings;
import java.util.List;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.ModConfiguration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DashboardLayoutEngine
{
    public static final int MIN_BLOCKS_THRESHOLD = 6;
    public static final int MIN_DIMENSION = 3;

    private DashboardLayoutEngine() {
    }

    private static int getMinBlocksThreshold() {
        return (int)ModConfiguration.COMMON.dashboardMinBlocks.get();
    }

    private static int getMinDimension() {
        return (int)ModConfiguration.COMMON.dashboardMinDimension.get();
    }

    public static boolean shouldActivate(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels) {
        final StatsDisplaySettings settings = be.getSettings();
        final StatsDisplaySettings.DisplayMode mode = settings.getDisplayMode();
        final int mbW = be.getMultiblockWidth();
        final int mbH = be.getMultiblockHeight();
        final int blocks = mbW * mbH;
        final int chCount = channels.size();
        if (mode == StatsDisplaySettings.DisplayMode.SPECIALIZED) {
            org.slf4j.LoggerFactory.getLogger("CreateStats").debug("[Dashboard] shouldActivate=false: mode=SPECIALIZED, {}x{} blocks={} ch={}", mbW, mbH, blocks, chCount);
            return false;
        }
        if (mode == StatsDisplaySettings.DisplayMode.SINGLE) {
            org.slf4j.LoggerFactory.getLogger("CreateStats").debug("[Dashboard] shouldActivate=false: mode=SINGLE, {}x{} blocks={} ch={}", mbW, mbH, blocks, chCount);
            return false;
        }
        if (mode == StatsDisplaySettings.DisplayMode.DASHBOARD) {
            boolean result = chCount >= 1 && blocks >= 4;
            org.slf4j.LoggerFactory.getLogger("CreateStats").debug("[Dashboard] shouldActivate={}: mode=DASHBOARD, {}x{} blocks={} ch={}", result, mbW, mbH, blocks, chCount);
            return result;
        }
        if (blocks < getMinBlocksThreshold()) {
            org.slf4j.LoggerFactory.getLogger("CreateStats").debug("[Dashboard] shouldActivate=false: blocks={}<{}, {}x{} ch={}", blocks, getMinBlocksThreshold(), mbW, mbH, chCount);
            return false;
        }
        final int minDim = getMinDimension();
        boolean result = (mbW >= minDim || mbH >= minDim) && chCount >= 2;
        org.slf4j.LoggerFactory.getLogger("CreateStats").debug("[Dashboard] shouldActivate={}: mode=AUTO, {}x{} blocks={} ch={} minDim={}", result, mbW, mbH, blocks, chCount, minDim);
        return result;
    }

    public static DashboardLayout compute(int texW, int texH, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels) {
        final StatsDisplaySettings settings = be.getSettings();
        final List<DashboardZone> zones = new ArrayList<DashboardZone>();
        final int margin = Math.max(3, texW / 64);
        final int drawX;
        final int drawY;
        final int M = drawY = (drawX = margin + 2);
        final int drawW = texW - 2 * M;
        final int drawH = texH - 2 * M;
        final float aspect = drawW / (float)drawH;
        final boolean isPortrait = aspect < 0.8f;
        final boolean isSquare = aspect >= 0.8f && aspect <= 1.2f;
        final int headerH = clamp(drawH * 10 / 100, 20, 40);
        final int bottomH = clamp(drawH * 8 / 100, 16, 32);
        final int cornerSize = Math.min(headerH - 4, 24);
        int sideW = 0;
        if (!isPortrait && drawW >= 300) {
            final int pct = isSquare ? 15 : 20;
            sideW = clamp(drawW * pct / 100, 60, 120);
        }
        zones.add(new DashboardZone(DashboardZone.ZoneType.HEADER, drawX, drawY, drawW, headerH));
        zones.add(new DashboardZone(DashboardZone.ZoneType.BOTTOM, drawX, drawY + drawH - bottomH, drawW, bottomH));
        zones.add(new DashboardZone(DashboardZone.ZoneType.CORNER_TL, drawX + 2, drawY + 2, cornerSize, cornerSize));
        zones.add(new DashboardZone(DashboardZone.ZoneType.CORNER_TR, drawX + drawW - cornerSize - 2, drawY + 2, cornerSize, cornerSize));
        zones.add(new DashboardZone(DashboardZone.ZoneType.CORNER_BL, drawX + 2, drawY + drawH - bottomH + 2, cornerSize, cornerSize));
        zones.add(new DashboardZone(DashboardZone.ZoneType.CORNER_BR, drawX + drawW - cornerSize - 2, drawY + drawH - bottomH + 2, cornerSize, cornerSize));
        final int mainX = drawX + sideW;
        int mainY = drawY + headerH;
        final int mainW = drawW - 2 * sideW;
        int mainH = drawH - headerH - bottomH;
        int sparkRowH = 0;
        int sparkTopRows = 0;
        int sparkBotRows = 0;
        if (isPortrait) {
            sparkRowH = clamp(drawH * 10 / 100, 40, 70);
            final int totalSparkRows = Math.min(4, channels.size() / 2);
            sparkTopRows = (totalSparkRows + 1) / 2;
            sparkBotRows = totalSparkRows / 2;
            final int sparkTopH = sparkTopRows * sparkRowH;
            final int sparkBotH = sparkBotRows * sparkRowH;
            mainY += sparkTopH;
            mainH -= sparkTopH + sparkBotH;
        }
        zones.add(new DashboardZone(DashboardZone.ZoneType.MAIN, mainX, mainY, mainW, mainH));
        if (!isPortrait && sideW > 0) {
            final int maxPerSide = Math.max(1, (mainH - 8) / 100);
            final int sparkSlotH = (mainH - 8) / maxPerSide;
            for (int i = 0; i < maxPerSide; ++i) {
                final DashboardZone sz = new DashboardZone(DashboardZone.ZoneType.SPARKLINE, drawX + 4, mainY + 4 + i * sparkSlotH, sideW - 8, sparkSlotH - 4);
                sz.sparklineIndex = i;
                zones.add(sz);
            }
            for (int i = 0; i < maxPerSide; ++i) {
                final DashboardZone sz = new DashboardZone(DashboardZone.ZoneType.SPARKLINE, drawX + drawW - sideW + 4, mainY + 4 + i * sparkSlotH, sideW - 8, sparkSlotH - 4);
                sz.sparklineIndex = maxPerSide + i;
                zones.add(sz);
            }
        }
        else if (isPortrait) {
            final int sparkSlotW = (drawW - 8) / 2;
            int idx = 0;
            for (int row = 0; row < sparkTopRows; ++row) {
                for (int col = 0; col < 2; ++col) {
                    final DashboardZone sz2 = new DashboardZone(DashboardZone.ZoneType.SPARKLINE, drawX + 4 + col * (sparkSlotW + 4), drawY + headerH + row * sparkRowH + 2, sparkSlotW - 4, sparkRowH - 4);
                    sz2.sparklineIndex = idx++;
                    zones.add(sz2);
                }
            }
            for (int row = 0; row < sparkBotRows; ++row) {
                for (int col = 0; col < 2; ++col) {
                    final DashboardZone sz2 = new DashboardZone(DashboardZone.ZoneType.SPARKLINE, drawX + 4 + col * (sparkSlotW + 4), drawY + drawH - bottomH - (sparkBotRows - row) * sparkRowH + 2, sparkSlotW - 4, sparkRowH - 4);
                    sz2.sparklineIndex = idx++;
                    zones.add(sz2);
                }
            }
        }
        assignChannelsToZones(channels, zones, settings);
        return new DashboardLayout(zones);
    }

    private static void assignChannelsToZones(List<StatsDisplayPanelBE.DataChannel> channels, List<DashboardZone> zones, StatsDisplaySettings settings) {
        final List<StatsDisplayPanelBE.DataChannel> scored = new ArrayList<StatsDisplayPanelBE.DataChannel>(channels);
        scored.sort((a, b) -> Float.compare(ChannelPriorityScorer.computePriority(b), ChannelPriorityScorer.computePriority(a)));
        final String mainOverride = settings.getDashboardMainChannelId();
        StatsDisplayPanelBE.DataChannel mainChannel = null;
        if (!mainOverride.isEmpty()) {
            final Iterator<StatsDisplayPanelBE.DataChannel> scoredIt = scored.iterator();
            while (scoredIt.hasNext()) {
                final StatsDisplayPanelBE.DataChannel ch = scoredIt.next();
                if (ch.getId().equals(mainOverride)) {
                    mainChannel = ch;
                    scoredIt.remove();
                    break;
                }
            }
        }
        if (mainChannel == null && !scored.isEmpty()) {
            mainChannel = scored.remove(0);
        }
        for (DashboardZone zone : zones) {
            if (zone.type == DashboardZone.ZoneType.MAIN) {
                zone.assignedChannel = mainChannel;
                break;
            }
        }
        final List<StatsDisplayPanelBE.DataChannel> cornerChannels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        final Iterator<StatsDisplayPanelBE.DataChannel> it = scored.iterator();
        while (it.hasNext() && cornerChannels.size() < 4) {
            final StatsDisplayPanelBE.DataChannel ch2 = it.next();
            final MetricType mt = ch2.getStore().getMetricType();
            if (mt == MetricType.BOOLEAN_FLAG || mt == MetricType.REDSTONE_POWER) {
                cornerChannels.add(ch2);
                it.remove();
            }
        }
        final DashboardZone.ZoneType[] cornerTypes = { DashboardZone.ZoneType.CORNER_TL, DashboardZone.ZoneType.CORNER_TR, DashboardZone.ZoneType.CORNER_BL, DashboardZone.ZoneType.CORNER_BR };
        for (int i = 0; i < cornerTypes.length; ++i) {
            final DashboardZone.ZoneType ct = cornerTypes[i];
            final int idx = i;
            zones.stream().filter(z -> z.type == ct).findFirst().ifPresent(z -> z.assignedChannel = ((idx < cornerChannels.size()) ? cornerChannels.get(idx) : null));
        }
        final List<DashboardZone> sparkZones = new ArrayList<DashboardZone>();
        for (DashboardZone zone2 : zones) {
            if (zone2.type == DashboardZone.ZoneType.SPARKLINE) {
                sparkZones.add(zone2);
            }
        }
        sparkZones.sort(Comparator.comparingInt(z -> z.sparklineIndex));
        for (int j = 0; j < sparkZones.size() && j < scored.size(); ++j) {
            sparkZones.get(j).assignedChannel = scored.get(j);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
