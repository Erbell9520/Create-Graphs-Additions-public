package com.createstats.block;

import com.createstats.blockentity.GarageDoorBE;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class GarageDoorHeaderBlock extends HorizontalDirectionalBlock
        implements EntityBlock, com.simibubi.create.content.equipment.wrench.IWrenchable,
        com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    public static final MapCodec<GarageDoorHeaderBlock> CODEC =
            simpleCodec(GarageDoorHeaderBlock::new);

    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");

    public static final float DRUM_LONG = 7.5f;

    public static final float DRUM_SHORT = 3.0f;

    public static final float DRUM_DIAG = 5.25f;

    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Direction.Axis.class);

    static {
        final float lo = 8.0f - DRUM_LONG;
        final float hi = 8.0f + DRUM_LONG;
        final float slo = 8.0f - DRUM_SHORT;
        final float shi = 8.0f + DRUM_SHORT;
        final float dlo = 8.0f - DRUM_DIAG;
        final float dhi = 8.0f + DRUM_DIAG;
        SHAPES.put(Direction.Axis.X, Shapes.or(
                Block.box(0, slo, lo, 16, shi, hi),
                Block.box(0, lo, slo, 16, hi, shi),
                Block.box(0, dlo, dlo, 16, dhi, dhi)));
        SHAPES.put(Direction.Axis.Z, Shapes.or(
                Block.box(lo, slo, 0, hi, shi, 16),
                Block.box(slo, lo, 0, shi, hi, 16),
                Block.box(dlo, dlo, 0, dhi, dhi, 16)));
    }

    private static final int PLACEMENT_HELPER_ID =
            net.createmod.catnip.placement.PlacementHelpers.register(new GarageDoorPlacementHelper());

    public GarageDoorHeaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONTROLLER, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTROLLER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext ctx) {
        return SHAPES.get(drumAxis(state));
    }

    private static Direction.Axis drumAxis(BlockState state) {
        final Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        return GarageDoorSite.widthAxis(facing).getAxis();
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CONTROLLER) ? ModBlockEntities.GARAGE_DOOR.create(pos, state) : null;
    }

    // a property flip doesn't drop the BE (onRemove checks the block, not the state), so sweep the leftovers
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!state.getValue(CONTROLLER)) {
            return (lvl, pos, st, be) -> lvl.removeBlockEntity(pos);
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof GarageDoorBE door) {
                if (lvl.isClientSide()) {
                    door.clientTick();
                } else {
                    door.tick();
                }
            }
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CONTROLLER, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide()) {
            return;
        }

        final GarageDoorSite.Detected site = GarageDoorSite.form(level, pos);
        if (!site.ok() && placer instanceof Player player) {
            tell(player, site.frame().fault());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!level.isClientSide() && state.getValue(CONTROLLER)
                && (!newState.is(this) || !newState.getValue(CONTROLLER))) {
            level.removeBlockEntity(pos);
        }
        if (!level.isClientSide() && !newState.is(this) && state.hasProperty(FACING)) {
            final Direction facing = state.getValue(FACING);
            final GarageDoorSite.Detected site = GarageDoorSite.detect(level, pos, facing, pos);
            if (site.ok()) {
                GarageDoorSite.clearCurtain(level, site.body());
            }

            final Direction axis = GarageDoorSite.widthAxis(facing);
            for (int sign = -1; sign <= 1; sign += 2) {
                final BlockPos side = pos.relative(axis, sign);
                if (level.getBlockState(side).getBlock() instanceof GarageDoorHeaderBlock) {
                    GarageDoorSite.form(level, side);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos from, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, from, movedByPiston);
        if (level.isClientSide() || isOwnDoor(block)) {
            return;
        }
        final GarageDoorBE door = controllerAt(level, pos, state);
        if (door != null) {
            door.onRedstone(door.frameHasSignal());
        }
    }

    public static boolean isOwnDoor(Block block) {
        return block instanceof GarageDoorHeaderBlock || block instanceof GarageDoorBodyBlock;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return use(level, pos, state, player);
    }

    public static InteractionResult use(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final GarageDoorBE door = controllerFrom(level, pos, state);
        if (door == null) {
            tell(player, GarageDoorSite.detect(level, pos).frame().fault());
            return InteractionResult.CONSUME;
        }
        return door.toggleByHand(player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (com.simibubi.create.AllItems.WRENCH.isIn(stack) || isWiringTool(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        final net.createmod.catnip.placement.IPlacementHelper helper =
                net.createmod.catnip.placement.PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return placeNext(helper, stack, state, level, pos, player, hand, hit);
    }

    private static boolean isWiringTool(ItemStack stack) {
        return stack.getItem() instanceof com.createstats.item.ApplianceCordItem
                || stack.getItem() instanceof com.createstats.item.CableBoreItem;
    }

    private ItemInteractionResult placeNext(net.createmod.catnip.placement.IPlacementHelper helper,
                                            ItemStack stack, BlockState state, Level level,
                                            BlockPos pos, Player player, InteractionHand hand,
                                            BlockHitResult hit) {
        if (player == null || player.isShiftKeyDown()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        final net.createmod.catnip.placement.PlacementOffset offset =
                helper.getOffset(player, level, state, pos, hit);
        if (!offset.isSuccessful()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return offset.placeInWorld(level, (net.minecraft.world.item.BlockItem) stack.getItem(),
                player, hand, hit);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final GarageDoorBE door = controllerFrom(level, pos, state);
        if (door == null) {
            return InteractionResult.PASS;
        }
        final Player player = context.getPlayer();
        if (!door.mayOperate(player)) {
            if (player != null) {
                door.noteAccess(player, false);
                player.displayClientMessage(
                        Component.translatable("message.create_stats.garage_door.no_clearance"), true);
            }
            return InteractionResult.SUCCESS;
        }
        final boolean now = door.toggleUnmanned();
        if (player != null) {
            player.displayClientMessage(Component.translatable(now
                    ? "message.create_stats.garage_door.unmanned_on"
                    : "message.create_stats.garage_door.unmanned_off"), true);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COMPARATOR_CLICK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, now ? 1.2f : 0.9f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        final GarageDoorBE door = controllerAt(level, pos, state);
        return door == null ? pos : door.getBlockPos();
    }

    @Nullable
    public static GarageDoorBE controllerAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(FACING)) {
            return null;
        }
        final BlockPos controller = state.getValue(CONTROLLER) ? pos
                : GarageDoorSite.controllerNear(level, pos, state.getValue(FACING));
        if (controller == null) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof GarageDoorBE door ? door : null;
    }

    @Nullable
    public static GarageDoorBE frameHostAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof GarageDoorHeaderBlock)) {
            return null;
        }
        return controllerAt(level, pos, state);
    }

    @Nullable
    public static GarageDoorBE controllerFrom(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof GarageDoorHeaderBlock) {
            return controllerAt(level, pos, state);
        }
        if (!(state.getBlock() instanceof GarageDoorBodyBlock) || !state.hasProperty(FACING)) {
            return null;
        }

        BlockPos probe = pos;
        for (int i = 0; i <= GarageDoorFrame.MAX_DROP; i++) {
            probe = probe.above();
            final BlockState above = level.getBlockState(probe);
            if (above.getBlock() instanceof GarageDoorHeaderBlock) {
                return controllerAt(level, probe, above);
            }
            if (!(above.getBlock() instanceof GarageDoorBodyBlock)) {
                return null;
            }
        }
        return null;
    }

    public static void tell(Player player, GarageDoorFrame.Fault fault) {
        if (fault.ok()) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.create_stats.garage_door." + fault.key()), true);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
