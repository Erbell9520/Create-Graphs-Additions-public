package com.createstats.block;

import com.createstats.blockentity.VacuumSnifferBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.vacuum.SuctionReach;
import com.createstats.vacuum.TypeSlots;
import com.createstats.vacuum.VacuumDraw;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

public class VacuumSnifferBlock extends DirectionalBlock implements IBE<VacuumSnifferBE>, IWrenchable {
    public static final MapCodec<VacuumSnifferBlock> CODEC = simpleCodec(VacuumSnifferBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = VacuumShapes.mounted(
            new double[] {1, 0, 1.5, 15, 1, 15.5},
            new double[] {2.25, 1, 2.75, 13.75, 7.5, 14.25},
            new double[] {3.25, 7.5, 4.0, 12.75, 12.25, 13.0});

    public VacuumSnifferBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VacuumSnifferBE sniffer) {
            for (ItemStack stack : sniffer.dropContents()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.vacuum_sniffer.tooltip", TypeSlots.SLOTS,
                VacuumDraw.SNIFFER_IN_FLIGHT, SuctionReach.SNIFFER_REACH).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Class<VacuumSnifferBE> getBlockEntityClass() {
        return VacuumSnifferBE.class;
    }

    @Override
    public BlockEntityType<? extends VacuumSnifferBE> getBlockEntityType() {
        return ModBlockEntities.VACUUM_SNIFFER.get();
    }
}
