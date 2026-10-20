package com.createstats.renderer;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.power.BatteryControllerPorts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

import java.util.List;

public class BatteryControllerRenderer implements BlockEntityRenderer<BatteryControllerBE> {
    static final float SCREEN_L = 2.0f / 16.0f - 0.5f;
    static final float SCREEN_R = 14.0f / 16.0f - 0.5f;
    static final float SCREEN_B = 4.0f / 16.0f - 0.5f;
    static final float SCREEN_T = 13.0f / 16.0f - 0.5f;

    private static final float SCREEN_OUT = 0.5f - 3.0f / 16.0f + 0.002f;

    private static final float Z_FACE = 0.005f;

    public BatteryControllerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BatteryControllerBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        final Direction facing = be.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? be.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;

        drawPortWires(be, facing, poseStack, buffers, packedLight);

        final ResourceLocation texture = BatteryControllerTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0.0f, 0.0f, SCREEN_OUT);

        final VertexConsumer vc = Buffers.live(buffers, RenderType.entityCutoutNoCull(texture));
        final Matrix4f mat = poseStack.last().pose();
        final int light = LightTexture.FULL_BRIGHT;

        vertex(vc, mat, SCREEN_L, SCREEN_T, 0.0f, 0.0f, light);
        vertex(vc, mat, SCREEN_L, SCREEN_B, 0.0f, 1.0f, light);
        vertex(vc, mat, SCREEN_R, SCREEN_B, 1.0f, 1.0f, light);
        vertex(vc, mat, SCREEN_R, SCREEN_T, 1.0f, 0.0f, light);

        poseStack.popPose();
    }

    private static void drawPortWires(BatteryControllerBE be, Direction facing, PoseStack poseStack,
                                      MultiBufferSource buffers, int packedLight) {
        if (be.getLevel() == null) {
            return;
        }
        VertexConsumer vc = null;
        final Matrix4f mat = poseStack.last().pose();
        final BlockPos origin = be.getBlockPos();
        for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
            final List<BatteryControllerBE.PortLink> links = be.links(port);
            if (links.isEmpty()) {
                continue;
            }
            if (vc == null) {
                vc = PowerConnectorRenderer.wireBuffer(buffers);
            }
            final double[] box = BatteryControllerPorts.boxOf(facing, port);
            final float sx = (float) ((box[0] + box[2]) * 0.5);
            final float sz = (float) ((box[1] + box[3]) * 0.5);
            final float sy = (float) BatteryControllerPorts.topY();
            for (BatteryControllerBE.PortLink link : links) {
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

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y,
                               float u, float v, int light) {
        vc.addVertex(mat, x, y, Z_FACE)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(com.createstats.blockentity.BatteryControllerBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.createstats.blockentity.BatteryControllerBE be) {
        final PowerConnectorRenderer.WireBounds bounds =
                new PowerConnectorRenderer.WireBounds(be.getBlockPos());
        for (int port = 0; port < com.createstats.power.BatteryControllerPorts.COUNT; port++) {
            for (com.createstats.blockentity.BatteryControllerBE.PortLink link : be.links(port)) {
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
}
