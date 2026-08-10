package com.createstats.pathfinder.client;

import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.menu.PathfinderComputerMenu;
import com.createstats.pathfinder.model.PlannerJobStage;
import com.createstats.pathfinder.model.RouteGuidance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PathfinderComputerScreen extends AbstractContainerScreen<PathfinderComputerMenu> {
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 360;
    private static final int FLOW_X = 18;
    private static final int FLOW_Y = 56;
    private static final int FLOW_W = 564;
    private static final int FLOW_H = 78;
    private static final int MAP_X = 18;
    private static final int MAP_Y = 150;
    private static final int MAP_W = 356;
    private static final int MAP_H = 154;
    private static final int ROUTE_X = 392;
    private static final int ROUTE_Y = 150;
    private static final int ROUTE_W = 190;
    private static final int ROUTE_H = 154;
    private static final int CONTROL_X = 18;
    private static final int CONTROL_Y = 316;
    private static final int CONTROL_W = 564;
    private static final int CONTROL_H = 28;
    private static final int BG = 0xF0091018;
    private static final int PANEL = 0xF0111A23;
    private static final int PANEL_SOFT = 0xE515222D;
    private static final int BORDER = 0xFF50667A;
    private static final int SOFT_BORDER = 0x665A7188;
    private static final int ACCENT = 0xFF78C8FF;
    private static final int CYAN = 0xFF64D8C8;
    private static final int OK = 0xFF7EE0A2;
    private static final int TEXT = 0xFFE8EEF7;
    private static final int DIM = 0xFF9AA8B6;
    private static final int WARN = 0xFFFFC75F;
    private static final int BAD = 0xFFFF7F6E;
    public PathfinderComputerScreen(PathfinderComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int y = this.topPos + CONTROL_Y + 5;
        this.addActionButton(PathfinderComputerMenu.BUTTON_SPEED_DOWN, this.leftPos + CONTROL_X + 156, y, 20, "-");
        this.addActionButton(PathfinderComputerMenu.BUTTON_SPEED_UP, this.leftPos + CONTROL_X + 180, y, 20, "+");
        this.addRenderableWidget(new CornerClearanceSlider(this.leftPos + CONTROL_X + 266, y, 154, 18));
        this.addActionButton(PathfinderComputerMenu.BUTTON_REFRESH_LINK, this.leftPos + CONTROL_X + 438, y, 62, "Refresh");
        this.addActionButton(PathfinderComputerMenu.BUTTON_CLEAR_LINK, this.leftPos + CONTROL_X + 506, y, 48, "Clear");
    }

    private void addActionButton(int id, int x, int y, int width, String label) {
        this.addRenderableWidget(Button.builder(Component.literal(label), ignored -> this.sendButton(id)).bounds(x, y, width, 18).build());
    }

    private void sendButton(int id) {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        this.renderConsole(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        // FE Dependent Mode side panel (self-gating: draws nothing while the mode is off).
        com.createstats.power.client.FeStatusWidget.draw(graphics, font, leftPos + imageWidth + 6, topPos + 6, 110,
                this.menu.getComputer() == null ? null : this.menu.getComputer().feConsumer());
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        PathfinderComputerBE computer = this.menu.getComputer();
        RouteGuidance guidance = computer.activeGuidance();
        graphics.drawString(this.font, "Pathfinder Computer", 18, 14, ACCENT, false);
        graphics.drawString(this.font, "Scanner -> dynamic A* -> continuous VCC route", 18, 28, DIM, false);
        drawHeaderMetric(graphics, computer.hasLinkedFcc() ? "VCC linked" : "VCC not linked", 582, 14,
            computer.hasLinkedFcc() ? OK : WARN);
        drawHeaderMetric(graphics, routeHeader(guidance), 582, 28, guidance != null && guidance.confidence() > 0.0f ? TEXT : DIM);

        drawBox(graphics, FLOW_X, FLOW_Y, FLOW_W, FLOW_H, PANEL_SOFT);
        drawBox(graphics, MAP_X, MAP_Y, MAP_W, MAP_H, PANEL);
        drawBox(graphics, ROUTE_X, ROUTE_Y, ROUTE_W, ROUTE_H, PANEL);
        drawBox(graphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H, 0xE70D171F);

        renderFlow(graphics, computer, guidance);
        renderRouteMap(graphics, computer, guidance);
        renderRouteStatus(graphics, computer, guidance);
        renderControls(graphics, computer);
    }

    private void renderFlow(GuiGraphics graphics, PathfinderComputerBE computer, RouteGuidance guidance) {
        graphics.drawString(this.font, "Route Flow", FLOW_X + 12, FLOW_Y + 10, TEXT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(computer.plannerStatus(), FLOW_W - 132), FLOW_X + 96, FLOW_Y + 10,
            guidance != null && guidance.confidence() > 0f ? CYAN : WARN, false);

        int y = FLOW_Y + 36;
        int w = 124;
        int gap = 14;
        drawFlowSegment(graphics, FLOW_X + 12, y, w, "Scanner", scannerStatus(computer),
            computer.hasLinkedScanner() && !computer.isLinkedScannerStale() ? OK : WARN);
        drawFlowSegment(graphics, FLOW_X + 12 + (w + gap), y, w, "Target", targetStatus(guidance),
            guidance != null && guidance.waypoints().size() > 1 ? ACCENT : DIM);
        drawFlowSegment(graphics, FLOW_X + 12 + (w + gap) * 2, y, w, "A*", astarStatus(computer, guidance),
            guidance != null && guidance.confidence() > 0f ? CYAN : WARN);
        drawFlowSegment(graphics, FLOW_X + 12 + (w + gap) * 3, y, w, "VCC", fccStatus(computer),
            computer.hasLinkedFcc() ? OK : DIM);
    }

    private void renderRouteMap(GuiGraphics graphics, PathfinderComputerBE computer, RouteGuidance guidance) {
        graphics.drawString(this.font, "Continuous Route", MAP_X + 12, MAP_Y + 10, TEXT, false);
        String continuity = continuityLabel(computer);
        graphics.drawString(this.font, continuity, MAP_X + MAP_W - 12 - this.font.width(continuity), MAP_Y + 10,
            computer.routeBufferMerged() ? CYAN : DIM, false);

        int gridX = MAP_X + 12;
        int gridY = MAP_Y + 30;
        int gridW = MAP_W - 24;
        int gridH = MAP_H - 42;
        graphics.fill(gridX, gridY, gridX + gridW, gridY + gridH, 0xFF081018);
        border(graphics, gridX, gridY, gridW, gridH, 0x55445A6C);
        for (int i = 1; i < 6; i++) {
            int gx = gridX + i * gridW / 6;
            int gy = gridY + i * gridH / 6;
            graphics.fill(gx, gridY + 1, gx + 1, gridY + gridH - 1, 0x223F5368);
            graphics.fill(gridX + 1, gy, gridX + gridW - 1, gy + 1, 0x223F5368);
        }

        List<Vec3> points = guidance == null ? List.of() : guidance.waypoints();
        if (points.size() < 2) {
            graphics.drawCenteredString(this.font, computer.hasLinkedScanner() ? "WAITING FOR A* ROUTE" : "LINK SCANNER",
                gridX + gridW / 2, gridY + gridH / 2 - 4, computer.hasLinkedScanner() ? WARN : DIM);
            return;
        }

        RouteBounds bounds = routeBounds(points);
        int activeIndex = Math.max(0, Math.min(computer.linkedFccRouteWaypointIndex(), points.size() - 2));
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 first = points.get(i);
            Vec3 second = points.get(i + 1);
            int color = i < activeIndex ? 0xFF2D6C56 : i == activeIndex ? ACCENT : CYAN;
            drawLine(graphics, mapRouteX(first.x, bounds, gridX, gridW), mapRouteY(first.z, bounds, gridY, gridH),
                mapRouteX(second.x, bounds, gridX, gridW), mapRouteY(second.z, bounds, gridY, gridH), color);
        }

        Vec3 start = points.get(0);
        Vec3 end = points.get(points.size() - 1);
        fillCircle(graphics, mapRouteX(start.x, bounds, gridX, gridW), mapRouteY(start.z, bounds, gridY, gridH), 3, OK);
        fillCircle(graphics, mapRouteX(end.x, bounds, gridX, gridW), mapRouteY(end.z, bounds, gridY, gridH), 4, ACCENT);
        Vec3 active = points.get(activeIndex);
        int activeX = mapRouteX(active.x, bounds, gridX, gridW);
        int activeY = mapRouteY(active.z, bounds, gridY, gridH);
        graphics.fill(activeX - 3, activeY - 3, activeX + 4, activeY + 4, 0xFF081018);
        border(graphics, activeX - 4, activeY - 4, 8, 8, TEXT);
    }

    private void renderRouteStatus(GuiGraphics graphics, PathfinderComputerBE computer, RouteGuidance guidance) {
        graphics.drawString(this.font, "Planner", ROUTE_X + 12, ROUTE_Y + 10, TEXT, false);
        drawProgressBar(graphics, ROUTE_X + 12, ROUTE_Y + 28, ROUTE_W - 24, 8, computer.plannerProgressPercent(),
            computer.plannerStage() == PlannerJobStage.HOLD ? WARN : ACCENT);
        graphics.drawString(this.font, computer.plannerStage().label(), ROUTE_X + 12, ROUTE_Y + 42, DIM, false);
        if (guidance == null || guidance.confidence() <= 0f) {
            drawMetric(graphics, "Usable cells", computer.plannerUsableCells(), ROUTE_Y + 62, TEXT);
            drawMetric(graphics, "Blocked cells", computer.plannerBlockedCells(), ROUTE_Y + 78, BAD);
            drawMetric(graphics, "A* expanded", computer.plannerExpandedStates(), ROUTE_Y + 94, ACCENT);
            drawMetric(graphics, "Coverage", computer.linkedLoadedChunks(), ROUTE_Y + 116, computer.hasLinkedScanner() ? TEXT : DIM);
            return;
        }
        drawMetric(graphics, "Confidence", Math.round(guidance.confidence() * 100f), ROUTE_Y + 62, OK, "%");
        drawMetric(graphics, "Waypoints", guidance.waypoints().size(), ROUTE_Y + 78, TEXT);
        drawMetric(graphics, "Distance", (int) Math.round(guidance.estimatedDistance()), ROUTE_Y + 94, ACCENT, "m");
        drawMetric(graphics, "Kept + fresh", (int) Math.round(computer.routeBufferPreservedDistance()
            + computer.routeBufferFreshDistance()), ROUTE_Y + 110, CYAN, "m");
        drawMetric(graphics, "A* expanded", computer.plannerExpandedStates(), ROUTE_Y + 126, DIM);
    }

    private void renderControls(GuiGraphics graphics, PathfinderComputerBE computer) {
        graphics.drawString(this.font, "Speed", CONTROL_X + 12, CONTROL_Y + 10, DIM, false);
        String speed = formatSpeed(computer.maxGuidanceSpeed()) + " m/s";
        graphics.drawString(this.font, speed, CONTROL_X + 92 - this.font.width(speed), CONTROL_Y + 10, TEXT, false);
        graphics.drawString(this.font, "Corner", CONTROL_X + 218, CONTROL_Y + 10, DIM, false);
    }

    private void drawFlowSegment(GuiGraphics graphics, int x, int y, int width, String title, String status, int color) {
        graphics.fill(x, y, x + width, y + 26, 0xFF0C1720);
        border(graphics, x, y, width, 26, color);
        graphics.drawString(this.font, title, x + 6, y + 4, TEXT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(status, width - 12), x + 6, y + 16, color, false);
    }

    private void drawMetric(GuiGraphics graphics, String label, int value, int y, int color) {
        drawMetric(graphics, label, value, y, color, "");
    }

    private void drawMetric(GuiGraphics graphics, String label, int value, int y, int color, String suffix) {
        graphics.drawString(this.font, label, ROUTE_X + 12, y, DIM, false);
        String text = value + suffix;
        graphics.drawString(this.font, text, ROUTE_X + ROUTE_W - 12 - this.font.width(text), y, color, false);
    }

    private static String scannerStatus(PathfinderComputerBE computer) {
        if (!computer.hasLinkedScanner()) return "missing";
        if (computer.isLinkedScannerStale()) return "stale";
        return computer.linkedLoadedChunks() + "/" + Math.max(1, computer.linkedExpectedChunks()) + " chunks";
    }

    private static String targetStatus(RouteGuidance guidance) {
        if (guidance == null || guidance.waypoints().size() < 2) return "waiting";
        return guidance.routeName().isBlank() ? "closest point" : trimUpper(guidance.routeName(), 13);
    }

    private static String astarStatus(PathfinderComputerBE computer, RouteGuidance guidance) {
        if (guidance == null || guidance.confidence() <= 0f) return computer.plannerExpandedStates() + " cells";
        return Math.round(guidance.confidence() * 100f) + "% ready";
    }

    private static String fccStatus(PathfinderComputerBE computer) {
        if (!computer.hasLinkedFcc()) return "link open";
        int count = Math.max(1, computer.linkedFccRouteWaypointCount());
        return (computer.linkedFccRouteWaypointIndex() + 1) + "/" + count + " wp";
    }

    private static String continuityLabel(PathfinderComputerBE computer) {
        if (!computer.routeBufferMerged()) return "gen " + computer.routeGeneration();
        return Math.round(computer.routeBufferPreservedDistance()) + "m kept + "
            + Math.round(computer.routeBufferFreshDistance()) + "m fresh";
    }

    private static String routeHeader(RouteGuidance guidance) {
        if (guidance == null || guidance.waypoints().isEmpty()) return "No active route";
        return guidance.waypoints().size() + " wp  " + Math.round(guidance.estimatedDistance()) + "m";
    }

    private static String trimUpper(String text, int maxChars) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.length() > maxChars) cleaned = cleaned.substring(0, maxChars);
        return cleaned;
    }

    private static String formatSpeed(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value))
            : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String cornerBufferLabel(float value) {
        return "+" + formatSpeed(value) + " blocks";
    }

    private class CornerClearanceSlider extends AbstractSliderButton {
        private int lastSentStep;

        private CornerClearanceSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.syncFromComputer();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(cornerBufferLabel(PathfinderComputerBE.cornerClearanceExtraForStep(this.currentStep()))));
        }

        @Override
        protected void applyValue() {
            int step = this.currentStep();
            this.value = step / (double) PathfinderComputerBE.CORNER_CLEARANCE_STEPS;
            if (step != this.lastSentStep) {
                this.lastSentStep = step;
                PathfinderComputerScreen.this.sendButton(PathfinderComputerMenu.BUTTON_CORNER_CLEARANCE_BASE + step);
            }
            this.updateMessage();
        }

        private int currentStep() {
            return Mth.clamp((int) Math.round(this.value * PathfinderComputerBE.CORNER_CLEARANCE_STEPS),
                0, PathfinderComputerBE.CORNER_CLEARANCE_STEPS);
        }

        private void syncFromComputer() {
            int step = PathfinderComputerScreen.this.menu.getComputer().cornerClearanceStep();
            this.lastSentStep = step;
            this.value = step / (double) PathfinderComputerBE.CORNER_CLEARANCE_STEPS;
            this.updateMessage();
        }
    }

    private static RouteBounds routeBounds(List<Vec3> points) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }
        double pad = 8.0;
        return new RouteBounds(minX - pad, maxX + pad, minZ - pad, maxZ + pad);
    }

    private static int mapRouteX(double value, RouteBounds bounds, int x, int width) {
        double span = Math.max(1.0, bounds.maxX() - bounds.minX());
        return x + (int) Math.round((value - bounds.minX()) / span * Math.max(1, width - 1));
    }

    private static int mapRouteY(double value, RouteBounds bounds, int y, int height) {
        double span = Math.max(1.0, bounds.maxZ() - bounds.minZ());
        return y + height - 1 - (int) Math.round((value - bounds.minZ()) / span * Math.max(1, height - 1));
    }

    private void drawHeaderMetric(GuiGraphics graphics, String text, int rightX, int y, int color) {
        graphics.drawString(this.font, text, rightX - this.font.width(text), y, color, false);
    }

    private void renderConsole(GuiGraphics graphics) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, BG);
        border(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, BORDER);
    }

    private static void drawBox(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        border(graphics, x, y, width, height, BORDER);
    }

    private static void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height, int percent, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF071018);
        int fill = Math.round(width * Math.max(0, Math.min(100, percent)) / 100.0f);
        if (fill > 0) graphics.fill(x, y, x + fill, y + height, color);
        border(graphics, x, y, width, height, SOFT_BORDER);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
            if (x == x1 && y == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radius * radius) {
                    graphics.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
                }
            }
        }
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record RouteBounds(double minX, double maxX, double minZ, double maxZ) {
    }
}