package com.createstats.train.manifest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class DeliveryOrderHistoryEntry {

    private final DeliveryOrderHistoryType type;
    private final long tick;
    private final String title;
    private final String detail;

    public DeliveryOrderHistoryEntry(DeliveryOrderHistoryType type, long tick, String title, String detail) {
        this.type = type;
        this.tick = tick;
        this.title = title;
        this.detail = detail;
    }

    public DeliveryOrderHistoryType getType() {
        return this.type;
    }

    public long getTick() {
        return this.tick;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDetail() {
        return this.detail;
    }

    public DeliveryOrderHistoryEntry copy() {
        return new DeliveryOrderHistoryEntry(this.type, this.tick, this.title, this.detail);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", this.type.getSerializedName());
        tag.putLong("Tick", this.tick);
        tag.putString("Title", this.title);
        tag.putString("Detail", this.detail);
        return tag;
    }

    public static DeliveryOrderHistoryEntry load(CompoundTag tag) {
        return new DeliveryOrderHistoryEntry(
            DeliveryOrderHistoryType.byName(tag.getString("Type")),
            tag.getLong("Tick"),
            tag.getString("Title"),
            tag.getString("Detail"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.type);
        buf.writeLong(this.tick);
        buf.writeUtf(this.title);
        buf.writeUtf(this.detail);
    }

    public static DeliveryOrderHistoryEntry decode(FriendlyByteBuf buf) {
        return new DeliveryOrderHistoryEntry(buf.readEnum(DeliveryOrderHistoryType.class), buf.readLong(), buf.readUtf(), buf.readUtf());
    }
}
