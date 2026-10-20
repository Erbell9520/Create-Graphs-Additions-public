package com.createstats.network;

import com.createstats.tuner.TunerStation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record TunerStationsPacket(BlockPos pos, List<TunerStation> stations) implements CustomPacketPayload {
    static final int MAX_STATIONS = 96;
    private static final int MAX_TEXT = 256;

    public static final CustomPacketPayload.Type<TunerStationsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "tuner_stations"));

    public static final StreamCodec<FriendlyByteBuf, TunerStationsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                final int n = Math.min(MAX_STATIONS, pkt.stations.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    final TunerStation s = pkt.stations.get(i);
                    buf.writeUtf(s.id(), MAX_TEXT);
                    buf.writeUtf(s.labelKey(), MAX_TEXT);
                    buf.writeUtf(s.subLabel(), MAX_TEXT);
                    buf.writeUtf(s.icon(), MAX_TEXT);
                    buf.writeInt(s.colour());
                    buf.writeVarInt(s.minRange());
                    buf.writeVarInt(s.style());
                    buf.writeVarInt(s.dial());
                }
            },
            buf -> {
                final BlockPos pos = buf.readBlockPos();
                final int n = Math.min(MAX_STATIONS, Math.max(0, buf.readVarInt()));
                final List<TunerStation> stations = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    stations.add(new TunerStation(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                            buf.readUtf(MAX_TEXT), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                return new TunerStationsPacket(pos, List.copyOf(stations));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TunerStationsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.createstats.client.tuner.TunerSurveys.receive(packet.pos, packet.stations));
    }
}
