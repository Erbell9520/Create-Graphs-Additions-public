package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record PathwayHealthPacket(UUID pathway, long version, byte[] items) implements CustomPacketPayload {
    public static final int MAX_BYTES = 16 * 1024;

    public static final Type<PathwayHealthPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pathway_health"));

    public static final StreamCodec<FriendlyByteBuf, PathwayHealthPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.pathway());
                buf.writeVarLong(p.version());
                buf.writeByteArray(p.items());
            },
            buf -> new PathwayHealthPacket(buf.readUUID(), buf.readVarLong(), buf.readByteArray(MAX_BYTES)));

    public PathwayHealthPacket {
        items = items == null ? new byte[0] : items;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PathwayHealthPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SurveyorClientBridge.call("health", PathwayHealthPacket.class, packet));
    }
}
