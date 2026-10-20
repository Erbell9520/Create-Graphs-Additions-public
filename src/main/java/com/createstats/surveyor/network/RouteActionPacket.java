package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.RouteOrders;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RouteActionPacket(int op, UUID console, long pos, UUID pathway, int a) implements CustomPacketPayload {
    public static final UUID NONE = new UUID(0L, 0L);

    public static final int PICK = 0;

    public static final int ASSIGN = 1;
    public static final int UNASSIGN = 2;

    public static final int START = 3;
    public static final int PAUSE = 4;
    public static final int SKIP_STOP = 5;

    public static final int SEND_TO = 6;
    public static final int RECALL = 7;

    public static final int ARM_RECORD = 8;

    public static final int STOP_RECORD = 9;

    public static final int OPTION = 10;

    public static final int CANCEL_RECORD = 11;

    public static final int KEEP_TRACE = 12;

    public static final int REMOVE_SCHEDULE = 13;

    public static final Type<RouteActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "route_action"));

    public static final StreamCodec<FriendlyByteBuf, RouteActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.op());
                buf.writeUUID(p.console());
                buf.writeLong(p.pos());
                buf.writeUUID(p.pathway());
                buf.writeVarInt(p.a());
            },
            buf -> new RouteActionPacket(buf.readVarInt(), buf.readUUID(), buf.readLong(), buf.readUUID(), buf.readVarInt()));

    public RouteActionPacket {
        console = console == null ? NONE : console;
        pathway = pathway == null ? NONE : pathway;
    }

    public static RouteActionPacket to(int op, UUID console) {
        return new RouteActionPacket(op, console, 0L, NONE, 0);
    }

    public static RouteActionPacket at(int op, long pos, int a) {
        return new RouteActionPacket(op, NONE, pos, NONE, a);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) RouteOrders.apply(player, packet);
        });
    }
}
