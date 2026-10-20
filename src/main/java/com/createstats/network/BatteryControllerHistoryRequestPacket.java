package com.createstats.network;

import com.createstats.blockentity.BatteryControllerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BatteryControllerHistoryRequestPacket(BlockPos controllerPos, int range, int action)
        implements CustomPacketPayload {
    public static final int ACTION_SUBSCRIBE = 0;
    public static final int ACTION_KEEPALIVE = 1;
    public static final int ACTION_CLOSE = 2;

    public static final CustomPacketPayload.Type<BatteryControllerHistoryRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "create_stats", "battery_controller_history_request"));

    public static final StreamCodec<FriendlyByteBuf, BatteryControllerHistoryRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.controllerPos());
                buf.writeVarInt(pkt.range());
                buf.writeVarInt(pkt.action());
            }, buf -> new BatteryControllerHistoryRequestPacket(
                    buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BatteryControllerHistoryRequestPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (packet.action() == ACTION_SUBSCRIBE) {
                if (!player.level().isLoaded(packet.controllerPos())
                        || player.distanceToSqr(packet.controllerPos().getX() + 0.5,
                                packet.controllerPos().getY() + 0.5,
                                packet.controllerPos().getZ() + 0.5) > 64.0 * 64.0) {
                    return;
                }
            }
            if (!(player.level().getBlockEntity(packet.controllerPos())
                    instanceof BatteryControllerBE controller)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_SUBSCRIBE -> controller.subscribeHistory(player, packet.range());
                case ACTION_KEEPALIVE -> controller.keepAliveHistory(player.getUUID(), packet.range());
                case ACTION_CLOSE -> controller.unsubscribeHistory(player.getUUID());
                default -> {
                }
            }
        });
    }
}
