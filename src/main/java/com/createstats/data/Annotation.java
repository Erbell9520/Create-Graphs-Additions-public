package com.createstats.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public class Annotation
{
    private final UUID id;
    private final long tickStamp;
    private long endTickStamp;
    private String label;
    private final UUID playerUUID;
    private int color;
    private AnnotationType type;

    public Annotation(UUID id, long tickStamp, long endTickStamp, String label, UUID playerUUID, int color, AnnotationType type) {
        this.id = id;
        this.tickStamp = tickStamp;
        this.endTickStamp = endTickStamp;
        this.label = label;
        this.playerUUID = playerUUID;
        this.color = (color | 0xFF000000);
        this.type = type;
    }

    public UUID getId() {
        return this.id;
    }

    public long getTickStamp() {
        return this.tickStamp;
    }

    public long getEndTickStamp() {
        return this.endTickStamp;
    }

    public String getLabel() {
        return this.label;
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public int getColor() {
        return this.color;
    }

    public AnnotationType getType() {
        return this.type;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setColor(int color) {
        this.color = (color | 0xFF000000);
    }

    public void setEndTickStamp(long tick) {
        this.endTickStamp = tick;
    }

    public void setType(AnnotationType type) {
        this.type = type;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        final CompoundTag idTag = new CompoundTag();
        idTag.putLong("IdMost", this.id.getMostSignificantBits());
        idTag.putLong("IdLeast", this.id.getLeastSignificantBits());
        tag.put("Id", (Tag)idTag);
        tag.putLong("Tick", this.tickStamp);
        tag.putLong("EndTick", this.endTickStamp);
        tag.putString("Label", this.label);
        final CompoundTag playerTag = new CompoundTag();
        playerTag.putLong("PlayerMost", this.playerUUID.getMostSignificantBits());
        playerTag.putLong("PlayerLeast", this.playerUUID.getLeastSignificantBits());
        tag.put("Player", (Tag)playerTag);
        tag.putInt("Color", this.color);
        tag.putString("Type", this.type.name());
        return tag;
    }

    public static Annotation load(CompoundTag tag) {
        final CompoundTag idTag = tag.getCompound("Id");
        final UUID id = new UUID(idTag.getLong("IdMost"), idTag.getLong("IdLeast"));
        final long tick = tag.getLong("Tick");
        final long endTick = tag.contains("EndTick") ? tag.getLong("EndTick") : tick;
        final String label = tag.getString("Label");
        final CompoundTag pt = tag.getCompound("Player");
        final UUID player = new UUID(pt.getLong("PlayerMost"), pt.getLong("PlayerLeast"));
        final int color = tag.getInt("Color") | 0xFF000000;
        AnnotationType type;
        try {
            type = AnnotationType.valueOf(tag.getString("Type"));
        }
        catch (IllegalArgumentException e) {
            type = AnnotationType.MARKER;
        }
        return new Annotation(id, tick, endTick, label, player, color, type);
    }

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeLong(this.tickStamp);
        buf.writeLong(this.endTickStamp);
        buf.writeUtf(this.label, 48);
        buf.writeUUID(this.playerUUID);
        buf.writeInt(this.color);
        buf.writeEnum((Enum)this.type);
    }

    public static Annotation readFromBuf(FriendlyByteBuf buf) {
        final UUID id = buf.readUUID();
        final long tick = buf.readLong();
        final long endTick = buf.readLong();
        final String label = buf.readUtf(48);
        final UUID player = buf.readUUID();
        final int color = buf.readInt();
        final AnnotationType type = (AnnotationType)buf.readEnum((Class)AnnotationType.class);
        return new Annotation(id, tick, endTick, label, player, color, type);
    }

    public enum AnnotationType
    {
        MARKER,
        REGION;
    }
}
