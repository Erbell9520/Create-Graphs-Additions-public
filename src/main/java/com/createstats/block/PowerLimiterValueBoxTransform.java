package com.createstats.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PowerLimiterValueBoxTransform extends ValueBoxTransform {
    private static final Vec3 ON_DIAL = VecHelper.voxelSpace(8.0, 15.55, 9.5);

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return VecHelper.rotateCentered(ON_DIAL, yaw(state), Axis.Y);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        TransformStack.of(ms)
                .rotateYDegrees(yaw(state))
                .rotateXDegrees(90.0f);
    }

    private static float yaw(BlockState state) {
        final Direction facing = state.hasProperty(PowerLimiterBlock.FACING)
                ? state.getValue(PowerLimiterBlock.FACING) : Direction.NORTH;
        return AngleHelper.horizontalAngle(facing.getOpposite());
    }

    @Override
    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof PowerLimiterBlock && super.shouldRender(level, pos, state);
    }

    @Override
    public float getScale() {
        return 0.45f;
    }
}
