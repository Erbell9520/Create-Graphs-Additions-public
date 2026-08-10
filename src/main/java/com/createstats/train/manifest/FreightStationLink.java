package com.createstats.train.manifest;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class FreightStationLink {

    @Nullable
    private UUID stationId;
    private String stationName;
    @Nullable
    private ResourceLocation stationDimension;
    @Nullable
    private BlockPos stationPos;

    public FreightStationLink() {
        this.stationName = "";
    }

    public FreightStationLink(UUID stationId, String stationName, ResourceLocation stationDimension, BlockPos stationPos) {
        this.stationId = stationId;
        this.stationName = stationName;
        this.stationDimension = stationDimension;
        this.stationPos = stationPos;
    }

    @Nullable
    public UUID getStationId() {
        return this.stationId;
    }

    public String getStationName() {
        return this.stationName;
    }

    @Nullable
    public ResourceLocation getStationDimension() {
        return this.stationDimension;
    }

    @Nullable
    public BlockPos getStationPos() {
        return this.stationPos;
    }

    public boolean isLinked() {
        return this.stationId != null && this.stationDimension != null && this.stationPos != null;
    }

    public FreightStationLink copy() {
        if (!this.isLinked()) {
            return new FreightStationLink();
        }
        return new FreightStationLink(this.stationId, this.stationName, this.stationDimension, this.stationPos);
    }

    public void clear() {
        this.stationId = null;
        this.stationName = "";
        this.stationDimension = null;
        this.stationPos = null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (this.stationId != null) {
            tag.putUUID("StationId", this.stationId);
        }
        if (this.stationDimension != null) {
            tag.putString("StationDimension", this.stationDimension.toString());
        }
        if (this.stationPos != null) {
            tag.putLong("StationPos", this.stationPos.asLong());
        }
        tag.putString("StationName", this.stationName);
        return tag;
    }

    public static FreightStationLink load(CompoundTag tag) {
        FreightStationLink link = new FreightStationLink();
        if (tag.hasUUID("StationId")) {
            link.stationId = tag.getUUID("StationId");
        }
        link.stationName = tag.getString("StationName");
        if (tag.contains("StationDimension")) {
            link.stationDimension = ResourceLocation.tryParse(tag.getString("StationDimension"));
        }
        if (tag.contains("StationPos")) {
            link.stationPos = BlockPos.of(tag.getLong("StationPos"));
        }
        return link;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.isLinked());
        if (!this.isLinked()) {
            return;
        }
        buf.writeUUID(this.stationId);
        buf.writeUtf(this.stationName);
        buf.writeResourceLocation(this.stationDimension);
        buf.writeBlockPos(this.stationPos);
    }

    public static FreightStationLink decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return new FreightStationLink();
        }
        return new FreightStationLink(buf.readUUID(), buf.readUtf(), buf.readResourceLocation(), buf.readBlockPos());
    }
}
