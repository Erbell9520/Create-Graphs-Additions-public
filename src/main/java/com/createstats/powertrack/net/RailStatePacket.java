package com.createstats.powertrack.net;

import com.createstats.CreateStats;
import com.createstats.powertrack.SectionView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RailStatePacket(UUID graph, int fingerprint, List<SectionView> sections) implements CustomPacketPayload {
    public static final int MAX_SECTIONS = 4096;

    public static final Type<RailStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "power_track_state"));

    public static final StreamCodec<FriendlyByteBuf, RailStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.graph());
                buf.writeInt(p.fingerprint());
                final int n = Math.min(MAX_SECTIONS, p.sections().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    final SectionView v = p.sections().get(i);
                    buf.writeByte(v.state());
                    buf.writeVarInt(v.feeders());
                    buf.writeVarInt(v.cars());
                    buf.writeVarInt(v.flow());
                }
            },
            buf -> {
                final UUID graph = buf.readUUID();
                final int fingerprint = buf.readInt();
                final int n = Math.min(MAX_SECTIONS, buf.readVarInt());
                final List<SectionView> sections = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    sections.add(new SectionView(Math.floorMod(buf.readByte(), 4), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt()));
                }
                return new RailStatePacket(graph, fingerprint, sections);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RailStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.powertrack.client.ClientRail.acceptStates(packet));
    }
}
