package com.createstats.block;

import com.createstats.blockentity.SolarPanelBE;
import com.createstats.power.SolarCurve;
import com.createstats.power.SolarTier;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SolarPanelBlock extends Block implements EntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public static final double HEIGHT = 3.4;

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

    private final SolarTier tier;

    public SolarPanelBlock(Properties properties, SolarTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false));
    }

    public SolarTier tier() {
        return tier;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new SolarPanelBlock(p, SolarTier.REINFORCED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joinsAt(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        final BooleanProperty edge = edgeFor(direction);
        if (edge == null) {
            return state;
        }
        return state.setValue(edge, neighborState.getBlock() instanceof SolarPanelBlock);
    }

    private static BlockState joinsAt(BlockState state, LevelReader level, BlockPos pos) {
        BlockState out = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            final BooleanProperty edge = edgeFor(direction);
            if (edge != null) {
                out = out.setValue(edge,
                        level.getBlockState(pos.relative(direction)).getBlock() instanceof SolarPanelBlock);
            }
        }
        return out;
    }

    @Nullable
    private static BooleanProperty edgeFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> null;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        final int one = SolarCurve.fePerTick(tier.peakFePerTick(), 1, 6_000,
                SolarCurve.Sky.CLEAR, SolarCurve.FULL_SKY_LIGHT);
        final int roof = SolarCurve.fePerTick(tier.peakFePerTick(), 9, 6_000,
                SolarCurve.Sky.CLEAR, SolarCurve.FULL_SKY_LIGHT) * 9;
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats." + tierKey(),
                java.util.List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "tooltip.create_stats.solar_panel_output", one, roof)));
    }

    private String tierKey() {
        return switch (tier) {
            case BASIC -> "solar_panel";
            case REINFORCED -> "reinforced_solar_panel";
            case PHOTOVOLTAIC -> "photovoltaic_array";
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SOLAR_PANEL.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.SOLAR_PANEL.get() || level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((SolarPanelBE) be).serverTick();
    }
}
