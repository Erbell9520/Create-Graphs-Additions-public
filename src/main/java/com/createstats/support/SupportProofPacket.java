package com.createstats.support;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SupportProofPacket(byte[] signature) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportProofPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "support_proof"));

    public static final StreamCodec<FriendlyByteBuf, SupportProofPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> buf.writeByteArray(pkt.signature),
            buf -> new SupportProofPacket(buf.readByteArray(SupportKeyProof.MAX_SIGNATURE_BYTES)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportProofPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            // anyone can send it, only the creator with a live challenge counts
            if (context.player() instanceof ServerPlayer player && SupportAccess.isCreator(player)) {
                SupportSessions.acceptProof(player, pkt.signature);
            }
        });
    }
}
