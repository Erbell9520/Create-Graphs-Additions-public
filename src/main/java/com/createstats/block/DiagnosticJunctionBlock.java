package com.createstats.block;

import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DiagnosticJunctionBlock extends Block implements EntityBlock, GirderMounting.Fixture {
    public enum Kind {
        CABLE,
        WIRE
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final VoxelShape SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 15.4, 8.05);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0, 0.0, 7.95, 16.0, 15.4, 16.0);
    private static final VoxelShape SHAPE_EAST = Block.box(7.95, 0.0, 0.0, 16.0, 15.4, 16.0);
    private static final VoxelShape SHAPE_WEST = Block.box(0.0, 0.0, 0.0, 8.05, 15.4, 16.0);

    private final Kind kind;

    public DiagnosticJunctionBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind == null ? Kind.CABLE : kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Nullable
    @Override
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
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (hitCutSwitch(state, pos, hit)
            && level.getBlockEntity(pos) instanceof DiagnosticJunctionBE junction) {
            if (!level.isClientSide()) {
            junction.toggleCutOff();
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                0.5f, junction.isCutOff() ? 0.75f : 1.45f);
            player.displayClientMessage(Component.translatable(junction.isCutOff()
                ? "message.create_stats.diagnostic_junction.cut_off"
                : "message.create_stats.diagnostic_junction.conducting"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (kind == Kind.WIRE && hitModeSwitch(state, pos, hit)
                && level.getBlockEntity(pos) instanceof DiagnosticJunctionBE junction) {
            if (!level.isClientSide()) {
                junction.toggleMerged();
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                        0.45f, junction.isMerged() ? 1.6f : 1.1f);
                player.displayClientMessage(Component.translatable(junction.isMerged()
                        ? "message.create_stats.wire_terminal_junction.merged"
                        : "message.create_stats.wire_terminal_junction.separate"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        boolean open = !state.getValue(OPEN);
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.45f, open ? 1.25f : 1.05f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean hitModeSwitch(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (!state.getValue(OPEN)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        double x = hit.getLocation().x - pos.getX();
        double y = hit.getLocation().y - pos.getY();
        double z = hit.getLocation().z - pos.getZ();
        double[] local = com.createstats.power.PduStuds.toModelSpace(facing, x, z);
        return local[0] >= 3.3 / 16.0 && local[0] <= 5.6 / 16.0
                && y >= 2.8 / 16.0 && y <= 6.0 / 16.0;
    }

    private static boolean hitCutSwitch(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing.getClockWise()) {
            return false;
        }
        double x = hit.getLocation().x - pos.getX();
        double y = hit.getLocation().y - pos.getY();
        double z = hit.getLocation().z - pos.getZ();
        double[] local = com.createstats.power.PduStuds.toModelSpace(facing, x, z);
        return local[0] >= 13.0 / 16.0 && local[1] >= 4.6 / 16.0 && local[1] <= 8.05 / 16.0
                && y >= 5.2 / 16.0 && y <= 10.8 / 16.0;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.WIRE && stack.getItem() instanceof com.createstats.item.CopperWireItem) {
            return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof DiagnosticJunctionBE junction) {
            junction.dropAllStudWires();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagnosticJunctionBE(ModBlockEntities.DIAGNOSTIC_JUNCTION.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.DIAGNOSTIC_JUNCTION.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((DiagnosticJunctionBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = kind == Kind.WIRE
                ? "block.create_stats.wire_terminal_junction"
                : "block.create_stats.cable_diagnostic_junction";
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, key);
    }
}
