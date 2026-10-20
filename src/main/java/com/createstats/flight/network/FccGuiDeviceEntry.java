package com.createstats.flight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public record FccGuiDeviceEntry(
        UUID id,
        String label,
        String categoryLabel,
        int displayNumber,
        String assignmentLabel,
        boolean resolved,
        boolean grouped
) {
    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(label == null ? "" : label, 128);
        buf.writeUtf(categoryLabel == null ? "" : categoryLabel, 64);
        buf.writeVarInt(displayNumber);
        buf.writeUtf(assignmentLabel == null ? "" : assignmentLabel, 128);
        buf.writeBoolean(resolved);
        buf.writeBoolean(grouped);
    }

    public static FccGuiDeviceEntry decode(RegistryFriendlyByteBuf buf) {
        return new FccGuiDeviceEntry(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readUtf(128),
                buf.readBoolean(),
                buf.readBoolean());
    }
}
