package com.createstats.network;

import com.createstats.tuner.TunerHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TunerSurveyPacket(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TunerSurveyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "tuner_survey"));

    public static final StreamCodec<FriendlyByteBuf, TunerSurveyPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeBlockPos(pkt.pos),
            buf -> new TunerSurveyPacket(buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TunerSurveyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final TunerHost host = TunerApplyPacket.reach(player, packet.pos);
            if (host != null) {
                PacketDistributor.sendToPlayer(player, new TunerStationsPacket(packet.pos, host.surveyStations(player)));
            }
        });
    }
}
