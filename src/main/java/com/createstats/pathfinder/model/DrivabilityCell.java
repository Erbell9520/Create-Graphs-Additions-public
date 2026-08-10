package com.createstats.pathfinder.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record DrivabilityCell(
    int localX,
    int localZ,
    int supportY,
    int clearance,
    Passability passability,
    byte heightDelta,
    int hazardFlags,
    short cost
) {
    public static final int SCHEMA_VERSION = 1;

    public DrivabilityCell {
        localX = clamp(localX, 0, 15);
        localZ = clamp(localZ, 0, 15);
        clearance = clamp(clearance, 0, 32);
        passability = passability == null ? Passability.UNKNOWN : passability;
        cost = (short) Math.max(0, cost);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putByte("LocalX", (byte) localX);
        tag.putByte("LocalZ", (byte) localZ);
        tag.putInt("SupportY", supportY);
        tag.putByte("Clearance", (byte) clearance);
        tag.putString("Passability", passability.name());
        tag.putByte("HeightDelta", heightDelta);
        tag.putInt("HazardFlags", hazardFlags);
        tag.putShort("Cost", cost);
        return tag;
    }

    public static DrivabilityCell load(CompoundTag tag) {
        if (tag == null) return unknown(0, 0, 0);
        return new DrivabilityCell(Byte.toUnsignedInt(tag.getByte("LocalX")), Byte.toUnsignedInt(tag.getByte("LocalZ")),
            tag.getInt("SupportY"), Byte.toUnsignedInt(tag.getByte("Clearance")),
            Passability.safeValueOf(tag.getString("Passability"), Passability.UNKNOWN),
            tag.getByte("HeightDelta"), tag.getInt("HazardFlags"), tag.getShort("Cost"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(localX);
        buf.writeByte(localZ);
        buf.writeVarInt(supportY);
        buf.writeByte(clearance);
        buf.writeEnum(passability);
        buf.writeByte(heightDelta);
        buf.writeVarInt(hazardFlags);
        buf.writeShort(cost);
    }

    public static DrivabilityCell decode(FriendlyByteBuf buf) {
        return new DrivabilityCell(Byte.toUnsignedInt(buf.readByte()), Byte.toUnsignedInt(buf.readByte()),
            buf.readVarInt(), Byte.toUnsignedInt(buf.readByte()), buf.readEnum(Passability.class),
            buf.readByte(), buf.readVarInt(), buf.readShort());
    }

    public static DrivabilityCell unknown(int localX, int localZ, int supportY) {
        return new DrivabilityCell(localX, localZ, supportY, 0, Passability.UNKNOWN, (byte) 0, 0, (short) 0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}