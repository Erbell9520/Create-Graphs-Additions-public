package com.createstats.block;

import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.util.ClientScreenAccess;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

import java.util.List;

public class LoadBalancerBlock extends HorizontalKineticBlock implements IBE<LoadBalancerBE> {
    public static final IntegerProperty VISUAL_STATUS = IntegerProperty.create("visual_status", 1, 4);

    public LoadBalancerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(VISUAL_STATUS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VISUAL_STATUS);
    }

    public static Direction getFrontFacing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    public static Direction getInputFacing(BlockState state) {
        return getFrontFacing(state).getOpposite();
    }

    public static Direction getLeftOutputFacing(BlockState state) {
        return getFrontFacing(state).getCounterClockWise();
    }

    public static Direction getRightOutputFacing(BlockState state) {
        return getFrontFacing(state).getClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredHorizontalFacing(context);
        if (preferred != null && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())) {
            return defaultBlockState().setValue(HORIZONTAL_FACING, preferred.getOpposite());
        }
        return super.getStateForPlacement(context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        InteractionResult result = onBlockEntityUse(level, pos, balancer -> {
            if (level.isClientSide()) {
                ClientScreenAccess.openLoadBalancer(balancer);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        });
        if (result == InteractionResult.SUCCESS) return ItemInteractionResult.SUCCESS;
        if (result == InteractionResult.CONSUME) return ItemInteractionResult.CONSUME;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == getInputFacing(state)
                || face == getLeftOutputFacing(state)
                || face == getRightOutputFacing(state);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LoadBalancerBE balancer) {
            return balancer.getAnalogOutputSignal();
        }
        return 0;
    }

    @Override
    public Class<LoadBalancerBE> getBlockEntityClass() {
        return LoadBalancerBE.class;
    }

    @Override
    public BlockEntityType<? extends LoadBalancerBE> getBlockEntityType() {
        return ModBlockEntities.LOAD_BALANCER.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.load_balancer");
    }
}
