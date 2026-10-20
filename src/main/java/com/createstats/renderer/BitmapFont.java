package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BitmapFont
{
    public static final int CHAR_WIDTH = 5;
    public static final int CHAR_HEIGHT = 7;
    public static final int CHAR_SPACING = 1;
    public static final int LINE_SPACING = 2;
    public static final int CELL_WIDTH = 6;
    public static final int CELL_HEIGHT = 9;
    private static final byte[][] GLYPHS;

    private BitmapFont() {
    }

    private static void g(int idx, int r0, int r1, int r2, int r3, int r4, int r5, int r6) {
        BitmapFont.GLYPHS[idx] = new byte[] { (byte)r0, (byte)r1, (byte)r2, (byte)r3, (byte)r4, (byte)r5, (byte)r6 };
    }

    public static int argbToAbgr(int argb) {
        final int a = argb >> 24 & 0xFF;
        final int r = argb >> 16 & 0xFF;
        final int g = argb >> 8 & 0xFF;
        final int b = argb & 0xFF;
        return a << 24 | b << 16 | g << 8 | r;
    }

    public static int drawChar(NativeImage image, char c, int x, int y, int argbColor, int scale) {
        int idx = c - ' ';
        if (idx < 0 || idx >= BitmapFont.GLYPHS.length || BitmapFont.GLYPHS[idx] == null) {
            idx = 31;
        }
        final int abgr = argbToAbgr(argbColor);
        final byte[] glyph = BitmapFont.GLYPHS[idx];
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int row = 0; row < 7; ++row) {
            final int rowBits = glyph[row] & 0x1F;
            for (int col = 0; col < 5; ++col) {
                if ((rowBits & 1 << 4 - col) != 0x0) {
                    for (int sy = 0; sy < scale; ++sy) {
                        for (int sx = 0; sx < scale; ++sx) {
                            final int px = x + col * scale + sx;
                            final int py = y + row * scale + sy;
                            if (px >= 0 && px < imgW && py >= 0 && py < imgH) {
                                image.setPixelRGBA(px, py, abgr);
                            }
                        }
                    }
                }
            }
        }
        return 5 * scale + 1 * scale;
    }

    public static int drawString(NativeImage image, String text, int x, int y, int argbColor, int scale) {
        int cx = x;
        for (int i = 0; i < text.length(); ++i) {
            cx += drawChar(image, text.charAt(i), cx, y, argbColor, scale);
        }
        return cx - x;
    }

    public static void drawCentered(NativeImage image, String text, int x, int y, int width, int argbColor, int scale) {
        final int textW = measureString(text, scale);
        final int startX = x + (width - textW) / 2;
        drawString(image, text, startX, y, argbColor, scale);
    }

    public static void drawRight(NativeImage image, String text, int rightX, int y, int argbColor, int scale) {
        final int textW = measureString(text, scale);
        drawString(image, text, rightX - textW, y, argbColor, scale);
    }

    public static int measureString(String text, int scale) {
        if (text.isEmpty()) {
            return 0;
        }
        return text.length() * 6 * scale - 1 * scale;
    }

    public static float measurePixels(String text) {
        return text.isEmpty() ? 0.0f : text.length() * 6.0f - 1.0f;
    }

    public static void drawScaled(NativeImage image, String text, float x, float y, int argbColor,
                                  float scaleX, float scaleY) {
        if (text == null || text.isEmpty() || scaleX <= 0.0f || scaleY <= 0.0f) {
            return;
        }
        final int abgr = argbToAbgr(argbColor);
        final int imgW = image.getWidth();
        final int imgH = image.getHeight();
        for (int i = 0; i < text.length(); ++i) {
            final char c = text.charAt(i);
            int idx = c - ' ';
            if (idx < 0 || idx >= GLYPHS.length || GLYPHS[idx] == null) {
                idx = 31;
            }
            final byte[] glyph = GLYPHS[idx];
            final float gx = x + i * 6.0f * scaleX;
            for (int row = 0; row < CHAR_HEIGHT; ++row) {
                final int rowBits = glyph[row] & 0x1F;
                if (rowBits == 0) {
                    continue;
                }
                final int py0 = Math.round(y + row * scaleY);
                final int py1 = Math.max(py0 + 1, Math.round(y + (row + 1) * scaleY));
                for (int col = 0; col < CHAR_WIDTH; ++col) {
                    if ((rowBits & 1 << CHAR_WIDTH - 1 - col) == 0) {
                        continue;
                    }
                    final int px0 = Math.round(gx + col * scaleX);
                    final int px1 = Math.max(px0 + 1, Math.round(gx + (col + 1) * scaleX));
                    for (int py = Math.max(0, py0); py < Math.min(imgH, py1); ++py) {
                        for (int px = Math.max(0, px0); px < Math.min(imgW, px1); ++px) {
                            image.setPixelRGBA(px, py, abgr);
                        }
                    }
                }
            }
        }
    }

    public static int textHeight(int scale) {
        return 7 * scale;
    }

    public static byte[] glyphRows(char c) {
        int idx = c - ' ';
        if (idx < 0 || idx >= GLYPHS.length || GLYPHS[idx] == null) {
            idx = 31;
        }
        return GLYPHS[idx].clone();
    }

    static {
        GLYPHS = new byte[95][];
        g(0, 0, 0, 0, 0, 0, 0, 0);
        g(1, 4, 4, 4, 4, 4, 0, 4);
        g(2, 10, 10, 10, 0, 0, 0, 0);
        g(3, 10, 10, 31, 10, 31, 10, 10);
        g(4, 4, 15, 20, 14, 5, 30, 4);
        g(5, 24, 25, 2, 4, 8, 19, 3);
        g(6, 12, 18, 20, 8, 21, 18, 13);
        g(7, 4, 4, 8, 0, 0, 0, 0);
        g(8, 2, 4, 8, 8, 8, 4, 2);
        g(9, 8, 4, 2, 2, 2, 4, 8);
        g(10, 0, 4, 21, 14, 21, 4, 0);
        g(11, 0, 4, 4, 31, 4, 4, 0);
        g(12, 0, 0, 0, 0, 0, 4, 8);
        g(13, 0, 0, 0, 31, 0, 0, 0);
        g(14, 0, 0, 0, 0, 0, 0, 4);
        g(15, 1, 1, 2, 4, 8, 16, 16);
        g(16, 14, 17, 19, 21, 25, 17, 14);
        g(17, 4, 12, 4, 4, 4, 4, 14);
        g(18, 14, 17, 1, 2, 4, 8, 31);
        g(19, 14, 17, 1, 6, 1, 17, 14);
        g(20, 2, 6, 10, 18, 31, 2, 2);
        g(21, 31, 16, 30, 1, 1, 17, 14);
        g(22, 6, 8, 16, 30, 17, 17, 14);
        g(23, 31, 1, 2, 4, 8, 8, 8);
        g(24, 14, 17, 17, 14, 17, 17, 14);
        g(25, 14, 17, 17, 15, 1, 2, 12);
        g(26, 0, 0, 4, 0, 4, 0, 0);
        g(27, 0, 0, 4, 0, 4, 4, 8);
        g(28, 2, 4, 8, 16, 8, 4, 2);
        g(29, 0, 0, 31, 0, 31, 0, 0);
        g(30, 8, 4, 2, 1, 2, 4, 8);
        g(31, 14, 17, 1, 2, 4, 0, 4);
        g(32, 14, 17, 23, 21, 23, 16, 14);
        g(33, 14, 17, 17, 31, 17, 17, 17);
        g(34, 30, 17, 17, 30, 17, 17, 30);
        g(35, 14, 17, 16, 16, 16, 17, 14);
        g(36, 28, 18, 17, 17, 17, 18, 28);
        g(37, 31, 16, 16, 30, 16, 16, 31);
        g(38, 31, 16, 16, 30, 16, 16, 16);
        g(39, 14, 17, 16, 23, 17, 17, 15);
        g(40, 17, 17, 17, 31, 17, 17, 17);
        g(41, 14, 4, 4, 4, 4, 4, 14);
        g(42, 7, 2, 2, 2, 2, 18, 12);
        g(43, 17, 18, 20, 24, 20, 18, 17);
        g(44, 16, 16, 16, 16, 16, 16, 31);
        g(45, 17, 27, 21, 21, 17, 17, 17);
        g(46, 17, 25, 21, 19, 17, 17, 17);
        g(47, 14, 17, 17, 17, 17, 17, 14);
        g(48, 30, 17, 17, 30, 16, 16, 16);
        g(49, 14, 17, 17, 17, 21, 18, 13);
        g(50, 30, 17, 17, 30, 20, 18, 17);
        g(51, 14, 17, 16, 14, 1, 17, 14);
        g(52, 31, 4, 4, 4, 4, 4, 4);
        g(53, 17, 17, 17, 17, 17, 17, 14);
        g(54, 17, 17, 17, 17, 10, 10, 4);
        g(55, 17, 17, 17, 21, 21, 21, 10);
        g(56, 17, 17, 10, 4, 10, 17, 17);
        g(57, 17, 17, 10, 4, 4, 4, 4);
        g(58, 31, 1, 2, 4, 8, 16, 31);
        g(59, 14, 8, 8, 8, 8, 8, 14);
        g(60, 16, 16, 8, 4, 2, 1, 1);
        g(61, 14, 2, 2, 2, 2, 2, 14);
        g(62, 4, 10, 17, 0, 0, 0, 0);
        g(63, 0, 0, 0, 0, 0, 0, 31);
        g(64, 8, 4, 2, 0, 0, 0, 0);
        g(65, 0, 0, 14, 1, 15, 17, 15);
        g(66, 16, 16, 30, 17, 17, 17, 30);
        g(67, 0, 0, 14, 17, 16, 17, 14);
        g(68, 1, 1, 15, 17, 17, 17, 15);
        g(69, 0, 0, 14, 17, 31, 16, 14);
        g(70, 6, 9, 8, 28, 8, 8, 8);
        g(71, 0, 0, 15, 17, 15, 1, 14);
        g(72, 16, 16, 22, 25, 17, 17, 17);
        g(73, 4, 0, 12, 4, 4, 4, 14);
        g(74, 2, 0, 6, 2, 2, 18, 12);
        g(75, 16, 16, 18, 20, 24, 20, 18);
        g(76, 12, 4, 4, 4, 4, 4, 14);
        g(77, 0, 0, 26, 21, 21, 17, 17);
        g(78, 0, 0, 22, 25, 17, 17, 17);
        g(79, 0, 0, 14, 17, 17, 17, 14);
        g(80, 0, 0, 30, 17, 30, 16, 16);
        g(81, 0, 0, 15, 17, 15, 1, 1);
        g(82, 0, 0, 22, 25, 16, 16, 16);
        g(83, 0, 0, 14, 16, 14, 1, 30);
        g(84, 8, 8, 28, 8, 8, 9, 6);
        g(85, 0, 0, 17, 17, 17, 19, 13);
        g(86, 0, 0, 17, 17, 17, 10, 4);
        g(87, 0, 0, 17, 17, 21, 21, 10);
        g(88, 0, 0, 17, 10, 4, 10, 17);
        g(89, 0, 0, 17, 17, 15, 1, 14);
        g(90, 0, 0, 31, 2, 4, 8, 31);
        g(91, 2, 4, 4, 8, 4, 4, 2);
        g(92, 4, 4, 4, 4, 4, 4, 4);
        g(93, 8, 4, 4, 2, 4, 4, 8);
        g(94, 0, 8, 21, 2, 0, 0, 0);
    }
}
