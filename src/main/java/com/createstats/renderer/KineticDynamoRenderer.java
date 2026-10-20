package com.createstats.renderer;

import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.power.DynamoTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

public class KineticDynamoRenderer implements BlockEntityRenderer<KineticDynamoBE> {
    private static final ResourceLocation SHAFT =
            ResourceLocation.fromNamespaceAndPath("create", "textures/block/axis.png");
    private static final ResourceLocation SHAFT_END =
            ResourceLocation.fromNamespaceAndPath("create", "textures/block/axis_top.png");
    private static final ResourceLocation FAN = tex("dynamo_fan");
    private static final ResourceLocation ARMATURE = tex("dynamo_armature");
    private static final ResourceLocation BAND = tex("dynamo_band");

    private static final float PX = 1.0f / 16.0f;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/" + name + ".png");
    }

    public KineticDynamoRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(KineticDynamoBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }

        final float speed = be.getSpeed();

        final double time = com.createstats.client.AnimationClock.time(be.getLevel(), partialTicks);
        final float angle = (float) (time * speed * 0.3 % 360.0);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-0.5, 0.0, -0.5);
        final Matrix4f mat = poseStack.last().pose();

        drawShaft(buffers, mat, light);
        switch (be.tier()) {
            case OPEN_FRAME -> drawArmature(buffers, mat, light);
            case STANDARD -> drawFan(buffers, mat, light, 14.0f, 15.0f, 12.5f);
            case INDUSTRIAL -> drawFan(buffers, mat, light, 15.0f, 16.0f, 11.5f);
        }
        poseStack.popPose();
    }

    private void drawShaft(MultiBufferSource buffers, Matrix4f mat, int light) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SHAFT));
        box(vc, mat, 6, -5, 6, 10, 2, 10, light, Direction.UP, Direction.DOWN);

        final VertexConsumer end = buffers.getBuffer(RenderType.entityCutoutNoCull(SHAFT_END));
        box(end, mat, 6, -5.01f, 6, 10, -5.0f, 10, light, Direction.UP);
    }

    private void drawArmature(MultiBufferSource buffers, Matrix4f mat, int light) {
        final VertexConsumer core = buffers.getBuffer(RenderType.entityCutoutNoCull(ARMATURE));
        box(core, mat, 5.5f, 2, 5.5f, 10.5f, 12.5f, 10.5f, light);

        box(core, mat, 5.0f, 3, 7.4f, 11.0f, 11.5f, 8.6f, light);
        box(core, mat, 7.4f, 3, 5.0f, 8.6f, 11.5f, 11.0f, light);

        final VertexConsumer band = buffers.getBuffer(RenderType.entityCutoutNoCull(BAND));
        for (float y : new float[] {3.5f, 6.0f, 8.5f, 11.0f}) {
            box(band, mat, 5.2f, y, 5.2f, 10.8f, y + 0.9f, 10.8f, light);
        }
    }

    private void drawFan(MultiBufferSource buffers, Matrix4f mat, int light,
                         float y0, float y1, float reach) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FAN));
        box(vc, mat, 6.5f, y0, 6.5f, 9.5f, y1, 9.5f, light);
        final float lo = y0 + (y1 - y0) * 0.25f;
        final float hi = y1 - (y1 - y0) * 0.25f;
        box(vc, mat, 9.5f, lo, 7.2f, reach, hi, 8.8f, light);
        box(vc, mat, 16.0f - reach, lo, 7.2f, 6.5f, hi, 8.8f, light);
        box(vc, mat, 7.2f, lo, 9.5f, 8.8f, hi, reach, light);
        box(vc, mat, 7.2f, lo, 16.0f - reach, 8.8f, hi, 6.5f, light);
    }

    private void box(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0,
                     float x1, float y1, float z1, int light, Direction... skip) {
        final float[] min = {Math.min(x0, x1) * PX, Math.min(y0, y1) * PX, Math.min(z0, z1) * PX};
        final float[] max = {Math.max(x0, x1) * PX, Math.max(y0, y1) * PX, Math.max(z0, z1) * PX};
        int mask = 0;
        for (Direction d : skip) {
            if (d != null) {
                mask |= 1 << d.ordinal();
            }
        }
        for (Direction dir : Direction.values()) {
            if ((mask & (1 << dir.ordinal())) != 0) {
                continue;
            }
            final float[][] c = corners(min, max, dir);
            final float su = Math.max(0.06f, span(min, max, dir.getAxis() == Direction.Axis.X
                    ? Direction.Axis.Z : Direction.Axis.X));
            final float sv = Math.max(0.06f, span(min, max, dir.getAxis() == Direction.Axis.Y
                    ? Direction.Axis.Z : Direction.Axis.Y));
            final float[][] uv = {{0, 0}, {su, 0}, {su, sv}, {0, sv}};
            for (int i = 0; i < 4; i++) {
                vc.addVertex(mat, c[i][0], c[i][1], c[i][2])
                        .setColor(255, 255, 255, 255)
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }

    private static float span(float[] min, float[] max, Direction.Axis axis) {
        return max[axis.ordinal()] - min[axis.ordinal()];
    }

    private static float[][] corners(float[] n, float[] x, Direction dir) {
        return switch (dir) {
            case DOWN -> new float[][] {{n[0], n[1], n[2]}, {x[0], n[1], n[2]}, {x[0], n[1], x[2]}, {n[0], n[1], x[2]}};
            case UP -> new float[][] {{n[0], x[1], x[2]}, {x[0], x[1], x[2]}, {x[0], x[1], n[2]}, {n[0], x[1], n[2]}};
            case NORTH -> new float[][] {{x[0], n[1], n[2]}, {n[0], n[1], n[2]}, {n[0], x[1], n[2]}, {x[0], x[1], n[2]}};
            case SOUTH -> new float[][] {{n[0], n[1], x[2]}, {x[0], n[1], x[2]}, {x[0], x[1], x[2]}, {n[0], x[1], x[2]}};
            case WEST -> new float[][] {{n[0], n[1], n[2]}, {n[0], n[1], x[2]}, {n[0], x[1], x[2]}, {n[0], x[1], n[2]}};
            case EAST -> new float[][] {{x[0], n[1], x[2]}, {x[0], n[1], n[2]}, {x[0], x[1], n[2]}, {x[0], x[1], x[2]}};
        };
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(KineticDynamoBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5);
    }

    @Override
    public boolean shouldRenderOffScreen(KineticDynamoBE be) {
        return false;
    }

    static {
        assert DynamoTier.values().length == 3 : "a grade was added without deciding what moves on it";
    }
}
