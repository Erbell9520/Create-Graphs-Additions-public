package com.createstats.train.manifest;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public class DeliveryOrderLineState {
    private final UUID lineId;
    private int deliveredAmount;
    private boolean dismissed;
    private long cycleStartTick;
    private long lastUpdateTick;
    private long lastCompletionTick;

    public DeliveryOrderLineState(UUID lineId, int deliveredAmount, boolean dismissed, long cycleStartTick,
                                  long lastUpdateTick, long lastCompletionTick) {
        this.lineId = lineId;
        this.deliveredAmount = Math.max(0, deliveredAmount);
        this.dismissed = dismissed;
        this.cycleStartTick = Math.max(0L, cycleStartTick);
        this.lastUpdateTick = Math.max(0L, lastUpdateTick);
        this.lastCompletionTick = lastCompletionTick;
    }

    public static DeliveryOrderLineState create(UUID lineId, long currentTick) {
        return new DeliveryOrderLineState(lineId, 0, false, currentTick, currentTick, -1L);
    }

    public UUID getLineId() {
        return this.lineId;
    }

    public int getDeliveredAmount() {
        return this.deliveredAmount;
    }

    public boolean isDismissed() {
        return this.dismissed;
    }

    public long getCycleStartTick() {
        return this.cycleStartTick;
    }

    public long getLastUpdateTick() {
        return this.lastUpdateTick;
    }

    public long getLastCompletionTick() {
        return this.lastCompletionTick;
    }

    public boolean hasProgress() {
        return this.deliveredAmount > 0;
    }

    public boolean isComplete(int targetAmount) {
        return targetAmount > 0 && this.deliveredAmount >= targetAmount;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public void markComplete(int targetAmount, long currentTick) {
        int clampedTarget = Math.max(0, targetAmount);
        this.deliveredAmount = Math.max(this.deliveredAmount, clampedTarget);
        this.dismissed = false;
        this.lastUpdateTick = currentTick;
        if (clampedTarget > 0) {
            this.lastCompletionTick = currentTick;
        }
    }

    public int addDelivered(int delta, int targetAmount, long currentTick) {
        int remaining = Math.max(0, Math.max(0, targetAmount) - this.deliveredAmount);
        if (delta <= 0 || remaining <= 0) {
            return delta;
        }
        int applied = Math.min(delta, remaining);
        this.deliveredAmount += applied;
        this.dismissed = false;
        this.lastUpdateTick = currentTick;
        if (this.deliveredAmount >= Math.max(0, targetAmount)) {
            this.lastCompletionTick = currentTick;
        }
        return delta - applied;
    }

    public void reset(long currentTick) {
        this.deliveredAmount = 0;
        this.dismissed = false;
        this.cycleStartTick = Math.max(0L, currentTick);
        this.lastUpdateTick = Math.max(0L, currentTick);
        this.lastCompletionTick = -1L;
    }

    public void reconcileTarget(int targetAmount, long currentTick) {
        int clampedTarget = Math.max(0, targetAmount);
        if (clampedTarget == 0) {
            this.deliveredAmount = 0;
            this.dismissed = false;
            this.lastCompletionTick = -1L;
            return;
        }
        if (this.deliveredAmount > clampedTarget) {
            this.deliveredAmount = clampedTarget;
        }
        if (!this.isComplete(clampedTarget)) {
            this.dismissed = false;
            if (this.deliveredAmount <= 0) {
                this.lastCompletionTick = -1L;
            }
        } else if (this.lastCompletionTick < 0L) {
            this.lastCompletionTick = currentTick;
        }
    }

    public DeliveryOrderLineState copy() {
        return new DeliveryOrderLineState(this.lineId, this.deliveredAmount, this.dismissed, this.cycleStartTick,
            this.lastUpdateTick, this.lastCompletionTick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("LineId", this.lineId);
        tag.putInt("DeliveredAmount", this.deliveredAmount);
        tag.putBoolean("Dismissed", this.dismissed);
        tag.putLong("CycleStartTick", this.cycleStartTick);
        tag.putLong("LastUpdateTick", this.lastUpdateTick);
        tag.putLong("LastCompletionTick", this.lastCompletionTick);
        return tag;
    }

    public static DeliveryOrderLineState load(CompoundTag tag) {
        if (!tag.hasUUID("LineId")) {
            return create(UUID.randomUUID(), 0L);
        }
        return new DeliveryOrderLineState(
            tag.getUUID("LineId"),
            tag.getInt("DeliveredAmount"),
            tag.getBoolean("Dismissed"),
            tag.contains("CycleStartTick") ? tag.getLong("CycleStartTick") : 0L,
            tag.contains("LastUpdateTick") ? tag.getLong("LastUpdateTick") : 0L,
            tag.contains("LastCompletionTick") ? tag.getLong("LastCompletionTick") : -1L);
    }
}
