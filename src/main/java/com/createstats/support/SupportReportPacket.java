package com.createstats.support;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SupportReportPacket(String title, List<String> lines) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportReportPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "support_report"));

    public static final StreamCodec<FriendlyByteBuf, SupportReportPacket> STREAM_CODEC =
        StreamCodec.of(SupportReportPacket::encode, SupportReportPacket::decode);

    private static final int MAX_LINES = 1000;
    private static final int MAX_LINE_LENGTH = 400;

    private static void encode(FriendlyByteBuf buf, SupportReportPacket pkt) {
        buf.writeUtf(pkt.title, 64);
        List<String> lines = pkt.lines;
        int count = Math.min(lines.size(), MAX_LINES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH - 3) + "...";
            }
            buf.writeUtf(line, MAX_LINE_LENGTH);
        }
    }

    private static SupportReportPacket decode(FriendlyByteBuf buf) {
        String title = buf.readUtf(64);
        int count = Math.min(buf.readVarInt(), MAX_LINES);
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(buf.readUtf(MAX_LINE_LENGTH));
        }
        return new SupportReportPacket(title, lines);
    }

    public static SupportReportPacket of(String title, List<String> lines) {
        if (lines.size() <= MAX_LINES) {
            return new SupportReportPacket(title, lines);
        }
        List<String> trimmed = new ArrayList<>(lines.subList(0, MAX_LINES - 1));
        trimmed.add("... truncated, " + (lines.size() - MAX_LINES + 1)
            + " more lines. Use the export button for the complete report.");
        return new SupportReportPacket(title, trimmed);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportReportPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> SupportClientAccess.deliverReport(pkt.title, pkt.lines));
    }
}
