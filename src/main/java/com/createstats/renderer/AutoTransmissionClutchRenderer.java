package com.createstats.renderer;

import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.client.ModPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import com.mojang.blaze3d.vertex.PoseStack;

public class AutoTransmissionClutchRenderer extends KineticBlockEntityRenderer<AutoTransmissionClutchBE> {

    private static final float NEEDLE_PIVOT_X = 8.0f / 16.0f;
    private static final float NEEDLE_PIVOT_Z = 8.0f / 16.0f;
    private static final float NEEDLE_START_ANGLE = (float) (Math.PI * 5.0 / 4.0);

    public AutoTransmissionClutchRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AutoTransmissionClutchBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        renderShaftHalves(be, ms, buffer, light);
        renderNeedle(be, partialTicks, ms, buffer, light);
    }

    private void renderShaftHalves(AutoTransmissionClutchBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        Block block = be.getBlockState().getBlock();
        Axis boxAxis = ((IRotate) block).getRotationAxis(be.getBlockState());
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());

        for (Direction direction : Iterate.directions) {
            Axis axis = direction.getAxis();
            if (boxAxis != axis) {
                continue;
            }

            float offset = getRotationOffsetForPosition(be, pos, axis);
            float angle = (time * be.getSpeed() * 3.0f / 10.0f) % 360.0f;
            float modifier = be.getRotationSpeedModifier(direction);
            angle *= modifier;
            angle += offset;
            angle = angle / 180.0f * (float) Math.PI;

            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), direction);
            kineticRotationTransform(shaft, be, axis, angle, light);
            shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    private void renderNeedle(AutoTransmissionClutchBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        float progress = Mth.lerp(partialTicks, be.prevDialState, be.dialState);
        float angle = NEEDLE_START_ANGLE - NeedlePhysics.SWEEP_RADIANS * progress;

        SuperByteBuffer needle = CachedBuffers.partial(ModPartialModels.AUTO_TRANSMISSION_CLUTCH_GAUGE_NEEDLE, be.getBlockState());
        needle.translate(NEEDLE_PIVOT_X, 0.0f, NEEDLE_PIVOT_Z)
            .rotate(angle, Direction.UP)
            .translate(-NEEDLE_PIVOT_X, 0.0f, -NEEDLE_PIVOT_Z)
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
