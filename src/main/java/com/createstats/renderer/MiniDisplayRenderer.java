package com.createstats.renderer;

import com.createstats.block.MiniDisplayBlock;
import com.createstats.blockentity.MiniDisplayBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

@OnlyIn(Dist.CLIENT)
public class MiniDisplayRenderer implements BlockEntityRenderer<MiniDisplayBE> {

    private static final float FACE_EPSILON = 1.0f / 512.0f;

    public MiniDisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MiniDisplayBE be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        if (!be.isController()) {
            return;
        }
        final ResourceLocation textureLoc = MiniDisplayTextureManager.getOrUpdateTexture(be);
        if (textureLoc == null) {
            return;
        }

        final Direction facing = be.getBlockState().getValue(MiniDisplayBlock.FACING);
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
        ps.translate(0.0, 0.0, 2.7 / 16.0 + FACE_EPSILON);
        this.renderTexturedQuad(ps, buf, textureLoc, be.getDisplayWidth(), packedLight, packedOverlay);
        ps.popPose();
    }

    private void renderTexturedQuad(PoseStack ps, MultiBufferSource buf, ResourceLocation texture, int width, int packedLight, int packedOverlay) {
        final VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(texture));
        final Matrix4f mat = ps.last().pose();
        final Matrix3f norm = ps.last().normal();
        final float x0 = 1.5f / 16.0f;
        final float x1 = width - (1.5f / 16.0f);
        final float y0 = 2.5f / 16.0f;
        final float y1 = 13.5f / 16.0f;
        vc.addVertex(mat, x0, y1, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, x0, y0, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, x1, y0, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(MiniDisplayBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
