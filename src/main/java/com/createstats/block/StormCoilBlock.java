package com.createstats.block;

import com.createstats.blockentity.StormCoilBE;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

// never a full cube, strikes need to land on the crown
public class StormCoilBlock extends HorizontalDirectionalBlock implements IBE<StormCoilBE>, IWrenchable {
    public static final MapCodec<StormCoilBlock> CODEC = simpleCodec(StormCoilBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(5, 10, 5, 11, 16, 11));

    public StormCoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static int turns(BlockState state) {
        return state.hasProperty(FACING) ? Math.floorMod(state.getValue(FACING).get2DDataValue() - 2, 4) : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide()) {
            final boolean powered = level.hasNeighborSignal(pos);
            withBlockEntityDo(level, pos, be -> be.setRedstone(powered));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide()) {
            final boolean powered = level.hasNeighborSignal(pos);
            withBlockEntityDo(level, pos, be -> be.setRedstone(powered));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            withBlockEntityDo(level, pos, StormCoilBE::onBroken);
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.storm_coil.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.create_stats.storm_coil.tooltip.studs")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Class<StormCoilBE> getBlockEntityClass() {
        return StormCoilBE.class;
    }

    @Override
    public BlockEntityType<? extends StormCoilBE> getBlockEntityType() {
        return ModBlockEntities.STORM_COIL.get();
    }
}
