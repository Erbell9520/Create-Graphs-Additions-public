package com.createstats.renderer;

import com.createstats.block.CalculatorBlock;
import com.createstats.blockentity.CalculatorBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class CalculatorRenderer implements BlockEntityRenderer<CalculatorBE>
{
    private static final float FACE_EPSILON = 1.0f / 512.0f;

    public CalculatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    public void render(CalculatorBE be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        if (!be.isController()) {
            return;
        }
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CalculatorBlock)) {
            return;
        }
        final ResourceLocation texture = CalculatorTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }
        final Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        final float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        ps.translate(-0.5, -0.5, -0.5);
        ps.translate(0.0, 0.0, 3.0 / 16.0 + CalculatorRenderer.FACE_EPSILON);
        this.renderFaceQuad(ps, buf, texture, be.getMultiblockWidth(), be.getMultiblockHeight(), packedLight, packedOverlay);
        ps.popPose();
    }

    private void renderFaceQuad(PoseStack ps, MultiBufferSource buf, ResourceLocation texture, int width, int height, int packedLight, int packedOverlay) {
        final VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(texture));
        final PoseStack.Pose pose = ps.last();
        final float w = width;
        final float h = height;
        final float inset = -0.03125f;
        final float x0 = inset;
        final float x1 = w - inset;
        final float y0 = inset;
        final float y1 = h - inset;
        this.vertex(vc, pose, x0, y1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, packedLight, packedOverlay, 255, 255, 255, 255);
        this.vertex(vc, pose, x0, y0, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, -1.0f, packedLight, packedOverlay, 255, 255, 255, 255);
        this.vertex(vc, pose, x1, y0, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, packedLight, packedOverlay, 255, 255, 255, 255);
        this.vertex(vc, pose, x1, y1, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, packedLight, packedOverlay, 255, 255, 255, 255);
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz, int packedLight, int packedOverlay, int r, int g, int b, int a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
    }

    public boolean shouldRenderOffScreen(CalculatorBE be) {
        return true;
    }

    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
