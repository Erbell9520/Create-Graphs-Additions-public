package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.PathwaySync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SurveyorViewPacket(boolean looking, boolean board) implements CustomPacketPayload {
    public static final Type<SurveyorViewPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_view"));

    public static final StreamCodec<FriendlyByteBuf, SurveyorViewPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.looking());
                buf.writeBoolean(p.board());
            },
            buf -> new SurveyorViewPacket(buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SurveyorViewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PathwaySync.ping(player, packet.looking(), packet.board());
        });
    }
}
