package com.createstats.block;

import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

import java.util.List;

public class MiniDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<Segment> SEGMENT = EnumProperty.create("segment", Segment.class);
    private static final VoxelShape SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0, 0.0, 12.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_EAST = Block.box(12.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_WEST = Block.box(0.0, 0.0, 0.0, 4.0, 16.0, 16.0);

    public MiniDisplayBlock(BlockBehaviour.Properties properties) {
        super(properties.mapColor(MapColor.COLOR_ORANGE));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false)
                .setValue(SEGMENT, Segment.SINGLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(MiniDisplayBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, SEGMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal()) {
            return null;
        }
        final BlockState state = this.defaultBlockState()
                .setValue(FACING, clickedFace.getOpposite())
                .setValue(SEGMENT, Segment.SINGLE);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction supportDirection = state.getValue(FACING);
        final BlockPos supportPos = pos.relative(supportDirection);
        final BlockState supportState = level.getBlockState(supportPos);
        return supportState.isFaceSturdy(level, supportPos, supportDirection.getOpposite())
                || supportState.getBlock() instanceof ChestBlock;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction.getAxis().isHorizontal()) {
            return this.withComputedSegment(level, currentPos, state);
        }
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MiniDisplayBE miniDisplay)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openMiniDisplay(miniDisplay);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            this.refreshSegmentState(level, pos);
            this.refreshAdjacentSegments(level, pos, state.getValue(FACING));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            final BlockEntity removedBE = level.getBlockEntity(pos);
            if (state.getValue(SEGMENT) == Segment.LEFT && removedBE instanceof MiniDisplayBE controller) {
                final BlockPos partnerPos = pos.relative(state.getValue(FACING).getClockWise());
                final BlockEntity partnerBE = level.getBlockEntity(partnerPos);
                if (partnerBE instanceof MiniDisplayBE follower) {
                    follower.inheritStateFrom(controller);
                }
            }
            this.refreshAdjacentSegments(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiniDisplayBE(ModBlockEntities.MINI_DISPLAY.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.MINI_DISPLAY.get() || level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((MiniDisplayBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.mini_display");
    }

    private void refreshAdjacentSegments(Level level, BlockPos pos, Direction facing) {
        final Direction right = facing.getClockWise();
        this.refreshSegmentState(level, pos.relative(right));
        this.refreshSegmentState(level, pos.relative(right.getOpposite()));
    }

    private void refreshSegmentState(Level level, BlockPos pos) {
        final BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof MiniDisplayBlock)) {
            return;
        }
        final BlockState updated = this.withComputedSegment(level, pos, current);
        if (updated != current) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MiniDisplayBE miniDisplay
                    && current.getValue(SEGMENT) == Segment.RIGHT
                    && updated.getValue(SEGMENT) == Segment.SINGLE) {
                final MiniDisplayBE controller = miniDisplay.getControllerBE();
                if (controller != miniDisplay) {
                    miniDisplay.inheritStateFrom(controller);
                }
            }
            level.setBlock(pos, updated, 3);
        }
    }

    private BlockState withComputedSegment(LevelReader level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MiniDisplayBlock)) {
            return state;
        }
        final Segment computed = computeSegment(level, pos, state);
        return state.getValue(SEGMENT) == computed ? state : state.setValue(SEGMENT, computed);
    }

    public static Segment computeSegment(LevelReader level, BlockPos pos, BlockState state) {
        final Direction facing = state.getValue(FACING);
        final Direction right = facing.getClockWise();
        final BlockPos leftPos = pos.relative(right.getOpposite());
        final BlockPos rightPos = pos.relative(right);
        final boolean hasLeft = matchesDisplayStatic(level, leftPos, facing);
        final boolean hasRight = matchesDisplayStatic(level, rightPos, facing);
        if (hasRight && !hasLeft && !matchesDisplayStatic(level, rightPos.relative(right), facing)) {
            return Segment.LEFT;
        }
        if (hasLeft && !hasRight && !matchesDisplayStatic(level, leftPos.relative(right.getOpposite()), facing)) {
            return Segment.RIGHT;
        }
        return Segment.SINGLE;
    }

    private static boolean matchesDisplayStatic(LevelReader level, BlockPos pos, Direction facing) {
        final BlockState neighbor = level.getBlockState(pos);
        return neighbor.getBlock() instanceof MiniDisplayBlock
                && neighbor.hasProperty(FACING)
                && neighbor.getValue(FACING) == facing;
    }

    private boolean matchesDisplay(LevelReader level, BlockPos pos, Direction facing) {
        return matchesDisplayStatic(level, pos, facing);
    }

    public enum Segment implements StringRepresentable {
        SINGLE("single"),
        LEFT("left"),
        RIGHT("right");

        private final String serializedName;

        Segment(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
}
