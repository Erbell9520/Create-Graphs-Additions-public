package com.createstats.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

final class CoilMesh {
    static final int WHITE = 0xFFFFFFFF;

    private final VertexConsumer vc;
    private final int overlay;

    CoilMesh(VertexConsumer vc, int overlay) {
        this.vc = vc;
        this.overlay = overlay;
    }

    static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    static int grey(double k) {
        final int v = (int) Math.round(255 * Math.max(0.0, Math.min(1.0, k)));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    void quad(PoseStack.Pose pose, float[][] p, TextureAtlasSprite s, float u0, float v0, float u1, float v1,
              float nx, float ny, float nz, int light, int colour, boolean glow) {
        final float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        final int l = glow ? LightTexture.FULL_BRIGHT : light;
        final float ax = glow ? 0f : nx;
        final float ay = glow ? 1f : ny;
        final float az = glow ? 0f : nz;
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(colour)
                    .setUv(s.getU(uv[i][0] / 16f), s.getV(uv[i][1] / 16f))
                    .setOverlay(overlay)
                    .setLight(l)
                    .setNormal(pose, ax, ay, az);
        }
    }

    void quadFacing(PoseStack.Pose pose, float[][] p, TextureAtlasSprite s, float[][] uv, float fx, float fy, float fz,
                    int light, int colour, boolean glow) {
        final float ax = p[1][0] - p[0][0];
        final float ay = p[1][1] - p[0][1];
        final float az = p[1][2] - p[0][2];
        final float bx = p[2][0] - p[0][0];
        final float by = p[2][1] - p[0][1];
        final float bz = p[2][2] - p[0][2];
        final float wx = ay * bz - az * by;
        final float wy = az * bx - ax * bz;
        final float wz = ax * by - ay * bx;
        final boolean flip = wx * fx + wy * fy + wz * fz < 0;
        final int l = glow ? LightTexture.FULL_BRIGHT : light;
        for (int n = 0; n < 4; n++) {
            final int i = flip ? 3 - n : n;
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(colour)
                    .setUv(s.getU(uv[i][0] / 16f), s.getV(uv[i][1] / 16f))
                    .setOverlay(overlay)
                    .setLight(l)
                    .setNormal(pose, glow ? 0f : fx, glow ? 1f : fy, glow ? 0f : fz);
        }
    }

    void prismInside(PoseStack.Pose pose, double cx, double cz, double y0, double y1, double r, int n,
                     TextureAtlasSprite side, TextureAtlasSprite floor, int light, int colour, boolean glow) {
        final float fy0 = (float) y0;
        final float fy1 = (float) y1;
        final float vh = (float) Math.min(16.0, y1 - y0);
        for (int k = 0; k < n; k++) {
            final double a0 = 2 * Math.PI * k / n;
            final double a1 = 2 * Math.PI * (k + 1) / n;
            final float x0 = (float) (cx + r * Math.cos(a0));
            final float z0 = (float) (cz + r * Math.sin(a0));
            final float x1 = (float) (cx + r * Math.cos(a1));
            final float z1 = (float) (cz + r * Math.sin(a1));
            final double am = (a0 + a1) / 2;
            final float u0 = (16f * k / n) % 16f;
            final float u1 = u0 + 16f / n;
            quadFacing(pose, new float[][] {{x0, fy0, z0}, {x1, fy0, z1}, {x1, fy1, z1}, {x0, fy1, z0}}, side,
                    new float[][] {{u0, 16}, {u1, 16}, {u1, 16 - vh}, {u0, 16 - vh}},
                    (float) -Math.cos(am), 0, (float) -Math.sin(am), light, colour, glow);
            if (floor != null) {
                final float fcx = (float) cx;
                final float fcz = (float) cz;
                final float half = (float) Math.min(8.0, r);
                quadFacing(pose, new float[][] {{fcx, fy0, fcz}, {x0, fy0, z0}, {x1, fy0, z1}, {x1, fy0, z1}}, floor,
                        new float[][] {{8, 8}, {8 + half, 8 - half}, {8 + half, 8 + half}, {8 + half, 8 + half}},
                        0, 1, 0, light, colour, glow);
            }
        }
    }

    void sphereInside(PoseStack.Pose pose, double cx, double cy, double cz, double r, int rows, int n,
                      TextureAtlasSprite s, int light, int colour, boolean glow) {
        for (int i = 0; i < rows; i++) {
            final double t0 = -Math.PI / 2 + Math.PI * i / rows;
            final double t1 = -Math.PI / 2 + Math.PI * (i + 1) / rows;
            final float y0 = (float) (cy + r * Math.sin(t0));
            final float y1 = (float) (cy + r * Math.sin(t1));
            final double r0 = r * Math.cos(t0);
            final double r1 = r * Math.cos(t1);
            for (int k = 0; k < n; k++) {
                final double a0 = 2 * Math.PI * k / n;
                final double a1 = 2 * Math.PI * (k + 1) / n;
                final float[][] p = {
                        {(float) (cx + r0 * Math.cos(a0)), y0, (float) (cz + r0 * Math.sin(a0))},
                        {(float) (cx + r0 * Math.cos(a1)), y0, (float) (cz + r0 * Math.sin(a1))},
                        {(float) (cx + r1 * Math.cos(a1)), y1, (float) (cz + r1 * Math.sin(a1))},
                        {(float) (cx + r1 * Math.cos(a0)), y1, (float) (cz + r1 * Math.sin(a0))}};
                final double tm = (t0 + t1) / 2;
                final double am = (a0 + a1) / 2;
                final float u0 = 16f * k / n;
                final float v0 = 16f * i / rows;
                final float u1 = u0 + 16f / n;
                final float v1 = v0 + 16f / rows;
                quadFacing(pose, p, s, new float[][] {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}},
                        (float) (-Math.cos(tm) * Math.cos(am)), (float) -Math.sin(tm),
                        (float) (-Math.cos(tm) * Math.sin(am)), light, colour, glow);
            }
        }
    }

    void beam(PoseStack.Pose pose, double[] a, double[] b, double half, TextureAtlasSprite s, int light, int colour,
              boolean glow) {
        final double dx = b[0] - a[0];
        final double dy = b[1] - a[1];
        final double dz = b[2] - a[2];
        final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return;
        }
        final double ux = dx / len;
        final double uy = dy / len;
        final double uz = dz / len;

        double px = Math.abs(uy) < 0.9 ? -uz : 0;
        double py = Math.abs(uy) < 0.9 ? 0 : uz;
        double pz = Math.abs(uy) < 0.9 ? ux : -uy;
        final double pl = Math.sqrt(px * px + py * py + pz * pz);
        px /= pl;
        py /= pl;
        pz /= pl;
        final double qx = uy * pz - uz * py;
        final double qy = uz * px - ux * pz;
        final double qz = ux * py - uy * px;
        final double[][] side = {{px, py, pz}, {qx, qy, qz}, {-px, -py, -pz}, {-qx, -qy, -qz}};
        final float vlen = (float) Math.min(16.0, len);
        for (int k = 0; k < 4; k++) {
            final double[] m = side[k];
            final double[] n2 = side[(k + 1) % 4];
            final double ox0 = (m[0] - n2[0]) * half;
            final double oy0 = (m[1] - n2[1]) * half;
            final double oz0 = (m[2] - n2[2]) * half;
            final double ox1 = (m[0] + n2[0]) * half;
            final double oy1 = (m[1] + n2[1]) * half;
            final double oz1 = (m[2] + n2[2]) * half;
            final float[][] p = {
                    {(float) (a[0] + ox0), (float) (a[1] + oy0), (float) (a[2] + oz0)},
                    {(float) (a[0] + ox1), (float) (a[1] + oy1), (float) (a[2] + oz1)},
                    {(float) (b[0] + ox1), (float) (b[1] + oy1), (float) (b[2] + oz1)},
                    {(float) (b[0] + ox0), (float) (b[1] + oy0), (float) (b[2] + oz0)}};
            quad(pose, p, s, 0, 0, 2, vlen, (float) m[0], (float) m[1], (float) m[2], light, colour, glow);
        }
    }

    void disc(PoseStack.Pose pose, double cx, double y, double cz, double r, int n, TextureAtlasSprite s, int light,
              int colour, boolean glow, boolean up) {
        final float fy = (float) y;
        final double k = 8.0 / r;
        for (int i = 0; i < n; i++) {
            final double a0 = 2 * Math.PI * i / n;
            final double a1 = 2 * Math.PI * (i + 1) / n;
            final double x0 = r * Math.cos(a0);
            final double z0 = r * Math.sin(a0);
            final double x1 = r * Math.cos(a1);
            final double z1 = r * Math.sin(a1);
            final float[][] p = {{(float) cx, fy, (float) cz}, {(float) (cx + x0), fy, (float) (cz + z0)},
                    {(float) (cx + x1), fy, (float) (cz + z1)}, {(float) (cx + x1), fy, (float) (cz + z1)}};
            final float[][] uv = {{8, 8}, {(float) (8 + x0 * k), (float) (8 + z0 * k)},
                    {(float) (8 + x1 * k), (float) (8 + z1 * k)}, {(float) (8 + x1 * k), (float) (8 + z1 * k)}};
            quadFacing(pose, p, s, uv, 0, up ? 1 : -1, 0, light, colour, glow);
        }
    }

    void box(PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1,
             TextureAtlasSprite s, int light, int colour, boolean glow, int faces) {
        box(pose, x0, y0, z0, x1, y1, z1, s, light, colour, glow, faces, 0f);
    }

    void box(PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1,
             TextureAtlasSprite s, int light, int colour, boolean glow, int faces, float inset) {
        final float ax = (float) x0;
        final float ay = (float) y0;
        final float az = (float) z0;
        final float bx = (float) x1;
        final float by = (float) y1;
        final float bz = (float) z1;
        final float i = inset;
        final float w = Math.min(16f - i, i + bx - ax);
        final float h = Math.min(16f - i, i + by - ay);
        final float d = Math.min(16f - i, i + bz - az);
        if ((faces & 1) != 0) {
            quad(pose, new float[][] {{ax, ay, az}, {bx, ay, az}, {bx, ay, bz}, {ax, ay, bz}}, s, i, i, w, d,
                    0, -1, 0, light, colour, glow);
        }
        if ((faces & 2) != 0) {
            quad(pose, new float[][] {{ax, by, bz}, {bx, by, bz}, {bx, by, az}, {ax, by, az}}, s, i, i, w, d,
                    0, 1, 0, light, colour, glow);
        }
        if ((faces & 4) != 0) {
            quad(pose, new float[][] {{bx, ay, az}, {ax, ay, az}, {ax, by, az}, {bx, by, az}}, s, i, i, w, h,
                    0, 0, -1, light, colour, glow);
        }
        if ((faces & 8) != 0) {
            quad(pose, new float[][] {{ax, ay, bz}, {bx, ay, bz}, {bx, by, bz}, {ax, by, bz}}, s, i, i, w, h,
                    0, 0, 1, light, colour, glow);
        }
        if ((faces & 16) != 0) {
            quad(pose, new float[][] {{ax, ay, az}, {ax, ay, bz}, {ax, by, bz}, {ax, by, az}}, s, i, i, d, h,
                    -1, 0, 0, light, colour, glow);
        }
        if ((faces & 32) != 0) {
            quad(pose, new float[][] {{bx, ay, bz}, {bx, ay, az}, {bx, by, az}, {bx, by, bz}}, s, i, i, d, h,
                    1, 0, 0, light, colour, glow);
        }
    }

    void box(PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1,
             TextureAtlasSprite s, int light) {
        box(pose, x0, y0, z0, x1, y1, z1, s, light, WHITE, false, 0x3F);
    }

    void prism(PoseStack.Pose pose, double cx, double cz, double y0, double y1, double r, int n,
               TextureAtlasSprite side, TextureAtlasSprite cap, int light, int colour, boolean glow) {
        final float fy0 = (float) y0;
        final float fy1 = (float) y1;
        final float vh = (float) Math.min(16.0, y1 - y0);
        for (int k = 0; k < n; k++) {
            final double a0 = 2 * Math.PI * k / n;
            final double a1 = 2 * Math.PI * (k + 1) / n;
            final float x0 = (float) (cx + r * Math.cos(a0));
            final float z0 = (float) (cz + r * Math.sin(a0));
            final float x1 = (float) (cx + r * Math.cos(a1));
            final float z1 = (float) (cz + r * Math.sin(a1));
            final double am = (a0 + a1) / 2;
            final float u0 = (16f * k / n) % 16f;
            quad(pose, new float[][] {{x0, fy0, z0}, {x1, fy0, z1}, {x1, fy1, z1}, {x0, fy1, z0}}, side,
                    u0, 16 - vh, u0 + 16f / n, 16, (float) Math.cos(am), 0, (float) Math.sin(am), light, colour, glow);
            if (cap != null) {
                final float fcx = (float) cx;
                final float fcz = (float) cz;
                final float half = (float) Math.min(8.0, r);
                quad(pose, new float[][] {{fcx, fy1, fcz}, {x1, fy1, z1}, {x0, fy1, z0}, {x0, fy1, z0}}, cap,
                        8 - half, 8 - half, 8 + half, 8 + half, 0, 1, 0, light, colour, glow);
                quad(pose, new float[][] {{fcx, fy0, fcz}, {x0, fy0, z0}, {x1, fy0, z1}, {x1, fy0, z1}}, cap,
                        8 - half, 8 - half, 8 + half, 8 + half, 0, -1, 0, light, colour, glow);
            }
        }
    }

    void torus(PoseStack.Pose pose, double cx, double cy, double cz, double big, double tube, int around, int across,
               TextureAtlasSprite s, int light, int colour, boolean glow) {
        torus(pose, cx, cy, cz, big, tube, around, across, s, light, colour, glow, 1);
    }

    void torus(PoseStack.Pose pose, double cx, double cy, double cz, double big, double tube, int around, int across,
               TextureAtlasSprite s, int light, int colour, boolean glow, int repeat) {
        final int per = Math.max(1, around / Math.max(1, repeat));
        for (int i = 0; i < around; i++) {
            final double a0 = 2 * Math.PI * i / around;
            final double a1 = 2 * Math.PI * (i + 1) / around;
            for (int j = 0; j < across; j++) {
                final double b0 = 2 * Math.PI * j / across;
                final double b1 = 2 * Math.PI * (j + 1) / across;
                final float[][] p = {torusPoint(cx, cy, cz, big, tube, a0, b0), torusPoint(cx, cy, cz, big, tube, a1, b0),
                        torusPoint(cx, cy, cz, big, tube, a1, b1), torusPoint(cx, cy, cz, big, tube, a0, b1)};
                final double am = (a0 + a1) / 2;
                final double bm = (b0 + b1) / 2;
                final float u0 = 16f * (i % per) / per;
                final float v0 = 16f * j / across;
                quad(pose, p, s, u0, v0, u0 + 16f / per, v0 + 16f / across,
                        (float) (Math.cos(bm) * Math.cos(am)), (float) Math.sin(bm), (float) (Math.cos(bm) * Math.sin(am)),
                        light, colour, glow);
            }
        }
    }

    private static float[] torusPoint(double cx, double cy, double cz, double big, double tube, double a, double b) {
        final double ring = big + tube * Math.cos(b);
        return new float[] {(float) (cx + ring * Math.cos(a)), (float) (cy + tube * Math.sin(b)),
                (float) (cz + ring * Math.sin(a))};
    }

    void sphere(PoseStack.Pose pose, double cx, double cy, double cz, double r, int rows, int n,
                TextureAtlasSprite s, int light, int colour, boolean glow) {
        for (int i = 0; i < rows; i++) {
            final double t0 = -Math.PI / 2 + Math.PI * i / rows;
            final double t1 = -Math.PI / 2 + Math.PI * (i + 1) / rows;
            final float y0 = (float) (cy + r * Math.sin(t0));
            final float y1 = (float) (cy + r * Math.sin(t1));
            final double r0 = r * Math.cos(t0);
            final double r1 = r * Math.cos(t1);
            for (int k = 0; k < n; k++) {
                final double a0 = 2 * Math.PI * k / n;
                final double a1 = 2 * Math.PI * (k + 1) / n;
                final float[][] p = {
                        {(float) (cx + r0 * Math.cos(a0)), y0, (float) (cz + r0 * Math.sin(a0))},
                        {(float) (cx + r0 * Math.cos(a1)), y0, (float) (cz + r0 * Math.sin(a1))},
                        {(float) (cx + r1 * Math.cos(a1)), y1, (float) (cz + r1 * Math.sin(a1))},
                        {(float) (cx + r1 * Math.cos(a0)), y1, (float) (cz + r1 * Math.sin(a0))}};
                final double tm = (t0 + t1) / 2;
                final double am = (a0 + a1) / 2;
                final float u0 = 16f * k / n;
                final float v0 = 16f * i / rows;
                quad(pose, p, s, u0, v0, u0 + 16f / n, v0 + 16f / rows, (float) (Math.cos(tm) * Math.cos(am)),
                        (float) Math.sin(tm), (float) (Math.cos(tm) * Math.sin(am)), light, colour, glow);
            }
        }
    }

    void ring(PoseStack ps, double radius, double width, double thick, int segs, TextureAtlasSprite brass,
              TextureAtlasSprite inlay, int inlayColour, TextureAtlasSprite lug, int lugs, int light) {
        final double len = 2 * Math.PI * radius / segs + 0.35;
        for (int k = 0; k < segs; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(360f * k / segs));
            box(ps.last(), radius - thick / 2, -width / 2, -len / 2, radius + thick / 2, width / 2, len / 2, brass,
                    light);
            if (inlay != null) {
                box(ps.last(), radius - thick / 2 - 0.25, -width / 4, -len / 2, radius - thick / 2, width / 4, len / 2,
                        inlay, light, inlayColour, true, 1 | 2 | 16);
            }
            ps.popPose();
        }
        for (int k = 0; k < lugs; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(360f * k / lugs + 45f));
            box(ps.last(), radius + thick / 2, -width / 2 - 0.4, -1.0, radius + thick / 2 + 1.0, width / 2 + 0.4, 1.0,
                    lug, light);
            ps.popPose();
        }
    }
}
