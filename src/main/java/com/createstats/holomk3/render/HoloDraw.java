package com.createstats.holomk3.render;

import org.joml.Vector3f;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HoloDraw {
    private static final float CORE = 0.34f;

    private static final float OVERSHOOT = 0.55f;

    private HoloDraw() {
    }

    public static int fade(int argb, float mul) {
        final int a = Math.round(((argb >>> 24) & 0xFF) * (mul < 0.0f ? 0.0f : mul));
        return (argb & 0x00FFFFFF) | (Math.min(a, 255) << 24);
    }

    public static int alpha(int argb, float a) {
        final int aa = Math.max(0, Math.min(255, Math.round(a * 255.0f)));
        return (argb & 0x00FFFFFF) | (aa << 24);
    }

    public static int mix(int a, int b, float t) {
        final float k = t < 0.0f ? 0.0f : Math.min(t, 1.0f);
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            final int ca = (a >>> shift) & 0xFF;
            final int cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }

    public static void beam(HoloSink s, float ax, float ay, float az,
                            float bx, float by, float bz, float halfWidth, int color) {
        beam(s, ax, ay, az, bx, by, bz, halfWidth, color, color);
    }

    public static void beam(HoloSink s, float ax, float ay, float az,
                            float bx, float by, float bz, float halfWidth, int ca, int cb) {
        final float hw = Math.max(halfWidth, s.pixel() * 0.5f);
        final Vector3f dir = new Vector3f(bx - ax, by - ay, bz - az);
        final float len = dir.length();
        if (len < 1.0e-6f) {
            point(s, ax, ay, az, hw, ca);
            return;
        }
        dir.div(len);
        final Vector3f sideVec = side(s, dir, (ax + bx) * 0.5f, (ay + by) * 0.5f, (az + bz) * 0.5f);

        final float ex = dir.x * hw * OVERSHOOT;
        final float ey = dir.y * hw * OVERSHOOT;
        final float ez = dir.z * hw * OVERSHOOT;
        final float x0 = ax - ex;
        final float y0 = ay - ey;
        final float z0 = az - ez;
        final float x1 = bx + ex;
        final float y1 = by + ey;
        final float z1 = bz + ez;

        final float sx = sideVec.x;
        final float sy = sideVec.y;
        final float sz = sideVec.z;
        final float core = hw * CORE;
        final float f = s.feather();

        s.quad(x0 - sx * core, y0 - sy * core, z0 - sz * core, ca,
                x1 - sx * core, y1 - sy * core, z1 - sz * core, cb,
                x1 + sx * core, y1 + sy * core, z1 + sz * core, cb,
                x0 + sx * core, y0 + sy * core, z0 + sz * core, ca);
        if (f <= 0.02f) {
            return;
        }

        final float outer = hw * (CORE + (1.0f - CORE) * f);
        final int za = alpha(ca, 0.0f);
        final int zb = alpha(cb, 0.0f);
        s.quad(x0 + sx * core, y0 + sy * core, z0 + sz * core, ca,
                x1 + sx * core, y1 + sy * core, z1 + sz * core, cb,
                x1 + sx * outer, y1 + sy * outer, z1 + sz * outer, zb,
                x0 + sx * outer, y0 + sy * outer, z0 + sz * outer, za);
        s.quad(x0 - sx * outer, y0 - sy * outer, z0 - sz * outer, za,
                x1 - sx * outer, y1 - sy * outer, z1 - sz * outer, zb,
                x1 - sx * core, y1 - sy * core, z1 - sz * core, cb,
                x0 - sx * core, y0 - sy * core, z0 - sz * core, ca);
    }

    private static Vector3f side(HoloSink s, Vector3f dir, float mx, float my, float mz) {
        final Vector3f eye = s.eye();
        final Vector3f toEye = new Vector3f(eye.x - mx, eye.y - my, eye.z - mz);
        final Vector3f out = new Vector3f();
        dir.cross(toEye, out);
        if (out.lengthSquared() < 1.0e-10f) {
            return new Vector3f(s.right());
        }
        return out.normalize();
    }

    public static void polyline(HoloSink s, float[] xyz, int count, float halfWidth,
                                int color, float tail, float head) {
        if (count < 2) {
            if (count == 1) {
                point(s, xyz[0], xyz[1], xyz[2], halfWidth * 1.6f, fade(color, head));
            }
            return;
        }
        for (int i = 0; i < count - 1; i++) {
            final float t0 = i / (float) (count - 1);
            final float t1 = (i + 1) / (float) (count - 1);
            beam(s, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2],
                    xyz[(i + 1) * 3], xyz[(i + 1) * 3 + 1], xyz[(i + 1) * 3 + 2],
                    halfWidth, fade(color, tail + (head - tail) * t0),
                    fade(color, tail + (head - tail) * t1));
        }
    }

    public static void point(HoloSink s, float x, float y, float z, float r, int color) {
        final float rr = Math.max(r, s.pixel() * 0.6f);
        final Vector3f right = s.right();
        final Vector3f up = s.up();
        final float f = s.feather();
        if (f <= 0.02f) {
            billboard(s, x, y, z, rr, right, up, color, color);
            return;
        }

        final int seg = 6;
        final int rim = alpha(color, 0.0f);
        final float outer = rr * (0.45f + 0.55f * f);
        float px = x + right.x * outer;
        float py = y + right.y * outer;
        float pz = z + right.z * outer;
        for (int i = 1; i <= seg; i++) {
            final double a = i * 2.0 * Math.PI / seg;
            final float cx = (float) Math.cos(a);
            final float cy = (float) Math.sin(a);
            final float nx = x + (right.x * cx + up.x * cy) * outer;
            final float ny = y + (right.y * cx + up.y * cy) * outer;
            final float nz = z + (right.z * cx + up.z * cy) * outer;
            s.tri(x, y, z, color, px, py, pz, rim, nx, ny, nz, rim);
            px = nx;
            py = ny;
            pz = nz;
        }
        billboard(s, x, y, z, rr * 0.34f, right, up, color, color);
    }

    public static void star(HoloSink s, float x, float y, float z, float r, int color) {
        point(s, x, y, z, r, color);
        if (s.feather() <= 0.15f) {
            return;
        }
        final Vector3f right = s.right();
        final Vector3f up = s.up();
        final int tipColor = alpha(color, 0.0f);
        final float reach = r * 3.6f;
        final float waist = r * 0.34f;
        spike(s, x, y, z, right, up, reach, waist, color, tipColor);
        spike(s, x, y, z, up, right, reach, waist, color, tipColor);
    }

    private static void spike(HoloSink s, float x, float y, float z, Vector3f along, Vector3f across,
                              float reach, float waist, int core, int tip) {
        for (int sign = -1; sign <= 1; sign += 2) {
            final float tx = x + along.x * reach * sign;
            final float ty = y + along.y * reach * sign;
            final float tz = z + along.z * reach * sign;
            s.tri(x + across.x * waist, y + across.y * waist, z + across.z * waist, core,
                    x - across.x * waist, y - across.y * waist, z - across.z * waist, core,
                    tx, ty, tz, tip);
        }
    }

    private static void billboard(HoloSink s, float x, float y, float z, float r,
                                  Vector3f right, Vector3f up, int inner, int outer) {
        final float rx = right.x * r;
        final float ry = right.y * r;
        final float rz = right.z * r;
        final float ux = up.x * r;
        final float uy = up.y * r;
        final float uz = up.z * r;
        s.quad(x - rx - ux, y - ry - uy, z - rz - uz, outer,
                x + rx - ux, y + ry - uy, z + rz - uz, outer,
                x + rx + ux, y + ry + uy, z + rz + uz, inner,
                x - rx + ux, y - ry + uy, z - rz + uz, inner);
    }

    public static void cage(HoloSink s, float w, float h, float d, float hw, int color) {
        final float x0 = -w * 0.5f;
        final float x1 = w * 0.5f;
        final float z0 = -d * 0.5f;
        final float z1 = d * 0.5f;
        final int floor = color;
        final int roof = fade(color, 0.45f);
        final int post = fade(color, 0.62f);

        rect(s, x0, 0.0f, z0, x1, 0.0f, z1, hw, floor);
        rect(s, x0, h, z0, x1, h, z1, hw * 0.8f, roof);
        beam(s, x0, 0.0f, z0, x0, h, z0, hw * 0.8f, floor, roof);
        beam(s, x1, 0.0f, z0, x1, h, z0, hw * 0.8f, floor, roof);
        beam(s, x0, 0.0f, z1, x0, h, z1, hw * 0.8f, post, roof);
        beam(s, x1, 0.0f, z1, x1, h, z1, hw * 0.8f, post, roof);
    }

    public static void rect(HoloSink s, float x0, float y, float z0, float x1, float z1,
                            float hw, int color) {
        rect(s, x0, y, z0, x1, y, z1, hw, color);
    }

    private static void rect(HoloSink s, float x0, float y, float z0, float x1, float y1, float z1,
                             float hw, int color) {
        beam(s, x0, y, z0, x1, y1, z0, hw, color);
        beam(s, x1, y, z0, x1, y1, z1, hw, color);
        beam(s, x1, y, z1, x0, y1, z1, hw, color);
        beam(s, x0, y, z1, x0, y1, z0, hw, color);
    }

    public static void tick(HoloSink s, float x, float y, float z, Vector3f dir, float len,
                            float hw, int color) {
        beam(s, x, y, z, x + dir.x * len, y + dir.y * len, z + dir.z * len, hw, color,
                alpha(color, 0.0f));
    }

    public static void planeX(HoloSink s, float z, float w, float h, int color, boolean feathered) {
        final float x0 = -w * 0.5f;
        final float x1 = w * 0.5f;
        final int edge = feathered ? alpha(color, 0.0f) : color;
        s.quad(x0, 0.0f, z, edge, x1, 0.0f, z, edge, x1, h, z, color, x0, h, z, color);
    }

    public static void planeY(HoloSink s, float y, float w, float d, int centre, int edge) {
        final float x0 = -w * 0.5f;
        final float x1 = w * 0.5f;
        final float z0 = -d * 0.5f;
        final float z1 = d * 0.5f;
        s.quad(x0, y, z0, edge, x1, y, z0, edge, x1, y, z1, centre, x0, y, z1, centre);
    }

    public static void cone(HoloSink s, float y0, float r0, float y1, float r1, int seg,
                            int c0, int c1) {
        float px0 = r0;
        float pz0 = 0.0f;
        float px1 = r1;
        float pz1 = 0.0f;
        for (int i = 1; i <= seg; i++) {
            final double a = i * 2.0 * Math.PI / seg;
            final float cx = (float) Math.cos(a);
            final float cz = (float) Math.sin(a);
            final float nx0 = cx * r0;
            final float nz0 = cz * r0;
            final float nx1 = cx * r1;
            final float nz1 = cz * r1;
            s.quad(px0, y0, pz0, c0, nx0, y0, nz0, c0, nx1, y1, nz1, c1, px1, y1, pz1, c1);
            px0 = nx0;
            pz0 = nz0;
            px1 = nx1;
            pz1 = nz1;
        }
    }

    public static void disc(HoloSink s, float y, float r, int seg, int centre, int rim) {
        float px = r;
        float pz = 0.0f;
        for (int i = 1; i <= seg; i++) {
            final double a = i * 2.0 * Math.PI / seg;
            final float nx = (float) Math.cos(a) * r;
            final float nz = (float) Math.sin(a) * r;
            s.tri(0.0f, y, 0.0f, centre, px, y, pz, rim, nx, y, nz, rim);
            px = nx;
            pz = nz;
        }
    }

    public static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x00FFFFFF) / (float) 0x01000000;
    }
}
