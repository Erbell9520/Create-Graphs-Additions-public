package com.createstats.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class BoreFitting {
    private BoreFitting() {
    }

    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/cable_gland.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float FLANGE_DEPTH = 1.0f * PX;

    private static final float BODY_DEPTH = 2.5f * PX;

    private static final float FLANGE_PROUD = 1.25f * PX;
    private static final float BODY_PROUD = 0.5f * PX;

    private static final float BITE = 0.5f * PX;

    public static VertexConsumer buffer(MultiBufferSource buffers) {
        return Buffers.live(buffers, RenderType.entityCutoutNoCull(SKIN));
    }

    public static void draw(VertexConsumer vc, Matrix4f mat, Direction towards, float along,
                            float u, float v, float cordU, float cordV, int light) {
        final boolean positive = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        final float outer = positive ? along + BITE : along - BITE;
        final float flangeBack = positive ? along - FLANGE_DEPTH : along + FLANGE_DEPTH;
        ring(vc, mat, towards, u, v, outer, flangeBack,
                cordU + FLANGE_PROUD, cordV + FLANGE_PROUD, 0xFF8E949B, light);
        ring(vc, mat, towards, u, v, flangeBack,
                positive ? flangeBack - BODY_DEPTH : flangeBack + BODY_DEPTH,
                cordU + BODY_PROUD, cordV + BODY_PROUD, 0xFF6E747B, light);
    }

    private static void ring(VertexConsumer vc, Matrix4f mat, Direction towards, float u, float v,
                             float from, float to, float halfU, float halfV, int argb, int light) {
        final Direction.Axis axis = towards.getAxis();
        final Direction.Axis[] across = across(axis);
        final float[] min = new float[3];
        final float[] max = new float[3];
        min[axis.ordinal()] = Math.min(from, to);
        max[axis.ordinal()] = Math.max(from, to);
        min[across[0].ordinal()] = u - halfU;
        max[across[0].ordinal()] = u + halfU;
        min[across[1].ordinal()] = v - halfV;
        max[across[1].ordinal()] = v + halfV;
        emit(vc, mat, min, max, argb, light);
    }

    public static Direction.Axis[] across(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.X};
        };
    }

    private static void emit(VertexConsumer vc, Matrix4f mat, float[] min, float[] max, int argb, int light) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        for (Direction dir : Direction.values()) {
            final float[][] corners = faceCorners(min, max, dir);
            for (int i = 0; i < 4; i++) {
                vc.addVertex(mat, corners[i][0], corners[i][1], corners[i][2])
                        .setColor(r, g, b, a)
                        .setUv(UV[i][0], UV[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }

    private static final float[][] UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static float[][] faceCorners(float[] n, float[] x, Direction dir) {
        return switch (dir) {
            case DOWN -> new float[][] {{n[0], n[1], n[2]}, {x[0], n[1], n[2]}, {x[0], n[1], x[2]}, {n[0], n[1], x[2]}};
            case UP -> new float[][] {{n[0], x[1], x[2]}, {x[0], x[1], x[2]}, {x[0], x[1], n[2]}, {n[0], x[1], n[2]}};
            case NORTH -> new float[][] {{x[0], n[1], n[2]}, {n[0], n[1], n[2]}, {n[0], x[1], n[2]}, {x[0], x[1], n[2]}};
            case SOUTH -> new float[][] {{n[0], n[1], x[2]}, {x[0], n[1], x[2]}, {x[0], x[1], x[2]}, {n[0], x[1], x[2]}};
            case WEST -> new float[][] {{n[0], n[1], n[2]}, {n[0], n[1], x[2]}, {n[0], x[1], x[2]}, {n[0], x[1], n[2]}};
            case EAST -> new float[][] {{x[0], n[1], x[2]}, {x[0], n[1], n[2]}, {x[0], x[1], n[2]}, {x[0], x[1], x[2]}};
        };
    }
}
