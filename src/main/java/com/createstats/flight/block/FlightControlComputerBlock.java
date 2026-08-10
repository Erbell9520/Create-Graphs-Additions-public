package com.createstats.flight.block;

import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/** [1] */
public class FlightControlComputerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Drives the block-model variant; synced server→client on mode change. */
    public static final EnumProperty<VisualState> VISUAL_STATE =
            EnumProperty.create("visual_state", VisualState.class);

    // The FCC reuses the factory_logic_controller model, so it shares that block's authored
    // per-facing collision/outline shape instead of defaulting to a full cube.
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(0.0, 3.0, 0.0, 16.0, 5.0, 6.0),
            Block.box(0.0, 5.0, 2.0, 16.0, 15.0, 12.0),
            Block.box(2.0, 5.0, 12.0, 14.0, 14.0, 16.0),
            Block.box(0.0, 14.0, 5.0, 16.0, 16.0, 12.0));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(0.0, 3.0, 10.0, 16.0, 5.0, 16.0),
            Block.box(0.0, 5.0, 4.0, 16.0, 15.0, 14.0),
            Block.box(2.0, 5.0, 0.0, 14.0, 14.0, 4.0),
            Block.box(0.0, 14.0, 4.0, 16.0, 16.0, 11.0));
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(10.0, 3.0, 0.0, 16.0, 5.0, 16.0),
            Block.box(4.0, 5.0, 0.0, 14.0, 15.0, 16.0),
            Block.box(0.0, 5.0, 2.0, 4.0, 14.0, 14.0),
            Block.box(4.0, 14.0, 0.0, 11.0, 16.0, 16.0));
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(0.0, 3.0, 0.0, 6.0, 5.0, 16.0),
            Block.box(2.0, 5.0, 0.0, 12.0, 15.0, 16.0),
            Block.box(12.0, 5.0, 2.0, 16.0, 14.0, 14.0),
            Block.box(5.0, 14.0, 0.0, 12.0, 16.0, 16.0));

    public FlightControlComputerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(VISUAL_STATE, VisualState.OFF));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(FlightControlComputerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VISUAL_STATE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VISUAL_STATE, VisualState.OFF);
    }

    private static VoxelShape getFacingShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getFacingShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getFacingShape(state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FlightControlComputerBE fcc)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            warnIfPhysicsMissing(serverPlayer);
            serverPlayer.openMenu(fcc, pos);
        }
        return ItemInteractionResult.CONSUME;
    }

    // Per-player cooldown so reopening the FCC does not spam the physics-missing warning.
    private static final java.util.Map<java.util.UUID, Long> PHYSICS_WARN_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PHYSICS_WARN_COOLDOWN_MS = 60_000L;

    // The FCC degrades silently (server-log only) when Aeronautics/Sable is absent. Mirror the Hinge
    // tool's up-front chat warning so the player understands why physics-dependent features do nothing.
    private static void warnIfPhysicsMissing(ServerPlayer player) {
        if (SableBridge.isAvailable()) return;
        long now = System.currentTimeMillis();
        Long last = PHYSICS_WARN_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < PHYSICS_WARN_COOLDOWN_MS) return;
        PHYSICS_WARN_COOLDOWN.put(player.getUUID(), now);
        player.displayClientMessage(
            Component.translatable("message.create_stats.fcc.physics_missing").withStyle(ChatFormatting.RED), false);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FlightControlComputerBE fcc) {
                fcc.releaseAllOwnedGovernors();
                fcc.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlightControlComputerBE(ModBlockEntities.FLIGHT_CONTROL_COMPUTER.get(), pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FLIGHT_CONTROL_COMPUTER.get()) {
            return null;
        }
        return (lvl, pos, blockState, blockEntity) -> ((FlightControlComputerBE) blockEntity).serverTick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.flight_control_computer");
    }

    // visual state enum

    public enum VisualState implements StringRepresentable {
        /** FCC powered off or not yet placed in a contraption. */
        OFF("off"),
        /** FCC on, no active flight mode (STANDBY). */
        STANDBY("standby"),
        /** FCC actively controlling a flight mode. */
        ACTIVE("active"),
        /** Warning: sensor lost, failsafe active, or gimbal not validated. */
        WARNING("warning");

        private final String name;
        VisualState(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }

        @Override
        public String toString() { return name; }
    }
}
