package com.createstats.flight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public record FccGuiRouteEntry(
        UUID id,
        UUID controlId,
        UUID targetGroupId,
        String targetLabel,
        String outputType,
        double scale,
        boolean invert
) {
    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUUID(controlId);
        buf.writeUUID(targetGroupId);
        buf.writeUtf(targetLabel == null ? "" : targetLabel, 128);
        buf.writeUtf(outputType == null ? "" : outputType, 64);
        buf.writeDouble(scale);
        buf.writeBoolean(invert);
    }

    public static FccGuiRouteEntry decode(RegistryFriendlyByteBuf buf) {
        return new FccGuiRouteEntry(
                buf.readUUID(),
                buf.readUUID(),
                buf.readUUID(),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readDouble(),
                buf.readBoolean());
    }
}
