package com.createstats.network;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BatteryControllerSetPacket(BlockPos pos, int action, int index, int value)
        implements CustomPacketPayload {
    public static final int ACTION_OUTPUT_ENABLED = 0;

    public static final int ACTION_OUTPUT_MODE = 1;

    public static final int ACTION_OUTPUT_LIMIT = 2;

    public static final int ACTION_OUTPUT_FLOOR = 3;

    public static final int ACTION_OUTPUT_PRIORITY = 4;

    public static final int ACTION_INPUT_ENABLED = 5;

    public static final int ACTION_BAND_UNIT = 6;

    public static final int ACTION_BAND_EDGE = 7;

    public static final int ACTION_BAND_CAP = 8;

    public static final int ACTION_BAND_DEFAULTS = 9;

    public static final CustomPacketPayload.Type<BatteryControllerSetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "create_stats", "battery_controller_set"));

    public static final StreamCodec<FriendlyByteBuf, BatteryControllerSetPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeVarInt(pkt.action());
                        buf.writeVarInt(pkt.index());
                        buf.writeVarInt(pkt.value());
                    },
                    buf -> new BatteryControllerSetPacket(buf.readBlockPos(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BatteryControllerSetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            // reach guard
            if (!player.level().isLoaded(packet.pos())
                    || player.distanceToSqr(packet.pos().getX() + 0.5,
                            packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos()) instanceof BatteryControllerBE controller)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_OUTPUT_ENABLED ->
                        controller.setOutputEnabled(packet.index(), packet.value() != 0);
                case ACTION_OUTPUT_MODE ->
                        controller.setOutputMode(packet.index(), BatteryOutputMode.byOrdinal(packet.value()));
                case ACTION_OUTPUT_LIMIT ->
                        controller.setOutputLimit(packet.index(), packet.value());
                case ACTION_OUTPUT_FLOOR ->
                        controller.setOutputFloorPct(packet.index(), packet.value());
                case ACTION_OUTPUT_PRIORITY ->
                        controller.setOutputPriority(packet.index(), priorityByOrdinal(packet.value()));
                case ACTION_INPUT_ENABLED ->
                        controller.setInputEnabled(packet.index(), packet.value() != 0);
                case ACTION_BAND_UNIT -> controller.setBandUnit(
                        packet.value() == com.createstats.power.FeBandSettings.Unit.PERCENT.ordinal()
                                ? com.createstats.power.FeBandSettings.Unit.PERCENT
                                : com.createstats.power.FeBandSettings.Unit.MINUTES);
                case ACTION_BAND_EDGE -> controller.setBandEdge(packet.index(), packet.value());
                case ACTION_BAND_CAP -> controller.setBandCap(packet.index(), packet.value());
                case ACTION_BAND_DEFAULTS -> controller.resetBand();
                default -> {
                }
            }
        });
    }

    private static FePriority priorityByOrdinal(int ordinal) {
        final FePriority[] all = FePriority.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FePriority.NORMAL;
    }
}
