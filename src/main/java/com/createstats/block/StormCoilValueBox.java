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

// constant getScale on purpose, see the value box scale trap
public class StormCoilValueBox extends ValueBoxTransform {
    public enum Slot {
        TARGETS(com.createstats.stormcoil.CoilCabinet.TARGETS_Z), HEIGHT(com.createstats.stormcoil.CoilCabinet.HEIGHT_Z);

        final double z;

        Slot(double z) {
            this.z = z;
        }
    }

    private final Slot slot;

    public StormCoilValueBox(Slot slot) {
        this.slot = slot;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(StormCoilBlock.FACING) ? state.getValue(StormCoilBlock.FACING) : Direction.NORTH;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        final Vec3 south = VecHelper.voxelSpace(16 - 16.1, com.createstats.stormcoil.CoilCabinet.BOX_Y, 16 - slot.z);
        return VecHelper.rotateCentered(south, AngleHelper.horizontalAngle(facing(state)), Direction.Axis.Y);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        final Direction side = facing(state).getClockWise();
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(side) + 180);
    }

    @Override
    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof StormCoilBlock && super.shouldRender(level, pos, state);
    }

    @Override
    public float getScale() {
        return (float) com.createstats.stormcoil.CoilCabinet.BOX_SCALE;
    }
}
