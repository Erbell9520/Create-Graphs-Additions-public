package com.createstats.pathfinder.client;

import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.menu.PathScannerMenu;
import com.createstats.pathfinder.model.ChunkScanCacheEntry;
import com.createstats.pathfinder.model.ScanRegion;
import com.createstats.pathfinder.model.UnknownPolicy;
import com.createstats.pathfinder.settings.PathScanSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PathScannerScreen extends AbstractContainerScreen<PathScannerMenu> {
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 360;
    private static final int MAP_X = 18;
    private static final int MAP_Y = 56;
    private static final int MAP_W = 278;
    private static final int MAP_H = 254;
    private static final int CONTROL_X = 314;
    private static final int CONTROL_Y = 56;
    private static final int CONTROL_W = 268;
    private static final int CONTROL_H = 132;
    private static final int COVERAGE_X = 314;
    private static final int COVERAGE_Y = 204;
    private static final int COVERAGE_W = 268;
    private static final int COVERAGE_H = 106;
    private static final int FOOTER_X = 18;
    private static final int FOOTER_Y = 322;
    private static final int FOOTER_W = 564;
    private static final int FOOTER_H = 24;
    private static final int CELL_SIZE = 34;
    private static final int CELL_GAP = 5;
    private static final int BG = 0xF00A1114;
    private static final int PANEL = 0xF0111D22;
    private static final int PANEL_SOFT = 0xE516252B;
    private static final int BORDER = 0xFF4D6670;
    private static final int SOFT_BORDER = 0x665A737B;
    private static final int ACCENT = 0xFF64D8C8;
    private static final int OK = 0xFF7EE0A2;
    private static final int TEXT = 0xFFE8F2EE;
    private static final int DIM = 0xFF92A8AD;
    private static final int WARN = 0xFFFFC75F;
    private static final int BAD = 0xFFFF7F6E;
    private static final int MUTED = 0xFF4C5B60;

    private Button unknownButton;

    public PathScannerScreen(PathScannerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int controlX = this.leftPos + CONTROL_X;
        int controlY = this.topPos + CONTROL_Y;
        this.addActionButton(PathScannerMenu.BUTTON_FORWARD_MINUS, controlX + 198, controlY + 43, 20, "-");
        this.addActionButton(PathScannerMenu.BUTTON_FORWARD_PLUS, controlX + 224, controlY + 43, 20, "+");
        this.addActionButton(PathScannerMenu.BUTTON_SIDE_MINUS, controlX + 198, controlY + 71, 20, "-");
        this.addActionButton(PathScannerMenu.BUTTON_SIDE_PLUS, controlX + 224, controlY + 71, 20, "+");
        this.unknownButton = this.addActionButton(PathScannerMenu.BUTTON_UNKNOWN_TOGGLE, controlX + 116, controlY + 103, 92, "Unknown");
        this.addActionButton(PathScannerMenu.BUTTON_SCAN_NOW, controlX + 214, controlY + 103, 54, "Scan");
        this.refreshButtonText();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshButtonText();
    }

    private Button addActionButton(int id, int x, int y, int width, String label) {
        Button button = Button.builder(Component.literal(label), ignored -> this.sendButton(id)).bounds(x, y, width, 18).build();
        this.addRenderableWidget(button);
        return button;
    }

    private void sendButton(int id) {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void refreshButtonText() {
        if (this.unknownButton != null) {
            this.unknownButton.setMessage(Component.literal(unknownPolicyLabel(this.menu.getScanner().settings().unknownPolicy())));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        this.renderConsole(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        // FE Dependent Mode side panel (self-gating: draws nothing while the mode is off).
        com.createstats.power.client.FeStatusWidget.draw(graphics, font, leftPos + imageWidth + 6, topPos + 6, 110,
                this.menu.getScanner() == null ? null : this.menu.getScanner().feConsumer());
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
        PathScannerBE scanner = this.menu.getScanner();
        PathScanSettings settings = scanner.settings();
        ScanRegion region = scanner.currentRegion();
        int forward = region == null ? settings.forwardChunks() : region.forwardChunks();
        int side = region == null ? settings.sideChunks() : region.sideChunks();

        graphics.drawString(this.font, "Path Scanner", 18, 14, ACCENT, false);
        graphics.drawString(this.font, "top-surface drive silhouette", 18, 28, DIM, false);
        drawHeaderMetric(graphics, coverageText(scanner), 582, 14,
            scanner.loadedChunkCount() >= scanner.expectedChunkCount() && scanner.unknownChunkCount() <= 0 ? OK : WARN);
        drawHeaderMetric(graphics, scanner.debugFacingLabel(), 582, 28, DIM);

        drawBox(graphics, MAP_X, MAP_Y, MAP_W, MAP_H, PANEL);
        drawBox(graphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H, PANEL_SOFT);
        drawBox(graphics, COVERAGE_X, COVERAGE_Y, COVERAGE_W, COVERAGE_H, PANEL);
        drawBox(graphics, FOOTER_X, FOOTER_Y, FOOTER_W, FOOTER_H, 0xE70D171B);

        renderSilhouetteBoard(graphics, scanner, settings, region, forward, side);
        renderControlPanel(graphics, scanner, settings, forward, side);
        renderCoveragePanel(graphics, scanner);

        String footer = scanner.hasActiveVehicleScanFrame()
            ? "Vehicle frame active: scanner is following the moving contraption."
            : "Mounted frame: scanner uses this block position and facing.";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(footer, FOOTER_W - 18), FOOTER_X + 9, FOOTER_Y + 8,
            scanner.hasActiveVehicleScanFrame() ? ACCENT : DIM, false);
    }

    private void renderSilhouetteBoard(GuiGraphics graphics, PathScannerBE scanner, PathScanSettings settings,
                                       ScanRegion region, int forwardChunks, int sideChunks) {
        graphics.drawString(this.font, "Top Silhouette", MAP_X + 12, MAP_Y + 10, TEXT, false);
        int backChunks = PathScanSettings.BACKWARD_CHUNKS;
        String size = (forwardChunks + backChunks) + " deep x " + (sideChunks * 2 + 1) + " wide";
        graphics.drawString(this.font, size, MAP_X + MAP_W - 12 - this.font.width(size), MAP_Y + 10, ACCENT, false);
        graphics.drawString(this.font, forwardChunks + " ahead / " + backChunks + " behind", MAP_X + 12, MAP_Y + 24, DIM, false);

        int rows = forwardChunks + backChunks;
        int cols = sideChunks * 2 + 1;
        int gridTop = MAP_Y + 40;
        int legendY = MAP_Y + MAP_H - 40;
        int availW = MAP_W - 24;
        int availH = legendY - gridTop - 8;
        int gap = (rows > 6 || cols > 6) ? 2 : CELL_GAP;
        int cell = Math.min((availW - (cols - 1) * gap) / cols, (availH - (rows - 1) * gap) / rows);
        cell = Math.max(8, Math.min(CELL_SIZE, cell));
        boolean labels = cell >= 22;
        int gridW = cols * cell + (cols - 1) * gap;
        int gridH = rows * cell + (rows - 1) * gap;
        int gridX = MAP_X + (MAP_W - gridW) / 2;
        int gridY = gridTop + Math.max(0, (availH - gridH) / 2);

        BlockPos origin = region == null ? scanner.debugScanOrigin() : region.origin();
        Direction facing = region == null ? Direction.SOUTH : facingForYaw(region.yawDeg());
        int baseChunkX = origin.getX() >> 4;
        int baseChunkZ = origin.getZ() >> 4;
        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();
        int rightX = -forwardZ;
        int rightZ = forwardX;

        // Row 0 is the farthest-forward chunk (top); the vehicle sits at forward=0 (highlighted); the
        // rows below it are the fixed rearward margin.
        for (int row = 0; row < rows; row++) {
            int forward = (forwardChunks - 1) - row;
            for (int col = 0; col < cols; col++) {
                int side = col - sideChunks;
                int chunkX = baseChunkX + forwardX * forward + rightX * side;
                int chunkZ = baseChunkZ + forwardZ * forward + rightZ * side;
                ChunkScanCacheEntry entry = scanner.getCacheEntry(chunkX, chunkZ);
                int x = gridX + col * (cell + gap);
                int y = gridY + row * (cell + gap);
                graphics.fill(x, y, x + cell, y + cell, chunkColor(entry, true));
                border(graphics, x, y, cell, cell, forward == 0 ? ACCENT : SOFT_BORDER);
                if (labels) {
                    graphics.drawCenteredString(this.font, chunkLabel(entry), x + cell / 2, y + (cell - 8) / 2,
                        entry == null || !entry.loaded() ? DIM : TEXT);
                }
            }
        }

        // Separate the forward window from the rearward margin, and mark the vehicle's own cell so
        // the top-down orientation reads at a glance.
        int vehicleRowY = gridY + (forwardChunks - 1) * (cell + gap);
        if (backChunks > 0) {
            int divY = vehicleRowY + cell + (gap + 1) / 2;
            graphics.fill(gridX - 2, divY, gridX + gridW + 2, divY + 1, 0x66516E86);
        }
        int vcx = gridX + sideChunks * (cell + gap) + cell / 2;
        int vcy = vehicleRowY + cell / 2;
        int marker = Math.max(2, cell / 6);
        graphics.fill(vcx - marker, vcy - marker, vcx + marker, vcy + marker, ACCENT);
        if (gridY - gridTop >= 9) {
            graphics.drawCenteredString(this.font, "ahead", gridX + gridW / 2, gridY - 9, DIM);
        }

        drawLegend(graphics, MAP_X + 18, legendY, OK, "clear");
        drawLegend(graphics, MAP_X + 88, legendY, WARN, "rough");
        drawLegend(graphics, MAP_X + 160, legendY, BAD, "blocked");
        drawLegend(graphics, MAP_X + 232, legendY, MUTED, "wait");
    }

    private void renderControlPanel(GuiGraphics graphics, PathScannerBE scanner, PathScanSettings settings,
                                    int forwardChunks, int sideChunks) {
        graphics.drawString(this.font, "Scan Square", CONTROL_X + 12, CONTROL_Y + 10, TEXT, false);
        graphics.drawString(this.font, "player adjustable scan envelope", CONTROL_X + 12, CONTROL_Y + 24, DIM, false);
        drawControlLine(graphics, "Depth", forwardChunks + "/" + PathScanSettings.MAX_FORWARD_CHUNKS + " chunks", CONTROL_Y + 48, ACCENT);
        drawControlLine(graphics, "Width", (sideChunks * 2 + 1) + "/" + (PathScanSettings.MAX_SIDE_CHUNKS * 2 + 1) + " chunks", CONTROL_Y + 76, ACCENT);
        graphics.drawString(this.font, "Unknown", CONTROL_X + 12, CONTROL_Y + 106, DIM, false);
        drawControlLine(graphics, "Budget", scanner.lastProcessedChunks() + "/" + settings.chunksPerTick() + " per tick",
            CONTROL_Y + 122, DIM);
    }

    private void renderCoveragePanel(GuiGraphics graphics, PathScannerBE scanner) {
        graphics.drawString(this.font, "Coverage", COVERAGE_X + 12, COVERAGE_Y + 10, TEXT, false);
        int expected = Math.max(1, scanner.expectedChunkCount());
        int loaded = Math.min(expected, scanner.loadedChunkCount());
        drawProgressBar(graphics, COVERAGE_X + 12, COVERAGE_Y + 28, COVERAGE_W - 24, 8,
            Math.round(loaded * 100.0f / expected), scanner.unknownChunkCount() > 0 ? WARN : ACCENT);
        drawMetric(graphics, "Loaded chunks", scanner.loadedChunkCount(), COVERAGE_Y + 46, TEXT);
        drawMetric(graphics, "Unknown chunks", scanner.unknownChunkCount(), COVERAGE_Y + 62,
            scanner.unknownChunkCount() > 0 ? WARN : TEXT);
        drawMetric(graphics, "Passable cells", scanner.totalPassableCells(), COVERAGE_Y + 78, OK);
        drawMetric(graphics, "Marginal / blocked", scanner.totalMarginalCells() + scanner.totalBlockedCells(), COVERAGE_Y + 94,
            scanner.totalBlockedCells() > 0 ? BAD : WARN);
    }

    private void drawControlLine(GuiGraphics graphics, String label, String value, int y, int color) {
        graphics.drawString(this.font, label, CONTROL_X + 12, y, DIM, false);
        graphics.drawString(this.font, value, CONTROL_X + 188 - this.font.width(value), y, color, false);
    }

    private void drawMetric(GuiGraphics graphics, String label, int value, int y, int color) {
        graphics.drawString(this.font, label, COVERAGE_X + 12, y, DIM, false);
        String text = String.valueOf(value);
        graphics.drawString(this.font, text, COVERAGE_X + COVERAGE_W - 14 - this.font.width(text), y, color, false);
    }

    private void drawLegend(GuiGraphics graphics, int x, int y, int color, String label) {
        graphics.fill(x, y, x + 10, y + 10, color);
        border(graphics, x, y, 10, 10, SOFT_BORDER);
        graphics.drawString(this.font, label, x + 14, y + 1, DIM, false);
    }

    private static int chunkColor(ChunkScanCacheEntry entry, boolean active) {
        if (!active) return 0xFF0C1215;
        if (entry == null || !entry.loaded()) return 0xFF223038;
        int total = Math.max(1, entry.passableCount() + entry.marginalCount() + entry.blockedCount());
        float blocked = entry.blockedCount() / (float) total;
        float marginal = entry.marginalCount() / (float) total;
        if (blocked > 0.55f) return 0xFF4B2024;
        if (marginal > 0.28f || blocked > 0.22f) return 0xFF4A3A1E;
        return 0xFF183F34;
    }

    private static String chunkLabel(ChunkScanCacheEntry entry) {
        if (entry == null) return "...";
        if (!entry.loaded()) return "?";
        int total = Math.max(1, entry.passableCount() + entry.marginalCount() + entry.blockedCount());
        int score = Math.round((entry.passableCount() + entry.marginalCount() * 0.45f) * 100.0f / total);
        return score + "%";
    }

    private static Direction facingForYaw(float yaw) {
        float normalized = yaw % 360f;
        if (normalized < -180f) normalized += 360f;
        if (normalized > 180f) normalized -= 360f;
        if (normalized > 45f && normalized <= 135f) return Direction.WEST;
        if (normalized < -45f && normalized >= -135f) return Direction.EAST;
        if (normalized > 135f || normalized <= -135f) return Direction.NORTH;
        return Direction.SOUTH;
    }

    private static String coverageText(PathScannerBE scanner) {
        int expected = Math.max(1, scanner.expectedChunkCount());
        int loaded = Math.min(expected, scanner.loadedChunkCount());
        return loaded + "/" + expected + " loaded";
    }

    private static String unknownPolicyLabel(UnknownPolicy policy) {
        return switch (policy) {
            case WANDERING -> "Wander";
            case TENTATIVE -> "Tentative";
            default -> "Block";
        };
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
        graphics.fill(x, y, x + width, y + height, 0xFF071014);
        int fill = Math.round(width * Math.max(0, Math.min(100, percent)) / 100.0f);
        if (fill > 0) graphics.fill(x, y, x + fill, y + height, color);
        border(graphics, x, y, width, height, SOFT_BORDER);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}