package com.createstats.network;

import com.createstats.blockentity.PowerDistributionUnitBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PduHistoryRequestPacket(BlockPos pduPos, int range, int action) implements CustomPacketPayload {
    public static final int ACTION_SUBSCRIBE = 0;

    public static final int ACTION_KEEPALIVE = 1;

    public static final int ACTION_CLOSE = 2;

    public static final CustomPacketPayload.Type<PduHistoryRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "pdu_history_request"));

    public static final StreamCodec<FriendlyByteBuf, PduHistoryRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pduPos());
                buf.writeVarInt(pkt.range());
                buf.writeVarInt(pkt.action());
            }, buf -> new PduHistoryRequestPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PduHistoryRequestPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (packet.action() == ACTION_SUBSCRIBE) {
                if (!player.level().isLoaded(packet.pduPos())
                        || player.distanceToSqr(packet.pduPos().getX() + 0.5,
                                packet.pduPos().getY() + 0.5, packet.pduPos().getZ() + 0.5) > 64.0 * 64.0) {
                    return;
                }
            }
            if (!(player.level().getBlockEntity(packet.pduPos()) instanceof PowerDistributionUnitBE pdu)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_SUBSCRIBE -> pdu.subscribeHistory(player, packet.range());
                case ACTION_KEEPALIVE -> pdu.keepAliveHistory(player.getUUID(), packet.range());
                case ACTION_CLOSE -> pdu.unsubscribeHistory(player.getUUID());
                default -> {
                }
            }
        });
    }
}
