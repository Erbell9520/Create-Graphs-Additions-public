package com.createstats.block;

import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.power.PowerCouplerMode;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class UniversalPowerCouplerBlock extends HorizontalDirectionalBlock
        implements IBE<UniversalPowerCouplerBE>, IWrenchable {
    public static final EnumProperty<PowerCouplerMode> MODE =
            EnumProperty.create("mode", PowerCouplerMode.class);
    public static final MapCodec<UniversalPowerCouplerBlock> CODEC = simpleCodec(UniversalPowerCouplerBlock::new);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(5, 0, 0, 11, 6.5, 16),
            Block.box(6.8, 5, 2, 9.2, 7.7, 14));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(5, 0, 0, 11, 6.5, 16),
            Block.box(6.8, 5, 2, 9.2, 7.7, 14));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0, 0, 5, 16, 6.5, 11),
            Block.box(2, 5, 6.8, 14, 7.7, 9.2));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(0, 0, 5, 16, 6.5, 11),
            Block.box(2, 5, 6.8, 14, 7.7, 9.2));

    public UniversalPowerCouplerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MODE, PowerCouplerMode.PULL));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction clicked = context.getClickedFace();
        Direction run = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            run = run.getOpposite();
        }
        return this.defaultBlockState()
                .setValue(FACING, run)
                .setValue(MODE, PowerCouplerMode.PULL);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        final BlockPos pos = context.getClickedPos();
        level.setBlock(pos, state.setValue(MODE, state.getValue(MODE).next()), Block.UPDATE_ALL);

        level.updateNeighborsAt(pos, this);

        level.invalidateCapabilities(pos);
        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        if (!level.isClientSide()) {
            level.invalidateCapabilities(pos);
            if (level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE coupler) {
                coupler.invalidateForeignSeam();
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE coupler) {
            coupler.dropWiresAndUnlinkAll();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<UniversalPowerCouplerBE> getBlockEntityClass() {
        return UniversalPowerCouplerBE.class;
    }

    @Override
    public BlockEntityType<? extends UniversalPowerCouplerBE> getBlockEntityType() {
        return ModBlockEntities.UNIVERSAL_POWER_COUPLER.get();
    }

    @Nullable
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<S> type) {
        return level.isClientSide() ? null
                : (lvl, pos, blockState, be) -> ((UniversalPowerCouplerBE) be).tick();
    }
}
