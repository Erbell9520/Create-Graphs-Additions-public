package com.createstats.support;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SupportHelloPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportHelloPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "support_hello"));

    public static final StreamCodec<FriendlyByteBuf, SupportHelloPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> { }, buf -> new SupportHelloPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportHelloPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !SupportSessions.access(player).atLeast(SupportPolicy.Level.SAFE)
                || !SupportSessions.allowRequest(player, false)) {
                return;
            }
            SupportSessions.sendStatus(player);
            SupportSessions.issueChallenge(player);
        });
    }
}
