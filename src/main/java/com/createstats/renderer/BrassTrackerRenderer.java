package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BrassTrackerRenderer
{
    private static final int BRASS = -3630276;
    private static final int BRASS_DIM = -7640812;
    private static final int COPPER = -4689101;
    private static final int DARK_IRON = -13882320;
    private static final int LAMP_OFF = -11184811;
    
    private BrassTrackerRenderer() {
    }
    
    public static float animateProgress(BlockPos pos, int slotIndex, float targetProgress) {
        final float clamped = clamp01(targetProgress);
        if (pos == null) {
            return clamped;
        }
        final NeedlePhysics needle = NeedlePhysicsRegistry.getOrCreate(pos, slotIndex);
        needle.setTarget(clamped * 4.712389f);
        needle.tick();
        return clamp01(needle.getCurrentAngle() / 4.712389f);
    }
    
    public static void drawHorizontalTexture(NativeImage image, int x, int y, int w, int h, BlockPos pos, int slotIndex, float targetProgress, float bandStart, float bandEnd, int bandColor, float markerA, int markerAColor, float markerB, int markerBColor, int carriageColor, boolean pulse, long tick) {
        if (w < 8 || h < 6) {
            return;
        }
        final int railH = Math.max(6, h / 3);
        final int railY = y + (h - railH) / 2;
        final int inset = 2;
        TextureGraphRenderer.fillRect(image, x, railY, w, railH, DARK_IRON);
        TextureGraphRenderer.fillRect(image, x + 1, railY + 1, w - 2, railH - 2, BRASS_DIM);
        TextureGraphRenderer.fillRect(image, x + 1, railY + 1, w - 2, Math.max(1, railH / 4), brighten(BRASS, 1.16f));
        if (!Float.isNaN(bandStart) && !Float.isNaN(bandEnd) && bandEnd > bandStart) {
            final int bandX1 = x + inset + Math.round(clamp01(bandStart) * (w - inset * 2));
            final int bandX2 = x + inset + Math.round(clamp01(bandEnd) * (w - inset * 2));
            TextureGraphRenderer.fillRect(image, Math.min(bandX1, bandX2), railY + 1, Math.max(1, Math.abs(bandX2 - bandX1)), railH - 2, darken(bandColor, 0.82f));
            TextureGraphRenderer.fillRect(image, Math.min(bandX1, bandX2), railY + 1, Math.max(1, Math.abs(bandX2 - bandX1)), Math.max(1, railH / 5), brighten(bandColor, 1.12f));
        }
        drawMarkerTexture(image, x, railY, w, railH, markerA, markerAColor);
        drawMarkerTexture(image, x, railY, w, railH, markerB, markerBColor);
        final int capR = Math.max(2, railH / 2);
        TextureGraphRenderer.fillCircle(image, x + 1, railY + railH / 2, capR, COPPER);
        TextureGraphRenderer.fillCircle(image, x + w - 2, railY + railH / 2, capR, COPPER);
        final float smooth = animateProgress(pos, slotIndex, targetProgress);
        final int carriageW = Math.max(8, w / 15);
        final int carriageX = clamp(x + Math.round(smooth * (w - 1)) - carriageW / 2, x + 1, x + w - carriageW - 1);
        TextureGraphRenderer.fillRect(image, carriageX, y, carriageW, h, DARK_IRON);
        TextureGraphRenderer.fillRect(image, carriageX + 1, y + 1, carriageW - 2, h - 2, carriageColor);
        TextureGraphRenderer.fillRect(image, carriageX + 1, y + 1, carriageW - 2, Math.max(1, h / 4), brighten(carriageColor, 1.18f));
        final int sightY = y + h / 2;
        TextureGraphRenderer.fillRect(image, carriageX + carriageW / 2, sightY - 3, 1, 7, -1);
        TextureGraphRenderer.fillRect(image, carriageX + carriageW / 2 - 3, sightY, 7, 1, -1);
        if (pulse && tick / 4L % 2L == 0L) {
            TextureGraphRenderer.drawBorder(image, carriageX - 1, y - 1, carriageW + 2, h + 2, 1, brighten(markerBColor, 1.25f));
        }
    }
    
    public static void drawHorizontalGui(GuiGraphics g, int x, int y, int w, int h, BlockPos pos, int slotIndex, float targetProgress, float bandStart, float bandEnd, int bandColor, float markerA, int markerAColor, float markerB, int markerBColor, int carriageColor, boolean pulse, long tick) {
        if (w < 8 || h < 6) {
            return;
        }
        final int railH = Math.max(8, h / 3);
        final int railY = y + (h - railH) / 2;
        final int inset = 2;
        border(g, x, railY, w, railH, DARK_IRON);
        g.fill(x + 1, railY + 1, x + w - 1, railY + railH - 1, BRASS_DIM);
        g.fill(x + 1, railY + 1, x + w - 1, railY + 1 + Math.max(1, railH / 4), brighten(BRASS, 1.16f));
        if (!Float.isNaN(bandStart) && !Float.isNaN(bandEnd) && bandEnd > bandStart) {
            final int bandX1 = x + inset + Math.round(clamp01(bandStart) * (w - inset * 2));
            final int bandX2 = x + inset + Math.round(clamp01(bandEnd) * (w - inset * 2));
            g.fill(Math.min(bandX1, bandX2), railY + 1, Math.max(bandX1, bandX2), railY + railH - 1, darken(bandColor, 0.82f));
            g.fill(Math.min(bandX1, bandX2), railY + 1, Math.max(bandX1, bandX2), railY + 1 + Math.max(1, railH / 5), brighten(bandColor, 1.12f));
        }
        drawMarkerGui(g, x, railY, w, railH, markerA, markerAColor);
        drawMarkerGui(g, x, railY, w, railH, markerB, markerBColor);
        final int capR = Math.max(2, railH / 2);
        g.fill(x, railY + railH / 2 - capR, x + capR, railY + railH / 2 + capR + 1, COPPER);
        g.fill(x + w - capR, railY + railH / 2 - capR, x + w, railY + railH / 2 + capR + 1, COPPER);
        final float smooth = animateProgress(pos, slotIndex, targetProgress);
        final int carriageW = Math.max(12, w / 15);
        final int carriageX = clamp(x + Math.round(smooth * (w - 1)) - carriageW / 2, x + 1, x + w - carriageW - 1);
        border(g, carriageX, y, carriageW, h, DARK_IRON);
        g.fill(carriageX + 1, y + 1, carriageX + carriageW - 1, y + h - 1, carriageColor);
        g.fill(carriageX + 1, y + 1, carriageX + carriageW - 1, y + 1 + Math.max(1, h / 4), brighten(carriageColor, 1.18f));
        final int sightY = y + h / 2;
        g.fill(carriageX + carriageW / 2, sightY - 4, carriageX + carriageW / 2 + 1, sightY + 4, -1);
        g.fill(carriageX + carriageW / 2 - 4, sightY, carriageX + carriageW / 2 + 4, sightY + 1, -1);
        if (pulse && tick / 4L % 2L == 0L) {
            border(g, carriageX - 1, y - 1, carriageW + 2, h + 2, brighten(markerBColor, 1.25f));
        }
    }
    
    public static void drawLampTexture(NativeImage image, int cx, int cy, int radius, boolean on, int onColor, long tick) {
        TextureGraphRenderer.fillCircle(image, cx, cy, radius + 1, DARK_IRON);
        TextureGraphRenderer.fillCircle(image, cx, cy, radius, on ? darken(onColor, 0.75f) : LAMP_OFF);
        if (on) {
            final int inner = (tick / 5L % 2L == 0L) ? brighten(onColor, 1.18f) : onColor;
            TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(1, radius - 2), inner);
        }
        else {
            TextureGraphRenderer.fillCircle(image, cx, cy, Math.max(1, radius - 2), -13619152);
        }
    }
    
    public static void drawLampGui(GuiGraphics g, int cx, int cy, int radius, boolean on, int onColor, long tick) {
        g.fill(cx - radius - 1, cy - radius - 1, cx + radius + 2, cy + radius + 2, DARK_IRON);
        g.fill(cx - radius, cy - radius, cx + radius + 1, cy + radius + 1, on ? darken(onColor, 0.75f) : LAMP_OFF);
        if (on) {
            final int inner = (tick / 5L % 2L == 0L) ? brighten(onColor, 1.18f) : onColor;
            g.fill(cx - radius + 2, cy - radius + 2, cx + radius - 1, cy + radius - 1, inner);
        }
        else {
            g.fill(cx - radius + 2, cy - radius + 2, cx + radius - 1, cy + radius - 1, -13619152);
        }
    }
    
    private static void drawMarkerTexture(NativeImage image, int x, int railY, int w, int railH, float marker, int color) {
        if (Float.isNaN(marker)) {
            return;
        }
        final int mx = x + Math.round(clamp01(marker) * (w - 1));
        TextureGraphRenderer.fillRect(image, mx, railY - 4, 1, railH + 8, color);
    }
    
    private static void drawMarkerGui(GuiGraphics g, int x, int railY, int w, int railH, float marker, int color) {
        if (Float.isNaN(marker)) {
            return;
        }
        final int mx = x + Math.round(clamp01(marker) * (w - 1));
        g.fill(mx, railY - 5, mx + 1, railY + railH + 5, color);
    }
    
    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
    
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static int brighten(int color, float factor) {
        return scaleRgb(color, factor);
    }
    
    private static int darken(int color, float factor) {
        return scaleRgb(color, factor);
    }
    
    private static int scaleRgb(int color, float factor) {
        final int a = color >>> 24;
        final int alpha = (a == 0) ? 255 : a;
        final int r = Math.max(0, Math.min(255, Math.round((color >> 16 & 0xFF) * factor)));
        final int g = Math.max(0, Math.min(255, Math.round((color >> 8 & 0xFF) * factor)));
        final int b = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return alpha << 24 | r << 16 | g << 8 | b;
    }
}
