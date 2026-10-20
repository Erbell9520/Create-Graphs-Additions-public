package com.createstats.renderer;

import com.createstats.client.ModPartialModels;
import com.createstats.vccmk3.VccMk3Block;
import com.createstats.vccmk3.VccMk3BlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

// the world moves, the aircraft symbol doesn't
public class VccMk3Renderer implements BlockEntityRenderer<VccMk3BlockEntity> {
    private static final float RAKE_DEGREES = -22.5f;
    private static final float HINGE_X = 8f / 16f;
    private static final float HINGE_Y = 6.5f / 16f;
    private static final float HINGE_Z = 5.5f / 16f;
    private static final float PFD_X = 5.5f / 16f;
    private static final float PFD_Y = 10.25f / 16f;
    private static final float PFD_Z = 5.03f / 16f;
    private static final float PFD_HALF_W = 2.5f / 16f;
    private static final float PFD_HALF_H = 2.75f / 16f;
    private static final float TRACK_X0 = 2.75f / 16f;
    private static final float TRACK_X1 = 13.25f / 16f;
    private static final float TRACK_Y = 6.75f / 16f;
    private static final float TRACK_Z = 10f / 16f;

    private static final float RAKE_RADIANS = (float) Math.toRadians(RAKE_DEGREES);

    private static final float BAR_HALF_LENGTH = 2.3f / 16f;

    private static final float PITCH_FULL_SCALE = 20.0f;

    public VccMk3Renderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VccMk3BlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        boolean armed = state.hasProperty(VccMk3Block.ARMED) && state.getValue(VccMk3Block.ARMED);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(facingDegrees(state)));
        ms.translate(-0.5, -0.5, -0.5);

        if (armed) {
            renderAttitude(be, partialTick, ms, buffer);
        }
        renderShuttle(be, partialTick, ms, buffer, light, armed);

        ms.popPose();
    }

    private static float facingDegrees(BlockState state) {
        if (!state.hasProperty(VccMk3Block.FACING)) return 0.0f;
        return switch (state.getValue(VccMk3Block.FACING)) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
    }

    private void renderAttitude(VccMk3BlockEntity be, float partialTick,
                                PoseStack ms, MultiBufferSource buffer) {
        boolean measured = be.hasAttitude();
        PartialModel model = measured ? ModPartialModels.VCC_MK3_HORIZON_BAR : ModPartialModels.VCC_MK3_ATT_FLAG;
        SuperByteBuffer part = CachedBuffers.partial(model, be.getBlockState());

        part.translate(HINGE_X, HINGE_Y, HINGE_Z)
                .rotate(RAKE_RADIANS, Direction.EAST)
                .translate(PFD_X - HINGE_X, PFD_Y - HINGE_Y, PFD_Z - HINGE_Z);

        if (measured) {
            float roll = be.renderRoll(partialTick);
            float pitch = be.renderPitch(partialTick);

            float rollRadians = (float) Math.toRadians(roll);
            float reach = BAR_HALF_LENGTH * Mth.abs(Mth.sin(rollRadians));
            float room = Math.max(0.0f, PFD_HALF_H - reach - 1.0f / 32.0f);
            float slide = Mth.clamp(pitch / PITCH_FULL_SCALE, -1.0f, 1.0f) * room;

            part.rotate(rollRadians, Direction.SOUTH).translate(0.0f, -slide, 0.0f);
        } else {
            part.translate(-PFD_HALF_W * 0.35f, -PFD_HALF_H * 0.45f, 0.0f);
        }

        part.light(LightTexture.FULL_BRIGHT).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderShuttle(VccMk3BlockEntity be, float partialTick, PoseStack ms,
                               MultiBufferSource buffer, int light, boolean armed) {
        PartialModel model;
        if (!armed) {
            model = ModPartialModels.VCC_MK3_SHUTTLE_IDLE;
        } else {
            model = be.isRenderAuto() ? ModPartialModels.VCC_MK3_SHUTTLE_AUTO
                    : ModPartialModels.VCC_MK3_SHUTTLE_MANUAL;
        }

        float command = armed ? Mth.clamp(be.renderCommand(partialTick), -1.0f, 1.0f) : 0.0f;
        float centre = (TRACK_X0 + TRACK_X1) * 0.5f;
        float x = centre + command * (TRACK_X1 - TRACK_X0) * 0.5f;

        CachedBuffers.partial(model, be.getBlockState())
                .translate(x, TRACK_Y, TRACK_Z)
                .light(armed ? LightTexture.FULL_BRIGHT : light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    @Override
    public boolean shouldRenderOffScreen(VccMk3BlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
