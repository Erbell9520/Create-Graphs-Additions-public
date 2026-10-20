package com.createstats.train.manifest;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

public class FreightStationCargoView {
    private final boolean trainPresent;
    private final String trainName;
    private final String statusMessage;
    private final int carriageCount;
    private final TrainCargoSnapshot freightTotals;
    private final TrainCargoSnapshot movedToStation;
    private final TrainCargoSnapshot movedToTrain;

    public FreightStationCargoView(boolean trainPresent, String trainName, String statusMessage, int carriageCount,
                                   TrainCargoSnapshot freightTotals, TrainCargoSnapshot movedToStation, TrainCargoSnapshot movedToTrain) {
        this.trainPresent = trainPresent;
        this.trainName = trainName;
        this.statusMessage = statusMessage;
        this.carriageCount = carriageCount;
        this.freightTotals = freightTotals.copy();
        this.movedToStation = movedToStation.copy();
        this.movedToTrain = movedToTrain.copy();
    }

    public static FreightStationCargoView unavailable(String statusMessage) {
        return new FreightStationCargoView(false, "", statusMessage, 0,
            new TrainCargoSnapshot(), new TrainCargoSnapshot(), new TrainCargoSnapshot());
    }

    public static FreightStationCargoView capture(Train train, FreightTransferProgress progress) {
        String trainName = train.name != null ? train.name.getString() : "Train";
        return new FreightStationCargoView(true, trainName, "Left: freight total | Green: to station | Red: to train",
            train.carriages.size(), progress.getCurrentSnapshot(), progress.getMovedToStation(), progress.getMovedToTrain());
    }

    public static FreightStationCargoView preview(Train train, String statusMessage) {
        String trainName = train.name != null ? train.name.getString() : "Train";
        FreightTransferProgress progress = FreightTransferProgress.empty(TrainCargoSnapshot.capture(train));
        return new FreightStationCargoView(true, trainName, statusMessage,
            train.carriages.size(), progress.getCurrentSnapshot(), progress.getMovedToStation(), progress.getMovedToTrain());
    }

    public boolean isTrainPresent() {
        return this.trainPresent;
    }

    public String getTrainName() {
        return this.trainName;
    }

    public String getStatusMessage() {
        return this.statusMessage;
    }

    public int getCarriageCount() {
        return this.carriageCount;
    }

    public TrainCargoSnapshot getFreightTotals() {
        return this.freightTotals.copy();
    }

    public TrainCargoSnapshot getMovedToStation() {
        return this.movedToStation.copy();
    }

    public TrainCargoSnapshot getMovedToTrain() {
        return this.movedToTrain.copy();
    }

    public FreightStationCargoView copy() {
        return new FreightStationCargoView(this.trainPresent, this.trainName, this.statusMessage, this.carriageCount,
            this.freightTotals, this.movedToStation, this.movedToTrain);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("TrainPresent", this.trainPresent);
        tag.putString("TrainName", this.trainName);
        tag.putString("StatusMessage", this.statusMessage);
        tag.putInt("CarriageCount", this.carriageCount);
        tag.put("FreightTotals", this.freightTotals.save());
        tag.put("MovedToStation", this.movedToStation.save());
        tag.put("MovedToTrain", this.movedToTrain.save());
        return tag;
    }

    public static FreightStationCargoView load(CompoundTag tag) {
        return new FreightStationCargoView(
            tag.getBoolean("TrainPresent"),
            tag.getString("TrainName"),
            tag.getString("StatusMessage"),
            tag.getInt("CarriageCount"),
            TrainCargoSnapshot.load(tag.getCompound("FreightTotals")),
            TrainCargoSnapshot.load(tag.getCompound("MovedToStation")),
            TrainCargoSnapshot.load(tag.getCompound("MovedToTrain")));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.trainPresent);
        buf.writeUtf(this.trainName);
        buf.writeUtf(this.statusMessage);
        buf.writeVarInt(this.carriageCount);
        buf.writeNbt(this.freightTotals.save());
        buf.writeNbt(this.movedToStation.save());
        buf.writeNbt(this.movedToTrain.save());
    }

    public static FreightStationCargoView decode(FriendlyByteBuf buf) {
        boolean trainPresent = buf.readBoolean();
        String trainName = buf.readUtf();
        String statusMessage = buf.readUtf();
        int carriageCount = buf.readVarInt();
        return new FreightStationCargoView(trainPresent, trainName, statusMessage, carriageCount,
            TrainCargoSnapshot.load(buf.readNbt()),
            TrainCargoSnapshot.load(buf.readNbt()),
            TrainCargoSnapshot.load(buf.readNbt()));
    }
}
