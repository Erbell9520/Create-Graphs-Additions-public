package com.createstats.renderer;

import com.createstats.block.EInkPanelBlock;
import com.createstats.blockentity.EInkPanelBE;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class EInkPanelRenderer implements BlockEntityRenderer<EInkPanelBE> {
    private static final float PX = 1.0f / 16.0f;
    private static final float X0 = 2.8f * PX;
    private static final float X1 = 13.2f * PX;
    private static final float Y0 = 2.8f * PX;
    private static final float Y1 = 13.2f * PX;
    private static final float Z = 1.8f * PX + (1.0f / 512.0f);

    public EInkPanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EInkPanelBE be, float partialTick, PoseStack ps, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        final ResourceLocation texture = EInkPanelTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }
        final Direction facing = be.getBlockState().getValue(EInkPanelBlock.FACING);

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ps.translate(-0.5, -0.5, -0.5);

        final VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(texture));
        final Matrix4f mat = ps.last().pose();
        vertex(vc, mat, X0, Y1, 0.0f, 0.0f, packedLight);
        vertex(vc, mat, X0, Y0, 0.0f, 1.0f, packedLight);
        vertex(vc, mat, X1, Y0, 1.0f, 1.0f, packedLight);
        vertex(vc, mat, X1, Y1, 1.0f, 0.0f, packedLight);
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float u, float v, int light) {
        vc.addVertex(mat, x, y, Z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 0.0f, -1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(EInkPanelBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }
}
