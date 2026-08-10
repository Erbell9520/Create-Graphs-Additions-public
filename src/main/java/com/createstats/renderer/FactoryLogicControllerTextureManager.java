package com.createstats.renderer;

import com.createstats.CreateStats;
import com.createstats.blockentity.FactoryLogicControllerBE;
import com.createstats.blockentity.FactoryLogicControllerBE.RuleRuntime;
import com.createstats.blockentity.FactoryLogicControllerBE.ScreenCellState;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class FactoryLogicControllerTextureManager {

    private static final Map<BlockPos, TextureEntry> TEXTURES = new HashMap<>();
    private static final int TEX_WIDTH = 160;
    private static final int TEX_HEIGHT = 128;
    private static final int UPDATE_INTERVAL = 4;
    private static final int MAX_IDLE_TICKS = 200;
    private static final int CLEANUP_INTERVAL = 100;
    private static int cleanupCounter;

    private FactoryLogicControllerTextureManager() {
    }

    public static ResourceLocation getOrUpdateTexture(FactoryLogicControllerBE be) {
        BlockPos pos = be.getBlockPos();
        TextureEntry entry = TEXTURES.get(pos);
        if (entry == null) {
            entry = createTextureEntry(pos);
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

    private static TextureEntry createTextureEntry(BlockPos pos) {
        try {
            NativeImage image = new NativeImage(TEX_WIDTH, TEX_HEIGHT, false);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            String texName = "create_stats/factory_logic_controller_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register(texName, dynamicTexture);
            TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynamicTexture = dynamicTexture;
            entry.textureLocation = location;
            entry.lastAccessTick = getGameTime();
            entry.needsRedraw = true;
            return entry;
        } catch (Exception e) {
            CreateStats.LOGGER.warn("Failed to create Factory Logic Controller texture", e);
            return null;
        }
    }

    private static void updateTexture(TextureEntry entry, FactoryLogicControllerBE be, long gameTime) {
        renderScreen(entry.image, be, gameTime);
        entry.dynamicTexture.upload();
    }

    private static void renderScreen(NativeImage image, FactoryLogicControllerBE be, long gameTime) {
        int width = image.getWidth();
        int height = image.getHeight();
        int innerX = 4;
        int innerY = 4;
        int innerW = width - innerX * 2;
        int innerH = height - innerY * 2;

        TextureGraphRenderer.fillRect(image, 0, 0, width, height, 0xFF040907);
        TextureGraphRenderer.drawBorder(image, 0, 0, width, height, 1, 0xFF16261D);
        TextureGraphRenderer.fillRect(image, 1, 1, width - 2, height - 2, 0xFF070F0B);
        TextureGraphRenderer.drawBorder(image, innerX - 1, innerY - 1, innerW + 2, innerH + 2, 1, 0xFF21352A);

        drawWallpaper(image, innerX, innerY, innerW, innerH);
        drawCells(image, be, innerX, innerY, innerW, innerH, gameTime);
        drawSweep(image, innerX, innerY, innerW, innerH, gameTime);
        drawReflection(image, innerX, innerY, innerW, innerH);
        drawScanlines(image, innerX, innerY, innerW, innerH);
        drawVignette(image, innerX, innerY, innerW, innerH);
        drawPowerLed(image, innerX, innerY, innerW, innerH, gameTime);
        TextureGraphRenderer.drawBorder(image, innerX, innerY, innerW, innerH, 1, 0xFF0D1712);
    }

    private static void drawWallpaper(NativeImage image, int x, int y, int w, int h) {
        int horizon = y + Math.round(h * 0.43f);
        int bottom = y + h;
        for (int py = y; py < bottom; py++) {
            float t = (py - y) / (float) Math.max(1, h - 1);
            int sky = lerpColor(0xFF4A92CB, 0xFFB2E1FF, t);
            drawHLine(image, x, py, w, sky);
        }

        for (int px = 0; px < w; px++) {
            float t = px / (float) Math.max(1, w - 1);
            float backWave = (float) Math.sin(t * Math.PI * 1.2f - 0.45f) * 0.08f
                    + (float) Math.sin(t * Math.PI * 2.7f + 0.35f) * 0.03f;
            float frontWave = (float) Math.sin(t * Math.PI * 1.05f + 0.9f) * 0.11f
                    + (float) Math.sin(t * Math.PI * 4.0f - 0.35f) * 0.035f;
            int backStart = horizon + Math.round(h * (0.09f + backWave));
            int frontStart = horizon + Math.round(h * (0.19f + frontWave));

            for (int py = Math.max(y, backStart); py < bottom; py++) {
                float blend = (py - backStart) / (float) Math.max(1, bottom - backStart - 1);
                image.setPixelRGBA(x + px, py, BitmapFont.argbToAbgr(lerpColor(0xFF67BA49, 0xFF2F6D2B, blend)));
            }
            for (int py = Math.max(y, frontStart); py < bottom; py++) {
                float blend = (py - frontStart) / (float) Math.max(1, bottom - frontStart - 1);
                image.setPixelRGBA(x + px, py, BitmapFont.argbToAbgr(lerpColor(0xFF88D951, 0xFF317F30, blend)));
            }
        }

        drawCloud(image, x + Math.round(w * 0.18f), y + Math.round(h * 0.19f), 24, 9, 0x26FFFFFF);
        drawCloud(image, x + Math.round(w * 0.54f), y + Math.round(h * 0.14f), 30, 11, 0x1EFFFFFF);
    }

    private static void drawCells(NativeImage image, FactoryLogicControllerBE be, int x, int y, int w, int h, long gameTime) {
        int insetX = 14;
        int insetY = 12;
        int gapX = 5;
        int gapY = 6;
        int cellW = (w - insetX * 2 - gapX * 3) / 4;
        int cellH = (h - insetY * 2 - gapY * 3) / 4;

        for (int i = 0; i < FactoryLogicControllerBE.RULE_COUNT; i++) {
            ScreenCellState state = be.getScreenCellState(i);
            if (state == ScreenCellState.UNUSED) {
                continue;
            }

            int col = i % 4;
            int row = i / 4;
            int x0 = x + insetX + col * (cellW + gapX);
            int y0 = y + insetY + row * (cellH + gapY);
            int x1 = x0 + cellW;
            int y1 = y0 + cellH;
            int innerX = x0 + 3;
            int innerY = y0 + 3;
            int innerW = Math.max(2, cellW - 6);
            int innerH = Math.max(2, cellH - 6);
            int innerX1 = innerX + innerW;
            int innerY1 = innerY + innerH;

            TextureGraphRenderer.fillRect(image, x0, y0, cellW, cellH, 0xCC06100D);
            TextureGraphRenderer.drawBorder(image, x0, y0, cellW, cellH, 1, 0xFF243A2E);
            TextureGraphRenderer.drawBorder(image, x0 + 1, y0 + 1, cellW - 2, cellH - 2, 1, 0xAA101D17);

            RuleRuntime runtime = be.getRuntime(i);
            CellPalette palette = paletteFor(state, runtime, gameTime, i);
            TextureGraphRenderer.fillRect(image, innerX, innerY, innerW, innerH, palette.baseColor);
            TextureGraphRenderer.fillRect(image, innerX, innerY, innerW, Math.max(1, innerH / 3), palette.highlightColor);
            if (palette.glowColor != 0 && palette.glowAlpha > 0) {
                drawInnerGlow(image, innerX, innerY, innerW, innerH, palette.glowColor, palette.glowAlpha);
            }
            if (runtime.isManualStopLatched()) {
                int stripeY = innerY1 - Math.max(2, innerH / 5);
                TextureGraphRenderer.fillRect(image, innerX + 2, stripeY, Math.max(1, innerW - 4), 2, 0xFFED8181);
            }
            if (state == ScreenCellState.INCOMPLETE) {
                TextureGraphRenderer.fillRect(image, innerX + 1, innerY + innerH / 2, Math.max(1, innerW - 2), 1, 0x66343D3D);
            }
            if (palette.cornerSparkle != 0) {
                TextureGraphRenderer.fillRect(image, innerX1 - 4, innerY + 2, 2, 2, palette.cornerSparkle);
            }
        }
    }

    private static CellPalette paletteFor(ScreenCellState state, RuleRuntime runtime, long gameTime, int idx) {
        float pulseSlow = 0.5f + 0.5f * (float) Math.sin(gameTime * 0.10f + idx * 0.45f);
        float pulseFast = 0.5f + 0.5f * (float) Math.sin(gameTime * 0.22f + idx * 0.65f);
        return switch (state) {
            case INCOMPLETE -> new CellPalette(0xFF090C0E, 0xFF171B1D, 0, 0, 0);
            case RUNNING -> new CellPalette(
                    lerpColor(0xFF1A7D35, 0xFF48F06D, pulseSlow),
                    lerpColor(0xFF4FA764, 0xFFCCFFD5, pulseSlow * 0.65f),
                    0xFF84FFA8,
                    64 + Math.round(48 * pulseSlow),
                    0xFFE3FFF0);
            case COUNTDOWN -> new CellPalette(
                    lerpColor(0xFF874C10, 0xFFF5B34A, pulseFast),
                    lerpColor(0xFFC58B3C, 0xFFFFE0A2, pulseFast * 0.75f),
                    0xFFFFD36C,
                    46 + Math.round(32 * pulseFast),
                    0xFFFFF0C8);
            case STOPPED -> new CellPalette(
                    runtime.isManualStopLatched() ? 0xFF8E181B : 0xFF741113,
                    runtime.isManualStopLatched() ? 0xFFD14A4E : 0xFFB42D31,
                    runtime.isManualStopLatched() ? 0xFFF27276 : 0xFFCC4245,
                    runtime.isManualStopLatched() ? 42 : 24,
                    runtime.isManualStopLatched() ? 0xFFFFA5A7 : 0);
            case ERROR -> {
                boolean blinkOn = (((gameTime + idx * 3L) / 7L) & 1L) == 0L;
                yield new CellPalette(
                        blinkOn ? 0xFFD81B1E : 0xFF4F0C0D,
                        blinkOn ? 0xFFFF6C6C : 0xFF7D2325,
                        blinkOn ? 0xFFFF7D7D : 0xFF80282A,
                        blinkOn ? 84 : 20,
                        blinkOn ? 0xFFFFD2D2 : 0);
            }
            case NO_MACHINES -> new CellPalette(
                    lerpColor(0xFF25578D, 0xFF5CB4FF, pulseSlow),
                    lerpColor(0xFF4A85C8, 0xFFC5E6FF, pulseSlow * 0.8f),
                    0xFFA8DFFF,
                    36 + Math.round(28 * pulseSlow),
                    0xFFE9F7FF);
            default -> new CellPalette(0xFF111616, 0xFF202929, 0, 0, 0);
        };
    }

    private static void drawInnerGlow(NativeImage image, int x, int y, int w, int h, int color, int alpha) {
        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        float radius = Math.max(w, h) * 0.58f;
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                float dx = (px + 0.5f - cx) / radius;
                float dy = (py + 0.5f - cy) / radius;
                float dist = dx * dx + dy * dy;
                if (dist >= 1.0f) {
                    continue;
                }
                int a = Math.round(alpha * (1.0f - dist) * (1.0f - dist));
                blendPixel(image, px, py, withAlpha(color, a));
            }
        }
    }

    private static void drawSweep(NativeImage image, int x, int y, int w, int h, long gameTime) {
        int center = y + (int) ((gameTime % 90L) * h / 90.0f);
        int halfHeight = Math.max(4, h / 9);
        for (int py = Math.max(y, center - halfHeight); py < Math.min(y + h, center + halfHeight); py++) {
            float dist = Math.abs(py - center) / (float) halfHeight;
            int alpha = Math.round((1.0f - dist) * 20.0f);
            int color = withAlpha(0xFFE7FFE8, alpha);
            drawHBlendLine(image, x + 2, py, Math.max(1, w - 4), color);
        }
    }

    private static void drawReflection(NativeImage image, int x, int y, int w, int h) {
        int rx0 = x + Math.round(w * 0.08f);
        int ry0 = y + Math.round(h * 0.10f);
        int rw = Math.round(w * 0.52f);
        int rh = Math.round(h * 0.20f);
        for (int py = 0; py < rh; py++) {
            float vy = py / (float) Math.max(1, rh - 1);
            for (int px = 0; px < rw; px++) {
                float vx = px / (float) Math.max(1, rw - 1);
                float alpha = (1.0f - vy) * (1.0f - vx * 0.75f);
                if (alpha <= 0.0f) {
                    continue;
                }
                blendPixel(image, rx0 + px, ry0 + py, withAlpha(0xFFFFFFFF, Math.round(alpha * 24.0f)));
            }
        }
    }

    private static void drawScanlines(NativeImage image, int x, int y, int w, int h) {
        for (int py = y; py < y + h; py++) {
            int alpha = ((py - y) & 1) == 0 ? 16 : 8;
            drawHBlendLine(image, x, py, w, withAlpha(0xFF050907, alpha));
        }
    }

    private static void drawVignette(NativeImage image, int x, int y, int w, int h) {
        float cx = x + (w - 1) / 2.0f;
        float cy = y + (h - 1) / 2.0f;
        float rx = w / 2.0f;
        float ry = h / 2.0f;
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                float dx = (px - cx) / rx;
                float dy = (py - cy) / ry;
                float radial = dx * dx + dy * dy;
                float edge = Math.min(Math.min(px - x, x + w - 1 - px), Math.min(py - y, y + h - 1 - py));
                float edgeFade = edge < 10.0f ? 1.0f - edge / 10.0f : 0.0f;
                float radialFade = radial > 0.58f ? (radial - 0.58f) / 0.42f : 0.0f;
                float alpha = Mth.clamp(Math.max(edgeFade * 0.55f, radialFade * 0.72f), 0.0f, 1.0f);
                if (alpha > 0.0f) {
                    blendPixel(image, px, py, withAlpha(0xFF06110D, Math.round(alpha * 180.0f)));
                }
            }
        }
    }

    private static void drawPowerLed(NativeImage image, int x, int y, int w, int h, long gameTime) {
        float breathe = 0.5f + 0.5f * (float) Math.sin(gameTime * 0.08f);
        int ledColor = withAlpha(lerpColor(0xFF137D22, 0xFF3EFF62, breathe), 220);
        int ledX = x + w - 11;
        int ledY = y + h - 10;
        TextureGraphRenderer.fillRect(image, ledX - 1, ledY - 1, 5, 5, 0xFF0A120C);
        TextureGraphRenderer.drawBorder(image, ledX - 1, ledY - 1, 5, 5, 1, 0xFF1F3026);
        TextureGraphRenderer.fillRect(image, ledX, ledY, 3, 3, 0xFF1A3A22);
        blendPixel(image, ledX + 1, ledY + 1, ledColor);
        blendPixel(image, ledX, ledY + 1, withAlpha(0xFF7FFF9A, 70));
        blendPixel(image, ledX + 2, ledY + 1, withAlpha(0xFF7FFF9A, 70));
    }

    private static void drawCloud(NativeImage image, int x, int y, int w, int h, int color) {
        for (int py = 0; py < h; py++) {
            float ny = (py - h / 2.0f) / Math.max(1.0f, h / 2.0f);
            for (int px = 0; px < w; px++) {
                float nx = (px - w / 2.0f) / Math.max(1.0f, w / 2.0f);
                float dist = nx * nx + ny * ny;
                if (dist > 1.0f) {
                    continue;
                }
                int alpha = Math.round(((color >>> 24) & 0xFF) * (1.0f - dist));
                blendPixel(image, x + px, y + py, withAlpha(color, alpha));
            }
        }
    }

    private static void drawHLine(NativeImage image, int x, int y, int w, int argb) {
        TextureGraphRenderer.fillRect(image, x, y, w, 1, argb);
    }

    private static void drawHBlendLine(NativeImage image, int x, int y, int w, int argb) {
        for (int px = x; px < x + w; px++) {
            blendPixel(image, px, y, argb);
        }
    }

    private static void blendPixel(NativeImage image, int x, int y, int argb) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            return;
        }

        int srcA = (argb >>> 24) & 0xFF;
        if (srcA <= 0) {
            return;
        }

        int srcR = (argb >>> 16) & 0xFF;
        int srcG = (argb >>> 8) & 0xFF;
        int srcB = argb & 0xFF;

        int dst = image.getPixelRGBA(x, y);
        int dstR = dst & 0xFF;
        int dstG = (dst >>> 8) & 0xFF;
        int dstB = (dst >>> 16) & 0xFF;
        int dstA = (dst >>> 24) & 0xFF;

        float alpha = srcA / 255.0f;
        int outR = Math.round(srcR * alpha + dstR * (1.0f - alpha));
        int outG = Math.round(srcG * alpha + dstG * (1.0f - alpha));
        int outB = Math.round(srcB * alpha + dstB * (1.0f - alpha));
        int outA = Math.max(dstA, srcA);
        image.setPixelRGBA(x, y, outA << 24 | outB << 16 | outG << 8 | outR);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        int aA = (a >>> 24) & 0xFF;
        int aR = (a >>> 16) & 0xFF;
        int aG = (a >>> 8) & 0xFF;
        int aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF;
        int bR = (b >>> 16) & 0xFF;
        int bG = (b >>> 8) & 0xFF;
        int bB = b & 0xFF;
        int outA = Math.round(Mth.lerp(clamped, aA, bA));
        int outR = Math.round(Mth.lerp(clamped, aR, bR));
        int outG = Math.round(Mth.lerp(clamped, aG, bG));
        int outB = Math.round(Mth.lerp(clamped, aB, bB));
        return outA << 24 | outR << 16 | outG << 8 | outB;
    }

    private static void cleanupStaleTextures(long currentTick) {
        Iterator<Map.Entry<BlockPos, TextureEntry>> iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, TextureEntry> entry = iterator.next();
            if (currentTick - entry.getValue().lastAccessTick > MAX_IDLE_TICKS) {
                disposeEntry(entry.getValue());
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
            CreateStats.LOGGER.warn("Failed to dispose Factory Logic Controller texture", ex);
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
        private long lastUpdateTick;
        private long lastAccessTick;
        private boolean needsRedraw;
    }

    private record CellPalette(int baseColor, int highlightColor, int glowColor, int glowAlpha, int cornerSparkle) {
    }
}
