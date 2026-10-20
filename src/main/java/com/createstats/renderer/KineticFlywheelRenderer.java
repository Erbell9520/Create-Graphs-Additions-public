package com.createstats.renderer;

import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.client.ModPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;

public class KineticFlywheelRenderer extends KineticBlockEntityRenderer<KineticFlywheelBE> {
    private static final float ROTOR_BAND_CENTRE = 6.5f / 16f;
    private static final float ROTOR_BAND_DEPTH = 7.0f / 16f;

    private static final int TINT_CHARGING = 0xFF5AC8FF;
    private static final int TINT_DISCHARGING = 0xFFFFB44E;
    private static final int TINT_IDLE = 0xFF6FBF7A;
    private static final int TINT_HALTED = 0xFFFF4A3C;

    public KineticFlywheelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(KineticFlywheelBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IRotate)) {
            return;
        }
        Direction facing = facingOf(be);
        Direction.Axis axis = facing.getAxis();

        renderConnectionShaft(be, state, facing, axis, ms, buffer, light);
        renderRotor(be, state, facing, axis, partialTicks, ms, buffer, light);
        renderGauge(be, state, facing, ms, buffer, light);
    }

    private void renderConnectionShaft(KineticFlywheelBE be, BlockState state, Direction facing, Direction.Axis axis,
                                       PoseStack ms, MultiBufferSource buffer, int light) {
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        float angle = (time * be.getSpeed() * 3.0f / 10.0f) % 360.0f;
        angle += offset;
        angle = angle / 180.0f * (float) Math.PI;

        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing);
        kineticRotationTransform(shaft, be, axis, angle, light);
        shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderRotor(KineticFlywheelBE be, BlockState state, Direction facing, Direction.Axis axis,
                             float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        float angle = (be.getRenderAngle(partialTicks) % 360.0f) / 180.0f * (float) Math.PI;

        int count = Math.max(1, be.getFlywheelCount());
        float slot = ROTOR_BAND_DEPTH / count;
        float thickness = count == 1 ? 1.0f : Math.min(1.0f, 0.78f / count);

        boolean xAxis = axis == Direction.Axis.X;
        boolean yAxis = axis == Direction.Axis.Y;
        boolean zAxis = axis == Direction.Axis.Z;

        for (int i = 0; i < count; i++) {
            float discCentre = (ROTOR_BAND_CENTRE - ROTOR_BAND_DEPTH / 2f) + (i + 0.5f) * slot;
            float axisDelta = discCentre - ROTOR_BAND_CENTRE;

            ms.pushPose();

            ms.translate(xAxis ? axisDelta : 0.0, yAxis ? axisDelta : 0.0, zAxis ? axisDelta : 0.0);
            ms.translate(0.5, 0.5, 0.5);
            ms.scale(xAxis ? thickness : 1.0f, yAxis ? thickness : 1.0f, zAxis ? thickness : 1.0f);
            ms.translate(-0.5, -0.5, -0.5);

            SuperByteBuffer rotor = CachedBuffers.partialFacing(ModPartialModels.FLYWHEEL_WHEEL, state, facing);
            kineticRotationTransform(rotor, be, axis, angle, light);
            rotor.renderInto(ms, buffer.getBuffer(RenderType.solid()));
            ms.popPose();
        }
    }

    private void renderGauge(KineticFlywheelBE be, BlockState state, Direction facing,
                             PoseStack ms, MultiBufferSource buffer, int light) {
        int total = ModPartialModels.FLYWHEEL_GAUGE.length;
        int filled = Mth.clamp(Math.round(be.getChargePercent() / 100f * total), 0, total);

        if (filled == 0 && be.getChargePercent() > 0.5f) {
            filled = 1;
        }
        if (filled == 0) {
            return;
        }

        int tint = tintFor(be);

        int emissive = LightTexture.pack(15, LightTexture.sky(light));

        for (int i = 0; i < filled; i++) {
            SuperByteBuffer lens = CachedBuffers.partialFacing(ModPartialModels.FLYWHEEL_GAUGE[i], state, facing);
            lens.color(tint);
            lens.light(emissive);
            lens.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    private static int tintFor(KineticFlywheelBE be) {
        if (be.isOverstressHalted()) {
            return TINT_HALTED;
        }
        return switch (be.getMode()) {
            case CHARGING -> TINT_CHARGING;
            case DISCHARGING -> TINT_DISCHARGING;
            case IDLE -> TINT_IDLE;
        };
    }

    private static Direction facingOf(KineticFlywheelBE be) {
        BlockState state = be.getBlockState();
        if (state.hasProperty(com.createstats.block.KineticFlywheelBlock.FACING)) {
            return state.getValue(com.createstats.block.KineticFlywheelBlock.FACING);
        }
        return Direction.NORTH;
    }
}
