package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DriverHelmetPacket(int action) implements CustomPacketPayload {
    public static final int FLICK_VISOR = 0;
    public static final int NIGHT_VISION = 1;
    public static final int THEME = 2;

    public static final Type<DriverHelmetPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet"));

    public static final StreamCodec<ByteBuf, DriverHelmetPacket> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(DriverHelmetPacket::new, DriverHelmetPacket::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriverHelmetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            switch (packet.action()) {
                case FLICK_VISOR -> DriverHelmets.flickVisor(sender);
                case NIGHT_VISION -> DriverHelmets.toggleNightVision(sender);
                case THEME -> DriverHelmets.cycleTheme(sender);
                default -> {
                }
            }
        });
    }
}
