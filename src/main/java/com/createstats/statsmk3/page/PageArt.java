package com.createstats.statsmk3.page;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PageArt {
    private PageArt() {
    }

    public static void flow(PanelSurface s, float x1, float y1, float x2, float y2,
                            float thickness, int color, float rate, float scale, float time) {
        Shapes.line(s, x1, y1, x2, y2, thickness, Tokens.alpha(color, 0.22f));

        final float dx = x2 - x1;
        final float dy = y2 - y1;
        final float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 2.0f || Math.abs(rate) < 1.0e-4f || scale <= 0.0f) {
            return;
        }

        final int pips = Math.max(3, Math.min(16, Math.round(length / 16.0f)));
        final float speed = Math.max(-3.0f, Math.min(3.0f, rate / scale));
        final float phase = (time * speed) % 1.0f;
        final float pipLen = Math.min(11.0f, length / (pips * 1.7f));

        for (int i = 0; i < pips; i++) {
            float t = (i / (float) pips + phase) % 1.0f;
            if (t < 0.0f) {
                t += 1.0f;
            }

            final float edge = Math.min(1.0f, Math.min(t, 1.0f - t) * 6.0f);
            final float head = t * length;
            final float tail = Math.max(0.0f, head - pipLen);
            Shapes.line(s,
                    x1 + dx * (tail / length), y1 + dy * (tail / length),
                    x1 + dx * (head / length), y1 + dy * (head / length),
                    thickness * 1.35f, Tokens.alpha(color, 0.95f * edge));
        }

        final float ux = dx / length;
        final float uy = dy / length;
        final float sign = rate >= 0.0f ? 1.0f : -1.0f;
        final float tipX = rate >= 0.0f ? x2 : x1;
        final float tipY = rate >= 0.0f ? y2 : y1;
        final float back = 6.0f;
        final float spread = 3.6f;
        s.tri(tipX, tipY, color,
                tipX - ux * back * sign - uy * spread, tipY - uy * back * sign + ux * spread, color,
                tipX - ux * back * sign + uy * spread, tipY - uy * back * sign - ux * spread, color);
    }

    // ribbon width is the number, it can't lie
    public static void ribbon(PanelSurface s, float x0, float top0, float bottom0,
                              float x1, float top1, float bottom1, int colorFrom, int colorTo) {
        if (x1 - x0 < 0.5f || (bottom0 - top0 < 0.15f && bottom1 - top1 < 0.15f)) {
            return;
        }
        final int steps = Math.max(8, Math.min(48, Math.round((x1 - x0) / 5.0f)));
        float px = x0;
        float pTop = top0;
        float pBottom = bottom0;
        for (int i = 1; i <= steps; i++) {
            final float t = i / (float) steps;
            final float e = t * t * (3.0f - 2.0f * t);
            final float x = x0 + (x1 - x0) * t;
            final float top = top0 + (top1 - top0) * e;
            final float bottom = bottom0 + (bottom1 - bottom0) * e;
            final int ca = lerp(colorFrom, colorTo, (i - 1) / (float) steps);
            final int cb = lerp(colorFrom, colorTo, t);
            Shapes.quad(s, px, pTop, x, top, x, bottom, px, pBottom, ca, cb, cb, ca);
            px = x;
            pTop = top;
            pBottom = bottom;
        }
    }

    public static void ribbonFlow(PanelSurface s, float x0, float mid0, float x1, float mid1,
                                  float thickness, int color, float rate, float scale, float time) {
        if (Math.abs(rate) < 1.0e-4f || scale <= 0.0f || x1 - x0 < 4.0f) {
            return;
        }
        final int pips = Math.max(2, Math.min(10, Math.round((x1 - x0) / 26.0f)));
        final float speed = Math.max(-3.0f, Math.min(3.0f, rate / scale));
        final float phase = (time * speed) % 1.0f;
        for (int i = 0; i < pips; i++) {
            float t = (i / (float) pips + phase) % 1.0f;
            if (t < 0.0f) {
                t += 1.0f;
            }
            final float e = t * t * (3.0f - 2.0f * t);
            final float x = x0 + (x1 - x0) * t;
            final float y = mid0 + (mid1 - mid0) * e;
            final float fade = Math.min(1.0f, Math.min(t, 1.0f - t) * 5.0f);
            Shapes.circle(s, x, y, thickness, Tokens.alpha(color, 0.9f * fade));
        }
    }

    public static void vessel(PanelSurface s, Rect r, float fill, int color,
                              float agitation, float time) {
        if (r.isEmpty()) {
            return;
        }
        final float clamped = Math.max(0.0f, Math.min(1.0f, fill));
        Shapes.roundedRect(s, r, 4.0f, Tokens.alpha(Tokens.CANVAS, 0.85f));

        if (clamped > 0.001f) {
            final float amp = Math.max(0.0f, Math.min(1.0f, agitation)) * Math.min(3.5f, r.h * 0.03f);
            final float surfaceY = r.bottom() - r.h * clamped;
            final int steps = Math.max(6, Math.min(48, Math.round(r.w / 5.0f)));
            final float step = r.w / steps;

            for (int i = 0; i < steps; i++) {
                final float xa = r.x + i * step;
                final float xb = xa + step;
                final float ya = surfaceY + wave(xa - r.x, r.w, amp, time);
                final float yb = surfaceY + wave(xb - r.x, r.w, amp, time);
                Shapes.quad(s, xa, ya, xb, yb, xb, r.bottom(), xa, r.bottom(),
                        Tokens.alpha(color, 0.55f), Tokens.alpha(color, 0.55f),
                        Tokens.alpha(color, 0.85f), Tokens.alpha(color, 0.85f));
                Shapes.line(s, xa, ya, xb, yb, 1.4f, color);
            }
        }
        Shapes.roundedRectOutline(s, r, 4.0f, 1.0f, Tokens.LINE_HI);
    }

    private static float wave(float x, float width, float amp, float time) {
        if (amp <= 0.0f) {
            return 0.0f;
        }
        final float k = (float) (Math.PI * 2.0 / Math.max(24.0f, width * 0.55f));
        return (float) (Math.sin(x * k + time * 2.6) * amp
                + Math.sin(x * k * 1.7 - time * 1.9) * amp * 0.45);
    }

    public static void cells(PanelSurface s, Rect r, int columns, int rows, float fill,
                             int color, boolean charging, float time) {
        if (r.isEmpty() || columns < 1 || rows < 1) {
            return;
        }
        final float gap = Math.max(1.0f, Math.min(3.0f, Math.min(r.w / columns, r.h / rows) * 0.14f));
        final float cw = (r.w - gap * (columns - 1)) / columns;
        final float ch = (r.h - gap * (rows - 1)) / rows;
        final int total = columns * rows;
        final float lit = Math.max(0.0f, Math.min(1.0f, fill)) * total;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                final int index = (rows - 1 - row) * columns + col;
                final Rect cell = new Rect(r.x + col * (cw + gap), r.y + row * (ch + gap), cw, ch);
                final float amount = Math.max(0.0f, Math.min(1.0f, lit - index));
                Shapes.roundedRect(s, cell, 2.0f, Tokens.alpha(Tokens.LINE, 0.9f));
                if (amount > 0.001f) {
                    final Rect filled = new Rect(cell.x, cell.bottom() - cell.h * amount,
                            cell.w, cell.h * amount);
                    final boolean edgeCell = amount < 0.999f;
                    final float pulse = charging && edgeCell
                            ? 0.72f + 0.28f * (float) Math.sin(time * 5.0) : 1.0f;
                    Shapes.roundedRect(s, filled, 2.0f, Tokens.alpha(color, 0.92f * pulse));
                }
            }
        }
    }

    public static void element(PanelSurface s, float cx, float cy, float radius,
                               float heat, float time) {
        final float h = Math.max(0.0f, Math.min(1.0f, heat));
        final int core = heatColor(h);
        Shapes.circle(s, cx, cy, radius * 0.42f, Tokens.alpha(core, 0.16f + 0.5f * h));

        if (h > 0.02f) {
            final int shells = 3;
            final float period = 1.6f - h * 1.0f;
            for (int i = 0; i < shells; i++) {
                final float t = (float) (((time / period) + i / (float) shells) % 1.0);
                final float rr = radius * (0.42f + 0.58f * t);
                Shapes.arc(s, cx, cy, rr, Math.max(1.2f, radius * 0.05f), 0.0f,
                        (float) (Math.PI * 2.0), Tokens.alpha(core, 0.55f * (1.0f - t) * h));
            }
        }
        Shapes.arc(s, cx, cy, radius * 0.42f, Math.max(1.6f, radius * 0.08f), 0.0f,
                (float) (Math.PI * 2.0), Tokens.alpha(core, 0.35f + 0.65f * h));
    }

    public static int heatColor(float heat) {
        final float h = Math.max(0.0f, Math.min(1.0f, heat));
        if (h < 0.02f) {
            return Tokens.LINE_HI;
        }
        if (h < 0.34f) {
            return lerp(0xFF6B2410, 0xFFE4622A, h / 0.34f);
        }
        if (h < 0.67f) {
            return lerp(0xFFE4622A, 0xFFFBBF24, (h - 0.34f) / 0.33f);
        }
        return lerp(0xFFFBBF24, 0xFFFFF3D0, (h - 0.67f) / 0.33f);
    }

    public static void flame(PanelSurface s, float cx, float baseY, float width, float height,
                             float intensity, float time) {
        final float k = Math.max(0.0f, Math.min(1.0f, intensity));
        if (k < 0.01f || height <= 0.0f) {
            return;
        }
        for (int lobe = 0; lobe < 2; lobe++) {
            final float seed = lobe * 2.3f;
            final float flicker = 0.82f + 0.18f * (float) Math.sin(time * (9.0 + lobe * 4.0) + seed);
            final float h = height * k * flicker * (lobe == 0 ? 1.0f : 0.62f);
            final float w = width * (lobe == 0 ? 1.0f : 0.55f);
            final float lean = (float) Math.sin(time * (3.0 + lobe) + seed) * w * 0.18f;
            final int color = lobe == 0 ? heatColor(0.45f + 0.3f * k) : heatColor(0.85f);
            s.tri(cx - w * 0.5f, baseY, Tokens.alpha(color, 0.10f),
                    cx + w * 0.5f, baseY, Tokens.alpha(color, 0.10f),
                    cx + lean, baseY - h, Tokens.alpha(color, 0.85f));
        }
    }

    public static int fluidColor(String fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return Tokens.SERIES[0];
        }
        final String f = fluid.toLowerCase(java.util.Locale.ROOT);
        if (f.contains("lava")) {
            return 0xFFE4622A;
        }
        if (f.contains("water")) {
            return 0xFF3B82F6;
        }
        if (f.contains("honey")) {
            return 0xFFFBBF24;
        }
        if (f.contains("chocolate")) {
            return 0xFF8A5A34;
        }
        if (f.contains("crude") || f.contains("oil")) {
            return 0xFF6C5CE0;
        }
        if (f.contains("diesel")) {
            return 0xFFBC8309;
        }
        if (f.contains("gasoline") || f.contains("petrol")) {
            return 0xFF0FA97A;
        }
        if (f.contains("steam")) {
            return 0xFF98A0AD;
        }
        return Tokens.seriesFor(f);
    }

    public static void stat(PanelSurface s, Rect r, String label, String value, String unit,
                            int valueColor, float valueScale) {
        if (r.isEmpty()) {
            return;
        }
        s.text(Text.truncate(s, label.toUpperCase(java.util.Locale.ROOT), r.w, Text.MICRO),
                r.x, r.y, Tokens.INK_3, Text.MICRO);
        final float y = r.y + Text.lineHeight(Text.MICRO) + 3.0f;
        s.text(value, r.x, y, valueColor, valueScale);
        if (unit != null && !unit.isEmpty()) {
            s.text(unit, r.x + s.textWidth(value, valueScale) + 3.0f,
                    y + Text.lineHeight(valueScale) - Text.lineHeight(Text.MICRO),
                    Tokens.INK_3, Text.MICRO);
        }
    }

    public static float chip(PanelSurface s, float x, float y, String text, int color) {
        final float w = s.textWidth(text, Text.MICRO) + 24.0f;
        final Rect r = new Rect(x, y, w, 17.0f);
        Shapes.roundedRect(s, r, 8.5f, Tokens.alpha(color, 0.12f));
        Shapes.roundedRectOutline(s, r, 8.5f, 1.0f, Tokens.alpha(color, 0.45f));
        Shapes.circle(s, r.x + 9.0f, r.centerY(), 3.0f, color);
        s.textV(text, r.x + 16.0f, r.centerY(), color, Text.MICRO);
        return r.right() + 6.0f;
    }

    public static void machineHeader(PanelSurface s, Rect r, String name, String state, int color) {
        s.text(Text.truncate(s, name, r.w - s.textWidth(state, Text.MICRO) - 34.0f, Text.META),
                r.x, r.y, Tokens.INK, Text.META);
        if (state != null && !state.isEmpty()) {
            chip(s, r.right() - s.textWidth(state, Text.MICRO) - 24.0f, r.y - 3.0f, state, color);
        }
    }

    public static int lerp(int a, int b, float t) {
        final float k = Math.max(0.0f, Math.min(1.0f, t));
        return channel(a, b, k, 24) << 24 | channel(a, b, k, 16) << 16
                | channel(a, b, k, 8) << 8 | channel(a, b, k, 0);
    }

    private static int channel(int a, int b, float t, int shift) {
        final int ca = (a >>> shift) & 0xFF;
        final int cb = (b >>> shift) & 0xFF;
        return Math.round(ca + (cb - ca) * t);
    }

    public static String duration(float seconds, float horizonSeconds) {
        if (!Float.isFinite(seconds) || seconds <= 0.0f || seconds > horizonSeconds) {
            return null;
        }
        if (seconds < 90.0f) {
            return Math.round(seconds) + "s";
        }
        if (seconds < 5400.0f) {
            return Math.round(seconds / 60.0f) + "m";
        }
        return String.format(java.util.Locale.ROOT, "%.1fh", seconds / 3600.0f);
    }

    public static float slope(float[] data, int count) {
        if (data == null || count < 4) {
            return 0.0f;
        }
        final int n = Math.min(count, data.length);
        final int start = Math.max(0, n - 60);
        final int len = n - start;
        if (len < 4) {
            return 0.0f;
        }
        float sx = 0.0f;
        float sy = 0.0f;
        float sxy = 0.0f;
        float sxx = 0.0f;
        for (int i = 0; i < len; i++) {
            final float x = i;
            final float y = data[start + i];
            sx += x;
            sy += y;
            sxy += x * y;
            sxx += x * x;
        }
        final float denom = len * sxx - sx * sx;
        if (Math.abs(denom) < 1.0e-6f) {
            return 0.0f;
        }
        return (len * sxy - sx * sy) / denom;
    }
}
