package com.createstats.dispatch;

import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.ManifestCargoKind;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.train.manifest.DeliveryOrderResetMode;
import net.minecraft.world.item.ItemStack;

/** [1] */
public class DispatchRowEntry {

    private final DeliveryOrderLineView lineView;
    private final DispatchRowState state;
    private final int rowIndex;

    public DispatchRowEntry(DeliveryOrderLineView lineView, DispatchRowState state, int rowIndex) {
        this.lineView = lineView;
        this.state = state;
        this.rowIndex = rowIndex;
    }

    public DeliveryOrderLineView getLineView() {
        return this.lineView;
    }

    public DispatchRowState getState() {
        return this.state;
    }

    /** Zero-based index of this row in the visible page. */
    public int getRowIndex() {
        return this.rowIndex;
    }

    // convenience pass-throughs

    public String getDisplayName() {
        return this.lineView.getDisplayName();
    }

    public ItemStack getDisplayStack() {
        return this.lineView.getDisplayStack();
    }

    public ManifestCargoKind getCargoKind() {
        return this.lineView.getCargoKind();
    }

    public ManifestOperation getOperation() {
        return this.lineView.getOperation();
    }

    public int getTargetAmount() {
        return this.lineView.getTargetAmount();
    }

    public int getDeliveredAmount() {
        return this.lineView.getDeliveredAmount();
    }

    public int getFreightAmount() {
        return this.lineView.getFreightAmount();
    }

    public DeliveryOrderResetMode getResetMode() {
        return this.lineView.getResetMode();
    }

    public int getResetIntervalValue() {
        return this.lineView.getResetIntervalValue();
    }

    /**
     * Fractional progress [0.0, 1.0] for the progress bar.
     * Returns 0 if target is zero (NEEDS_SETUP).
     */
    public float getProgress() {
        if (this.lineView.getTargetAmount() <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) this.lineView.getDeliveredAmount() / this.lineView.getTargetAmount());
    }

    /** Short status label for compact display (e.g. "12/64 Load"). */
    public String getCompactLabel() {
        if (this.lineView.getTargetAmount() <= 0) {
            return this.lineView.getOperation().getDisplayName();
        }
        return this.lineView.getDeliveredAmount() + "/" + this.lineView.getTargetAmount()
                + " " + this.lineView.getOperation().getDisplayName();
    }

    /**
     * Cycle/reset badge text. Returns empty string if the row is one-shot (ONCE)
     * or if the tier doesn't show row detail.
     */
    public String getResetBadge() {
        return this.lineView.getResetMode().getBadgeLabel(this.lineView.getResetIntervalValue());
    }

    /** RGB color for this row's state indicator. */
    public int getStateColor() {
        return this.state.getColor();
    }
}
