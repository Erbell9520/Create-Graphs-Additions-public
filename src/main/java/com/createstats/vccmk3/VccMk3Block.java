package com.createstats.vccmk3;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class VccMk3Block extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    public static final com.mojang.serialization.MapCodec<VccMk3Block> CODEC = simpleCodec(VccMk3Block::new);

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16),
            Block.box(0.5, 1, 0.5, 15.5, 4.5, 15.5),
            Block.box(0, 4.5, 0, 16, 6.5, 16),
            Block.box(0, 6.4, 0, 16, 14.4, 1.5),
            Block.box(0, 6.4, 1.5, 1.5, 14.4, 5.5),
            Block.box(14.5, 6.4, 1.5, 16, 14.4, 5.5),
            Block.box(0, 6.5, 5.5, 1.5, 9, 13.5),
            Block.box(14.5, 6.5, 5.5, 16, 9, 13.5),
            Block.box(0, 14.4, 0, 16, 15.7, 5.5),
            Block.box(0.5, 15.7, 0.5, 15.5, 16, 5),
            Block.box(1.5, 6.5, 0.591, 14.5, 13.891, 5.5));

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, SHAPE_NORTH);
        SHAPES.put(Direction.EAST, rotateY(SHAPE_NORTH));
        SHAPES.put(Direction.SOUTH, rotateY(SHAPES.get(Direction.EAST)));
        SHAPES.put(Direction.WEST, rotateY(SHAPES.get(Direction.SOUTH)));
    }

    // rotate in code, four hand typed shapes are four chances to typo
    private static VoxelShape rotateY(VoxelShape source) {
        VoxelShape[] result = { Shapes.empty() };
        source.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                result[0] = Shapes.or(result[0], Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)));
        return result[0];
    }

    public VccMk3Block(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ARMED, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_NORTH);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.VCC_MK3.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return (tickLevel, pos, tickState, be) -> {
                if (be instanceof VccMk3BlockEntity controller) {
                    VccMk3BlockEntity.clientTick(tickLevel, pos, tickState, controller);
                }
            };
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof VccMk3BlockEntity controller) {
                VccMk3BlockEntity.serverTick(tickLevel, pos, tickState, controller);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof VccMk3BlockEntity controller)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            warnIfPhysicsMissing(serverPlayer);
            serverPlayer.openMenu(controller, pos);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VccMk3BlockEntity controller) {
            for (int slot = 0; slot < VccMk3BlockEntity.SLOT_COUNT; slot++) {
                ItemStack stack = controller.inventory().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private static final Map<java.util.UUID, Long> PHYSICS_WARN_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PHYSICS_WARN_COOLDOWN_MS = 60_000L;

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
    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.vcc_mk3",
                java.util.List.of(),
                java.util.List.of(com.createstats.util.CreateStatsTooltipHelper.note(
                        "block.create_stats.vcc_mk3.tooltip.summary")));
    }
}
