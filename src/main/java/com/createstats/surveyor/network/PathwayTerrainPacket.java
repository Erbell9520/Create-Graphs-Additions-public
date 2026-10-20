package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record PathwayTerrainPacket(UUID pathway, int x0, int z0, int cell, int w, int h, byte[] colours)
        implements CustomPacketPayload {
    public static final int MAX_CELLS = 256 * 256;

    public static final Type<PathwayTerrainPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pathway_terrain"));

    public static final StreamCodec<FriendlyByteBuf, PathwayTerrainPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.pathway());
                buf.writeVarInt(p.x0());
                buf.writeVarInt(p.z0());
                buf.writeVarInt(p.cell());
                buf.writeVarInt(p.w());
                buf.writeVarInt(p.h());
                buf.writeByteArray(p.colours());
            },
            buf -> {
                UUID id = buf.readUUID();
                int x0 = buf.readVarInt();
                int z0 = buf.readVarInt();
                int cell = Math.max(1, buf.readVarInt());
                int w = Math.max(0, buf.readVarInt());
                int h = Math.max(0, buf.readVarInt());
                byte[] c = buf.readByteArray(MAX_CELLS);
                return new PathwayTerrainPacket(id, x0, z0, cell, w, h, c);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PathwayTerrainPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SurveyorClientBridge.call("terrain", PathwayTerrainPacket.class, packet));
    }
}
