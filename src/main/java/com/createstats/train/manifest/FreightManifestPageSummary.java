package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

// tiny summary, the full board view per block per client forever would be absurd
public final class FreightManifestPageSummary {
    public static final int MAX_ROWS = 4;

    private static final int BIT_TO_TRAIN = 0x80;
    private static final int BIT_COMPLETE = 0x40;
    private static final int MASK_PROGRESS = 0x3F;

    public static final FreightManifestPageSummary BLANK =
            new FreightManifestPageSummary(false, false, false, 0, 0, 0, new byte[0]);

    private final boolean linked;
    private final boolean reachable;
    private final boolean trainPresent;
    private final int totalOrders;
    private final int completedOrders;
    private final int lineCount;
    private final byte[] rows;

    private FreightManifestPageSummary(boolean linked, boolean reachable, boolean trainPresent,
                                       int totalOrders, int completedOrders, int lineCount, byte[] rows) {
        this.linked = linked;
        this.reachable = reachable;
        this.trainPresent = trainPresent;
        this.totalOrders = totalOrders;
        this.completedOrders = completedOrders;
        this.lineCount = lineCount;
        this.rows = rows;
    }

    public static FreightManifestPageSummary unreachable() {
        return new FreightManifestPageSummary(true, false, false, 0, 0, 0, new byte[0]);
    }

    public static FreightManifestPageSummary from(DeliveryOrderBoardView view) {
        if (view == null) {
            return unreachable();
        }
        List<DeliveryOrderLineView> ordered = new ArrayList<>(view.getLineViews());
        ordered.removeIf(DeliveryOrderLineView::isDismissed);
        ordered.sort((a, b) -> Boolean.compare(a.isComplete(), b.isComplete()));

        int count = Math.min(MAX_ROWS, ordered.size());
        byte[] rows = new byte[count];
        for (int i = 0; i < count; i++) {
            DeliveryOrderLineView line = ordered.get(i);
            int progress = line.getTargetAmount() <= 0 ? 0
                    : Math.round(MASK_PROGRESS * Math.min(1f,
                            line.getDeliveredAmount() / (float) line.getTargetAmount()));
            int packed = Math.min(MASK_PROGRESS, Math.max(0, progress));
            if (line.getOperation() == ManifestOperation.LOAD) {
                packed |= BIT_TO_TRAIN;
            }
            if (line.isComplete()) {
                packed |= BIT_COMPLETE;
            }
            rows[i] = (byte) packed;
        }
        return new FreightManifestPageSummary(true, true, view.isTrainPresent(),
                view.getTotalOrders(), view.getCompletedOrders(), ordered.size(), rows);
    }

    public boolean isLinked() {
        return this.linked;
    }

    public boolean isReachable() {
        return this.reachable;
    }

    public boolean isTrainPresent() {
        return this.trainPresent;
    }

    public int getRowCount() {
        return this.rows.length;
    }

    public boolean hasOverflow() {
        return this.lineCount > this.rows.length;
    }

    public boolean isRowToTrain(int index) {
        return (this.rows[index] & BIT_TO_TRAIN) != 0;
    }

    public boolean isRowComplete(int index) {
        return (this.rows[index] & BIT_COMPLETE) != 0;
    }

    public float getRowProgress(int index) {
        return (this.rows[index] & MASK_PROGRESS) / (float) MASK_PROGRESS;
    }

    public float getBoardProgress() {
        return this.totalOrders <= 0 ? 0f
                : Math.min(1f, this.completedOrders / (float) this.totalOrders);
    }

    public boolean isStamped() {
        return this.totalOrders > 0 && this.completedOrders >= this.totalOrders;
    }

    public boolean hasWork() {
        return this.totalOrders > 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Linked", this.linked);
        tag.putBoolean("Reachable", this.reachable);
        tag.putBoolean("Train", this.trainPresent);
        tag.putInt("Total", this.totalOrders);
        tag.putInt("Done", this.completedOrders);
        tag.putInt("Lines", this.lineCount);
        tag.putByteArray("Rows", this.rows);
        return tag;
    }

    public static FreightManifestPageSummary load(CompoundTag tag) {
        byte[] rows = tag.getByteArray("Rows");
        if (rows.length > MAX_ROWS) {
            rows = Arrays.copyOf(rows, MAX_ROWS);
        }
        return new FreightManifestPageSummary(
                tag.getBoolean("Linked"), tag.getBoolean("Reachable"), tag.getBoolean("Train"),
                tag.getInt("Total"), tag.getInt("Done"), tag.getInt("Lines"), rows);
    }

    public boolean matches(FreightManifestPageSummary other) {
        return other != null
                && this.linked == other.linked
                && this.reachable == other.reachable
                && this.trainPresent == other.trainPresent
                && this.totalOrders == other.totalOrders
                && this.completedOrders == other.completedOrders
                && this.lineCount == other.lineCount
                && Arrays.equals(this.rows, other.rows);
    }
}
