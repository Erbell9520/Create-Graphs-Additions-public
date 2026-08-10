package com.createstats.block;

import com.createstats.blockentity.InductionHeaterBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** [1] */
public class InductionHeaterBlock extends KineticBlock implements IBE<InductionHeaterBE> {

    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 10, 16),
        Block.box(1, 10, 1, 15, 12, 15));

    public InductionHeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlazeBurnerBlock.HEAT_LEVEL);
        super.createBlockStateDefinition(builder);
    }

    public static int getLight(BlockState state) {
        return switch (state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
            case SMOULDERING -> 4;
            case FADING, KINDLED -> 9;
            case SEETHING -> 13;
            default -> 0;
        };
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InductionHeaterBE heater)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (heater.isCoilItem(stack) && !heater.hasCoil()) {
            if (!level.isClientSide()) {
                heater.insertCoil(stack.split(1));
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (heater.isCookableItem(stack)) {
            if (!level.isClientSide()) {
                int accepted = heater.placeFood(stack);
                if (accepted > 0) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(accepted);
                    }
                    level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.7f, 1.3f);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InductionHeaterBE heater)) {
            return InteractionResult.PASS;
        }
        // [2]
        if (player.isShiftKeyDown()) {
            if (heater.hasCoil()) {
                if (!level.isClientSide()) {
                    ItemStack coil = heater.extractCoil();
                    if (!player.getInventory().add(coil)) {
                        player.drop(coil, false);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 0.9f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            return InteractionResult.PASS;
        }
        if (heater.hasCookStack()) {
            if (!level.isClientSide()) {
                ItemStack food = heater.takeFood();
                if (!player.getInventory().add(food)) {
                    player.drop(food, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 1.0f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            withBlockEntityDo(level, pos, be -> {
                be.dropCoil();
                be.dropFood();
            });
        }
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return switch (state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
            case SMOULDERING -> 5;
            case FADING, KINDLED -> 10;
            case SEETHING -> 15;
            default -> 0;
        };
    }

    /** [3] */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        HeatLevel heat = state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
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

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        HeatLevel heat = state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        if (heat == HeatLevel.NONE || heat == HeatLevel.SMOULDERING) {
            return;
        }
        if (heat == HeatLevel.SEETHING) {
            for (int i = 0; i < 2; i++) {
                double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
                double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 0.78, z, 0, 0.02, 0);
            }
        }
        // Food actively sizzling on the plate sends up a thin smoke column.
        if (level.getBlockEntity(pos) instanceof InductionHeaterBE heater
            && heater.hasCookStack() && !heater.isCookDone() && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE,
                pos.getX() + 0.4 + random.nextDouble() * 0.2,
                pos.getY() + 0.85,
                pos.getZ() + 0.4 + random.nextDouble() * 0.2,
                0, 0.015, 0);
        }
        if (random.nextInt(heat == HeatLevel.SEETHING ? 4 : 10) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5,
                SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                heat == HeatLevel.SEETHING ? 0.5f : 0.25f, 1.2f, false);
        }
    }

    @Override
    public Class<InductionHeaterBE> getBlockEntityClass() {
        return InductionHeaterBE.class;
    }

    @Override
    public BlockEntityType<? extends InductionHeaterBE> getBlockEntityType() {
        return ModBlockEntities.INDUCTION_HEATER.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.induction_heater");
    }
}
