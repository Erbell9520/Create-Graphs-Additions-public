package com.createstats.network;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.seismic.archive.SceneCodec;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SeismicScenePacket(BlockPos panelPos, long stamp, boolean full, byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES = SceneCodec.MAX_INFLATED;

    public static final CustomPacketPayload.Type<SeismicScenePacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "seismic_scene"));

    public static final StreamCodec<FriendlyByteBuf, SeismicScenePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeLong(pkt.stamp());
                buf.writeBoolean(pkt.full());
                buf.writeByteArray(pkt.data());
            },
            buf -> {
                final BlockPos pos = buf.readBlockPos();
                final long stamp = buf.readLong();
                final boolean full = buf.readBoolean();
                final byte[] data = buf.readByteArray(MAX_BYTES);
                if (data == null) {
                    throw new DecoderException("seismic scene without a body");
                }
                return new SeismicScenePacket(pos, stamp, full, data);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeismicScenePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final Level level = ctx.player().level();
            if (level.isLoaded(packet.panelPos())
                    && level.getBlockEntity(packet.panelPos()) instanceof StatsDisplayPanelBE panel) {
                final StatsDisplayPanelBE controller = panel.getControllerBE() != null ? panel.getControllerBE() : panel;
                controller.seismicLinks().acceptScene(packet.stamp(), packet.full(), packet.data());
            }
        });
    }
}
