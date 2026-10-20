package com.createstats.network;

import com.createstats.menu.SecurityLockerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockerScrollPacket(int containerId, int row) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LockerScrollPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "locker_scroll"));

    public static final StreamCodec<FriendlyByteBuf, LockerScrollPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeVarInt(pkt.containerId());
                buf.writeVarInt(pkt.row());
            }, buf -> new LockerScrollPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockerScrollPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            if (sender.containerMenu instanceof SecurityLockerMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.setScrollRow(packet.row());
            }
        });
    }
}
