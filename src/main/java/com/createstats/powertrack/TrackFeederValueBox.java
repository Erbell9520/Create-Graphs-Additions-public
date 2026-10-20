package com.createstats.powertrack;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TrackFeederValueBox extends ValueBoxTransform.Sided {
    public static final double[][] WINDOWS = com.createstats.powertrack.core.FeederMeter.WINDOWS;

    private static final double DOOR_Z = 4.0 - 0.1;
    private static final float SCALE = 0.4f;

    private final double x;
    private final double y;

    public TrackFeederValueBox(boolean dial) {
        final double[] w = WINDOWS[dial ? 0 : 1];
        this.x = w[0];
        this.y = w[1];

        this.scale = SCALE;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return Vec3.ZERO;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return turn(facing(state), x / 16.0, y / 16.0, DOOR_Z / 16.0);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction == facing(state);
    }

    @Override
    public float getScale() {
        return SCALE;
    }

    static Vec3 turn(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case EAST -> new Vec3(1.0 - z, y, x);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }
}
