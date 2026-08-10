package com.createstats.statsmk2;

import java.util.List;
import javax.annotation.Nullable;

import com.createstats.block.StatsDisplayPanelBlock;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

/** [1] */
public class StatsDisplayMk2Block extends StatsDisplayPanelBlock {

    public StatsDisplayMk2Block(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(StatsDisplayMk2Block::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatsDisplayMk2BlockEntity((BlockEntityType<?>) ModBlockEntities.STATS_DISPLAY_MK2.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.STATS_DISPLAY_MK2.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((StatsDisplayPanelBE) be).tick();
    }

    @Override
    protected void openConfigScreen(StatsDisplayPanelBE panel) {
        ClientScreenAccess.openStatsDisplayMk2(panel);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.stats_display_mk2");
    }
}
