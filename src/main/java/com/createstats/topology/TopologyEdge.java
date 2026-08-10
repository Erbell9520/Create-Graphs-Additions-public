package com.createstats.topology;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

public class TopologyEdge
{
    private final BlockPos from;
    private final BlockPos to;
    private final float speedRatio;
    private final float stressTransfer;
    
    public TopologyEdge(BlockPos from, BlockPos to, float speedRatio, float stressTransfer) {
        this.from = from;
        this.to = to;
        this.speedRatio = speedRatio;
        this.stressTransfer = stressTransfer;
    }
    
    public BlockPos getFrom() {
        return this.from;
    }
    
    public BlockPos getTo() {
        return this.to;
    }
    
    public float getSpeedRatio() {
        return this.speedRatio;
    }
    
    public float getStressTransfer() {
        return this.stressTransfer;
    }
    
    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("From", this.from.asLong());
        tag.putLong("To", this.to.asLong());
        tag.putFloat("SpeedRatio", this.speedRatio);
        tag.putFloat("StressTransfer", this.stressTransfer);
        return tag;
    }
    
    public static TopologyEdge load(CompoundTag tag) {
        return new TopologyEdge(BlockPos.of(tag.getLong("From")), BlockPos.of(tag.getLong("To")), tag.getFloat("SpeedRatio"), tag.getFloat("StressTransfer"));
    }
}
