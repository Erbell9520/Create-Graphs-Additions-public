package com.createstats.network;

import com.createstats.gui.pdu.PduHistory;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PduHistoryPacket(BlockPos pduPos, int range, boolean full,
                               short[] supply, short[] demand, byte[] shed) implements CustomPacketPayload {
    public static PduHistoryPacket full(BlockPos pos, int range, short[] supply, short[] demand, byte[] shed) {
        return new PduHistoryPacket(pos, range, true, supply, demand, shed);
    }

    public static PduHistoryPacket delta(BlockPos pos, int range, short supply, short demand, byte shed) {
        return new PduHistoryPacket(pos, range, false, new short[]{supply}, new short[]{demand}, new byte[]{shed});
    }

    public static final CustomPacketPayload.Type<PduHistoryPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "pdu_history"));

    public static final StreamCodec<FriendlyByteBuf, PduHistoryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pduPos());
                buf.writeVarInt(pkt.range());
                buf.writeBoolean(pkt.full());
                final int n = pkt.supply().length;
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeShort(pkt.supply()[i]);
                    buf.writeShort(pkt.demand()[i]);
                    buf.writeByte(pkt.shed()[i]);
                }
            },
            buf -> {
                final BlockPos pos = buf.readBlockPos();
                final int range = buf.readVarInt();
                final boolean full = buf.readBoolean();
                final int n = buf.readVarInt();
                if (n < 0 || n > PduHistory.SIZE) {
                    throw new DecoderException("PDU history sample count out of range: " + n);
                }
                final short[] supply = new short[n];
                final short[] demand = new short[n];
                final byte[] shed = new byte[n];
                for (int i = 0; i < n; i++) {
                    supply[i] = buf.readShort();
                    demand[i] = buf.readShort();
                    shed[i] = buf.readByte();
                }
                return new PduHistoryPacket(pos, range, full, supply, demand, shed);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PduHistoryPacket packet, IPayloadContext ctx) {
        // Routed through the reflection guarded client hook, so this class stays loadable on a

        ctx.enqueueWork(() -> StatsClientHooks.handlePduHistory(
                packet.pduPos(), packet.range(), packet.full(), packet.supply(), packet.demand(), packet.shed()));
    }
}
