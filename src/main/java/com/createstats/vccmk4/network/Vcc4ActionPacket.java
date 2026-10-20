package com.createstats.vccmk4.network;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.Vcc4BE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// one packet, validated once
public record Vcc4ActionPacket(BlockPos pos, int action, int a, int b, long target) implements CustomPacketPayload {
    public static final int CLOSE = 0;
    public static final int KEEPALIVE = 1;

    public static final int SET_PRESET = 2;
    public static final int SET_AXLE_ROLE = 3;
    public static final int TOGGLE_AXLE_DRIVE = 4;

    public static final int SET_KEY = 5;
    public static final int TOGGLE_INVERT = 6;
    public static final int RESET_KEYS = 7;
    public static final int SET_OPTION = 8;
    public static final int SET_JET_ROLE = 9;
    public static final int SET_SWIVEL_INPUT = 10;
    public static final int SET_SWIVEL_TRAVEL = 11;
    public static final int TOGGLE_SWIVEL_LATCHED = 12;
    public static final int TOGGLE_SWIVEL_INVERT = 13;
    public static final int TOGGLE_GOVERNOR_FLIP = 14;
    public static final int TOGGLE_GOVERNOR_EXCLUDE = 15;
    public static final int FLIP_NOSE = 16;
    public static final int RESET_AUTO = 17;
    public static final int RESCAN = 18;
    public static final int RELEARN = 19;

    public static final int TURN_NOSE = 20;
    public static final int TOGGLE_JET_INVERT = 21;

    public static final int SET_MACHINE_INPUT = 22;

    public static final int SET_MACHINE_RPM = 23;
    public static final int TOGGLE_MACHINE_LATCHED = 24;
    public static final int TOGGLE_MACHINE_INVERT = 25;

    public static final int SET_LINK_SOURCE = 26;
    public static final int TOGGLE_LINK_TOGGLE = 27;

    public static final int SET_TRAILER_MODE = 28;

    public static final int PRESET_AUTO = -1;

    public static final int OPTION_BRAKE_THEN_REVERSE = 0;
    public static final int OPTION_SPEED_STEER = 1;
    public static final int OPTION_STEER_TICKS = 2;
    public static final int OPTION_LOCK = 3;
    public static final int OPTION_MAX_RPM = 4;
    public static final int OPTION_REVERSE = 5;
    public static final int OPTION_JETS_STEER_ALONE = 6;
    public static final int OPTION_THROTTLE_TICKS = 7;
    public static final int OPTION_HOLD = 8;
    public static final int OPTION_PARK_BRAKE = 9;

    public static final int OPTION_LIFT_HOLD = 10;

    public static final int OPTION_RELEASE_TICKS = 11;

    public static final int OPTION_GREETING = 12;

    public static final Type<Vcc4ActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc4_action"));

    public static final StreamCodec<FriendlyByteBuf, Vcc4ActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.action());
                buf.writeVarInt(packet.a());
                buf.writeVarInt(packet.b());
                buf.writeLong(packet.target());
            },
            buf -> new Vcc4ActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong()));

    public static Vcc4ActionPacket of(BlockPos pos, int action) {
        return new Vcc4ActionPacket(pos, action, 0, 0, 0L);
    }

    public static Vcc4ActionPacket of(BlockPos pos, int action, int a) {
        return new Vcc4ActionPacket(pos, action, a, 0, 0L);
    }

    public static Vcc4ActionPacket of(BlockPos pos, int action, int a, int b) {
        return new Vcc4ActionPacket(pos, action, a, b, 0L);
    }

    public static Vcc4ActionPacket part(BlockPos pos, int action, long target, int a) {
        return new Vcc4ActionPacket(pos, action, a, 0, target);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Vcc4ActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(player.level(), packet.pos());
            if (be instanceof Vcc4BE console) console.handleAction(player, packet);
        });
    }
}
