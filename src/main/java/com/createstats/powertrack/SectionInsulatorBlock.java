package com.createstats.powertrack;

import com.createstats.registry.ModBlockEntities;
import com.createstats.util.CreateStatsTooltipHelper;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
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

public class SectionInsulatorBlock extends HorizontalDirectionalBlock implements IBE<SectionInsulatorBE>, IWrenchable {
    public static final MapCodec<SectionInsulatorBlock> CODEC = simpleCodec(SectionInsulatorBlock::new);

    // a quarter pixel clear of create's sleepers which overhang into this block
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(5, 0, 5, 11, 1.75, 11),
            Block.box(7.3, 1.75, 7.3, 8.7, 9.25, 8.7), Block.box(2, 9.25, 7.4, 14, 14.5, 8.6),
            Block.box(2.4, 14.5, 6.45, 5.6, 16.85, 9.55), Block.box(10.4, 14.5, 6.45, 13.6, 16.85, 9.55));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(5, 0, 5, 11, 1.75, 11),
            Block.box(7.3, 1.75, 7.3, 8.7, 9.25, 8.7), Block.box(7.4, 9.25, 2, 8.6, 14.5, 14),
            Block.box(6.45, 14.5, 2.4, 9.55, 16.85, 5.6), Block.box(6.45, 14.5, 10.4, 9.55, 16.85, 13.6));

    public SectionInsulatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.section_insulator");
    }

    @Override
    public Class<SectionInsulatorBE> getBlockEntityClass() {
        return SectionInsulatorBE.class;
    }

    @Override
    public BlockEntityType<? extends SectionInsulatorBE> getBlockEntityType() {
        return ModBlockEntities.SECTION_INSULATOR.get();
    }
}
