package com.createstats.chunkloader.net;

import com.createstats.chunkloader.CrownScreenAccess;
import com.createstats.chunkloader.core.CrownPicks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record CrownPickReplyPacket(BlockPos crown, boolean drop, int asked, byte[] verdicts, long refusedAt)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CrownPickReplyPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "crown_pick_reply"));

    public static final StreamCodec<FriendlyByteBuf, CrownPickReplyPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.crown());
                buf.writeBoolean(p.drop());
                buf.writeVarInt(p.asked());
                buf.writeByteArray(p.verdicts());
                buf.writeLong(p.refusedAt());
            },
            buf -> new CrownPickReplyPacket(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(),
                    buf.readByteArray(CrownPickPacket.MAX), buf.readLong()));

    public static CrownPickReplyPacket of(BlockPos crown, boolean drop, int asked, List<CrownPicks.Verdict> verdicts,
                                          long refusedAt) {
        final byte[] b = new byte[verdicts.size()];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) verdicts.get(i).ordinal();
        }
        return new CrownPickReplyPacket(crown, drop, asked, b, refusedAt);
    }

    public List<CrownPicks.Verdict> verdictList() {
        final CrownPicks.Verdict[] all = CrownPicks.Verdict.values();
        final List<CrownPicks.Verdict> out = new ArrayList<>(verdicts.length);
        for (byte b : verdicts) {
            final int o = b & 0xFF;
            out.add(o < all.length ? all[o] : CrownPicks.Verdict.STATION_DARK_FOR_GOOD);
        }
        return out;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrownPickReplyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CrownScreenAccess.pickReply(packet));
    }
}
