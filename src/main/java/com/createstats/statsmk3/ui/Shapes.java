package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Shapes {
    private Shapes() {
    }

    public static void rect(PanelSurface s, Rect r, int color) {
        rect(s, r.x, r.y, r.w, r.h, color);
    }

    public static void rect(PanelSurface s, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0 || isClear(color)) {
            return;
        }
        quad(s, x, y, x + w, y, x + w, y + h, x, y + h, color, color, color, color);
    }

    public static void gradientV(PanelSurface s, Rect r, int top, int bottom) {
        if (r.isEmpty()) {
            return;
        }
        quad(s, r.x, r.y, r.right(), r.y, r.right(), r.bottom(), r.x, r.bottom(),
                top, top, bottom, bottom);
    }

    public static void gradientH(PanelSurface s, Rect r, int left, int right) {
        if (r.isEmpty()) {
            return;
        }
        quad(s, r.x, r.y, r.right(), r.y, r.right(), r.bottom(), r.x, r.bottom(),
                left, right, right, left);
    }

    public static void roundedRect(PanelSurface s, Rect r, float radius, int color) {
        if (r.isEmpty() || isClear(color)) {
            return;
        }
        fillPolygon(s, roundedOutline(r, radius), color, s.feather(), 0.0f);
    }

    public static void shadow(PanelSurface s, Rect r, float radius, float spread, int color) {
        if (r.isEmpty() || isClear(color)) {
            return;
        }
        fillPolygon(s, roundedOutline(r, radius), color, spread, 0.0f);
    }

    public static void roundedRectOutline(PanelSurface s, Rect r, float radius, float thickness, int color) {
        if (r.isEmpty() || thickness <= 0 || isClear(color)) {
            return;
        }
        final Rect innerRect = r.inset(thickness);
        if (innerRect.isEmpty()) {
            roundedRect(s, r, radius, color);
            return;
        }
        final int segs = cornerSegments(radius);
        final float[] outer = roundedOutline(r, radius, segs);
        final float[] inner = roundedOutline(innerRect, Math.max(0.0f, radius - thickness), segs);
        ring(s, outer, inner, color);
    }

    public static void card(PanelSurface s, Rect r, float radius, int fill, int border) {
        roundedRect(s, r, radius, fill);
        roundedRectOutline(s, r, radius, 1.0f, border);
    }

    public static void circle(PanelSurface s, float cx, float cy, float radius, int color) {
        if (radius <= 0 || isClear(color)) {
            return;
        }
        final int segs = segmentsFor(radius);
        final float[] pts = new float[segs * 2];
        for (int i = 0; i < segs; i++) {
            final double a = (i / (double) segs) * Math.PI * 2.0;
            pts[i * 2] = cx + (float) Math.cos(a) * radius;
            pts[i * 2 + 1] = cy + (float) Math.sin(a) * radius;
        }
        fillPolygon(s, pts, color, s.feather(), 0.0f);
    }

    public static void arc(PanelSurface s, float cx, float cy, float radius, float thickness,
                           float from, float sweep, int color) {
        if (thickness <= 0 || Math.abs(sweep) < 1.0e-4f || isClear(color)) {
            return;
        }
        final float outerR = radius + thickness * 0.5f;
        final float innerR = Math.max(0.0f, radius - thickness * 0.5f);

        final float step = Math.max(0.08f,
                Math.min(0.5f, (float) Math.sqrt(2.4 / Math.max(1.0f, radius))));
        final int segs = Math.max(2, (int) Math.ceil(Math.abs(sweep) / step));
        final float[] outer = new float[(segs + 1) * 2];
        final float[] inner = new float[(segs + 1) * 2];
        for (int i = 0; i <= segs; i++) {
            final float a = from + sweep * (i / (float) segs);
            final float ca = (float) Math.cos(a);
            final float sa = (float) Math.sin(a);
            outer[i * 2] = cx + ca * outerR;
            outer[i * 2 + 1] = cy + sa * outerR;
            inner[i * 2] = cx + ca * innerR;
            inner[i * 2 + 1] = cy + sa * innerR;
        }
        strip(s, outer, inner, color);
    }

    public static void arcCap(PanelSurface s, float cx, float cy, float radius, float angle,
                              float thickness, int color) {
        circle(s, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius,
                thickness * 0.5f, color);
    }

    public static void line(PanelSurface s, float x1, float y1, float x2, float y2,
                            float thickness, int color) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-5f || thickness <= 0.0f || isClear(color)) {
            return;
        }
        final float nx = -dy / len * thickness * 0.5f;
        final float ny = dx / len * thickness * 0.5f;
        final float ax = x1 + nx;
        final float ay = y1 + ny;
        final float bx = x2 + nx;
        final float by = y2 + ny;
        final float cx = x2 - nx;
        final float cy = y2 - ny;
        final float dx0 = x1 - nx;
        final float dy0 = y1 - ny;
        quad(s, ax, ay, bx, by, cx, cy, dx0, dy0, color, color, color, color);

        final int transparent = Tokens.fade(color, 0.0f);
        final float feather = s.feather();
        final float fx = nx / (thickness * 0.5f) * feather;
        final float fy = ny / (thickness * 0.5f) * feather;
        quad(s, ax, ay, bx, by, bx + fx, by + fy, ax + fx, ay + fy,
                color, color, transparent, transparent);
        quad(s, dx0, dy0, cx, cy, cx - fx, cy - fy, dx0 - fx, dy0 - fy,
                color, color, transparent, transparent);
    }

    public static void polyline(PanelSurface s, float[] pts, int pointCount, float thickness, int color) {
        if (pointCount < 2 || isClear(color)) {
            return;
        }
        for (int i = 0; i < pointCount - 1; i++) {
            line(s, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], thickness, color);
        }
        if (thickness <= 1.6f) {
            return;
        }
        final float half = thickness * 0.5f;
        for (int i = 1; i < pointCount - 1; i++) {
            final float px = pts[i * 2];
            final float py = pts[i * 2 + 1];
            final float beforeDx = px - pts[(i - 1) * 2];
            final float beforeDy = py - pts[(i - 1) * 2 + 1];
            final float afterDx = pts[(i + 1) * 2] - px;
            final float afterDy = pts[(i + 1) * 2 + 1] - py;
            final float beforeLen = (float) Math.sqrt(beforeDx * beforeDx + beforeDy * beforeDy);
            final float afterLen = (float) Math.sqrt(afterDx * afterDx + afterDy * afterDy);
            if (beforeLen < 1.0e-5f || afterLen < 1.0e-5f) {
                continue;
            }
            final float beforeNx = -beforeDy / beforeLen * half;
            final float beforeNy = beforeDx / beforeLen * half;
            final float afterNx = -afterDy / afterLen * half;
            final float afterNy = afterDx / afterLen * half;
            s.tri(px, py, color, px + beforeNx, py + beforeNy, color,
                    px + afterNx, py + afterNy, color);
            s.tri(px, py, color, px - beforeNx, py - beforeNy, color,
                    px - afterNx, py - afterNy, color);
        }
    }

    public static void areaFill(PanelSurface s, float[] pts, int pointCount, float baseline,
                                int topColor, int bottomColor) {
        if (pointCount < 2) {
            return;
        }
        for (int i = 0; i < pointCount - 1; i++) {
            final float x1 = pts[i * 2];
            final float y1 = pts[i * 2 + 1];
            final float x2 = pts[i * 2 + 2];
            final float y2 = pts[i * 2 + 3];
            quad(s, x1, y1, x2, y2, x2, baseline, x1, baseline,
                    topColor, topColor, bottomColor, bottomColor);
        }
    }

    public static void bar(PanelSurface s, Rect r, int color) {
        if (r.isEmpty()) {
            return;
        }
        final float radius = Math.min(Tokens.BAR_END_RADIUS, Math.min(r.w, r.h) * 0.5f);
        roundedRect(s, r, radius, color);
    }

    private static boolean isClear(int argb) {
        return ((argb >>> 24) & 0xFF) == 0;
    }

    private static int segmentsFor(float radius) {
        return Math.max(6, Math.min(64, (int) Math.ceil(radius * 2.2f)));
    }

    public static void quad(PanelSurface s, float x1, float y1, float x2, float y2,
                            float x3, float y3, float x4, float y4,
                            int c1, int c2, int c3, int c4) {
        s.tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
        s.tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
    }

    private static int cornerSegments(float radius) {
        return Math.max(2, (int) Math.ceil(Math.max(0.0f, radius) * 0.9f) + 1);
    }

    private static float[] roundedOutline(Rect r, float radius) {
        return roundedOutline(r, radius, cornerSegments(radius));
    }

    private static float[] roundedOutline(Rect r, float radius, int segs) {
        final float rad = Math.max(0.0f, Math.min(radius, Math.min(r.w, r.h) * 0.5f));
        final float[] pts = new float[(segs + 1) * 4 * 2];
        int i = 0;
        final float[][] centers = {
                {r.x + rad, r.y + rad},
                {r.right() - rad, r.y + rad},
                {r.right() - rad, r.bottom() - rad},
                {r.x + rad, r.bottom() - rad}
        };
        final float[] startAngles = {
                (float) Math.PI, (float) (Math.PI * 1.5), 0.0f, (float) (Math.PI * 0.5)
        };
        for (int c = 0; c < 4; c++) {
            for (int seg = 0; seg <= segs; seg++) {
                final float a = startAngles[c] + (float) (Math.PI * 0.5) * (seg / (float) segs);
                pts[i++] = centers[c][0] + (float) Math.cos(a) * rad;
                pts[i++] = centers[c][1] + (float) Math.sin(a) * rad;
            }
        }
        return pts;
    }

    private static void fillPolygon(PanelSurface s, float[] pts, int color, float feather, float edgeAlphaMul) {
        final int n = pts.length / 2;
        if (n < 3) {
            return;
        }
        float cx = 0.0f;
        float cy = 0.0f;
        for (int i = 0; i < n; i++) {
            cx += pts[i * 2];
            cy += pts[i * 2 + 1];
        }
        cx /= n;
        cy /= n;

        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            s.tri(cx, cy, color, pts[i * 2], pts[i * 2 + 1], color, pts[j * 2], pts[j * 2 + 1], color);
        }
        if (feather <= 0.0f) {
            return;
        }
        final int edge = Tokens.fade(color, edgeAlphaMul);
        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            final float ax = pts[i * 2];
            final float ay = pts[i * 2 + 1];
            final float bx = pts[j * 2];
            final float by = pts[j * 2 + 1];
            final float[] an = outward(ax, ay, cx, cy, feather);
            final float[] bn = outward(bx, by, cx, cy, feather);
            quad(s, ax, ay, bx, by, bx + bn[0], by + bn[1], ax + an[0], ay + an[1],
                    color, color, edge, edge);
        }
    }

    private static float[] outward(float px, float py, float cx, float cy, float amount) {
        float dx = px - cx;
        float dy = py - cy;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-5f) {
            return new float[]{0.0f, 0.0f};
        }
        return new float[]{dx / len * amount, dy / len * amount};
    }

    // refuse mismatched outlines, they fill straight across the middle
    private static void ring(PanelSurface s, float[] outer, float[] inner, int color) {
        if (outer.length != inner.length) {
            return;
        }
        final int n = outer.length / 2;
        if (n < 3) {
            return;
        }
        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            quad(s, outer[i * 2], outer[i * 2 + 1], outer[j * 2], outer[j * 2 + 1],
                    inner[j * 2], inner[j * 2 + 1], inner[i * 2], inner[i * 2 + 1],
                    color, color, color, color);
        }
        float cx = 0.0f;
        float cy = 0.0f;
        for (int i = 0; i < n; i++) {
            cx += outer[i * 2];
            cy += outer[i * 2 + 1];
        }
        cx /= n;
        cy /= n;
        final int edge = Tokens.fade(color, 0.0f);
        final float f = s.feather();
        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            final float ax = outer[i * 2];
            final float ay = outer[i * 2 + 1];
            final float bx = outer[j * 2];
            final float by = outer[j * 2 + 1];
            final float[] an = outward(ax, ay, cx, cy, f);
            final float[] bn = outward(bx, by, cx, cy, f);
            quad(s, ax, ay, bx, by, bx + bn[0], by + bn[1], ax + an[0], ay + an[1],
                    color, color, edge, edge);
        }
    }

    private static void strip(PanelSurface s, float[] a, float[] b, int color) {
        final int n = Math.min(a.length, b.length) / 2;
        if (n < 2) {
            return;
        }
        for (int i = 0; i < n - 1; i++) {
            quad(s, a[i * 2], a[i * 2 + 1], a[i * 2 + 2], a[i * 2 + 3],
                    b[i * 2 + 2], b[i * 2 + 3], b[i * 2], b[i * 2 + 1], color, color, color, color);
        }
        featherEdge(s, a, b, n, color);
        featherEdge(s, b, a, n, color);
    }

    private static void featherEdge(PanelSurface s, float[] edge, float[] other, int n, int color) {
        final int transparent = Tokens.fade(color, 0.0f);
        final float f = s.feather();
        for (int i = 0; i < n - 1; i++) {
            final float ax = edge[i * 2];
            final float ay = edge[i * 2 + 1];
            final float bx = edge[i * 2 + 2];
            final float by = edge[i * 2 + 3];
            float dx = ax - other[i * 2];
            float dy = ay - other[i * 2 + 1];
            final float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0e-5f) {
                continue;
            }
            dx = dx / len * f;
            dy = dy / len * f;
            quad(s, ax, ay, bx, by, bx + dx, by + dy, ax + dx, ay + dy,
                    color, color, transparent, transparent);
        }
    }
}
