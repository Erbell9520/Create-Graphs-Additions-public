package com.createstats.block;

import com.createstats.blockentity.FlowMeterBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;

// the default pipe interactions would turn it into a plain pipe and eat the meter, so they're off
public class FlowMeterBlock extends AxisPipeBlock implements IBE<FlowMeterBE>, SimpleWaterloggedBlock {
    public FlowMeterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
        return this.defaultBlockState()
                .setValue(AXIS, this.chooseAxis(context))
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
    }

    private Axis chooseAxis(BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        Axis best = null;
        int bestScore = -1;
        for (final Axis axis : Axis.values()) {
            int score = 0;
            for (final AxisDirection ad : AxisDirection.values()) {
                final Direction d = Direction.get(ad, axis);
                if (this.connectsTo(level, pos.relative(d), d)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = axis;
            }
        }
        if (bestScore > 0 && best != null) {
            return best;
        }
        return context.getNearestLookingDirection().getAxis();
    }

    private boolean connectsTo(Level level, BlockPos pos, Direction fromDir) {
        if (FluidPropagator.getPipe(level, pos) != null) {
            return true;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, fromDir.getOpposite()) != null;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public MapCodec<? extends FlowMeterBlock> codec() {
        return simpleCodec(FlowMeterBlock::new);
    }

    @Override
    public Class<FlowMeterBE> getBlockEntityClass() {
        return FlowMeterBE.class;
    }

    @Override
    public BlockEntityType<? extends FlowMeterBE> getBlockEntityType() {
        return ModBlockEntities.FLOW_METER.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
            Player player) {
        return ModBlocks.FLOW_METER.asStack();
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FlowMeterBE meter ? meter.getEmittedRedstone() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.flow_meter");
    }
}
