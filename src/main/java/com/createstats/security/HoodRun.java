package com.createstats.security;

import com.createstats.block.GarageDoorHeaderBlock;
import com.createstats.block.GarageDoorSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class HoodRun {
    private HoodRun() {
    }

    public static final int MAX_WIDTH = 12;

    public static BlockPos anchorOf(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof GarageDoorHeaderBlock)
                || !state.hasProperty(GarageDoorHeaderBlock.FACING)) {
            return pos.immutable();
        }
        final Direction facing = state.getValue(GarageDoorHeaderBlock.FACING);
        final Direction axis = GarageDoorSite.widthAxis(facing);

        BlockPos best = pos.immutable();
        best = lower(best, sweep(level, pos, axis, facing, MAX_WIDTH));
        best = lower(best, sweep(level, pos, axis.getOpposite(), facing, MAX_WIDTH));
        return best;
    }

    public static boolean isAnchor(BlockGetter level, BlockPos pos, BlockState state) {
        return anchorOf(level, pos, state).equals(pos);
    }

    private static BlockPos sweep(BlockGetter level, BlockPos from, Direction step,
                                  Direction facing, int limit) {
        BlockPos best = from.immutable();
        BlockPos at = from.relative(step);
        for (int i = 1; i < limit; i++) {
            final BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof GarageDoorHeaderBlock)
                    || !state.hasProperty(GarageDoorHeaderBlock.FACING)
                    || state.getValue(GarageDoorHeaderBlock.FACING) != facing) {
                return best;
            }
            best = lower(best, at.immutable());
            at = at.relative(step);
        }
        return best;
    }

    private static BlockPos lower(BlockPos a, BlockPos b) {
        return b.asLong() < a.asLong() ? b : a;
    }
}
