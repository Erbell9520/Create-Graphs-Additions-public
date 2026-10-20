package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.RouteEvents;
import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.SurveyorClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RouteTrackPacket(long time, List<RouteTrack> tracks, List<RouteEvents.Line> log, boolean logChanged)
        implements CustomPacketPayload {
    public static final Type<RouteTrackPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "route_tracks"));

    public static final StreamCodec<FriendlyByteBuf, RouteTrackPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.time());
                buf.writeVarInt(p.tracks().size());
                for (RouteTrack t : p.tracks()) RouteTrack.write(buf, t);
                buf.writeBoolean(p.logChanged());
                if (p.logChanged()) {
                    buf.writeVarInt(p.log().size());
                    for (RouteEvents.Line l : p.log()) {
                        buf.writeLong(l.time());
                        buf.writeUUID(l.pathway());
                        buf.writeUtf(l.text(), 160);
                    }
                }
            },
            buf -> {
                long time = buf.readLong();
                int n = Math.min(buf.readVarInt(), 512);
                List<RouteTrack> tracks = new ArrayList<>(n);
                for (int i = 0; i < n; i++) tracks.add(RouteTrack.read(buf));
                boolean changed = buf.readBoolean();
                List<RouteEvents.Line> log = new ArrayList<>();
                if (changed) {
                    int m = Math.min(buf.readVarInt(), RouteEvents.KEEP);
                    for (int i = 0; i < m; i++) {
                        long t = buf.readLong();
                        UUID id = buf.readUUID();
                        log.add(new RouteEvents.Line(t, id, buf.readUtf(160)));
                    }
                }
                return new RouteTrackPacket(time, tracks, log, changed);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteTrackPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SurveyorClientBridge.call("tracks", RouteTrackPacket.class, packet));
    }
}
