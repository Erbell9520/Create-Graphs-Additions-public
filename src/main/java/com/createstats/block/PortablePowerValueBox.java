package com.createstats.block;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class PortablePowerValueBox extends ValueBoxTransform.Sided {
    private static final double INSET = 7.5 / 16.0;
    private static final double ALONG_FACING = -1.5 / 16.0;
    private static final double LATERAL = 3.5 / 16.0;

    private final int lateral;

    public PortablePowerValueBox(boolean right) {
        this.lateral = right ? 1 : -1;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return Vec3.ZERO;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        final Direction side = getSide();
        final Direction facing = facing(state);
        if (side.getAxis() == facing.getAxis()) {
            return null;
        }
        final Vec3 s = Vec3.atLowerCornerOf(side.getNormal());
        final Vec3 f = Vec3.atLowerCornerOf(facing.getNormal());
        final Vec3 t = f.cross(s);
        return new Vec3(0.5, 0.5, 0.5)
                .add(s.scale(INSET))
                .add(f.scale(ALONG_FACING))
                .add(t.scale(LATERAL * lateral));
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction.getAxis() != facing(state).getAxis();
    }

    @Override
    public float getScale() {
        return 0.4f;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
    }
}
