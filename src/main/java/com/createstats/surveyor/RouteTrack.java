package com.createstats.surveyor;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record RouteTrack(UUID console, UUID pathway, String name, int state, String reason, double x, double y, double z,
                         float heading, float speed, float s, int nextStop, float eta, float waitFraction, String waitText,
                         int laps, float lastLap, float front, float rear, long consolePos, int cargo, UUID owner,
                         float minRadius, float halfWidth) {
    public static void write(FriendlyByteBuf buf, RouteTrack t) {
        buf.writeUUID(t.console());
        buf.writeUUID(t.pathway());
        buf.writeUtf(t.name(), 48);
        buf.writeVarInt(t.state());
        buf.writeUtf(t.reason(), 96);
        buf.writeDouble(t.x());
        buf.writeDouble(t.y());
        buf.writeDouble(t.z());
        buf.writeFloat(t.heading());
        buf.writeFloat(t.speed());
        buf.writeFloat(t.s());
        buf.writeVarInt(t.nextStop() + 1);
        buf.writeFloat(t.eta());
        buf.writeFloat(t.waitFraction());
        buf.writeUtf(t.waitText(), 96);
        buf.writeVarInt(t.laps());
        buf.writeFloat(t.lastLap());
        buf.writeFloat(t.front());
        buf.writeFloat(t.rear());
        buf.writeLong(t.consolePos());
        buf.writeVarInt(t.cargo() + 1);
        buf.writeUUID(t.owner());
        buf.writeFloat(t.minRadius());
        buf.writeFloat(t.halfWidth());
    }

    public static RouteTrack read(FriendlyByteBuf buf) {
        return new RouteTrack(buf.readUUID(), buf.readUUID(), buf.readUtf(48), buf.readVarInt(), buf.readUtf(96),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readUtf(96), buf.readVarInt(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readVarInt() - 1, buf.readUUID(), buf.readFloat(),
                buf.readFloat());
    }

    static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
