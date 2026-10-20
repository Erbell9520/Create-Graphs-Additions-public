package com.createstats.power;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface FeStationLoad {
    void acceptBoardFeed(FeStationFeed feed);

    @Nullable
    BlockPos studFarEnd(int stud);
}
