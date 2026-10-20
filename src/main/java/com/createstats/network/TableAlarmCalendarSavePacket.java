package com.createstats.network;

import com.createstats.alarm.TableAlarmCalendarEvent;
import com.createstats.alarm.TableAlarmCalendarSavedData;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TableAlarmCalendarSavePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TableAlarmCalendarSavePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "table_alarm_calendar_save"));
    public static final StreamCodec<FriendlyByteBuf, TableAlarmCalendarSavePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), TableAlarmCalendarSavePacket::new);

    private final List<TableAlarmCalendarEvent> events;

    public TableAlarmCalendarSavePacket(List<TableAlarmCalendarEvent> events) {
        this.events = List.copyOf(events);
    }

    public TableAlarmCalendarSavePacket(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        this.events = List.copyOf(TableAlarmCalendarEvent.loadPacketTag(tag));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(TableAlarmCalendarEvent.savePacketTag(this.events));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TableAlarmCalendarSavePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null || player.getServer() == null) {
                return;
            }
            ServerLevel storageLevel = player.getServer().overworld();
            TableAlarmCalendarSavedData data = TableAlarmCalendarSavedData.get(storageLevel);
            data.replaceEvents(player.getUUID(), packet.events);
            PacketDistributor.sendToPlayer(player, new TableAlarmCalendarDataPacket(data.getEvents(player.getUUID())));
        });
    }
}
