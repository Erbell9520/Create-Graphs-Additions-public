package com.createstats.support;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// silent unless every condition holds
public record SupportChallengePacket(byte[] nonce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportChallengePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "support_challenge"));

    public static final StreamCodec<FriendlyByteBuf, SupportChallengePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> buf.writeByteArray(pkt.nonce),
            buf -> new SupportChallengePacket(buf.readByteArray(SupportKeyProof.NONCE_BYTES)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportChallengePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) {
                return;
            }
            byte[] signature = SupportClientState.answerChallenge(context.player().getUUID(), pkt.nonce);
            if (signature != null) {
                context.reply(new SupportProofPacket(signature));
            }
            SupportClientAccess.refreshTerminal();
        });
    }
}
