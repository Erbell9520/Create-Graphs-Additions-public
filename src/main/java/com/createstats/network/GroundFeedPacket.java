package com.createstats.network;

import com.createstats.blockentity.StatsDisplayPanelBE;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GroundFeedPacket(BlockPos panelPos, byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES = 256 * 1024;

    public static final CustomPacketPayload.Type<GroundFeedPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "ground_feed"));

    public static final StreamCodec<FriendlyByteBuf, GroundFeedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeByteArray(pkt.data());
            },
            buf -> {
                final BlockPos pos = buf.readBlockPos();
                final byte[] data = buf.readByteArray(MAX_BYTES);
                if (data == null) {
                    throw new DecoderException("ground feed without a body");
                }
                return new GroundFeedPacket(pos, data);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GroundFeedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final Level level = ctx.player().level();
            if (level.isLoaded(packet.panelPos())
                    && level.getBlockEntity(packet.panelPos()) instanceof StatsDisplayPanelBE panel) {
                panel.groundLinks().acceptFeed(packet.data());
            }
        });
    }
}
