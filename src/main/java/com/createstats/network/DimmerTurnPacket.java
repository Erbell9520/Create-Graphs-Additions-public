package com.createstats.network;

import com.createstats.block.RotaryDimmerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DimmerTurnPacket(BlockPos pos, int delta) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DimmerTurnPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "dimmer_turn"));

    public static final StreamCodec<FriendlyByteBuf, DimmerTurnPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeByte(Integer.signum(pkt.delta));
            },
            buf -> new DimmerTurnPacket(buf.readBlockPos(), buf.readByte()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DimmerTurnPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final Level level = player.level();
            final BlockPos pos = packet.pos;
            if (!level.isLoaded(pos)
                    || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return;
            }
            if (level.getBlockState(pos).getBlock() instanceof RotaryDimmerBlock dimmer) {
                dimmer.turn(level, pos, Integer.signum(packet.delta));
            }
        });
    }
}
