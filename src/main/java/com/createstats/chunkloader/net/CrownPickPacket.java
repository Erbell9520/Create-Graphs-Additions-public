package com.createstats.chunkloader.net;

import com.createstats.chunkloader.map.CrownMapService;
import com.createstats.chunkloader.map.CrownMapView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// direction travels with the chunks, toggle would flip a chunk the server already holds
public record CrownPickPacket(BlockPos crown, boolean drop, long[] chunks) implements CustomPacketPayload {
    public static final int MAX = CrownMapView.WINDOW * CrownMapView.WINDOW;

    public static final CustomPacketPayload.Type<CrownPickPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "crown_pick"));

    public static final StreamCodec<FriendlyByteBuf, CrownPickPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.crown());
                buf.writeBoolean(p.drop());
                final int n = Math.min(MAX, p.chunks().length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeLong(p.chunks()[i]);
                }
            },
            buf -> {
                final BlockPos crown = buf.readBlockPos();
                final boolean drop = buf.readBoolean();
                final int n = buf.readVarInt();
                if (n < 0 || n > MAX) {
                    throw new IllegalArgumentException("crown_pick names " + n + " chunks, at most " + MAX);
                }
                final long[] chunks = new long[n];
                for (int i = 0; i < n; i++) {
                    chunks[i] = buf.readLong();
                }
                return new CrownPickPacket(crown, drop, chunks);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrownPickPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                CrownMapService.pick(player, packet);
            }
        });
    }
}
