package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class HiFiFont {
    public static final float TRACKING = 0.10f;

    private record Glyph(float width, float[][] strokes) {
    }

    private static final Map<Character, Glyph> GLYPHS = new HashMap<>();

    private HiFiFont() {
    }

    public static float width(String text, float size) {
        final String upper = text.toUpperCase(java.util.Locale.ROOT);
        float total = 0.0f;
        for (int i = 0; i < upper.length(); i++) {
            final Glyph glyph = GLYPHS.get(upper.charAt(i));
            total += (glyph == null ? 0.34f : glyph.width()) * size;
            if (i < upper.length() - 1) {
                total += TRACKING * size;
            }
        }
        return total;
    }

    public static void draw(NativeImage image, String text, float x, float y, float size,
                            float weight, int argb) {
        final String upper = text.toUpperCase(java.util.Locale.ROOT);
        float pen = x;
        for (int i = 0; i < upper.length(); i++) {
            final Glyph glyph = GLYPHS.get(upper.charAt(i));
            if (glyph == null) {
                pen += (0.34f + TRACKING) * size;
                continue;
            }
            for (float[] stroke : glyph.strokes()) {
                for (int p = 0; p + 3 < stroke.length; p += 2) {
                    HiFiCanvas.line(image,
                            pen + stroke[p] * size, y + stroke[p + 1] * size,
                            pen + stroke[p + 2] * size, y + stroke[p + 3] * size,
                            weight, argb);
                }
                if (stroke.length == 2) {
                    HiFiCanvas.dot(image, pen + stroke[0] * size, y + stroke[1] * size, weight / 2.0f, argb);
                }
            }
            pen += (glyph.width() + TRACKING) * size;
        }
    }

    public static void drawCentred(NativeImage image, String text, float centreX, float y, float size,
                                   float weight, int argb) {
        draw(image, text, centreX - width(text, size) / 2.0f, y, size, weight, argb);
    }

    public static String clip(String text, float size, float maxWidth) {
        if (width(text, size) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && width(cut + ".", size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ".";
    }

    private static void glyph(char c, float width, float[]... strokes) {
        GLYPHS.put(c, new Glyph(width, strokes));
    }

    private static float[] arc(float cx, float cy, float rx, float ry, float from, float to, int steps) {
        final float[] points = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            final double a = Math.toRadians(from + (to - from) * i / (float) steps);
            points[i * 2] = cx + rx * (float) Math.cos(a);
            points[i * 2 + 1] = cy + ry * (float) Math.sin(a);
        }
        return points;
    }

    private static float[] join(float[] head, float[] tail) {
        final float[] out = new float[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    static {
        glyph('A', 0.70f, new float[]{0.02f, 1.00f, 0.35f, 0.00f, 0.68f, 1.00f},
                new float[]{0.14f, 0.68f, 0.56f, 0.68f});
        glyph('B', 0.66f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                join(new float[]{0.05f, 0.00f}, join(arc(0.34f, 0.25f, 0.24f, 0.25f, -90.0f, 90.0f, 8),
                        new float[]{0.05f, 0.50f})),
                join(new float[]{0.05f, 0.50f}, join(arc(0.34f, 0.75f, 0.28f, 0.25f, -90.0f, 90.0f, 8),
                        new float[]{0.05f, 1.00f})));
        glyph('C', 0.68f, arc(0.35f, 0.50f, 0.30f, 0.46f, -55.0f, -305.0f, 22));
        glyph('D', 0.70f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                join(new float[]{0.05f, 0.00f}, join(arc(0.31f, 0.50f, 0.34f, 0.50f, -90.0f, 90.0f, 12),
                        new float[]{0.05f, 1.00f})));
        glyph('E', 0.60f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                new float[]{0.05f, 0.02f, 0.56f, 0.02f},
                new float[]{0.05f, 0.50f, 0.48f, 0.50f},
                new float[]{0.05f, 0.98f, 0.56f, 0.98f});
        glyph('F', 0.58f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                new float[]{0.05f, 0.02f, 0.56f, 0.02f},
                new float[]{0.05f, 0.50f, 0.48f, 0.50f});
        glyph('G', 0.72f, arc(0.35f, 0.50f, 0.30f, 0.46f, -55.0f, -350.0f, 26),
                new float[]{0.65f, 0.58f, 0.40f, 0.58f});
        glyph('H', 0.70f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                new float[]{0.65f, 0.00f, 0.65f, 1.00f},
                new float[]{0.05f, 0.52f, 0.65f, 0.52f});
        glyph('I', 0.20f, new float[]{0.10f, 0.00f, 0.10f, 1.00f});
        glyph('J', 0.58f, join(new float[]{0.48f, 0.00f}, arc(0.26f, 0.70f, 0.22f, 0.29f, 0.0f, 155.0f, 8)));
        glyph('K', 0.66f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                new float[]{0.62f, 0.00f, 0.07f, 0.56f},
                new float[]{0.28f, 0.38f, 0.64f, 1.00f});
        glyph('L', 0.56f, new float[]{0.05f, 0.00f, 0.05f, 0.98f, 0.53f, 0.98f});
        glyph('M', 0.86f, new float[]{0.05f, 1.00f, 0.05f, 0.00f, 0.43f, 0.68f, 0.81f, 0.00f, 0.81f, 1.00f});
        glyph('N', 0.72f, new float[]{0.05f, 1.00f, 0.05f, 0.00f, 0.67f, 1.00f, 0.67f, 0.00f});
        glyph('O', 0.72f, arc(0.36f, 0.50f, 0.31f, 0.46f, 0.0f, 360.0f, 28));
        glyph('P', 0.64f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                join(new float[]{0.05f, 0.00f}, join(arc(0.33f, 0.28f, 0.26f, 0.28f, -90.0f, 90.0f, 10),
                        new float[]{0.05f, 0.56f})));
        glyph('Q', 0.72f, arc(0.36f, 0.50f, 0.31f, 0.46f, 0.0f, 360.0f, 28),
                new float[]{0.44f, 0.72f, 0.70f, 1.04f});
        glyph('R', 0.66f, new float[]{0.05f, 0.00f, 0.05f, 1.00f},
                join(new float[]{0.05f, 0.00f}, join(arc(0.33f, 0.27f, 0.26f, 0.27f, -90.0f, 90.0f, 10),
                        new float[]{0.05f, 0.54f})),
                new float[]{0.34f, 0.54f, 0.64f, 1.00f});
        glyph('S', 0.64f, new float[]{
                0.60f, 0.19f, 0.53f, 0.06f, 0.36f, 0.02f, 0.17f, 0.06f, 0.07f, 0.19f,
                0.09f, 0.33f, 0.22f, 0.43f, 0.42f, 0.52f, 0.55f, 0.62f, 0.58f, 0.77f,
                0.49f, 0.92f, 0.31f, 0.98f, 0.13f, 0.94f, 0.04f, 0.80f});
        glyph('T', 0.62f, new float[]{0.02f, 0.02f, 0.60f, 0.02f},
                new float[]{0.31f, 0.02f, 0.31f, 1.00f});
        glyph('U', 0.70f, join(new float[]{0.05f, 0.00f},
                join(arc(0.35f, 0.66f, 0.30f, 0.34f, 180.0f, 0.0f, 12), new float[]{0.65f, 0.00f})));
        glyph('V', 0.70f, new float[]{0.03f, 0.00f, 0.35f, 1.00f, 0.67f, 0.00f});
        glyph('W', 0.98f, new float[]{0.03f, 0.00f, 0.24f, 1.00f, 0.49f, 0.32f, 0.74f, 1.00f, 0.95f, 0.00f});
        glyph('X', 0.68f, new float[]{0.04f, 0.00f, 0.64f, 1.00f},
                new float[]{0.64f, 0.00f, 0.04f, 1.00f});
        glyph('Y', 0.68f, new float[]{0.04f, 0.00f, 0.34f, 0.50f, 0.64f, 0.00f},
                new float[]{0.34f, 0.50f, 0.34f, 1.00f});
        glyph('Z', 0.64f, new float[]{0.05f, 0.02f, 0.60f, 0.02f, 0.05f, 0.98f, 0.60f, 0.98f});

        glyph('0', 0.68f, arc(0.34f, 0.50f, 0.29f, 0.46f, 0.0f, 360.0f, 28));
        glyph('1', 0.42f, new float[]{0.04f, 0.20f, 0.29f, 0.02f, 0.29f, 1.00f});
        glyph('2', 0.64f, join(arc(0.33f, 0.28f, 0.27f, 0.26f, 180.0f, 380.0f, 12),
                new float[]{0.06f, 0.97f, 0.61f, 0.97f}));
        glyph('3', 0.62f, new float[]{
                0.07f, 0.16f, 0.17f, 0.05f, 0.35f, 0.02f, 0.52f, 0.09f, 0.55f, 0.24f,
                0.44f, 0.40f, 0.28f, 0.46f, 0.48f, 0.52f, 0.59f, 0.68f, 0.53f, 0.88f,
                0.34f, 0.98f, 0.15f, 0.95f, 0.05f, 0.83f});
        glyph('4', 0.68f, new float[]{0.50f, 0.02f, 0.04f, 0.72f, 0.64f, 0.72f},
                new float[]{0.50f, 0.02f, 0.50f, 1.00f});
        glyph('5', 0.62f, new float[]{
                0.56f, 0.03f, 0.11f, 0.03f, 0.07f, 0.40f, 0.26f, 0.34f, 0.45f, 0.40f,
                0.56f, 0.56f, 0.54f, 0.78f, 0.38f, 0.94f, 0.19f, 0.98f, 0.05f, 0.88f});
        glyph('6', 0.64f, join(new float[]{0.55f, 0.07f, 0.38f, 0.02f, 0.20f, 0.12f, 0.09f, 0.36f},
                arc(0.33f, 0.66f, 0.26f, 0.32f, 180.0f, 540.0f, 22)));
        glyph('7', 0.60f, new float[]{0.03f, 0.03f, 0.57f, 0.03f, 0.24f, 1.00f});
        glyph('8', 0.64f, arc(0.33f, 0.26f, 0.24f, 0.24f, -90.0f, 270.0f, 20),
                arc(0.33f, 0.72f, 0.28f, 0.26f, -90.0f, 270.0f, 22));
        glyph('9', 0.64f, join(arc(0.33f, 0.34f, 0.26f, 0.32f, 0.0f, 360.0f, 22),
                new float[]{0.56f, 0.62f, 0.44f, 0.86f, 0.26f, 0.97f, 0.08f, 0.94f}));

        glyph(' ', 0.32f);
        glyph('/', 0.48f, new float[]{0.03f, 1.04f, 0.45f, -0.04f});
        glyph('-', 0.46f, new float[]{0.06f, 0.55f, 0.40f, 0.55f});
        glyph('_', 0.58f, new float[]{0.03f, 1.04f, 0.55f, 1.04f});
        glyph('.', 0.26f, new float[]{0.13f, 0.97f});
        glyph(',', 0.26f, new float[]{0.15f, 0.95f, 0.07f, 1.12f});
        glyph(':', 0.26f, new float[]{0.13f, 0.30f}, new float[]{0.13f, 0.90f});
        glyph('·', 0.30f, new float[]{0.15f, 0.55f});
        glyph('%', 0.86f, arc(0.20f, 0.20f, 0.16f, 0.18f, 0.0f, 360.0f, 12),
                arc(0.66f, 0.80f, 0.16f, 0.18f, 0.0f, 360.0f, 12),
                new float[]{0.78f, 0.02f, 0.08f, 0.98f});
        glyph('+', 0.62f, new float[]{0.08f, 0.55f, 0.54f, 0.55f}, new float[]{0.31f, 0.32f, 0.31f, 0.78f});
        glyph('\'', 0.22f, new float[]{0.11f, 0.02f, 0.11f, 0.26f});
    }
}
