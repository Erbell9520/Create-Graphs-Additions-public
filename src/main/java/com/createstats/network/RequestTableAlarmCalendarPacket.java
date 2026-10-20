package com.createstats.network;

import com.createstats.alarm.TableAlarmCalendarSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RequestTableAlarmCalendarPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestTableAlarmCalendarPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_table_alarm_calendar"));
    public static final StreamCodec<FriendlyByteBuf, RequestTableAlarmCalendarPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestTableAlarmCalendarPacket::new);

    public RequestTableAlarmCalendarPacket() {
    }

    public RequestTableAlarmCalendarPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestTableAlarmCalendarPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null || player.getServer() == null) {
                return;
            }
            ServerLevel storageLevel = player.getServer().overworld();
            TableAlarmCalendarSavedData data = TableAlarmCalendarSavedData.get(storageLevel);
            PacketDistributor.sendToPlayer(player, new TableAlarmCalendarDataPacket(data.getEvents(player.getUUID())));
        });
    }
}
