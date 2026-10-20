package com.createstats.renderer;

import com.createstats.block.FactoryLogicControllerBlock;
import com.createstats.blockentity.FactoryLogicControllerBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class FactoryLogicControllerRenderer implements BlockEntityRenderer<FactoryLogicControllerBE> {
    private static final float PX = 1.0f / 16.0f;
    private static final float FACE_EPSILON = 1.0f / 512.0f;
    private static final float SCREEN_LEFT_PX = 3.15f;
    private static final float SCREEN_RIGHT_PX = 12.85f;
    private static final float SCREEN_BOTTOM_PX = 6.15f;
    private static final float SCREEN_TOP_PX = 13.85f;
    private static final float SCREEN_DEPTH_PX = 3.5f;
    private static final int FULL_BRIGHT = 0x00F000F0;

    public FactoryLogicControllerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(FactoryLogicControllerBE be, float partialTick, PoseStack ps, MultiBufferSource buf,
                       int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof FactoryLogicControllerBlock)) {
            return;
        }

        AntennaRendererHelper.render(be, ps, buf, packedLight, packedOverlay);

        ResourceLocation texture = FactoryLogicControllerTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yRot(facing)));
        ps.translate(-0.5, -0.5, -0.5);
        ps.translate(0.0, 0.0, SCREEN_DEPTH_PX * PX + FACE_EPSILON);
        renderScreenQuad(ps, buf, texture, packedOverlay);
        ps.popPose();
    }

    private void renderScreenQuad(PoseStack ps, MultiBufferSource buf, ResourceLocation texture, int packedOverlay) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(texture));
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        float x0 = SCREEN_LEFT_PX * PX;
        float x1 = SCREEN_RIGHT_PX * PX;
        float y0 = SCREEN_BOTTOM_PX * PX;
        float y1 = SCREEN_TOP_PX * PX;
        vertex(vc, pose, normal, x0, y1, 0.0f, 0.0f, 0.0f, packedOverlay);
        vertex(vc, pose, normal, x0, y0, 0.0f, 0.0f, 1.0f, packedOverlay);
        vertex(vc, pose, normal, x1, y0, 0.0f, 1.0f, 1.0f, packedOverlay);
        vertex(vc, pose, normal, x1, y1, 0.0f, 1.0f, 0.0f, packedOverlay);
    }

    private void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                        float x, float y, float z, float u, float v, int packedOverlay) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay == 0 ? OverlayTexture.NO_OVERLAY : packedOverlay)
                .setLight(FULL_BRIGHT)
                .setNormal(0.0f, 0.0f, -1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(FactoryLogicControllerBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }
}
