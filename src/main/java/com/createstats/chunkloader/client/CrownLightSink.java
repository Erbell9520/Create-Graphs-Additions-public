package com.createstats.chunkloader.client;

import com.createstats.chunkloader.look.CrownFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class CrownLightSink implements CrownFx.Sink {
    public static final int MODE_LIGHT = 0;
    public static final int MODE_FLOOR = 1;

    public static final int MAX_SPARKS = 8;

    public final boolean[] floor = new boolean[25];
    public final double[] sparks = new double[MAX_SPARKS * 3];
    public int sparkCount;

    private final Vector3f v = new Vector3f();
    private VertexConsumer vc;
    private Matrix4f pose;
    private int mode;
    private boolean collect;

    private float rx;
    private float ry;
    private float rz;
    private float ux;
    private float uy;
    private float uz;
    private float cx;
    private float cy;
    private float cz;

    public void begin(VertexConsumer consumer, Matrix4f at, int passMode, Vector3f right, Vector3f up, Vector3f camera,
                      boolean sparks) {
        vc = consumer;
        pose = at;
        mode = passMode;
        rx = right.x;
        ry = right.y;
        rz = right.z;
        ux = up.x;
        uy = up.y;
        uz = up.z;
        cx = camera.x;
        cy = camera.y;
        cz = camera.z;
        collect = sparks;
        if (sparks) {
            sparkCount = 0;
        }
    }

    public void end() {
        vc = null;
    }

    private void vertex(double x, double y, double z, int rgb, double alpha) {
        pose.transformPosition((float) x, (float) y, (float) z, v);
        vc.addVertex(v.x, v.y, v.z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                (int) Math.round(255 * Math.max(0.0, Math.min(1.0, alpha))));
    }

    @Override
    public void glow(double x, double y, double z, double radius, int rgb, double alpha, double core, double nudge) {
        if (mode != MODE_LIGHT || alpha <= 0.004 || radius <= 0.0) {
            return;
        }

        double tx = cx - x;
        double ty = cy - y;
        double tz = cz - z;
        final double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl > 1e-6) {
            tx /= tl;
            ty /= tl;
            tz /= tl;
        }
        final double px = x + tx * nudge;
        final double py = y + ty * nudge;
        final double pz = z + tz * nudge;
        if (radius < 1.0) {
            final double r = radius * 1.2;
            square(px, py, pz, r, rgb, alpha * 0.6);
            return;
        }

        final int n = 8;
        final double r1 = radius;
        final double r2 = radius * 2.2;
        for (int k = 0; k < n; k++) {
            final double a0 = 2 * Math.PI * k / n;
            final double a1 = 2 * Math.PI * (k + 1) / n;
            final double c0 = Math.cos(a0);
            final double s0 = Math.sin(a0);
            final double c1 = Math.cos(a1);
            final double s1 = Math.sin(a1);
            vertex(px, py, pz, rgb, alpha);
            vertex(px + (rx * c0 + ux * s0) * r1, py + (ry * c0 + uy * s0) * r1, pz + (rz * c0 + uz * s0) * r1, rgb,
                    alpha * 0.32);
            vertex(px + (rx * c1 + ux * s1) * r1, py + (ry * c1 + uy * s1) * r1, pz + (rz * c1 + uz * s1) * r1, rgb,
                    alpha * 0.32);
            vertex(px, py, pz, rgb, alpha);
            vertex(px + (rx * c0 + ux * s0) * r1, py + (ry * c0 + uy * s0) * r1, pz + (rz * c0 + uz * s0) * r1, rgb,
                    alpha * 0.32);
            vertex(px + (rx * c0 + ux * s0) * r2, py + (ry * c0 + uy * s0) * r2, pz + (rz * c0 + uz * s0) * r2, rgb,
                    0.0);
            vertex(px + (rx * c1 + ux * s1) * r2, py + (ry * c1 + uy * s1) * r2, pz + (rz * c1 + uz * s1) * r2, rgb,
                    0.0);
            vertex(px + (rx * c1 + ux * s1) * r1, py + (ry * c1 + uy * s1) * r1, pz + (rz * c1 + uz * s1) * r1, rgb,
                    alpha * 0.32);
        }
        if (core > 0.011) {
            final double rc = radius * core;
            for (int k = 0; k < 6; k++) {
                final double a0 = 2 * Math.PI * k / 6;
                final double a1 = 2 * Math.PI * (k + 1) / 6;
                vertex(px, py, pz, 0xFFFFFF, alpha * 0.85);
                vertex(px + (rx * Math.cos(a0) + ux * Math.sin(a0)) * rc, py + (ry * Math.cos(a0) + uy * Math.sin(a0)) * rc,
                        pz + (rz * Math.cos(a0) + uz * Math.sin(a0)) * rc, 0xFFFFFF, 0.0);
                vertex(px + (rx * Math.cos(a1) + ux * Math.sin(a1)) * rc, py + (ry * Math.cos(a1) + uy * Math.sin(a1)) * rc,
                        pz + (rz * Math.cos(a1) + uz * Math.sin(a1)) * rc, 0xFFFFFF, 0.0);
                vertex(px, py, pz, 0xFFFFFF, alpha * 0.85);
            }
        }
    }

    private void square(double x, double y, double z, double r, int rgb, double alpha) {
        vertex(x - (rx + ux) * r, y - (ry + uy) * r, z - (rz + uz) * r, rgb, alpha);
        vertex(x + (rx - ux) * r, y + (ry - uy) * r, z + (rz - uz) * r, rgb, alpha);
        vertex(x + (rx + ux) * r, y + (ry + uy) * r, z + (rz + uz) * r, rgb, alpha);
        vertex(x - (rx - ux) * r, y - (ry - uy) * r, z - (rz - uz) * r, rgb, alpha);
    }

    @Override
    public void stroke(double ax, double ay, double az, double bx, double by, double bz, double core, double sheath,
                       int coreRgb, int sheathRgb, double alpha) {
        if (mode != MODE_LIGHT || alpha <= 0.004) {
            return;
        }

        final double dx = bx - ax;
        final double dy = by - ay;
        final double dz = bz - az;
        final double vx = cx - (ax + bx) / 2;
        final double vy = cy - (ay + by) / 2;
        final double vz = cz - (az + bz) / 2;
        double wx = dy * vz - dz * vy;
        double wy = dz * vx - dx * vz;
        double wz = dx * vy - dy * vx;
        final double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1e-9) {
            return;
        }
        wx /= wl;
        wy /= wl;
        wz /= wl;
        final double h = core / 2;
        vertex(ax - wx * h, ay - wy * h, az - wz * h, coreRgb, alpha);
        vertex(bx - wx * h, by - wy * h, bz - wz * h, coreRgb, alpha);
        vertex(bx + wx * h, by + wy * h, bz + wz * h, coreRgb, alpha);
        vertex(ax + wx * h, ay + wy * h, az + wz * h, coreRgb, alpha);
        final double s = sheath * 0.75;
        final double a = alpha * 0.55;
        for (int side = -1; side <= 1; side += 2) {
            vertex(ax, ay, az, sheathRgb, a);
            vertex(bx, by, bz, sheathRgb, a);
            vertex(bx + side * wx * s, by + side * wy * s, bz + side * wz * s, sheathRgb, 0.0);
            vertex(ax + side * wx * s, ay + side * wy * s, az + side * wz * s, sheathRgb, 0.0);
        }
    }

    @Override
    public void band(double y, double r0, double r1, double a0, double a1, int rgb, double alpha0, double alpha1,
                     boolean onFloor) {
        if (mode != MODE_FLOOR || Math.max(alpha0, alpha1) <= 0.004) {
            return;
        }
        final double span = a1 - a0;
        final int segs = Math.max(2, (int) Math.ceil(Math.abs(span) / 360.0 * (r1 > 12.0 ? 40 : 28)));
        for (int i = 0; i < segs; i++) {
            final double t0 = Math.toRadians(a0 + span * i / segs);
            final double t1 = Math.toRadians(a0 + span * (i + 1) / segs);
            if (onFloor) {
                final double tm = (t0 + t1) / 2;
                final double rm = (r0 + r1) / 2;
                final int ci = (int) Math.floor((8.0 + rm * Math.cos(tm)) / 16.0);
                final int cj = (int) Math.floor((8.0 - rm * Math.sin(tm)) / 16.0);
                if (ci < -2 || ci > 2 || cj < -2 || cj > 2 || !floor[(ci + 2) * 5 + (cj + 2)]) {
                    continue;
                }
            }
            final double c0 = Math.cos(t0);
            final double s0 = Math.sin(t0);
            final double c1 = Math.cos(t1);
            final double s1 = Math.sin(t1);
            vertex(8 + r0 * c0, y, 8 - r0 * s0, rgb, alpha0);
            vertex(8 + r1 * c0, y, 8 - r1 * s0, rgb, alpha1);
            vertex(8 + r1 * c1, y, 8 - r1 * s1, rgb, alpha1);
            vertex(8 + r0 * c1, y, 8 - r0 * s1, rgb, alpha0);
        }
    }

    @Override
    public void flare(double x, double y, double z, double halfH, double halfV, double width, int coreRgb,
                      int glowRgb, double alpha) {
        if (mode != MODE_LIGHT || alpha <= 0.004) {
            return;
        }
        stroke(x - rx * halfH, y - ry * halfH, z - rz * halfH, x + rx * halfH, y + ry * halfH, z + rz * halfH, width,
                width * 3.0, coreRgb, glowRgb, alpha);
        stroke(x - ux * halfV, y - uy * halfV, z - uz * halfV, x + ux * halfV, y + uy * halfV, z + uz * halfV, width,
                width * 3.0, coreRgb, glowRgb, alpha);
    }

    @Override
    public void star(double x, double y, double z, double size, double alpha) {
        if (mode != MODE_LIGHT || alpha <= 0.004) {
            return;
        }

        final double l = size * 0.95;
        final double w = 0.09;
        vertex(x - rx * l - ux * w, y - ry * l - uy * w, z - rz * l - uz * w, 0xFFFFFF, alpha);
        vertex(x + rx * l - ux * w, y + ry * l - uy * w, z + rz * l - uz * w, 0xFFFFFF, alpha);
        vertex(x + rx * l + ux * w, y + ry * l + uy * w, z + rz * l + uz * w, 0xFFFFFF, alpha);
        vertex(x - rx * l + ux * w, y - ry * l + uy * w, z - rz * l + uz * w, 0xFFFFFF, alpha);
        vertex(x - ux * l - rx * w, y - uy * l - ry * w, z - uz * l - rz * w, 0xFFFFFF, alpha);
        vertex(x - ux * l + rx * w, y - uy * l + ry * w, z - uz * l + rz * w, 0xFFFFFF, alpha);
        vertex(x + ux * l + rx * w, y + uy * l + ry * w, z + uz * l + rz * w, 0xFFFFFF, alpha);
        vertex(x + ux * l - rx * w, y + uy * l - ry * w, z + uz * l - rz * w, 0xFFFFFF, alpha);
        square(x, y, z, size * 0.32, 0xFFFFFF, alpha);
    }

    @Override
    public void front(double x0, double y0, double x1, double y1, double z, int rgb, double alpha) {
        if (mode != MODE_LIGHT || alpha <= 0.004) {
            return;
        }
        vertex(x0, y0, z, rgb, alpha);
        vertex(x1, y0, z, rgb, alpha);
        vertex(x1, y1, z, rgb, alpha);
        vertex(x0, y1, z, rgb, alpha);
    }

    @Override
    public void spark(double x, double y, double z) {
        if (collect && sparkCount < MAX_SPARKS) {
            sparks[sparkCount * 3] = x;
            sparks[sparkCount * 3 + 1] = y;
            sparks[sparkCount * 3 + 2] = z;
            sparkCount++;
        }
    }
}
