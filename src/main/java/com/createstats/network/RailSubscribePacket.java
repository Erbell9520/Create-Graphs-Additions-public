package com.createstats.network;

import com.createstats.train.smart.RailNetworkService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RailSubscribePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RailSubscribePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "rail_subscribe"));

    public static final StreamCodec<FriendlyByteBuf, RailSubscribePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), RailSubscribePacket::new);

    private final boolean active;

    public RailSubscribePacket(boolean active) {
        this.active = active;
    }

    public RailSubscribePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.active);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RailSubscribePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (packet.active) {
                RailNetworkService.subscribe(player);
            } else {
                RailNetworkService.unsubscribe(player);
            }
        });
    }
}
