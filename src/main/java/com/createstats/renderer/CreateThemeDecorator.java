package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;

public final class CreateThemeDecorator
{
    private static final int BRASS = -3630276;
    private static final int BRASS_DIM = -7640812;
    private static final int COPPER = -4689101;
    private static final int DARK_IRON = -13882320;
    
    private CreateThemeDecorator() {
    }
    
    public static void decorate(NativeImage image, int bgColor, int contentX, int contentY, int contentW, int contentH) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        drawCogWatermark(image, bgColor, w / 2, h / 2, Math.min(w, h) * 2 / 5);
        drawPipeBorder(image, bgColor, 0, 0, w, h);
        final int cornerSize = Math.max(8, Math.min(w, h) / 10);
        drawCornerGear(image, bgColor, 2, 2, cornerSize, false, false);
        drawCornerGear(image, bgColor, w - 2 - cornerSize, 2, cornerSize, true, false);
        drawCornerGear(image, bgColor, 2, h - 2 - cornerSize, cornerSize, false, true);
        drawCornerGear(image, bgColor, w - 2 - cornerSize, h - 2 - cornerSize, cornerSize, true, true);
        final int wrenchSize = Math.max(6, Math.min(w, h) / 12);
        drawWrenchIcon(image, bgColor, w - wrenchSize - 6, 6, wrenchSize);
    }
    
    private static void drawCogWatermark(NativeImage image, int bgColor, int cx, int cy, int radius) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final double angle = 0.4487989505128276;
        final int teeth = 12;
        final int innerR = radius * 55 / 100;
        final int outerR = radius * 75 / 100;
        final int hubR = radius * 30 / 100;
        final int holeR = radius * 15 / 100;
        final int cogBlended = opaqueBlend(bgColor, -7640812, 0.08);
        final int hubBlended = opaqueBlend(bgColor, -3630276, 0.06);
        final int abgrCog = BitmapFont.argbToAbgr(cogBlended);
        final int abgrHub = BitmapFont.argbToAbgr(hubBlended);
        final double toothAngle = 6.283185307179586 / teeth;
        final double spokeAngle = 1.0471975511965976;
        final double spokeWidth = spokeAngle * 0.15;
        final int innerR2 = innerR * innerR;
        final int outerR2 = outerR * outerR;
        final int hubR2 = hubR * hubR;
        final int holeR2 = holeR * holeR;
        for (int py = cy - outerR - 2; py <= cy + outerR + 2; ++py) {
            if (py >= 0) {
                if (py < imgH) {
                    for (int px = cx - outerR - 2; px <= cx + outerR + 2; ++px) {
                        if (px >= 0) {
                            if (px < imgW) {
                                final int dx = px - cx;
                                final int dy = py - cy;
                                final int dist2 = dx * dx + dy * dy;
                                if (dist2 <= hubR2 && dist2 > holeR2) {
                                    image.setPixelRGBA(px, py, abgrHub);
                                }
                                else if (dist2 >= innerR2 && dist2 <= outerR2) {
                                    double pixAngle = Math.atan2(dy, dx) - angle;
                                    pixAngle = (pixAngle % 6.283185307179586 + 6.283185307179586) % 6.283185307179586;
                                    final double within = pixAngle % toothAngle;
                                    if (within < toothAngle * 0.55) {
                                        image.setPixelRGBA(px, py, abgrCog);
                                    }
                                }
                                else if (dist2 > hubR2 && dist2 < innerR2) {
                                    double pixAngle = Math.atan2(dy, dx) - angle;
                                    pixAngle = (pixAngle % 6.283185307179586 + 6.283185307179586) % 6.283185307179586;
                                    final double within = pixAngle % spokeAngle;
                                    if (within < spokeWidth || within > spokeAngle - spokeWidth) {
                                        image.setPixelRGBA(px, py, abgrCog);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static void drawPipeBorder(NativeImage image, int bgColor, int x, int y, int w, int h) {
        final int thickness = Math.max(2, Math.min(w, h) / 48);
        final int ironColor = opaqueBlend(bgColor, -13882320, 0.85);
        final int brassColor = opaqueBlend(bgColor, -7640812, 0.45);
        final int abgrIron = BitmapFont.argbToAbgr(ironColor);
        final int abgrBrass = BitmapFont.argbToAbgr(brassColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int t = 0; t < thickness; ++t) {
            for (int px = x; px < x + w; ++px) {
                safePx(image, px, y + t, imgW, imgH, abgrIron);
                safePx(image, px, y + h - 1 - t, imgW, imgH, abgrIron);
            }
            for (int py = y; py < y + h; ++py) {
                safePx(image, x + t, py, imgW, imgH, abgrIron);
                safePx(image, x + w - 1 - t, py, imgW, imgH, abgrIron);
            }
        }
        final int t2 = thickness;
        for (int px = x + t2; px < x + w - t2; ++px) {
            safePx(image, px, y + t2, imgW, imgH, abgrBrass);
            safePx(image, px, y + h - 1 - t2, imgW, imgH, abgrBrass);
        }
        for (int py = y + t2; py < y + h - t2; ++py) {
            safePx(image, x + t2, py, imgW, imgH, abgrBrass);
            safePx(image, x + w - 1 - t2, py, imgW, imgH, abgrBrass);
        }
        final int rivetSpacing = Math.max(12, Math.min(w, h) / 8);
        final int rivetR = Math.max(1, thickness / 2);
        final int rivetColor = opaqueBlend(bgColor, -4689101, 0.7);
        final int abgrRivet = BitmapFont.argbToAbgr(rivetColor);
        for (int i = rivetSpacing; i < w - rivetSpacing; i += rivetSpacing) {
            drawRivet(image, x + i, y + thickness / 2, rivetR, abgrRivet);
            drawRivet(image, x + i, y + h - 1 - thickness / 2, rivetR, abgrRivet);
        }
        for (int i = rivetSpacing; i < h - rivetSpacing; i += rivetSpacing) {
            drawRivet(image, x + thickness / 2, y + i, rivetR, abgrRivet);
            drawRivet(image, x + w - 1 - thickness / 2, y + i, rivetR, abgrRivet);
        }
    }
    
    private static void drawRivet(NativeImage image, int cx, int cy, int r, int abgr) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int dy = -r; dy <= r; ++dy) {
            for (int dx = -r; dx <= r; ++dx) {
                if (dx * dx + dy * dy <= r * r) {
                    safePx(image, cx + dx, cy + dy, imgW, imgH, abgr);
                }
            }
        }
    }
    
    private static void drawCornerGear(NativeImage image, int bgColor, int x, int y, int size, boolean flipX, boolean flipY) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int gearColor = opaqueBlend(bgColor, -7640812, 0.25);
        final int hubColor = opaqueBlend(bgColor, -3630276, 0.18);
        final int abgr = BitmapFont.argbToAbgr(gearColor);
        final int abgrH = BitmapFont.argbToAbgr(hubColor);
        final double angle = 0.6283185307179586;
        final int teeth = 8;
        final int outerR = size;
        final int innerR = size * 65 / 100;
        final int outerR2 = outerR * outerR;
        final int innerR2 = innerR * innerR;
        int hubLimitR2 = (int)(innerR * 0.6);
        hubLimitR2 *= hubLimitR2;
        final double toothAngle = 6.283185307179586 / teeth;
        for (int py = 0; py <= size; ++py) {
            for (int px = 0; px <= size; ++px) {
                final int dx = flipX ? (size - px) : px;
                final int dy = flipY ? (size - py) : py;
                final int dist2 = dx * dx + dy * dy;
                if (dist2 >= innerR2 && dist2 <= outerR2) {
                    double pixAngle = Math.atan2(dy, dx) - angle;
                    pixAngle = (pixAngle % 6.283185307179586 + 6.283185307179586) % 6.283185307179586;
                    final double within = pixAngle % toothAngle;
                    if (within < toothAngle * 0.5) {
                        safePx(image, x + px, y + py, imgW, imgH, abgr);
                    }
                }
                else if (dist2 < innerR2 && dist2 > hubLimitR2) {
                    safePx(image, x + px, y + py, imgW, imgH, abgrH);
                }
            }
        }
    }
    
    private static void drawWrenchIcon(NativeImage image, int bgColor, int x, int y, int size) {
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        final int wrenchColor = opaqueBlend(bgColor, -3630276, 0.12);
        final int abgr = BitmapFont.argbToAbgr(wrenchColor);
        final int headR = size * 35 / 100;
        final int headCx = x + size - headR;
        final int headCy = y + headR;
        final int headR2outer = headR * headR;
        int headR2inner = (int)(headR * 0.5);
        headR2inner *= headR2inner;
        for (int py = -headR; py <= headR; ++py) {
            for (int px = -headR; px <= headR; ++px) {
                final int dist2 = px * px + py * py;
                if (dist2 >= headR2inner && dist2 <= headR2outer) {
                    final double a = Math.atan2(-py, px);
                    if (a <= 0.3 || a >= 1.2) {
                        safePx(image, headCx + px, headCy + py, imgW, imgH, abgr);
                    }
                }
            }
        }
        final int handleLen = size * 70 / 100;
        final int thickness = Math.max(1, size / 8);
        for (int i = 0; i < handleLen; ++i) {
            final int hx = headCx - i;
            final int hy = headCy + i;
            for (int t = -thickness / 2; t <= thickness / 2; ++t) {
                safePx(image, hx + t, hy, imgW, imgH, abgr);
            }
        }
    }
    
    private static int opaqueBlend(int bgArgb, int fgArgb, double opacity) {
        final int bgR = bgArgb >> 16 & 0xFF;
        final int bgG = bgArgb >> 8 & 0xFF;
        final int bgB = bgArgb & 0xFF;
        final int fgR = fgArgb >> 16 & 0xFF;
        final int fgG = fgArgb >> 8 & 0xFF;
        final int fgB = fgArgb & 0xFF;
        final int r = (int)(bgR + (fgR - bgR) * opacity);
        final int g = (int)(bgG + (fgG - bgG) * opacity);
        final int b = (int)(bgB + (fgB - bgB) * opacity);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    
    private static void safePx(NativeImage image, int x, int y, int imgW, int imgH, int abgr) {
        if (x >= 0 && x < imgW && y >= 0 && y < imgH) {
            image.setPixelRGBA(x, y, abgr);
        }
    }
}
