package com.createstats.support;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// never grants anything, the server re-decides
public record SupportStatusPacket(int level, String basis, String note, int grantSecondsLeft)
    implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportStatusPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "support_status"));

    public static final StreamCodec<FriendlyByteBuf, SupportStatusPacket> STREAM_CODEC =
        StreamCodec.of(SupportStatusPacket::encode, SupportStatusPacket::decode);

    private static void encode(FriendlyByteBuf buf, SupportStatusPacket pkt) {
        buf.writeVarInt(pkt.level);
        buf.writeUtf(pkt.basis, 32);
        buf.writeUtf(pkt.note.length() > 160 ? pkt.note.substring(0, 160) : pkt.note, 160);
        buf.writeVarInt(pkt.grantSecondsLeft + 1);
    }

    private static SupportStatusPacket decode(FriendlyByteBuf buf) {
        return new SupportStatusPacket(buf.readVarInt(), buf.readUtf(32), buf.readUtf(160), buf.readVarInt() - 1);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportStatusPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            SupportClientState.acceptStatus(pkt.level, pkt.basis, pkt.note, pkt.grantSecondsLeft);
            SupportClientAccess.refreshTerminal();
        });
    }
}
