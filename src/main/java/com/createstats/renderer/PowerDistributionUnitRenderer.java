package com.createstats.renderer;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

// one quad off a cached texture. three dozen quads and 8 font batches a frame before
public class PowerDistributionUnitRenderer implements BlockEntityRenderer<PowerDistributionUnitBE> {
    static final float SCREEN_L = 3.0f / 16.0f - 0.5f;
    static final float SCREEN_R = 13.0f / 16.0f - 0.5f;

    static final float GLASS_B = 6.0f / 16.0f - 0.5f;

    static final float SCREEN_B = 7.8f / 16.0f - 0.5f;
    static final float SCREEN_T = 14.0f / 16.0f - 0.5f;

    private static final float SCREEN_OUT = 0.5f - 4.0f / 16.0f + 0.002f;

    private static final float Z_FACE = 0.005f;

    public PowerDistributionUnitRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PowerDistributionUnitBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        final Direction facing = be.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? be.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;

        drawStudWires(be, facing, poseStack, buffers, packedLight);

        poseStack.pushPose();

        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0.0f, 0.0f, SCREEN_OUT);

        final ResourceLocation texture = PowerDistributionUnitTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            poseStack.popPose();
            return;
        }
        final VertexConsumer vc = Buffers.live(buffers, RenderType.entityCutoutNoCull(texture));
        final Matrix4f mat = poseStack.last().pose();
        final int light = LightTexture.FULL_BRIGHT;

        vertex(vc, mat, SCREEN_L, SCREEN_T, 0.0f, 0.0f, light);
        vertex(vc, mat, SCREEN_L, GLASS_B, 0.0f, 1.0f, light);
        vertex(vc, mat, SCREEN_R, GLASS_B, 1.0f, 1.0f, light);
        vertex(vc, mat, SCREEN_R, SCREEN_T, 1.0f, 0.0f, light);

        poseStack.popPose();
    }

    private static void drawStudWires(PowerDistributionUnitBE be, Direction facing, PoseStack poseStack,
                                      MultiBufferSource buffers, int packedLight) {
        if (be.wiresOnStuds() <= 0 || be.getLevel() == null) {
            return;
        }
        final VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final Matrix4f mat = poseStack.last().pose();
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            final java.util.List<PowerDistributionUnitBE.StudLink> links = be.studLinks(stud);
            if (links.isEmpty()) {
                continue;
            }
            final double[] box = com.createstats.power.PduStuds.boxOf(facing, stud);
            final float sx = (float) ((box[0] + box[2]) * 0.5);
            final float sz = (float) ((box[1] + box[3]) * 0.5);
            final float sy = (float) com.createstats.power.PduStuds.topY();
            for (PowerDistributionUnitBE.StudLink link : links) {
                final net.minecraft.world.phys.Vec3 to = PowerConnectorRenderer.attachPoint(
                        be.getLevel().getBlockState(link.to()));
                PowerConnectorRenderer.drawCable(vc, mat, be.getLevel(), origin, link.to(), sx, sy, sz,
                        (float) (link.to().getX() - origin.getX() + to.x),
                        (float) (link.to().getY() - origin.getY() + to.y),
                        (float) (link.to().getZ() - origin.getZ() + to.z),
                        packedLight, link.tier());
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PowerDistributionUnitBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PowerDistributionUnitBE be) {
        final PowerConnectorRenderer.WireBounds bounds =
                new PowerConnectorRenderer.WireBounds(be.getBlockPos());
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            for (PowerDistributionUnitBE.StudLink link : be.studLinks(stud)) {
                bounds.include(link.to());
            }
        }
        return bounds.build();
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
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
