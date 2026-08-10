package com.createstats.block;

import com.createstats.blockentity.InductionRangeBE;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** [1] */
public class InductionRangeBlock extends Block implements EntityBlock {

    /** True when the supply cannot hold the dial setting, so the hob is running cooler than asked. */
    public static final BooleanProperty STARVED = BooleanProperty.create("starved");

    /** Base, body and plate are one solid box to 11; the element stands proud of it to 12.4. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(3.2, 11, 3.2, 12.8, 13.6, 12.8));

    public InductionRangeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(STARVED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlazeBurnerBlock.HEAT_LEVEL, HorizontalDirectionalBlock.FACING, STARVED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    /** Light output rises with the element, so a hob on high lights the room it is in. */
    public static int getLight(BlockState state) {
        return switch (state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
            case SMOULDERING -> 3;
            case FADING, KINDLED -> 8;
            case SEETHING -> 13;
            default -> 0;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof InductionRangeBE range) || !range.isCookable(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            final int taken = range.placeFood(stack);
            if (taken > 0) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(taken);
                }
                level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.7f, 1.3f);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** [2] */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof InductionRangeBE range)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (!range.hasFood()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                final ItemStack food = range.takeFood();
                if (!player.getInventory().add(food)) {
                    player.drop(food, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 1.0f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            range.cycleSetting();
            // Pitch rises with the setting, so the dial can be worked by ear without looking at it.
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                    0.5f, 0.8f + 0.2f * range.setting());
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.induction_range.setting_" + range.setting(),
                    range.draw()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof InductionRangeBE range) {
            range.dropFood();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // redstone

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Reads out the heat actually being managed, not the heat that was asked for. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return switch (state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
            case SMOULDERING -> 5;
            case FADING, KINDLED -> 10;
            case SEETHING -> 15;
            default -> 0;
        };
    }

    // hazard

    /** [3] */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        final HeatLevel heat = state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        if (heat.isAtLeast(HeatLevel.FADING) && !entity.isSteppingCarefully()
                && entity instanceof LivingEntity living && !living.fireImmune()) {
            if (heat == HeatLevel.SEETHING) {
                living.hurt(level.damageSources().hotFloor(), 2.0f);
                living.igniteForSeconds(3.0f);
            } else {
                living.hurt(level.damageSources().hotFloor(), 1.0f);
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    // idle animation

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        final HeatLevel heat = state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        if (state.getValue(STARVED) && random.nextInt(12) == 0) {
            // A brown-out sputters. One spark, irregularly, is enough to say "this is not getting
            // what it asked for" without competing with the working animation of a healthy hob.
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.7,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.01, 0.0);
        }
        if (heat == HeatLevel.NONE) {
            return;
        }
        if (heat == HeatLevel.SEETHING && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME,
                    pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.70,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
        }
        if (level.getBlockEntity(pos) instanceof InductionRangeBE range
                && range.hasFood() && !range.isCookDone() && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE,
                    pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 0.75,
                    pos.getZ() + 0.4 + random.nextDouble() * 0.2, 0.0, 0.015, 0.0);
        }
        if (heat.isAtLeast(HeatLevel.KINDLED) && random.nextInt(heat == HeatLevel.SEETHING ? 5 : 12) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    heat == HeatLevel.SEETHING ? 0.45f : 0.22f, 1.5f, false);
        }
    }

    // block entity

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.INDUCTION_RANGE.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.createstats.registry.ModBlockEntities.INDUCTION_RANGE.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((InductionRangeBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.induction_range");
    }
}
