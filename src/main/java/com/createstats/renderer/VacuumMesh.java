package com.createstats.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

// one tile per part, same brass texel for texel as the static model. (the lens is THIS CUBE, see the model)
public final class VacuumMesh {
    private VacuumMesh() {
    }

    public static final int SHEET = 128;
    public static final int TILE = 16;

    public enum Tile {
        IRON, IRON_MID, IRON_DARK, BRASS, BRASS_PLATE, STEEL, STEEL_DARK, ENAMEL,
        ENAMEL_FINS, YELLOW, YELLOW_DARK, HAZARD, RUBBER, FLUID, GREEN_FINS, GREEN_DARK,
        LAMP_GREEN, LAMP_AMBER, LAMP_RED, LAMP_OFF, XP, RING_ON, RING_OFF, MOUTH,
        FAN, GRILLE, SCALE, IVORY, PORTHOLE, CLAMP, NAMEPLATE, PAD,
        GLASS, SIGHT;

        public float u(double px) {
            return (float) (((ordinal() % 8) * TILE + px) / SHEET);
        }

        public float v(double px) {
            return (float) (((ordinal() / 8) * TILE + px) / SHEET);
        }
    }

    public static final int WHITE = 0xFFFFFFFF;

    public static void quad(VertexConsumer vc, PoseStack.Pose pose, float[][] p, float[][] uv, float nx, float ny,
                            float nz, int argb, int light, int overlay) {
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(argb)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    public static void box(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1,
                           double y1, double z1, Tile tile, int light, int overlay) {
        box(vc, pose, x0, y0, z0, x1, y1, z1, tile, WHITE, light, overlay);
    }

    public static void box(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1,
                           double y1, double z1, Tile tile, int argb, int light, int overlay) {
        final float ax = (float) x0;
        final float ay = (float) y0;
        final float az = (float) z0;
        final float bx = (float) x1;
        final float by = (float) y1;
        final float bz = (float) z1;
        final float w = Math.min(16f, bx - ax);
        final float h = Math.min(16f, by - ay);
        final float d = Math.min(16f, bz - az);

        quad(vc, pose, new float[][] {{bx, ay, az}, {ax, ay, az}, {ax, by, az}, {bx, by, az}},
                uvs(tile, w, h), 0, 0, -1, argb, light, overlay);

        quad(vc, pose, new float[][] {{ax, ay, bz}, {bx, ay, bz}, {bx, by, bz}, {ax, by, bz}},
                uvs(tile, w, h), 0, 0, 1, argb, light, overlay);

        quad(vc, pose, new float[][] {{ax, ay, az}, {ax, ay, bz}, {ax, by, bz}, {ax, by, az}},
                uvs(tile, d, h), -1, 0, 0, argb, light, overlay);

        quad(vc, pose, new float[][] {{bx, ay, bz}, {bx, ay, az}, {bx, by, az}, {bx, by, bz}},
                uvs(tile, d, h), 1, 0, 0, argb, light, overlay);

        quad(vc, pose, new float[][] {{ax, by, bz}, {bx, by, bz}, {bx, by, az}, {ax, by, az}},
                uvs(tile, w, d), 0, 1, 0, argb, light, overlay);

        quad(vc, pose, new float[][] {{ax, ay, az}, {bx, ay, az}, {bx, ay, bz}, {ax, ay, bz}},
                uvs(tile, w, d), 0, -1, 0, argb, light, overlay);
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
                             double innerLo, double innerHi, int argb, int light, int overlay) {
        final double R = a / Math.cos(Math.PI / n);
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
                        world(axis, c1, c2, R * Math.cos(pa), R * Math.sin(pa), lo),
                        world(axis, c1, c2, R * Math.cos(pb), R * Math.sin(pb), lo),
                        world(axis, c1, c2, R * Math.cos(pb), R * Math.sin(pb), hi),
                        world(axis, c1, c2, R * Math.cos(pa), R * Math.sin(pa), hi)};
                final int order = Math.floorMod(-k, n);
                final double u0 = (order % per) * wUv;
                final float[][] uv = {
                        {side.u(u0), side.v(vLen)}, {side.u(u0 + wUv), side.v(vLen)},
                        {side.u(u0 + wUv), side.v(0)}, {side.u(u0), side.v(0)}};
                final float[] nrm = dir(axis, Math.cos(theta), Math.sin(theta), 0);
                quad(vc, pose, p, uv, nrm[0], nrm[1], nrm[2], argb, light, overlay);
            }
        }
        if (capLo) {
            cap(vc, pose, axis, c1, c2, R, n, lo, -1, innerLo, cap == null ? side : cap, argb, light, overlay);
        }
        if (capHi) {
            cap(vc, pose, axis, c1, c2, R, n, hi, 1, innerHi, cap == null ? side : cap, argb, light, overlay);
        }
    }

    private static void cap(VertexConsumer vc, PoseStack.Pose pose, char axis, double c1, double c2, double R, int n,
                            double at, int sign, double inner, Tile tile, int argb, int light, int overlay) {
        final double Ri = inner > 0 ? inner / Math.cos(Math.PI / n) : 0.0;
        final float[] nrm = dir(axis, 0, 0, sign);
        for (int k = 0; k < n; k++) {
            final double t0 = (k + 0.5) * 2 * Math.PI / n;
            final double t1 = (k + 1.5) * 2 * Math.PI / n;
            double[][] loc = {
                    {Ri * Math.cos(t0), Ri * Math.sin(t0)}, {R * Math.cos(t0), R * Math.sin(t0)},
                    {R * Math.cos(t1), R * Math.sin(t1)}, {Ri * Math.cos(t1), Ri * Math.sin(t1)}};
            if (sign < 0) {
                loc = new double[][] {loc[3], loc[2], loc[1], loc[0]};
            }
            final float[][] p = new float[4][];
            final float[][] uv = new float[4][];
            for (int i = 0; i < 4; i++) {
                p[i] = world(axis, c1, c2, loc[i][0], loc[i][1], at);

                uv[i] = new float[] {tile.u(8 + clamp(loc[i][0])), tile.v(8 + clamp(loc[i][1]))};
            }
            quad(vc, pose, p, uv, nrm[0], nrm[1], nrm[2], argb, light, overlay);
        }
    }

    private static double clamp(double v) {
        return Math.max(-8.0, Math.min(8.0, v));
    }

    public static void disc(VertexConsumer vc, PoseStack.Pose pose, char axis, double c1, double c2, double a,
                            double at, int sign, int n, Tile tile, int argb, int light, int overlay) {
        cap(vc, pose, axis, c1, c2, a / Math.cos(Math.PI / n), n, at, sign, 0.0, tile, argb, light, overlay);
    }

    private static float[] world(char axis, double c1, double c2, double p, double q, double w) {
        return switch (axis) {
            case 'x' -> new float[] {(float) w, (float) (c2 + q), (float) (c1 + p)};
            case 'z' -> new float[] {(float) (c1 + p), (float) (c2 + q), (float) w};
            default -> new float[] {(float) (c1 + p), (float) w, (float) (c2 + q)};
        };
    }

    private static float[] dir(char axis, double p, double q, double w) {
        return switch (axis) {
            case 'x' -> new float[] {(float) w, (float) q, (float) p};
            case 'z' -> new float[] {(float) p, (float) q, (float) w};
            default -> new float[] {(float) p, (float) w, (float) q};
        };
    }

    public static void tube(VertexConsumer vc, PoseStack.Pose pose, double[][] pts, double radius, Tile tile,
                            int light, int overlay) {
        final int sides = 8;
        if (pts.length < 2) {
            return;
        }
        double[] prevRight = null;
        double arc = 0.0;
        float[][] ringA = null;
        float[][] nrmA = null;
        for (int i = 0; i < pts.length; i++) {
            final double[] t = tangent(pts, i);
            double[] right = prevRight == null ? perp(t) : project(prevRight, t);
            final double[] up = cross(right, t);
            prevRight = right;
            final float[][] ring = new float[sides][];
            final float[][] nrm = new float[sides][];
            for (int k = 0; k < sides; k++) {
                final double ang = 2 * Math.PI * k / sides;
                final double c = Math.cos(ang);
                final double s = Math.sin(ang);
                final double nx = right[0] * c + up[0] * s;
                final double ny = right[1] * c + up[1] * s;
                final double nz = right[2] * c + up[2] * s;
                nrm[k] = new float[] {(float) nx, (float) ny, (float) nz};
                ring[k] = new float[] {(float) (pts[i][0] + nx * radius), (float) (pts[i][1] + ny * radius),
                        (float) (pts[i][2] + nz * radius)};
            }
            if (ringA != null) {
                final double step = dist(pts[i - 1], pts[i]);
                final double v0 = arc % 16.0;
                final double v1 = v0 + Math.min(step, 16.0 - v0);
                for (int k = 0; k < sides; k++) {
                    final int k2 = (k + 1) % sides;
                    final float u0 = tile.u(k * 2.0);
                    final float u1 = tile.u(k * 2.0 + 2.0);
                    final float[] fn = {(nrmA[k][0] + nrm[k2][0]) * 0.5f, (nrmA[k][1] + nrm[k2][1]) * 0.5f,
                            (nrmA[k][2] + nrm[k2][2]) * 0.5f};
                    quad(vc, pose, new float[][] {ringA[k], ringA[k2], ring[k2], ring[k]},
                            new float[][] {{u0, tile.v(v0)}, {u1, tile.v(v0)}, {u1, tile.v(v1)}, {u0, tile.v(v1)}},
                            fn[0], fn[1], fn[2], WHITE, light, overlay);
                }
                arc += step;
            }
            ringA = ring;
            nrmA = nrm;
        }
    }

    private static double[] tangent(double[][] p, int i) {
        final double[] a = p[Math.max(0, i - 1)];
        final double[] b = p[Math.min(p.length - 1, i + 1)];
        return norm(new double[] {b[0] - a[0], b[1] - a[1], b[2] - a[2]});
    }

    private static double[] perp(double[] t) {
        final double[] seed = Math.abs(t[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        return norm(cross(seed, t));
    }

    private static double[] project(double[] r, double[] t) {
        final double d = r[0] * t[0] + r[1] * t[1] + r[2] * t[2];
        final double[] out = {r[0] - t[0] * d, r[1] - t[1] * d, r[2] - t[2] * d};
        final double l = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        return l < 1.0e-9 ? perp(t) : new double[] {out[0] / l, out[1] / l, out[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] norm(double[] v) {
        final double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0e-9 ? new double[] {0, 1, 0} : new double[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static double dist(double[] a, double[] b) {
        final double dx = b[0] - a[0];
        final double dy = b[1] - a[1];
        final double dz = b[2] - a[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
