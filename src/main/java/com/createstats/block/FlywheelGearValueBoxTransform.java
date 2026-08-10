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

/** [1] */
public class FlywheelGearValueBoxTransform extends ValueBoxTransform {

    /** True = upper box (charge gear); false = lower box (discharge gear). */
    private final boolean upper;

    public FlywheelGearValueBoxTransform(boolean upper) {
        this.upper = upper;
    }

    private static Direction rearOf(BlockState state) {
        if (state.getBlock() instanceof KineticFlywheelBlock) {
            return state.getValue(KineticFlywheelBlock.FACING).getOpposite();
        }
        return Direction.NORTH;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction side = rearOf(state);
        Vec3 location = VecHelper.voxelSpace(8, upper ? 11 : 5, 15.5);
        location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(side), Axis.Y);
        location = VecHelper.rotateCentered(location, AngleHelper.verticalAngle(side), Axis.X);
        return location;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction side = rearOf(state);
        float yRot = AngleHelper.horizontalAngle(side) + 180;
        float xRot = side == Direction.UP ? 90 : side == Direction.DOWN ? 270 : 0;
        TransformStack.of(ms)
            .rotateYDegrees(yRot)
            .rotateXDegrees(xRot);
    }

    @Override
    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof KineticFlywheelBlock && super.shouldRender(level, pos, state);
    }

    @Override
    public float getScale() {
        return 0.45f;
    }
}
