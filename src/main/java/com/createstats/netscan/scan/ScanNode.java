package com.createstats.netscan.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

// equal by position only
public final class ScanNode {
    private final BlockPos pos;
    private final String name;
    private final String blockId;
    private final ScanNodeKind kind;
    private final float speed;
    private final float stressImpact;
    private final float capacityProvided;
    private final float stressCoefficient;
    private final boolean source;
    private final boolean overStressed;
    private final BlockPos upstream;

    public ScanNode(BlockPos pos, String name, String blockId, ScanNodeKind kind, float speed,
                    float stressImpact, float capacityProvided, float stressCoefficient,
                    boolean source, boolean overStressed, BlockPos upstream) {
        this.pos = pos.immutable();
        this.name = name == null ? "" : name;
        this.blockId = blockId == null ? "" : blockId;
        this.kind = kind == null ? ScanNodeKind.OTHER : kind;
        this.speed = speed;
        this.stressImpact = stressImpact;
        this.capacityProvided = capacityProvided;
        this.stressCoefficient = stressCoefficient;
        this.source = source;
        this.overStressed = overStressed;
        this.upstream = upstream == null ? null : upstream.immutable();
    }

    public BlockPos pos() {
        return this.pos;
    }

    public String name() {
        return this.name;
    }

    public String blockId() {
        return this.blockId;
    }

    public ScanNodeKind kind() {
        return this.kind;
    }

    public float speed() {
        return this.speed;
    }

    public float stressImpact() {
        return this.stressImpact;
    }

    public float capacityProvided() {
        return this.capacityProvided;
    }

    public float stressCoefficient() {
        return this.stressCoefficient;
    }

    public boolean isSource() {
        return this.source;
    }

    public boolean isOverStressed() {
        return this.overStressed;
    }

    public BlockPos upstream() {
        return this.upstream;
    }

    public float drawnStress() {
        return Math.abs(this.stressImpact);
    }

    public ScanNode withLive(float newSpeed, float newImpact, float newCapacity, boolean newOverStressed) {
        return new ScanNode(this.pos, this.name, this.blockId, this.kind, newSpeed, newImpact,
                newCapacity, this.stressCoefficient, this.source, newOverStressed, this.upstream);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", this.pos.asLong());
        tag.putString("Name", this.name);
        tag.putString("Id", this.blockId);
        tag.putInt("Kind", this.kind.ordinal());
        tag.putFloat("Speed", this.speed);
        tag.putFloat("Stress", this.stressImpact);
        tag.putFloat("Capacity", this.capacityProvided);
        tag.putFloat("Coeff", this.stressCoefficient);
        tag.putBoolean("Source", this.source);
        tag.putBoolean("Over", this.overStressed);
        if (this.upstream != null) {
            tag.putLong("Up", this.upstream.asLong());
        }
        return tag;
    }

    public static ScanNode load(CompoundTag tag) {
        return new ScanNode(
                BlockPos.of(tag.getLong("Pos")),
                tag.getString("Name"),
                tag.getString("Id"),
                ScanNodeKind.byOrdinal(tag.getInt("Kind")),
                tag.getFloat("Speed"),
                tag.getFloat("Stress"),
                tag.getFloat("Capacity"),
                tag.getFloat("Coeff"),
                tag.getBoolean("Source"),
                tag.getBoolean("Over"),
                tag.contains("Up") ? BlockPos.of(tag.getLong("Up")) : null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScanNode n && this.pos.equals(n.pos);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode();
    }

    @Override
    public String toString() {
        return "ScanNode[" + this.pos.toShortString() + " " + this.kind + " " + this.name + "]";
    }
}
