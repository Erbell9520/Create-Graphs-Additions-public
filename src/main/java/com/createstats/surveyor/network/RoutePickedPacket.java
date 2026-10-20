package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RoutePickedPacket(UUID console, String name, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<RoutePickedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "route_picked"));

    public static final StreamCodec<FriendlyByteBuf, RoutePickedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.console());
                buf.writeUtf(p.name(), 48);
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
            },
            buf -> new RoutePickedPacket(buf.readUUID(), buf.readUtf(48), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RoutePickedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SurveyorClientBridge.call("picked", RoutePickedPacket.class, packet));
    }
}
