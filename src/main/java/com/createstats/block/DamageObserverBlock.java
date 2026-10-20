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

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class DamageObserverBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty WALL_MOUNT = BooleanProperty.create("wall_mount");
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

    private static final VoxelShape FLOOR = Shapes.or(Block.box(3, 0, 3, 13, 1.5, 13), Block.box(6, 1.5, 6, 10, 3, 10));
    private static final VoxelShape CEILING_SHAPE = Shapes.or(Block.box(3, 14.5, 3, 13, 16, 13), Block.box(6, 13, 6, 10, 14.5, 10));
    private static final VoxelShape WALL_NORTH = Shapes.or(Block.box(3, 3, 0, 13, 13, 1.5), Block.box(6, 6, 1.5, 10, 10, 3));
    private static final VoxelShape WALL_SOUTH = Shapes.or(Block.box(3, 3, 14.5, 13, 13, 16), Block.box(6, 6, 13, 10, 10, 14.5));
    private static final VoxelShape WALL_EAST = Shapes.or(Block.box(14.5, 3, 3, 16, 13, 13), Block.box(13, 6, 6, 14.5, 10, 10));
    private static final VoxelShape WALL_WEST = Shapes.or(Block.box(0, 3, 3, 1.5, 13, 13), Block.box(1.5, 6, 6, 3, 10, 10));

    public DamageObserverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, true)
                .setValue(WALL_MOUNT, false).setValue(CEILING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(DamageObserverBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, WALL_MOUNT, CEILING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state;
        if (face.getAxis().isHorizontal()) {
            state = defaultBlockState().setValue(FACING, face.getOpposite()).setValue(WALL_MOUNT, true);
        } else {
            state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(CEILING, face == Direction.DOWN);
        }
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    private static Direction support(BlockState state) {
        if (state.getValue(WALL_MOUNT)) return state.getValue(FACING);
        return state.getValue(CEILING) ? Direction.UP : Direction.DOWN;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = support(state);
        BlockPos supportPos = pos.relative(toSupport);
        BlockState supportState = level.getBlockState(supportPos);
        return supportState.isFaceSturdy(level, supportPos, toSupport.getOpposite()) || supportState.getBlock() instanceof ChestBlock;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos currentPos, BlockPos neighborPos) {
        if (direction == support(state) && !state.canSurvive(level, currentPos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(WALL_MOUNT)) {
            return switch (state.getValue(FACING)) {
                case SOUTH -> WALL_SOUTH;
                case EAST -> WALL_EAST;
                case WEST -> WALL_WEST;
                default -> WALL_NORTH;
            };
        }
        return state.getValue(CEILING) ? CEILING_SHAPE : FLOOR;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack) || stack.getItem() instanceof ClickToLinkBlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof DamageObserverBE marker)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) {
            ClientScreenAccess.openDamageObserver(marker);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DamageObserverBE be ? be.comparatorOutput() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DamageObserverBE(ModBlockEntities.DAMAGE_OBSERVER.get(), pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.DAMAGE_OBSERVER.get()) return null;
        return (lvl, pos, blockState, blockEntity) -> ((DamageObserverBE) blockEntity).serverTick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "battle.create_stats.marker");
    }
}
