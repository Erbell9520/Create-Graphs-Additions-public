package com.createstats.dispatch;

import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.DeliveryOrderResetMode;

/**
 * Dominant visual/logical state for a single dispatch row.
 * Drives row color, badge icon, and available operator actions.
 */
public enum DispatchRowState {

    /** Manifest line exists but no target amount is configured, row is not actionable. */
    NEEDS_SETUP("Setup", 0x888888),

    /** Waiting for the next train arrival (arrival-reset rows between trains). */
    WAITING("Waiting", 0xAAAAAA),

    /** Train is present and this row's arrival tracking window is active. */
    TRACKING("Tracking", 0x55CCFF),

    /** Delivery/loading/unloading is actively progressing. */
    IN_PROGRESS("Active", 0xFFCC00),

    /** Target amount reached and row is visible. */
    COMPLETE("Done", 0x00FF88),

    /** Target amount reached and row has been dismissed/hidden by the operator. */
    COMPLETE_HIDDEN("Hidden", 0x666666),

    /** Waiting for the next cycle reset (timed or manual). */
    WAITING_NEXT_CYCLE("Cycle", 0x9999FF),

    /** Row data is inconsistent: missing cargo definition, duplicate conflict, etc. */
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

    /** Suggested RGB row-state color for rendering. */
    public int getColor() {
        return this.color;
    }

    /**
     * Derives the row state from a delivery order line view plus whether a train is currently present.
     */
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
