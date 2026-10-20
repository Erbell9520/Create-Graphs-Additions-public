package com.createstats.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// only to who stood within 8 blocks, otherwise it's an x-ray from across the map
public record SeismicVeinsPacket(BlockPos rig, long impactAt, String station, int flags, List<Vein> veins)
        implements CustomPacketPayload {
    public static final int MAX_VEINS = 96;

    public static final int MAX_CELLS = 16_384;
    private static final int MAX_LABEL = 32;

    public static final int MAX_STATION = 256;

    public static final int OPENS = 1;

    public static final int REPLAY = 2;

    public record Vein(String label, int colour, int count, int echo, float cx, float cy, float cz, int cell,
                       int[] cells) {
    }

    public SeismicVeinsPacket(BlockPos rig, long impactAt, List<Vein> veins) {
        this(rig, impactAt, "", 0, veins);
    }

    public boolean replay() {
        return (flags & REPLAY) != 0;
    }

    public boolean opens() {
        return (flags & OPENS) != 0;
    }

    public static final CustomPacketPayload.Type<SeismicVeinsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "seismic_veins"));

    public static final StreamCodec<FriendlyByteBuf, SeismicVeinsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.rig);
                buf.writeLong(pkt.impactAt);
                buf.writeUtf(pkt.station.length() <= MAX_STATION ? pkt.station : "", MAX_STATION);
                buf.writeByte(pkt.flags & (OPENS | REPLAY));
                final int n = Math.min(MAX_VEINS, pkt.veins.size());
                buf.writeVarInt(n);
                int budget = MAX_CELLS;
                for (int i = 0; i < n; i++) {
                    final Vein v = pkt.veins.get(i);
                    buf.writeUtf(v.label(), MAX_LABEL);
                    buf.writeInt(v.colour());
                    buf.writeVarInt(v.count());
                    buf.writeVarInt(v.echo());
                    buf.writeFloat(v.cx());
                    buf.writeFloat(v.cy());
                    buf.writeFloat(v.cz());
                    buf.writeByte(v.cell());
                    final int cells = Math.max(0, Math.min(budget, v.cells().length));
                    budget -= cells;
                    buf.writeVarInt(cells);
                    for (int k = 0; k < cells; k++) {
                        final int c = v.cells()[k];
                        buf.writeByte(((c >>> 16) & 0xFF) - 128);
                        buf.writeByte(((c >>> 8) & 0xFF) - 128);
                        buf.writeByte((c & 0xFF) - 128);
                    }
                }
            },
            buf -> {
                final BlockPos rig = buf.readBlockPos();
                final long impactAt = buf.readLong();
                final String station = buf.readUtf(MAX_STATION);
                final int flags = buf.readByte() & (OPENS | REPLAY);
                final int n = Math.min(MAX_VEINS, Math.max(0, buf.readVarInt()));
                final List<Vein> veins = new ArrayList<>(n);
                int budget = MAX_CELLS;
                for (int i = 0; i < n; i++) {
                    final String label = buf.readUtf(MAX_LABEL);
                    final int colour = buf.readInt();
                    final int count = buf.readVarInt();
                    final int echo = buf.readVarInt();
                    final float cx = buf.readFloat();
                    final float cy = buf.readFloat();
                    final float cz = buf.readFloat();
                    final int cell = Math.max(1, Math.min(8, buf.readByte()));
                    final int cells = Math.min(budget, Math.max(0, buf.readVarInt()));
                    budget -= cells;
                    final int[] packed = new int[cells];
                    for (int k = 0; k < cells; k++) {
                        final int x = buf.readByte() + 128;
                        final int y = buf.readByte() + 128;
                        final int z = buf.readByte() + 128;
                        packed[k] = (x << 16) | (y << 8) | z;
                    }
                    veins.add(new Vein(label, colour, count, echo, cx, cy, cz, cell, packed));
                }
                return new SeismicVeinsPacket(rig, impactAt, station, flags, List.copyOf(veins));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeismicVeinsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.createstats.seismic.client.SeismicReveals.accept(packet));
    }
}
