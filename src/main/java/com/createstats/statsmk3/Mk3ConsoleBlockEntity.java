package com.createstats.statsmk3;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// hard veto on merge, not a flag a stale save could clear
public class Mk3ConsoleBlockEntity extends Mk3PanelBlockEntity {
    public Mk3ConsoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean isMultiblockMergeDisabled() {
        return true;
    }

    @Override
    public int layoutCols() {
        return com.createstats.statsmk3.layout.GridSpec.consoleCols();
    }

    @Override
    public int layoutRows() {
        return com.createstats.statsmk3.layout.GridSpec.consoleRows();
    }
}
