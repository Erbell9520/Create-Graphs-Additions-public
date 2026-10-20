package com.createstats.chunkloader.net;

import com.createstats.chunkloader.map.CrownMapService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CrownMapRequestPacket(BlockPos crown, int chunkX, int chunkZ, int size) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CrownMapRequestPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "crown_map_request"));

    public static final StreamCodec<FriendlyByteBuf, CrownMapRequestPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.crown());
                buf.writeInt(p.chunkX());
                buf.writeInt(p.chunkZ());
                buf.writeVarInt(p.size());
            },
            buf -> new CrownMapRequestPacket(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrownMapRequestPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                CrownMapService.answer(player, packet);
            }
        });
    }
}
