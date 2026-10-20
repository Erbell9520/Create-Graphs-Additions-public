package com.createstats.gui.security;

import com.createstats.security.SensorKind;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// strokes, same weight as everything else
@OnlyIn(Dist.CLIENT)
public final class SecurityIcons {
    private SecurityIcons() {
    }

    private static final float W = 1.25f;

    private static final float R = 7.0f;

    private static float at(float cx, float u) {
        return cx + u;
    }

    private static float rad(float degrees) {
        return degrees * (float) (Math.PI / 180.0);
    }

    public static void house(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, -R), at(cy, 0.0f), cx, at(cy, -R), W, color);
        s.line(cx, at(cy, -R), at(cx, R), at(cy, 0.0f), W, color);
        s.line(at(cx, -5.0f), at(cy, -1.0f), at(cx, -5.0f), at(cy, R), W, color);
        s.line(at(cx, 5.0f), at(cy, -1.0f), at(cx, 5.0f), at(cy, R), W, color);
        s.line(at(cx, -5.0f), at(cy, R), at(cx, 5.0f), at(cy, R), W, color);
        s.line(at(cx, -2.0f), at(cy, R), at(cx, -2.0f), at(cy, 2.0f), W, color);
        s.line(at(cx, -2.0f), at(cy, 2.0f), at(cx, 2.0f), at(cy, 2.0f), W, color);
        s.line(at(cx, 2.0f), at(cy, 2.0f), at(cx, 2.0f), at(cy, R), W, color);
    }

    public static void people(PanelSurface s, float cx, float cy, int color) {
        s.arc(at(cx, -2.0f), at(cy, -3.0f), 2.6f, W, rad(0.0f), rad(360.0f), color);
        s.arc(at(cx, -2.0f), at(cy, 5.0f), 5.0f, W, rad(200.0f), rad(140.0f), color);
        s.arc(at(cx, 4.0f), at(cy, -3.0f), 2.2f, W, rad(300.0f), rad(120.0f), color);
        s.arc(at(cx, 4.0f), at(cy, 5.0f), 4.4f, W, rad(250.0f), rad(60.0f), color);
    }

    public static void door(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, -5.0f), at(cy, -R), at(cx, -5.0f), at(cy, 6.0f), W, color);
        s.line(at(cx, 5.0f), at(cy, -R), at(cx, 5.0f), at(cy, 6.0f), W, color);
        s.line(at(cx, -5.0f), at(cy, -R), at(cx, 5.0f), at(cy, -R), W, color);
        s.line(at(cx, -R), at(cy, 6.0f), at(cx, R), at(cy, 6.0f), W, color);
        s.circle(at(cx, 2.6f), at(cy, 0.0f), 0.9f, color);
    }

    public static void sensor(PanelSurface s, float cx, float cy, int color) {
        s.circle(cx, cy, 1.4f, color);
        s.arc(cx, cy, 4.4f, W, rad(300.0f), rad(120.0f), color);
        s.arc(cx, cy, 4.4f, W, rad(120.0f), rad(120.0f), color);
        s.arc(cx, cy, 7.0f, W, rad(300.0f), rad(120.0f), color);
        s.arc(cx, cy, 7.0f, W, rad(120.0f), rad(120.0f), color);
    }

    public static void sensor(PanelSurface s, float cx, float cy, int color, SensorKind kind) {
        if (kind == null) {
            sensor(s, cx, cy, color);
            return;
        }
        switch (kind) {
            case MOTION -> motion(s, cx, cy, color);
            case TAMPER -> tamper(s, cx, cy, color);
            case FIRE -> fire(s, cx, cy, color);
            case WATER -> water(s, cx, cy, color);
        }
    }

    public static void motion(PanelSurface s, float cx, float cy, int color) {
        s.arc(at(cx, 2.6f), at(cy, -4.6f), 1.7f, W, rad(0.0f), rad(360.0f), color);
        s.line(at(cx, 2.6f), at(cy, -2.8f), at(cx, 1.6f), at(cy, 1.0f), W, color);
        s.line(at(cx, 1.6f), at(cy, 1.0f), at(cx, -0.4f), at(cy, 5.4f), W, color);
        s.line(at(cx, 1.6f), at(cy, 1.0f), at(cx, 4.6f), at(cy, 5.0f), W, color);
        s.line(at(cx, 2.2f), at(cy, -1.6f), at(cx, 5.0f), at(cy, 0.6f), W, color);

        s.arc(at(cx, -1.2f), cy, 3.6f, W, rad(130.0f), rad(100.0f), color);
        s.arc(at(cx, -1.2f), cy, 5.8f, W, rad(130.0f), rad(100.0f), color);
    }

    public static void tamper(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, -5.4f), at(cy, -5.4f), at(cx, 5.4f), at(cy, -5.4f), W, color);
        s.line(at(cx, -5.4f), at(cy, 5.4f), at(cx, 5.4f), at(cy, 5.4f), W, color);
        s.line(at(cx, -5.4f), at(cy, -5.4f), at(cx, -5.4f), at(cy, 5.4f), W, color);
        s.line(at(cx, 5.4f), at(cy, -5.4f), at(cx, 5.4f), at(cy, 5.4f), W, color);

        s.line(at(cx, -0.8f), at(cy, -5.4f), at(cx, 1.4f), at(cy, -1.4f), W, color);
        s.line(at(cx, 1.4f), at(cy, -1.4f), at(cx, -1.6f), at(cy, 0.6f), W, color);
        s.line(at(cx, -1.6f), at(cy, 0.6f), at(cx, 0.8f), at(cy, 5.4f), W, color);
    }

    public static void fire(PanelSurface s, float cx, float cy, int color) {
        s.arc(cx, at(cy, 2.0f), 4.0f, W, rad(0.0f), rad(180.0f), color);
        s.line(at(cx, -4.0f), at(cy, 2.0f), at(cx, -1.8f), at(cy, -2.6f), W, color);
        s.line(at(cx, -1.8f), at(cy, -2.6f), at(cx, 0.6f), at(cy, -6.4f), W, color);
        s.line(at(cx, 0.6f), at(cy, -6.4f), at(cx, 1.8f), at(cy, -2.4f), W, color);
        s.line(at(cx, 1.8f), at(cy, -2.4f), at(cx, 4.0f), at(cy, 2.0f), W, color);
        s.arc(cx, at(cy, 2.4f), 1.9f, W, rad(0.0f), rad(180.0f), color);
        s.line(at(cx, -1.9f), at(cy, 2.4f), at(cx, 0.4f), at(cy, -0.8f), W, color);
        s.line(at(cx, 0.4f), at(cy, -0.8f), at(cx, 1.9f), at(cy, 2.4f), W, color);
    }

    public static void water(PanelSurface s, float cx, float cy, int color) {
        s.arc(cx, at(cy, -1.4f), 3.4f, W, rad(0.0f), rad(180.0f), color);
        s.line(at(cx, -3.4f), at(cy, -1.4f), cx, at(cy, -6.6f), W, color);
        s.line(at(cx, 3.4f), at(cy, -1.4f), cx, at(cy, -6.6f), W, color);

        s.line(at(cx, -R), at(cy, 4.2f), at(cx, -2.4f), at(cy, 4.2f), W, color);
        s.line(at(cx, -2.4f), at(cy, 4.2f), cx, at(cy, 6.0f), W, color);
        s.line(cx, at(cy, 6.0f), at(cx, 2.4f), at(cy, 4.2f), W, color);
        s.line(at(cx, 2.4f), at(cy, 4.2f), at(cx, R), at(cy, 4.2f), W, color);
    }

    public static void grid(PanelSurface s, float cx, float cy, int color) {
        for (int i = -1; i <= 1; i += 2) {
            s.line(at(cx, i * 2.2f), at(cy, -R), at(cx, i * 2.2f), at(cy, R), W, color);
            s.line(at(cx, -R), at(cy, i * 2.2f), at(cx, R), at(cy, i * 2.2f), W, color);
        }
        s.line(at(cx, -R), at(cy, -R), at(cx, R), at(cy, -R), W, color);
        s.line(at(cx, -R), at(cy, R), at(cx, R), at(cy, R), W, color);
        s.line(at(cx, -R), at(cy, -R), at(cx, -R), at(cy, R), W, color);
        s.line(at(cx, R), at(cy, -R), at(cx, R), at(cy, R), W, color);
    }

    public static void clock(PanelSurface s, float cx, float cy, int color) {
        s.arc(cx, cy, 6.2f, W, rad(0.0f), rad(360.0f), color);
        s.line(cx, cy, cx, at(cy, -3.4f), W, color);
        s.line(cx, cy, at(cx, 2.6f), at(cy, 1.4f), W, color);
    }

    public static void sliders(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, -R), at(cy, -3.0f), at(cx, R), at(cy, -3.0f), W, color);
        s.line(at(cx, -R), at(cy, 3.0f), at(cx, R), at(cy, 3.0f), W, color);
        s.arc(at(cx, -2.0f), at(cy, -3.0f), 2.0f, W, rad(0.0f), rad(360.0f), color);
        s.arc(at(cx, 2.8f), at(cy, 3.0f), 2.0f, W, rad(0.0f), rad(360.0f), color);
    }

    public static void shield(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, -5.2f), at(cy, -4.6f), cx, at(cy, -6.4f), W, color);
        s.line(cx, at(cy, -6.4f), at(cx, 5.2f), at(cy, -4.6f), W, color);
        s.line(at(cx, -5.2f), at(cy, -4.6f), at(cx, -5.2f), at(cy, 0.4f), W, color);
        s.line(at(cx, 5.2f), at(cy, -4.6f), at(cx, 5.2f), at(cy, 0.4f), W, color);
        s.line(at(cx, -5.2f), at(cy, 0.4f), cx, at(cy, 6.2f), W, color);
        s.line(at(cx, 5.2f), at(cy, 0.4f), cx, at(cy, 6.2f), W, color);
    }

    public static void key(PanelSurface s, float cx, float cy, int color) {
        s.arc(at(cx, -2.2f), cy, 2.4f, 1.4f, rad(0.0f), rad(360.0f), color);
        s.line(at(cx, 0.2f), cy, at(cx, 4.6f), cy, 1.4f, color);
        s.line(at(cx, 3.4f), cy, at(cx, 3.4f), at(cy, 2.2f), 1.4f, color);
    }

    public static void pencil(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, -3.4f), at(cy, 3.4f), at(cx, 2.6f), at(cy, -2.6f), 1.4f, color);
        s.line(at(cx, 1.4f), at(cy, -3.8f), at(cx, 3.8f), at(cy, -1.4f), 1.4f, color);
        s.line(at(cx, 1.4f), at(cy, -3.8f), at(cx, 3.8f), at(cy, -1.4f), 1.4f, color);
        s.line(at(cx, -4.4f), at(cy, 4.4f), at(cx, -2.4f), at(cy, 3.8f), 1.0f, color);
        s.line(at(cx, -4.4f), at(cy, 4.4f), at(cx, -3.8f), at(cy, 2.4f), 1.0f, color);
    }

    private static void frame(PanelSurface s, float cx, float cy,
                              float x0, float y0, float x1, float y1, float w, int color) {
        s.line(at(cx, x0), at(cy, y0), at(cx, x1), at(cy, y0), w, color);
        s.line(at(cx, x0), at(cy, y1), at(cx, x1), at(cy, y1), w, color);
        s.line(at(cx, x0), at(cy, y0), at(cx, x0), at(cy, y1), w, color);
        s.line(at(cx, x1), at(cy, y0), at(cx, x1), at(cy, y1), w, color);
    }

    public static void locker(PanelSurface s, float cx, float cy, int color) {
        frame(s, cx, cy, -6.0f, -6.0f, 6.0f, 6.0f, W, color);
        frame(s, cx, cy, -3.2f, -3.4f, 3.4f, 3.4f, 1.0f, color);
        s.arc(at(cx, 0.1f), cy, 1.9f, 1.2f, rad(0.0f), rad(360.0f), color);
        s.line(at(cx, 3.4f), cy, at(cx, 5.6f), cy, 1.6f, color);
    }

    public static void shutter(PanelSurface s, float cx, float cy, int color) {
        frame(s, cx, cy, -6.0f, -6.0f, 6.0f, 6.0f, W, color);
        for (float y : new float[] {-4.2f, -2.4f, 2.4f, 4.2f}) {
            s.line(at(cx, -4.4f), at(cy, y), at(cx, 4.4f), at(cy, y), 1.0f, color);
        }
        s.line(at(cx, -6.0f), cy, at(cx, 6.0f), cy, 1.9f, color);
    }

    public static void repeller(PanelSurface s, float cx, float cy, int color) {
        frame(s, cx, cy, -4.0f, 2.0f, 4.0f, 6.0f, W, color);
        s.line(cx, at(cy, 2.0f), cx, at(cy, -0.4f), W, color);
        s.arc(cx, at(cy, -0.4f), 3.0f, W, rad(215.0f), rad(110.0f), color);
        s.arc(cx, at(cy, -0.4f), 5.8f, W, rad(215.0f), rad(110.0f), color);
    }

    public static void relay(PanelSurface s, float cx, float cy, int color) {
        frame(s, cx, cy, -6.2f, -6.0f, 4.0f, 6.0f, W, color);
        s.arc(at(cx, -3.2f), at(cy, -2.8f), 1.3f, 1.0f, rad(0.0f), rad(360.0f), color);
        s.arc(at(cx, 0.9f), at(cy, -2.8f), 1.3f, 1.0f, rad(0.0f), rad(360.0f), color);
        s.line(at(cx, -4.4f), at(cy, 2.6f), at(cx, -1.8f), at(cy, 2.6f), W, color);
        s.circle(at(cx, 0.9f), at(cy, 2.6f), 1.6f, color);
        s.line(at(cx, 4.0f), cy, at(cx, R), cy, W, color);
    }

    public static void coil(PanelSurface s, float cx, float cy, int color) {
        frame(s, cx, cy, -4.4f, 4.0f, 4.4f, 6.8f, W, color);

        s.line(at(cx, -1.4f), at(cy, 4.0f), at(cx, -1.4f), at(cy, -2.4f), 1.0f, color);
        s.line(at(cx, 1.4f), at(cy, 4.0f), at(cx, 1.4f), at(cy, -2.4f), 1.0f, color);
        s.line(at(cx, -1.4f), at(cy, 2.6f), at(cx, 1.4f), at(cy, 1.6f), 1.0f, color);
        s.line(at(cx, -1.4f), at(cy, 0.6f), at(cx, 1.4f), at(cy, -0.4f), 1.0f, color);

        s.roundedRectOutline(new Rect(at(cx, -5.8f), at(cy, -5.6f), 11.6f, 3.4f), 1.7f, W, color);

        s.line(at(cx, 5.8f), at(cy, -4.6f), at(cx, 6.4f), at(cy, -6.6f), 1.0f, color);
        s.line(at(cx, 6.4f), at(cy, -6.6f), at(cx, 6.0f), at(cy, -5.4f), 1.0f, color);
        s.line(at(cx, 6.0f), at(cy, -5.4f), at(cx, R + 0.4f), at(cy, -7.6f), 1.0f, color);
    }

    public static void approach(PanelSurface s, float cx, float cy, int color) {
        s.line(at(cx, 6.0f), at(cy, -6.5f), at(cx, 6.0f), at(cy, 6.5f), W, color);

        s.arc(at(cx, 6.0f), cy, 4.2f, 1.0f, rad(115.0f), rad(130.0f), color);
        s.arc(at(cx, 6.0f), cy, 7.4f, 1.0f, rad(125.0f), rad(110.0f), color);

        s.arc(at(cx, -5.2f), at(cy, -2.6f), 1.5f, 1.0f, rad(0.0f), rad(360.0f), color);
        s.line(at(cx, -5.2f), at(cy, -0.8f), at(cx, -5.2f), at(cy, 3.4f), W, color);
    }

    public static void bell(PanelSurface s, float cx, float cy, int color) {
        s.arc(cx, at(cy, 1.0f), 4.6f, W, rad(180.0f), rad(180.0f), color);
        s.line(at(cx, -4.6f), at(cy, 1.0f), at(cx, -4.6f), at(cy, 3.0f), W, color);
        s.line(at(cx, 4.6f), at(cy, 1.0f), at(cx, 4.6f), at(cy, 3.0f), W, color);
        s.line(at(cx, -6.2f), at(cy, 3.0f), at(cx, 6.2f), at(cy, 3.0f), W, color);

        s.line(cx, at(cy, -3.6f), cx, at(cy, -5.4f), 1.0f, color);
        s.arc(cx, at(cy, 4.6f), 1.3f, 1.0f, rad(0.0f), rad(360.0f), color);
    }

    public static void link(PanelSurface s, float cx, float cy, int color) {
        s.arc(at(cx, -1.6f), cy, 1.8f, 1.0f, rad(40.0f), rad(260.0f), color);
        s.arc(at(cx, 1.6f), cy, 1.8f, 1.0f, rad(220.0f), rad(260.0f), color);
    }

    @FunctionalInterface
    public interface Glyph {
        void draw(PanelSurface s, float cx, float cy, int color);
    }

    public static void small(PanelSurface s, Glyph glyph, float cx, float cy, float scale,
                             int color) {
        if (scale >= 0.999f) {
            glyph.draw(s, cx, cy, color);
            return;
        }
        glyph.draw(new Scaled(s, cx, cy, scale), cx, cy, color);
    }

    private record Scaled(PanelSurface base, float px, float py, float k) implements PanelSurface {
        private float sx(float x) {
            return this.px + (x - this.px) * this.k;
        }

        private float sy(float y) {
            return this.py + (y - this.py) * this.k;
        }

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2,
                        float x3, float y3, int c3) {
            this.base.tri(sx(x1), sy(y1), c1, sx(x2), sy(y2), c2, sx(x3), sy(y3), c3);
        }

        @Override
        public float feather() {
            return this.base.feather() / this.k;
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            this.base.text(s, sx(x), sy(y), color, scale * this.k);
        }

        @Override
        public void textCenter(String s, float centerX, float y, int color, float scale) {
            this.base.textCenter(s, sx(centerX), sy(y), color, scale * this.k);
        }

        @Override
        public void textRight(String s, float rightX, float y, int color, float scale) {
            this.base.textRight(s, sx(rightX), sy(y), color, scale * this.k);
        }

        @Override
        public float textWidth(String s, float scale) {
            return this.base.textWidth(s, scale);
        }

        @Override
        public void clip(Rect r) {
            this.base.clip(new Rect(sx(r.x), sy(r.y), r.w * this.k, r.h * this.k));
        }

        @Override
        public void unclip() {
            this.base.unclip();
        }

        @Override
        public void setAlpha(float mul) {
            this.base.setAlpha(mul);
        }

        @Override
        public float alpha() {
            return this.base.alpha();
        }

        @Override
        public void advanceLayer() {
            this.base.advanceLayer();
        }
    }
}
