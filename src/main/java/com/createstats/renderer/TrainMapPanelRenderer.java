package com.createstats.renderer;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.DisplayTheme;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.train.TrainMapData;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TrainMapPanelRenderer
{
    private static final Map<BlockPos, CachedTrainLayout> layoutCache = new HashMap<BlockPos, CachedTrainLayout>();
    private static final int[] ROUTE_COLORS = { -12276993, -12255352, -21948, -47958, -7811841, -5570748, -30652, -12255284 };
    private static final int ACTIVE_ROUTE = -39356;
    
    private TrainMapPanelRenderer() {
    }
    
    public static void disposeAll() {
        TrainMapPanelRenderer.layoutCache.clear();
    }
    
    public static void renderTrainMap(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TrainMapData data = be.getTrainMapData();
        if (data == null || data.getStations().isEmpty()) {
            renderNoTrainData(image, x, y, w, h, theme, "");
            return;
        }
        final String activeDimension = selectPanelDimension(be, data);
        final TrainMapData visibleData = data.filterByDimension(activeDimension);
        if (visibleData.getStations().isEmpty()) {
            renderNoTrainData(image, x, y, w, h, theme, activeDimension);
            return;
        }
        final int layoutHash = computeLayoutHash(visibleData, be.getTrainMapGroups());
        final BlockPos pos = be.getBlockPos();
        CachedTrainLayout cached = TrainMapPanelRenderer.layoutCache.get(pos);
        if (cached == null || cached.hash != layoutHash || cached.x != x || cached.y != y || cached.w != w || cached.h != h) {
            cached = computeLayout(visibleData, be.getTrainMapGroups(), x, y, w, h);
            cached.hash = layoutHash;
            TrainMapPanelRenderer.layoutCache.put(pos, cached);
        }
        if (settings.showGrid()) {
            renderGrid(image, x, y, w, h, theme);
        }
        renderRouteEdges(image, visibleData, cached, theme);
        renderNodeMarkers(image, visibleData, cached, theme);
        renderTrainIcons(image, visibleData, cached, theme, be);
        renderHeader(image, x, y, w, theme, activeDimension, visibleData.getStations().size(), cached.nodeCount, visibleData.getTrains().size(), cached.groupCount);
    }

    public static void renderHighQualityTrainMap(NativeImage image, StatsDisplayPanelBE be, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        final TrainMapData data = be.getTrainMapData();
        if (data == null || data.getStations().isEmpty()) {
            renderNoTrainData(image, x, y, w, h, theme, "");
            return;
        }
        final String activeDimension = selectPanelDimension(be, data);
        final TrainMapData visibleData = data.filterByDimension(activeDimension);
        if (visibleData.getStations().isEmpty()) {
            renderNoTrainData(image, x, y, w, h, theme, activeDimension);
            return;
        }
        final int layoutHash = computeLayoutHash(visibleData, be.getTrainMapGroups()) * 31 + 17;
        final BlockPos pos = be.getBlockPos();
        CachedTrainLayout cached = TrainMapPanelRenderer.layoutCache.get(pos);
        if (cached == null || cached.hash != layoutHash || cached.x != x || cached.y != y || cached.w != w || cached.h != h) {
            cached = computeLayout(visibleData, be.getTrainMapGroups(), x, y, w, h);
            cached.hash = layoutHash;
            TrainMapPanelRenderer.layoutCache.put(pos, cached);
        }
        renderFullscreenStyleBackplate(image, x, y, w, h, theme);
        if (settings.showGrid()) {
            renderHighQualityGrid(image, x, y, w, h, theme);
        }
        renderRouteEdgesHighQuality(image, visibleData, cached, theme);
        renderNodeMarkersHighQuality(image, visibleData, cached, theme);
        renderTrainIconsHighQuality(image, visibleData, cached, theme, be);
        renderHighQualityHeader(image, x, y, w, theme, activeDimension, visibleData.getStations().size(), visibleData.getTrains().size(), cached.groupCount);
    }
    
    private static void renderNoTrainData(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, String dimension) {
        final int scale = (w >= 256) ? 2 : 1;
        final String title = dimension.isEmpty() ? "No Train Data" : ("No " + shortDimensionName(dimension) + " Data");
        BitmapFont.drawCentered(image, title, x, y + h / 2 - BitmapFont.textHeight(scale), w, theme.dim, scale);
        BitmapFont.drawCentered(image, "Connect Display Link", x, y + h / 2 + 2, w, theme.text, scale);
        BitmapFont.drawCentered(image, "to a Train Station", x, y + h / 2 + 2 + BitmapFont.textHeight(scale) + 2, w, theme.text, scale);
    }
    
    private static void renderHeader(NativeImage image, int x, int y, int w, DisplayTheme theme, String dimension, int stationCount, int nodeCount, int trainCount, int groupCount) {
        final int scale = (w >= 256) ? 2 : 1;
        final String header = dimension.isEmpty() ? "Train Map" : ("Train Map - " + shortDimensionName(dimension));
        final String info = (w >= 360) ? (stationCount + " stn | " + nodeCount + " nodes | " + trainCount + " train" + ((trainCount != 1) ? "s" : "") + ((groupCount > 0) ? (" | " + groupCount + " grp") : "")) : (nodeCount + " nodes | " + trainCount + " trn" + ((groupCount > 0) ? (" | " + groupCount + " grp") : ""));
        BitmapFont.drawString(image, header, x + 2, y + 2, theme.border, scale);
        BitmapFont.drawRight(image, info, x + w - 2, y + 2, theme.dim, scale);
    }
    
    private static String selectPanelDimension(StatsDisplayPanelBE be, TrainMapData data) {
        if (be.getLevel() != null) {
            final String panelDimension = be.getLevel().dimension().location().toString();
            if (data.getDimensions().contains(panelDimension)) {
                return panelDimension;
            }
        }
        for (String dimension : data.getDimensions()) {
            return dimension;
        }
        return "";
    }
    
    private static String shortDimensionName(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "Trains";
        }
        if (dimension.equals("minecraft:overworld")) {
            return "Overworld";
        }
        if (dimension.equals("minecraft:the_nether")) {
            return "Nether";
        }
        if (dimension.equals("minecraft:the_end")) {
            return "The End";
        }
        final int idx = dimension.indexOf(58);
        final String path = (idx >= 0 && idx + 1 < dimension.length()) ? dimension.substring(idx + 1) : dimension;
        final String[] parts = path.split("_");
        final StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? dimension : sb.toString();
    }
    
    private static void renderGrid(NativeImage image, int x, int y, int w, int h, DisplayTheme theme) {
        final int gridRgb = theme.grid & 0xFFFFFF;
        final float opacity = 0.09f;
        final int spacing = Math.max(12, w / 16);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int gx = x + spacing; gx < x + w; gx += spacing) {
            for (int gy = y; gy < y + h; ++gy) {
                if (gx >= 0 && gx < imgW && gy >= 0 && gy < imgH) {
                    image.setPixelRGBA(gx, gy, blendPixel(image.getPixelRGBA(gx, gy), gridRgb, opacity));
                }
            }
        }
        for (int gy2 = y + spacing; gy2 < y + h; gy2 += spacing) {
            for (int gx2 = x; gx2 < x + w; ++gx2) {
                if (gx2 >= 0 && gx2 < imgW && gy2 >= 0 && gy2 < imgH) {
                    image.setPixelRGBA(gx2, gy2, blendPixel(image.getPixelRGBA(gx2, gy2), gridRgb, opacity));
                }
            }
        }
    }

    private static void renderFullscreenStyleBackplate(NativeImage image, int x, int y, int w, int h, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, darkenColor(theme.graphBg, 0.72f));
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, mixColors(theme.border, theme.accent, 0.18f));
        TextureGraphRenderer.fillRect(image, x + 1, y + 1, Math.max(0, w - 2), 2, mixColors(theme.accent, theme.border, 0.35f));
    }

    private static void renderHighQualityGrid(NativeImage image, int x, int y, int w, int h, DisplayTheme theme) {
        final int gridRgb = mixColors(theme.grid, theme.accent, 0.12f) & 0xFFFFFF;
        final int spacing = Math.max(14, w / 18);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int gx = x + spacing; gx < x + w; gx += spacing) {
            for (int gy = y + 4; gy < y + h - 2; ++gy) {
                if (gx >= 0 && gx < imgW && gy >= 0 && gy < imgH) {
                    image.setPixelRGBA(gx, gy, blendPixel(image.getPixelRGBA(gx, gy), gridRgb, 0.07f));
                }
            }
        }
        for (int gy2 = y + spacing; gy2 < y + h; gy2 += spacing) {
            for (int gx2 = x + 2; gx2 < x + w - 2; ++gx2) {
                if (gx2 >= 0 && gx2 < imgW && gy2 >= 0 && gy2 < imgH) {
                    image.setPixelRGBA(gx2, gy2, blendPixel(image.getPixelRGBA(gx2, gy2), gridRgb, 0.07f));
                }
            }
        }
    }

    private static void renderHighQualityHeader(NativeImage image, int x, int y, int w, DisplayTheme theme, String dimension, int stationCount, int trainCount, int groupCount) {
        final int scale = (w >= 300) ? 2 : 1;
        final String header = dimension.isEmpty() ? "Train Map" : ("Train Map - " + shortDimensionName(dimension));
        final String info = stationCount + " stn | " + trainCount + " train" + (trainCount == 1 ? "" : "s") + (groupCount > 0 ? (" | " + groupCount + " grp") : "");
        TextureGraphRenderer.fillRect(image, x + 2, y + 4, Math.max(0, w - 4), BitmapFont.textHeight(scale) + 6, 0x88000000 | (theme.graphBg & 0xFFFFFF));
        BitmapFont.drawString(image, header, x + 6, y + 7, mixColors(theme.text, theme.accent, 0.32f), scale);
        BitmapFont.drawRight(image, info, x + w - 6, y + 7, theme.dim, scale);
    }
    
    private static int blendPixel(int existingAbgr, int overlayArgbRgb, float opacity) {
        final int eR = existingAbgr & 0xFF;
        final int eG = (existingAbgr >> 8) & 0xFF;
        final int eB = (existingAbgr >> 16) & 0xFF;
        final int eA = (existingAbgr >> 24) & 0xFF;
        final int oR = (overlayArgbRgb >> 16) & 0xFF;
        final int oG = (overlayArgbRgb >> 8) & 0xFF;
        final int oB = overlayArgbRgb & 0xFF;
        final int r = (int)(eR + (oR - eR) * opacity);
        final int g = (int)(eG + (oG - eG) * opacity);
        final int b = (int)(eB + (oB - eB) * opacity);
        return eA << 24 | b << 16 | g << 8 | r;
    }
    
    private static void renderRouteEdges(NativeImage image, TrainMapData data, CachedTrainLayout layout, DisplayTheme theme) {
        if (layout.edges.isEmpty()) {
            return;
        }
        final Map<String, Integer> activeEdges = computeActiveEdgeCounts(data, layout);
        for (EdgeInfo edge : layout.edges) {
            final int[] a = layout.positions.get(edge.a);
            final int[] b = layout.positions.get(edge.b);
            if (a == null || b == null) {
                continue;
            }
            int color = (edge.routeIndex >= 0) ? ROUTE_COLORS[edge.routeIndex % ROUTE_COLORS.length] : mixColors(theme.border, theme.dim, 0.35f);
            if (edge.routeCount > 1) {
                color = mixColors(color, theme.accent, 0.35f);
            }
            if (activeEdges.getOrDefault(edge.key, 0) > 0) {
                color = mixColors(color, ACTIVE_ROUTE, 0.65f);
            }
            drawWeightedLine(image, a[0], a[1], b[0], b[1], color, edge.routeCount);
            final int activeCount = activeEdges.getOrDefault(edge.key, 0);
            if (activeCount > 0) {
                drawRoutePulse(image, a[0], a[1], b[0], b[1], color, edge, activeCount, data.getServerTick());
            }
        }
    }

    private static void renderRouteEdgesHighQuality(NativeImage image, TrainMapData data, CachedTrainLayout layout, DisplayTheme theme) {
        if (layout.edges.isEmpty()) {
            return;
        }
        final Map<String, Integer> activeEdges = computeActiveEdgeCounts(data, layout);
        for (EdgeInfo edge : layout.edges) {
            final int[] a = layout.positions.get(edge.a);
            final int[] b = layout.positions.get(edge.b);
            if (a == null || b == null) {
                continue;
            }
            int color = (edge.routeIndex >= 0) ? ROUTE_COLORS[edge.routeIndex % ROUTE_COLORS.length] : mixColors(theme.border, theme.dim, 0.35f);
            final int activeCount = activeEdges.getOrDefault(edge.key, 0);
            if (edge.routeCount > 1) {
                color = mixColors(color, theme.accent, 0.28f);
            }
            if (activeCount > 0) {
                color = mixColors(color, ACTIVE_ROUTE, 0.55f);
            }
            drawWideLine(image, a[0], a[1], b[0], b[1], darkenColor(color, 0.45f), Math.min(5, edge.routeCount + 3));
            drawWideLine(image, a[0], a[1], b[0], b[1], color, Math.min(3, edge.routeCount + 1));
            drawRouteBeads(image, a[0], a[1], b[0], b[1], color, edge, data.getServerTick(), activeCount);
            if (activeCount > 0) {
                drawRoutePulse(image, a[0], a[1], b[0], b[1], color, edge, activeCount, data.getServerTick());
            }
        }
    }
    
    private static void renderNodeMarkers(NativeImage image, TrainMapData data, CachedTrainLayout layout, DisplayTheme theme) {
        final Map<UUID, Integer> dockedCounts = computeDockedCounts(data, layout);
        final List<LabelCandidate> labels = new ArrayList<LabelCandidate>();
        for (UUID nodeId : layout.nodeOrder) {
            final NodeInfo node = layout.nodes.get(nodeId);
            final int[] pos = layout.positions.get(nodeId);
            if (node == null || pos == null) {
                continue;
            }
            final int docked = dockedCounts.getOrDefault(nodeId, 0);
            int radius = node.group ? 6 : 4;
            if (layout.w >= 512) {
                ++radius;
            }
            if (node.degree >= 3) {
                ++radius;
            }
            if (docked > 0) {
                ++radius;
            }
            int ringColor = node.group ? theme.accent : (node.assembling ? -13244 : -2232577);
            if (node.degree >= 3) {
                ringColor = mixColors(ringColor, theme.border, 0.35f);
            }
            if (docked > 0) {
                ringColor = mixColors(ringColor, ACTIVE_ROUTE, 0.6f);
            }
            if (docked > 0) {
                drawDockedHalo(image, pos[0], pos[1], radius + 3, ACTIVE_ROUTE, data.getServerTick(), docked);
            }
            fillCircle(image, pos[0], pos[1], radius + 1, darkenColor(ringColor, 0.72f));
            fillCircle(image, pos[0], pos[1], radius, ringColor);
            fillCircle(image, pos[0], pos[1], Math.max(1, radius - 2), darkenColor(theme.graphBg, node.group ? 0.7f : 0.84f));
            if (docked > 0) {
                fillCircle(image, pos[0], pos[1], Math.max(1, radius / 3), ACTIVE_ROUTE);
            }
            final int priority = (node.group ? 1000 : 0) + docked * 250 + node.degree * 40 + (node.assembling ? 20 : 0);
            labels.add(new LabelCandidate(formatNodeLabel(node, layout.nodeCount > 16), pos[0], pos[1] + radius + 4, node.group ? mixColors(theme.accent, -1, 0.2f) : theme.text, priority, node.group));
        }
        labels.sort((a, b) -> Integer.compare(b.priority, a.priority));
        final int maxLabels = Math.max(6, Math.min(labels.size(), layout.groupCount + 4 + Math.max(2, layout.w / 140)));
        final ArrayList<int[]> occupied = new ArrayList<int[]>();
        int drawn = 0;
        for (LabelCandidate label : labels) {
            if (drawn >= maxLabels && !label.emphasized) {
                continue;
            }
            final int textW = BitmapFont.measureString(label.text, 1);
            int lx = label.centerX - textW / 2;
            int ly = label.baseY;
            lx = Math.max(layout.drawX, Math.min(layout.drawX + layout.drawW - textW - 2, lx));
            ly = Math.max(layout.drawY, Math.min(layout.drawY + layout.drawH - BitmapFont.textHeight(1) - 2, ly));
            final int[] rect = new int[] { lx - 2, ly - 1, lx + textW + 2, ly + BitmapFont.textHeight(1) + 1 };
            if (intersectsAny(rect, occupied)) {
                continue;
            }
            occupied.add(rect);
            TextureGraphRenderer.fillRect(image, rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], 0xBB000000 | (theme.graphBg & 0xFFFFFF));
            BitmapFont.drawString(image, label.text, lx, ly, label.color, 1);
            ++drawn;
        }
    }

    private static void renderNodeMarkersHighQuality(NativeImage image, TrainMapData data, CachedTrainLayout layout, DisplayTheme theme) {
        final Map<UUID, Integer> dockedCounts = computeDockedCounts(data, layout);
        final List<LabelCandidate> labels = new ArrayList<LabelCandidate>();
        for (UUID nodeId : layout.nodeOrder) {
            final NodeInfo node = layout.nodes.get(nodeId);
            final int[] pos = layout.positions.get(nodeId);
            if (node == null || pos == null) {
                continue;
            }
            final int docked = dockedCounts.getOrDefault(nodeId, 0);
            int radius = node.group ? 7 : 5;
            if (layout.w >= 384) {
                ++radius;
            }
            if (node.degree >= 3) {
                ++radius;
            }
            int stationColor = node.group ? mixColors(theme.accent, theme.text, 0.16f) : (node.assembling ? -13244 : -2232577);
            if (docked > 0) {
                stationColor = mixColors(stationColor, ACTIVE_ROUTE, 0.42f);
                drawDockedHalo(image, pos[0], pos[1], radius + 5, ACTIVE_ROUTE, data.getServerTick(), docked);
            }
            drawBlendedCircleRing(image, pos[0], pos[1], radius + 1, radius + 4, stationColor, 0.22f);
            fillCircle(image, pos[0], pos[1], radius + 2, darkenColor(stationColor, 0.48f));
            fillCircle(image, pos[0], pos[1], radius, stationColor);
            fillCircle(image, pos[0], pos[1], Math.max(2, radius - 3), darkenColor(theme.graphBg, node.group ? 0.58f : 0.76f));
            if (docked > 0) {
                fillCircle(image, pos[0], pos[1], Math.max(1, radius / 3), ACTIVE_ROUTE);
            }
            final int priority = (node.group ? 1200 : 0) + docked * 300 + node.degree * 60 + (node.assembling ? 30 : 0);
            labels.add(new LabelCandidate(formatNodeLabel(node, layout.nodeCount > 24), pos[0], pos[1] + radius + 5, node.group ? mixColors(theme.accent, -1, 0.28f) : theme.text, priority, node.group || docked > 0));
        }
        labels.sort((a, b) -> Integer.compare(b.priority, a.priority));
        final int maxLabels = Math.max(8, Math.min(labels.size(), layout.groupCount + 6 + Math.max(3, layout.w / 110)));
        final ArrayList<int[]> occupied = new ArrayList<int[]>();
        int drawn = 0;
        for (LabelCandidate label : labels) {
            if (drawn >= maxLabels && !label.emphasized) {
                continue;
            }
            final int textW = BitmapFont.measureString(label.text, 1);
            int lx = label.centerX - textW / 2;
            int ly = label.baseY;
            lx = Math.max(layout.drawX, Math.min(layout.drawX + layout.drawW - textW - 2, lx));
            ly = Math.max(layout.drawY, Math.min(layout.drawY + layout.drawH - BitmapFont.textHeight(1) - 2, ly));
            final int[] rect = new int[] { lx - 3, ly - 2, lx + textW + 3, ly + BitmapFont.textHeight(1) + 2 };
            if (intersectsAny(rect, occupied)) {
                continue;
            }
            occupied.add(rect);
            TextureGraphRenderer.fillRect(image, rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], 0xCC000000 | (theme.graphBg & 0xFFFFFF));
            BitmapFont.drawString(image, label.text, lx, ly, label.color, 1);
            ++drawn;
        }
    }
    
    private static void renderTrainIcons(NativeImage image, TrainMapData data, CachedTrainLayout layout, DisplayTheme theme, StatsDisplayPanelBE be) {
        if (data.getTrains().isEmpty()) {
            return;
        }
        final Map<String, TrainMapData.StationEntry> byName = indexStationsByName(data.getStations());
        final Map<UUID, TrainMapData.StationEntry> byId = indexStationsById(data.getStations());
        final boolean showLabels = data.getTrains().size() <= 4 && layout.nodeCount <= 14;
        final ArrayList<int[]> occupied = new ArrayList<int[]>();
        final int iconScale = (layout.w >= 512 || layout.h >= 384) ? 2 : 1;
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final int[] pos = resolveTrainPosition(train, byName, byId, layout);
            if (pos == null) {
                continue;
            }
            final int tx = Math.max(layout.drawX + 2, Math.min(layout.drawX + layout.drawW - 2, pos[0]));
            final int ty = Math.max(layout.drawY + 2, Math.min(layout.drawY + layout.drawH - 2, pos[1]));
            int trainColor = train.stopped ? ACTIVE_ROUTE : (train.waitingForSignal ? -13278 : -12255352);
            if (train.stopped && be.getLevel() != null && be.getLevel().getGameTime() % 20L < 7L) {
                trainColor = trainColor & 0xFFFFFF | 0x88000000;
            }
            drawTrainTrail(image, train, byName, byId, layout, tx, ty, trainColor, data.getServerTick());
            setPixelSafe(image, tx, ty - iconScale * 2, trainColor);
            for (int d = -iconScale; d <= iconScale; ++d) {
                setPixelSafe(image, tx + d, ty - iconScale, trainColor);
                setPixelSafe(image, tx + d, ty, trainColor);
                setPixelSafe(image, tx + d, ty + iconScale, trainColor);
            }
            setPixelSafe(image, tx, ty + iconScale * 2, trainColor);
            if (!showLabels && !train.waitingForSignal) {
                continue;
            }
            String name = train.name;
            if (name.length() > 10) {
                name = name.substring(0, 8) + "..";
            }
            final int textW = BitmapFont.measureString(name, 1);
            int lx = tx - textW / 2;
            int ly = ty - iconScale * 2 - BitmapFont.textHeight(1) - 2;
            if (ly < layout.drawY) {
                ly = ty + iconScale * 2 + 2;
            }
            lx = Math.max(layout.drawX, Math.min(layout.drawX + layout.drawW - textW - 1, lx));
            final int[] rect = new int[] { lx - 1, ly - 1, lx + textW + 1, ly + BitmapFont.textHeight(1) + 1 };
            if (intersectsAny(rect, occupied)) {
                continue;
            }
            occupied.add(rect);
            TextureGraphRenderer.fillRect(image, rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], 0xBB000000 | (theme.graphBg & 0xFFFFFF));
            BitmapFont.drawString(image, name, lx, ly, trainColor, 1);
        }
    }

    private static void renderTrainIconsHighQuality(NativeImage image, TrainMapData data, CachedTrainLayout layout, DisplayTheme theme, StatsDisplayPanelBE be) {
        if (data.getTrains().isEmpty()) {
            return;
        }
        final Map<String, TrainMapData.StationEntry> byName = indexStationsByName(data.getStations());
        final Map<UUID, TrainMapData.StationEntry> byId = indexStationsById(data.getStations());
        final boolean showLabels = data.getTrains().size() <= 8 && layout.nodeCount <= 22;
        final ArrayList<int[]> occupied = new ArrayList<int[]>();
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final int[] pos = resolveTrainPosition(train, byName, byId, layout);
            if (pos == null) {
                continue;
            }
            final int tx = Math.max(layout.drawX + 3, Math.min(layout.drawX + layout.drawW - 3, pos[0]));
            final int ty = Math.max(layout.drawY + 3, Math.min(layout.drawY + layout.drawH - 3, pos[1]));
            int trainColor = resolveTrainColor(train, theme);
            if (train.stopped && be.getLevel() != null && be.getLevel().getGameTime() % 20L < 7L) {
                trainColor = trainColor & 0xFFFFFF | 0x99000000;
            }
            final float[] dir = resolveTrainDirection(train, byName, byId, layout);
            drawTrainTrail(image, train, byName, byId, layout, tx, ty, trainColor, data.getServerTick());
            final int bodyLen = Math.max(6, Math.min(13, layout.w / 30));
            final int bodyW = Math.max(3, Math.min(5, layout.w / 100));
            final int x0 = Math.round(tx - dir[0] * bodyLen / 2.0f);
            final int y0 = Math.round(ty - dir[1] * bodyLen / 2.0f);
            final int x1 = Math.round(tx + dir[0] * bodyLen / 2.0f);
            final int y1 = Math.round(ty + dir[1] * bodyLen / 2.0f);
            if (train.waitingForSignal) {
                final float pulse = (float)(0.5 + 0.5 * Math.sin(data.getServerTick() * 0.2617993877991494));
                drawWideLine(image, x0, y0, x1, y1, 0x77000000 | (ACTIVE_ROUTE & 0xFFFFFF), bodyW + 5 + (int)(pulse * 2.0f));
            }
            drawWideLine(image, x0, y0, x1, y1, darkenColor(trainColor, 0.45f), bodyW + 2);
            drawWideLine(image, x0, y0, x1, y1, trainColor, bodyW);
            blendSquare(image, tx - 1, ty - 1, 3, mixColors(trainColor, -1, 0.32f), 0.88f);
            if (!showLabels && !train.waitingForSignal) {
                continue;
            }
            String name = train.name;
            if (name.length() > 13) {
                name = name.substring(0, 11) + "..";
            }
            final int textW = BitmapFont.measureString(name, 1);
            int lx = tx - textW / 2;
            int ly = ty - bodyLen / 2 - BitmapFont.textHeight(1) - 3;
            if (ly < layout.drawY) {
                ly = ty + bodyLen / 2 + 3;
            }
            lx = Math.max(layout.drawX, Math.min(layout.drawX + layout.drawW - textW - 1, lx));
            final int[] rect = new int[] { lx - 2, ly - 1, lx + textW + 2, ly + BitmapFont.textHeight(1) + 1 };
            if (intersectsAny(rect, occupied)) {
                continue;
            }
            occupied.add(rect);
            TextureGraphRenderer.fillRect(image, rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], 0xCC000000 | (theme.graphBg & 0xFFFFFF));
            BitmapFont.drawString(image, name, lx, ly, trainColor, 1);
        }
    }

    private static void drawRoutePulse(NativeImage image, int x0, int y0, int x1, int y1, int color, EdgeInfo edge, int activeCount, long serverTick) {
        final float dx = (float)(x1 - x0);
        final float dy = (float)(y1 - y0);
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 4.0f) {
            return;
        }
        final int pulseCount = Math.max(1, Math.min(3, activeCount));
        final int pulseColor = mixColors(color, ACTIVE_ROUTE, 0.72f);
        final double basePhase = (serverTick % 80L) / 80.0 + Math.max(0, edge.routeIndex) * 0.137;
        final int size = Math.max(2, Math.min(5, edge.routeCount + 2));
        for (int i = 0; i < pulseCount; ++i) {
            final float t = (float)((basePhase + i / (double)pulseCount) % 1.0);
            final int px = Math.round(x0 + dx * t) - size / 2;
            final int py = Math.round(y0 + dy * t) - size / 2;
            blendSquare(image, px, py, size, pulseColor, 0.74f);
        }
    }

    private static void drawWideLine(NativeImage image, int x0, int y0, int x1, int y1, int color, int width) {
        final int radius = Math.max(0, width / 2);
        if (radius <= 0) {
            TextureGraphRenderer.drawLine(image, x0, y0, x1, y1, color);
            return;
        }
        for (int ox = -radius; ox <= radius; ++ox) {
            for (int oy = -radius; oy <= radius; ++oy) {
                if (ox * ox + oy * oy <= radius * radius) {
                    TextureGraphRenderer.drawLine(image, x0 + ox, y0 + oy, x1 + ox, y1 + oy, color);
                }
            }
        }
    }

    private static void drawRouteBeads(NativeImage image, int x0, int y0, int x1, int y1, int color, EdgeInfo edge, long serverTick, int activeCount) {
        final float dx = (float)(x1 - x0);
        final float dy = (float)(y1 - y0);
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 18.0f) {
            return;
        }
        final int beadCount = Math.max(1, Math.min(5, (int)(len / 48.0f)));
        final float phase = activeCount > 0 ? (serverTick % 60L) / 60.0f : 0.0f;
        for (int i = 1; i <= beadCount; ++i) {
            final float t = ((i / (float)(beadCount + 1)) + phase * 0.16f + Math.max(0, edge.routeIndex) * 0.031f) % 1.0f;
            final int px = Math.round(x0 + dx * t);
            final int py = Math.round(y0 + dy * t);
            blendSquare(image, px - 1, py - 1, activeCount > 0 ? 4 : 3, mixColors(color, ACTIVE_ROUTE, activeCount > 0 ? 0.38f : 0.08f), activeCount > 0 ? 0.78f : 0.42f);
        }
    }

    private static void drawDockedHalo(NativeImage image, int cx, int cy, int baseRadius, int color, long serverTick, int dockedCount) {
        final float pulse = (float)(0.5 + 0.5 * Math.sin(serverTick * 0.3141592653589793));
        final int outerRadius = baseRadius + 1 + Math.min(3, dockedCount) + (int)(pulse * 2.0f);
        final int innerRadius = Math.max(1, baseRadius - 1);
        final float opacity = 0.16f + pulse * 0.18f;
        drawBlendedCircleRing(image, cx, cy, innerRadius, outerRadius, color, opacity);
    }

    private static void drawTrainTrail(NativeImage image, TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, CachedTrainLayout layout, int tx, int ty, int color, long serverTick) {
        if (train.stopped || train.waitingForSignal) {
            final float pulse = (float)(0.5 + 0.5 * Math.sin(serverTick * 0.39269908169872414));
            drawBlendedCircleRing(image, tx, ty, 3, 5, color, 0.18f + pulse * 0.2f);
            return;
        }
        TrainMapData.StationEntry destination = byId.get(train.activeSegmentToId);
        if (destination == null) {
            destination = byId.get(train.destinationStationId);
        }
        if (destination == null) {
            destination = train.nextDestination.isEmpty() ? null : findStation(byName, train.nextDestination);
        }
        TrainMapData.StationEntry origin = byId.get(train.activeSegmentFromId);
        if (origin == null) {
            origin = byId.get(train.previousStationId);
        }
        if (origin == null) {
            origin = findPreviousStop(train, byName, byId);
        }
        if (origin == null || destination == null) {
            return;
        }
        final UUID a = layout.effectiveMap.getOrDefault(origin.id, origin.id);
        final UUID b = layout.effectiveMap.getOrDefault(destination.id, destination.id);
        final int[] pa = layout.positions.get(a);
        final int[] pb = layout.positions.get(b);
        if (pa == null || pb == null) {
            return;
        }
        final float dx = (float)(pb[0] - pa[0]);
        final float dy = (float)(pb[1] - pa[1]);
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0f) {
            return;
        }
        final float ux = dx / len;
        final float uy = dy / len;
        final float speedGlow = Math.min(1.0f, Math.abs(train.signedSpeed) / 24.0f);
        for (int i = 1; i <= 3; ++i) {
            final int px = Math.round(tx - ux * i * 4.0f) - 1;
            final int py = Math.round(ty - uy * i * 4.0f) - 1;
            blendSquare(image, px, py, 2, color, Math.max(0.08f, (0.28f - i * 0.06f) * (0.45f + speedGlow)));
        }
    }

    private static void blendSquare(NativeImage image, int x, int y, int size, int color, float opacity) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int py = Math.max(0, y); py < Math.min(imgH, y + size); ++py) {
            for (int px = Math.max(0, x); px < Math.min(imgW, x + size); ++px) {
                image.setPixelRGBA(px, py, blendPixel(image.getPixelRGBA(px, py), color, opacity));
            }
        }
    }

    private static void drawBlendedCircleRing(NativeImage image, int cx, int cy, int innerRadius, int outerRadius, int color, float opacity) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int innerSq = innerRadius * innerRadius;
        final int outerSq = outerRadius * outerRadius;
        for (int dy = -outerRadius; dy <= outerRadius; ++dy) {
            for (int dx = -outerRadius; dx <= outerRadius; ++dx) {
                final int distSq = dx * dx + dy * dy;
                if (distSq < innerSq || distSq > outerSq) {
                    continue;
                }
                final int px = cx + dx;
                final int py = cy + dy;
                if (px >= 0 && px < imgW && py >= 0 && py < imgH) {
                    image.setPixelRGBA(px, py, blendPixel(image.getPixelRGBA(px, py), color, opacity));
                }
            }
        }
    }
    
    private static CachedTrainLayout computeLayout(TrainMapData data, List<String> groupPatterns, int x, int y, int w, int h) {
        final GroupResolution groups = resolveGroups(data.getStations(), groupPatterns);
        final Map<String, TrainMapData.StationEntry> byName = indexStationsByName(data.getStations());
        final Map<UUID, TrainMapData.StationEntry> byId = indexStationsById(data.getStations());
        final LinkedHashMap<UUID, NodeInfo> nodes = buildNodes(data.getStations(), groups);
        final List<EdgeInfo> edges = buildEdges(data, byName, byId, groups.effectiveMap);
        applyDegrees(nodes, edges);
        final Map<UUID, float[]> normalized = computeGraphLayout(nodes, edges);
        final CachedTrainLayout layout = new CachedTrainLayout();
        layout.x = x;
        layout.y = y;
        layout.w = w;
        layout.h = h;
        layout.groupCount = groups.groupMembers.size();
        layout.nodeCount = nodes.size();
        layout.drawX = x + Math.max(10, Math.min(w, h) / 18);
        layout.drawY = y + ((w >= 320) ? 20 : 16);
        layout.drawW = Math.max(24, w - (layout.drawX - x) * 2);
        layout.drawH = Math.max(24, h - (layout.drawY - y) - Math.max(10, Math.min(w, h) / 20));
        layout.nodes = nodes;
        layout.nodeOrder = new ArrayList<UUID>(nodes.keySet());
        layout.positions = new HashMap<UUID, int[]>();
        layout.effectiveMap = groups.effectiveMap;
        layout.edges = edges;
        for (UUID nodeId : layout.nodeOrder) {
            final float[] pos = normalized.get(nodeId);
            if (pos == null) {
                continue;
            }
            final int px = clamp(layout.drawX + Math.round(pos[0] * layout.drawW), layout.drawX + 4, layout.drawX + layout.drawW - 4);
            final int py = clamp(layout.drawY + Math.round(pos[1] * layout.drawH), layout.drawY + 4, layout.drawY + layout.drawH - 4);
            layout.positions.put(nodeId, new int[] { px, py });
        }
        return layout;
    }
    
    private static int computeLayoutHash(TrainMapData data, List<String> groupPatterns) {
        int hash = data.getStations().size();
        for (TrainMapData.StationEntry station : data.getStations()) {
            hash = hash * 31 + station.id.hashCode();
            hash = hash * 31 + station.name.hashCode();
            hash = hash * 31 + Double.hashCode(station.x);
            hash = hash * 31 + Double.hashCode(station.z);
            hash = hash * 31 + Boolean.hashCode(station.assembling);
        }
        for (String pattern : groupPatterns) {
            hash = hash * 31 + pattern.hashCode();
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            hash = hash * 31 + train.id.hashCode();
            hash = hash * 31 + train.routeKey.hashCode();
            hash = hash * 31 + ((train.currentStationId != null) ? train.currentStationId.hashCode() : 0);
            hash = hash * 31 + ((train.previousStationId != null) ? train.previousStationId.hashCode() : 0);
            hash = hash * 31 + ((train.destinationStationId != null) ? train.destinationStationId.hashCode() : 0);
            hash = hash * 31 + ((train.activeSegmentFromId != null) ? train.activeSegmentFromId.hashCode() : 0);
            hash = hash * 31 + ((train.activeSegmentToId != null) ? train.activeSegmentToId.hashCode() : 0);
            for (String stop : train.scheduleStops) {
                hash = hash * 31 + stop.hashCode();
            }
        }
        return hash;
    }
    
    private static GroupResolution resolveGroups(List<TrainMapData.StationEntry> stations, List<String> groupPatterns) {
        final LinkedHashMap<String, List<UUID>> groupMembers = new LinkedHashMap<String, List<UUID>>();
        final HashMap<UUID, String> stationToGroup = new HashMap<UUID, String>();
        final HashMap<String, UUID> groupIds = new HashMap<String, UUID>();
        for (String pattern : groupPatterns) {
            final ArrayList<UUID> members = new ArrayList<UUID>();
            for (TrainMapData.StationEntry station : stations) {
                if (matchesGroupPattern(pattern, station.name)) {
                    members.add(station.id);
                    stationToGroup.put(station.id, pattern);
                }
            }
            if (!members.isEmpty()) {
                groupMembers.put(pattern, members);
                groupIds.put(pattern, UUID.nameUUIDFromBytes(("create_stats:train_map:" + pattern).getBytes(StandardCharsets.UTF_8)));
            }
        }
        final HashMap<UUID, UUID> effectiveMap = new HashMap<UUID, UUID>();
        for (TrainMapData.StationEntry station2 : stations) {
            final String group = stationToGroup.get(station2.id);
            effectiveMap.put(station2.id, (group != null) ? groupIds.get(group) : station2.id);
        }
        return new GroupResolution(effectiveMap, groupMembers, stationToGroup);
    }
    
    private static boolean matchesGroupPattern(String pattern, String name) {
        if (pattern == null || name == null) {
            return false;
        }
        if (pattern.endsWith("*") && pattern.startsWith("*")) {
            return name.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return name.endsWith(pattern.substring(1));
        }
        return name.equals(pattern);
    }
    
    private static Map<String, TrainMapData.StationEntry> indexStationsByName(List<TrainMapData.StationEntry> stations) {
        final LinkedHashMap<String, TrainMapData.StationEntry> byName = new LinkedHashMap<String, TrainMapData.StationEntry>();
        final ArrayList<TrainMapData.StationEntry> ordered = new ArrayList<TrainMapData.StationEntry>(stations);
        ordered.sort((a, b) -> {
            final int nameCompare = a.name.compareToIgnoreCase(b.name);
            return nameCompare != 0 ? nameCompare : a.id.compareTo(b.id);
        });
        for (TrainMapData.StationEntry station : ordered) {
            byName.putIfAbsent(station.name, station);
        }
        return byName;
    }

    private static Map<UUID, TrainMapData.StationEntry> indexStationsById(List<TrainMapData.StationEntry> stations) {
        final HashMap<UUID, TrainMapData.StationEntry> byId = new HashMap<UUID, TrainMapData.StationEntry>();
        for (TrainMapData.StationEntry station : stations) {
            byId.put(station.id, station);
        }
        return byId;
    }
    
    private static LinkedHashMap<UUID, NodeInfo> buildNodes(List<TrainMapData.StationEntry> stations, GroupResolution groups) {
        final LinkedHashMap<UUID, NodeInfo> nodes = new LinkedHashMap<UUID, NodeInfo>();
        for (TrainMapData.StationEntry station : stations) {
            final UUID effectiveId = groups.effectiveMap.getOrDefault(station.id, station.id);
            NodeInfo node = nodes.get(effectiveId);
            if (node == null) {
                final String groupPattern = groups.stationToGroup.get(station.id);
                node = new NodeInfo(effectiveId, station.name, groupPattern != null, groupPattern);
                nodes.put(effectiveId, node);
            }
            node.addStation(station);
        }
        return nodes;
    }
    
    private static List<EdgeInfo> buildEdges(TrainMapData data, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, Map<UUID, UUID> effectiveMap) {
        final LinkedHashMap<String, EdgeInfo> edges = new LinkedHashMap<String, EdgeInfo>();
        final HashMap<String, Integer> routeByKey = new HashMap<String, Integer>();
        int nextRouteIndex = 0;
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final List<TrainMapData.StationEntry> route = resolveRoute(train, byName, byId);
            if (route.size() < 2) {
                continue;
            }
            final String routeKey = (train.routeKey != null && !train.routeKey.isEmpty()) ? train.routeKey : routeKey(route);
            Integer routeIndex = routeByKey.get(routeKey);
            if (routeIndex == null) {
                routeIndex = nextRouteIndex++;
                routeByKey.put(routeKey, routeIndex);
            }
            UUID first = null;
            UUID previous = null;
            for (TrainMapData.StationEntry station : route) {
                final UUID effective = effectiveMap.getOrDefault(station.id, station.id);
                if (first == null) {
                    first = effective;
                }
                if (previous != null && !previous.equals(effective)) {
                    addEdge(edges, previous, effective, routeIndex);
                }
                previous = effective;
            }
            if (first != null && previous != null && !first.equals(previous)) {
                addEdge(edges, previous, first, routeIndex);
            }
        }
        return new ArrayList<EdgeInfo>(edges.values());
    }

    private static String routeKey(List<TrainMapData.StationEntry> route) {
        final ArrayList<String> ids = new ArrayList<String>();
        for (TrainMapData.StationEntry station : route) {
            ids.add(station.id.toString());
        }
        return String.join("|", ids);
    }
    
    private static void addEdge(Map<String, EdgeInfo> edges, UUID a, UUID b, int routeIndex) {
        final String key = edgeKey(a, b);
        EdgeInfo edge = edges.get(key);
        if (edge == null) {
            final UUID first = (a.compareTo(b) <= 0) ? a : b;
            final UUID second = (first == a) ? b : a;
            edge = new EdgeInfo(first, second, key, routeIndex);
            edges.put(key, edge);
        }
        edge.routeCount += 1;
    }
    
    private static void applyDegrees(Map<UUID, NodeInfo> nodes, List<EdgeInfo> edges) {
        for (EdgeInfo edge : edges) {
            final NodeInfo a = nodes.get(edge.a);
            final NodeInfo b = nodes.get(edge.b);
            if (a != null) {
                ++a.degree;
            }
            if (b != null) {
                ++b.degree;
            }
        }
    }
    
    private static Map<UUID, float[]> computeGraphLayout(Map<UUID, NodeInfo> nodes, List<EdgeInfo> edges) {
        final LinkedHashMap<UUID, float[]> result = new LinkedHashMap<UUID, float[]>();
        if (nodes.isEmpty()) {
            return result;
        }
        final ArrayList<UUID> nodeList = new ArrayList<UUID>(nodes.keySet());
        final int n = nodeList.size();
        if (n == 1) {
            result.put(nodeList.get(0), new float[] { 0.5f, 0.5f });
            return result;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -1.7976931348623157E308;
        double minZ = Double.MAX_VALUE;
        double maxZ = -1.7976931348623157E308;
        final HashMap<UUID, double[]> worldPos = new HashMap<UUID, double[]>();
        for (UUID nodeId2 : nodeList) {
            final NodeInfo node2 = nodes.get(nodeId2);
            final double[] pos = new double[] { node2.worldX / Math.max(1, node2.samples), node2.worldZ / Math.max(1, node2.samples) };
            worldPos.put(nodeId2, pos);
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minZ = Math.min(minZ, pos[1]);
            maxZ = Math.max(maxZ, pos[1]);
        }
        final double rangeX = Math.max(1.0, maxX - minX);
        final double rangeZ = Math.max(1.0, maxZ - minZ);
        final HashMap<Long, Integer> duplicateBuckets = new HashMap<Long, Integer>();
        final boolean collapsed = Math.abs(maxX - minX) < 1.0E-3 && Math.abs(maxZ - minZ) < 1.0E-3;
        for (int i = 0; i < n; ++i) {
            final UUID nodeId3 = nodeList.get(i);
            final double[] pos2 = worldPos.get(nodeId3);
            float nx = collapsed ? 0.5f : (float)(0.12 + (pos2[0] - minX) / rangeX * 0.76);
            float ny = collapsed ? 0.5f : (float)(0.12 + (pos2[1] - minZ) / rangeZ * 0.76);
            final long bucket = ((long)Math.round(nx * 1000.0f) << 32) ^ (long)Math.round(ny * 1000.0f);
            final int duplicateIndex = duplicateBuckets.getOrDefault(bucket, 0);
            duplicateBuckets.put(bucket, duplicateIndex + 1);
            if (duplicateIndex > 0 || collapsed) {
                final double angle = (nodeId3.hashCode() & 0xFFFF) / 65535.0 * 6.283185307179586 + duplicateIndex * 1.618033988749895;
                final float radius = collapsed ? (0.12f + (i % Math.max(1, n)) * 0.015f) : duplicateIndex * 0.022f;
                nx += (float)(Math.cos(angle) * radius);
                ny += (float)(Math.sin(angle) * radius);
            }
            result.put(nodeId3, new float[] { clampFloat(nx, 0.06f, 0.94f), clampFloat(ny, 0.06f, 0.94f) });
        }
        return result;
    }
    
    private static long pairKey(int a, int b, int n) {
        return (long)Math.min(a, b) * (long)n + Math.max(a, b);
    }
    
    private static List<TrainMapData.StationEntry> resolveRoute(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId) {
        final ArrayList<TrainMapData.StationEntry> result = new ArrayList<TrainMapData.StationEntry>();
        if (train.routeKey != null && !train.routeKey.isEmpty()) {
            for (String token : train.routeKey.split("\\|")) {
                addRouteStation(result, findRouteStation(byName, byId, token));
            }
        }
        if (result.size() < 2) {
            result.clear();
            for (String filter : train.scheduleStops) {
                addRouteStation(result, findStation(byName, filter));
            }
        }
        if (result.size() < 2) {
            result.clear();
            TrainMapData.StationEntry from = byId.get(train.activeSegmentFromId);
            if (from == null) {
                from = byId.get(train.previousStationId);
            }
            if (from == null) {
                from = byId.get(train.currentStationId);
            }
            TrainMapData.StationEntry to = byId.get(train.activeSegmentToId);
            if (to == null) {
                to = byId.get(train.destinationStationId);
            }
            if (to == null) {
                to = findStation(byName, train.nextDestination);
            }
            addRouteStation(result, from);
            addRouteStation(result, to);
        }
        return result;
    }

    private static TrainMapData.StationEntry findRouteStation(Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            final TrainMapData.StationEntry station = byId.get(UUID.fromString(token));
            if (station != null) {
                return station;
            }
        }
        catch (IllegalArgumentException ex) {}
        return findStation(byName, token);
    }

    private static void addRouteStation(List<TrainMapData.StationEntry> route, TrainMapData.StationEntry station) {
        if (station == null) {
            return;
        }
        if (!route.isEmpty() && route.get(route.size() - 1).id.equals(station.id)) {
            return;
        }
        route.add(station);
    }
    
    private static TrainMapData.StationEntry findStation(Map<String, TrainMapData.StationEntry> byName, String filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        final TrainMapData.StationEntry exact = byName.get(filter);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, TrainMapData.StationEntry> entry : byName.entrySet()) {
            if (matchesFilter(filter, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    private static boolean matchesFilter(String filter, String name) {
        return TrainMapData.matchesStationFilter(filter, name);
    }
    
    private static Map<UUID, Integer> computeDockedCounts(TrainMapData data, CachedTrainLayout layout) {
        final HashMap<UUID, Integer> docked = new HashMap<UUID, Integer>();
        final Map<String, TrainMapData.StationEntry> byName = indexStationsByName(data.getStations());
        final Map<UUID, TrainMapData.StationEntry> byId = indexStationsById(data.getStations());
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            TrainMapData.StationEntry station = byId.get(train.currentStationId);
            if (station == null && !train.currentStationName.isEmpty()) {
                station = findStation(byName, train.currentStationName);
            }
            if (station == null) {
                continue;
            }
            final UUID nodeId = layout.effectiveMap.getOrDefault(station.id, station.id);
            docked.put(nodeId, docked.getOrDefault(nodeId, 0) + 1);
        }
        return docked;
    }
    
    private static Map<String, Integer> computeActiveEdgeCounts(TrainMapData data, CachedTrainLayout layout) {
        final HashMap<String, Integer> counts = new HashMap<String, Integer>();
        final Map<String, TrainMapData.StationEntry> byName = indexStationsByName(data.getStations());
        final Map<UUID, TrainMapData.StationEntry> byId = indexStationsById(data.getStations());
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            TrainMapData.StationEntry destination = byId.get(train.activeSegmentToId);
            if (destination == null) {
                destination = byId.get(train.destinationStationId);
            }
            if (destination == null) {
                destination = train.nextDestination.isEmpty() ? null : findStation(byName, train.nextDestination);
            }
            TrainMapData.StationEntry origin = byId.get(train.activeSegmentFromId);
            if (origin == null) {
                origin = byId.get(train.previousStationId);
            }
            if (origin == null) {
                origin = findPreviousStop(train, byName, byId);
            }
            if (origin == null || destination == null) {
                continue;
            }
            final UUID a = layout.effectiveMap.getOrDefault(origin.id, origin.id);
            final UUID b = layout.effectiveMap.getOrDefault(destination.id, destination.id);
            if (!a.equals(b)) {
                final String key = edgeKey(a, b);
                counts.put(key, counts.getOrDefault(key, 0) + 1);
            }
        }
        return counts;
    }
    
    private static int[] resolveTrainPosition(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, CachedTrainLayout layout) {
        TrainMapData.StationEntry station = byId.get(train.currentStationId);
        if (station == null && !train.currentStationName.isEmpty()) {
            station = findStation(byName, train.currentStationName);
        }
        if (station != null) {
            final int[] pos = layout.positions.get(layout.effectiveMap.getOrDefault(station.id, station.id));
            if (pos != null) {
                return new int[] { pos[0], pos[1] - 4 };
            }
        }
        TrainMapData.StationEntry destination = byId.get(train.activeSegmentToId);
        if (destination == null) {
            destination = byId.get(train.destinationStationId);
        }
        if (destination == null) {
            destination = train.nextDestination.isEmpty() ? null : findStation(byName, train.nextDestination);
        }
        TrainMapData.StationEntry origin = byId.get(train.activeSegmentFromId);
        if (origin == null) {
            origin = byId.get(train.previousStationId);
        }
        if (origin == null) {
            origin = findPreviousStop(train, byName, byId);
        }
        if (origin != null && destination != null) {
            final UUID a = layout.effectiveMap.getOrDefault(origin.id, origin.id);
            final UUID b = layout.effectiveMap.getOrDefault(destination.id, destination.id);
            final int[] pa = layout.positions.get(a);
            final int[] pb = layout.positions.get(b);
            if (pa != null && pb != null) {
                if (a.equals(b)) {
                    return new int[] { pa[0], pa[1] - 4 };
                }
                final double dx = destination.x - origin.x;
                final double dz = destination.z - origin.z;
                final double total = Math.sqrt(dx * dx + dz * dz);
                double progress;
                if (total < 1.0) {
                    progress = 0.5;
                }
                else if (train.activeSegmentFromId != null && train.activeSegmentToId != null) {
                    progress = train.segmentProgress;
                }
                else {
                    final double tx = train.x - origin.x;
                    final double tz = train.z - origin.z;
                    progress = (tx * dx + tz * dz) / (total * total);
                    progress = Math.max(0.0, Math.min(1.0, progress));
                }
                return new int[] { (int)(pa[0] + (pb[0] - pa[0]) * progress), (int)(pa[1] + (pb[1] - pa[1]) * progress) };
            }
        }
        if (!train.positionValid) {
            return null;
        }
        double best = Double.MAX_VALUE;
        int[] nearest = null;
        for (TrainMapData.StationEntry station2 : byName.values()) {
            final int[] pos2 = layout.positions.get(layout.effectiveMap.getOrDefault(station2.id, station2.id));
            if (pos2 == null) {
                continue;
            }
            final double dx2 = train.x - station2.x;
            final double dz2 = train.z - station2.z;
            final double dist = dx2 * dx2 + dz2 * dz2;
            if (dist < best) {
                best = dist;
                nearest = pos2;
            }
        }
        return (nearest != null) ? new int[] { nearest[0], nearest[1] } : null;
    }

    private static int resolveTrainColor(TrainMapData.TrainEntry train, DisplayTheme theme) {
        if (train.waitingForSignal) {
            return -13278;
        }
        if (train.stopped) {
            return ACTIVE_ROUTE;
        }
        if (Math.abs(train.signedSpeed) > 0.01f) {
            return -12255352;
        }
        return mixColors(theme.accent, theme.dim, 0.35f);
    }

    private static float[] resolveTrainDirection(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, CachedTrainLayout layout) {
        TrainMapData.StationEntry from = byId.get(train.activeSegmentFromId);
        if (from == null) {
            from = byId.get(train.previousStationId);
        }
        if (from == null) {
            from = findPreviousStop(train, byName, byId);
        }
        TrainMapData.StationEntry to = byId.get(train.activeSegmentToId);
        if (to == null) {
            to = byId.get(train.destinationStationId);
        }
        if (to == null && !train.nextDestination.isEmpty()) {
            to = findStation(byName, train.nextDestination);
        }
        if (from == null || to == null || from.id.equals(to.id)) {
            return new float[] { 0.0f, 1.0f };
        }
        final int[] pa = layout.positions.get(layout.effectiveMap.getOrDefault(from.id, from.id));
        final int[] pb = layout.positions.get(layout.effectiveMap.getOrDefault(to.id, to.id));
        if (pa == null || pb == null) {
            return new float[] { 0.0f, 1.0f };
        }
        float dx = pb[0] - pa[0];
        float dy = pb[1] - pa[1];
        if (train.signedSpeed < -0.001f) {
            dx = -dx;
            dy = -dy;
        }
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        return len < 0.001f ? new float[] { 0.0f, 1.0f } : new float[] { dx / len, dy / len };
    }
    
    private static TrainMapData.StationEntry findPreviousStop(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId) {
        TrainMapData.StationEntry station = byId.get(train.previousStationId);
        if (station != null) {
            return station;
        }
        station = byId.get(train.activeSegmentFromId);
        if (station != null) {
            return station;
        }
        if (train.scheduleStops.size() < 2 || train.nextDestination.isEmpty()) {
            return null;
        }
        for (int i = 0; i < train.scheduleStops.size(); ++i) {
            if (TrainMapData.matchesStationFilter(train.scheduleStops.get(i), train.nextDestination)) {
                final int previousIndex = (i - 1 + train.scheduleStops.size()) % train.scheduleStops.size();
                return findStation(byName, train.scheduleStops.get(previousIndex));
            }
        }
        return null;
    }
    
    private static void drawWeightedLine(NativeImage image, int x0, int y0, int x1, int y1, int color, int weight) {
        TextureGraphRenderer.drawLine(image, x0, y0, x1, y1, color);
        if (weight > 1) {
            final int offX = (Math.abs(y1 - y0) > Math.abs(x1 - x0)) ? 1 : 0;
            final int offY = (offX == 0) ? 1 : 0;
            TextureGraphRenderer.drawLine(image, x0 + offX, y0 + offY, x1 + offX, y1 + offY, darkenColor(color, 0.9f));
            if (weight > 2) {
                TextureGraphRenderer.drawLine(image, x0 - offX, y0 - offY, x1 - offX, y1 - offY, darkenColor(color, 0.78f));
            }
        }
    }
    
    private static String formatNodeLabel(NodeInfo node, boolean dense) {
        String label = node.group ? cleanGroupLabel(node.groupPattern) + " x" + node.memberCount : node.primaryLabel;
        final int maxLen = node.group ? (dense ? 12 : 16) : (dense ? 10 : 14);
        if (label.length() > maxLen) {
            label = label.substring(0, Math.max(1, maxLen - 2)) + "..";
        }
        return label;
    }
    
    private static String cleanGroupLabel(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return "Group";
        }
        final String cleaned = pattern.replace("*", "").trim();
        return cleaned.isEmpty() ? "Group" : cleaned;
    }
    
    private static boolean intersectsAny(int[] rect, List<int[]> occupied) {
        for (int[] other : occupied) {
            if (rect[0] < other[2] && rect[2] > other[0] && rect[1] < other[3] && rect[3] > other[1]) {
                return true;
            }
        }
        return false;
    }
    
    private static int edgeKeyCompare(UUID a, UUID b) {
        return a.compareTo(b);
    }
    
    private static String edgeKey(UUID a, UUID b) {
        return (edgeKeyCompare(a, b) <= 0) ? (a.toString() + ":" + b.toString()) : (b.toString() + ":" + a.toString());
    }
    
    private static int darkenColor(int color, float factor) {
        final int a = color >> 24 & 0xFF;
        final int r = color >> 16 & 0xFF;
        final int g = color >> 8 & 0xFF;
        final int b = color & 0xFF;
        return a << 24 | (int)(r * factor) << 16 | (int)(g * factor) << 8 | (int)(b * factor);
    }
    
    private static int mixColors(int a, int b, float t) {
        final int aa = a >> 24 & 0xFF;
        final int ar = a >> 16 & 0xFF;
        final int ag = a >> 8 & 0xFF;
        final int ab = a & 0xFF;
        final int ba = b >> 24 & 0xFF;
        final int br = b >> 16 & 0xFF;
        final int bg = b >> 8 & 0xFF;
        final int bb = b & 0xFF;
        final int outA = (int)(aa + (ba - aa) * t);
        final int outR = (int)(ar + (br - ar) * t);
        final int outG = (int)(ag + (bg - ag) * t);
        final int outB = (int)(ab + (bb - ab) * t);
        return outA << 24 | outR << 16 | outG << 8 | outB;
    }
    
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static void fillCircle(NativeImage image, int cx, int cy, int r, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int dy = -r; dy <= r; ++dy) {
            for (int dx = -r; dx <= r; ++dx) {
                if (dx * dx + dy * dy <= r * r) {
                    final int px = cx + dx;
                    final int py = cy + dy;
                    if (px >= 0 && px < imgW && py >= 0 && py < imgH) {
                        image.setPixelRGBA(px, py, abgr);
                    }
                }
            }
        }
    }
    
    private static void setPixelSafe(NativeImage image, int x, int y, int argbColor) {
        if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
            image.setPixelRGBA(x, y, BitmapFont.argbToAbgr(argbColor));
        }
    }
    
    private static final class CachedTrainLayout
    {
        int hash;
        int x;
        int y;
        int w;
        int h;
        int drawX;
        int drawY;
        int drawW;
        int drawH;
        int groupCount;
        int nodeCount;
        Map<UUID, NodeInfo> nodes;
        List<UUID> nodeOrder;
        Map<UUID, int[]> positions;
        Map<UUID, UUID> effectiveMap;
        List<EdgeInfo> edges;
    }
    
    private static final class NodeInfo
    {
        final UUID id;
        final boolean group;
        final String groupPattern;
        final String primaryLabel;
        int memberCount;
        int degree;
        int samples;
        double worldX;
        double worldZ;
        boolean assembling;
        
        NodeInfo(UUID id, String primaryLabel, boolean group, String groupPattern) {
            this.id = id;
            this.primaryLabel = primaryLabel;
            this.group = group;
            this.groupPattern = groupPattern;
        }
        
        void addStation(TrainMapData.StationEntry station) {
            this.worldX += station.x;
            this.worldZ += station.z;
            this.assembling |= station.assembling;
            ++this.memberCount;
            ++this.samples;
        }
    }
    
    private static final class EdgeInfo
    {
        final UUID a;
        final UUID b;
        final String key;
        final int routeIndex;
        int routeCount;
        
        EdgeInfo(UUID a, UUID b, String key, int routeIndex) {
            this.a = a;
            this.b = b;
            this.key = key;
            this.routeIndex = routeIndex;
            this.routeCount = 0;
        }
    }
    
    private static final class GroupResolution
    {
        final Map<UUID, UUID> effectiveMap;
        final Map<String, List<UUID>> groupMembers;
        final Map<UUID, String> stationToGroup;
        
        GroupResolution(Map<UUID, UUID> effectiveMap, Map<String, List<UUID>> groupMembers, Map<UUID, String> stationToGroup) {
            this.effectiveMap = effectiveMap;
            this.groupMembers = groupMembers;
            this.stationToGroup = stationToGroup;
        }
    }
    
    private static final class LabelCandidate
    {
        final String text;
        final int centerX;
        final int baseY;
        final int color;
        final int priority;
        final boolean emphasized;
        
        LabelCandidate(String text, int centerX, int baseY, int color, int priority, boolean emphasized) {
            this.text = text;
            this.centerX = centerX;
            this.baseY = baseY;
            this.color = color;
            this.priority = priority;
            this.emphasized = emphasized;
        }
    }
}
