package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HiFiCanvas {
    private HiFiCanvas() {
    }

    public static void blend(NativeImage image, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        final int srcA = (argb >>> 24) & 0xFF;
        if (srcA == 0) {
            return;
        }
        if (srcA == 0xFF) {
            image.setPixelRGBA(x, y, BitmapFont.argbToAbgr(argb));
            return;
        }

        final int dst = image.getPixelRGBA(x, y);
        final int dstB = (dst >>> 16) & 0xFF;
        final int dstG = (dst >>> 8) & 0xFF;
        final int dstR = dst & 0xFF;

        final int srcR = (argb >>> 16) & 0xFF;
        final int srcG = (argb >>> 8) & 0xFF;
        final int srcB = argb & 0xFF;

        final int r = (srcR * srcA + dstR * (255 - srcA)) / 255;
        final int g = (srcG * srcA + dstG * (255 - srcA)) / 255;
        final int b = (srcB * srcA + dstB * (255 - srcA)) / 255;
        image.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
    }

    public static int fade(int argb, float coverage) {
        if (coverage <= 0.0f) {
            return 0;
        }
        final int a = Math.round(((argb >>> 24) & 0xFF) * Math.min(1.0f, coverage));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColour(int from, int to, float t) {
        final float k = clamp01(t);
        final int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * k);
        final int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * k);
        final int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * k);
        final int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void clear(NativeImage image, int argb) {
        final int abgr = BitmapFont.argbToAbgr(argb);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    public static void verticalGradient(NativeImage image, int top, int bottom) {
        final int h = image.getHeight();
        final int w = image.getWidth();
        for (int y = 0; y < h; y++) {
            final int abgr = BitmapFont.argbToAbgr(lerpColour(top, bottom, y / (float) Math.max(1, h - 1)));
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    private static float sdRoundRect(float px, float py, float cx, float cy, float hw, float hh, float r) {
        final float qx = Math.abs(px - cx) - (hw - r);
        final float qy = Math.abs(py - cy) - (hh - r);
        final float ax = Math.max(qx, 0.0f);
        final float ay = Math.max(qy, 0.0f);
        return (float) Math.sqrt(ax * ax + ay * ay) + Math.min(Math.max(qx, qy), 0.0f) - r;
    }

    public static void fillRoundRect(NativeImage image, float x, float y, float w, float h,
                                     float radius, int argb) {
        final float cx = x + w / 2.0f;
        final float cy = y + h / 2.0f;
        final float hw = w / 2.0f;
        final float hh = h / 2.0f;
        final float r = Math.min(radius, Math.min(hw, hh));
        forEachPixel(image, x - 1, y - 1, w + 2, h + 2, (px, py) -> {
            final float d = sdRoundRect(px + 0.5f, py + 0.5f, cx, cy, hw, hh, r);
            blend(image, px, py, fade(argb, 0.5f - d));
        });
    }

    public static void strokeRoundRect(NativeImage image, float x, float y, float w, float h,
                                       float radius, float weight, int argb) {
        final float cx = x + w / 2.0f;
        final float cy = y + h / 2.0f;
        final float hw = w / 2.0f;
        final float hh = h / 2.0f;
        final float r = Math.min(radius, Math.min(hw, hh));
        final float half = weight / 2.0f;
        forEachPixel(image, x - weight, y - weight, w + weight * 2, h + weight * 2, (px, py) -> {
            final float d = Math.abs(sdRoundRect(px + 0.5f, py + 0.5f, cx, cy, hw, hh, r) + half);
            blend(image, px, py, fade(argb, half + 0.5f - d));
        });
    }

    public static void dropShadow(NativeImage image, float x, float y, float w, float h,
                                  float radius, float spread, float offsetY, int argb) {
        final float cx = x + w / 2.0f;
        final float cy = y + h / 2.0f + offsetY;
        final float hw = w / 2.0f;
        final float hh = h / 2.0f;
        final float r = Math.min(radius, Math.min(hw, hh));
        forEachPixel(image, x - spread, y - spread + offsetY, w + spread * 2, h + spread * 2, (px, py) -> {
            final float d = sdRoundRect(px + 0.5f, py + 0.5f, cx, cy, hw, hh, r);
            if (d <= 0.0f || d >= spread) {
                return;
            }
            final float t = 1.0f - d / spread;
            blend(image, px, py, fade(argb, t * t));
        });
    }

    private static float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        final float dx = bx - ax;
        final float dy = by - ay;
        final float lenSq = dx * dx + dy * dy;
        float t = 0.0f;
        if (lenSq > 1.0e-6f) {
            t = clamp01(((px - ax) * dx + (py - ay) * dy) / lenSq);
        }
        final float qx = px - (ax + dx * t);
        final float qy = py - (ay + dy * t);
        return (float) Math.sqrt(qx * qx + qy * qy);
    }

    public static void line(NativeImage image, float ax, float ay, float bx, float by,
                            float weight, int argb) {
        final float half = weight / 2.0f;
        final float x0 = Math.min(ax, bx) - half - 1.0f;
        final float y0 = Math.min(ay, by) - half - 1.0f;
        final float x1 = Math.max(ax, bx) + half + 1.0f;
        final float y1 = Math.max(ay, by) + half + 1.0f;
        forEachPixel(image, x0, y0, x1 - x0, y1 - y0, (px, py) -> {
            final float d = distToSegment(px + 0.5f, py + 0.5f, ax, ay, bx, by);
            blend(image, px, py, fade(argb, half + 0.5f - d));
        });
    }

    public static void dot(NativeImage image, float cx, float cy, float radius, int argb) {
        line(image, cx, cy, cx, cy, radius * 2.0f, argb);
    }

    public static void arc(NativeImage image, float cx, float cy, float radius,
                           float fromDegrees, float toDegrees, float weight, int argb) {
        final float span = toDegrees - fromDegrees;
        final int steps = Math.max(2, Math.round(Math.abs(span) / 4.0f));
        float px = cx + radius * (float) Math.cos(Math.toRadians(fromDegrees));
        float py = cy + radius * (float) Math.sin(Math.toRadians(fromDegrees));
        for (int i = 1; i <= steps; i++) {
            final double a = Math.toRadians(fromDegrees + span * i / (float) steps);
            final float nx = cx + radius * (float) Math.cos(a);
            final float ny = cy + radius * (float) Math.sin(a);
            line(image, px, py, nx, ny, weight, argb);
            px = nx;
            py = ny;
        }
    }

    public static void ring(NativeImage image, float cx, float cy, float radius, float weight, int argb) {
        arc(image, cx, cy, radius, 0.0f, 360.0f, weight, argb);
    }

    @FunctionalInterface
    interface PixelSink {
        void accept(int x, int y);
    }

    private static void forEachPixel(NativeImage image, float x, float y, float w, float h, PixelSink sink) {
        final int x0 = Math.max(0, (int) Math.floor(x));
        final int y0 = Math.max(0, (int) Math.floor(y));
        final int x1 = Math.min(image.getWidth(), (int) Math.ceil(x + w));
        final int y1 = Math.min(image.getHeight(), (int) Math.ceil(y + h));
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                sink.accept(px, py);
            }
        }
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }
}
