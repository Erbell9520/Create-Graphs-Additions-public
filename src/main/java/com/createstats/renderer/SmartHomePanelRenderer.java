package com.createstats.renderer;

import com.createstats.block.SmartHomePanelBlock;
import com.createstats.blockentity.SmartHomePanelBE;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

// pips are drawn, a blockstate per toggle = remesh per click
@OnlyIn(Dist.CLIENT)
public class SmartHomePanelRenderer implements BlockEntityRenderer<SmartHomePanelBE> {
    private static final ResourceLocation PIP =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/smart_home_pip.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float Z = 1.6f * PX + (1.0f / 512.0f);

    private static final float GLASS_X0 = 4.0f;
    private static final float GLASS_X1 = 12.0f;
    private static final float GLASS_Y0 = 3.0f;
    private static final float GLASS_Y1 = 13.0f;

    private static final float PIP_X0 = 10.0f;
    private static final float PIP_X1 = 11.0f;

    private static final float[] PIP_Y = {10.5f, 8.5f, 6.5f, 4.5f};
    private static final float PIP_H = 1.0f;

    private static final int ON = 0xFF18A957;
    private static final int OFF = 0xFFC3CEC7;

    public SmartHomePanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SmartHomePanelBE be, float partialTick, PoseStack ps, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!(be.getBlockState().getBlock() instanceof SmartHomePanelBlock)) {
            return;
        }

        if (!be.isAwake()) {
            return;
        }
        final Direction facing = be.getBlockState().getValue(SmartHomePanelBlock.FACING);
        if (be.isHiFiFace()) {
            renderHiFi(be, ps, buffer, facing, packedLight);
            return;
        }
        final int devices = be.shownDevices();
        if (devices <= 0) {
            return;
        }
        final int on = Math.min(be.shownOn(), devices);

        final int shown = Math.min(PIP_Y.length, devices);
        final int lit = Math.round(on * shown / (float) devices);

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ps.translate(-0.5, -0.5, -0.5);

        final VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(PIP));
        final Matrix4f mat = ps.last().pose();
        for (int i = 0; i < shown; i++) {
            final boolean alight = i < lit;

            quad(vc, mat, PIP_X0, PIP_Y[i] - PIP_H / 2, PIP_X1, PIP_Y[i] + PIP_H / 2,
                    alight ? ON : OFF, alight ? LightTexture.FULL_BRIGHT : packedLight);
        }
        ps.popPose();
    }

    private void renderHiFi(SmartHomePanelBE be, PoseStack ps, MultiBufferSource buffer,
                            Direction facing, int packedLight) {
        final ResourceLocation face = SmartHomeFaceTextureManager.getOrUpdateTexture(be);
        if (face == null) {
            return;
        }
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ps.translate(-0.5, -0.5, -0.5);

        final int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), 13),
                LightTexture.sky(packedLight));
        final VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(face));
        final Matrix4f mat = ps.last().pose();
        vertex(vc, mat, GLASS_X0 * PX, GLASS_Y1 * PX, 0.0f, 0.0f, 255, 255, 255, light);
        vertex(vc, mat, GLASS_X0 * PX, GLASS_Y0 * PX, 0.0f, 1.0f, 255, 255, 255, light);
        vertex(vc, mat, GLASS_X1 * PX, GLASS_Y0 * PX, 1.0f, 1.0f, 255, 255, 255, light);
        vertex(vc, mat, GLASS_X1 * PX, GLASS_Y1 * PX, 1.0f, 0.0f, 255, 255, 255, light);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1,
                             int argb, int light) {
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        vertex(vc, mat, x0 * PX, y1 * PX, 0.0f, 0.0f, r, g, b, light);
        vertex(vc, mat, x0 * PX, y0 * PX, 0.0f, 1.0f, r, g, b, light);
        vertex(vc, mat, x1 * PX, y0 * PX, 1.0f, 1.0f, r, g, b, light);
        vertex(vc, mat, x1 * PX, y1 * PX, 1.0f, 0.0f, r, g, b, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float u, float v,
                               int r, int g, int b, int light) {
        vc.addVertex(mat, x, y, Z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 0.0f, -1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(SmartHomePanelBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(32);
    }
}
