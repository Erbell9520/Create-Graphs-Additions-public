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

public class LoadBalancerValueBoxTransform extends ValueBoxTransform {
    private static Direction frontOf(BlockState state) {
        if (state.getBlock() instanceof LoadBalancerBlock) {
            return LoadBalancerBlock.getFrontFacing(state);
        }
        return Direction.NORTH;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction side = frontOf(state);

        Vec3 location = VecHelper.voxelSpace(8, 8, 15.5);
        location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(side), Axis.Y);
        return location;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(frontOf(state)) + 180);
    }

    @Override
    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof LoadBalancerBlock && super.shouldRender(level, pos, state);
    }

    @Override
    public float getScale() {
        return 0.45f;
    }
}
