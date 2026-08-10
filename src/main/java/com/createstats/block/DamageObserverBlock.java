package com.createstats.block;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

public class DamageObserverBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
        public static final BooleanProperty WALL_MOUNT = BooleanProperty.create("wall_mount");
    private static final VoxelShape SHAPE_NORTH = Block.box(0.0, 1.0, 0.0, 16.0, 15.0, 9.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0, 1.0, 7.0, 16.0, 15.0, 16.0);
    private static final VoxelShape SHAPE_EAST = Block.box(7.0, 1.0, 0.0, 16.0, 15.0, 16.0);
    private static final VoxelShape SHAPE_WEST = Block.box(0.0, 1.0, 0.0, 9.0, 15.0, 16.0);
        private static final VoxelShape SHAPE_REGULAR = Shapes.or(
            Block.box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0),
            Block.box(4.0, 3.0, 4.0, 12.0, 12.0, 12.0),
            Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 15.0));

    public DamageObserverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, true)
            .setValue(WALL_MOUNT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(DamageObserverBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, WALL_MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState state = clickedFace.getAxis().isHorizontal()
                ? this.defaultBlockState().setValue(FACING, clickedFace.getOpposite()).setValue(WALL_MOUNT, true)
                : this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WALL_MOUNT, false);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(WALL_MOUNT)) {
            BlockPos supportPos = pos.below();
            BlockState supportState = level.getBlockState(supportPos);
            return supportState.isFaceSturdy(level, supportPos, Direction.UP)
                    || supportState.getBlock() instanceof ChestBlock;
        }
        Direction supportDirection = state.getValue(FACING);
        BlockPos supportPos = pos.relative(supportDirection);
        BlockState supportState = level.getBlockState(supportPos);
        return supportState.isFaceSturdy(level, supportPos, supportDirection.getOpposite())
                || supportState.getBlock() instanceof ChestBlock;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        Direction supportDirection = state.getValue(WALL_MOUNT) ? state.getValue(FACING) : Direction.DOWN;
        if (direction == supportDirection && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(WALL_MOUNT)) {
            return SHAPE_REGULAR;
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack) || stack.getItem() instanceof ClickToLinkBlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DamageObserverBE observer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openDamageObserver(observer);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DamageObserverBE(ModBlockEntities.DAMAGE_OBSERVER.get(), pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.DAMAGE_OBSERVER.get()) {
            return null;
        }
        return (lvl, pos, blockState, blockEntity) -> ((DamageObserverBE) blockEntity).serverTick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.damage_observer");
    }
}