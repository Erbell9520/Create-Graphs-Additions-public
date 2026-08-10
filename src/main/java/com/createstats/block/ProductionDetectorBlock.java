package com.createstats.block;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class ProductionDetectorBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final DirectionProperty FACING;
    public static final BooleanProperty POWERED;
    /** Which vertical half of the 1x2 machine this block occupies. */
    public static final EnumProperty<DoubleBlockHalf> HALF;

    // Lower-half collision/interaction shapes
    private static final VoxelShape SHAPE_NORTH;
    private static final VoxelShape SHAPE_SOUTH;
    private static final VoxelShape SHAPE_EAST;
    private static final VoxelShape SHAPE_WEST;

    // Upper-half collision/interaction shapes (screen panel at bottom of upper block space)
    private static final VoxelShape SHAPE_NORTH_UPPER;
    private static final VoxelShape SHAPE_SOUTH_UPPER;
    private static final VoxelShape SHAPE_EAST_UPPER;
    private static final VoxelShape SHAPE_WEST_UPPER;
    private static final double[][] LOWER_SHAPE_BOXES = {
        { 2.0, 0.0, 2.0, 14.0, 14.0, 14.0 }
    };
    private static final double[][] UPPER_SHAPE_BOXES = {
        { 1.0, 0.0, 4.0, 15.0, 14.0,  6.0 },
        { 1.0, 0.0, 6.0, 15.0,  8.0, 13.0 }
    };

    public ProductionDetectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any()
                .setValue(ProductionDetectorBlock.FACING, Direction.NORTH)
                .setValue(ProductionDetectorBlock.POWERED, false)
                .setValue(ProductionDetectorBlock.HALF, DoubleBlockHalf.LOWER)
        );
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ProductionDetectorBlock::new);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ProductionDetectorBlock.FACING, ProductionDetectorBlock.POWERED, ProductionDetectorBlock.HALF);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        // Require the block above to be free so the upper half can be placed
        if (pos.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                .setValue(ProductionDetectorBlock.FACING, context.getHorizontalDirection())
                .setValue(ProductionDetectorBlock.HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Automatically place the upper half
        level.setBlock(pos.above(), state.setValue(ProductionDetectorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    /**
     * When a vertical neighbour changes, check whether our partner half is still present.
     * If not, collapse this half to air (mirrors vanilla DoublePlantBlock behaviour).
     */
    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (facing.getAxis() == Direction.Axis.Y) {
            boolean expectingPartnerAbove = (half == DoubleBlockHalf.LOWER);
            boolean partnerIsAbove = (facing == Direction.UP);
            if (expectingPartnerAbove == partnerIsAbove) {
                // This is the direction we depend on, verify partner is still valid
                if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }
        return super.updateShape(state, facing, neighborState, level, pos, neighborPos);
    }

    /** [1] */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lowerState = level.getBlockState(lowerPos);
            if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, lowerPos, Block.getId(lowerState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return switch ((Direction) state.getValue(ProductionDetectorBlock.FACING)) {
                case SOUTH -> ProductionDetectorBlock.SHAPE_SOUTH_UPPER;
                case EAST  -> ProductionDetectorBlock.SHAPE_EAST_UPPER;
                case WEST  -> ProductionDetectorBlock.SHAPE_WEST_UPPER;
                default    -> ProductionDetectorBlock.SHAPE_NORTH_UPPER;
            };
        }
        return switch ((Direction) state.getValue(ProductionDetectorBlock.FACING)) {
            case SOUTH -> ProductionDetectorBlock.SHAPE_SOUTH;
            case EAST  -> ProductionDetectorBlock.SHAPE_EAST;
            case WEST  -> ProductionDetectorBlock.SHAPE_WEST;
            default    -> ProductionDetectorBlock.SHAPE_NORTH;
        };
    }

    // helpers, always operate on the lower half

    /** Returns the block-position of the lower half regardless of which half {@code state} is. */
    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /**
     * Resolves the {@link ProductionDetectorBE} for either half by always reading
     * from the lower half position.  Returns {@code null} if not found or out of sync.
     */
    @Nullable
    private static ProductionDetectorBE lowerBE(Level level, BlockState state, BlockPos pos) {
        BlockPos lower = lowerPos(state, pos);
        // When delegating from the upper half, verify the lower block is still valid
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lowerState = level.getBlockState(lower);
            if (!lowerState.is(state.getBlock()) || lowerState.getValue(HALF) != DoubleBlockHalf.LOWER) {
                return null;
            }
        }
        BlockEntity be = level.getBlockEntity(lower);
        return be instanceof ProductionDetectorBE d ? d : null;
    }

    // interaction, both halves open the same screen via the lower BE

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        final ProductionDetectorBE detector = lowerBE(level, state, pos);
        if (detector == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openProductionDetector(detector);
            return InteractionResult.SUCCESS;
        }
        detector.onPlayerInteract(player);
        return InteractionResult.CONSUME;
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.production_detector");
    }

    // blockEntity, only the lower half owns the BE

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return new ProductionDetectorBE((BlockEntityType<?>)ModBlockEntities.PRODUCTION_DETECTOR.get(), pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        // Only the lower half receives a ticker
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.PRODUCTION_DETECTOR.get()) return null;
        return (lvl, bPos, st, be) -> ((ProductionDetectorBE) be).tick();
    }

    // redstone / comparator, delegate upper half to lower

    public boolean isSignalSource(BlockState state) {
        return true;
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                return lower.getValue(ProductionDetectorBlock.POWERED) ? 15 : 0;
            }
            return 0;
        }
        return state.getValue(ProductionDetectorBlock.POWERED) ? 15 : 0;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                return getDirectSignal(lower, level, pos.below(), dir);
            }
            return 0;
        }
        if (!(boolean) state.getValue(ProductionDetectorBlock.POWERED)) {
            return 0;
        }
        final Direction facing = (Direction) state.getValue(ProductionDetectorBlock.FACING);
        return (dir == facing.getOpposite()) ? 15 : 0;
    }

    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        final ProductionDetectorBE detector = lowerBE(level, state, pos);
        return detector != null ? detector.getAnalogOutputSignal() : 0;
    }

    // static initialiser

    private static VoxelShape shapeFor(Direction direction, double[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            final double minX = box[0];
            final double minY = box[1];
            final double minZ = box[2];
            final double maxX = box[3];
            final double maxY = box[4];
            final double maxZ = box[5];
            shape = Shapes.or(shape, switch (direction) {
                case SOUTH -> Block.box(16.0 - maxX, minY, 16.0 - maxZ, 16.0 - minX, maxY, 16.0 - minZ);
                case EAST -> Block.box(16.0 - maxZ, minY, minX, 16.0 - minZ, maxY, maxX);
                case WEST -> Block.box(minZ, minY, 16.0 - maxX, maxZ, maxY, 16.0 - minX);
                default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
            });
        }
        return shape;
    }

    static {
        FACING = HorizontalDirectionalBlock.FACING;
        POWERED = BooleanProperty.create("powered");
        HALF   = BlockStateProperties.DOUBLE_BLOCK_HALF;

        SHAPE_NORTH = shapeFor(Direction.NORTH, LOWER_SHAPE_BOXES);
        SHAPE_SOUTH = shapeFor(Direction.SOUTH, LOWER_SHAPE_BOXES);
        SHAPE_EAST = shapeFor(Direction.EAST, LOWER_SHAPE_BOXES);
        SHAPE_WEST = shapeFor(Direction.WEST, LOWER_SHAPE_BOXES);

        SHAPE_NORTH_UPPER = shapeFor(Direction.NORTH, UPPER_SHAPE_BOXES);
        SHAPE_SOUTH_UPPER = shapeFor(Direction.SOUTH, UPPER_SHAPE_BOXES);
        SHAPE_EAST_UPPER = shapeFor(Direction.EAST, UPPER_SHAPE_BOXES);
        SHAPE_WEST_UPPER = shapeFor(Direction.WEST, UPPER_SHAPE_BOXES);
    }
}
