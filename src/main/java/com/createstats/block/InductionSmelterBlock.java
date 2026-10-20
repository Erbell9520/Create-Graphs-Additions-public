package com.createstats.block;

import com.createstats.blockentity.InductionSmelterBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InductionSmelterBlock extends Block implements EntityBlock {
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public InductionSmelterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(HOT, false)
                .setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, HOT, WORKING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    public static int getLight(BlockState state) {
        if (!state.getValue(HOT)) {
            return 0;
        }
        return state.getValue(WORKING) ? 9 : 5;
    }

    private static final double[] LEVER_ZONE = {11.5, 4.0, 16.0, 9.5};

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof InductionSmelterBE smelter)) {
            return InteractionResult.PASS;
        }
        if (onLever(state, pos, hit)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            final int detent = smelter.cycleDamper();
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f,
                    0.7f + detent * 0.22f);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(smelter, pos);
        }
        return InteractionResult.CONSUME;
    }

    private static boolean onLever(BlockState state, BlockPos pos, BlockHitResult hit) {
        final Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        if (hit.getDirection() != facing) {
            return false;
        }
        final Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        double dx = local.x * 16.0 - 8.0;
        double dz = local.z * 16.0 - 8.0;
        final int steps = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < steps; i++) {
            final double t = dx;
            dx = dz;
            dz = -t;
        }
        final double mx = dx + 8.0;
        final double my = local.y * 16.0;
        return mx >= LEVER_ZONE[0] && mx <= LEVER_ZONE[2]
                && my >= LEVER_ZONE[1] && my <= LEVER_ZONE[3];
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof InductionSmelterBE smelter) {
            smelter.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof InductionSmelterBE smelter)) {
            return 0;
        }
        final float fraction = smelter.batchFraction();
        return fraction <= 0.0f ? 0 : 1 + Math.round(fraction * 14.0f);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HOT)) {
            return;
        }
        final boolean busy = state.getValue(WORKING);
        final double cx = pos.getX() + 0.5;
        final double cz = pos.getZ() + 0.5;
        final double vent = pos.getY() + 1.0;

        if (random.nextInt(busy ? 3 : 8) == 0) {
            level.addParticle(ParticleTypes.SMOKE,
                    cx + (random.nextDouble() - 0.5) * 0.32, vent,
                    cz + (random.nextDouble() - 0.5) * 0.32,
                    0.0, 0.015 + random.nextDouble() * 0.02, 0.0);
        }
        if (busy && random.nextInt(9) == 0) {
            level.playLocalSound(cx, pos.getY() + 0.6, cz, SoundEvents.BLASTFURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.22f, 1.6f, false);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.INDUCTION_SMELTER.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (type != com.createstats.registry.ModBlockEntities.INDUCTION_SMELTER.get()) {
            return null;
        }

        if (level.isClientSide()) {
            return (lvl, pos, blockState, be) -> ((InductionSmelterBE) be).clientTick();
        }
        return (lvl, pos, blockState, be) -> ((InductionSmelterBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.induction_smelter");
    }
}
