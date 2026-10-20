package com.createstats.vccmk3.client;

import net.minecraft.client.gui.GuiGraphics;

public final class ConsoleDraw {
    private ConsoleDraw() {}

    public static void pixel(GuiGraphics graphics, int x, int y, int argb) {
        graphics.fill(x, y, x + 1, y + 1, argb);
    }

    public static void outline(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + 1, argb);
        graphics.fill(x, y + h - 1, x + w, y + h, argb);
        graphics.fill(x, y, x + 1, y + h, argb);
        graphics.fill(x + w - 1, y, x + w, y + h, argb);
    }

    public static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int guard = 0;
        while (guard++ < 4096) {
            pixel(graphics, x0, y0, argb);
            if (x0 == x1 && y0 == y1) return;
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    public static void thickLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int thickness, int argb) {
        if (thickness <= 1) {
            line(graphics, x0, y0, x1, y1, argb);
            return;
        }
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        int half = thickness / 2;
        for (int offset = -half; offset <= half; offset++) {
            if (steep) {
                line(graphics, x0 + offset, y0, x1 + offset, y1, argb);
            } else {
                line(graphics, x0, y0 + offset, x1, y1 + offset, argb);
            }
        }
    }

    public static void circle(GuiGraphics graphics, int cx, int cy, int radius, int argb) {
        if (radius <= 0) return;
        int x = radius;
        int y = 0;
        int error = 1 - radius;
        while (x >= y) {
            plotOctants(graphics, cx, cy, x, y, argb);
            y++;
            if (error < 0) {
                error += 2 * y + 1;
            } else {
                x--;
                error += 2 * (y - x) + 1;
            }
        }
    }

    public static void ring(GuiGraphics graphics, int cx, int cy, int radius, int thickness, int argb) {
        for (int i = 0; i < Math.max(1, thickness); i++) {
            circle(graphics, cx, cy, radius - i, argb);
        }
    }

    public static void disc(GuiGraphics graphics, int cx, int cy, int radius, int argb) {
        if (radius <= 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy));
            graphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, argb);
        }
    }

    public static void polygon(GuiGraphics graphics, int[] xs, int[] ys, int argb) {
        int n = Math.min(xs.length, ys.length);
        if (n < 3) return;

        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            top = Math.min(top, ys[i]);
            bottom = Math.max(bottom, ys[i]);
        }
        if (bottom <= top) return;

        double[] crossings = new double[n];
        for (int y = top; y < bottom; y++) {
            int count = 0;
            double scan = y + 0.5;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double y0 = ys[j], y1 = ys[i];
                if ((y0 <= scan && y1 > scan) || (y1 <= scan && y0 > scan)) {
                    double t = (scan - y0) / (y1 - y0);
                    crossings[count++] = xs[j] + t * (xs[i] - xs[j]);
                }
            }
            if (count < 2) continue;
            java.util.Arrays.sort(crossings, 0, count);
            for (int i = 0; i + 1 < count; i += 2) {
                int x0 = (int) Math.round(crossings[i]);
                int x1 = (int) Math.round(crossings[i + 1]);
                if (x1 > x0) graphics.fill(x0, y, x1, y + 1, argb);
            }
        }
    }

    public static void polyOutline(GuiGraphics graphics, int[] xs, int[] ys, int argb) {
        int n = Math.min(xs.length, ys.length);
        for (int i = 0, j = n - 1; i < n; j = i++) {
            line(graphics, xs[j], ys[j], xs[i], ys[i], argb);
        }
    }

    public static void polyDashed(GuiGraphics graphics, int[] xs, int[] ys, int argb) {
        int n = Math.min(xs.length, ys.length);
        for (int i = 0, j = n - 1; i < n; j = i++) {
            dashedLine(graphics, xs[j], ys[j], xs[i], ys[i], argb, 3, 3);
        }
    }

    public static void dashedLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                  int argb, int dash, int gap) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        if (length < 1) return;
        int steps = (int) length;
        for (int i = 0; i < steps; i += dash + gap) {
            double t0 = i / length;
            double t1 = Math.min(1.0, (i + dash) / length);
            line(graphics,
                    (int) Math.round(x0 + (x1 - x0) * t0), (int) Math.round(y0 + (y1 - y0) * t0),
                    (int) Math.round(x0 + (x1 - x0) * t1), (int) Math.round(y0 + (y1 - y0) * t1),
                    argb);
        }
    }

    public static void horizonDisc(GuiGraphics graphics, int cx, int cy, int r,
                                   double offset, double slope, int skyArgb, int groundArgb,
                                   int lineArgb) {
        if (r <= 0) return;
        disc(graphics, cx, cy, r, skyArgb);

        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
            if (span < 0) continue;
            double k = dy - offset;

            int from, to;
            if (slope > 1.0e-9) {
                int limit = (int) Math.ceil(k / slope) - 1;
                from = -span;
                to = Math.min(span, limit);
            } else if (slope < -1.0e-9) {
                int limit = (int) Math.floor(k / slope) + 1;
                from = Math.max(-span, limit);
                to = span;
            } else {
                if (k <= 0) continue;
                from = -span;
                to = span;
            }
            if (to < from) continue;
            graphics.fill(cx + from, cy + dy, cx + to + 1, cy + dy + 1, groundArgb);
        }

        for (int dx = -r; dx <= r; dx++) {
            int dy = (int) Math.round(offset + dx * slope);
            if (dx * dx + dy * dy <= r * r) {
                pixel(graphics, cx + dx, cy + dy, lineArgb);
            }
        }
    }

    private static void plotOctants(GuiGraphics graphics, int cx, int cy, int x, int y, int argb) {
        pixel(graphics, cx + x, cy + y, argb);
        pixel(graphics, cx - x, cy + y, argb);
        pixel(graphics, cx + x, cy - y, argb);
        pixel(graphics, cx - x, cy - y, argb);
        pixel(graphics, cx + y, cy + x, argb);
        pixel(graphics, cx - y, cy + x, argb);
        pixel(graphics, cx + y, cy - x, argb);
        pixel(graphics, cx - y, cy - x, argb);
    }

    public static void tick(GuiGraphics graphics, int cx, int cy, int inner, int outer,
                            double degrees, int argb) {
        double radians = Math.toRadians(degrees - 90.0);
        int x0 = cx + (int) Math.round(Math.cos(radians) * inner);
        int y0 = cy + (int) Math.round(Math.sin(radians) * inner);
        int x1 = cx + (int) Math.round(Math.cos(radians) * outer);
        int y1 = cy + (int) Math.round(Math.sin(radians) * outer);
        line(graphics, x0, y0, x1, y1, argb);
    }

    public static void needle(GuiGraphics graphics, int cx, int cy, int radius,
                              double degrees, int thickness, int argb) {
        double radians = Math.toRadians(degrees - 90.0);
        int x1 = cx + (int) Math.round(Math.cos(radians) * radius);
        int y1 = cy + (int) Math.round(Math.sin(radians) * radius);
        thickLine(graphics, cx, cy, x1, y1, thickness, argb);
    }

    public static int[] onCircle(int cx, int cy, int radius, double degrees) {
        double radians = Math.toRadians(degrees - 90.0);
        return new int[] {
                cx + (int) Math.round(Math.cos(radians) * radius),
                cy + (int) Math.round(Math.sin(radians) * radius)
        };
    }

    // centre anchored, left anchored can't show no thrust vs full reverse
    public static void signedBar(GuiGraphics graphics, int x, int y, int w, int h,
                                 double fraction, int trackArgb, int edgeArgb, int fillArgb) {
        graphics.fill(x, y, x + w, y + h, trackArgb);
        outline(graphics, x, y, w, h, edgeArgb);
        int mid = x + w / 2;
        graphics.fill(mid, y, mid + 1, y + h, edgeArgb);

        double clamped = Math.max(-1.0, Math.min(1.0, fraction));
        int span = (int) Math.round(Math.abs(clamped) * ((w - 2) / 2.0));
        if (span <= 0) return;
        if (clamped >= 0) {
            graphics.fill(mid, y + 1, mid + span, y + h - 1, fillArgb);
        } else {
            graphics.fill(mid - span, y + 1, mid, y + h - 1, fillArgb);
        }
    }

    public static void bar(GuiGraphics graphics, int x, int y, int w, int h,
                           double fraction, int trackArgb, int edgeArgb, int fillArgb) {
        graphics.fill(x, y, x + w, y + h, trackArgb);
        outline(graphics, x, y, w, h, edgeArgb);
        int span = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * (w - 2));
        if (span > 0) graphics.fill(x + 1, y + 1, x + 1 + span, y + h - 1, fillArgb);
    }

    public static int blend(int a, int b, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * clamped);
        int rr = Math.round(ar + (br - ar) * clamped);
        int rg = Math.round(ag + (bg - ag) * clamped);
        int rb = Math.round(ab + (bb - ab) * clamped);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }
}
