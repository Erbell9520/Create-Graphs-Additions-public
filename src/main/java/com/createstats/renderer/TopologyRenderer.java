package com.createstats.renderer;

import com.createstats.topology.NodeType;
import com.createstats.topology.TopologyIcons;
import com.createstats.topology.TopologyLayoutMode;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import com.createstats.ModConfiguration;
import java.util.HashMap;
import java.util.Collections;
import javax.annotation.Nullable;
import java.util.Iterator;
import com.createstats.topology.TopologyNode;
import com.createstats.topology.TopologyEdge;
import com.createstats.topology.NetworkGraph;
import com.createstats.topology.TopologyFlowLayout;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.display.DisplayTheme;
import com.mojang.blaze3d.platform.NativeImage;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.topology.CachedTopologyLayout;
import net.minecraft.core.BlockPos;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TopologyRenderer
{
    private static final Map<BlockPos, CachedTopologyLayout> layoutCache;
    private static StatsDisplayPanelBE currentBE;

    private TopologyRenderer() {
    }

    public static void setCurrentBE(StatsDisplayPanelBE be) {
        TopologyRenderer.currentBE = be;
    }

    public static void clearCurrentBE() {
        TopologyRenderer.currentBE = null;
    }

    public static void disposeAll() {
        TopologyRenderer.layoutCache.clear();
    }

    public static void renderTopology(NativeImage image, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings) {
        if (TopologyRenderer.currentBE == null) {
            return;
        }
        final Object graphObj = TopologyRenderer.currentBE.getCachedNetworkGraph();
        NetworkGraph graph = null;
        if (graphObj instanceof NetworkGraph g) {
            if (!g.isEmpty()) {
                graph = g;
            }
        }
        if (graph == null) {
            renderNoNetwork(image, x, y, w, h, theme);
            return;
        }
        final BlockPos panelPos = TopologyRenderer.currentBE.getBlockPos();
        final CachedTopologyLayout cached = TopologyRenderer.layoutCache.get(panelPos);
        final int layoutHash = computeLayoutHash(graph);
        final boolean needsLayout = cached == null || !cached.isValid(layoutHash, graph.getNodes().size(), w, h);
        Map<BlockPos, float[]> positions;
        TopologyFlowLayout.LayoutResult flowLayout;
        if (needsLayout) {
            flowLayout = TopologyFlowLayout.layout(graph, TopologyFlowLayout.configuredIterationCount(graph.getNodes().size()), null, TopologyLayoutMode.ORGANIC);
            positions = scaleToTexture(flowLayout.getPositions(), w, h);
            TopologyRenderer.layoutCache.put(panelPos, new CachedTopologyLayout(positions, graph.getNodes().size(), layoutHash, w, h, 0L, true, flowLayout));
        }
        else {
            positions = cached.getPositions();
            flowLayout = cached.getFlowLayout();
        }
        if (settings.showGrid()) {
            renderBlueprintGrid(image, x, y, w, h, theme);
        }
        if (graph.isOverStressed()) {
            final long flash = System.currentTimeMillis() / 300L;
            final int alertAlpha = (int)(180.0 + 75.0 * Math.sin(flash * 3.141592653589793));
            final int alertColor = alertAlpha << 24 | 0xFF2222;
            TextureGraphRenderer.drawBorder(image, x, y, w, h, 2, alertColor);
            renderOverstressPulse(image, x, y, w, h, theme);
        }
        if (graph.isTruncated()) {
            BitmapFont.drawString(image, "TRUNCATED @" + graph.getNodes().size(), x + 3, y + 3, theme.warn, 1);
        }
        for (TopologyEdge edge : graph.getEdges()) {
            final float[] fromPos = positions.get(edge.getFrom());
            final float[] toPos = positions.get(edge.getTo());
            if (fromPos != null) {
                if (toPos == null) {
                    continue;
                }
                renderEdge(image, x + (int)fromPos[0], y + (int)fromPos[1], x + (int)toPos[0], y + (int)toPos[1], edge, graph, settings, flowLayout);
            }
        }
        final boolean isDense = graph.getNodes().size() > 30;
        for (Map.Entry<BlockPos, TopologyNode> entry : graph.getNodes().entrySet()) {
            final float[] nodePos = positions.get(entry.getKey());
            if (nodePos == null) {
                continue;
            }
            renderNode(image, x + (int)nodePos[0], y + (int)nodePos[1], entry.getKey(), entry.getValue(), graph, theme, settings, isDense, flowLayout);
        }
        if (!graph.getUnloadedBoundary().isEmpty()) {
            renderGhostNode(image, x + w / 2, y + h / 2, theme, 1);
        }
        if (settings.showValueOverlay()) {
            renderNetworkSummary(image, graph, x, y, w, h, theme);
        }
    }

    private static int computeLayoutHash(NetworkGraph graph) {
        int h = graph.getNodes().size() * 31;
        final List<TopologyEdge> edges = new ArrayList<TopologyEdge>(graph.getEdges());
        edges.sort((a, b) -> {
            final long aMin = Math.min(a.getFrom().asLong(), a.getTo().asLong());
            final long bMin = Math.min(b.getFrom().asLong(), b.getTo().asLong());
            if (aMin != bMin) {
                return Long.compare(aMin, bMin);
            }
            final long aMax = Math.max(a.getFrom().asLong(), a.getTo().asLong());
            final long bMax = Math.max(b.getFrom().asLong(), b.getTo().asLong());
            return Long.compare(aMax, bMax);
        });
        h = h * 31 + edges.size();
        for (TopologyEdge edge : edges) {
            final long min = Math.min(edge.getFrom().asLong(), edge.getTo().asLong());
            final long max = Math.max(edge.getFrom().asLong(), edge.getTo().asLong());
            h = h * 31 + (int)(min ^ max);
        }
        final List<TopologyNode> nodes = new ArrayList<TopologyNode>(graph.getNodes().values());
        nodes.sort((a, b) -> Long.compare(a.getPos().asLong(), b.getPos().asLong()));
        for (TopologyNode node : nodes) {
            h = h * 31 + (int)node.getPos().asLong();
            h = h * 31 + node.getType().ordinal();
            h = h * 31 + (node.isSource() ? 1 : 0);
            h = h * 31 + (node.isRootLike() ? 1 : 0);
            h = h * 31 + ((node.getUpstreamPos() != null) ? (int)node.getUpstreamPos().asLong() : 0);
        }
        return h;
    }

    private static Map<BlockPos, float[]> scaleToTexture(Map<BlockPos, float[]> normalized, int width, int height) {
        if (normalized.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<BlockPos, float[]> scaled = new HashMap<BlockPos, float[]>();
        final int padding = 15;
        final float usableW = Math.max(1.0f, width - padding * 2.0f);
        final float usableH = Math.max(1.0f, height - padding * 2.0f);
        for (Map.Entry<BlockPos, float[]> entry : normalized.entrySet()) {
            final float[] pos = entry.getValue();
            scaled.put(entry.getKey(), new float[] { padding + pos[0] * usableW, padding + pos[1] * usableH });
        }
        return scaled;
    }

    private static void renderNoNetwork(NativeImage image, int x, int y, int w, int h, DisplayTheme theme) {
        final int scale = (image.getWidth() >= 512) ? 2 : 1;
        final int bigScale = scale + 1;
        final int lineH = BitmapFont.textHeight(scale) + 2;
        BitmapFont.drawCentered(image, "Network Topology", x, y + h / 3, w, theme.border, bigScale);
        BitmapFont.drawCentered(image, "No kinetic network", x, y + h / 2, w, theme.dim, scale);
        BitmapFont.drawCentered(image, "Link a stats source", x, y + h / 2 + lineH, w, theme.dim, scale);
    }

    private static void renderBlueprintGrid(NativeImage image, int x, int y, int w, int h, DisplayTheme theme) {
        final int step = 32;
        final int gridRgb = theme.grid & 0xFFFFFF;
        final float opacity = 0.09f;
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int gx = x; gx < x + w; gx += step) {
            for (int gy = y; gy < y + h; ++gy) {
                if (gx >= 0 && gx < imgW && gy >= 0 && gy < imgH) {
                    image.setPixelRGBA(gx, gy, blendGridPixel(image.getPixelRGBA(gx, gy), gridRgb, opacity));
                }
            }
        }
        for (int gy2 = y; gy2 < y + h; gy2 += step) {
            for (int gx2 = x; gx2 < x + w; ++gx2) {
                if (gx2 >= 0 && gx2 < imgW && gy2 >= 0 && gy2 < imgH) {
                    image.setPixelRGBA(gx2, gy2, blendGridPixel(image.getPixelRGBA(gx2, gy2), gridRgb, opacity));
                }
            }
        }
    }

    private static int blendGridPixel(int existingAbgr, int overlayRgb, float opacity) {
        final int eR = existingAbgr & 0xFF;
        final int eG = (existingAbgr >> 8) & 0xFF;
        final int eB = (existingAbgr >> 16) & 0xFF;
        final int eA = (existingAbgr >> 24) & 0xFF;
        final int oR = (overlayRgb >> 16) & 0xFF;
        final int oG = (overlayRgb >> 8) & 0xFF;
        final int oB = overlayRgb & 0xFF;
        final int r = (int)(eR + (oR - eR) * opacity);
        final int g = (int)(eG + (oG - eG) * opacity);
        final int b = (int)(eB + (oB - eB) * opacity);
        return (eA << 24) | (b << 16) | (g << 8) | r;
    }

    private static void renderNode(NativeImage image, int cx, int cy, BlockPos pos, TopologyNode node, NetworkGraph graph, DisplayTheme theme, StatsDisplaySettings settings, boolean isDense, @Nullable TopologyFlowLayout.LayoutResult flowLayout) {
        final int scale = (image.getWidth() >= 512) ? 2 : 1;
        final NodeType type = node.getType();
        final int iconSize = type.getIconSize();
        final boolean inferredRoot = node.isInferredRoot() || (!node.isSourceLike() && flowLayout != null && flowLayout.isPrimaryRoot(pos));
        int nodeW;
        int nodeH;
        if (node.isSource()) {
            nodeW = 20 * scale;
            nodeH = 20 * scale;
        }
        else if (inferredRoot) {
            nodeW = 18 * scale;
            nodeH = 18 * scale;
        }
        else if (type.showLabel()) {
            nodeW = 16 * scale;
            nodeH = 16 * scale;
        }
        else {
            nodeW = 12 * scale;
            nodeH = 12 * scale;
        }
        final int nodeX = cx - nodeW / 2;
        final int nodeY = cy - nodeH / 2;
        final float load = (graph.getTotalCapacity() > 0.0f) ? (graph.getTotalStress() / graph.getTotalCapacity()) : 0.0f;
        int statusColor;
        if (node.isOverStressed()) {
            statusColor = -56798;
        }
        else if (node.getSpeed() == 0.0f) {
            statusColor = -10066330;
        }
        else if (type.isObserverLike()) {
            statusColor = -10040065;
        }
        else if (type.isTransferOrgan()) {
            statusColor = (type == NodeType.GEARBOX || type == NodeType.GEARSHIFT || type == NodeType.SPEED_CONTROLLER) ? -4487100 : -7829368;
        }
        else if (type.isMachineLike()) {
            statusColor = -12285697;
        }
        else if (load > 0.8f) {
            statusColor = -30720;
        }
        else if (load > 0.5f) {
            statusColor = -3355648;
        }
        else {
            statusColor = -16724890;
        }
        if (Math.abs(node.getSpeed()) > 0.01f) {
            final long time = System.currentTimeMillis();
            final double pulseSpeed = 200.0 + 200.0 / (1.0 + Math.abs(node.getSpeed()) / 64.0);
            final float pulse = (float)(0.5 + 0.5 * Math.sin(time / pulseSpeed * 3.141592653589793));
            final int glowAlpha = (int)(40.0f + 60.0f * pulse);
            final int glowColor = glowAlpha << 24 | (statusColor & 0xFFFFFF);
            TextureGraphRenderer.fillRect(image, nodeX - 2, nodeY - 2, nodeW + 4, nodeH + 4, glowColor);
        }
        TextureGraphRenderer.fillRect(image, nodeX, nodeY, nodeW, nodeH, theme.graphBg);
        if (Math.abs(node.getSpeed()) <= 0.01f && !node.isSource() && !node.isOverStressed()) {
            fillRectBlended(image, nodeX + 1, nodeY + 1, Math.max(1, nodeW - 2), Math.max(1, nodeH - 2), 0x33000000);
        }
        final int borderColor = node.isSource() ? -12276993 : (inferredRoot ? theme.warn : statusColor);
        TextureGraphRenderer.drawBorder(image, nodeX, nodeY, nodeW, nodeH, 1, borderColor);
        if (node.isSource()) {
            TextureGraphRenderer.fillRect(image, cx - scale, nodeY - 4 * scale, 2 * scale + 1, 3 * scale, theme.accent);
        }
        else if (inferredRoot) {
            TextureGraphRenderer.fillRect(image, cx - scale, nodeY - 4 * scale, 2 * scale + 1, 3 * scale, theme.warn);
        }
        final byte[] icon = TopologyIcons.getIcon(type);
        final int iconScale = scale;
        final int iconX = cx - iconSize * iconScale / 2;
        final int iconY = cy - iconSize * iconScale / 2;
        TopologyIcons.drawIcon(image, icon, iconSize, iconX, iconY, theme.text, iconScale);
        final boolean showLabel = !isDense || type.showLabel();
        if (showLabel && settings.topologyShowLabels()) {
            final int labelColor = node.isSource() ? theme.accent : (inferredRoot ? theme.warn : theme.text);
            final String label = truncateName(node.getName(), nodeW + 20, 1);
            BitmapFont.drawCentered(image, label, nodeX - 10, nodeY + nodeH + 2, nodeW + 20, labelColor, 1);
        }
    }

    private static void renderEdge(NativeImage image, int x0, int y0, int x1, int y1, TopologyEdge edge, NetworkGraph graph, StatsDisplaySettings settings, @Nullable TopologyFlowLayout.LayoutResult flowLayout) {
        int edgeColor;
        if (settings.topologyColorByStress()) {
            final float load = (graph.getTotalCapacity() > 0.0f) ? (graph.getTotalStress() / graph.getTotalCapacity()) : 0.0f;
            if (load < 0.5f) {
                edgeColor = WorldGraphRenderer.lerpColor(-16724890, -3355648, load * 2.0f);
            }
            else if (load < 0.8f) {
                edgeColor = WorldGraphRenderer.lerpColor(-3355648, -30720, (load - 0.5f) * 3.33f);
            }
            else {
                edgeColor = WorldGraphRenderer.lerpColor(-30720, -56798, (load - 0.8f) * 5.0f);
            }
        }
        else {
            edgeColor = settings.getCustomAccentColor();
        }
        if (graph.isOverStressed()) {
            final long flash = System.currentTimeMillis() / 300L;
            final int alpha = (flash % 2L == 0L) ? 255 : 136;
            edgeColor = (alpha << 24 | 0xFF2222);
        }
        final float stressFrac = (graph.getTotalCapacity() > 0.0f) ? (edge.getStressTransfer() / graph.getTotalCapacity()) : 0.1f;
        final boolean primary = flowLayout == null || flowLayout.isPrimaryEdge(edge);
        final int thickness = primary ? ((stressFrac < 0.1f) ? 2 : ((stressFrac < 0.4f) ? 3 : 4)) : 1;
        if (settings.topologyAnimateEdges() && primary) {
            final long time = System.currentTimeMillis();
            final float speed = Math.max(0.01f, Math.abs(edge.getSpeedRatio()));
            final float scrollRate = speed * 0.05f;
            float animOffset = time * scrollRate % 9.0f;
            if (edge.getSpeedRatio() < 0.0f) {
                animOffset = -animOffset;
            }
            drawDashedLine(image, x0, y0, x1, y1, edgeColor, thickness, 6, 3, animOffset);
            drawFlowBeads(image, x0, y0, x1, y1, edge, edgeColor, thickness, time);
        }
        else {
            final int lineColor = primary ? edgeColor : dimColorOpaque(edgeColor, 0.45f);
            for (int t = -(thickness / 2); t <= thickness / 2; ++t) {
                TextureGraphRenderer.drawLine(image, x0 + t, y0, x1 + t, y1, lineColor);
            }
        }
        if (Math.abs(Math.abs(edge.getSpeedRatio()) - 1.0f) > 0.01f) {
            final int midX = (x0 + x1) / 2;
            final int midY = (y0 + y1) / 2;
            drawArrowhead(image, midX, midY, (float)(x1 - x0), (float)(y1 - y0), edgeColor, 3);
        }
    }

    private static void drawFlowBeads(NativeImage image, int x0, int y0, int x1, int y1, TopologyEdge edge, int argbColor, int thickness, long time) {
        final float speed = Math.abs(edge.getSpeedRatio());
        if (speed <= 0.01f) {
            return;
        }
        final float dx = (float)(x1 - x0);
        final float dy = (float)(y1 - y0);
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 6.0f) {
            return;
        }
        final int beadCount = Math.max(2, Math.min(12, 2 + (int)(speed * 8.0f)));
        final int beadSize = 1;
        final int beadColor = 0xFF000000 | (argbColor & 0x00FFFFFF);
        final double rate = Math.max(0.00008, speed * 0.00018);
        for (int i = 0; i < beadCount; ++i) {
            float t = (float)((time * rate + i / (double)beadCount) % 1.0);
            if (edge.getSpeedRatio() < 0.0f) {
                t = 1.0f - t;
            }
            final int px = Math.round(x0 + dx * t) - beadSize / 2;
            final int py = Math.round(y0 + dy * t) - beadSize / 2;
            TextureGraphRenderer.fillRect(image, px, py, beadSize, beadSize, beadColor);
        }
    }

    private static void renderOverstressPulse(NativeImage image, int x, int y, int w, int h, DisplayTheme theme) {
        final long time = System.currentTimeMillis();
        final float pulse = (float)(0.5 + 0.5 * Math.sin(time * 0.010471975511965976));
        final int alpha = (int)(28.0f + pulse * 44.0f);
        final int color = (alpha << 24) | 0xFF2222;
        drawBorderBlended(image, x + 3, y + 3, Math.max(1, w - 6), Math.max(1, h - 6), 2, color);
        final int bandAlpha = (int)(18.0f + pulse * 22.0f);
        fillRectBlended(image, x + 2, y + Math.max(2, h / 2 - 1), Math.max(1, w - 4), 2, (bandAlpha << 24) | (theme.warn & 0x00FFFFFF));
    }

    private static void drawBorderBlended(NativeImage image, int x, int y, int w, int h, int thickness, int argbColor) {
        fillRectBlended(image, x, y, w, thickness, argbColor);
        fillRectBlended(image, x, y + h - thickness, w, thickness, argbColor);
        fillRectBlended(image, x, y, thickness, h, argbColor);
        fillRectBlended(image, x + w - thickness, y, thickness, h, argbColor);
    }

    private static void fillRectBlended(NativeImage image, int x, int y, int w, int h, int argbColor) {
        final int xEnd = Math.min(x + w, image.getWidth());
        final int yEnd = Math.min(y + h, image.getHeight());
        for (int py = Math.max(0, y); py < yEnd; ++py) {
            for (int px = Math.max(0, x); px < xEnd; ++px) {
                TextureGraphRenderer.setPixelBlended(image, px, py, argbColor);
            }
        }
    }

    private static void renderNetworkSummary(NativeImage image, NetworkGraph graph, int x, int y, int w, int h, DisplayTheme theme) {
        final int scale = 1;
        final int lineH = 9 * scale;
        final boolean hasCapacity = graph.hasMeasuredCapacity();
        final float load = hasCapacity ? (graph.getTotalStress() / graph.getTotalCapacity() * 100.0f) : 0.0f;
        String[] lines;
        if (graph.isOverStressed()) {
            lines = new String[] { "Nodes:" + graph.getNodes().size(), "OVERSTRESSED", "Load:>100%" };
        }
        else if (!hasCapacity) {
            lines = new String[] { "Nodes:" + graph.getNodes().size(), "SU:" + formatCompact(graph.getTotalStress()) + "/?", graph.hasExplicitSource() ? "Cap:unknown" : "Root:inferred" };
        }
        else {
            lines = new String[] { "Nodes:" + graph.getNodes().size(), "SU:" + formatCompact(graph.getTotalStress()) + "/" + formatCompact(graph.getTotalCapacity()), "Load:" + String.format("%.0f%%", load) };
        }
        final int summaryColor = graph.isOverStressed() ? -56798 : theme.dim;
        for (int i = 0; i < lines.length; ++i) {
            BitmapFont.drawRight(image, lines[i], x + w - 2, y + 2 + i * lineH, summaryColor, scale);
        }
    }

    private static void renderGhostNode(NativeImage image, int cx, int cy, DisplayTheme theme, int scale) {
        final int radius = 6 * scale;
        final int color = 0x88000000 | (theme.dim & 0xFFFFFF);
        drawCircleOutline(image, cx, cy, radius, color);
        BitmapFont.drawCentered(image, "?", cx - 3 * scale, cy - 3 * scale, 6 * scale, color, scale);
    }

    private static void drawDashedLine(NativeImage image, int x0, int y0, int x1, int y1, int argbColor, int thickness, int dashLen, int gapLen, float animOffset) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final float dx = (float)(x1 - x0);
        final float dy = (float)(y1 - y0);
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0f) {
            return;
        }
        final float nx = -dy / len;
        final float ny = dx / len;
        final int steps = (int)Math.ceil(len);
        final float patternLen = (float)(dashLen + gapLen);
        for (int s = 0; s <= steps; ++s) {
            final float tFrac = s / len;
            final float patternPos = ((s + animOffset) % patternLen + patternLen) % patternLen;
            if (patternPos < dashLen) {
                final float cx = x0 + dx * tFrac;
                final float cy = y0 + dy * tFrac;
                for (int th = -(thickness / 2); th <= thickness / 2; ++th) {
                    final int px = (int)(cx + nx * th);
                    final int py = (int)(cy + ny * th);
                    if (px >= 0 && px < imgW && py >= 0 && py < imgH) {
                        image.setPixelRGBA(px, py, abgr);
                    }
                }
            }
        }
    }

    private static void drawArrowhead(NativeImage image, int x, int y, float dx, float dy, int argbColor, int size) {
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) {
            return;
        }
        dx /= len;
        dy /= len;
        final float wingX = -dy;
        final float wingY = dx;
        final int tipX = x;
        final int tipY = y;
        final int w1x = (int)(x - dx * size + wingX * size / 2.0f);
        final int w1y = (int)(y - dy * size + wingY * size / 2.0f);
        final int w2x = (int)(x - dx * size - wingX * size / 2.0f);
        final int w2y = (int)(y - dy * size - wingY * size / 2.0f);
        fillTriangle(image, tipX, tipY, w1x, w1y, w2x, w2y, argbColor);
    }

    private static int dimColorOpaque(int argbColor, float factor) {
        final int alpha = argbColor >> 24 & 0xFF;
        final int r = Math.max(0, Math.min(255, (int)((argbColor >> 16 & 0xFF) * factor)));
        final int g = Math.max(0, Math.min(255, (int)((argbColor >> 8 & 0xFF) * factor)));
        final int b = Math.max(0, Math.min(255, (int)((argbColor & 0xFF) * factor)));
        return alpha << 24 | r << 16 | g << 8 | b;
    }

    private static void fillTriangle(NativeImage image, int x0, int y0, int x1, int y1, int x2, int y2, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int minX = Math.max(0, Math.min(x0, Math.min(x1, x2)));
        final int maxX = Math.min(imgW - 1, Math.max(x0, Math.max(x1, x2)));
        final int minY = Math.max(0, Math.min(y0, Math.min(y1, y2)));
        for (int maxY = Math.min(imgH - 1, Math.max(y0, Math.max(y1, y2))), py = minY; py <= maxY; ++py) {
            for (int px = minX; px <= maxX; ++px) {
                if (pointInTriangle(px, py, x0, y0, x1, y1, x2, y2)) {
                    image.setPixelRGBA(px, py, abgr);
                }
            }
        }
    }

    private static boolean pointInTriangle(int px, int py, int x0, int y0, int x1, int y1, int x2, int y2) {
        final int d1 = (px - x1) * (y0 - y1) - (x0 - x1) * (py - y1);
        final int d2 = (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
        final int d3 = (px - x0) * (y2 - y0) - (x2 - x0) * (py - y0);
        final boolean hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
        final boolean hasPos = d1 > 0 || d2 > 0 || d3 > 0;
        return !hasNeg || !hasPos;
    }

    private static void drawCircleOutline(NativeImage image, int cx, int cy, int radius, int argbColor) {
        final int abgr = BitmapFont.argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        int x = radius;
        int y = 0;
        int err = 1 - radius;
        while (x >= y) {
            setPixelSafe(image, cx + x, cy + y, abgr, imgW, imgH);
            setPixelSafe(image, cx + y, cy + x, abgr, imgW, imgH);
            setPixelSafe(image, cx - y, cy + x, abgr, imgW, imgH);
            setPixelSafe(image, cx - x, cy + y, abgr, imgW, imgH);
            setPixelSafe(image, cx - x, cy - y, abgr, imgW, imgH);
            setPixelSafe(image, cx - y, cy - x, abgr, imgW, imgH);
            setPixelSafe(image, cx + y, cy - x, abgr, imgW, imgH);
            setPixelSafe(image, cx + x, cy - y, abgr, imgW, imgH);
            ++y;
            if (err < 0) {
                err += 2 * y + 1;
            }
            else {
                --x;
                err += 2 * (y - x) + 1;
            }
        }
    }

    private static void setPixelSafe(NativeImage image, int x, int y, int abgr, int imgW, int imgH) {
        if (x >= 0 && x < imgW && y >= 0 && y < imgH) {
            image.setPixelRGBA(x, y, abgr);
        }
    }

    private static String truncateName(String text, int maxWidth, int scale) {
        if (BitmapFont.measureString(text, scale) <= maxWidth) {
            return text;
        }
        String t = null;
        for (t = text; t.length() > 3 && BitmapFont.measureString(t + "..", scale) > maxWidth; t = t.substring(0, t.length() - 1)) {}
        return t + "..";
    }

    private static String formatCompact(float val) {
        if (val >= 1000000.0f) {
            return String.format("%.1fM", val / 1000000.0f);
        }
        if (val >= 1000.0f) {
            return String.format("%.1fK", val / 1000.0f);
        }
        return String.format("%.0f", val);
    }

    static {
        layoutCache = new HashMap<BlockPos, CachedTopologyLayout>();
    }
}
