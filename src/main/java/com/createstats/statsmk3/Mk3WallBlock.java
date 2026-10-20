package com.createstats.statsmk3;

import java.util.List;
import javax.annotation.Nullable;

import com.createstats.block.StatsDisplayPanelBlock;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class Mk3WallBlock extends StatsDisplayPanelBlock {
    public Mk3WallBlock(BlockBehaviour.Properties properties) {
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
        return simpleCodec(Mk3WallBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Mk3PanelBlockEntity(
                (BlockEntityType<?>) ModBlockEntities.STATS_DISPLAY_MK3.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.STATS_DISPLAY_MK3.get()) {
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
                tooltip, "block.create_stats.stats_display_mk3");
    }
}
