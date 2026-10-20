package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class WallPlateBlock extends Block implements GirderMounting.Fixture {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final VoxelShape shapeNorth;
    private final VoxelShape shapeSouth;
    private final VoxelShape shapeEast;
    private final VoxelShape shapeWest;

    public WallPlateBlock(Properties properties, double x0, double y0, double z0,
                          double x1, double y1, double z1) {
        super(properties);
        this.shapeNorth = Block.box(x0, y0, z0, x1, y1, z1);
        this.shapeSouth = Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
        this.shapeEast = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
        this.shapeWest = Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, clickedFace.getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing);
        return GirderMounting.holds(level, support, facing.getOpposite())
                || level.getBlockState(support).getBlock() instanceof ChestBlock;
    }

    @Override
    public boolean isBackedBy(BlockState state, Direction towardSupport) {
        return state.getValue(FACING) == towardSupport;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> shapeSouth;
            case EAST -> shapeEast;
            case WEST -> shapeWest;
            default -> shapeNorth;
        };
    }
}
