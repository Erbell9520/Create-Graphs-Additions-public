package com.createstats.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class StarterModeValueBox extends ValueBoxTransform {
    private static Direction frontOf(BlockState state) {
        return state.hasProperty(StarterCapacitorBlock.FACING) ? state.getValue(StarterCapacitorBlock.FACING)
                : Direction.NORTH;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        final Vec3 location = VecHelper.voxelSpace(12, 3.25, 14.75);
        return VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(frontOf(state)), Direction.Axis.Y);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(frontOf(state)) + 180);
    }

    @Override
    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof StarterCapacitorBlock && super.shouldRender(level, pos, state);
    }

    @Override
    public float getScale() {
        return 0.4f;
    }
}
