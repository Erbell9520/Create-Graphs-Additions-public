package com.createstats.security;

import com.createstats.block.BlastShutterBlock;
import com.createstats.block.ShutterSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ShutterBank {
    private ShutterBank() {
    }

    public static final int MAX_WIDTH = 32;

    public static Direction widthAxis(Direction facing) {
        return facing.getClockWise();
    }

    public static List<BlockPos> membersOf(BlockGetter level, BlockPos lower, BlockState state) {
        final List<BlockPos> out = new ArrayList<>(4);
        if (!(state.getBlock() instanceof BlastShutterBlock block)
                || state.getValue(BlastShutterBlock.HALF) != ShutterSegment.LOWER) {
            return out;
        }
        final Direction facing = state.getValue(BlastShutterBlock.FACING);
        final Direction axis = widthAxis(facing);
        final int height = heightAt(level, lower);

        out.add(lower.immutable());
        walk(level, lower, axis, facing, height, block, out);
        walk(level, lower, axis.getOpposite(), facing, height, block, out);
        out.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        return out;
    }

    private static void walk(BlockGetter level, BlockPos from, Direction step, Direction facing,
                             int height, BlastShutterBlock block, List<BlockPos> out) {
        BlockPos at = from.relative(step);
        while (out.size() < MAX_WIDTH) {
            final BlockState state = level.getBlockState(at);
            if (!state.is(block)
                    || state.getValue(BlastShutterBlock.HALF) != ShutterSegment.LOWER
                    || state.getValue(BlastShutterBlock.FACING) != facing
                    || heightAt(level, at) != height) {
                return;
            }
            out.add(at.immutable());
            at = at.relative(step);
        }
    }

    private static int heightAt(BlockGetter level, BlockPos lower) {
        return level.getBlockEntity(lower)
                instanceof com.createstats.blockentity.BlastShutterBE unit
                ? unit.columnHeight() : BlastShutterBlock.MIN_HEIGHT;
    }

    public static BlockPos anchorOf(BlockGetter level, BlockPos lower, BlockState state) {
        final List<BlockPos> members = membersOf(level, lower, state);
        return members.isEmpty() ? lower.immutable() : members.get(0);
    }

    public static boolean isAnchor(BlockGetter level, BlockPos lower, BlockState state) {
        return anchorOf(level, lower, state).equals(lower);
    }
}
