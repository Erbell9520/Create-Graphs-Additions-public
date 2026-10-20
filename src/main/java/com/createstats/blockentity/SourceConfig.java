package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

public class SourceConfig
{
    private final String sourceId;
    private String userLabel;
    private BlockPos sourcePos;
    private String sourceType;
    private long firstConnectTick;
    private long lastDataTick;
    private boolean active;
    private BlockPos observedTargetPos;
    private String observedTargetLabel;

    public SourceConfig(String sourceId, BlockPos pos, String sourceType) {
        this.sourceId = sourceId;
        this.sourcePos = pos;
        this.sourceType = sourceType;
        this.firstConnectTick = 0L;
        this.lastDataTick = 0L;
        this.active = true;
        this.userLabel = null;
        this.observedTargetPos = null;
        this.observedTargetLabel = null;
    }

    public String getDisplayLabel() {
        if (this.userLabel != null && !this.userLabel.isEmpty()) {
            return this.userLabel;
        }
        return this.sourceType + " @ " + this.sourcePos.toShortString();
    }

    public BlockPos getObservedTargetPos() {
        return this.observedTargetPos;
    }

    public String getObservedTargetLabel() {
        return this.observedTargetLabel;
    }

    public boolean hasObservedTarget() {
        return this.observedTargetPos != null && this.observedTargetLabel != null && !this.observedTargetLabel.isEmpty();
    }

    public boolean setObservedTarget(BlockPos targetPos, String targetLabel) {
        final boolean posChanged = (this.observedTargetPos == null) != (targetPos == null)
                || (this.observedTargetPos != null && !this.observedTargetPos.equals(targetPos));
        final String label = (targetLabel == null) ? null : targetLabel;
        final boolean labelChanged = (this.observedTargetLabel == null) != (label == null)
                || (this.observedTargetLabel != null && !this.observedTargetLabel.equals(label));
        if (posChanged) {
            this.observedTargetPos = targetPos;
        }
        if (labelChanged) {
            this.observedTargetLabel = label;
        }
        return posChanged || labelChanged;
    }

    public boolean clearObservedTarget() {
        return this.setObservedTarget(null, null);
    }

    public String getObservedDisplayLabel() {
        if (this.userLabel != null && !this.userLabel.isEmpty()) {
            return this.userLabel;
        }
        if (this.hasObservedTarget()) {
            return this.observedTargetLabel + " @ " + this.observedTargetPos.toShortString();
        }
        return this.getDisplayLabel();
    }

    public String getSourceId() {
        return this.sourceId;
    }

    public String getUserLabel() {
        return this.userLabel;
    }

    public void setUserLabel(String label) {
        this.userLabel = label;
    }

    public BlockPos getSourcePos() {
        return this.sourcePos;
    }

    public void setSourcePos(BlockPos pos) {
        this.sourcePos = pos;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public void setSourceType(String type) {
        this.sourceType = type;
    }

    public long getFirstConnectTick() {
        return this.firstConnectTick;
    }

    public void setFirstConnectTick(long tick) {
        this.firstConnectTick = tick;
    }

    public long getLastDataTick() {
        return this.lastDataTick;
    }

    public void setLastDataTick(long tick) {
        this.lastDataTick = tick;
        this.active = true;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.sourceId);
        tag.putString("UserLabel", (this.userLabel != null) ? this.userLabel : "");
        tag.putLong("Pos", this.sourcePos.asLong());
        tag.putString("Type", this.sourceType);
        tag.putLong("FirstConnect", this.firstConnectTick);
        tag.putLong("LastData", this.lastDataTick);
        tag.putBoolean("Active", this.active);
        if (this.observedTargetPos != null) {
            tag.putLong("ObsPos", this.observedTargetPos.asLong());
        }
        if (this.observedTargetLabel != null && !this.observedTargetLabel.isEmpty()) {
            tag.putString("ObsLabel", this.observedTargetLabel);
        }
        return tag;
    }

    public static SourceConfig load(CompoundTag tag) {
        final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        final SourceConfig config = new SourceConfig(tag.getString("Id"), pos, tag.getString("Type"));
        final String label = tag.getString("UserLabel");
        config.userLabel = (label.isEmpty() ? null : label);
        config.firstConnectTick = tag.getLong("FirstConnect");
        config.lastDataTick = tag.getLong("LastData");
        config.active = tag.getBoolean("Active");
        if (tag.contains("ObsPos")) {
            config.observedTargetPos = BlockPos.of(tag.getLong("ObsPos"));
        }
        if (tag.contains("ObsLabel")) {
            final String obs = tag.getString("ObsLabel");
            config.observedTargetLabel = obs.isEmpty() ? null : obs;
        }
        return config;
    }
}
