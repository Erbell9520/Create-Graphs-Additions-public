package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GarageDoorSite {
    private GarageDoorSite() {
    }

    public static Direction widthAxis(Direction facing) {
        return facing.getClockWise();
    }

    public static int axisCoord(BlockPos pos, Direction axis) {
        return switch (axis) {
            case EAST -> pos.getX();
            case WEST -> -pos.getX();
            case SOUTH -> pos.getZ();
            case NORTH -> -pos.getZ();
            default -> 0;
        };
    }

    public static BlockPos posOf(BlockPos reference, Direction axis, int h, int v) {
        final BlockPos moved = reference.relative(axis, h - axisCoord(reference, axis));
        return new BlockPos(moved.getX(), v, moved.getZ());
    }

    private record WorldProbe(LevelReader level, BlockPos reference, Direction axis, Direction facing,
                              @Nullable BlockPos ghostHood)
            implements GarageDoorFrame.Probe {
        private BlockState at(int h, int v) {
            return level.getBlockState(posOf(reference, axis, h, v));
        }

        private boolean facingMatches(BlockState state) {
            return state.hasProperty(HorizontalDirectionalBlock.FACING)
                    && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
        }

        @Override
        public boolean hood(int h, int v) {
            if (ghostHood != null && ghostHood.equals(posOf(reference, axis, h, v))) {
                return true;
            }
            final BlockState state = at(h, v);
            return state.getBlock() instanceof GarageDoorHeaderBlock && facingMatches(state);
        }

        @Override
        public boolean clear(int h, int v) {
            final BlockState state = at(h, v);
            if (state.getBlock() instanceof GarageDoorBodyBlock) {
                return facingMatches(state);
            }
            return state.isAir() || state.canBeReplaced();
        }
    }

    public record Detected(GarageDoorFrame.Result frame, BlockPos reference, Direction axis,
                           Direction facing) {
        public boolean ok() {
            return frame.ok();
        }

        public BlockPos at(int h, int v) {
            return posOf(reference, axis, h, v);
        }

        public BlockPos controllerPos() {
            return at(frame.left(), frame.headerV());
        }

        public BlockPos originPos() {
            return at(frame.left(), frame.headerV());
        }

        public int columnOf(BlockPos hood) {
            return axisCoord(hood, axis) - frame.left();
        }

        public List<BlockPos> hoods() {
            final List<BlockPos> out = new ArrayList<>();
            for (int h = frame.left(); h <= frame.right(); h++) {
                out.add(at(h, frame.headerV()));
            }
            return out;
        }

        public List<BlockPos> body() {
            final List<BlockPos> out = new ArrayList<>();
            for (int v = frame.bottomV(); v <= frame.topV(); v++) {
                for (int h = frame.interiorMinH(); h <= frame.interiorMaxH(); h++) {
                    out.add(at(h, v));
                }
            }
            return out;
        }

        public List<BlockPos> bodyRow(int row) {
            final List<BlockPos> out = new ArrayList<>();
            final int v = frame.bottomV() + row;
            for (int h = frame.interiorMinH(); h <= frame.interiorMaxH(); h++) {
                out.add(at(h, v));
            }
            return out;
        }
    }

    public static Detected detect(LevelReader level, BlockPos hoodPos) {
        final BlockState state = level.getBlockState(hoodPos);
        if (!(state.getBlock() instanceof GarageDoorHeaderBlock)
                || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return new Detected(GarageDoorFrame.Result.fail(GarageDoorFrame.Fault.NOT_A_HOOD),
                    hoodPos, Direction.EAST, Direction.NORTH);
        }
        return detect(level, hoodPos, state.getValue(HorizontalDirectionalBlock.FACING), null);
    }

    public static Detected detect(LevelReader level, BlockPos hoodPos, Direction facing,
                                  @Nullable BlockPos ghostHood) {
        final Direction axis = widthAxis(facing);
        final WorldProbe probe = new WorldProbe(level, hoodPos, axis, facing, ghostHood);
        final GarageDoorFrame.Result frame =
                GarageDoorFrame.detect(probe, axisCoord(hoodPos, axis), hoodPos.getY());
        return new Detected(frame, hoodPos, axis, facing);
    }

    public static Detected form(Level level, BlockPos hoodPos) {
        final Detected site = detect(level, hoodPos);
        if (level.isClientSide() || !site.ok()) {
            return site;
        }
        final BlockPos controller = electController(level, site);

        final BlockState curtain = com.createstats.registry.ModBlocks.GARAGE_DOOR_BODY.getDefaultState()
                .setValue(HorizontalDirectionalBlock.FACING, site.facing())
                .setValue(GarageDoorBodyBlock.OPEN, false);
        for (BlockPos pos : site.body()) {
            if (!level.getBlockState(pos).is(curtain.getBlock())) {
                level.setBlock(pos, curtain, Block.UPDATE_ALL);
            }
        }

        for (BlockPos pos : site.hoods()) {
            final BlockState hood = level.getBlockState(pos);
            if (!(hood.getBlock() instanceof GarageDoorHeaderBlock)) {
                continue;
            }
            final boolean wanted = pos.equals(controller);
            if (hood.getValue(GarageDoorHeaderBlock.CONTROLLER) != wanted) {
                level.setBlock(pos, hood.setValue(GarageDoorHeaderBlock.CONTROLLER, wanted),
                        Block.UPDATE_ALL);
            }
        }
        return site;
    }

    // keep the existing controller. electing fresh when you widen leftwards threw away the buffer and the tier
    private static BlockPos electController(Level level, Detected site) {
        for (BlockPos pos : site.hoods()) {
            final BlockState hood = level.getBlockState(pos);
            if (hood.getBlock() instanceof GarageDoorHeaderBlock
                    && hood.getValue(GarageDoorHeaderBlock.CONTROLLER)) {
                return pos;
            }
        }
        return site.controllerPos();
    }

    public static void unform(Level level, Detected site) {
        if (level.isClientSide() || !site.ok()) {
            return;
        }
        clearCurtain(level, site.body());
        for (BlockPos pos : site.hoods()) {
            final BlockState hood = level.getBlockState(pos);
            if (hood.getBlock() instanceof GarageDoorHeaderBlock
                    && hood.getValue(GarageDoorHeaderBlock.CONTROLLER)) {
                level.setBlock(pos, hood.setValue(GarageDoorHeaderBlock.CONTROLLER, false),
                        Block.UPDATE_ALL);
            }
        }
    }

    public static void clearCurtain(Level level, Iterable<BlockPos> body) {
        if (level.isClientSide()) {
            return;
        }
        for (BlockPos pos : body) {
            if (level.getBlockState(pos).getBlock() instanceof GarageDoorBodyBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
            }
        }
    }

    @Nullable
    public static BlockPos controllerNear(BlockGetter level, BlockPos hoodPos, Direction facing) {
        final Direction axis = widthAxis(facing);
        if (isController(level, hoodPos, facing)) {
            return hoodPos;
        }

        for (int sign = -1; sign <= 1; sign += 2) {
            for (int step = 1; step <= GarageDoorFrame.MAX_WIDTH; step++) {
                final BlockPos probe = hoodPos.relative(axis, sign * step);
                final BlockState state = level.getBlockState(probe);
                if (!(state.getBlock() instanceof GarageDoorHeaderBlock)
                        || state.getValue(HorizontalDirectionalBlock.FACING) != facing) {
                    break;
                }
                if (state.getValue(GarageDoorHeaderBlock.CONTROLLER)) {
                    return probe;
                }
            }
        }
        return null;
    }

    private static boolean isController(BlockGetter level, BlockPos pos, Direction facing) {
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof GarageDoorHeaderBlock
                && state.getValue(GarageDoorHeaderBlock.CONTROLLER)
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }
}
