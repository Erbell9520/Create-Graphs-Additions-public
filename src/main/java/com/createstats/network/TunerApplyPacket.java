package com.createstats.network;

import com.createstats.tuner.TunerHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record TunerApplyPacket(BlockPos pos, String station, int range) implements CustomPacketPayload {
    static final int MAX_ID = 256;

    static final double REACH_SQR = 64.0;

    public static final CustomPacketPayload.Type<TunerApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "tuner_apply"));

    public static final StreamCodec<FriendlyByteBuf, TunerApplyPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeUtf(pkt.station, MAX_ID);
                buf.writeVarInt(pkt.range);
            },
            buf -> new TunerApplyPacket(buf.readBlockPos(), buf.readUtf(MAX_ID), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TunerApplyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final TunerHost host = reach(player, packet.pos);
            if (host != null) {
                host.applyTuning(player, packet.station, host.tunerRange().clamp(packet.range));
            }
        });
    }

    @Nullable
    static TunerHost reach(ServerPlayer player, BlockPos pos) {
        final Level level = player.level();
        if (player.isSpectator() || !player.isAlive() || !level.isLoaded(pos)
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof TunerHost host ? host : null;
    }
}
