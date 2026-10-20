package com.createstats.statsmk2;

import com.createstats.blockentity.StatsDisplayPanelBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class StatsDisplayMk2BlockEntity extends StatsDisplayPanelBE {
    public StatsDisplayMk2BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
