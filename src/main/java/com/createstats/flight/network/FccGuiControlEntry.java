package com.createstats.flight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public record FccGuiControlEntry(
        UUID id,
        String label,
        String widgetType,
        boolean springCentered,
        String curveType,
        int routeCount,
        double value,
        double inputMin,
        double inputMax,
        double neutralValue
) {
    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(label == null ? "" : label, 128);
        buf.writeUtf(widgetType == null ? "" : widgetType, 64);
        buf.writeBoolean(springCentered);
        buf.writeUtf(curveType == null ? "" : curveType, 64);
        buf.writeVarInt(routeCount);
        buf.writeDouble(value);
        buf.writeDouble(inputMin);
        buf.writeDouble(inputMax);
        buf.writeDouble(neutralValue);
    }

    public static FccGuiControlEntry decode(RegistryFriendlyByteBuf buf) {
        return new FccGuiControlEntry(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble());
    }
}
