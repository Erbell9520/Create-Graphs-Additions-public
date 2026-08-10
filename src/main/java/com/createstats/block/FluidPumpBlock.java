package com.createstats.block;

import com.createstats.blockentity.FluidPumpBE;
import com.createstats.power.PumpTier;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** [1] */
public class FluidPumpBlock extends Block implements IBE<FluidPumpBE>, IWrenchable {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final PumpTier tier;

    public FluidPumpBlock(Properties properties, PumpTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Which grade this is. Read by the block entity for its throughput ceiling and dial range. */
    public PumpTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        // [2]
        Direction best = null;
        int bestScore = -1;
        for (final Direction face : Direction.values()) {
            int score = 0;
            if (connectsTo(level, pos.relative(face), face)) {
                score++;
            }
            if (connectsTo(level, pos.relative(face.getOpposite()), face.getOpposite())) {
                score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = face;
            }
        }
        if (bestScore > 0 && best != null) {
            return defaultBlockState().setValue(FACING, best);
        }
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    private boolean connectsTo(Level level, BlockPos pos, Direction fromDir) {
        if (FluidPropagator.getPipe(level, pos) != null) {
            return true;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, fromDir.getOpposite()) != null;
    }

    // wrench

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        final Direction facing = originalState.getValue(FACING);
        // Wrenching the pipe faces flips the pump and reverses the flow, like flipping a Mechanical
        // Pump; wrenching a side spins it about that side.
        if (targetedFace.getAxis() == facing.getAxis()) {
            return originalState.setValue(FACING, facing.getOpposite());
        }
        return originalState.setValue(FACING, facing.getClockWise(targetedFace.getAxis()));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final InteractionResult result = IWrenchable.super.onWrenched(state, context);
        // Rotating changes which axis the pump drives, so the pipe runs either side have to be told.
        if (result.consumesAction() && !context.getLevel().isClientSide()) {
            final BlockPos pos = context.getClickedPos();
            FluidPropagator.propagateChangedPipe(context.getLevel(), pos,
                    context.getLevel().getBlockState(pos));
        }
        return result;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FluidPumpBE pump) {
                pump.dropWiresAndUnlinkAll();   // return the Copper Wires strung to its built-in connector
            }
            if (!level.isClientSide()) {
                FluidPropagator.propagateChangedPipe(level, pos, state);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.fluid_pump.tooltip",
                        tier.maxFlowMbPerTick())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public MapCodec<? extends FluidPumpBlock> codec() {
        return simpleCodec(p -> new FluidPumpBlock(p, tier));
    }

    @Override
    public Class<FluidPumpBE> getBlockEntityClass() {
        return FluidPumpBE.class;
    }

    @Override
    public BlockEntityType<? extends FluidPumpBE> getBlockEntityType() {
        return ModBlockEntities.FLUID_PUMP.get();
    }
}
