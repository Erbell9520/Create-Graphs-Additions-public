package com.createstats.renderer;

import com.createstats.blockentity.ProductionDetectorBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ProductionDetectorTextureManager {
    private static final int TEXTURE_WIDTH = 224;
    private static final int TEXTURE_HEIGHT = 220;
    private static final int BG = 0xFF121719;
    private static final int PANEL = 0xFF0B2E30;
    private static final int PANEL_DARK = 0xFF062022;
    private static final int FRAME = 0xFF22292A;
    private static final int BRASS = 0xFFC69B4A;
    private static final int BRASS_DARK = 0xFF6D5122;
    private static final int TEXT = 0xFFF4E6C7;
    private static final int MUTED = 0xFFB8A37F;
    private static final int CYAN = 0xFF42E3D4;
    private static final int GOOD = 0xFF58D575;
    private static final int ENERGY = 0xFFE6A342;
    private static final int ENERGY_BLUE = 0xFF66A8FF;
    private static final int EMPTY = 0xFF78919B;
    private static final int WARN = 0xFFD6A84F;
    private static final float ENERGY_FLOW_EPSILON = 0.01f;
    private static final Map<ResourceLocation, TextureEntry> ACTIVE_TEXTURES = new HashMap<>();

    private ProductionDetectorTextureManager() {
    }

    public static ResourceLocation getOrUpdateTexture(ProductionDetectorBE be) {
        final ResourceLocation key = ResourceLocation.fromNamespaceAndPath("create_stats", "prod_detector/" + be.getBlockPos().asLong());
        TextureEntry entry = ACTIVE_TEXTURES.get(key);
        final int newHash = be.getRenderDataHash();
        if (entry == null) {
            entry = createTexture(key, be, newHash);
            ACTIVE_TEXTURES.put(key, entry);
        } else if (entry.renderHash != newHash) {
            entry.renderHash = newHash;
            updateTexture(entry, be);
        }
        entry.lastUseTick = nowTicks();
        return key;
    }

    public static void cleanupExpired() {
        final long now = nowTicks();
        final Iterator<Map.Entry<ResourceLocation, TextureEntry>> iterator = ACTIVE_TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<ResourceLocation, TextureEntry> mapEntry = iterator.next();
            if (now - mapEntry.getValue().lastUseTick > 200L) {
                mapEntry.getValue().close();
                iterator.remove();
            }
        }
    }

    public static void cleanupForWorld(LevelAccessor level) {
        final List<ResourceLocation> toRemove = new ArrayList<>();
        for (Map.Entry<ResourceLocation, TextureEntry> mapEntry : ACTIVE_TEXTURES.entrySet()) {
            if (mapEntry.getValue().world == level) {
                mapEntry.getValue().close();
                toRemove.add(mapEntry.getKey());
            }
        }
        for (ResourceLocation location : toRemove) {
            ACTIVE_TEXTURES.remove(location);
        }
    }

    public static void cleanupAll() {
        for (TextureEntry entry : ACTIVE_TEXTURES.values()) {
            entry.close();
        }
        ACTIVE_TEXTURES.clear();
    }

    public static void cleanup(BlockPos pos) {
        final ResourceLocation key = ResourceLocation.fromNamespaceAndPath("create_stats", "prod_detector/" + pos.asLong());
        final TextureEntry entry = ACTIVE_TEXTURES.remove(key);
        if (entry != null) {
            entry.close();
        }
    }

    private static TextureEntry createTexture(ResourceLocation key, ProductionDetectorBE be, int renderHash) {
        final NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEXTURE_WIDTH, TEXTURE_HEIGHT, false);
        final DynamicTexture texture = new DynamicTexture(image);
        final TextureEntry entry = new TextureEntry(be.getLevel(), texture, renderHash, nowTicks());
        updateTexture(entry, be);
        Minecraft.getInstance().getTextureManager().register(key, texture);
        return entry;
    }

    private static void updateTexture(TextureEntry entry, ProductionDetectorBE be) {
        final DynamicTexture texture = entry.texture;
        final NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }

        TextureGraphRenderer.fillRect(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, BG);

        switch (be.getDisplayMode()) {
            case DISPLAY_LINK_FEED -> drawDisplayLinkBoard(image, be);
            case FLUID -> drawFluidBoard(image, be);
            case ENERGY -> drawEnergyBoard(image, be);
            case DIESEL -> drawDieselBoard(image, be);
            default -> drawInventoryBoard(image, be);
        }

        texture.upload();
    }

    private static void drawFluidBoard(NativeImage image, ProductionDetectorBE be) {
        TextureGraphRenderer.fillRect(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, FRAME);
        TextureGraphRenderer.drawBorder(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, 4, BRASS_DARK);
        TextureGraphRenderer.drawBorder(image, 5, 5, TEXTURE_WIDTH - 10, TEXTURE_HEIGHT - 10, 2, BRASS);
        drawCornerCaps(image);

        final int screenX = 16;
        final int screenY = 16;
        final int screenW = TEXTURE_WIDTH - 32;
        final int screenH = TEXTURE_HEIGHT - 32;
        TextureGraphRenderer.fillRect(image, screenX, screenY, screenW, screenH, PANEL_DARK);
        TextureGraphRenderer.drawBorder(image, screenX, screenY, screenW, screenH, 2, BRASS_DARK);

        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 2, screenW - 4, 28, 0xFF17282A);
        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 29, screenW - 4, 1, BRASS);
        BitmapFont.drawString(image, "FLUID", screenX + 8, screenY + 7, TEXT, 1);
        BitmapFont.drawRight(image, TextureGraphRenderer.truncate(be.getTrackingSummaryLabel(), screenW - 60, 1),
            screenX + screenW - 8, screenY + 7, MUTED, 1);

        final List<ProductionDetectorBE.FluidEntry> fluids = be.getFluidEntriesSnapshot();
        final List<ProductionDetectorBE.SyntheticContentEntry> synthetic = be.getSyntheticContentSnapshot();
        ProductionDetectorBE.BoilerSnapshot bs = be.getBoilerSnapshot();
        final boolean hasFluid = !fluids.isEmpty();
        final boolean hasSynthetic = !synthetic.isEmpty();
        final boolean hasBoiler = bs != null && bs.active();
        if (!hasFluid && !hasSynthetic && !hasBoiler) {
            BitmapFont.drawCentered(image, "NO FLUID", screenX, screenY + screenH / 2 - 4, screenW, EMPTY, 1);
            return;
        }

        final String primaryName;
        final long primaryAmount;
        final long primaryCapacity;
        final String primaryFluidId;
        if (hasFluid) {
            ProductionDetectorBE.FluidEntry primary = fluids.get(0);
            primaryName = primary.displayName();
            primaryAmount = primary.amount();
            primaryCapacity = primary.capacity();
            primaryFluidId = primary.fluidId();
        } else if (hasSynthetic) {
            ProductionDetectorBE.SyntheticContentEntry s0 = synthetic.get(0);
            primaryName = s0.label();
            primaryAmount = s0.amount();
            primaryCapacity = s0.capacity();
            primaryFluidId = s0.id();
        } else {
            primaryName = "Boiler";
            primaryAmount = 0;
            primaryCapacity = 0;
            primaryFluidId = "minecraft:water";
        }

        final int capX = screenX + Math.round(screenW * FluidPanelLayout.CAP_X_RATIO);
        final int capW = Math.round(screenW * FluidPanelLayout.CAP_W_RATIO);
        final int capY = screenY + Math.round(screenH * FluidPanelLayout.CAP_Y_RATIO);
        final int capH = Math.round(screenH * FluidPanelLayout.CAP_H_RATIO);
        TextureGraphRenderer.fillRect(image, capX, capY, capW, capH, 0xFF050B0E);
        TextureGraphRenderer.drawBorder(image, capX, capY, capW, capH, 2, BRASS);
        for (int i = 1; i < 4; i++) {
            int ty = capY + (capH * i) / 4;
            TextureGraphRenderer.fillRect(image, capX + capW - 8, ty, 6, 1, BRASS_DARK);
            TextureGraphRenderer.fillRect(image, capX + 2, ty, 6, 1, BRASS_DARK);
        }

        // Animated wave fill, color from FluidPalette
        if (primaryCapacity > 0 && primaryAmount > 0) {
            int color = FluidPalette.colorForFluidId(primaryFluidId);
            int rB = (color >> 16) & 0xFF;
            int gB = (color >>  8) & 0xFF;
            int bB =  color        & 0xFF;
            int colorAlpha = 0xE0;
            int colorPacked = (colorAlpha << 24) | (rB << 16) | (gB << 8) | bB;
            int hiR = Math.min(255, rB + 40);
            int hiG = Math.min(255, gB + 40);
            int hiB = Math.min(255, bB + 40);
            int hiPacked = 0xFF000000 | (hiR << 16) | (hiG << 8) | hiB;

            float ratio = Math.max(0.02f, Math.min(1f, primaryAmount / (float) primaryCapacity));
            int interiorTop = capY + 2;
            int interiorBottom = capY + capH - 2;
            int interiorLeft = capX + 2;
            int interiorRight = capX + capW - 2;
            int fillHeight = Math.round((interiorBottom - interiorTop) * ratio);
            int fillTop = interiorBottom - fillHeight;
            long ms = nowTicks();
            for (int col = interiorLeft; col < interiorRight; col++) {
                int relCol = col - interiorLeft;
                double phase = (ms * 0.35) + relCol * 0.42;
                int wave = (int) Math.round(Math.sin(phase) * FluidPanelLayout.WAVE_AMPLITUDE_PX);
                int top = Math.max(interiorTop, fillTop + wave);
                if (top >= interiorBottom) continue;
                // body
                TextureGraphRenderer.fillRect(image, col, top + 1, 1, interiorBottom - top - 1, colorPacked);
                // 1px highlight at the surface
                if (top >= interiorTop && top < interiorBottom) {
                    image.setPixelRGBA(col, top, abgrFromArgb(hiPacked));
                }
            }

            // Bubbles
            for (int b = 0; b < FluidPanelLayout.BUBBLE_COLUMNS; b++) {
                long t = ms + b * 17L;
                int span = Math.max(1, fillHeight);
                int by = interiorBottom - (int) ((t * 1L) % span);
                int bx = interiorLeft + 4 + (int) (((t * 3 + b * 13L) % Math.max(1, (interiorRight - interiorLeft - 8))));
                if (by < fillTop) continue;
                int bubble = 0xFFE9F8FF;
                TextureGraphRenderer.fillRect(image, bx, by, 2, 1, bubble);
                TextureGraphRenderer.fillRect(image, bx, by - 1, 1, 1, bubble);
            }

            // Steam (if boiler active) above the surface
            if (hasBoiler) {
                for (int s = 0; s < FluidPanelLayout.STEAM_COLUMNS; s++) {
                    long t = ms + s * 23L;
                    int sy = fillTop - 2 - (int) ((t / 2L) % 14L);
                    int sx = interiorLeft + 6 + (int) (((t * 2L + s * 11L) % Math.max(1, (interiorRight - interiorLeft - 12))));
                    if (sy <= interiorTop) continue;
                    TextureGraphRenderer.fillRect(image, sx, sy, 2, 1, 0xC0E2EAEC);
                }
            }
        }

        final String name = TextureGraphRenderer.truncate(primaryName, screenW - 14, 1);
        final String amount = formatCount(primaryAmount) + " mB";
        final String capacity = "/ " + formatCount(primaryCapacity) + " mB";
        final int pct = primaryCapacity > 0 ? Math.round(100.0f * primaryAmount / primaryCapacity) : 0;
        BitmapFont.drawCentered(image, name, screenX, screenY + 38, screenW, TEXT, 1);
        if (primaryCapacity > 0) {
            BitmapFont.drawCentered(image, amount, screenX, capY + capH + 8, screenW, CYAN, 1);
            BitmapFont.drawCentered(image, capacity, screenX, capY + capH + 18, screenW, MUTED, 1);
            BitmapFont.drawCentered(image, pct + "%", screenX, capY + capH + 28, screenW, GOOD, 1);
        }

        if (hasBoiler) {
            int bx = screenX + 4;
            int by = screenY + screenH - 28;
            TextureGraphRenderer.fillRect(image, bx, by, screenW - 8, 24, 0xFF1A2628);
            TextureGraphRenderer.drawBorder(image, bx, by, screenW - 8, 24, 1, BRASS_DARK);
            BitmapFont.drawString(image, "HEAT " + bs.activeHeat(), bx + 4, by + 4, WARN, 1);
            BitmapFont.drawString(image, "ENG " + bs.engines(), bx + 4, by + 14, MUTED, 1);
            BitmapFont.drawRight(image, "WHST " + bs.whistles(), bx + screenW - 12, by + 4, MUTED, 1);
            BitmapFont.drawRight(image, "WTR " + formatCount(bs.waterSupply()), bx + screenW - 12, by + 14, CYAN, 1);
        }
    }

    private static int abgrFromArgb(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b =  argb         & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static void drawEnergyBoard(NativeImage image, ProductionDetectorBE be) {
        TextureGraphRenderer.fillRect(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, FRAME);
        TextureGraphRenderer.drawBorder(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, 4, BRASS_DARK);
        TextureGraphRenderer.drawBorder(image, 5, 5, TEXTURE_WIDTH - 10, TEXTURE_HEIGHT - 10, 2, BRASS);
        drawCornerCaps(image);

        final int screenX = 16;
        final int screenY = 16;
        final int screenW = TEXTURE_WIDTH - 32;
        final int screenH = TEXTURE_HEIGHT - 32;
        TextureGraphRenderer.fillRect(image, screenX, screenY, screenW, screenH, PANEL_DARK);
        drawDisplayPattern(image, screenX, screenY, screenW, screenH);
        TextureGraphRenderer.drawBorder(image, screenX, screenY, screenW, screenH, 2, BRASS_DARK);

        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 2, screenW - 4, 28, 0xFF17282A);
        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 29, screenW - 4, 1, ENERGY);
        BitmapFont.drawString(image, "ENERGY", screenX + 8, screenY + 7, TEXT, 1);

        final ProductionDetectorBE.EnergySnapshot snapshot = be.getEnergySnapshot();
        if (snapshot == null) {
            BitmapFont.drawCentered(image, "NO ENERGY DATA", screenX, screenY + screenH / 2 - 4, screenW, EMPTY, 1);
            return;
        }

        BitmapFont.drawRight(image, String.format("%.0f%%", snapshot.fillPercent()), screenX + screenW - 8, screenY + 7, energyColor(snapshot), 1);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(snapshot.sourceType(), screenW - 16, 1), screenX + 8, screenY + 19, MUTED, 1);

        final int batteryX = screenX + 16;
        final int batteryY = screenY + 43;
        final int batteryW = screenW - 32;
        final int batteryH = 34;
        drawEnergyBatteryTexture(image, batteryX, batteryY, batteryW, batteryH, snapshot);
        BitmapFont.drawCentered(image, formatEnergy(snapshot.stored()) + " / " + formatEnergy(snapshot.capacity()), batteryX, batteryY + batteryH + 5, batteryW, CYAN, 1);

        final int railY = batteryY + batteryH + 33;
        final int leftNodeX = screenX + 10;
        final int nodeW = 48;
        final int centerX = screenX + screenW / 2;
        final int coreW = 60;
        final int rightNodeX = screenX + screenW - nodeW - 10;
        drawEnergyTerminalTexture(image, leftNodeX, railY - 14, nodeW, 34, "IN", formatEnergyRate(snapshot.inputRate()), GOOD);
        drawEnergyTerminalTexture(image, centerX - coreW / 2, railY - 20, coreW, 46, "NET", formatEnergyRate(snapshot.netRate()), energyColor(snapshot));
        drawEnergyTerminalTexture(image, rightNodeX, railY - 14, nodeW, 34, "OUT", formatEnergyRate(snapshot.outputRate()), WARN);

        drawEnergyFlowTexture(image, leftNodeX + nodeW, centerX - coreW / 2, railY, true, hasInputFlow(snapshot), GOOD, nowTicks());
        drawEnergyFlowTexture(image, centerX + coreW / 2, rightNodeX, railY, true, hasOutputFlow(snapshot), WARN, nowTicks());

        final int connectorY = screenY + screenH - 30;
        drawEnergyConnectorStripTexture(image, screenX + 12, connectorY, screenW - 24, 20, snapshot);
    }

    private static void drawEnergyBatteryTexture(NativeImage image, int x, int y, int w, int h, ProductionDetectorBE.EnergySnapshot snapshot) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xFF050B0E);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 2, BRASS);
        TextureGraphRenderer.fillRect(image, x + w, y + h / 3, 5, h / 3, BRASS);
        final int innerX = x + 4;
        final int innerY = y + 4;
        final int innerW = Math.max(1, w - 8);
        final int innerH = Math.max(1, h - 8);
        final int fillW = Math.round(innerW * Math.max(0.0f, Math.min(1.0f, snapshot.fillPercent() / 100.0f)));
        if (fillW > 0) {
            TextureGraphRenderer.fillRect(image, innerX, innerY, fillW, innerH, 0xCC3FA86D);
            TextureGraphRenderer.fillRect(image, innerX, innerY, fillW, 2, 0xFFEAFBD6);
        }
        for (int segment = 1; segment < 5; segment++) {
            final int segmentX = innerX + segment * innerW / 5;
            TextureGraphRenderer.fillRect(image, segmentX, innerY, 1, innerH, BRASS_DARK);
        }
        if (hasEnergyFlow(snapshot)) {
            final int span = Math.max(6, innerW - 4);
            final long tick = nowTicks();
            for (int pulse = 0; pulse < 4; pulse++) {
                final int phase = Math.floorMod((int)(tick * 3L + pulse * span / 4), span);
                final int pulseX = isDischarging(snapshot) ? innerX + innerW - 3 - phase : innerX + 2 + phase;
                TextureGraphRenderer.fillRect(image, pulseX - 1, y + h / 2 - 1, 3, 3, energyColor(snapshot));
            }
        }
    }

    private static void drawEnergyTerminalTexture(NativeImage image, int x, int y, int w, int h, String label, String value, int color) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xDD0B2E30);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, color);
        BitmapFont.drawCentered(image, label, x, y + 4, w, MUTED, 1);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, w - 8, 1), x, y + h - 13, w, color, 1);
    }

    private static void drawEnergyFlowTexture(NativeImage image, int startX, int endX, int y, boolean toRight, boolean active, int color, long tick) {
        final int left = Math.min(startX, endX);
        final int right = Math.max(startX, endX);
        final int width = Math.max(1, right - left);
        TextureGraphRenderer.fillRect(image, left, y - 1, width, 2, active ? BRASS_DARK : 0x66425355);
        if (!active) {
            return;
        }
        final int span = Math.max(8, width - 2);
        for (int pulse = 0; pulse < 4; pulse++) {
            final int phase = Math.floorMod((int)(tick * 4L + pulse * span / 4), span);
            final int pulseX = toRight ? left + 1 + phase : right - 2 - phase;
            TextureGraphRenderer.fillRect(image, pulseX - 1, y - 3, 4, 6, color);
        }
    }

    private static void drawEnergyConnectorStripTexture(NativeImage image, int x, int y, int w, int h, ProductionDetectorBE.EnergySnapshot snapshot) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xCC081A1D);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, BRASS_DARK);
        BitmapFont.drawString(image, "CONNECT " + formatCount(Math.round(snapshot.connectors())), x + 5, y + 6, snapshot.connectors() > 0.5f ? GOOD : MUTED, 1);
        BitmapFont.drawRight(image, "BLOCKS " + formatCount(Math.round(snapshot.blocks())), x + w - 5, y + 6, MUTED, 1);
        final int nodeCount = Math.max(1, Math.min(8, Math.round(snapshot.connectors())));
        final int startX = x + w / 2 - 34;
        for (int node = 0; node < 8; node++) {
            final int nodeColor = node < nodeCount && snapshot.connectors() > 0.5f ? ENERGY_BLUE : 0xFF243336;
            TextureGraphRenderer.fillRect(image, startX + node * 9, y + h - 6, 5, 3, nodeColor);
        }
    }

    private static int energyColor(ProductionDetectorBE.EnergySnapshot snapshot) {
        if (isDischarging(snapshot)) {
            return WARN;
        }
        if (isCharging(snapshot)) {
            return GOOD;
        }
        if (hasEnergyFlow(snapshot)) {
            return ENERGY_BLUE;
        }
        if (snapshot.fillPercent() <= 10.0f) {
            return 0xFFD05050;
        }
        return ENERGY;
    }

    private static boolean hasInputFlow(ProductionDetectorBE.EnergySnapshot snapshot) {
        return snapshot.inputRate() > ENERGY_FLOW_EPSILON || snapshot.netRate() > ENERGY_FLOW_EPSILON;
    }

    private static boolean hasOutputFlow(ProductionDetectorBE.EnergySnapshot snapshot) {
        return snapshot.outputRate() > ENERGY_FLOW_EPSILON || snapshot.netRate() < -ENERGY_FLOW_EPSILON;
    }

    private static boolean hasEnergyFlow(ProductionDetectorBE.EnergySnapshot snapshot) {
        return hasInputFlow(snapshot) || hasOutputFlow(snapshot);
    }

    private static boolean isCharging(ProductionDetectorBE.EnergySnapshot snapshot) {
        return hasInputFlow(snapshot) && (!hasOutputFlow(snapshot) || snapshot.inputRate() > snapshot.outputRate() + ENERGY_FLOW_EPSILON || snapshot.netRate() > ENERGY_FLOW_EPSILON);
    }

    private static boolean isDischarging(ProductionDetectorBE.EnergySnapshot snapshot) {
        return hasOutputFlow(snapshot) && (!hasInputFlow(snapshot) || snapshot.outputRate() > snapshot.inputRate() + ENERGY_FLOW_EPSILON || snapshot.netRate() < -ENERGY_FLOW_EPSILON);
    }

    private static void drawDieselBoard(NativeImage image, ProductionDetectorBE be) {
        TextureGraphRenderer.fillRect(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, FRAME);
        TextureGraphRenderer.drawBorder(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, 4, BRASS_DARK);
        TextureGraphRenderer.drawBorder(image, 5, 5, TEXTURE_WIDTH - 10, TEXTURE_HEIGHT - 10, 2, BRASS);
        drawCornerCaps(image);

        final int screenX = 16;
        final int screenY = 16;
        final int screenW = TEXTURE_WIDTH - 32;
        final int screenH = TEXTURE_HEIGHT - 32;
        TextureGraphRenderer.fillRect(image, screenX, screenY, screenW, screenH, PANEL_DARK);
        drawDisplayPattern(image, screenX, screenY, screenW, screenH);
        TextureGraphRenderer.drawBorder(image, screenX, screenY, screenW, screenH, 2, BRASS_DARK);

        final ProductionDetectorBE.DieselSnapshot snapshot = be.getDieselSnapshot();
        if (snapshot == null) {
            BitmapFont.drawCentered(image, "NO DIESEL DATA", screenX, screenY + screenH / 2 - 4, screenW, EMPTY, 1);
            return;
        }

        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 2, screenW - 4, 28, 0xFF17282A);
        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 29, screenW - 4, 1, dieselStateColor(snapshot));
        BitmapFont.drawString(image, dieselBoardTitle(snapshot), screenX + 8, screenY + 7, TEXT, 1);
        BitmapFont.drawRight(image, dieselStateLabel(snapshot), screenX + screenW - 8, screenY + 7, dieselStateColor(snapshot), 1);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(snapshot.sourceType(), screenW - 16, 1), screenX + 8, screenY + 19, MUTED, 1);

        if (snapshot.sourceType().contains("Engine")) {
            drawEngineBoard(image, screenX, screenY + 36, screenW, screenH - 42, snapshot);
        } else if ("Oil Pumpjack".equals(snapshot.sourceType())) {
            drawPumpjackBoard(image, screenX, screenY + 36, screenW, screenH - 42, snapshot);
        } else if ("Diesel Distillation Tower".equals(snapshot.sourceType())) {
            drawDistillationBoard(image, screenX, screenY + 36, screenW, screenH - 42, snapshot);
        } else {
            drawGenericDieselBoard(image, screenX, screenY + 36, screenW, screenH - 42, snapshot);
        }
    }

    private static void drawEngineBoard(NativeImage image, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot) {
        final int tankX = x + 8;
        final int tankY = y + 12;
        final int tankW = 32;
        final int tankH = h - 40;
        TextureGraphRenderer.fillRect(image, tankX, tankY, tankW, tankH, 0xFF050B0E);
        TextureGraphRenderer.drawBorder(image, tankX, tankY, tankW, tankH, 1, BRASS);
        final int tankFill = Math.round((tankH - 6) * clamp01(snapshot.fillPercent() / 100.0f));
        if (tankFill > 0) {
            TextureGraphRenderer.fillRect(image, tankX + 3, tankY + tankH - 3 - tankFill, tankW - 6, tankFill, 0xCC9D6525);
            TextureGraphRenderer.fillRect(image, tankX + 3, tankY + tankH - 3 - tankFill, tankW - 6, 2, 0xFFFFD27A);
        }
        BitmapFont.drawCentered(image, "FUEL", tankX, tankY + 5, tankW, MUTED, 1);
        BitmapFont.drawCentered(image, String.format("%.0f%%", snapshot.fillPercent()), tankX, tankY + tankH - 13, tankW, dieselFillColor(snapshot), 1);

        final int engineX = x + 52;
        final int engineY = y + 20;
        final int engineW = 76;
        final int engineH = h - 58;
        final int stateColor = dieselStateColor(snapshot);
        TextureGraphRenderer.fillRect(image, engineX, engineY, engineW, engineH, 0xDD081A1D);
        TextureGraphRenderer.drawBorder(image, engineX, engineY, engineW, engineH, 1, stateColor);
        BitmapFont.drawCentered(image, snapshot.sourceType().contains("Group") ? "GROUP" : "ENGINE", engineX, engineY + 4, engineW, MUTED, 1);
        final int crankY = engineY + engineH - 18;
        TextureGraphRenderer.fillRect(image, engineX + 6, crankY - 1, engineW - 12, 2, stateColor);
        final double time = nowTicks() / 3.0;
        final int cylinderCount = Math.max(2, Math.min(5, Math.round(snapshot.engineCount())));
        final int bayW = Math.max(10, (engineW - 16) / cylinderCount);
        for (int cyl = 0; cyl < cylinderCount; cyl++) {
            final int cx = engineX + 8 + cyl * bayW + bayW / 2;
            final int travel = Math.max(5, engineH / 5);
            final int pistonY = engineY + 22 + (snapshot.running() ? (int)Math.round((Math.sin(time + cyl * 1.2) + 1.0) * travel * 0.5) : travel / 2);
            TextureGraphRenderer.fillRect(image, cx - 4, engineY + 18, 8, crankY - engineY - 22, 0xFF102D30);
            TextureGraphRenderer.drawBorder(image, cx - 4, engineY + 18, 8, crankY - engineY - 22, 1, BRASS_DARK);
            TextureGraphRenderer.fillRect(image, cx - 3, pistonY, 7, 5, snapshot.running() ? ENERGY : MUTED);
            TextureGraphRenderer.drawLineAA(image, cx, pistonY + 5, cx + (cyl % 2 == 0 ? 5 : -5), crankY, MUTED);
            if (snapshot.running() && Math.sin(time + cyl * 1.2) > 0.55) {
                TextureGraphRenderer.fillRect(image, cx - 1, engineY + 13, 3, 3, 0xFFFFD27A);
            }
        }
        final int flyX = engineX + engineW - 10;
        for (int spoke = 0; spoke < 6; spoke++) {
            final double angle = time * (snapshot.running() ? 0.9 : 0.08) + spoke * Math.PI / 3.0;
            final int sx = flyX + (int)Math.round(Math.cos(angle) * 9.0);
            final int sy = crankY + (int)Math.round(Math.sin(angle) * 9.0);
            TextureGraphRenderer.drawLineAA(image, flyX, crankY, sx, sy, stateColor);
        }
        TextureGraphRenderer.fillRect(image, flyX - 2, crankY - 2, 4, 4, stateColor);

        drawTexturePipe(image, tankX + tankW, engineX, tankY + tankH / 2, snapshot.fillPercent() > 0.0f, dieselFillColor(snapshot), true);
        final int statsX = x + w - 58;
        drawTexturePipe(image, engineX + engineW, statsX, crankY, snapshot.running(), CYAN, true);
        drawDieselTextureNode(image, statsX, y + 10, 50, 23, "RPM", String.format("%.0f", snapshot.generatedSpeed()), CYAN);
        drawDieselTextureNode(image, statsX, y + 39, 50, 23, "GEN", formatCompact(snapshot.generatedStress()) + "SU", snapshot.generatedStress() > 0.0f ? GOOD : MUTED);
        drawDieselTextureNode(image, statsX, y + 68, 50, 23, "LOAD", formatCompact(snapshot.networkStress()) + "SU", snapshot.networkStress() > 0.0f ? WARN : MUTED);
        final int stripY = y + h - 22;
        TextureGraphRenderer.fillRect(image, x + 8, stripY, w - 16, 17, 0xDD081A1D);
        TextureGraphRenderer.drawBorder(image, x + 8, stripY, w - 16, 17, 1, BRASS_DARK);
        final String groupLine = snapshot.sourceType().contains("Group")
            ? "OK " + Math.round(snapshot.okEngines()) + " ST " + Math.round(snapshot.stallingEngines()) + " OFF " + Math.round(snapshot.offEngines())
            : "ENGINES " + Math.round(snapshot.engineCount());
        BitmapFont.drawString(image, groupLine, x + 13, stripY + 5, stateColor, 1);
        BitmapFont.drawRight(image, dieselStateLabel(snapshot), x + w - 13, stripY + 5, stateColor, 1);
    }

    private static void drawDistillationBoard(NativeImage image, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot) {
        final int columnX = x + w / 2 - 22;
        final int columnY = y + 12;
        final int columnW = 44;
        final int columnH = h - 42;
        TextureGraphRenderer.fillRect(image, columnX, columnY, columnW, columnH, 0xFF071B1D);
        TextureGraphRenderer.drawBorder(image, columnX, columnY, columnW, columnH, 2, BRASS);
        final int fillH = Math.round((columnH - 6) * clamp01(snapshot.fillPercent() / 100.0f));
        if (fillH > 0) {
            TextureGraphRenderer.fillRect(image, columnX + 3, columnY + columnH - 3 - fillH, columnW - 6, fillH, 0xCC9D6525);
        }
        for (int tray = 1; tray < 6; tray++) {
            final int trayY = columnY + tray * columnH / 6;
            TextureGraphRenderer.fillRect(image, columnX + 5, trayY, columnW - 10, 2, tray % 2 == 0 ? BRASS_DARK : 0xFF3DBCAF);
        }
        final int bubble = Math.floorMod((int)nowTicks(), 18);
        for (int i = 0; i < 5; i++) {
            final int dotY = columnY + columnH - 12 - Math.floorMod(bubble + i * 7, Math.max(12, columnH - 20));
            final int dotX = columnX + 10 + (i % 3) * 10;
            TextureGraphRenderer.fillRect(image, dotX, dotY, 3, 3, snapshot.processing() ? GOOD : BRASS_DARK);
        }

        final int heaterX = columnX - 54;
        final int heaterY = columnY + columnH - 28;
        drawDieselTextureNode(image, heaterX, heaterY, 46, 28, "HEAT", formatCompact(snapshot.heatLevel()), snapshot.heatLevel() > 0.0f ? WARN : MUTED);
        drawTexturePipe(image, heaterX + 46, columnX, heaterY + 14, snapshot.heatLevel() > 0.0f, WARN, true);

        final int feedX = columnX - 54;
        final int feedY = columnY + 18;
        drawDieselTextureNode(image, feedX, feedY, 46, 28, "CRUDE", String.format("%.0f%%", snapshot.fillPercent()), dieselFillColor(snapshot));
        drawTexturePipe(image, feedX + 46, columnX, feedY + 14, snapshot.fillPercent() > 0.0f, dieselFillColor(snapshot), true);

        final int outX = columnX + columnW + 8;
        final String[] labels = {"GASOLINE", "DIESEL"};
        for (int i = 0; i < labels.length; i++) {
            final int outY = columnY + 24 + i * 42;
            drawTexturePipe(image, columnX + columnW, outX, outY + 11, snapshot.processing(), CYAN, true);
            drawDieselTextureNode(image, outX, outY, 50, 23, labels[i], snapshot.processing() ? "FLOW" : "HOLD", snapshot.processing() ? CYAN : MUTED);
        }

        final int bottomY = y + h - 22;
        drawDieselMetricPill(image, x + 8, bottomY, 48, "FILL", String.format("%.0f%%", snapshot.fillPercent()), dieselFillColor(snapshot));
        drawDieselMetricPill(image, x + 62, bottomY, 58, "PROCESS", String.format("%.0f%%", snapshot.processPercent()), snapshot.processing() ? GOOD : MUTED);
        drawDieselMetricPill(image, x + 126, bottomY, 56, "TANKS", formatCompact(snapshot.engineCount()), TEXT);
    }

    private static void drawPumpjackBoard(NativeImage image, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot) {
        final int groundY = y + h - 36;
        TextureGraphRenderer.fillRect(image, x + 8, groundY, w - 16, 3, BRASS_DARK);
        TextureGraphRenderer.fillRect(image, x + 8, groundY + 3, w - 16, 22, 0xFF1A1410);
        for (int px = x + 12; px < x + w - 12; px += 11) {
            TextureGraphRenderer.fillRect(image, px, groundY + 7, 5, 2, 0xFF3D2A1B);
        }

        final int pivotX = x + w / 2 - 12;
        final int pivotY = y + 62;
        TextureGraphRenderer.fillRect(image, pivotX - 3, pivotY, 6, groundY - pivotY, BRASS_DARK);
        TextureGraphRenderer.fillRect(image, pivotX - 12, pivotY + 8, 24, 5, BRASS);
        final int beamOffset = snapshot.processing() ? Math.floorMod((int)nowTicks(), 24) - 12 : 0;
        TextureGraphRenderer.drawLineAA(image, pivotX - 54, pivotY + beamOffset / 3, pivotX + 48, pivotY - beamOffset / 4, BRASS);
        TextureGraphRenderer.drawLineAA(image, pivotX - 53, pivotY + beamOffset / 3 + 1, pivotX + 47, pivotY - beamOffset / 4 + 1, BRASS_DARK);
        TextureGraphRenderer.fillRect(image, pivotX + 38, pivotY - 2 - beamOffset / 4, 10, 18, WARN);
        TextureGraphRenderer.fillRect(image, pivotX - 62, pivotY + beamOffset / 3 + 8, 10, 20, 0xFF66A8FF);
        TextureGraphRenderer.fillRect(image, pivotX - 58, pivotY + beamOffset / 3 + 28, 3, groundY - pivotY - 28, CYAN);
        TextureGraphRenderer.fillRect(image, pivotX - 61, groundY - 4, 9, 8, CYAN);

        final int depositW = 76;
        final int depositX = x + w / 2 - depositW / 2;
        final int depositY = groundY + 9;
        TextureGraphRenderer.fillRect(image, depositX, depositY, depositW, 11, 0xFF090D0F);
        final int depositFill = Math.round((depositW - 4) * clamp01(snapshot.oilRemaining() / Math.max(1.0f, snapshot.oilRemaining() + 20000.0f)));
        TextureGraphRenderer.fillRect(image, depositX + 2, depositY + 2, Math.max(3, depositFill), 7, 0xCC9D6525);
        TextureGraphRenderer.drawBorder(image, depositX, depositY, depositW, 11, 1, BRASS_DARK);

        drawDieselTextureNode(image, x + 8, y + 12, 58, 32, "DEPOSIT", formatCompact(snapshot.oilRemaining()) + " mB", snapshot.oilRemaining() > 0.0f ? WARN : MUTED);
        drawDieselTextureNode(image, x + w - 66, y + 12, 58, 32, "PIPE", formatCompact(snapshot.pipeLength()), snapshot.validFuel() ? GOOD : WARN);
        drawDieselTextureNode(image, x + w / 2 - 32, y + 16, 64, 28, "PUMP", dieselStateLabel(snapshot), dieselStateColor(snapshot));
        drawTexturePipe(image, pivotX - 54, x + 66, pivotY + 10, snapshot.processing(), CYAN, false);
        drawTexturePipe(image, pivotX + 48, x + w - 66, pivotY + 8, snapshot.validFuel(), WARN, true);
    }

    private static void drawGenericDieselBoard(NativeImage image, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot) {
        final int gaugeX = x + 14;
        final int gaugeY = y + 18;
        final int gaugeW = w - 28;
        final int gaugeH = 34;
        TextureGraphRenderer.fillRect(image, gaugeX, gaugeY, gaugeW, gaugeH, 0xFF050B0E);
        TextureGraphRenderer.drawBorder(image, gaugeX, gaugeY, gaugeW, gaugeH, 1, BRASS);
        final int fillW = Math.round((gaugeW - 8) * clamp01(snapshot.fillPercent() / 100.0f));
        TextureGraphRenderer.fillRect(image, gaugeX + 4, gaugeY + 4, fillW, gaugeH - 8, 0xCC9D6525);
        BitmapFont.drawCentered(image, String.format("%.0f%%", snapshot.fillPercent()), gaugeX, gaugeY + 13, gaugeW, TEXT, 1);
        final int railY = y + h - 54;
        drawDieselTextureNode(image, x + 8, railY, 54, 32, "FUEL", formatCompact(snapshot.fluidAmount()), dieselFillColor(snapshot));
        drawDieselTextureNode(image, x + w / 2 - 30, railY - 6, 60, 44, dieselCoreLabel(snapshot), dieselStateLabel(snapshot), dieselStateColor(snapshot));
        drawDieselTextureNode(image, x + w - 62, railY, 54, 32, "OUT", dieselOutputValue(snapshot), CYAN);
        drawTexturePipe(image, x + 62, x + w / 2 - 30, railY + 16, snapshot.fillPercent() > 0.0f, dieselFillColor(snapshot), true);
        drawTexturePipe(image, x + w / 2 + 30, x + w - 62, railY + 16, snapshot.hasActivity(), CYAN, true);
    }

    private static void drawDieselTextureNode(NativeImage image, int x, int y, int w, int h, String label, String value, int color) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xDD0B2E30);
        TextureGraphRenderer.drawBorder(image, x, y, w, h, 1, color);
        BitmapFont.drawCentered(image, label, x, y + 4, w, MUTED, 1);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, w - 8, 1), x, y + h - 13, w, color, 1);
    }

    private static void drawDieselMetricPill(NativeImage image, int x, int y, int w, String label, String value, int color) {
        TextureGraphRenderer.fillRect(image, x, y, w, 17, 0xDD081A1D);
        TextureGraphRenderer.drawBorder(image, x, y, w, 17, 1, BRASS_DARK);
        BitmapFont.drawString(image, label, x + 4, y + 5, MUTED, 1);
        BitmapFont.drawRight(image, value, x + w - 4, y + 5, color, 1);
    }

    private static void drawTexturePipe(NativeImage image, int startX, int endX, int y, boolean active, int color, boolean toRight) {
        final int left = Math.min(startX, endX);
        final int right = Math.max(startX, endX);
        TextureGraphRenderer.fillRect(image, left, y - 1, Math.max(1, right - left), 2, active ? BRASS_DARK : 0x66425355);
        if (!active) {
            return;
        }
        final int width = Math.max(8, right - left - 2);
        final int phase = Math.floorMod((int)nowTicks() * 3, width);
        final int pulseX = toRight ? left + 1 + phase : right - 2 - phase;
        TextureGraphRenderer.fillRect(image, pulseX - 1, y - 3, 4, 6, color);
    }

    private static String dieselBoardTitle(ProductionDetectorBE.DieselSnapshot snapshot) {
        if ("Diesel Distillation Tower".equals(snapshot.sourceType())) {
            return "DISTILLERY";
        }
        if ("Oil Pumpjack".equals(snapshot.sourceType())) {
            return "PUMPJACK";
        }
        if (snapshot.sourceType().contains("Engine")) {
            return "DIESEL ENGINE";
        }
        return "DIESEL PLANT";
    }

    private static String dieselCoreLabel(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.sourceType().contains("Engine")) {
            return "ENGINE";
        }
        if ("Diesel Distillation Tower".equals(snapshot.sourceType())) {
            return "TOWER";
        }
        if ("Diesel Burner".equals(snapshot.sourceType())) {
            return "BURNER";
        }
        if ("Oil Barrel".equals(snapshot.sourceType())) {
            return "BARREL";
        }
        return "PUMP";
    }

    private static String dieselStateLabel(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.sourceType().contains("Group")) {
            if (snapshot.offEngines() >= snapshot.engineCount()) {
                return "OFF";
            }
            if (snapshot.stallingEngines() > 0.0f) {
                return "STALL";
            }
            if (snapshot.okEngines() > 0.0f) {
                return "OK";
            }
        }
        if (snapshot.redstoneDisabled()) {
            return "STOP";
        }
        if ("Oil Pumpjack".equals(snapshot.sourceType())) {
            if (snapshot.processing()) return "PUMPING";
            if (snapshot.oilRemaining() > 0.0f) return "PRIMED";
            return "DRY";
        }
        if ("Diesel Distillation Tower".equals(snapshot.sourceType())) {
            if (snapshot.processing()) return "RUNNING";
            if (snapshot.fillPercent() > 0.0f && snapshot.heatLevel() <= 0.0f) return "HEAT";
            if (snapshot.fillPercent() > 0.0f) return "STORED";
            return "EMPTY";
        }
        if (!snapshot.validFuel()) return "NO FUEL";
        if (snapshot.running()) return "RUNNING";
        if (snapshot.processing()) return "PROCESS";
        return "IDLE";
    }

    private static int dieselStateColor(ProductionDetectorBE.DieselSnapshot snapshot) {
        final String label = dieselStateLabel(snapshot);
        if ("RUNNING".equals(label) || "PUMPING".equals(label) || "STORED".equals(label) || "OK".equals(label)) {
            return GOOD;
        }
        if ("HEAT".equals(label) || "PRIMED".equals(label) || "PROCESS".equals(label) || "STALL".equals(label)) {
            return WARN;
        }
        if ("STOP".equals(label) || "NO FUEL".equals(label)) {
            return 0xFFD05050;
        }
        return MUTED;
    }

    private static int dieselFillColor(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.fillPercent() <= 0.0f && snapshot.oilRemaining() <= 0.0f) {
            return MUTED;
        }
        if (snapshot.fillPercent() > 0.0f && snapshot.fillPercent() < 20.0f) {
            return WARN;
        }
        return 0xFFE6A342;
    }

    private static String dieselOutputValue(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (Math.abs(snapshot.generatedSpeed()) > 0.01f) {
            return String.format("%.0f RPM", snapshot.generatedSpeed());
        }
        if (snapshot.heatLevel() > 0.0f) {
            return "HEAT " + formatCompact(snapshot.heatLevel());
        }
        if (snapshot.oilRemaining() > 0.0f) {
            return formatCompact(snapshot.oilRemaining()) + " mB";
        }
        return snapshot.hasActivity() ? "ACTIVE" : "IDLE";
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void drawDisplayLinkBoard(NativeImage image, ProductionDetectorBE be) {
        TextureGraphRenderer.fillRect(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, FRAME);
        TextureGraphRenderer.drawBorder(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, 4, BRASS_DARK);
        TextureGraphRenderer.drawBorder(image, 5, 5, TEXTURE_WIDTH - 10, TEXTURE_HEIGHT - 10, 2, BRASS);
        drawCornerCaps(image);

        final int screenX = 16;
        final int screenY = 16;
        final int screenW = TEXTURE_WIDTH - 32;
        final int screenH = TEXTURE_HEIGHT - 32;
        TextureGraphRenderer.fillRect(image, screenX, screenY, screenW, screenH, PANEL_DARK);
        TextureGraphRenderer.drawBorder(image, screenX, screenY, screenW, screenH, 2, BRASS_DARK);

        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 2, screenW - 4, 22, 0xFF17282A);
        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 23, screenW - 4, 1, BRASS);
        BitmapFont.drawString(image, "DISPLAY LINK", screenX + 8, screenY + 7, TEXT, 1);

        final List<ProductionDetectorBE.DisplayLinkLine> lines = be.getDisplayLinkLinesSnapshot();
        if (lines.isEmpty()) {
            BitmapFont.drawCentered(image, "WAITING...", screenX, screenY + screenH / 2 - 4, screenW, EMPTY, 1);
            return;
        }
        int y = screenY + 32;
        for (ProductionDetectorBE.DisplayLinkLine line : lines) {
            String text = TextureGraphRenderer.truncate(line.plainText(), screenW - 16, 1);
            BitmapFont.drawString(image, text, screenX + 8, y, TEXT, 1);
            y += 12;
            if (y > screenY + screenH - 6) break;
        }
    }

    private static void drawInventoryBoard(NativeImage image, ProductionDetectorBE be) {
        TextureGraphRenderer.fillRect(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, FRAME);
        TextureGraphRenderer.drawBorder(image, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, 4, BRASS_DARK);
        TextureGraphRenderer.drawBorder(image, 5, 5, TEXTURE_WIDTH - 10, TEXTURE_HEIGHT - 10, 2, BRASS);
        drawCornerCaps(image);

        final int screenX = 16;
        final int screenY = 16;
        final int screenW = TEXTURE_WIDTH - 32;
        final int screenH = TEXTURE_HEIGHT - 32;
        TextureGraphRenderer.fillRect(image, screenX, screenY, screenW, screenH, PANEL_DARK);
        drawDisplayPattern(image, screenX, screenY, screenW, screenH);
        TextureGraphRenderer.drawBorder(image, screenX, screenY, screenW, screenH, 2, BRASS_DARK);

        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 2, screenW - 4, 28, 0xFF17282A);
        TextureGraphRenderer.fillRect(image, screenX + 2, screenY + 29, screenW - 4, 1, BRASS);

        final String title = "INVENTORY";
        final String target = TextureGraphRenderer.truncate(be.getTrackingSummaryLabel(), screenW - 14, 1);
        final String total = formatCount(be.getTotalItemsSeen()) + " ITEMS";
        BitmapFont.drawString(image, title, screenX + 8, screenY + 7, TEXT, 1);
        BitmapFont.drawRight(image, total, screenX + screenW - 8, screenY + 7, CYAN, 1);
        BitmapFont.drawString(image, target, screenX + 8, screenY + 19, MUTED, 1);

        final List<ProductionDetectorBE.ItemCountEntry> entries = be.getTopItemEntries(6);
        if (entries.isEmpty()) {
            drawEmptyState(image, be);
            return;
        }

        final int topCount = Math.max(1, entries.get(0).count());
        int y = screenY + 39;
        for (int i = 0; i < entries.size(); ++i) {
            drawItemRow(image, i, entries.get(i), topCount, screenX + 8, y, screenW - 16);
            y += 23;
        }
        if (entries.size() < 6) {
            BitmapFont.drawCentered(image, "ONLY " + entries.size() + " TYPES", screenX, TEXTURE_HEIGHT - 27, screenW, EMPTY, 1);
        }
    }

    private static void drawCornerCaps(NativeImage image) {
        TextureGraphRenderer.fillRect(image, 6, 6, 22, 5, BRASS);
        TextureGraphRenderer.fillRect(image, 6, 6, 5, 22, BRASS);
        TextureGraphRenderer.fillRect(image, TEXTURE_WIDTH - 28, 6, 22, 5, BRASS);
        TextureGraphRenderer.fillRect(image, TEXTURE_WIDTH - 11, 6, 5, 22, BRASS);
        TextureGraphRenderer.fillRect(image, 6, TEXTURE_HEIGHT - 11, 22, 5, BRASS);
        TextureGraphRenderer.fillRect(image, 6, TEXTURE_HEIGHT - 28, 5, 22, BRASS);
        TextureGraphRenderer.fillRect(image, TEXTURE_WIDTH - 28, TEXTURE_HEIGHT - 11, 22, 5, BRASS);
        TextureGraphRenderer.fillRect(image, TEXTURE_WIDTH - 11, TEXTURE_HEIGHT - 28, 5, 22, BRASS);
    }

    private static void drawDisplayPattern(NativeImage image, int x, int y, int w, int h) {
        for (int px = x + 12; px < x + w - 12; px += 16) {
            TextureGraphRenderer.fillRect(image, px, y + 4, 1, h - 8, 0x2200FFFF);
        }
        for (int py = y + 10; py < y + h - 6; py += 16) {
            TextureGraphRenderer.fillRect(image, x + 4, py, w - 8, 1, 0x221ADCD2);
        }
        for (int py = y + 3; py < y + h - 3; py += 4) {
            TextureGraphRenderer.fillRect(image, x + 3, py, w - 6, 1, 0x18000000);
        }
    }

    private static void drawItemRow(NativeImage image, int index, ProductionDetectorBE.ItemCountEntry entry, int topCount, int rowX, int y, int rowW) {
        TextureGraphRenderer.fillRect(image, rowX, y, rowW, 20, index == 0 ? 0x772A3429 : PANEL);
        TextureGraphRenderer.drawBorder(image, rowX, y, rowW, 20, 1, index == 0 ? BRASS : BRASS_DARK);

        final int barW = Math.max(2, (int)((rowW - 4) * (entry.count() / (float)topCount)));
        TextureGraphRenderer.fillRect(image, rowX + 2, y + 17, rowW - 4, 1, 0x55324748);
        TextureGraphRenderer.fillRect(image, rowX + 2, y + 17, barW, 2, index == 0 ? GOOD : CYAN);

        final String rank = String.valueOf(index + 1);
        final String name = TextureGraphRenderer.truncate(entry.displayName(), 122, 1);
        final String count = formatCount(entry.count());
        TextureGraphRenderer.fillRect(image, rowX + 4, y + 4, 12, 11, index == 0 ? BRASS_DARK : 0xFF183638);
        BitmapFont.drawCentered(image, rank, rowX + 4, y + 6, 12, index == 0 ? TEXT : MUTED, 1);
        BitmapFont.drawString(image, name, rowX + 22, y + 5, TEXT, 1);
        BitmapFont.drawRight(image, count, rowX + rowW - 6, y + 5, index == 0 ? GOOD : CYAN, 1);
    }

    private static void drawEmptyState(NativeImage image, ProductionDetectorBE be) {
        final String state = be.getTargetStatusSummary();
        final String title = switch (be.getTargetState()) {
            case NO_TARGET -> "NO TARGET";
            case IDLE -> "NO ITEMS";
            case BLOCKED -> "TARGET BLOCKED";
            case MONITORING -> "SYNCING...";
        };
        TextureGraphRenderer.fillRect(image, 32, 82, TEXTURE_WIDTH - 64, 60, 0xCC0B2E30);
        TextureGraphRenderer.drawBorder(image, 32, 82, TEXTURE_WIDTH - 64, 60, 2, BRASS_DARK);
        BitmapFont.drawCentered(image, title, 32, 99, TEXTURE_WIDTH - 64, WARN, 1);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(state, TEXTURE_WIDTH - 84, 1), 32, 116, TEXTURE_WIDTH - 64, EMPTY, 1);
    }

    private static String formatCount(long value) {
        if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.format("%,d", value);
    }

    private static String formatCompact(float value) {
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) {
            return String.format("%.1fM", value / 1_000_000.0f);
        }
        if (abs >= 10_000.0f) {
            return String.format("%.1fk", value / 1_000.0f);
        }
        return String.format("%.0f", value);
    }

    private static String formatEnergy(float value) {
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) {
            return String.format("%.1fM FE", value / 1_000_000.0f);
        }
        if (abs >= 10_000.0f) {
            return String.format("%.1fk FE", value / 1_000.0f);
        }
        return String.format("%.0f FE", value);
    }

    private static String formatEnergyRate(float value) {
        final String sign = value > 0.01f ? "+" : "";
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) {
            return sign + String.format("%.1fM FE/t", value / 1_000_000.0f);
        }
        if (abs >= 10_000.0f) {
            return sign + String.format("%.1fk FE/t", value / 1_000.0f);
        }
        return sign + String.format("%.0f FE/t", value);
    }

    private static long nowTicks() {
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    private static final class TextureEntry {
        private final LevelAccessor world;
        private final DynamicTexture texture;
        private int renderHash;
        private long lastUseTick;

        private TextureEntry(LevelAccessor world, DynamicTexture texture, int renderHash, long lastUseTick) {
            this.world = world;
            this.texture = texture;
            this.renderHash = renderHash;
            this.lastUseTick = lastUseTick;
        }

        private void close() {
            if (this.texture.getPixels() != null) {
                this.texture.getPixels().close();
            }
            this.texture.close();
        }
    }
}
