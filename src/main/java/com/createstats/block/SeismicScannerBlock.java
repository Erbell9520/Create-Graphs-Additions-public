package com.createstats.block;

import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.SeismicRig;
import com.createstats.registry.ModSounds;
import com.createstats.util.CreateStatsTooltipHelper;
import com.createstats.tuner.TunerHost;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SeismicScannerBlock extends HorizontalDirectionalBlock implements IBE<SeismicScannerBE>, IWrenchable,
        SimpleWaterloggedBlock, IProxyHoveringInformation, TunerHost.Part {
    public static final MapCodec<SeismicScannerBlock> CODEC = simpleCodec(SeismicScannerBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final double[][] LOWER_BOXES = {
            {0.25, 0, 0.25, 15.75, 9.0, 15.75},
            {5.0, 9.0, 3.0, 11.0, 16.0, 12.0},
            {14.5, 5.5, 4.0, 16.0, 13.75, 9.0},
    };

    private static final double[][] UPPER_BOXES = {
            {5.0, 0, 3.0, 11.0, 13.25, 12.0},
            {5.5, 13.0, 4.0, 12.5, 15.95, 12.25},
            {12.25, 10.4, 4.4, 15.35, 14.5, 7.35},
    };

    private static final Map<Direction, VoxelShape> LOWER = shapes(LOWER_BOXES);
    private static final Map<Direction, VoxelShape> UPPER = shapes(UPPER_BOXES);

    public SeismicScannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER).get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        return base(state, pos);
    }

    @Override
    public BlockPos tunerHostPos(BlockState state, BlockPos pos) {
        return base(state, pos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockPos pos = context.getClickedPos();
        final Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        final BlockPos up = pos.above();
        level.setBlock(up, state.setValue(HALF, DoubleBlockHalf.UPPER)
                .setValue(WATERLOGGED, level.getFluidState(up).getType() == Fluids.WATER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        final BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        final DoubleBlockHalf half = state.getValue(HALF);
        final boolean towardPartner = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardPartner) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockPos below = pos.below();
            final BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, lower.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            if (!level.isClientSide() && !moved && !newState.is(this)
                    && level.getBlockEntity(pos) instanceof SeismicScannerBE be && be.stored() > 0) {
                level.playSound(null, pos, ModSounds.SEISMIC_THUD.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                        0.5f + 0.7f * (float) SeismicCharge.heightOf(be.stored()), 1.0f);
            }
            IBE.onRemove(state, level, pos, newState);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return InteractionResult.PASS;
        }
        final double x = (hit.getLocation().x - pos.getX()) * 16.0;
        final double y = (hit.getLocation().y - pos.getY()) * 16.0;
        final double z = (hit.getLocation().z - pos.getZ()) * 16.0;
        if (!SeismicGeometry.onLever(SeismicScannerBE.quarter(state.getValue(FACING)), x, y, z)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SeismicScannerBE be) {
            be.pullLever(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide() && level.getBlockEntity(base(state, pos)) instanceof SeismicScannerBE be) {
            be.neighbourChanged();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(base(state, pos)) instanceof SeismicScannerBE be)) {
            return 0;
        }
        return SeismicRig.comparator(be.stored(), SeismicCharge.burstFor(be.radius()));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos lower = base(state, context.getClickedPos());
        final BlockState low = level.getBlockState(lower);
        final BlockState high = level.getBlockState(lower.above());
        if (!low.is(this) || !high.is(this)) {
            return InteractionResult.PASS;
        }
        final Direction next = low.getValue(FACING).getClockWise();
        if (!level.isClientSide()) {
            level.setBlock(lower, low.setValue(FACING, next), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(lower.above(), high.setValue(FACING, next), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            IWrenchable.playRotateSound(level, lower);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        final BlockPos lower = base(state, context.getClickedPos());
        if (lower.equals(context.getClickedPos())) {
            return IWrenchable.super.onSneakWrenched(state, context);
        }
        final Level level = context.getLevel();
        final BlockState low = level.getBlockState(lower);
        if (!low.is(this)) {
            return InteractionResult.PASS;
        }
        final UseOnContext moved = new UseOnContext(level, context.getPlayer(), context.getHand(), context.getItemInHand(),
                new BlockHitResult(context.getClickLocation().subtract(0, 1, 0), context.getClickedFace(), lower,
                        context.isInside()));
        return IWrenchable.super.onSneakWrenched(low, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        final List<Component> specs = new java.util.ArrayList<>();
        specs.add(CreateStatsTooltipHelper.spec("block.create_stats.seismic_scanner.tooltip.spec",
                SeismicCharge.MIN_RADIUS, SeismicCharge.MAX_RADIUS,
                String.format(java.util.Locale.ROOT, "%,d", SeismicCharge.burstFor(SeismicCharge.MIN_RADIUS)),
                String.format(java.util.Locale.ROOT, "%,d", SeismicCharge.burstFor(SeismicCharge.MAX_RADIUS))));
        final Component tuned = SeismicScannerBE.tuningLine(stack, context.level());
        if (tuned != null) {
            specs.add(tuned.copy().withStyle(ChatFormatting.DARK_AQUA));
        }
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.seismic_scanner", specs,
                List.of(CreateStatsTooltipHelper.note("block.create_stats.seismic_scanner.tooltip.drop"),
                        Component.translatable("block.create_stats.seismic_scanner.tooltip.rules",
                                (int) SeismicScannerBE.WITNESS_RADIUS).withStyle(ChatFormatting.DARK_GRAY)));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Class<SeismicScannerBE> getBlockEntityClass() {
        return SeismicScannerBE.class;
    }

    @Override
    public BlockEntityType<? extends SeismicScannerBE> getBlockEntityType() {
        return ModBlockEntities.SEISMIC_SCANNER.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? ModBlockEntities.SEISMIC_SCANNER.create(pos, state) : null;
    }

    static double[] turn(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {16 - z, x};
            case SOUTH -> new double[] {16 - x, 16 - z};
            case WEST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    private static Map<Direction, VoxelShape> shapes(double[][] boxes) {
        final Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                final double[] a = turn(facing, b[0], b[2]);
                final double[] c = turn(facing, b[3], b[5]);
                shape = Shapes.or(shape, Block.box(Math.min(a[0], c[0]), b[1], Math.min(a[1], c[1]),
                        Math.max(a[0], c[0]), b[4], Math.max(a[1], c[1])));
            }
            out.put(facing, shape.optimize());
        }
        return out;
    }
}
