package com.createstats.statsmk3.instrument;

import com.createstats.statsmk3.YAxisRange;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Plot {
    private Plot() {
    }

    public static final class Points {
        public float[] xy = new float[0];
        public int count;
        public float lo;
        public float hi;
        public boolean clippedHigh;
        public boolean clippedLow;
        private final float[] extent = new float[2];

        public float yFor(float value, Rect area) {
            final float span = this.hi - this.lo;
            if (span <= 1.0e-6f) {
                return area.centerY();
            }
            final float t = clamp01((value - this.lo) / span);
            return area.bottom() - t * area.h;
        }

        private void reset() {
            this.count = 0;
            this.clippedHigh = false;
            this.clippedLow = false;
        }

        private void ensureCapacity(int floats) {
            if (this.xy.length < floats) {
                this.xy = new float[Math.max(floats, this.xy.length * 2 + 16)];
            }
        }
    }

    public static int columnsFor(Rect area) {
        return Math.max(2, Math.round(area.w * 0.5f) * 2);
    }

    public static Points map(ChannelView ch, Rect area, int budget, float pad) {
        return map(ch, area, budget, pad, YAxisRange.auto(), new Points());
    }

    public static Points map(ChannelView ch, Rect area, int budget, float pad,
                             YAxisRange yAxis, Points reuse) {
        final Points p = reuse == null ? new Points() : reuse;
        p.reset();
        final int n = Math.min(Math.max(budget, 2), ch.historyCount);
        final YAxisRange scale = yAxis == null ? YAxisRange.auto() : yAxis;
        if (n < 2) {
            p.lo = scale.isFixed() ? scale.min() : ch.min;
            p.hi = scale.isFixed() ? scale.max() : ch.max;
            return p;
        }

        if (scale.isFixed()) {
            p.lo = scale.min();
            p.hi = scale.max();
        } else {
            ch.historyExtent(n, pad, p.extent);
            p.lo = p.extent[0];
            p.hi = p.extent[1];
        }

        final int start = ch.historyCount - n;
        final int limit = columnsFor(area);
        if (n <= limit) {
            fill(p, ch, area, start, n);
            return p;
        }
        envelope(p, ch, area, start, n, limit / 2);
        return p;
    }

    private static void fill(Points p, ChannelView ch, Rect area, int start, int n) {
        final float span = p.hi - p.lo;
        p.ensureCapacity(n * 2);
        p.count = n;
        for (int i = 0; i < n; i++) {
            final float v = ch.history[start + i];
            markClipped(p, v);
            final float t = span <= 1.0e-6f ? 0.5f : clamp01((v - p.lo) / span);
            p.xy[i * 2] = area.x + area.w * (i / (float) (n - 1));
            p.xy[i * 2 + 1] = area.bottom() - t * area.h;
        }
    }

    private static void envelope(Points p, ChannelView ch, Rect area, int start, int n, int cols) {
        final float span = p.hi - p.lo;
        p.ensureCapacity(cols * 4);
        int out = 0;
        for (int c = 0; c < cols; c++) {
            final int from = start + (int) ((long) c * n / cols);
            final int to = start + (int) ((long) (c + 1) * n / cols);
            int loIndex = from;
            int hiIndex = from;
            for (int i = from; i < to; i++) {
                if (ch.history[i] < ch.history[loIndex]) {
                    loIndex = i;
                }
                if (ch.history[i] > ch.history[hiIndex]) {
                    hiIndex = i;
                }
            }
            final int first = Math.min(loIndex, hiIndex);
            final int second = Math.max(loIndex, hiIndex);
            out = emit(p, ch, area, span, start, n, first, out);
            if (second != first) {
                out = emit(p, ch, area, span, start, n, second, out);
            }
        }
        p.count = out;
    }

    private static int emit(Points p, ChannelView ch, Rect area, float span, int start, int n,
                            int index, int out) {
        final float v = ch.history[index];
        markClipped(p, v);
        final float t = span <= 1.0e-6f ? 0.5f : clamp01((v - p.lo) / span);
        p.xy[out * 2] = area.x + area.w * ((index - start) / (float) (n - 1));
        p.xy[out * 2 + 1] = area.bottom() - t * area.h;
        return out + 1;
    }

    public static void grid(PanelSurface s, Rect area, int rows) {
        grid(s, area, rows, 1.0f);
    }

    public static void grid(PanelSurface s, Rect area, int rows, float alpha) {
        final float a = 0.85f * (alpha < 0.0f ? 0.0f : Math.min(alpha, 1.0f));
        if (a <= 0.01f) {
            return;
        }
        for (int i = 0; i <= rows; i++) {
            final float y = Math.round(area.y + area.h * (i / (float) rows));
            Shapes.rect(s, area.x, y, area.w, 1.0f, Tokens.alpha(Tokens.LINE, a));
        }
    }

    public static void endpoint(PanelSurface s, float x, float y, float stroke, int color) {
        Shapes.circle(s, x, y, stroke + 3.4f, Tokens.alpha(color, 0.28f));
        Shapes.circle(s, x, y, stroke + 1.6f, color);
    }

    public static void overflowMarkers(PanelSurface s, Rect area, Points points, int color) {
        overflowMarkers(s, area, points.clippedHigh, points.clippedLow, color);
    }

    public static void overflowMarkers(PanelSurface s, Rect area, boolean high, boolean low,
                                       int color) {
        if (high) {
            final float x = area.right() - 6.0f;
            s.tri(x, area.y + 4.5f, color, x + 6.0f, area.y + 4.5f, color,
                    x + 3.0f, area.y, color);
        }
        if (low) {
            final float x = area.right() - 6.0f;
            s.tri(x, area.bottom() - 4.5f, color, x + 3.0f, area.bottom(), color,
                    x + 6.0f, area.bottom() - 4.5f, color);
        }
    }

    private static void markClipped(Points points, float value) {
        points.clippedHigh |= value > points.hi;
        points.clippedLow |= value < points.lo;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
