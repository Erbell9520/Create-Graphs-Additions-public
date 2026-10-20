package com.createstats.holomk3.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WorldHoloSink implements HoloSink {
    private final MultiBufferSource buffers;
    private final Font font;
    private final int light;
    private final RenderType additiveType;

    private Matrix4f base = new Matrix4f();
    private final Vector3f eye = new Vector3f();
    private final Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
    private final Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
    private Matrix4f textBasis = new Matrix4f();

    private VertexConsumer additive;
    private VertexConsumer skin;

    private float cue;
    private float near;
    private float far = 1.0f;
    private float pixel = 0.01f;
    private float feather = 1.0f;

    private final List<Glyph> deferred = new ArrayList<>();

    public WorldHoloSink(MultiBufferSource buffers, Font font, int light, boolean shaderPath) {
        this.buffers = buffers;
        this.font = font;
        this.light = light;
        this.additiveType = HoloRenderTypes.additive(shaderPath);
    }

    public void frame(PoseStack pose, Vector3f camRight, Vector3f camUp) {
        this.base = new Matrix4f(pose.last().pose());
        final Matrix4f inverse = new Matrix4f(this.base).invert();

        this.eye.set(0.0f, 0.0f, 0.0f);
        inverse.transformPosition(this.eye);
        inverse.transformDirection(this.right.set(camRight)).normalize();
        inverse.transformDirection(this.up.set(camUp)).normalize();

        final Vector3f down = new Vector3f(this.up).negate();
        final Vector3f forward = new Vector3f(this.right).cross(down);
        this.textBasis = new Matrix4f(
                this.right.x, this.right.y, this.right.z, 0.0f,
                down.x, down.y, down.z, 0.0f,
                forward.x, forward.y, forward.z, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f);

        this.additive = null;
        this.skin = null;
    }

    public void volume(float strength, float w, float h, float d, float fovDegrees, int screenHeight) {
        this.cue = strength < 0.0f ? 0.0f : Math.min(strength, 1.0f);
        float lo = Float.MAX_VALUE;
        float hi = 0.0f;
        for (int i = 0; i < 8; i++) {
            final float x = ((i & 1) == 0 ? -0.5f : 0.5f) * w;
            final float y = (i & 2) == 0 ? 0.0f : h;
            final float z = ((i & 4) == 0 ? -0.5f : 0.5f) * d;
            final float dist = this.eye.distance(x, y, z);
            lo = Math.min(lo, dist);
            hi = Math.max(hi, dist);
        }
        this.near = lo;
        this.far = Math.max(hi, lo + 1.0e-3f);

        final float centre = (lo + hi) * 0.5f;
        final float tan = (float) Math.tan(Math.toRadians(Math.max(1.0, fovDegrees)) * 0.5);
        this.pixel = 2.0f * tan * centre / Math.max(1, screenHeight);

        this.feather = clamp01(1.0f - (centre - 6.0f) / 26.0f);
    }

    @Override
    public void tri(float x1, float y1, float z1, int c1,
                    float x2, float y2, float z2, int c2,
                    float x3, float y3, float z3, int c3) {
        final VertexConsumer vc = additive();

        vertex(vc, x1, y1, z1, c1);
        vertex(vc, x2, y2, z2, c2);
        vertex(vc, x3, y3, z3, c3);
        vertex(vc, x3, y3, z3, c3);
    }

    @Override
    public void solid(float x1, float y1, float z1, int c1,
                      float x2, float y2, float z2, int c2,
                      float x3, float y3, float z3, int c3) {
        final VertexConsumer vc = skin();
        vertex(vc, x1, y1, z1, c1);
        vertex(vc, x2, y2, z2, c2);
        vertex(vc, x3, y3, z3, c3);
        vertex(vc, x3, y3, z3, c3);
    }

    private VertexConsumer additive() {
        if (this.additive == null) {
            this.additive = this.buffers.getBuffer(this.additiveType);
        }
        return this.additive;
    }

    private VertexConsumer skin() {
        if (this.skin == null) {
            this.skin = this.buffers.getBuffer(HoloRenderTypes.SKIN);
        }
        return this.skin;
    }

    private void vertex(VertexConsumer vc, float x, float y, float z, int argb) {
        final int a = Math.round(((argb >>> 24) & 0xFF) * attenuation(x, y, z));
        vc.addVertex(this.base, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, Math.min(a, 255));
    }

    private float attenuation(float x, float y, float z) {
        if (this.cue <= 0.001f) {
            return 1.0f;
        }
        final float t = clamp01((this.eye.distance(x, y, z) - this.near) / (this.far - this.near));
        return 1.0f - this.cue * t;
    }

    private record Glyph(String text, Matrix4f matrix, int color) {
    }

    @Override
    public void text(String s, float x, float y, float z, int color, float scale) {
        if (s == null || s.isEmpty() || scale <= 0.0f) {
            return;
        }
        final int a = Math.round(((color >>> 24) & 0xFF) * attenuation(x, y, z));
        if (a < 6) {
            return;
        }
        final Matrix4f m = new Matrix4f(this.base)
                .translate(x, y, z)
                .mul(this.textBasis)
                .scale(scale, scale, scale);
        this.deferred.add(new Glyph(s, m, (Math.min(a, 255) << 24) | (color & 0x00FFFFFF)));
    }

    @Override
    public float textWidth(String s, float scale) {
        return this.font.width(s) * scale;
    }

    public void finish() {
        this.additive = null;
        this.skin = null;
        if (this.buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(HoloRenderTypes.SKIN);
            source.endBatch(this.additiveType);
        }
        for (final Glyph g : this.deferred) {
            this.font.drawInBatch(g.text(), 0.0f, 0.0f, g.color(), false, g.matrix(), this.buffers,
                    Font.DisplayMode.NORMAL, 0, this.light);
        }
        this.deferred.clear();
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
        return this.pixel;
    }

    public float viewDistance() {
        return (this.near + this.far) * 0.5f;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }
}
