package com.createstats.vccmk4.network;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.air.AerialBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AerialActionPacket(BlockPos pos, int action, int a, int b, long target) implements CustomPacketPayload {
    public static final int CLOSE = 0;
    public static final int KEEPALIVE = 1;

    public static final int TELEGRAPH = 2;

    public static final int ALTITUDE = 3;

    public static final int HEADING = 4;

    public static final int TRIM = 5;

    public static final int LEAVE = 6;

    public static final int FEEL = 7;

    public static final int NOSE = 8;

    public static final int CAP = 9;
    public static final int BELL = 10;

    public static final int FORGET = 11;

    public static final int RESCAN = 12;

    public static final int ENGINE_JOB = 13;
    public static final int ENGINE_REVERSE = 14;

    public static final int ENGINE_LIMIT = 15;

    public static final int BALLOON_DRIVEN = 16;

    public static final int SURFACE_ROLE = 17;

    public static final int SURFACE_THROW = 18;
    public static final int SURFACE_INVERT = 19;

    public static final int SURFACE_INPUT = 20;
    public static final int SURFACE_LATCH = 21;

    public static final int TOOL_INPUT = 22;

    public static final int TOOL_RPM = 23;
    public static final int TOOL_LATCH = 24;
    public static final int TOOL_INVERT = 25;

    public static final int LIGHT_JOB = 26;
    public static final int LIGHT_TOGGLE = 27;

    public static final int SET_KEY = 28;

    public static final int KEY_INVERT = 29;
    public static final int KEYS_RESET = 30;

    public static final int PARTS_RESET = 31;

    public static final int RELEARN = 32;

    public static final Type<AerialActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc4_aerial_action"));

    public static final StreamCodec<FriendlyByteBuf, AerialActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.action());
                buf.writeVarInt(packet.a());
                buf.writeVarInt(packet.b());
                buf.writeLong(packet.target());
            },
            buf -> new AerialActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong()));

    public static AerialActionPacket of(BlockPos pos, int action) {
        return new AerialActionPacket(pos, action, 0, 0, 0L);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AerialActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(player.level(), packet.pos());
            if (be instanceof AerialBE aerial) aerial.handleAction(player, packet);
        });
    }
}
