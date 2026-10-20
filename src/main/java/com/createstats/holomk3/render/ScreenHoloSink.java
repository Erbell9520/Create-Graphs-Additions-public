package com.createstats.holomk3.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Vector3f;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

// the console preview runs the real painter, what you see is what floats
@OnlyIn(Dist.CLIENT)
public final class ScreenHoloSink implements HoloSink {
    private final PanelSurface out;
    private final Rect area;
    private final Vector3f eye;
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final float focal;
    private final float pixelSize;

    private final List<Solid> solids = new ArrayList<>();

    private record Solid(float[] xyz, int c1, int c2, int c3, float depth) {
    }

    public ScreenHoloSink(PanelSurface out, Rect area, Vector3f eye, Vector3f target,
                          float fovDegrees) {
        this.out = out;
        this.area = area;
        this.eye = new Vector3f(eye);
        this.forward.set(target).sub(eye).normalize();
        this.right.set(this.forward).cross(new Vector3f(0.0f, 1.0f, 0.0f)).normalize();
        this.up.set(this.right).cross(this.forward).normalize();
        this.focal = (float) (area.h * 0.5 / Math.tan(Math.toRadians(fovDegrees) * 0.5));
        this.pixelSize = 2.0f * (float) Math.tan(Math.toRadians(fovDegrees) * 0.5)
                * eye.distance(target) / Math.max(1.0f, area.h);
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
                this.area.centerX() + vx * this.focal / vz,
                this.area.centerY() - vy * this.focal / vz,
                vz};
    }

    @Override
    public void tri(float x1, float y1, float z1, int c1,
                    float x2, float y2, float z2, int c2,
                    float x3, float y3, float z3, int c3) {
        final float[] a = project(x1, y1, z1);
        final float[] b = project(x2, y2, z2);
        final float[] c = project(x3, y3, z3);
        if (a == null || b == null || c == null) {
            return;
        }
        this.out.tri(a[0], a[1], c1, b[0], b[1], c2, c[0], c[1], c3);
    }

    @Override
    public void solid(float x1, float y1, float z1, int c1,
                      float x2, float y2, float z2, int c2,
                      float x3, float y3, float z3, int c3) {
        final float depth = this.eye.distance((x1 + x2 + x3) / 3.0f, (y1 + y2 + y3) / 3.0f,
                (z1 + z2 + z3) / 3.0f);
        this.solids.add(new Solid(new float[]{x1, y1, z1, x2, y2, z2, x3, y3, z3}, c1, c2, c3, depth));
    }

    public void flushSolids() {
        this.solids.sort(Comparator.comparingDouble(Solid::depth).reversed());
        for (final Solid s : this.solids) {
            final float[] p = s.xyz();
            final float[] a = project(p[0], p[1], p[2]);
            final float[] b = project(p[3], p[4], p[5]);
            final float[] c = project(p[6], p[7], p[8]);
            if (a == null || b == null || c == null) {
                continue;
            }
            this.out.tri(a[0], a[1], s.c1(), b[0], b[1], s.c2(), c[0], c[1], s.c3());
        }
        this.solids.clear();
    }

    @Override
    public void text(String s, float x, float y, float z, int color, float scale) {
        final float[] p = project(x, y, z);
        if (p == null) {
            return;
        }

        final float onScreen = scale * 9.0f * this.focal / p[2];
        if (onScreen < 3.0f) {
            return;
        }
        this.out.text(s, p[0], p[1] - onScreen * 0.5f, color, onScreen / 9.0f);
    }

    @Override
    public float textWidth(String s, float scale) {
        return this.out.textWidth(s, 1.0f) * scale;
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
        return 1.0f;
    }

    @Override
    public float pixel() {
        return this.pixelSize;
    }
}
