package com.createstats.statsmk3;

import java.util.List;
import javax.annotation.Nullable;

import com.createstats.block.StatsDisplayPanelBlock;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Mk3ConsoleBlock extends StatsDisplayPanelBlock {
    private static final VoxelShape BASE = Block.box(2.0, 0.0, 3.0, 14.0, 9.0, 14.0);
    private static final VoxelShape HOUSING_NORTH = Block.box(1.0, 9.0, 4.0, 15.0, 14.0, 15.0);
    private static final VoxelShape HOUSING_SOUTH = Block.box(1.0, 9.0, 1.0, 15.0, 14.0, 12.0);
    private static final VoxelShape HOUSING_EAST = Block.box(1.0, 9.0, 1.0, 12.0, 14.0, 15.0);
    private static final VoxelShape HOUSING_WEST = Block.box(4.0, 9.0, 1.0, 15.0, 14.0, 15.0);

    private static final VoxelShape SHAPE_NORTH = Shapes.join(BASE, HOUSING_NORTH, BooleanOp.OR);
    private static final VoxelShape SHAPE_SOUTH = Shapes.join(BASE, HOUSING_SOUTH, BooleanOp.OR);
    private static final VoxelShape SHAPE_EAST = Shapes.join(BASE, HOUSING_EAST, BooleanOp.OR);
    private static final VoxelShape SHAPE_WEST = Shapes.join(BASE, HOUSING_WEST, BooleanOp.OR);

    public Mk3ConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.defaultBlockState().setValue(BlockStateProperties.LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(Mk3ConsoleBlock::new);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(StatsDisplayPanelBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch ((Direction) state.getValue(HorizontalDirectionalBlock.FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Mk3ConsoleBlockEntity(
                (BlockEntityType<?>) ModBlockEntities.STATS_DISPLAY_MK3_CONSOLE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.STATS_DISPLAY_MK3_CONSOLE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((StatsDisplayPanelBE) be).tick();
    }

    @Override
    protected void openConfigScreen(StatsDisplayPanelBE panel) {
        ClientScreenAccess.openStatsDisplayMk3(panel);
    }

    @Override
    protected void onShiftUse(Level level, StatsDisplayPanelBE controller, Player player) {
        if (!(controller instanceof Mk3PanelBlockEntity mk3)) {
            super.onShiftUse(level, controller, player);
            return;
        }
        player.displayClientMessage(Component.translatable(
                "message.create_stats.stats_panel.mk3_range", mk3.cycleTimeRange()), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(
                tooltip, "block.create_stats.stats_display_mk3_console");
    }
}
