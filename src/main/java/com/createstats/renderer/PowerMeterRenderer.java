package com.createstats.renderer;

import com.createstats.block.PowerMeterBlock;
import com.createstats.blockentity.PowerMeterBE;
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

// two quads, both side windows
@OnlyIn(Dist.CLIENT)
public class PowerMeterRenderer implements BlockEntityRenderer<PowerMeterBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float LIFT = 1.0f / 512.0f;

    private static final float X_WEST = 1.5f * PX - LIFT;
    private static final float X_EAST = 14.5f * PX + LIFT;
    private static final float Z0 = 4.0f * PX;
    private static final float Z1 = 12.0f * PX;
    private static final float Y0 = 5.25f * PX;
    private static final float Y1 = 10.75f * PX;

    public PowerMeterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PowerMeterBE be, float partialTick, PoseStack ps, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        final ResourceLocation texture = PowerMeterTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }
        final Direction facing = be.getBlockState().hasProperty(PowerMeterBlock.FACING)
                ? be.getBlockState().getValue(PowerMeterBlock.FACING)
                : Direction.NORTH;

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);

        ps.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ps.translate(-0.5, -0.5, -0.5);

        final VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        final Matrix4f mat = ps.last().pose();

        vertex(vc, mat, X_WEST, Y1, Z0, 0.0f, 0.0f, -1.0f);
        vertex(vc, mat, X_WEST, Y0, Z0, 0.0f, 1.0f, -1.0f);
        vertex(vc, mat, X_WEST, Y0, Z1, 1.0f, 1.0f, -1.0f);
        vertex(vc, mat, X_WEST, Y1, Z1, 1.0f, 0.0f, -1.0f);

        vertex(vc, mat, X_EAST, Y1, Z1, 0.0f, 0.0f, 1.0f);
        vertex(vc, mat, X_EAST, Y0, Z1, 0.0f, 1.0f, 1.0f);
        vertex(vc, mat, X_EAST, Y0, Z0, 1.0f, 1.0f, 1.0f);
        vertex(vc, mat, X_EAST, Y1, Z0, 1.0f, 0.0f, 1.0f);

        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                               float u, float v, float normalX) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normalX, 0.0f, 0.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(PowerMeterBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }
}
