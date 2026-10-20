package com.createstats.client;

import com.createstats.topology.NodeType;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import com.createstats.ModConfiguration;
import com.createstats.topology.TopologyIcons;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Matrix4f;
import com.createstats.topology.TopologyEdge;
import com.createstats.topology.TopologyLayoutMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import java.util.function.Supplier;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Iterator;
import com.createstats.topology.TopologyNode;
import com.createstats.topology.NetworkGraph;
import com.createstats.topology.TopologyFlowLayout;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.display.DisplayTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import java.util.Map;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class TopologyPopupScreen extends Screen
{
    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    private Map<BlockPos, float[]> nodePositions;
    private Map<BlockPos, float[]> targetNodePositions;
    private int lastGraphHash;
    private TopologyFlowLayout.LayoutResult flowLayout;
    private long lastAnimationMs;
    private TopologyLayoutMode layoutMode;
    private float panX;
    private float panY;
    private float zoom;
    private boolean panning;
    private double panStartX;
    private double panStartY;
    private float panStartPanX;
    private float panStartPanY;
    private BlockPos hoveredNode;
    private BlockPos draggedNode;
    private boolean nodeDragging;
    private int BG;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int GRID;
    private int BORDER;
    private int WARN;
    private int modePrevX;
    private int modePrevY;
    private int modePrevW;
    private int modePrevH;
    private int modeLabelX;
    private int modeLabelY;
    private int modeLabelW;
    private int modeLabelH;
    private int modeNextX;
    private int modeNextY;
    private int modeNextW;
    private int modeNextH;

    public TopologyPopupScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Network Topology"));
        this.lastGraphHash = 0;
        this.layoutMode = TopologyLayoutMode.ORGANIC;
        this.panX = 0.0f;
        this.panY = 0.0f;
        this.zoom = 1.0f;
        this.panning = false;
        this.hoveredNode = null;
        this.draggedNode = null;
        this.nodeDragging = false;
        this.panel = panel;
        this.parentScreen = parentScreen;
    }

    protected void init() {
        final int margin = 20;
        this.areaX = margin;
        this.areaY = margin + 14;
        this.areaW = this.width - margin * 2;
        this.areaH = this.height - margin * 2 - 14;
        final DisplayTheme theme = this.panel.getSettings().getDisplayTheme();
        this.BG = theme.bg;
        this.TEXT = theme.text;
        this.DIM = theme.dim;
        this.ACCENT = theme.accent;
        this.GRID = theme.grid;
        this.BORDER = theme.border;
        this.WARN = theme.warn;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, -301331942);
        g.drawCenteredString(this.font, "§lKinetic Network Topology", this.width / 2, 6, this.ACCENT);
        g.fill(this.areaX, this.areaY, this.areaX + this.areaW, this.areaY + this.areaH, -15921888);
        this.drawBorder(g, this.areaX, this.areaY, this.areaW, this.areaH, this.BORDER);
        final Object graphObj = this.panel.getCachedNetworkGraph();
        if (graphObj instanceof NetworkGraph graph) {
            if (!graph.isEmpty()) {
                this.renderModeSelector(g, mouseX, mouseY);
                this.computeLayout(graph);
                this.animateLayout();
                final int baseGrid = 40;
                final int gridStep = Math.max(10, (int)(baseGrid * this.zoom));
                final int offsetX = (int)(this.panX * this.zoom) % gridStep;
                final int offsetY = (int)(this.panY * this.zoom) % gridStep;
                for (int gx = this.areaX + offsetX; gx < this.areaX + this.areaW; gx += gridStep) {
                    g.fill(gx, this.areaY, gx + 1, this.areaY + this.areaH, 285212671);
                }
                for (int gy = this.areaY + offsetY; gy < this.areaY + this.areaH; gy += gridStep) {
                    g.fill(this.areaX, gy, this.areaX + this.areaW, gy + 1, 285212671);
                }
                g.enableScissor(this.areaX, this.areaY, this.areaX + this.areaW, this.areaY + this.areaH);
                final int cx = this.areaX + this.areaW / 2;
                final int cy = this.areaY + this.areaH / 2;
                this.drawEdges(g, graph, cx, cy);
                this.hoveredNode = null;
                for (Map.Entry<BlockPos, TopologyNode> entry : graph.getNodes().entrySet()) {
                    final BlockPos pos = entry.getKey();
                    final TopologyNode node = entry.getValue();
                    final float[] nPos = this.nodePositions.get(pos);
                    if (nPos == null) {
                        continue;
                    }
                    final int nx = cx + (int)((nPos[0] + this.panX) * this.zoom);
                    final int ny = cy + (int)((nPos[1] + this.panY) * this.zoom);
                    final double dist = Math.sqrt((mouseX - nx) * (mouseX - nx) + (mouseY - ny) * (mouseY - ny));
                    if (dist < 12.0) {
                        this.hoveredNode = pos;
                    }
                    final int nodeColor = this.getNodeColor(node);
                    final int radius = Math.max(3, (int)(this.getNodeRadius(node) * this.zoom));
                    final boolean hovered = pos.equals(this.hoveredNode);
                    final boolean isDragged = pos.equals(this.draggedNode);
                    final boolean inferredRoot = node.isInferredRoot() || (!node.isSourceLike() && this.flowLayout != null && this.flowLayout.isPrimaryRoot(pos));
                    if (hovered || isDragged) {
                        final int glowColor = (nodeColor & 0xFFFFFF) | 0x50000000;
                        g.fill(nx - radius - 4, ny - radius - 4, nx + radius + 4, ny + radius + 4, glowColor);
                    }
                    g.fill(nx - radius, ny - radius, nx + radius, ny + radius, nodeColor);
                    this.renderNodeIcon(g, node, nx, ny, radius);
                    final int borderColor = isDragged ? -188 : (hovered ? -1 : (node.isSource() ? this.ACCENT : (inferredRoot ? this.WARN : this.darken(nodeColor, 0.6f))));
                    this.drawBorder(g, nx - radius, ny - radius, radius * 2, radius * 2, borderColor);
                    if (node.isSource()) {
                        g.fill(nx - 2, ny - radius - 5, nx + 2, ny - radius - 1, -13312);
                    }
                    else if (inferredRoot) {
                        g.fill(nx - 2, ny - radius - 5, nx + 2, ny - radius - 1, this.WARN);
                    }
                    if (node.isOverStressed()) {
                        this.drawBorder(g, nx - radius - 1, ny - radius - 1, radius * 2 + 2, radius * 2 + 2, -48060);
                    }
                    final boolean showLabel = this.shouldShowPopupLabel(node, hovered, inferredRoot);
                    if (!showLabel) {
                        continue;
                    }
                    String label = this.compactLabel(node.getName(), hovered ? 18 : 12);
                    final int labelColor = node.isSource() ? this.ACCENT : (inferredRoot ? this.WARN : this.DIM);
                    g.drawCenteredString(this.font, label, nx, ny + radius + 2, labelColor);
                    if (this.zoom <= 1.1f || !hovered) {
                        continue;
                    }
                    g.drawCenteredString(this.font, this.formatSpeed(node.getSpeed()), nx, ny + radius + 12, -10066296);
                }
                g.disableScissor();
                if (graph.isOverStressed()) {
                    this.renderOverstressPulse(g);
                }
                this.renderSummaryBar(g, graph);
                this.renderTruncationWarning(g, graph);
                this.renderStalenessIndicator(g, graph);
                if (this.hoveredNode != null) {
                    final TopologyNode node2 = graph.getNodes().get(this.hoveredNode);
                    if (node2 != null) {
                        this.renderNodeTooltip(g, node2, mouseX, mouseY);
                    }
                }
                final String hint = this.nodeDragging ? "§eDragging node...  |  Release to drop" : "§7Scroll: Zoom  |  Drag: Pan  |  Click node: Drag  |  Top-left: Change layout  |  ESC: Close";
                g.drawString(this.font, hint, this.areaX + 4, this.areaY + this.areaH - 12, -11184777);
                this.renderLegend(g);
                return;
            }
        }
        g.drawCenteredString(this.font, "No kinetic network detected", this.areaX + this.areaW / 2, this.areaY + this.areaH / 2 - 16, this.DIM);
        g.drawCenteredString(this.font, "Link a Stressometer/Speedometer with Stats Link", this.areaX + this.areaW / 2, this.areaY + this.areaH / 2, -12303258);
        g.drawCenteredString(this.font, "Press ESC to go back", this.areaX + this.areaW / 2, this.areaY + this.areaH / 2 + 16, -12303258);
    }

    private void renderModeSelector(GuiGraphics g, int mouseX, int mouseY) {
        final String label = this.layoutMode.getLabel();
        this.modePrevX = this.areaX + 6;
        this.modePrevY = this.areaY + 6;
        this.modePrevW = 14;
        this.modePrevH = 16;
        this.modeLabelX = this.modePrevX + this.modePrevW + 2;
        this.modeLabelY = this.modePrevY;
        this.modeLabelW = Math.max(68, this.font.width(label) + 16);
        this.modeLabelH = 16;
        this.modeNextX = this.modeLabelX + this.modeLabelW + 2;
        this.modeNextY = this.modePrevY;
        this.modeNextW = 14;
        this.modeNextH = 16;
        g.drawString(this.font, "View", this.modePrevX, this.modePrevY - 9, this.DIM);
        this.drawModeButton(g, this.modePrevX, this.modePrevY, this.modePrevW, this.modePrevH, mouseX, mouseY, "<");
        final boolean labelHovered = mouseX >= this.modeLabelX && mouseX < this.modeLabelX + this.modeLabelW && mouseY >= this.modeLabelY && mouseY < this.modeLabelY + this.modeLabelH;
        g.fill(this.modeLabelX, this.modeLabelY, this.modeLabelX + this.modeLabelW, this.modeLabelY + this.modeLabelH, labelHovered ? -13745534 : -14531550);
        this.drawBorder(g, this.modeLabelX, this.modeLabelY, this.modeLabelW, this.modeLabelH, -12040120);
        g.drawCenteredString(this.font, label, this.modeLabelX + this.modeLabelW / 2, this.modeLabelY + 4, -1);
        this.drawModeButton(g, this.modeNextX, this.modeNextY, this.modeNextW, this.modeNextH, mouseX, mouseY, ">");
    }

    private void drawModeButton(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, String text) {
        final boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hovered ? -13745534 : -14531550);
        this.drawBorder(g, x, y, w, h, -12040120);
        g.drawCenteredString(this.font, text, x + w / 2, y + 4, -1);
    }

    private void drawEdges(GuiGraphics g, NetworkGraph graph, int cx, int cy) {
        final Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader((Supplier)GameRenderer::getPositionColorShader);
        final Tesselator tess = Tesselator.getInstance();
        final BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (TopologyEdge edge : graph.getEdges()) {
            final float[] fromPos = this.nodePositions.get(edge.getFrom());
            final float[] toPos = this.nodePositions.get(edge.getTo());
            if (fromPos != null) {
                if (toPos == null) {
                    continue;
                }
                final float x1 = cx + (fromPos[0] + this.panX) * this.zoom;
                final float y1 = cy + (fromPos[1] + this.panY) * this.zoom;
                final float x2 = cx + (toPos[0] + this.panX) * this.zoom;
                final float y2 = cy + (toPos[1] + this.panY) * this.zoom;
                final boolean primary = this.flowLayout == null || this.flowLayout.isPrimaryEdge(edge);
                final int edgeColor = primary ? ((Math.abs(edge.getSpeedRatio()) != 1.0f) ? -7838174 : -13417370) : 1722463098;
                final float r = (edgeColor >> 16 & 0xFF) / 255.0f;
                final float green = (edgeColor >> 8 & 0xFF) / 255.0f;
                final float b = (edgeColor & 0xFF) / 255.0f;
                final float a = (edgeColor >> 24 & 0xFF) / 255.0f;
                buf.addVertex(pose, x1, y1, 0.0f).setColor(r, green, b, a);
                buf.addVertex(pose, x2, y2, 0.0f).setColor(r, green, b, a);
            }
        }
        MeshData meshData = buf.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }
        RenderSystem.disableBlend();
        for (TopologyEdge edge : graph.getEdges()) {
            final boolean primary = this.flowLayout == null || this.flowLayout.isPrimaryEdge(edge);
            if (!primary || this.zoom < 0.28f) {
                continue;
            }
            final float[] fromPos = this.nodePositions.get(edge.getFrom());
            final float[] toPos = this.nodePositions.get(edge.getTo());
            if (fromPos == null || toPos == null) {
                continue;
            }
            final int x1 = cx + (int)((fromPos[0] + this.panX) * this.zoom);
            final int y1 = cy + (int)((fromPos[1] + this.panY) * this.zoom);
            final int x2 = cx + (int)((toPos[0] + this.panX) * this.zoom);
            final int y2 = cy + (int)((toPos[1] + this.panY) * this.zoom);
            this.drawFlowBeads(g, x1, y1, x2, y2, edge);
        }
        for (TopologyEdge edge : graph.getEdges()) {
            if (Math.abs(edge.getSpeedRatio()) != 1.0f && (this.flowLayout == null || this.flowLayout.isPrimaryEdge(edge))) {
                if (this.zoom <= 0.6f) {
                    continue;
                }
                final float[] fromPos = this.nodePositions.get(edge.getFrom());
                final float[] toPos = this.nodePositions.get(edge.getTo());
                if (fromPos == null) {
                    continue;
                }
                if (toPos == null) {
                    continue;
                }
                final int mx = cx + (int)(((fromPos[0] + toPos[0]) / 2.0f + this.panX) * this.zoom);
                final int my = cy + (int)(((fromPos[1] + toPos[1]) / 2.0f + this.panY) * this.zoom);
                g.drawCenteredString(this.font, this.formatRatio(edge.getSpeedRatio()), mx, my - 4, -5601212);
            }
        }
    }

    private void drawFlowBeads(GuiGraphics g, int x1, int y1, int x2, int y2, TopologyEdge edge) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 28.0f) {
            return;
        }
        final long tick = (this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : System.currentTimeMillis() / 50L;
        final float direction = edge.getSpeedRatio() < 0.0f ? -1.0f : 1.0f;
        final float phase = ((tick % 56L) / 56.0f) * direction;
        final int count = Math.max(2, Math.min(8, (int)(len / 55.0f) + 2));
        final int size = Math.max(1, Math.min(2, (int)Math.ceil(Math.sqrt(this.zoom))));
        final int color = (Math.abs(edge.getSpeedRatio()) != 1.0f) ? -7811841 : -12255352;
        for (int i = 0; i < count; ++i) {
            float t = (phase + i / (float)count) % 1.0f;
            if (t < 0.0f) {
                t += 1.0f;
            }
            final int px = Math.round(x1 + dx * t);
            final int py = Math.round(y1 + dy * t);
            g.fill(px - size, py - size, px + size + 1, py + size + 1, (color & 0xFFFFFF) | 0x70000000);
            g.fill(px, py, px + 1, py + 1, (color & 0xFFFFFF) | 0xDD000000);
        }
    }

    private void renderOverstressPulse(GuiGraphics g) {
        final long tick = (this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : System.currentTimeMillis() / 50L;
        final float pulse = (float)(0.5 + 0.5 * Math.sin(tick * 0.3141592653589793));
        final int alpha = 34 + (int)(pulse * 42.0f);
        final int color = (alpha << 24) | 0xFF4433;
        g.fill(this.areaX + 1, this.areaY + 1, this.areaX + this.areaW - 1, this.areaY + 3, color);
        g.fill(this.areaX + 1, this.areaY + this.areaH - 3, this.areaX + this.areaW - 1, this.areaY + this.areaH - 1, color);
        g.fill(this.areaX + 1, this.areaY + 1, this.areaX + 3, this.areaY + this.areaH - 1, color);
        g.fill(this.areaX + this.areaW - 3, this.areaY + 1, this.areaX + this.areaW - 1, this.areaY + this.areaH - 1, color);
    }

    private void renderNodeIcon(GuiGraphics g, TopologyNode node, int nx, int ny, int nodeRadius) {
        final byte[] icon = TopologyIcons.getIcon(node.getType());
        final int iconSize = node.getType().getIconSize();
        final int pixelScale = Math.max(1, (nodeRadius * 2 - 2) / iconSize);
        final int totalSize = iconSize * pixelScale;
        final int startX = nx - totalSize / 2;
        final int startY = ny - totalSize / 2;
        final int iconColor = node.isOverStressed() ? -21846 : (node.isSource() ? -16768512 : (node.isInferredRoot() ? this.WARN : -2236929));
        final int mask = (1 << iconSize) - 1;
        for (int row = 0; row < iconSize; ++row) {
            final int rowBits = icon[row] & mask;
            for (int col = 0; col < iconSize; ++col) {
                if ((rowBits & 1 << iconSize - 1 - col) != 0x0) {
                    final int px = startX + col * pixelScale;
                    final int py = startY + row * pixelScale;
                    g.fill(px, py, px + pixelScale, py + pixelScale, iconColor);
                }
            }
        }
    }

    private void renderTruncationWarning(GuiGraphics g, NetworkGraph graph) {
        if (graph.isTruncated()) {
            final String warn = "\u26a0 Network truncated at " + graph.getNodes().size() + " nodes, raise Max Nodes for full topology";
            final int tw = this.font.width(warn);
            final int wx = this.areaX + this.areaW / 2 - tw / 2 - 4;
            final int wy = this.areaY + 2;
            g.fill(wx, wy, wx + tw + 8, wy + 12, -582737408);
            g.drawCenteredString(this.font, warn, this.areaX + this.areaW / 2, wy + 2, -21948);
        }
    }

    private void renderStalenessIndicator(GuiGraphics g, NetworkGraph graph) {
        final long discoveredTick = graph.getDiscoveredAtTick();
        long currentTick = 0L;
        if (this.panel.getLevel() != null) {
            currentTick = this.panel.getLevel().getGameTime();
        }
        final long ageTicks = currentTick - discoveredTick;
        final float ageSecs = ageTicks / 20.0f;
        String ageText;
        if (ageSecs < 10.0f) {
            ageText = "§aLive";
        }
        else if (ageSecs < 60.0f) {
            ageText = String.format("§e%.0fs ago", ageSecs);
        }
        else {
            ageText = String.format("§c%.0fm ago", ageSecs / 60.0f);
        }
        final String indicator = "Data: " + ageText;
        final int iw = this.font.width(indicator) + 4;
        g.drawString(this.font, indicator, this.areaX + this.areaW - iw, this.areaY - 12, -7829351);
    }

    private void computeLayout(NetworkGraph graph) {
        final int hash = this.computeLayoutHash(graph);
        final boolean graphChanged = hash != this.lastGraphHash || this.targetNodePositions == null || !this.targetNodePositions.keySet().equals(graph.getNodes().keySet());
        if (!graphChanged) {
            return;
        }
        this.lastGraphHash = hash;
        this.flowLayout = TopologyFlowLayout.layout(graph, TopologyFlowLayout.configuredIterationCount(graph.getNodes().size()), null, this.layoutMode);
        final Map<BlockPos, float[]> scaledTargets = this.scaleFlowLayout(this.flowLayout);
        if (this.nodePositions == null || this.nodePositions.isEmpty()) {
            this.nodePositions = this.copyPositions(scaledTargets);
        }
        else {
            final Map<BlockPos, float[]> current = new HashMap<BlockPos, float[]>();
            for (Map.Entry<BlockPos, float[]> entry : scaledTargets.entrySet()) {
                final float[] existing = this.nodePositions.get(entry.getKey());
                current.put(entry.getKey(), (existing != null) ? new float[] { existing[0], existing[1] } : new float[] { entry.getValue()[0], entry.getValue()[1] });
            }
            this.nodePositions = current;
        }
        this.targetNodePositions = this.copyPositions(scaledTargets);
        this.lastAnimationMs = System.currentTimeMillis();
    }

    private int computeLayoutHash(NetworkGraph graph) {
        int h = graph.getNodes().size() * 31;
        h = h * 31 + this.layoutMode.ordinal();
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

    private void cycleLayoutMode(int direction) {
        this.setLayoutMode((direction < 0) ? this.layoutMode.previous() : this.layoutMode.next());
    }

    private void setLayoutMode(TopologyLayoutMode mode) {
        if (mode == null || mode == this.layoutMode) {
            return;
        }
        this.layoutMode = mode;
        this.lastGraphHash = 0;
        this.hoveredNode = null;
        this.draggedNode = null;
        this.nodeDragging = false;
    }

    private void animateLayout() {
        if (this.nodePositions == null || this.targetNodePositions == null || this.nodePositions.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (this.lastAnimationMs == 0L) {
            this.lastAnimationMs = now;
            return;
        }
        final float deltaSeconds = Math.max(0.0f, Math.min(0.1f, (now - this.lastAnimationMs) / 1000.0f));
        this.lastAnimationMs = now;
        final float alpha = 1.0f - (float)Math.exp(-deltaSeconds * 9.0f * 0.75f);
        for (Map.Entry<BlockPos, float[]> entry : this.targetNodePositions.entrySet()) {
            if (this.nodeDragging && entry.getKey().equals(this.draggedNode)) {
                continue;
            }
            final float[] current = this.nodePositions.get(entry.getKey());
            final float[] target = entry.getValue();
            if (current == null || target == null) {
                continue;
            }
            current[0] += (target[0] - current[0]) * alpha;
            current[1] += (target[1] - current[1]) * alpha;
            if (Math.abs(target[0] - current[0]) < 0.05f) {
                current[0] = target[0];
            }
            if (Math.abs(target[1] - current[1]) < 0.05f) {
                current[1] = target[1];
            }
        }
    }

    private Map<BlockPos, float[]> scaleFlowLayout(TopologyFlowLayout.LayoutResult layout) {
        final Map<BlockPos, float[]> scaled = new HashMap<BlockPos, float[]>();
        final int layoutW = Math.max(80, this.areaW - 72);
        final int layoutH = Math.max(80, this.areaH - 48);
        for (Map.Entry<BlockPos, float[]> entry : layout.getPositions().entrySet()) {
            final float[] pos = entry.getValue();
            scaled.put(entry.getKey(), new float[] { (pos[0] - 0.5f) * layoutW, (pos[1] - 0.5f) * layoutH });
        }
        return scaled;
    }

    private Map<BlockPos, float[]> copyPositions(Map<BlockPos, float[]> source) {
        final Map<BlockPos, float[]> copy = new HashMap<BlockPos, float[]>();
        for (Map.Entry<BlockPos, float[]> entry : source.entrySet()) {
            final float[] pos = entry.getValue();
            copy.put(entry.getKey(), new float[] { pos[0], pos[1] });
        }
        return copy;
    }

    private void renderSummaryBar(GuiGraphics g, NetworkGraph graph) {
        final int barY = this.areaY + this.areaH + 2;
        final int barH = 16;
        final boolean hasCapacity = graph.hasMeasuredCapacity();
        final float load = hasCapacity ? (graph.getTotalStress() / graph.getTotalCapacity()) : 0.0f;
        final int loadColor = hasCapacity ? ((load > 0.9f) ? -48060 : ((load > 0.7f) ? -21982 : -12255352)) : this.WARN;
        final String summary;
        if (graph.isOverStressed()) {
            summary = String.format("§lNodes§r: %d  |  §lStress§r: %.0f / %.0f SU (%.0f%%)  |  §c§lOVERSTRESSED", graph.getNodes().size(), graph.getTotalStress(), graph.getTotalCapacity(), load * 100.0f);
        }
        else if (!hasCapacity) {
            summary = String.format("§lNodes§r: %d  |  §lStress§r: %.0f SU  |  §6§l%s", graph.getNodes().size(), graph.getTotalStress(), graph.hasExplicitSource() ? "CAPACITY UNKNOWN" : "ROOT INFERRED");
        }
        else {
            summary = String.format("§lNodes§r: %d  |  §lStress§r: %.0f / %.0f SU (%.0f%%)  |  §a§lOK", graph.getNodes().size(), graph.getTotalStress(), graph.getTotalCapacity(), load * 100.0f);
        }
        g.drawString(this.font, summary, this.areaX + 4, barY + 2, this.TEXT);
        final int barX = this.areaX + this.areaW - 104;
        final int barW = 100;
        g.fill(barX, barY, barX + barW, barY + barH, -15066582);
        final int fillW = hasCapacity ? (int)(barW * Math.min(1.0f, load)) : barW / 2;
        g.fill(barX, barY, barX + fillW, barY + barH, loadColor);
        this.drawBorder(g, barX, barY, barW, barH, -13421739);
        g.drawCenteredString(this.font, hasCapacity ? String.format("%.0f%%", load * 100.0f) : "?", barX + barW / 2, barY + 4, -1);
    }

    private void renderNodeTooltip(GuiGraphics g, TopologyNode node, int mx, int my) {
        final List<Component> lines = new ArrayList<Component>();
        lines.add(Component.literal("§l" + node.getName()));
        lines.add(Component.literal("§7Type: §f" + this.formatNodeType(node.getType())));
        lines.add(Component.literal("§7Speed: §f" + this.formatSpeed(node.getSpeed())));
        if (node.getStressImpact() > 0.0f) {
            lines.add(Component.literal("§7Stress: §c" + this.formatSU(node.getStressImpact())));
        }
        if (node.getCapacityProvided() > 0.0f) {
            lines.add(Component.literal("§7Capacity: §a" + this.formatSU(node.getCapacityProvided())));
        }
        if (node.isSource()) {
            lines.add(Component.literal("§6\u26a1 Power Source"));
        }
        else if (node.isInferredRoot()) {
            lines.add(Component.literal("§6\u21E2 Inferred Root"));
        }
        else if (this.flowLayout != null && this.flowLayout.isPrimaryRoot(node.getPos())) {
            lines.add(Component.literal("§6\u21E2 Primary Flow Root"));
        }
        if (node.getUpstreamPos() != null) {
            lines.add(Component.literal("§8Upstream: " + node.getUpstreamPos().toShortString()));
        }
        if (node.isOverStressed()) {
            lines.add(Component.literal("§c\u26a0 Overstressed!"));
        }
        lines.add(Component.literal("§8Pos: " + node.getPos().toShortString()));
        lines.add(Component.literal("§8§oClick & drag to reposition"));
        final int tipX = Math.min(mx + 8, this.width - 180);
        final int tipY = Math.min(my - 8, this.height - lines.size() * 12 - 10);
        g.renderTooltip(this.font, (List)lines, (Optional)Optional.empty(), tipX, tipY);
    }

    private void renderLegend(GuiGraphics g) {
        final int lx = this.areaX + this.areaW - 110;
        final int ly = this.areaY + 4;
        final int lw = 106;
        final int lh = 72;
        g.fill(lx, ly, lx + lw, ly + lh, -871559904);
        this.drawBorder(g, lx, ly, lw, lh, -13421739);
        g.drawString(this.font, "§7Legend:", lx + 4, ly + 2, this.DIM);
        g.fill(lx + 4, ly + 14, lx + 12, ly + 22, -12255352);
        g.drawString(this.font, "Source", lx + 16, ly + 14, -12255352);
        g.fill(lx + 4, ly + 26, lx + 12, ly + 34, this.WARN);
        g.drawString(this.font, "Flow root", lx + 16, ly + 26, this.WARN);
        g.fill(lx + 4, ly + 38, lx + 12, ly + 46, -12285697);
        g.drawString(this.font, "Consumer", lx + 16, ly + 38, -12285697);
        g.fill(lx + 4, ly + 50, lx + 12, ly + 58, -7829368);
        g.drawString(this.font, "Transfer", lx + 16, ly + 50, -7829368);
        g.fill(lx + 4, ly + 62, lx + 12, ly + 70, -48060);
        g.drawString(this.font, "§cOverstressed", lx + 16, ly + 62, -48060);
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        this.zoom *= ((scrollY > 0.0) ? 1.15f : 0.87f);
        this.zoom = Math.max(0.2f, Math.min(3.0f, this.zoom));
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (mx >= this.modePrevX && mx < this.modePrevX + this.modePrevW && my >= this.modePrevY && my < this.modePrevY + this.modePrevH) {
                this.cycleLayoutMode(-1);
                return true;
            }
            if (mx >= this.modeLabelX && mx < this.modeLabelX + this.modeLabelW && my >= this.modeLabelY && my < this.modeLabelY + this.modeLabelH) {
                this.cycleLayoutMode(1);
                return true;
            }
            if (mx >= this.modeNextX && mx < this.modeNextX + this.modeNextW && my >= this.modeNextY && my < this.modeNextY + this.modeNextH) {
                this.cycleLayoutMode(1);
                return true;
            }
        }
        if (button != 0 || mx < this.areaX || mx > this.areaX + this.areaW || my < this.areaY || my > this.areaY + this.areaH) {
            return super.mouseClicked(mx, my, button);
        }
        if (this.hoveredNode != null && this.nodePositions.containsKey(this.hoveredNode)) {
            this.draggedNode = this.hoveredNode;
            return this.nodeDragging = true;
        }
        this.panning = true;
        this.panStartX = mx;
        this.panStartY = my;
        this.panStartPanX = this.panX;
        this.panStartPanY = this.panY;
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            this.panning = false;
            if (this.nodeDragging) {
                this.nodeDragging = false;
                this.draggedNode = null;
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && this.nodeDragging && this.draggedNode != null) {
            final float[] pos = this.nodePositions.get(this.draggedNode);
            final float[] target = (this.targetNodePositions != null) ? this.targetNodePositions.get(this.draggedNode) : null;
            if (pos != null) {
                final float[] array = pos;
                final int n = 0;
                array[n] += (float)(dx / this.zoom);
                final float[] array2 = pos;
                final int n2 = 1;
                array2[n2] += (float)(dy / this.zoom);
            }
            if (target != null && pos != null) {
                target[0] = pos[0];
                target[1] = pos[1];
            }
            return true;
        }
        if (button == 0 && this.panning) {
            this.panX = this.panStartPanX + (float)(mx - this.panStartX) / this.zoom;
            this.panY = this.panStartPanY + (float)(my - this.panStartY) / this.zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private int getNodeColor(TopologyNode node) {
        if (node.isOverStressed()) {
            return -48060;
        }
        if (node.isSource()) {
            return -12255352;
        }
        if (node.isInferredRoot()) {
            return this.WARN;
        }
        final NodeType t = node.getType();
        if (t.isObserverLike()) {
            return -10040065;
        }
        if (t.isTransferOrgan()) {
            return switch (t) {
                case GEARBOX, GEARSHIFT, SPEED_CONTROLLER -> -4487100;
                default -> -7829368;
            };
        }
        if (t.isMachineLike()) {
            return -12285697;
        }
        return switch (t) {
            default -> -11171892;
        };
    }

    private int getNodeRadius(TopologyNode node) {
        if (node.isSource() || node.isInferredRoot()) {
            return 7;
        }
        return (node.getType().getIconSize() >= 7) ? 6 : 4;
    }

    private String formatSpeed(float speed) {
        if (Math.abs(speed) >= 1000.0f) {
            return String.format("%.1fk RPM", speed / 1000.0f);
        }
        return String.format("%.0f RPM", speed);
    }

    private String formatSU(float su) {
        if (su >= 1000.0f) {
            return String.format("%.1fk SU", su / 1000.0f);
        }
        return String.format("%.0f SU", su);
    }

    private String formatRatio(float ratio) {
        if (ratio == (int)ratio) {
            return (int)ratio + ":1";
        }
        return String.format("%.1f:1", ratio);
    }

    private String formatNodeType(NodeType type) {
        final String name = type.name().replace('_', ' ').toLowerCase();
        final StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private boolean shouldShowPopupLabel(TopologyNode node, boolean hovered, boolean inferredRoot) {
        if (hovered || node.isSource() || inferredRoot || node.getType().showLabel()) {
            return this.zoom > 0.45f;
        }
        if (node.getType().isObserverLike()) {
            return this.zoom > 0.75f;
        }
        if (node.getType().isTransferOrgan()) {
            return this.zoom > 1.1f;
        }
        return this.zoom > 0.8f;
    }

    private String compactLabel(String label, int maxLen) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        if (label.length() <= maxLen) {
            return label;
        }
        return label.substring(0, Math.max(1, maxLen - 2)) + "..";
    }

    private int darken(int color, float factor) {
        final int a = color >> 24 & 0xFF;
        final int r = (int)((color >> 16 & 0xFF) * factor);
        final int gr = (int)((color >> 8 & 0xFF) * factor);
        final int b = (int)((color & 0xFF) * factor);
        return a << 24 | r << 16 | gr << 8 | b;
    }
}
