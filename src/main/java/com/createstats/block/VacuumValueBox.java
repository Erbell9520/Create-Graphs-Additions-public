package com.createstats.block;

import com.createstats.vacuum.VacuumFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VacuumValueBox extends ValueBoxTransform {
    public enum Mount {
        TOP,

        FRONT
    }

    private static final double FACE_EPS = 0.1;

    private final double x;
    private final double y;
    private final double z;
    private final float boxScale;
    private final Mount mount;
    private final double face;

    public VacuumValueBox(double x, double y, double z, float boxScale, Mount mount, double face) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.boxScale = boxScale;
        this.mount = mount;
        this.face = face;

        this.scale = boxScale;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return VacuumFrame.horizontal(BlockPos.ZERO, facing(state), x, y, z);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        if (mount == Mount.FRONT) {
            TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(facing(state)) + 180.0f);
            return;
        }
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(facing(state))).rotateXDegrees(90.0f);
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        final Vec3 m = VacuumFrame.toHorizontal(facing(state), localHit).scale(16.0);
        return hits(m.x, m.y, m.z);
    }

    boolean hits(double mx, double my, double mz) {
        final double reach = boxScale * 16.0 / 2.0;
        if (mount == Mount.FRONT) {
            return mz <= face + FACE_EPS && Math.hypot(mx - x, my - y) < reach;
        }
        return my >= face - FACE_EPS && Math.hypot(mx - x, mz - z) < reach;
    }

    @Override
    public float getScale() {
        return boxScale;
    }
}
