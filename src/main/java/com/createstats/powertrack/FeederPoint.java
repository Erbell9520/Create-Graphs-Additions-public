package com.createstats.powertrack;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

// create sends edge points without the owner block, so send our pos
public class FeederPoint extends SingleBlockEntityEdgePoint {
    @Override
    public boolean canCoexistWith(EdgePointType<?> otherType, boolean front) {
        return otherType == PowerTrack.INSULATOR;
    }

    @Override
    public void write(FriendlyByteBuf buffer, DimensionPalette dimensions) {
        super.write(buffer, dimensions);
        buffer.writeBlockPos(blockEntityPos == null ? BlockPos.ZERO : blockEntityPos);
    }

    @Override
    public void read(FriendlyByteBuf buffer, DimensionPalette dimensions) {
        super.read(buffer, dimensions);
        blockEntityPos = buffer.readBlockPos();
    }
}
