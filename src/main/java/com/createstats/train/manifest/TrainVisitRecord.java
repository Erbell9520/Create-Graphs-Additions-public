package com.createstats.train.manifest;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class TrainVisitRecord {
    private final UUID trainId;
    private final String trainName;
    private final long arrivalTick;
    private long departureTick;

    public TrainVisitRecord(UUID trainId, String trainName, long arrivalTick, long departureTick) {
        this.trainId = trainId;
        this.trainName = trainName;
        this.arrivalTick = arrivalTick;
        this.departureTick = departureTick;
    }

    public UUID getTrainId() {
        return this.trainId;
    }

    public String getTrainName() {
        return this.trainName;
    }

    public long getArrivalTick() {
        return this.arrivalTick;
    }

    public long getDepartureTick() {
        return this.departureTick;
    }

    public boolean isStillPresent() {
        return this.departureTick < 0;
    }

    public void setDepartureTick(long tick) {
        this.departureTick = tick;
    }

    public TrainVisitRecord copy() {
        return new TrainVisitRecord(this.trainId, this.trainName, this.arrivalTick, this.departureTick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("TrainId", this.trainId);
        tag.putString("TrainName", this.trainName);
        tag.putLong("ArrivalTick", this.arrivalTick);
        tag.putLong("DepartureTick", this.departureTick);
        return tag;
    }

    public static TrainVisitRecord load(CompoundTag tag) {
        return new TrainVisitRecord(
                tag.getUUID("TrainId"),
                tag.getString("TrainName"),
                tag.getLong("ArrivalTick"),
                tag.getLong("DepartureTick"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.trainId);
        buf.writeUtf(this.trainName);
        buf.writeLong(this.arrivalTick);
        buf.writeLong(this.departureTick);
    }

    public static TrainVisitRecord decode(FriendlyByteBuf buf) {
        return new TrainVisitRecord(buf.readUUID(), buf.readUtf(), buf.readLong(), buf.readLong());
    }
}
