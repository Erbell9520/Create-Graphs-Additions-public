package com.createstats.block;

import com.createstats.power.MainBreakerStuds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MainBreakerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");

    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public static final BooleanProperty PASSTHROUGH = BooleanProperty.create("passthrough");

    private final boolean creative;

    public MainBreakerBlock(Properties properties) {
        this(properties, false);
    }

    public MainBreakerBlock(Properties properties, boolean creative) {
        super(properties);
        this.creative = creative;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(TRIPPED, false).setValue(LOCKED, false)
                .setValue(PASSTHROUGH, false));
    }

    public boolean isCreative() {
        return creative;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, TRIPPED, LOCKED, PASSTHROUGH);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LampShapes.rotate(LampShapes.MAIN_BREAKER, state.getValue(FACING));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        final BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos)
                ? state
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.MainBreakerBE breaker)) {
            return InteractionResult.PASS;
        }

        final Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (!player.isSecondaryUseActive() && MainBreakerStuds.isOnSelector(state, local)) {
            if (state.getValue(LOCKED)) {
                level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.7f, 0.75f);
                actionBar(player, "message.create_stats.main_breaker.padlocked");
                return InteractionResult.CONSUME;
            }
            final boolean passthrough = breaker.toggleMode();
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                    0.7f, passthrough ? 1.5f : 1.1f);
            actionBar(player, passthrough
                    ? "message.create_stats.main_breaker.mode_passthrough"
                    : "message.create_stats.main_breaker.mode_metered");
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) {
            final boolean locked = breaker.toggleLock();
            lockSound(level, pos, locked);
            actionBar(player, locked
                    ? "message.create_stats.main_breaker.locked"
                    : "message.create_stats.main_breaker.unlocked");
            return InteractionResult.CONSUME;
        }

        switch (breaker.toggle()) {
            case REFUSED -> {
                level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.7f, 0.75f);
                actionBar(player, "message.create_stats.main_breaker.padlocked");
            }
            case RESET -> {
                resetSound(level, pos);
                actionBar(player, "message.create_stats.main_breaker.reset");
            }
            case CLOSE -> {
                throwSound(level, pos, true);
                if (level instanceof ServerLevel server) {
                    arcFlash(server, state.getValue(FACING), pos, 4, 0.02);
                }
            }
            case OPEN -> throwSound(level, pos, false);
        }
        return InteractionResult.CONSUME;
    }

    private static void actionBar(Player player, String key) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            server.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof com.createstats.item.CopperWireItem
                || com.createstats.compat.ForeignSpools.isWiringItem(stack)) {
            if (state.getValue(LOCKED)) {
                if (!level.isClientSide()) {
                    actionBar(player, "message.create_stats.main_breaker.padlocked_wiring");
                }
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
            return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    public static void tripEffects(Level level, BlockPos pos, Direction facing) {
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.5f);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35f, 1.6f);

        if (level instanceof ServerLevel server) {
            arcFlash(server, facing, pos, 9, 0.05);
        }
    }

    private static void lockSound(Level level, BlockPos pos, boolean locked) {
        level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, locked ? 1.3f : 0.9f);
        level.playSound(null, pos, locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                SoundSource.BLOCKS, 0.42f, locked ? 1.7f : 1.5f);
    }

    private static void resetSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.40f, 0.95f);
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.30f, 1.25f);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return isMade(state) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return isMade(state) && state.getValue(FACING) == direction ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!isMade(state)
                || !(level.getBlockEntity(pos) instanceof com.createstats.blockentity.MainBreakerBE breaker)) {
            return 0;
        }

        if (state.getValue(PASSTHROUGH)) {
            return 0;
        }
        final int rating = breaker.ratingFePerTick();
        if (rating <= 0) {
            return 0;
        }
        final int rate = breaker.getRateFePerTick();
        return rate <= 0 ? 0 : (int) Math.min(15L, 1L + (long) rate * 14L / rating);
    }

    public static boolean isMade(BlockState state) {
        return state.hasProperty(POWERED) && state.getValue(POWERED)
                && state.hasProperty(TRIPPED) && !state.getValue(TRIPPED);
    }

    public static void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        final Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        if (state.hasProperty(FACING)) {
            level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), block);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof com.createstats.blockentity.MainBreakerBE breaker) {
            breaker.dropAllStudWires();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof com.createstats.blockentity.MainBreakerBE breaker) {
                breaker.serverTick();
            }
        };
    }

    private static void throwSound(Level level, BlockPos pos, boolean on) {
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS,
                0.55f, on ? 0.62f : 0.82f);
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS,
                0.34f, on ? 0.72f : 1.05f);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                0.22f, on ? 0.55f : 0.90f);
    }

    private static void arcFlash(ServerLevel level, Direction facing, BlockPos pos, int sparks,
                                 double speed) {
        for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
            final Vec3 at = MainBreakerStuds.anchor(facing, stud);
            final double cx = pos.getX() + at.x + facing.getStepX() * 0.03;
            final double cy = pos.getY() + at.y + facing.getStepY() * 0.03;
            final double cz = pos.getZ() + at.z + facing.getStepZ() * 0.03;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, sparks, 0.05, 0.05, 0.05, speed);
        }
        final boolean fault = sparks > 4;
        level.sendParticles(ParticleTypes.CRIT,
                pos.getX() + 0.5 + facing.getStepX() * 0.3,
                pos.getY() + 0.5 + facing.getStepY() * 0.3,
                pos.getZ() + 0.5 + facing.getStepZ() * 0.3, fault ? 7 : 3, 0.08, 0.08, 0.08, 0.0);
        if (fault) {
            level.sendParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5 + facing.getStepX() * 0.35,
                    pos.getY() + 0.5 + facing.getStepY() * 0.35,
                    pos.getZ() + 0.5 + facing.getStepZ() * 0.35, 4, 0.10, 0.10, 0.10, 0.01);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.MAIN_BREAKER.create(pos, state);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, stack.getItem().getDescriptionId());
    }
}
