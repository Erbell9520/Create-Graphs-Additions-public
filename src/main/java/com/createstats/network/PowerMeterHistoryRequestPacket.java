package com.createstats.network;

import com.createstats.blockentity.PowerMeterBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PowerMeterHistoryRequestPacket(BlockPos meterPos, int range, int action)
        implements CustomPacketPayload {
    public static final int ACTION_SUBSCRIBE = 0;
    public static final int ACTION_KEEPALIVE = 1;
    public static final int ACTION_CLOSE = 2;

    public static final CustomPacketPayload.Type<PowerMeterHistoryRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "power_meter_history_request"));

    public static final StreamCodec<FriendlyByteBuf, PowerMeterHistoryRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.meterPos());
                buf.writeVarInt(pkt.range());
                buf.writeVarInt(pkt.action());
            }, buf -> new PowerMeterHistoryRequestPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PowerMeterHistoryRequestPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (packet.action() == ACTION_SUBSCRIBE) {
                if (!player.level().isLoaded(packet.meterPos())
                        || player.distanceToSqr(packet.meterPos().getX() + 0.5,
                                packet.meterPos().getY() + 0.5,
                                packet.meterPos().getZ() + 0.5) > 64.0 * 64.0) {
                    return;
                }
            }
            if (!(player.level().getBlockEntity(packet.meterPos()) instanceof PowerMeterBE meter)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_SUBSCRIBE -> meter.subscribeHistory(player, packet.range());
                case ACTION_KEEPALIVE -> meter.keepAliveHistory(player.getUUID(), packet.range());
                case ACTION_CLOSE -> meter.unsubscribeHistory(player.getUUID());
                default -> {
                }
            }
        });
    }
}
