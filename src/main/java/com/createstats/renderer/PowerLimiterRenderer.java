package com.createstats.renderer;

import com.createstats.block.PowerLimiterBlock;
import com.createstats.blockentity.PowerLimiterBE;
import com.createstats.client.ModPartialModels;
import com.createstats.power.LimiterLadder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
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

@OnlyIn(Dist.CLIENT)
public class PowerLimiterRenderer implements BlockEntityRenderer<PowerLimiterBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float LIFT = 1.0f / 512.0f;

    private static final float X_WEST = 1.0f * PX - LIFT;
    private static final float X_EAST = 15.0f * PX + LIFT;
    private static final float Z0 = 3.7f * PX;
    private static final float Z1 = 12.7f * PX;
    private static final float Y0 = 3.7f * PX;
    private static final float Y1 = 9.7f * PX;

    private static final float DIAL_CX = 8.0f * PX;
    private static final float DIAL_CZ = 9.5f * PX;
    private static final float SWEEP_DEG = 135.0f;

    private static final int ARC_CELLS = 7;
    private static final float ARC_STEP_DEG = 2.0f * SWEEP_DEG / (ARC_CELLS - 1);

    public PowerLimiterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PowerLimiterBE be, float partialTick, PoseStack ps, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        final Direction facing = be.getBlockState().hasProperty(PowerLimiterBlock.FACING)
                ? be.getBlockState().getValue(PowerLimiterBlock.FACING)
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

        final Matrix4f mat = ps.last().pose();
        this.drawScreens(be, buffer, mat);
        this.drawControl(be, ps, buffer, packedLight);

        ps.popPose();
    }

    private void drawScreens(PowerLimiterBE be, MultiBufferSource buffer, Matrix4f mat) {
        final ResourceLocation texture = PowerLimiterTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));

        flat(vc, mat, X_WEST, Y1, Z0, 0.0f, 0.0f, -1.0f);
        flat(vc, mat, X_WEST, Y0, Z0, 0.0f, 1.0f, -1.0f);
        flat(vc, mat, X_WEST, Y0, Z1, 1.0f, 1.0f, -1.0f);
        flat(vc, mat, X_WEST, Y1, Z1, 1.0f, 0.0f, -1.0f);

        flat(vc, mat, X_EAST, Y1, Z1, 0.0f, 0.0f, 1.0f);
        flat(vc, mat, X_EAST, Y0, Z1, 0.0f, 1.0f, 1.0f);
        flat(vc, mat, X_EAST, Y0, Z0, 1.0f, 1.0f, 1.0f);
        flat(vc, mat, X_EAST, Y1, Z0, 1.0f, 0.0f, 1.0f);
    }

    private void drawControl(PowerLimiterBE be, PoseStack ps, MultiBufferSource buffer, int light) {
        final int notch = be.notch();
        final float fraction = LimiterLadder.fractionForNotch(notch);

        spin(CachedBuffers.partial(ModPartialModels.POWER_LIMITER_CAM, be.getBlockState()),
                -SWEEP_DEG + 2.0f * SWEEP_DEG * fraction)
                .light(light)
                .renderInto(ps, buffer.getBuffer(RenderType.solid()));

        if (notch <= 0) {
            spin(CachedBuffers.partial(ModPartialModels.POWER_LIMITER_ARC_SHUT, be.getBlockState()),
                    -SWEEP_DEG)
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ps, buffer.getBuffer(RenderType.solid()));
            return;
        }

        final int cells = Math.min(ARC_CELLS, 1 + Math.round(fraction * (ARC_CELLS - 1)));
        for (int i = 0; i < cells; i++) {
            spin(CachedBuffers.partial(ModPartialModels.POWER_LIMITER_ARC_LIT, be.getBlockState()),
                    -SWEEP_DEG + ARC_STEP_DEG * i)
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ps, buffer.getBuffer(RenderType.solid()));
        }
    }

    private static SuperByteBuffer spin(SuperByteBuffer part, float degrees) {
        return part.translate(DIAL_CX, 0.0f, DIAL_CZ)
                .rotate((float) Math.toRadians(-degrees), Direction.UP)
                .translate(-DIAL_CX, 0.0f, -DIAL_CZ);
    }

    private static void flat(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                             float u, float v, float normalX) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normalX, 0.0f, 0.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(PowerLimiterBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }
}
