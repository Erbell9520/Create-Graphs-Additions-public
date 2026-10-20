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

// own payload, the update tag carries every channel's history on a debounce
public record AerialFeedPacket(BlockPos panelPos, byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES = 256 * 1024;

    public static final CustomPacketPayload.Type<AerialFeedPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "aerial_feed"));

    public static final StreamCodec<FriendlyByteBuf, AerialFeedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeByteArray(pkt.data());
            },
            buf -> {
                final BlockPos pos = buf.readBlockPos();
                final byte[] data = buf.readByteArray(MAX_BYTES);
                if (data == null) {
                    throw new DecoderException("aerial feed without a body");
                }
                return new AerialFeedPacket(pos, data);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AerialFeedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final Level level = ctx.player().level();
            if (level.isLoaded(packet.panelPos())
                    && level.getBlockEntity(packet.panelPos()) instanceof StatsDisplayPanelBE panel) {
                panel.aerialLinks().acceptFeed(packet.data());
            }
        });
    }
}
