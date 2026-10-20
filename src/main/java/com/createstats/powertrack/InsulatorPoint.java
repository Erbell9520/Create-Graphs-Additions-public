package com.createstats.powertrack;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint;

public class InsulatorPoint extends SingleBlockEntityEdgePoint {
    @Override
    public boolean canCoexistWith(EdgePointType<?> otherType, boolean front) {
        return otherType == PowerTrack.FEEDER;
    }
}
