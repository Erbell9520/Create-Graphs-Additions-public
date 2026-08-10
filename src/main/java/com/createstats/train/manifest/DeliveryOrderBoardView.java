package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.Tag;

public class DeliveryOrderBoardView {

    private final boolean trainPresent;
    private final boolean arrivalTrackingActive;
    private final String trainName;
    private final String summaryStatus;
    private final long currentTick;
    private final int totalOrders;
    private final int completedOrders;
    private final int dismissedOrders;
    private final List<DeliveryOrderLineView> lineViews;
    private final List<DeliveryOrderHistoryEntry> historyEntries;

    public DeliveryOrderBoardView(boolean trainPresent, boolean arrivalTrackingActive, String trainName, String summaryStatus,
                                  long currentTick, int totalOrders, int completedOrders, int dismissedOrders,
                                  List<DeliveryOrderLineView> lineViews, List<DeliveryOrderHistoryEntry> historyEntries) {
        this.trainPresent = trainPresent;
        this.arrivalTrackingActive = arrivalTrackingActive;
        this.trainName = trainName;
        this.summaryStatus = summaryStatus;
        this.currentTick = currentTick;
        this.totalOrders = totalOrders;
        this.completedOrders = completedOrders;
        this.dismissedOrders = dismissedOrders;
        this.lineViews = new ArrayList<>();
        for (DeliveryOrderLineView lineView : lineViews) {
            this.lineViews.add(lineView);
        }
        this.historyEntries = new ArrayList<>();
        for (DeliveryOrderHistoryEntry historyEntry : historyEntries) {
            this.historyEntries.add(historyEntry.copy());
        }
    }

    public static DeliveryOrderBoardView empty(long currentTick, String summaryStatus) {
        return new DeliveryOrderBoardView(false, false, "", summaryStatus, currentTick, 0, 0, 0, List.of(), List.of());
    }

    public DeliveryOrderBoardView copy() {
        return new DeliveryOrderBoardView(this.trainPresent, this.arrivalTrackingActive, this.trainName, this.summaryStatus,
            this.currentTick, this.totalOrders, this.completedOrders, this.dismissedOrders, this.lineViews, this.historyEntries);
    }

    public boolean isTrainPresent() {
        return this.trainPresent;
    }

    public boolean isArrivalTrackingActive() {
        return this.arrivalTrackingActive;
    }

    public String getTrainName() {
        return this.trainName;
    }

    public String getSummaryStatus() {
        return this.summaryStatus;
    }

    public long getCurrentTick() {
        return this.currentTick;
    }

    public int getTotalOrders() {
        return this.totalOrders;
    }

    public int getCompletedOrders() {
        return this.completedOrders;
    }

    public int getDismissedOrders() {
        return this.dismissedOrders;
    }

    public List<DeliveryOrderLineView> getLineViews() {
        return this.lineViews;
    }

    public List<DeliveryOrderHistoryEntry> getHistoryEntries() {
        return this.historyEntries;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("TrainPresent", this.trainPresent);
        tag.putBoolean("ArrivalTrackingActive", this.arrivalTrackingActive);
        tag.putString("TrainName", this.trainName);
        tag.putString("SummaryStatus", this.summaryStatus);
        tag.putLong("CurrentTick", this.currentTick);
        tag.putInt("TotalOrders", this.totalOrders);
        tag.putInt("CompletedOrders", this.completedOrders);
        tag.putInt("DismissedOrders", this.dismissedOrders);
        ListTag lines = new ListTag();
        for (DeliveryOrderLineView lineView : this.lineViews) {
            lines.add(lineView.save(registries));
        }
        tag.put("LineViews", lines);
        ListTag history = new ListTag();
        for (DeliveryOrderHistoryEntry historyEntry : this.historyEntries) {
            history.add(historyEntry.save());
        }
        tag.put("HistoryEntries", history);
        return tag;
    }

    public static DeliveryOrderBoardView load(CompoundTag tag, HolderLookup.Provider registries) {
        List<DeliveryOrderLineView> lineViews = new ArrayList<>();
        NBTHelper.iterateCompoundList(tag.getList("LineViews", Tag.TAG_COMPOUND), entryTag -> lineViews.add(DeliveryOrderLineView.load(entryTag, registries)));
        List<DeliveryOrderHistoryEntry> historyEntries = new ArrayList<>();
        NBTHelper.iterateCompoundList(tag.getList("HistoryEntries", Tag.TAG_COMPOUND), entryTag -> historyEntries.add(DeliveryOrderHistoryEntry.load(entryTag)));
        return new DeliveryOrderBoardView(
            tag.getBoolean("TrainPresent"),
            tag.getBoolean("ArrivalTrackingActive"),
            tag.getString("TrainName"),
            tag.getString("SummaryStatus"),
            tag.getLong("CurrentTick"),
            tag.getInt("TotalOrders"),
            tag.getInt("CompletedOrders"),
            tag.getInt("DismissedOrders"),
            lineViews,
            historyEntries);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.trainPresent);
        buf.writeBoolean(this.arrivalTrackingActive);
        buf.writeUtf(this.trainName);
        buf.writeUtf(this.summaryStatus);
        buf.writeLong(this.currentTick);
        buf.writeVarInt(this.totalOrders);
        buf.writeVarInt(this.completedOrders);
        buf.writeVarInt(this.dismissedOrders);
        buf.writeVarInt(this.lineViews.size());
        for (DeliveryOrderLineView lineView : this.lineViews) {
            lineView.encode(buf);
        }
        buf.writeVarInt(this.historyEntries.size());
        for (DeliveryOrderHistoryEntry historyEntry : this.historyEntries) {
            historyEntry.encode(buf);
        }
    }

    public static DeliveryOrderBoardView decode(RegistryFriendlyByteBuf buf) {
        boolean trainPresent = buf.readBoolean();
        boolean arrivalTrackingActive = buf.readBoolean();
        String trainName = buf.readUtf();
        String summaryStatus = buf.readUtf();
        long currentTick = buf.readLong();
        int totalOrders = buf.readVarInt();
        int completedOrders = buf.readVarInt();
        int dismissedOrders = buf.readVarInt();
        int size = buf.readVarInt();
        List<DeliveryOrderLineView> lines = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            lines.add(DeliveryOrderLineView.decode(buf));
        }
        int historySize = buf.readVarInt();
        List<DeliveryOrderHistoryEntry> historyEntries = new ArrayList<>();
        for (int i = 0; i < historySize; i++) {
            historyEntries.add(DeliveryOrderHistoryEntry.decode(buf));
        }
        return new DeliveryOrderBoardView(trainPresent, arrivalTrackingActive, trainName, summaryStatus, currentTick,
            totalOrders, completedOrders, dismissedOrders, lines, historyEntries);
    }
}
