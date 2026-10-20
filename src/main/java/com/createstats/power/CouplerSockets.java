package com.createstats.power;

import com.createstats.block.UniversalPowerCouplerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class CouplerSockets {
    public enum End {
        FOREIGN,

        GRID
    }

    private CouplerSockets() {
    }

    @Nullable
    public static AABB local(@Nullable BlockState state, End end) {
        final Direction facing = facingOf(state);
        return facing == null ? null : local(facing, end);
    }

    public static AABB local(Direction facing, End end) {
        if (end == End.FOREIGN) {
            return switch (facing) {
                case SOUTH -> new AABB(0.38750, 0.10625, 0.92500, 0.61250, 0.35000, 1.06250);
                case EAST -> new AABB(0.92500, 0.10625, 0.38750, 1.06250, 0.35000, 0.61250);
                case WEST -> new AABB(-0.06250, 0.10625, 0.38750, 0.07500, 0.35000, 0.61250);
                default -> new AABB(0.38750, 0.10625, -0.06250, 0.61250, 0.35000, 0.07500);
            };
        }
        return switch (facing) {
            case SOUTH -> new AABB(0.35625, 0.09375, -0.02500, 0.64375, 0.36875, 0.13125);
            case EAST -> new AABB(-0.02500, 0.09375, 0.35625, 0.13125, 0.36875, 0.64375);
            case WEST -> new AABB(0.86875, 0.09375, 0.35625, 1.02500, 0.36875, 0.64375);
            default -> new AABB(0.35625, 0.09375, 0.86875, 0.64375, 0.36875, 1.02500);
        };
    }

    @Nullable
    public static End endOn(@Nullable BlockState state, @Nullable Direction face) {
        return endOn(facingOf(state), face);
    }

    @Nullable
    public static End endOn(@Nullable Direction facing, @Nullable Direction face) {
        if (facing == null || face == null) {
            return null;
        }
        if (face == facing) {
            return End.FOREIGN;
        }
        return face == facing.getOpposite() ? End.GRID : null;
    }

    public static End endToward(@Nullable BlockState state, BlockPos couplerPos, BlockPos far) {
        return endToward(facingOf(state), couplerPos, far);
    }

    public static End endToward(@Nullable Direction facing, BlockPos couplerPos, BlockPos far) {
        if (facing == null) {
            return End.FOREIGN;
        }
        final int along = (far.getX() - couplerPos.getX()) * facing.getStepX()
                + (far.getY() - couplerPos.getY()) * facing.getStepY()
                + (far.getZ() - couplerPos.getZ()) * facing.getStepZ();
        return along > 0 ? End.FOREIGN : End.GRID;
    }

    @Nullable
    public static AABB world(@Nullable BlockState state, BlockPos pos, End end) {
        final AABB box = local(state, end);
        return box == null ? null : box.move(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 anchor(@Nullable BlockState state, End end) {
        final Direction facing = facingOf(state);
        return facing == null ? new Vec3(0.5, 0.5, 0.5) : anchor(facing, end);
    }

    public static Vec3 anchor(Direction facing, End end) {
        final Direction out = end == End.FOREIGN ? facing : facing.getOpposite();

        final double y = end == End.FOREIGN ? 0.22812 : 0.23125;
        return switch (out) {
            case NORTH -> new Vec3(0.50000, y, -0.01875);
            case SOUTH -> new Vec3(0.50000, y, 1.01875);
            case EAST -> new Vec3(1.01875, y, 0.50000);
            case WEST -> new Vec3(-0.01875, y, 0.50000);
            default -> new Vec3(0.5, y, 0.5);
        };
    }

    @Nullable
    private static Direction facingOf(@Nullable BlockState state) {
        if (state == null || !(state.getBlock() instanceof UniversalPowerCouplerBlock)
                || !state.hasProperty(UniversalPowerCouplerBlock.FACING)) {
            return null;
        }
        return state.getValue(UniversalPowerCouplerBlock.FACING);
    }
}
