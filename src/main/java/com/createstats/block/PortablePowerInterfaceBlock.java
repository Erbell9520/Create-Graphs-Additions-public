package com.createstats.block;

import com.createstats.blockentity.PortablePowerInterfaceBE;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class PortablePowerInterfaceBlock extends WrenchableDirectionalBlock implements IBE<PortablePowerInterfaceBE> {
    public static final MapCodec<PortablePowerInterfaceBlock> CODEC = simpleCodec(PortablePowerInterfaceBlock::new);

    public PortablePowerInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                boolean moving) {
        withBlockEntityDo(level, pos, PortablePowerInterfaceBE::neighbourChanged);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getNearestLookingDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            direction = direction.getOpposite();
        }
        return defaultBlockState().setValue(FACING, direction.getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.PORTABLE_STORAGE_INTERFACE.get(state.getValue(FACING));
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos).map(PortablePowerInterfaceBE::comparatorLevel).orElse(0);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final int ceiling = PortablePowerInterfaceBE.ceiling();
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.portable_power_interface",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "block.create_stats.portable_power_interface.tooltip.rate",
                        com.createstats.power.FeRates.perTick(com.createstats.power.PortableFeLadder.feAt(0, ceiling)),
                        com.createstats.power.FeRates.perTick(com.createstats.power.PortableFeLadder.feAt(
                                com.createstats.power.PortableFeLadder.maxDial(ceiling), ceiling)))));
    }

    @Override
    public Class<PortablePowerInterfaceBE> getBlockEntityClass() {
        return PortablePowerInterfaceBE.class;
    }

    @Override
    public BlockEntityType<? extends PortablePowerInterfaceBE> getBlockEntityType() {
        return ModBlockEntities.PORTABLE_POWER_INTERFACE.get();
    }
}
