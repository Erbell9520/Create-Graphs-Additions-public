package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.PathwayOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// one packet, op codes, checked in one place
public record SurveyorEditPacket(int op, UUID pathway, long version, int node, double x, double y, double z, int a,
                                 String text, byte[] data) implements CustomPacketPayload {
    public static final int MAX_DATA = 32 * 1024;
    public static final int MAX_TEXT = 64;
    public static final UUID NONE = new UUID(0L, 0L);

    public static final int CREATE = 0;

    public static final int APPEND = 1;

    public static final int APPEND_STROKE = 2;
    public static final int CLOSE_LOOP = 3;

    public static final int END_ONE_WAY = 4;
    public static final int MOVE_NODE = 5;

    public static final int INSERT_AFTER = 6;
    public static final int DELETE_NODE = 7;

    public static final int SET_SPEED = 8;
    public static final int SET_CORNER = 9;
    public static final int SET_NODE_NAME = 10;

    public static final int SET_STOP = 11;
    public static final int SET_NAME = 12;
    public static final int SET_COLOUR = 13;
    public static final int SET_KIND = 14;
    public static final int SET_CRUISE = 15;
    public static final int SET_COMFORT = 16;
    public static final int SET_SHARED = 17;
    public static final int DELETE_PATHWAY = 18;
    public static final int REVERSE = 19;

    public static final int UNDO = 20;

    public static final int STEP_SPEED = 21;

    public static final int STEP_CORNER = 22;

    public static final int FAIR = 23;

    public static final int FIX = 24;

    public static final int SET_AMPHIBIOUS = 25;

    public static final int STEP_STOP = 26;

    public static final int SPLIT = 27;

    public static final int JOIN = 28;

    public static final int DUPLICATE = 29;

    public static final int SIMPLIFY = 30;

    public static final int SMOOTH = 31;

    public static final int MOVE_LINE = 32;

    public static final int REDO = 33;

    public static final int MAKE_JUNCTION = 34;

    public static final Type<SurveyorEditPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_edit"));

    public static final StreamCodec<FriendlyByteBuf, SurveyorEditPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.op());
                buf.writeUUID(p.pathway());
                buf.writeVarLong(p.version());
                buf.writeVarInt(p.node());
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeVarInt(p.a());
                buf.writeUtf(p.text(), MAX_TEXT);
                buf.writeByteArray(p.data());
            },
            buf -> new SurveyorEditPacket(buf.readVarInt(), buf.readUUID(), buf.readVarLong(), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readUtf(MAX_TEXT),
                    buf.readByteArray(MAX_DATA)));

    public SurveyorEditPacket {
        pathway = pathway == null ? NONE : pathway;
        text = text == null ? "" : (text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
        data = data == null ? new byte[0] : data;
    }

    public static SurveyorEditPacket of(int op, UUID pathway, long version) {
        return new SurveyorEditPacket(op, pathway, version, -1, 0, 0, 0, 0, "", null);
    }

    public static SurveyorEditPacket at(int op, UUID pathway, long version, int node, double x, double y, double z) {
        return new SurveyorEditPacket(op, pathway, version, node, x, y, z, 0, "", null);
    }

    public static SurveyorEditPacket arg(int op, UUID pathway, long version, int node, int a) {
        return new SurveyorEditPacket(op, pathway, version, node, 0, 0, 0, a, "", null);
    }

    public static SurveyorEditPacket value(int op, UUID pathway, long version, int node, double v) {
        return new SurveyorEditPacket(op, pathway, version, node, v, 0, 0, 0, "", null);
    }

    public static SurveyorEditPacket named(int op, UUID pathway, long version, int node, String text) {
        return new SurveyorEditPacket(op, pathway, version, node, 0, 0, 0, 0, text, null);
    }

    public static SurveyorEditPacket bytes(int op, UUID pathway, long version, int node, byte[] data) {
        return new SurveyorEditPacket(op, pathway, version, node, 0, 0, 0, 0, "", data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SurveyorEditPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PathwayOps.apply(player, packet);
        });
    }
}
