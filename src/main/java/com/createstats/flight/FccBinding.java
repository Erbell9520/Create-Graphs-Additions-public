package com.createstats.flight;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/** [1] */
public record FccBinding(BlockPos prop, BlockPos governor,
                         boolean calibratedGainValid,
                         double calibratedK,
                         double yawFactor) {

    public FccBinding(BlockPos prop, BlockPos governor) {
        this(prop, governor, false, 0.0, 0.0);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Prop", prop.asLong());
        tag.putLong("Governor", governor.asLong());
        if (calibratedGainValid) tag.putDouble("CommandK", calibratedK);
        if (yawFactor != 0.0) tag.putDouble("YawFactor", yawFactor);
        return tag;
    }

    @Nullable
    public static FccBinding fromNbt(CompoundTag tag) {
        if (tag == null || !tag.contains("Prop") || !tag.contains("Governor")) return null;
        BlockPos prop = BlockPos.of(tag.getLong("Prop"));
        BlockPos gov = BlockPos.of(tag.getLong("Governor"));
        boolean hasCal = tag.contains("CommandK");
        double calK = hasCal ? tag.getDouble("CommandK") : 0.0;
        double yaw = tag.contains("YawFactor") ? tag.getDouble("YawFactor") : 0.0;
        return new FccBinding(prop, gov, hasCal, calK, yaw);
    }
}
