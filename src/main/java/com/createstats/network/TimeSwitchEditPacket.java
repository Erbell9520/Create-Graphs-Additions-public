package com.createstats.network;

import com.createstats.block.TimeSwitchBlock;
import com.createstats.timeswitch.TimeSwitchProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// intents only, the server rechecks everything
public record TimeSwitchEditPacket(BlockPos pos, byte action, byte arg, int mask) implements CustomPacketPayload {
    public static final byte PINS = 0;
    public static final byte MODE = 1;
    public static final byte PRESET = 2;

    public static final CustomPacketPayload.Type<TimeSwitchEditPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "time_switch_edit"));

    public static final StreamCodec<FriendlyByteBuf, TimeSwitchEditPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeByte(pkt.action);
                buf.writeByte(pkt.arg);
                buf.writeVarInt(pkt.mask & TimeSwitchProgram.ALL);
            },
            buf -> new TimeSwitchEditPacket(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readVarInt()));

    public static TimeSwitchEditPacket pins(BlockPos pos, int mask, boolean in) {
        return new TimeSwitchEditPacket(pos, PINS, (byte) (in ? 1 : 0), mask & TimeSwitchProgram.ALL);
    }

    public static TimeSwitchEditPacket mode(BlockPos pos, TimeSwitchProgram.Mode mode) {
        return new TimeSwitchEditPacket(pos, MODE, (byte) mode.id(), 0);
    }

    public static TimeSwitchEditPacket preset(BlockPos pos, TimeSwitchProgram.Preset preset) {
        return new TimeSwitchEditPacket(pos, PRESET, (byte) preset.id(), 0);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TimeSwitchEditPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.isSpectator()) {
                return;
            }
            final Level level = player.level();
            final BlockPos pos = packet.pos;
            if (!level.isLoaded(pos)
                    || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0
                    || !(level.getBlockState(pos).getBlock() instanceof TimeSwitchBlock clock)) {
                return;
            }

            switch (packet.action) {
                case PINS -> clock.setPins(level, pos, packet.mask & TimeSwitchProgram.ALL, packet.arg != 0);
                case MODE -> {
                    if (packet.arg >= 0 && packet.arg < TimeSwitchProgram.Mode.values().length) {
                        clock.setMode(level, pos, TimeSwitchProgram.Mode.byId(packet.arg));
                    }
                }
                case PRESET -> {
                    if (packet.arg >= 0 && packet.arg < TimeSwitchProgram.Preset.values().length) {
                        clock.applyPreset(level, pos, TimeSwitchProgram.Preset.byId(packet.arg));
                    }
                }
                default -> {
                }
            }
        });
    }
}
