package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Layout {
    private Layout() {
    }

    public static Rect[] column(Rect area, float gap, float... heights) {
        final Rect[] out = new Rect[heights.length];
        float y = area.y;
        for (int i = 0; i < heights.length; i++) {
            out[i] = new Rect(area.x, y, area.w, heights[i]);
            y += heights[i] + gap;
        }
        return out;
    }

    public static Rect[] rows(Rect area, int n, float gap) {
        final Rect[] out = new Rect[Math.max(0, n)];
        if (n <= 0) {
            return out;
        }
        final float h = (area.h - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            out[i] = new Rect(area.x, area.y + i * (h + gap), area.w, h);
        }
        return out;
    }

    public static Rect[] columns(Rect area, int n, float gap) {
        final Rect[] out = new Rect[Math.max(0, n)];
        if (n <= 0) {
            return out;
        }
        final float w = (area.w - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            out[i] = new Rect(area.x + i * (w + gap), area.y, w, area.h);
        }
        return out;
    }

    public static Rect[] weighted(Rect area, float gap, float... weights) {
        final Rect[] out = new Rect[weights.length];
        float total = 0.0f;
        for (final float w : weights) {
            total += w;
        }
        if (total <= 0.0f) {
            return out;
        }
        final float usable = area.w - gap * (weights.length - 1);
        float x = area.x;
        for (int i = 0; i < weights.length; i++) {
            final float w = usable * (weights[i] / total);
            out[i] = new Rect(x, area.y, w, area.h);
            x += w + gap;
        }
        return out;
    }

    public static Rect[] grid(Rect area, int cols, int rows, float gap) {
        final Rect[] out = new Rect[Math.max(0, cols * rows)];
        if (cols <= 0 || rows <= 0) {
            return out;
        }
        final float cw = (area.w - gap * (cols - 1)) / cols;
        final float ch = (area.h - gap * (rows - 1)) / rows;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r * cols + c] = new Rect(
                        area.x + c * (cw + gap),
                        area.y + r * (ch + gap),
                        cw, ch);
            }
        }
        return out;
    }

    public static Rect gridCell(Rect area, int cols, float rowHeight, float gap, int index) {
        final int c = index % cols;
        final int r = index / cols;
        final float cw = (area.w - gap * (cols - 1)) / cols;
        return new Rect(area.x + c * (cw + gap), area.y + r * (rowHeight + gap), cw, rowHeight);
    }

    public static float gridHeight(int n, int cols, float rowHeight, float gap) {
        if (n <= 0 || cols <= 0) {
            return 0.0f;
        }
        final int rows = (n + cols - 1) / cols;
        return rows * rowHeight + (rows - 1) * gap;
    }

    public static Rect center(Rect area, float w, float h) {
        return new Rect(area.centerX() - w * 0.5f, area.centerY() - h * 0.5f, w, h);
    }
}
