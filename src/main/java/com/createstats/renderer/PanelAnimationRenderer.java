package com.createstats.renderer;

import com.createstats.display.DisplayTheme;
import com.createstats.renderer.DisplayTextureManager.PanelState;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders state-based animations and overlays for Stats Display Panels.
 * Handles corner indicators, boot animations, and stale overlays.
 */
@OnlyIn(Dist.CLIENT)
public final class PanelAnimationRenderer {
    
    private static final int CORNER_LAMP_SIZE = 4;
    private static final int CORNER_LAMP_MARGIN = 6;
    
    private PanelAnimationRenderer() {
        // Utility class
    }
    
    /**
     * Apply state-specific animations and overlays to the panel texture.
     * Called after base content is rendered.
     */
    public static void applyStateVisuals(NativeImage image, PanelState state, int bootProgress, 
                                         long currentTick, DisplayTheme theme) {
        if (state == null) {
            return; // Defensive check
        }
        
        // Render corner lamps for all states (color-coded)
        renderCornerLamps(image, state, currentTick, theme);
        
        // State-specific overlays
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
                // UNCONFIGURED, AWAITING_DATA, ERROR: corner lamps only
                break;
        }
    }
    
    /**
     * Render corner indicator lamps in all four corners.
     * Color based on panel state.
     */
    private static void renderCornerLamps(NativeImage image, PanelState state, long tick, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        
        // Determine lamp color and pulse behavior
        int lampColor;
        boolean shouldPulse = false;
        
        switch (state) {
            case UNCONFIGURED:
            case AWAITING_DATA:
                lampColor = theme.dim; // Gray/dim
                break;
            case SETUP_REQUIRED:
                lampColor = 0xFFFFAA00; // Yellow/amber
                shouldPulse = true;
                break;
            case BOOTING:
                lampColor = theme.accent; // Blue (boot progress)
                shouldPulse = true;
                break;
            case LIVE:
                lampColor = 0xFF00FF00; // Green (healthy)
                break;
            case STALE:
                lampColor = 0xFFFF6600; // Orange/red warning
                break;
            case ERROR:
                lampColor = 0xFFFF0000; // Red error
                shouldPulse = true;
                break;
            default:
                lampColor = theme.dim;
        }
        
        // Apply pulse effect (50% alpha oscillation over 40 ticks)
        if (shouldPulse) {
            float pulseFactor = (float)Math.sin((tick % 40) * Math.PI / 20.0) * 0.5f + 0.5f;
            int alpha = (int)(pulseFactor * 255);
            lampColor = (lampColor & 0x00FFFFFF) | (alpha << 24);
        }
        
        // Draw 4 corner lamps
        final int size = Math.max(CORNER_LAMP_SIZE, Math.min(w, h) / 64);
        final int margin = Math.max(CORNER_LAMP_MARGIN, size + 2);
        
        // Top-left
        fillRectBlended(image, margin, margin, size, size, lampColor);
        // Top-right
        fillRectBlended(image, w - margin - size, margin, size, size, lampColor);
        // Bottom-left
        fillRectBlended(image, margin, h - margin - size, size, size, lampColor);
        // Bottom-right
        fillRectBlended(image, w - margin - size, h - margin - size, size, size, lampColor);
    }
    
    /**
     * Render horizontal boot sweep animation (0-100% progress).
     */
    private static void renderBootSweepAnimation(NativeImage image, int progress, DisplayTheme theme) {
        if (progress <= 0 || progress > 100) {
            return;
        }
        
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int sweepHeight = Math.max(2, h / 64); // Thin horizontal line
        final int sweepY = h / 2 - sweepHeight / 2; // Centered vertically
        
        // Calculate sweep position (left to right)
        final int sweepWidth = (w * progress) / 100;
        
        // Draw sweep with gradient effect (brighter at leading edge)
        final int baseColor = theme.accent; // Use theme accent color (blue)
        
        for (int x = 0; x < sweepWidth; x++) {
            // Fade effect: brighter near the leading edge
            float fadeDistance = Math.min(20, sweepWidth / 4);
            float distanceFromEdge = sweepWidth - x;
            float fadeFactor = Math.min(1.0f, distanceFromEdge / fadeDistance);
            
            int alpha = (int)(fadeFactor * 128); // Max 50% opacity
            int sweepColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
            
            // Draw vertical strip
            for (int y = sweepY; y < sweepY + sweepHeight; y++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    TextureGraphRenderer.setPixelBlended(image, x, y, sweepColor);
                }
            }
        }
    }
    
    /**
     * Render "DATA STALE" warning badge overlay.
     */
    private static void renderStaleBadgeOverlay(NativeImage image, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int textScale = TextureGraphRenderer.getTextScale(w);
        
        // Semi-transparent dark overlay strip
        final int stripHeight = Math.max(14, BitmapFont.textHeight(textScale) + 7);
        final int stripY = 0;
        final int overlayColor = 0x99000000; // 60% black
        
        for (int y = stripY; y < stripY + stripHeight; y++) {
            for (int x = 0; x < w; x++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    TextureGraphRenderer.setPixelBlended(image, x, y, overlayColor);
                }
            }
        }
        fillRectBlended(image, 0, stripHeight - 2, w, 2, 0xCCFF6600);
        drawBorderBlended(image, 0, 0, w, h, 2, 0x99FF6600);
        
        // Draw warning text
        final String warningText = "DATA STALE - SNAPSHOT";
        BitmapFont.drawCentered(image, warningText, 0, stripY + (stripHeight - BitmapFont.textHeight(textScale)) / 2, 
                                w, 0xFFFF6600, textScale); // Orange warning color
    }
    
    /**
     * Render pulsing corner highlight for setup required state.
     */
    private static void renderPulsingCornerHighlight(NativeImage image, long tick, DisplayTheme theme) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int borderWidth = 3;
        
        // Pulse alpha over 40 ticks
        float pulseFactor = (float)Math.sin((tick % 40) * Math.PI / 20.0) * 0.5f + 0.5f;
        int alpha = (int)(pulseFactor * 128); // 0-50% opacity
        int highlightColor = 0xFFAA00 | (alpha << 24); // Yellow with variable alpha
        
        // Draw border overlay
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
