package com.createstats.blockentity;

import com.createstats.data.MetricType;
import net.minecraft.core.BlockPos;

public interface IAutoDataReceiver
{
    void onAutoData(BlockPos p0, String p1, String p2, String p3, float p4, MetricType p5);
    
    void setSourceType(String p0);
    
    SourceConfig registerOrUpdateSource(BlockPos p0, String p1);
}
