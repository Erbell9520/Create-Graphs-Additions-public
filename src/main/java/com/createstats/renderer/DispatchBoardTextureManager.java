package com.createstats.renderer;

import com.createstats.CreateStats;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.dispatch.BoardHeaderState;
import com.createstats.dispatch.BoardTier;
import com.createstats.dispatch.CargoSummaryEntry;
import com.createstats.dispatch.DispatchBoardSnapshot;
import com.createstats.dispatch.DispatchRowEntry;
import com.createstats.dispatch.DispatchRowState;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderHistoryType;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.train.manifest.TrainSummarySnapshot;
import com.createstats.train.manifest.TrainVisitRecord;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class DispatchBoardTextureManager {

    private static final Map<BlockPos, TextureEntry> TEXTURES = new HashMap<>();
    private static final int BASE_RESOLUTION = 192;
    private static final int UPDATE_INTERVAL = 5;
    private static final int MAX_IDLE_TICKS = 200;
    private static final int CLEANUP_INTERVAL = 100;
    private static int cleanupCounter;
    private static long currentFrameTime;

    // Board color palette, dark industrial look with brass/copper accents
    private static final int BG_DARK = 0xFF1A1814;
    private static final int BG_PANEL = 0xFF231F1A;
    private static final int BORDER_OUTER = 0xFF3D342A;
    private static final int BORDER_INNER = 0xFF554A3C;
    private static final int BRASS_ACCENT = 0xFFD4B254;
    private static final int BRASS_DIM = 0xFF9A8240;
    private static final int HEADER_BG = 0xFF2E2820;
    private static final int ROW_BG_EVEN = 0xFF262118;
    private static final int ROW_BG_ODD = 0xFF2A2519;
    private static final int ROW_SEPARATOR = 0xFF3A3228;
    private static final int TEXT_PRIMARY = 0xFFE8D8B4;
    private static final int TEXT_DIM = 0xFF8D857C;
    private static final int TEXT_BRIGHT = 0xFFFFF0D0;
    private static final int SECTION_HEADER = 0xFF9A8240;
    private static final int TRAIN_PANEL_BG = 0xFF2A261E;
    private static final int TRAIN_DOCKED_COLOR = 0xFF74D87F;
    private static final int TRAIN_SIGNAL_COLOR = 0xFFFFCC00;
    private static final int TRAIN_ENROUTE_COLOR = 0xFF55CCFF;

    // 9x7 pixel-art train locomotive (side view)
    private static final boolean[][] ICON_TRAIN = {
        {false, false, true,  true,  true,  true,  true,  false, false},
        {false, true,  true,  true,  true,  true,  true,  true,  false},
        {true,  true,  true,  true,  true,  true,  true,  true,  true},
        {true,  true,  true,  true,  true,  true,  true,  true,  true},
        {true,  true,  true,  true,  true,  true,  true,  true,  true},
        {false, true,  false, true,  false, true,  false, true,  false},
        {false, false, false, false, false, false, false, false, false},
    };
    // 7x7 pixel-art crate/box
    private static final boolean[][] ICON_CARGO = {
        {true,  true,  true,  true,  true,  true,  true},
        {true,  false, false, true,  false, false, true},
        {true,  false, false, true,  false, false, true},
        {true,  true,  true,  true,  true,  true,  true},
        {true,  false, false, true,  false, false, true},
        {true,  false, false, true,  false, false, true},
        {true,  true,  true,  true,  true,  true,  true},
    };
    // 7x7 pixel-art clock
    private static final boolean[][] ICON_CLOCK = {
        {false, false, true,  true,  true,  false, false},
        {false, true,  false, false, false, true,  false},
        {true,  false, false, true,  false, false, true},
        {true,  false, false, true,  true,  false, true},
        {true,  false, false, false, false, false, true},
        {false, true,  false, false, false, true,  false},
        {false, false, true,  true,  true,  false, false},
    };
    // 7x7 pixel-art tracks/rail
    private static final boolean[][] ICON_TRACKS = {
        {true,  false, true,  false, true,  false, true},
        {false, false, false, false, false, false, false},
        {true,  true,  true,  true,  true,  true,  true},
        {false, false, false, false, false, false, false},
        {true,  true,  true,  true,  true,  true,  true},
        {false, false, false, false, false, false, false},
        {true,  false, true,  false, true,  false, true},
    };

    private static void drawPixelIcon(NativeImage image, boolean[][] icon, int x, int y, int color, int pixScale) {
        for (int iy = 0; iy < icon.length; iy++) {
            for (int ix = 0; ix < icon[iy].length; ix++) {
                if (icon[iy][ix]) {
                    for (int sy = 0; sy < pixScale; sy++) {
                        for (int sx = 0; sx < pixScale; sx++) {
                            int px = x + ix * pixScale + sx;
                            int py = y + iy * pixScale + sy;
                            if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) {
                                image.setPixelRGBA(px, py, color);
                            }
                        }
                    }
                }
            }
        }
    }

    private DispatchBoardTextureManager() {
    }

    /** Brightens an opaque color toward white by {@code amount} (0..1). */
    private static int brightenColor(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.round(r + (255 - r) * amount);
        g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** A gently breathing version of a color, driven by the board frame clock. */
    private static int pulseColor(int argb, float speed, float amount) {
        float p = 0.5f + 0.5f * (float) Math.sin(currentFrameTime * speed);
        return brightenColor(argb, amount * p);
    }

    /** Draws a marching sheen of bright 1px stripes over a horizontal progress bar. */
    private static void drawBarSheen(NativeImage image, int x, int y, int fillW, int h, int baseColor, float speed) {
        if (fillW <= 4) {
            return;
        }
        int sheen = brightenColor(baseColor, 0.5f);
        int period = 11;
        int phase = (int) ((currentFrameTime * speed) % period);
        for (int sx = x + phase; sx < x + fillW; sx += period) {
            TextureGraphRenderer.fillRect(image, sx, y, 1, h, sheen);
        }
    }

    public static ResourceLocation getOrUpdateTexture(DispatchBoardBE be) {
        BlockPos pos = be.getBlockPos();
        TextureEntry entry = TEXTURES.get(pos);
        int mbW = be.getMultiblockWidth();
        int mbH = be.getMultiblockHeight();
        int texW = mbW * BASE_RESOLUTION;
        int texH = mbH * BASE_RESOLUTION;

        if (entry == null || entry.texWidth != texW || entry.texHeight != texH) {
            if (entry != null) {
                disposeEntry(entry);
                TEXTURES.remove(pos);
            }
            entry = createTextureEntry(pos, texW, texH);
            if (entry == null) {
                return null;
            }
            TEXTURES.put(pos, entry);
        }

        long currentTick = getGameTime();
        entry.lastAccessTick = currentTick;
        if (currentTick - entry.lastUpdateTick >= UPDATE_INTERVAL || entry.needsRedraw) {
            updateTexture(entry, be, currentTick);
            entry.lastUpdateTick = currentTick;
            entry.needsRedraw = false;
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStaleTextures(currentTick);
        }

        return entry.textureLocation;
    }

    public static void markDirty(BlockPos pos) {
        TextureEntry entry = TEXTURES.get(pos);
        if (entry != null) {
            entry.needsRedraw = true;
        }
    }

    public static void disposeTexture(BlockPos pos) {
        TextureEntry entry = TEXTURES.remove(pos);
        if (entry != null) {
            disposeEntry(entry);
        }
    }

    public static void disposeAll() {
        for (TextureEntry entry : TEXTURES.values()) {
            disposeEntry(entry);
        }
        TEXTURES.clear();
    }

    // ==================== Texture lifecycle ====================

    private static TextureEntry createTextureEntry(BlockPos pos, int w, int h) {
        try {
            NativeImage image = new NativeImage(w, h, false);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            String texName = "create_stats/dispatch_board_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register(texName, dynamicTexture);
            TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynamicTexture = dynamicTexture;
            entry.textureLocation = location;
            entry.texWidth = w;
            entry.texHeight = h;
            entry.lastAccessTick = getGameTime();
            entry.needsRedraw = true;
            return entry;
        } catch (Exception e) {
            CreateStats.LOGGER.warn("Failed to create Dispatch Board texture", e);
            return null;
        }
    }

    private static void updateTexture(TextureEntry entry, DispatchBoardBE be, long gameTime) {
        renderBoard(entry.image, be, gameTime);
        entry.dynamicTexture.upload();
    }

    // ==================== Main render entry ====================

    private static void renderBoard(NativeImage image, DispatchBoardBE be, long gameTime) {
        currentFrameTime = gameTime;
        int w = image.getWidth();
        int h = image.getHeight();

        // Full background fill
        TextureGraphRenderer.fillRect(image, 0, 0, w, h, BG_DARK);

        // Outer border frame, drawn by the texture, not the model
        int borderT = Math.max(2, Math.min(w, h) / 64);
        TextureGraphRenderer.drawBorder(image, 0, 0, w, h, borderT, BORDER_OUTER);
        TextureGraphRenderer.drawBorder(image, borderT, borderT, w - borderT * 2, h - borderT * 2, 1, BORDER_INNER);

        // Brass accent lines on top and bottom edges
        int margin = borderT + 2;
        int innerW = w - margin * 2;
        int innerH = h - margin * 2;
        TextureGraphRenderer.fillRect(image, margin, margin, innerW, innerH, BG_PANEL);
        TextureGraphRenderer.fillRect(image, margin, margin, innerW, 1, BRASS_ACCENT);
        TextureGraphRenderer.fillRect(image, margin, margin + innerH - 1, innerW, 1, BRASS_ACCENT);
        TextureGraphRenderer.fillRect(image, margin, margin, 1, innerH, BRASS_DIM);
        TextureGraphRenderer.fillRect(image, margin + innerW - 1, margin, 1, innerH, BRASS_DIM);

        DispatchBoardSnapshot snapshot = be.getBoardSnapshot();
        if (snapshot == null) {
            BitmapFont.drawCentered(image, "Initializing...", margin, margin + innerH / 2 - 4, innerW, TEXT_DIM, 1);
            return;
        }

        BoardTier tier = snapshot.getTier();
        int contentX = margin + 3;
        int contentY = margin + 3;
        int contentW = innerW - 6;
        int contentH = innerH - 6;

        switch (tier) {
            case MINI -> renderMini(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case COMPACT -> renderCompact(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case STANDARD -> renderStandard(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case RICH -> renderRich(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case FULL -> renderFull(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
        }
    }

    // ==================== MINI (1x1, 192x192) ====================

    private static void renderMini(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = 1;
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        // Unlinked empty state
        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "DISPATCH");
            return;
        }

        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;

        // Header: indicator bar + station name (full width)
        int barW = 3;
        TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 2, HEADER_BG);
        TextureGraphRenderer.fillRect(image, x, cy, barW, lineH + 2, indicatorColor);
        String stationName = truncate(snap.getStationName(), w / (6 * scale) - 4);
        BitmapFont.drawString(image, stationName, x + barW + 2, cy + 1, TEXT_BRIGHT, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy + 1, indicatorColor, scale);
        cy += lineH + 3;

        // Train + berth line
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
        cy += 2;
        if (snap.isTrainPresent()) {
            String tName = snap.getTrainName().isEmpty() ? "Train present" : truncate(snap.getTrainName(), w / (6 * scale) - 6);
            BitmapFont.drawString(image, tName, x + 1, cy, 0xFF74D87F, scale);
        } else if (snap.isArrivalTracking()) {
            BitmapFont.drawString(image, "Arriving...", x + 1, cy, 0xFF55CCFF, scale);
        } else {
            BitmapFont.drawString(image, "No train", x + 1, cy, TEXT_DIM, scale);
        }
        BitmapFont.drawRight(image, snap.getHeaderState().getDisplayName(), x + w - 1, cy, indicatorColor, scale);
        cy += lineH + 1;

        // Separator
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        // Dispatch rows, pack all that fit
        List<DispatchRowEntry> rows = snap.getVisibleRows();
        int barH = 4;
        int rowSpace = y + h - cy - barH - 3;
        int maxRows = Math.min(rows.size(), rowSpace / (lineH + 1));
        for (int i = 0; i < maxRows; i++) {
            int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
            TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
            drawDispatchRow(image, rows.get(i), x + 1, cy, w - 2, lineH, scale, false);
            cy += lineH + 1;
        }

        // Show "no orders" or "more" if relevant
        if (rows.isEmpty() && snap.getTotalRowCount() == 0) {
            BitmapFont.drawCentered(image, "No orders", x, cy, w, TEXT_DIM, scale);
            cy += lineH;
        } else if (snap.hasOverflowRows()) {
            BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rows.size()) + " more", x + w - 1, cy, TEXT_DIM, scale);
            cy += lineH;
        }

        // Progress bar at bottom
        drawProgressBar(image, snap, x + 1, y + h - barH - 1, w - 2, barH);
    }

    // ==================== COMPACT (1x2 / 2x1) ====================

    private static void renderCompact(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 160);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "DISPATCH BOARD");
            return;
        }

        // Header bar with accent
        int headerH = lineH + 4;
        drawHeaderBar(image, snap, x, cy, w, headerH, scale);
        cy += headerH + 1;

        // Summary strip
        drawSummaryStrip(image, snap, x, cy, w, lineH, scale);
        cy += lineH + 1;

        // Separator
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        // Exception strip reservation at bottom
        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int bottomReserved = hasExc ? lineH + 3 : 0;
        int barH = 4;
        int rowEndY = y + h - bottomReserved - barH - 2;

        // Dispatch rows, fill remaining space
        List<DispatchRowEntry> rows = snap.getVisibleRows();
        for (int i = 0; i < rows.size() && cy + lineH < rowEndY; i++) {
            int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
            TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
            drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true);
            cy += lineH + 1;
        }
        if (rows.isEmpty()) {
            BitmapFont.drawCentered(image, "No orders yet", x, cy, w, TEXT_DIM, scale);
            cy += lineH;
        } else if (snap.hasOverflowRows()) {
            BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rows.size()) + " more", x + w - 1, cy, TEXT_DIM, scale);
        }

        // Progress bar
        drawProgressBar(image, snap, x + 1, y + h - bottomReserved - barH - 1, w - 2, barH);

        // Exception strip at bottom
        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - lineH - 2, w, lineH + 1, scale);
        }
    }

    // ==================== STANDARD (2x2, first real wall) ====================

    private static void renderStandard(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 192);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "DISPATCH BOARD");
            return;
        }

        // Header zone (2 lines)
        int headerH = lineH * 2 + 6;
        drawHeaderZone(image, snap, x, cy, w, headerH, scale);
        cy += headerH + 1;

        // Summary strip
        drawSummaryStrip(image, snap, x, cy, w, lineH, scale);
        cy += lineH + 1;

        // Brass separator
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_ACCENT);
        cy += 2;

        // Reserve bottom for cargo + exception
        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int excH = hasExc ? lineH + 3 : 0;
        // Cargo gets a fixed block at the bottom
        int cargoEntryCount = Math.min(snap.getCargoSummary().size(), snap.getLayout().getCargoSummarySlots());
        int cargoH = 0;
        if (snap.getLayout().hasCargoRegion() && cargoEntryCount > 0) {
            cargoH = lineH * (cargoEntryCount + 1) + 6;
        }
        int histEntryCount = Math.min(snap.getVisibleHistory().size(), snap.getLayout().getHistoryEntryCapacity());
        int histH = 0;
        if (snap.getLayout().hasHistoryRegion() && histEntryCount > 0) {
            histH = lineH * (histEntryCount + 1) + 6;
        }
        int bottomH = Math.max(cargoH, histH);
        int rowEndY = y + h - excH - bottomH - 2;

        // Dispatch rows, fill main area
        List<DispatchRowEntry> rows = snap.getVisibleRows();
        for (int i = 0; i < rows.size() && cy + lineH < rowEndY; i++) {
            int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
            TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
            drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true);
            cy += lineH + 1;
            TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
            cy += 1;
        }
        if (rows.isEmpty()) {
            BitmapFont.drawCentered(image, "No dispatch orders", x, cy + lineH, w, TEXT_DIM, scale);
        } else if (snap.hasOverflowRows()) {
            BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rows.size()) + " more", x + w - 1, cy, TEXT_DIM, scale);
        }

        // Bottom widgets: cargo (left) + history (right), side by side
        if (bottomH > 0) {
            cy = y + h - excH - bottomH;
            TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
            cy += 2;
            if (cargoH > 0 && histH > 0) {
                int halfW = (w - 4) / 2;
                drawCargoSummary(image, snap, x, cy, halfW, bottomH - 3, scale);
                drawHistory(image, snap, x + halfW + 4, cy, halfW, bottomH - 3, scale);
            } else if (cargoH > 0) {
                drawCargoSummary(image, snap, x, cy, w, bottomH - 3, scale);
            } else {
                drawHistory(image, snap, x, cy, w, bottomH - 3, scale);
            }
        }

        // Exception strip
        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - lineH - 2, w, lineH + 1, scale);
        }
    }

    // ==================== RICH (3x3, full operations wall) ====================

    private static void renderRich(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 192);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "STATION DISPATCH BOARD");
            return;
        }

        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int excH = hasExc ? lineH + 4 : 0;
        int progressH = 5;

        // ── 1. HEADER (compact 2 lines) ──
        int headerH = lineH * 2 + 6;
        drawHeaderZone(image, snap, x, cy, w, headerH, scale);
        cy += headerH;
        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        // ── 2. TRAIN DETAIL PANEL ──
        int trainPanelH = drawTrainDetailPanel(image, snap, x, cy, w, lineH, scale);
        cy += trainPanelH;
        if (trainPanelH > 0) {
            TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_ACCENT);
            cy += 3;
        }

        // ── 3. DISPATCH ORDERS (adaptive) ──
        drawPixelIcon(image, ICON_CARGO, x + 2, cy + 1, SECTION_HEADER, scale);
        int iconW = 7 * scale + 4;
        BitmapFont.drawString(image, "ORDERS (" + snap.getTotalRowCount() + ")", x + 2 + iconW, cy, SECTION_HEADER, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy, TEXT_DIM, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        int bottomReserved = excH + progressH + 6;
        int widgetMinH = lineH * 5;
        int maxRowPixels = (y + h) - cy - bottomReserved - widgetMinH;
        int maxRows = Math.max(1, maxRowPixels / (lineH + 2));
        int rowsToDraw = Math.min(rows.size(), maxRows);

        if (rows.isEmpty()) {
            BitmapFont.drawString(image, "No dispatch orders", x + 4, cy, TEXT_DIM, scale);
            cy += lineH + 2;
        } else {
            for (int i = 0; i < rowsToDraw; i++) {
                int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
                TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
                drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true);
                cy += lineH + 1;
                TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
                cy += 1;
            }
            if (snap.hasOverflowRows()) {
                BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rowsToDraw) + " more",
                        x + w - 1, cy, TEXT_DIM, scale);
                cy += lineH;
            }
        }

        // ── 4. BOTTOM WIDGETS (3 columns: cargo | history | roster/visits) ──
        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        int widgetEndY = y + h - excH - progressH - 2;
        int widgetH = widgetEndY - cy;
        int thirdW = (w - 10) / 3;

        drawSectionWithIcon(image, "CARGO", ICON_CARGO, x, cy, thirdW, widgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawCargoContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + thirdW + 3, cy, 1, widgetH, BRASS_DIM);
        drawSectionWithIcon(image, "HISTORY", ICON_CLOCK, x + thirdW + 6, cy, thirdW, widgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawHistoryContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + thirdW * 2 + 7, cy, 1, widgetH, BRASS_DIM);

        // Right third: roster if available, else visits
        if (snap.getLayout().hasRosterRegion() && !snap.getRosterEntries().isEmpty()) {
            drawSectionWithIcon(image, "TRAINS", ICON_TRAIN, x + thirdW * 2 + 10, cy, thirdW, widgetH, scale,
                    (img, sx, sy, sw, sh, sc) -> drawTrainRosterContent(img, snap, sx, sy, sw, sh, sc));
        } else {
            drawSectionWithIcon(image, "VISITS", ICON_TRACKS, x + thirdW * 2 + 10, cy, thirdW, widgetH, scale,
                    (img, sx, sy, sw, sh, sc) -> drawVisitsContent(img, snap, sx, sy, sw, sh, sc));
        }

        // ── 5. EXCEPTION STRIP ──
        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - excH - progressH - 1, w, lineH + 2, scale);
        }

        // ── 6. PROGRESS BAR ──
        drawProgressBar(image, snap, x + 1, y + h - progressH - 1, w - 2, progressH);
    }

    // ==================== FULL (4x4+, station intelligence wall) ====================

    private static void renderFull(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 256);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "STATION DISPATCH BOARD");
            return;
        }

        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int excH = hasExc ? lineH + 4 : 0;
        int progressH = 6;

        // ── 1. HEADER (compact 2 lines) ──
        int headerH = lineH * 2 + 6;
        drawHeaderZone(image, snap, x, cy, w, headerH, scale);
        cy += headerH;
        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        // ── 2. TRAIN DETAIL PANEL ──
        int trainPanelH = drawTrainDetailPanel(image, snap, x, cy, w, lineH, scale);
        cy += trainPanelH;
        if (trainPanelH > 0) {
            TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
            cy += 4;
        }

        // ── 3. DISPATCH ORDERS (adaptive, only rows that exist) ──
        drawPixelIcon(image, ICON_CARGO, x + 2, cy + 1, SECTION_HEADER, scale);
        int iconW = 7 * scale + 4;
        BitmapFont.drawString(image, "ORDERS (" + snap.getTotalRowCount() + ")", x + 2 + iconW, cy, SECTION_HEADER, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy, TEXT_DIM, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        int bottomReserved = excH + progressH + 8;
        int widgetMinH = lineH * 6; // minimum 6 lines for bottom widgets
        int maxRowPixels = (y + h) - cy - bottomReserved - widgetMinH;
        int maxRows = Math.max(1, maxRowPixels / (lineH + 2));
        int rowsToDraw = Math.min(rows.size(), maxRows);

        if (rows.isEmpty()) {
            BitmapFont.drawString(image, "No dispatch orders", x + 4, cy, TEXT_DIM, scale);
            cy += lineH + 2;
        } else {
            for (int i = 0; i < rowsToDraw; i++) {
                int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
                TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
                drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true);
                cy += lineH + 1;
                TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
                cy += 1;
            }
            if (snap.hasOverflowRows()) {
                BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rowsToDraw) + " more",
                        x + w - 1, cy, TEXT_DIM, scale);
                cy += lineH;
            }
        }

        // ── 4. BOTTOM WIDGETS (fill remaining space in 2×2 grid) ──
        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        int widgetEndY = y + h - excH - progressH - 2;
        int widgetTotalH = widgetEndY - cy;
        int halfW = (w - 6) / 2;
        int halfH = (widgetTotalH - 4) / 2;

        // Top row: CARGO (left) + TRAINS (right)
        drawSectionWithIcon(image, "CARGO", ICON_CARGO, x, cy, halfW, halfH, scale,
                (img, sx, sy, sw, sh, sc) -> drawCargoContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + halfW + 2, cy, 1, halfH, BRASS_DIM);
        drawSectionWithIcon(image, "TRAINS", ICON_TRAIN, x + halfW + 5, cy, halfW, halfH, scale,
                (img, sx, sy, sw, sh, sc) -> drawTrainRosterContent(img, snap, sx, sy, sw, sh, sc));

        cy += halfH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        // Bottom row: HISTORY (left) + VISITS (right)
        int bottomWidgetH = widgetEndY - cy;
        drawSectionWithIcon(image, "HISTORY", ICON_CLOCK, x, cy, halfW, bottomWidgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawHistoryContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + halfW + 2, cy, 1, bottomWidgetH, BRASS_DIM);
        drawSectionWithIcon(image, "VISITS", ICON_TRACKS, x + halfW + 5, cy, halfW, bottomWidgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawVisitsContent(img, snap, sx, sy, sw, sh, sc));

        // ── 5. EXCEPTION STRIP ──
        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - excH - progressH - 1, w, lineH + 2, scale);
        }

        // ── 6. PROGRESS BAR ──
        drawProgressBar(image, snap, x + 1, y + h - progressH - 1, w - 2, progressH);
    }

    // ==================== Helper renderers ====================

    private static void renderEmptyState(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale, String title) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int centerY = y + h / 2;

        // Dimmed placeholder grid lines
        int gridSpacing = 24 * scale;
        for (int gx = x + gridSpacing; gx < x + w; gx += gridSpacing) {
            for (int gy = y; gy < y + h; gy++) {
                if (gx >= 0 && gx < image.getWidth() && gy >= 0 && gy < image.getHeight()) {
                    image.setPixelRGBA(gx, gy, 0xFF2A2520);
                }
            }
        }
        for (int gy = y + gridSpacing; gy < y + h; gy += gridSpacing) {
            for (int gx = x; gx < x + w; gx++) {
                if (gx >= 0 && gx < image.getWidth() && gy >= 0 && gy < image.getHeight()) {
                    image.setPixelRGBA(gx, gy, 0xFF2A2520);
                }
            }
        }

        // Centered title
        BitmapFont.drawCentered(image, title, x, centerY - lineH * 2, w, BRASS_ACCENT, scale);

        // Link instruction
        BitmapFont.drawCentered(image, "Link to station", x, centerY - lineH / 2, w, TEXT_DIM, scale);
        BitmapFont.drawCentered(image, "with clipboard", x, centerY + lineH, w, TEXT_DIM, scale);
    }

    private static void drawProgressBar(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h) {
        // Background track
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xFF3A3228);

        if (snap.getTotalRowCount() > 0) {
            int done = snap.getCompletedRowCount() + snap.getDismissedRowCount();
            float pct = (float) done / snap.getTotalRowCount();
            int fillW = Math.round(w * pct);
            if (fillW > 0) {
                int barColor = pct >= 1.0f ? 0xFF74D87F : 0xFFFFCC00;
                TextureGraphRenderer.fillRect(image, x, y, fillW, h, barColor);
                if (pct < 1.0f) {
                    drawBarSheen(image, x, y, fillW, h, barColor, 1.4f);
                }
            }
        }

        // Thin top edge accent
        TextureGraphRenderer.fillRect(image, x, y, w, 1, BRASS_DIM);
    }

    private static void drawHeaderZoneFull(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, HEADER_BG);
        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;
        int lineH = BitmapFont.textHeight(scale) + 2;

        // Left indicator bar
        int barW = Math.max(4, 5 * scale);
        TextureGraphRenderer.fillRect(image, x, y, barW, h, indicatorColor);

        int textX = x + barW + 3;
        int cy = y + 2;
        int maxChars = (w - barW - 6) / (6 * scale);

        // Line 1: Station name (large)
        String stationName = truncate(snap.getStationName(), maxChars);
        BitmapFont.drawString(image, stationName, textX, cy, TEXT_BRIGHT, scale);
        cy += lineH;

        // Line 2: Train + berth
        if (snap.isTrainPresent()) {
            String trainLabel = snap.getTrainName().isEmpty() ? "Train present" : truncate(snap.getTrainName(), maxChars / 2);
            BitmapFont.drawString(image, trainLabel, textX, cy, 0xFF74D87F, scale);
        } else if (snap.isArrivalTracking()) {
            BitmapFont.drawString(image, "Arriving...", textX, cy, 0xFF55CCFF, scale);
        } else {
            BitmapFont.drawString(image, "No train", textX, cy, TEXT_DIM, scale);
        }
        cy += lineH;

        // Line 3: Berth state + completion + additional metadata
        BitmapFont.drawString(image, snap.getHeaderState().getDisplayName(), textX, cy, indicatorColor, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy, TEXT_PRIMARY, scale);

        // Right-aligned header labels
        BitmapFont.drawRight(image, snap.getHeaderState().getDisplayName(), x + w - 1, y + 2, indicatorColor, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, y + 2 + lineH, TEXT_PRIMARY, scale);
    }

    /**
     * Draws a prominent train detail panel with pixel-art icon, speed, carriages,
     * destination, schedule stops, and current status.
     * Returns the total pixel height consumed (0 if nothing to show).
     */
    private static int drawTrainDetailPanel(NativeImage image, DispatchBoardSnapshot snap,
                                             int x, int y, int w, int lineH, int scale) {
        if (!snap.isTrainPresent() && !snap.isArrivalTracking()) {
            // No train, show a minimal "awaiting" line
            TextureGraphRenderer.fillRect(image, x, y, w, lineH + 4, TRAIN_PANEL_BG);
            drawPixelIcon(image, ICON_TRAIN, x + 4, y + 2, TEXT_DIM, scale);
            int iconW = 9 * scale + 6;
            BitmapFont.drawString(image, "Awaiting train", x + iconW, y + 2, TEXT_DIM, scale);
            return lineH + 6;
        }

        // Train present or arriving, build detail panel
        List<TrainSummarySnapshot> roster = snap.getRosterEntries();
        TrainSummarySnapshot train = !roster.isEmpty() ? roster.get(0) : null;

        int cy = y;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        // Panel background
        int panelStartY = cy;

        // Line 1: Train icon + name (large, colored)
        int trainColor = snap.isTrainPresent() ? TRAIN_DOCKED_COLOR : TRAIN_ENROUTE_COLOR;
        drawPixelIcon(image, ICON_TRAIN, x + 4, cy + 1, trainColor, scale);
        int iconW = 9 * scale + 6;
        String trainName = snap.getTrainName().isEmpty() ? "Train" : snap.getTrainName();
        BitmapFont.drawString(image, trainName, x + iconW, cy, trainColor, scale);
        if (train != null && train.isWaitingForSignal()) {
            BitmapFont.drawRight(image, "SIGNAL WAIT", x + w - 1, cy, pulseColor(TRAIN_SIGNAL_COLOR, 0.6f, 0.5f), scale);
        } else if (snap.isTrainPresent()) {
            BitmapFont.drawRight(image, "DOCKED", x + w - 1, cy, pulseColor(TRAIN_DOCKED_COLOR, 0.3f, 0.4f), scale);
        } else {
            BitmapFont.drawRight(image, "ARRIVING", x + w - 1, cy, pulseColor(TRAIN_ENROUTE_COLOR, 0.5f, 0.5f), scale);
        }
        cy += lineH + 1;

        if (train != null) {
            // Line 2: Carriages | Speed | Destination
            int cx = x + iconW;
            cx += BitmapFont.drawString(image, train.getCarriageCount() + " cars", cx, cy, TEXT_PRIMARY, scale);
            cx += 4 * scale;
            TextureGraphRenderer.fillRect(image, cx, cy + 2, 1, lineH - 4, BRASS_DIM);
            cx += 4 * scale;
            String speedStr = String.format("%.1f m/t", train.getSpeed());
            cx += BitmapFont.drawString(image, speedStr, cx, cy, TEXT_PRIMARY, scale);
            if (!train.getNextDestination().isEmpty()) {
                cx += 4 * scale;
                TextureGraphRenderer.fillRect(image, cx, cy + 2, 1, lineH - 4, BRASS_DIM);
                cx += 4 * scale;
                BitmapFont.drawString(image, "> " + truncate(train.getNextDestination(), 16), cx, cy, TRAIN_ENROUTE_COLOR, scale);
            }
            cy += lineH;

            // Line 3: Current station
            if (!train.getCurrentStationName().isEmpty()) {
                BitmapFont.drawString(image, "At: " + truncate(train.getCurrentStationName(), 24), x + iconW, cy, TEXT_DIM, scale);
                cy += lineH;
            }

            // Line 4: Schedule stops (if available)
            List<String> stops = train.getScheduleStops();
            if (!stops.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int maxChars = (w - iconW - 4) / (6 * scale);
                for (int i = 0; i < stops.size(); i++) {
                    String stop = stops.get(i);
                    String addition = (i == 0 ? "" : " > ") + stop;
                    if (sb.length() + addition.length() > maxChars) {
                        sb.append(" (+").append(stops.size() - i).append(")");
                        break;
                    }
                    sb.append(addition);
                }
                BitmapFont.drawString(image, sb.toString(), x + iconW, cy, TEXT_DIM, scale);
                cy += lineH;
            }
        }

        cy += 2;

        // Fill panel background behind everything we drew
        int panelH = cy - panelStartY;
        // Draw background UNDER text by re-filling then re-drawing would be wasteful;
        // instead just draw a subtle left accent bar that breathes while a train is around
        TextureGraphRenderer.fillRect(image, x, panelStartY, 3 * scale, panelH, pulseColor(trainColor, 0.4f, 0.35f));

        return cy - y;
    }

    @FunctionalInterface
    private interface SectionContentRenderer {
        void render(NativeImage image, int x, int y, int w, int h, int scale);
    }

    private static void drawSectionWithIcon(NativeImage image, String title, boolean[][] icon,
                                             int x, int y, int w, int h, int scale,
                                             SectionContentRenderer contentRenderer) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        // Section header with icon
        int iconW = icon[0].length * scale;
        drawPixelIcon(image, icon, x + 2, cy + 1, SECTION_HEADER, scale);
        BitmapFont.drawString(image, title, x + iconW + 5, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        // Content
        int contentH = y + h - cy;
        if (contentH > 0) {
            contentRenderer.render(image, x, cy, w, contentH, scale);
        }
    }

    private static void drawCargoContent(NativeImage image, DispatchBoardSnapshot snap,
                                          int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<CargoSummaryEntry> entries = snap.getCargoSummary();
        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No cargo", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            CargoSummaryEntry entry = entries.get(i);
            String label = truncate(entry.getCompactLabel(), w / (6 * scale));
            BitmapFont.drawString(image, label, x + 2, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }
    }

    private static void drawHistoryContent(NativeImage image, DispatchBoardSnapshot snap,
                                            int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<DeliveryOrderHistoryEntry> entries = snap.getVisibleHistory();
        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No events", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            DeliveryOrderHistoryEntry entry = entries.get(i);
            int typeColor = switch (entry.getType()) {
                case COMPLETED, MANUAL_COMPLETE -> 0xFF74D87F;
                case AUTO_RESET, MANUAL_RESET -> 0xFF9999FF;
                case HIDDEN -> TEXT_DIM;
                case SHOWN -> TEXT_PRIMARY;
            };
            int dotSize = Math.max(3, 4 * scale);
            TextureGraphRenderer.fillRect(image, x + 2, cy + 2, dotSize, dotSize, typeColor);
            String title = truncate(entry.getTitle(), (w - dotSize - 6) / (6 * scale));
            BitmapFont.drawString(image, title, x + dotSize + 5, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }
    }

    private static void drawTrainRosterContent(NativeImage image, DispatchBoardSnapshot snap,
                                                int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<TrainSummarySnapshot> roster = snap.getRosterEntries();
        if (roster.isEmpty()) {
            BitmapFont.drawString(image, "No trains", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < roster.size() && cy + lineH * 2 < y + h; i++) {
            TrainSummarySnapshot train = roster.get(i);
            int trainColor = train.isStoppedAtStation() ? TRAIN_DOCKED_COLOR
                    : train.isWaitingForSignal() ? TRAIN_SIGNAL_COLOR : TEXT_PRIMARY;
            // Mini train icon for each entry
            drawPixelIcon(image, ICON_TRAIN, x + 2, cy + 1, trainColor, Math.max(1, scale - 1));
            int miniIconW = 9 * Math.max(1, scale - 1) + 4;
            String trainName = truncate(train.getTrainName(), (w - miniIconW - 2) / (6 * scale));
            BitmapFont.drawString(image, trainName, x + miniIconW + 2, cy, trainColor, scale);
            cy += lineH;
            // Detail line: carriages + destination
            String detail = train.getCarriageCount() + " cars";
            if (!train.getNextDestination().isEmpty()) {
                detail += " > " + truncate(train.getNextDestination(), 10);
            }
            BitmapFont.drawString(image, detail, x + miniIconW + 2, cy, TEXT_DIM, scale);
            cy += lineH + 1;
        }
    }

    private static void drawVisitsContent(NativeImage image, DispatchBoardSnapshot snap,
                                           int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<TrainVisitRecord> visits = snap.getVisibleVisits();
        if (visits.isEmpty()) {
            BitmapFont.drawString(image, "No visits", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < visits.size() && cy + lineH < y + h; i++) {
            TrainVisitRecord visit = visits.get(i);
            int visitColor = visit.isStillPresent() ? TRAIN_DOCKED_COLOR : TEXT_DIM;
            String bullet = visit.isStillPresent() ? ">" : "-";
            BitmapFont.drawString(image, bullet, x + 2, cy, visitColor, scale);
            String label = truncate(visit.getTrainName(), (w - 8 * scale) / (6 * scale));
            BitmapFont.drawString(image, label, x + 2 + 6 * scale, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }
    }

    private static void drawHeaderBar(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, HEADER_BG);
        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;

        // Left indicator bar
        int barW = Math.max(2, 3 * scale);
        TextureGraphRenderer.fillRect(image, x, y, barW, h, indicatorColor);

        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int textX = x + barW + 2;
        int maxChars = (w - barW - 4) / (6 * scale);
        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            BitmapFont.drawString(image, "Unlinked", textX, textY, TEXT_DIM, scale);
        } else {
            String compLabel = snap.getCompletionLabel();
            int compW = BitmapFont.measureString(compLabel, scale);
            int nameMax = (w - barW - compW - 8) / (6 * scale);
            String name = truncate(snap.getStationName(), Math.max(6, nameMax));
            BitmapFont.drawString(image, name, textX, textY, TEXT_BRIGHT, scale);
            BitmapFont.drawRight(image, compLabel, x + w - 1, textY, indicatorColor, scale);
        }
    }

    private static void drawHeaderZone(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, HEADER_BG);
        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;
        int lineH = BitmapFont.textHeight(scale) + 2;

        // Left indicator bar
        int barW = Math.max(3, 4 * scale);
        TextureGraphRenderer.fillRect(image, x, y, barW, h, indicatorColor);

        int textX = x + barW + 3;
        int cy = y + 2;
        int maxChars = (w - barW - 6) / (6 * scale);

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            BitmapFont.drawString(image, "Dispatch Board", textX, cy, BRASS_ACCENT, scale);
            cy += lineH;
            BitmapFont.drawString(image, "Not linked to a station", textX, cy, TEXT_DIM, scale);
            return;
        }

        // Station name
        String stationName = truncate(snap.getStationName(), maxChars);
        BitmapFont.drawString(image, stationName, textX, cy, TEXT_BRIGHT, scale);

        // Train status
        cy += lineH;
        if (snap.isTrainPresent()) {
            String trainLabel = snap.getTrainName().isEmpty() ? "Train present" : truncate(snap.getTrainName(), maxChars / 2);
            BitmapFont.drawString(image, trainLabel, textX, cy, 0xFF74D87F, scale);
        } else if (snap.isArrivalTracking()) {
            BitmapFont.drawString(image, "Arriving...", textX, cy, 0xFF55CCFF, scale);
        } else {
            BitmapFont.drawString(image, "No train", textX, cy, TEXT_DIM, scale);
        }

        // Right-aligned berth badge + completion
        BitmapFont.drawRight(image, snap.getHeaderState().getDisplayName(), x + w - 1, y + 2, indicatorColor, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, y + 2 + lineH, TEXT_PRIMARY, scale);
    }

    private static void drawSummaryStrip(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int cx = x + 1;
        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int gap = 4 * scale;

        int active = snap.countRowsInState(DispatchRowState.IN_PROGRESS);
        int waiting = snap.countRowsInState(DispatchRowState.WAITING);
        int complete = snap.countRowsInState(DispatchRowState.COMPLETE);
        int invalid = snap.countRowsInState(DispatchRowState.INVALID) + snap.countRowsInState(DispatchRowState.NEEDS_SETUP);

        if (active > 0) {
            cx += BitmapFont.drawString(image, active + " active", cx, textY, 0xFFFFCC00, scale) + gap;
        }
        if (waiting > 0) {
            cx += BitmapFont.drawString(image, waiting + " wait", cx, textY, TEXT_DIM, scale) + gap;
        }
        if (complete > 0) {
            cx += BitmapFont.drawString(image, complete + " done", cx, textY, 0xFF74D87F, scale) + gap;
        }
        if (invalid > 0) {
            BitmapFont.drawString(image, invalid + " err", cx, textY, 0xFFFF5555, scale);
        }
    }

    private static void drawDispatchRow(NativeImage image, DispatchRowEntry row, int x, int y, int w, int h, int scale, boolean showDetail) {
        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int stateColor = row.getStateColor() | 0xFF000000;
        int dotSize = Math.max(3, 4 * scale);

        // State color dot
        TextureGraphRenderer.fillRect(image, x, textY + 1, dotSize, dotSize, stateColor);
        int cx = x + dotSize + 2 * scale;

        // Operation label (Load/Unload)
        String opLabel = row.getOperation() == ManifestOperation.LOAD ? "L" : "U";
        int opColor = row.getOperation() == ManifestOperation.LOAD ? 0xFF88AAFF : 0xFFFFAA66;
        cx += BitmapFont.drawString(image, opLabel, cx, textY, opColor, scale);
        cx += scale;

        // Compute available space for name vs progress bar
        int rightReserved = showDetail ? 50 * scale : 24 * scale;
        int nameSpace = w - (cx - x) - rightReserved;
        int maxNameChars = Math.max(4, nameSpace / (6 * scale));
        String cargoName = truncate(row.getDisplayName(), maxNameChars);
        cx += BitmapFont.drawString(image, cargoName, cx, textY, TEXT_PRIMARY, scale);
        cx += 3 * scale;

        // Progress bar
        if (row.getTargetAmount() > 0) {
            int progressTextW = BitmapFont.measureString(row.getDeliveredAmount() + "/" + row.getTargetAmount(), scale);
            int badgeW = 0;
            if (showDetail && !row.getResetBadge().isEmpty()) {
                badgeW = BitmapFont.measureString(row.getResetBadge(), scale) + 4 * scale;
            }
            int barMaxW = Math.max(12, x + w - cx - progressTextW - badgeW - 4 * scale);
            int barH = Math.max(2, 3 * scale);
            int barY = textY + (BitmapFont.textHeight(scale) - barH) / 2;
            TextureGraphRenderer.fillRect(image, cx, barY, barMaxW, barH, 0xFF3A3228);
            int fillW = Math.round(barMaxW * row.getProgress());
            if (fillW > 0) {
                TextureGraphRenderer.fillRect(image, cx, barY, fillW, barH, stateColor);
                if (row.getState() == DispatchRowState.IN_PROGRESS) {
                    drawBarSheen(image, cx, barY, fillW, barH, stateColor, 1.3f);
                }
            }
            cx += barMaxW + 2 * scale;

            String progressText = row.getDeliveredAmount() + "/" + row.getTargetAmount();
            BitmapFont.drawString(image, progressText, cx, textY, TEXT_DIM, scale);
        }

        // Reset badge (far right)
        if (showDetail) {
            String badge = row.getResetBadge();
            if (!badge.isEmpty()) {
                BitmapFont.drawRight(image, badge, x + w, textY, BRASS_DIM, scale);
            }
        }
    }

    private static void drawCargoSummary(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        // Section header
        BitmapFont.drawString(image, "CARGO", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<CargoSummaryEntry> entries = snap.getCargoSummary();
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            CargoSummaryEntry entry = entries.get(i);
            String label = truncate(entry.getCompactLabel(), w / (6 * scale));
            BitmapFont.drawString(image, label, x + 2, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }

        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No cargo", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawHistory(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        // Section header
        BitmapFont.drawString(image, "HISTORY", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DeliveryOrderHistoryEntry> entries = snap.getVisibleHistory();
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            DeliveryOrderHistoryEntry entry = entries.get(i);
            int typeColor = switch (entry.getType()) {
                case COMPLETED, MANUAL_COMPLETE -> 0xFF74D87F;
                case AUTO_RESET, MANUAL_RESET -> 0xFF9999FF;
                case HIDDEN -> TEXT_DIM;
                case SHOWN -> TEXT_PRIMARY;
            };
            String title = truncate(entry.getTitle(), w / (6 * scale) - 4);
            BitmapFont.drawString(image, "*", x + 2, cy, typeColor, scale);
            BitmapFont.drawString(image, title, x + 2 + 6 * scale, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }

        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No events", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawTrainRoster(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        // Section header
        BitmapFont.drawString(image, "TRAINS", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<TrainSummarySnapshot> roster = snap.getRosterEntries();
        for (int i = 0; i < roster.size() && cy + lineH * 2 < y + h; i++) {
            TrainSummarySnapshot train = roster.get(i);

            // Train name with status indicator
            int trainColor = train.isStoppedAtStation() ? 0xFF74D87F
                    : train.isWaitingForSignal() ? 0xFFFFCC00 : TEXT_PRIMARY;
            String trainName = truncate(train.getTrainName(), w / (6 * scale) - 2);
            BitmapFont.drawString(image, trainName, x + 2, cy, trainColor, scale);
            cy += lineH;

            // Carriages + destination
            String detail = train.getCarriageCount() + " cars";
            if (!train.getNextDestination().isEmpty()) {
                detail += " > " + truncate(train.getNextDestination(), 10);
            }
            BitmapFont.drawString(image, detail, x + 8, cy, TEXT_DIM, scale);
            cy += lineH;
        }

        if (roster.isEmpty()) {
            BitmapFont.drawString(image, "No trains", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawVisitTimeline(NativeImage image, List<TrainVisitRecord> visits, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        // Section header
        BitmapFont.drawString(image, "VISITS", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        for (int i = 0; i < visits.size() && cy + lineH < y + h; i++) {
            TrainVisitRecord visit = visits.get(i);
            int visitColor = visit.isStillPresent() ? 0xFF74D87F : TEXT_DIM;
            String label = truncate(visit.getTrainName(), w / (6 * scale) - 6);

            // Bullet + train name
            BitmapFont.drawString(image, visit.isStillPresent() ? ">" : "-", x + 2, cy, visitColor, scale);
            BitmapFont.drawString(image, label, x + 2 + 6 * scale, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }

        if (visits.isEmpty()) {
            BitmapFont.drawString(image, "No visits", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawExceptionStrip(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xFF3A1010);
        TextureGraphRenderer.fillRect(image, x, y, w, 1, 0xFFFF5555);

        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int invalid = snap.countRowsInState(DispatchRowState.INVALID);
        int setup = snap.countRowsInState(DispatchRowState.NEEDS_SETUP);
        String msg = "";
        if (invalid > 0) msg += invalid + " invalid";
        if (setup > 0) msg += (msg.isEmpty() ? "" : " | ") + setup + " need setup";
        if (msg.isEmpty()) msg = "Exceptions present";
        BitmapFont.drawCentered(image, "! " + msg, x, textY, w, 0xFFFF8888, scale);
    }

    // ==================== Utility ====================

    private static String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    // ==================== Lifecycle management ====================

    private static void cleanupStaleTextures(long currentTick) {
        Iterator<Map.Entry<BlockPos, TextureEntry>> iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, TextureEntry> e = iterator.next();
            if (currentTick - e.getValue().lastAccessTick > MAX_IDLE_TICKS) {
                disposeEntry(e.getValue());
                iterator.remove();
            }
        }
    }

    private static void disposeEntry(TextureEntry entry) {
        try {
            if (entry.dynamicTexture != null) {
                entry.dynamicTexture.close();
            }
        } catch (RuntimeException ex) {
            CreateStats.LOGGER.warn("Failed to dispose Dispatch Board texture", ex);
        }
    }

    private static long getGameTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    private static class TextureEntry {
        private NativeImage image;
        private DynamicTexture dynamicTexture;
        private ResourceLocation textureLocation;
        private int texWidth;
        private int texHeight;
        private long lastUpdateTick;
        private long lastAccessTick;
        private boolean needsRedraw;
    }
}
