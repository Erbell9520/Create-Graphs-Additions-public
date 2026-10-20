package com.createstats.renderer;

import com.createstats.display.DisplayTheme;
import com.createstats.renderer.DisplayTextureManager.PanelState;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PanelAnimationRenderer {
    private static final int CORNER_LAMP_SIZE = 4;
    private static final int CORNER_LAMP_MARGIN = 6;

    private PanelAnimationRenderer() {
    }

    public static void applyStateVisuals(NativeImage image, PanelState state, int bootProgress,
                                         long currentTick, DisplayTheme theme) {
        if (state == null) {
            return;
        }

        renderCornerLamps(image, state, currentTick, theme);

        switch (state) {
            case BOOTING:
                renderBootSweepAnimation(image, bootProgress, theme);
                break;
            case LIVE:
                break;
            case STALE:
                renderStaleBadgeOverlay(image, theme);
                break;
            case SETUP_REQUIRED:
                renderPulsingCornerHighlight(image, currentTick, theme);
                break;
            default:

                break;
        }
    }

    private static void renderCornerLamps(NativeImage image, PanelState state, long tick, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();

        int lampColor;
        boolean shouldPulse = false;

        switch (state) {
            case UNCONFIGURED:
            case AWAITING_DATA:
                lampColor = theme.dim;
                break;
            case SETUP_REQUIRED:
                lampColor = 0xFFFFAA00;
                shouldPulse = true;
                break;
            case BOOTING:
                lampColor = theme.accent;
                shouldPulse = true;
                break;
            case LIVE:
                lampColor = 0xFF00FF00;
                break;
            case STALE:
                lampColor = 0xFFFF6600;
                break;
            case ERROR:
                lampColor = 0xFFFF0000;
                shouldPulse = true;
                break;
            default:
                lampColor = theme.dim;
        }

        if (shouldPulse) {
            float pulseFactor = (float)Math.sin((tick % 40) * Math.PI / 20.0) * 0.5f + 0.5f;
            int alpha = (int)(pulseFactor * 255);
            lampColor = (lampColor & 0x00FFFFFF) | (alpha << 24);
        }

        final int size = Math.max(CORNER_LAMP_SIZE, Math.min(w, h) / 64);
        final int margin = Math.max(CORNER_LAMP_MARGIN, size + 2);

        fillRectBlended(image, margin, margin, size, size, lampColor);

        fillRectBlended(image, w - margin - size, margin, size, size, lampColor);

        fillRectBlended(image, margin, h - margin - size, size, size, lampColor);

        fillRectBlended(image, w - margin - size, h - margin - size, size, size, lampColor);
    }

    private static void renderBootSweepAnimation(NativeImage image, int progress, DisplayTheme theme) {
        if (progress <= 0 || progress > 100) {
            return;
        }

        final int w = image.getWidth();
        final int h = image.getHeight();
        final int sweepHeight = Math.max(2, h / 64);
        final int sweepY = h / 2 - sweepHeight / 2;

        final int sweepWidth = (w * progress) / 100;

        final int baseColor = theme.accent;

        for (int x = 0; x < sweepWidth; x++) {
            float fadeDistance = Math.min(20, sweepWidth / 4);
            float distanceFromEdge = sweepWidth - x;
            float fadeFactor = Math.min(1.0f, distanceFromEdge / fadeDistance);

            int alpha = (int)(fadeFactor * 128);
            int sweepColor = (baseColor & 0x00FFFFFF) | (alpha << 24);

            for (int y = sweepY; y < sweepY + sweepHeight; y++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    TextureGraphRenderer.setPixelBlended(image, x, y, sweepColor);
                }
            }
        }
    }

    private static void renderStaleBadgeOverlay(NativeImage image, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int textScale = TextureGraphRenderer.getTextScale(w);

        final int stripHeight = Math.max(14, BitmapFont.textHeight(textScale) + 7);
        final int stripY = 0;
        final int overlayColor = 0x99000000;

        for (int y = stripY; y < stripY + stripHeight; y++) {
            for (int x = 0; x < w; x++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    TextureGraphRenderer.setPixelBlended(image, x, y, overlayColor);
                }
            }
        }
        fillRectBlended(image, 0, stripHeight - 2, w, 2, 0xCCFF6600);
        drawBorderBlended(image, 0, 0, w, h, 2, 0x99FF6600);

        final String warningText = "DATA STALE - SNAPSHOT";
        BitmapFont.drawCentered(image, warningText, 0, stripY + (stripHeight - BitmapFont.textHeight(textScale)) / 2,
                                w, 0xFFFF6600, textScale);
    }

    private static void renderPulsingCornerHighlight(NativeImage image, long tick, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int borderWidth = 3;

        float pulseFactor = (float)Math.sin((tick % 40) * Math.PI / 20.0) * 0.5f + 0.5f;
        int alpha = (int)(pulseFactor * 128);
        int highlightColor = 0xFFAA00 | (alpha << 24);

        drawBorderBlended(image, 0, 0, w, h, borderWidth, highlightColor);
    }

    private static void drawBorderBlended(NativeImage image, int x, int y, int width, int height, int thickness, int color) {
        fillRectBlended(image, x, y, width, thickness, color);
        fillRectBlended(image, x, y + height - thickness, width, thickness, color);
        fillRectBlended(image, x, y, thickness, height, color);
        fillRectBlended(image, x + width - thickness, y, thickness, height, color);
    }

    private static void fillRectBlended(NativeImage image, int x, int y, int width, int height, int color) {
        final int xEnd = Math.min(x + width, image.getWidth());
        final int yEnd = Math.min(y + height, image.getHeight());
        for (int pixelY = Math.max(0, y); pixelY < yEnd; ++pixelY) {
            for (int pixelX = Math.max(0, x); pixelX < xEnd; ++pixelX) {
                TextureGraphRenderer.setPixelBlended(image, pixelX, pixelY, color);
            }
        }
    }
}
