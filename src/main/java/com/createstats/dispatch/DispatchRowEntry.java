package com.createstats.dispatch;

import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.ManifestCargoKind;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.train.manifest.DeliveryOrderResetMode;
import net.minecraft.world.item.ItemStack;

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

    public int getRowIndex() {
        return this.rowIndex;
    }

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

    public float getProgress() {
        if (this.lineView.getTargetAmount() <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) this.lineView.getDeliveredAmount() / this.lineView.getTargetAmount());
    }

    public String getCompactLabel() {
        if (this.lineView.getTargetAmount() <= 0) {
            return this.lineView.getOperation().getDisplayName();
        }
        return this.lineView.getDeliveredAmount() + "/" + this.lineView.getTargetAmount()
                + " " + this.lineView.getOperation().getDisplayName();
    }

    public String getResetBadge() {
        return this.lineView.getResetMode().getBadgeLabel(this.lineView.getResetIntervalValue());
    }

    public int getStateColor() {
        return this.state.getColor();
    }
}
