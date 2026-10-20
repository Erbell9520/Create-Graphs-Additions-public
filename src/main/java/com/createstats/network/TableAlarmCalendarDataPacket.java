package com.createstats.network;

import com.createstats.alarm.TableAlarmCalendarEvent;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TableAlarmCalendarDataPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TableAlarmCalendarDataPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "table_alarm_calendar_data"));
    public static final StreamCodec<FriendlyByteBuf, TableAlarmCalendarDataPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), TableAlarmCalendarDataPacket::new);

    private final List<TableAlarmCalendarEvent> events;

    public TableAlarmCalendarDataPacket(List<TableAlarmCalendarEvent> events) {
        this.events = List.copyOf(events);
    }

    public TableAlarmCalendarDataPacket(FriendlyByteBuf buf) {
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

    public static void handle(TableAlarmCalendarDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TableAlarmClockClientHooks.handleCalendarData(packet.events));
    }
}
