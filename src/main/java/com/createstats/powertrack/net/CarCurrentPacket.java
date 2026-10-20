package com.createstats.powertrack.net;

import com.createstats.CreateStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CarCurrentPacket(int entityId, int current, int rating) implements CustomPacketPayload {
    public static final Type<CarCurrentPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "power_track_current"));

    public static final StreamCodec<FriendlyByteBuf, CarCurrentPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeInt(p.current());
                buf.writeVarInt(Math.max(0, p.rating()));
            },
            buf -> new CarCurrentPacket(buf.readVarInt(), buf.readInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CarCurrentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.powertrack.client.ClientRail.acceptCurrent(packet));
    }
}
