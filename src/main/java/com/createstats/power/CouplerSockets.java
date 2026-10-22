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
        return turn(end == End.FOREIGN ? FOREIGN_NORTH : GRID_NORTH, facing);
    }

    private static final AABB FOREIGN_NORTH = new AABB(0.671875, 0.78125, 0.015625, 0.828125, 0.96875, 0.140625);
    private static final Vec3 FOREIGN_ANCHOR_NORTH = new Vec3(0.75, 0.953125, 0.078125);

    private static final AABB GRID_NORTH = new AABB(0.671875, 0.78125, 0.859375, 0.828125, 0.9375, 0.984375);
    private static final Vec3 GRID_ANCHOR_NORTH = new Vec3(0.75, 0.921875, 0.921875);

    private static final double HIT_SLACK = 1.0 / 16;

    public static AABB turn(AABB box, Direction facing) {
        return switch (facing) {
            case SOUTH -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
            case EAST -> new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
            case WEST -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
            default -> box;
        };
    }

    public static Vec3 turn(Vec3 p, Direction facing) {
        return switch (facing) {
            case SOUTH -> new Vec3(1 - p.x, p.y, 1 - p.z);
            case EAST -> new Vec3(1 - p.z, p.y, p.x);
            case WEST -> new Vec3(p.z, p.y, 1 - p.x);
            default -> p;
        };
    }

    @Nullable
    public static End endAt(@Nullable Direction facing, @Nullable Vec3 localHit, @Nullable Direction face) {
        if (facing != null && localHit != null) {
            if (local(facing, End.FOREIGN).inflate(HIT_SLACK).contains(localHit)) {
                return End.FOREIGN;
            }
            if (local(facing, End.GRID).inflate(HIT_SLACK).contains(localHit)) {
                return End.GRID;
            }
        }
        return endOn(facing, face);
    }

    @Nullable
    public static End endAt(@Nullable BlockState state, BlockPos pos, @Nullable Vec3 hit, @Nullable Direction face) {
        final Vec3 local = hit == null ? null : hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        return endAt(facingOf(state), local, face);
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

    public static End endFor(@Nullable net.minecraft.world.level.BlockGetter level, BlockPos couplerPos,
                             BlockPos far) {
        final net.minecraft.world.level.block.entity.BlockEntity be =
                level == null ? null : level.getBlockEntity(couplerPos);
        if (be instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler
                && coupler.links().contains(far)) {
            return coupler.socketOf(far);
        }
        return endToward(level == null ? null : level.getBlockState(couplerPos), couplerPos, far);
    }

    public static End endOf(boolean seamFlag) {
        return seamFlag ? End.FOREIGN : End.GRID;
    }

    public static final String SEAM_ONLY = "message.create_stats.copper_wire.seam_only";

    public static final String FOREIGN_WANTS_SEAM = "message.create_stats.coupler.foreign_wants_seam";

    public static final String GRID_WANTS_OURS = "message.create_stats.coupler.grid_wants_ours";

    @Nullable
    public static String refusalKey(@Nullable End socket, boolean farForeign, boolean foreignFirst) {
        if (socket == null) {
            return null;
        }
        if (socket == End.FOREIGN) {
            return farForeign ? null : SEAM_ONLY;
        }
        if (!farForeign) {
            return null;
        }
        return foreignFirst ? FOREIGN_WANTS_SEAM : GRID_WANTS_OURS;
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
        return turn(end == End.FOREIGN ? FOREIGN_ANCHOR_NORTH : GRID_ANCHOR_NORTH, facing);
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
