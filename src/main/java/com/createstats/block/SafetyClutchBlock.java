package com.createstats.block;

import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

import java.util.List;

public class SafetyClutchBlock extends AbstractEncasedShaftBlock implements IBE<SafetyClutchBE> {
    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");

    public SafetyClutchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TRIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIPPED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean trip = player.isShiftKeyDown();
        if (!trip && !state.getValue(TRIPPED)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            onBlockEntityUse(level, pos, clutch -> {
                if (trip) {
                    clutch.manualTrip();
                } else {
                    clutch.manualReset();
                }
                return InteractionResult.SUCCESS;
            });

            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, trip ? 0.5f : 0.7f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(TRIPPED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(TRIPPED) ? 15 : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SafetyClutchBE clutch) {
            return clutch.getAnalogOutputSignal();
        }
        return 0;
    }

    public void detachKinetics(Level level, BlockPos pos, boolean reAttachNextTick) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleRemoved(level, pos, kbe);
        if (reAttachNextTick) {
            level.scheduleTick(pos, this, 1, TickPriority.EXTREMELY_HIGH);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KineticBlockEntity kbe) {
            RotationPropagator.handleAdded(level, pos, kbe);
            if (be instanceof SafetyClutchBE clutch) {
                clutch.syncNetworkState();
            } else if (kbe.hasNetwork()) {
                kbe.getOrCreateNetwork().updateNetwork();
            }
        }
    }

    @Override
    public Class<SafetyClutchBE> getBlockEntityClass() {
        return SafetyClutchBE.class;
    }

    @Override
    public BlockEntityType<? extends SafetyClutchBE> getBlockEntityType() {
        return ModBlockEntities.SAFETY_CLUTCH.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.safety_clutch");
    }
}
