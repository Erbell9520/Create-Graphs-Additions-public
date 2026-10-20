package com.createstats.dispatch;

import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.DeliveryOrderResetMode;

public enum DispatchRowState {
    NEEDS_SETUP("Setup", 0x888888),

    WAITING("Waiting", 0xAAAAAA),

    TRACKING("Tracking", 0x55CCFF),

    IN_PROGRESS("Active", 0xFFCC00),

    COMPLETE("Done", 0x00FF88),

    COMPLETE_HIDDEN("Hidden", 0x666666),

    WAITING_NEXT_CYCLE("Cycle", 0x9999FF),

    INVALID("Invalid", 0xFF5555);

    private final String label;
    private final int color;

    DispatchRowState(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return this.label;
    }

    public int getColor() {
        return this.color;
    }

    public static DispatchRowState derive(DeliveryOrderLineView line, boolean trainPresent) {
        if (line.getTargetAmount() <= 0) {
            return NEEDS_SETUP;
        }
        if (line.getDisplayName() == null || line.getDisplayName().isEmpty()) {
            return INVALID;
        }

        boolean complete = line.isComplete();
        if (complete && line.isDismissed()) {
            return COMPLETE_HIDDEN;
        }
        if (complete) {
            if (line.getResetMode() != DeliveryOrderResetMode.ONCE
                    && line.getTicksUntilReset() > 0) {
                return WAITING_NEXT_CYCLE;
            }
            return COMPLETE;
        }

        if (!trainPresent) {
            if (line.getResetMode() == DeliveryOrderResetMode.EVERY_ARRIVAL) {
                return WAITING;
            }
            if (line.getDeliveredAmount() > 0) {
                return IN_PROGRESS;
            }
            return WAITING;
        }

        if (line.getDeliveredAmount() > 0) {
            return IN_PROGRESS;
        }
        return TRACKING;
    }
}
