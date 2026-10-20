package com.createstats.network;

import com.createstats.blockentity.PowerLimiterBE;
import com.createstats.power.LimiterLadder;
import com.createstats.power.LimiterMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PowerLimiterConfigPacket(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int ACTION_SET_NOTCH = 0;

    public static final int ACTION_SET_MODE = 1;

    public static final int ACTION_RESET = 2;

    public static final CustomPacketPayload.Type<PowerLimiterConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "power_limiter_config"));

    public static final StreamCodec<FriendlyByteBuf, PowerLimiterConfigPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeVarInt(pkt.action());
                buf.writeVarInt(pkt.value());
            }, buf -> new PowerLimiterConfigPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PowerLimiterConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(packet.pos())
                    || player.distanceToSqr(packet.pos().getX() + 0.5,
                            packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos()) instanceof PowerLimiterBE limiter)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_SET_NOTCH -> limiter.setNotch(LimiterLadder.clamp(packet.value()));
                case ACTION_SET_MODE -> limiter.setMode(LimiterMode.byOrdinal(packet.value()));
                case ACTION_RESET -> limiter.resetTotals();
                default -> {
                }
            }
        });
    }
}
