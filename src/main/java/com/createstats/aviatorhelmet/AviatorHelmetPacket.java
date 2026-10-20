package com.createstats.aviatorhelmet;

import com.createstats.CreateStats;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AviatorHelmetPacket(int action) implements CustomPacketPayload {
    public static final int FLICK_GOGGLES = 0;
    public static final int TONE = 1;

    public static final Type<AviatorHelmetPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_helmet"));

    public static final StreamCodec<ByteBuf, AviatorHelmetPacket> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(AviatorHelmetPacket::new, AviatorHelmetPacket::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AviatorHelmetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            switch (packet.action()) {
                case FLICK_GOGGLES -> AviatorHelmets.flickGoggles(sender);
                case TONE -> AviatorHelmets.cycleTone(sender);
                default -> {
                }
            }
        });
    }
}
