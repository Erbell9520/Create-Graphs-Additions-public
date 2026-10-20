package com.createstats.topology;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

public class TopologyNode
{
    private final BlockPos pos;
    private final String name;
    private final NodeType type;
    private float speed;
    private float stressImpact;
    private float capacityProvided;
    private boolean isSource;
    private boolean isRootLike;
    private boolean isOverStressed;
    private final BlockPos upstreamPos;

    public TopologyNode(BlockPos pos, String name, NodeType type, float speed, float stressImpact, float capacityProvided, boolean isSource, boolean isOverStressed) {
        this(pos, name, type, speed, stressImpact, capacityProvided, isSource, isSource || type.isSourceType(), isOverStressed, null);
    }

    public TopologyNode(BlockPos pos, String name, NodeType type, float speed, float stressImpact, float capacityProvided, boolean isSource, boolean isRootLike, boolean isOverStressed, BlockPos upstreamPos) {
        this.pos = pos;
        this.name = name;
        this.type = type;
        this.speed = speed;
        this.stressImpact = stressImpact;
        this.capacityProvided = capacityProvided;
        this.isSource = isSource;
        this.isRootLike = isRootLike;
        this.isOverStressed = isOverStressed;
        this.upstreamPos = (upstreamPos != null) ? upstreamPos.immutable() : null;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public String getName() {
        return this.name;
    }

    public NodeType getType() {
        return this.type;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getStressImpact() {
        return this.stressImpact;
    }

    public float getCapacityProvided() {
        return this.capacityProvided;
    }

    public boolean isSource() {
        return this.isSource;
    }

    public boolean isRootLike() {
        return this.isRootLike;
    }

    public boolean isInferredRoot() {
        return this.isRootLike && !this.isSource;
    }

    public boolean isSourceLike() {
        return this.isRootLike || this.type.isSourceType();
    }

    public BlockPos getUpstreamPos() {
        return this.upstreamPos;
    }

    public boolean isOverStressed() {
        return this.isOverStressed;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TopologyNode && this.pos.equals(((TopologyNode)o).pos));
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode();
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", this.pos.asLong());
        tag.putString("Name", this.name);
        tag.putInt("Type", this.type.ordinal());
        tag.putFloat("Speed", this.speed);
        tag.putFloat("StressImpact", this.stressImpact);
        tag.putFloat("CapacityProvided", this.capacityProvided);
        tag.putBoolean("IsSource", this.isSource);
        tag.putBoolean("IsRootLike", this.isRootLike);
        tag.putBoolean("IsOverStressed", this.isOverStressed);
        if (this.upstreamPos != null) {
            tag.putLong("UpstreamPos", this.upstreamPos.asLong());
        }
        return tag;
    }

    public static TopologyNode load(CompoundTag tag) {
        final NodeType type = NodeType.values()[Math.min(tag.getInt("Type"), NodeType.values().length - 1)];
        final boolean isSource = tag.getBoolean("IsSource");
        final boolean isRootLike = tag.contains("IsRootLike") ? tag.getBoolean("IsRootLike") : (isSource || type.isSourceType());
        final BlockPos upstreamPos = tag.contains("UpstreamPos") ? BlockPos.of(tag.getLong("UpstreamPos")) : null;
        return new TopologyNode(BlockPos.of(tag.getLong("Pos")), tag.getString("Name"), type, tag.getFloat("Speed"), tag.getFloat("StressImpact"), tag.getFloat("CapacityProvided"), isSource, isRootLike, tag.getBoolean("IsOverStressed"), upstreamPos);
    }
}
