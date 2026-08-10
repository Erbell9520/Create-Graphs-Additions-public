package com.createstats.renderer;

import com.createstats.block.FeMotorBlock;
import com.createstats.blockentity.FeMotorBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** [1] */
public class FeMotorRenderer extends KineticBlockEntityRenderer<FeMotorBE> {

    public FeMotorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FeMotorBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IRotate)) {
            return;
        }
        final Direction facing = state.getValue(FeMotorBlock.FACING);
        renderShaft(be, state, facing, facing.getAxis(), ms, buffer, light);
        renderWires(be, ms, buffer, light);
    }

    private void renderShaft(FeMotorBE be, BlockState state, Direction facing, Direction.Axis axis,
                             PoseStack ms, MultiBufferSource buffer, int light) {
        final BlockPos pos = be.getBlockPos();
        final float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float angle = (time * be.getSpeed() * 3.0f / 10.0f) % 360.0f;
        angle += getRotationOffsetForPosition(be, pos, axis);
        angle = angle / 180.0f * (float) Math.PI;

        final SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing);
        kineticRotationTransform(shaft, be, axis, angle, light);
        shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderWires(FeMotorBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
        final Matrix4f matrix = ms.last().pose();
        final Vec3 from = studAttachPoint(be.getBlockState());
        for (final BlockPos link : be.links()) {
            // Each pair is drawn once, by the terminal with the smaller packed position.
            if (link.asLong() <= origin.asLong()) {
                continue;
            }
            // Both ends resolved, so a cable lands on a real terminal at each end rather than
            // sprouting from the middle of a block at one of them.
            final Vec3 to = be.getLevel() == null
                    ? PowerConnectorRenderer.attachPoint(null)
                    : PowerConnectorRenderer.attachPoint(be.getLevel().getBlockState(link));
            PowerConnectorRenderer.drawCable(consumer, matrix,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    /** [2] */
    public static Vec3 studAttachPoint(@org.jetbrains.annotations.Nullable BlockState state) {
        if (state == null || !(state.getBlock() instanceof FeMotorBlock)) {
            return new Vec3(0.5, 0.5, 0.5);
        }
        return switch (state.getValue(FeMotorBlock.FACING)) {
            case SOUTH -> new Vec3(0.1219, 0.7656, 0.6625);
            case EAST -> new Vec3(0.6625, 0.7656, 0.8781);
            case WEST -> new Vec3(0.3375, 0.7656, 0.1219);
            case UP -> new Vec3(0.8781, 0.6625, 0.7656);
            case DOWN -> new Vec3(0.8781, 0.3375, 0.2344);
            default -> new Vec3(0.8781, 0.7656, 0.3375);
        };
    }
}
