package com.createstats.holomk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Vector3f;

import com.createstats.holomk3.render.HoloSink;

public final class HoloRasterSink implements HoloSink {
    private static final int SS = 3;

    private final int w;
    private final int h;
    private final float[] accum;
    private final float[] base;
    private final float[] depth;

    private final Vector3f eye;
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final float focal;
    private final float pixelSize;
    private final float feather;

    private record SolidTri(float x1, float y1, float z1, int c1,
                            float x2, float y2, float z2, int c2,
                            float x3, float y3, float z3, int c3) {
    }

    private final List<SolidTri> solids = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    private final int background;

    public HoloRasterSink(int width, int height, Vector3f eye, Vector3f target, float fovDegrees,
                          int background) {
        this.w = width * SS;
        this.h = height * SS;
        this.accum = new float[this.w * this.h * 3];
        this.base = new float[this.w * this.h * 3];
        this.depth = new float[this.w * this.h];
        this.background = background;
        for (int i = 0; i < this.w * this.h; i++) {
            this.depth[i] = Float.MAX_VALUE;
            this.base[i * 3] = ((background >> 16) & 0xFF) / 255.0f;
            this.base[i * 3 + 1] = ((background >> 8) & 0xFF) / 255.0f;
            this.base[i * 3 + 2] = (background & 0xFF) / 255.0f;
        }

        this.eye = new Vector3f(eye);
        this.forward.set(target).sub(eye).normalize();

        this.right.set(this.forward).cross(new Vector3f(0.0f, 1.0f, 0.0f)).normalize();
        this.up.set(this.right).cross(this.forward).normalize();

        this.focal = (float) (this.h * 0.5 / Math.tan(Math.toRadians(fovDegrees) * 0.5));
        final float dist = eye.distance(target);
        this.pixelSize = 2.0f * (float) Math.tan(Math.toRadians(fovDegrees) * 0.5) * dist
                / (this.h / (float) SS);
        this.feather = 1.0f;
    }

    private float[] project(float x, float y, float z) {
        final float dx = x - this.eye.x;
        final float dy = y - this.eye.y;
        final float dz = z - this.eye.z;
        final float vz = dx * this.forward.x + dy * this.forward.y + dz * this.forward.z;
        if (vz <= 0.02f) {
            return null;
        }
        final float vx = dx * this.right.x + dy * this.right.y + dz * this.right.z;
        final float vy = dx * this.up.x + dy * this.up.y + dz * this.up.z;
        return new float[]{
                this.w * 0.5f + vx * this.focal / vz,
                this.h * 0.5f - vy * this.focal / vz,
                vz};
    }

    @Override
    public void tri(float x1, float y1, float z1, int c1,
                    float x2, float y2, float z2, int c2,
                    float x3, float y3, float z3, int c3) {
        raster(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3, true);
    }

    @Override
    public void solid(float x1, float y1, float z1, int c1,
                      float x2, float y2, float z2, int c2,
                      float x3, float y3, float z3, int c3) {
        this.solids.add(new SolidTri(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3));
    }

    public void flushSolids() {
        this.solids.sort(Comparator.comparingDouble(t -> -centroidDepth(t)));
        for (final SolidTri t : this.solids) {
            raster(t.x1(), t.y1(), t.z1(), t.c1(),
                    t.x2(), t.y2(), t.z2(), t.c2(),
                    t.x3(), t.y3(), t.z3(), t.c3(), false);
        }
        this.solids.clear();
    }

    private double centroidDepth(SolidTri t) {
        return this.eye.distance((t.x1() + t.x2() + t.x3()) / 3.0f,
                (t.y1() + t.y2() + t.y3()) / 3.0f,
                (t.z1() + t.z2() + t.z3()) / 3.0f);
    }

    private void raster(float x1, float y1, float z1, int c1,
                        float x2, float y2, float z2, int c2,
                        float x3, float y3, float z3, int c3, boolean additive) {
        if (((c1 >>> 24) & 0xFF) == 0 && ((c2 >>> 24) & 0xFF) == 0 && ((c3 >>> 24) & 0xFF) == 0) {
            return;
        }
        final float[] a = project(x1, y1, z1);
        final float[] b = project(x2, y2, z2);
        final float[] c = project(x3, y3, z3);
        if (a == null || b == null || c == null) {
            return;
        }
        float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < 1.0e-6f) {
            return;
        }
        float[] pa = a;
        float[] pb = b;
        float[] pc = c;
        int ca = c1;
        int cb = c2;
        int cc = c3;
        if (area < 0.0f) {
            pb = c;
            pc = b;
            cb = c3;
            cc = c2;
            area = -area;
        }

        final int minX = Math.max(0, (int) Math.floor(Math.min(pa[0], Math.min(pb[0], pc[0]))));
        final int maxX = Math.min(this.w - 1, (int) Math.ceil(Math.max(pa[0], Math.max(pb[0], pc[0]))));
        final int minY = Math.max(0, (int) Math.floor(Math.min(pa[1], Math.min(pb[1], pc[1]))));
        final int maxY = Math.min(this.h - 1, (int) Math.ceil(Math.max(pa[1], Math.max(pb[1], pc[1]))));
        if (minX > maxX || minY > maxY) {
            return;
        }
        final float inv = 1.0f / area;

        final float wa = 1.0f / pa[2];
        final float wb = 1.0f / pb[2];
        final float wc = 1.0f / pc[2];

        for (int py = minY; py <= maxY; py++) {
            final float sy = py + 0.5f;
            for (int px = minX; px <= maxX; px++) {
                final float sx = px + 0.5f;
                final float eAB = (pb[0] - pa[0]) * (sy - pa[1]) - (pb[1] - pa[1]) * (sx - pa[0]);
                final float eBC = (pc[0] - pb[0]) * (sy - pb[1]) - (pc[1] - pb[1]) * (sx - pb[0]);
                final float eCA = (pa[0] - pc[0]) * (sy - pc[1]) - (pa[1] - pc[1]) * (sx - pc[0]);
                if (eAB < 0.0f || eBC < 0.0f || eCA < 0.0f) {
                    continue;
                }
                final float u = eBC * inv;
                final float v = eCA * inv;
                final float t = eAB * inv;
                final float wsum = u * wa + v * wb + t * wc;
                if (wsum <= 0.0f) {
                    continue;
                }
                final float z = 1.0f / wsum;
                final int idx = py * this.w + px;
                if (z > this.depth[idx] + 1.0e-4f) {
                    continue;
                }
                final float pu = u * wa / wsum;
                final float pv = v * wb / wsum;
                final float pt = t * wc / wsum;
                final float alpha = (chan(ca, 24) * pu + chan(cb, 24) * pv + chan(cc, 24) * pt) / 255.0f;
                if (alpha <= 0.002f) {
                    continue;
                }
                final float r = (chan(ca, 16) * pu + chan(cb, 16) * pv + chan(cc, 16) * pt) / 255.0f;
                final float g = (chan(ca, 8) * pu + chan(cb, 8) * pv + chan(cc, 8) * pt) / 255.0f;
                final float bl = (chan(ca, 0) * pu + chan(cb, 0) * pv + chan(cc, 0) * pt) / 255.0f;
                if (additive) {
                    this.accum[idx * 3] += r * alpha;
                    this.accum[idx * 3 + 1] += g * alpha;
                    this.accum[idx * 3 + 2] += bl * alpha;
                    continue;
                }

                this.base[idx * 3] = r * alpha + this.base[idx * 3] * (1.0f - alpha);
                this.base[idx * 3 + 1] = g * alpha + this.base[idx * 3 + 1] * (1.0f - alpha);
                this.base[idx * 3 + 2] = bl * alpha + this.base[idx * 3 + 2] * (1.0f - alpha);
                this.depth[idx] = z;
            }
        }
    }

    private static float chan(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    private record Label(String text, float x, float y, float z, int color, float scale) {
    }

    @Override
    public void text(String s, float x, float y, float z, int color, float scale) {
        if (s == null || s.isEmpty() || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        this.labels.add(new Label(s, x, y, z, color, scale));
    }

    @Override
    public float textWidth(String s, float scale) {
        if (s == null) {
            return 0.0f;
        }

        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            width += switch (s.charAt(i)) {
                case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case ' ', 't', 'I', '[', ']' -> 4;
                case 'f', 'k', '(', ')', '{', '}', '<', '>', '"', '*' -> 5;
                case '@' -> 7;
                default -> 6;
            };
        }
        return width * scale;
    }

    @Override
    public Vector3f eye() {
        return this.eye;
    }

    @Override
    public Vector3f right() {
        return this.right;
    }

    @Override
    public Vector3f up() {
        return this.up;
    }

    @Override
    public float feather() {
        return this.feather;
    }

    @Override
    public float pixel() {
        return this.pixelSize;
    }

    public BufferedImage finish() {
        final BufferedImage full = new BufferedImage(this.w, this.h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < this.w * this.h; i++) {
            final int r = clamp(this.base[i * 3] + this.accum[i * 3]);
            final int g = clamp(this.base[i * 3 + 1] + this.accum[i * 3 + 1]);
            final int b = clamp(this.base[i * 3 + 2] + this.accum[i * 3 + 2]);
            full.setRGB(i % this.w, i / this.w, (r << 16) | (g << 8) | b);
        }

        final Graphics2D g2 = full.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (final Label label : this.labels) {
            final float[] p = project(label.x(), label.y(), label.z());
            if (p == null) {
                continue;
            }

            final float onScreen = label.scale() * 9.0f * this.focal / p[2];
            if (onScreen < 3.0f) {
                continue;
            }
            g2.setColor(new Color(label.color(), true));
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(3, Math.round(onScreen))));
            g2.drawString(label.text(), p[0], p[1] + onScreen * 0.78f);
        }
        g2.dispose();

        final BufferedImage out = new BufferedImage(this.w / SS, this.h / SS,
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        final int rgb = full.getRGB(x * SS + sx, y * SS + sy);
                        r += (rgb >> 16) & 0xFF;
                        g += (rgb >> 8) & 0xFF;
                        b += rgb & 0xFF;
                    }
                }
                final int n = SS * SS;
                out.setRGB(x, y, ((r / n) << 16) | ((g / n) << 8) | (b / n));
            }
        }
        return out;
    }

    private static int clamp(float v) {
        return v <= 0.0f ? 0 : v >= 1.0f ? 255 : Math.round(v * 255.0f);
    }

    public int background() {
        return this.background;
    }
}
