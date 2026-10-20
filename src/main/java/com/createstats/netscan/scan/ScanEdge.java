package com.createstats.netscan.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ScanEdge {
    private final BlockPos from;
    private final BlockPos to;
    private final float speedRatio;

    public ScanEdge(BlockPos from, BlockPos to, float speedRatio) {
        this.from = from.immutable();
        this.to = to.immutable();
        this.speedRatio = speedRatio;
    }

    public BlockPos from() {
        return this.from;
    }

    public BlockPos to() {
        return this.to;
    }

    public float speedRatio() {
        return this.speedRatio;
    }

    public boolean isRatioChange() {
        return Math.abs(Math.abs(this.speedRatio) - 1.0f) > 1.0e-4f;
    }

    public boolean isReversal() {
        return this.speedRatio < 0.0f && !isRatioChange();
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("From", this.from.asLong());
        tag.putLong("To", this.to.asLong());
        tag.putFloat("Ratio", this.speedRatio);
        return tag;
    }

    public static ScanEdge load(CompoundTag tag) {
        return new ScanEdge(BlockPos.of(tag.getLong("From")), BlockPos.of(tag.getLong("To")),
                tag.getFloat("Ratio"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScanEdge e && this.from.equals(e.from) && this.to.equals(e.to);
    }

    @Override
    public int hashCode() {
        return this.from.hashCode() * 31 + this.to.hashCode();
    }

    @Override
    public String toString() {
        return this.from.toShortString() + " -> " + this.to.toShortString() + " x" + this.speedRatio;
    }
}
