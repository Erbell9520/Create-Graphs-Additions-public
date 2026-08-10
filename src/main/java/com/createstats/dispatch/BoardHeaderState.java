package com.createstats.dispatch;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.ManifestOperation;

/**
 * Top-level berth/header state for the dispatch wall.
 * Drives the header color, icon, and summary text across all tiers.
 */
public enum BoardHeaderState {

    UNLINKED("Unlinked", 0x999999),
    IDLE("Idle", 0xAAAAAA),
    ARRIVING("Arriving", 0x55CCFF),
    PRESENT("Present", 0x55FF55),
    LOADING("Loading", 0xFFCC00),
    UNLOADING("Unloading", 0xFF9900),
    READY("Ready", 0x00FF88),
    WAITING_ON_CARGO("Waiting", 0xFFFF55),
    ERROR("Error", 0xFF5555);

    private final String displayName;
    private final int indicatorColor;

    BoardHeaderState(String displayName, int indicatorColor) {
        this.displayName = displayName;
        this.indicatorColor = indicatorColor;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /** Suggested ARGB color for the header status indicator (alpha = 0xFF implied). */
    public int getIndicatorColor() {
        return this.indicatorColor;
    }

    /**
     * Derives the header state from the current board data.
     * Uses link state, train presence, arrival tracking, and row progress.
     */
    public static BoardHeaderState derive(FreightStationLink link, DeliveryOrderBoardView board) {
        if (!link.isLinked()) {
            return UNLINKED;
        }
        if (!board.isTrainPresent()) {
            if (board.isArrivalTrackingActive()) {
                return ARRIVING;
            }
            return IDLE;
        }

        int total = board.getTotalOrders();
        int completed = board.getCompletedOrders() + board.getDismissedOrders();
        if (total > 0 && completed >= total) {
            return READY;
        }

        boolean hasActiveLoad = false;
        boolean hasActiveUnload = false;
        boolean allWaiting = true;
        for (DeliveryOrderLineView line : board.getLineViews()) {
            if (line.isDismissed() || line.isComplete()) {
                continue;
            }
            if (line.getDeliveredAmount() > 0) {
                allWaiting = false;
                if (line.getOperation() == ManifestOperation.LOAD) {
                    hasActiveLoad = true;
                } else {
                    hasActiveUnload = true;
                }
            }
        }

        if (allWaiting && total > 0 && completed < total) {
            return WAITING_ON_CARGO;
        }
        if (hasActiveUnload) {
            return UNLOADING;
        }
        if (hasActiveLoad) {
            return LOADING;
        }

        return PRESENT;
    }
}
