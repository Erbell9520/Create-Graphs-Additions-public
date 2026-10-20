package com.createstats.block;

import com.createstats.blockentity.ArcFurnaceBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ArcFurnaceBlock extends Block implements EntityBlock {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty ARC = BooleanProperty.create("arc");
    public static final BooleanProperty STARVED = BooleanProperty.create("starved");

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 13, 16),
            Block.box(4, 13, 4, 12, 16, 12));

    public ArcFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(RUNNING, false)
                .setValue(ARC, false)
                .setValue(STARVED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, RUNNING, ARC, STARVED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static int getLight(BlockState state) {
        if (!state.getValue(RUNNING)) {
            return 0;
        }
        if (!state.getValue(ARC)) {
            return 3;
        }
        return state.getValue(STARVED) ? 9 : 14;
    }

    private static final double[] HATCH_ZONE = {4.0, 3.0, 12.0, 8.0};

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ArcFurnaceBE furnace)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (onHatch(state, pos, hit)) {
            final boolean open = furnace.toggleHatch();
            level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN
                    : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.8f);
            return InteractionResult.CONSUME;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(furnace, pos);
        }
        return InteractionResult.CONSUME;
    }

    private static boolean onHatch(BlockState state, BlockPos pos, BlockHitResult hit) {
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
        return mx >= HATCH_ZONE[0] && mx <= HATCH_ZONE[2]
                && my >= HATCH_ZONE[1] && my <= HATCH_ZONE[3];
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof ArcFurnaceBE furnace) {
            furnace.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ArcFurnaceBE furnace)) {
            return 0;
        }
        final float fraction = furnace.batchFraction();
        return fraction <= 0.0f ? 0 : 1 + Math.round(fraction * 14.0f);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) {
            return;
        }
        final boolean arcing = state.getValue(ARC);
        final boolean starved = state.getValue(STARVED);
        final double cx = pos.getX() + 0.5;
        final double cz = pos.getZ() + 0.5;

        final double rim = pos.getY() + 13.0 / 16.0;

        if (starved && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    cx + (random.nextDouble() - 0.5) * 0.4, rim,
                    cz + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
        }
        if (arcing) {
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        cx + (random.nextDouble() - 0.5) * 0.4, rim,
                        cz + (random.nextDouble() - 0.5) * 0.4,
                        (random.nextDouble() - 0.5) * 0.03, 0.04, (random.nextDouble() - 0.5) * 0.03);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.LAVA,
                        cx + (random.nextDouble() - 0.5) * 0.3, rim,
                        cz + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
            }
            if (random.nextInt(starved ? 10 : 5) == 0) {
                level.playLocalSound(cx, pos.getY() + 0.7, cz, SoundEvents.BLASTFURNACE_FIRE_CRACKLE,
                        SoundSource.BLOCKS, starved ? 0.25f : 0.55f, 0.6f, false);
            }
        }

        if (arcing && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    cx + (random.nextDouble() - 0.5) * 0.45, rim + 0.05,
                    cz + (random.nextDouble() - 0.5) * 0.45,
                    0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.ARC_FURNACE.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (type != com.createstats.registry.ModBlockEntities.ARC_FURNACE.get()) {
            return null;
        }

        if (level.isClientSide()) {
            return (lvl, pos, blockState, be) -> ((ArcFurnaceBE) be).clientTick();
        }
        return (lvl, pos, blockState, be) -> ((ArcFurnaceBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.arc_furnace");
    }
}
