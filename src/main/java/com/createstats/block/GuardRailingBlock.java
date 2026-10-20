package com.createstats.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class GuardRailingBlock extends CrossCollisionBlock {
    public static final MapCodec<GuardRailingBlock> CODEC = simpleCodec(GuardRailingBlock::new);

    public GuardRailingBlock(Properties properties) {
        super(1.5F, 1.25F, 16.0F, 16.0F, 24.0F, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public MapCodec<? extends GuardRailingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(direction);
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    connectsTo(level.getBlockState(neighbour), level, neighbour, direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (!direction.getAxis().isHorizontal()) {
            return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
        }
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                connectsTo(neighbourState, level, neighbourPos, direction));
    }

    private static boolean connectsTo(BlockState neighbour, BlockGetter level, BlockPos neighbourPos,
                                      Direction towardNeighbour) {
        if (neighbour.getBlock() instanceof GuardRailingBlock) {
            return true;
        }
        return !isExceptionForConnection(neighbour)
                && neighbour.isFaceSturdy(level, neighbourPos, towardNeighbour.getOpposite());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
