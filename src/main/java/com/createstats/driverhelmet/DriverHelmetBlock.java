package com.createstats.driverhelmet;

import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DriverHelmetBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DriverHelmetBlock> CODEC = simpleCodec(DriverHelmetBlock::new);

    public static final IntegerProperty LIVERY = IntegerProperty.create("livery", 0, HudTheme.values().length - 1);

    private static final VoxelShape[] SHAPES = {
        Block.box(1.0, 0.0, 1.0, 15.0, 13.5, 16.0),
        Block.box(0.0, 0.0, 1.0, 15.0, 13.5, 15.0),
        Block.box(1.0, 0.0, 0.0, 15.0, 13.5, 15.0),
        Block.box(1.0, 0.0, 1.0, 16.0, 13.5, 15.0),
    };

    public DriverHelmetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIVERY, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIVERY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIVERY, HudTheme.of(DriverHelmetItem.visor(context.getItemInHand()).theme()).ordinal());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DriverHelmetBE helmet) {
            if (player.isShiftKeyDown()) {
                helmet.nextTheme();
            } else {
                helmet.flick();
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.DRIVER_HELMET.create(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
