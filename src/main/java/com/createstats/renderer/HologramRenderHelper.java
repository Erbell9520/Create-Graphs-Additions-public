package com.createstats.renderer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HologramRenderHelper
{
    static final float[] SIN_TABLE;
    static final float[] COS_TABLE;
    public static final int FULL_BRIGHT = 15728880;
    
    private HologramRenderHelper() {
    }
    
    public static void emitQuad(VertexConsumer vc, Matrix4f mat, Matrix3f norm, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int r, int g, int b, int a, int packedLight, float nx, float ny, float nz) {
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(nx, ny, nz);
    }
    
    public static void emitLine3D(VertexConsumer vc, Matrix4f mat, Matrix3f norm, float x0, float y0, float z0, float x1, float y1, float z1, float halfWidth, int r, int g, int b, int a, int packedLight) {
        final float dx = x1 - x0;
        final float dz = z1 - z0;
        final float len = (float)Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4f) {
            return;
        }
        final float px = -dz / len * halfWidth;
        final float pz = dx / len * halfWidth;
        emitQuad(vc, mat, norm, x0 + px, y0, z0 + pz, x0 - px, y0, z0 - pz, x1 - px, y1, z1 - pz, x1 + px, y1, z1 + pz, r, g, b, a, packedLight, 0.0f, 1.0f, 0.0f);
    }
    
    public static void emitArcSegment(VertexConsumer vc, Matrix4f mat, Matrix3f norm, float innerR, float outerR, float angle0, float angle1, int r, int g, int b, int a, int packedLight) {
        final float ix0 = (float)Math.cos(angle0) * innerR;
        final float iy0 = (float)Math.sin(angle0) * innerR;
        final float ox0 = (float)Math.cos(angle0) * outerR;
        final float oy0 = (float)Math.sin(angle0) * outerR;
        final float ix2 = (float)Math.cos(angle1) * innerR;
        final float iy2 = (float)Math.sin(angle1) * innerR;
        final float ox2 = (float)Math.cos(angle1) * outerR;
        final float oy2 = (float)Math.sin(angle1) * outerR;
        emitQuad(vc, mat, norm, ix0, iy0, 0.0f, ox0, oy0, 0.0f, ox2, oy2, 0.0f, ix2, iy2, 0.0f, r, g, b, a, packedLight, 0.0f, 0.0f, -1.0f);
    }
    
    public static void emitHorizontalRingSegment(VertexConsumer vc, Matrix4f mat, Matrix3f norm, float innerR, float outerR, float angle0, float angle1, float y, int r, int g, int b, int a, int packedLight) {
        final float ix0 = (float)Math.cos(angle0) * innerR;
        final float iz0 = (float)Math.sin(angle0) * innerR;
        final float ox0 = (float)Math.cos(angle0) * outerR;
        final float oz0 = (float)Math.sin(angle0) * outerR;
        final float ix2 = (float)Math.cos(angle1) * innerR;
        final float iz2 = (float)Math.sin(angle1) * innerR;
        final float ox2 = (float)Math.cos(angle1) * outerR;
        final float oz2 = (float)Math.sin(angle1) * outerR;
        emitQuad(vc, mat, norm, ix0, y, iz0, ox0, y, oz0, ox2, y, oz2, ix2, y, iz2, r, g, b, a, packedLight, 0.0f, 1.0f, 0.0f);
    }
    
    public static void emitCube(VertexConsumer vc, Matrix4f mat, Matrix3f norm, float cx, float cy, float cz, float s, int r, int g, int b, int a, int packedLight) {
        emitQuad(vc, mat, norm, cx - s, cy - s, cz - s, cx - s, cy + s, cz - s, cx + s, cy + s, cz - s, cx + s, cy - s, cz - s, r, g, b, a, packedLight, 0.0f, 0.0f, -1.0f);
        emitQuad(vc, mat, norm, cx + s, cy - s, cz + s, cx + s, cy + s, cz + s, cx - s, cy + s, cz + s, cx - s, cy - s, cz + s, r, g, b, a, packedLight, 0.0f, 0.0f, 1.0f);
        emitQuad(vc, mat, norm, cx - s, cy + s, cz - s, cx - s, cy + s, cz + s, cx + s, cy + s, cz + s, cx + s, cy + s, cz - s, r, g, b, a, packedLight, 0.0f, 1.0f, 0.0f);
        emitQuad(vc, mat, norm, cx - s, cy - s, cz + s, cx - s, cy - s, cz - s, cx + s, cy - s, cz - s, cx + s, cy - s, cz + s, r, g, b, a, packedLight, 0.0f, -1.0f, 0.0f);
        emitQuad(vc, mat, norm, cx - s, cy - s, cz + s, cx - s, cy + s, cz + s, cx - s, cy + s, cz - s, cx - s, cy - s, cz - s, r, g, b, a, packedLight, -1.0f, 0.0f, 0.0f);
        emitQuad(vc, mat, norm, cx + s, cy - s, cz - s, cx + s, cy + s, cz - s, cx + s, cy + s, cz + s, cx + s, cy - s, cz + s, r, g, b, a, packedLight, 1.0f, 0.0f, 0.0f);
    }
    
    public static float[] downsample(float[] data, int maxPoints) {
        if (data.length <= maxPoints) {
            return data;
        }
        final float[] result = new float[maxPoints];
        final float binSize = data.length / (float)maxPoints;
        for (int i = 0; i < maxPoints; ++i) {
            final int start = (int)(i * binSize);
            int end = Math.min((int)((i + 1) * binSize), data.length);
            if (end <= start) {
                end = start + 1;
            }
            float sum = 0.0f;
            for (int j = start; j < end; ++j) {
                sum += data[j];
            }
            result[i] = sum / (end - start);
        }
        return result;
    }
    
    public static float[] downsampleToBuckets(float[] data, int numBuckets) {
        return downsample(data, numBuckets);
    }
    
    static {
        SIN_TABLE = new float[64];
        COS_TABLE = new float[64];
        for (int i = 0; i < 64; ++i) {
            final double angle = 6.283185307179586 * i / 64.0;
            HologramRenderHelper.SIN_TABLE[i] = (float)Math.sin(angle);
            HologramRenderHelper.COS_TABLE[i] = (float)Math.cos(angle);
        }
    }
}
