package com.createstats.block;

import com.createstats.blockentity.BiofuelBurnerBE;
import com.createstats.power.BiofuelFuel;
import com.createstats.power.BiofuelValue;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BiofuelBurnerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final int LIT_LEVEL = 9;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 1.5, 16.0),
            Block.box(0.25, 1.5, 0.25, 15.75, 14.0, 15.75),
            Block.box(0.25, 14.0, 0.25, 15.75, 15.5, 15.75),
            Block.box(6.0, 15.5, 11.4, 10.0, 16.0, 15.0));

    public BiofuelBurnerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(BiofuelBurnerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BiofuelBurnerBE burner)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!BiofuelFuel.accepts(stack)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.create_stats.biofuel_burner.not_bio"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        final ItemStack left = burner.insertFuel(stack, false);
        if (left.getCount() == stack.getCount()) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.biofuel_burner.full"), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (!player.isCreative()) {
            player.setItemInHand(hand, left);
        }
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BiofuelBurnerBE burner)) {
            return InteractionResult.PASS;
        }
        final ItemStack out = burner.takeFuel();
        if (out.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.biofuel_burner.empty"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getInventory().add(out)) {
            Block.popResource(level, pos, out);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BiofuelBurnerBE burner
                ? burner.comparatorSignal() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof BiofuelBurnerBE burner) {
            burner.dropWiresAndUnlinkAll();
            final ItemStack fuel = burner.takeFuel();
            if (!fuel.isEmpty()) {
                Block.popResource(level, pos, fuel);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        final Direction front = state.getValue(FACING);
        final double cx = pos.getX() + 0.5;
        final double cy = pos.getY();
        final double cz = pos.getZ() + 0.5;
        final double back = -0.28;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                cx + front.getStepX() * back + (random.nextDouble() - 0.5) * 0.2,
                cy + 1.0,
                cz + front.getStepZ() * back + (random.nextDouble() - 0.5) * 0.2,
                0.0, 0.02, 0.0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME,
                    cx + front.getStepX() * 0.46,
                    cy + 0.30 + random.nextDouble() * 0.12,
                    cz + front.getStepZ() * 0.46,
                    0.0, 0.0, 0.0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(cx, cy + 0.5, cz, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.45f + random.nextFloat() * 0.15f, 0.8f + random.nextFloat() * 0.2f, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.biofuel_burner",
                java.util.List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "tooltip.create_stats.biofuel_burner_output",
                        BiofuelValue.OUTPUT_FE_PER_TICK, BiofuelBurnerBE.BUFFER)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.BIOFUEL_BURNER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.BIOFUEL_BURNER.get() || level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((BiofuelBurnerBE) be).serverTick();
    }
}
