package com.createstats.renderer;

import com.createstats.blockentity.CapacitorBankBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

// gauge length follows the build, content follows charge. can't bake that
public class CapacitorBankRenderer implements BlockEntityRenderer<CapacitorBankBE> {
    private static final float PROUD = 0.02f;

    private static final float Z_FACE = 0.005f;

    public CapacitorBankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CapacitorBankBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.isRenderController() || !be.isSolidBox()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final BlockPos min = be.structureMin();
        final BlockPos max = be.structureMax();

        final int light = LightTexture.FULL_BRIGHT;

        final float x0 = min.getX() - origin.getX();
        final float y0 = min.getY() - origin.getY();
        final float z0 = min.getZ() - origin.getZ();
        final float x1 = max.getX() - origin.getX() + 1.0f;
        final float z1 = max.getZ() - origin.getZ() + 1.0f;

        final int spanX = max.getX() - min.getX() + 1;
        final int spanZ = max.getZ() - min.getZ() + 1;
        final int height = max.getY() - min.getY() + 1;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            final int faceWidth = (dir.getAxis() == Direction.Axis.Z) ? spanX : spanZ;

            final ResourceLocation texture =
                    CapacitorBankTextureManager.getOrUpdateTexture(be, faceWidth, height);
            if (texture == null) {
                continue;
            }
            poseStack.pushPose();
            faceTransform(poseStack, dir, x0, y0, z0, x1, z1);
            final VertexConsumer vc = Buffers.live(bufferSource, RenderType.entityCutoutNoCull(texture));
            final Matrix4f mat = poseStack.last().pose();

            vertex(vc, mat, 0.0f, height, 0.0f, 0.0f, light);
            vertex(vc, mat, 0.0f, 0.0f, 0.0f, 1.0f, light);
            vertex(vc, mat, faceWidth, 0.0f, 1.0f, 1.0f, light);
            vertex(vc, mat, faceWidth, height, 1.0f, 0.0f, light);
            poseStack.popPose();
        }
    }

    private static void faceTransform(PoseStack poseStack, Direction dir,
                                      float x0, float y0, float z0, float x1, float z1) {
        switch (dir) {
            case SOUTH -> poseStack.translate(x0, y0, z1);
            case NORTH -> {
                poseStack.translate(x1, y0, z0);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case WEST -> {
                poseStack.translate(x0, y0, z0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
            }
            default -> {
                poseStack.translate(x1, y0, z1);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
        }
        poseStack.translate(0.0f, 0.0f, PROUD);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y,
                               float u, float v, int light) {
        vc.addVertex(mat, x, y, Z_FACE)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 0.0f, 1.0f);
    }
}
