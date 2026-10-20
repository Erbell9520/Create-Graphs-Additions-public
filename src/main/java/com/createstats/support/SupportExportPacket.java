package com.createstats.support;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SupportExportPacket(int index, int total, List<String> lines) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportExportPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "support_export"));

    public static final StreamCodec<FriendlyByteBuf, SupportExportPacket> STREAM_CODEC =
        StreamCodec.of(SupportExportPacket::encode, SupportExportPacket::decode);

    public static final int MAX_CHUNKS = 64;

    private static final int CHUNK_CHARS = 200_000;

    private static final int MAX_LINE = 32_767;

    private static void encode(FriendlyByteBuf buf, SupportExportPacket pkt) {
        buf.writeVarInt(pkt.index);
        buf.writeVarInt(pkt.total);
        buf.writeVarInt(pkt.lines.size());
        for (String line : pkt.lines) {
            buf.writeUtf(line, MAX_LINE);
        }
    }

    private static SupportExportPacket decode(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), CHUNK_CHARS);
        List<String> lines = new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            lines.add(buf.readUtf(MAX_LINE));
        }
        return new SupportExportPacket(index, total, lines);
    }

    public static boolean send(ServerPlayer player, List<String> report) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int chars = 0;
        for (String raw : report) {
            String line = raw.length() > MAX_LINE ? raw.substring(0, MAX_LINE - 3) + "..." : raw;
            if (chars + line.length() > CHUNK_CHARS && !current.isEmpty()) {
                chunks.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(line);
            chars += line.length();
        }
        chunks.add(current);
        if (chunks.size() > MAX_CHUNKS) {
            return false;
        }
        for (int i = 0; i < chunks.size(); i++) {
            PacketDistributor.sendToPlayer(player, new SupportExportPacket(i, chunks.size(), chunks.get(i)));
        }
        return true;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportExportPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<String> complete = SupportClientState.acceptExportChunk(pkt.index, pkt.total, pkt.lines);
            if (complete != null) {
                SupportClientAccess.saveExport(complete);
            }
        });
    }
}
