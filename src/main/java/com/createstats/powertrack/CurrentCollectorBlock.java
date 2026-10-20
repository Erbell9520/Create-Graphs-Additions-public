package com.createstats.powertrack;

import com.createstats.power.FeRates;
import com.createstats.powertrack.core.RailPower;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.CreateStatsTooltipHelper;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

// hardwood beam, real shoe beams are wood because wood insulates
public class CurrentCollectorBlock extends Block implements IBE<CurrentCollectorBE>, IWrenchable {
    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(3, 15, 2, 13, 16, 14), Block.box(4, 6, 4, 12, 15, 12));
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(2, 15, 3, 14, 16, 13), Block.box(4, 6, 4, 12, 15, 12));

    public static void registerHanging() {
        com.simibubi.create.api.contraption.BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) ->
                state.getBlock() instanceof CurrentCollectorBlock && direction == Direction.UP
                        ? com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.SUCCESS
                        : com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS);
    }

    public CurrentCollectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(BlockStateProperties.HORIZONTAL_AXIS));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,
                context.getHorizontalDirection().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(BlockStateProperties.HORIZONTAL_AXIS,
                    state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X
                            ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final int base = FeRates.base();
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.current_collector",
                List.of(CreateStatsTooltipHelper.spec("block.create_stats.current_collector.tooltip.rate",
                        FeRates.perTick(RailPower.collectorRate(base)), RailPower.MAX_COLLECTORS_PER_CAR,
                        FeRates.perTick(RailPower.carRate(base, RailPower.MAX_COLLECTORS_PER_CAR)))));
    }

    @Override
    public Class<CurrentCollectorBE> getBlockEntityClass() {
        return CurrentCollectorBE.class;
    }

    @Override
    public BlockEntityType<? extends CurrentCollectorBE> getBlockEntityType() {
        return ModBlockEntities.CURRENT_COLLECTOR.get();
    }
}
