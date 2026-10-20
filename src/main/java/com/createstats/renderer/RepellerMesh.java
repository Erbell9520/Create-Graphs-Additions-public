package com.createstats.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;

// own sheet, borrowing the vacuum one would change our brass whenever theirs does
public final class RepellerMesh {
    public static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/mob_repeller_parts.png");

    private static final int SHEET_W = 128;
    private static final int SHEET_H = 48;
    private static final int TILE = 16;
    public static final int WHITE = 0xFFFFFFFF;

    public enum Tile {
        IRON_MID, IRON_DARK, BRASS, BRASS_PLATE, KNURL, GREEN, GREEN_LIP, HORN_MOUTH,
        TWEETER, SCOPE_ON, SCOPE_OFF, TRACE, NEEDLE, POINTER, LAMP_GREEN, LAMP_AMBER,
        LAMP_RED, LAMP_OFF, BEZEL;

        public float u(double px) {
            return (float) (((ordinal() % 8) * TILE + px) / SHEET_W);
        }

        public float v(double px) {
            return (float) (((ordinal() / 8) * TILE + px) / SHEET_H);
        }
    }

    private RepellerMesh() {
    }

    public static void quad(VertexConsumer vc, PoseStack.Pose pose, float[][] p, float[][] uv, float nx, float ny,
                            float nz, int light, int overlay) {
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(WHITE)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    public static void box(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1,
                           double y1, double z1, Tile tile, int light, int overlay) {
        final float ax = (float) x0;
        final float ay = (float) y0;
        final float az = (float) z0;
        final float bx = (float) x1;
        final float by = (float) y1;
        final float bz = (float) z1;
        final float w = Math.min(16f, bx - ax);
        final float h = Math.min(16f, by - ay);
        final float d = Math.min(16f, bz - az);
        quad(vc, pose, new float[][] {{bx, ay, az}, {ax, ay, az}, {ax, by, az}, {bx, by, az}}, uvs(tile, w, h),
                0, 0, -1, light, overlay);
        quad(vc, pose, new float[][] {{ax, ay, bz}, {bx, ay, bz}, {bx, by, bz}, {ax, by, bz}}, uvs(tile, w, h),
                0, 0, 1, light, overlay);
        quad(vc, pose, new float[][] {{ax, ay, az}, {ax, ay, bz}, {ax, by, bz}, {ax, by, az}}, uvs(tile, d, h),
                -1, 0, 0, light, overlay);
        quad(vc, pose, new float[][] {{bx, ay, bz}, {bx, ay, az}, {bx, by, az}, {bx, by, bz}}, uvs(tile, d, h),
                1, 0, 0, light, overlay);
        quad(vc, pose, new float[][] {{ax, by, bz}, {bx, by, bz}, {bx, by, az}, {ax, by, az}}, uvs(tile, w, d),
                0, 1, 0, light, overlay);
        quad(vc, pose, new float[][] {{ax, ay, az}, {bx, ay, az}, {bx, ay, bz}, {ax, ay, bz}}, uvs(tile, w, d),
                0, -1, 0, light, overlay);
    }

    private static float[][] uvs(Tile t, float w, float h) {
        final float u0 = t.u(0);
        final float u1 = t.u(w);
        final float v0 = t.v(0);
        final float v1 = t.v(h);
        return new float[][] {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
    }

    public static void prism(VertexConsumer vc, PoseStack.Pose pose, char axis, double c1, double c2, double a,
                             double lo, double hi, int n, Tile side, Tile cap, boolean capLo, boolean capHi,
                             double innerLo, double innerHi, int light, int overlay) {
        final double r = a / Math.cos(Math.PI / n);
        final double s = 2 * a * Math.tan(Math.PI / n);
        final int per = Math.max(1, (int) Math.round(16.0 / s));
        final double wUv = 16.0 / per;
        final double vLen = Math.min(16.0, hi - lo);
        if (side != null) {
            for (int k = 0; k < n; k++) {
                final double theta = k * 2 * Math.PI / n;
                final double pa = theta + Math.PI / n;
                final double pb = theta - Math.PI / n;
                final float[][] p = {
                        world(axis, c1, c2, r * Math.cos(pa), r * Math.sin(pa), lo),
                        world(axis, c1, c2, r * Math.cos(pb), r * Math.sin(pb), lo),
                        world(axis, c1, c2, r * Math.cos(pb), r * Math.sin(pb), hi),
                        world(axis, c1, c2, r * Math.cos(pa), r * Math.sin(pa), hi)};
                final double u0 = (Math.floorMod(-k, n) % per) * wUv;
                final float[][] uv = {
                        {side.u(u0), side.v(vLen)}, {side.u(u0 + wUv), side.v(vLen)},
                        {side.u(u0 + wUv), side.v(0)}, {side.u(u0), side.v(0)}};
                final float[] nrm = dir(axis, Math.cos(theta), Math.sin(theta), 0);
                quad(vc, pose, p, uv, nrm[0], nrm[1], nrm[2], light, overlay);
            }
        }
        final Tile capTile = cap == null ? side : cap;
        if (capLo && capTile != null) {
            cap(vc, pose, axis, c1, c2, r, n, lo, -1, innerLo, capTile, light, overlay);
        }
        if (capHi && capTile != null) {
            cap(vc, pose, axis, c1, c2, r, n, hi, 1, innerHi, capTile, light, overlay);
        }
    }

    private static void cap(VertexConsumer vc, PoseStack.Pose pose, char axis, double c1, double c2, double r, int n,
                            double at, int sign, double inner, Tile tile, int light, int overlay) {
        final double ri = inner > 0 ? inner / Math.cos(Math.PI / n) : 0.0;
        final float[] nrm = dir(axis, 0, 0, sign);
        for (int k = 0; k < n; k++) {
            final double t0 = (k + 0.5) * 2 * Math.PI / n;
            final double t1 = (k + 1.5) * 2 * Math.PI / n;
            double[][] loc = {
                    {ri * Math.cos(t0), ri * Math.sin(t0)}, {r * Math.cos(t0), r * Math.sin(t0)},
                    {r * Math.cos(t1), r * Math.sin(t1)}, {ri * Math.cos(t1), ri * Math.sin(t1)}};
            if (sign < 0) {
                loc = new double[][] {loc[3], loc[2], loc[1], loc[0]};
            }
            final float[][] p = new float[4][];
            final float[][] uv = new float[4][];
            for (int i = 0; i < 4; i++) {
                p[i] = world(axis, c1, c2, loc[i][0], loc[i][1], at);

                uv[i] = new float[] {tile.u(8 + clamp(loc[i][0])), tile.v(8 + clamp(loc[i][1]))};
            }
            quad(vc, pose, p, uv, nrm[0], nrm[1], nrm[2], light, overlay);
        }
    }

    public static void disc(VertexConsumer vc, PoseStack.Pose pose, char axis, double c1, double c2, double a,
                            double at, int sign, int n, Tile tile, int light, int overlay) {
        cap(vc, pose, axis, c1, c2, a / Math.cos(Math.PI / n), n, at, sign, 0.0, tile, light, overlay);
    }

    public static void horn(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double z0, double z1,
                            double w0, double h0, double w1, double h1, Tile wall, Tile mouth, Tile lip, int light,
                            int overlay) {
        final double[][] n0 = corners(cx, cy, w0, h0, z0);
        final double[][] n1 = corners(cx, cy, w1, h1, z1);
        final double len = Math.abs(z0 - z1);
        for (int i = 0; i < 4; i++) {
            final double[] a0 = n0[i];
            final double[] b0 = n0[(i + 1) % 4];
            final double[] a1 = n1[i];
            final double[] b1 = n1[(i + 1) % 4];
            final double[] ex = {b1[0] - a1[0], b1[1] - a1[1], b1[2] - a1[2]};
            final double[] ez = {a0[0] - a1[0], a0[1] - a1[1], a0[2] - a1[2]};
            double[] nn = normalize(cross(ez, ex));
            final double midX = (a1[0] + b1[0]) / 2 - cx;
            final double midY = (a1[1] + b1[1]) / 2 - cy;
            if (nn[0] * midX + nn[1] * midY < 0) {
                nn = new double[] {-nn[0], -nn[1], -nn[2]};
            }
            final double span = Math.max(Math.abs(ex[0]), Math.abs(ex[1]));
            final float u0 = wall.u(8 - span / 2);
            final float u1 = wall.u(8 + span / 2);
            final float v0 = wall.v(8 - len / 2);
            final float v1 = wall.v(8 + len / 2);
            quad(vc, pose, new float[][] {f(a1), f(b1), f(b0), f(a0)},
                    new float[][] {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, (float) nn[0], (float) nn[1],
                    (float) nn[2], light, overlay);
        }
        quad(vc, pose, new float[][] {f(n1[0]), f(n1[1]), f(n1[2]), f(n1[3])},
                new float[][] {{mouth.u(0), mouth.v(16)}, {mouth.u(16), mouth.v(16)}, {mouth.u(16), mouth.v(0)},
                        {mouth.u(0), mouth.v(0)}}, 0, 0, -1, light, overlay);
        final double t = 0.3;
        final double za = z1 - 0.01;
        final double zb = z1 + 0.35;
        box(vc, pose, cx - w1 / 2 - t, cy - h1 / 2 - t, za, cx + w1 / 2 + t, cy - h1 / 2, zb, lip, light, overlay);
        box(vc, pose, cx - w1 / 2 - t, cy + h1 / 2, za, cx + w1 / 2 + t, cy + h1 / 2 + t, zb, lip, light, overlay);
        box(vc, pose, cx - w1 / 2 - t, cy - h1 / 2, za, cx - w1 / 2, cy + h1 / 2, zb, lip, light, overlay);
        box(vc, pose, cx + w1 / 2, cy - h1 / 2, za, cx + w1 / 2 + t, cy + h1 / 2, zb, lip, light, overlay);
    }

    public static void strip(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double x1, double y1,
                             double z, double halfWidth, Tile tile, int light, int overlay) {
        final double dx = x1 - x0;
        final double dy = y1 - y0;
        final double m = Math.max(1.0e-6, Math.hypot(dx, dy));
        final double nx = -dy / m * halfWidth;
        final double ny = dx / m * halfWidth;
        final float u = tile.u(1);
        final float v = tile.v(1);
        quad(vc, pose, new float[][] {
                        {(float) (x0 - nx), (float) (y0 - ny), (float) z}, {(float) (x1 - nx), (float) (y1 - ny), (float) z},
                        {(float) (x1 + nx), (float) (y1 + ny), (float) z}, {(float) (x0 + nx), (float) (y0 + ny), (float) z}},
                new float[][] {{u, v}, {u, v}, {u, v}, {u, v}}, 0, 0, -1, light, overlay);
    }

    private static double[][] corners(double cx, double cy, double w, double h, double z) {
        return new double[][] {{cx + w / 2, cy - h / 2, z}, {cx - w / 2, cy - h / 2, z}, {cx - w / 2, cy + h / 2, z},
                {cx + w / 2, cy + h / 2, z}};
    }

    private static float[] f(double[] p) {
        return new float[] {(float) p[0], (float) p[1], (float) p[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        final double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0e-9 ? new double[] {0, 0, -1} : new double[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static double clamp(double v) {
        return Math.max(-8.0, Math.min(8.0, v));
    }

    private static float[] world(char axis, double c1, double c2, double p, double q, double w) {
        return switch (axis) {
            case 'z' -> new float[] {(float) (c1 + p), (float) (c2 + q), (float) w};
            default -> new float[] {(float) (c1 + p), (float) w, (float) (c2 + q)};
        };
    }

    private static float[] dir(char axis, double p, double q, double w) {
        return switch (axis) {
            case 'z' -> new float[] {(float) p, (float) q, (float) w};
            default -> new float[] {(float) p, (float) w, (float) q};
        };
    }
}
