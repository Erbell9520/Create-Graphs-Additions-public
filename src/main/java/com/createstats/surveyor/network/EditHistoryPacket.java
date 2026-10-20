package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record EditHistoryPacket(List<String> undo, List<String> redo) implements CustomPacketPayload {
    public static final int MAX_NAMES = 4;
    public static final int MAX_NAME = 80;

    public static final Type<EditHistoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pathway_edit_history"));

    public static final StreamCodec<FriendlyByteBuf, EditHistoryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                write(buf, p.undo());
                write(buf, p.redo());
            },
            buf -> new EditHistoryPacket(read(buf), read(buf)));

    private static void write(FriendlyByteBuf buf, List<String> names) {
        buf.writeVarInt(names.size());
        for (String s : names) buf.writeUtf(s, MAX_NAME);
    }

    private static List<String> read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_NAMES) throw new io.netty.handler.codec.DecoderException("edit history of " + n + " names");
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(MAX_NAME));
        return out;
    }

    public EditHistoryPacket {
        undo = cut(undo);
        redo = cut(redo);
    }

    private static List<String> cut(List<String> names) {
        List<String> out = new ArrayList<>(MAX_NAMES);
        if (names == null) return out;
        for (String s : names) {
            if (out.size() >= MAX_NAMES) break;
            if (s == null) continue;
            out.add(s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s);
        }
        return List.copyOf(out);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EditHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SurveyorClientBridge.call("history", EditHistoryPacket.class, packet));
    }
}
