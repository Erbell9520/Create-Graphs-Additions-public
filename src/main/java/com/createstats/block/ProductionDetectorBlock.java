package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.PanelGeometry;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

public class ProductionDetectorBlock extends HorizontalDirectionalBlock implements IBE<ProductionDetectorBE> {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final double[][] LOWER_BOXES = {
            {2.0, 0.0, 2.0, 14.0, 16.0, 14.0},
            {0.6, 5.0, 5.0, 2.4, 11.0, 11.0},
            {13.6, 5.0, 5.0, 15.4, 11.0, 11.0}
    };

    private static final double[][] UPPER_BOXES = {
            {0.0, 0.0, 3.0, 16.0, 15.0, 15.0}
    };
    private static final VoxelShape[] LOWER = new VoxelShape[4];
    private static final VoxelShape[] UPPER = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            LOWER[d.get2DDataValue()] = shapeFor(d, LOWER_BOXES);
            UPPER[d.get2DDataValue()] = shapeFor(d, UPPER_BOXES);
        }
    }

    public ProductionDetectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ProductionDetectorBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockPos pos = context.getClickedPos();
        final Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        final DoubleBlockHalf half = state.getValue(HALF);
        if (facing.getAxis() == Direction.Axis.Y) {
            final boolean partnerAbove = half == DoubleBlockHalf.LOWER;
            if (partnerAbove == (facing == Direction.UP)) {
                if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }
        return super.updateShape(state, facing, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockPos lowerPos = pos.below();
            final BlockState lowerState = level.getBlockState(lowerPos);
            if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, lowerPos, Block.getId(lowerState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            IBE.onRemove(state, level, pos, newState);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        final int i = state.getValue(FACING).get2DDataValue();
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER[i] : LOWER[i];
    }

    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Nullable
    private static ProductionDetectorBE lowerBE(BlockGetter level, BlockState state, BlockPos pos) {
        final BlockPos lower = lowerPos(state, pos);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockState lowerState = level.getBlockState(lower);
            if (!lowerState.is(state.getBlock()) || lowerState.getValue(HALF) != DoubleBlockHalf.LOWER) {
                return null;
            }
        }
        return level.getBlockEntity(lower) instanceof ProductionDetectorBE d ? d : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        final ProductionDetectorBE detector = lowerBE(level, state, pos);
        if (detector == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return AntennaAttachmentHelper.handleUse(stack, level, player, hand, detector);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        final ProductionDetectorBE detector = lowerBE(level, state, pos);
        if (detector == null) {
            return InteractionResult.PASS;
        }
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && hitsTest(detector, state, pos, player, hit)) {
            if (!level.isClientSide()) {
                detector.pressTest(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openProductionDetector(detector);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    private static boolean hitsTest(ProductionDetectorBE detector, BlockState state, BlockPos pos, Player player,
                                    BlockHitResult hit) {
        final Direction facing = state.getValue(FACING);
        final BlockPos lower = pos.below();
        final Vec3 hitLocal = hit.getLocation().subtract(lower.getX(), lower.getY(), lower.getZ());
        final Vec3 hitModel = PanelGeometry.toModel(facing, hitLocal);
        Vec3 eye = player.getEyePosition(1.0f);
        final Object subLevel = SableBridge.subLevelAccessOf(detector);
        if (subLevel != null) {
            final Vector3d local = SableBridge.worldToLocal(subLevel, new Vector3d(eye.x, eye.y, eye.z));
            if (local == null || !Double.isFinite(local.x)) {
                return hitModel.x >= PanelGeometry.TEST_X0 - 0.3 && hitModel.x <= PanelGeometry.TEST_X1 + 0.3
                        && hitModel.y >= 28.6;
            }
            eye = new Vec3(local.x, local.y, local.z);
        }
        final Vec3 eyeModel = PanelGeometry.toModel(facing, eye.subtract(lower.getX(), lower.getY(), lower.getZ()));
        final double[] panel = PanelGeometry.rayOnPanel(eyeModel, hitModel.subtract(eyeModel));
        return PanelGeometry.onTest(panel);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.production_detector");
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        final BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    public Class<ProductionDetectorBE> getBlockEntityClass() {
        return ProductionDetectorBE.class;
    }

    @Override
    public BlockEntityType<? extends ProductionDetectorBE> getBlockEntityType() {
        return ModBlockEntities.PRODUCTION_DETECTOR.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? null : IBE.super.newBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER || type != ModBlockEntities.PRODUCTION_DETECTOR.get()) {
            return null;
        }
        return new SmartBlockEntityTicker<>();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof ProductionDetectorBE d ? d.relaySignal(direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        final ProductionDetectorBE detector = lowerBE(level, state, pos);
        return detector != null ? detector.comparatorLevel() : 0;
    }

    private static VoxelShape shapeFor(Direction direction, double[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            final double minX = b[0], minY = b[1], minZ = b[2], maxX = b[3], maxY = b[4], maxZ = b[5];
            shape = Shapes.or(shape, switch (direction) {
                case SOUTH -> Block.box(16.0 - maxX, minY, 16.0 - maxZ, 16.0 - minX, maxY, 16.0 - minZ);
                case EAST -> Block.box(16.0 - maxZ, minY, minX, 16.0 - minZ, maxY, maxX);
                case WEST -> Block.box(minZ, minY, 16.0 - maxX, maxZ, maxY, 16.0 - minX);
                default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
            });
        }
        return shape;
    }
}
