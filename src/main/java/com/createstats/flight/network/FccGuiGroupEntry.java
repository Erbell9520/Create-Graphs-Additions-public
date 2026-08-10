package com.createstats.flight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public record FccGuiGroupEntry(
        UUID id,
        String label,
        int deviceCount,
        String zoneKey
) {

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(label == null ? "" : label, 128);
        buf.writeVarInt(deviceCount);
        buf.writeUtf(zoneKey == null ? "" : zoneKey, 64);
    }

    public static FccGuiGroupEntry decode(RegistryFriendlyByteBuf buf) {
        return new FccGuiGroupEntry(buf.readUUID(), buf.readUtf(128), buf.readVarInt(), buf.readUtf(64));
    }
}