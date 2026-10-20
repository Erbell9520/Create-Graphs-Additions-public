package com.createstats.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FeMotorValueBoxTransform extends ValueBoxTransform {
    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return switch (facing(state)) {
            case SOUTH -> VecHelper.voxelSpace(4.9, 13.7, 10.7);
            case EAST -> VecHelper.voxelSpace(10.7, 13.7, 11.1);
            case WEST -> VecHelper.voxelSpace(5.3, 13.7, 4.9);
            case UP -> VecHelper.voxelSpace(11.1, 10.7, 13.7);
            case DOWN -> VecHelper.voxelSpace(11.1, 5.3, 2.3);
            default -> VecHelper.voxelSpace(11.1, 13.7, 5.3);
        };
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        final TransformStack tstack = TransformStack.of(ms);
        switch (facing(state)) {
            case UP -> {
            }
            case DOWN -> tstack.rotateYDegrees(180.0f);
            default -> tstack.rotateXDegrees(90.0f);
        }
    }

    @Override
    public float getScale() {
        return 0.45f;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(FeMotorBlock.FACING) ? state.getValue(FeMotorBlock.FACING) : Direction.NORTH;
    }
}
