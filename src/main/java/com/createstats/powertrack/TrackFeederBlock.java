package com.createstats.powertrack;

import com.createstats.blockentity.TrackFeederBE;
import com.createstats.power.FeRates;
import com.createstats.power.PortableFeLadder;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TrackFeederBlock extends HorizontalDirectionalBlock implements IBE<TrackFeederBE>, IWrenchable {
    public static final MapCodec<TrackFeederBlock> CODEC = simpleCodec(TrackFeederBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES.put(d, Shapes.or(box(d, 1, 0, 2.5, 15, 1.75, 13.5), box(d, 2.5, 1.75, 3.5, 13.5, 15, 12),
                    box(d, 2, 15, 3.25, 14, 16, 12.5), box(d, 6.25, 16, 6.25, 9.75, 19.5, 9.75),
                    box(d, 7.35, 5.8, 12, 8.65, 7.1, 16)));
        }
    }

    public TrackFeederBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        final net.minecraft.world.phys.Vec3 a = TrackFeederValueBox.turn(facing, x0 / 16, y0 / 16, z0 / 16);
        final net.minecraft.world.phys.Vec3 b = TrackFeederValueBox.turn(facing, x1 / 16, y1 / 16, z1 / 16);
        return Shapes.box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
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
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final int ceiling = TrackFeederBE.ceiling();
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.track_feeder",
                List.of(CreateStatsTooltipHelper.spec("block.create_stats.track_feeder.tooltip.rate",
                        FeRates.perTick(PortableFeLadder.feAt(0, ceiling)),
                        FeRates.perTick(PortableFeLadder.feAt(PortableFeLadder.maxDial(ceiling), ceiling)))));
    }

    @Override
    public Class<TrackFeederBE> getBlockEntityClass() {
        return TrackFeederBE.class;
    }

    @Override
    public BlockEntityType<? extends TrackFeederBE> getBlockEntityType() {
        return ModBlockEntities.TRACK_FEEDER.get();
    }
}
