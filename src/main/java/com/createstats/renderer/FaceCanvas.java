package com.createstats.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// same coordinates as the old world-space quads, so moving to a texture didn't mean relaying everything
@OnlyIn(Dist.CLIENT)
final class FaceCanvas {
    private final NativeImage image;
    private final float u0;
    private final float v1;

    private final float sx;
    private final float sy;

    FaceCanvas(NativeImage image, float u0, float v0, float u1, float v1) {
        this.image = image;
        this.u0 = u0;
        this.v1 = v1;
        this.sx = image.getWidth() / Math.max(1.0e-6f, u1 - u0);
        this.sy = image.getHeight() / Math.max(1.0e-6f, v1 - v0);
    }

    NativeImage image() {
        return image;
    }

    float scaleX() {
        return sx;
    }

    float scaleY() {
        return sy;
    }

    int toX(float u) {
        return Math.round((u - u0) * sx);
    }

    int toY(float v) {
        return Math.round((v1 - v) * sy);
    }

    void clear(int argb) {
        final int abgr = BitmapFont.argbToAbgr(argb);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    void quad(float qu0, float qv0, float qu1, float qv1, int argb) {
        if (qu1 <= qu0 || qv1 <= qv0) {
            return;
        }
        final int x0 = toX(qu0);
        final int x1 = Math.max(x0 + 1, toX(qu1));
        final int yTop = toY(qv1);
        final int yBottom = Math.max(yTop + 1, toY(qv0));
        final int abgr = BitmapFont.argbToAbgr(argb);
        final int w = image.getWidth();
        final int h = image.getHeight();
        for (int y = Math.max(0, yTop); y < Math.min(h, yBottom); y++) {
            for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    void text(String s, float u, float v, int argb, float scale) {
        if (s == null || s.isEmpty() || scale <= 0.0f) {
            return;
        }
        BitmapFont.drawScaled(image, s, (u - u0) * sx, (v1 - (v + 8.0f * scale)) * sy,
                argb, scale * sx, scale * sy);
    }

    void textRight(String s, float uRight, float v, int argb, float scale) {
        text(s, uRight - width(s, scale), v, argb, scale);
    }

    void textShrunk(String s, float u, float v, float maxWidth, int argb, float scale) {
        if (s == null || s.isEmpty() || maxWidth <= 0.0f) {
            return;
        }
        final float w = width(s, scale);
        text(s, u, v, argb, w > maxWidth ? scale * (maxWidth / w) : scale);
    }

    void textCentred(String s, float uCentre, float v, int argb, float scale) {
        text(s, uCentre - width(s, scale) * 0.5f, v, argb, scale);
    }

    static float width(String s, float scale) {
        return s == null ? 0.0f : BitmapFont.measurePixels(s) * scale;
    }
}
