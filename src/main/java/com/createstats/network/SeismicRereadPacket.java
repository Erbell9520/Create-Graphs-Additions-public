package com.createstats.network;

import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.seismic.archive.SurveyReread;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// only when the client lacks something, checked again on arrival
public record SeismicRereadPacket(BlockPos rig, List<Held> held) implements CustomPacketPayload {
    public record Held(String station, long at) {
    }

    public static final CustomPacketPayload.Type<SeismicRereadPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "seismic_reread"));

    public static final StreamCodec<FriendlyByteBuf, SeismicRereadPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.rig());
                final List<Held> sent = new ArrayList<>();
                for (final Held h : pkt.held()) {
                    if (sent.size() < SurveyReread.MAX_HELD && h.station().length() <= SeismicVeinsPacket.MAX_STATION) {
                        sent.add(h);
                    }
                }
                buf.writeVarInt(sent.size());
                for (final Held h : sent) {
                    buf.writeUtf(h.station(), SeismicVeinsPacket.MAX_STATION);
                    buf.writeLong(h.at());
                }
            },
            buf -> {
                final BlockPos rig = buf.readBlockPos();
                final int n = buf.readVarInt();
                if (n < 0 || n > SurveyReread.MAX_HELD) {
                    throw new IllegalArgumentException("seismic_reread names " + n + " maps, at most "
                            + SurveyReread.MAX_HELD);
                }
                final List<Held> held = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    held.add(new Held(buf.readUtf(SeismicVeinsPacket.MAX_STATION), buf.readLong()));
                }
                return new SeismicRereadPacket(rig, List.copyOf(held));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeismicRereadPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }

            if (!level.isLoaded(packet.rig()) || !(level.getBlockEntity(packet.rig()) instanceof SeismicScannerBE rig)) {
                return;
            }
            rig.reread(player, packet.held());
        });
    }
}
